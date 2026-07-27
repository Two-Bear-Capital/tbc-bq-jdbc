/*
 * Copyright 2026 Two Bear Capital
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package vc.tbc.bq.jdbc.integration.scale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vc.tbc.bq.jdbc.config.MetadataCache;
import vc.tbc.bq.jdbc.metadata.BQDatabaseMetaData;
import vc.tbc.bq.jdbc.metrics.DriverMetrics;
import vc.tbc.bq.jdbc.metrics.MetricsSnapshot;

import java.sql.Connection;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Watches the shared metadata cache across TTL boundaries.
 *
 * <h2>What this is looking for</h2>
 *
 * <p>
 * The metadata cache is static, shared by every connection to a project, and
 * deliberately <em>not</em> cleared when a connection closes — that is what
 * makes it survive IntelliJ reopening connections all day. The cost of that
 * design is that nothing ever prunes it on a schedule: entries are only evicted
 * when someone looks one up and finds it expired.
 *
 * <p>
 * So the question is whether a long-lived process reaches a steady state or
 * accumulates. A cache keyed by query shape that grows without bound is a slow
 * leak that would never show up in a test suite where every JVM lives for
 * seconds. #99 already found one cache that never evicted; this asserts the
 * property rather than trusting it.
 *
 * <h2>How</h2>
 *
 * <p>
 * A short TTL and a small, fixed set of distinct metadata queries, cycled for
 * several TTL periods. A healthy cache holds at most one entry per distinct
 * query no matter how many cycles run. A leaking one grows with cycles.
 *
 * <p>
 * The set of queries is fixed and small on purpose: cycling <em>distinct</em>
 * keys would grow the cache legitimately and prove nothing, since the cache has
 * no size bound and is not claimed to have one.
 */
@DisplayName("Scale: metadata cache steady state")
class MetadataCacheSteadyStateScaleTest extends AbstractScaleTest {

	private static final Logger logger = LoggerFactory.getLogger(MetadataCacheSteadyStateScaleTest.class);

	/** Short enough that several boundaries are crossed in a reasonable runtime. */
	private static final int CACHE_TTL_SECONDS = 2;

	private static final int CYCLES = envInt("BQ_SCALE_CACHE_CYCLES", 6);

	/**
	 * Distinct metadata lookups cycled each round. Each produces its own cache key,
	 * so a healthy cache settles at this many entries.
	 */
	private static final List<String> TABLE_PATTERNS = List.of("%", "t\\_%", "nonexistent\\_%");

	@BeforeEach
	void clearSharedState() {
		// The cache is static and survives connections, so a previous test class in
		// the same JVM would otherwise leave entries that make the size assertions
		// meaningless.
		BQDatabaseMetaData.clearAllSharedCaches();
		DriverMetrics.reset();
	}

	@Test
	@DisplayName("cache size settles instead of growing across repeated TTL expiries")
	void cacheReachesSteadyStateAcrossTtlBoundaries() throws Exception {
		String cacheKey = TEST_PROJECT_ID + ":" + CACHE_TTL_SECONDS;
		MetadataCache cache = BQDatabaseMetaData.getOrCreateSharedCache(cacheKey, Duration.ofSeconds(CACHE_TTL_SECONDS),
				TEST_PROJECT_ID);

		MetricsSnapshot before = DriverMetrics.snapshot();
		List<Integer> sizeAfterEachCycle = new ArrayList<>();

		try (Connection connection = openConnection("&metadataCacheTtl=" + CACHE_TTL_SECONDS)) {
			for (int cycle = 1; cycle <= CYCLES; cycle++) {
				for (String pattern : TABLE_PATTERNS) {
					// Twice in succession: the first call after an expiry is a miss that
					// repopulates, the second must be a hit. Without the second call the
					// run would record no hits at all and the hit-rate assertion below
					// would be measuring nothing.
					readTables(connection, pattern);
					readTables(connection, pattern);
				}

				sizeAfterEachCycle.add(cache.size());
				logger.info("Cycle {}: cache size {} ({})", cycle, cache.size(), cache.getStats());

				// Past the TTL, so the next cycle's first lookup finds every entry
				// expired. The margin covers coarse clock behaviour around the boundary.
				Thread.sleep(Duration.ofSeconds(CACHE_TTL_SECONDS).toMillis() + 500);
			}
		}

		MetricsSnapshot window = DriverMetrics.snapshot().minus(before);
		logger.info("Metrics over the run: {}", window);

		int firstCycleSize = sizeAfterEachCycle.get(0);
		int lastCycleSize = sizeAfterEachCycle.get(sizeAfterEachCycle.size() - 1);

		assertTrue(lastCycleSize <= firstCycleSize,
				() -> String.format(
						"metadata cache grew from %d entries after cycle 1 to %d after cycle %d while cycling "
								+ "the same %d queries — entries are accumulating rather than being replaced. "
								+ "Sizes per cycle: %s",
						firstCycleSize, lastCycleSize, CYCLES, TABLE_PATTERNS.size(), sizeAfterEachCycle));

		assertTrue(lastCycleSize <= TABLE_PATTERNS.size(),
				() -> String.format(
						"cache holds %d entries for %d distinct queries — more entries than there are "
								+ "distinct keys means keys are not stable across calls",
						lastCycleSize, TABLE_PATTERNS.size()));

		// Both must be non-zero: hits prove the cache serves repeats, misses prove
		// the TTL actually expires them. A run with no misses would mean the TTL was
		// never crossed and the growth assertion above proved nothing.
		assertTrue(window.metadataCacheHits() > 0,
				"no cache hits recorded — repeated identical metadata calls are not being served from cache");
		assertTrue(window.metadataCacheMisses() > 0,
				() -> "no cache misses recorded — entries never expired, so no TTL boundary was crossed in " + CYCLES
						+ " cycles of " + CACHE_TTL_SECONDS + "s");

		logger.info("Metadata cache hit rate over the run: {}%",
				String.format("%.1f", 100.0 * window.metadataCacheHitRate()));
	}

	private static void readTables(Connection connection, String tableNamePattern) throws Exception {
		try (ResultSet rs = connection.getMetaData().getTables(null, TEST_DATASET, tableNamePattern, null)) {
			while (rs.next()) {
				rs.getString("TABLE_NAME");
			}
		}
	}
}

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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Iterates a result set far larger than any fixture in the ordinary suite.
 *
 * <p>
 * Two properties are under test, and they fail in different ways:
 *
 * <ul>
 * <li><b>Throughput</b> — rows per second stays above a floor. Catches a
 * per-row cost regression, the kind #99 found when a regex was being compiled
 * for every metadata row.</li>
 * <li><b>Retained heap does not grow with rows read</b> — a forward-only JDBC
 * {@code ResultSet} over a paginated source should hold about one page, not the
 * whole result. If the driver ever starts materialising everything, memory use
 * becomes a function of result size and a large query stops being survivable at
 * all.</li>
 * </ul>
 *
 * <p>
 * The heap assertion is deliberately loose and is the weaker of the two guards.
 * {@code System.gc()} is advisory and a JVM under a generous heap may simply
 * not collect. The stronger guard is the {@code -Xmx} the scale-tests profile
 * pins: full materialisation of this result would exhaust it and the test would
 * die with {@code OutOfMemoryError}, which is unambiguous. A test that merely
 * measured heap under an unbounded {@code -Xmx} could pass while the driver
 * buffered everything.
 *
 * @see AbstractScaleTest
 */
@DisplayName("Scale: large result set iteration")
class LargeResultSetScaleTest extends AbstractScaleTest {

	private static final Logger logger = LoggerFactory.getLogger(LargeResultSetScaleTest.class);

	/**
	 * Rows to generate. A million by default: enough to cross a hundred pages at
	 * the 10,000 default page size, so pagination is genuinely exercised, and
	 * enough that full materialisation would be obvious.
	 */
	private static final int ROW_COUNT = envInt("BQ_SCALE_ROWS", 1_000_000);

	/**
	 * Floor on sustained throughput. Set well below what a healthy run achieves —
	 * the point is to catch a collapse, not to police normal variance in BigQuery
	 * latency, which would make this flake and get the assertion widened until it
	 * meant nothing. That is precisely how #93 stayed hidden.
	 */
	private static final long MIN_ROWS_PER_SECOND = envInt("BQ_SCALE_MIN_ROWS_PER_SEC", 2_000);

	/**
	 * How much retained heap may grow between the first checkpoint and the last.
	 *
	 * <p>
	 * Linear materialisation of a million four-column rows would run to hundreds of
	 * megabytes. A 64 MB allowance is far above the page-sized working set a
	 * streaming implementation needs and far below what buffering would cost, so it
	 * discriminates between the two without tracking GC noise.
	 */
	private static final long MAX_HEAP_GROWTH_BYTES = 64L * 1024 * 1024;

	@Test
	@DisplayName("iterates a million rows at sustained throughput without retaining them")
	void iteratesLargeResultWithoutMaterialising() throws Exception {
		String query = String.format("SELECT row_num, CONCAT('name_', CAST(row_num AS STRING)) AS name, "
				+ "row_num * 100 AS value, MOD(row_num, 2) = 0 AS is_even "
				+ "FROM UNNEST(GENERATE_ARRAY(1, %d)) AS row_num", ROW_COUNT);

		// Heap is sampled at quarter points rather than only at the end: a single
		// end-of-run reading cannot distinguish "held one page throughout" from
		// "held everything and the collector happened to run just before the
		// measurement".
		int checkpointInterval = ROW_COUNT / 4;
		List<Long> heapAtCheckpoints = new ArrayList<>();

		long rowsRead = 0;
		long startNanos = System.nanoTime();

		try (Connection connection = openConnection();
				Statement statement = connection.createStatement();
				ResultSet rs = statement.executeQuery(query)) {

			while (rs.next()) {
				// Every column is read. Reading only the first would let a lazily
				// decoded implementation skip the per-row work this is meant to time.
				long rowNum = rs.getLong(1);
				String name = rs.getString(2);
				rs.getLong(3);
				rs.getBoolean(4);

				rowsRead++;

				// Sanity check on the first row only - asserting per row would make the
				// assertion machinery itself a measurable part of the throughput number.
				if (rowsRead == 1) {
					assertEquals("name_" + rowNum, name, "generated row should round-trip intact");
				}

				if (rowsRead % checkpointInterval == 0) {
					long heap = usedHeapBytes();
					heapAtCheckpoints.add(heap);
					logger.info("Read {} rows, retained heap ~{} MB", rowsRead, heap / (1024 * 1024));
				}
			}
		}

		long elapsedNanos = System.nanoTime() - startNanos;
		// Final copies: the failure messages below are lambdas, and rowsRead was
		// mutated by the read loop.
		final double elapsedSeconds = elapsedNanos / 1_000_000_000.0;
		final long totalRows = rowsRead;
		final long rowsPerSecond = (long) (totalRows / elapsedSeconds);

		logger.info("Read {} rows in {} s ({} rows/s)", totalRows, String.format("%.1f", elapsedSeconds),
				rowsPerSecond);

		assertEquals(ROW_COUNT, totalRows, "every generated row should be returned exactly once");

		assertTrue(rowsPerSecond >= MIN_ROWS_PER_SECOND,
				() -> String.format("throughput collapsed: %d rows/s, floor is %d rows/s (%d rows in %.1f s)",
						rowsPerSecond, MIN_ROWS_PER_SECOND, totalRows, elapsedSeconds));

		assertTrue(heapAtCheckpoints.size() >= 2,
				"expected at least two heap checkpoints — the interval calculation is wrong");

		long firstCheckpoint = heapAtCheckpoints.get(0);
		long lastCheckpoint = heapAtCheckpoints.get(heapAtCheckpoints.size() - 1);
		long growth = lastCheckpoint - firstCheckpoint;

		assertTrue(growth <= MAX_HEAP_GROWTH_BYTES,
				() -> String.format(
						"retained heap grew %d MB between 25%% and 100%% of the result (limit %d MB), which is "
								+ "the signature of the driver materialising rows instead of streaming them. "
								+ "Checkpoints (MB): %s",
						growth / (1024 * 1024), MAX_HEAP_GROWTH_BYTES / (1024 * 1024),
						heapAtCheckpoints.stream().map(bytes -> bytes / (1024 * 1024)).toList()));
	}
}

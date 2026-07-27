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

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vc.tbc.bq.jdbc.metrics.DriverMetrics;
import vc.tbc.bq.jdbc.metrics.MetricsSnapshot;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives the driver through HikariCP under concurrent load.
 *
 * <h2>Why this test exists</h2>
 *
 * <p>
 * A connection pool is how this driver is deployed, and it is the scenario #98
 * broke: queries were dispatched to {@code ForkJoinPool.commonPool()}, so eight
 * concurrent callers serialized and a {@code SELECT 1} took over 30 seconds.
 * That defect was found by a test timeout while enabling parallel execution,
 * after two rounds of widening the timeout that was treating the symptom.
 *
 * <p>
 * What replaced it — {@code SharedThreadPoolGuardTest} — is a bytecode check
 * for executor-less {@code supplyAsync}, {@code runAsync} and
 * {@code parallelStream}. That is a regression guard for the exact shape of the
 * old bug and nothing else. A new way to serialize concurrent queries, through
 * a lock or a bounded executor or a synchronized method, would pass it. This
 * test asserts the property that actually matters: that throughput rises when
 * callers are added.
 *
 * <h2>The three claims</h2>
 *
 * <ol>
 * <li><b>Throughput scales.</b> Concurrent throughput must clear a multiple of
 * single-threaded throughput. The bar is set low on purpose — see
 * {@link #MIN_SCALING_FACTOR}.</li>
 * <li><b>{@code isValid()} stays responsive.</b> A pool calls it on every
 * checkout. If it can be starved by query load, the pool stalls or evicts live
 * connections under exactly the traffic it exists to handle.</li>
 * <li><b>Nothing leaks.</b> Every connection returns to the pool, and Hikari's
 * own leak detector stays quiet.</li>
 * </ol>
 */
@DisplayName("Scale: pooled load")
class PooledLoadScaleTest extends AbstractScaleTest {

	private static final Logger logger = LoggerFactory.getLogger(PooledLoadScaleTest.class);

	private static final int POOL_SIZE = envInt("BQ_SCALE_POOL_SIZE", 8);

	private static final int QUERIES_PER_THREAD = envInt("BQ_SCALE_QUERIES_PER_THREAD", 12);

	/**
	 * How much faster {@link #POOL_SIZE} threads must be than one.
	 *
	 * <p>
	 * Deliberately far below the pool size. Perfect scaling would be
	 * {@code POOL_SIZE}x and a healthy run lands well above this, but the assertion
	 * is not trying to measure efficiency — it is trying to catch a collapse. Under
	 * #98, eight threads were <em>slower</em> than one, so 2x separates the broken
	 * case from the working one by a wide margin while leaving room for BigQuery's
	 * own variance. A tighter bar would flake, get widened, and end up asserting
	 * nothing — the failure mode of #93.
	 */
	private static final double MIN_SCALING_FACTOR = 2.0;

	/**
	 * Ceiling on a single {@code isValid()} call while queries are in flight.
	 *
	 * <p>
	 * A pool checkout that blocks for seconds behind unrelated query traffic is
	 * indistinguishable, from the application's side, from a dead connection.
	 */
	private static final long MAX_IS_VALID_MILLIS = envInt("BQ_SCALE_MAX_IS_VALID_MS", 5_000);

	@Test
	@DisplayName("throughput rises with concurrent callers and no connection leaks")
	void throughputScalesUnderPooledLoad() throws Exception {
		MetricsSnapshot before = DriverMetrics.snapshot();

		try (HikariDataSource pool = createPool()) {
			// Warm the pool before timing anything. Otherwise the single-threaded
			// baseline pays connection establishment and the concurrent run does not,
			// which would inflate the scaling factor into meaninglessness.
			warmUp(pool);

			double singleThreadedOpsPerSecond = measureThroughput(pool, 1);
			logger.info("1 thread:  {} queries/s", String.format("%.2f", singleThreadedOpsPerSecond));

			double concurrentOpsPerSecond = measureThroughput(pool, POOL_SIZE);
			logger.info("{} threads: {} queries/s", POOL_SIZE, String.format("%.2f", concurrentOpsPerSecond));

			double scaling = concurrentOpsPerSecond / singleThreadedOpsPerSecond;
			logger.info("Scaling with {} threads: {}x", POOL_SIZE, String.format("%.2f", scaling));

			assertTrue(scaling >= MIN_SCALING_FACTOR,
					() -> String.format(
							"throughput did not scale: %.2f queries/s at 1 thread, %.2f at %d threads (%.2fx, "
									+ "floor %.1fx). Concurrent queries are being serialized somewhere — this is "
									+ "the shape of #98.",
							singleThreadedOpsPerSecond, concurrentOpsPerSecond, POOL_SIZE, scaling,
							MIN_SCALING_FACTOR));

			// Every borrowed connection must be back. Hikari's leakDetectionThreshold
			// logs a stack trace for anything held too long; this catches the rest.
			assertEquals(0, pool.getHikariPoolMXBean().getActiveConnections(),
					"connections were still checked out after every task completed — the pool is leaking them");
		}

		MetricsSnapshot window = DriverMetrics.snapshot().minus(before);
		logger.info("Metrics over the pooled run: {}", window);

		assertEquals(0, window.queriesFailed(), "no query should have failed under load");

		// Under a pool every physical connection after the first should reuse one
		// credentials object. A rate near zero means each connection is separately
		// probing the environment and fetching a token - the cost #99 removed.
		assertTrue(window.credentialCacheHitRate() > 0.5,
				() -> String.format(
						"credential cache hit rate was %.0f%% across %d pooled connections — connections are "
								+ "rebuilding credentials instead of sharing them",
						100.0 * window.credentialCacheHitRate(), POOL_SIZE));
	}

	@Test
	@DisplayName("isValid() stays responsive while the pool is under query load")
	void isValidStaysResponsiveUnderLoad() throws Exception {
		try (HikariDataSource pool = createPool()) {
			warmUp(pool);

			AtomicInteger completed = new AtomicInteger();
			AtomicLong worstIsValidMillis = new AtomicLong();

			// Saturate the pool with real query traffic, then ask a connection whether
			// it is valid while that traffic is in flight.
			try (ExecutorService load = Executors.newVirtualThreadPerTaskExecutor()) {
				List<Future<?>> running = new ArrayList<>();
				for (int i = 0; i < POOL_SIZE; i++) {
					running.add(load.submit(() -> {
						runQueries(pool, QUERIES_PER_THREAD);
						completed.incrementAndGet();
						return null;
					}));
				}

				// One connection is held back from the load by sizing the pool one
				// larger than the number of load threads, so this never simply waits
				// for a checkout - what is being measured is isValid() itself, not
				// pool contention.
				while (completed.get() < POOL_SIZE) {
					try (Connection connection = pool.getConnection()) {
						long startNanos = System.nanoTime();
						boolean valid = connection.isValid(2);
						long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;

						worstIsValidMillis.accumulateAndGet(elapsedMillis, Math::max);

						assertTrue(valid, "isValid() reported a live pooled connection as invalid under load");
					}
					Thread.sleep(100);
				}

				for (Future<?> future : running) {
					future.get(5, TimeUnit.MINUTES);
				}
			}

			logger.info("Worst isValid() under load: {} ms", worstIsValidMillis.get());

			assertTrue(worstIsValidMillis.get() <= MAX_IS_VALID_MILLIS,
					() -> String.format(
							"isValid() took %d ms under load against a %d ms ceiling — a pool calls this on every "
									+ "checkout, so this stalls the application or evicts healthy connections",
							worstIsValidMillis.get(), MAX_IS_VALID_MILLIS));
		}
	}

	private static HikariDataSource createPool() {
		HikariConfig config = new HikariConfig();
		config.setJdbcUrl(
				String.format("jdbc:bigquery:%s/%s?authType=ADC%s", TEST_PROJECT_ID, TEST_DATASET, COST_CEILING));
		config.setDriverClassName("vc.tbc.bq.jdbc.BQDriver");

		// One more than the number of load threads, so the isValid() probe always has
		// a connection available and measures the call rather than the queue.
		config.setMaximumPoolSize(POOL_SIZE + 1);
		config.setMinimumIdle(POOL_SIZE + 1);

		// Opening a BigQuery connection is not fast, and the pool must not give up on
		// one while the driver is busy.
		config.setConnectionTimeout(TimeUnit.SECONDS.toMillis(60));
		config.setInitializationFailTimeout(TimeUnit.SECONDS.toMillis(60));
		config.setValidationTimeout(TimeUnit.SECONDS.toMillis(10));

		// Logs a stack trace for any connection held longer than this. It does not
		// fail the test by itself - the active-connection assertion does - but it
		// names the culprit when something does leak.
		config.setLeakDetectionThreshold(TimeUnit.SECONDS.toMillis(120));

		config.setPoolName("bq-scale-" + RUN_ID);
		return new HikariDataSource(config);
	}

	/**
	 * Opens and returns every connection once, so the pool is fully established
	 * before anything is timed.
	 */
	private static void warmUp(HikariDataSource pool) throws Exception {
		List<Connection> held = new ArrayList<>();
		try {
			for (int i = 0; i < POOL_SIZE + 1; i++) {
				held.add(pool.getConnection());
			}
		} finally {
			for (Connection connection : held) {
				connection.close();
			}
		}
	}

	/**
	 * Runs {@link #QUERIES_PER_THREAD} queries on each of {@code threads} workers
	 * concurrently and returns the aggregate rate.
	 *
	 * @param pool
	 *            the pool to borrow from
	 * @param threads
	 *            number of concurrent workers
	 * @return queries per second across all workers
	 */
	private static double measureThroughput(HikariDataSource pool, int threads) throws Exception {
		long startNanos = System.nanoTime();

		try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
			List<Callable<Void>> tasks = new ArrayList<>();
			for (int i = 0; i < threads; i++) {
				tasks.add(() -> {
					runQueries(pool, QUERIES_PER_THREAD);
					return null;
				});
			}

			// invokeAll blocks until every task finishes, so the elapsed time below
			// covers the whole batch.
			for (Future<Void> future : executor.invokeAll(tasks)) {
				// Surfaces any exception a worker threw; without this a failing task
				// would show up only as suspiciously good throughput.
				future.get();
			}
		}

		double elapsedSeconds = (System.nanoTime() - startNanos) / 1_000_000_000.0;
		return (threads * QUERIES_PER_THREAD) / elapsedSeconds;
	}

	private static void runQueries(HikariDataSource pool, int count) {
		try {
			for (int i = 0; i < count; i++) {
				// Borrow and return per query, as an application using a pool does.
				try (Connection connection = pool.getConnection();
						Statement statement = connection.createStatement();
						ResultSet rs = statement.executeQuery("SELECT 1 AS value")) {
					assertTrue(rs.next(), "SELECT 1 returned no rows");
					assertEquals(1, rs.getInt(1));
				}
			}
		} catch (Exception e) {
			throw new IllegalStateException("pooled query failed", e);
		}
	}
}

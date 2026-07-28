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
package vc.tbc.bq.jdbc.metrics;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("DriverMetrics")
class DriverMetricsTest {

	private static final long ONE_MILLI_IN_NANOS = TimeUnit.MILLISECONDS.toNanos(1);

	@BeforeEach
	void resetBefore() {
		DriverMetrics.setEnabled(true);
		DriverMetrics.reset();
	}

	@AfterEach
	void resetAfter() {
		// The registry is static and shared with every other test in this JVM.
		// Leaving counters or the enabled flag dirty would make an unrelated test
		// fail depending on ordering.
		DriverMetrics.setEnabled(true);
		DriverMetrics.reset();
	}

	@Nested
	@DisplayName("counting")
	class Counting {

		@Test
		@DisplayName("records successes and failures separately but times both")
		void recordsSuccessesAndFailures() {
			// Submission is counted at dispatch, separately from the terminal
			// counters, so that queriesInFlight() can be non-zero. Recording only
			// completions here would leave queriesSubmitted at 0.
			DriverMetrics.recordQuerySubmitted();
			DriverMetrics.recordQuerySubmitted();
			DriverMetrics.recordQuerySubmitted();

			DriverMetrics.recordQuerySucceeded(10 * ONE_MILLI_IN_NANOS);
			DriverMetrics.recordQuerySucceeded(30 * ONE_MILLI_IN_NANOS);
			DriverMetrics.recordQueryFailed(20 * ONE_MILLI_IN_NANOS);

			MetricsSnapshot snapshot = DriverMetrics.snapshot();

			assertEquals(3, snapshot.queriesSubmitted());
			assertEquals(2, snapshot.queriesSucceeded());
			assertEquals(1, snapshot.queriesFailed());
			assertEquals(0, snapshot.queriesInFlight());

			// Failed queries count toward the timing totals: a workload whose queries
			// fail slowly is exactly what these numbers should reveal.
			assertEquals(20.0, snapshot.meanQueryMillis(), 0.001);
			assertEquals(30.0, snapshot.maxQueryMillis(), 0.001);
		}

		@Test
		@DisplayName("max tracks the slowest query, not the most recent")
		void maxTracksSlowest() {
			DriverMetrics.recordQuerySucceeded(500 * ONE_MILLI_IN_NANOS);
			DriverMetrics.recordQuerySucceeded(5 * ONE_MILLI_IN_NANOS);

			assertEquals(500.0, DriverMetrics.snapshot().maxQueryMillis(), 0.001);
		}

		@Test
		@DisplayName("computes cache hit rates")
		void computesHitRates() {
			DriverMetrics.recordMetadataCacheHit();
			DriverMetrics.recordMetadataCacheHit();
			DriverMetrics.recordMetadataCacheHit();
			DriverMetrics.recordMetadataCacheMiss();

			DriverMetrics.recordCredentialCacheHit();
			DriverMetrics.recordCredentialCacheMiss();

			MetricsSnapshot snapshot = DriverMetrics.snapshot();

			assertEquals(0.75, snapshot.metadataCacheHitRate(), 0.001);
			assertEquals(0.5, snapshot.credentialCacheHitRate(), 0.001);
		}

		@Test
		@DisplayName("tracks open sessions as created minus closed")
		void tracksOpenSessions() {
			DriverMetrics.recordSessionCreated();
			DriverMetrics.recordSessionCreated();
			DriverMetrics.recordSessionClosed();

			assertEquals(1, DriverMetrics.snapshot().sessionsOpen());
		}
	}

	@Nested
	@DisplayName("empty state")
	class EmptyState {

		@Test
		@DisplayName("reports zero rather than dividing by zero")
		void reportsZeroWithNoActivity() {
			MetricsSnapshot snapshot = DriverMetrics.snapshot();

			assertEquals(0.0, snapshot.meanQueryMillis(), 0.001);
			assertEquals(0.0, snapshot.maxQueryMillis(), 0.001);
			assertEquals(0.0, snapshot.metadataCacheHitRate(), 0.001);
			assertEquals(0.0, snapshot.credentialCacheHitRate(), 0.001);
			assertEquals(0, snapshot.queriesInFlight());
			assertEquals(0, snapshot.sessionsOpen());
		}

		@Test
		@DisplayName("never reports negative derived counts")
		void clampsDerivedCounts() {
			// Cannot happen through the public recording methods, but a snapshot taken
			// mid-flight can read completion counters that were incremented after the
			// submitted counter was read. The record must not answer "-1 in flight".
			MetricsSnapshot skewed = new MetricsSnapshot(1, 2, 1, 0, 0, 0, 0, 1, 3, 0, 0);

			assertEquals(0, skewed.queriesInFlight());
			assertEquals(0, skewed.sessionsOpen());
		}
	}

	@Nested
	@DisplayName("disabling")
	class Disabling {

		@Test
		@DisplayName("stops collecting but keeps what was already counted")
		void stopsCollectingWithoutDiscarding() {
			DriverMetrics.recordQuerySucceeded(ONE_MILLI_IN_NANOS);

			DriverMetrics.setEnabled(false);
			assertFalse(DriverMetrics.isEnabled());

			DriverMetrics.recordQuerySucceeded(ONE_MILLI_IN_NANOS);
			DriverMetrics.recordMetadataCacheHit();
			DriverMetrics.recordSessionCreated();

			MetricsSnapshot snapshot = DriverMetrics.snapshot();

			assertEquals(1, snapshot.queriesSucceeded(), "counting continued while disabled");
			assertEquals(0, snapshot.metadataCacheHits());
			assertEquals(0, snapshot.sessionsCreated());
		}
	}

	@Nested
	@DisplayName("snapshot arithmetic")
	class SnapshotArithmetic {

		@Test
		@DisplayName("minus() describes the window, not the life of the JVM")
		void minusDescribesTheWindow() {
			DriverMetrics.recordMetadataCacheMiss();
			DriverMetrics.recordQuerySucceeded(100 * ONE_MILLI_IN_NANOS);
			MetricsSnapshot before = DriverMetrics.snapshot();

			// A window in which the cache performs perfectly, following one in which
			// it did not. The delta's hit rate must describe the window alone.
			DriverMetrics.recordMetadataCacheHit();
			DriverMetrics.recordMetadataCacheHit();
			DriverMetrics.recordQuerySucceeded(10 * ONE_MILLI_IN_NANOS);

			MetricsSnapshot window = DriverMetrics.snapshot().minus(before);

			assertEquals(2, window.metadataCacheHits());
			assertEquals(0, window.metadataCacheMisses());
			assertEquals(1.0, window.metadataCacheHitRate(), 0.001);

			assertEquals(1, window.queriesSucceeded());
			assertEquals(10.0, window.meanQueryMillis(), 0.001);
		}

		@Test
		@DisplayName("minus() carries the max over instead of subtracting it")
		void minusCarriesMaxOver() {
			DriverMetrics.recordQuerySucceeded(900 * ONE_MILLI_IN_NANOS);
			MetricsSnapshot before = DriverMetrics.snapshot();

			DriverMetrics.recordQuerySucceeded(5 * ONE_MILLI_IN_NANOS);
			MetricsSnapshot window = DriverMetrics.snapshot().minus(before);

			// Subtracting maxima would give 0ms here, which is not the maximum of
			// anything. Carrying it over overstates the window instead, which is the
			// documented and safer direction.
			assertEquals(900.0, window.maxQueryMillis(), 0.001);
		}
	}

	@Nested
	@DisplayName("concurrency")
	class Concurrency {

		@Test
		@DisplayName("loses no increments under concurrent recording")
		void losesNoIncrementsUnderContention() throws Exception {
			int threads = 8;
			int perThread = 1_000;

			CountDownLatch startLine = new CountDownLatch(1);
			CountDownLatch finished = new CountDownLatch(threads);

			try (ExecutorService executor = Executors.newFixedThreadPool(threads)) {
				for (int i = 0; i < threads; i++) {
					executor.submit(() -> {
						try {
							startLine.await();
							for (int n = 0; n < perThread; n++) {
								DriverMetrics.recordQuerySucceeded(ONE_MILLI_IN_NANOS);
								DriverMetrics.recordMetadataCacheHit();
							}
						} catch (InterruptedException e) {
							Thread.currentThread().interrupt();
						} finally {
							finished.countDown();
						}
						return null;
					});
				}

				startLine.countDown();
				assertTrue(finished.await(30, TimeUnit.SECONDS), "recording threads did not finish");
			}

			MetricsSnapshot snapshot = DriverMetrics.snapshot();

			assertEquals((long) threads * perThread, snapshot.queriesSucceeded());
			assertEquals((long) threads * perThread, snapshot.metadataCacheHits());
			assertEquals((long) threads * perThread * ONE_MILLI_IN_NANOS, snapshot.queryNanosTotal());
		}
	}

	@Nested
	@DisplayName("toString")
	class ToString {

		@Test
		@DisplayName("names every group so a log line is readable on its own")
		void namesEveryGroup() {
			DriverMetrics.recordQuerySucceeded(ONE_MILLI_IN_NANOS);
			DriverMetrics.recordMetadataCacheHit();
			DriverMetrics.recordSessionCreated();
			DriverMetrics.recordCredentialCacheMiss();

			String rendered = DriverMetrics.snapshot().toString();

			assertTrue(rendered.contains("queries["), rendered);
			assertTrue(rendered.contains("metadataCache["), rendered);
			assertTrue(rendered.contains("sessions["), rendered);
			assertTrue(rendered.contains("credentialCache["), rendered);
		}
	}
}

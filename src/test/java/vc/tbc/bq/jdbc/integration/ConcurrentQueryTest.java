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
package vc.tbc.bq.jdbc.integration;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that queries from separate connections actually run at the same
 * time.
 *
 * <p>
 * Query execution used to be dispatched to {@code ForkJoinPool.commonPool()},
 * whose parallelism is {@code availableProcessors() - 1}, while each task
 * blocks for a whole BigQuery round-trip. Concurrent callers therefore queued
 * behind one another no matter how many connections they held — the shape of a
 * connection pool under load. This test measures overlap rather than elapsed
 * time, so it says something meaningful without being a timing assertion.
 *
 * @since 1.0.99
 */
class ConcurrentQueryTest extends AbstractBigQueryIntegrationTest {

	private static final Logger logger = LoggerFactory.getLogger(ConcurrentQueryTest.class);

	private static final int THREADS = 8;

	@Test
	void queriesFromSeparateConnectionsOverlap() throws Exception {
		List<Connection> connections = new ArrayList<>();
		AtomicInteger inFlight = new AtomicInteger();
		AtomicInteger peakInFlight = new AtomicInteger();
		AtomicInteger completed = new AtomicInteger();
		CountDownLatch ready = new CountDownLatch(THREADS);
		CountDownLatch go = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(THREADS);

		try {
			for (int i = 0; i < THREADS; i++) {
				connections.add(createTestConnection());
			}

			for (Connection conn : connections) {
				Thread.ofVirtual().start(() -> {
					try {
						ready.countDown();
						go.await();

						try (Statement stmt = conn.createStatement()) {
							int current = inFlight.incrementAndGet();
							peakInFlight.accumulateAndGet(current, Math::max);
							try (ResultSet rs = stmt.executeQuery("SELECT 1 AS value")) {
								if (rs.next()) {
									completed.incrementAndGet();
								}
							} finally {
								inFlight.decrementAndGet();
							}
						}
					} catch (Exception e) {
						logger.warn("Concurrent query failed", e);
					} finally {
						done.countDown();
					}
				});
			}

			assertTrue(ready.await(30, TimeUnit.SECONDS), "threads should reach the start line");
			go.countDown();
			assertTrue(done.await(120, TimeUnit.SECONDS), "all concurrent queries should finish");

			assertEquals(THREADS, completed.get(), "every concurrent query should return its row");

			// Serialized execution can never show more than one query in flight. Two is
			// a deliberately weak bar: it fails the old common-pool behaviour without
			// depending on how fast any individual query happens to be.
			logger.info("Peak concurrent queries in flight: {} of {}", peakInFlight.get(), THREADS);
			assertTrue(peakInFlight.get() >= 2, "queries should run concurrently, but never more than "
					+ peakInFlight.get()
					+ " was in flight — query dispatch is serializing (see the ForkJoinPool.commonPool defect)");
		} finally {
			for (Connection conn : connections) {
				try {
					conn.close();
				} catch (SQLException e) {
					logger.debug("Ignoring close failure: {}", e.getMessage());
				}
			}
		}
	}
}

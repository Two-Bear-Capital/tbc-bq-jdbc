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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies that {@code queriesSubmitted} is counted at dispatch, so
 * {@link MetricsSnapshot#queriesInFlight()} can report a non-zero value.
 *
 * <p>
 * Previously the submitted counter was incremented inside the two terminal
 * record methods, which made it equal to succeeded + failed by construction and
 * left in-flight permanently zero. Nothing asserted otherwise.
 *
 * <p>
 * {@code DriverMetrics.reset()} is JVM-global, so this resets in both
 * {@code @BeforeEach} and {@code @AfterEach} to avoid corrupting unrelated
 * tests sharing the JVM.
 *
 * @since 3.0.0
 */
class QueriesInFlightTest {

	@BeforeEach
	void reset() {
		DriverMetrics.reset();
	}

	@AfterEach
	void resetAgain() {
		DriverMetrics.reset();
	}

	@Test
	void dispatchedButUnfinishedQueriesAreInFlight() {
		// Given: three queries dispatched
		DriverMetrics.recordQuerySubmitted();
		DriverMetrics.recordQuerySubmitted();
		DriverMetrics.recordQuerySubmitted();

		// When: one has succeeded and one has failed
		DriverMetrics.recordQuerySucceeded(1_000_000L);
		DriverMetrics.recordQueryFailed(2_000_000L);

		// Then: the third is still in flight
		MetricsSnapshot snapshot = DriverMetrics.snapshot();
		assertEquals(3, snapshot.queriesSubmitted());
		assertEquals(1, snapshot.queriesSucceeded());
		assertEquals(1, snapshot.queriesFailed());
		assertEquals(1, snapshot.queriesInFlight());
	}

	@Test
	void inFlightReturnsToZeroWhenEverythingSettles() {
		// Given: two dispatched queries that both finish
		DriverMetrics.recordQuerySubmitted();
		DriverMetrics.recordQuerySubmitted();
		DriverMetrics.recordQuerySucceeded(1_000_000L);
		DriverMetrics.recordQueryFailed(1_000_000L);

		// Then: nothing is outstanding
		assertEquals(0, DriverMetrics.snapshot().queriesInFlight());
	}

	@Test
	void terminalCountersNoLongerImplySubmission() {
		// Given: a completion recorded with no matching dispatch, which is the shape
		// that previously kept submitted == succeeded + failed at all times
		DriverMetrics.recordQuerySucceeded(1_000_000L);

		// Then: submitted stays at zero, so the two counters are genuinely independent
		MetricsSnapshot snapshot = DriverMetrics.snapshot();
		assertEquals(0, snapshot.queriesSubmitted());
		assertEquals(1, snapshot.queriesSucceeded());
	}
}

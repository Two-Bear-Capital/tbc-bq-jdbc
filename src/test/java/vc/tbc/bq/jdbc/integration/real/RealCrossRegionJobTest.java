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
package vc.tbc.bq.jdbc.integration.real;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vc.tbc.bq.jdbc.exception.BQSQLException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs and cancels a query outside the project's default region.
 *
 * <p>
 * BigQuery job IDs are location-scoped: resolving a job created in {@code EU}
 * from a client defaulting to {@code US} returns 404 unless the location
 * travels with the ID. JetBrains
 * <a href="https://youtrack.jetbrains.com/issue/DBE-20897">DBE-20897</a>
 * reports exactly that, as a cancellation that never lands.
 *
 * <p>
 * The driver does not build job IDs itself, and deliberately should not:
 * {@code BigQueryImpl.create(JobInfo)} stamps the client's configured location
 * onto the ID it generates, and {@code getJob}/{@code cancel} fall back to that
 * same location for an ID that carries none. Supplying our own ID would take
 * the driver off the library's random-ID path, which is what lets a create RPC
 * that fails after the job was accepted recover by fetching it rather than
 * surfacing an error for a job that is running and billing. So nothing in the
 * driver enforces this — which is the reason it needs a test rather than a
 * comment.
 *
 * <p>
 * These connect to the project with <b>no default dataset</b> and query no
 * table. A dataset lives in one region, so pointing an {@code EU} connection at
 * the {@code US} fixture dataset would fail on the dataset rather than on
 * anything this test is about; the region under test is then the connection's
 * alone, and no {@code EU} fixtures have to be provisioned to run it.
 *
 * @since 3.1.0
 */
class RealCrossRegionJobTest extends AbstractRealBigQueryIntegrationTest {

	private static final Logger logger = LoggerFactory.getLogger(RealCrossRegionJobTest.class);

	/**
	 * A region the integration project's dataset is not in — see
	 * {@code terraform/variables.tf}, where {@code region} defaults to {@code US}.
	 */
	private static final String NON_DEFAULT_REGION = "EU";

	/**
	 * Long enough to still be running when the cancel arrives, and referencing no
	 * table so it scans no bytes and can run in any region. A cross join of two
	 * generated arrays is ten billion rows of pure compute.
	 */
	private static final String LONG_RUNNING_QUERY = "SELECT COUNT(*) FROM UNNEST(GENERATE_ARRAY(1, 100000)) a "
			+ "CROSS JOIN UNNEST(GENERATE_ARRAY(1, 100000)) b";

	/**
	 * Bound on the cancel loop below. Generous: it is a backstop against hanging
	 * CI, not a performance assertion.
	 */
	private static final long CANCEL_DEADLINE_SECONDS = 120;

	/**
	 * Opens a connection whose jobs run in the given region, with no default
	 * dataset.
	 */
	private Connection connectionInRegion(String region) throws SQLException {
		String url = String.format("jdbc:bigquery:%s?authType=ADC&location=%s&maxBillingBytes=1073741824",
				TEST_PROJECT_ID, region);
		logger.debug("Connecting with URL: {}", url);
		return DriverManager.getConnection(url);
	}

	/**
	 * The baseline: a job created in a non-default region runs and its results come
	 * back. Reading the results is its own round-trip against the job, so this
	 * covers more than job creation.
	 */
	@Test
	void queryRunsInANonDefaultRegion() throws Exception {
		try (Connection eu = connectionInRegion(NON_DEFAULT_REGION);
				Statement stmt = eu.createStatement();
				ResultSet rs = stmt.executeQuery("SELECT 42 AS answer")) {

			assertTrue(rs.next(), "a query in " + NON_DEFAULT_REGION + " returned no rows");
			assertEquals(42, rs.getInt("answer"));
		}
	}

	/**
	 * The one DBE-20897 describes: cancelling a job that is not in the client's
	 * default region.
	 *
	 * <p>
	 * Both halves matter. {@code cancel()} must not throw — a job ID that does not
	 * resolve comes back as a 404 the driver wraps in {@code SQLException}. And the
	 * query must end in that cancellation rather than completing: {@code cancel()}
	 * is a silent no-op before the job exists, so a test that only asserted "cancel
	 * did not throw" would pass just as happily against a driver whose cancel never
	 * reached anything.
	 */
	@Test
	void cancelResolvesAJobInANonDefaultRegion() throws Exception {
		ExecutorService executor = Executors.newSingleThreadExecutor();
		try (Connection eu = connectionInRegion(NON_DEFAULT_REGION); Statement stmt = eu.createStatement()) {

			Future<?> query = executor.submit(() -> {
				try (ResultSet rs = stmt.executeQuery(LONG_RUNNING_QUERY)) {
					rs.next();
					return null;
				}
			});

			// cancel() is a no-op until the job exists, and the job is created on the
			// other thread, so ask repeatedly rather than guessing how long creation
			// takes. BigQuery's cancel is idempotent, so the extra calls are harmless.
			long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(CANCEL_DEADLINE_SECONDS);
			while (!query.isDone() && System.nanoTime() < deadline) {
				assertDoesNotThrow(stmt::cancel, "cancel() could not resolve the job — the location did not travel "
						+ "with the job ID, which is the DBE-20897 failure");
				Thread.sleep(500);
			}

			ExecutionException failure = assertThrows(ExecutionException.class, () -> query.get(60, TimeUnit.SECONDS),
					"the query completed instead of being cancelled, so cancel() never reached the job and this "
							+ "test proved nothing");
			SQLException cause = assertInstanceOf(SQLException.class, failure.getCause(),
					"a cancelled query must surface as SQLException");
			// Pinning the SQLState is what separates "the cancel landed" from "the
			// query failed for some other reason and the assertion above was
			// satisfied by accident". BigQuery reports a cancelled job with reason
			// "stopped", which AbstractBQStatement.sqlStateFor maps to HY008.
			assertEquals(BQSQLException.SQLSTATE_OPERATION_CANCELED, cause.getSQLState(),
					"the query failed, but not as a cancellation: " + cause.getMessage());
			logger.debug("Cancelled query failed as expected: {}", cause.getMessage());
		} finally {
			executor.shutdownNow();
		}
	}
}

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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import vc.tbc.bq.jdbc.BQPreparedStatement;
import vc.tbc.bq.jdbc.base.AbstractBQStatement;
import vc.tbc.bq.jdbc.util.QueryCostEstimate;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Query cost estimation against real BigQuery.
 *
 * <p>
 * Port of {@code QueryCostEstimationTest} (issue #118). Every assertion about
 * the cost warning in that class sat behind {@code if (warning != null)},
 * because the emulator does not implement dry-run. The feature was therefore
 * entirely optional: deleting the estimation code would not have failed a
 * single test.
 *
 * <p>
 * Real BigQuery does implement dry-run, so the warning is required here.
 *
 * @since 1.0.118
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RealQueryCostEstimationTest extends AbstractRealBigQueryIntegrationTest {

	private static final String TEST_TABLE = tableName("cost_estimation");

	/** A rate to price estimates at. Nothing in the driver assumes this number. */
	private static final BigDecimal RATE = new BigDecimal("6.25");

	private Connection costConnection;

	/** Estimation on, no rate configured. */
	private Connection pricedConnection;

	@BeforeAll
	void setUpClass() throws SQLException {
		createSharedTestTable(TEST_TABLE);
		String url = String.format("jdbc:bigquery:%s/%s?authType=ADC&enableQueryCostEstimation=true%s", TEST_PROJECT_ID,
				TEST_DATASET, TEST_CONNECTION_DEFAULTS);
		costConnection = DriverManager.getConnection(url);
		pricedConnection = DriverManager.getConnection(url + "&queryPricePerTiB=" + RATE);
	}

	@AfterAll
	void tearDownClass() throws SQLException {
		if (costConnection != null && !costConnection.isClosed()) {
			costConnection.close();
		}
		if (pricedConnection != null && !pricedConnection.isClosed()) {
			pricedConnection.close();
		}
		dropSharedTestTable(TEST_TABLE);
	}

	/** The warning the driver raises for an estimated query. */
	private static void assertIsCostWarning(SQLWarning warning) throws SQLException {
		assertNotNull(warning, "Cost estimation is enabled, so a warning must be raised");
		assertNotNull(warning.getMessage(), "Warning should carry a message");
		assertTrue(warning.getMessage().contains("Query will process") || warning.getMessage().contains("estimated"),
				"Warning should describe the estimate, was: " + warning.getMessage());
		assertEquals("01000", warning.getSQLState(), "Should use the standard warning SQL state");
	}

	@Test
	void testCostEstimationRaisesAWarning() throws SQLException {
		try (Statement stmt = costConnection.createStatement();
				ResultSet rs = stmt.executeQuery("SELECT * FROM " + TEST_TABLE)) {
			assertTrue(rs.next(), "Query should return rows");
			assertIsCostWarning(stmt.getWarnings());
		}
	}

	@Test
	void testMultipleQueriesClearWarnings() throws SQLException {
		try (Statement stmt = costConnection.createStatement()) {
			stmt.executeQuery("SELECT * FROM " + TEST_TABLE + " WHERE id = 1").close();
			SQLWarning first = stmt.getWarnings();
			assertIsCostWarning(first);

			stmt.clearWarnings();
			assertNull(stmt.getWarnings(), "Warnings should be cleared");

			stmt.executeQuery("SELECT * FROM " + TEST_TABLE + " WHERE id = 2").close();
			SQLWarning second = stmt.getWarnings();
			assertIsCostWarning(second);

			// The emulator tier only compared these when both happened to be non-null,
			// which on the emulator was never.
			assertNotSame(first, second, "Each execution should raise its own warning");
		}
	}

	@Test
	void testCostEstimationWithPreparedStatement() throws SQLException {
		try (PreparedStatement pstmt = costConnection
				.prepareStatement("SELECT * FROM " + TEST_TABLE + " WHERE id = ?")) {
			pstmt.setInt(1, 1);
			try (ResultSet rs = pstmt.executeQuery()) {
				assertTrue(rs.next(), "Query should return a row");
			}
			assertIsCostWarning(pstmt.getWarnings());
		}
	}

	@Test
	void testCostEstimationDoesNotFailQuery() throws SQLException {
		try (Statement stmt = costConnection.createStatement();
				ResultSet rs = stmt.executeQuery("SELECT COUNT(*) AS cnt FROM " + TEST_TABLE)) {
			assertTrue(rs.next());
			assertEquals(3, rs.getInt("cnt"), "The estimate must not disturb the result");
		}
	}

	@Test
	void testDmlRaisesACostWarning() throws SQLException {
		// #140: enableQueryCostEstimation was read only on the query path, so DML got
		// no dry-run. The emulator test that should have caught this was called
		// testWarningsForDMLStatements and commented "dry-run should work for DML
		// too", but only logged the warning if it happened to exist.
		String table = tableName("cost_dml");
		try (Statement stmt = costConnection.createStatement()) {
			stmt.execute("CREATE OR REPLACE TABLE " + table + " (id INT64, name STRING) "
					+ "OPTIONS(expiration_timestamp = TIMESTAMP_ADD(CURRENT_TIMESTAMP(), INTERVAL 2 HOUR))");
			stmt.clearWarnings();

			assertEquals(1, stmt.executeUpdate("INSERT INTO " + table + " (id, name) VALUES (4, 'David')"),
					"INSERT should report one affected row");
			assertIsCostWarning(stmt.getWarnings());
		} finally {
			try (Statement cleanup = costConnection.createStatement()) {
				cleanup.execute("DROP TABLE IF EXISTS " + table);
			} catch (SQLException ignored) {
				// best effort; the table expires on its own
			}
		}
	}

	@Test
	void testParameterizedDmlIsEstimatedWithItsParameters() throws SQLException {
		// The estimate binds the same parameters as the statement. A dry-run of a
		// parameterized statement without them is rejected by BigQuery, so this
		// failing would show up as a swallowed "Dry-run estimation failed" and no
		// warning — which is exactly what the assertion catches.
		String table = tableName("cost_dml_params");
		try (Statement ddl = costConnection.createStatement()) {
			ddl.execute("CREATE OR REPLACE TABLE " + table + " (id INT64, name STRING) "
					+ "OPTIONS(expiration_timestamp = TIMESTAMP_ADD(CURRENT_TIMESTAMP(), INTERVAL 2 HOUR))");
		}
		try (PreparedStatement pstmt = costConnection
				.prepareStatement("INSERT INTO " + table + " (id, name) VALUES (?, ?)")) {
			pstmt.setInt(1, 7);
			pstmt.setString(2, "estimated");
			assertEquals(1, pstmt.executeUpdate());
			assertIsCostWarning(pstmt.getWarnings());
		} finally {
			try (Statement cleanup = costConnection.createStatement()) {
				cleanup.execute("DROP TABLE IF EXISTS " + table);
			} catch (SQLException ignored) {
				// best effort
			}
		}
	}

	@Test
	void testCollapsedBatchIsEstimatedPerChunkNotPerRow() throws SQLException {
		// The collapsed multi-row INSERT runs one job per chunk, so it estimates per
		// chunk. Sequential batches deliberately do not estimate at all — doing so
		// would double the job count on the driver's most expensive path.
		String table = tableName("cost_batch");
		try (Statement ddl = costConnection.createStatement()) {
			ddl.execute("CREATE OR REPLACE TABLE " + table + " (id INT64, name STRING) "
					+ "OPTIONS(expiration_timestamp = TIMESTAMP_ADD(CURRENT_TIMESTAMP(), INTERVAL 2 HOUR))");
		}
		try (PreparedStatement pstmt = costConnection
				.prepareStatement("INSERT INTO " + table + " (id, name) VALUES (?, ?)")) {
			for (int i = 1; i <= 3; i++) {
				pstmt.setInt(1, i);
				pstmt.setString(2, "row_" + i);
				pstmt.addBatch();
			}
			assertEquals(3, pstmt.executeBatch().length);

			SQLWarning warning = pstmt.getWarnings();
			assertIsCostWarning(warning);
			assertNull(warning.getNextWarning(), "Three rows collapse into one chunk, so one estimate");
			assertEquals(1, pstmt.unwrap(AbstractBQStatement.class).getCostEstimates().size(),
					"The typed estimates track the warning chain, one entry per chunk");
		} finally {
			try (Statement cleanup = costConnection.createStatement()) {
				cleanup.execute("DROP TABLE IF EXISTS " + table);
			} catch (SQLException ignored) {
				// best effort
			}
		}
	}

	@Test
	void testEstimatesAreReadableAsTypedValues() throws SQLException {
		// #195: the SQLWarning used to be the only way to read an estimate, so a
		// caller wanting the byte count had to parse an English sentence.
		try (Statement stmt = costConnection.createStatement();
				ResultSet rs = stmt.executeQuery("SELECT * FROM " + TEST_TABLE)) {
			assertTrue(rs.next());

			List<QueryCostEstimate> estimates = stmt.unwrap(AbstractBQStatement.class).getCostEstimates();
			assertEquals(1, estimates.size(), "One statement, one dry-run, one estimate");

			QueryCostEstimate estimate = estimates.get(0);
			assertNotNull(estimate.totalBytesProcessed(), "BigQuery reports bytes processed for a table scan");
			assertTrue(estimate.totalBytesProcessed() > 0, "A real scan reads something");
			assertTrue(estimate.billableBytes() >= 10L * 1024 * 1024, "BigQuery's 10 MiB minimum applies to a scan");
		}
	}

	@Test
	void testDryRunsReportNoBilledBytes() throws SQLException {
		// Pins the fact the pricing depends on: BigQuery bills nothing for a dry run,
		// so totalBytesBilled is 0 on every estimate however much the query reads.
		// Pricing that figure -- which the driver used to do -- costs every query at
		// zero. If BigQuery ever starts populating it, this test says so.
		try (Statement stmt = connection.createStatement()) {
			QueryCostEstimate estimate = stmt.unwrap(AbstractBQStatement.class)
					.estimateCost("SELECT * FROM " + TEST_TABLE);

			assertEquals(0L, estimate.totalBytesBilled(), "A dry run is not billed");
			assertTrue(estimate.totalBytesProcessed() > 0, "but it does report what the query would read");
		}
	}

	@Test
	void testEstimatesCarryNoCostWithoutAConfiguredRate() throws SQLException {
		// The driver cannot see a customer's contract. Reporting bytes and no money
		// is the honest answer; inventing the on-demand rate is wrong for anyone on
		// editions or a negotiated price.
		try (Statement stmt = costConnection.createStatement();
				ResultSet rs = stmt.executeQuery("SELECT * FROM " + TEST_TABLE)) {
			assertTrue(rs.next());

			QueryCostEstimate estimate = stmt.unwrap(AbstractBQStatement.class).getCostEstimates().get(0);
			assertNull(estimate.estimatedCost());
			assertNull(estimate.pricePerTiB());
			assertFalse(estimate.isPriced());
			assertFalse(stmt.getWarnings().getMessage().contains("estimated cost"),
					"An unpriced estimate must not quote a cost: " + stmt.getWarnings().getMessage());
		}
	}

	@Test
	void testEstimatesArePricedWhenARateIsConfigured() throws SQLException {
		try (Statement stmt = pricedConnection.createStatement();
				ResultSet rs = stmt.executeQuery("SELECT * FROM " + TEST_TABLE)) {
			assertTrue(rs.next());

			QueryCostEstimate estimate = stmt.unwrap(AbstractBQStatement.class).getCostEstimates().get(0);
			assertTrue(estimate.isPriced());
			assertEquals(RATE, estimate.pricePerTiB());
			// The cost prices what the query reads, which is the figure the warning
			// quotes. It is not zero, which pricing totalBytesBilled would have made it.
			assertEquals(QueryCostEstimate.calculateCost(estimate.totalBytesProcessed(), RATE),
					estimate.estimatedCost());
			assertTrue(estimate.estimatedCost().signum() > 0, "A real scan costs something");
			assertTrue(stmt.getWarnings().getMessage().contains("estimated cost"),
					"A priced estimate should quote its cost: " + stmt.getWarnings().getMessage());
		}
	}

	@Test
	void testEstimateCostPricesAStatementWithoutRunningIt() throws SQLException {
		// The point of the on-demand API: no enableQueryCostEstimation on this
		// connection, and the statement must not execute. An INSERT makes that
		// checkable — if the dry-run ran it, the row would be there.
		String table = tableName("estimate_only");
		try (Statement ddl = connection.createStatement()) {
			ddl.execute("CREATE OR REPLACE TABLE " + table + " (id INT64) "
					+ "OPTIONS(expiration_timestamp = TIMESTAMP_ADD(CURRENT_TIMESTAMP(), INTERVAL 2 HOUR))");
		}
		try (Statement stmt = connection.createStatement()) {
			QueryCostEstimate estimate = stmt.unwrap(AbstractBQStatement.class)
					.estimateCost("INSERT INTO " + table + " (id) VALUES (1)");

			assertNotNull(estimate, "estimateCost works without the connection property");
			assertNotNull(estimate.totalBytesProcessed());
			// Not the advisory path, so it raises no warning of its own.
			assertNull(stmt.getWarnings(), "estimateCost is a typed call, not a SQLWarning");

			try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) AS cnt FROM " + table)) {
				assertTrue(rs.next());
				assertEquals(0, rs.getInt("cnt"), "A dry run must not insert the row");
			}
		} finally {
			try (Statement cleanup = connection.createStatement()) {
				cleanup.execute("DROP TABLE IF EXISTS " + table);
			} catch (SQLException ignored) {
				// best effort; the table expires on its own
			}
		}
	}

	@Test
	void testEstimateCostThrowsWhenBigQueryRejectsTheStatement() throws SQLException {
		// The caller asked for the estimate, so a failure to produce one is an answer
		// they need. The automatic path swallows instead, because there an estimate
		// must never be the reason a statement does not run.
		try (Statement stmt = connection.createStatement()) {
			assertThrows(SQLException.class,
					() -> stmt.unwrap(AbstractBQStatement.class).estimateCost("SELECT * FROM no_such_table_here"));
		}
	}

	@Test
	void testPreparedStatementEstimateCostUsesTheBoundParameters() throws SQLException {
		// BigQuery rejects a dry-run of a parameterized statement with no parameters,
		// so this returning an estimate at all proves the bindings were sent.
		try (PreparedStatement pstmt = connection.prepareStatement("SELECT * FROM " + TEST_TABLE + " WHERE id = ?")) {
			pstmt.setInt(1, 1);

			QueryCostEstimate estimate = pstmt.unwrap(BQPreparedStatement.class).estimateCost();

			assertNotNull(estimate);
			assertNotNull(estimate.totalBytesProcessed());
		}
	}

	@Test
	void testEstimationDisabledByDefault() throws SQLException {
		// The inherited connection does not enable estimation, so no warning should
		// appear — the other half of the contract, which the emulator tier could not
		// distinguish from "dry-run unsupported".
		try (Statement stmt = connection.createStatement();
				ResultSet rs = stmt.executeQuery("SELECT * FROM " + TEST_TABLE)) {
			assertTrue(rs.next());
			assertNull(stmt.getWarnings(), "No cost warning without enableQueryCostEstimation");
		}
	}
}

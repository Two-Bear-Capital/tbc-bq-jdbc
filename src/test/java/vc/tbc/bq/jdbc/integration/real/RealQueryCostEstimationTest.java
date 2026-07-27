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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
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

	private Connection costConnection;

	@BeforeAll
	void setUpClass() throws SQLException {
		createSharedTestTable(TEST_TABLE);
		String url = String.format(
				"jdbc:bigquery:%s/%s?authType=ADC&enableQueryCostEstimation=true&maxBillingBytes=1073741824",
				TEST_PROJECT_ID, TEST_DATASET);
		costConnection = DriverManager.getConnection(url);
	}

	@AfterAll
	void tearDownClass() throws SQLException {
		if (costConnection != null && !costConnection.isClosed()) {
			costConnection.close();
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
		} finally {
			try (Statement cleanup = costConnection.createStatement()) {
				cleanup.execute("DROP TABLE IF EXISTS " + table);
			} catch (SQLException ignored) {
				// best effort
			}
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

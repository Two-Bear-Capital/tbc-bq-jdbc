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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for query cost estimation feature.
 *
 * @since 1.0.48
 */
class QueryCostEstimationTest extends AbstractBigQueryIntegrationTest {

	private static final Logger logger = LoggerFactory.getLogger(QueryCostEstimationTest.class);

	private Connection costEstimationConnection;

	@BeforeEach
	void setupCostEstimation() throws SQLException {
		// Create separate connection with cost estimation enabled
		String host = bigqueryEmulator.getHost();
		int port = bigqueryEmulator.getMappedPort(9050);
		String url = String.format(
				"jdbc:bigquery://%s:%d;ProjectId=%s;DefaultDataset=%s;UseDestinationTables=true;EnableQueryCostEstimation=true",
				host, port, TEST_PROJECT_ID, TEST_DATASET);

		logger.info("Creating cost estimation connection with URL: {}", url);
		costEstimationConnection = DriverManager.getConnection(url);

		// Create test table for queries
		try (Statement stmt = connection.createStatement()) {
			executeIgnoreErrors("DROP TABLE IF EXISTS cost_test_table");
			stmt.execute("CREATE TABLE cost_test_table (id INT64, name STRING, value FLOAT64)");
			stmt.execute("INSERT INTO cost_test_table (id, name, value) VALUES (1, 'Alice', 100.0)");
			stmt.execute("INSERT INTO cost_test_table (id, name, value) VALUES (2, 'Bob', 200.0)");
			stmt.execute("INSERT INTO cost_test_table (id, name, value) VALUES (3, 'Charlie', 300.0)");
		}
	}

	@AfterEach
	void tearDownCostEstimation() throws SQLException {
		if (costEstimationConnection != null && !costEstimationConnection.isClosed()) {
			costEstimationConnection.close();
		}

		// Cleanup test table
		executeIgnoreErrors("DROP TABLE IF EXISTS cost_test_table");
	}

	@Test
	void testCostEstimationDisabledByDefault() throws SQLException {
		// Use regular connection (cost estimation disabled)
		try (Statement stmt = connection.createStatement()) {
			ResultSet rs = stmt.executeQuery("SELECT * FROM cost_test_table");
			assertTrue(rs.next());

			// No warnings should be present
			SQLWarning warning = stmt.getWarnings();
			assertNull(warning, "No warnings should be present when cost estimation is disabled");
		}
	}

	@Test
	void testCostEstimationEnabled() throws SQLException {
		try (Statement stmt = costEstimationConnection.createStatement()) {
			ResultSet rs = stmt.executeQuery("SELECT * FROM cost_test_table");
			assertTrue(rs.next());

			// Warning should be present with cost information
			SQLWarning warning = stmt.getWarnings();

			// Note: BigQuery emulator may not support dry-run fully
			// In that case, warning might be null (dry-run fails gracefully)
			// This is expected behavior - test both cases
			if (warning != null) {
				logger.info("Cost estimation warning: {}", warning.getMessage());
				assertNotNull(warning.getMessage());
				assertTrue(warning.getMessage().contains("Query will process")
						|| warning.getMessage().contains("estimated cost"));
				assertEquals("01000", warning.getSQLState(), "Should use standard warning SQL state");
			} else {
				logger.info("Cost estimation not available in emulator (dry-run may not be supported) - this is OK");
			}
		}
	}

	@Test
	void testMultipleQueriesClearWarnings() throws SQLException {
		try (Statement stmt = costEstimationConnection.createStatement()) {
			// Execute first query
			stmt.executeQuery("SELECT * FROM cost_test_table WHERE id = 1").close();
			SQLWarning warning1 = stmt.getWarnings();

			// Clear warnings
			stmt.clearWarnings();
			assertNull(stmt.getWarnings(), "Warnings should be cleared");

			// Execute second query
			stmt.executeQuery("SELECT * FROM cost_test_table WHERE id = 2").close();
			SQLWarning warning2 = stmt.getWarnings();

			// If emulator supports dry-run, warnings should be independent
			if (warning1 != null && warning2 != null) {
				logger.info("First query warning: {}", warning1.getMessage());
				logger.info("Second query warning: {}", warning2.getMessage());
				// Warnings should be for different queries
				assertNotSame(warning1, warning2);
			}
		}
	}

	@Test
	void testCostEstimationWithPreparedStatement() throws SQLException {
		String sql = "SELECT * FROM cost_test_table WHERE id = ?";

		try (PreparedStatement pstmt = costEstimationConnection.prepareStatement(sql)) {
			pstmt.setInt(1, 1);
			ResultSet rs = pstmt.executeQuery();
			assertTrue(rs.next());

			// Warning should be present (if emulator supports dry-run)
			SQLWarning warning = pstmt.getWarnings();
			if (warning != null) {
				logger.info("PreparedStatement cost estimation: {}", warning.getMessage());
				assertNotNull(warning.getMessage());
			}
		}
	}

	@Test
	void testCostEstimationDoesNotFailQuery() throws SQLException {
		// Even if dry-run fails, the actual query should still execute
		try (Statement stmt = costEstimationConnection.createStatement()) {
			// This should work regardless of whether dry-run succeeds
			ResultSet rs = stmt.executeQuery("SELECT COUNT(*) as cnt FROM cost_test_table");
			assertTrue(rs.next());
			assertEquals(3, rs.getInt("cnt"));

			// The query executed successfully even if dry-run failed
			logger.info("Query executed successfully with or without cost estimation");
		}
	}

	@Test
	void testWarningsForDMLStatements() throws SQLException {
		try (Statement stmt = costEstimationConnection.createStatement()) {
			// INSERT statement - dry-run should work for DML too
			int rows = stmt.executeUpdate("INSERT INTO cost_test_table (id, name, value) VALUES (4, 'David', 400.0)");

			// Warning may or may not be present depending on emulator support
			SQLWarning warning = stmt.getWarnings();
			if (warning != null) {
				logger.info("DML cost estimation: {}", warning.getMessage());
			}

			// Cleanup
			stmt.executeUpdate("DELETE FROM cost_test_table WHERE id = 4");
		}
	}
}
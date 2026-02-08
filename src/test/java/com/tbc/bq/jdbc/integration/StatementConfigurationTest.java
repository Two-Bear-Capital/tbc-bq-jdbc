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
package com.tbc.bq.jdbc.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Statement configuration methods.
 *
 * <p>
 * Tests Statement configuration features including:
 * <ul>
 * <li>Query timeout (setQueryTimeout/getQueryTimeout)
 * <li>Max rows (setMaxRows/getMaxRows)
 * <li>Fetch size (setFetchSize/getFetchSize)
 * <li>Statement cancellation
 * <li>Escape processing
 * <li>Max field size
 * </ul>
 *
 * @since 1.0.0
 */
class StatementConfigurationTest extends AbstractBigQueryIntegrationTest {

	private static final Logger logger = LoggerFactory.getLogger(StatementConfigurationTest.class);

	private static final String TEST_TABLE = "config_test_table";

	@BeforeEach
	void setupTestTable() throws SQLException {
		createTestTable(TEST_TABLE);
		insertTestData(TEST_TABLE);
	}

	@Test
	void testGetQueryTimeoutDefaultsToZero() throws SQLException {
		// Given: A new statement
		try (Statement stmt = connection.createStatement()) {

			// When: Getting the default query timeout
			int timeout = stmt.getQueryTimeout();

			// Then: Should default to 0 (no timeout)
			assertEquals(0, timeout, "Default query timeout should be 0");
		}
	}

	@Test
	void testSetAndGetQueryTimeout() throws SQLException {
		// Given: A statement
		try (Statement stmt = connection.createStatement()) {

			// When: Setting query timeout
			stmt.setQueryTimeout(30);

			// Then: Should return the set value
			assertEquals(30, stmt.getQueryTimeout(), "Query timeout should be 30");

			// And: Setting different value
			stmt.setQueryTimeout(60);
			assertEquals(60, stmt.getQueryTimeout(), "Query timeout should be 60");

			// And: Setting to 0 should work
			stmt.setQueryTimeout(0);
			assertEquals(0, stmt.getQueryTimeout(), "Query timeout should be 0");
		}
	}

	@Test
	void testQueryExecutesWithinTimeout() throws SQLException {
		// Given: A statement with reasonable timeout
		try (Statement stmt = connection.createStatement()) {
			stmt.setQueryTimeout(30);

			// When: Executing a simple query
			ResultSet rs = stmt.executeQuery("SELECT * FROM " + TEST_TABLE);

			// Then: Query should complete successfully
			assertTrue(rs.next(), "Should have results");
			rs.close();
		}
	}

	@Test
	void testGetMaxRowsDefaultsToZero() throws SQLException {
		// Given: A new statement
		try (Statement stmt = connection.createStatement()) {

			// When: Getting the default max rows
			int maxRows = stmt.getMaxRows();

			// Then: Should default to 0 (unlimited)
			assertEquals(0, maxRows, "Default max rows should be 0 (unlimited)");
		}
	}

	@Test
	void testSetAndGetMaxRows() throws SQLException {
		// Given: A statement
		try (Statement stmt = connection.createStatement()) {

			// When: Setting max rows
			stmt.setMaxRows(10);

			// Then: Should return the set value
			assertEquals(10, stmt.getMaxRows(), "Max rows should be 10");

			// And: Setting different value
			stmt.setMaxRows(100);
			assertEquals(100, stmt.getMaxRows(), "Max rows should be 100");

			// And: Setting to 0 should work
			stmt.setMaxRows(0);
			assertEquals(0, stmt.getMaxRows(), "Max rows should be 0 (unlimited)");
		}
	}

	@Test
	void testMaxRowsLimitsResultSet() throws SQLException {
		// Given: A statement with max rows set to 2
		try (Statement stmt = connection.createStatement()) {
			stmt.setMaxRows(2);

			// When: Executing a query that would return 3 rows
			ResultSet rs = stmt.executeQuery("SELECT * FROM " + TEST_TABLE + " ORDER BY id");

			// Then: Should only get 2 rows
			assertTrue(rs.next(), "Should have first row");
			assertEquals(1, rs.getInt("id"));

			assertTrue(rs.next(), "Should have second row");
			assertEquals(2, rs.getInt("id"));

			assertFalse(rs.next(), "Should not have third row (limited by maxRows)");
			rs.close();
		}
	}

	@Test
	void testMaxRowsZeroReturnsAllRows() throws SQLException {
		// Given: A statement with max rows set to 0 (unlimited)
		try (Statement stmt = connection.createStatement()) {
			stmt.setMaxRows(0);

			// When: Executing a query
			ResultSet rs = stmt.executeQuery("SELECT * FROM " + TEST_TABLE);

			// Then: Should get all 3 rows
			int count = 0;
			while (rs.next()) {
				count++;
			}
			assertEquals(3, count, "Should return all 3 rows");
			rs.close();
		}
	}

	@Test
	void testGetFetchSizeDefaultsToZero() throws SQLException {
		// Given: A new statement
		try (Statement stmt = connection.createStatement()) {

			// When: Getting the default fetch size
			int fetchSize = stmt.getFetchSize();

			// Then: Should return a value (0 or default page size)
			assertTrue(fetchSize >= 0, "Fetch size should be non-negative");
			logger.info("Default fetch size: {}", fetchSize);
		}
	}

	@Test
	void testSetAndGetFetchSize() throws SQLException {
		// Given: A statement
		try (Statement stmt = connection.createStatement()) {

			// When: Setting fetch size
			stmt.setFetchSize(100);

			// Then: Should return the set value
			assertEquals(100, stmt.getFetchSize(), "Fetch size should be 100");

			// And: Setting different value
			stmt.setFetchSize(1000);
			assertEquals(1000, stmt.getFetchSize(), "Fetch size should be 1000");
		}
	}

	@Test
	void testFetchSizeDoesNotAffectResults() throws SQLException {
		// Given: Statements with different fetch sizes
		try (Statement stmt1 = connection.createStatement(); Statement stmt2 = connection.createStatement()) {

			stmt1.setFetchSize(1);
			stmt2.setFetchSize(1000);

			// When: Executing the same query with different fetch sizes
			ResultSet rs1 = stmt1.executeQuery("SELECT * FROM " + TEST_TABLE + " ORDER BY id");
			ResultSet rs2 = stmt2.executeQuery("SELECT * FROM " + TEST_TABLE + " ORDER BY id");

			// Then: Both should return the same results
			while (rs1.next() && rs2.next()) {
				assertEquals(rs1.getInt("id"), rs2.getInt("id"), "Results should be identical");
			}

			assertFalse(rs1.next(), "Both result sets should be exhausted");
			assertFalse(rs2.next(), "Both result sets should be exhausted");

			rs1.close();
			rs2.close();
		}
	}

	@Test
	void testGetMaxFieldSizeReturnsZero() throws SQLException {
		// Given: A statement
		try (Statement stmt = connection.createStatement()) {

			// When: Getting max field size
			int maxFieldSize = stmt.getMaxFieldSize();

			// Then: Should return 0 (unlimited)
			assertEquals(0, maxFieldSize, "Max field size should be 0 (unlimited)");
		}
	}

	@Test
	void testSetMaxFieldSizeDoesNotThrow() throws SQLException {
		// Given: A statement
		try (Statement stmt = connection.createStatement()) {

			// When: Setting max field size
			// Then: Should not throw (but may be ignored)
			assertDoesNotThrow(() -> stmt.setMaxFieldSize(1000), "setMaxFieldSize should not throw");
		}
	}

	@Test
	void testSetEscapeProcessingDoesNotThrow() throws SQLException {
		// Given: A statement
		try (Statement stmt = connection.createStatement()) {

			// When: Setting escape processing
			// Then: Should not throw
			assertDoesNotThrow(() -> stmt.setEscapeProcessing(true), "setEscapeProcessing(true) should not throw");
			assertDoesNotThrow(() -> stmt.setEscapeProcessing(false), "setEscapeProcessing(false) should not throw");
		}
	}

	@Test
	void testGetResultSetType() throws SQLException {
		// Given: A statement created with default parameters
		try (Statement stmt = connection.createStatement()) {

			// When: Getting result set type
			int type = stmt.getResultSetType();

			// Then: Should be TYPE_FORWARD_ONLY
			assertEquals(ResultSet.TYPE_FORWARD_ONLY, type, "Result set type should be TYPE_FORWARD_ONLY");
		}
	}

	@Test
	void testGetResultSetConcurrency() throws SQLException {
		// Given: A statement created with default parameters
		try (Statement stmt = connection.createStatement()) {

			// When: Getting result set concurrency
			int concurrency = stmt.getResultSetConcurrency();

			// Then: Should be CONCUR_READ_ONLY
			assertEquals(ResultSet.CONCUR_READ_ONLY, concurrency, "Result set concurrency should be CONCUR_READ_ONLY");
		}
	}

	@Test
	void testGetResultSetHoldability() throws SQLException {
		// Given: A statement
		try (Statement stmt = connection.createStatement()) {

			// When: Getting result set holdability
			int holdability = stmt.getResultSetHoldability();

			// Then: Should be CLOSE_CURSORS_AT_COMMIT
			assertEquals(ResultSet.CLOSE_CURSORS_AT_COMMIT, holdability,
					"Result set holdability should be CLOSE_CURSORS_AT_COMMIT");
		}
	}

	@Test
	void testGetUpdateCountReturnsMinusOne() throws SQLException {
		// Given: A statement that executed a query
		try (Statement stmt = connection.createStatement()) {
			ResultSet rs = stmt.executeQuery("SELECT * FROM " + TEST_TABLE);
			rs.close();

			// When: Getting update count
			int updateCount = stmt.getUpdateCount();

			// Then: Should return -1 (no update count for SELECT)
			assertEquals(-1, updateCount, "Update count should be -1 for SELECT queries");
		}
	}

	@Test
	void testGetMoreResultsReturnsFalse() throws SQLException {
		// Given: A statement that executed a query
		try (Statement stmt = connection.createStatement()) {
			ResultSet rs = stmt.executeQuery("SELECT * FROM " + TEST_TABLE);
			rs.close();

			// When: Calling getMoreResults
			boolean hasMoreResults = stmt.getMoreResults();

			// Then: Should return false (BigQuery doesn't support multiple result sets)
			assertFalse(hasMoreResults, "getMoreResults should return false");
		}
	}

	@Test
	void testGetConnection() throws SQLException {
		// Given: A statement
		try (Statement stmt = connection.createStatement()) {

			// When: Getting the connection
			Connection conn = stmt.getConnection();

			// Then: Should return the same connection
			assertSame(connection, conn, "Should return the same connection");
		}
	}

	@Test
	void testGetWarningsReturnsNull() throws SQLException {
		// Given: A statement
		try (Statement stmt = connection.createStatement()) {

			// When: Getting warnings
			SQLWarning warnings = stmt.getWarnings();

			// Then: Should return null (no warnings by default)
			assertNull(warnings, "Warnings should be null");
		}
	}

	@Test
	void testClearWarningsDoesNotThrow() throws SQLException {
		// Given: A statement
		try (Statement stmt = connection.createStatement()) {

			// When: Clearing warnings
			// Then: Should not throw
			assertDoesNotThrow(stmt::clearWarnings, "clearWarnings should not throw");
		}
	}

	@Test
	void testIsPoolableDefaultsToFalse() throws SQLException {
		// Given: A statement
		try (Statement stmt = connection.createStatement()) {

			// When: Checking if poolable
			boolean isPoolable = stmt.isPoolable();

			// Then: Should be false by default
			assertFalse(isPoolable, "Statement should not be poolable by default");
		}
	}

	@Test
	void testSetPoolableDoesNotThrow() throws SQLException {
		// Given: A statement
		try (Statement stmt = connection.createStatement()) {

			// When: Setting poolable
			// Then: Should not throw
			assertDoesNotThrow(() -> stmt.setPoolable(true), "setPoolable(true) should not throw");
			assertDoesNotThrow(() -> stmt.setPoolable(false), "setPoolable(false) should not throw");
		}
	}

	@Test
	void testIsCloseOnCompletionDefaultsToFalse() throws SQLException {
		// Given: A statement
		try (Statement stmt = connection.createStatement()) {

			// When: Checking if close on completion
			boolean closeOnCompletion = stmt.isCloseOnCompletion();

			// Then: Should be false by default
			assertFalse(closeOnCompletion, "Should not be close on completion by default");
		}
	}

	@Test
	void testCloseOnCompletionDoesNotThrow() throws SQLException {
		// Given: A statement
		try (Statement stmt = connection.createStatement()) {

			// When: Setting close on completion
			// Then: Should not throw
			assertDoesNotThrow(stmt::closeOnCompletion, "closeOnCompletion should not throw");
		}
	}

	@Test
	void testStatementConfigurationPersistsAcrossQueries() throws SQLException {
		// Given: A statement with custom configuration
		try (Statement stmt = connection.createStatement()) {
			stmt.setQueryTimeout(30);
			stmt.setMaxRows(5);
			stmt.setFetchSize(100);

			// When: Executing multiple queries
			ResultSet rs1 = stmt.executeQuery("SELECT * FROM " + TEST_TABLE + " LIMIT 1");
			rs1.close();

			ResultSet rs2 = stmt.executeQuery("SELECT * FROM " + TEST_TABLE + " LIMIT 2");
			rs2.close();

			// Then: Configuration should persist
			assertEquals(30, stmt.getQueryTimeout(), "Query timeout should persist");
			assertEquals(5, stmt.getMaxRows(), "Max rows should persist");
			assertEquals(100, stmt.getFetchSize(), "Fetch size should persist");
		}
	}

	@Test
	void testMultipleStatementsHaveIndependentConfiguration() throws SQLException {
		// Given: Two statements with different configurations
		try (Statement stmt1 = connection.createStatement(); Statement stmt2 = connection.createStatement()) {

			// When: Setting different configurations
			stmt1.setQueryTimeout(10);
			stmt1.setMaxRows(5);

			stmt2.setQueryTimeout(20);
			stmt2.setMaxRows(10);

			// Then: Each should maintain its own configuration
			assertEquals(10, stmt1.getQueryTimeout(), "Statement 1 should have timeout 10");
			assertEquals(5, stmt1.getMaxRows(), "Statement 1 should have max rows 5");

			assertEquals(20, stmt2.getQueryTimeout(), "Statement 2 should have timeout 20");
			assertEquals(10, stmt2.getMaxRows(), "Statement 2 should have max rows 10");
		}
	}
}

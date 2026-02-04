/*
 * Copyright 2026 Two Bear Capital
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.twobearcapital.bigquery.jdbc.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.*;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for basic connection functionality.
 *
 * @since 1.0.0
 */
class BasicConnectionTest extends AbstractBigQueryIntegrationTest {

	@Test
	void testConnectionIsValid() throws SQLException {
		// Then: Connection should be valid
		assertTrue(connection.isValid(5));
		assertFalse(connection.isClosed());
	}

	@Test
	void testGetCatalog() throws SQLException {
		// When: Getting catalog
		String catalog = connection.getCatalog();

		// Then: Should return project ID
		assertEquals(TEST_PROJECT_ID, catalog);
	}

	@Test
	void testGetSchema() throws SQLException {
		// When: Getting schema
		String schema = connection.getSchema();

		// Then: Should return dataset
		assertEquals(TEST_DATASET, schema);
	}

	@Test
	void testAutoCommitIsTrue() throws SQLException {
		// Then: Auto-commit should be true (BigQuery has no transactions)
		assertTrue(connection.getAutoCommit());
	}

	@Test
	void testSetAutoCommitFalseThrowsException() {
		// Then: Setting auto-commit to false should throw exception
		assertThrows(SQLFeatureNotSupportedException.class, () -> connection.setAutoCommit(false));
	}

	@Test
	void testCommitThrowsException() {
		// Then: Commit should throw exception
		assertThrows(SQLFeatureNotSupportedException.class, () -> connection.commit());
	}

	@Test
	void testRollbackThrowsException() {
		// Then: Rollback should throw exception
		assertThrows(SQLFeatureNotSupportedException.class, () -> connection.rollback());
	}

	@Test
	void testTransactionIsolationIsNone() throws SQLException {
		// When: Getting transaction isolation
		int isolation = connection.getTransactionIsolation();

		// Then: Should be TRANSACTION_NONE
		assertEquals(Connection.TRANSACTION_NONE, isolation);
	}

	@Test
	void testSetTransactionIsolationNonNoneThrowsException() {
		// Then: Setting isolation level should throw exception
		assertThrows(SQLFeatureNotSupportedException.class,
				() -> connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED));
	}

	@Test
	void testGetMetaData() throws SQLException {
		// When: Getting metadata
		DatabaseMetaData metaData = connection.getMetaData();

		// Then: Should return metadata object
		assertNotNull(metaData);
		assertEquals("BigQuery (TBC Driver)", metaData.getDatabaseProductName());
		assertEquals("Two Bear Capital BigQuery JDBC Driver", metaData.getDriverName());
	}

	@Test
	void testCreateStatement() throws SQLException {
		// When: Creating statement
		Statement stmt = connection.createStatement();

		// Then: Should create successfully
		assertNotNull(stmt);
		assertFalse(stmt.isClosed());

		stmt.close();
		assertTrue(stmt.isClosed());
	}

	@Test
	void testPrepareStatement() throws SQLException {
		// When: Preparing statement
		PreparedStatement pstmt = connection.prepareStatement("SELECT ?");

		// Then: Should create successfully
		assertNotNull(pstmt);
		assertFalse(pstmt.isClosed());

		pstmt.close();
		assertTrue(pstmt.isClosed());
	}

	@Test
	void testCloseConnection() throws SQLException {
		// Given: An open connection
		Connection conn = createTestConnection();
		assertFalse(conn.isClosed());

		// When: Closing connection
		conn.close();

		// Then: Should be closed
		assertTrue(conn.isClosed());

		// And: Operations should throw exception
		assertThrows(SQLException.class, conn::createStatement);
	}

	@Test
	void testMultipleConnections() throws SQLException {
		// Given: Multiple connections
		Connection conn1 = createTestConnection();
		Connection conn2 = createTestConnection();

		// Then: Both should be independent and valid
		assertTrue(conn1.isValid(5));
		assertTrue(conn2.isValid(5));

		// When: Closing one
		conn1.close();

		// Then: Other should still be valid
		assertTrue(conn1.isClosed());
		assertFalse(conn2.isClosed());
		assertTrue(conn2.isValid(5));

		conn2.close();
	}

	@Test
	void testConnectionWarnings() throws SQLException {
		// When: Getting warnings
		SQLWarning warning = connection.getWarnings();

		// Then: Should be null (no warnings)
		assertNull(warning);

		// When: Clearing warnings
		connection.clearWarnings();

		// Then: Should not throw exception
		assertNull(connection.getWarnings());
	}

	@Test
	void testBeginEndRequest() throws SQLException {
		// When: Using request lifecycle methods
		connection.beginRequest();
		connection.endRequest();

		// Then: Should not throw exception
		assertTrue(connection.isValid(5));
	}

	@Test
	void testSetReadOnly() throws SQLException {
		// When: Setting read-only
		connection.setReadOnly(true);

		// Then: Should be set
		assertTrue(connection.isReadOnly());

		// When: Setting back to read-write
		connection.setReadOnly(false);

		// Then: Should be unset
		assertFalse(connection.isReadOnly());
	}

	@Test
	void testNativeSQL() throws SQLException {
		// Given: A SQL string
		String sql = "SELECT * FROM table";

		// When: Converting to native SQL
		String nativeSql = connection.nativeSQL(sql);

		// Then: Should return as-is (no transformation)
		assertEquals(sql, nativeSql);
	}
}

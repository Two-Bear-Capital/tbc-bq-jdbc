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

import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real BigQuery integration tests for basic connection functionality.
 *
 * <p>
 * Mirrors {@link vc.tbc.bq.jdbc.integration.BasicConnectionTest} but runs
 * against a real BigQuery instance.
 *
 * @since 1.0.68
 */
class RealBasicConnectionTest extends AbstractRealBigQueryIntegrationTest {

	@Test
	void testConnectionIsValid() throws SQLException {
		assertTrue(connection.isValid(5));
		assertFalse(connection.isClosed());
	}

	@Test
	void testGetCatalog() throws SQLException {
		String catalog = connection.getCatalog();
		assertEquals(TEST_PROJECT_ID, catalog);
	}

	@Test
	void testGetSchema() throws SQLException {
		String schema = connection.getSchema();
		assertEquals(TEST_DATASET, schema);
	}

	@Test
	void testAutoCommitIsTrue() throws SQLException {
		assertTrue(connection.getAutoCommit());
	}

	@Test
	void testSetAutoCommitFalseIsSupported() throws SQLException {
		// Disabling auto-commit starts a BigQuery session on demand
		connection.setAutoCommit(false);
		assertFalse(connection.getAutoCommit());
		connection.setAutoCommit(true);
		assertTrue(connection.getAutoCommit());
	}

	@Test
	void testCommitInAutoCommitModeThrowsException() {
		assertThrows(SQLException.class, () -> connection.commit());
	}

	@Test
	void testRollbackInAutoCommitModeThrowsException() {
		assertThrows(SQLException.class, () -> connection.rollback());
	}

	@Test
	void testTransactionIsolationIsRepeatableRead() throws SQLException {
		int isolation = connection.getTransactionIsolation();
		assertEquals(Connection.TRANSACTION_REPEATABLE_READ, isolation);
	}

	@Test
	void testSetTransactionIsolationUnsupportedLevelThrowsException() {
		assertThrows(SQLFeatureNotSupportedException.class,
				() -> connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED));
	}

	@Test
	void testGetMetaData() throws SQLException {
		DatabaseMetaData metaData = connection.getMetaData();
		assertNotNull(metaData);
		assertEquals("BigQuery (TBC Driver)", metaData.getDatabaseProductName());
		assertEquals("Two Bear Capital BigQuery JDBC Driver", metaData.getDriverName());
	}

	@Test
	void testCreateStatement() throws SQLException {
		Statement stmt = connection.createStatement();
		assertNotNull(stmt);
		assertFalse(stmt.isClosed());

		stmt.close();
		assertTrue(stmt.isClosed());
	}

	@Test
	void testPrepareStatement() throws SQLException {
		PreparedStatement pstmt = connection.prepareStatement("SELECT ?");
		assertNotNull(pstmt);
		assertFalse(pstmt.isClosed());

		pstmt.close();
		assertTrue(pstmt.isClosed());
	}

	@Test
	void testCloseConnection() throws SQLException {
		Connection conn = createTestConnection();
		assertFalse(conn.isClosed());

		conn.close();
		assertTrue(conn.isClosed());

		assertThrows(SQLException.class, conn::createStatement);
	}

	@Test
	void testMultipleConnections() throws SQLException {
		Connection conn1 = createTestConnection();
		Connection conn2 = createTestConnection();

		assertTrue(conn1.isValid(5));
		assertTrue(conn2.isValid(5));

		conn1.close();

		assertTrue(conn1.isClosed());
		assertFalse(conn2.isClosed());
		assertTrue(conn2.isValid(5));

		conn2.close();
	}

	@Test
	void testConnectionWarnings() throws SQLException {
		SQLWarning warning = connection.getWarnings();
		assertNull(warning);

		connection.clearWarnings();
		assertNull(connection.getWarnings());
	}

	@Test
	void testBeginEndRequest() throws SQLException {
		connection.beginRequest();
		connection.endRequest();

		assertTrue(connection.isValid(5));
	}

	@Test
	void testSetReadOnly() throws SQLException {
		connection.setReadOnly(true);
		assertTrue(connection.isReadOnly());

		connection.setReadOnly(false);
		assertFalse(connection.isReadOnly());
	}

	@Test
	void testNativeSQL() throws SQLException {
		String sql = "SELECT * FROM table";
		String nativeSql = connection.nativeSQL(sql);
		assertEquals(sql, nativeSql);
	}
}

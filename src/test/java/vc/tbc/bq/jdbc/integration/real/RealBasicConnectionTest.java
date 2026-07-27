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

	/**
	 * Budget for {@link Connection#isValid(int)}, which runs a real {@code SELECT
	 * 1} against BigQuery.
	 *
	 * <p>
	 * Deliberately generous: these tests run concurrently with the rest of the real
	 * suite, and returning {@code false} once the budget expires is correct per the
	 * JDBC contract. A tight value turns ordinary CI load into a failure — a
	 * 5-second budget did exactly that, timing out at 5.025s. What these tests mean
	 * to assert is that the connection is usable, not that BigQuery answers within
	 * any particular time.
	 */
	private static final int VALIDATION_TIMEOUT_SECONDS = 30;

	@Test
	void testConnectionIsValid() throws SQLException {
		assertTrue(connection.isValid(VALIDATION_TIMEOUT_SECONDS));
		assertFalse(connection.isClosed());

		// No timeout at all: validity must not depend on the budget
		assertTrue(connection.isValid(0));
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
		// The emulator version also covered SERIALIZABLE; folded in here (#118).
		assertThrows(SQLFeatureNotSupportedException.class,
				() -> connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED));
		assertThrows(SQLFeatureNotSupportedException.class,
				() -> connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE));
		assertThrows(SQLFeatureNotSupportedException.class,
				() -> connection.setTransactionIsolation(Connection.TRANSACTION_READ_UNCOMMITTED));
	}

	@Test
	void testSetTransactionIsolationAcceptsSupportedLevels() throws SQLException {
		// BigQuery gives snapshot isolation, reported as REPEATABLE_READ since JDBC
		// has no snapshot constant. NONE is accepted and recorded but changes nothing.
		connection.setTransactionIsolation(Connection.TRANSACTION_NONE);
		assertEquals(Connection.TRANSACTION_NONE, connection.getTransactionIsolation());

		connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
		assertEquals(Connection.TRANSACTION_REPEATABLE_READ, connection.getTransactionIsolation());
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

		assertTrue(conn1.isValid(VALIDATION_TIMEOUT_SECONDS));
		assertTrue(conn2.isValid(VALIDATION_TIMEOUT_SECONDS));

		conn1.close();

		assertTrue(conn1.isClosed());
		assertFalse(conn2.isClosed());
		assertTrue(conn2.isValid(VALIDATION_TIMEOUT_SECONDS));

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

		assertTrue(connection.isValid(VALIDATION_TIMEOUT_SECONDS));
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

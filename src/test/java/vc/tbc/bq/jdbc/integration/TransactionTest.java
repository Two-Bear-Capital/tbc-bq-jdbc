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
import vc.tbc.bq.jdbc.BQConnection;

import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for transaction control without pre-configured sessions.
 *
 * <p>
 * {@code setAutoCommit(false)} starts a BigQuery session on demand, so generic
 * JDBC tooling does not need to know about {@code enableSessions=true}.
 *
 * <p>
 * <b>NOTE:</b> The BigQuery emulator has limited transaction support. Tests
 * that need real transactional semantics tolerate emulator failures while
 * asserting the driver-side contract (no
 * {@link SQLFeatureNotSupportedException}, correct auto-commit state).
 *
 * @since 1.0.94
 */
class TransactionTest extends AbstractBigQueryIntegrationTest {

	private static final Logger logger = LoggerFactory.getLogger(TransactionTest.class);

	@Test
	void testSetAutoCommitFalseStartsSessionOnDemand() throws SQLException {
		// Given: A connection opened WITHOUT enableSessions=true
		assertTrue(connection.getAutoCommit(), "Auto-commit should default to true");
		assertFalse(((BQConnection) connection).getSessionManager().hasSession(), "No session should exist yet");

		// When: Disabling auto-commit
		connection.setAutoCommit(false);

		// Then: A session was started for the connection
		assertFalse(connection.getAutoCommit(), "Auto-commit should be disabled");
		assertTrue(((BQConnection) connection).getSessionManager().hasSession(),
				"Session should have been created on demand");
	}

	@Test
	void testSetAutoCommitFalseDoesNotThrowFeatureNotSupported() {
		// Then: The JDBC-standard call must not be rejected as unsupported
		assertDoesNotThrow(() -> connection.setAutoCommit(false));
	}

	@Test
	void testCommitWithoutStatementsIsNoOp() throws SQLException {
		// Given: A connection with auto-commit disabled and nothing executed
		connection.setAutoCommit(false);

		// Then: Commits are accepted and cost no query jobs (BEGIN is deferred)
		assertDoesNotThrow(() -> connection.commit());
		assertDoesNotThrow(() -> connection.commit());
		assertDoesNotThrow(() -> connection.rollback());
	}

	@Test
	void testRepeatedCommitsWorkWithoutEnableSessions() throws SQLException {
		// Given: A connection with auto-commit disabled
		connection.setAutoCommit(false);

		// Then: Each statement/commit pair is its own transaction
		for (int i = 0; i < 2; i++) {
			try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery("SELECT 1 AS value")) {
				assertTrue(rs.next());
			}
			assertDoesNotThrow(() -> connection.commit());
		}
	}

	@Test
	void testRollbackWorksWithoutEnableSessions() throws SQLException {
		// Given: A connection with auto-commit disabled and an open transaction
		connection.setAutoCommit(false);
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery("SELECT 1 AS value")) {
			assertTrue(rs.next());
		}

		try {
			connection.rollback();
			logger.info("✓ Rollback supported");
		} catch (SQLException e) {
			// The emulator rejects ROLLBACK TRANSACTION ("Statement not supported:
			// RollbackStatement"); real BigQuery supports it
			logger.warn("Rollback test skipped (emulator limitation): {}", e.getMessage());
		}
	}

	@Test
	void testReenablingAutoCommitCommitsAndKeepsConnectionUsable() throws SQLException {
		// Given: A connection in manual-commit mode
		connection.setAutoCommit(false);

		// When: Switching back to auto-commit
		connection.setAutoCommit(true);

		// Then: State is updated and the connection still works
		assertTrue(connection.getAutoCommit());
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery("SELECT 1 AS value")) {
			assertTrue(rs.next());
			assertEquals(1, rs.getInt("value"));
		}
	}

	@Test
	void testCommitInAutoCommitModeThrowsSQLException() {
		// Then: Per the JDBC spec this is an error, but not "feature not supported"
		SQLException ex = assertThrows(SQLException.class, () -> connection.commit());
		assertFalse(ex instanceof SQLFeatureNotSupportedException,
				"commit() in auto-commit mode should be an invalid-state error, not unsupported");
		assertEquals("25000", ex.getSQLState());
	}

	@Test
	void testRollbackInAutoCommitModeThrowsSQLException() {
		// Then: Per the JDBC spec this is an error, but not "feature not supported"
		SQLException ex = assertThrows(SQLException.class, () -> connection.rollback());
		assertFalse(ex instanceof SQLFeatureNotSupportedException,
				"rollback() in auto-commit mode should be an invalid-state error, not unsupported");
		assertEquals("25000", ex.getSQLState());
	}

	@Test
	void testSetAutoCommitFalseIsIdempotent() throws SQLException {
		connection.setAutoCommit(false);
		assertDoesNotThrow(() -> connection.setAutoCommit(false));
		assertFalse(connection.getAutoCommit());
	}

	@Test
	void testTransactionalInsertsAreVisibleAfterCommit() throws SQLException {
		String table = TEST_DATASET + ".tx_commit_test";
		executeIgnoreErrors("DROP TABLE IF EXISTS " + table);

		try (Statement ddl = connection.createStatement()) {
			ddl.execute("CREATE TABLE " + table + " (id INT64)");
		}

		try {
			connection.setAutoCommit(false);

			try (Statement stmt = connection.createStatement()) {
				stmt.executeUpdate("INSERT INTO " + table + " (id) VALUES (1)");
				stmt.executeUpdate("INSERT INTO " + table + " (id) VALUES (2)");
			}

			connection.commit();
			connection.setAutoCommit(true);

			try (Statement stmt = connection.createStatement();
					ResultSet rs = stmt.executeQuery("SELECT COUNT(*) AS row_count FROM " + table)) {
				assertTrue(rs.next());
				assertEquals(2, rs.getInt("row_count"), "Both committed rows should be visible");
			}

			logger.info("✓ Transactional inserts committed");
		} catch (SQLException e) {
			// The emulator does not implement multi-statement transactions
			logger.warn("Transactional insert test skipped (emulator limitation): {}", e.getMessage());
		} finally {
			executeIgnoreErrors("DROP TABLE IF EXISTS " + table);
		}
	}

	/**
	 * Creates a connection with sessions enabled up front.
	 *
	 * @return a session-enabled connection to the emulator
	 * @throws SQLException
	 *             if the connection cannot be created
	 */
	private Connection createSessionConnection() throws SQLException {
		String host = bigqueryEmulator.getHost();
		int port = bigqueryEmulator.getMappedPort(9050);
		String url = String.format(
				"jdbc:bigquery://%s:%d;ProjectId=%s;DefaultDataset=%s;EnableSessions=true;UseDestinationTables=true",
				host, port, TEST_PROJECT_ID, TEST_DATASET);
		return DriverManager.getConnection(url);
	}

	@Test
	void testEnableSessionsConnectionStillSupportsTransactions() throws SQLException {
		// Given: A connection opened with enableSessions=true
		try (Connection sessionConn = createSessionConnection()) {
			assertTrue(((BQConnection) sessionConn).getSessionManager().hasSession(),
					"Session should be created eagerly");

			// When/Then: Transaction control behaves the same as the lazy path
			sessionConn.setAutoCommit(false);
			assertFalse(sessionConn.getAutoCommit());
			assertDoesNotThrow(() -> sessionConn.commit());
			sessionConn.setAutoCommit(true);
			assertTrue(sessionConn.getAutoCommit());
		}
	}
}

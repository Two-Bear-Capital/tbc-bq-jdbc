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
import vc.tbc.bq.jdbc.BQConnection;

import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real BigQuery integration tests for transaction control.
 *
 * <p>
 * These tests exercise the session/transaction paths that the emulator cannot
 * validate: BigQuery-assigned session IDs, {@code BEGIN}/{@code COMMIT}/
 * {@code ROLLBACK TRANSACTION} across multiple query jobs, and on-demand
 * session creation from {@code setAutoCommit(false)}.
 *
 * @since 1.0.94
 */
class RealTransactionTest extends AbstractRealBigQueryIntegrationTest {

	@Test
	void testSetAutoCommitFalseStartsSessionOnDemand() throws SQLException {
		// Given: A connection opened WITHOUT enableSessions=true
		BQConnection bqConnection = connection.unwrap(BQConnection.class);
		assertFalse(bqConnection.getSessionManager().hasSession(), "No session should exist yet");

		// When: Disabling auto-commit
		connection.setAutoCommit(false);

		// Then: BigQuery assigned a session to this connection
		assertFalse(connection.getAutoCommit());
		assertTrue(bqConnection.getSessionManager().hasSession(), "Session should have been created on demand");
		assertNotNull(bqConnection.getSessionManager().getSessionId(), "BigQuery should return a session ID");
	}

	@Test
	void testCommittedRowsAreVisibleAndRolledBackRowsAreNot() throws SQLException {
		String table = TEST_PROJECT_ID + "." + TEST_DATASET + "." + tableName("tx_rows");

		try (Statement ddl = connection.createStatement()) {
			ddl.execute("CREATE OR REPLACE TABLE " + table + " (id INT64)");
		}

		try {
			connection.setAutoCommit(false);

			// Committed rows
			try (Statement stmt = connection.createStatement()) {
				stmt.executeUpdate("INSERT INTO " + table + " (id) VALUES (1)");
				stmt.executeUpdate("INSERT INTO " + table + " (id) VALUES (2)");
			}
			connection.commit();

			// Rolled-back rows (commit() started the next transaction automatically)
			try (Statement stmt = connection.createStatement()) {
				stmt.executeUpdate("INSERT INTO " + table + " (id) VALUES (3)");
			}
			connection.rollback();

			connection.setAutoCommit(true);

			try (Statement stmt = connection.createStatement();
					ResultSet rs = stmt.executeQuery("SELECT COUNT(*) AS row_count FROM " + table)) {
				assertTrue(rs.next());
				assertEquals(2, rs.getInt("row_count"), "Only the committed rows should be visible");
			}
		} finally {
			executeIgnoreErrors("DROP TABLE IF EXISTS " + table);
		}
	}

	@Test
	void testUncommittedWorkIsDiscardedWhenConnectionCloses() throws SQLException {
		String table = TEST_PROJECT_ID + "." + TEST_DATASET + "." + tableName("tx_close");

		try (Statement ddl = connection.createStatement()) {
			ddl.execute("CREATE OR REPLACE TABLE " + table + " (id INT64)");
		}

		try {
			try (Connection txConnection = createTestConnection()) {
				txConnection.setAutoCommit(false);
				try (Statement stmt = txConnection.createStatement()) {
					stmt.executeUpdate("INSERT INTO " + table + " (id) VALUES (1)");
				}
				// Closed without commit
			}

			try (Statement stmt = connection.createStatement();
					ResultSet rs = stmt.executeQuery("SELECT COUNT(*) AS row_count FROM " + table)) {
				assertTrue(rs.next());
				assertEquals(0, rs.getInt("row_count"), "Uncommitted rows should be rolled back on close");
			}
		} finally {
			executeIgnoreErrors("DROP TABLE IF EXISTS " + table);
		}
	}

	@Test
	void testEnableSessionsConnectionCreatesSessionEagerly() throws SQLException {
		String url = String.format("jdbc:bigquery:%s/%s?authType=ADC&enableSessions=true%s", TEST_PROJECT_ID,
				TEST_DATASET, TEST_CONNECTION_DEFAULTS);

		try (Connection sessionConn = DriverManager.getConnection(url)) {
			BQConnection bqConnection = sessionConn.unwrap(BQConnection.class);
			assertTrue(bqConnection.getSessionManager().hasSession(), "Session should be created at connection open");
			assertNotNull(bqConnection.getSessionManager().getSessionId());

			sessionConn.setAutoCommit(false);
			sessionConn.commit();
			sessionConn.setAutoCommit(true);
			assertTrue(sessionConn.getAutoCommit());
		}
	}

	@Test
	void testTemporaryTableWorksOverOnDemandSession() throws SQLException {
		// Given: A session started implicitly by disabling auto-commit
		connection.setAutoCommit(false);
		connection.setAutoCommit(true);

		// Then: The session outlives the transaction and supports temp tables
		String tempTable = "tx_temp_" + RUN_ID;
		try (Statement stmt = connection.createStatement()) {
			stmt.execute("CREATE TEMP TABLE " + tempTable + " (id INT64)");
			stmt.executeUpdate("INSERT INTO " + tempTable + " VALUES (7)");

			try (ResultSet rs = stmt.executeQuery("SELECT id FROM " + tempTable)) {
				assertTrue(rs.next());
				assertEquals(7, rs.getInt("id"));
			}
		}
	}

	@Test
	void testCommitInAutoCommitModeThrowsInvalidState() {
		SQLException ex = assertThrows(SQLException.class, () -> connection.commit());
		assertFalse(ex instanceof SQLFeatureNotSupportedException);
		assertEquals("25000", ex.getSQLState());
	}

	// ── Ported from the emulator TransactionTest (#118) ───────────────────────

	@Test
	void testSetAutoCommitFalseDoesNotThrowFeatureNotSupported() {
		// A JDBC-standard call must not be rejected as unsupported. The driver used to
		// throw SQLFeatureNotSupportedException here, which made connection pools and
		// ORMs treat the driver as transaction-less.
		assertDoesNotThrow(() -> connection.setAutoCommit(false));
	}

	@Test
	void testSetAutoCommitFalseIsIdempotent() throws SQLException {
		connection.setAutoCommit(false);
		assertDoesNotThrow(() -> connection.setAutoCommit(false));
		assertFalse(connection.getAutoCommit());
	}

	@Test
	void testCommitWithoutStatementsIsNoOp() throws SQLException {
		// BEGIN is deferred to the first statement, so commits with nothing in flight
		// cost no query jobs. This is what makes pools that toggle auto-commit cheap.
		connection.setAutoCommit(false);

		assertDoesNotThrow(() -> connection.commit());
		assertDoesNotThrow(() -> connection.commit());
		assertDoesNotThrow(() -> connection.rollback());
		assertFalse(connection.getAutoCommit(), "The connection stays in manual-commit mode");
	}

	@Test
	void testRepeatedCommitsWorkWithoutEnableSessions() throws SQLException {
		connection.setAutoCommit(false);

		for (int i = 1; i <= 2; i++) {
			try (Statement stmt = connection.createStatement();
					ResultSet rs = stmt.executeQuery("SELECT " + i + " AS value")) {
				assertTrue(rs.next());
				assertEquals(i, rs.getInt("value"));
			}
			connection.commit();
		}

		// Each statement/commit pair is its own transaction over one session.
		assertTrue(connection.unwrap(BQConnection.class).getSessionManager().hasSession(),
				"The session created on demand is reused across transactions");
	}

	@Test
	void testRollbackWorksWithoutEnableSessions() throws SQLException {
		// The emulator rejects ROLLBACK TRANSACTION ("Statement not supported:
		// RollbackStatement"), so its version caught SQLException and logged a skip —
		// it could not tell "rollback works" from "rollback is unimplemented".
		connection.setAutoCommit(false);
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery("SELECT 1 AS value")) {
			assertTrue(rs.next());
		}

		connection.rollback();

		// And the connection is still usable afterwards, on the same session.
		assertFalse(connection.getAutoCommit());
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery("SELECT 2 AS value")) {
			assertTrue(rs.next());
			assertEquals(2, rs.getInt("value"));
		}
	}

	@Test
	void testRollbackInAutoCommitModeThrowsInvalidState() {
		// The commit() half of this contract was already covered; the rollback half
		// was not.
		SQLException ex = assertThrows(SQLException.class, () -> connection.rollback());
		assertFalse(ex instanceof SQLFeatureNotSupportedException,
				"rollback() in auto-commit mode is an invalid-state error, not unsupported");
		assertEquals("25000", ex.getSQLState());
	}

	@Test
	void testReenablingAutoCommitCommitsAndKeepsConnectionUsable() throws SQLException {
		connection.setAutoCommit(false);
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery("SELECT 1 AS value")) {
			assertTrue(rs.next());
		}

		// Switching back commits whatever is in flight rather than stranding it.
		connection.setAutoCommit(true);

		assertTrue(connection.getAutoCommit());
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery("SELECT 1 AS value")) {
			assertTrue(rs.next());
			assertEquals(1, rs.getInt("value"));
		}
		// commit() is now an error again, which proves the mode really changed.
		assertThrows(SQLException.class, () -> connection.commit());
	}
}

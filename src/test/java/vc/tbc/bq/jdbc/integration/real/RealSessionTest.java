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
import vc.tbc.bq.jdbc.BQConnection;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BigQuery session behaviour against real BigQuery.
 *
 * <p>
 * Port of {@code SessionTest} (issue #118). Three of that class's tests — the
 * only three that exercised session <em>semantics</em> rather than plumbing —
 * caught {@code SQLException} and logged "(emulator limitation)", so temp table
 * creation, persistence across statements, and isolation between sessions all
 * passed without any of them working. Its class javadoc said as much: "Many
 * tests will gracefully handle emulator limitations."
 *
 * <p>
 * The emulator also returns no {@code sessionInfo}, so
 * {@code SessionManager.getSessionId()} is always null there and the
 * {@code session_id} property is never attached to a job. Every assertion here
 * about a BigQuery-assigned session id is therefore one the emulator tier could
 * not have made at all.
 *
 * <p>
 * Session lifecycle via {@code setAutoCommit(false)} is covered by
 * {@link RealTransactionTest}; this class covers connections opened with
 * {@code enableSessions=true}.
 *
 * <p>
 * Read-only tests share one session connection, because creating a session
 * costs a BigQuery job. Tests that create temp tables or close the connection
 * take their own, since session state and lifetime are what they are asserting.
 *
 * @since 1.0.114
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RealSessionTest extends AbstractRealBigQueryIntegrationTest {

	private Connection sharedSession;

	@BeforeAll
	void openSharedSession() throws SQLException {
		sharedSession = createSessionConnection();
	}

	@AfterAll
	void closeSharedSession() throws SQLException {
		if (sharedSession != null && !sharedSession.isClosed()) {
			sharedSession.close();
		}
	}

	/** A connection with {@code enableSessions=true}, i.e. an eager session. */
	private Connection createSessionConnection() throws SQLException {
		String url = String.format("jdbc:bigquery:%s/%s?authType=ADC&enableSessions=true%s", TEST_PROJECT_ID,
				TEST_DATASET, TEST_CONNECTION_DEFAULTS);
		return DriverManager.getConnection(url);
	}

	private static String sessionIdOf(Connection conn) throws SQLException {
		return conn.unwrap(BQConnection.class).getSessionManager().getSessionId();
	}

	// ── Session establishment ─────────────────────────────────────────────────

	@Test
	void testEnableSessionsCreatesASessionWithAnAssignedId() throws SQLException {
		assertTrue(sharedSession.isValid(30), "Session connection should be valid");
		assertFalse(sharedSession.isClosed(), "Session connection should not be closed");

		BQConnection bq = sharedSession.unwrap(BQConnection.class);
		assertTrue(bq.getSessionManager().hasSession(), "enableSessions=true creates the session at connection open");
		// The emulator returns no sessionInfo, so this assertion was impossible there.
		assertNotNull(bq.getSessionManager().getSessionId(), "BigQuery should assign and report a session id");
	}

	@Test
	void testEachConnectionGetsADistinctSession() throws SQLException {
		try (Connection other = createSessionConnection()) {
			String a = sessionIdOf(sharedSession);
			String b = sessionIdOf(other);

			assertNotNull(a);
			assertNotNull(b);
			assertNotEquals(a, b, "Two connections must not share a BigQuery session");
		}
	}

	@Test
	void testSessionConnectionCanExecuteQueries() throws SQLException {
		try (Statement stmt = sharedSession.createStatement(); ResultSet rs = stmt.executeQuery("SELECT 1 AS value")) {
			assertTrue(rs.next(), "Should have a result");
			assertEquals(1, rs.getInt("value"));
			assertFalse(rs.next(), "Should have only one row");
		}
	}

	@Test
	void testMultipleQueriesInSession() throws SQLException {
		// BigQuery forbids concurrent queries inside a session, so these must be
		// sequential — which is exactly what this asserts still works.
		try (Statement stmt = sharedSession.createStatement()) {
			for (int i = 1; i <= 3; i++) {
				try (ResultSet rs = stmt.executeQuery("SELECT " + i + " AS iteration")) {
					assertTrue(rs.next(), "Should have a result for iteration " + i);
					assertEquals(i, rs.getInt("iteration"));
				}
			}
		}
	}

	@Test
	void testPreparedStatementInSession() throws SQLException {
		try (PreparedStatement pstmt = sharedSession.prepareStatement("SELECT ? AS value")) {
			pstmt.setInt(1, 100);
			try (ResultSet rs = pstmt.executeQuery()) {
				assertTrue(rs.next(), "Should have a result");
				assertEquals(100, rs.getInt("value"));
				assertFalse(rs.next(), "Should have only one row");
			}
		}
	}

	// ── Temp tables: the three the emulator tier could not verify ─────────────

	@Test
	void testCreateTemporaryTableInSession() throws SQLException {
		// Emulator tier: whole body wrapped in try/catch, "Temp tables not supported
		// (emulator limitation)", test still passes.
		try (Connection session = createSessionConnection(); Statement stmt = session.createStatement()) {
			stmt.execute("CREATE TEMP TABLE temp_users (id INT64, name STRING)");
			assertEquals(2, stmt.executeUpdate("INSERT INTO temp_users (id, name) VALUES (1, 'Alice'), (2, 'Bob')"),
					"INSERT into a temp table should report both rows");

			try (ResultSet rs = stmt.executeQuery("SELECT id, name FROM temp_users ORDER BY id")) {
				assertTrue(rs.next(), "Should have the first row");
				assertEquals(1, rs.getInt("id"));
				assertEquals("Alice", rs.getString("name"));

				assertTrue(rs.next(), "Should have the second row");
				assertEquals(2, rs.getInt("id"));
				assertEquals("Bob", rs.getString("name"));

				assertFalse(rs.next(), "Should have no more rows");
			}
		}
	}

	@Test
	void testTemporaryTablePersistsAcrossStatements() throws SQLException {
		// This is the point of a session: state outlives the Statement that made it.
		try (Connection session = createSessionConnection()) {
			try (Statement create = session.createStatement()) {
				create.execute("CREATE TEMP TABLE temp_data (value INT64)");
				assertEquals(1, create.executeUpdate("INSERT INTO temp_data VALUES (42)"));
			}

			// A different Statement, therefore a different query job, same session.
			try (Statement read = session.createStatement();
					ResultSet rs = read.executeQuery("SELECT value FROM temp_data")) {
				assertTrue(rs.next(), "Temp table should still be visible to a later statement");
				assertEquals(42, rs.getInt("value"));
				assertFalse(rs.next(), "Should have only one row");
			}
		}
	}

	@Test
	void testTemporaryTablesAreIsolatedBetweenSessions() throws SQLException {
		try (Connection first = createSessionConnection(); Connection second = createSessionConnection()) {
			assertNotEquals(sessionIdOf(first), sessionIdOf(second), "Precondition: two distinct sessions");

			try (Statement stmt = first.createStatement()) {
				stmt.execute("CREATE TEMP TABLE temp_isolated (id INT64)");
				assertEquals(1, stmt.executeUpdate("INSERT INTO temp_isolated VALUES (1)"));

				try (ResultSet rs = stmt.executeQuery("SELECT id FROM temp_isolated")) {
					assertTrue(rs.next(), "Temp table should exist in its own session");
					assertEquals(1, rs.getInt("id"));
				}
			}

			// Emulator tier swallowed "Table not found" here and logged a skip, so it
			// never distinguished "isolated correctly" from "temp tables do not work".
			try (Statement stmt = second.createStatement()) {
				SQLException thrown = assertThrows(SQLException.class,
						() -> stmt.executeQuery("SELECT id FROM temp_isolated"),
						"A temp table must not be visible from a different session");
				assertTrue(thrown.getMessage().toLowerCase().contains("not found"),
						"Expected a not-found error, was: " + thrown.getMessage());
			}
		}
	}

	// ── Lifecycle ─────────────────────────────────────────────────────────────

	@Test
	void testSessionClosedOnConnectionClose() throws SQLException {
		Connection session = createSessionConnection();
		String sessionId = sessionIdOf(session);
		assertNotNull(sessionId, "Precondition: a session was created");

		try (Statement stmt = session.createStatement(); ResultSet rs = stmt.executeQuery("SELECT 1")) {
			assertTrue(rs.next(), "Connection should be usable");
		}

		session.close();

		assertTrue(session.isClosed(), "Connection should be closed");
		assertThrows(SQLException.class, session::createStatement,
				"Should not create a statement on a closed connection");
	}

	@Test
	void testQueriesWithoutSessionsStillWork() throws SQLException {
		// The inherited connection has no enableSessions, so no session exists.
		assertFalse(connection.unwrap(BQConnection.class).getSessionManager().hasSession(),
				"A plain connection should not open a session");

		try (Statement stmt = connection.createStatement();
				ResultSet rs = stmt.executeQuery("SELECT 1 AS value, 'test' AS name")) {
			assertTrue(rs.next(), "Should have a result");
			assertEquals(1, rs.getInt("value"));
			assertEquals("test", rs.getString("name"));
			assertFalse(rs.next(), "Should have only one row");
		}
	}

	// ── Connection surface inside a session ───────────────────────────────────

	@Test
	void testSessionConnectionMetadata() throws SQLException {
		DatabaseMetaData metadata = sharedSession.getMetaData();

		assertNotNull(metadata, "Metadata should be available");
		assertTrue(metadata.getURL().contains(TEST_PROJECT_ID), "URL should contain the project id");
		assertNotNull(metadata.getDatabaseProductName(), "Should have a product name");
		assertNotNull(metadata.getDriverName(), "Should have a driver name");
	}

	@Test
	void testSessionConnectionProperties() throws SQLException {
		assertFalse(sharedSession.isReadOnly(), "Connection should not be read-only by default");
		assertTrue(sharedSession.getAutoCommit(), "Auto-commit should be true");
		assertEquals(Connection.TRANSACTION_REPEATABLE_READ, sharedSession.getTransactionIsolation(),
				"Snapshot isolation is reported as REPEATABLE_READ");
	}

	@Test
	void testCreateStatementVariantsInSession() throws SQLException {
		try (Statement stmt = sharedSession.createStatement()) {
			assertNotNull(stmt, "createStatement() should work");
		}

		try (Statement stmt = sharedSession.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
			assertNotNull(stmt, "createStatement(type, concurrency) should work");
		}

		try (Statement stmt = sharedSession.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY,
				ResultSet.CLOSE_CURSORS_AT_COMMIT)) {
			assertNotNull(stmt, "createStatement(type, concurrency, holdability) should work");
		}
	}
}

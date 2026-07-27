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
import vc.tbc.bq.jdbc.config.ConnectionProperties;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Statement configuration against real BigQuery.
 *
 * <p>
 * Port of {@code StatementConfigurationTest} (issue #118). Most of that class
 * was already strict; two shapes were not.
 *
 * <p>
 * {@code testGetFetchSizeDefaultsToZero} asserted {@code fetchSize >= 0}, true
 * of any non-negative int, and its name was wrong besides —
 * {@code getFetchSize()} returns
 * {@code fetchSize > 0 ? fetchSize : properties.pageSize()}, so the default is
 * the page size, not zero. Both are corrected here.
 *
 * <p>
 * Five tests asserted only {@code assertDoesNotThrow} on setters that are
 * deliberate no-ops. Not throwing is the smaller half of that contract; the
 * larger half is that the value is ignored, and that the call still rejects a
 * closed statement. Both are asserted now.
 *
 * @since 1.0.120
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RealStatementConfigurationTest extends AbstractRealBigQueryIntegrationTest {

	private static final String TEST_TABLE = tableName("stmt_config");

	@BeforeAll
	void createFixture() throws SQLException {
		createSharedTestTable(TEST_TABLE);
	}

	@AfterAll
	void dropFixture() {
		dropSharedTestTable(TEST_TABLE);
	}

	// ── Query timeout ─────────────────────────────────────────────────────────

	@Test
	void testGetQueryTimeoutDefaultsToZero() throws SQLException {
		try (Statement stmt = connection.createStatement()) {
			assertEquals(0, stmt.getQueryTimeout(), "Default query timeout should be 0 (no timeout)");
		}
	}

	@Test
	void testSetAndGetQueryTimeout() throws SQLException {
		try (Statement stmt = connection.createStatement()) {
			stmt.setQueryTimeout(30);
			assertEquals(30, stmt.getQueryTimeout());

			stmt.setQueryTimeout(60);
			assertEquals(60, stmt.getQueryTimeout());

			stmt.setQueryTimeout(0);
			assertEquals(0, stmt.getQueryTimeout(), "Zero restores 'no timeout'");
		}
	}

	@Test
	void testQueryExecutesWithinTimeout() throws SQLException {
		try (Statement stmt = connection.createStatement()) {
			stmt.setQueryTimeout(60);
			try (ResultSet rs = stmt.executeQuery("SELECT * FROM " + TEST_TABLE)) {
				assertTrue(rs.next(), "A query well inside the timeout should complete");
			}
		}
	}

	// ── Max rows ──────────────────────────────────────────────────────────────

	@Test
	void testGetMaxRowsDefaultsToZero() throws SQLException {
		try (Statement stmt = connection.createStatement()) {
			assertEquals(0, stmt.getMaxRows(), "Default max rows should be 0 (unlimited)");
		}
	}

	@Test
	void testSetAndGetMaxRows() throws SQLException {
		try (Statement stmt = connection.createStatement()) {
			stmt.setMaxRows(10);
			assertEquals(10, stmt.getMaxRows());

			stmt.setMaxRows(100);
			assertEquals(100, stmt.getMaxRows());

			stmt.setMaxRows(0);
			assertEquals(0, stmt.getMaxRows(), "Zero restores 'unlimited'");
		}
	}

	@Test
	void testMaxRowsLimitsResultSet() throws SQLException {
		try (Statement stmt = connection.createStatement()) {
			stmt.setMaxRows(2);
			try (ResultSet rs = stmt.executeQuery("SELECT * FROM " + TEST_TABLE + " ORDER BY id")) {
				assertTrue(rs.next(), "Should have the first row");
				assertEquals(1, rs.getInt("id"));
				assertTrue(rs.next(), "Should have the second row");
				assertEquals(2, rs.getInt("id"));
				assertFalse(rs.next(), "The third row is cut off by maxRows");
			}
		}
	}

	@Test
	void testMaxRowsZeroReturnsAllRows() throws SQLException {
		try (Statement stmt = connection.createStatement()) {
			stmt.setMaxRows(0);
			try (ResultSet rs = stmt.executeQuery("SELECT * FROM " + TEST_TABLE)) {
				int count = 0;
				while (rs.next()) {
					count++;
				}
				assertEquals(3, count, "All three fixture rows");
			}
		}
	}

	// ── Fetch size ────────────────────────────────────────────────────────────

	@Test
	void testGetFetchSizeDefaultsToThePageSize() throws SQLException {
		// The emulator version was named ...DefaultsToZero and asserted
		// `fetchSize >= 0`. Neither matched the driver: with nothing set,
		// getFetchSize() reports the connection's page size.
		try (Statement stmt = connection.createStatement()) {
			assertEquals(ConnectionProperties.DEFAULT_PAGE_SIZE, stmt.getFetchSize(),
					"An unset fetch size reports the connection page size, not zero");
		}
	}

	@Test
	void testSetAndGetFetchSize() throws SQLException {
		try (Statement stmt = connection.createStatement()) {
			stmt.setFetchSize(100);
			assertEquals(100, stmt.getFetchSize());

			stmt.setFetchSize(1000);
			assertEquals(1000, stmt.getFetchSize());
		}
	}

	@Test
	void testFetchSizeZeroFallsBackToThePageSize() throws SQLException {
		// The other half of the same contract: zero means "unset", not "no rows".
		try (Statement stmt = connection.createStatement()) {
			stmt.setFetchSize(100);
			stmt.setFetchSize(0);
			assertEquals(ConnectionProperties.DEFAULT_PAGE_SIZE, stmt.getFetchSize(),
					"Zero returns to the page-size default");
		}
	}

	@Test
	void testFetchSizeDoesNotAffectResults() throws SQLException {
		try (Statement small = connection.createStatement(); Statement large = connection.createStatement()) {
			small.setFetchSize(1);
			large.setFetchSize(1000);

			try (ResultSet a = small.executeQuery("SELECT * FROM " + TEST_TABLE + " ORDER BY id");
					ResultSet b = large.executeQuery("SELECT * FROM " + TEST_TABLE + " ORDER BY id")) {
				int rows = 0;
				while (a.next()) {
					assertTrue(b.next(), "Both result sets should have the same length");
					assertEquals(a.getInt("id"), b.getInt("id"), "Row " + rows + " should match");
					rows++;
				}
				assertFalse(b.next(), "Both result sets should be exhausted together");
				assertEquals(3, rows, "Paging must not change how many rows come back");
			}
		}
	}

	// ── Deliberate no-ops ─────────────────────────────────────────────────────
	// The emulator tier asserted only assertDoesNotThrow on these. Not throwing is
	// the smaller half; that the value is ignored is the contract.

	@Test
	void testMaxFieldSizeIsReportedAsUnlimitedAndIgnored() throws SQLException {
		try (Statement stmt = connection.createStatement()) {
			assertEquals(0, stmt.getMaxFieldSize(), "0 means unlimited");
			stmt.setMaxFieldSize(1000);
			assertEquals(0, stmt.getMaxFieldSize(), "BigQuery does not truncate fields; the setting is ignored");
		}
	}

	@Test
	void testSetEscapeProcessingIsAccepted() throws SQLException {
		try (Statement stmt = connection.createStatement()) {
			stmt.setEscapeProcessing(true);
			stmt.setEscapeProcessing(false);
			// Still usable afterwards — the call is a no-op, not a state change.
			try (ResultSet rs = stmt.executeQuery("SELECT 1 AS n")) {
				assertTrue(rs.next());
				assertEquals(1, rs.getInt("n"));
			}
		}
	}

	@Test
	void testPoolableIsAlwaysFalse() throws SQLException {
		try (Statement stmt = connection.createStatement()) {
			assertFalse(stmt.isPoolable(), "Statements are not poolable");
			stmt.setPoolable(true);
			assertFalse(stmt.isPoolable(), "setPoolable is ignored");
		}
	}

	@Test
	void testCloseOnCompletionIsAlwaysFalse() throws SQLException {
		try (Statement stmt = connection.createStatement()) {
			assertFalse(stmt.isCloseOnCompletion());
			stmt.closeOnCompletion();
			assertFalse(stmt.isCloseOnCompletion(), "closeOnCompletion is ignored");
		}
	}

	@Test
	void testNoOpSettersStillRejectAClosedStatement() throws SQLException {
		// Each of them calls checkClosed() first, which is the part that actually has
		// to work — a no-op that silently accepts a closed statement would be wrong.
		Statement stmt = connection.createStatement();
		stmt.close();

		assertThrows(SQLException.class, () -> stmt.setMaxFieldSize(1000));
		assertThrows(SQLException.class, () -> stmt.setEscapeProcessing(true));
		assertThrows(SQLException.class, () -> stmt.setPoolable(true));
		assertThrows(SQLException.class, stmt::closeOnCompletion);
		assertThrows(SQLException.class, stmt::isPoolable);
		assertThrows(SQLException.class, stmt::isCloseOnCompletion);
	}

	// ── ResultSet characteristics ─────────────────────────────────────────────

	@Test
	void testGetResultSetType() throws SQLException {
		try (Statement stmt = connection.createStatement()) {
			assertEquals(ResultSet.TYPE_FORWARD_ONLY, stmt.getResultSetType());
		}
	}

	@Test
	void testGetResultSetConcurrency() throws SQLException {
		try (Statement stmt = connection.createStatement()) {
			assertEquals(ResultSet.CONCUR_READ_ONLY, stmt.getResultSetConcurrency());
		}
	}

	@Test
	void testGetResultSetHoldability() throws SQLException {
		try (Statement stmt = connection.createStatement()) {
			assertEquals(ResultSet.CLOSE_CURSORS_AT_COMMIT, stmt.getResultSetHoldability());
		}
	}

	// ── Result bookkeeping ────────────────────────────────────────────────────

	@Test
	void testGetUpdateCountReturnsMinusOneAfterSelect() throws SQLException {
		try (Statement stmt = connection.createStatement()) {
			stmt.executeQuery("SELECT * FROM " + TEST_TABLE).close();
			assertEquals(-1, stmt.getUpdateCount(), "-1 when the result is a ResultSet");
		}
	}

	@Test
	void testGetMoreResultsReturnsFalse() throws SQLException {
		try (Statement stmt = connection.createStatement()) {
			stmt.executeQuery("SELECT * FROM " + TEST_TABLE).close();
			assertFalse(stmt.getMoreResults(), "BigQuery returns a single result set");
		}
	}

	@Test
	void testGetConnection() throws SQLException {
		try (Statement stmt = connection.createStatement()) {
			Connection conn = stmt.getConnection();
			assertSame(connection, conn, "Should return the connection that created it");
		}
	}

	// ── Warnings ──────────────────────────────────────────────────────────────

	@Test
	void testGetWarningsReturnsNull() throws SQLException {
		try (Statement stmt = connection.createStatement()) {
			assertNull(stmt.getWarnings(), "No warnings before anything runs");
			stmt.executeQuery("SELECT 1 AS n").close();
			assertNull(stmt.getWarnings(), "and none from an ordinary query without cost estimation");
		}
	}

	@Test
	void testClearWarningsLeavesTheStatementUsable() throws SQLException {
		try (Statement stmt = connection.createStatement()) {
			stmt.clearWarnings();
			assertNull(stmt.getWarnings());
			try (ResultSet rs = stmt.executeQuery("SELECT 1 AS n")) {
				assertTrue(rs.next());
			}
		}
	}

	// ── Configuration lifetime ────────────────────────────────────────────────

	@Test
	void testStatementConfigurationPersistsAcrossQueries() throws SQLException {
		try (Statement stmt = connection.createStatement()) {
			stmt.setQueryTimeout(30);
			stmt.setMaxRows(5);
			stmt.setFetchSize(100);

			stmt.executeQuery("SELECT * FROM " + TEST_TABLE + " LIMIT 1").close();
			stmt.executeQuery("SELECT * FROM " + TEST_TABLE + " LIMIT 2").close();

			assertEquals(30, stmt.getQueryTimeout(), "Query timeout should persist");
			assertEquals(5, stmt.getMaxRows(), "Max rows should persist");
			assertEquals(100, stmt.getFetchSize(), "Fetch size should persist");
		}
	}

	@Test
	void testMultipleStatementsHaveIndependentConfiguration() throws SQLException {
		try (Statement first = connection.createStatement(); Statement second = connection.createStatement()) {
			first.setQueryTimeout(10);
			first.setMaxRows(5);

			second.setQueryTimeout(20);
			second.setMaxRows(10);

			assertEquals(10, first.getQueryTimeout());
			assertEquals(5, first.getMaxRows());
			assertEquals(20, second.getQueryTimeout());
			assertEquals(10, second.getMaxRows());
		}
	}
}

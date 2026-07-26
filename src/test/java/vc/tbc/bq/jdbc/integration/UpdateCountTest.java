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

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for JDBC update counts from BigQuery DML statistics.
 *
 * <p>
 * <b>Emulator limitation:</b> the BigQuery emulator does not populate
 * {@code numDmlAffectedRows} in query job statistics, so DML update counts come
 * back as 0 here and {@code execute()} cannot detect DML. Real BigQuery returns
 * exact counts — covered by
 * {@link vc.tbc.bq.jdbc.integration.real.RealUpdateCountTest}. These tests
 * assert the JDBC contract holds structurally (correct table state, count
 * either exact or the emulator's 0, consistent execute()/getUpdateCount()
 * behavior).
 *
 * @since 1.0.94
 */
class UpdateCountTest extends AbstractBigQueryIntegrationTest {

	private static final String TEST_TABLE = "update_count_test_table";

	@BeforeEach
	void setupTestTable() throws SQLException {
		createTestTable(TEST_TABLE);
		insertTestData(TEST_TABLE); // ids 1-3
	}

	@AfterEach
	void cleanupTestTable() {
		executeIgnoreErrors("DROP TABLE IF EXISTS " + TEST_TABLE);
	}

	@Test
	void testExecuteUpdateInsertReportsCountAndInsertsRows() throws SQLException {
		try (Statement stmt = connection.createStatement()) {
			int count = stmt.executeUpdate(
					"INSERT INTO " + TEST_TABLE + " (id, name) VALUES (100, 'x'), (101, 'y'), (102, 'z')");

			// Real BigQuery: 3; emulator: 0 (numDmlAffectedRows not reported)
			assertTrue(count == 3 || count == 0, "Unexpected update count: " + count);
		}
		assertRowCount(6);
	}

	@Test
	void testExecuteUpdateUpdateModifiesRows() throws SQLException {
		try (Statement stmt = connection.createStatement()) {
			int count = stmt.executeUpdate("UPDATE " + TEST_TABLE + " SET age = 99 WHERE id <= 2");

			// Real BigQuery: 2; emulator: 0 (numDmlAffectedRows not reported)
			assertTrue(count == 2 || count == 0, "Unexpected update count: " + count);

			try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) AS cnt FROM " + TEST_TABLE + " WHERE age = 99")) {
				assertTrue(rs.next());
				assertEquals(2, rs.getInt("cnt"), "UPDATE should have modified 2 rows");
			}
		}
	}

	@Test
	void testExecuteUpdateDeleteRemovesRows() throws SQLException {
		try (Statement stmt = connection.createStatement()) {
			int count = stmt.executeUpdate("DELETE FROM " + TEST_TABLE + " WHERE id >= 2");

			// Real BigQuery: 2; emulator: 0 (numDmlAffectedRows not reported)
			assertTrue(count == 2 || count == 0, "Unexpected update count: " + count);
		}
		assertRowCount(1);
	}

	@Test
	void testGetUpdateCountMatchesExecuteUpdateResult() throws SQLException {
		try (Statement stmt = connection.createStatement()) {
			int count = stmt.executeUpdate("INSERT INTO " + TEST_TABLE + " (id, name) VALUES (300, 'a'), (301, 'b')");

			assertEquals(count, stmt.getUpdateCount(), "getUpdateCount should match the executeUpdate result");
			assertEquals(count, stmt.getLargeUpdateCount());
		}
	}

	@Test
	void testGetUpdateCountIsMinusOneAfterSelect() throws SQLException {
		try (Statement stmt = connection.createStatement();
				ResultSet rs = stmt.executeQuery("SELECT * FROM " + TEST_TABLE)) {
			assertNotNull(rs);
			assertEquals(-1, stmt.getUpdateCount(), "getUpdateCount must be -1 when the result is a ResultSet");
		}
	}

	@Test
	void testExecuteDmlContractIsConsistent() throws SQLException {
		try (Statement stmt = connection.createStatement()) {
			boolean isResultSet = stmt.execute("INSERT INTO " + TEST_TABLE + " (id, name) VALUES (400, 'dml')");

			if (isResultSet) {
				// Emulator path: no DML statistics, result surfaced as (empty) ResultSet
				assertNotNull(stmt.getResultSet());
				assertEquals(-1, stmt.getUpdateCount());
			} else {
				// Real BigQuery path: DML detected, result is an update count
				assertNull(stmt.getResultSet(), "getResultSet() must return null when the result is an update count");
				assertEquals(1, stmt.getUpdateCount());
			}
		}
		assertRowCount(4);
	}

	@Test
	void testExecuteReturnsTrueForSelect() throws SQLException {
		try (Statement stmt = connection.createStatement()) {
			boolean isResultSet = stmt.execute("SELECT * FROM " + TEST_TABLE);

			assertTrue(isResultSet, "execute() should return true for SELECT");
			assertNotNull(stmt.getResultSet());
			assertEquals(-1, stmt.getUpdateCount());
		}
	}

	@Test
	void testPreparedStatementExecuteUpdateReportsCount() throws SQLException {
		// Single INT64 parameter: the emulator mis-binds mixed-type parameter
		// lists in UPDATE statements
		String sql = "UPDATE " + TEST_TABLE + " SET age = 99 WHERE id <= ?";
		try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
			pstmt.setInt(1, 2);
			int count = pstmt.executeUpdate();

			// Real BigQuery: 2; emulator: 0 (numDmlAffectedRows not reported)
			assertTrue(count == 2 || count == 0, "Unexpected update count: " + count);
			assertEquals(count, pstmt.getUpdateCount());
		}
	}

	@Test
	void testPreparedStatementExecuteDmlContractIsConsistent() throws SQLException {
		String sql = "DELETE FROM " + TEST_TABLE + " WHERE id = ?";
		try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
			pstmt.setInt(1, 1);

			boolean isResultSet = pstmt.execute();

			if (isResultSet) {
				assertNotNull(pstmt.getResultSet());
				assertEquals(-1, pstmt.getUpdateCount());
			} else {
				assertNull(pstmt.getResultSet());
				assertEquals(1, pstmt.getUpdateCount());
			}
		}
		assertRowCount(2);
	}

	@Test
	void testExecuteUpdateOnDdlReturnsZero() throws SQLException {
		String ddlTable = TEST_TABLE + "_ddl";
		try (Statement stmt = connection.createStatement()) {
			int count = stmt.executeUpdate("CREATE TABLE " + ddlTable + " (id INT64)");

			assertEquals(0, count, "executeUpdate on DDL should return 0");
		} finally {
			executeIgnoreErrors("DROP TABLE IF EXISTS " + ddlTable);
		}
	}

	private void assertRowCount(int expected) throws SQLException {
		try (Statement stmt = connection.createStatement();
				ResultSet rs = stmt.executeQuery("SELECT COUNT(*) AS cnt FROM " + TEST_TABLE)) {
			assertTrue(rs.next());
			assertEquals(expected, rs.getInt("cnt"));
		}
	}
}

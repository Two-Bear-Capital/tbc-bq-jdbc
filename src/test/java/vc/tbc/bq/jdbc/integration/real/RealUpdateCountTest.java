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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real BigQuery integration tests for JDBC update counts.
 *
 * <p>
 * Mirrors {@link vc.tbc.bq.jdbc.integration.UpdateCountTest} with exact
 * assertions: real BigQuery populates {@code numDmlAffectedRows} in DML job
 * statistics, so {@code executeUpdate()}, {@code getUpdateCount()}, and
 * {@code execute()} must report per-spec results (the emulator does not
 * populate these statistics, so the emulator variant is tolerant).
 *
 * @since 1.0.94
 */
class RealUpdateCountTest extends AbstractRealBigQueryIntegrationTest {

	private final String testTable = tableName("update_count_test");

	@BeforeEach
	void setupTestTable() throws SQLException {
		createTestTable(testTable);
		insertTestData(testTable); // ids 1-3
	}

	@AfterEach
	void cleanupTestTable() {
		executeIgnoreErrors("DROP TABLE IF EXISTS " + testTable);
	}

	@Test
	void testExecuteUpdateReturnsInsertedRowCount() throws SQLException {
		try (Statement stmt = connection.createStatement()) {
			int count = stmt.executeUpdate(
					"INSERT INTO " + testTable + " (id, name) VALUES (100, 'x'), (101, 'y'), (102, 'z')");

			assertEquals(3, count, "executeUpdate should return the number of inserted rows");
		}
	}

	@Test
	void testExecuteUpdateReturnsUpdatedRowCount() throws SQLException {
		try (Statement stmt = connection.createStatement()) {
			int count = stmt.executeUpdate("UPDATE " + testTable + " SET age = 99 WHERE id <= 2");

			assertEquals(2, count, "executeUpdate should return the number of updated rows");
		}
	}

	@Test
	void testExecuteUpdateReturnsDeletedRowCount() throws SQLException {
		try (Statement stmt = connection.createStatement()) {
			int count = stmt.executeUpdate("DELETE FROM " + testTable + " WHERE id >= 2");

			assertEquals(2, count, "executeUpdate should return the number of deleted rows");
		}
	}

	@Test
	void testGetUpdateCountAfterExecuteUpdate() throws SQLException {
		try (Statement stmt = connection.createStatement()) {
			stmt.executeUpdate("INSERT INTO " + testTable + " (id, name) VALUES (300, 'a'), (301, 'b')");

			assertEquals(2, stmt.getUpdateCount(), "getUpdateCount should report the DML affected-row count");
			assertEquals(2L, stmt.getLargeUpdateCount());
		}
	}

	@Test
	void testGetUpdateCountIsMinusOneAfterSelect() throws SQLException {
		try (Statement stmt = connection.createStatement();
				ResultSet rs = stmt.executeQuery("SELECT * FROM " + testTable)) {
			assertNotNull(rs);
			assertEquals(-1, stmt.getUpdateCount(), "getUpdateCount must be -1 when the result is a ResultSet");
		}
	}

	@Test
	void testExecuteReturnsFalseForDml() throws SQLException {
		try (Statement stmt = connection.createStatement()) {
			boolean isResultSet = stmt.execute("INSERT INTO " + testTable + " (id, name) VALUES (400, 'dml')");

			assertFalse(isResultSet, "execute() should return false for DML");
			assertNull(stmt.getResultSet(), "getResultSet() must return null when the result is an update count");
			assertEquals(1, stmt.getUpdateCount());
		}
	}

	@Test
	void testExecuteReturnsTrueForSelect() throws SQLException {
		try (Statement stmt = connection.createStatement()) {
			boolean isResultSet = stmt.execute("SELECT * FROM " + testTable);

			assertTrue(isResultSet, "execute() should return true for SELECT");
			assertNotNull(stmt.getResultSet());
			assertEquals(-1, stmt.getUpdateCount());
		}
	}

	@Test
	void testPreparedStatementExecuteUpdateReturnsAffectedRowCount() throws SQLException {
		String sql = "UPDATE " + testTable + " SET name = ? WHERE id <= ?";
		try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
			pstmt.setString(1, "renamed");
			pstmt.setInt(2, 2);

			assertEquals(2, pstmt.executeUpdate(), "PreparedStatement.executeUpdate should return affected rows");
		}
	}

	@Test
	void testPreparedStatementExecuteReturnsFalseForDml() throws SQLException {
		String sql = "DELETE FROM " + testTable + " WHERE id = ?";
		try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
			pstmt.setInt(1, 1);

			boolean isResultSet = pstmt.execute();

			assertFalse(isResultSet, "execute() should return false for DML");
			assertEquals(1, pstmt.getUpdateCount());
		}
	}

	@Test
	void testPreparedStatementBatchInsertReportsPerRowCounts() throws SQLException {
		String sql = "INSERT INTO " + testTable + " (id, name, age) VALUES (?, ?, ?)";
		try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
			for (int i = 500; i < 505; i++) {
				pstmt.setInt(1, i);
				pstmt.setString(2, "batch_" + i);
				pstmt.setInt(3, i);
				pstmt.addBatch();
			}

			int[] counts = pstmt.executeBatch();

			assertEquals(5, counts.length);
			for (int count : counts) {
				assertEquals(1, count, "Collapsed batch INSERT should confirm 1 affected row per entry");
			}
		}
	}

	@Test
	void testExecuteUpdateOnDdlReturnsZero() throws SQLException {
		String ddlTable = tableName("update_count_ddl");
		try (Statement stmt = connection.createStatement()) {
			int count = stmt.executeUpdate("CREATE TABLE " + ddlTable + " (id INT64)");

			assertEquals(0, count, "executeUpdate on DDL should return 0");
		} finally {
			executeIgnoreErrors("DROP TABLE IF EXISTS " + ddlTable);
		}
	}
}

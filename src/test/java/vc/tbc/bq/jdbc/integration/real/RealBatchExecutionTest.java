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
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Batch execution against real BigQuery.
 *
 * <p>
 * Port of {@code BatchExecutionTest} (issue #118). Its update-count assertion
 * was {@code count == 1 || count == SUCCESS_NO_INFO}, which accepts either
 * answer, and several tests checked only {@code counts.length} without looking
 * at the values.
 *
 * <p>
 * That tolerance existed solely for the emulator, which never populates
 * {@code numDmlAffectedRows}. {@code executeCollapsedBatch} decides
 * {@code perRowCount = affectedRows == chunkRows ? 1 : SUCCESS_NO_INFO}, so
 * against real BigQuery every entry is exactly 1 — asserted here.
 *
 * <p>
 * Each test writes, so each gets its own table and the class runs its methods
 * concurrently, following {@code RealUpdateCountTest}.
 *
 * @since 1.0.118
 */
@Execution(ExecutionMode.CONCURRENT)
class RealBatchExecutionTest extends AbstractRealBigQueryIntegrationTest {

	private String testTable;

	@BeforeEach
	void createTable(TestInfo testInfo) throws SQLException {
		String method = testInfo.getTestMethod().map(java.lang.reflect.Method::getName).orElse("unknown");
		testTable = tableName("batch_" + method);
		createTestTable(testTable);
	}

	@AfterEach
	void dropTable() {
		executeIgnoreErrors("DROP TABLE IF EXISTS " + testTable);
	}

	private void assertRowCount(int expected) throws SQLException {
		try (Statement stmt = connection.createStatement();
				ResultSet rs = stmt.executeQuery("SELECT COUNT(*) AS cnt FROM " + testTable)) {
			assertTrue(rs.next());
			assertEquals(expected, rs.getInt("cnt"));
		}
	}

	@Test
	void testPreparedStatementBatchInsertCollapsesToMultiRowInsert() throws SQLException {
		int rowCount = 5;
		try (PreparedStatement pstmt = connection
				.prepareStatement("INSERT INTO " + testTable + " (id, name, age) VALUES (?, ?, ?)")) {
			for (int i = 1; i <= rowCount; i++) {
				pstmt.setInt(1, i);
				pstmt.setString(2, "user_" + i);
				pstmt.setInt(3, 20 + i);
				pstmt.addBatch();
			}

			int[] counts = pstmt.executeBatch();

			assertEquals(rowCount, counts.length, "One update count per batched row");
			for (int count : counts) {
				// The emulator tier accepted SUCCESS_NO_INFO here. Real BigQuery reports
				// numDmlAffectedRows, so the driver can claim per-row success.
				assertEquals(1, count, "Each row should report exactly one affected row");
			}
		}

		assertRowCount(rowCount);
		try (Statement stmt = connection.createStatement();
				ResultSet rs = stmt.executeQuery("SELECT id, name, age FROM " + testTable + " ORDER BY id")) {
			for (int i = 1; i <= rowCount; i++) {
				assertTrue(rs.next());
				assertEquals(i, rs.getInt("id"));
				assertEquals("user_" + i, rs.getString("name"));
				assertEquals(20 + i, rs.getInt("age"));
			}
			assertFalse(rs.next());
		}
	}

	@Test
	void testPreparedStatementBatchWithMixedLiteralFallsBackToSequential() throws SQLException {
		try (PreparedStatement pstmt = connection
				.prepareStatement("INSERT INTO " + testTable + " (id, name, is_active) VALUES (?, ?, true)")) {
			pstmt.setInt(1, 1);
			pstmt.setString(2, "alpha");
			pstmt.addBatch();

			pstmt.setInt(1, 2);
			pstmt.setString(2, "beta");
			pstmt.addBatch();

			int[] counts = pstmt.executeBatch();

			assertEquals(2, counts.length);
			// Sequential path: one job per set, each reporting its own affected rows.
			assertEquals(1, counts[0], "First row");
			assertEquals(1, counts[1], "Second row");
		}

		assertRowCount(2);
		try (Statement stmt = connection.createStatement();
				ResultSet rs = stmt.executeQuery("SELECT id, name, is_active FROM " + testTable + " ORDER BY id")) {
			assertTrue(rs.next());
			assertEquals("alpha", rs.getString("name"));
			assertTrue(rs.getBoolean("is_active"), "The literal in the tuple should be applied");
			assertTrue(rs.next());
			assertEquals("beta", rs.getString("name"));
			assertTrue(rs.getBoolean("is_active"));
		}
	}

	@Test
	void testExecuteBatchClearsBatch() throws SQLException {
		try (PreparedStatement pstmt = connection
				.prepareStatement("INSERT INTO " + testTable + " (id, name) VALUES (?, ?)")) {
			pstmt.setInt(1, 1);
			pstmt.setString(2, "once");
			pstmt.addBatch();

			int[] first = pstmt.executeBatch();
			assertEquals(1, first.length);
			assertEquals(1, first[0]);

			assertEquals(0, pstmt.executeBatch().length, "The batch was consumed by the first execution");
		}

		assertRowCount(1);
	}

	@Test
	void testClearBatchDiscardsPendingRows() throws SQLException {
		try (PreparedStatement pstmt = connection
				.prepareStatement("INSERT INTO " + testTable + " (id, name) VALUES (?, ?)")) {
			pstmt.setInt(1, 1);
			pstmt.setString(2, "discarded");
			pstmt.addBatch();

			pstmt.clearBatch();

			assertEquals(0, pstmt.executeBatch().length);
		}

		assertRowCount(0);
	}

	@Test
	void testStatementHeterogeneousSqlBatch() throws SQLException {
		try (Statement stmt = connection.createStatement()) {
			stmt.addBatch("INSERT INTO " + testTable + " (id, name) VALUES (10, 'ten')");
			stmt.addBatch("INSERT INTO " + testTable + " (id, name) VALUES (20, 'twenty')");

			int[] counts = stmt.executeBatch();

			assertEquals(2, counts.length, "One update count per command");
			assertEquals(1, counts[0], "First command");
			assertEquals(1, counts[1], "Second command");
		}

		assertRowCount(2);
	}

	@Test
	void testStatementClearBatch() throws SQLException {
		try (Statement stmt = connection.createStatement()) {
			stmt.addBatch("INSERT INTO " + testTable + " (id, name) VALUES (1, 'never')");
			stmt.clearBatch();

			assertEquals(0, stmt.executeBatch().length);
		}

		assertRowCount(0);
	}

	@Test
	void testDatabaseMetaDataReportsBatchSupport() throws SQLException {
		assertTrue(connection.getMetaData().supportsBatchUpdates());
	}

	@Test
	void testLargeBatchExecutesInSingleCollapsedStatement() throws SQLException {
		int rowCount = 50;
		try (PreparedStatement pstmt = connection
				.prepareStatement("INSERT INTO " + testTable + " (id, name, age) VALUES (?, ?, ?)")) {
			for (int i = 1; i <= rowCount; i++) {
				pstmt.setInt(1, i);
				pstmt.setString(2, "bulk_" + i);
				pstmt.setInt(3, i);
				pstmt.addBatch();
			}

			int[] counts = pstmt.executeBatch();
			assertEquals(rowCount, counts.length);
			for (int count : counts) {
				assertEquals(1, count, "50 rows still collapse into one job with per-row counts");
			}
		}

		assertRowCount(rowCount);
	}
}

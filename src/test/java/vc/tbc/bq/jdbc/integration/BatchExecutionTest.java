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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for JDBC batch execution (addBatch/executeBatch).
 *
 * <p>
 * Covers the multi-row INSERT collapse path on PreparedStatement, the
 * sequential fallback for non-collapsible statements, and heterogeneous SQL
 * batches on plain Statement.
 *
 * @since 1.0.94
 */
class BatchExecutionTest extends AbstractBigQueryIntegrationTest {

	private static final Logger logger = LoggerFactory.getLogger(BatchExecutionTest.class);

	private static final String TEST_TABLE = "batch_test_table";

	@BeforeEach
	void setupTestTable() throws SQLException {
		createTestTable(TEST_TABLE);
	}

	@AfterEach
	void cleanupTestTable() {
		executeIgnoreErrors("DROP TABLE IF EXISTS " + TEST_TABLE);
	}

	@Test
	void testPreparedStatementBatchInsertCollapsesToMultiRowInsert() throws SQLException {
		// Given: A simple parameterized INSERT (collapsible)
		String sql = "INSERT INTO " + TEST_TABLE + " (id, name, age) VALUES (?, ?, ?)";
		int rowCount = 5;

		try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
			for (int i = 1; i <= rowCount; i++) {
				pstmt.setInt(1, i);
				pstmt.setString(2, "user_" + i);
				pstmt.setInt(3, 20 + i);
				pstmt.addBatch();
			}

			// When: Executing the batch (single multi-row INSERT job)
			int[] counts = pstmt.executeBatch();

			// Then: One update count per batched row, each 1 or SUCCESS_NO_INFO
			assertEquals(rowCount, counts.length);
			for (int count : counts) {
				assertTrue(count == 1 || count == Statement.SUCCESS_NO_INFO, "Unexpected update count: " + count);
			}
		}

		// Then: All rows are present with correct values
		assertRowCount(rowCount);
		try (Statement stmt = connection.createStatement();
				ResultSet rs = stmt.executeQuery("SELECT id, name, age FROM " + TEST_TABLE + " ORDER BY id")) {
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
		// Given: A tuple mixing a literal with placeholders (not collapsible)
		String sql = "INSERT INTO " + TEST_TABLE + " (id, name, is_active) VALUES (?, ?, true)";

		try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
			pstmt.setInt(1, 1);
			pstmt.setString(2, "alpha");
			pstmt.addBatch();

			pstmt.setInt(1, 2);
			pstmt.setString(2, "beta");
			pstmt.addBatch();

			// When: Executing the batch (sequential, one job per row)
			int[] counts = pstmt.executeBatch();

			// Then: Both rows inserted
			assertEquals(2, counts.length);
		}

		assertRowCount(2);
	}

	@Test
	void testExecuteBatchClearsBatch() throws SQLException {
		String sql = "INSERT INTO " + TEST_TABLE + " (id, name) VALUES (?, ?)";

		try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
			pstmt.setInt(1, 1);
			pstmt.setString(2, "once");
			pstmt.addBatch();

			// When: Executing twice
			assertEquals(1, pstmt.executeBatch().length);
			int[] secondRun = pstmt.executeBatch();

			// Then: The batch was consumed by the first execution
			assertEquals(0, secondRun.length);
		}

		assertRowCount(1);
	}

	@Test
	void testClearBatchDiscardsPendingRows() throws SQLException {
		String sql = "INSERT INTO " + TEST_TABLE + " (id, name) VALUES (?, ?)";

		try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
			pstmt.setInt(1, 1);
			pstmt.setString(2, "discarded");
			pstmt.addBatch();

			// When: Clearing before execution
			pstmt.clearBatch();

			// Then: Nothing executes and nothing is inserted
			assertEquals(0, pstmt.executeBatch().length);
		}

		assertRowCount(0);
	}

	@Test
	void testStatementHeterogeneousSqlBatch() throws SQLException {
		// Given: Plain Statement with heterogeneous SQL commands
		try (Statement stmt = connection.createStatement()) {
			stmt.addBatch("INSERT INTO " + TEST_TABLE + " (id, name) VALUES (10, 'ten')");
			stmt.addBatch("INSERT INTO " + TEST_TABLE + " (id, name) VALUES (20, 'twenty')");

			// When: Executing the batch (sequential jobs)
			int[] counts = stmt.executeBatch();

			// Then: One update count per command
			assertEquals(2, counts.length);
		}

		assertRowCount(2);
	}

	@Test
	void testStatementClearBatch() throws SQLException {
		try (Statement stmt = connection.createStatement()) {
			stmt.addBatch("INSERT INTO " + TEST_TABLE + " (id, name) VALUES (1, 'never')");
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
		// Given: A batch large enough to prove collapse works beyond a handful of
		// rows (still one chunk: 50 rows * 3 params is far under BigQuery limits)
		String sql = "INSERT INTO " + TEST_TABLE + " (id, name, age) VALUES (?, ?, ?)";
		int rowCount = 50;

		try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
			for (int i = 1; i <= rowCount; i++) {
				pstmt.setInt(1, i);
				pstmt.setString(2, "bulk_" + i);
				pstmt.setInt(3, i);
				pstmt.addBatch();
			}

			int[] counts = pstmt.executeBatch();
			assertEquals(rowCount, counts.length);
		}

		assertRowCount(rowCount);
		logger.info("✓ Collapsed batch insert of {} rows completed", rowCount);
	}

	private void assertRowCount(int expected) throws SQLException {
		try (Statement stmt = connection.createStatement();
				ResultSet rs = stmt.executeQuery("SELECT COUNT(*) AS cnt FROM " + TEST_TABLE)) {
			assertTrue(rs.next());
			assertEquals(expected, rs.getInt("cnt"));
		}
	}
}

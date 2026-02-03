/*
 * Copyright 2026 Two Bear Capital
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.twobearcapital.bigquery.jdbc.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.*;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for ResultSet operations.
 *
 * @since 1.0.0
 */
class ResultSetOperationsTest extends AbstractBigQueryIntegrationTest {

	@Test
	void testResultSetNext() throws SQLException {
		// Given: A query with multiple rows
		createTestTable("users");
		insertTestData("users");

		String sql = "SELECT id FROM users ORDER BY id";

		// When: Iterating through results
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			// Then: Should iterate correctly
			assertTrue(rs.next());
			assertEquals(1, rs.getInt("id"));

			assertTrue(rs.next());
			assertEquals(2, rs.getInt("id"));

			assertTrue(rs.next());
			assertEquals(3, rs.getInt("id"));

			assertFalse(rs.next());
		}
	}

	@Test
	void testResultSetGetByColumnIndex() throws SQLException {
		// Given: A query
		String sql = "SELECT 'Alice' as name, 30 as age";

		// When: Getting by column index
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			assertTrue(rs.next());

			// Then: Should get by index (1-based)
			assertEquals("Alice", rs.getString(1));
			assertEquals(30, rs.getInt(2));
		}
	}

	@Test
	void testResultSetGetByColumnName() throws SQLException {
		// Given: A query
		String sql = "SELECT 'Bob' as name, 25 as age";

		// When: Getting by column name
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			assertTrue(rs.next());

			// Then: Should get by name
			assertEquals("Bob", rs.getString("name"));
			assertEquals(25, rs.getInt("age"));
		}
	}

	@Test
	void testResultSetWasNull() throws SQLException {
		// Given: Query with NULL
		String sql = "SELECT NULL as null_col, 'not null' as str_col";

		// When: Accessing NULL value
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			assertTrue(rs.next());

			// Then: wasNull should reflect NULL status
			assertNull(rs.getString("null_col"));
			assertTrue(rs.wasNull());

			assertEquals("not null", rs.getString("str_col"));
			assertFalse(rs.wasNull());
		}
	}

	@Test
	void testResultSetMetaData() throws SQLException {
		// Given: A query
		String sql = "SELECT 1 as id, 'test' as name, 3.14 as value";

		// When: Getting metadata
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			ResultSetMetaData metaData = rs.getMetaData();

			// Then: Should have correct metadata
			assertEquals(3, metaData.getColumnCount());
			assertEquals("id", metaData.getColumnName(1));
			assertEquals("name", metaData.getColumnName(2));
			assertEquals("value", metaData.getColumnName(3));
		}
	}

	@Test
	void testResultSetClose() throws SQLException {
		// Given: An open result set
		Statement stmt = connection.createStatement();
		ResultSet rs = stmt.executeQuery("SELECT 1");

		assertFalse(rs.isClosed());

		// When: Closing result set
		rs.close();

		// Then: Should be closed
		assertTrue(rs.isClosed());

		// And: Operations should throw exception
		assertThrows(SQLException.class, rs::next);

		stmt.close();
	}

	@Test
	void testStatementCloseClosesResultSet() throws SQLException {
		// Given: Statement with result set
		Statement stmt = connection.createStatement();
		ResultSet rs = stmt.executeQuery("SELECT 1");

		assertFalse(rs.isClosed());

		// When: Closing statement
		stmt.close();

		// Then: Result set should also be closed
		assertTrue(rs.isClosed());
		assertTrue(stmt.isClosed());
	}

	@Test
	void testResultSetType() throws SQLException {
		// Given: A result set
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery("SELECT 1")) {

			// Then: Should be TYPE_FORWARD_ONLY
			assertEquals(ResultSet.TYPE_FORWARD_ONLY, rs.getType());
		}
	}

	@Test
	void testResultSetConcurrency() throws SQLException {
		// Given: A result set
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery("SELECT 1")) {

			// Then: Should be CONCUR_READ_ONLY
			assertEquals(ResultSet.CONCUR_READ_ONLY, rs.getConcurrency());
		}
	}

	@Test
	void testResultSetFetchDirection() throws SQLException {
		// Given: A result set
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery("SELECT 1")) {

			// Then: Should be FETCH_FORWARD
			assertEquals(ResultSet.FETCH_FORWARD, rs.getFetchDirection());
		}
	}

	@Test
	void testResultSetFindColumn() throws SQLException {
		// Given: A query with named columns
		String sql = "SELECT 1 as id, 'test' as name";

		// When: Finding column index
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			// Then: Should find correct indices
			assertEquals(1, rs.findColumn("id"));
			assertEquals(2, rs.findColumn("name"));
		}
	}

	@Test
	void testResultSetFindColumnCaseInsensitive() throws SQLException {
		// Given: A query
		String sql = "SELECT 1 as MyColumn";

		// When: Finding column with different case
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			// Then: Should find regardless of case
			assertEquals(1, rs.findColumn("mycolumn"));
			assertEquals(1, rs.findColumn("MYCOLUMN"));
			assertEquals(1, rs.findColumn("MyColumn"));
		}
	}

	@Test
	void testResultSetInvalidColumnName() throws SQLException {
		// Given: A result set
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery("SELECT 1 as id")) {

			assertTrue(rs.next());

			// Then: Invalid column name should throw exception
			assertThrows(SQLException.class, () -> rs.getString("nonexistent"));
		}
	}

	@Test
	void testResultSetInvalidColumnIndex() throws SQLException {
		// Given: A result set with 1 column
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery("SELECT 1")) {

			assertTrue(rs.next());

			// Then: Invalid index should throw exception
			assertThrows(SQLException.class, () -> rs.getString(0)); // Index starts at 1
			assertThrows(SQLException.class, () -> rs.getString(2)); // Only 1 column
		}
	}

	@Test
	void testResultSetBeforeFirst() throws SQLException {
		// Given: A new result set
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery("SELECT 1")) {

			// Then: Accessing data before next() should throw exception
			assertThrows(SQLException.class, () -> rs.getString(1));
		}
	}

	@Test
	void testResultSetAfterLast() throws SQLException {
		// Given: A result set
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery("SELECT 1")) {

			assertTrue(rs.next());
			assertFalse(rs.next()); // Now after last

			// Then: Accessing data after last should throw exception or return null
			assertThrows(SQLException.class, () -> rs.getString(1));
		}
	}

	@Test
	void testMultipleResultSets() throws SQLException {
		// Given: Multiple result sets
		Statement stmt = connection.createStatement();
		ResultSet rs1 = stmt.executeQuery("SELECT 1");
		ResultSet rs2 = stmt.executeQuery("SELECT 2");

		// Then: First should be closed when second is created
		assertTrue(rs1.isClosed());
		assertFalse(rs2.isClosed());

		rs2.close();
		stmt.close();
	}

	@Test
	void testResultSetGetStatement() throws SQLException {
		// Given: A result set
		Statement stmt = connection.createStatement();
		ResultSet rs = stmt.executeQuery("SELECT 1");

		// Then: Should return parent statement
		assertEquals(stmt, rs.getStatement());

		rs.close();
		stmt.close();
	}

	@Test
	void testResultSetWarnings() throws SQLException {
		// Given: A result set
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery("SELECT 1")) {

			// Then: Should have no warnings
			assertNull(rs.getWarnings());

			// When: Clearing warnings
			rs.clearWarnings();

			// Then: Should still have no warnings
			assertNull(rs.getWarnings());
		}
	}
}

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
package com.twobearcapital.bigquery.jdbc.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.*;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for simple query execution.
 *
 * @since 1.0.0
 */
class SimpleQueryTest extends AbstractBigQueryIntegrationTest {

	@Test
	void testSelectLiteral() throws SQLException {
		// Given: A simple SELECT query
		String sql = "SELECT 1 as num";

		// When: Executing query
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			// Then: Should return result
			assertTrue(rs.next());
			assertEquals(1, rs.getInt("num"));
			assertFalse(rs.next());
		}
	}

	@Test
	void testSelectMultipleColumns() throws SQLException {
		// Given: A query with multiple columns
		String sql = "SELECT 1 as id, 'test' as name, true as active";

		// When: Executing query
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			// Then: Should return all columns
			assertTrue(rs.next());
			assertEquals(1, rs.getInt("id"));
			assertEquals("test", rs.getString("name"));
			assertTrue(rs.getBoolean("active"));
			assertFalse(rs.next());
		}
	}

	@Test
	void testSelectWithMath() throws SQLException {
		// Given: A query with mathematical operations
		String sql = "SELECT 2 + 2 as sum, 10 * 5 as product, 100 / 4 as quotient";

		// When: Executing query
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			// Then: Should calculate correctly
			assertTrue(rs.next());
			assertEquals(4, rs.getInt("sum"));
			assertEquals(50, rs.getInt("product"));
			assertEquals(25, rs.getInt("quotient"));
		}
	}

	@Test
	void testSelectWithTableScan() throws SQLException {
		// Given: A test table with data
		createTestTable("users");
		insertTestData("users");

		// When: Querying the table
		String sql = "SELECT id, name, age FROM users ORDER BY id";
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			// Then: Should return all rows
			assertTrue(rs.next());
			assertEquals(1, rs.getInt("id"));
			assertEquals("Alice", rs.getString("name"));
			assertEquals(30, rs.getInt("age"));

			assertTrue(rs.next());
			assertEquals(2, rs.getInt("id"));
			assertEquals("Bob", rs.getString("name"));
			assertEquals(25, rs.getInt("age"));

			assertTrue(rs.next());
			assertEquals(3, rs.getInt("id"));
			assertEquals("Charlie", rs.getString("name"));
			assertEquals(35, rs.getInt("age"));

			assertFalse(rs.next());
		}
	}

	@Test
	void testSelectWithWhere() throws SQLException {
		// Given: A test table with data
		createTestTable("users");
		insertTestData("users");

		// When: Querying with WHERE clause
		String sql = "SELECT name, age FROM users WHERE age > 25 ORDER BY age";
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			// Then: Should return filtered rows
			assertTrue(rs.next());
			assertEquals("Alice", rs.getString("name"));
			assertEquals(30, rs.getInt("age"));

			assertTrue(rs.next());
			assertEquals("Charlie", rs.getString("name"));
			assertEquals(35, rs.getInt("age"));

			assertFalse(rs.next());
		}
	}

	@Test
	void testSelectCount() throws SQLException {
		// Given: A test table with data
		createTestTable("users");
		insertTestData("users");

		// When: Counting rows
		String sql = "SELECT COUNT(*) as total FROM users";
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			// Then: Should return count
			assertTrue(rs.next());
			assertEquals(3, rs.getInt("total"));
		}
	}

	@Test
	void testSelectWithAggregate() throws SQLException {
		// Given: A test table with data
		createTestTable("users");
		insertTestData("users");

		// When: Using aggregate functions
		String sql = "SELECT COUNT(*) as count, AVG(age) as avg_age, MAX(salary) as max_salary FROM users";
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			// Then: Should return aggregates
			assertTrue(rs.next());
			assertEquals(3, rs.getInt("count"));
			assertEquals(30.0, rs.getDouble("avg_age"), 0.01);
			assertEquals(85000.75, rs.getDouble("max_salary"), 0.01);
		}
	}

	@Test
	void testSelectWithGroupBy() throws SQLException {
		// Given: A test table with data
		createTestTable("users");
		insertTestData("users");

		// When: Using GROUP BY
		String sql = "SELECT is_active, COUNT(*) as count FROM users GROUP BY is_active ORDER BY is_active";
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			// Then: Should group correctly
			assertTrue(rs.next());
			assertFalse(rs.getBoolean("is_active"));
			assertEquals(1, rs.getInt("count"));

			assertTrue(rs.next());
			assertTrue(rs.getBoolean("is_active"));
			assertEquals(2, rs.getInt("count"));

			assertFalse(rs.next());
		}
	}

	@Test
	void testSelectWithNull() throws SQLException {
		// Given: A query with NULL
		String sql = "SELECT NULL as null_value, 'not null' as not_null";

		// When: Executing query
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			// Then: Should handle NULL correctly
			assertTrue(rs.next());
			assertNull(rs.getString("null_value"));
			assertTrue(rs.wasNull());
			assertEquals("not null", rs.getString("not_null"));
			assertFalse(rs.wasNull());
		}
	}

	@Test
	void testEmptyResultSet() throws SQLException {
		// Given: A query that returns no rows
		createTestTable("users");
		insertTestData("users");
		String sql = "SELECT * FROM users WHERE age > 100";

		// When: Executing query
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			// Then: Should return empty result set
			assertFalse(rs.next());
		}
	}

	@Test
	void testInvalidSQLThrowsException() {
		// Given: Invalid SQL
		String sql = "SELECT * FROM nonexistent_table_xyz";

		// Then: Should throw SQLException
		assertThrows(SQLException.class, () -> {
			try (Statement stmt = connection.createStatement()) {
				stmt.executeQuery(sql);
			}
		});
	}

	@Test
	void testSyntaxErrorThrowsException() {
		// Given: SQL with syntax error
		String sql = "SELECT INVALID SYNTAX";

		// Then: Should throw SQLException
		assertThrows(SQLException.class, () -> {
			try (Statement stmt = connection.createStatement()) {
				stmt.executeQuery(sql);
			}
		});
	}

	@Test
	void testResultSetMetaData() throws SQLException {
		// Given: A query
		String sql = "SELECT 1 as id, 'test' as name, 123.45 as amount";

		// When: Getting result set metadata
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			ResultSetMetaData metaData = rs.getMetaData();

			// Then: Should have correct metadata
			assertEquals(3, metaData.getColumnCount());

			assertEquals("id", metaData.getColumnName(1));
			assertEquals("name", metaData.getColumnName(2));
			assertEquals("amount", metaData.getColumnName(3));
		}
	}

	@Test
	void testMultipleStatements() throws SQLException {
		// Given: Multiple statements
		Statement stmt1 = connection.createStatement();
		Statement stmt2 = connection.createStatement();

		// When: Executing on both
		ResultSet rs1 = stmt1.executeQuery("SELECT 1 as num");
		ResultSet rs2 = stmt2.executeQuery("SELECT 2 as num");

		// Then: Both should work independently
		assertTrue(rs1.next());
		assertEquals(1, rs1.getInt("num"));

		assertTrue(rs2.next());
		assertEquals(2, rs2.getInt("num"));

		rs1.close();
		rs2.close();
		stmt1.close();
		stmt2.close();
	}

	@Test
	void testExecuteMethod() throws SQLException {
		// Given: A query
		String sql = "SELECT 1 as num";

		// When: Using execute() method
		try (Statement stmt = connection.createStatement()) {
			boolean hasResultSet = stmt.execute(sql);

			// Then: Should return true for SELECT
			assertTrue(hasResultSet);

			ResultSet rs = stmt.getResultSet();
			assertNotNull(rs);
			assertTrue(rs.next());
			assertEquals(1, rs.getInt("num"));
		}
	}
}

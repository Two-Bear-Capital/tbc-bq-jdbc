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

import java.math.BigDecimal;
import java.sql.*;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for parameterized queries using PreparedStatement.
 *
 * @since 1.0.0
 */
class ParameterizedQueryTest extends AbstractBigQueryIntegrationTest {

	@Test
	void testPreparedStatementWithIntParameter() throws SQLException {
		// Given: A test table with data
		createTestTable("users");
		insertTestData("users");

		// When: Using PreparedStatement with int parameter
		String sql = "SELECT name FROM users WHERE age > ? ORDER BY name";
		try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
			pstmt.setInt(1, 25);

			try (ResultSet rs = pstmt.executeQuery()) {
				// Then: Should return filtered results
				assertTrue(rs.next());
				assertEquals("Alice", rs.getString("name"));

				assertTrue(rs.next());
				assertEquals("Charlie", rs.getString("name"));

				assertFalse(rs.next());
			}
		}
	}

	@Test
	void testPreparedStatementWithStringParameter() throws SQLException {
		// Given: A test table with data
		createTestTable("users");
		insertTestData("users");

		// When: Using PreparedStatement with string parameter
		String sql = "SELECT age FROM users WHERE name = ?";
		try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
			pstmt.setString(1, "Bob");

			try (ResultSet rs = pstmt.executeQuery()) {
				// Then: Should return matching row
				assertTrue(rs.next());
				assertEquals(25, rs.getInt("age"));
				assertFalse(rs.next());
			}
		}
	}

	@Test
	void testPreparedStatementWithMultipleParameters() throws SQLException {
		// Given: A test table with data
		createTestTable("users");
		insertTestData("users");

		// When: Using multiple parameters
		String sql = "SELECT name FROM users WHERE age >= ? AND age <= ? ORDER BY name";
		try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
			pstmt.setInt(1, 25);
			pstmt.setInt(2, 30);

			try (ResultSet rs = pstmt.executeQuery()) {
				// Then: Should return range
				assertTrue(rs.next());
				assertEquals("Alice", rs.getString("name"));

				assertTrue(rs.next());
				assertEquals("Bob", rs.getString("name"));

				assertFalse(rs.next());
			}
		}
	}

	@Test
	void testPreparedStatementWithBooleanParameter() throws SQLException {
		// Given: A test table with data
		createTestTable("users");
		insertTestData("users");

		// When: Using boolean parameter
		String sql = "SELECT COUNT(*) as count FROM users WHERE is_active = ?";
		try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
			pstmt.setBoolean(1, true);

			try (ResultSet rs = pstmt.executeQuery()) {
				// Then: Should count active users
				assertTrue(rs.next());
				assertEquals(2, rs.getInt("count"));
			}
		}
	}

	@Test
	void testPreparedStatementWithDoubleParameter() throws SQLException {
		// Given: A test table with data
		createTestTable("users");
		insertTestData("users");

		// When: Using double parameter
		String sql = "SELECT name FROM users WHERE salary > ? ORDER BY name";
		try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
			pstmt.setDouble(1, 70000.0);

			try (ResultSet rs = pstmt.executeQuery()) {
				// Then: Should return high earners
				assertTrue(rs.next());
				assertEquals("Alice", rs.getString("name"));

				assertTrue(rs.next());
				assertEquals("Charlie", rs.getString("name"));

				assertFalse(rs.next());
			}
		}
	}

	@Test
	void testPreparedStatementWithBigDecimalParameter() throws SQLException {
		// Given: A test table
		createTestTable("users");
		insertTestData("users");

		// When: Using BigDecimal parameter
		String sql = "SELECT name FROM users WHERE salary >= ? ORDER BY name";
		try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
			pstmt.setBigDecimal(1, new BigDecimal("75000.00"));

			try (ResultSet rs = pstmt.executeQuery()) {
				// Then: Should filter correctly
				assertTrue(rs.next());
				assertEquals("Alice", rs.getString("name"));

				assertTrue(rs.next());
				assertEquals("Charlie", rs.getString("name"));

				assertFalse(rs.next());
			}
		}
	}

	@Test
	void testPreparedStatementWithDateParameter() throws SQLException {
		// Given: A test table with data
		createTestTable("users");
		insertTestData("users");

		// When: Using date parameter
		String sql = "SELECT name FROM users WHERE created_date >= ? ORDER BY name";
		try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
			pstmt.setDate(1, Date.valueOf("2024-02-01"));

			try (ResultSet rs = pstmt.executeQuery()) {
				// Then: Should filter by date
				assertTrue(rs.next());
				assertEquals("Bob", rs.getString("name"));

				assertTrue(rs.next());
				assertEquals("Charlie", rs.getString("name"));

				assertFalse(rs.next());
			}
		}
	}

	@Test
	@org.junit.jupiter.api.Disabled("BigQuery emulator bug: NULL parameters return '0' instead of null")
	void testPreparedStatementWithNullParameter() throws SQLException {
		// Given: A prepared statement
		String sql = "SELECT ? as value";

		// When: Setting NULL parameter
		try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
			pstmt.setNull(1, Types.VARCHAR);

			try (ResultSet rs = pstmt.executeQuery()) {
				// Then: Should handle NULL
				assertTrue(rs.next());
				assertNull(rs.getString("value"));
				assertTrue(rs.wasNull());
			}
		}
	}

	@Test
	void testPreparedStatementReuseWithDifferentParameters() throws SQLException {
		// Given: A test table
		createTestTable("users");
		insertTestData("users");

		// When: Reusing same PreparedStatement with different parameters
		String sql = "SELECT name FROM users WHERE age = ?";
		try (PreparedStatement pstmt = connection.prepareStatement(sql)) {

			// First execution
			pstmt.setInt(1, 30);
			try (ResultSet rs = pstmt.executeQuery()) {
				assertTrue(rs.next());
				assertEquals("Alice", rs.getString("name"));
				assertFalse(rs.next());
			}

			// Second execution with different parameter
			pstmt.setInt(1, 25);
			try (ResultSet rs = pstmt.executeQuery()) {
				assertTrue(rs.next());
				assertEquals("Bob", rs.getString("name"));
				assertFalse(rs.next());
			}
		}
	}

	@Test
	void testPreparedStatementWithLongParameter() throws SQLException {
		// Given: A query with long parameter
		String sql = "SELECT ? as big_number";

		// When: Using long parameter
		try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
			pstmt.setLong(1, 9223372036854775807L); // Long.MAX_VALUE

			try (ResultSet rs = pstmt.executeQuery()) {
				// Then: Should handle large numbers
				assertTrue(rs.next());
				assertEquals(9223372036854775807L, rs.getLong("big_number"));
			}
		}
	}

	@Test
	@org.junit.jupiter.api.Disabled("BigQuery emulator bug: setObject causes 'strconv.ParseInt: parsing \"test\": invalid syntax'")
	void testPreparedStatementWithObjectParameter() throws SQLException {
		// Given: A query
		String sql = "SELECT ? as str_value, ? as int_value, ? as bool_value";

		// When: Using setObject
		try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
			pstmt.setObject(1, "test");
			pstmt.setObject(2, 42);
			pstmt.setObject(3, true);

			try (ResultSet rs = pstmt.executeQuery()) {
				// Then: Should handle objects
				assertTrue(rs.next());
				assertEquals("test", rs.getString("str_value"));
				assertEquals(42, rs.getInt("int_value"));
				assertTrue(rs.getBoolean("bool_value"));
			}
		}
	}

	@Test
	void testPreparedStatementClearParameters() throws SQLException {
		// Given: A prepared statement with parameters
		String sql = "SELECT ? as value";

		try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
			pstmt.setInt(1, 100);

			// When: Clearing parameters
			pstmt.clearParameters();

			// Then: Should need to set parameters again
			assertThrows(SQLException.class, pstmt::executeQuery);
		}
	}

	@Test
	void testPreparedStatementExecuteMethod() throws SQLException {
		// Given: A prepared statement
		String sql = "SELECT ? as num";

		// When: Using execute() method
		try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
			pstmt.setInt(1, 42);

			boolean hasResultSet = pstmt.execute();

			// Then: Should return true for SELECT
			assertTrue(hasResultSet);

			ResultSet rs = pstmt.getResultSet();
			assertNotNull(rs);
			assertTrue(rs.next());
			assertEquals(42, rs.getInt("num"));
		}
	}

	@Test
	void testPreparedStatementWithComplexQuery() throws SQLException {
		// Given: A test table with data
		createTestTable("users");
		insertTestData("users");

		// When: Using complex query with multiple parameters
		String sql = "SELECT name, age, salary " + "FROM users " + "WHERE age BETWEEN ? AND ? " + "  AND is_active = ? "
				+ "  AND salary > ? " + "ORDER BY age";

		try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
			pstmt.setInt(1, 20);
			pstmt.setInt(2, 35);
			pstmt.setBoolean(3, true);
			pstmt.setDouble(4, 50000.0);

			try (ResultSet rs = pstmt.executeQuery()) {
				// Then: Should apply all filters
				assertTrue(rs.next());
				assertEquals("Bob", rs.getString("name"));
				assertEquals(25, rs.getInt("age"));

				assertTrue(rs.next());
				assertEquals("Alice", rs.getString("name"));
				assertEquals(30, rs.getInt("age"));

				assertFalse(rs.next());
			}
		}
	}
}

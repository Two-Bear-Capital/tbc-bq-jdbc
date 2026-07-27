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

import java.math.BigDecimal;
import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real BigQuery integration tests for parameterized queries using
 * PreparedStatement.
 *
 * <p>
 * Mirrors {@link vc.tbc.bq.jdbc.integration.ParameterizedQueryTest} but runs
 * against a real BigQuery instance. Re-enables the two tests that were disabled
 * due to emulator bugs.
 *
 * @since 1.0.68
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RealParameterizedQueryTest extends AbstractRealBigQueryIntegrationTest {

	/**
	 * Session-unique table name — prevents conflicts between concurrent CI runs.
	 */
	private static final String TABLE = tableName("users_params");

	@BeforeAll
	void createFixture() throws SQLException {
		// Every test in this class only reads TABLE, so build it once
		createSharedTestTable(TABLE);
	}

	@AfterAll
	void dropFixture() {
		dropSharedTestTable(TABLE);
	}

	@Test
	void testPreparedStatementWithIntParameter() throws SQLException {

		String sql = "SELECT name FROM " + TABLE + " WHERE age > ? ORDER BY name";
		try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
			pstmt.setInt(1, 25);

			try (ResultSet rs = pstmt.executeQuery()) {
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

		String sql = "SELECT age FROM " + TABLE + " WHERE name = ?";
		try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
			pstmt.setString(1, "Bob");

			try (ResultSet rs = pstmt.executeQuery()) {
				assertTrue(rs.next());
				assertEquals(25, rs.getInt("age"));
				assertFalse(rs.next());
			}
		}

	}

	@Test
	void testPreparedStatementWithMultipleParameters() throws SQLException {

		String sql = "SELECT name FROM " + TABLE + " WHERE age >= ? AND age <= ? ORDER BY name";
		try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
			pstmt.setInt(1, 25);
			pstmt.setInt(2, 30);

			try (ResultSet rs = pstmt.executeQuery()) {
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

		String sql = "SELECT COUNT(*) as count FROM " + TABLE + " WHERE is_active = ?";
		try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
			pstmt.setBoolean(1, true);

			try (ResultSet rs = pstmt.executeQuery()) {
				assertTrue(rs.next());
				assertEquals(2, rs.getInt("count"));
			}
		}

	}

	@Test
	void testPreparedStatementWithDoubleParameter() throws SQLException {

		String sql = "SELECT name FROM " + TABLE + " WHERE salary > ? ORDER BY name";
		try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
			pstmt.setDouble(1, 70000.0);

			try (ResultSet rs = pstmt.executeQuery()) {
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

		String sql = "SELECT name FROM " + TABLE + " WHERE salary >= ? ORDER BY name";
		try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
			pstmt.setBigDecimal(1, new BigDecimal("75000.00"));

			try (ResultSet rs = pstmt.executeQuery()) {
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

		String sql = "SELECT name FROM " + TABLE + " WHERE created_date >= ? ORDER BY name";
		try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
			pstmt.setDate(1, Date.valueOf("2024-02-01"));

			try (ResultSet rs = pstmt.executeQuery()) {
				assertTrue(rs.next());
				assertEquals("Bob", rs.getString("name"));

				assertTrue(rs.next());
				assertEquals("Charlie", rs.getString("name"));

				assertFalse(rs.next());
			}
		}

	}

	@Test
	void testPreparedStatementWithNullParameter() throws SQLException {
		// Re-enabled: emulator bug does not affect real BigQuery
		String sql = "SELECT ? as value";

		try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
			pstmt.setNull(1, Types.VARCHAR);

			try (ResultSet rs = pstmt.executeQuery()) {
				assertTrue(rs.next());
				assertNull(rs.getString("value"));
				assertTrue(rs.wasNull());
			}
		}
	}

	@Test
	void testPreparedStatementReuseWithDifferentParameters() throws SQLException {

		String sql = "SELECT name FROM " + TABLE + " WHERE age = ?";
		try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
			pstmt.setInt(1, 30);
			try (ResultSet rs = pstmt.executeQuery()) {
				assertTrue(rs.next());
				assertEquals("Alice", rs.getString("name"));
				assertFalse(rs.next());
			}

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
		String sql = "SELECT ? as big_number";

		try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
			pstmt.setLong(1, 9223372036854775807L);

			try (ResultSet rs = pstmt.executeQuery()) {
				assertTrue(rs.next());
				assertEquals(9223372036854775807L, rs.getLong("big_number"));
			}
		}
	}

	@Test
	void testPreparedStatementWithObjectParameter() throws SQLException {
		// Re-enabled: emulator bug does not affect real BigQuery
		String sql = "SELECT ? as str_value, ? as int_value, ? as bool_value";

		try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
			pstmt.setObject(1, "test");
			pstmt.setObject(2, 42);
			pstmt.setObject(3, true);

			try (ResultSet rs = pstmt.executeQuery()) {
				assertTrue(rs.next());
				assertEquals("test", rs.getString("str_value"));
				assertEquals(42, rs.getInt("int_value"));
				assertTrue(rs.getBoolean("bool_value"));
			}
		}
	}

	@Test
	void testPreparedStatementClearParameters() throws SQLException {
		String sql = "SELECT ? as value";

		try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
			pstmt.setInt(1, 100);

			pstmt.clearParameters();

			assertThrows(SQLException.class, pstmt::executeQuery);
		}
	}

	@Test
	void testPreparedStatementExecuteMethod() throws SQLException {
		String sql = "SELECT ? as num";

		try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
			pstmt.setInt(1, 42);

			boolean hasResultSet = pstmt.execute();
			assertTrue(hasResultSet);

			ResultSet rs = pstmt.getResultSet();
			assertNotNull(rs);
			assertTrue(rs.next());
			assertEquals(42, rs.getInt("num"));
		}
	}

	@Test
	void testPreparedStatementWithComplexQuery() throws SQLException {

		String sql = "SELECT name, age, salary " + "FROM " + TABLE + " " + "WHERE age BETWEEN ? AND ? "
				+ "  AND is_active = ? " + "  AND salary > ? " + "ORDER BY age";

		try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
			pstmt.setInt(1, 20);
			pstmt.setInt(2, 35);
			pstmt.setBoolean(3, true);
			pstmt.setDouble(4, 50000.0);

			try (ResultSet rs = pstmt.executeQuery()) {
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

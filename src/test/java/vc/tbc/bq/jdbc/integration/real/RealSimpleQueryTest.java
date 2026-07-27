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

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real BigQuery integration tests for simple query execution.
 *
 * <p>
 * Mirrors {@link vc.tbc.bq.jdbc.integration.SimpleQueryTest} but runs against a
 * real BigQuery instance.
 *
 * @since 1.0.68
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RealSimpleQueryTest extends AbstractRealBigQueryIntegrationTest {

	private static final String TABLE = tableName("users_simple");

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
	void testSelectLiteral() throws SQLException {
		String sql = "SELECT 1 as num";

		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
			assertTrue(rs.next());
			assertEquals(1, rs.getInt("num"));
			assertFalse(rs.next());
		}
	}

	@Test
	void testSelectMultipleColumns() throws SQLException {
		String sql = "SELECT 1 as id, 'test' as name, true as active";

		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
			assertTrue(rs.next());
			assertEquals(1, rs.getInt("id"));
			assertEquals("test", rs.getString("name"));
			assertTrue(rs.getBoolean("active"));
			assertFalse(rs.next());
		}
	}

	@Test
	void testSelectWithMath() throws SQLException {
		String sql = "SELECT 2 + 2 as sum, 10 * 5 as product, 100 / 4 as quotient";

		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
			assertTrue(rs.next());
			assertEquals(4, rs.getInt("sum"));
			assertEquals(50, rs.getInt("product"));
			assertEquals(25, rs.getInt("quotient"));
		}
	}

	@Test
	void testSelectWithTableScan() throws SQLException {

		String sql = "SELECT id, name, age FROM " + TABLE + " ORDER BY id";
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
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

		String sql = "SELECT name, age FROM " + TABLE + " WHERE age > 25 ORDER BY age";
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
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

		String sql = "SELECT COUNT(*) as total FROM " + TABLE;
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
			assertTrue(rs.next());
			assertEquals(3, rs.getInt("total"));
		}

	}

	@Test
	void testSelectWithAggregate() throws SQLException {

		String sql = "SELECT COUNT(*) as count, AVG(age) as avg_age, MAX(salary) as max_salary FROM " + TABLE;
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
			assertTrue(rs.next());
			assertEquals(3, rs.getInt("count"));
			assertEquals(30.0, rs.getDouble("avg_age"), 0.01);
			assertEquals(85000.75, rs.getDouble("max_salary"), 0.01);
		}

	}

	@Test
	void testSelectWithGroupBy() throws SQLException {

		String sql = "SELECT is_active, COUNT(*) as count FROM " + TABLE + " GROUP BY is_active ORDER BY is_active";
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
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
		String sql = "SELECT NULL as null_value, 'not null' as not_null";

		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
			assertTrue(rs.next());
			assertNull(rs.getString("null_value"));
			assertTrue(rs.wasNull());
			assertEquals("not null", rs.getString("not_null"));
			assertFalse(rs.wasNull());
		}
	}

	@Test
	void testEmptyResultSet() throws SQLException {
		String sql = "SELECT * FROM " + TABLE + " WHERE age > 100";

		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
			assertFalse(rs.next());
		}

	}

	@Test
	void testInvalidSQLThrowsException() {
		String sql = "SELECT * FROM nonexistent_table_xyz";

		assertThrows(SQLException.class, () -> {
			try (Statement stmt = connection.createStatement()) {
				stmt.executeQuery(sql);
			}
		});
	}

	@Test
	void testSyntaxErrorThrowsException() {
		String sql = "SELECT INVALID SYNTAX";

		assertThrows(SQLException.class, () -> {
			try (Statement stmt = connection.createStatement()) {
				stmt.executeQuery(sql);
			}
		});
	}

	@Test
	void testResultSetMetaData() throws SQLException {
		String sql = "SELECT 1 as id, 'test' as name, 123.45 as amount";

		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
			ResultSetMetaData metaData = rs.getMetaData();

			assertEquals(3, metaData.getColumnCount());
			assertEquals("id", metaData.getColumnName(1));
			assertEquals("name", metaData.getColumnName(2));
			assertEquals("amount", metaData.getColumnName(3));
		}
	}

	@Test
	void testMultipleStatements() throws SQLException {
		try (Statement stmt1 = connection.createStatement();
				Statement stmt2 = connection.createStatement();
				ResultSet rs1 = stmt1.executeQuery("SELECT 1 as num");
				ResultSet rs2 = stmt2.executeQuery("SELECT 2 as num")) {

			assertTrue(rs1.next());
			assertEquals(1, rs1.getInt("num"));

			assertTrue(rs2.next());
			assertEquals(2, rs2.getInt("num"));
		}
	}

	@Test
	void testExecuteMethod() throws SQLException {
		String sql = "SELECT 1 as num";

		try (Statement stmt = connection.createStatement()) {
			boolean hasResultSet = stmt.execute(sql);
			assertTrue(hasResultSet);

			ResultSet rs = stmt.getResultSet();
			assertNotNull(rs);
			assertTrue(rs.next());
			assertEquals(1, rs.getInt("num"));
		}
	}
}

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

import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real BigQuery integration tests for ResultSet operations.
 *
 * <p>
 * Mirrors {@link vc.tbc.bq.jdbc.integration.ResultSetOperationsTest} but runs
 * against a real BigQuery instance.
 *
 * @since 1.0.68
 */
class RealResultSetOperationsTest extends AbstractRealBigQueryIntegrationTest {

	/**
	 * Session-unique table name — prevents conflicts between concurrent CI runs.
	 */
	private static final String TABLE = tableName("users");

	@Test
	void testResultSetNext() throws SQLException {
		createTestTable(TABLE);
		insertTestData(TABLE);

		String sql = "SELECT id FROM " + TABLE + " ORDER BY id";

		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
			assertTrue(rs.next());
			assertEquals(1, rs.getInt("id"));

			assertTrue(rs.next());
			assertEquals(2, rs.getInt("id"));

			assertTrue(rs.next());
			assertEquals(3, rs.getInt("id"));

			assertFalse(rs.next());
		}

		executeIgnoreErrors("DROP TABLE IF EXISTS " + TABLE);
	}

	@Test
	void testResultSetGetByColumnIndex() throws SQLException {
		String sql = "SELECT 'Alice' as name, 30 as age";

		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
			assertTrue(rs.next());
			assertEquals("Alice", rs.getString(1));
			assertEquals(30, rs.getInt(2));
		}
	}

	@Test
	void testResultSetGetByColumnName() throws SQLException {
		String sql = "SELECT 'Bob' as name, 25 as age";

		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
			assertTrue(rs.next());
			assertEquals("Bob", rs.getString("name"));
			assertEquals(25, rs.getInt("age"));
		}
	}

	@Test
	void testResultSetWasNull() throws SQLException {
		String sql = "SELECT NULL as null_col, 'not null' as str_col";

		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
			assertTrue(rs.next());

			assertNull(rs.getString("null_col"));
			assertTrue(rs.wasNull());

			assertEquals("not null", rs.getString("str_col"));
			assertFalse(rs.wasNull());
		}
	}

	@Test
	void testResultSetMetaData() throws SQLException {
		String sql = "SELECT 1 as id, 'test' as name, 3.14 as value";

		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
			ResultSetMetaData metaData = rs.getMetaData();

			assertEquals(3, metaData.getColumnCount());
			assertEquals("id", metaData.getColumnName(1));
			assertEquals("name", metaData.getColumnName(2));
			assertEquals("value", metaData.getColumnName(3));
		}
	}

	@Test
	void testResultSetClose() throws SQLException {
		Statement stmt = connection.createStatement();
		ResultSet rs = stmt.executeQuery("SELECT 1");

		assertFalse(rs.isClosed());

		rs.close();

		assertTrue(rs.isClosed());
		assertThrows(SQLException.class, rs::next);

		stmt.close();
	}

	@Test
	void testStatementCloseClosesResultSet() throws SQLException {
		Statement stmt = connection.createStatement();
		ResultSet rs = stmt.executeQuery("SELECT 1");

		assertFalse(rs.isClosed());

		stmt.close();

		assertTrue(rs.isClosed());
		assertTrue(stmt.isClosed());
	}

	@Test
	void testResultSetType() throws SQLException {
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery("SELECT 1")) {
			assertEquals(ResultSet.TYPE_FORWARD_ONLY, rs.getType());
		}
	}

	@Test
	void testResultSetConcurrency() throws SQLException {
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery("SELECT 1")) {
			assertEquals(ResultSet.CONCUR_READ_ONLY, rs.getConcurrency());
		}
	}

	@Test
	void testResultSetFetchDirection() throws SQLException {
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery("SELECT 1")) {
			assertEquals(ResultSet.FETCH_FORWARD, rs.getFetchDirection());
		}
	}

	@Test
	void testResultSetFindColumn() throws SQLException {
		String sql = "SELECT 1 as id, 'test' as name";

		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
			assertEquals(1, rs.findColumn("id"));
			assertEquals(2, rs.findColumn("name"));
		}
	}

	@Test
	void testResultSetFindColumnCaseInsensitive() throws SQLException {
		String sql = "SELECT 1 as MyColumn";

		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
			assertEquals(1, rs.findColumn("mycolumn"));
			assertEquals(1, rs.findColumn("MYCOLUMN"));
			assertEquals(1, rs.findColumn("MyColumn"));
		}
	}

	@Test
	void testResultSetInvalidColumnName() throws SQLException {
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery("SELECT 1 as id")) {
			assertTrue(rs.next());
			assertThrows(SQLException.class, () -> rs.getString("nonexistent"));
		}
	}

	@Test
	void testResultSetInvalidColumnIndex() throws SQLException {
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery("SELECT 1")) {
			assertTrue(rs.next());
			assertThrows(SQLException.class, () -> rs.getString(0));
			assertThrows(SQLException.class, () -> rs.getString(2));
		}
	}

	@Test
	void testResultSetBeforeFirst() throws SQLException {
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery("SELECT 1")) {
			assertThrows(SQLException.class, () -> rs.getString(1));
		}
	}

	@Test
	void testResultSetAfterLast() throws SQLException {
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery("SELECT 1")) {
			assertTrue(rs.next());
			assertFalse(rs.next());

			assertThrows(SQLException.class, () -> rs.getString(1));
		}
	}

	@Test
	void testMultipleResultSets() throws SQLException {
		Statement stmt = connection.createStatement();
		ResultSet rs1 = stmt.executeQuery("SELECT 1");
		ResultSet rs2 = stmt.executeQuery("SELECT 2");

		assertTrue(rs1.isClosed());
		assertFalse(rs2.isClosed());

		rs2.close();
		stmt.close();
	}

	@Test
	void testResultSetGetStatement() throws SQLException {
		Statement stmt = connection.createStatement();
		ResultSet rs = stmt.executeQuery("SELECT 1");

		assertEquals(stmt, rs.getStatement());

		rs.close();
		stmt.close();
	}

	@Test
	void testResultSetWarnings() throws SQLException {
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery("SELECT 1")) {
			assertNull(rs.getWarnings());

			rs.clearWarnings();

			assertNull(rs.getWarnings());
		}
	}
}

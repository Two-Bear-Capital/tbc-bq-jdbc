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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Calendar;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Advanced ResultSet accessors against real BigQuery.
 *
 * <p>
 * Port of {@code ResultSetAdvancedTest} (issue #118). Five of its tests could
 * not fail: {@code getBytes}, {@code getInt}-from-string,
 * {@code getDouble}-from-string and {@code getBoolean}-from-integer each
 * wrapped the call in try/catch and logged, and
 * {@code getBigDecimal(col, scale)} logged whichever scale came back. Three
 * more asserted only {@code assertNotNull} on Calendar-based getters, which
 * could not distinguish a correct instant from a wrong one — and those were
 * wrong, per #121.
 *
 * <p>
 * The Calendar tests here assert concrete instants, which is only meaningful
 * now that #121 is fixed. {@code getBoolean} on an INT64 column is covered
 * against #129, since it currently throws an unchecked exception.
 *
 * @since 1.0.115
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RealResultSetAdvancedTest extends AbstractRealBigQueryIntegrationTest {

	private static final String TEST_TABLE = tableName("rs_advanced");

	@BeforeAll
	void createFixture() throws SQLException {
		createSharedTestTable(TEST_TABLE);
	}

	@AfterAll
	void dropFixture() {
		dropSharedTestTable(TEST_TABLE);
	}

	/**
	 * Runs a query and hands the caller a ResultSet positioned on the first row.
	 */
	private void firstRow(String sql, ResultSetAssertion body) throws SQLException {
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
			assertTrue(rs.next(), "Query should return a row: " + sql);
			body.accept(rs);
		}
	}

	@FunctionalInterface
	private interface ResultSetAssertion {
		void accept(ResultSet rs) throws SQLException;
	}

	// ── Type-specific getters ─────────────────────────────────────────────────

	@Test
	void testGetByte() throws SQLException {
		firstRow("SELECT age FROM " + TEST_TABLE + " WHERE name = 'Bob'", rs -> {
			assertEquals((byte) 25, rs.getByte("age"));
			assertFalse(rs.wasNull(), "Should not be null");
		});
	}

	@Test
	void testGetByteByIndex() throws SQLException {
		firstRow("SELECT age FROM " + TEST_TABLE + " WHERE name = 'Bob'", rs -> assertEquals((byte) 25, rs.getByte(1)));
	}

	@Test
	void testGetShort() throws SQLException {
		firstRow("SELECT age FROM " + TEST_TABLE + " WHERE name = 'Alice'",
				rs -> assertEquals((short) 30, rs.getShort("age")));
	}

	@Test
	void testGetFloat() throws SQLException {
		firstRow("SELECT salary FROM " + TEST_TABLE + " WHERE name = 'Alice'",
				rs -> assertEquals(75000.50f, rs.getFloat("salary"), 0.01f));
	}

	@Test
	void testGetFloatByIndex() throws SQLException {
		firstRow("SELECT salary FROM " + TEST_TABLE + " WHERE name = 'Bob'",
				rs -> assertEquals(60000.00f, rs.getFloat(1), 0.01f));
	}

	@Test
	void testGetByteOutOfRangeThrows() throws SQLException {
		// getByte routes through the range-checked toIntegralLong, unlike the getters
		// in #129 — assert that the guard actually reports SQLException.
		firstRow("SELECT 300 AS too_big",
				rs -> assertThrows(SQLException.class, () -> rs.getByte(1), "300 does not fit in a byte"));
	}

	// ── Calendar-based temporal getters ───────────────────────────────────────
	// Emulator tier asserted only assertNotNull, which cannot distinguish a
	// correct instant from one shifted the wrong way — and it was shifted the
	// wrong way (#121). These assert the value.

	@Test
	void testGetTimestampWithCalendar() throws SQLException {
		Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
		firstRow("SELECT TIMESTAMP '2024-01-15 00:00:00+00' AS ts", rs -> {
			Timestamp ts = rs.getTimestamp("ts", utc);
			assertNotNull(ts, "Timestamp should not be null");
			// Read through a UTC Calendar, the wall clock is the stored UTC wall clock.
			assertEquals(Timestamp.valueOf("2024-01-15 00:00:00"), ts,
					"A UTC Calendar should render the stored UTC wall clock");
		});
	}

	@Test
	void testGetTimestampWithCalendarShiftsByZone() throws SQLException {
		// The same instant read through two zones must differ by their offset.
		// Structurally impossible to assert on the emulator, which never returned a
		// usable timestamp here at all.
		Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
		Calendar tokyo = Calendar.getInstance(TimeZone.getTimeZone("Asia/Tokyo"));
		firstRow("SELECT TIMESTAMP '2024-01-15 00:00:00+00' AS ts", rs -> {
			long inUtc = rs.getTimestamp("ts", utc).getTime();
			long inTokyo = rs.getTimestamp("ts", tokyo).getTime();
			assertEquals(9L * 60 * 60 * 1000, inTokyo - inUtc,
					"Asia/Tokyo renders the same instant 9 hours ahead of UTC");
		});
	}

	@Test
	void testGetDateWithCalendar() throws SQLException {
		// DATE is zoneless, so "a UTC Calendar leaves it unchanged" is only true when
		// the JVM default is already UTC — which is exactly the vacuous-on-CI shape
		// this port exists to remove. Assert the zoneless value directly, and use the
		// JVM's own zone for the Calendar overload so the identity holds anywhere.
		firstRow("SELECT created_date FROM " + TEST_TABLE + " WHERE name = 'Bob'", rs -> {
			assertEquals(Date.valueOf("2024-02-20"), rs.getDate("created_date"), "Bob's created_date");
			assertEquals(rs.getDate("created_date"), rs.getDate("created_date", Calendar.getInstance()),
					"the default-zone Calendar must be a no-op");
		});
	}

	@Test
	void testGetTimeWithCalendar() throws SQLException {
		// Same reasoning as the DATE case above: TIME carries no zone.
		firstRow("SELECT TIME '10:30:00' AS time_value", rs -> {
			assertEquals(Time.valueOf("10:30:00"), rs.getTime(1), "TIME should read as its wall clock");
			assertEquals(rs.getTime(1), rs.getTime(1, Calendar.getInstance()),
					"the default-zone Calendar must be a no-op");
		});
	}

	// ── Binary data ───────────────────────────────────────────────────────────

	@Test
	void testGetBytes() throws SQLException {
		// Emulator tier: no assertion at all in the failure branch.
		firstRow("SELECT CAST('Hello' AS BYTES) AS binary_data", rs -> {
			byte[] data = rs.getBytes(1);
			assertNotNull(data, "Bytes should not be null");
			assertArrayEquals("Hello".getBytes(StandardCharsets.UTF_8), data, "BYTES should round-trip exactly");
		});
	}

	@Test
	void testGetBytesNull() throws SQLException {
		firstRow("SELECT CAST(NULL AS BYTES) AS binary_data", rs -> {
			assertNull(rs.getBytes(1), "NULL BYTES should return null");
			assertTrue(rs.wasNull(), "wasNull should be true");
		});
	}

	// ── BigDecimal ────────────────────────────────────────────────────────────

	@Test
	void testGetBigDecimalIgnoresTheScaleArgument() throws SQLException {
		// The emulator tier logged whichever scale came back. It is not uncertain:
		// BQResultSet.getBigDecimal(int, int) returns value.getNumericValue() and
		// never looks at `scale`. Pinned so a change is visible. The two-arg method
		// is deprecated in JDBC, so ignoring the scale is defensible — but it should
		// be stated rather than discovered.
		firstRow("SELECT salary FROM " + TEST_TABLE + " WHERE name = 'Alice'", rs -> {
			@SuppressWarnings("deprecation")
			BigDecimal scaled = rs.getBigDecimal("salary", 2);
			assertNotNull(scaled);
			assertEquals(0, new BigDecimal("75000.50").compareTo(scaled), "Value should be correct");
			assertEquals(rs.getBigDecimal("salary").scale(), scaled.scale(),
					"the scale argument is ignored, so the result matches the no-arg call");
		});
	}

	@Test
	void testGetBigDecimalWithoutScale() throws SQLException {
		firstRow("SELECT salary FROM " + TEST_TABLE + " WHERE name = 'Bob'", rs -> {
			BigDecimal value = rs.getBigDecimal("salary");
			assertNotNull(value);
			assertEquals(0, new BigDecimal("60000").compareTo(value));
		});
	}

	// ── Type coercion ─────────────────────────────────────────────────────────

	@Test
	void testGetStringFromInteger() throws SQLException {
		firstRow("SELECT age FROM " + TEST_TABLE + " WHERE name = 'Alice'",
				rs -> assertEquals("30", rs.getString("age")));
	}

	@Test
	void testGetIntFromString() throws SQLException {
		// Emulator tier logged and passed on SQLException.
		firstRow("SELECT '42' AS str_number", rs -> assertEquals(42, rs.getInt(1), "STRING '42' should read as int"));
	}

	@Test
	void testGetDoubleFromString() throws SQLException {
		// Emulator tier logged and passed on SQLException.
		firstRow("SELECT '3.14159' AS str_decimal",
				rs -> assertEquals(3.14159, rs.getDouble(1), 0.00001, "STRING should read as double"));
	}

	@Test
	void testGetBooleanFromBooleanColumn() throws SQLException {
		firstRow("SELECT is_active FROM " + TEST_TABLE + " WHERE name = 'Alice'", rs -> {
			assertTrue(rs.getBoolean("is_active"), "Alice is active");
			assertFalse(rs.wasNull());
		});
	}

	@Test
	@Disabled("#129 — getBoolean on INT64 throws IllegalStateException, not SQLException")
	void testGetBooleanFromIntegerReportsSqlException() throws SQLException {
		// The emulator tier caught `IllegalStateException | SQLException` by name and
		// logged, which normalised a JDBC contract violation into a pass. Whether the
		// driver should coerce 1/0 or reject the conversion is open (#129), but it
		// must not throw unchecked.
		firstRow("SELECT 1 AS true_val", rs -> assertThrows(SQLException.class, () -> rs.getBoolean("true_val"),
				"A failed conversion must be reported as SQLException"));
	}

	// ── NULL handling ─────────────────────────────────────────────────────────

	@Test
	void testGetByteNull() throws SQLException {
		firstRow("SELECT CAST(NULL AS INT64) AS null_value", rs -> {
			assertEquals(0, rs.getByte(1), "NULL byte should return 0");
			assertTrue(rs.wasNull());
		});
	}

	@Test
	void testGetShortNull() throws SQLException {
		firstRow("SELECT CAST(NULL AS INT64) AS null_value", rs -> {
			assertEquals(0, rs.getShort(1), "NULL short should return 0");
			assertTrue(rs.wasNull());
		});
	}

	@Test
	void testGetFloatNull() throws SQLException {
		firstRow("SELECT CAST(NULL AS FLOAT64) AS null_value", rs -> {
			assertEquals(0.0f, rs.getFloat(1), 0.001f, "NULL float should return 0.0");
			assertTrue(rs.wasNull());
		});
	}

	@Test
	void testGetBigDecimalNull() throws SQLException {
		firstRow("SELECT CAST(NULL AS NUMERIC) AS null_value", rs -> {
			assertNull(rs.getBigDecimal(1), "NULL BigDecimal should return null");
			assertTrue(rs.wasNull());
		});
	}

	// ── Numeric edges ─────────────────────────────────────────────────────────

	@Test
	void testGetLargeInteger() throws SQLException {
		firstRow("SELECT 9223372036854775807 AS max_int64", rs -> assertEquals(Long.MAX_VALUE, rs.getLong(1)));
	}

	@Test
	void testGetSmallInteger() throws SQLException {
		firstRow("SELECT -9223372036854775808 AS min_int64", rs -> assertEquals(Long.MIN_VALUE, rs.getLong(1)));
	}

	@Test
	void testGetVeryLargeDouble() throws SQLException {
		firstRow("SELECT 1.7976931348623157e+308 AS large_double",
				rs -> assertEquals(Double.MAX_VALUE, rs.getDouble(1), 0.0, "Should round-trip Double.MAX_VALUE"));
	}

	@Test
	void testGetEmptyString() throws SQLException {
		firstRow("SELECT '' AS empty_string", rs -> {
			assertEquals("", rs.getString(1), "Should be an empty string");
			assertFalse(rs.wasNull(), "An empty string is not SQL NULL");
		});
	}

	// ── Error handling ────────────────────────────────────────────────────────

	@Test
	void testGetInvalidColumnIndex() throws SQLException {
		firstRow("SELECT name FROM " + TEST_TABLE + " LIMIT 1", rs -> {
			assertThrows(SQLException.class, () -> rs.getString(0), "Index 0 should throw");
			assertThrows(SQLException.class, () -> rs.getString(10), "Index past the last column should throw");
		});
	}

	@Test
	void testGetInvalidColumnName() throws SQLException {
		firstRow("SELECT name FROM " + TEST_TABLE + " LIMIT 1",
				rs -> assertThrows(SQLException.class, () -> rs.getString("nonexistent_column")));
	}

	@Test
	void testGetBeforeFirst() throws SQLException {
		try (Statement stmt = connection.createStatement();
				ResultSet rs = stmt.executeQuery("SELECT name FROM " + TEST_TABLE + " LIMIT 1")) {
			assertThrows(SQLException.class, () -> rs.getString("name"), "Reading before next() should throw");
		}
	}

	@Test
	void testGetAfterLast() throws SQLException {
		try (Statement stmt = connection.createStatement();
				ResultSet rs = stmt.executeQuery("SELECT name FROM " + TEST_TABLE + " WHERE name = 'Alice'")) {
			assertTrue(rs.next(), "Should have the first row");
			assertFalse(rs.next(), "Should have no second row");
			assertThrows(SQLException.class, () -> rs.getString("name"), "Reading past the last row should throw");
		}
	}

	@Test
	void testGetOnClosedResultSet() throws SQLException {
		try (Statement stmt = connection.createStatement()) {
			ResultSet rs = stmt.executeQuery("SELECT name FROM " + TEST_TABLE + " LIMIT 1");
			assertTrue(rs.next(), "Should have a row");
			rs.close();

			assertThrows(SQLException.class, () -> rs.getString("name"), "Reading a closed ResultSet should throw");
			assertThrows(SQLException.class, rs::next, "next() on a closed ResultSet should throw");
		}
	}

	// ── Metadata ──────────────────────────────────────────────────────────────

	@Test
	void testGetColumnCount() throws SQLException {
		try (Statement stmt = connection.createStatement();
				ResultSet rs = stmt.executeQuery("SELECT name, age, salary FROM " + TEST_TABLE + " LIMIT 1")) {
			assertEquals(3, rs.getMetaData().getColumnCount(), "Should have 3 columns");
		}
	}

	@Test
	void testGetColumnLabels() throws SQLException {
		try (Statement stmt = connection.createStatement();
				ResultSet rs = stmt.executeQuery(
						"SELECT name AS person_name, age AS person_age FROM " + TEST_TABLE + " LIMIT 1")) {
			ResultSetMetaData rsmd = rs.getMetaData();
			assertEquals("person_name", rsmd.getColumnLabel(1));
			assertEquals("person_age", rsmd.getColumnLabel(2));
		}
	}

	@Test
	void testGetColumnTypes() throws SQLException {
		try (Statement stmt = connection.createStatement();
				ResultSet rs = stmt
						.executeQuery("SELECT name, age, salary, is_active FROM " + TEST_TABLE + " LIMIT 1")) {
			ResultSetMetaData rsmd = rs.getMetaData();
			assertEquals(Types.VARCHAR, rsmd.getColumnType(1), "name should be VARCHAR");
			assertEquals(Types.BIGINT, rsmd.getColumnType(2), "age should be BIGINT");
			assertEquals(Types.DOUBLE, rsmd.getColumnType(3), "salary should be DOUBLE");
			assertEquals(Types.BOOLEAN, rsmd.getColumnType(4), "is_active should be BOOLEAN");
		}
	}
}

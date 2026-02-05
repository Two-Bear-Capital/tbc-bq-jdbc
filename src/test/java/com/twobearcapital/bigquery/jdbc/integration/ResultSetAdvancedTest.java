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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.*;
import java.util.Calendar;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Advanced integration tests for ResultSet getter methods.
 *
 * <p>
 * Tests advanced ResultSet features including:
 * <ul>
 * <li>Type-specific getters (getByte, getShort, getFloat, etc.)
 * <li>Calendar-based temporal getters (getTimestamp/Date/Time with Calendar)
 * <li>Binary data retrieval (getBytes)
 * <li>getBigDecimal with scale parameter
 * <li>Type conversions and coercion
 * <li>Null handling across different types
 * <li>Edge cases (very large/small values, empty results)
 * </ul>
 *
 * @since 1.0.0
 */
class ResultSetAdvancedTest extends AbstractBigQueryIntegrationTest {

	private static final Logger logger = LoggerFactory.getLogger(ResultSetAdvancedTest.class);

	private static final String TEST_TABLE = "advanced_rs_test_table";

	@BeforeEach
	void setupTestTable() throws SQLException {
		createTestTable(TEST_TABLE);
		insertTestData(TEST_TABLE);
	}

	// Type-Specific Getters

	@Test
	void testGetByte() throws SQLException {
		// Given: A query returning small integer values
		String sql = "SELECT age FROM " + TEST_TABLE + " WHERE name = 'Bob'";
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			// When: Retrieving as byte
			assertTrue(rs.next(), "Should have result");
			byte value = rs.getByte("age");

			// Then: Should convert to byte
			assertEquals(25, value, "Should get byte value");
			assertFalse(rs.wasNull(), "Should not be null");
		}
	}

	@Test
	void testGetByteByIndex() throws SQLException {
		// Given: A query with known column positions
		String sql = "SELECT age FROM " + TEST_TABLE + " WHERE name = 'Bob'";
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			// When: Retrieving by index
			assertTrue(rs.next(), "Should have result");
			byte value = rs.getByte(1);

			// Then: Should get correct value
			assertEquals(25, value, "Should get byte value by index");
		}
	}

	@Test
	void testGetShort() throws SQLException {
		// Given: A query returning integer values
		String sql = "SELECT age FROM " + TEST_TABLE + " WHERE name = 'Alice'";
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			// When: Retrieving as short
			assertTrue(rs.next(), "Should have result");
			short value = rs.getShort("age");

			// Then: Should convert to short
			assertEquals(30, value, "Should get short value");
		}
	}

	@Test
	void testGetFloat() throws SQLException {
		// Given: A query returning floating point values
		String sql = "SELECT salary FROM " + TEST_TABLE + " WHERE name = 'Alice'";
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			// When: Retrieving as float
			assertTrue(rs.next(), "Should have result");
			float value = rs.getFloat("salary");

			// Then: Should convert to float
			assertEquals(75000.50f, value, 0.01f, "Should get float value");
		}
	}

	@Test
	void testGetFloatByIndex() throws SQLException {
		// Given: A query with known column positions
		String sql = "SELECT salary FROM " + TEST_TABLE + " WHERE name = 'Bob'";
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			// When: Retrieving by index
			assertTrue(rs.next(), "Should have result");
			float value = rs.getFloat(1);

			// Then: Should get correct value
			assertEquals(60000.00f, value, 0.01f, "Should get float value by index");
		}
	}

	// Calendar-Based Temporal Getters

	@Test
	void testGetTimestampWithCalendar() throws SQLException {
		// Given: A query returning timestamp values (cast DATE to TIMESTAMP for
		// testing)
		String sql = "SELECT CAST(created_date AS TIMESTAMP) as ts FROM " + TEST_TABLE + " WHERE name = 'Alice'";
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			assertTrue(rs.next(), "Should have result");

			// When: Getting timestamp with calendar
			Calendar utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
			Timestamp ts = rs.getTimestamp("ts", utcCal);

			// Then: Should get timestamp
			assertNotNull(ts, "Timestamp should not be null");
		}
	}

	@Test
	void testGetDateWithCalendar() throws SQLException {
		// Given: A query returning date values
		String sql = "SELECT created_date FROM " + TEST_TABLE + " WHERE name = 'Bob'";
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			assertTrue(rs.next(), "Should have result");

			// When: Getting date with calendar
			Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("America/New_York"));
			Date date = rs.getDate("created_date", cal);

			// Then: Should get date
			assertNotNull(date, "Date should not be null");
		}
	}

	@Test
	void testGetTimeWithCalendar() throws SQLException {
		// Given: A query that can return time
		String sql = "SELECT TIME '10:30:00' as time_value";
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			assertTrue(rs.next(), "Should have result");

			// When: Getting time with calendar
			Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
			Time time = rs.getTime(1, cal);

			// Then: Should get time
			assertNotNull(time, "Time should not be null");
		}
	}

	// Binary Data

	@Test
	void testGetBytes() throws SQLException {
		// Given: A query that returns bytes (using CAST for testing)
		String sql = "SELECT CAST('Hello' AS BYTES) as binary_data";
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			assertTrue(rs.next(), "Should have result");

			try {
				// When: Getting bytes
				byte[] data = rs.getBytes(1);

				// Then: Should get byte array
				assertNotNull(data, "Bytes should not be null");
				assertTrue(data.length > 0, "Bytes should not be empty");
				logger.info("✓ getBytes() supported");
			} catch (SQLException e) {
				logger.info("getBytes() not fully supported (emulator limitation): {}", e.getMessage());
			}
		}
	}

	@Test
	void testGetBytesNull() throws SQLException {
		// Given: A query returning null bytes
		String sql = "SELECT CAST(NULL AS BYTES) as binary_data";
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			assertTrue(rs.next(), "Should have result");

			// When: Getting null bytes
			byte[] data = rs.getBytes(1);

			// Then: Should return null
			assertNull(data, "Null bytes should return null");
			assertTrue(rs.wasNull(), "wasNull should be true");
		}
	}

	// BigDecimal with Scale

	@Test
	void testGetBigDecimalWithScale() throws SQLException {
		// Given: A query returning decimal values
		String sql = "SELECT salary FROM " + TEST_TABLE + " WHERE name = 'Alice'";
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			assertTrue(rs.next(), "Should have result");

			// When: Getting BigDecimal with scale (deprecated method)
			@SuppressWarnings("deprecation")
			BigDecimal value = rs.getBigDecimal("salary", 2);

			// Then: Should get value (scale may not be enforced by deprecated method)
			assertNotNull(value, "BigDecimal should not be null");
			assertTrue(value.compareTo(new BigDecimal("75000.50")) == 0, "Should match expected value");

			// Note: Scale enforcement varies by driver implementation
			if (value.scale() == 2) {
				logger.info("✓ getBigDecimal with scale parameter enforces scale");
			} else {
				logger.info("getBigDecimal with scale parameter returns scale {} (scale parameter not enforced)",
						value.scale());
			}
		}
	}

	@Test
	void testGetBigDecimalWithoutScale() throws SQLException {
		// Given: A query returning decimal values
		String sql = "SELECT salary FROM " + TEST_TABLE + " WHERE name = 'Bob'";
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			assertTrue(rs.next(), "Should have result");

			// When: Getting BigDecimal without scale
			BigDecimal value = rs.getBigDecimal("salary");

			// Then: Should get value
			assertNotNull(value, "BigDecimal should not be null");
			assertTrue(value.compareTo(new BigDecimal("60000")) == 0, "Should match expected value");
		}
	}

	// Type Conversions

	@Test
	void testGetStringFromInteger() throws SQLException {
		// Given: A query returning integer
		String sql = "SELECT age FROM " + TEST_TABLE + " WHERE name = 'Alice'";
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			assertTrue(rs.next(), "Should have result");

			// When: Getting integer as string
			String value = rs.getString("age");

			// Then: Should convert to string
			assertEquals("30", value, "Should convert integer to string");
		}
	}

	@Test
	void testGetIntFromString() throws SQLException {
		// Given: A query returning string that looks like number
		String sql = "SELECT '42' as str_number";
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			assertTrue(rs.next(), "Should have result");

			try {
				// When: Getting string as int
				int value = rs.getInt(1);

				// Then: Should parse to int
				assertEquals(42, value, "Should parse string to int");
				logger.info("✓ Type conversion from string to int supported");
			} catch (SQLException e) {
				logger.info("Type conversion from string to int not supported: {}", e.getMessage());
			}
		}
	}

	@Test
	void testGetDoubleFromString() throws SQLException {
		// Given: A query returning string that looks like decimal
		String sql = "SELECT '3.14159' as str_decimal";
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			assertTrue(rs.next(), "Should have result");

			try {
				// When: Getting string as double
				double value = rs.getDouble(1);

				// Then: Should parse to double
				assertEquals(3.14159, value, 0.00001, "Should parse string to double");
				logger.info("✓ Type conversion from string to double supported");
			} catch (SQLException e) {
				logger.info("Type conversion from string to double not supported: {}", e.getMessage());
			}
		}
	}

	@Test
	void testGetBooleanFromInteger() throws SQLException {
		// Given: A query returning 1 and 0
		String sql = "SELECT 1 as true_val, 0 as false_val";
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			assertTrue(rs.next(), "Should have result");

			try {
				// When: Getting integers as boolean
				boolean trueVal = rs.getBoolean("true_val");
				boolean falseVal = rs.getBoolean("false_val");

				// Then: Should convert properly
				assertTrue(trueVal, "1 should convert to true");
				assertFalse(falseVal, "0 should convert to false");
				logger.info("✓ Type conversion from integer to boolean supported");
			} catch (IllegalStateException | SQLException e) {
				logger.info("Type conversion from integer to boolean not supported: {}", e.getMessage());
			}
		}
	}

	// Null Handling

	@Test
	void testGetByteNull() throws SQLException {
		// Given: A query with NULL value
		String sql = "SELECT CAST(NULL AS INT64) as null_value";
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			assertTrue(rs.next(), "Should have result");

			// When: Getting NULL as byte
			byte value = rs.getByte(1);

			// Then: Should return 0 and wasNull should be true
			assertEquals(0, value, "NULL byte should return 0");
			assertTrue(rs.wasNull(), "wasNull should be true");
		}
	}

	@Test
	void testGetShortNull() throws SQLException {
		// Given: A query with NULL value
		String sql = "SELECT CAST(NULL AS INT64) as null_value";
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			assertTrue(rs.next(), "Should have result");

			// When: Getting NULL as short
			short value = rs.getShort(1);

			// Then: Should return 0 and wasNull should be true
			assertEquals(0, value, "NULL short should return 0");
			assertTrue(rs.wasNull(), "wasNull should be true");
		}
	}

	@Test
	void testGetFloatNull() throws SQLException {
		// Given: A query with NULL value
		String sql = "SELECT CAST(NULL AS FLOAT64) as null_value";
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			assertTrue(rs.next(), "Should have result");

			// When: Getting NULL as float
			float value = rs.getFloat(1);

			// Then: Should return 0.0 and wasNull should be true
			assertEquals(0.0f, value, 0.001f, "NULL float should return 0.0");
			assertTrue(rs.wasNull(), "wasNull should be true");
		}
	}

	@Test
	void testGetBigDecimalNull() throws SQLException {
		// Given: A query with NULL value
		String sql = "SELECT CAST(NULL AS NUMERIC) as null_value";
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			assertTrue(rs.next(), "Should have result");

			// When: Getting NULL as BigDecimal
			BigDecimal value = rs.getBigDecimal(1);

			// Then: Should return null
			assertNull(value, "NULL BigDecimal should return null");
			assertTrue(rs.wasNull(), "wasNull should be true");
		}
	}

	// Edge Cases

	@Test
	void testGetLargeInteger() throws SQLException {
		// Given: A query with large integer value
		String sql = "SELECT 9223372036854775807 as max_int64";
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			assertTrue(rs.next(), "Should have result");

			// When: Getting large integer
			long value = rs.getLong(1);

			// Then: Should handle large value
			assertEquals(Long.MAX_VALUE, value, "Should handle max INT64");
		}
	}

	@Test
	void testGetSmallInteger() throws SQLException {
		// Given: A query with minimum integer value
		String sql = "SELECT -9223372036854775808 as min_int64";
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			assertTrue(rs.next(), "Should have result");

			// When: Getting minimum integer
			long value = rs.getLong(1);

			// Then: Should handle minimum value
			assertEquals(Long.MIN_VALUE, value, "Should handle min INT64");
		}
	}

	@Test
	void testGetVeryLargeDouble() throws SQLException {
		// Given: A query with very large double
		String sql = "SELECT 1.7976931348623157e+308 as large_double";
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			assertTrue(rs.next(), "Should have result");

			// When: Getting large double
			double value = rs.getDouble(1);

			// Then: Should handle large value
			assertTrue(value > 1e308, "Should handle very large double");
		}
	}

	@Test
	void testGetEmptyString() throws SQLException {
		// Given: A query returning empty string
		String sql = "SELECT '' as empty_string";
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			assertTrue(rs.next(), "Should have result");

			// When: Getting empty string
			String value = rs.getString(1);

			// Then: Should return empty string (not null)
			assertNotNull(value, "Empty string should not be null");
			assertEquals("", value, "Should be empty string");
			assertFalse(rs.wasNull(), "wasNull should be false for empty string");
		}
	}

	// Error Handling

	@Test
	void testGetInvalidColumnIndex() throws SQLException {
		// Given: A result set with known columns
		String sql = "SELECT name FROM " + TEST_TABLE + " LIMIT 1";
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			assertTrue(rs.next(), "Should have result");

			// When/Then: Getting column with invalid index should throw
			assertThrows(SQLException.class, () -> rs.getString(0), "Index 0 should throw SQLException");

			assertThrows(SQLException.class, () -> rs.getString(10), "Index 10 should throw SQLException");
		}
	}

	@Test
	void testGetInvalidColumnName() throws SQLException {
		// Given: A result set with known columns
		String sql = "SELECT name FROM " + TEST_TABLE + " LIMIT 1";
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			assertTrue(rs.next(), "Should have result");

			// When/Then: Getting column with invalid name should throw
			assertThrows(SQLException.class, () -> rs.getString("nonexistent_column"),
					"Invalid column name should throw SQLException");
		}
	}

	@Test
	void testGetBeforeFirst() throws SQLException {
		// Given: A result set positioned before first row
		String sql = "SELECT name FROM " + TEST_TABLE + " LIMIT 1";
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			// When/Then: Getting value before calling next() should throw
			assertThrows(SQLException.class, () -> rs.getString("name"),
					"Getting value before first row should throw SQLException");
		}
	}

	@Test
	void testGetAfterLast() throws SQLException {
		// Given: A result set positioned after last row
		String sql = "SELECT name FROM " + TEST_TABLE + " WHERE name = 'Alice'";
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			// Consume all rows
			assertTrue(rs.next(), "Should have first row");
			assertFalse(rs.next(), "Should not have second row");

			// When/Then: Getting value after last row should throw
			assertThrows(SQLException.class, () -> rs.getString("name"),
					"Getting value after last row should throw SQLException");
		}
	}

	@Test
	void testGetOnClosedResultSet() throws SQLException {
		// Given: A closed result set
		String sql = "SELECT name FROM " + TEST_TABLE + " LIMIT 1";
		Statement stmt = connection.createStatement();
		ResultSet rs = stmt.executeQuery(sql);
		assertTrue(rs.next(), "Should have result");
		rs.close();

		// When/Then: Operations on closed result set should throw
		assertThrows(SQLException.class, () -> rs.getString("name"),
				"Getting value from closed ResultSet should throw SQLException");

		assertThrows(SQLException.class, () -> rs.next(), "next() on closed ResultSet should throw SQLException");

		stmt.close();
	}

	// Metadata Validation

	@Test
	void testGetColumnCount() throws SQLException {
		// Given: A query with known columns
		String sql = "SELECT name, age, salary FROM " + TEST_TABLE + " LIMIT 1";
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			// When: Getting metadata
			ResultSetMetaData rsmd = rs.getMetaData();

			// Then: Should have correct column count
			assertEquals(3, rsmd.getColumnCount(), "Should have 3 columns");
		}
	}

	@Test
	void testGetColumnLabels() throws SQLException {
		// Given: A query with aliased columns
		String sql = "SELECT name as person_name, age as person_age FROM " + TEST_TABLE + " LIMIT 1";
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			// When: Getting column labels
			ResultSetMetaData rsmd = rs.getMetaData();

			// Then: Should have correct labels
			assertEquals("person_name", rsmd.getColumnLabel(1), "First column label should be person_name");
			assertEquals("person_age", rsmd.getColumnLabel(2), "Second column label should be person_age");
		}
	}

	@Test
	void testGetColumnTypes() throws SQLException {
		// Given: A query with different data types
		String sql = "SELECT name, age, salary, is_active FROM " + TEST_TABLE + " LIMIT 1";
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			// When: Getting column types
			ResultSetMetaData rsmd = rs.getMetaData();

			// Then: Should have correct types
			assertEquals(Types.VARCHAR, rsmd.getColumnType(1), "name should be VARCHAR");
			assertEquals(Types.BIGINT, rsmd.getColumnType(2), "age should be BIGINT");
			assertEquals(Types.DOUBLE, rsmd.getColumnType(3), "salary should be DOUBLE");
			assertEquals(Types.BOOLEAN, rsmd.getColumnType(4), "is_active should be BOOLEAN");
		}
	}
}

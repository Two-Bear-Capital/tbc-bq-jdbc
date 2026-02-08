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
package com.tbc.bq.jdbc.integration;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for BigQuery to JDBC type mapping.
 *
 * @since 1.0.0
 */
class TypeMappingTest extends AbstractBigQueryIntegrationTest {

	@Test
	void testStringType() throws SQLException {
		// Given: A STRING value
		String sql = "SELECT 'Hello, World!' as str_value";

		// When: Querying
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			// Then: Should return as String
			assertTrue(rs.next());
			assertEquals("Hello, World!", rs.getString("str_value"));
			assertEquals("Hello, World!", rs.getObject("str_value", String.class));
		}
	}

	@Test
	void testInt64Type() throws SQLException {
		// Given: An INT64 value
		String sql = "SELECT 9223372036854775807 as big_int";

		// When: Querying
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			// Then: Should return as long
			assertTrue(rs.next());
			assertEquals(9223372036854775807L, rs.getLong("big_int"));
			assertEquals(9223372036854775807L, rs.getObject("big_int", Long.class));
		}
	}

	@Test
	void testFloat64Type() throws SQLException {
		// Given: A FLOAT64 value
		String sql = "SELECT 3.14159265359 as pi";

		// When: Querying
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			// Then: Should return as double
			assertTrue(rs.next());
			assertEquals(3.14159265359, rs.getDouble("pi"), 0.0001);
			assertEquals(3.14159265359, rs.getObject("pi", Double.class), 0.0001);
		}
	}

	@Test
	void testBoolType() throws SQLException {
		// Given: BOOL values
		String sql = "SELECT true as is_true, false as is_false";

		// When: Querying
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			// Then: Should return as boolean
			assertTrue(rs.next());
			assertTrue(rs.getBoolean("is_true"));
			assertFalse(rs.getBoolean("is_false"));
			assertEquals(Boolean.TRUE, rs.getObject("is_true", Boolean.class));
			assertEquals(Boolean.FALSE, rs.getObject("is_false", Boolean.class));
		}
	}

	@Test
	void testNumericType() throws SQLException {
		// Given: A NUMERIC value
		String sql = "SELECT NUMERIC '123.456' as num_value";

		// When: Querying
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			// Then: Should return as BigDecimal
			assertTrue(rs.next());
			BigDecimal expected = new BigDecimal("123.456");
			assertEquals(expected, rs.getBigDecimal("num_value"));
		}
	}

	@Test
	void testDateType() throws SQLException {
		// Given: A DATE value
		String sql = "SELECT DATE '2024-03-15' as date_value";

		// When: Querying
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			// Then: Should return as Date
			assertTrue(rs.next());
			Date expectedDate = Date.valueOf("2024-03-15");
			assertEquals(expectedDate, rs.getDate("date_value"));
		}
	}

	@Test
	void testTimeType() throws SQLException {
		// Given: A TIME value
		String sql = "SELECT TIME '14:30:45' as time_value";

		// When: Querying
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			// Then: Should return as Time
			assertTrue(rs.next());
			assertNotNull(rs.getTime("time_value"));
		}
	}

	@Test
	void testTimestampType() throws SQLException {
		// Given: A TIMESTAMP value
		String sql = "SELECT TIMESTAMP '2024-03-15 14:30:45.123456 UTC' as ts_value";

		// When: Querying
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			// Then: Should return as Timestamp
			assertTrue(rs.next());
			assertNotNull(rs.getTimestamp("ts_value"));
		}
	}

	@Test
	void testNullValues() throws SQLException {
		// Given: NULL values of different types
		String sql = "SELECT " + "CAST(NULL AS STRING) as null_string, " + "CAST(NULL AS INT64) as null_int, "
				+ "CAST(NULL AS FLOAT64) as null_float, " + "CAST(NULL AS BOOL) as null_bool, "
				+ "CAST(NULL AS DATE) as null_date";

		// When: Querying
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			// Then: All should be NULL
			assertTrue(rs.next());

			assertNull(rs.getString("null_string"));
			assertTrue(rs.wasNull());

			assertEquals(0, rs.getLong("null_int"));
			assertTrue(rs.wasNull());

			assertEquals(0.0, rs.getDouble("null_float"));
			assertTrue(rs.wasNull());

			assertFalse(rs.getBoolean("null_bool"));
			assertTrue(rs.wasNull());

			assertNull(rs.getDate("null_date"));
			assertTrue(rs.wasNull());
		}
	}

	@Test
	void testBytesType() throws SQLException {
		// Given: BYTES value
		String sql = "SELECT CAST('Hello' AS BYTES) as bytes_value";

		// When: Querying
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			// Then: Should return as byte array
			assertTrue(rs.next());
			byte[] bytes = rs.getBytes("bytes_value");
			assertNotNull(bytes);
			assertTrue(bytes.length > 0);
		}
	}

	@Test
	void testLargeNumbers() throws SQLException {
		// Given: Large numbers
		String sql = "SELECT " + "9223372036854775807 as max_int64, " + "-9223372036854775808 as min_int64, "
				+ "1.7976931348623157E308 as large_float";

		// When: Querying
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			// Then: Should handle large values
			assertTrue(rs.next());
			assertEquals(Long.MAX_VALUE, rs.getLong("max_int64"));
			assertEquals(Long.MIN_VALUE, rs.getLong("min_int64"));
			assertEquals(1.7976931348623157E308, rs.getDouble("large_float"), 1E290);
		}
	}

	@Test
	void testZeroValues() throws SQLException {
		// Given: Zero values
		String sql = "SELECT 0 as zero_int, 0.0 as zero_float, '' as empty_string";

		// When: Querying
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			// Then: Should handle zeros correctly
			assertTrue(rs.next());
			assertEquals(0, rs.getInt("zero_int"));
			assertEquals(0.0, rs.getDouble("zero_float"));
			assertEquals("", rs.getString("empty_string"));
		}
	}

	@Test
	void testNegativeNumbers() throws SQLException {
		// Given: Negative numbers
		String sql = "SELECT -42 as neg_int, -3.14 as neg_float";

		// When: Querying
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			// Then: Should handle negatives
			assertTrue(rs.next());
			assertEquals(-42, rs.getInt("neg_int"));
			assertEquals(-3.14, rs.getDouble("neg_float"), 0.01);
		}
	}

	@Test
	void testGetObjectWithoutType() throws SQLException {
		// Given: Various types
		String sql = "SELECT 42 as int_val, 'text' as str_val, true as bool_val";

		// When: Using getObject without type
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			// Then: Should return appropriate Java types
			assertTrue(rs.next());
			Object intObj = rs.getObject("int_val");
			Object strObj = rs.getObject("str_val");
			Object boolObj = rs.getObject("bool_val");

			assertNotNull(intObj);
			assertNotNull(strObj);
			assertNotNull(boolObj);

			assertInstanceOf(Number.class, intObj);
			assertInstanceOf(String.class, strObj);
			assertInstanceOf(Boolean.class, boolObj);
		}
	}

	@Test
	void testGetStringOnNonStringTypes() throws SQLException {
		// Given: Non-string types
		String sql = "SELECT 42 as int_val, 3.14 as float_val, true as bool_val";

		// When: Getting as String
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			// Then: Should convert to String
			assertTrue(rs.next());
			assertNotNull(rs.getString("int_val"));
			assertNotNull(rs.getString("float_val"));
			assertNotNull(rs.getString("bool_val"));
		}
	}

	@Test
	void testColumnMetaDataTypes() throws SQLException {
		// Given: Query with various types
		String sql = "SELECT " + "CAST(1 AS INT64) as int_col, " + "CAST('text' AS STRING) as str_col, "
				+ "CAST(3.14 AS FLOAT64) as float_col, " + "CAST(true AS BOOL) as bool_col";

		// When: Getting column metadata
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			ResultSetMetaData metaData = rs.getMetaData();

			// Then: Should have correct type information
			assertEquals(4, metaData.getColumnCount());

			assertEquals("int_col", metaData.getColumnName(1));
			assertEquals("str_col", metaData.getColumnName(2));
			assertEquals("float_col", metaData.getColumnName(3));
			assertEquals("bool_col", metaData.getColumnName(4));
		}
	}

	@Test
	void testArrayOfStrings() throws SQLException {
		// Given: An ARRAY of STRING values
		String sql = "SELECT ['Selector', 'Option', 'Value'] as string_array";

		// When: Querying
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			// Then: Should return as JSON array string
			assertTrue(rs.next());
			String arrayValue = rs.getString("string_array");
			assertNotNull(arrayValue);

			// Should be a JSON array, not FieldValue toString representation
			assertTrue(arrayValue.startsWith("["), "Array should start with [");
			assertTrue(arrayValue.endsWith("]"), "Array should end with ]");
			assertFalse(arrayValue.contains("FieldValue"), "Should not contain FieldValue object representation");

			// Should contain the actual values
			assertTrue(arrayValue.contains("Selector"), "Should contain 'Selector'");
			assertTrue(arrayValue.contains("Option"), "Should contain 'Option'");
			assertTrue(arrayValue.contains("Value"), "Should contain 'Value'");
		}
	}

	@Test
	void testArrayOfNumbers() throws SQLException {
		// Given: An ARRAY of INT64 values
		String sql = "SELECT [1, 2, 3, 42] as int_array";

		// When: Querying
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			// Then: Should return as JSON array string
			assertTrue(rs.next());
			String arrayValue = rs.getString("int_array");
			assertNotNull(arrayValue);

			// Should be a JSON array
			assertTrue(arrayValue.startsWith("["), "Array should start with [");
			assertTrue(arrayValue.endsWith("]"), "Array should end with ]");

			// Should contain the numbers (not quoted)
			assertTrue(arrayValue.contains("1"));
			assertTrue(arrayValue.contains("42"));
		}
	}

	@Test
	void testEmptyArray() throws SQLException {
		// Given: An empty ARRAY
		String sql = "SELECT ARRAY<STRING>[] as empty_array";

		// When: Querying
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			// Then: Should return as empty JSON array
			assertTrue(rs.next());
			String arrayValue = rs.getString("empty_array");
			assertEquals("[]", arrayValue, "Empty array should be []");
		}
	}

	@Test
	void testArrayWithNulls() throws SQLException {
		// Given: An ARRAY with NULL values
		String sql = "SELECT ['first', NULL, 'third'] as array_with_nulls";

		// When: Querying
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			// Then: Should return as JSON array with null
			assertTrue(rs.next());
			String arrayValue = rs.getString("array_with_nulls");
			assertNotNull(arrayValue);

			// Should contain null as JSON null
			assertTrue(arrayValue.contains("null"), "Should contain JSON null");
			assertTrue(arrayValue.contains("first"));
			assertTrue(arrayValue.contains("third"));
		}
	}

	@Test
	void testArrayGetObject() throws SQLException {
		// Given: An ARRAY of STRING values
		String sql = "SELECT ['Alpha', 'Beta', 'Gamma'] as string_array";

		// When: Using getObject() instead of getString()
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			// Then: Should return as JSON string, not FieldValue representation
			assertTrue(rs.next());
			Object arrayValue = rs.getObject("string_array");
			assertNotNull(arrayValue);
			assertInstanceOf(String.class, arrayValue, "getObject() should return String for arrays");

			String arrayStr = (String) arrayValue;
			assertTrue(arrayStr.startsWith("["), "Array should start with [");
			assertTrue(arrayStr.endsWith("]"), "Array should end with ]");
			assertFalse(arrayStr.contains("FieldValue"), "Should not contain FieldValue object representation");

			// Should contain the actual values
			assertTrue(arrayStr.contains("Alpha"));
			assertTrue(arrayStr.contains("Beta"));
			assertTrue(arrayStr.contains("Gamma"));
		}
	}

	@Test
	void testArrayOfIntegers() throws SQLException {
		// Given: An ARRAY of INT64 values
		String sql = "SELECT [10, 20, 30, 100] as int_array";

		// When: Querying
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			// Then: Should return as JSON array with unquoted numbers
			assertTrue(rs.next());
			String arrayValue = rs.getString("int_array");
			assertNotNull(arrayValue);

			// Verify proper JSON format
			assertTrue(arrayValue.startsWith("["));
			assertTrue(arrayValue.endsWith("]"));
			assertFalse(arrayValue.contains("FieldValue"));

			// Numbers should be present (as strings in the JSON)
			assertTrue(arrayValue.contains("10"));
			assertTrue(arrayValue.contains("20"));
			assertTrue(arrayValue.contains("100"));
		}
	}

	@Test
	void testArrayOfBooleans() throws SQLException {
		// Given: An ARRAY of BOOL values
		String sql = "SELECT [true, false, true] as bool_array";

		// When: Querying
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			// Then: Should return as JSON array with boolean values
			assertTrue(rs.next());
			String arrayValue = rs.getString("bool_array");
			assertNotNull(arrayValue);

			assertTrue(arrayValue.startsWith("["));
			assertTrue(arrayValue.endsWith("]"));
			assertFalse(arrayValue.contains("FieldValue"));

			// Should contain boolean values
			assertTrue(arrayValue.contains("true"));
			assertTrue(arrayValue.contains("false"));
		}
	}

	@Test
	void testArrayOfFloats() throws SQLException {
		// Given: An ARRAY of FLOAT64 values
		String sql = "SELECT [1.5, 2.7, 3.14] as float_array";

		// When: Querying
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			// Then: Should return as JSON array with float values
			assertTrue(rs.next());
			String arrayValue = rs.getString("float_array");
			assertNotNull(arrayValue);

			assertTrue(arrayValue.startsWith("["));
			assertTrue(arrayValue.endsWith("]"));
			assertFalse(arrayValue.contains("FieldValue"));

			// Should contain float values
			assertTrue(arrayValue.contains("1.5"));
			assertTrue(arrayValue.contains("3.14"));
		}
	}

	@Test
	void testMixedNullAndValues() throws SQLException {
		// Given: An ARRAY with various null positions
		String sql = "SELECT [NULL, 'value', NULL, 'another'] as mixed_array";

		// When: Querying
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			// Then: Should handle nulls properly in JSON
			assertTrue(rs.next());
			String arrayValue = rs.getString("mixed_array");
			assertNotNull(arrayValue);

			assertFalse(arrayValue.contains("FieldValue"));

			// Should contain both null and actual values
			assertTrue(arrayValue.contains("null"));
			assertTrue(arrayValue.contains("value"));
			assertTrue(arrayValue.contains("another"));
		}
	}
}

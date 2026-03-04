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

import java.math.BigDecimal;
import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real BigQuery integration tests for BigQuery to JDBC type mapping.
 *
 * <p>
 * Mirrors {@link vc.tbc.bq.jdbc.integration.TypeMappingTest} but runs against a
 * real BigQuery instance.
 *
 * @since 1.0.68
 */
class RealTypeMappingTest extends AbstractRealBigQueryIntegrationTest {

	@Test
	void testStringType() throws SQLException {
		String sql = "SELECT 'Hello, World!' as str_value";

		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
			assertTrue(rs.next());
			assertEquals("Hello, World!", rs.getString("str_value"));
			assertEquals("Hello, World!", rs.getObject("str_value", String.class));
		}
	}

	@Test
	void testInt64Type() throws SQLException {
		String sql = "SELECT 9223372036854775807 as big_int";

		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
			assertTrue(rs.next());
			assertEquals(9223372036854775807L, rs.getLong("big_int"));
			assertEquals(9223372036854775807L, rs.getObject("big_int", Long.class));
		}
	}

	@Test
	void testFloat64Type() throws SQLException {
		String sql = "SELECT 3.14159265359 as pi";

		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
			assertTrue(rs.next());
			assertEquals(3.14159265359, rs.getDouble("pi"), 0.0001);
			assertEquals(3.14159265359, rs.getObject("pi", Double.class), 0.0001);
		}
	}

	@Test
	void testBoolType() throws SQLException {
		String sql = "SELECT true as is_true, false as is_false";

		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
			assertTrue(rs.next());
			assertTrue(rs.getBoolean("is_true"));
			assertFalse(rs.getBoolean("is_false"));
			assertEquals(Boolean.TRUE, rs.getObject("is_true", Boolean.class));
			assertEquals(Boolean.FALSE, rs.getObject("is_false", Boolean.class));
		}
	}

	@Test
	void testNumericType() throws SQLException {
		String sql = "SELECT NUMERIC '123.456' as num_value";

		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
			assertTrue(rs.next());
			BigDecimal expected = new BigDecimal("123.456");
			assertEquals(expected, rs.getBigDecimal("num_value"));
		}
	}

	@Test
	void testDateType() throws SQLException {
		String sql = "SELECT DATE '2024-03-15' as date_value";

		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
			assertTrue(rs.next());
			Date expectedDate = Date.valueOf("2024-03-15");
			assertEquals(expectedDate, rs.getDate("date_value"));
		}
	}

	@Test
	void testTimeType() throws SQLException {
		String sql = "SELECT TIME '14:30:45' as time_value";

		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
			assertTrue(rs.next());
			assertNotNull(rs.getTime("time_value"));
		}
	}

	@Test
	void testTimestampType() throws SQLException {
		String sql = "SELECT TIMESTAMP '2024-03-15 14:30:45.123456 UTC' as ts_value";

		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
			assertTrue(rs.next());
			assertNotNull(rs.getTimestamp("ts_value"));
		}
	}

	@Test
	void testNullValues() throws SQLException {
		String sql = "SELECT " + "CAST(NULL AS STRING) as null_string, " + "CAST(NULL AS INT64) as null_int, "
				+ "CAST(NULL AS FLOAT64) as null_float, " + "CAST(NULL AS BOOL) as null_bool, "
				+ "CAST(NULL AS DATE) as null_date";

		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
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
		String sql = "SELECT CAST('Hello' AS BYTES) as bytes_value";

		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
			assertTrue(rs.next());
			byte[] bytes = rs.getBytes("bytes_value");
			assertNotNull(bytes);
			assertTrue(bytes.length > 0);
		}
	}

	@Test
	void testLargeNumbers() throws SQLException {
		String sql = "SELECT " + "9223372036854775807 as max_int64, " + "-9223372036854775808 as min_int64, "
				+ "1.7976931348623157E308 as large_float";

		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
			assertTrue(rs.next());
			assertEquals(Long.MAX_VALUE, rs.getLong("max_int64"));
			assertEquals(Long.MIN_VALUE, rs.getLong("min_int64"));
			assertEquals(1.7976931348623157E308, rs.getDouble("large_float"), 1E290);
		}
	}

	@Test
	void testZeroValues() throws SQLException {
		String sql = "SELECT 0 as zero_int, 0.0 as zero_float, '' as empty_string";

		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
			assertTrue(rs.next());
			assertEquals(0, rs.getInt("zero_int"));
			assertEquals(0.0, rs.getDouble("zero_float"));
			assertEquals("", rs.getString("empty_string"));
		}
	}

	@Test
	void testNegativeNumbers() throws SQLException {
		String sql = "SELECT -42 as neg_int, -3.14 as neg_float";

		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
			assertTrue(rs.next());
			assertEquals(-42, rs.getInt("neg_int"));
			assertEquals(-3.14, rs.getDouble("neg_float"), 0.01);
		}
	}

	@Test
	void testGetObjectWithoutType() throws SQLException {
		String sql = "SELECT 42 as int_val, 'text' as str_val, true as bool_val";

		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
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
		String sql = "SELECT 42 as int_val, 3.14 as float_val, true as bool_val";

		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
			assertTrue(rs.next());
			assertNotNull(rs.getString("int_val"));
			assertNotNull(rs.getString("float_val"));
			assertNotNull(rs.getString("bool_val"));
		}
	}

	@Test
	void testColumnMetaDataTypes() throws SQLException {
		String sql = "SELECT " + "CAST(1 AS INT64) as int_col, " + "CAST('text' AS STRING) as str_col, "
				+ "CAST(3.14 AS FLOAT64) as float_col, " + "CAST(true AS BOOL) as bool_col";

		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
			ResultSetMetaData metaData = rs.getMetaData();

			assertEquals(4, metaData.getColumnCount());
			assertEquals("int_col", metaData.getColumnName(1));
			assertEquals("str_col", metaData.getColumnName(2));
			assertEquals("float_col", metaData.getColumnName(3));
			assertEquals("bool_col", metaData.getColumnName(4));
		}
	}

	@Test
	void testArrayOfStrings() throws SQLException {
		String sql = "SELECT ['Selector', 'Option', 'Value'] as string_array";

		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
			assertTrue(rs.next());
			String arrayValue = rs.getString("string_array");
			assertNotNull(arrayValue);

			assertTrue(arrayValue.startsWith("["), "Array should start with [");
			assertTrue(arrayValue.endsWith("]"), "Array should end with ]");
			assertFalse(arrayValue.contains("FieldValue"), "Should not contain FieldValue object representation");
			assertTrue(arrayValue.contains("Selector"));
			assertTrue(arrayValue.contains("Option"));
			assertTrue(arrayValue.contains("Value"));
		}
	}

	@Test
	void testArrayOfNumbers() throws SQLException {
		String sql = "SELECT [1, 2, 3, 42] as int_array";

		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
			assertTrue(rs.next());
			String arrayValue = rs.getString("int_array");
			assertNotNull(arrayValue);

			assertTrue(arrayValue.startsWith("["));
			assertTrue(arrayValue.endsWith("]"));
			assertTrue(arrayValue.contains("1"));
			assertTrue(arrayValue.contains("42"));
		}
	}

	@Test
	void testEmptyArray() throws SQLException {
		String sql = "SELECT ARRAY<STRING>[] as empty_array";

		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
			assertTrue(rs.next());
			String arrayValue = rs.getString("empty_array");
			assertEquals("[]", arrayValue, "Empty array should be []");
		}
	}

	@Test
	void testArrayWithNulls() throws SQLException {
		String sql = "SELECT ['first', NULL, 'third'] as array_with_nulls";

		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
			assertTrue(rs.next());
			String arrayValue = rs.getString("array_with_nulls");
			assertNotNull(arrayValue);

			assertTrue(arrayValue.contains("null"));
			assertTrue(arrayValue.contains("first"));
			assertTrue(arrayValue.contains("third"));
		}
	}

	@Test
	void testArrayGetObject() throws SQLException {
		String sql = "SELECT ['Alpha', 'Beta', 'Gamma'] as string_array";

		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
			assertTrue(rs.next());
			Object arrayValue = rs.getObject("string_array");
			assertNotNull(arrayValue);
			assertInstanceOf(String.class, arrayValue, "getObject() should return String for arrays");

			String arrayStr = (String) arrayValue;
			assertTrue(arrayStr.startsWith("["));
			assertTrue(arrayStr.endsWith("]"));
			assertFalse(arrayStr.contains("FieldValue"));
			assertTrue(arrayStr.contains("Alpha"));
			assertTrue(arrayStr.contains("Beta"));
			assertTrue(arrayStr.contains("Gamma"));
		}
	}
}

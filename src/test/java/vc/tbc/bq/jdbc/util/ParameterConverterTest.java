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
package vc.tbc.bq.jdbc.util;

import org.junit.jupiter.api.Test;
import vc.tbc.bq.jdbc.exception.BQSQLException;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ParameterConverter utility class.
 *
 * @since 1.0.49
 */
class ParameterConverterTest {

	// toBoolean Tests

	@Test
	void testToBooleanWithBoolean() throws SQLException {
		// When: Converting Boolean
		boolean result = ParameterConverter.toBoolean(Boolean.TRUE, 1);

		// Then: Should return boolean value
		assertTrue(result);
	}

	@Test
	void testToBooleanWithNumberNonZero() throws SQLException {
		// When: Converting non-zero number
		boolean result = ParameterConverter.toBoolean(42, 1);

		// Then: Should return true
		assertTrue(result);
	}

	@Test
	void testToBooleanWithNumberZero() throws SQLException {
		// When: Converting zero
		boolean result = ParameterConverter.toBoolean(0, 1);

		// Then: Should return false
		assertFalse(result);
	}

	@Test
	void testToBooleanWithStringTrue() throws SQLException {
		// When: Converting "true" string
		boolean result = ParameterConverter.toBoolean("true", 1);

		// Then: Should return true
		assertTrue(result);
	}

	@Test
	void testToBooleanWithStringFalse() throws SQLException {
		// When: Converting "false" string
		boolean result = ParameterConverter.toBoolean("false", 1);

		// Then: Should return false
		assertFalse(result);
	}

	@Test
	void testToBooleanWithNull() {
		// When: Converting null
		// Then: Should throw SQLException
		SQLException ex = assertThrows(BQSQLException.class, () -> ParameterConverter.toBoolean(null, 1));
		assertTrue(ex.getMessage().contains("Cannot convert NULL to boolean"));
	}

	@Test
	void testToBooleanWithInvalidType() {
		// When: Converting invalid type
		// Then: Should throw SQLException
		SQLException ex = assertThrows(BQSQLException.class, () -> ParameterConverter.toBoolean(new Object(), 1));
		assertTrue(ex.getMessage().contains("Cannot convert"));
		assertTrue(ex.getMessage().contains("to boolean"));
	}

	// toString Tests

	@Test
	void testToStringWithString() throws SQLException {
		// When: Converting String
		String result = ParameterConverter.toString("test", 1);

		// Then: Should return same string
		assertEquals("test", result);
	}

	@Test
	void testToStringWithNumber() throws SQLException {
		// When: Converting number
		String result = ParameterConverter.toString(42, 1);

		// Then: Should return string representation
		assertEquals("42", result);
	}

	@Test
	void testToStringWithNull() throws SQLException {
		// When: Converting null
		String result = ParameterConverter.toString(null, 1);

		// Then: Should return null
		assertNull(result);
	}

	@Test
	void testToStringWithBoolean() throws SQLException {
		// When: Converting boolean
		String result = ParameterConverter.toString(true, 1);

		// Then: Should return string representation
		assertEquals("true", result);
	}

	// toBytes Tests

	@Test
	void testToBytesWithByteArray() throws SQLException {
		// When: Converting byte array
		byte[] input = {1, 2, 3};
		byte[] result = ParameterConverter.toBytes(input, 1);

		// Then: Should return same array
		assertSame(input, result);
	}

	@Test
	void testToBytesWithString() throws SQLException {
		// When: Converting string
		byte[] result = ParameterConverter.toBytes("hello", 1);

		// Then: Should return bytes
		assertArrayEquals("hello".getBytes(), result);
	}

	@Test
	void testToBytesWithNull() throws SQLException {
		// When: Converting null
		byte[] result = ParameterConverter.toBytes(null, 1);

		// Then: Should return null
		assertNull(result);
	}

	@Test
	void testToBytesWithInvalidType() {
		// When: Converting invalid type
		// Then: Should throw SQLException
		SQLException ex = assertThrows(BQSQLException.class, () -> ParameterConverter.toBytes(42, 1));
		assertTrue(ex.getMessage().contains("Cannot convert"));
		assertTrue(ex.getMessage().contains("to byte[]"));
	}

	// toDate Tests

	@Test
	void testToDateWithDate() throws SQLException {
		// When: Converting Date
		Date input = Date.valueOf("2024-01-15");
		Date result = ParameterConverter.toDate(input, 1);

		// Then: Should return same date
		assertEquals(input, result);
	}

	@Test
	void testToDateWithTimestamp() throws SQLException {
		// When: Converting Timestamp
		Timestamp ts = Timestamp.valueOf("2024-01-15 10:30:00");
		Date result = ParameterConverter.toDate(ts, 1);

		// Then: Should return date with same time
		assertEquals(ts.getTime(), result.getTime());
	}

	@Test
	void testToDateWithString() throws SQLException {
		// When: Converting valid date string
		Date result = ParameterConverter.toDate("2024-01-15", 1);

		// Then: Should return date
		assertEquals(Date.valueOf("2024-01-15"), result);
	}

	@Test
	void testToDateWithJavaUtilDate() throws SQLException {
		// When: Converting java.util.Date
		java.util.Date utilDate = new java.util.Date();
		Date result = ParameterConverter.toDate(utilDate, 1);

		// Then: Should return sql.Date with same time
		assertEquals(utilDate.getTime(), result.getTime());
	}

	@Test
	void testToDateWithNull() throws SQLException {
		// When: Converting null
		Date result = ParameterConverter.toDate(null, 1);

		// Then: Should return null
		assertNull(result);
	}

	@Test
	void testToDateWithInvalidString() {
		// When: Converting invalid date string
		// Then: Should throw SQLException
		SQLException ex = assertThrows(BQSQLException.class, () -> ParameterConverter.toDate("not a date", 1));
		assertTrue(ex.getMessage().contains("Cannot convert"));
		assertTrue(ex.getMessage().contains("to Date"));
	}

	@Test
	void testToDateWithInvalidType() {
		// When: Converting invalid type
		// Then: Should throw SQLException
		SQLException ex = assertThrows(BQSQLException.class, () -> ParameterConverter.toDate(42, 1));
		assertTrue(ex.getMessage().contains("Cannot convert"));
		assertTrue(ex.getMessage().contains("to Date"));
	}

	// toTime Tests

	@Test
	void testToTimeWithTime() throws SQLException {
		// When: Converting Time
		Time input = Time.valueOf("10:30:00");
		Time result = ParameterConverter.toTime(input, 1);

		// Then: Should return same time
		assertEquals(input, result);
	}

	@Test
	void testToTimeWithTimestamp() throws SQLException {
		// When: Converting Timestamp
		Timestamp ts = Timestamp.valueOf("2024-01-15 10:30:00");
		Time result = ParameterConverter.toTime(ts, 1);

		// Then: Should return time with same millis
		assertEquals(ts.getTime(), result.getTime());
	}

	@Test
	void testToTimeWithString() throws SQLException {
		// When: Converting valid time string
		Time result = ParameterConverter.toTime("10:30:00", 1);

		// Then: Should return time
		assertEquals(Time.valueOf("10:30:00"), result);
	}

	@Test
	void testToTimeWithJavaUtilDate() throws SQLException {
		// When: Converting java.util.Date
		java.util.Date utilDate = new java.util.Date();
		Time result = ParameterConverter.toTime(utilDate, 1);

		// Then: Should return sql.Time with same millis
		assertEquals(utilDate.getTime(), result.getTime());
	}

	@Test
	void testToTimeWithNull() throws SQLException {
		// When: Converting null
		Time result = ParameterConverter.toTime(null, 1);

		// Then: Should return null
		assertNull(result);
	}

	@Test
	void testToTimeWithInvalidString() {
		// When: Converting invalid time string
		// Then: Should throw SQLException
		SQLException ex = assertThrows(BQSQLException.class, () -> ParameterConverter.toTime("not a time", 1));
		assertTrue(ex.getMessage().contains("Cannot convert"));
		assertTrue(ex.getMessage().contains("to Time"));
	}

	@Test
	void testToTimeWithInvalidType() {
		// When: Converting invalid type
		// Then: Should throw SQLException
		SQLException ex = assertThrows(BQSQLException.class, () -> ParameterConverter.toTime(42, 1));
		assertTrue(ex.getMessage().contains("Cannot convert"));
		assertTrue(ex.getMessage().contains("to Time"));
	}

	// toTimestamp Tests

	@Test
	void testToTimestampWithTimestamp() throws SQLException {
		// When: Converting Timestamp
		Timestamp input = Timestamp.valueOf("2024-01-15 10:30:00");
		Timestamp result = ParameterConverter.toTimestamp(input, 1);

		// Then: Should return same timestamp
		assertEquals(input, result);
	}

	@Test
	void testToTimestampWithDate() throws SQLException {
		// When: Converting Date
		Date date = Date.valueOf("2024-01-15");
		Timestamp result = ParameterConverter.toTimestamp(date, 1);

		// Then: Should return timestamp with same time
		assertEquals(date.getTime(), result.getTime());
	}

	@Test
	void testToTimestampWithTime() throws SQLException {
		// When: Converting Time
		Time time = Time.valueOf("10:30:00");
		Timestamp result = ParameterConverter.toTimestamp(time, 1);

		// Then: Should return timestamp with same millis
		assertEquals(time.getTime(), result.getTime());
	}

	@Test
	void testToTimestampWithString() throws SQLException {
		// When: Converting valid timestamp string
		Timestamp result = ParameterConverter.toTimestamp("2024-01-15 10:30:00", 1);

		// Then: Should return timestamp
		assertEquals(Timestamp.valueOf("2024-01-15 10:30:00"), result);
	}

	@Test
	void testToTimestampWithJavaUtilDate() throws SQLException {
		// When: Converting java.util.Date
		java.util.Date utilDate = new java.util.Date();
		Timestamp result = ParameterConverter.toTimestamp(utilDate, 1);

		// Then: Should return timestamp with same millis
		assertEquals(utilDate.getTime(), result.getTime());
	}

	@Test
	void testToTimestampWithNull() throws SQLException {
		// When: Converting null
		Timestamp result = ParameterConverter.toTimestamp(null, 1);

		// Then: Should return null
		assertNull(result);
	}

	@Test
	void testToTimestampWithInvalidString() {
		// When: Converting invalid timestamp string
		// Then: Should throw SQLException
		SQLException ex = assertThrows(BQSQLException.class,
				() -> ParameterConverter.toTimestamp("not a timestamp", 1));
		assertTrue(ex.getMessage().contains("Cannot convert"));
		assertTrue(ex.getMessage().contains("to Timestamp"));
	}

	@Test
	void testToTimestampWithInvalidType() {
		// When: Converting invalid type
		// Then: Should throw SQLException
		SQLException ex = assertThrows(BQSQLException.class, () -> ParameterConverter.toTimestamp(42, 1));
		assertTrue(ex.getMessage().contains("Cannot convert"));
		assertTrue(ex.getMessage().contains("to Timestamp"));
	}

	// Numeric Conversions (delegated to NumberParser)

	@Test
	void testToByteWithNumber() throws SQLException {
		// When: Converting number to byte
		byte result = ParameterConverter.toByte(42, 1);

		// Then: Should return byte value
		assertEquals(42, result);
	}

	@Test
	void testToByteWithString() throws SQLException {
		// When: Converting string to byte
		byte result = ParameterConverter.toByte("42", 1);

		// Then: Should return parsed byte
		assertEquals(42, result);
	}

	@Test
	void testToShortWithNumber() throws SQLException {
		// When: Converting number to short
		short result = ParameterConverter.toShort(1000, 1);

		// Then: Should return short value
		assertEquals(1000, result);
	}

	@Test
	void testToShortWithString() throws SQLException {
		// When: Converting string to short
		short result = ParameterConverter.toShort("1000", 1);

		// Then: Should return parsed short
		assertEquals(1000, result);
	}

	@Test
	void testToIntWithNumber() throws SQLException {
		// When: Converting number to int
		int result = ParameterConverter.toInt(42L, 1);

		// Then: Should return int value
		assertEquals(42, result);
	}

	@Test
	void testToIntWithString() throws SQLException {
		// When: Converting string to int
		int result = ParameterConverter.toInt("42", 1);

		// Then: Should return parsed int
		assertEquals(42, result);
	}

	@Test
	void testToLongWithNumber() throws SQLException {
		// When: Converting number to long
		long result = ParameterConverter.toLong(42, 1);

		// Then: Should return long value
		assertEquals(42L, result);
	}

	@Test
	void testToLongWithString() throws SQLException {
		// When: Converting string to long
		long result = ParameterConverter.toLong("42", 1);

		// Then: Should return parsed long
		assertEquals(42L, result);
	}

	@Test
	void testToFloatWithNumber() throws SQLException {
		// When: Converting number to float
		float result = ParameterConverter.toFloat(3.14, 1);

		// Then: Should return float value
		assertEquals(3.14f, result, 0.01f);
	}

	@Test
	void testToFloatWithString() throws SQLException {
		// When: Converting string to float
		float result = ParameterConverter.toFloat("3.14", 1);

		// Then: Should return parsed float
		assertEquals(3.14f, result, 0.01f);
	}

	@Test
	void testToDoubleWithNumber() throws SQLException {
		// When: Converting number to double
		double result = ParameterConverter.toDouble(3.14f, 1);

		// Then: Should return double value
		assertEquals(3.14, result, 0.01);
	}

	@Test
	void testToDoubleWithString() throws SQLException {
		// When: Converting string to double
		double result = ParameterConverter.toDouble("3.14", 1);

		// Then: Should return parsed double
		assertEquals(3.14, result, 0.01);
	}

	@Test
	void testToBigDecimalWithBigDecimal() throws SQLException {
		// When: Converting BigDecimal
		BigDecimal input = new BigDecimal("123.456");
		BigDecimal result = ParameterConverter.toBigDecimal(input, 1);

		// Then: Should return same BigDecimal
		assertEquals(input, result);
	}

	@Test
	void testToBigDecimalWithString() throws SQLException {
		// When: Converting string to BigDecimal
		BigDecimal result = ParameterConverter.toBigDecimal("123.456", 1);

		// Then: Should return parsed BigDecimal
		assertEquals(new BigDecimal("123.456"), result);
	}

	@Test
	void testToBigDecimalWithNull() throws SQLException {
		// When: Converting null
		BigDecimal result = ParameterConverter.toBigDecimal(null, 1);

		// Then: Should return null
		assertNull(result);
	}

	// Edge Cases and Error Messages

	@Test
	void testErrorMessageIncludesParameterIndex() {
		// When: Conversion fails with invalid type
		// Then: Error message should include parameter index
		SQLException ex = assertThrows(BQSQLException.class, () -> ParameterConverter.toBoolean(new Object(), 5));
		assertTrue(ex.getMessage().contains("parameter 5"), "Error message should include parameter index");
	}

	@Test
	void testToIntWithInvalidStringThrows() {
		// When: Converting invalid string to int
		// Then: Should throw SQLException with clear message
		SQLException ex = assertThrows(SQLException.class, () -> ParameterConverter.toInt("not a number", 1));
		assertTrue(ex.getMessage().contains("Cannot convert"));
	}

	@Test
	void testToDateWithEmptyString() {
		// When: Converting empty string to date
		// Then: Should throw SQLException
		assertThrows(SQLException.class, () -> ParameterConverter.toDate("", 1));
	}

	@Test
	void testToTimeWithEmptyString() {
		// When: Converting empty string to time
		// Then: Should throw SQLException
		assertThrows(SQLException.class, () -> ParameterConverter.toTime("", 1));
	}

	@Test
	void testToTimestampWithEmptyString() {
		// When: Converting empty string to timestamp
		// Then: Should throw SQLException
		assertThrows(SQLException.class, () -> ParameterConverter.toTimestamp("", 1));
	}
}
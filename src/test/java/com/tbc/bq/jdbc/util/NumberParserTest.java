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
package com.tbc.bq.jdbc.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for NumberParser utility class.
 *
 * @since 1.0.15
 */
class NumberParserTest {

	// parseInt Tests

	@Test
	void testParseIntWithValidString() {
		// When: Parsing valid integer string
		int result = NumberParser.parseInt("42", 0);

		// Then: Should return parsed value
		assertEquals(42, result);
	}

	@Test
	void testParseIntWithNegativeNumber() {
		// When: Parsing negative integer
		int result = NumberParser.parseInt("-123", 0);

		// Then: Should return negative value
		assertEquals(-123, result);
	}

	@Test
	void testParseIntWithWhitespace() {
		// When: Parsing string with whitespace
		int result = NumberParser.parseInt("  456  ", 0);

		// Then: Should trim and parse
		assertEquals(456, result);
	}

	@Test
	void testParseIntWithNullReturnsDefault() {
		// When: Parsing null
		int result = NumberParser.parseInt(null, 99);

		// Then: Should return default value
		assertEquals(99, result);
	}

	@Test
	void testParseIntWithEmptyStringReturnsDefault() {
		// When: Parsing empty string
		int result = NumberParser.parseInt("", 99);

		// Then: Should return default value
		assertEquals(99, result);
	}

	@Test
	void testParseIntWithInvalidStringReturnsDefault() {
		// When: Parsing invalid string
		int result = NumberParser.parseInt("not a number", 99);

		// Then: Should return default value
		assertEquals(99, result);
	}

	// toByte Tests

	@Test
	void testToByteWithNull() throws SQLException {
		// When: Converting null
		byte result = NumberParser.toByte(null, 1);

		// Then: Should return 0
		assertEquals(0, result);
	}

	@Test
	void testToByteWithByteNumber() throws SQLException {
		// When: Converting Byte object
		byte result = NumberParser.toByte((byte) 42, 1);

		// Then: Should return value
		assertEquals(42, result);
	}

	@Test
	void testToByteWithIntegerNumber() throws SQLException {
		// When: Converting Integer object
		byte result = NumberParser.toByte(100, 1);

		// Then: Should convert to byte
		assertEquals(100, result);
	}

	@Test
	void testToByteWithValidString() throws SQLException {
		// When: Converting valid string
		byte result = NumberParser.toByte("42", 1);

		// Then: Should parse and return
		assertEquals(42, result);
	}

	@Test
	void testToByteWithInvalidStringThrows() {
		// Then: Invalid string should throw SQLException
		SQLException ex = assertThrows(SQLException.class, () -> NumberParser.toByte("invalid", 1));
		assertTrue(ex.getMessage().contains("Cannot convert 'invalid' to byte"));
		assertTrue(ex.getMessage().contains("column 1"));
	}

	// toShort Tests

	@Test
	void testToShortWithNull() throws SQLException {
		// When: Converting null
		short result = NumberParser.toShort(null, 1);

		// Then: Should return 0
		assertEquals(0, result);
	}

	@Test
	void testToShortWithShortNumber() throws SQLException {
		// When: Converting Short object
		short result = NumberParser.toShort((short) 1000, 1);

		// Then: Should return value
		assertEquals(1000, result);
	}

	@Test
	void testToShortWithValidString() throws SQLException {
		// When: Converting valid string
		short result = NumberParser.toShort("1234", 1);

		// Then: Should parse and return
		assertEquals(1234, result);
	}

	@Test
	void testToShortWithMaxValue() throws SQLException {
		// When: Converting Short.MAX_VALUE as string
		short result = NumberParser.toShort(String.valueOf(Short.MAX_VALUE), 1);

		// Then: Should handle max value
		assertEquals(Short.MAX_VALUE, result);
	}

	// toInt Tests

	@Test
	void testToIntWithNull() throws SQLException {
		// When: Converting null
		int result = NumberParser.toInt(null, 1);

		// Then: Should return 0
		assertEquals(0, result);
	}

	@Test
	void testToIntWithIntegerNumber() throws SQLException {
		// When: Converting Integer object
		int result = NumberParser.toInt(123456, 1);

		// Then: Should return value
		assertEquals(123456, result);
	}

	@Test
	void testToIntWithValidString() throws SQLException {
		// When: Converting valid string
		int result = NumberParser.toInt("987654", 1);

		// Then: Should parse and return
		assertEquals(987654, result);
	}

	@Test
	void testToIntWithMaxValue() throws SQLException {
		// When: Converting Integer.MAX_VALUE as string
		int result = NumberParser.toInt(String.valueOf(Integer.MAX_VALUE), 1);

		// Then: Should handle max value
		assertEquals(Integer.MAX_VALUE, result);
	}

	// toLong Tests

	@Test
	void testToLongWithNull() throws SQLException {
		// When: Converting null
		long result = NumberParser.toLong(null, 1);

		// Then: Should return 0
		assertEquals(0L, result);
	}

	@Test
	void testToLongWithLongNumber() throws SQLException {
		// When: Converting Long object
		long result = NumberParser.toLong(123456789L, 1);

		// Then: Should return value
		assertEquals(123456789L, result);
	}

	@Test
	void testToLongWithValidString() throws SQLException {
		// When: Converting valid string
		long result = NumberParser.toLong("9876543210", 1);

		// Then: Should parse and return
		assertEquals(9876543210L, result);
	}

	@Test
	void testToLongWithMaxValue() throws SQLException {
		// When: Converting Long.MAX_VALUE as string
		long result = NumberParser.toLong(String.valueOf(Long.MAX_VALUE), 1);

		// Then: Should handle max value
		assertEquals(Long.MAX_VALUE, result);
	}

	// toFloat Tests

	@Test
	void testToFloatWithNull() throws SQLException {
		// When: Converting null
		float result = NumberParser.toFloat(null, 1);

		// Then: Should return 0
		assertEquals(0.0f, result, 0.0001f);
	}

	@Test
	void testToFloatWithFloatNumber() throws SQLException {
		// When: Converting Float object
		float result = NumberParser.toFloat(3.14f, 1);

		// Then: Should return value
		assertEquals(3.14f, result, 0.0001f);
	}

	@Test
	void testToFloatWithValidString() throws SQLException {
		// When: Converting valid string
		float result = NumberParser.toFloat("2.718", 1);

		// Then: Should parse and return
		assertEquals(2.718f, result, 0.0001f);
	}

	@Test
	void testToFloatWithScientificNotation() throws SQLException {
		// When: Converting scientific notation
		float result = NumberParser.toFloat("1.23e5", 1);

		// Then: Should parse correctly
		assertEquals(123000.0f, result, 1.0f);
	}

	@Test
	void testToFloatWithInvalidStringThrows() {
		// Then: Invalid string should throw SQLException
		SQLException ex = assertThrows(SQLException.class, () -> NumberParser.toFloat("not a float", 2));
		assertTrue(ex.getMessage().contains("Cannot convert 'not a float' to float"));
		assertTrue(ex.getMessage().contains("column 2"));
	}

	// toDouble Tests

	@Test
	void testToDoubleWithNull() throws SQLException {
		// When: Converting null
		double result = NumberParser.toDouble(null, 1);

		// Then: Should return 0
		assertEquals(0.0, result, 0.00001);
	}

	@Test
	void testToDoubleWithDoubleNumber() throws SQLException {
		// When: Converting Double object
		double result = NumberParser.toDouble(3.14159, 1);

		// Then: Should return value
		assertEquals(3.14159, result, 0.00001);
	}

	@Test
	void testToDoubleWithValidString() throws SQLException {
		// When: Converting valid string
		double result = NumberParser.toDouble("2.718281828", 1);

		// Then: Should parse and return
		assertEquals(2.718281828, result, 0.000000001);
	}

	@Test
	void testToDoubleWithScientificNotation() throws SQLException {
		// When: Converting scientific notation
		double result = NumberParser.toDouble("6.022e23", 1);

		// Then: Should parse correctly
		assertEquals(6.022e23, result, 1e20);
	}

	// toBigDecimal Tests

	@Test
	void testToBigDecimalWithNull() throws SQLException {
		// When: Converting null
		BigDecimal result = NumberParser.toBigDecimal(null, 1);

		// Then: Should return null
		assertNull(result);
	}

	@Test
	void testToBigDecimalWithBigDecimalObject() throws SQLException {
		// When: Converting BigDecimal object
		BigDecimal input = new BigDecimal("123.456");
		BigDecimal result = NumberParser.toBigDecimal(input, 1);

		// Then: Should return same object
		assertSame(input, result);
	}

	@Test
	void testToBigDecimalWithValidString() throws SQLException {
		// When: Converting valid string
		BigDecimal result = NumberParser.toBigDecimal("99999.99999", 1);

		// Then: Should parse and return
		assertEquals(new BigDecimal("99999.99999"), result);
	}

	@Test
	void testToBigDecimalWithVeryLargeNumber() throws SQLException {
		// When: Converting very large number
		String largeNumber = "123456789012345678901234567890.123456789";
		BigDecimal result = NumberParser.toBigDecimal(largeNumber, 1);

		// Then: Should handle large precision
		assertEquals(new BigDecimal(largeNumber), result);
	}

	@Test
	void testToBigDecimalWithInvalidStringThrows() {
		// Then: Invalid string should throw SQLException
		SQLException ex = assertThrows(SQLException.class, () -> NumberParser.toBigDecimal("not a number", 3));
		assertTrue(ex.getMessage().contains("Cannot convert 'not a number' to BigDecimal"));
		assertTrue(ex.getMessage().contains("column 3"));
	}

	// parseXOrThrow Tests - Null Handling

	@Test
	void testParseIntOrThrowWithNullThrows() {
		// Then: Null should throw SQLException
		SQLException ex = assertThrows(SQLException.class, () -> NumberParser.parseIntOrThrow(null, 1));
		assertTrue(ex.getMessage().contains("Cannot convert NULL to int"));
	}

	@Test
	void testParseLongOrThrowWithNullThrows() {
		// Then: Null should throw SQLException
		SQLException ex = assertThrows(SQLException.class, () -> NumberParser.parseLongOrThrow(null, 1));
		assertTrue(ex.getMessage().contains("Cannot convert NULL to long"));
	}

	@Test
	void testParseShortOrThrowWithNullThrows() {
		// Then: Null should throw SQLException
		SQLException ex = assertThrows(SQLException.class, () -> NumberParser.parseShortOrThrow(null, 1));
		assertTrue(ex.getMessage().contains("Cannot convert NULL to short"));
	}

	@Test
	void testParseByteOrThrowWithNullThrows() {
		// Then: Null should throw SQLException
		SQLException ex = assertThrows(SQLException.class, () -> NumberParser.parseByteOrThrow(null, 1));
		assertTrue(ex.getMessage().contains("Cannot convert NULL to byte"));
	}

	@Test
	void testParseFloatOrThrowWithNullThrows() {
		// Then: Null should throw SQLException
		SQLException ex = assertThrows(SQLException.class, () -> NumberParser.parseFloatOrThrow(null, 1));
		assertTrue(ex.getMessage().contains("Cannot convert NULL to float"));
	}

	@Test
	void testParseDoubleOrThrowWithNullThrows() {
		// Then: Null should throw SQLException
		SQLException ex = assertThrows(SQLException.class, () -> NumberParser.parseDoubleOrThrow(null, 1));
		assertTrue(ex.getMessage().contains("Cannot convert NULL to double"));
	}

	@Test
	void testParseBigDecimalOrThrowWithNullThrows() {
		// Then: Null should throw SQLException
		SQLException ex = assertThrows(SQLException.class, () -> NumberParser.parseBigDecimalOrThrow(null, 1));
		assertTrue(ex.getMessage().contains("Cannot convert NULL to BigDecimal"));
	}

	// parseXOrThrow Tests - Valid Values

	@Test
	void testParseIntOrThrowWithValidString() throws SQLException {
		// When: Parsing valid string
		int result = NumberParser.parseIntOrThrow("42", 1);

		// Then: Should return parsed value
		assertEquals(42, result);
	}

	@Test
	void testParseLongOrThrowWithValidString() throws SQLException {
		// When: Parsing valid string
		long result = NumberParser.parseLongOrThrow("1234567890", 1);

		// Then: Should return parsed value
		assertEquals(1234567890L, result);
	}

	@Test
	void testParseShortOrThrowWithValidString() throws SQLException {
		// When: Parsing valid string
		short result = NumberParser.parseShortOrThrow("1234", 1);

		// Then: Should return parsed value
		assertEquals(1234, result);
	}

	@Test
	void testParseByteOrThrowWithValidString() throws SQLException {
		// When: Parsing valid string
		byte result = NumberParser.parseByteOrThrow("127", 1);

		// Then: Should return parsed value
		assertEquals(127, result);
	}

	@Test
	void testParseFloatOrThrowWithValidString() throws SQLException {
		// When: Parsing valid string
		float result = NumberParser.parseFloatOrThrow("3.14", 1);

		// Then: Should return parsed value
		assertEquals(3.14f, result, 0.0001f);
	}

	@Test
	void testParseDoubleOrThrowWithValidString() throws SQLException {
		// When: Parsing valid string
		double result = NumberParser.parseDoubleOrThrow("2.718281828", 1);

		// Then: Should return parsed value
		assertEquals(2.718281828, result, 0.000000001);
	}

	@Test
	void testParseBigDecimalOrThrowWithValidString() throws SQLException {
		// When: Parsing valid string
		BigDecimal result = NumberParser.parseBigDecimalOrThrow("12345.6789", 1);

		// Then: Should return parsed value
		assertEquals(new BigDecimal("12345.6789"), result);
	}

	// parseXOrThrow Tests - Whitespace Handling

	@Test
	void testParseIntOrThrowTrimsWhitespace() throws SQLException {
		// When: Parsing string with whitespace
		int result = NumberParser.parseIntOrThrow("  789  ", 1);

		// Then: Should trim and parse
		assertEquals(789, result);
	}

	@Test
	void testParseDoubleOrThrowTrimsWhitespace() throws SQLException {
		// When: Parsing string with whitespace
		double result = NumberParser.parseDoubleOrThrow("  3.14  ", 1);

		// Then: Should trim and parse
		assertEquals(3.14, result, 0.0001);
	}

	// parseXOrThrow Tests - Invalid Values

	@Test
	void testParseIntOrThrowWithInvalidStringThrows() {
		// Then: Invalid string should throw SQLException with correct message
		SQLException ex = assertThrows(SQLException.class, () -> NumberParser.parseIntOrThrow("not an int", 5));
		assertTrue(ex.getMessage().contains("Cannot convert 'not an int' to int"));
		assertTrue(ex.getMessage().contains("column 5"));
		assertNotNull(ex.getCause());
		assertInstanceOf(NumberFormatException.class, ex.getCause());
	}

	@Test
	void testParseLongOrThrowWithInvalidStringThrows() {
		// Then: Invalid string should throw SQLException
		SQLException ex = assertThrows(SQLException.class, () -> NumberParser.parseLongOrThrow("abc", 2));
		assertTrue(ex.getMessage().contains("Cannot convert 'abc' to long"));
		assertTrue(ex.getMessage().contains("column 2"));
	}

	@Test
	void testParseShortOrThrowWithOverflowThrows() {
		// Then: Overflow should throw SQLException
		SQLException ex = assertThrows(SQLException.class, () -> NumberParser.parseShortOrThrow("99999", 1));
		assertTrue(ex.getMessage().contains("Cannot convert '99999' to short"));
	}

	@Test
	void testParseByteOrThrowWithOverflowThrows() {
		// Then: Overflow should throw SQLException
		SQLException ex = assertThrows(SQLException.class, () -> NumberParser.parseByteOrThrow("1000", 1));
		assertTrue(ex.getMessage().contains("Cannot convert '1000' to byte"));
	}
}

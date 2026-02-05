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
package com.twobearcapital.bigquery.jdbc.util;

import com.twobearcapital.bigquery.jdbc.exception.BQSQLException;

import java.math.BigDecimal;
import java.sql.SQLException;

/**
 * Utility class for safely parsing string values to numeric types.
 *
 * <p>
 * Provides consistent error handling for number format exceptions across the
 * JDBC driver.
 *
 * @since 1.0.0
 */
public final class NumberParser {

	private NumberParser() {
	}

	/**
	 * Safely parse a string to an integer with a default value.
	 *
	 * @param value
	 *            the string to parse
	 * @param defaultValue
	 *            the default value to return if parsing fails
	 * @return the parsed integer or default value
	 */
	public static int parseInt(String value, int defaultValue) {
		if (value == null || value.isEmpty()) {
			return defaultValue;
		}
		try {
			return Integer.parseInt(value.trim());
		} catch (NumberFormatException e) {
			return defaultValue;
		}
	}

	/**
	 * Convert an Object to a byte value.
	 *
	 * @param value
	 *            the value to convert (can be null, Number, or String)
	 * @param columnIndex
	 *            the column index (for error messages)
	 * @return the byte value (0 if null)
	 * @throws SQLException
	 *             if conversion fails
	 */
	public static byte toByte(Object value, int columnIndex) throws SQLException {
		if (value == null)
			return 0;
		if (value instanceof Number)
			return ((Number) value).byteValue();
		return parseByteOrThrow(value.toString(), columnIndex);
	}

	/**
	 * Convert an Object to a short value.
	 *
	 * @param value
	 *            the value to convert (can be null, Number, or String)
	 * @param columnIndex
	 *            the column index (for error messages)
	 * @return the short value (0 if null)
	 * @throws SQLException
	 *             if conversion fails
	 */
	public static short toShort(Object value, int columnIndex) throws SQLException {
		if (value == null)
			return 0;
		if (value instanceof Number)
			return ((Number) value).shortValue();
		return parseShortOrThrow(value.toString(), columnIndex);
	}

	/**
	 * Convert an Object to an int value.
	 *
	 * @param value
	 *            the value to convert (can be null, Number, or String)
	 * @param columnIndex
	 *            the column index (for error messages)
	 * @return the int value (0 if null)
	 * @throws SQLException
	 *             if conversion fails
	 */
	public static int toInt(Object value, int columnIndex) throws SQLException {
		if (value == null)
			return 0;
		if (value instanceof Number)
			return ((Number) value).intValue();
		return parseIntOrThrow(value.toString(), columnIndex);
	}

	/**
	 * Convert an Object to a long value.
	 *
	 * @param value
	 *            the value to convert (can be null, Number, or String)
	 * @param columnIndex
	 *            the column index (for error messages)
	 * @return the long value (0 if null)
	 * @throws SQLException
	 *             if conversion fails
	 */
	public static long toLong(Object value, int columnIndex) throws SQLException {
		if (value == null)
			return 0;
		if (value instanceof Number)
			return ((Number) value).longValue();
		return parseLongOrThrow(value.toString(), columnIndex);
	}

	/**
	 * Convert an Object to a float value.
	 *
	 * @param value
	 *            the value to convert (can be null, Number, or String)
	 * @param columnIndex
	 *            the column index (for error messages)
	 * @return the float value (0 if null)
	 * @throws SQLException
	 *             if conversion fails
	 */
	public static float toFloat(Object value, int columnIndex) throws SQLException {
		if (value == null)
			return 0;
		if (value instanceof Number)
			return ((Number) value).floatValue();
		return parseFloatOrThrow(value.toString(), columnIndex);
	}

	/**
	 * Convert an Object to a double value.
	 *
	 * @param value
	 *            the value to convert (can be null, Number, or String)
	 * @param columnIndex
	 *            the column index (for error messages)
	 * @return the double value (0 if null)
	 * @throws SQLException
	 *             if conversion fails
	 */
	public static double toDouble(Object value, int columnIndex) throws SQLException {
		if (value == null)
			return 0;
		if (value instanceof Number)
			return ((Number) value).doubleValue();
		return parseDoubleOrThrow(value.toString(), columnIndex);
	}

	/**
	 * Convert an Object to a BigDecimal value.
	 *
	 * @param value
	 *            the value to convert (can be null, BigDecimal, or String)
	 * @param columnIndex
	 *            the column index (for error messages)
	 * @return the BigDecimal value (null if input is null)
	 * @throws SQLException
	 *             if conversion fails
	 */
	public static BigDecimal toBigDecimal(Object value, int columnIndex) throws SQLException {
		if (value == null)
			return null;
		if (value instanceof BigDecimal)
			return (BigDecimal) value;
		return parseBigDecimalOrThrow(value.toString(), columnIndex);
	}

	/**
	 * Safely parse a string to an integer, throwing SQLException on failure.
	 *
	 * @param value
	 *            the string to parse
	 * @param columnIndex
	 *            the column index (for error messages)
	 * @return the parsed integer
	 * @throws SQLException
	 *             if parsing fails
	 */
	public static int parseIntOrThrow(String value, int columnIndex) throws SQLException {
		if (value == null) {
			throw new BQSQLException("Cannot convert NULL to int at column " + columnIndex,
					BQSQLException.SQLSTATE_INVALID_PARAMETER_VALUE);
		}
		try {
			return Integer.parseInt(value.trim());
		} catch (NumberFormatException e) {
			throw new BQSQLException("Cannot convert '" + value + "' to int at column " + columnIndex,
					BQSQLException.SQLSTATE_INVALID_PARAMETER_VALUE, e);
		}
	}

	/**
	 * Safely parse a string to a long, throwing SQLException on failure.
	 *
	 * @param value
	 *            the string to parse
	 * @param columnIndex
	 *            the column index (for error messages)
	 * @return the parsed long
	 * @throws SQLException
	 *             if parsing fails
	 */
	public static long parseLongOrThrow(String value, int columnIndex) throws SQLException {
		if (value == null) {
			throw new BQSQLException("Cannot convert NULL to long at column " + columnIndex,
					BQSQLException.SQLSTATE_INVALID_PARAMETER_VALUE);
		}
		try {
			return Long.parseLong(value.trim());
		} catch (NumberFormatException e) {
			throw new BQSQLException("Cannot convert '" + value + "' to long at column " + columnIndex,
					BQSQLException.SQLSTATE_INVALID_PARAMETER_VALUE, e);
		}
	}

	/**
	 * Safely parse a string to a short, throwing SQLException on failure.
	 *
	 * @param value
	 *            the string to parse
	 * @param columnIndex
	 *            the column index (for error messages)
	 * @return the parsed short
	 * @throws SQLException
	 *             if parsing fails
	 */
	public static short parseShortOrThrow(String value, int columnIndex) throws SQLException {
		if (value == null) {
			throw new BQSQLException("Cannot convert NULL to short at column " + columnIndex,
					BQSQLException.SQLSTATE_INVALID_PARAMETER_VALUE);
		}
		try {
			return Short.parseShort(value.trim());
		} catch (NumberFormatException e) {
			throw new BQSQLException("Cannot convert '" + value + "' to short at column " + columnIndex,
					BQSQLException.SQLSTATE_INVALID_PARAMETER_VALUE, e);
		}
	}

	/**
	 * Safely parse a string to a byte, throwing SQLException on failure.
	 *
	 * @param value
	 *            the string to parse
	 * @param columnIndex
	 *            the column index (for error messages)
	 * @return the parsed byte
	 * @throws SQLException
	 *             if parsing fails
	 */
	public static byte parseByteOrThrow(String value, int columnIndex) throws SQLException {
		if (value == null) {
			throw new BQSQLException("Cannot convert NULL to byte at column " + columnIndex,
					BQSQLException.SQLSTATE_INVALID_PARAMETER_VALUE);
		}
		try {
			return Byte.parseByte(value.trim());
		} catch (NumberFormatException e) {
			throw new BQSQLException("Cannot convert '" + value + "' to byte at column " + columnIndex,
					BQSQLException.SQLSTATE_INVALID_PARAMETER_VALUE, e);
		}
	}

	/**
	 * Safely parse a string to a float, throwing SQLException on failure.
	 *
	 * @param value
	 *            the string to parse
	 * @param columnIndex
	 *            the column index (for error messages)
	 * @return the parsed float
	 * @throws SQLException
	 *             if parsing fails
	 */
	public static float parseFloatOrThrow(String value, int columnIndex) throws SQLException {
		if (value == null) {
			throw new BQSQLException("Cannot convert NULL to float at column " + columnIndex,
					BQSQLException.SQLSTATE_INVALID_PARAMETER_VALUE);
		}
		try {
			return Float.parseFloat(value.trim());
		} catch (NumberFormatException e) {
			throw new BQSQLException("Cannot convert '" + value + "' to float at column " + columnIndex,
					BQSQLException.SQLSTATE_INVALID_PARAMETER_VALUE, e);
		}
	}

	/**
	 * Safely parse a string to a double, throwing SQLException on failure.
	 *
	 * @param value
	 *            the string to parse
	 * @param columnIndex
	 *            the column index (for error messages)
	 * @return the parsed double
	 * @throws SQLException
	 *             if parsing fails
	 */
	public static double parseDoubleOrThrow(String value, int columnIndex) throws SQLException {
		if (value == null) {
			throw new BQSQLException("Cannot convert NULL to double at column " + columnIndex,
					BQSQLException.SQLSTATE_INVALID_PARAMETER_VALUE);
		}
		try {
			return Double.parseDouble(value.trim());
		} catch (NumberFormatException e) {
			throw new BQSQLException("Cannot convert '" + value + "' to double at column " + columnIndex,
					BQSQLException.SQLSTATE_INVALID_PARAMETER_VALUE, e);
		}
	}

	/**
	 * Safely parse a string to a BigDecimal, throwing SQLException on failure.
	 *
	 * @param value
	 *            the string to parse
	 * @param columnIndex
	 *            the column index (for error messages)
	 * @return the parsed BigDecimal
	 * @throws SQLException
	 *             if parsing fails
	 */
	public static BigDecimal parseBigDecimalOrThrow(String value, int columnIndex) throws SQLException {
		if (value == null) {
			throw new BQSQLException("Cannot convert NULL to BigDecimal at column " + columnIndex,
					BQSQLException.SQLSTATE_INVALID_PARAMETER_VALUE);
		}
		try {
			return new BigDecimal(value.trim());
		} catch (NumberFormatException e) {
			throw new BQSQLException("Cannot convert '" + value + "' to BigDecimal at column " + columnIndex,
					BQSQLException.SQLSTATE_INVALID_PARAMETER_VALUE, e);
		}
	}
}

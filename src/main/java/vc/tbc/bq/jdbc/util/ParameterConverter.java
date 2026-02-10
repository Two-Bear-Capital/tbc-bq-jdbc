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

import vc.tbc.bq.jdbc.exception.BQSQLException;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;

/**
 * Utility class for converting objects to specific types for PreparedStatement
 * parameters.
 *
 * <p>
 * Provides type conversion methods that handle common conversions (e.g., String
 * to Integer, Number to String) with consistent error handling. All methods
 * follow the pattern of taking an Object value and a parameter index for error
 * messages.
 *
 * @since 1.0.49
 */
public final class ParameterConverter {

	private ParameterConverter() {
		// Utility class
	}

	/**
	 * Convert an Object to a boolean value.
	 *
	 * @param value
	 *            the value to convert (Boolean, Number, or String)
	 * @param parameterIndex
	 *            the parameter index (for error messages)
	 * @return the boolean value
	 * @throws SQLException
	 *             if conversion fails
	 */
	public static boolean toBoolean(Object value, int parameterIndex) throws SQLException {
		if (value == null) {
			throw new BQSQLException("Cannot convert NULL to boolean at parameter " + parameterIndex,
					BQSQLException.SQLSTATE_INVALID_PARAMETER_VALUE);
		}

		return switch (value) {
			case Boolean b -> b;
			case Number n -> n.intValue() != 0;
			case String s -> Boolean.parseBoolean(s);
			default -> throw new BQSQLException(
					"Cannot convert " + value.getClass().getName() + " to boolean at parameter " + parameterIndex,
					BQSQLException.SQLSTATE_INVALID_PARAMETER_VALUE);
		};
	}

	/**
	 * Convert an Object to a String value.
	 *
	 * @param value
	 *            the value to convert
	 * @param parameterIndex
	 *            the parameter index (for error messages)
	 * @return the String value (null if input is null)
	 * @throws SQLException
	 *             if conversion fails
	 */
	public static String toString(Object value, int parameterIndex) throws SQLException {
		if (value == null) {
			return null;
		}
		if (value instanceof String s) {
			return s;
		}
		return value.toString();
	}

	/**
	 * Convert an Object to a byte array.
	 *
	 * @param value
	 *            the value to convert (byte[] or String)
	 * @param parameterIndex
	 *            the parameter index (for error messages)
	 * @return the byte array
	 * @throws SQLException
	 *             if conversion fails
	 */
	public static byte[] toBytes(Object value, int parameterIndex) throws SQLException {
		if (value == null) {
			return null;
		}

		return switch (value) {
			case byte[] bytes -> bytes;
			case String s -> s.getBytes();
			default -> throw new BQSQLException(
					"Cannot convert " + value.getClass().getName() + " to byte[] at parameter " + parameterIndex,
					BQSQLException.SQLSTATE_INVALID_PARAMETER_VALUE);
		};
	}

	/**
	 * Convert an Object to a Date value.
	 *
	 * @param value
	 *            the value to convert (Date, Timestamp, String, or java.util.Date)
	 * @param parameterIndex
	 *            the parameter index (for error messages)
	 * @return the Date value
	 * @throws SQLException
	 *             if conversion fails
	 */
	public static Date toDate(Object value, int parameterIndex) throws SQLException {
		if (value == null) {
			return null;
		}

		try {
			return switch (value) {
				case Date d -> d;
				case Timestamp ts -> new Date(ts.getTime());
				case String s -> Date.valueOf(s);
				case java.util.Date ud -> new Date(ud.getTime());
				default -> throw new BQSQLException(
						"Cannot convert " + value.getClass().getName() + " to Date at parameter " + parameterIndex,
						BQSQLException.SQLSTATE_INVALID_PARAMETER_VALUE);
			};
		} catch (IllegalArgumentException e) {
			throw new BQSQLException("Cannot convert '" + value + "' to Date at parameter " + parameterIndex,
					BQSQLException.SQLSTATE_INVALID_PARAMETER_VALUE, e);
		}
	}

	/**
	 * Convert an Object to a Time value.
	 *
	 * @param value
	 *            the value to convert (Time, Timestamp, String, or java.util.Date)
	 * @param parameterIndex
	 *            the parameter index (for error messages)
	 * @return the Time value
	 * @throws SQLException
	 *             if conversion fails
	 */
	public static Time toTime(Object value, int parameterIndex) throws SQLException {
		if (value == null) {
			return null;
		}

		try {
			return switch (value) {
				case Time t -> t;
				case Timestamp ts -> new Time(ts.getTime());
				case String s -> Time.valueOf(s);
				case java.util.Date ud -> new Time(ud.getTime());
				default -> throw new BQSQLException(
						"Cannot convert " + value.getClass().getName() + " to Time at parameter " + parameterIndex,
						BQSQLException.SQLSTATE_INVALID_PARAMETER_VALUE);
			};
		} catch (IllegalArgumentException e) {
			throw new BQSQLException("Cannot convert '" + value + "' to Time at parameter " + parameterIndex,
					BQSQLException.SQLSTATE_INVALID_PARAMETER_VALUE, e);
		}
	}

	/**
	 * Convert an Object to a Timestamp value.
	 *
	 * @param value
	 *            the value to convert (Timestamp, Date, Time, String, or
	 *            java.util.Date)
	 * @param parameterIndex
	 *            the parameter index (for error messages)
	 * @return the Timestamp value
	 * @throws SQLException
	 *             if conversion fails
	 */
	public static Timestamp toTimestamp(Object value, int parameterIndex) throws SQLException {
		if (value == null) {
			return null;
		}

		try {
			return switch (value) {
				case Timestamp ts -> ts;
				case Date d -> new Timestamp(d.getTime());
				case Time t -> new Timestamp(t.getTime());
				case String s -> Timestamp.valueOf(s);
				case java.util.Date ud -> new Timestamp(ud.getTime());
				default -> throw new BQSQLException(
						"Cannot convert " + value.getClass().getName() + " to Timestamp at parameter " + parameterIndex,
						BQSQLException.SQLSTATE_INVALID_PARAMETER_VALUE);
			};
		} catch (IllegalArgumentException e) {
			throw new BQSQLException("Cannot convert '" + value + "' to Timestamp at parameter " + parameterIndex,
					BQSQLException.SQLSTATE_INVALID_PARAMETER_VALUE, e);
		}
	}

	// Numeric conversions - delegate to NumberParser for consistency

	/**
	 * Convert an Object to a byte value.
	 *
	 * @param value
	 *            the value to convert (Number or String)
	 * @param parameterIndex
	 *            the parameter index (for error messages)
	 * @return the byte value (0 if null)
	 * @throws SQLException
	 *             if conversion fails
	 */
	public static byte toByte(Object value, int parameterIndex) throws SQLException {
		return NumberParser.toByte(value, parameterIndex);
	}

	/**
	 * Convert an Object to a short value.
	 *
	 * @param value
	 *            the value to convert (Number or String)
	 * @param parameterIndex
	 *            the parameter index (for error messages)
	 * @return the short value (0 if null)
	 * @throws SQLException
	 *             if conversion fails
	 */
	public static short toShort(Object value, int parameterIndex) throws SQLException {
		return NumberParser.toShort(value, parameterIndex);
	}

	/**
	 * Convert an Object to an int value.
	 *
	 * @param value
	 *            the value to convert (Number or String)
	 * @param parameterIndex
	 *            the parameter index (for error messages)
	 * @return the int value (0 if null)
	 * @throws SQLException
	 *             if conversion fails
	 */
	public static int toInt(Object value, int parameterIndex) throws SQLException {
		return NumberParser.toInt(value, parameterIndex);
	}

	/**
	 * Convert an Object to a long value.
	 *
	 * @param value
	 *            the value to convert (Number or String)
	 * @param parameterIndex
	 *            the parameter index (for error messages)
	 * @return the long value (0 if null)
	 * @throws SQLException
	 *             if conversion fails
	 */
	public static long toLong(Object value, int parameterIndex) throws SQLException {
		return NumberParser.toLong(value, parameterIndex);
	}

	/**
	 * Convert an Object to a float value.
	 *
	 * @param value
	 *            the value to convert (Number or String)
	 * @param parameterIndex
	 *            the parameter index (for error messages)
	 * @return the float value (0 if null)
	 * @throws SQLException
	 *             if conversion fails
	 */
	public static float toFloat(Object value, int parameterIndex) throws SQLException {
		return NumberParser.toFloat(value, parameterIndex);
	}

	/**
	 * Convert an Object to a double value.
	 *
	 * @param value
	 *            the value to convert (Number or String)
	 * @param parameterIndex
	 *            the parameter index (for error messages)
	 * @return the double value (0 if null)
	 * @throws SQLException
	 *             if conversion fails
	 */
	public static double toDouble(Object value, int parameterIndex) throws SQLException {
		return NumberParser.toDouble(value, parameterIndex);
	}

	/**
	 * Convert an Object to a BigDecimal value.
	 *
	 * @param value
	 *            the value to convert (BigDecimal, Number, or String)
	 * @param parameterIndex
	 *            the parameter index (for error messages)
	 * @return the BigDecimal value (null if input is null)
	 * @throws SQLException
	 *             if conversion fails
	 */
	public static BigDecimal toBigDecimal(Object value, int parameterIndex) throws SQLException {
		return NumberParser.toBigDecimal(value, parameterIndex);
	}
}
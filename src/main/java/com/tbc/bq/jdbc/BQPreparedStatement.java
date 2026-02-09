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
package com.tbc.bq.jdbc;

import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.QueryParameterValue;
import com.google.cloud.bigquery.StandardSQLTypeName;
import com.tbc.bq.jdbc.base.AbstractBQPreparedStatement;
import com.tbc.bq.jdbc.exception.BQSQLException;
import com.tbc.bq.jdbc.metadata.BQParameterMetaData;
import com.tbc.bq.jdbc.util.ErrorMessages;
import com.tbc.bq.jdbc.util.TimezoneUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.net.URL;
import java.sql.*;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * JDBC PreparedStatement implementation for BigQuery.
 *
 * @since 1.0.0
 */
public final class BQPreparedStatement extends AbstractBQPreparedStatement {

	private static final Logger logger = LoggerFactory.getLogger(BQPreparedStatement.class);

	private final String sqlTemplate;
	private final List<QueryParameterValue> parameters = new ArrayList<>();

	public BQPreparedStatement(BQConnection connection, String sql) {
		super(connection);
		this.sqlTemplate = sql;
	}

	private void validateParameterIndex(int parameterIndex) throws SQLException {
		if (parameterIndex < 1) {
			throw new BQSQLException(String.format(ErrorMessages.INVALID_PARAMETER_INDEX, parameterIndex),
					BQSQLException.SQLSTATE_INVALID_PARAMETER_VALUE);
		}
	}

	private void ensureCapacity(int parameterIndex) {
		while (parameters.size() < parameterIndex) {
			parameters.add(null);
		}
	}

	private void setParameter(int parameterIndex, QueryParameterValue value) throws SQLException {
		checkClosed();
		validateParameterIndex(parameterIndex);
		ensureCapacity(parameterIndex);
		parameters.set(parameterIndex - 1, value);
	}

	@Override
	protected QueryJobConfiguration.Builder buildQueryConfig(String sql) {
		return QueryJobConfiguration.newBuilder(sql).setUseLegacySql(properties.useLegacySql())
				.setPositionalParameters(parameters);
	}

	@Override
	protected String getLogPrefix() {
		return "Prepared query";
	}

	@Override
	public ResultSet executeQuery() throws SQLException {
		return executeQueryInternal(sqlTemplate);
	}

	@Override
	@SuppressWarnings("resource") // ResultSet managed by statement, closed in statement.close()
	public int executeUpdate() throws SQLException {
		executeQuery();
		return 0;
	}

	/**
	 * Sets the designated parameter to SQL NULL with explicit type information.
	 *
	 * <p>
	 * BigQuery requires explicit type information for NULL values because it cannot
	 * infer the type from NULL alone. The SQL type is mapped to the corresponding
	 * BigQuery StandardSQLTypeName.
	 *
	 * @param parameterIndex
	 *            the first parameter is 1, the second is 2, ...
	 * @param sqlType
	 *            the SQL type code from {@link java.sql.Types}
	 * @throws SQLException
	 *             if parameterIndex is invalid or the statement is closed
	 */
	@Override
	public void setNull(int parameterIndex, int sqlType) throws SQLException {
		// Use explicit type information for NULL values
		// This is critical because BigQuery cannot infer type from a NULL value
		StandardSQLTypeName bqType = TypeMapper.toStandardSQLTypeName(sqlType);
		setParameter(parameterIndex, QueryParameterValue.of(null, bqType));
	}

	/**
	 * Sets the designated parameter to the given Java boolean value.
	 *
	 * <p>
	 * Maps to BigQuery BOOL type. The value is converted to a QueryParameterValue
	 * with explicit type information for optimal BigQuery compatibility.
	 *
	 * @param parameterIndex
	 *            the first parameter is 1, the second is 2, ...
	 * @param x
	 *            the parameter value
	 * @throws SQLException
	 *             if parameterIndex is invalid or the statement is closed
	 */
	@Override
	public void setBoolean(int parameterIndex, boolean x) throws SQLException {
		setParameter(parameterIndex, QueryParameterValue.of(x, StandardSQLTypeName.BOOL));
	}

	/**
	 * Sets the designated parameter to the given Java byte value.
	 *
	 * <p>
	 * Maps to BigQuery INT64 type. The byte value is widened to long for BigQuery
	 * storage.
	 *
	 * @param parameterIndex
	 *            the first parameter is 1, the second is 2, ...
	 * @param x
	 *            the parameter value
	 * @throws SQLException
	 *             if parameterIndex is invalid or the statement is closed
	 */
	@Override
	public void setByte(int parameterIndex, byte x) throws SQLException {
		setParameter(parameterIndex, QueryParameterValue.of(Long.valueOf(x), StandardSQLTypeName.INT64));
	}

	/**
	 * Sets the designated parameter to the given Java short value.
	 *
	 * <p>
	 * Maps to BigQuery INT64 type. The short value is widened to long for BigQuery
	 * storage.
	 *
	 * @param parameterIndex
	 *            the first parameter is 1, the second is 2, ...
	 * @param x
	 *            the parameter value
	 * @throws SQLException
	 *             if parameterIndex is invalid or the statement is closed
	 */
	@Override
	public void setShort(int parameterIndex, short x) throws SQLException {
		setParameter(parameterIndex, QueryParameterValue.of(Long.valueOf(x), StandardSQLTypeName.INT64));
	}

	/**
	 * Sets the designated parameter to the given Java int value.
	 *
	 * <p>
	 * Maps to BigQuery INT64 type. The int value is widened to long for BigQuery
	 * storage.
	 *
	 * @param parameterIndex
	 *            the first parameter is 1, the second is 2, ...
	 * @param x
	 *            the parameter value
	 * @throws SQLException
	 *             if parameterIndex is invalid or the statement is closed
	 */
	@Override
	public void setInt(int parameterIndex, int x) throws SQLException {
		setParameter(parameterIndex, QueryParameterValue.of(Long.valueOf(x), StandardSQLTypeName.INT64));
	}

	/**
	 * Sets the designated parameter to the given Java long value.
	 *
	 * <p>
	 * Maps to BigQuery INT64 type with direct conversion.
	 *
	 * @param parameterIndex
	 *            the first parameter is 1, the second is 2, ...
	 * @param x
	 *            the parameter value
	 * @throws SQLException
	 *             if parameterIndex is invalid or the statement is closed
	 */
	@Override
	public void setLong(int parameterIndex, long x) throws SQLException {
		setParameter(parameterIndex, QueryParameterValue.of(x, StandardSQLTypeName.INT64));
	}

	/**
	 * Sets the designated parameter to the given Java float value.
	 *
	 * <p>
	 * Maps to BigQuery FLOAT64 type. The float value is widened to double for
	 * BigQuery storage.
	 *
	 * @param parameterIndex
	 *            the first parameter is 1, the second is 2, ...
	 * @param x
	 *            the parameter value
	 * @throws SQLException
	 *             if parameterIndex is invalid or the statement is closed
	 */
	@Override
	public void setFloat(int parameterIndex, float x) throws SQLException {
		// Use explicit type for better emulator compatibility
		setParameter(parameterIndex, QueryParameterValue.of(Double.valueOf(x), StandardSQLTypeName.FLOAT64));
	}

	/**
	 * Sets the designated parameter to the given Java double value.
	 *
	 * <p>
	 * Maps to BigQuery FLOAT64 type with direct conversion.
	 *
	 * @param parameterIndex
	 *            the first parameter is 1, the second is 2, ...
	 * @param x
	 *            the parameter value
	 * @throws SQLException
	 *             if parameterIndex is invalid or the statement is closed
	 */
	@Override
	public void setDouble(int parameterIndex, double x) throws SQLException {
		setParameter(parameterIndex, QueryParameterValue.of(x, StandardSQLTypeName.FLOAT64));
	}

	/**
	 * Sets the designated parameter to the given {@link java.math.BigDecimal}
	 * value.
	 *
	 * <p>
	 * Maps to BigQuery NUMERIC type. If the value is null, calls
	 * {@link #setNull(int, int)} with {@link Types#NUMERIC}.
	 *
	 * @param parameterIndex
	 *            the first parameter is 1, the second is 2, ...
	 * @param x
	 *            the parameter value (may be null)
	 * @throws SQLException
	 *             if parameterIndex is invalid or the statement is closed
	 */
	@Override
	public void setBigDecimal(int parameterIndex, BigDecimal x) throws SQLException {
		if (x == null) {
			setNull(parameterIndex, Types.NUMERIC);
		} else {
			setParameter(parameterIndex, QueryParameterValue.of(x, StandardSQLTypeName.NUMERIC));
		}
	}

	/**
	 * Sets the designated parameter to the given Java String value.
	 *
	 * <p>
	 * Maps to BigQuery STRING type. If the value is null, calls
	 * {@link #setNull(int, int)} with {@link Types#VARCHAR}.
	 *
	 * @param parameterIndex
	 *            the first parameter is 1, the second is 2, ...
	 * @param x
	 *            the parameter value (may be null)
	 * @throws SQLException
	 *             if parameterIndex is invalid or the statement is closed
	 */
	@Override
	public void setString(int parameterIndex, String x) throws SQLException {
		// Use explicit type for better emulator compatibility
		if (x == null) {
			setNull(parameterIndex, Types.VARCHAR);
		} else {
			setParameter(parameterIndex, QueryParameterValue.of(x, StandardSQLTypeName.STRING));
		}
	}

	/**
	 * Sets the designated parameter to the given Java byte array value.
	 *
	 * <p>
	 * Maps to BigQuery BYTES type. If the value is null, calls
	 * {@link #setNull(int, int)} with {@link Types#VARBINARY}.
	 *
	 * @param parameterIndex
	 *            the first parameter is 1, the second is 2, ...
	 * @param x
	 *            the parameter value (may be null)
	 * @throws SQLException
	 *             if parameterIndex is invalid or the statement is closed
	 */
	@Override
	public void setBytes(int parameterIndex, byte[] x) throws SQLException {
		// Use explicit type for better emulator compatibility
		if (x == null) {
			setNull(parameterIndex, Types.VARBINARY);
		} else {
			setParameter(parameterIndex, QueryParameterValue.of(x, StandardSQLTypeName.BYTES));
		}
	}

	/**
	 * Sets the designated parameter to the given {@link java.sql.Date} value.
	 *
	 * <p>
	 * Maps to BigQuery DATE type. The date is converted to ISO-8601 format
	 * (yyyy-MM-dd). If the value is null, calls {@link #setNull(int, int)} with
	 * {@link Types#DATE}.
	 *
	 * @param parameterIndex
	 *            the first parameter is 1, the second is 2, ...
	 * @param x
	 *            the parameter value (may be null)
	 * @throws SQLException
	 *             if parameterIndex is invalid or the statement is closed
	 */
	@Override
	public void setDate(int parameterIndex, Date x) throws SQLException {
		if (x == null) {
			setNull(parameterIndex, Types.DATE);
		} else {
			setParameter(parameterIndex, QueryParameterValue.of(x.toString(), StandardSQLTypeName.DATE));
		}
	}

	/**
	 * Sets the designated parameter to the given {@link java.sql.Time} value.
	 *
	 * <p>
	 * Maps to BigQuery TIME type. The time is converted to ISO-8601 format
	 * (HH:mm:ss[.SSS]). If the value is null, calls {@link #setNull(int, int)} with
	 * {@link Types#TIME}.
	 *
	 * @param parameterIndex
	 *            the first parameter is 1, the second is 2, ...
	 * @param x
	 *            the parameter value (may be null)
	 * @throws SQLException
	 *             if parameterIndex is invalid or the statement is closed
	 */
	@Override
	public void setTime(int parameterIndex, Time x) throws SQLException {
		if (x == null) {
			setNull(parameterIndex, Types.TIME);
		} else {
			setParameter(parameterIndex, QueryParameterValue.of(x.toString(), StandardSQLTypeName.TIME));
		}
	}

	/**
	 * Sets the designated parameter to the given {@link java.sql.Timestamp} value.
	 *
	 * <p>
	 * Maps to BigQuery TIMESTAMP type. The timestamp is converted to an Instant in
	 * UTC and formatted as ISO-8601 (yyyy-MM-dd'T'HH:mm:ss[.SSS]'Z'). If the value
	 * is null, calls {@link #setNull(int, int)} with {@link Types#TIMESTAMP}.
	 *
	 * @param parameterIndex
	 *            the first parameter is 1, the second is 2, ...
	 * @param x
	 *            the parameter value (may be null)
	 * @throws SQLException
	 *             if parameterIndex is invalid or the statement is closed
	 */
	@Override
	public void setTimestamp(int parameterIndex, Timestamp x) throws SQLException {
		if (x == null) {
			setNull(parameterIndex, Types.TIMESTAMP);
		} else {
			setParameter(parameterIndex,
					QueryParameterValue.of(x.toInstant().toString(), StandardSQLTypeName.TIMESTAMP));
		}
	}

	/**
	 * Clears all previously set parameter values.
	 *
	 * <p>
	 * After calling this method, all parameters must be set again before executing
	 * the prepared statement.
	 *
	 * @throws SQLException
	 *             if the statement is closed
	 */
	@Override
	public void clearParameters() throws SQLException {
		checkClosed();
		parameters.clear();
	}

	/**
	 * Sets the designated parameter to the given Java object value with target SQL
	 * type.
	 *
	 * <p>
	 * The targetSqlType parameter is ignored; this method delegates to
	 * {@link #setObject(int, Object)}.
	 *
	 * @param parameterIndex
	 *            the first parameter is 1, the second is 2, ...
	 * @param x
	 *            the parameter value (may be null)
	 * @param targetSqlType
	 *            the SQL type (ignored)
	 * @throws SQLException
	 *             if parameterIndex is invalid, the statement is closed, or the
	 *             object type is unsupported
	 */
	@Override
	public void setObject(int parameterIndex, Object x, int targetSqlType) throws SQLException {
		setObject(parameterIndex, x);
	}

	/**
	 * Sets the designated parameter to the given Java object value.
	 *
	 * <p>
	 * The driver infers the BigQuery type from the Java object type:
	 * <ul>
	 * <li>{@link String} → STRING
	 * <li>{@link Integer}, {@link Long} → INT64
	 * <li>{@link Float}, {@link Double} → FLOAT64
	 * <li>{@link Boolean} → BOOL
	 * <li>{@link BigDecimal} → NUMERIC
	 * <li>{@link Timestamp} → TIMESTAMP (converted to UTC Instant)
	 * <li>{@link Date} → DATE
	 * <li>{@link Time} → TIME
	 * <li>{@code byte[]} → BYTES
	 * </ul>
	 *
	 * <p>
	 * If the value is null, calls {@link #setNull(int, int)} with
	 * {@link Types#NULL}.
	 *
	 * @param parameterIndex
	 *            the first parameter is 1, the second is 2, ...
	 * @param x
	 *            the parameter value (may be null)
	 * @throws SQLException
	 *             if parameterIndex is invalid, the statement is closed, or the
	 *             object type is unsupported
	 */
	@Override
	public void setObject(int parameterIndex, Object x) throws SQLException {
		checkClosed();
		if (x == null) {
			setNull(parameterIndex, Types.NULL);
			return;
		}

		// Use explicit type information for better compatibility with BigQuery
		// emulators
		switch (x) {
			case String s -> setParameter(parameterIndex, QueryParameterValue.of(s, StandardSQLTypeName.STRING));
			case Integer i ->
				setParameter(parameterIndex, QueryParameterValue.of(Long.valueOf(i), StandardSQLTypeName.INT64));
			case Long l -> setParameter(parameterIndex, QueryParameterValue.of(l, StandardSQLTypeName.INT64));
			case Float f ->
				setParameter(parameterIndex, QueryParameterValue.of(Double.valueOf(f), StandardSQLTypeName.FLOAT64));
			case Double d -> setParameter(parameterIndex, QueryParameterValue.of(d, StandardSQLTypeName.FLOAT64));
			case Boolean b -> setParameter(parameterIndex, QueryParameterValue.of(b, StandardSQLTypeName.BOOL));
			case BigDecimal bd -> setParameter(parameterIndex, QueryParameterValue.of(bd, StandardSQLTypeName.NUMERIC));
			case Timestamp ts -> setParameter(parameterIndex,
					QueryParameterValue.of(ts.toInstant().toString(), StandardSQLTypeName.TIMESTAMP));
			case Date dt ->
				setParameter(parameterIndex, QueryParameterValue.of(dt.toString(), StandardSQLTypeName.DATE));
			case Time t -> setParameter(parameterIndex, QueryParameterValue.of(t.toString(), StandardSQLTypeName.TIME));
			case byte[] bytes -> setParameter(parameterIndex, QueryParameterValue.of(bytes, StandardSQLTypeName.BYTES));
			default -> throw new SQLException("Unsupported parameter type: " + x.getClass().getName());
		}
	}

	@Override
	@SuppressWarnings("resource") // ResultSet managed by statement, closed in statement.close()
	public boolean execute() throws SQLException {
		executeQuery();
		return true;
	}

	@Override
	public ResultSetMetaData getMetaData() throws SQLException {
		if (currentResultSet != null) {
			return currentResultSet.getMetaData();
		}
		return null;
	}

	/**
	 * Sets the designated parameter to the given {@link java.sql.Date} value using
	 * the specified Calendar.
	 *
	 * <p>
	 * The Calendar is used to interpret the date in a specific timezone. The date
	 * is adjusted from the Calendar's timezone to UTC before storing in BigQuery.
	 * If the Calendar is null, this method behaves the same as
	 * {@link #setDate(int, Date)}.
	 *
	 * @param parameterIndex
	 *            the first parameter is 1, the second is 2, ...
	 * @param x
	 *            the parameter value (may be null)
	 * @param cal
	 *            the Calendar to use for timezone interpretation (may be null)
	 * @throws SQLException
	 *             if parameterIndex is invalid or the statement is closed
	 */
	@Override
	public void setDate(int parameterIndex, Date x, Calendar cal) throws SQLException {
		if (x == null) {
			setNull(parameterIndex, Types.DATE);
			return;
		}

		if (cal == null) {
			setDate(parameterIndex, x);
			return;
		}

		// Use utility to adjust date for Calendar's timezone
		Date adjustedDate = TimezoneUtils.adjustDateToCalendar(x, cal);
		setDate(parameterIndex, adjustedDate);
	}

	/**
	 * Sets the designated parameter to the given {@link java.sql.Time} value using
	 * the specified Calendar.
	 *
	 * <p>
	 * The Calendar is used to interpret the time in a specific timezone. The time
	 * is adjusted from the Calendar's timezone to UTC before storing in BigQuery.
	 * If the Calendar is null, this method behaves the same as
	 * {@link #setTime(int, Time)}.
	 *
	 * @param parameterIndex
	 *            the first parameter is 1, the second is 2, ...
	 * @param x
	 *            the parameter value (may be null)
	 * @param cal
	 *            the Calendar to use for timezone interpretation (may be null)
	 * @throws SQLException
	 *             if parameterIndex is invalid or the statement is closed
	 */
	@Override
	public void setTime(int parameterIndex, Time x, Calendar cal) throws SQLException {
		if (x == null) {
			setNull(parameterIndex, Types.TIME);
			return;
		}

		if (cal == null) {
			setTime(parameterIndex, x);
			return;
		}

		// Use utility to adjust time for Calendar's timezone
		Time adjustedTime = TimezoneUtils.adjustTimeToCalendar(x, cal);
		setTime(parameterIndex, adjustedTime);
	}

	/**
	 * Sets the designated parameter to the given {@link java.sql.Timestamp} value
	 * using the specified Calendar.
	 *
	 * <p>
	 * The Calendar is used to interpret the timestamp in a specific timezone. The
	 * timestamp is adjusted from the Calendar's timezone to UTC before storing in
	 * BigQuery. Nanosecond precision is preserved during the conversion. If the
	 * Calendar is null, this method behaves the same as
	 * {@link #setTimestamp(int, Timestamp)}.
	 *
	 * @param parameterIndex
	 *            the first parameter is 1, the second is 2, ...
	 * @param x
	 *            the parameter value (may be null)
	 * @param cal
	 *            the Calendar to use for timezone interpretation (may be null)
	 * @throws SQLException
	 *             if parameterIndex is invalid or the statement is closed
	 */
	@Override
	public void setTimestamp(int parameterIndex, Timestamp x, Calendar cal) throws SQLException {
		if (x == null) {
			setNull(parameterIndex, Types.TIMESTAMP);
			return;
		}

		if (cal == null) {
			setTimestamp(parameterIndex, x);
			return;
		}

		// Adjust timestamp for Calendar's timezone and convert to Instant
		Timestamp adjusted = TimezoneUtils.adjustTimestampToCalendar(x, cal);

		// Create instant from adjusted timestamp
		// Add back the nanosecond precision that was lost in millisecond conversion
		java.time.Instant instant = java.time.Instant.ofEpochMilli(adjusted.getTime())
				.plusNanos(adjusted.getNanos() % 1000000);

		// Set parameter using instant string representation with explicit type
		setParameter(parameterIndex, QueryParameterValue.of(instant.toString(), StandardSQLTypeName.TIMESTAMP));
	}

	/**
	 * Sets the designated parameter to SQL NULL with type name.
	 *
	 * <p>
	 * The typeName parameter is ignored; this method delegates to
	 * {@link #setNull(int, int)}.
	 *
	 * @param parameterIndex
	 *            the first parameter is 1, the second is 2, ...
	 * @param sqlType
	 *            the SQL type code from {@link java.sql.Types}
	 * @param typeName
	 *            the fully-qualified name of an SQL user-defined type (ignored)
	 * @throws SQLException
	 *             if parameterIndex is invalid or the statement is closed
	 */
	@Override
	public void setNull(int parameterIndex, int sqlType, String typeName) throws SQLException {
		setNull(parameterIndex, sqlType);
	}

	/**
	 * Sets the designated parameter to the given {@link java.net.URL} value.
	 *
	 * <p>
	 * The URL is converted to a string representation and stored as a BigQuery
	 * STRING type.
	 *
	 * @param parameterIndex
	 *            the first parameter is 1, the second is 2, ...
	 * @param x
	 *            the parameter value
	 * @throws SQLException
	 *             if parameterIndex is invalid or the statement is closed
	 */
	@Override
	public void setURL(int parameterIndex, URL x) throws SQLException {
		setString(parameterIndex, x.toString());
	}

	@Override
	public ParameterMetaData getParameterMetaData() throws SQLException {
		checkClosed();
		return new BQParameterMetaData(parameters.size());
	}

	/**
	 * Sets the designated parameter to the given String value (NCHAR support).
	 *
	 * <p>
	 * This method behaves identically to {@link #setString(int, String)} since
	 * BigQuery STRING type natively supports Unicode characters.
	 *
	 * @param parameterIndex
	 *            the first parameter is 1, the second is 2, ...
	 * @param value
	 *            the parameter value (may be null)
	 * @throws SQLException
	 *             if parameterIndex is invalid or the statement is closed
	 */
	@Override
	public void setNString(int parameterIndex, String value) throws SQLException {
		setString(parameterIndex, value);
	}

	/**
	 * Sets the designated parameter to the given Java object with target SQL type
	 * and scale/length.
	 *
	 * <p>
	 * The targetSqlType and scaleOrLength parameters are ignored; this method
	 * delegates to {@link #setObject(int, Object)}.
	 *
	 * @param parameterIndex
	 *            the first parameter is 1, the second is 2, ...
	 * @param x
	 *            the parameter value (may be null)
	 * @param targetSqlType
	 *            the SQL type (ignored)
	 * @param scaleOrLength
	 *            the scale or length (ignored)
	 * @throws SQLException
	 *             if parameterIndex is invalid, the statement is closed, or the
	 *             object type is unsupported
	 */
	@Override
	public void setObject(int parameterIndex, Object x, int targetSqlType, int scaleOrLength) throws SQLException {
		setObject(parameterIndex, x);
	}

	/**
	 * Sets the designated parameter to the given Java object with target SQLType
	 * and scale/length.
	 *
	 * <p>
	 * The targetSqlType and scaleOrLength parameters are ignored; this method
	 * delegates to {@link #setObject(int, Object)}.
	 *
	 * @param parameterIndex
	 *            the first parameter is 1, the second is 2, ...
	 * @param x
	 *            the parameter value (may be null)
	 * @param targetSqlType
	 *            the SQLType (ignored)
	 * @param scaleOrLength
	 *            the scale or length (ignored)
	 * @throws SQLException
	 *             if parameterIndex is invalid, the statement is closed, or the
	 *             object type is unsupported
	 */
	@Override
	public void setObject(int parameterIndex, Object x, SQLType targetSqlType, int scaleOrLength) throws SQLException {
		setObject(parameterIndex, x);
	}

	/**
	 * Sets the designated parameter to the given Java object with target SQLType.
	 *
	 * <p>
	 * The targetSqlType parameter is ignored; this method delegates to
	 * {@link #setObject(int, Object)}.
	 *
	 * @param parameterIndex
	 *            the first parameter is 1, the second is 2, ...
	 * @param x
	 *            the parameter value (may be null)
	 * @param targetSqlType
	 *            the SQLType (ignored)
	 * @throws SQLException
	 *             if parameterIndex is invalid, the statement is closed, or the
	 *             object type is unsupported
	 */
	@Override
	public void setObject(int parameterIndex, Object x, SQLType targetSqlType) throws SQLException {
		setObject(parameterIndex, x);
	}

	@Override
	public long executeLargeUpdate() throws SQLException {
		executeUpdate();
		return 0L;
	}
}

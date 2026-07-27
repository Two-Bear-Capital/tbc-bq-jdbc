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
package vc.tbc.bq.jdbc;

import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.QueryParameterValue;
import com.google.cloud.bigquery.StandardSQLTypeName;
import vc.tbc.bq.jdbc.base.AbstractBQPreparedStatement;
import vc.tbc.bq.jdbc.exception.BQSQLException;
import vc.tbc.bq.jdbc.metadata.BQParameterMetaData;
import vc.tbc.bq.jdbc.util.BatchInsertRewriter;
import vc.tbc.bq.jdbc.util.ErrorMessages;
import vc.tbc.bq.jdbc.util.ParameterConverter;
import vc.tbc.bq.jdbc.util.TimezoneUtils;

import java.math.BigDecimal;
import java.net.URL;
import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Optional;

/**
 * JDBC PreparedStatement implementation for BigQuery.
 *
 * @since 1.0.0
 */
public final class BQPreparedStatement extends AbstractBQPreparedStatement {

	private final String sqlTemplate;
	private final List<QueryParameterValue> parameters = new ArrayList<>();

	/** Parameter sets accumulated via {@link #addBatch()} for batch execution. */
	private final List<List<QueryParameterValue>> batchParameterSets = new ArrayList<>();

	public BQPreparedStatement(BQConnection connection, String sql) {
		super(connection);
		this.sqlTemplate = sql;
	}

	/**
	 * Formatter for TIME parameters. {@code QueryParameterValue.time} requires
	 * exactly six fractional digits; {@code Time.toString()} emits none, which made
	 * every {@code setTime} call throw (#123).
	 */
	private static final java.time.format.DateTimeFormatter TIME_FORMATTER = java.time.format.DateTimeFormatter
			.ofPattern("HH:mm:ss.SSSSSS");

	/**
	 * Binds a Timestamp as epoch microseconds.
	 *
	 * <p>
	 * {@code QueryParameterValue.of(instant.toString(), TIMESTAMP)} was rejected
	 * client-side — the validator wants a space-separated format, not ISO-8601's
	 * {@code T} separator, so every call threw (#123). The typed factory takes
	 * microseconds and does its own formatting, which removes the question.
	 *
	 * @param value
	 *            the timestamp to bind
	 * @return a TIMESTAMP query parameter
	 */
	private static QueryParameterValue timestampParameter(Timestamp value) {
		java.time.Instant instant = value.toInstant();
		long micros = instant.getEpochSecond() * 1_000_000L + instant.getNano() / 1_000L;
		return QueryParameterValue.timestamp(micros);
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

	/**
	 * Executes the prepared DML statement and returns the number of affected rows,
	 * taken from BigQuery's DML job statistics ({@code numDmlAffectedRows}).
	 * Returns 0 for statements that carry no DML statistics (DDL, SELECT), per the
	 * JDBC contract for statements that return nothing.
	 */
	@Override
	public int executeUpdate() throws SQLException {
		long count = executeLargeUpdate();
		return count > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) count;
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
		setParameter(parameterIndex, QueryParameterValue.of((long) x, StandardSQLTypeName.INT64));
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
		setParameter(parameterIndex, QueryParameterValue.of((long) x, StandardSQLTypeName.INT64));
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
		setParameter(parameterIndex, QueryParameterValue.of((long) x, StandardSQLTypeName.INT64));
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
		setParameter(parameterIndex, QueryParameterValue.of((double) x, StandardSQLTypeName.FLOAT64));
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
			setParameter(parameterIndex, QueryParameterValue.time(TIME_FORMATTER.format(x.toLocalTime())));
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
			setParameter(parameterIndex, timestampParameter(x));
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
	 * When a targetSqlType is provided, the driver attempts to convert the object
	 * to the target type before setting the parameter. This allows type conversions
	 * such as String to Integer. Type conversion is handled by
	 * {@link ParameterConverter}.
	 *
	 * @param parameterIndex
	 *            the first parameter is 1, the second is 2, ...
	 * @param x
	 *            the parameter value (may be null)
	 * @param targetSqlType
	 *            the SQL type from {@link java.sql.Types}
	 * @throws SQLException
	 *             if parameterIndex is invalid, the statement is closed, or type
	 *             conversion fails
	 */
	@Override
	public void setObject(int parameterIndex, Object x, int targetSqlType) throws SQLException {
		checkClosed();
		if (x == null) {
			setNull(parameterIndex, targetSqlType);
			return;
		}

		// Convert the object to the target SQL type using ParameterConverter utility
		switch (targetSqlType) {
			case Types.BIT, Types.BOOLEAN ->
				setBoolean(parameterIndex, ParameterConverter.toBoolean(x, parameterIndex));
			case Types.TINYINT -> setByte(parameterIndex, ParameterConverter.toByte(x, parameterIndex));
			case Types.SMALLINT -> setShort(parameterIndex, ParameterConverter.toShort(x, parameterIndex));
			case Types.INTEGER -> setInt(parameterIndex, ParameterConverter.toInt(x, parameterIndex));
			case Types.BIGINT -> setLong(parameterIndex, ParameterConverter.toLong(x, parameterIndex));
			case Types.REAL -> setFloat(parameterIndex, ParameterConverter.toFloat(x, parameterIndex));
			case Types.FLOAT, Types.DOUBLE -> setDouble(parameterIndex, ParameterConverter.toDouble(x, parameterIndex));
			case Types.DECIMAL, Types.NUMERIC ->
				setBigDecimal(parameterIndex, ParameterConverter.toBigDecimal(x, parameterIndex));
			case Types.CHAR, Types.VARCHAR, Types.LONGVARCHAR, Types.NCHAR, Types.NVARCHAR, Types.LONGNVARCHAR ->
				setString(parameterIndex, ParameterConverter.toString(x));
			case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY ->
				setBytes(parameterIndex, ParameterConverter.toBytes(x, parameterIndex));
			case Types.DATE -> setDate(parameterIndex, ParameterConverter.toDate(x, parameterIndex));
			case Types.TIME, Types.TIME_WITH_TIMEZONE ->
				setTime(parameterIndex, ParameterConverter.toTime(x, parameterIndex));
			case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE ->
				setTimestamp(parameterIndex, ParameterConverter.toTimestamp(x, parameterIndex));
			default -> setObject(parameterIndex, x);
		}
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
			case java.sql.Array a -> setArray(parameterIndex, a);
			case java.util.List<?> list -> setListParameter(parameterIndex, list);
			default -> throw new SQLException("Unsupported parameter type: " + x.getClass().getName());
		}
	}

	@Override
	public void setArray(int parameterIndex, java.sql.Array x) throws SQLException {
		checkClosed();
		if (x == null) {
			setNull(parameterIndex, java.sql.Types.ARRAY);
			return;
		}
		Object[] arr = (Object[]) x.getArray();
		StandardSQLTypeName elemType = TypeMapper.toStandardSQLTypeName(x.getBaseType());
		QueryParameterValue[] paramValues = new QueryParameterValue[arr.length];
		for (int i = 0; i < arr.length; i++) {
			Object elem = arr[i];
			if (elem == null) {
				paramValues[i] = QueryParameterValue.of(null, elemType);
			} else {
				paramValues[i] = QueryParameterValue.of(elem.toString(), elemType);
			}
		}
		setParameter(parameterIndex, QueryParameterValue.array(paramValues, elemType));
	}

	private void setListParameter(int parameterIndex, java.util.List<?> list) throws SQLException {
		if (list.isEmpty()) {
			setParameter(parameterIndex,
					QueryParameterValue.array(new QueryParameterValue[0], StandardSQLTypeName.STRING));
			return;
		}
		// Infer element type from first non-null element
		Object first = list.stream().filter(e -> e != null).findFirst().orElse(null);
		StandardSQLTypeName elemType = inferSqlType(first);
		QueryParameterValue[] paramValues = new QueryParameterValue[list.size()];
		for (int i = 0; i < list.size(); i++) {
			Object elem = list.get(i);
			if (elem == null) {
				paramValues[i] = QueryParameterValue.of(null, elemType);
			} else {
				paramValues[i] = QueryParameterValue.of(elem.toString(), elemType);
			}
		}
		setParameter(parameterIndex, QueryParameterValue.array(paramValues, elemType));
	}

	private static StandardSQLTypeName inferSqlType(Object obj) {
		if (obj instanceof String)
			return StandardSQLTypeName.STRING;
		if (obj instanceof Long || obj instanceof Integer)
			return StandardSQLTypeName.INT64;
		if (obj instanceof Double || obj instanceof Float)
			return StandardSQLTypeName.FLOAT64;
		if (obj instanceof Boolean)
			return StandardSQLTypeName.BOOL;
		if (obj instanceof java.math.BigDecimal)
			return StandardSQLTypeName.NUMERIC;
		if (obj instanceof java.sql.Timestamp)
			return StandardSQLTypeName.TIMESTAMP;
		if (obj instanceof java.sql.Date)
			return StandardSQLTypeName.DATE;
		if (obj instanceof java.sql.Time)
			return StandardSQLTypeName.TIME;
		return StandardSQLTypeName.STRING;
	}

	/**
	 * Executes the prepared statement, which may return multiple types of results.
	 *
	 * <p>
	 * Returns {@code false} when BigQuery reports the statement was DML — retrieve
	 * the affected-row count via {@link #getUpdateCount()}. Returns {@code true}
	 * otherwise — retrieve results via {@link #getResultSet()}.
	 */
	@Override
	@SuppressWarnings("resource") // ResultSet managed by statement, closed in statement.close()
	public boolean execute() throws SQLException {
		executeQuery();
		return !finishExecuteAsUpdateIfDml();
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

		// Reinterpret the wall clock as belonging to the Calendar's zone (#121)
		Date adjustedDate = TimezoneUtils.dateToCalendarZone(x, cal);
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

		// Reinterpret the wall clock as belonging to the Calendar's zone (#121)
		Time adjustedTime = TimezoneUtils.timeToCalendarZone(x, cal);
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

		// Reinterpret the wall clock as belonging to the Calendar's zone (#121)
		Timestamp adjusted = TimezoneUtils.timestampToCalendarZone(x, cal);
		setParameter(parameterIndex, timestampParameter(adjusted));
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

	/**
	 * Large-count variant of {@link #executeUpdate()}.
	 */
	@Override
	public long executeLargeUpdate() throws SQLException {
		long affectedRows = executeDmlInternal(sqlTemplate, parameters);
		// JDBC: 0 for statements that return nothing (DDL, or no DML statistics)
		long count = Math.max(0L, affectedRows);
		currentUpdateCount = count;
		return count;
	}

	// Batch operations

	/**
	 * Adds the current parameter set to this statement's batch.
	 *
	 * <p>
	 * The parameter set is snapshotted and the working parameters are cleared, so
	 * all parameters must be set again before the next {@code addBatch()} call.
	 *
	 * @throws SQLException
	 *             if the statement is closed
	 */
	@Override
	public void addBatch() throws SQLException {
		checkClosed();
		batchParameterSets.add(new ArrayList<>(parameters));
		parameters.clear();
	}

	/**
	 * Discards all parameter sets accumulated via {@link #addBatch()}.
	 *
	 * @throws SQLException
	 *             if the statement is closed
	 */
	@Override
	public void clearBatch() throws SQLException {
		checkClosed();
		batchParameterSets.clear();
	}

	/**
	 * Executes the batched parameter sets against BigQuery.
	 *
	 * <p>
	 * When the SQL template is a simple single-row parameterized INSERT
	 * ({@code INSERT INTO t (...) VALUES (?, ...)}), the batch is collapsed into
	 * multi-row {@code INSERT ... VALUES (...), (...), ...} statements — the moral
	 * equivalent of PostgreSQL's {@code reWriteBatchedInserts} and the only DML
	 * shape that performs acceptably on BigQuery. Large batches are chunked to stay
	 * under BigQuery's per-query limits (10,000 query parameters, ~1 MB query
	 * text), one query job per chunk.
	 *
	 * <p>
	 * Statements that cannot be collapsed (non-INSERT DML,
	 * {@code INSERT ... SELECT}, tuples mixing literals with placeholders) are
	 * executed sequentially, one job per parameter set.
	 *
	 * <p>
	 * The batch is cleared once execution completes or fails. If execution fails, a
	 * {@link BatchUpdateException} is thrown containing the update counts of the
	 * parameter sets that completed before the failure.
	 *
	 * @return per-row update counts: the affected-row count when BigQuery reports
	 *         one, otherwise {@link #SUCCESS_NO_INFO}
	 * @throws SQLException
	 *             if the statement is closed
	 * @throws BatchUpdateException
	 *             if any part of the batch fails
	 */
	@Override
	public int[] executeBatch() throws SQLException {
		checkClosed();
		if (batchParameterSets.isEmpty()) {
			return new int[0];
		}
		List<List<QueryParameterValue>> parameterSets = new ArrayList<>(batchParameterSets);
		batchParameterSets.clear();

		Optional<BatchInsertRewriter.RewritableInsert> rewritable = BatchInsertRewriter.parse(sqlTemplate);
		if (rewritable.isPresent() && allSetsMatchTemplate(rewritable.get().parametersPerRow(), parameterSets)) {
			return executeCollapsedBatch(rewritable.get(), parameterSets);
		}
		return executeSequentialBatch(parameterSets);
	}

	/**
	 * Verifies every batched parameter set has exactly the number of parameters the
	 * INSERT template expects. Collapsing misaligned sets would silently shift
	 * values across rows; such batches fall back to sequential execution, where
	 * BigQuery reports an accurate per-statement error.
	 */
	private static boolean allSetsMatchTemplate(int parametersPerRow, List<List<QueryParameterValue>> parameterSets) {
		return parameterSets.stream().allMatch(set -> set.size() == parametersPerRow);
	}

	/**
	 * Executes the batch as chunked multi-row INSERT query jobs.
	 */
	private int[] executeCollapsedBatch(BatchInsertRewriter.RewritableInsert insert,
			List<List<QueryParameterValue>> parameterSets) throws SQLException {
		int totalRows = parameterSets.size();
		int maxRowsPerChunk = insert.maxRowsPerChunk();
		int[] updateCounts = new int[totalRows];
		int completedRows = 0;

		while (completedRows < totalRows) {
			int chunkRows = Math.min(maxRowsPerChunk, totalRows - completedRows);
			String chunkSql = insert.buildSql(chunkRows);
			List<QueryParameterValue> chunkParameters = new ArrayList<>(chunkRows * insert.parametersPerRow());
			for (int row = completedRows; row < completedRows + chunkRows; row++) {
				chunkParameters.addAll(parameterSets.get(row));
			}

			long affectedRows;
			try {
				affectedRows = executeDmlInternal(chunkSql, chunkParameters);
			} catch (SQLException e) {
				throw new BatchUpdateException(
						"Batch INSERT failed on rows " + completedRows + "-" + (completedRows + chunkRows - 1) + ": "
								+ e.getMessage(),
						e.getSQLState(), e.getErrorCode(), Arrays.copyOf(updateCounts, completedRows), e);
			}

			// Only claim per-row success when BigQuery confirms the expected count
			int perRowCount = affectedRows == chunkRows ? 1 : SUCCESS_NO_INFO;
			Arrays.fill(updateCounts, completedRows, completedRows + chunkRows, perRowCount);
			completedRows += chunkRows;
		}
		return updateCounts;
	}

	/**
	 * Executes the batch sequentially, one query job per parameter set. Fallback
	 * for statements that cannot be collapsed into a multi-row INSERT.
	 */
	private int[] executeSequentialBatch(List<List<QueryParameterValue>> parameterSets) throws SQLException {
		int[] updateCounts = new int[parameterSets.size()];
		for (int i = 0; i < parameterSets.size(); i++) {
			try {
				updateCounts[i] = toUpdateCount(executeDmlInternal(sqlTemplate, parameterSets.get(i)));
			} catch (SQLException e) {
				throw new BatchUpdateException("Batch entry " + i + " failed: " + e.getMessage(), e.getSQLState(),
						e.getErrorCode(), Arrays.copyOf(updateCounts, i), e);
			}
		}
		return updateCounts;
	}
}

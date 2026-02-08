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

	@Override
	public void setNull(int parameterIndex, int sqlType) throws SQLException {
		// Use explicit type information for NULL values
		// This is critical because BigQuery cannot infer type from a NULL value
		StandardSQLTypeName bqType = TypeMapper.toStandardSQLTypeName(sqlType);
		setParameter(parameterIndex, QueryParameterValue.of(null, bqType));
	}

	@Override
	public void setBoolean(int parameterIndex, boolean x) throws SQLException {
		setParameter(parameterIndex, QueryParameterValue.of(x, StandardSQLTypeName.BOOL));
	}

	@Override
	public void setByte(int parameterIndex, byte x) throws SQLException {
		setParameter(parameterIndex, QueryParameterValue.of(Long.valueOf(x), StandardSQLTypeName.INT64));
	}

	@Override
	public void setShort(int parameterIndex, short x) throws SQLException {
		setParameter(parameterIndex, QueryParameterValue.of(Long.valueOf(x), StandardSQLTypeName.INT64));
	}

	@Override
	public void setInt(int parameterIndex, int x) throws SQLException {
		setParameter(parameterIndex, QueryParameterValue.of(Long.valueOf(x), StandardSQLTypeName.INT64));
	}

	@Override
	public void setLong(int parameterIndex, long x) throws SQLException {
		setParameter(parameterIndex, QueryParameterValue.of(x, StandardSQLTypeName.INT64));
	}

	@Override
	public void setFloat(int parameterIndex, float x) throws SQLException {
		// Use explicit type for better emulator compatibility
		setParameter(parameterIndex, QueryParameterValue.of(Double.valueOf(x), StandardSQLTypeName.FLOAT64));
	}

	@Override
	public void setDouble(int parameterIndex, double x) throws SQLException {
		setParameter(parameterIndex, QueryParameterValue.of(x, StandardSQLTypeName.FLOAT64));
	}

	@Override
	public void setBigDecimal(int parameterIndex, BigDecimal x) throws SQLException {
		if (x == null) {
			setNull(parameterIndex, Types.NUMERIC);
		} else {
			setParameter(parameterIndex, QueryParameterValue.of(x, StandardSQLTypeName.NUMERIC));
		}
	}

	@Override
	public void setString(int parameterIndex, String x) throws SQLException {
		// Use explicit type for better emulator compatibility
		if (x == null) {
			setNull(parameterIndex, Types.VARCHAR);
		} else {
			setParameter(parameterIndex, QueryParameterValue.of(x, StandardSQLTypeName.STRING));
		}
	}

	@Override
	public void setBytes(int parameterIndex, byte[] x) throws SQLException {
		// Use explicit type for better emulator compatibility
		if (x == null) {
			setNull(parameterIndex, Types.VARBINARY);
		} else {
			setParameter(parameterIndex, QueryParameterValue.of(x, StandardSQLTypeName.BYTES));
		}
	}

	@Override
	public void setDate(int parameterIndex, Date x) throws SQLException {
		if (x == null) {
			setNull(parameterIndex, Types.DATE);
		} else {
			setParameter(parameterIndex, QueryParameterValue.of(x.toString(), StandardSQLTypeName.DATE));
		}
	}

	@Override
	public void setTime(int parameterIndex, Time x) throws SQLException {
		if (x == null) {
			setNull(parameterIndex, Types.TIME);
		} else {
			setParameter(parameterIndex, QueryParameterValue.of(x.toString(), StandardSQLTypeName.TIME));
		}
	}

	@Override
	public void setTimestamp(int parameterIndex, Timestamp x) throws SQLException {
		if (x == null) {
			setNull(parameterIndex, Types.TIMESTAMP);
		} else {
			setParameter(parameterIndex,
					QueryParameterValue.of(x.toInstant().toString(), StandardSQLTypeName.TIMESTAMP));
		}
	}

	@Override
	public void clearParameters() throws SQLException {
		checkClosed();
		parameters.clear();
	}

	@Override
	public void setObject(int parameterIndex, Object x, int targetSqlType) throws SQLException {
		setObject(parameterIndex, x);
	}

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

	@Override
	public void setNull(int parameterIndex, int sqlType, String typeName) throws SQLException {
		setNull(parameterIndex, sqlType);
	}

	@Override
	public void setURL(int parameterIndex, URL x) throws SQLException {
		setString(parameterIndex, x.toString());
	}

	@Override
	public ParameterMetaData getParameterMetaData() throws SQLException {
		checkClosed();
		return new BQParameterMetaData(parameters.size());
	}

	@Override
	public void setNString(int parameterIndex, String value) throws SQLException {
		setString(parameterIndex, value);
	}

	@Override
	public void setObject(int parameterIndex, Object x, int targetSqlType, int scaleOrLength) throws SQLException {
		setObject(parameterIndex, x);
	}

	@Override
	public void setObject(int parameterIndex, Object x, SQLType targetSqlType, int scaleOrLength) throws SQLException {
		setObject(parameterIndex, x);
	}

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

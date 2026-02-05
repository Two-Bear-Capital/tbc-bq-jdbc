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
package com.twobearcapital.bigquery.jdbc;

import com.google.cloud.bigquery.*;
import com.twobearcapital.bigquery.jdbc.base.AbstractBQPreparedStatement;
import com.twobearcapital.bigquery.jdbc.exception.BQSQLException;
import com.twobearcapital.bigquery.jdbc.metadata.BQParameterMetaData;
import com.twobearcapital.bigquery.jdbc.util.ErrorMessages;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.*;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
		setParameter(parameterIndex, QueryParameterValue.string(null));
	}

	@Override
	public void setBoolean(int parameterIndex, boolean x) throws SQLException {
		setParameter(parameterIndex, QueryParameterValue.bool(x));
	}

	@Override
	public void setByte(int parameterIndex, byte x) throws SQLException {
		setParameter(parameterIndex, QueryParameterValue.int64((long) x));
	}

	@Override
	public void setShort(int parameterIndex, short x) throws SQLException {
		setParameter(parameterIndex, QueryParameterValue.int64((long) x));
	}

	@Override
	public void setInt(int parameterIndex, int x) throws SQLException {
		setParameter(parameterIndex, QueryParameterValue.int64((long) x));
	}

	@Override
	public void setLong(int parameterIndex, long x) throws SQLException {
		setParameter(parameterIndex, QueryParameterValue.int64(x));
	}

	@Override
	public void setFloat(int parameterIndex, float x) throws SQLException {
		setParameter(parameterIndex, QueryParameterValue.float64((double) x));
	}

	@Override
	public void setDouble(int parameterIndex, double x) throws SQLException {
		setParameter(parameterIndex, QueryParameterValue.float64(x));
	}

	@Override
	public void setBigDecimal(int parameterIndex, BigDecimal x) throws SQLException {
		setParameter(parameterIndex, QueryParameterValue.numeric(x));
	}

	@Override
	public void setString(int parameterIndex, String x) throws SQLException {
		setParameter(parameterIndex, QueryParameterValue.string(x));
	}

	@Override
	public void setBytes(int parameterIndex, byte[] x) throws SQLException {
		setParameter(parameterIndex, QueryParameterValue.bytes(x));
	}

	@Override
	public void setDate(int parameterIndex, Date x) throws SQLException {
		if (x == null) {
			setNull(parameterIndex, Types.DATE);
		} else {
			setParameter(parameterIndex, QueryParameterValue.date(x.toString()));
		}
	}

	@Override
	public void setTime(int parameterIndex, Time x) throws SQLException {
		if (x == null) {
			setNull(parameterIndex, Types.TIME);
		} else {
			setParameter(parameterIndex, QueryParameterValue.time(x.toString()));
		}
	}

	@Override
	public void setTimestamp(int parameterIndex, Timestamp x) throws SQLException {
		if (x == null) {
			setNull(parameterIndex, Types.TIMESTAMP);
		} else {
			setParameter(parameterIndex, QueryParameterValue.timestamp(x.toInstant().toString()));
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

		switch (x) {
			case String s -> setString(parameterIndex, s);
			case Integer i -> setInt(parameterIndex, i);
			case Long l -> setLong(parameterIndex, l);
			case Double d -> setDouble(parameterIndex, d);
			case Boolean b -> setBoolean(parameterIndex, b);
			case BigDecimal bd -> setBigDecimal(parameterIndex, bd);
			case Timestamp ts -> setTimestamp(parameterIndex, ts);
			case Date dt -> setDate(parameterIndex, dt);
			case Time t -> setTime(parameterIndex, t);
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
		setDate(parameterIndex, x);
	}

	@Override
	public void setTime(int parameterIndex, Time x, Calendar cal) throws SQLException {
		setTime(parameterIndex, x);
	}

	@Override
	public void setTimestamp(int parameterIndex, Timestamp x, Calendar cal) throws SQLException {
		setTimestamp(parameterIndex, x);
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

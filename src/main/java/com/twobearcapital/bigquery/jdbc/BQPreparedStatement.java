/*
 * Copyright 2026 Two Bear Capital
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.twobearcapital.bigquery.jdbc;

import com.google.cloud.bigquery.*;
import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.*;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JDBC PreparedStatement implementation for BigQuery.
 *
 * @since 1.0.0
 */
public final class BQPreparedStatement extends BQStatement implements PreparedStatement {

	private static final Logger logger = LoggerFactory.getLogger(BQPreparedStatement.class);

	private final String sqlTemplate;
	private final List<QueryParameterValue> parameters = new ArrayList<>();

	public BQPreparedStatement(BQConnection connection, String sql) {
		super(connection);
		this.sqlTemplate = sql;
	}

	private void ensureCapacity(int parameterIndex) {
		while (parameters.size() < parameterIndex) {
			parameters.add(null);
		}
	}

	@Override
	protected QueryJobConfiguration.Builder buildQueryConfig(String sql) {
		return QueryJobConfiguration.newBuilder(sql)
			.setUseLegacySql(properties.useLegacySql())
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
	public int executeUpdate() throws SQLException {
		executeQuery();
		return 0;
	}

	@Override
	public void setNull(int parameterIndex, int sqlType) throws SQLException {
		checkClosed();
		ensureCapacity(parameterIndex);
		parameters.set(parameterIndex - 1, QueryParameterValue.string(null));
	}

	@Override
	public void setBoolean(int parameterIndex, boolean x) throws SQLException {
		checkClosed();
		ensureCapacity(parameterIndex);
		parameters.set(parameterIndex - 1, QueryParameterValue.bool(x));
	}

	@Override
	public void setByte(int parameterIndex, byte x) throws SQLException {
		checkClosed();
		ensureCapacity(parameterIndex);
		parameters.set(parameterIndex - 1, QueryParameterValue.int64((long) x));
	}

	@Override
	public void setShort(int parameterIndex, short x) throws SQLException {
		checkClosed();
		ensureCapacity(parameterIndex);
		parameters.set(parameterIndex - 1, QueryParameterValue.int64((long) x));
	}

	@Override
	public void setInt(int parameterIndex, int x) throws SQLException {
		checkClosed();
		ensureCapacity(parameterIndex);
		parameters.set(parameterIndex - 1, QueryParameterValue.int64((long) x));
	}

	@Override
	public void setLong(int parameterIndex, long x) throws SQLException {
		checkClosed();
		ensureCapacity(parameterIndex);
		parameters.set(parameterIndex - 1, QueryParameterValue.int64(x));
	}

	@Override
	public void setFloat(int parameterIndex, float x) throws SQLException {
		checkClosed();
		ensureCapacity(parameterIndex);
		parameters.set(parameterIndex - 1, QueryParameterValue.float64((double) x));
	}

	@Override
	public void setDouble(int parameterIndex, double x) throws SQLException {
		checkClosed();
		ensureCapacity(parameterIndex);
		parameters.set(parameterIndex - 1, QueryParameterValue.float64(x));
	}

	@Override
	public void setBigDecimal(int parameterIndex, BigDecimal x) throws SQLException {
		checkClosed();
		ensureCapacity(parameterIndex);
		parameters.set(parameterIndex - 1, QueryParameterValue.numeric(x));
	}

	@Override
	public void setString(int parameterIndex, String x) throws SQLException {
		checkClosed();
		ensureCapacity(parameterIndex);
		parameters.set(parameterIndex - 1, QueryParameterValue.string(x));
	}

	@Override
	public void setBytes(int parameterIndex, byte[] x) throws SQLException {
		checkClosed();
		ensureCapacity(parameterIndex);
		parameters.set(parameterIndex - 1, QueryParameterValue.bytes(x));
	}

	@Override
	public void setDate(int parameterIndex, Date x) throws SQLException {
		checkClosed();
		ensureCapacity(parameterIndex);
		parameters.set(parameterIndex - 1, QueryParameterValue.date(x.toString()));
	}

	@Override
	public void setTime(int parameterIndex, Time x) throws SQLException {
		checkClosed();
		ensureCapacity(parameterIndex);
		parameters.set(parameterIndex - 1, QueryParameterValue.time(x.toString()));
	}

	@Override
	public void setTimestamp(int parameterIndex, Timestamp x) throws SQLException {
		checkClosed();
		ensureCapacity(parameterIndex);
		parameters.set(parameterIndex - 1, QueryParameterValue.timestamp(x.toInstant().toString()));
	}

	@Override
	public void setAsciiStream(int parameterIndex, InputStream x, int length) throws SQLException {
		throw new BQSQLFeatureNotSupportedException("setAsciiStream not supported");
	}

	@Deprecated
	@SuppressWarnings("deprecation")
	@Override
	public void setUnicodeStream(int parameterIndex, InputStream x, int length) throws SQLException {
		throw new BQSQLFeatureNotSupportedException("setUnicodeStream not supported");
	}

	@Override
	public void setBinaryStream(int parameterIndex, InputStream x, int length) throws SQLException {
		throw new BQSQLFeatureNotSupportedException("setBinaryStream not supported");
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
		} else if (x instanceof String) {
			setString(parameterIndex, (String) x);
		} else if (x instanceof Integer) {
			setInt(parameterIndex, (Integer) x);
		} else if (x instanceof Long) {
			setLong(parameterIndex, (Long) x);
		} else if (x instanceof Double) {
			setDouble(parameterIndex, (Double) x);
		} else if (x instanceof Boolean) {
			setBoolean(parameterIndex, (Boolean) x);
		} else if (x instanceof BigDecimal) {
			setBigDecimal(parameterIndex, (BigDecimal) x);
		} else if (x instanceof Timestamp) {
			setTimestamp(parameterIndex, (Timestamp) x);
		} else if (x instanceof Date) {
			setDate(parameterIndex, (Date) x);
		} else if (x instanceof Time) {
			setTime(parameterIndex, (Time) x);
		} else {
			throw new SQLException("Unsupported parameter type: " + x.getClass().getName());
		}
	}

	@Override
	public boolean execute() throws SQLException {
		executeQuery();
		return true;
	}

	@Override
	public void addBatch() throws SQLException {
		throw new BQSQLFeatureNotSupportedException("Batch updates not supported");
	}

	@Override
	public void setCharacterStream(int parameterIndex, Reader reader, int length) throws SQLException {
		throw new BQSQLFeatureNotSupportedException("setCharacterStream not supported");
	}

	@Override
	public void setRef(int parameterIndex, Ref x) throws SQLException {
		throw new BQSQLFeatureNotSupportedException("setRef not supported");
	}

	@Override
	public void setBlob(int parameterIndex, Blob x) throws SQLException {
		throw new BQSQLFeatureNotSupportedException("setBlob not supported");
	}

	@Override
	public void setClob(int parameterIndex, Clob x) throws SQLException {
		throw new BQSQLFeatureNotSupportedException("setClob not supported");
	}

	@Override
	public void setArray(int parameterIndex, Array x) throws SQLException {
		throw new BQSQLFeatureNotSupportedException("setArray not supported yet");
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
	public void setRowId(int parameterIndex, RowId x) throws SQLException {
		throw new BQSQLFeatureNotSupportedException("setRowId not supported");
	}

	@Override
	public void setNString(int parameterIndex, String value) throws SQLException {
		setString(parameterIndex, value);
	}

	@Override
	public void setNCharacterStream(int parameterIndex, Reader value, long length) throws SQLException {
		throw new BQSQLFeatureNotSupportedException("setNCharacterStream not supported");
	}

	@Override
	public void setNClob(int parameterIndex, NClob value) throws SQLException {
		throw new BQSQLFeatureNotSupportedException("setNClob not supported");
	}

	@Override
	public void setClob(int parameterIndex, Reader reader, long length) throws SQLException {
		throw new BQSQLFeatureNotSupportedException("setClob not supported");
	}

	@Override
	public void setBlob(int parameterIndex, InputStream inputStream, long length) throws SQLException {
		throw new BQSQLFeatureNotSupportedException("setBlob not supported");
	}

	@Override
	public void setNClob(int parameterIndex, Reader reader, long length) throws SQLException {
		throw new BQSQLFeatureNotSupportedException("setNClob not supported");
	}

	@Override
	public void setSQLXML(int parameterIndex, SQLXML xmlObject) throws SQLException {
		throw new BQSQLFeatureNotSupportedException("setSQLXML not supported");
	}

	@Override
	public void setObject(int parameterIndex, Object x, int targetSqlType, int scaleOrLength) throws SQLException {
		setObject(parameterIndex, x);
	}

	@Override
	public void setAsciiStream(int parameterIndex, InputStream x, long length) throws SQLException {
		throw new BQSQLFeatureNotSupportedException("setAsciiStream not supported");
	}

	@Override
	public void setBinaryStream(int parameterIndex, InputStream x, long length) throws SQLException {
		throw new BQSQLFeatureNotSupportedException("setBinaryStream not supported");
	}

	@Override
	public void setCharacterStream(int parameterIndex, Reader reader, long length) throws SQLException {
		throw new BQSQLFeatureNotSupportedException("setCharacterStream not supported");
	}

	@Override
	public void setAsciiStream(int parameterIndex, InputStream x) throws SQLException {
		throw new BQSQLFeatureNotSupportedException("setAsciiStream not supported");
	}

	@Override
	public void setBinaryStream(int parameterIndex, InputStream x) throws SQLException {
		throw new BQSQLFeatureNotSupportedException("setBinaryStream not supported");
	}

	@Override
	public void setCharacterStream(int parameterIndex, Reader reader) throws SQLException {
		throw new BQSQLFeatureNotSupportedException("setCharacterStream not supported");
	}

	@Override
	public void setNCharacterStream(int parameterIndex, Reader value) throws SQLException {
		throw new BQSQLFeatureNotSupportedException("setNCharacterStream not supported");
	}

	@Override
	public void setClob(int parameterIndex, Reader reader) throws SQLException {
		throw new BQSQLFeatureNotSupportedException("setClob not supported");
	}

	@Override
	public void setBlob(int parameterIndex, InputStream inputStream) throws SQLException {
		throw new BQSQLFeatureNotSupportedException("setBlob not supported");
	}

	@Override
	public void setNClob(int parameterIndex, Reader reader) throws SQLException {
		throw new BQSQLFeatureNotSupportedException("setNClob not supported");
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

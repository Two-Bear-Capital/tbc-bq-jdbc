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

import com.google.cloud.bigquery.FieldValue;
import com.google.cloud.bigquery.FieldValueList;
import com.google.cloud.bigquery.TableResult;
import com.twobearcapital.bigquery.jdbc.base.BaseReadOnlyResultSet;
import com.twobearcapital.bigquery.jdbc.util.ErrorMessages;
import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.*;
import java.util.Calendar;
import java.util.Iterator;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JDBC ResultSet implementation for BigQuery.
 *
 * <p>
 * This implementation wraps a Google Cloud BigQuery {@link TableResult} and
 * provides JDBC ResultSet access to the query results.
 *
 * <p>
 * This class extends {@link BaseReadOnlyResultSet} which provides default
 * implementations for all unsupported operations. Only methods that are
 * actually supported by BigQuery are implemented here.
 *
 * @since 1.0.0
 */
public class BQResultSet extends BaseReadOnlyResultSet {

	private static final Logger logger = LoggerFactory.getLogger(BQResultSet.class);

	private final BQStatement statement;
	private final TableResult tableResult;
	private final Iterator<FieldValueList> rowIterator;
	private FieldValueList currentRow;
	private boolean wasNull = false;

	/**
	 * Creates a new BigQuery ResultSet.
	 *
	 * @param statement
	 *            the statement that produced this result set
	 * @param tableResult
	 *            the BigQuery table result
	 */
	public BQResultSet(BQStatement statement, TableResult tableResult) {
		this.statement = statement;
		this.tableResult = tableResult;
		this.rowIterator = tableResult.iterateAll().iterator();
		this.currentRow = null;
	}

	/**
	 * Creates a new BigQuery ResultSet for metadata operations.
	 *
	 * <p>
	 * This constructor is used internally for creating result sets that represent
	 * metadata query results (e.g., from DatabaseMetaData methods).
	 *
	 * @param tableResult
	 *            the BigQuery table result
	 */
	BQResultSet(TableResult tableResult) {
		this.statement = null;
		this.tableResult = tableResult;
		this.rowIterator = tableResult.iterateAll().iterator();
		this.currentRow = null;
	}

	@Override
	protected String getClosedErrorMessage() {
		return ErrorMessages.RESULTSET_CLOSED;
	}

	private void checkPosition() throws SQLException {
		checkClosed();
		if (currentRow == null) {
			throw new BQSQLException("ResultSet not positioned on a row");
		}
	}

	private FieldValue getFieldValue(int columnIndex) throws SQLException {
		checkPosition();
		if (columnIndex < 1 || columnIndex > currentRow.size()) {
			throw new BQSQLException("Column index out of bounds: " + columnIndex);
		}
		FieldValue value = currentRow.get(columnIndex - 1);
		wasNull = value.isNull();
		return value;
	}

	private FieldValue getFieldValue(String columnLabel) throws SQLException {
		checkPosition();
		try {
			FieldValue value = currentRow.get(columnLabel);
			wasNull = value.isNull();
			return value;
		} catch (IllegalArgumentException e) {
			throw new BQSQLException("Column not found: " + columnLabel, e);
		}
	}

	@Override
	public boolean next() throws SQLException {
		checkClosed();
		if (rowIterator.hasNext()) {
			currentRow = rowIterator.next();
			return true;
		}
		currentRow = null;
		return false;
	}

	@Override
	protected void doClose() throws SQLException {
		currentRow = null;
		logger.debug("ResultSet closed");
	}

	@Override
	public boolean wasNull() throws SQLException {
		checkClosed();
		return wasNull;
	}

	@Override
	public String getString(int columnIndex) throws SQLException {
		FieldValue value = getFieldValue(columnIndex);
		return value.isNull() ? null : value.getStringValue();
	}

	@Override
	public boolean getBoolean(int columnIndex) throws SQLException {
		FieldValue value = getFieldValue(columnIndex);
		return value.isNull() ? false : value.getBooleanValue();
	}

	@Override
	public byte getByte(int columnIndex) throws SQLException {
		FieldValue value = getFieldValue(columnIndex);
		if (value.isNull()) {
			return 0;
		}
		long longValue = value.getLongValue();
		if (longValue < Byte.MIN_VALUE || longValue > Byte.MAX_VALUE) {
			throw new BQSQLException(String.format(ErrorMessages.VALUE_OUT_OF_RANGE, "byte", longValue),
					BQSQLException.SQLSTATE_NUMERIC_VALUE_OUT_OF_RANGE);
		}
		return (byte) longValue;
	}

	@Override
	public short getShort(int columnIndex) throws SQLException {
		FieldValue value = getFieldValue(columnIndex);
		if (value.isNull()) {
			return 0;
		}
		long longValue = value.getLongValue();
		if (longValue < Short.MIN_VALUE || longValue > Short.MAX_VALUE) {
			throw new BQSQLException(String.format(ErrorMessages.VALUE_OUT_OF_RANGE, "short", longValue),
					BQSQLException.SQLSTATE_NUMERIC_VALUE_OUT_OF_RANGE);
		}
		return (short) longValue;
	}

	@Override
	public int getInt(int columnIndex) throws SQLException {
		FieldValue value = getFieldValue(columnIndex);
		if (value.isNull()) {
			return 0;
		}
		long longValue = value.getLongValue();
		if (longValue < Integer.MIN_VALUE || longValue > Integer.MAX_VALUE) {
			throw new BQSQLException(String.format(ErrorMessages.VALUE_OUT_OF_RANGE, "int", longValue),
					BQSQLException.SQLSTATE_NUMERIC_VALUE_OUT_OF_RANGE);
		}
		return (int) longValue;
	}

	@Override
	public long getLong(int columnIndex) throws SQLException {
		FieldValue value = getFieldValue(columnIndex);
		return value.isNull() ? 0 : value.getLongValue();
	}

	@Override
	public float getFloat(int columnIndex) throws SQLException {
		FieldValue value = getFieldValue(columnIndex);
		return value.isNull() ? 0 : (float) value.getDoubleValue();
	}

	@Override
	public double getDouble(int columnIndex) throws SQLException {
		FieldValue value = getFieldValue(columnIndex);
		return value.isNull() ? 0 : value.getDoubleValue();
	}

	@Deprecated
	@SuppressWarnings("deprecation")
	@Override
	public BigDecimal getBigDecimal(int columnIndex, int scale) throws SQLException {
		FieldValue value = getFieldValue(columnIndex);
		if (value.isNull()) {
			return null;
		}
		return value.getNumericValue();
	}

	@Override
	public byte[] getBytes(int columnIndex) throws SQLException {
		FieldValue value = getFieldValue(columnIndex);
		return value.isNull() ? null : value.getBytesValue();
	}

	@Override
	public Date getDate(int columnIndex) throws SQLException {
		FieldValue value = getFieldValue(columnIndex);
		if (value.isNull()) {
			return null;
		}
		String dateStr = value.getStringValue();
		return Date.valueOf(dateStr);
	}

	@Override
	public Time getTime(int columnIndex) throws SQLException {
		FieldValue value = getFieldValue(columnIndex);
		if (value.isNull()) {
			return null;
		}
		String timeStr = value.getStringValue();
		return Time.valueOf(timeStr);
	}

	@Override
	public Timestamp getTimestamp(int columnIndex) throws SQLException {
		FieldValue value = getFieldValue(columnIndex);
		if (value.isNull()) {
			return null;
		}
		long micros = value.getTimestampValue();
		return new Timestamp(micros / 1000);
	}

	@Override
	public String getString(String columnLabel) throws SQLException {
		FieldValue value = getFieldValue(columnLabel);
		return value.isNull() ? null : value.getStringValue();
	}

	@Override
	public boolean getBoolean(String columnLabel) throws SQLException {
		FieldValue value = getFieldValue(columnLabel);
		return value.isNull() ? false : value.getBooleanValue();
	}

	@Override
	public byte getByte(String columnLabel) throws SQLException {
		FieldValue value = getFieldValue(columnLabel);
		return value.isNull() ? 0 : (byte) value.getLongValue();
	}

	@Override
	public short getShort(String columnLabel) throws SQLException {
		FieldValue value = getFieldValue(columnLabel);
		return value.isNull() ? 0 : (short) value.getLongValue();
	}

	@Override
	public int getInt(String columnLabel) throws SQLException {
		FieldValue value = getFieldValue(columnLabel);
		return value.isNull() ? 0 : (int) value.getLongValue();
	}

	@Override
	public long getLong(String columnLabel) throws SQLException {
		FieldValue value = getFieldValue(columnLabel);
		return value.isNull() ? 0 : value.getLongValue();
	}

	@Override
	public float getFloat(String columnLabel) throws SQLException {
		FieldValue value = getFieldValue(columnLabel);
		return value.isNull() ? 0 : (float) value.getDoubleValue();
	}

	@Override
	public double getDouble(String columnLabel) throws SQLException {
		FieldValue value = getFieldValue(columnLabel);
		return value.isNull() ? 0 : value.getDoubleValue();
	}

	@Deprecated
	@SuppressWarnings("deprecation")
	@Override
	public BigDecimal getBigDecimal(String columnLabel, int scale) throws SQLException {
		FieldValue value = getFieldValue(columnLabel);
		if (value.isNull()) {
			return null;
		}
		return value.getNumericValue();
	}

	@Override
	public byte[] getBytes(String columnLabel) throws SQLException {
		FieldValue value = getFieldValue(columnLabel);
		return value.isNull() ? null : value.getBytesValue();
	}

	@Override
	public Date getDate(String columnLabel) throws SQLException {
		FieldValue value = getFieldValue(columnLabel);
		if (value.isNull()) {
			return null;
		}
		String dateStr = value.getStringValue();
		return Date.valueOf(dateStr);
	}

	@Override
	public Time getTime(String columnLabel) throws SQLException {
		FieldValue value = getFieldValue(columnLabel);
		if (value.isNull()) {
			return null;
		}
		String timeStr = value.getStringValue();
		return Time.valueOf(timeStr);
	}

	@Override
	public Timestamp getTimestamp(String columnLabel) throws SQLException {
		FieldValue value = getFieldValue(columnLabel);
		if (value.isNull()) {
			return null;
		}
		long micros = value.getTimestampValue();
		return new Timestamp(micros / 1000);
	}
	@Override
	public void clearWarnings() throws SQLException {
		checkClosed();
	}

	@Override
	public ResultSetMetaData getMetaData() throws SQLException {
		checkClosed();
		return new BQResultSetMetaData(tableResult.getSchema());
	}

	@Override
	public Object getObject(int columnIndex) throws SQLException {
		FieldValue value = getFieldValue(columnIndex);
		return value.isNull() ? null : value.getValue();
	}

	@Override
	public Object getObject(String columnLabel) throws SQLException {
		FieldValue value = getFieldValue(columnLabel);
		return value.isNull() ? null : value.getValue();
	}

	@Override
	public int findColumn(String columnLabel) throws SQLException {
		checkPosition();
		for (int i = 0; i < currentRow.size(); i++) {
			if (tableResult.getSchema().getFields().get(i).getName().equals(columnLabel)) {
				return i + 1;
			}
		}
		throw new BQSQLException("Column not found: " + columnLabel);
	}

	@Override
	public BigDecimal getBigDecimal(int columnIndex) throws SQLException {
		FieldValue value = getFieldValue(columnIndex);
		return value.isNull() ? null : value.getNumericValue();
	}

	@Override
	public BigDecimal getBigDecimal(String columnLabel) throws SQLException {
		FieldValue value = getFieldValue(columnLabel);
		return value.isNull() ? null : value.getNumericValue();
	}

	@Override
	public boolean isBeforeFirst() throws SQLException {
		checkClosed();
		return currentRow == null && rowIterator.hasNext();
	}

	@Override
	public boolean isAfterLast() throws SQLException {
		checkClosed();
		return currentRow == null && !rowIterator.hasNext();
	}

	@Override
	public void setFetchDirection(int direction) throws SQLException {
		checkClosed();
		if (direction != FETCH_FORWARD) {
			throw new BQSQLFeatureNotSupportedException("Only FETCH_FORWARD is supported");
		}
	}

	@Override
	public Statement getStatement() throws SQLException {
		checkClosed();
		return statement;
	}

	@Override
	public Object getObject(int columnIndex, Map<String, Class<?>> map) throws SQLException {
		return getObject(columnIndex);
	}

	@Override
	public Object getObject(String columnLabel, Map<String, Class<?>> map) throws SQLException {
		return getObject(columnLabel);
	}

	@Override
	public Date getDate(int columnIndex, Calendar cal) throws SQLException {
		return getDate(columnIndex);
	}

	@Override
	public Date getDate(String columnLabel, Calendar cal) throws SQLException {
		return getDate(columnLabel);
	}

	@Override
	public Time getTime(int columnIndex, Calendar cal) throws SQLException {
		return getTime(columnIndex);
	}

	@Override
	public Time getTime(String columnLabel, Calendar cal) throws SQLException {
		return getTime(columnLabel);
	}

	@Override
	public Timestamp getTimestamp(int columnIndex, Calendar cal) throws SQLException {
		return getTimestamp(columnIndex);
	}

	@Override
	public Timestamp getTimestamp(String columnLabel, Calendar cal) throws SQLException {
		return getTimestamp(columnLabel);
	}

	public String getNString(int columnIndex) throws SQLException {
		return getString(columnIndex);
	}

	@Override
	public String getNString(String columnLabel) throws SQLException {
		return getString(columnLabel);
	}

}

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
import com.twobearcapital.bigquery.jdbc.base.BaseReadOnlyResultSet;
import com.twobearcapital.bigquery.jdbc.exception.BQSQLException;
import com.twobearcapital.bigquery.jdbc.metadata.BQResultSetMetaData;
import com.twobearcapital.bigquery.jdbc.util.ErrorMessages;
import com.twobearcapital.bigquery.jdbc.util.TimezoneUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.*;
import java.util.Calendar;
import java.util.Iterator;
import java.util.Map;

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
	private int rowCount = 0; // Track rows returned for maxRows enforcement

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

	/**
	 * Protected constructor for subclasses that override iteration logic.
	 *
	 * <p>
	 * This constructor allows subclasses like StorageReadResultSet to provide their
	 * own iteration mechanism without requiring a TableResult.
	 *
	 * @param statement
	 *            the statement that produced this result set
	 * @param tableResult
	 *            the BigQuery table result (may be null for subclasses)
	 * @param allowNullTableResult
	 *            marker parameter to distinguish this constructor
	 */
	protected BQResultSet(BQStatement statement, TableResult tableResult, boolean allowNullTableResult) {
		this.statement = statement;
		this.tableResult = tableResult;
		this.rowIterator = tableResult != null ? tableResult.iterateAll().iterator() : null;
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

		// Check if maxRows limit has been reached (JDBC Statement.setMaxRows)
		int maxRows = statement.getMaxRows();
		if (maxRows > 0 && rowCount >= maxRows) {
			currentRow = null;
			return false;
		}

		if (rowIterator.hasNext()) {
			currentRow = rowIterator.next();
			rowCount++;
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
		if (value.isNull()) {
			return null;
		}

		// Complex types (ARRAY, STRUCT) are returned as JSON strings
		// This prevents IntelliJ IDEA crashes when using JDBC Array/Struct objects
		if (value.getAttribute() == FieldValue.Attribute.REPEATED
				|| value.getAttribute() == FieldValue.Attribute.RECORD) {
			// Convert to JSON representation
			return value.getValue().toString();
		}

		return value.getStringValue();
	}

	@Override
	public boolean getBoolean(int columnIndex) throws SQLException {
		FieldValue value = getFieldValue(columnIndex);
		return !value.isNull() && value.getBooleanValue();
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
		if (value.isNull()) {
			return null;
		}

		// Complex types (ARRAY, STRUCT) are returned as JSON strings
		// This prevents IntelliJ IDEA crashes when using JDBC Array/Struct objects
		if (value.getAttribute() == FieldValue.Attribute.REPEATED
				|| value.getAttribute() == FieldValue.Attribute.RECORD) {
			// Convert to JSON representation
			return value.getValue().toString();
		}

		return value.getStringValue();
	}

	@Override
	public boolean getBoolean(String columnLabel) throws SQLException {
		FieldValue value = getFieldValue(columnLabel);
		return !value.isNull() && value.getBooleanValue();
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
	public ResultSetMetaData getMetaData() throws SQLException {
		checkClosed();
		return new BQResultSetMetaData(tableResult.getSchema());
	}

	@Override
	public Object getObject(int columnIndex) throws SQLException {
		FieldValue value = getFieldValue(columnIndex);
		if (value.isNull()) {
			return null;
		}

		// Get field type to return appropriate Java type
		var schema = tableResult.getSchema();
		if (schema != null && columnIndex > 0 && columnIndex <= schema.getFields().size()) {
			Field field = schema.getFields().get(columnIndex - 1);
			StandardSQLTypeName type = field.getType().getStandardType();

			// Return appropriate Java type based on BigQuery type
			return switch (type) {
				case BOOL -> value.getBooleanValue();
				case INT64 -> value.getLongValue();
				case FLOAT64 -> value.getDoubleValue();
				case NUMERIC, BIGNUMERIC -> value.getNumericValue();
				case STRING -> value.getStringValue();
				case BYTES -> value.getBytesValue();
				case DATE -> {
					String dateStr = value.getStringValue();
					yield java.sql.Date.valueOf(dateStr);
				}
				case TIME -> {
					String timeStr = value.getStringValue();
					yield java.sql.Time.valueOf(timeStr);
				}
				case DATETIME, TIMESTAMP -> {
					long micros = value.getTimestampValue();
					yield new java.sql.Timestamp(micros / 1000);
				}
				default -> value.getValue(); // Fallback to raw value
			};
		}

		return value.getValue();
	}

	@Override
	public Object getObject(String columnLabel) throws SQLException {
		return getObject(findColumn(columnLabel));
	}

	@Override
	public int findColumn(String columnLabel) throws SQLException {
		checkClosed();
		var schema = tableResult.getSchema();
		if (schema == null) {
			throw new BQSQLException("Schema is not available");
		}

		// Find column by exact name match
		FieldList fields = schema.getFields();
		for (int i = 0; i < fields.size(); i++) {
			if (fields.get(i).getName().equals(columnLabel)) {
				return i + 1;
			}
		}

		// Try case-insensitive match
		for (int i = 0; i < fields.size(); i++) {
			if (fields.get(i).getName().equalsIgnoreCase(columnLabel)) {
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
		Date date = getDate(columnIndex);
		return TimezoneUtils.adjustDateToCalendar(date, cal);
	}

	@Override
	public Date getDate(String columnLabel, Calendar cal) throws SQLException {
		return getDate(findColumn(columnLabel), cal);
	}

	@Override
	public Time getTime(int columnIndex, Calendar cal) throws SQLException {
		Time time = getTime(columnIndex);
		return TimezoneUtils.adjustTimeToCalendar(time, cal);
	}

	@Override
	public Time getTime(String columnLabel, Calendar cal) throws SQLException {
		return getTime(findColumn(columnLabel), cal);
	}

	@Override
	public Timestamp getTimestamp(int columnIndex, Calendar cal) throws SQLException {
		Timestamp timestamp = getTimestamp(columnIndex);
		return TimezoneUtils.adjustTimestampToCalendar(timestamp, cal);
	}

	@Override
	public Timestamp getTimestamp(String columnLabel, Calendar cal) throws SQLException {
		return getTimestamp(findColumn(columnLabel), cal);
	}

	@Override
	public String getNString(int columnIndex) throws SQLException {
		return getString(columnIndex);
	}

	@Override
	public String getNString(String columnLabel) throws SQLException {
		return getString(columnLabel);
	}

}

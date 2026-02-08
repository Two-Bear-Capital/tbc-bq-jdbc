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
package com.tbc.bq.jdbc.metadata;

import com.tbc.bq.jdbc.base.BaseReadOnlyResultSet;
import com.tbc.bq.jdbc.util.ErrorMessages;
import com.tbc.bq.jdbc.util.NumberParser;

import java.math.BigDecimal;
import java.sql.*;
import java.util.List;
import java.util.Objects;

/**
 * A simple in-memory ResultSet implementation for database metadata queries.
 *
 * <p>
 * This implementation is used by DatabaseMetaData methods to return catalog,
 * schema, table, and column information without requiring actual BigQuery
 * queries.
 *
 * @since 1.0.0
 */
public final class MetadataResultSet extends BaseReadOnlyResultSet {

	private final String[] columnNames;
	private final int[] columnTypes;
	private final List<Object[]> rows;
	private int currentRowIndex = -1;
	private Object[] currentRow;
	private boolean wasNull = false;

	/**
	 * Creates a new metadata ResultSet.
	 *
	 * @param columnNames
	 *            array of column names
	 * @param columnTypes
	 *            array of JDBC type constants
	 * @param rows
	 *            list of data rows (each row is an Object array)
	 */
	public MetadataResultSet(String[] columnNames, int[] columnTypes, List<Object[]> rows) {
		this.columnNames = Objects.requireNonNull(columnNames, "columnNames cannot be null");
		this.columnTypes = Objects.requireNonNull(columnTypes, "columnTypes cannot be null");
		this.rows = Objects.requireNonNull(rows, "rows cannot be null");

		if (columnNames.length != columnTypes.length) {
			throw new IllegalArgumentException("columnNames and columnTypes must have the same length");
		}
	}

	/**
	 * Gets the column names.
	 *
	 * <p>
	 * Public accessor for caching support.
	 *
	 * @return the column names array
	 */
	public String[] getColumnNames() {
		return columnNames;
	}

	/**
	 * Gets the column types.
	 *
	 * <p>
	 * Public accessor for caching support.
	 *
	 * @return the column types array
	 */
	public int[] getColumnTypes() {
		return columnTypes;
	}

	/**
	 * Gets the data rows.
	 *
	 * <p>
	 * Public accessor for caching support.
	 *
	 * @return the rows list
	 */
	public List<Object[]> getRows() {
		return rows;
	}

	@Override
	protected String getClosedErrorMessage() {
		return ErrorMessages.RESULTSET_CLOSED;
	}

	private void checkPosition() throws SQLException {
		checkClosed();
		if (currentRow == null) {
			throw new SQLException("ResultSet not positioned on a row");
		}
	}

	private int getColumnIndex(String columnLabel) throws SQLException {
		for (int i = 0; i < columnNames.length; i++) {
			if (columnNames[i].equalsIgnoreCase(columnLabel)) {
				return i + 1;
			}
		}
		throw new SQLException("Column not found: " + columnLabel);
	}

	private Object getValue(int columnIndex) throws SQLException {
		checkPosition();
		if (columnIndex < 1 || columnIndex > columnNames.length) {
			throw new SQLException("Invalid column index: " + columnIndex);
		}
		Object value = currentRow[columnIndex - 1];
		wasNull = (value == null);
		return value;
	}

	@Override
	public boolean next() throws SQLException {
		checkClosed();
		currentRowIndex++;
		if (currentRowIndex < rows.size()) {
			currentRow = rows.get(currentRowIndex);
			return true;
		}
		currentRow = null;
		return false;
	}

	@Override
	protected void doClose() throws SQLException {
		currentRow = null;
	}

	@Override
	public boolean wasNull() throws SQLException {
		checkClosed();
		return wasNull;
	}

	@Override
	public String getString(int columnIndex) throws SQLException {
		Object value = getValue(columnIndex);
		return value == null ? null : value.toString();
	}

	@Override
	public boolean getBoolean(int columnIndex) throws SQLException {
		Object value = getValue(columnIndex);
		if (value == null)
			return false;
		if (value instanceof Boolean)
			return (Boolean) value;
		return Boolean.parseBoolean(value.toString());
	}

	@Override
	public byte getByte(int columnIndex) throws SQLException {
		return NumberParser.toByte(getValue(columnIndex), columnIndex);
	}

	@Override
	public short getShort(int columnIndex) throws SQLException {
		return NumberParser.toShort(getValue(columnIndex), columnIndex);
	}

	@Override
	public int getInt(int columnIndex) throws SQLException {
		return NumberParser.toInt(getValue(columnIndex), columnIndex);
	}

	@Override
	public long getLong(int columnIndex) throws SQLException {
		return NumberParser.toLong(getValue(columnIndex), columnIndex);
	}

	@Override
	public float getFloat(int columnIndex) throws SQLException {
		return NumberParser.toFloat(getValue(columnIndex), columnIndex);
	}

	@Override
	public double getDouble(int columnIndex) throws SQLException {
		return NumberParser.toDouble(getValue(columnIndex), columnIndex);
	}

	@Deprecated
	@SuppressWarnings("deprecation")
	@Override
	public BigDecimal getBigDecimal(int columnIndex, int scale) throws SQLException {
		return NumberParser.toBigDecimal(getValue(columnIndex), columnIndex);
	}

	@Override
	public byte[] getBytes(int columnIndex) throws SQLException {
		Object value = getValue(columnIndex);
		if (value == null)
			return null;
		if (value instanceof byte[])
			return (byte[]) value;
		return value.toString().getBytes();
	}

	@Override
	public java.sql.Date getDate(int columnIndex) throws SQLException {
		Object value = getValue(columnIndex);
		if (value == null)
			return null;
		if (value instanceof java.sql.Date)
			return (java.sql.Date) value;
		return java.sql.Date.valueOf(value.toString());
	}

	@Override
	public Time getTime(int columnIndex) throws SQLException {
		Object value = getValue(columnIndex);
		if (value == null)
			return null;
		if (value instanceof Time)
			return (Time) value;
		return Time.valueOf(value.toString());
	}

	@Override
	public Timestamp getTimestamp(int columnIndex) throws SQLException {
		Object value = getValue(columnIndex);
		if (value == null)
			return null;
		if (value instanceof Timestamp)
			return (Timestamp) value;
		return Timestamp.valueOf(value.toString());
	}

	@Override
	public String getString(String columnLabel) throws SQLException {
		return getString(getColumnIndex(columnLabel));
	}

	@Override
	public boolean getBoolean(String columnLabel) throws SQLException {
		return getBoolean(getColumnIndex(columnLabel));
	}

	@Override
	public byte getByte(String columnLabel) throws SQLException {
		return getByte(getColumnIndex(columnLabel));
	}

	@Override
	public short getShort(String columnLabel) throws SQLException {
		return getShort(getColumnIndex(columnLabel));
	}

	@Override
	public int getInt(String columnLabel) throws SQLException {
		return getInt(getColumnIndex(columnLabel));
	}

	@Override
	public long getLong(String columnLabel) throws SQLException {
		return getLong(getColumnIndex(columnLabel));
	}

	@Override
	public float getFloat(String columnLabel) throws SQLException {
		return getFloat(getColumnIndex(columnLabel));
	}

	@Override
	public double getDouble(String columnLabel) throws SQLException {
		return getDouble(getColumnIndex(columnLabel));
	}

	@Deprecated
	@SuppressWarnings("deprecation")
	@Override
	public BigDecimal getBigDecimal(String columnLabel, int scale) throws SQLException {
		return getBigDecimal(getColumnIndex(columnLabel), scale);
	}

	@Override
	public byte[] getBytes(String columnLabel) throws SQLException {
		return getBytes(getColumnIndex(columnLabel));
	}

	@Override
	public java.sql.Date getDate(String columnLabel) throws SQLException {
		return getDate(getColumnIndex(columnLabel));
	}

	@Override
	public Time getTime(String columnLabel) throws SQLException {
		return getTime(getColumnIndex(columnLabel));
	}

	@Override
	public Timestamp getTimestamp(String columnLabel) throws SQLException {
		return getTimestamp(getColumnIndex(columnLabel));
	}

	@Override
	public Object getObject(int columnIndex) throws SQLException {
		return getValue(columnIndex);
	}

	@Override
	public Object getObject(String columnLabel) throws SQLException {
		return getObject(getColumnIndex(columnLabel));
	}

	@Override
	public BigDecimal getBigDecimal(int columnIndex) throws SQLException {
		return getBigDecimal(columnIndex, 0);
	}

	@Override
	public BigDecimal getBigDecimal(String columnLabel) throws SQLException {
		return getBigDecimal(getColumnIndex(columnLabel));
	}

	@Override
	public int findColumn(String columnLabel) throws SQLException {
		return getColumnIndex(columnLabel);
	}

	@Override
	public ResultSetMetaData getMetaData() throws SQLException {
		checkClosed();
		return new MetadataResultSetMetaData(columnNames, columnTypes);
	}

	@Override
	public boolean isBeforeFirst() throws SQLException {
		checkClosed();
		return currentRowIndex < 0 && !rows.isEmpty();
	}

	@Override
	public boolean isAfterLast() throws SQLException {
		checkClosed();
		return currentRowIndex >= rows.size() && !rows.isEmpty();
	}

	@Override
	public int getRow() throws SQLException {
		checkClosed();
		return currentRowIndex + 1;
	}

	@Override
	public boolean isFirst() throws SQLException {
		checkClosed();
		return currentRowIndex == 0;
	}

	@Override
	public boolean isLast() throws SQLException {
		checkClosed();
		return currentRowIndex == rows.size() - 1;
	}

	/** Simple ResultSetMetaData implementation for metadata ResultSets. */
	private record MetadataResultSetMetaData(String[] columnNames, int[] columnTypes) implements ResultSetMetaData {

		@Override
		public int getColumnCount() {
			return columnNames.length;
		}

		@Override
		public String getColumnName(int column) throws SQLException {
			if (column < 1 || column > columnNames.length) {
				throw new SQLException("Invalid column index: " + column);
			}
			return columnNames[column - 1];
		}

		@Override
		public String getColumnLabel(int column) throws SQLException {
			return getColumnName(column);
		}

		@Override
		public int getColumnType(int column) throws SQLException {
			if (column < 1 || column > columnTypes.length) {
				throw new SQLException("Invalid column index: " + column);
			}
			return columnTypes[column - 1];
		}

		@Override
		public String getColumnTypeName(int column) throws SQLException {
			int type = getColumnType(column);
			return switch (type) {
				case Types.VARCHAR -> "VARCHAR";
				case Types.INTEGER -> "INTEGER";
				case Types.BIGINT -> "BIGINT";
				case Types.SMALLINT -> "SMALLINT";
				default -> "UNKNOWN";
			};
		}

		@Override
		public String getColumnClassName(int column) throws SQLException {
			int type = getColumnType(column);
			return switch (type) {
				case Types.VARCHAR -> String.class.getName();
				case Types.INTEGER -> Integer.class.getName();
				case Types.BIGINT -> Long.class.getName();
				case Types.SMALLINT -> Short.class.getName();
				default -> Object.class.getName();
			};
		}

		@Override
		public boolean isAutoIncrement(int column) {
			return false;
		}

		@Override
		public boolean isCaseSensitive(int column) {
			return true;
		}

		@Override
		public boolean isSearchable(int column) {
			return true;
		}

		@Override
		public boolean isCurrency(int column) {
			return false;
		}

		@Override
		public int isNullable(int column) {
			return columnNullable;
		}

		@Override
		public boolean isSigned(int column) throws SQLException {
			int type = getColumnType(column);
			return type == Types.INTEGER || type == Types.BIGINT || type == Types.SMALLINT;
		}

		@Override
		public int getColumnDisplaySize(int column) {
			return 50;
		}

		@Override
		public String getSchemaName(int column) {
			return "";
		}

		@Override
		public int getPrecision(int column) {
			return 0;
		}

		@Override
		public int getScale(int column) {
			return 0;
		}

		@Override
		public String getTableName(int column) {
			return "";
		}

		@Override
		public String getCatalogName(int column) {
			return "";
		}

		@Override
		public boolean isReadOnly(int column) {
			return true;
		}

		@Override
		public boolean isWritable(int column) {
			return false;
		}

		@Override
		public boolean isDefinitelyWritable(int column) {
			return false;
		}

		@Override
		public <T> T unwrap(Class<T> iface) throws SQLException {
			if (iface.isInstance(this)) {
				return iface.cast(this);
			}
			throw new SQLException("Cannot unwrap to " + iface.getName());
		}

		@Override
		public boolean isWrapperFor(Class<?> iface) {
			return iface.isInstance(this);
		}
	}
}

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

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.*;
import java.util.*;

/**
 * A simple in-memory ResultSet implementation for database metadata queries.
 *
 * <p>This implementation is used by DatabaseMetaData methods to return catalog, schema, table,
 * and column information without requiring actual BigQuery queries.
 *
 * @since 1.0.0
 */
final class MetadataResultSet implements ResultSet {

  private final String[] columnNames;
  private final int[] columnTypes;
  private final List<Object[]> rows;
  private int currentRowIndex = -1;
  private Object[] currentRow;
  private boolean closed = false;
  private boolean wasNull = false;

  /**
   * Creates a new metadata ResultSet.
   *
   * @param columnNames array of column names
   * @param columnTypes array of JDBC type constants
   * @param rows list of data rows (each row is an Object array)
   */
  MetadataResultSet(String[] columnNames, int[] columnTypes, List<Object[]> rows) {
    this.columnNames = Objects.requireNonNull(columnNames, "columnNames cannot be null");
    this.columnTypes = Objects.requireNonNull(columnTypes, "columnTypes cannot be null");
    this.rows = Objects.requireNonNull(rows, "rows cannot be null");

    if (columnNames.length != columnTypes.length) {
      throw new IllegalArgumentException("columnNames and columnTypes must have the same length");
    }
  }

  private void checkClosed() throws SQLException {
    if (closed) {
      throw new SQLException("ResultSet is closed");
    }
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
  public void close() throws SQLException {
    closed = true;
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
    if (value == null) return false;
    if (value instanceof Boolean) return (Boolean) value;
    return Boolean.parseBoolean(value.toString());
  }

  @Override
  public byte getByte(int columnIndex) throws SQLException {
    Object value = getValue(columnIndex);
    if (value == null) return 0;
    if (value instanceof Number) return ((Number) value).byteValue();
    return Byte.parseByte(value.toString());
  }

  @Override
  public short getShort(int columnIndex) throws SQLException {
    Object value = getValue(columnIndex);
    if (value == null) return 0;
    if (value instanceof Number) return ((Number) value).shortValue();
    return Short.parseShort(value.toString());
  }

  @Override
  public int getInt(int columnIndex) throws SQLException {
    Object value = getValue(columnIndex);
    if (value == null) return 0;
    if (value instanceof Number) return ((Number) value).intValue();
    return Integer.parseInt(value.toString());
  }

  @Override
  public long getLong(int columnIndex) throws SQLException {
    Object value = getValue(columnIndex);
    if (value == null) return 0;
    if (value instanceof Number) return ((Number) value).longValue();
    return Long.parseLong(value.toString());
  }

  @Override
  public float getFloat(int columnIndex) throws SQLException {
    Object value = getValue(columnIndex);
    if (value == null) return 0;
    if (value instanceof Number) return ((Number) value).floatValue();
    return Float.parseFloat(value.toString());
  }

  @Override
  public double getDouble(int columnIndex) throws SQLException {
    Object value = getValue(columnIndex);
    if (value == null) return 0;
    if (value instanceof Number) return ((Number) value).doubleValue();
    return Double.parseDouble(value.toString());
  }

  @Override
  public BigDecimal getBigDecimal(int columnIndex, int scale) throws SQLException {
    Object value = getValue(columnIndex);
    if (value == null) return null;
    if (value instanceof BigDecimal) return (BigDecimal) value;
    return new BigDecimal(value.toString());
  }

  @Override
  public byte[] getBytes(int columnIndex) throws SQLException {
    Object value = getValue(columnIndex);
    if (value == null) return null;
    if (value instanceof byte[]) return (byte[]) value;
    return value.toString().getBytes();
  }

  @Override
  public java.sql.Date getDate(int columnIndex) throws SQLException {
    Object value = getValue(columnIndex);
    if (value == null) return null;
    if (value instanceof java.sql.Date) return (java.sql.Date) value;
    return java.sql.Date.valueOf(value.toString());
  }

  @Override
  public Time getTime(int columnIndex) throws SQLException {
    Object value = getValue(columnIndex);
    if (value == null) return null;
    if (value instanceof Time) return (Time) value;
    return Time.valueOf(value.toString());
  }

  @Override
  public Timestamp getTimestamp(int columnIndex) throws SQLException {
    Object value = getValue(columnIndex);
    if (value == null) return null;
    if (value instanceof Timestamp) return (Timestamp) value;
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
  public void setFetchDirection(int direction) throws SQLException {
    checkClosed();
    if (direction != FETCH_FORWARD) {
      throw new SQLFeatureNotSupportedException("Only FETCH_FORWARD is supported");
    }
  }

  @Override
  public int getFetchDirection() throws SQLException {
    checkClosed();
    return FETCH_FORWARD;
  }

  @Override
  public void setFetchSize(int rows) throws SQLException {
    checkClosed();
  }

  @Override
  public int getFetchSize() throws SQLException {
    checkClosed();
    return 0;
  }

  @Override
  public int getType() throws SQLException {
    checkClosed();
    return TYPE_FORWARD_ONLY;
  }

  @Override
  public int getConcurrency() throws SQLException {
    checkClosed();
    return CONCUR_READ_ONLY;
  }

  @Override
  public Statement getStatement() throws SQLException {
    checkClosed();
    return null;
  }

  @Override
  public boolean isClosed() {
    return closed;
  }

  @Override
  public SQLWarning getWarnings() throws SQLException {
    checkClosed();
    return null;
  }

  @Override
  public void clearWarnings() throws SQLException {
    checkClosed();
  }

  // Unsupported operations - throw SQLException or return default values

  @Override
  public InputStream getAsciiStream(int columnIndex) throws SQLException {
    throw new SQLFeatureNotSupportedException("getAsciiStream not supported");
  }

  @Override
  public InputStream getUnicodeStream(int columnIndex) throws SQLException {
    throw new SQLFeatureNotSupportedException("getUnicodeStream not supported");
  }

  @Override
  public InputStream getBinaryStream(int columnIndex) throws SQLException {
    throw new SQLFeatureNotSupportedException("getBinaryStream not supported");
  }

  @Override
  public InputStream getAsciiStream(String columnLabel) throws SQLException {
    throw new SQLFeatureNotSupportedException("getAsciiStream not supported");
  }

  @Override
  public InputStream getUnicodeStream(String columnLabel) throws SQLException {
    throw new SQLFeatureNotSupportedException("getUnicodeStream not supported");
  }

  @Override
  public InputStream getBinaryStream(String columnLabel) throws SQLException {
    throw new SQLFeatureNotSupportedException("getBinaryStream not supported");
  }

  @Override
  public String getCursorName() throws SQLException {
    throw new SQLFeatureNotSupportedException("getCursorName not supported");
  }

  @Override
  public Reader getCharacterStream(int columnIndex) throws SQLException {
    throw new SQLFeatureNotSupportedException("getCharacterStream not supported");
  }

  @Override
  public Reader getCharacterStream(String columnLabel) throws SQLException {
    throw new SQLFeatureNotSupportedException("getCharacterStream not supported");
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

  @Override
  public void beforeFirst() throws SQLException {
    throw new SQLFeatureNotSupportedException("beforeFirst not supported");
  }

  @Override
  public void afterLast() throws SQLException {
    throw new SQLFeatureNotSupportedException("afterLast not supported");
  }

  @Override
  public boolean first() throws SQLException {
    throw new SQLFeatureNotSupportedException("first not supported");
  }

  @Override
  public boolean last() throws SQLException {
    throw new SQLFeatureNotSupportedException("last not supported");
  }

  @Override
  public boolean absolute(int row) throws SQLException {
    throw new SQLFeatureNotSupportedException("absolute not supported");
  }

  @Override
  public boolean relative(int rows) throws SQLException {
    throw new SQLFeatureNotSupportedException("relative not supported");
  }

  @Override
  public boolean previous() throws SQLException {
    throw new SQLFeatureNotSupportedException("previous not supported");
  }

  @Override
  public boolean rowUpdated() throws SQLException {
    return false;
  }

  @Override
  public boolean rowInserted() throws SQLException {
    return false;
  }

  @Override
  public boolean rowDeleted() throws SQLException {
    return false;
  }

  @Override
  public void updateNull(int columnIndex) throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateBoolean(int columnIndex, boolean x) throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateByte(int columnIndex, byte x) throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateShort(int columnIndex, short x) throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateInt(int columnIndex, int x) throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateLong(int columnIndex, long x) throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateFloat(int columnIndex, float x) throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateDouble(int columnIndex, double x) throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateBigDecimal(int columnIndex, BigDecimal x) throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateString(int columnIndex, String x) throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateBytes(int columnIndex, byte[] x) throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateDate(int columnIndex, java.sql.Date x) throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateTime(int columnIndex, Time x) throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateTimestamp(int columnIndex, Timestamp x) throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateAsciiStream(int columnIndex, InputStream x, int length) throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateBinaryStream(int columnIndex, InputStream x, int length) throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateCharacterStream(int columnIndex, Reader x, int length) throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateObject(int columnIndex, Object x, int scaleOrLength) throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateObject(int columnIndex, Object x) throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateNull(String columnLabel) throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateBoolean(String columnLabel, boolean x) throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateByte(String columnLabel, byte x) throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateShort(String columnLabel, short x) throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateInt(String columnLabel, int x) throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateLong(String columnLabel, long x) throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateFloat(String columnLabel, float x) throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateDouble(String columnLabel, double x) throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateBigDecimal(String columnLabel, BigDecimal x) throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateString(String columnLabel, String x) throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateBytes(String columnLabel, byte[] x) throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateDate(String columnLabel, java.sql.Date x) throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateTime(String columnLabel, Time x) throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateTimestamp(String columnLabel, Timestamp x) throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateAsciiStream(String columnLabel, InputStream x, int length) throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateBinaryStream(String columnLabel, InputStream x, int length)
      throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateCharacterStream(String columnLabel, Reader reader, int length)
      throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateObject(String columnLabel, Object x, int scaleOrLength) throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateObject(String columnLabel, Object x) throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void insertRow() throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateRow() throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void deleteRow() throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void refreshRow() throws SQLException {
    throw new SQLFeatureNotSupportedException("refreshRow not supported");
  }

  @Override
  public void cancelRowUpdates() throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void moveToInsertRow() throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void moveToCurrentRow() throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public Object getObject(int columnIndex, Map<String, Class<?>> map) throws SQLException {
    return getObject(columnIndex);
  }

  @Override
  public Ref getRef(int columnIndex) throws SQLException {
    throw new SQLFeatureNotSupportedException("Ref not supported");
  }

  @Override
  public Blob getBlob(int columnIndex) throws SQLException {
    throw new SQLFeatureNotSupportedException("Blob not supported");
  }

  @Override
  public Clob getClob(int columnIndex) throws SQLException {
    throw new SQLFeatureNotSupportedException("Clob not supported");
  }

  @Override
  public Array getArray(int columnIndex) throws SQLException {
    throw new SQLFeatureNotSupportedException("Array not supported");
  }

  @Override
  public Object getObject(String columnLabel, Map<String, Class<?>> map) throws SQLException {
    return getObject(columnLabel);
  }

  @Override
  public Ref getRef(String columnLabel) throws SQLException {
    throw new SQLFeatureNotSupportedException("Ref not supported");
  }

  @Override
  public Blob getBlob(String columnLabel) throws SQLException {
    throw new SQLFeatureNotSupportedException("Blob not supported");
  }

  @Override
  public Clob getClob(String columnLabel) throws SQLException {
    throw new SQLFeatureNotSupportedException("Clob not supported");
  }

  @Override
  public Array getArray(String columnLabel) throws SQLException {
    throw new SQLFeatureNotSupportedException("Array not supported");
  }

  @Override
  public java.sql.Date getDate(int columnIndex, Calendar cal) throws SQLException {
    return getDate(columnIndex);
  }

  @Override
  public java.sql.Date getDate(String columnLabel, Calendar cal) throws SQLException {
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

  @Override
  public URL getURL(int columnIndex) throws SQLException {
    throw new SQLFeatureNotSupportedException("URL not supported");
  }

  @Override
  public URL getURL(String columnLabel) throws SQLException {
    throw new SQLFeatureNotSupportedException("URL not supported");
  }

  @Override
  public void updateRef(int columnIndex, Ref x) throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateRef(String columnLabel, Ref x) throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateBlob(int columnIndex, Blob x) throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateBlob(String columnLabel, Blob x) throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateClob(int columnIndex, Clob x) throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateClob(String columnLabel, Clob x) throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateArray(int columnIndex, Array x) throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateArray(String columnLabel, Array x) throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public RowId getRowId(int columnIndex) throws SQLException {
    throw new SQLFeatureNotSupportedException("RowId not supported");
  }

  @Override
  public RowId getRowId(String columnLabel) throws SQLException {
    throw new SQLFeatureNotSupportedException("RowId not supported");
  }

  @Override
  public void updateRowId(int columnIndex, RowId x) throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateRowId(String columnLabel, RowId x) throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public int getHoldability() throws SQLException {
    checkClosed();
    return CLOSE_CURSORS_AT_COMMIT;
  }

  @Override
  public void updateNString(int columnIndex, String nString) throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateNString(String columnLabel, String nString) throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateNClob(int columnIndex, NClob nClob) throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateNClob(String columnLabel, NClob nClob) throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public NClob getNClob(int columnIndex) throws SQLException {
    throw new SQLFeatureNotSupportedException("NClob not supported");
  }

  @Override
  public NClob getNClob(String columnLabel) throws SQLException {
    throw new SQLFeatureNotSupportedException("NClob not supported");
  }

  @Override
  public SQLXML getSQLXML(int columnIndex) throws SQLException {
    throw new SQLFeatureNotSupportedException("SQLXML not supported");
  }

  @Override
  public SQLXML getSQLXML(String columnLabel) throws SQLException {
    throw new SQLFeatureNotSupportedException("SQLXML not supported");
  }

  @Override
  public void updateSQLXML(int columnIndex, SQLXML xmlObject) throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateSQLXML(String columnLabel, SQLXML xmlObject) throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public String getNString(int columnIndex) throws SQLException {
    return getString(columnIndex);
  }

  @Override
  public String getNString(String columnLabel) throws SQLException {
    return getString(columnLabel);
  }

  @Override
  public Reader getNCharacterStream(int columnIndex) throws SQLException {
    throw new SQLFeatureNotSupportedException("getNCharacterStream not supported");
  }

  @Override
  public Reader getNCharacterStream(String columnLabel) throws SQLException {
    throw new SQLFeatureNotSupportedException("getNCharacterStream not supported");
  }

  @Override
  public void updateNCharacterStream(int columnIndex, Reader x, long length) throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateNCharacterStream(String columnLabel, Reader reader, long length)
      throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateAsciiStream(int columnIndex, InputStream x, long length) throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateBinaryStream(int columnIndex, InputStream x, long length) throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateCharacterStream(int columnIndex, Reader x, long length) throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateAsciiStream(String columnLabel, InputStream x, long length)
      throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateBinaryStream(String columnLabel, InputStream x, long length)
      throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateCharacterStream(String columnLabel, Reader reader, long length)
      throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateBlob(int columnIndex, InputStream inputStream, long length)
      throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateBlob(String columnLabel, InputStream inputStream, long length)
      throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateClob(int columnIndex, Reader reader, long length) throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateClob(String columnLabel, Reader reader, long length) throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateNClob(int columnIndex, Reader reader, long length) throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateNClob(String columnLabel, Reader reader, long length) throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateNCharacterStream(int columnIndex, Reader x) throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateNCharacterStream(String columnLabel, Reader reader) throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateAsciiStream(int columnIndex, InputStream x) throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateBinaryStream(int columnIndex, InputStream x) throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateCharacterStream(int columnIndex, Reader x) throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateAsciiStream(String columnLabel, InputStream x) throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateBinaryStream(String columnLabel, InputStream x) throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateCharacterStream(String columnLabel, Reader reader) throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateBlob(int columnIndex, InputStream inputStream) throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateBlob(String columnLabel, InputStream inputStream) throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateClob(int columnIndex, Reader reader) throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateClob(String columnLabel, Reader reader) throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateNClob(int columnIndex, Reader reader) throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateNClob(String columnLabel, Reader reader) throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public <T> T getObject(int columnIndex, Class<T> type) throws SQLException {
    throw new SQLFeatureNotSupportedException("getObject with type not supported");
  }

  @Override
  public <T> T getObject(String columnLabel, Class<T> type) throws SQLException {
    throw new SQLFeatureNotSupportedException("getObject with type not supported");
  }

  @Override
  public void updateObject(int columnIndex, Object x, SQLType targetSqlType, int scaleOrLength)
      throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateObject(String columnLabel, Object x, SQLType targetSqlType, int scaleOrLength)
      throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateObject(int columnIndex, Object x, SQLType targetSqlType) throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateObject(String columnLabel, Object x, SQLType targetSqlType)
      throws SQLException {
    throw new SQLFeatureNotSupportedException("ResultSet updates not supported");
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

  /**
   * Simple ResultSetMetaData implementation for metadata ResultSets.
   */
  private static class MetadataResultSetMetaData implements ResultSetMetaData {
    private final String[] columnNames;
    private final int[] columnTypes;

    MetadataResultSetMetaData(String[] columnNames, int[] columnTypes) {
      this.columnNames = columnNames;
      this.columnTypes = columnTypes;
    }

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

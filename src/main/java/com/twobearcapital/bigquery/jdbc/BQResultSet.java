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
 * <p>This implementation wraps a Google Cloud BigQuery {@link TableResult} and provides JDBC
 * ResultSet access to the query results.
 *
 * @since 1.0.0
 */
public class BQResultSet implements ResultSet {

  private static final Logger logger = LoggerFactory.getLogger(BQResultSet.class);

  private final BQStatement statement;
  private final TableResult tableResult;
  private final Iterator<FieldValueList> rowIterator;
  private FieldValueList currentRow;
  private boolean closed = false;
  private boolean wasNull = false;

  /**
   * Creates a new BigQuery ResultSet.
   *
   * @param statement the statement that produced this result set
   * @param tableResult the BigQuery table result
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
   * <p>This constructor is used internally for creating result sets that represent metadata query
   * results (e.g., from DatabaseMetaData methods).
   *
   * @param tableResult the BigQuery table result
   */
  BQResultSet(TableResult tableResult) {
    this.statement = null;
    this.tableResult = tableResult;
    this.rowIterator = tableResult.iterateAll().iterator();
    this.currentRow = null;
  }

  private void checkClosed() throws SQLException {
    if (closed) {
      throw new BQSQLException("ResultSet is closed", BQSQLException.SQLSTATE_CONNECTION_CLOSED);
    }
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
  public void close() throws SQLException {
    if (closed) {
      return;
    }
    closed = true;
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
    return value.isNull() ? 0 : (byte) value.getLongValue();
  }

  @Override
  public short getShort(int columnIndex) throws SQLException {
    FieldValue value = getFieldValue(columnIndex);
    return value.isNull() ? 0 : (short) value.getLongValue();
  }

  @Override
  public int getInt(int columnIndex) throws SQLException {
    FieldValue value = getFieldValue(columnIndex);
    return value.isNull() ? 0 : (int) value.getLongValue();
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
  public InputStream getAsciiStream(int columnIndex) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("getAsciiStream not supported");
  }

  @Override
  public InputStream getUnicodeStream(int columnIndex) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("getUnicodeStream not supported");
  }

  @Override
  public InputStream getBinaryStream(int columnIndex) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("getBinaryStream not supported");
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
  public InputStream getAsciiStream(String columnLabel) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("getAsciiStream not supported");
  }

  @Override
  public InputStream getUnicodeStream(String columnLabel) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("getUnicodeStream not supported");
  }

  @Override
  public InputStream getBinaryStream(String columnLabel) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("getBinaryStream not supported");
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

  @Override
  public String getCursorName() throws SQLException {
    throw new BQSQLFeatureNotSupportedException("Named cursors not supported");
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
  public Reader getCharacterStream(int columnIndex) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("getCharacterStream not supported");
  }

  @Override
  public Reader getCharacterStream(String columnLabel) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("getCharacterStream not supported");
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
  public boolean isFirst() throws SQLException {
    throw new BQSQLFeatureNotSupportedException("isFirst not supported");
  }

  @Override
  public boolean isLast() throws SQLException {
    throw new BQSQLFeatureNotSupportedException("isLast not supported");
  }

  @Override
  public void beforeFirst() throws SQLException {
    throw new BQSQLFeatureNotSupportedException("beforeFirst not supported");
  }

  @Override
  public void afterLast() throws SQLException {
    throw new BQSQLFeatureNotSupportedException("afterLast not supported");
  }

  @Override
  public boolean first() throws SQLException {
    throw new BQSQLFeatureNotSupportedException("first not supported");
  }

  @Override
  public boolean last() throws SQLException {
    throw new BQSQLFeatureNotSupportedException("last not supported");
  }

  @Override
  public int getRow() throws SQLException {
    throw new BQSQLFeatureNotSupportedException("getRow not supported");
  }

  @Override
  public boolean absolute(int row) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("absolute positioning not supported");
  }

  @Override
  public boolean relative(int rows) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("relative positioning not supported");
  }

  @Override
  public boolean previous() throws SQLException {
    throw new BQSQLFeatureNotSupportedException("previous not supported");
  }

  @Override
  public void setFetchDirection(int direction) throws SQLException {
    checkClosed();
    if (direction != FETCH_FORWARD) {
      throw new BQSQLFeatureNotSupportedException("Only FETCH_FORWARD is supported");
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
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateBoolean(int columnIndex, boolean x) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateByte(int columnIndex, byte x) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateShort(int columnIndex, short x) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateInt(int columnIndex, int x) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateLong(int columnIndex, long x) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateFloat(int columnIndex, float x) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateDouble(int columnIndex, double x) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateBigDecimal(int columnIndex, BigDecimal x) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateString(int columnIndex, String x) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateBytes(int columnIndex, byte[] x) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateDate(int columnIndex, Date x) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateTime(int columnIndex, Time x) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateTimestamp(int columnIndex, Timestamp x) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateAsciiStream(int columnIndex, InputStream x, int length) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateBinaryStream(int columnIndex, InputStream x, int length) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateCharacterStream(int columnIndex, Reader x, int length) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateObject(int columnIndex, Object x, int scaleOrLength) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateObject(int columnIndex, Object x) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateNull(String columnLabel) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateBoolean(String columnLabel, boolean x) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateByte(String columnLabel, byte x) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateShort(String columnLabel, short x) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateInt(String columnLabel, int x) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateLong(String columnLabel, long x) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateFloat(String columnLabel, float x) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateDouble(String columnLabel, double x) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateBigDecimal(String columnLabel, BigDecimal x) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateString(String columnLabel, String x) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateBytes(String columnLabel, byte[] x) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateDate(String columnLabel, Date x) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateTime(String columnLabel, Time x) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateTimestamp(String columnLabel, Timestamp x) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateAsciiStream(String columnLabel, InputStream x, int length) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateBinaryStream(String columnLabel, InputStream x, int length)
      throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateCharacterStream(String columnLabel, Reader reader, int length)
      throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateObject(String columnLabel, Object x, int scaleOrLength) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateObject(String columnLabel, Object x) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void insertRow() throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateRow() throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void deleteRow() throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void refreshRow() throws SQLException {
    throw new BQSQLFeatureNotSupportedException("refreshRow not supported");
  }

  @Override
  public void cancelRowUpdates() throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void moveToInsertRow() throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void moveToCurrentRow() throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
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
  public Ref getRef(int columnIndex) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("Ref not supported");
  }

  @Override
  public Blob getBlob(int columnIndex) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("Blob not supported");
  }

  @Override
  public Clob getClob(int columnIndex) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("Clob not supported");
  }

  @Override
  public Array getArray(int columnIndex) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("Array not supported");
  }

  @Override
  public Object getObject(String columnLabel, Map<String, Class<?>> map) throws SQLException {
    return getObject(columnLabel);
  }

  @Override
  public Ref getRef(String columnLabel) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("Ref not supported");
  }

  @Override
  public Blob getBlob(String columnLabel) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("Blob not supported");
  }

  @Override
  public Clob getClob(String columnLabel) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("Clob not supported");
  }

  @Override
  public Array getArray(String columnLabel) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("Array not supported");
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

  @Override
  public URL getURL(int columnIndex) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("URL not supported");
  }

  @Override
  public URL getURL(String columnLabel) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("URL not supported");
  }

  @Override
  public void updateRef(int columnIndex, Ref x) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateRef(String columnLabel, Ref x) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateBlob(int columnIndex, Blob x) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateBlob(String columnLabel, Blob x) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateClob(int columnIndex, Clob x) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateClob(String columnLabel, Clob x) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateArray(int columnIndex, Array x) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateArray(String columnLabel, Array x) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public RowId getRowId(int columnIndex) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("RowId not supported");
  }

  @Override
  public RowId getRowId(String columnLabel) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("RowId not supported");
  }

  @Override
  public void updateRowId(int columnIndex, RowId x) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateRowId(String columnLabel, RowId x) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public int getHoldability() throws SQLException {
    checkClosed();
    return CLOSE_CURSORS_AT_COMMIT;
  }

  @Override
  public boolean isClosed() {
    return closed;
  }

  @Override
  public void updateNString(int columnIndex, String nString) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateNString(String columnLabel, String nString) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateNClob(int columnIndex, NClob nClob) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateNClob(String columnLabel, NClob nClob) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public NClob getNClob(int columnIndex) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("NClob not supported");
  }

  @Override
  public NClob getNClob(String columnLabel) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("NClob not supported");
  }

  @Override
  public SQLXML getSQLXML(int columnIndex) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("SQLXML not supported");
  }

  @Override
  public SQLXML getSQLXML(String columnLabel) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("SQLXML not supported");
  }

  @Override
  public void updateSQLXML(int columnIndex, SQLXML xmlObject) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateSQLXML(String columnLabel, SQLXML xmlObject) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
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
    throw new BQSQLFeatureNotSupportedException("getNCharacterStream not supported");
  }

  @Override
  public Reader getNCharacterStream(String columnLabel) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("getNCharacterStream not supported");
  }

  @Override
  public void updateNCharacterStream(int columnIndex, Reader x, long length) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateNCharacterStream(String columnLabel, Reader reader, long length)
      throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateAsciiStream(int columnIndex, InputStream x, long length) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateBinaryStream(int columnIndex, InputStream x, long length) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateCharacterStream(int columnIndex, Reader x, long length) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateAsciiStream(String columnLabel, InputStream x, long length)
      throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateBinaryStream(String columnLabel, InputStream x, long length)
      throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateCharacterStream(String columnLabel, Reader reader, long length)
      throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateBlob(int columnIndex, InputStream inputStream, long length)
      throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateBlob(String columnLabel, InputStream inputStream, long length)
      throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateClob(int columnIndex, Reader reader, long length) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateClob(String columnLabel, Reader reader, long length) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateNClob(int columnIndex, Reader reader, long length) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateNClob(String columnLabel, Reader reader, long length) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateNCharacterStream(int columnIndex, Reader x) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateNCharacterStream(String columnLabel, Reader reader) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateAsciiStream(int columnIndex, InputStream x) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateBinaryStream(int columnIndex, InputStream x) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateCharacterStream(int columnIndex, Reader x) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateAsciiStream(String columnLabel, InputStream x) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateBinaryStream(String columnLabel, InputStream x) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateCharacterStream(String columnLabel, Reader reader) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateBlob(int columnIndex, InputStream inputStream) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateBlob(String columnLabel, InputStream inputStream) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateClob(int columnIndex, Reader reader) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateClob(String columnLabel, Reader reader) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateNClob(int columnIndex, Reader reader) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateNClob(String columnLabel, Reader reader) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public <T> T getObject(int columnIndex, Class<T> type) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("getObject with type not supported");
  }

  @Override
  public <T> T getObject(String columnLabel, Class<T> type) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("getObject with type not supported");
  }

  @Override
  public void updateObject(int columnIndex, Object x, SQLType targetSqlType, int scaleOrLength)
      throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateObject(String columnLabel, Object x, SQLType targetSqlType, int scaleOrLength)
      throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateObject(int columnIndex, Object x, SQLType targetSqlType) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
  }

  @Override
  public void updateObject(String columnLabel, Object x, SQLType targetSqlType)
      throws SQLException {
    throw new BQSQLFeatureNotSupportedException("ResultSet updates not supported");
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

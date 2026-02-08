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
package com.tbc.bq.jdbc.base;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.sql.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for ReadOnlyResultSetMixin.
 *
 * @since 1.0.15
 */
class ReadOnlyResultSetMixinTest {

	private TestResultSet resultSet;

	/**
	 * Minimal test implementation of ResultSet using ReadOnlyResultSetMixin.
	 */
	private static class TestResultSet implements ReadOnlyResultSetMixin {
		// Minimal implementation - all update methods come from mixin
		// Only implement absolutely required methods to avoid abstract errors

		@Override
		public boolean next() {
			return false;
		}

		@Override
		public void close() {
		}

		@Override
		public boolean wasNull() {
			return false;
		}

		@Override
		public String getString(int columnIndex) {
			return null;
		}

		@Override
		public boolean getBoolean(int columnIndex) {
			return false;
		}

		@Override
		public byte getByte(int columnIndex) {
			return 0;
		}

		@Override
		public short getShort(int columnIndex) {
			return 0;
		}

		@Override
		public int getInt(int columnIndex) {
			return 0;
		}

		@Override
		public long getLong(int columnIndex) {
			return 0;
		}

		@Override
		public float getFloat(int columnIndex) {
			return 0;
		}

		@Override
		public double getDouble(int columnIndex) {
			return 0;
		}

		@Override
		public BigDecimal getBigDecimal(int columnIndex, int scale) {
			return null;
		}

		@Override
		public byte[] getBytes(int columnIndex) {
			return new byte[0];
		}

		@Override
		public Date getDate(int columnIndex) {
			return null;
		}

		@Override
		public Time getTime(int columnIndex) {
			return null;
		}

		@Override
		public Timestamp getTimestamp(int columnIndex) {
			return null;
		}

		@Override
		public InputStream getAsciiStream(int columnIndex) {
			return null;
		}

		@Override
		public InputStream getUnicodeStream(int columnIndex) {
			return null;
		}

		@Override
		public InputStream getBinaryStream(int columnIndex) {
			return null;
		}

		@Override
		public String getString(String columnLabel) {
			return null;
		}

		@Override
		public boolean getBoolean(String columnLabel) {
			return false;
		}

		@Override
		public byte getByte(String columnLabel) {
			return 0;
		}

		@Override
		public short getShort(String columnLabel) {
			return 0;
		}

		@Override
		public int getInt(String columnLabel) {
			return 0;
		}

		@Override
		public long getLong(String columnLabel) {
			return 0;
		}

		@Override
		public float getFloat(String columnLabel) {
			return 0;
		}

		@Override
		public double getDouble(String columnLabel) {
			return 0;
		}

		@Override
		public BigDecimal getBigDecimal(String columnLabel, int scale) {
			return null;
		}

		@Override
		public byte[] getBytes(String columnLabel) {
			return new byte[0];
		}

		@Override
		public Date getDate(String columnLabel) {
			return null;
		}

		@Override
		public Time getTime(String columnLabel) {
			return null;
		}

		@Override
		public Timestamp getTimestamp(String columnLabel) {
			return null;
		}

		@Override
		public InputStream getAsciiStream(String columnLabel) {
			return null;
		}

		@Override
		public InputStream getUnicodeStream(String columnLabel) {
			return null;
		}

		@Override
		public InputStream getBinaryStream(String columnLabel) {
			return null;
		}

		@Override
		public SQLWarning getWarnings() {
			return null;
		}

		@Override
		public void clearWarnings() {
		}

		@Override
		public String getCursorName() {
			return null;
		}

		@Override
		public ResultSetMetaData getMetaData() {
			return null;
		}

		@Override
		public Object getObject(int columnIndex) {
			return null;
		}

		@Override
		public Object getObject(String columnLabel) {
			return null;
		}

		@Override
		public int findColumn(String columnLabel) {
			return 0;
		}

		@Override
		public Reader getCharacterStream(int columnIndex) {
			return null;
		}

		@Override
		public Reader getCharacterStream(String columnLabel) {
			return null;
		}

		@Override
		public BigDecimal getBigDecimal(int columnIndex) {
			return null;
		}

		@Override
		public BigDecimal getBigDecimal(String columnLabel) {
			return null;
		}

		@Override
		public boolean isBeforeFirst() {
			return false;
		}

		@Override
		public boolean isAfterLast() {
			return false;
		}

		@Override
		public boolean isFirst() {
			return false;
		}

		@Override
		public boolean isLast() {
			return false;
		}

		@Override
		public void beforeFirst() {
		}

		@Override
		public void afterLast() {
		}

		@Override
		public boolean first() {
			return false;
		}

		@Override
		public boolean last() {
			return false;
		}

		@Override
		public int getRow() {
			return 0;
		}

		@Override
		public boolean absolute(int row) {
			return false;
		}

		@Override
		public boolean relative(int rows) {
			return false;
		}

		@Override
		public boolean previous() {
			return false;
		}

		@Override
		public void setFetchDirection(int direction) {
		}

		@Override
		public int getFetchDirection() {
			return 0;
		}

		@Override
		public void setFetchSize(int rows) {
		}

		@Override
		public int getFetchSize() {
			return 0;
		}

		@Override
		public int getType() {
			return 0;
		}

		@Override
		public int getConcurrency() {
			return 0;
		}

		@Override
		public boolean rowUpdated() {
			return false;
		}

		@Override
		public boolean rowInserted() {
			return false;
		}

		@Override
		public boolean rowDeleted() {
			return false;
		}

		@Override
		public Statement getStatement() {
			return null;
		}

		@Override
		public Object getObject(int columnIndex, java.util.Map<String, Class<?>> map) {
			return null;
		}

		@Override
		public Ref getRef(int columnIndex) {
			return null;
		}

		@Override
		public Blob getBlob(int columnIndex) {
			return null;
		}

		@Override
		public Clob getClob(int columnIndex) {
			return null;
		}

		@Override
		public Array getArray(int columnIndex) {
			return null;
		}

		@Override
		public Object getObject(String columnLabel, java.util.Map<String, Class<?>> map) {
			return null;
		}

		@Override
		public Ref getRef(String columnLabel) {
			return null;
		}

		@Override
		public Blob getBlob(String columnLabel) {
			return null;
		}

		@Override
		public Clob getClob(String columnLabel) {
			return null;
		}

		@Override
		public Array getArray(String columnLabel) {
			return null;
		}

		@Override
		public Date getDate(int columnIndex, java.util.Calendar cal) {
			return null;
		}

		@Override
		public Date getDate(String columnLabel, java.util.Calendar cal) {
			return null;
		}

		@Override
		public Time getTime(int columnIndex, java.util.Calendar cal) {
			return null;
		}

		@Override
		public Time getTime(String columnLabel, java.util.Calendar cal) {
			return null;
		}

		@Override
		public Timestamp getTimestamp(int columnIndex, java.util.Calendar cal) {
			return null;
		}

		@Override
		public Timestamp getTimestamp(String columnLabel, java.util.Calendar cal) {
			return null;
		}

		@Override
		public java.net.URL getURL(int columnIndex) {
			return null;
		}

		@Override
		public java.net.URL getURL(String columnLabel) {
			return null;
		}

		@Override
		public RowId getRowId(int columnIndex) {
			return null;
		}

		@Override
		public RowId getRowId(String columnLabel) {
			return null;
		}

		@Override
		public int getHoldability() {
			return 0;
		}

		@Override
		public boolean isClosed() {
			return false;
		}

		@Override
		public String getNString(int columnIndex) {
			return null;
		}

		@Override
		public String getNString(String columnLabel) {
			return null;
		}

		@Override
		public Reader getNCharacterStream(int columnIndex) {
			return null;
		}

		@Override
		public Reader getNCharacterStream(String columnLabel) {
			return null;
		}

		@Override
		public NClob getNClob(int columnIndex) {
			return null;
		}

		@Override
		public NClob getNClob(String columnLabel) {
			return null;
		}

		@Override
		public SQLXML getSQLXML(int columnIndex) {
			return null;
		}

		@Override
		public SQLXML getSQLXML(String columnLabel) {
			return null;
		}

		@Override
		public <T> T getObject(int columnIndex, Class<T> type) {
			return null;
		}

		@Override
		public <T> T getObject(String columnLabel, Class<T> type) {
			return null;
		}

		@Override
		public <T> T unwrap(Class<T> iface) {
			return null;
		}

		@Override
		public boolean isWrapperFor(Class<?> iface) {
			return false;
		}
	}

	@BeforeEach
	void setUp() {
		resultSet = new TestResultSet();
	}

	// Update methods by column index tests

	@Test
	void testUpdateNullByIndexThrows() {
		// Then: Should throw SQLFeatureNotSupportedException
		SQLFeatureNotSupportedException ex = assertThrows(SQLFeatureNotSupportedException.class,
				() -> resultSet.updateNull(1));
		assertEquals("ResultSet updates not supported", ex.getMessage());
	}

	@Test
	void testUpdateBooleanByIndexThrows() {
		// Then: Should throw SQLFeatureNotSupportedException
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.updateBoolean(1, true));
	}

	@Test
	void testUpdateByteByIndexThrows() {
		// Then: Should throw SQLFeatureNotSupportedException
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.updateByte(1, (byte) 1));
	}

	@Test
	void testUpdateShortByIndexThrows() {
		// Then: Should throw SQLFeatureNotSupportedException
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.updateShort(1, (short) 1));
	}

	@Test
	void testUpdateIntByIndexThrows() {
		// Then: Should throw SQLFeatureNotSupportedException
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.updateInt(1, 1));
	}

	@Test
	void testUpdateLongByIndexThrows() {
		// Then: Should throw SQLFeatureNotSupportedException
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.updateLong(1, 1L));
	}

	@Test
	void testUpdateFloatByIndexThrows() {
		// Then: Should throw SQLFeatureNotSupportedException
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.updateFloat(1, 1.0f));
	}

	@Test
	void testUpdateDoubleByIndexThrows() {
		// Then: Should throw SQLFeatureNotSupportedException
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.updateDouble(1, 1.0));
	}

	@Test
	void testUpdateBigDecimalByIndexThrows() {
		// Then: Should throw SQLFeatureNotSupportedException
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.updateBigDecimal(1, BigDecimal.ONE));
	}

	@Test
	void testUpdateStringByIndexThrows() {
		// Then: Should throw SQLFeatureNotSupportedException
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.updateString(1, "test"));
	}

	// Update methods by column label tests

	@Test
	void testUpdateNullByLabelThrows() {
		// Then: Should throw SQLFeatureNotSupportedException
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.updateNull("col"));
	}

	@Test
	void testUpdateStringByLabelThrows() {
		// Then: Should throw SQLFeatureNotSupportedException
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.updateString("col", "test"));
	}

	// Row operation tests

	@Test
	void testInsertRowThrows() {
		// Then: Should throw SQLFeatureNotSupportedException
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.insertRow());
	}

	@Test
	void testUpdateRowThrows() {
		// Then: Should throw SQLFeatureNotSupportedException
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.updateRow());
	}

	@Test
	void testDeleteRowThrows() {
		// Then: Should throw SQLFeatureNotSupportedException
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.deleteRow());
	}

	@Test
	void testRefreshRowThrows() {
		// Then: Should throw SQLFeatureNotSupportedException
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.refreshRow());
	}

	@Test
	void testCancelRowUpdatesThrows() {
		// Then: Should throw SQLFeatureNotSupportedException
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.cancelRowUpdates());
	}

	@Test
	void testMoveToInsertRowThrows() {
		// Then: Should throw SQLFeatureNotSupportedException
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.moveToInsertRow());
	}

	@Test
	void testMoveToCurrentRowThrows() {
		// Then: Should throw SQLFeatureNotSupportedException
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.moveToCurrentRow());
	}

	// JDBC 4.0 update methods tests

	@Test
	void testUpdateRefByIndexThrows() {
		// Then: Should throw SQLFeatureNotSupportedException
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.updateRef(1, null));
	}

	@Test
	void testUpdateBlobByIndexThrows() {
		// Then: Should throw SQLFeatureNotSupportedException
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.updateBlob(1, (Blob) null));
	}

	@Test
	void testUpdateClobByIndexThrows() {
		// Then: Should throw SQLFeatureNotSupportedException
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.updateClob(1, (Clob) null));
	}

	@Test
	void testUpdateArrayByIndexThrows() {
		// Then: Should throw SQLFeatureNotSupportedException
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.updateArray(1, null));
	}

	@Test
	void testUpdateNStringByIndexThrows() {
		// Then: Should throw SQLFeatureNotSupportedException
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.updateNString(1, "test"));
	}

	// Exception consistency test

	@Test
	void testAllUpdateMethodsThrowSameException() {
		// When: Calling different update methods
		SQLFeatureNotSupportedException ex1 = assertThrows(SQLFeatureNotSupportedException.class,
				() -> resultSet.updateNull(1));
		SQLFeatureNotSupportedException ex2 = assertThrows(SQLFeatureNotSupportedException.class,
				() -> resultSet.updateString(1, "test"));
		SQLFeatureNotSupportedException ex3 = assertThrows(SQLFeatureNotSupportedException.class,
				() -> resultSet.updateRow());

		// Then: All should have same message and SQL state
		assertEquals(ex1.getMessage(), ex2.getMessage());
		assertEquals(ex1.getMessage(), ex3.getMessage());
		assertEquals(ex1.getSQLState(), ex2.getSQLState());
		assertEquals(ex1.getSQLState(), ex3.getSQLState());
		assertEquals("0A000", ex1.getSQLState());
	}
}

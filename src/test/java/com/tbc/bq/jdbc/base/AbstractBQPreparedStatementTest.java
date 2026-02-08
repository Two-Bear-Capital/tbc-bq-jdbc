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

import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.DatasetId;
import com.tbc.bq.jdbc.BQConnection;
import com.tbc.bq.jdbc.config.ConnectionProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.sql.*;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link AbstractBQPreparedStatement}.
 *
 * @since 1.0.0
 */
class AbstractBQPreparedStatementTest {

	private BQConnection mockConnection;
	private BigQuery mockBigQuery;
	private ConnectionProperties mockProperties;
	private TestPreparedStatement statement;

	@BeforeEach
	void setUp() {
		mockConnection = mock(BQConnection.class);
		mockBigQuery = mock(BigQuery.class);
		mockProperties = mock(ConnectionProperties.class);

		when(mockConnection.getBigQuery()).thenReturn(mockBigQuery);
		when(mockConnection.getProperties()).thenReturn(mockProperties);
		when(mockProperties.getDatasetId()).thenReturn(DatasetId.of("test-project", "test-dataset"));
		when(mockProperties.labels()).thenReturn(java.util.Map.of());
		when(mockProperties.timeoutSeconds()).thenReturn(30);

		statement = new TestPreparedStatement(mockConnection);
	}

	@Test
	void testSetAsciiStreamThrowsException() {
		// Then: All setAsciiStream variants should throw
		// SQLFeatureNotSupportedException
		InputStream stream = new ByteArrayInputStream(new byte[0]);
		assertThrows(SQLFeatureNotSupportedException.class, () -> statement.setAsciiStream(1, stream, 100));
		assertThrows(SQLFeatureNotSupportedException.class, () -> statement.setAsciiStream(1, stream, 100L));
		assertThrows(SQLFeatureNotSupportedException.class, () -> statement.setAsciiStream(1, stream));
	}

	@Test
	@SuppressWarnings("deprecation")
	void testSetUnicodeStreamThrowsException() {
		// Then: setUnicodeStream should throw SQLFeatureNotSupportedException
		InputStream stream = new ByteArrayInputStream(new byte[0]);
		assertThrows(SQLFeatureNotSupportedException.class, () -> statement.setUnicodeStream(1, stream, 100));
	}

	@Test
	void testSetBinaryStreamThrowsException() {
		// Then: All setBinaryStream variants should throw
		// SQLFeatureNotSupportedException
		InputStream stream = new ByteArrayInputStream(new byte[0]);
		assertThrows(SQLFeatureNotSupportedException.class, () -> statement.setBinaryStream(1, stream, 100));
		assertThrows(SQLFeatureNotSupportedException.class, () -> statement.setBinaryStream(1, stream, 100L));
		assertThrows(SQLFeatureNotSupportedException.class, () -> statement.setBinaryStream(1, stream));
	}

	@Test
	void testSetCharacterStreamThrowsException() {
		// Then: All setCharacterStream variants should throw
		// SQLFeatureNotSupportedException
		Reader reader = new StringReader("");
		assertThrows(SQLFeatureNotSupportedException.class, () -> statement.setCharacterStream(1, reader, 100));
		assertThrows(SQLFeatureNotSupportedException.class, () -> statement.setCharacterStream(1, reader, 100L));
		assertThrows(SQLFeatureNotSupportedException.class, () -> statement.setCharacterStream(1, reader));
	}

	@Test
	void testSetNCharacterStreamThrowsException() {
		// Then: All setNCharacterStream variants should throw
		// SQLFeatureNotSupportedException
		Reader reader = new StringReader("");
		assertThrows(SQLFeatureNotSupportedException.class, () -> statement.setNCharacterStream(1, reader, 100L));
		assertThrows(SQLFeatureNotSupportedException.class, () -> statement.setNCharacterStream(1, reader));
	}

	@Test
	void testSetRefThrowsException() {
		// Then: setRef should throw SQLFeatureNotSupportedException
		assertThrows(SQLFeatureNotSupportedException.class, () -> statement.setRef(1, null));
	}

	@Test
	void testSetBlobThrowsException() {
		// Then: All setBlob variants should throw SQLFeatureNotSupportedException
		InputStream stream = new ByteArrayInputStream(new byte[0]);
		assertThrows(SQLFeatureNotSupportedException.class, () -> statement.setBlob(1, (Blob) null));
		assertThrows(SQLFeatureNotSupportedException.class, () -> statement.setBlob(1, stream, 100L));
		assertThrows(SQLFeatureNotSupportedException.class, () -> statement.setBlob(1, stream));
	}

	@Test
	void testSetClobThrowsException() {
		// Then: All setClob variants should throw SQLFeatureNotSupportedException
		Reader reader = new StringReader("");
		assertThrows(SQLFeatureNotSupportedException.class, () -> statement.setClob(1, (Clob) null));
		assertThrows(SQLFeatureNotSupportedException.class, () -> statement.setClob(1, reader, 100L));
		assertThrows(SQLFeatureNotSupportedException.class, () -> statement.setClob(1, reader));
	}

	@Test
	void testSetArrayThrowsException() {
		// Then: setArray should throw SQLFeatureNotSupportedException
		assertThrows(SQLFeatureNotSupportedException.class, () -> statement.setArray(1, null));
	}

	@Test
	void testSetRowIdThrowsException() {
		// Then: setRowId should throw SQLFeatureNotSupportedException
		assertThrows(SQLFeatureNotSupportedException.class, () -> statement.setRowId(1, null));
	}

	@Test
	void testSetNClobThrowsException() {
		// Then: All setNClob variants should throw SQLFeatureNotSupportedException
		Reader reader = new StringReader("");
		assertThrows(SQLFeatureNotSupportedException.class, () -> statement.setNClob(1, (NClob) null));
		assertThrows(SQLFeatureNotSupportedException.class, () -> statement.setNClob(1, reader, 100L));
		assertThrows(SQLFeatureNotSupportedException.class, () -> statement.setNClob(1, reader));
	}

	@Test
	void testSetSQLXMLThrowsException() {
		// Then: setSQLXML should throw SQLFeatureNotSupportedException
		assertThrows(SQLFeatureNotSupportedException.class, () -> statement.setSQLXML(1, null));
	}

	@Test
	void testAddBatchThrowsException() {
		// Then: addBatch should throw SQLFeatureNotSupportedException
		assertThrows(SQLFeatureNotSupportedException.class, () -> statement.addBatch());
	}

	/**
	 * Test implementation of AbstractBQPreparedStatement for testing purposes.
	 */
	private static class TestPreparedStatement extends AbstractBQPreparedStatement {

		TestPreparedStatement(BQConnection connection) {
			super(connection);
		}

		// Minimal implementations for required PreparedStatement methods

		@Override
		public ResultSet executeQuery() throws SQLException {
			return null;
		}

		@Override
		public int executeUpdate() throws SQLException {
			return 0;
		}

		@Override
		public void setNull(int parameterIndex, int sqlType) throws SQLException {
		}

		@Override
		public void setBoolean(int parameterIndex, boolean x) throws SQLException {
		}

		@Override
		public void setByte(int parameterIndex, byte x) throws SQLException {
		}

		@Override
		public void setShort(int parameterIndex, short x) throws SQLException {
		}

		@Override
		public void setInt(int parameterIndex, int x) throws SQLException {
		}

		@Override
		public void setLong(int parameterIndex, long x) throws SQLException {
		}

		@Override
		public void setFloat(int parameterIndex, float x) throws SQLException {
		}

		@Override
		public void setDouble(int parameterIndex, double x) throws SQLException {
		}

		@Override
		public void setBigDecimal(int parameterIndex, BigDecimal x) throws SQLException {
		}

		@Override
		public void setString(int parameterIndex, String x) throws SQLException {
		}

		@Override
		public void setBytes(int parameterIndex, byte[] x) throws SQLException {
		}

		@Override
		public void setDate(int parameterIndex, Date x) throws SQLException {
		}

		@Override
		public void setTime(int parameterIndex, Time x) throws SQLException {
		}

		@Override
		public void setTimestamp(int parameterIndex, Timestamp x) throws SQLException {
		}

		@Override
		public void clearParameters() throws SQLException {
		}

		@Override
		public void setObject(int parameterIndex, Object x, int targetSqlType) throws SQLException {
		}

		@Override
		public void setObject(int parameterIndex, Object x) throws SQLException {
		}

		@Override
		public boolean execute() throws SQLException {
			return false;
		}

		@Override
		public ResultSetMetaData getMetaData() throws SQLException {
			return null;
		}

		@Override
		public void setDate(int parameterIndex, Date x, java.util.Calendar cal) throws SQLException {
		}

		@Override
		public void setTime(int parameterIndex, Time x, java.util.Calendar cal) throws SQLException {
		}

		@Override
		public void setTimestamp(int parameterIndex, Timestamp x, java.util.Calendar cal) throws SQLException {
		}

		@Override
		public void setNull(int parameterIndex, int sqlType, String typeName) throws SQLException {
		}

		@Override
		public void setURL(int parameterIndex, java.net.URL x) throws SQLException {
		}

		@Override
		public ParameterMetaData getParameterMetaData() throws SQLException {
			return null;
		}

		@Override
		public void setObject(int parameterIndex, Object x, int targetSqlType, int scaleOrLength) throws SQLException {
		}

		@Override
		public void setNString(int parameterIndex, String value) throws SQLException {
		}
	}
}

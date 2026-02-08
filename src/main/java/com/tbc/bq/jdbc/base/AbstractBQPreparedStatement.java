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

import com.tbc.bq.jdbc.BQConnection;
import com.tbc.bq.jdbc.BQStatement;
import com.tbc.bq.jdbc.exception.BQSQLFeatureNotSupportedException;

import java.io.InputStream;
import java.io.Reader;
import java.sql.*;

/**
 * Base PreparedStatement implementation that provides default
 * exception-throwing implementations for all unsupported PreparedStatement
 * methods.
 *
 * <p>
 * Subclasses should override only the methods they actually support.
 * </p>
 *
 * @since 1.0.0
 */
public abstract class AbstractBQPreparedStatement extends BQStatement implements PreparedStatement {

	protected AbstractBQPreparedStatement(BQConnection connection) {
		super(connection);
	}

	// Stream setters - not supported

	@Override
	public void setAsciiStream(int parameterIndex, InputStream x, int length) throws SQLException {
		throw new BQSQLFeatureNotSupportedException("setAsciiStream not supported");
	}

	@Override
	public void setAsciiStream(int parameterIndex, InputStream x, long length) throws SQLException {
		throw new BQSQLFeatureNotSupportedException("setAsciiStream not supported");
	}

	@Override
	public void setAsciiStream(int parameterIndex, InputStream x) throws SQLException {
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
	public void setBinaryStream(int parameterIndex, InputStream x, long length) throws SQLException {
		throw new BQSQLFeatureNotSupportedException("setBinaryStream not supported");
	}

	@Override
	public void setBinaryStream(int parameterIndex, InputStream x) throws SQLException {
		throw new BQSQLFeatureNotSupportedException("setBinaryStream not supported");
	}

	@Override
	public void setCharacterStream(int parameterIndex, Reader reader, int length) throws SQLException {
		throw new BQSQLFeatureNotSupportedException("setCharacterStream not supported");
	}

	@Override
	public void setCharacterStream(int parameterIndex, Reader reader, long length) throws SQLException {
		throw new BQSQLFeatureNotSupportedException("setCharacterStream not supported");
	}

	@Override
	public void setCharacterStream(int parameterIndex, Reader reader) throws SQLException {
		throw new BQSQLFeatureNotSupportedException("setCharacterStream not supported");
	}

	@Override
	public void setNCharacterStream(int parameterIndex, Reader value, long length) throws SQLException {
		throw new BQSQLFeatureNotSupportedException("setNCharacterStream not supported");
	}

	@Override
	public void setNCharacterStream(int parameterIndex, Reader value) throws SQLException {
		throw new BQSQLFeatureNotSupportedException("setNCharacterStream not supported");
	}

	// Advanced type setters - not supported

	@Override
	public void setRef(int parameterIndex, Ref x) throws SQLException {
		throw new BQSQLFeatureNotSupportedException("setRef not supported");
	}

	@Override
	public void setBlob(int parameterIndex, Blob x) throws SQLException {
		throw new BQSQLFeatureNotSupportedException("setBlob not supported");
	}

	@Override
	public void setBlob(int parameterIndex, InputStream inputStream, long length) throws SQLException {
		throw new BQSQLFeatureNotSupportedException("setBlob not supported");
	}

	@Override
	public void setBlob(int parameterIndex, InputStream inputStream) throws SQLException {
		throw new BQSQLFeatureNotSupportedException("setBlob not supported");
	}

	@Override
	public void setClob(int parameterIndex, Clob x) throws SQLException {
		throw new BQSQLFeatureNotSupportedException("setClob not supported");
	}

	@Override
	public void setClob(int parameterIndex, Reader reader, long length) throws SQLException {
		throw new BQSQLFeatureNotSupportedException("setClob not supported");
	}

	@Override
	public void setClob(int parameterIndex, Reader reader) throws SQLException {
		throw new BQSQLFeatureNotSupportedException("setClob not supported");
	}

	@Override
	public void setArray(int parameterIndex, Array x) throws SQLException {
		throw new BQSQLFeatureNotSupportedException("setArray not supported");
	}

	@Override
	public void setRowId(int parameterIndex, RowId x) throws SQLException {
		throw new BQSQLFeatureNotSupportedException("setRowId not supported");
	}

	@Override
	public void setNClob(int parameterIndex, NClob value) throws SQLException {
		throw new BQSQLFeatureNotSupportedException("setNClob not supported");
	}

	@Override
	public void setNClob(int parameterIndex, Reader reader, long length) throws SQLException {
		throw new BQSQLFeatureNotSupportedException("setNClob not supported");
	}

	@Override
	public void setNClob(int parameterIndex, Reader reader) throws SQLException {
		throw new BQSQLFeatureNotSupportedException("setNClob not supported");
	}

	@Override
	public void setSQLXML(int parameterIndex, SQLXML xmlObject) throws SQLException {
		throw new BQSQLFeatureNotSupportedException("setSQLXML not supported");
	}

	// Batch operations - not supported

	@Override
	public void addBatch() throws SQLException {
		throw new BQSQLFeatureNotSupportedException("Batch updates not supported");
	}
}

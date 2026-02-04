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
package com.twobearcapital.bigquery.jdbc.base;

import com.twobearcapital.bigquery.jdbc.util.UnsupportedOperations;
import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.*;

/**
 * Mixin interface providing default implementations for all ResultSet update
 * methods. Consolidates ~140 lines of identical exception-throwing stubs per
 * ResultSet implementation.
 *
 * <p>
 * All update methods throw SQLFeatureNotSupportedException since BigQuery
 * ResultSets are read-only.
 */
public interface ReadOnlyResultSetMixin extends ResultSet {

	// Update methods by column index

	default void updateNull(int columnIndex) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateBoolean(int columnIndex, boolean x) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateByte(int columnIndex, byte x) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateShort(int columnIndex, short x) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateInt(int columnIndex, int x) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateLong(int columnIndex, long x) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateFloat(int columnIndex, float x) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateDouble(int columnIndex, double x) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateBigDecimal(int columnIndex, BigDecimal x) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateString(int columnIndex, String x) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateBytes(int columnIndex, byte[] x) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateDate(int columnIndex, Date x) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateTime(int columnIndex, Time x) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateTimestamp(int columnIndex, Timestamp x) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateAsciiStream(int columnIndex, InputStream x, int length) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateBinaryStream(int columnIndex, InputStream x, int length) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateCharacterStream(int columnIndex, Reader x, int length) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateObject(int columnIndex, Object x, int scaleOrLength) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateObject(int columnIndex, Object x) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	// Update methods by column label

	default void updateNull(String columnLabel) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateBoolean(String columnLabel, boolean x) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateByte(String columnLabel, byte x) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateShort(String columnLabel, short x) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateInt(String columnLabel, int x) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateLong(String columnLabel, long x) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateFloat(String columnLabel, float x) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateDouble(String columnLabel, double x) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateBigDecimal(String columnLabel, BigDecimal x) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateString(String columnLabel, String x) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateBytes(String columnLabel, byte[] x) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateDate(String columnLabel, Date x) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateTime(String columnLabel, Time x) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateTimestamp(String columnLabel, Timestamp x) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateAsciiStream(String columnLabel, InputStream x, int length) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateBinaryStream(String columnLabel, InputStream x, int length) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateCharacterStream(String columnLabel, Reader x, int length) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateObject(String columnLabel, Object x, int scaleOrLength) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateObject(String columnLabel, Object x) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	// Row update operations

	default void insertRow() throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateRow() throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void deleteRow() throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void refreshRow() throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void cancelRowUpdates() throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void moveToInsertRow() throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void moveToCurrentRow() throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	// JDBC 4.0 update methods

	default void updateRef(int columnIndex, Ref x) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateRef(String columnLabel, Ref x) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateBlob(int columnIndex, Blob x) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateBlob(String columnLabel, Blob x) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateClob(int columnIndex, Clob x) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateClob(String columnLabel, Clob x) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateArray(int columnIndex, Array x) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateArray(String columnLabel, Array x) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateRowId(int columnIndex, RowId x) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateRowId(String columnLabel, RowId x) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateNString(int columnIndex, String nString) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateNString(String columnLabel, String nString) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateNClob(int columnIndex, NClob nClob) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateNClob(String columnLabel, NClob nClob) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateSQLXML(int columnIndex, SQLXML xmlObject) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateSQLXML(String columnLabel, SQLXML xmlObject) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateNCharacterStream(int columnIndex, Reader x, long length) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateNCharacterStream(String columnLabel, Reader reader, long length) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateAsciiStream(int columnIndex, InputStream x, long length) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateBinaryStream(int columnIndex, InputStream x, long length) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateCharacterStream(int columnIndex, Reader x, long length) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateAsciiStream(String columnLabel, InputStream x, long length) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateBinaryStream(String columnLabel, InputStream x, long length) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateCharacterStream(String columnLabel, Reader reader, long length) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateBlob(int columnIndex, InputStream inputStream, long length) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateBlob(String columnLabel, InputStream inputStream, long length) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateClob(int columnIndex, Reader reader, long length) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateClob(String columnLabel, Reader reader, long length) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateNClob(int columnIndex, Reader reader, long length) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateNClob(String columnLabel, Reader reader, long length) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateNCharacterStream(int columnIndex, Reader x) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateNCharacterStream(String columnLabel, Reader reader) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateAsciiStream(int columnIndex, InputStream x) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateBinaryStream(int columnIndex, InputStream x) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateCharacterStream(int columnIndex, Reader x) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateAsciiStream(String columnLabel, InputStream x) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateBinaryStream(String columnLabel, InputStream x) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateCharacterStream(String columnLabel, Reader reader) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateBlob(int columnIndex, InputStream inputStream) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateBlob(String columnLabel, InputStream inputStream) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateClob(int columnIndex, Reader reader) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateClob(String columnLabel, Reader reader) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateNClob(int columnIndex, Reader reader) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateNClob(String columnLabel, Reader reader) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	// JDBC 4.2 update methods

	default void updateObject(int columnIndex, Object x, SQLType targetSqlType, int scaleOrLength) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateObject(String columnLabel, Object x, SQLType targetSqlType, int scaleOrLength)
			throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateObject(int columnIndex, Object x, SQLType targetSqlType) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}

	default void updateObject(String columnLabel, Object x, SQLType targetSqlType) throws SQLException {
		throw UnsupportedOperations.resultSetUpdates();
	}
}

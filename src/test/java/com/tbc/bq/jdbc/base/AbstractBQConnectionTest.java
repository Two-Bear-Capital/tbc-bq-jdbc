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

import java.sql.*;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link AbstractBQConnection}.
 *
 * @since 1.0.0
 */
class AbstractBQConnectionTest {

	private TestConnection connection;

	@BeforeEach
	void setUp() {
		connection = new TestConnection();
	}

	@Test
	void testPrepareCallThrowsException() {
		// Then: All prepareCall variants should throw SQLFeatureNotSupportedException
		assertThrows(SQLFeatureNotSupportedException.class, () -> connection.prepareCall("CALL proc()"));
		assertThrows(SQLFeatureNotSupportedException.class,
				() -> connection.prepareCall("CALL proc()", ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY));
		assertThrows(SQLFeatureNotSupportedException.class, () -> connection.prepareCall("CALL proc()",
				ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY, ResultSet.CLOSE_CURSORS_AT_COMMIT));
	}

	@Test
	void testSavepointsThrowException() {
		// Then: All savepoint methods should throw SQLFeatureNotSupportedException
		assertThrows(SQLFeatureNotSupportedException.class, () -> connection.setSavepoint());
		assertThrows(SQLFeatureNotSupportedException.class, () -> connection.setSavepoint("sp1"));
		assertThrows(SQLFeatureNotSupportedException.class, () -> connection.rollback((Savepoint) null));
		assertThrows(SQLFeatureNotSupportedException.class, () -> connection.releaseSavepoint(null));
	}

	@Test
	void testSetTypeMapThrowsException() {
		// Then: setTypeMap should throw SQLFeatureNotSupportedException
		assertThrows(SQLFeatureNotSupportedException.class, () -> connection.setTypeMap(Map.of()));
	}

	@Test
	void testPrepareStatementWithGeneratedKeysThrowsException() {
		// Then: Generated keys variants should throw SQLFeatureNotSupportedException
		assertThrows(SQLFeatureNotSupportedException.class,
				() -> connection.prepareStatement("SELECT 1", Statement.RETURN_GENERATED_KEYS));
		assertThrows(SQLFeatureNotSupportedException.class,
				() -> connection.prepareStatement("SELECT 1", new int[]{1}));
		assertThrows(SQLFeatureNotSupportedException.class,
				() -> connection.prepareStatement("SELECT 1", new String[]{"id"}));
	}

	@Test
	void testPrepareStatementWithNoGeneratedKeysDelegatesToPrepareStatement() throws SQLException {
		// When: Calling prepareStatement with NO_GENERATED_KEYS
		PreparedStatement stmt = connection.prepareStatement("SELECT 1", Statement.NO_GENERATED_KEYS);

		// Then: Should delegate to regular prepareStatement (our test implementation
		// returns null)
		assertNull(stmt, "Should delegate to prepareStatement(sql)");
	}

	@Test
	void testLobCreationThrowsException() {
		// Then: LOB creation methods should throw SQLFeatureNotSupportedException
		assertThrows(SQLFeatureNotSupportedException.class, () -> connection.createClob());
		assertThrows(SQLFeatureNotSupportedException.class, () -> connection.createBlob());
		assertThrows(SQLFeatureNotSupportedException.class, () -> connection.createNClob());
		assertThrows(SQLFeatureNotSupportedException.class, () -> connection.createSQLXML());
		assertThrows(SQLFeatureNotSupportedException.class,
				() -> connection.createArrayOf("INTEGER", new Object[]{1, 2, 3}));
		assertThrows(SQLFeatureNotSupportedException.class,
				() -> connection.createStruct("PERSON", new Object[]{"John", 30}));
	}

	@Test
	void testShardingThrowsException() {
		// Then: Sharding methods should throw SQLFeatureNotSupportedException
		assertThrows(SQLFeatureNotSupportedException.class, () -> connection.setShardingKey(null));
		assertThrows(SQLFeatureNotSupportedException.class, () -> connection.setShardingKey(null, null));
		assertThrows(SQLFeatureNotSupportedException.class, () -> connection.setShardingKeyIfValid(null, 10));
		assertThrows(SQLFeatureNotSupportedException.class, () -> connection.setShardingKeyIfValid(null, null, 10));
	}

	@Test
	void testClosedConnectionThrowsException() throws SQLException {
		// Given: A closed connection
		connection.close();
		assertTrue(connection.isClosed(), "Connection should be closed");

		// Then: Operations should check closed state
		assertThrows(SQLException.class, () -> connection.prepareStatement("SELECT 1", Statement.NO_GENERATED_KEYS));
	}

	/**
	 * Test implementation of AbstractBQConnection for testing purposes.
	 */
	private static class TestConnection extends AbstractBQConnection {

		@Override
		protected void doClose() throws SQLException {
			// Nothing to do for test
		}

		@Override
		protected String getClosedErrorMessage() {
			return "Test connection is closed";
		}

		// Minimal implementations for required Connection methods

		@Override
		public Statement createStatement() throws SQLException {
			return null;
		}

		@Override
		public PreparedStatement prepareStatement(String sql) throws SQLException {
			return null;
		}

		@Override
		public String nativeSQL(String sql) throws SQLException {
			return sql;
		}

		@Override
		public void setAutoCommit(boolean autoCommit) throws SQLException {
		}

		@Override
		public boolean getAutoCommit() throws SQLException {
			return true;
		}

		@Override
		public void commit() throws SQLException {
		}

		@Override
		public void rollback() throws SQLException {
		}

		@Override
		public DatabaseMetaData getMetaData() throws SQLException {
			return null;
		}

		@Override
		public void setReadOnly(boolean readOnly) throws SQLException {
		}

		@Override
		public boolean isReadOnly() throws SQLException {
			return false;
		}

		@Override
		public void setCatalog(String catalog) throws SQLException {
		}

		@Override
		public String getCatalog() throws SQLException {
			return null;
		}

		@Override
		public void setTransactionIsolation(int level) throws SQLException {
		}

		@Override
		public int getTransactionIsolation() throws SQLException {
			return Connection.TRANSACTION_NONE;
		}

		@Override
		public SQLWarning getWarnings() throws SQLException {
			return null;
		}

		@Override
		public void clearWarnings() throws SQLException {
		}

		@Override
		public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
			return null;
		}

		@Override
		public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency)
				throws SQLException {
			return null;
		}

		@Override
		public Map<String, Class<?>> getTypeMap() throws SQLException {
			return Map.of();
		}

		@Override
		public void setHoldability(int holdability) throws SQLException {
		}

		@Override
		public int getHoldability() throws SQLException {
			return ResultSet.CLOSE_CURSORS_AT_COMMIT;
		}

		@Override
		public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability)
				throws SQLException {
			return null;
		}

		@Override
		public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency,
				int resultSetHoldability) throws SQLException {
			return null;
		}

		@Override
		public boolean isValid(int timeout) throws SQLException {
			return !isClosed();
		}

		@Override
		public void setClientInfo(String name, String value) throws SQLClientInfoException {
		}

		@Override
		public void setClientInfo(Properties properties) throws SQLClientInfoException {
		}

		@Override
		public String getClientInfo(String name) throws SQLException {
			return null;
		}

		@Override
		public Properties getClientInfo() throws SQLException {
			return new Properties();
		}

		@Override
		public void setSchema(String schema) throws SQLException {
		}

		@Override
		public String getSchema() throws SQLException {
			return null;
		}

		@Override
		public void abort(Executor executor) throws SQLException {
		}

		@Override
		public void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException {
		}

		@Override
		public int getNetworkTimeout() throws SQLException {
			return 0;
		}

		@Override
		public <T> T unwrap(Class<T> iface) throws SQLException {
			return null;
		}

		@Override
		public boolean isWrapperFor(Class<?> iface) throws SQLException {
			return false;
		}
	}
}

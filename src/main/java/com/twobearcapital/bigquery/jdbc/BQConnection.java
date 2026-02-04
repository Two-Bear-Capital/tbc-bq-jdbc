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

import com.google.auth.Credentials;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryOptions;
import com.twobearcapital.bigquery.jdbc.base.AbstractBQConnection;
import com.twobearcapital.bigquery.jdbc.config.ConnectionProperties;
import com.twobearcapital.bigquery.jdbc.config.SessionManager;
import com.twobearcapital.bigquery.jdbc.exception.BQSQLException;
import com.twobearcapital.bigquery.jdbc.exception.BQSQLFeatureNotSupportedException;
import com.twobearcapital.bigquery.jdbc.metadata.BQDatabaseMetaData;
import com.twobearcapital.bigquery.jdbc.util.ErrorMessages;
import java.io.IOException;
import java.sql.*;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JDBC Connection implementation for BigQuery.
 *
 * @since 1.0.0
 */
public final class BQConnection extends AbstractBQConnection {

	private static final Logger logger = LoggerFactory.getLogger(BQConnection.class);

	private final BigQuery bigquery;
	private final ConnectionProperties properties;
	private final Set<BQStatement> runningStatements = ConcurrentHashMap.newKeySet();
	private final SessionManager sessionManager;
	private boolean autoCommit = true;
	private boolean readOnly = false;
	private int networkTimeout = 0;

	/**
	 * Creates a new BigQuery connection.
	 *
	 * @param properties
	 *            the connection properties
	 * @throws SQLException
	 *             if the connection cannot be created
	 */
	public BQConnection(ConnectionProperties properties) throws SQLException {
		this.properties = properties;
		try {
			Credentials credentials = properties.authType().toCredentials();
			BigQueryOptions.Builder builder = BigQueryOptions.newBuilder().setProjectId(properties.projectId())
					.setCredentials(credentials);

			if (properties.location() != null) {
				builder.setLocation(properties.location());
			}

			// Set custom host for emulator support
			if (properties.host() != null) {
				String endpoint = properties.host();
				if (properties.port() != null) {
					endpoint = "http://" + endpoint + ":" + properties.port();
				}
				builder.setHost(endpoint);
				logger.info("Using custom BigQuery endpoint: {}", endpoint);
			}

			this.bigquery = builder.build().getService();
			logger.info("Connected to BigQuery project: {}", properties.projectId());

			// Initialize session manager
			this.sessionManager = new SessionManager(bigquery, properties);

			// Initialize session if enabled
			if (properties.enableSessions()) {
				sessionManager.initializeSession();
				logger.info("BigQuery session mode enabled");
			}

		} catch (IOException e) {
			throw new BQSQLException("Failed to create BigQuery connection", BQSQLException.SQLSTATE_CONNECTION_ERROR,
					e);
		}
	}

	/**
	 * Gets the BigQuery client.
	 *
	 * @return the BigQuery client
	 */
	public BigQuery getBigQuery() {
		return bigquery;
	}

	/**
	 * Gets the connection properties.
	 *
	 * @return the connection properties
	 */
	public ConnectionProperties getProperties() {
		return properties;
	}

	/**
	 * Gets the session manager.
	 *
	 * @return the session manager
	 */
	public SessionManager getSessionManager() {
		return sessionManager;
	}

	/**
	 * Registers a running statement.
	 *
	 * @param statement
	 *            the statement to register
	 */
	public void registerStatement(BQStatement statement) {
		runningStatements.add(statement);
	}

	/**
	 * Unregisters a statement.
	 *
	 * @param statement
	 *            the statement to unregister
	 */
	public void unregisterStatement(BQStatement statement) {
		runningStatements.remove(statement);
	}

	protected String getClosedErrorMessage() {
		return ErrorMessages.CONNECTION_CLOSED;
	}

	public Statement createStatement() throws SQLException {
		checkClosed();
		return new BQStatement(this);
	}

	public PreparedStatement prepareStatement(String sql) throws SQLException {
		checkClosed();
		return new BQPreparedStatement(this, sql);
	}

	public String nativeSQL(String sql) throws SQLException {
		checkClosed();
		return sql;
	}

	public void setAutoCommit(boolean autoCommit) throws SQLException {
		checkClosed();

		// Thread-safe and atomic transaction state change
		synchronized (this) {
			// If already in the desired state, nothing to do
			if (this.autoCommit == autoCommit) {
				return;
			}

			// Sessions required for transaction support
			if (!autoCommit && !properties.enableSessions()) {
				throw new BQSQLFeatureNotSupportedException(
						"BigQuery does not support transactions outside of sessions. "
								+ "Enable sessions with: enableSessions=true");
			}

			// Change state atomically - only update flag if operations succeed
			if (autoCommit) {
				// Switching to auto-commit: commit pending transaction first
				if (properties.enableSessions()) {
					sessionManager.commit();
				}
				this.autoCommit = true;
			} else {
				// Switching to manual commit: begin transaction
				sessionManager.beginTransaction();
				this.autoCommit = false;
			}

			logger.debug("Auto-commit set to: {}", autoCommit);
		}
	}

	public boolean getAutoCommit() throws SQLException {
		checkClosed();
		return autoCommit;
	}

	public void commit() throws SQLException {
		checkClosed();

		// Allow commit in session mode
		if (properties.enableSessions()) {
			sessionManager.commit();
			return;
		}

		throw new BQSQLFeatureNotSupportedException("BigQuery does not support transactions outside of sessions. "
				+ "Enable sessions with: enableSessions=true");
	}

	public void rollback() throws SQLException {
		checkClosed();

		// Allow rollback in session mode
		if (properties.enableSessions()) {
			sessionManager.rollback();
			return;
		}

		throw new BQSQLFeatureNotSupportedException("BigQuery does not support transactions outside of sessions. "
				+ "Enable sessions with: enableSessions=true");
	}

	protected void doClose() throws SQLException {
		logger.debug("Closing BigQuery connection");

		// Cancel all running statements
		for (BQStatement stmt : runningStatements) {
			try {
				stmt.cancel();
			} catch (SQLException e) {
				logger.warn("Failed to cancel statement during connection close", e);
			}
		}

		// Clear statement references
		runningStatements.clear();

		// Close session if active
		if (sessionManager != null) {
			sessionManager.close();
		}

		logger.info("BigQuery connection closed");
	}

	public boolean isClosed() {
		return closed;
	}

	public DatabaseMetaData getMetaData() throws SQLException {
		checkClosed();
		return new BQDatabaseMetaData(this);
	}

	public void setReadOnly(boolean readOnly) throws SQLException {
		checkClosed();
		this.readOnly = readOnly;
	}

	public boolean isReadOnly() throws SQLException {
		checkClosed();
		return readOnly;
	}

	public void setCatalog(String catalog) throws SQLException {
		checkClosed();
		// BigQuery uses project as catalog, but we don't allow changing it
		logger.debug("setCatalog called with: {} (ignored)", catalog);
	}

	public String getCatalog() throws SQLException {
		checkClosed();
		return properties.projectId();
	}

	public void setTransactionIsolation(int level) throws SQLException {
		checkClosed();
		if (level != Connection.TRANSACTION_NONE) {
			throw new BQSQLFeatureNotSupportedException("BigQuery does not support transaction isolation levels");
		}
	}

	public int getTransactionIsolation() throws SQLException {
		checkClosed();
		return Connection.TRANSACTION_NONE;
	}

	public SQLWarning getWarnings() throws SQLException {
		checkClosed();
		return null;
	}

	public void clearWarnings() throws SQLException {
		checkClosed();
	}

	public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
		checkClosed();
		if (resultSetType != ResultSet.TYPE_FORWARD_ONLY) {
			throw new BQSQLFeatureNotSupportedException("Only TYPE_FORWARD_ONLY result sets are supported");
		}
		if (resultSetConcurrency != ResultSet.CONCUR_READ_ONLY) {
			throw new BQSQLFeatureNotSupportedException("Only CONCUR_READ_ONLY result sets are supported");
		}
		return createStatement();
	}

	public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency)
			throws SQLException {
		checkClosed();
		if (resultSetType != ResultSet.TYPE_FORWARD_ONLY) {
			throw new BQSQLFeatureNotSupportedException("Only TYPE_FORWARD_ONLY result sets are supported");
		}
		if (resultSetConcurrency != ResultSet.CONCUR_READ_ONLY) {
			throw new BQSQLFeatureNotSupportedException("Only CONCUR_READ_ONLY result sets are supported");
		}
		return prepareStatement(sql);
	}

	public Map<String, Class<?>> getTypeMap() throws SQLException {
		checkClosed();
		return Map.of();
	}

	public void setHoldability(int holdability) throws SQLException {
		checkClosed();
		if (holdability != ResultSet.CLOSE_CURSORS_AT_COMMIT) {
			throw new BQSQLFeatureNotSupportedException("Only CLOSE_CURSORS_AT_COMMIT holdability is supported");
		}
	}

	public int getHoldability() throws SQLException {
		checkClosed();
		return ResultSet.CLOSE_CURSORS_AT_COMMIT;
	}

	public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability)
			throws SQLException {
		checkClosed();
		if (resultSetHoldability != ResultSet.CLOSE_CURSORS_AT_COMMIT) {
			throw new BQSQLFeatureNotSupportedException("Only CLOSE_CURSORS_AT_COMMIT holdability is supported");
		}
		return createStatement(resultSetType, resultSetConcurrency);
	}

	public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency,
			int resultSetHoldability) throws SQLException {
		checkClosed();
		if (resultSetHoldability != ResultSet.CLOSE_CURSORS_AT_COMMIT) {
			throw new BQSQLFeatureNotSupportedException("Only CLOSE_CURSORS_AT_COMMIT holdability is supported");
		}
		return prepareStatement(sql, resultSetType, resultSetConcurrency);
	}

	public boolean isValid(int timeout) throws SQLException {
		if (timeout < 0) {
			throw new BQSQLException(ErrorMessages.NEGATIVE_TIMEOUT, BQSQLException.SQLSTATE_INVALID_PARAMETER_VALUE);
		}

		if (closed) {
			return false;
		}

		// Validate connection by executing a simple query
		try (Statement stmt = createStatement()) {
			if (timeout > 0) {
				stmt.setQueryTimeout(timeout);
			}
			try (ResultSet rs = stmt.executeQuery("SELECT 1")) {
				return rs.next();
			}
		} catch (SQLException e) {
			logger.debug("Connection validation failed", e);
			return false;
		}
	}

	public void setClientInfo(String name, String value) throws SQLClientInfoException {
		// Silently ignore
	}

	public void setClientInfo(Properties properties) throws SQLClientInfoException {
		// Silently ignore
	}

	public String getClientInfo(String name) throws SQLException {
		checkClosed();
		return null;
	}

	public Properties getClientInfo() throws SQLException {
		checkClosed();
		return new Properties();
	}

	public void setSchema(String schema) throws SQLException {
		checkClosed();
		// BigQuery uses dataset as schema, but we don't allow changing it after
		// connection
		logger.debug("setSchema called with: {} (ignored)", schema);
	}

	public String getSchema() throws SQLException {
		checkClosed();
		return properties.datasetId();
	}

	public void abort(Executor executor) throws SQLException {
		if (closed) {
			return;
		}
		close();
	}

	public void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException {
		checkClosed();
		this.networkTimeout = milliseconds;
	}

	public int getNetworkTimeout() throws SQLException {
		checkClosed();
		return networkTimeout;
	}

	// JDBC 4.3 methods

	public void beginRequest() throws SQLException {
		checkClosed();
		logger.debug("beginRequest called");
	}

	public void endRequest() throws SQLException {
		checkClosed();
		logger.debug("endRequest called");
	}

}

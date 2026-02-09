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
package vc.tbc.bq.jdbc;

import com.google.auth.Credentials;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vc.tbc.bq.jdbc.base.AbstractBQConnection;
import vc.tbc.bq.jdbc.config.ConnectionProperties;
import vc.tbc.bq.jdbc.config.SessionManager;
import vc.tbc.bq.jdbc.exception.BQSQLException;
import vc.tbc.bq.jdbc.exception.BQSQLFeatureNotSupportedException;
import vc.tbc.bq.jdbc.metadata.BQDatabaseMetaData;
import vc.tbc.bq.jdbc.util.ErrorMessages;
import vc.tbc.bq.jdbc.util.UnsupportedOperations;

import java.io.IOException;
import java.sql.*;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * JDBC Connection implementation for BigQuery.
 *
 * <p>
 * This connection provides access to BigQuery through standard JDBC interfaces.
 * It supports:
 * <ul>
 * <li>Statement and PreparedStatement execution
 * <li>Transaction support (requires session mode with
 * {@code enableSessions=true})
 * <li>Metadata queries through {@link DatabaseMetaData}
 * <li>Multiple authentication methods (ADC, service account, OAuth, etc.)
 * </ul>
 *
 * <p>
 * <b>Thread Safety:</b> This connection implementation is thread-safe. Multiple
 * statements can be created and executed concurrently from the same connection.
 * However, transaction operations ({@link #commit()}, {@link #rollback()})
 * affect all statements on the connection and should be coordinated
 * appropriately.
 *
 * <p>
 * <b>Session Mode:</b> BigQuery sessions are required for:
 * <ul>
 * <li>Transaction support (BEGIN, COMMIT, ROLLBACK)
 * <li>Temporary tables
 * <li>Multi-statement SQL execution
 * </ul>
 * Enable sessions with connection property: {@code enableSessions=true}
 *
 * @since 1.0.0
 */
public final class BQConnection extends AbstractBQConnection {

	private static final Logger logger = LoggerFactory.getLogger(BQConnection.class);

	private final BigQuery bigquery;
	private final ConnectionProperties properties;
	private final Set<BQStatement> runningStatements = ConcurrentHashMap.newKeySet();
	private final SessionManager sessionManager;
	private BQDatabaseMetaData metadata;
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
			this.sessionManager = new SessionManager(bigquery);

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

	@Override
	protected String getClosedErrorMessage() {
		return ErrorMessages.CONNECTION_CLOSED;
	}

	/**
	 * Creates a new Statement for executing SQL queries.
	 *
	 * <p>
	 * The returned statement can be used multiple times to execute different SQL
	 * queries. Each statement maintains its own result set and query execution
	 * state.
	 *
	 * <p>
	 * <b>Concurrency:</b> Multiple statements can be created and executed
	 * concurrently on the same connection. Each statement operates independently.
	 *
	 * <p>
	 * <b>Lifecycle:</b> The statement should be closed when no longer needed to
	 * free resources. Closing the connection automatically closes all associated
	 * statements.
	 *
	 * @return a new Statement object
	 * @throws SQLException
	 *             if the connection is closed
	 */
	@Override
	public Statement createStatement() throws SQLException {
		checkClosed();
		return new BQStatement(this);
	}

	/**
	 * Creates a PreparedStatement for executing parameterized SQL queries.
	 *
	 * <p>
	 * Prepared statements use positional parameter placeholders ({@code ?}) in the
	 * SQL. Parameters are bound using setter methods like
	 * {@link PreparedStatement#setString(int, String)} before execution.
	 *
	 * <p>
	 * <b>Example:</b>
	 *
	 * <pre>{@code
	 * PreparedStatement ps = conn.prepareStatement("SELECT * FROM dataset.table WHERE id = ? AND name = ?");
	 * ps.setInt(1, 42);
	 * ps.setString(2, "example");
	 * ResultSet rs = ps.executeQuery();
	 * }</pre>
	 *
	 * <p>
	 * <b>Performance:</b> BigQuery does not cache query plans like traditional
	 * databases, so prepared statements primarily provide convenience and SQL
	 * injection protection rather than performance benefits.
	 *
	 * @param sql
	 *            SQL query with positional parameter placeholders ({@code ?})
	 * @return a new PreparedStatement object
	 * @throws SQLException
	 *             if the connection is closed
	 */
	@Override
	public PreparedStatement prepareStatement(String sql) throws SQLException {
		checkClosed();
		return new BQPreparedStatement(this, sql);
	}

	@Override
	public String nativeSQL(String sql) throws SQLException {
		checkClosed();
		return sql;
	}

	@Override
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

	@Override
	public boolean getAutoCommit() throws SQLException {
		checkClosed();
		return autoCommit;
	}

	@Override
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

	@Override
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

	@Override
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

		// Log metadata cache statistics (cache persists across connections)
		if (metadata != null) {
			String cacheStats = metadata.getCacheStats();
			if (cacheStats != null) {
				logger.info("Metadata cache statistics: {}", cacheStats);
			}
			// Note: Cache is NOT cleared on connection close - it persists across
			// connections and expires based on TTL. This improves performance for
			// applications (like IntelliJ) that frequently reopen connections.
		}

		logger.info("BigQuery connection closed");
	}

	@Override
	public boolean isClosed() {
		return closed;
	}

	@Override
	public DatabaseMetaData getMetaData() throws SQLException {
		checkClosed();
		if (metadata == null) {
			metadata = new BQDatabaseMetaData(this);
		}
		return metadata;
	}

	@Override
	public void setReadOnly(boolean readOnly) throws SQLException {
		checkClosed();
		this.readOnly = readOnly;
	}

	@Override
	public boolean isReadOnly() throws SQLException {
		checkClosed();
		return readOnly;
	}

	@Override
	public void setCatalog(String catalog) throws SQLException {
		checkClosed();
		// BigQuery uses project as catalog, but we don't allow changing it
		logger.debug("setCatalog called with: {} (ignored)", catalog);
	}

	@Override
	public String getCatalog() throws SQLException {
		checkClosed();
		return properties.projectId();
	}

	@Override
	public void setTransactionIsolation(int level) throws SQLException {
		checkClosed();
		if (level != Connection.TRANSACTION_NONE) {
			throw new BQSQLFeatureNotSupportedException("BigQuery does not support transaction isolation levels");
		}
	}

	@Override
	public int getTransactionIsolation() throws SQLException {
		checkClosed();
		return Connection.TRANSACTION_NONE;
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

	@Override
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

	@Override
	public Map<String, Class<?>> getTypeMap() throws SQLException {
		checkClosed();
		return Map.of();
	}

	@Override
	public void setHoldability(int holdability) throws SQLException {
		checkClosed();
		if (holdability != ResultSet.CLOSE_CURSORS_AT_COMMIT) {
			throw UnsupportedOperations.holdability();
		}
	}

	@Override
	public int getHoldability() throws SQLException {
		checkClosed();
		return ResultSet.CLOSE_CURSORS_AT_COMMIT;
	}

	@Override
	public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability)
			throws SQLException {
		checkClosed();
		if (resultSetHoldability != ResultSet.CLOSE_CURSORS_AT_COMMIT) {
			throw new BQSQLFeatureNotSupportedException("Only CLOSE_CURSORS_AT_COMMIT holdability is supported");
		}
		return createStatement(resultSetType, resultSetConcurrency);
	}

	@Override
	public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency,
			int resultSetHoldability) throws SQLException {
		checkClosed();
		if (resultSetHoldability != ResultSet.CLOSE_CURSORS_AT_COMMIT) {
			throw new BQSQLFeatureNotSupportedException("Only CLOSE_CURSORS_AT_COMMIT holdability is supported");
		}
		return prepareStatement(sql, resultSetType, resultSetConcurrency);
	}

	/**
	 * Checks if the connection is valid by executing a simple query.
	 *
	 * <p>
	 * This method validates the connection by executing {@code SELECT 1} against
	 * BigQuery. If the query succeeds within the timeout, the connection is
	 * considered valid.
	 *
	 * <p>
	 * <b>Validation Approach:</b>
	 * <ul>
	 * <li>Returns {@code false} immediately if the connection is closed
	 * <li>Executes a lightweight query to verify BigQuery connectivity
	 * <li>Returns {@code false} if the query fails or times out
	 * </ul>
	 *
	 * <p>
	 * <b>Usage in Connection Pools:</b> Connection pool implementations (e.g.,
	 * HikariCP) use this method to validate connections before handing them to
	 * applications. Set a reasonable timeout (e.g., 5-10 seconds) to avoid blocking
	 * pool operations.
	 *
	 * @param timeout
	 *            maximum time in seconds to wait for validation (0 = no timeout)
	 * @return {@code true} if the connection is valid, {@code false} otherwise
	 * @throws SQLException
	 *             if timeout is negative
	 */
	@Override
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

	@Override
	public void setClientInfo(String name, String value) throws SQLClientInfoException {
		// Silently ignore
	}

	@Override
	public void setClientInfo(Properties properties) throws SQLClientInfoException {
		// Silently ignore
	}

	@Override
	public String getClientInfo(String name) throws SQLException {
		checkClosed();
		return null;
	}

	@Override
	public Properties getClientInfo() throws SQLException {
		checkClosed();
		return new Properties();
	}

	@Override
	public void setSchema(String schema) throws SQLException {
		checkClosed();
		// BigQuery uses dataset as schema, but we don't allow changing it after
		// connection
		logger.debug("setSchema called with: {} (ignored)", schema);
	}

	@Override
	public String getSchema() throws SQLException {
		checkClosed();
		return properties.datasetId();
	}

	@Override
	public void abort(Executor executor) throws SQLException {
		if (closed) {
			return;
		}
		close();
	}

	@Override
	public void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException {
		checkClosed();
		this.networkTimeout = milliseconds;
	}

	@Override
	public int getNetworkTimeout() throws SQLException {
		checkClosed();
		return networkTimeout;
	}

	// JDBC 4.3 methods

	@Override
	public void beginRequest() throws SQLException {
		checkClosed();
		logger.debug("beginRequest called");
	}

	@Override
	public void endRequest() throws SQLException {
		checkClosed();
		logger.debug("endRequest called");
	}

}

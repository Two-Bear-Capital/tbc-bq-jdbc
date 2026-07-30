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
import com.google.cloud.http.HttpTransportOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vc.tbc.bq.jdbc.auth.AccessTokenAuth;
import vc.tbc.bq.jdbc.auth.AuthType;
import vc.tbc.bq.jdbc.auth.CredentialsCache;
import vc.tbc.bq.jdbc.base.AbstractBQConnection;
import vc.tbc.bq.jdbc.config.ConnectionProperties;
import vc.tbc.bq.jdbc.config.MetadataCache;
import vc.tbc.bq.jdbc.config.SessionManager;
import vc.tbc.bq.jdbc.exception.BQSQLException;
import vc.tbc.bq.jdbc.util.BigQueryIdentifiers;
import vc.tbc.bq.jdbc.exception.BQSQLFeatureNotSupportedException;
import vc.tbc.bq.jdbc.metadata.BQDatabaseMetaData;
import vc.tbc.bq.jdbc.transport.DriverTransports;
import vc.tbc.bq.jdbc.util.ErrorMessages;
import vc.tbc.bq.jdbc.util.StructTypeNames;
import vc.tbc.bq.jdbc.util.UnsupportedOperations;

import java.io.IOException;
import java.sql.*;
import java.util.Locale;
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
 * <li>Transaction support (session-backed; the session starts automatically on
 * {@code setAutoCommit(false)})
 * <li>Metadata queries through {@link DatabaseMetaData}
 * <li>Multiple authentication methods (ADC, service account, OAuth, etc.)
 * </ul>
 *
 * <p>
 * <b>Thread Safety:</b> This connection's own state is safe to touch from
 * several threads. Statements, however, are not free to run concurrently once a
 * session exists: BigQuery rejects concurrent queries within a session, and
 * transaction control ({@link #setAutoCommit(boolean)}, {@link #commit()},
 * {@link #rollback()}) serializes on this connection while the corresponding
 * BigQuery job runs. Give each thread its own connection — as a connection pool
 * does — rather than sharing one.
 *
 * <p>
 * <b>Session Mode:</b> BigQuery sessions are required for:
 * <ul>
 * <li>Transaction support (BEGIN, COMMIT, ROLLBACK)
 * <li>Temporary tables
 * <li>Multi-statement SQL execution
 * </ul>
 * Set {@code enableSessions=true} to create the session when the connection
 * opens. Connections opened without it still start a session on demand the
 * first time {@code setAutoCommit(false)} is called, so generic JDBC tooling
 * (connection pools, ORMs, data loaders) works without BigQuery-specific
 * configuration.
 *
 * @since 1.0.0
 */
public final class BQConnection extends AbstractBQConnection {

	private static final Logger logger = LoggerFactory.getLogger(BQConnection.class);

	private final BigQuery bigquery;
	private final ConnectionProperties properties;

	/**
	 * The project a null {@code catalog} argument resolves to, moved by
	 * {@link #setCatalog(String)}.
	 *
	 * <p>
	 * volatile because a pool may hand the connection to another thread between
	 * uses; the field is only ever assigned whole.
	 */
	private volatile String currentCatalog;
	private final Set<BQStatement> runningStatements = ConcurrentHashMap.newKeySet();
	private final SessionManager sessionManager;
	/**
	 * Reused across calls so metadata caching actually pays off — see
	 * {@link #getMetaData()}. Volatile because callers may reach it from any
	 * thread.
	 */
	private volatile BQDatabaseMetaData metadata;

	/**
	 * Transaction state. Written under this connection's monitor and read without
	 * it (JDBC callers ask for auto-commit state freely), so both are volatile:
	 * otherwise a thread that did not perform the write can observe a stale value.
	 */
	private volatile boolean autoCommit = true;

	private volatile boolean transactionActive = false;
	private volatile int transactionIsolation = Connection.TRANSACTION_REPEATABLE_READ;
	private volatile boolean readOnly = false;
	private volatile int networkTimeout = 0;

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
		this.currentCatalog = properties.projectId();
		rejectExpiredAccessToken(properties.authType());
		try {
			// Shared across connections authenticating the same way: building these
			// means an ADC probe plus token fetch, or reading and parsing a key file
			Credentials credentials = CredentialsCache.forAuthType(properties.authType(), properties.transport());
			this.bigquery = buildOptions(properties, credentials).build().getService();
			logger.debug("Connected to BigQuery project: {}", properties.projectId());

			// Initialize session manager
			this.sessionManager = new SessionManager(bigquery);

			// Initialize session if enabled
			if (properties.enableSessions()) {
				sessionManager.initializeSession();
				logger.debug("BigQuery session mode enabled");
			}

		} catch (IOException e) {
			throw new BQSQLException("Failed to create BigQuery connection", BQSQLException.SQLSTATE_CONNECTION_ERROR,
					e);
		}
	}

	/**
	 * Refuses to open a connection on an access token that has already expired.
	 *
	 * <p>
	 * Checked here rather than left to BigQuery because the answers differ in kind.
	 * BigQuery would return a 401 on the first statement, which classifies as
	 * {@code 28000} but arrives a round trip later, after the connection appeared
	 * to open, and says nothing about which of the credential's properties is at
	 * fault. This says the token expired, and when.
	 *
	 * <p>
	 * Only possible when the caller supplied {@code accessTokenExpiry}; without it
	 * the driver has nothing to test and BigQuery remains the judge.
	 *
	 * @param authType
	 *            the connection's authentication
	 * @throws SQLException
	 *             with SQLState {@code 28000} when the token is known to be expired
	 */
	private static void rejectExpiredAccessToken(AuthType authType) throws SQLException {
		if (authType instanceof AccessTokenAuth accessToken && accessToken.isExpired()) {
			throw new BQSQLException(
					"The access token expired at " + accessToken.expiry()
							+ ". A pre-generated token cannot be refreshed by the driver; supply a new one.",
					BQSQLException.SQLSTATE_AUTH_FAILED);
		}
	}

	/**
	 * Builds the BigQuery client options for a connection.
	 *
	 * <p>
	 * Extracted from the constructor so the mapping from connection properties to
	 * client settings can be asserted without opening a connection — a unit test
	 * must not depend on ambient credentials being present.
	 *
	 * @param properties
	 *            the parsed connection properties
	 * @param credentials
	 *            credentials for the connection
	 * @return the configured options builder
	 * @throws IOException
	 *             if a configured truststore cannot be read or parsed
	 */
	static BigQueryOptions.Builder buildOptions(ConnectionProperties properties, Credentials credentials)
			throws IOException {
		BigQueryOptions.Builder builder = BigQueryOptions.newBuilder().setProjectId(properties.projectId())
				.setCredentials(credentials);

		if (properties.location() != null) {
			builder.setLocation(properties.location());
		}

		// retryCount is the total number of attempts, not retries after the first:
		// that is what RetrySettings#setMaxAttempts means, and a driver property
		// called "retryCount" set to 0 must not mean "never try at all". The rest of
		// the client's backoff policy is kept by building on the defaults.
		if (properties.retryCount() != null) {
			builder.setRetrySettings(BigQueryOptions.getDefaultRetrySettings().toBuilder()
					.setMaxAttempts(Math.max(1, properties.retryCount())).build());
		}

		// One HttpTransportOptions, not one per setting: the connect timeout and the
		// proxy are two fields of the same object, so setting them separately would
		// mean the second call discarding the first.
		//
		// The connect timeout applies to establishing the HTTP connection only.
		// Query duration is governed by the `timeout` property, enforced by the
		// driver itself, so the read timeout is deliberately left alone — capping it
		// here would sever long-running queries that are behaving exactly as
		// configured.
		HttpTransportOptions.Builder transport = BigQueryOptions.getDefaultHttpTransportOptions().toBuilder();
		boolean customTransport = false;
		if (properties.connectionTimeout() != null) {
			transport.setConnectTimeout(Math.toIntExact(properties.connectionTimeout() * 1000L));
			customTransport = true;
		}
		// The same factory the connection's credentials were built on — see
		// DriverTransports. Left unset when nothing is configured so the default
		// path keeps the client library's own transport rather than an
		// equivalent-looking copy of it.
		if (!properties.transport().isDefault()) {
			transport.setHttpTransportFactory(DriverTransports.forTransport(properties.transport()));
			customTransport = true;
			if (properties.transport().proxy() != null) {
				logger.info("Routing BigQuery traffic through proxy {}", properties.transport().proxy());
			}
			if (properties.transport().tls() != null) {
				logger.info("Verifying TLS against the truststore at {}", properties.transport().tls());
			}
		}
		if (customTransport) {
			builder.setTransportOptions(transport.build());
		}

		// Point the client at a non-default BigQuery endpoint.
		//
		// A host that already carries a scheme is used verbatim. Otherwise the scheme
		// defaults to https: hard-coding http:// whenever a port was present sent
		// credentials over an unencrypted connection. Write an explicit "http://"
		// host to opt into plaintext.
		if (properties.host() != null) {
			String endpoint = properties.host();
			boolean explicitScheme = endpoint.startsWith("http://") || endpoint.startsWith("https://");
			if (!explicitScheme) {
				endpoint = "https://" + endpoint;
			}
			if (properties.port() != null) {
				endpoint = endpoint + ":" + properties.port();
			}
			builder.setHost(endpoint);
			if (endpoint.startsWith("http://")) {
				logger.warn("Using an unencrypted BigQuery endpoint: {}", endpoint);
			} else {
				logger.info("Using custom BigQuery endpoint: {}", endpoint);
			}
		}
		return builder;
	}

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
	 * Returns the shared {@link MetadataCache} for this connection's project, or
	 * {@code null} if metadata caching is disabled.
	 *
	 * <p>
	 * The cache is shared across all connections to the same project so that
	 * results obtained on one connection are immediately available to the next —
	 * critical for IntelliJ IDEA, which opens a fresh connection for each
	 * introspection pass.
	 *
	 * @return the shared cache, or {@code null} when caching is disabled
	 */
	public MetadataCache getMetadataCache() {
		if (!properties.metadataCacheEnabled()) {
			return null;
		}
		String cacheKey = properties.projectId() + ":" + properties.metadataCacheTtl();
		return BQDatabaseMetaData.getOrCreateSharedCache(cacheKey,
				java.time.Duration.ofSeconds(properties.metadataCacheTtl()), properties.projectId());
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

	/**
	 * Sets the auto-commit mode for this connection.
	 *
	 * <p>
	 * BigQuery only supports transactions inside a session. When auto-commit is
	 * disabled on a connection that has no session yet, the driver starts one on
	 * demand — the caller does not need to have set {@code enableSessions=true} on
	 * the connection URL. Setting {@code enableSessions=true} still creates the
	 * session eagerly at connection open, which is preferable when temporary tables
	 * or multi-statement scripts are used before any transaction.
	 *
	 * <p>
	 * The {@code BEGIN TRANSACTION} itself is deferred until the first statement
	 * runs, so toggling auto-commit (as connection pools do when recycling
	 * connections) costs no query jobs. Re-enabling auto-commit commits any
	 * in-flight transaction first, per the JDBC contract.
	 *
	 * @param autoCommit
	 *            {@code true} to enable auto-commit, {@code false} for manual
	 *            transaction control
	 * @throws SQLException
	 *             if the connection is closed, the session cannot be created, or
	 *             the transaction cannot be started or committed
	 */
	@Override
	public void setAutoCommit(boolean autoCommit) throws SQLException {
		checkClosed();

		// Thread-safe and atomic transaction state change
		synchronized (this) {
			// If already in the desired state, nothing to do
			if (this.autoCommit == autoCommit) {
				return;
			}

			// Change state atomically - only update flag if operations succeed
			if (autoCommit) {
				// Switching to auto-commit: commit pending transaction first
				if (transactionActive) {
					sessionManager.commit();
					this.transactionActive = false;
				}
				this.autoCommit = true;
			} else {
				// Switching to manual commit: the session must exist so that every
				// statement on this connection is bound to it; BEGIN TRANSACTION is
				// deferred to the first statement
				ensureSession();
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

	/**
	 * Commits the current transaction and begins the next one.
	 *
	 * <p>
	 * Requires manual-commit mode ({@code setAutoCommit(false)}); the transaction
	 * runs inside the connection's BigQuery session.
	 *
	 * @throws SQLException
	 *             if the connection is closed, auto-commit is enabled, or the
	 *             commit fails
	 */
	@Override
	public void commit() throws SQLException {
		checkClosed();

		synchronized (this) {
			if (autoCommit) {
				throw new BQSQLException(ErrorMessages.COMMIT_IN_AUTO_COMMIT,
						BQSQLException.SQLSTATE_INVALID_TRANSACTION_STATE);
			}

			endTransaction(true);
		}
	}

	/**
	 * Rolls back the current transaction and begins the next one.
	 *
	 * <p>
	 * Requires manual-commit mode ({@code setAutoCommit(false)}); the transaction
	 * runs inside the connection's BigQuery session.
	 *
	 * @throws SQLException
	 *             if the connection is closed, auto-commit is enabled, or the
	 *             rollback fails
	 */
	@Override
	public void rollback() throws SQLException {
		checkClosed();

		synchronized (this) {
			if (autoCommit) {
				throw new BQSQLException(ErrorMessages.ROLLBACK_IN_AUTO_COMMIT,
						BQSQLException.SQLSTATE_INVALID_TRANSACTION_STATE);
			}

			endTransaction(false);
		}
	}

	/**
	 * Ends the in-flight transaction, if any. The next statement starts a new one
	 * (JDBC's chained transaction model), so repeated commits work.
	 *
	 * @param commit
	 *            {@code true} to commit, {@code false} to roll back
	 * @throws SQLException
	 *             if the commit or rollback fails
	 */
	private void endTransaction(boolean commit) throws SQLException {
		if (!transactionActive) {
			// Nothing has run since the last commit/rollback
			logger.debug("No active transaction to {}", commit ? "commit" : "roll back");
			return;
		}

		if (commit) {
			sessionManager.commit();
		} else {
			sessionManager.rollback();
		}
		transactionActive = false;
	}

	/**
	 * Begins a transaction if the connection is in manual-commit mode and no
	 * transaction is in flight. Called by statements immediately before they submit
	 * a job, which keeps {@code BEGIN TRANSACTION} out of the auto-commit toggling
	 * that connection pools perform.
	 *
	 * @throws SQLException
	 *             if the transaction cannot be started
	 */
	public void beginTransactionIfNeeded() throws SQLException {
		synchronized (this) {
			if (autoCommit || transactionActive) {
				return;
			}

			sessionManager.beginTransaction();
			transactionActive = true;
		}
	}

	/**
	 * Starts a BigQuery session on demand for connections that were opened without
	 * {@code enableSessions=true}.
	 *
	 * @throws SQLException
	 *             if the session cannot be created
	 */
	private void ensureSession() throws SQLException {
		if (sessionManager.hasSession()) {
			return;
		}

		logger.info("Starting BigQuery session on demand for transaction support "
				+ "(use enableSessions=true to create the session when the connection opens)");
		sessionManager.initializeSession();
	}

	@Override
	@SuppressWarnings("PMD.CloseResource") // stmt.close() is explicitly called in the loop body; PMD doesn't detect it
											// across separate try blocks
	protected void doClose() throws SQLException {
		logger.debug("Closing BigQuery connection");

		// Cancel and close all running statements (JDBC spec: closing a connection
		// closes its statements)
		for (BQStatement stmt : runningStatements) {
			try {
				stmt.cancel();
			} catch (SQLException e) {
				logger.warn("Failed to cancel statement during connection close", e);
			}
			try {
				stmt.close();
			} catch (SQLException e) {
				logger.warn("Failed to close statement during connection close", e);
			}
		}

		// Clear statement references
		runningStatements.clear();

		// Discard any uncommitted work (JDBC leaves this implementation-defined;
		// rolling back matches the behavior of mainstream drivers). A transaction can
		// only be active once a session exists.
		synchronized (this) {
			if (transactionActive) {
				try {
					sessionManager.rollback();
				} catch (SQLException e) {
					logger.warn("Failed to roll back open transaction during connection close", e);
				}
				transactionActive = false;
			}
		}

		// Terminate the session; sessionManager is assigned by the constructor, which
		// fails outright if it cannot be created, so it is never null here
		sessionManager.close();

		// Log metadata cache statistics (cache persists across connections)
		if (metadata != null) {
			String cacheStats = metadata.getCacheStats();
			if (cacheStats != null) {
				logger.debug("Metadata cache statistics: {}", cacheStats);
			}
			// Note: Cache is NOT cleared on connection close - it persists across
			// connections and expires based on TTL. This improves performance for
			// applications (like IntelliJ) that frequently reopen connections.
		}

		logger.debug("BigQuery connection closed");
	}

	@Override
	public boolean isClosed() {
		return closed;
	}

	@Override
	public DatabaseMetaData getMetaData() throws SQLException {
		checkClosed();

		// Double-checked locking: two threads racing here would each build their own
		// BQDatabaseMetaData, and the single-instance reuse is what keeps IntelliJ's
		// repeated introspection off the wire
		BQDatabaseMetaData result = metadata;
		if (result == null) {
			synchronized (this) {
				result = metadata;
				if (result == null) {
					result = new BQDatabaseMetaData(this);
					metadata = result;
				}
			}
		}
		return result;
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

		// null is JDBC's "no catalog", which for BigQuery means the project the
		// connection was opened against. Restoring it is a legitimate request and
		// the only reading of null that is not a silent no-op.
		if (catalog == null || catalog.isBlank()) {
			currentCatalog = properties.projectId();
			logger.debug("setCatalog(null): back to the connection's project {}", currentCatalog);
			return;
		}

		String trimmed = catalog.trim();
		// Rejected rather than ignored. Ignoring is what this method used to do,
		// and a caller had no way to tell a switch that did not happen from one
		// that did — the whole second half of #190. Existence is not checked: that
		// would cost an API call per call, and a project the driver cannot list is
		// still one the credential may be able to query.
		if (!BigQueryIdentifiers.isSafe(trimmed)) {
			throw new BQSQLException("Not a usable BigQuery project id: " + catalog,
					BQSQLException.SQLSTATE_INVALID_PARAMETER_VALUE);
		}

		currentCatalog = trimmed;
		logger.debug("setCatalog: metadata and unqualified names now default to project {}", currentCatalog);
	}

	@Override
	public String getCatalog() throws SQLException {
		checkClosed();
		return currentCatalog;
	}

	/**
	 * The project metadata calls default to, without the closed check.
	 *
	 * <p>
	 * For {@link vc.tbc.bq.jdbc.metadata.BQDatabaseMetaData}, which resolves a null
	 * {@code catalog} argument on paths that are already inside a call and must not
	 * re-check liveness — and in lambdas where a checked exception has nowhere to
	 * go.
	 *
	 * <p>
	 * This is the <b>metadata</b> default only. The project that owns and is billed
	 * for jobs is fixed when the connection opens, because it is baked into the
	 * BigQuery client; {@code setCatalog} does not move billing to another project.
	 *
	 * @return the current catalog, never null
	 */
	public String getCurrentCatalog() {
		return currentCatalog;
	}

	/**
	 * Sets the transaction isolation level.
	 *
	 * <p>
	 * BigQuery transactions always run at snapshot isolation, reported as
	 * {@link Connection#TRANSACTION_REPEATABLE_READ}. That level and
	 * {@link Connection#TRANSACTION_NONE} (for tools that ask to run without
	 * transactions) are accepted and recorded; the actual behavior never changes.
	 * Other levels cannot be honored and are rejected.
	 *
	 * @param level
	 *            one of {@code TRANSACTION_REPEATABLE_READ} or
	 *            {@code TRANSACTION_NONE}
	 * @throws SQLException
	 *             if the connection is closed
	 * @throws java.sql.SQLFeatureNotSupportedException
	 *             if another isolation level is requested
	 */
	@Override
	public void setTransactionIsolation(int level) throws SQLException {
		checkClosed();
		if (level != Connection.TRANSACTION_REPEATABLE_READ && level != Connection.TRANSACTION_NONE) {
			throw new BQSQLFeatureNotSupportedException("BigQuery transactions run at snapshot isolation "
					+ "(reported as TRANSACTION_REPEATABLE_READ); requested level not supported: " + level);
		}

		this.transactionIsolation = level;
		logger.debug("Transaction isolation set to: {} (BigQuery always uses snapshot isolation)", level);
	}

	@Override
	public int getTransactionIsolation() throws SQLException {
		checkClosed();
		return transactionIsolation;
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

	@Override
	public java.sql.Array createArrayOf(String typeName, Object[] elements) throws SQLException {
		checkClosed();
		com.google.cloud.bigquery.StandardSQLTypeName sqlType;
		try {
			sqlType = com.google.cloud.bigquery.StandardSQLTypeName.valueOf(typeName.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			// Fall back to STRING for unknown type names
			sqlType = com.google.cloud.bigquery.StandardSQLTypeName.STRING;
		}
		int jdbcType = TypeMapper.toJdbcType(sqlType);
		java.util.List<Object> elementList = elements != null ? java.util.Arrays.asList(elements) : java.util.List.of();
		return new BQArray(elementList, jdbcType, typeName.toUpperCase(Locale.ROOT));
	}

	/**
	 * Creates a STRUCT value that {@code PreparedStatement.setObject} can bind.
	 *
	 * <p>
	 * <b>{@code typeName} must declare the field names</b>, as
	 * {@code STRUCT<id INT64, name STRING>}. JDBC's {@link java.sql.Struct} is
	 * positional and BigQuery's struct parameters are named, so the names have to
	 * come from somewhere and this is the only argument that can carry them. It is
	 * the same form {@link java.sql.ResultSet#getObject} reports for a struct
	 * column, so a struct that was read can be rebuilt or bound back unchanged.
	 *
	 * <p>
	 * The declared types are used to type a {@code null} attribute, which BigQuery
	 * will not accept untyped. That is the reason to prefer this over passing a
	 * {@code Map<String, Object>} to {@code setObject}: a map has nothing to infer
	 * a null field's type from.
	 *
	 * @param typeName
	 *            the struct type, e.g. {@code STRUCT<id INT64, name STRING>}
	 * @param attributes
	 *            the field values, in the order the type declares them
	 * @return the struct
	 * @throws SQLException
	 *             if the type name does not name its fields, or declares a
	 *             different number of them than there are attributes
	 * @since 3.2.0
	 */
	@Override
	public java.sql.Struct createStruct(String typeName, Object[] attributes) throws SQLException {
		checkClosed();
		java.util.List<StructTypeNames.StructField> fields = StructTypeNames.parse(typeName);
		if (fields.isEmpty()) {
			throw new BQSQLException(
					"createStruct needs a type name that names its fields, e.g. "
							+ "STRUCT<id INT64, name STRING>; got: " + typeName,
					BQSQLException.SQLSTATE_INVALID_PARAMETER_VALUE);
		}
		int attributeCount = attributes != null ? attributes.length : 0;
		if (attributeCount != fields.size()) {
			// Caught here rather than at bind time, where the mismatch would be one
			// statement removed from the call that created it.
			throw new BQSQLException("createStruct got " + attributeCount + " attribute(s) for a type declaring "
					+ fields.size() + ": " + typeName, BQSQLException.SQLSTATE_INVALID_PARAMETER_VALUE);
		}
		return new BQStruct(typeName, attributes);
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

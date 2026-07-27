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
package vc.tbc.bq.jdbc.config;

import com.google.cloud.bigquery.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Manages BigQuery sessions for multi-statement SQL and temporary tables.
 *
 * <p>
 * BigQuery sessions provide:
 *
 * <ul>
 * <li>Multi-statement SQL execution
 * <li>Temporary table support
 * <li>Transaction support (BEGIN, COMMIT, ROLLBACK)
 * <li>Stateful execution context
 * </ul>
 *
 * <p>
 * Usage:
 *
 * <pre>{@code
 * // Connection with sessions enabled
 * String url = "jdbc:bigquery:project/dataset?enableSessions=true";
 * Connection conn = DriverManager.getConnection(url);
 *
 * // Create temporary table
 * stmt.execute("CREATE TEMP TABLE temp_data AS SELECT 1 as id");
 * stmt.execute("SELECT * FROM temp_data");
 * }</pre>
 *
 * <p>
 * <b>Thread Safety:</b> This class is thread-safe. All session state
 * modifications (initialization, commit, rollback, close) are protected by a
 * {@link ReentrantLock}. Multiple threads can safely call session methods
 * concurrently.
 *
 * @since 1.0.0
 */
public class SessionManager {

	private static final Logger logger = LoggerFactory.getLogger(SessionManager.class);

	private final BigQuery bigquery;
	private final ReentrantLock lock = new ReentrantLock();
	private String sessionId;
	private boolean initialized = false;
	private boolean closed = false;

	/**
	 * Creates a session manager.
	 *
	 * @param bigquery
	 *            the BigQuery client
	 */
	public SessionManager(BigQuery bigquery) {
		this.bigquery = bigquery;
	}

	/**
	 * Initializes a BigQuery session.
	 *
	 * @throws SQLException
	 *             if session creation fails
	 */
	public void initializeSession() throws SQLException {
		lock.lock();
		try {
			if (initialized) {
				logger.debug("Session already initialized: {}", sessionId);
				return;
			}

			// BigQuery assigns the session ID: run a trivial job with createSession set
			// and read the ID back from the job statistics (session IDs cannot be chosen
			// by the client).
			QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder("SELECT 1").setUseLegacySql(false)
					.setCreateSession(true).build();

			Job queryJob = bigquery.create(JobInfo.of(queryConfig));
			queryJob = queryJob.waitFor();

			if (queryJob == null) {
				throw new SQLException("Session creation job disappeared");
			}

			JobStatus status = queryJob.getStatus();
			if (status.getError() != null) {
				throw new SQLException("Failed to create session: " + status.getError().getMessage());
			}

			String assignedSessionId = extractSessionId(queryJob);
			if (assignedSessionId == null) {
				// Without an ID there is nothing to attach to later jobs, so every
				// statement would run outside the session while hasSession() claimed
				// otherwise: CREATE TEMP TABLE would succeed and the next statement
				// would report the table missing. Fail here, where the cause is still
				// visible, rather than at some later statement (#148).
				throw new SQLException("BigQuery created the session job but reported no session ID; "
						+ "session-scoped statements would silently run unbound to it");
			}

			this.sessionId = assignedSessionId;
			this.initialized = true;
			logger.info("BigQuery session created: {}", sessionId);

		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new SQLException("Session creation interrupted", e);
		} catch (BigQueryException e) {
			throw new SQLException("Failed to create BigQuery session", e);
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Reads the session ID BigQuery assigned to the session-creating job.
	 *
	 * @param job
	 *            the completed session-creating job
	 * @return the session ID, or {@code null} if the endpoint reported none
	 */
	private static String extractSessionId(Job job) {
		JobStatistics statistics = job.getStatistics();
		if (statistics == null) {
			return null;
		}

		JobStatistics.SessionInfo sessionInfo = statistics.getSessionInfo();
		return sessionInfo == null ? null : sessionInfo.getSessionId();
	}

	/**
	 * Gets the session ID.
	 *
	 * @return the session ID assigned by BigQuery, or null if no session is active
	 *         (or the endpoint did not report one)
	 */
	public String getSessionId() {
		return sessionId;
	}

	/**
	 * Checks if a session is active.
	 *
	 * @return true if session is active
	 */
	public boolean hasSession() {
		return initialized && !closed;
	}

	/**
	 * Adds session property to a query configuration.
	 *
	 * @param configBuilder
	 *            the query config builder
	 * @return the builder with session property added
	 */
	public QueryJobConfiguration.Builder addSessionProperty(QueryJobConfiguration.Builder configBuilder) {
		if (!hasSession() || sessionId == null) {
			return configBuilder;
		}

		ConnectionProperty sessionProperty = ConnectionProperty.newBuilder().setKey("session_id").setValue(sessionId)
				.build();

		return configBuilder.setConnectionProperties(java.util.List.of(sessionProperty));
	}

	/**
	 * Closes the session.
	 *
	 * <p>
	 * Terminates the BigQuery session with {@code BQ.ABORT_SESSION} on a
	 * best-effort basis so it does not linger until BigQuery's idle timeout (24
	 * hours of inactivity, 7 days maximum). Failures are logged, never thrown —
	 * closing a connection must not fail because of session cleanup.
	 */
	@SuppressWarnings("PMD.NullAssignment") // null-out sessionId when closed; intentional — session ID is no longer
											// valid
	public void close() {
		lock.lock();
		try {
			if (closed) {
				return;
			}

			if (sessionId != null) {
				logger.info("Closing BigQuery session: {}", sessionId);
				try {
					executeSessionStatement("CALL BQ.ABORT_SESSION()");
				} catch (SQLException e) {
					// Session will still expire on its own; nothing else to do
					logger.warn("Failed to terminate BigQuery session {}: {}", sessionId, e.getMessage());
				}
				sessionId = null;
			}

			closed = true;
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Begins a transaction within the session.
	 *
	 * @throws SQLException
	 *             if transaction cannot be started
	 */
	public void beginTransaction() throws SQLException {
		if (!hasSession()) {
			initializeSession();
		}

		executeSessionStatement("BEGIN TRANSACTION");
		logger.debug("Transaction started in session: {}", sessionId);
	}

	/**
	 * Commits the current transaction within the active BigQuery session.
	 *
	 * <p>
	 * All DML and DDL statements executed since the transaction began (via
	 * {@link #beginTransaction()}) are committed atomically. After commit, a new
	 * transaction must be started before executing additional transactional
	 * statements.
	 *
	 * <p>
	 * <b>Example:</b>
	 *
	 * <pre>{@code
	 * connection.setAutoCommit(false);
	 * stmt.executeUpdate("INSERT INTO table VALUES (1)");
	 * stmt.executeUpdate("INSERT INTO table VALUES (2)");
	 * connection.commit(); // Both inserts committed atomically
	 * }</pre>
	 *
	 * @throws SQLException
	 *             if no active session exists or commit fails
	 */
	public void commit() throws SQLException {
		if (!hasSession()) {
			throw new SQLException("No active session for transaction commit");
		}

		executeSessionStatement("COMMIT TRANSACTION");
		logger.debug("Transaction committed in session: {}", sessionId);
	}

	/**
	 * Rolls back the current transaction within the active BigQuery session.
	 *
	 * <p>
	 * All DML and DDL statements executed since the transaction began (via
	 * {@link #beginTransaction()}) are rolled back. The session remains active and
	 * a new transaction can be started afterwards.
	 *
	 * <p>
	 * <b>Rollback Scope:</b> Only statements executed within the current
	 * transaction are affected. Statements from previous committed transactions are
	 * not affected.
	 *
	 * <p>
	 * <b>Example:</b>
	 *
	 * <pre>{@code
	 * connection.setAutoCommit(false);
	 * stmt.executeUpdate("INSERT INTO table VALUES (1)");
	 * // Error detected, rollback
	 * connection.rollback(); // Insert is rolled back
	 * }</pre>
	 *
	 * @throws SQLException
	 *             if no active session exists or rollback fails
	 */
	public void rollback() throws SQLException {
		if (!hasSession()) {
			throw new SQLException("No active session for transaction rollback");
		}

		executeSessionStatement("ROLLBACK TRANSACTION");
		logger.debug("Transaction rolled back in session: {}", sessionId);
	}

	/**
	 * Executes a statement within the session.
	 *
	 * @param sql
	 *            the SQL to execute
	 * @throws SQLException
	 *             if execution fails
	 */
	private void executeSessionStatement(String sql) throws SQLException {
		try {
			QueryJobConfiguration.Builder configBuilder = QueryJobConfiguration.newBuilder(sql).setUseLegacySql(false);

			QueryJobConfiguration queryConfig = addSessionProperty(configBuilder).build();

			Job queryJob = bigquery.create(JobInfo.of(queryConfig));
			queryJob = queryJob.waitFor();

			if (queryJob == null) {
				throw new SQLException("Query job disappeared");
			}

			JobStatus status = queryJob.getStatus();
			if (status.getError() != null) {
				throw new SQLException("Query failed: " + status.getError().getMessage());
			}

		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new SQLException("Query interrupted", e);
		} catch (BigQueryException e) {
			throw new SQLException("Failed to execute session statement", e);
		}
	}
}

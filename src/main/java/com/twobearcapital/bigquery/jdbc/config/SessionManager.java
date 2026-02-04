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
package com.twobearcapital.bigquery.jdbc.config;

import com.google.cloud.bigquery.*;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * @since 1.0.0
 */
public class SessionManager {

	private static final Logger logger = LoggerFactory.getLogger(SessionManager.class);

	private final BigQuery bigquery;
	private final ReentrantLock lock = new ReentrantLock();
	private String sessionId;
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
			if (sessionId != null) {
				logger.debug("Session already initialized: {}", sessionId);
				return;
			}

			// Generate unique session ID
			String newSessionId = "jdbc_session_" + UUID.randomUUID().toString().replace("-", "");

			// Create session by executing a simple query with session parameter
			String createSessionSql = "SELECT 1";
			QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(createSessionSql)
					.setCreateSession(true)
					.setConnectionProperties(java.util.List
							.of(ConnectionProperty.newBuilder().setKey("session_id").setValue(newSessionId).build()))
					.build();

			Job queryJob = bigquery.create(JobInfo.of(queryConfig));
			queryJob = queryJob.waitFor();

			if (queryJob == null) {
				throw new SQLException("Session creation job disappeared");
			}

			JobStatus status = queryJob.getStatus();
			if (status.getError() != null) {
				throw new SQLException("Failed to create session: " + status.getError().getMessage());
			}

			this.sessionId = newSessionId;
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
	 * Gets the session ID.
	 *
	 * @return the session ID, or null if no session is active
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
		return sessionId != null && !closed;
	}

	/**
	 * Adds session property to a query configuration.
	 *
	 * @param configBuilder
	 *            the query config builder
	 * @return the builder with session property added
	 */
	public QueryJobConfiguration.Builder addSessionProperty(QueryJobConfiguration.Builder configBuilder) {
		if (!hasSession()) {
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
	 * Note: BigQuery sessions are automatically cleaned up after timeout. This
	 * method just marks the session as closed locally.
	 */
	public void close() {
		lock.lock();
		try {
			if (closed) {
				return;
			}

			closed = true;

			if (sessionId != null) {
				logger.info("Closing BigQuery session: {}", sessionId);
				// BigQuery will automatically clean up the session after timeout
				sessionId = null;
			}
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
	 * Commits the current transaction.
	 *
	 * @throws SQLException
	 *             if commit fails
	 */
	public void commit() throws SQLException {
		if (!hasSession()) {
			throw new SQLException("No active session for transaction commit");
		}

		executeSessionStatement("COMMIT TRANSACTION");
		logger.debug("Transaction committed in session: {}", sessionId);
	}

	/**
	 * Rolls back the current transaction.
	 *
	 * @throws SQLException
	 *             if rollback fails
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

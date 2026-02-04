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

import com.google.cloud.bigquery.*;
import com.twobearcapital.bigquery.jdbc.BQConnection;
import com.twobearcapital.bigquery.jdbc.BQResultSet;
import com.twobearcapital.bigquery.jdbc.BQStatement;
import com.twobearcapital.bigquery.jdbc.config.ConnectionProperties;
import com.twobearcapital.bigquery.jdbc.config.SessionManager;
import com.twobearcapital.bigquery.jdbc.exception.BQSQLException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract base class for BigQuery statement implementations. Provides common
 * query execution logic with template methods for customization. Eliminates ~60
 * lines of duplicate code between BQStatement and BQPreparedStatement.
 *
 * <p>
 * Fixes:
 * <ul>
 * <li>ResultSet leak when executeQuery() called multiple times
 * <li>Thread-safe access to currentJob during cancel operations
 * </ul>
 */
public abstract class AbstractBQStatement extends BaseCloseable implements Statement {

	private static final Logger logger = LoggerFactory.getLogger(AbstractBQStatement.class);

	protected final BQConnection connection;
	protected final BigQuery bigquery;
	protected final ConnectionProperties properties;

	/**
	 * Volatile ensures visibility across threads for cancel operations.
	 * Thread-safety fix for concurrent access during query execution.
	 */
	protected volatile Job currentJob;

	protected int queryTimeout = 0;
	protected int maxRows = 0;

	/**
	 * Current result set. Closed before creating new one to prevent resource leak.
	 */
	protected ResultSet currentResultSet;

	protected AbstractBQStatement(BQConnection connection) {
		this.connection = connection;
		this.bigquery = connection.getBigQuery();
		this.properties = connection.getProperties();
		connection.registerStatement((BQStatement) this);
	}

	@Override
	protected void doClose() throws SQLException {
		connection.unregisterStatement((BQStatement) this);
		if (currentResultSet != null) {
			currentResultSet.close();
		}
	}

	/**
	 * Builds the query configuration for execution. Template method for subclasses
	 * to customize query config.
	 *
	 * @param sql
	 *            the SQL query (may be template with ? placeholders)
	 * @return the query configuration builder
	 */
	protected abstract QueryJobConfiguration.Builder buildQueryConfig(String sql);

	/**
	 * Creates a ResultSet from the query result. Template method for subclasses to
	 * customize result set creation.
	 *
	 * @param result
	 *            the table result from BigQuery
	 * @return the JDBC ResultSet
	 */
	protected ResultSet createResultSet(TableResult result) {
		return createResultSet(result, null);
	}

	/**
	 * Creates a ResultSet for the given query result and job. Determines whether to
	 * use Storage API based on configuration and result size.
	 *
	 * @param result
	 *            the table result from BigQuery
	 * @param job
	 *            the completed query job (may be null)
	 * @return the JDBC ResultSet
	 */
	protected ResultSet createResultSet(TableResult result, Job job) {
		// Check if we should use Storage API
		String useStorageApiSetting = properties.useStorageApi();
		if (useStorageApiSetting != null && com.twobearcapital.bigquery.jdbc.storage.StorageReadResultSet
				.shouldUseStorageApi(result, useStorageApiSetting)) {

			// Extract destination table from job
			TableId tableId = extractTableId(job);

			if (tableId != null) {
				try {
					logger.debug("Using Storage API for result set (table: {})", tableId);
					return new com.twobearcapital.bigquery.jdbc.storage.StorageReadResultSet((BQStatement) this,
							tableId);
				} catch (SQLException e) {
					// Fallback to standard result set on Storage API failure
					logger.warn("Storage API initialization failed, falling back to standard ResultSet: {}",
							e.getMessage());
				}
			} else {
				logger.debug("Cannot use Storage API: no destination table found in job");
			}
		}

		// Default: use standard BQResultSet
		return new BQResultSet((BQStatement) this, result);
	}

	/**
	 * Extracts the destination TableId from a completed query job.
	 *
	 * @param job
	 *            the query job (may be null)
	 * @return the destination table ID, or null if not available
	 */
	private TableId extractTableId(Job job) {
		if (job == null) {
			return null;
		}

		try {
			JobConfiguration configuration = job.getConfiguration();
			if (configuration instanceof QueryJobConfiguration) {
				QueryJobConfiguration queryConfig = (QueryJobConfiguration) configuration;
				return queryConfig.getDestinationTable();
			}
		} catch (Exception e) {
			logger.debug("Failed to extract destination table from job: {}", e.getMessage());
		}

		return null;
	}

	/**
	 * Returns the log message prefix for this statement type. Used to differentiate
	 * log messages between Statement and PreparedStatement.
	 *
	 * @return log message prefix (e.g., "Query" or "Prepared query")
	 */
	protected abstract String getLogPrefix();

	/**
	 * Common query execution logic with resource leak fix. Closes previous
	 * ResultSet before creating new one. Thread-safe access to currentJob for
	 * cancel operations.
	 *
	 * @param sql
	 *            the SQL query to execute
	 * @return the result set
	 * @throws SQLException
	 *             if query fails
	 */
	protected ResultSet executeQueryInternal(String sql) throws SQLException {
		checkClosed();
		logger.debug("Executing {}: {}", getLogPrefix(), sql);

		// Fix: Close previous ResultSet to prevent resource leak
		if (currentResultSet != null) {
			currentResultSet.close();
			currentResultSet = null;
		}

		QueryJobConfiguration.Builder configBuilder = buildQueryConfig(sql);

		// Set default dataset if configured
		if (properties.getDatasetId() != null) {
			configBuilder.setDefaultDataset(properties.getDatasetId());
		}

		// Set labels
		if (!properties.labels().isEmpty()) {
			configBuilder.setLabels(properties.labels());
		}

		// Add session property if sessions are enabled
		SessionManager sessionManager = connection.getSessionManager();
		if (sessionManager != null && sessionManager.hasSession()) {
			configBuilder = sessionManager.addSessionProperty(configBuilder);
		}

		QueryJobConfiguration queryConfig = configBuilder.build();
		long timeoutSeconds = queryTimeout > 0 ? queryTimeout : properties.timeoutSeconds();

		try {
			// Submit job asynchronously with timeout enforcement
			CompletableFuture<JobResultPair> future = CompletableFuture.supplyAsync(() -> {
				try {
					Job job = bigquery.create(JobInfo.of(queryConfig));

					// Thread-safe assignment
					synchronized (this) {
						this.currentJob = job;
					}

					logger.info("{} job created: {}", getLogPrefix(), job.getJobId());

					// Wait for job completion
					job = job.waitFor();

					if (job == null) {
						throw new RuntimeException("Job no longer exists");
					}

					JobStatus status = job.getStatus();
					if (status.getError() != null) {
						BigQueryError error = status.getError();
						throw new RuntimeException("Query failed (job: " + job.getJobId() + "): " + error.getMessage());
					}

					return new JobResultPair(job, job.getQueryResults());

				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					throw new RuntimeException("Query interrupted", e);
				}
			});

			// Wait with timeout
			JobResultPair pair = future.get(timeoutSeconds, TimeUnit.SECONDS);
			currentResultSet = createResultSet(pair.result, pair.job);
			return currentResultSet;

		} catch (TimeoutException e) {
			// Thread-safe access to currentJob during cancel
			Job jobToCancel;
			synchronized (this) {
				jobToCancel = currentJob;
			}

			if (jobToCancel != null) {
				try {
					bigquery.cancel(jobToCancel.getJobId());
					logger.warn("{} cancelled due to timeout: {}", getLogPrefix(), jobToCancel.getJobId());
				} catch (Exception cancelEx) {
					logger.warn("Failed to cancel job after timeout", cancelEx);
				}
			}
			throw new SQLTimeoutException("Query timeout after " + timeoutSeconds + " seconds");
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new BQSQLException("Query interrupted", e);
		} catch (ExecutionException e) {
			Throwable cause = e.getCause();
			if (cause instanceof RuntimeException) {
				throw new BQSQLException(cause.getMessage(), BQSQLException.SQLSTATE_SYNTAX_ERROR, cause);
			}
			throw new BQSQLException("Query execution failed: " + cause.getMessage(), cause);
		} catch (BigQueryException e) {
			throw new BQSQLException("Query execution failed: " + e.getMessage(), e);
		}
	}

	@Override
	public int getMaxFieldSize() throws SQLException {
		checkClosed();
		return 0;
	}

	@Override
	public void setMaxFieldSize(int max) throws SQLException {
		checkClosed();
	}

	@Override
	public int getMaxRows() throws SQLException {
		checkClosed();
		return maxRows;
	}

	@Override
	public void setMaxRows(int max) throws SQLException {
		checkClosed();
		this.maxRows = max;
	}

	@Override
	public void setEscapeProcessing(boolean enable) throws SQLException {
		checkClosed();
	}

	@Override
	public int getQueryTimeout() throws SQLException {
		checkClosed();
		return queryTimeout;
	}

	@Override
	public void setQueryTimeout(int seconds) throws SQLException {
		checkClosed();
		this.queryTimeout = seconds;
	}

	/**
	 * Cancels the currently executing query. Thread-safe access to currentJob.
	 */
	@Override
	public void cancel() throws SQLException {
		Job jobToCancel;
		synchronized (this) {
			jobToCancel = currentJob;
		}

		if (jobToCancel != null) {
			try {
				bigquery.cancel(jobToCancel.getJobId());
				logger.info("Query cancelled: {}", jobToCancel.getJobId());
			} catch (BigQueryException e) {
				throw new BQSQLException("Failed to cancel query", e);
			}
		}
	}

	@Override
	public ResultSet getResultSet() throws SQLException {
		checkClosed();
		return currentResultSet;
	}

	@Override
	public int getUpdateCount() throws SQLException {
		checkClosed();
		return -1;
	}

	@Override
	public boolean getMoreResults() throws SQLException {
		checkClosed();
		return false;
	}

	@Override
	public BQConnection getConnection() throws SQLException {
		checkClosed();
		return connection;
	}

	/**
	 * Helper class to hold both Job and TableResult from async execution.
	 */
	private static class JobResultPair {
		final Job job;
		final TableResult result;

		JobResultPair(Job job, TableResult result) {
			this.job = job;
			this.result = result;
		}
	}
}

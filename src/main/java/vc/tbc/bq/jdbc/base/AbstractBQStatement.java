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
package vc.tbc.bq.jdbc.base;

import com.google.cloud.bigquery.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vc.tbc.bq.jdbc.BQConnection;
import vc.tbc.bq.jdbc.BQResultSet;
import vc.tbc.bq.jdbc.BQStatement;
import vc.tbc.bq.jdbc.config.ConnectionProperties;
import vc.tbc.bq.jdbc.config.MetadataCache;
import vc.tbc.bq.jdbc.config.SessionManager;
import vc.tbc.bq.jdbc.exception.BQSQLException;
import vc.tbc.bq.jdbc.storage.StorageReadResultSet;
import vc.tbc.bq.jdbc.util.QueryCostEstimate;

import java.sql.*;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

	/**
	 * Extracts the dataset/schema name from a fully-qualified INFORMATION_SCHEMA
	 * reference. Matches both unquoted ({@code project.schema.INFORMATION_SCHEMA})
	 * and backtick-quoted ({@code `project`.`schema`.INFORMATION_SCHEMA}) forms.
	 * Group 1 captures the schema token (with backticks if present).
	 */
	private static final Pattern IS_SCHEMA_EXTRACTOR = Pattern
			.compile("(?:`[^`]+`|[^\\s.`]+)\\.(`[^`]+`|[^\\s.`]+)\\.INFORMATION_SCHEMA", Pattern.CASE_INSENSITIVE);

	private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

	protected final BQConnection connection;
	protected final BigQuery bigquery;
	protected final ConnectionProperties properties;

	/**
	 * Volatile ensures visibility across threads for cancel operations.
	 * Thread-safety fix for concurrent access during query execution.
	 */
	protected volatile Job currentJob;

	protected volatile int queryTimeout = 0;
	protected volatile int maxRows = 0;

	/**
	 * Current result set. Closed before creating new one to prevent resource leak.
	 */
	protected ResultSet currentResultSet;

	/**
	 * Update count of the current result, from BigQuery DML statistics
	 * ({@code numDmlAffectedRows}). -1 when the current result is a ResultSet or
	 * there is no result, per the JDBC {@code getUpdateCount()} contract.
	 */
	protected volatile long currentUpdateCount = -1L;

	/**
	 * Query warnings (e.g., cost estimates from dry-run).
	 */
	protected SQLWarning queryWarnings = null;

	protected AbstractBQStatement(BQConnection connection) {
		this.connection = connection;
		this.bigquery = connection.getBigQuery();
		this.properties = connection.getProperties();
		// Apply maxResults connection property as the default statement row limit
		if (properties.maxResults() != null && properties.maxResults() > 0) {
			this.maxRows = properties.maxResults().intValue();
		}
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
	 * Gets the effective fetch size for pagination. Subclasses should return the
	 * configured fetch size or connection default.
	 *
	 * @return effective fetch size (never 0)
	 */
	protected abstract int getEffectiveFetchSize();

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
		if (useStorageApiSetting != null && StorageReadResultSet.shouldUseStorageApi(result, useStorageApiSetting)) {

			// Extract destination table from job
			TableId tableId = extractTableId(job);

			if (tableId != null) {
				try {
					logger.debug("Using Storage API for result set (table: {})", tableId);
					return new StorageReadResultSet((BQStatement) this, tableId);
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
			if (configuration instanceof QueryJobConfiguration queryConfig) {
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
	 * Checks if a SQL statement is a SELECT query (vs DDL/DML).
	 *
	 * @param sql
	 *            the SQL statement
	 * @return true if the statement is a SELECT query
	 */
	private boolean isSelectQuery(String sql) {
		if (sql == null) {
			return false;
		}
		String trimmed = sql.trim().toUpperCase(Locale.ROOT);
		return trimmed.startsWith("SELECT") || trimmed.startsWith("WITH");
	}

	/**
	 * Runs a dry-run query to estimate cost without executing.
	 *
	 * @param sql
	 *            the SQL query to estimate
	 * @return the cost estimate
	 * @throws SQLException
	 *             if dry-run fails
	 */
	protected QueryCostEstimate runDryRun(String sql) throws SQLException {
		try {
			QueryJobConfiguration.Builder dryRunConfigBuilder = buildQueryConfig(sql).setDryRun(true)
					.setUseQueryCache(false);

			// Set default dataset if configured
			if (properties.getDatasetId() != null) {
				dryRunConfigBuilder.setDefaultDataset(properties.getDatasetId());
			}

			QueryJobConfiguration dryRunConfig = dryRunConfigBuilder.build();

			Job dryRunJob = bigquery.create(JobInfo.of(dryRunConfig));
			JobStatistics.QueryStatistics stats = dryRunJob.getStatistics();

			Long bytesProcessed = stats.getTotalBytesProcessed();
			Long bytesBilled = stats.getTotalBytesBilled();
			Long estimatedBytes = stats.getEstimatedBytesProcessed();

			return new QueryCostEstimate(bytesProcessed, estimatedBytes, bytesBilled,
					QueryCostEstimate.calculateCost(bytesBilled));

		} catch (BigQueryException e) {
			throw new BQSQLException("Dry-run failed: " + e.getMessage(), e);
		}
	}

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
	@SuppressWarnings({"PMD.NullAssignment", "PMD.PreserveStackTrace"})
	protected ResultSet executeQueryInternal(String sql) throws SQLException {
		checkClosed();
		logger.debug("Executing {}: {}", getLogPrefix(), sql);

		// Fix: Close previous ResultSet to prevent resource leak
		if (currentResultSet != null) {
			currentResultSet.close();
			currentResultSet = null;
		}

		// Clear previous warnings and update count
		queryWarnings = null;
		currentUpdateCount = -1L;

		// Serve INFORMATION_SCHEMA queries from the shared cache when available.
		// These are read-only catalog views that IntelliJ executes on every
		// introspection pass; caching them eliminates redundant BigQuery jobs.
		if (isInformationSchemaQuery(sql)) {
			MetadataCache cache = connection.getMetadataCache();
			if (cache != null) {
				String cacheKey = normalizeSqlCacheKey(sql);
				Optional<ResultSet> cached = cache.get(cacheKey);
				if (cached.isPresent()) {
					IsSchemaMatch match = extractIsSchema(sql);
					logger.info("IS cache hit [schema={}]: {}", match != null ? match.rawName() : "?", cacheKey);
					currentResultSet = cached.get();
					return currentResultSet;
				}
				// Log unexpected misses at INFO so new IDE query patterns are visible in logs.
				if (!cache.getKnownSchemas().isEmpty()) {
					logger.info("IS cache miss (new pattern?): {}", cacheKey);
				}
			}
		}

		// Run dry-run if cost estimation is enabled
		if (properties.enableQueryCostEstimation()) {
			try {
				QueryCostEstimate estimate = runDryRun(sql);

				// Create warning with cost information
				String message = String.format("Query will process %s (%.2f MB), estimated cost: $%s",
						QueryCostEstimate.formatBytes(estimate.totalBytesProcessed()),
						estimate.totalBytesProcessed() / 1_000_000.0, estimate.estimatedCostUSD());

				queryWarnings = new SQLWarning(message, "01000", // Standard warning SQL state
						estimate.getMegabytes() // MB as vendor code
				);

				logger.info("Dry-run estimate: {}", message);

			} catch (Exception e) {
				// Don't fail query if dry-run fails - log and continue
				logger.warn("Dry-run estimation failed: {}", e.getMessage());
			}
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

		// Apply fetchSize for pagination (JDBC Statement.setFetchSize)
		// Note: maxRows is enforced at ResultSet level, not at query level
		int effectiveFetchSize = getEffectiveFetchSize();
		if (effectiveFetchSize > 0) {
			configBuilder.setMaxResults((long) effectiveFetchSize);
		}

		// Add session property if sessions are enabled
		SessionManager sessionManager = connection.getSessionManager();
		if (sessionManager != null && sessionManager.hasSession()) {
			configBuilder = sessionManager.addSessionProperty(configBuilder);
		}

		// Set destination table for SELECT queries when configured
		// Useful for BigQuery emulator compatibility (set UseDestinationTables=true)
		// Only applies to SELECT queries; DDL/DML don't need destination tables
		if (properties.useDestinationTables() && properties.datasetId() != null && isSelectQuery(sql)) {
			// Generate unique temp table name
			String tempTableName = "_jdbc_temp_" + System.currentTimeMillis() + "_" + Thread.currentThread().threadId();
			TableId destinationTable = TableId.of(properties.projectId(), properties.datasetId(), tempTableName);
			configBuilder.setDestinationTable(destinationTable)
					.setCreateDisposition(JobInfo.CreateDisposition.CREATE_IF_NEEDED)
					.setWriteDisposition(JobInfo.WriteDisposition.WRITE_TRUNCATE);
		}

		QueryJobConfiguration queryConfig = configBuilder.build();
		long timeoutSeconds = queryTimeout > 0 ? queryTimeout : properties.timeoutSeconds();

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

		JobResultPair pair = awaitWithTimeout(future, timeoutSeconds);

		// Record the DML affected-row count so getUpdateCount() and execute()
		// can report per-spec results when the statement turned out to be DML
		currentUpdateCount = readDmlAffectedRows(pair.job);

		try {
			// Store INFORMATION_SCHEMA results in the shared cache and return a
			// MetadataResultSet so the cached copy can be replayed on future hits.
			if (isInformationSchemaQuery(sql)) {
				MetadataCache cache = connection.getMetadataCache();
				if (cache != null) {
					String cacheKey = normalizeSqlCacheKey(sql);
					cache.put(cacheKey, pair.result);
					// Adaptive pre-warming: propagate this IS query pattern to all other
					// known schemas in the background so IntelliJ's next introspection pass
					// finds warm cache entries instead of firing live BigQuery jobs.
					triggerSpeculativePreWarm(sql, cache);
					// Serve from cache so the cursor state is independent of TableResult
					currentResultSet = cache.get(cacheKey).orElseGet(() -> createResultSet(pair.result, pair.job));
					return currentResultSet;
				}
			}

			currentResultSet = createResultSet(pair.result, pair.job);
			return currentResultSet;

		} catch (BigQueryException e) {
			throw new BQSQLException("Query execution failed: " + e.getMessage(), e);
		}
	}

	/**
	 * Awaits an async query job future with timeout enforcement. On timeout the
	 * running BigQuery job is cancelled. ExecutionExceptions are unwrapped to
	 * expose the root cause (the wrapper adds no diagnostic value).
	 *
	 * @param future
	 *            the future to await
	 * @param timeoutSeconds
	 *            maximum seconds to wait
	 * @return the future's result
	 * @throws SQLException
	 *             if the wait times out, is interrupted, or the job fails
	 */
	@SuppressWarnings("PMD.PreserveStackTrace")
	private <T> T awaitWithTimeout(CompletableFuture<T> future, long timeoutSeconds) throws SQLException {
		try {
			return future.get(timeoutSeconds, TimeUnit.SECONDS);
		} catch (TimeoutException e) {
			// Cancel the future to interrupt the virtual thread
			future.cancel(true);

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
			// Chain the TimeoutException so the full stack trace is preserved
			throw new SQLTimeoutException("Query timeout after " + timeoutSeconds + " seconds", "S1T00", e);
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

	/**
	 * Executes a DML statement as a BigQuery query job and returns the number of
	 * affected rows. Used by JDBC batch execution, where per-statement update
	 * counts are required and no ResultSet is produced.
	 *
	 * <p>
	 * Applies the same connection-level configuration as query execution (legacy
	 * SQL mode, default dataset, labels, session) and enforces the statement query
	 * timeout with job cancellation, but skips query-only concerns
	 * (INFORMATION_SCHEMA caching, cost estimation, fetch size).
	 *
	 * @param sql
	 *            the DML statement to execute
	 * @param positionalParameters
	 *            positional query parameters, or null/empty for none
	 * @return the number of rows affected, or -1 if BigQuery did not report DML
	 *         statistics
	 * @throws SQLException
	 *             if the statement is closed or execution fails
	 */
	@SuppressWarnings("PMD.NullAssignment")
	protected long executeDmlInternal(String sql, java.util.List<QueryParameterValue> positionalParameters)
			throws SQLException {
		checkClosed();
		logger.debug("Executing {} DML: {}", getLogPrefix(), sql);

		// Executing a statement implicitly closes the previous result
		if (currentResultSet != null) {
			currentResultSet.close();
			currentResultSet = null;
		}
		queryWarnings = null;
		currentUpdateCount = -1L;

		QueryJobConfiguration.Builder configBuilder = QueryJobConfiguration.newBuilder(sql)
				.setUseLegacySql(properties.useLegacySql());

		if (positionalParameters != null && !positionalParameters.isEmpty()) {
			configBuilder.setPositionalParameters(positionalParameters);
		}

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

		// SELECT via executeUpdate() is tolerated (returns 0); apply the same
		// destination-table workaround as the query path for emulator compatibility
		if (properties.useDestinationTables() && properties.datasetId() != null && isSelectQuery(sql)) {
			String tempTableName = "_jdbc_temp_" + System.currentTimeMillis() + "_" + Thread.currentThread().threadId();
			TableId destinationTable = TableId.of(properties.projectId(), properties.datasetId(), tempTableName);
			configBuilder.setDestinationTable(destinationTable)
					.setCreateDisposition(JobInfo.CreateDisposition.CREATE_IF_NEEDED)
					.setWriteDisposition(JobInfo.WriteDisposition.WRITE_TRUNCATE);
		}

		QueryJobConfiguration queryConfig = configBuilder.build();
		long timeoutSeconds = queryTimeout > 0 ? queryTimeout : properties.timeoutSeconds();

		CompletableFuture<Job> future = CompletableFuture.supplyAsync(() -> {
			try {
				Job job = bigquery.create(JobInfo.of(queryConfig));

				// Thread-safe assignment
				synchronized (this) {
					this.currentJob = job;
				}

				logger.info("{} DML job created: {}", getLogPrefix(), job.getJobId());

				job = job.waitFor();

				if (job == null) {
					throw new RuntimeException("Job no longer exists");
				}

				JobStatus status = job.getStatus();
				if (status.getError() != null) {
					BigQueryError error = status.getError();
					throw new RuntimeException("DML failed (job: " + job.getJobId() + "): " + error.getMessage());
				}

				return job;

			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new RuntimeException("Query interrupted", e);
			}
		});

		Job job = awaitWithTimeout(future, timeoutSeconds);
		return readDmlAffectedRows(job);
	}

	/**
	 * Reads the DML affected-row count ({@code numDmlAffectedRows}) from a
	 * completed query job's statistics.
	 *
	 * @param job
	 *            the completed query job (may be null)
	 * @return the affected-row count, or -1 when the job carries no DML statistics
	 *         (SELECT, DDL, or statistics unavailable)
	 */
	protected long readDmlAffectedRows(Job job) {
		if (job == null) {
			return -1L;
		}
		try {
			JobStatistics.QueryStatistics stats = job.getStatistics();
			Long affectedRows = stats == null ? null : stats.getNumDmlAffectedRows();
			return affectedRows == null ? -1L : affectedRows;
		} catch (BigQueryException | ClassCastException e) {
			// The statement itself succeeded; a statistics read failure only
			// degrades the update count to "unknown"
			logger.debug("Could not read DML statistics: {}", e.getMessage());
			return -1L;
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

	/**
	 * Returns the current result's update count, taken from BigQuery DML statistics
	 * ({@code numDmlAffectedRows}). Returns -1 when the current result is a
	 * ResultSet (SELECT) or there is no result, per the JDBC contract.
	 */
	@Override
	public int getUpdateCount() throws SQLException {
		checkClosed();
		if (currentUpdateCount < 0) {
			return -1;
		}
		return currentUpdateCount > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) currentUpdateCount;
	}

	/** Large-count variant of {@link #getUpdateCount()}. */
	@Override
	public long getLargeUpdateCount() throws SQLException {
		checkClosed();
		return currentUpdateCount < 0 ? -1L : currentUpdateCount;
	}

	@Override
	public boolean getMoreResults() throws SQLException {
		checkClosed();
		// No more results: per the JDBC contract, subsequent getUpdateCount()
		// must return -1
		currentUpdateCount = -1L;
		return false;
	}

	@Override
	public BQConnection getConnection() throws SQLException {
		checkClosed();
		return connection;
	}

	/**
	 * Returns {@code true} if the SQL targets a BigQuery INFORMATION_SCHEMA view.
	 *
	 * <p>
	 * INFORMATION_SCHEMA views are read-only catalog views — their content is safe
	 * to cache with a TTL because they reflect schema state, not transactional
	 * data. This check gates both the cache-read and cache-write paths.
	 *
	 * @param sql
	 *            the SQL string to inspect
	 * @return true when the SQL references INFORMATION_SCHEMA
	 */
	private static boolean isInformationSchemaQuery(String sql) {
		return sql != null && sql.toUpperCase(Locale.ROOT).contains("INFORMATION_SCHEMA");
	}

	/**
	 * Produces a stable cache key for an INFORMATION_SCHEMA query by normalising
	 * all whitespace runs to a single space and trimming leading/trailing
	 * whitespace.
	 *
	 * <p>
	 * IntelliJ embeds the fully-qualified dataset name in every query (e.g.
	 * {@code `project`.dataset.INFORMATION_SCHEMA.COLUMNS}), so no additional
	 * namespace prefix is needed — the SQL itself is already dataset-scoped. The
	 * {@code "query:"} prefix prevents collisions with the JDBC-metadata keys
	 * stored in the same {@link MetadataCache}.
	 *
	 * @param sql
	 *            the raw SQL string
	 * @return the normalised cache key
	 */
	private static String normalizeSqlCacheKey(String sql) {
		return "query:" + WHITESPACE_PATTERN.matcher(sql.strip()).replaceAll(" ");
	}

	/**
	 * Extracts the dataset/schema name from a fully-qualified INFORMATION_SCHEMA
	 * SQL reference using {@link #IS_SCHEMA_EXTRACTOR}.
	 *
	 * @param sql
	 *            the SQL string to inspect
	 * @return the schema match, or {@code null} if no IS reference is found
	 */
	private static IsSchemaMatch extractIsSchema(String sql) {
		Matcher m = IS_SCHEMA_EXTRACTOR.matcher(sql);
		if (!m.find())
			return null;
		String token = m.group(1);
		return token.startsWith("`")
				? new IsSchemaMatch(token.substring(1, token.length() - 1), true)
				: new IsSchemaMatch(token, false);
	}

	/**
	 * Substitutes the schema name in an INFORMATION_SCHEMA query, preserving the
	 * original backtick-quoting style of the source schema token. All occurrences
	 * are replaced to handle JOIN queries that reference the same schema twice.
	 *
	 * @param sql
	 *            the original SQL
	 * @param from
	 *            the schema name extracted from the original SQL
	 * @param toSchema
	 *            the target schema name to substitute
	 * @return the SQL with the schema name replaced
	 */
	private static String substituteSchema(String sql, IsSchemaMatch from, String toSchema) {
		String fromQuoted = from.backtickQuoted() ? "`" + from.rawName() + "`" : from.rawName();
		String toQuoted = from.backtickQuoted() ? "`" + toSchema + "`" : toSchema;
		return sql.replace("." + fromQuoted + ".INFORMATION_SCHEMA", "." + toQuoted + ".INFORMATION_SCHEMA");
	}

	/**
	 * Fires a speculative BigQuery query in a virtual thread and stores the result
	 * in the shared cache under the normalised SQL key. Uses a simple
	 * {@link QueryJobConfiguration} (no labels, no sessions) since this is a
	 * background cache-warming operation.
	 *
	 * @param cache
	 *            the metadata cache to store the result in
	 * @param specSql
	 *            the speculative SQL to execute
	 * @param specKey
	 *            the normalised cache key for this SQL
	 */
	private void fireSpeculative(MetadataCache cache, String specSql, String specKey) {
		Thread.ofVirtual().name("is-prewarm-" + specKey.hashCode()).start(() -> {
			try {
				QueryJobConfiguration config = QueryJobConfiguration.newBuilder(specSql).setUseLegacySql(false).build();
				Job job = bigquery.create(JobInfo.of(config));
				job = job.waitFor();
				if (job != null && job.getStatus().getError() == null) {
					cache.put(specKey, job.getQueryResults());
					logger.debug("Speculative pre-warm complete: {}", specKey);
				} else {
					logger.debug("Speculative pre-warm failed (BQ error): {}", specKey);
				}
			} catch (Exception e) {
				logger.debug("Speculative pre-warm exception for key {}: {}", specKey, e.getMessage());
			} finally {
				cache.releaseSpeculative(specKey);
			}
		});
	}

	/**
	 * Triggers speculative pre-warming for an INFORMATION_SCHEMA query across all
	 * known schemas. For each known schema that differs from the one in
	 * {@code originalSql}, derives the equivalent SQL by substituting the schema
	 * name, then fires a background BigQuery job (via {@link #fireSpeculative}) if
	 * the result is not already cached or in-flight.
	 *
	 * @param originalSql
	 *            the IS query that was just executed and cached
	 * @param cache
	 *            the metadata cache
	 */
	private void triggerSpeculativePreWarm(String originalSql, MetadataCache cache) {
		Set<String> schemas = cache.getKnownSchemas();
		if (schemas.isEmpty())
			return;

		IsSchemaMatch from = extractIsSchema(originalSql);
		if (from == null)
			return; // SCHEMATA or other no-dataset IS query — skip

		for (String toSchema : schemas) {
			if (toSchema.equals(from.rawName()))
				continue; // skip self
			String specSql = substituteSchema(originalSql, from, toSchema);
			String specKey = normalizeSqlCacheKey(specSql);
			// Only fire if not already cached and not already in-flight
			if (cache.get(specKey).isEmpty() && cache.claimSpeculative(specKey)) {
				fireSpeculative(cache, specSql, specKey);
			}
		}
	}

	/**
	 * Represents the schema/dataset name extracted from a BigQuery
	 * INFORMATION_SCHEMA SQL reference.
	 *
	 * @param rawName
	 *            the unquoted schema name
	 * @param backtickQuoted
	 *            whether the original token was enclosed in backticks
	 */
	private record IsSchemaMatch(String rawName, boolean backtickQuoted) {
	}

	/**
	 * Helper class to hold both Job and TableResult from async execution.
	 */
	private record JobResultPair(Job job, TableResult result) {
	}
}

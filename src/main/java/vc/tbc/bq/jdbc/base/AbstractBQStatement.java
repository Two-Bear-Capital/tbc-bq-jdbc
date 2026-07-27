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
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
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
 * <li>Safe publication of currentJob for cancel operations
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

	/**
	 * Guards the "Storage Read API not implemented" warning so a connection that
	 * asks for it does not log once per query. JVM-wide on purpose: the message is
	 * about a driver capability, not about one statement.
	 */
	private static final AtomicBoolean STORAGE_API_UNSUPPORTED_WARNED = new AtomicBoolean(false);

	/**
	 * Threads that run query jobs.
	 *
	 * <p>
	 * Query execution blocks for the whole BigQuery round-trip — job creation plus
	 * {@code waitFor} polling — so it must not run on
	 * {@link java.util.concurrent.ForkJoinPool#commonPool()}, whose parallelism is
	 * {@code availableProcessors() - 1} and which is shared with the host
	 * application. On the common pool, concurrent queries queue behind each other
	 * and starve unrelated parallel work; a trivial {@code SELECT 1} was observed
	 * taking over 30 seconds with eight callers on a four-core machine.
	 *
	 * <p>
	 * Virtual threads suit blocking I/O and need no pooling, so a thread per task
	 * requires no lifecycle management or shutdown.
	 */
	private static final java.util.concurrent.ThreadFactory QUERY_THREADS = Thread.ofVirtual().name("bq-jdbc-query-", 0)
			.factory();

	private static final java.util.concurrent.Executor QUERY_EXECUTOR = runnable -> QUERY_THREADS.newThread(runnable)
			.start();

	protected final BQConnection connection;
	protected final BigQuery bigquery;
	protected final ConnectionProperties properties;

	/**
	 * The in-flight BigQuery job, or null when none is running.
	 *
	 * <p>
	 * {@code volatile} is the whole synchronisation story here: the field is
	 * written once per execution and read by {@code cancel()} on another thread,
	 * with no read-modify-write anywhere, so visibility is all that is required.
	 * Readers copy it into a local first so a concurrent write cannot change the
	 * value between the null check and its use.
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
	 * Creates a ResultSet for the given query result and job.
	 *
	 * <p>
	 * The Storage Read API path is currently inert: {@link StorageReadResultSet}
	 * opens a read session but never decodes rows, so it is not wired up here. When
	 * {@code useStorageApi} asks for it we log once and return the standard
	 * ResultSet instead of a result set that cannot iterate.
	 *
	 * @param result
	 *            the table result from BigQuery
	 * @param job
	 *            the completed query job (may be null)
	 * @return the JDBC ResultSet
	 */
	protected ResultSet createResultSet(TableResult result, Job job) {
		String useStorageApiSetting = properties.useStorageApi();
		if (useStorageApiSetting != null && StorageReadResultSet.shouldUseStorageApi(result, useStorageApiSetting)
				&& STORAGE_API_UNSUPPORTED_WARNED.compareAndSet(false, true)) {
			logger.warn("useStorageApi={} requested, but the BigQuery Storage Read API path is not implemented yet "
					+ "(row decoding is unfinished); using the standard ResultSet instead. "
					+ "Set useStorageApi=false to silence this warning.", useStorageApiSetting);
		}

		return new BQResultSet((BQStatement) this, result);
	}

	/**
	 * Returns the log message prefix for this statement type. Used to differentiate
	 * log messages between Statement and PreparedStatement.
	 *
	 * @return log message prefix (e.g., "Query" or "Prepared query")
	 */
	protected abstract String getLogPrefix();

	/**
	 * Runs a dry-run query to estimate cost without executing.
	 *
	 * @param sql
	 *            the SQL query to estimate
	 * @return the cost estimate
	 * @throws SQLException
	 *             if dry-run fails
	 */
	/**
	 * Attaches a cost estimate as a {@link SQLWarning} when
	 * {@code enableQueryCostEstimation} is on, for any statement — SELECT or DML.
	 *
	 * <p>
	 * This used to live inline in {@code executeQueryInternal}, which is why DML
	 * and batches were never estimated (#140): the property was read in exactly one
	 * place, on the query path.
	 *
	 * <p>
	 * A failed dry-run is logged and swallowed. An estimate is advisory, so it must
	 * never be the reason a statement does not run.
	 *
	 * @param sql
	 *            the statement about to be executed
	 * @param positionalParameters
	 *            parameters to bind for the estimate, or null to let
	 *            {@link #buildQueryConfig(String)} supply them. Batches pass their
	 *            chunk's parameters explicitly, because the instance parameters
	 *            belong to a single row and would not match the chunk's SQL.
	 */
	protected void estimateCostIfEnabled(String sql, java.util.List<QueryParameterValue> positionalParameters) {
		if (!properties.enableQueryCostEstimation()) {
			return;
		}
		try {
			QueryCostEstimate estimate = positionalParameters == null
					? runDryRun(sql)
					: runDryRun(sql, positionalParameters);
			String message = estimate.formatWarning();

			// Chain rather than overwrite: a batch produces one estimate per chunk,
			// and getWarnings() is specified to return a chain.
			SQLWarning warning = new SQLWarning(message, "01000", estimate.getMegabytes());
			if (queryWarnings == null) {
				queryWarnings = warning;
			} else {
				queryWarnings.setNextWarning(warning);
			}

			logger.debug("Dry-run estimate: {}", message);
		} catch (Exception e) {
			// Logged with the throwable, not just its message: an NPE here used to
			// surface as "Dry-run estimation failed: null", which said nothing about
			// where it came from.
			logger.warn("Dry-run estimation failed", e);
		}
	}

	/**
	 * Dry-run for a statement whose parameters are supplied explicitly rather than
	 * taken from {@link #buildQueryConfig(String)}.
	 *
	 * @param sql
	 *            the statement to estimate
	 * @param positionalParameters
	 *            the parameters to bind
	 * @return the estimate
	 * @throws SQLException
	 *             if the dry-run fails
	 */
	protected QueryCostEstimate runDryRun(String sql, java.util.List<QueryParameterValue> positionalParameters)
			throws SQLException {
		QueryJobConfiguration.Builder builder = QueryJobConfiguration.newBuilder(sql)
				.setUseLegacySql(properties.useLegacySql()).setDryRun(true).setUseQueryCache(false);
		if (positionalParameters != null && !positionalParameters.isEmpty()) {
			builder.setPositionalParameters(positionalParameters);
		}
		if (properties.getDatasetId() != null) {
			builder.setDefaultDataset(properties.getDatasetId());
		}
		return dryRunEstimate(builder.build());
	}

	protected QueryCostEstimate runDryRun(String sql) throws SQLException {
		QueryJobConfiguration.Builder builder = buildQueryConfig(sql).setDryRun(true).setUseQueryCache(false);
		if (properties.getDatasetId() != null) {
			builder.setDefaultDataset(properties.getDatasetId());
		}
		return dryRunEstimate(builder.build());
	}

	/**
	 * Submits a dry-run job and reads the estimate off its statistics. Shared by
	 * both {@code runDryRun} overloads, which differ only in how the configuration
	 * is built.
	 *
	 * @param dryRunConfig
	 *            a configuration with {@code dryRun} already set
	 * @return the estimate
	 * @throws SQLException
	 *             if BigQuery rejects the dry-run
	 */
	private QueryCostEstimate dryRunEstimate(QueryJobConfiguration dryRunConfig) throws SQLException {
		try {
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
	 * ResultSet before creating new one. Publishes currentJob for cancel
	 * operations.
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

		discardPreviousResult();

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
					logger.debug("IS cache hit [schema={}]: {}", match != null ? match.rawName() : "?", cacheKey);
					currentResultSet = cached.get();
					return currentResultSet;
				}
				// Log unexpected misses at INFO so new IDE query patterns are visible in logs.
				if (!cache.getKnownSchemas().isEmpty()) {
					logger.debug("IS cache miss (new pattern?): {}", cacheKey);
				}
			}
		}

		// Estimate cost before running, when enabled. buildQueryConfig is overridden
		// by BQPreparedStatement to attach the parameters already set, so a
		// parameterized query estimates with them.
		estimateCostIfEnabled(sql, null);

		// fetchSize is a paging hint, applied when the results are read (see
		// QueryResultsOption.pageSize below). It deliberately is NOT mapped to
		// QueryJobConfiguration#setMaxResults: that property is only honoured on the
		// fast-query (jobs.query) path, which this driver never takes because it always
		// inserts an explicit job, so setting it here had no effect at all.
		// maxRows remains enforced at the ResultSet level, not at query level.
		final int effectiveFetchSize = getEffectiveFetchSize();

		QueryJobConfiguration queryConfig = applyConnectionConfig(buildQueryConfig(sql)).build();

		CompletableFuture<JobResultPair> future = CompletableFuture.supplyAsync(() -> {
			Job job = runJob(queryConfig, "Query");
			try {
				TableResult result = effectiveFetchSize > 0
						? job.getQueryResults(BigQuery.QueryResultsOption.pageSize(effectiveFetchSize))
						: job.getQueryResults();
				return new JobResultPair(job, result);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new QueryJobFailure("Query interrupted", BQSQLException.SQLSTATE_OPERATION_CANCELED, e);
			}
		}, QUERY_EXECUTOR);

		JobResultPair pair = awaitWithTimeout(future);

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
					// Serve the materialised copy that put() hands back, so the cursor
					// state is independent of TableResult. Reading it back with get()
					// instead would race: caching drains the TableResult, so an eviction
					// between the write and the read left the fallback below wrapping a
					// consumed result. An empty return means nothing was cached and
					// pair.result was not touched, so the fallback is safe.
					Optional<ResultSet> cached = cache.put(cacheKey, pair.result);
					// Adaptive pre-warming: propagate this IS query pattern to all other
					// known schemas in the background so IntelliJ's next introspection pass
					// finds warm cache entries instead of firing live BigQuery jobs.
					triggerSpeculativePreWarm(sql, cache);
					currentResultSet = cached.orElseGet(() -> createResultSet(pair.result, pair.job));
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
	 * Awaits an async query job future, bounded by the statement query timeout or,
	 * when none is set, the connection default. On timeout the running BigQuery job
	 * is cancelled. ExecutionExceptions are unwrapped to expose the root cause (the
	 * wrapper adds no diagnostic value).
	 *
	 * <p>
	 * An effective timeout of zero or less means wait indefinitely, the contract
	 * documented for {@code timeout=0}. It previously reached
	 * {@code future.get(0, SECONDS)}, which times out at once unless the job has
	 * already finished, so {@code timeout=0} failed nearly every query with "Query
	 * timeout after 0 seconds" — the exact opposite of what it promises.
	 *
	 * @param future
	 *            the future to await
	 * @return the future's result
	 * @throws SQLException
	 *             if the wait times out, is interrupted, or the job fails
	 */
	@SuppressWarnings("PMD.PreserveStackTrace") // ExecutionException is deliberately unwrapped: its cause is the
												// real failure and is passed through
	private <T> T awaitWithTimeout(CompletableFuture<T> future) throws SQLException {
		long timeoutSeconds = queryTimeout > 0 ? queryTimeout : properties.timeoutSeconds();
		try {
			return timeoutSeconds > 0 ? future.get(timeoutSeconds, TimeUnit.SECONDS) : future.get();
		} catch (TimeoutException e) {
			// Cancel the future to interrupt the virtual thread
			future.cancel(true);

			// Read once into a local so the null check and the cancel below cannot
			// see different values.
			Job jobToCancel = currentJob;

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
			if (cause instanceof QueryJobFailure failure) {
				throw new BQSQLException(failure.getMessage(), failure.getSQLState(), failure);
			}
			if (cause instanceof BigQueryException bqe) {
				throw new BQSQLException(bqe.getMessage(), sqlStateFor(bqe.getError()), bqe);
			}
			if (cause instanceof RuntimeException) {
				throw new BQSQLException(cause.getMessage(), BQSQLException.SQLSTATE_GENERAL_ERROR, cause);
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
	protected long executeDmlInternal(String sql, java.util.List<QueryParameterValue> positionalParameters)
			throws SQLException {
		return executeDmlInternal(sql, positionalParameters, true);
	}

	/**
	 * As {@link #executeDmlInternal(String, java.util.List)}, with control over
	 * cost estimation.
	 *
	 * <p>
	 * Sequential batch paths pass {@code false}. They already run one job per
	 * entry, so estimating each one would double the job count on the most
	 * expensive path in the driver — and a per-entry estimate is not reachable
	 * through {@code Statement.getWarnings()} in any useful way, since
	 * {@code executeBatch} returns before a caller reads warnings per statement.
	 * The collapsed multi-row INSERT path does estimate, because there the chunks
	 * are the jobs: one extra dry-run per chunk, not per row.
	 *
	 * @param sql
	 *            the DML to execute
	 * @param positionalParameters
	 *            parameters to bind, or null
	 * @param estimateCost
	 *            whether to attach a cost estimate when the property is enabled
	 * @return affected row count
	 * @throws SQLException
	 *             if execution fails
	 */
	protected long executeDmlInternal(String sql, java.util.List<QueryParameterValue> positionalParameters,
			boolean estimateCost) throws SQLException {
		checkClosed();
		logger.debug("Executing {} DML: {}", getLogPrefix(), sql);

		discardPreviousResult();

		// #140: DML is estimated too. A wide DELETE or a large INSERT batch is
		// exactly the statement whose cost someone wants to see beforehand.
		if (estimateCost) {
			estimateCostIfEnabled(sql, positionalParameters);
		}

		QueryJobConfiguration.Builder configBuilder = QueryJobConfiguration.newBuilder(sql)
				.setUseLegacySql(properties.useLegacySql());

		if (positionalParameters != null && !positionalParameters.isEmpty()) {
			configBuilder.setPositionalParameters(positionalParameters);
		}

		QueryJobConfiguration queryConfig = applyConnectionConfig(configBuilder).build();

		CompletableFuture<Job> future = CompletableFuture.supplyAsync(() -> runJob(queryConfig, "DML"), QUERY_EXECUTOR);

		Job job = awaitWithTimeout(future);
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
	 * Cancels the currently executing query, if any.
	 */
	@Override
	public void cancel() throws SQLException {
		Job jobToCancel = currentJob;

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
	static IsSchemaMatch extractIsSchema(String sql) {
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
	 * <p>
	 * The {@code INFORMATION_SCHEMA} token is matched case-insensitively, to stay
	 * consistent with {@link #IS_SCHEMA_EXTRACTOR}: a lowercase
	 * {@code information_schema} in the source SQL must still be substituted, or
	 * the SQL comes back unchanged and pre-warming silently does nothing. The
	 * matched token is re-emitted verbatim, because the derived SQL has to
	 * normalise to the same cache key the caller would later produce for that
	 * schema. The dataset name stays case-sensitive, as BigQuery treats it.
	 *
	 * @param sql
	 *            the original SQL
	 * @param from
	 *            the schema name extracted from the original SQL
	 * @param toSchema
	 *            the target schema name to substitute
	 * @return the SQL with the schema name replaced
	 */
	static String substituteSchema(String sql, IsSchemaMatch from, String toSchema) {
		String fromQuoted = from.backtickQuoted() ? "`" + from.rawName() + "`" : from.rawName();
		String toQuoted = from.backtickQuoted() ? "`" + toSchema + "`" : toSchema;
		Pattern reference = Pattern.compile(Pattern.quote("." + fromQuoted + ".") + "((?i:INFORMATION_SCHEMA))");
		return reference.matcher(sql)
				.replaceAll(match -> Matcher.quoteReplacement("." + toQuoted + "." + match.group(1)));
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
	record IsSchemaMatch(String rawName, boolean backtickQuoted) {
	}

	/**
	 * Helper class to hold both Job and TableResult from async execution.
	 */
	private record JobResultPair(Job job, TableResult result) {
	}

	/**
	 * Unchecked carrier for a job failure raised inside the async execution body.
	 * Holds the SQLState that {@link #awaitWithTimeout} should surface, so the
	 * failure reason is decided where it is known rather than guessed from the
	 * message afterwards.
	 */
	private static final class QueryJobFailure extends RuntimeException {

		private static final long serialVersionUID = 1L;

		private final String sqlState;

		QueryJobFailure(String message, String sqlState, Throwable cause) {
			super(message, cause);
			this.sqlState = sqlState;
		}

		String getSQLState() {
			return sqlState;
		}
	}

	/**
	 * Maps a BigQuery error reason to the closest JDBC SQLState.
	 *
	 * @param error
	 *            the BigQuery error, may be null
	 * @return the mapped SQLState, never null
	 */
	static String sqlStateFor(BigQueryError error) {
		String reason = error == null ? null : error.getReason();
		if (reason == null) {
			return BQSQLException.SQLSTATE_GENERAL_ERROR;
		}
		return switch (reason) {
			case "invalidQuery", "invalid" -> BQSQLException.SQLSTATE_SYNTAX_ERROR;
			case "notFound" -> BQSQLException.SQLSTATE_TABLE_NOT_FOUND;
			case "duplicate" -> BQSQLException.SQLSTATE_TABLE_ALREADY_EXISTS;
			case "accessDenied" -> BQSQLException.SQLSTATE_INSUFFICIENT_PRIVILEGE;
			case "quotaExceeded", "rateLimitExceeded", "resourcesExceeded", "responseTooLarge" ->
				BQSQLException.SQLSTATE_INSUFFICIENT_RESOURCES;
			case "stopped" -> BQSQLException.SQLSTATE_OPERATION_CANCELED;
			default -> BQSQLException.SQLSTATE_GENERAL_ERROR;
		};
	}

	/**
	 * Drops the result of the previous execution. Closing the old ResultSet is what
	 * keeps repeated execution on one Statement from leaking, and the counters must
	 * be cleared before the new job so a failure cannot leave stale values visible
	 * through {@code getUpdateCount()} or {@code getWarnings()}.
	 *
	 * @throws SQLException
	 *             if the previous ResultSet fails to close
	 */
	@SuppressWarnings("PMD.NullAssignment")
	private void discardPreviousResult() throws SQLException {
		if (currentResultSet != null) {
			currentResultSet.close();
			currentResultSet = null;
		}
		queryWarnings = null;
		currentUpdateCount = -1L;
	}

	/**
	 * Applies the connection-level settings every job carries: default dataset,
	 * labels, and the session property when a session is active. Entering a session
	 * also opens the transaction if the connection is in manual-commit mode.
	 *
	 * <p>
	 * Returns the builder rather than mutating in place, because
	 * {@link SessionManager#addSessionProperty} may hand back a different instance.
	 *
	 * @param configBuilder
	 *            the job configuration to extend
	 * @return the builder to keep building on
	 * @throws SQLException
	 *             if the deferred {@code BEGIN TRANSACTION} fails
	 */
	private QueryJobConfiguration.Builder applyConnectionConfig(QueryJobConfiguration.Builder configBuilder)
			throws SQLException {
		if (properties.getDatasetId() != null) {
			configBuilder.setDefaultDataset(properties.getDatasetId());
		}
		if (!properties.labels().isEmpty()) {
			configBuilder.setLabels(properties.labels());
		}
		SessionManager sessionManager = connection.getSessionManager();
		if (sessionManager != null && sessionManager.hasSession()) {
			connection.beginTransactionIfNeeded();
			return sessionManager.addSessionProperty(configBuilder);
		}
		return configBuilder;
	}

	/**
	 * Creates and awaits a BigQuery job, publishing it to {@link #currentJob} so
	 * {@code cancel()} can reach it. Shared by the query and DML paths, which
	 * differ only in what they do with the finished job.
	 *
	 * <p>
	 * Runs inside {@link #QUERY_EXECUTOR}, so failures are reported as
	 * {@link QueryJobFailure} — an unchecked carrier that {@link #awaitWithTimeout}
	 * unwraps back into a {@link BQSQLException} with the right SQLState.
	 *
	 * @param config
	 *            the job configuration to run
	 * @param operation
	 *            operation noun used in failure messages, e.g. "Query" or "DML"
	 * @return the completed, successful job
	 */
	private Job runJob(QueryJobConfiguration config, String operation) {
		try {
			Job job = bigquery.create(JobInfo.of(config));

			// currentJob is volatile: this single reference write is already visible
			// to cancel() on another thread. It used to also hold the monitor, which
			// added nothing and implied an invariant that does not exist.
			this.currentJob = job;

			logger.debug("{} job created: {}", getLogPrefix(), job.getJobId());

			job = job.waitFor();

			if (job == null) {
				throw new QueryJobFailure("Job no longer exists", BQSQLException.SQLSTATE_GENERAL_ERROR, null);
			}

			JobStatus status = job.getStatus();
			if (status.getError() != null) {
				BigQueryError error = status.getError();
				throw new QueryJobFailure(operation + " failed (job: " + job.getJobId() + "): " + error.getMessage(),
						sqlStateFor(error), null);
			}

			return job;

		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new QueryJobFailure(operation + " interrupted", BQSQLException.SQLSTATE_OPERATION_CANCELED, e);
		}
	}

}

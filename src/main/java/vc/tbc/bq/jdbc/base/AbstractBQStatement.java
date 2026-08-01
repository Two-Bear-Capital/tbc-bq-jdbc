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
import vc.tbc.bq.jdbc.auth.AccessTokenAuth;
import vc.tbc.bq.jdbc.config.ConnectionProperties;
import vc.tbc.bq.jdbc.config.MetadataCache;
import vc.tbc.bq.jdbc.config.SessionManager;
import vc.tbc.bq.jdbc.exception.BQSQLException;
import vc.tbc.bq.jdbc.exception.ServiceErrorDetail;
import vc.tbc.bq.jdbc.metrics.DriverMetrics;
import vc.tbc.bq.jdbc.storage.ArrowSupport;
import vc.tbc.bq.jdbc.telemetry.DriverTracing;
import vc.tbc.bq.jdbc.telemetry.QuerySpan;
import vc.tbc.bq.jdbc.storage.StorageReadResultSet;
import vc.tbc.bq.jdbc.util.ScriptResults;
import vc.tbc.bq.jdbc.util.QueryCostEstimate;

import java.sql.*;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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

	/**
	 * Cost estimates produced for the current execution, one per dry-run.
	 *
	 * <p>
	 * The typed counterpart to the estimate warnings in {@link #queryWarnings}, and
	 * populated alongside them. A caller that wants the byte counts should read
	 * this rather than parse the warning text; the two carry the same estimates in
	 * the same order.
	 */
	private List<QueryCostEstimate> costEstimates = List.of();

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
	 * When {@code useStorageApi} asks for it and the result qualifies, rows are
	 * streamed via the BigQuery Storage Read API instead of paged over REST — much
	 * faster on large results. That path is strictly optional: anything that makes
	 * it unavailable falls back to the standard {@link BQResultSet}, so a query
	 * always returns rows. The reasons it can be unavailable are all outside the
	 * caller's control, which is why none of them is an error:
	 *
	 * <ul>
	 * <li>Arrow cannot allocate without a JVM flag ({@link ArrowSupport})
	 * <li>the job reported no destination table, e.g. under
	 * {@code JOB_CREATION_OPTIONAL}
	 * <li>the result contains types the Arrow encoder does not cover
	 * <li>opening the read session fails — API disabled, or credentials without
	 * {@code bigquery.readsessions.create}
	 * </ul>
	 *
	 * @param result
	 *            the table result from BigQuery
	 * @param job
	 *            the completed query job (may be null)
	 * @return the JDBC ResultSet
	 */
	protected ResultSet createResultSet(TableResult result, Job job) {
		if (!StorageReadResultSet.shouldUseStorageApi(result, properties.useStorageApi())) {
			return new BQResultSet((BQStatement) this, result);
		}
		if (!ArrowSupport.isUsable()) {
			// ArrowSupport already logged why, once, with the flag to add.
			return new BQResultSet((BQStatement) this, result);
		}

		TableId destination = destinationTableOf(job);
		try {
			return new StorageReadResultSet((BQStatement) this, result, destination);
		} catch (SQLException | RuntimeException e) {
			if (STORAGE_API_UNSUPPORTED_WARNED.compareAndSet(false, true)) {
				logger.warn("Could not use the BigQuery Storage Read API for this query; falling back to the "
						+ "standard result path (this is logged once per JVM). Set useStorageApi=false to stop "
						+ "attempting it. Reason: {}", describeCause(e));
			} else {
				logger.debug("Storage Read API unavailable, using the standard result path", e);
			}
			return new BQResultSet((BQStatement) this, result);
		}
	}

	/**
	 * Flattens an exception chain into one line for the fallback warning.
	 *
	 * <p>
	 * The wrapper message alone is close to useless here — "Failed to open a
	 * BigQuery Storage read session" does not distinguish an API that is not
	 * enabled from credentials missing {@code bigquery.readsessions.create}, and
	 * this warning is the only thing most users will ever see about it, since the
	 * driver deliberately carries on rather than failing. The root cause is the
	 * part that tells them what to fix.
	 *
	 * @param throwable
	 *            the failure to describe
	 * @return the message chain, outermost first
	 */
	private static String describeCause(Throwable throwable) {
		StringBuilder description = new StringBuilder(
				throwable.getMessage() == null ? throwable.getClass().getSimpleName() : throwable.getMessage());
		Throwable cause = throwable.getCause();
		// Bounded so a self-referential chain cannot spin.
		for (int depth = 0; cause != null && depth < 5; depth++) {
			String message = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
			description.append(" <- ").append(message);
			cause = cause.getCause();
		}
		return description.toString();
	}

	/** The anonymous table a completed query wrote its results to, if any. */
	private static TableId destinationTableOf(Job job) {
		if (job == null || !(job.getConfiguration() instanceof QueryJobConfiguration config)) {
			return null;
		}
		return config.getDestinationTable();
	}

	/**
	 * Returns the log message prefix for this statement type. Used to differentiate
	 * log messages between Statement and PreparedStatement.
	 *
	 * @return log message prefix (e.g., "Query" or "Prepared query")
	 */
	protected abstract String getLogPrefix();

	/**
	 * Estimates what a statement would cost, without running it and without
	 * {@code enableQueryCostEstimation} being on.
	 *
	 * <p>
	 * This is the way to price a statement in production. The connection property
	 * dry-runs <em>every</em> statement, doubling the job count, which is why it is
	 * not something to leave enabled — and cost surprises happen in production.
	 * Here the caller decides which statement is worth an extra job.
	 *
	 * <p>
	 * Driver-specific, so reach it through {@link Statement#unwrap}:
	 *
	 * <pre>{@code
	 * var bq = statement.unwrap(AbstractBQStatement.class);
	 * QueryCostEstimate estimate = bq.estimateCost("SELECT * FROM huge_table");
	 * if (estimate.totalBytesBilled() > BUDGET) {
	 * 	throw new IllegalStateException(estimate.formatSummary());
	 * }
	 * }</pre>
	 *
	 * <p>
	 * Unlike the automatic path this throws rather than swallowing: the caller
	 * asked for the estimate, so a failure to produce one is an answer they need.
	 *
	 * @param sql
	 *            the statement to price. Not executed
	 * @return the estimate, with a cost only when {@code queryPricePerTiB} is
	 *         configured
	 * @throws SQLException
	 *             if the statement is closed, or BigQuery rejects the dry-run —
	 *             invalid SQL and missing tables both surface here
	 * @since 3.2.0
	 */
	public QueryCostEstimate estimateCost(String sql) throws SQLException {
		checkClosed();
		return runDryRun(sql);
	}

	/**
	 * The cost estimates produced for the most recent execution, oldest first.
	 *
	 * <p>
	 * Empty unless {@code enableQueryCostEstimation} is on. Usually one entry; a
	 * collapsed batch insert contributes one per chunk, because each chunk is its
	 * own job with its own cost. Cleared when the statement next executes, so read
	 * it before reusing the statement.
	 *
	 * <p>
	 * The typed answer to "what will this cost" — the same estimates are also
	 * available as {@link Statement#getWarnings()} text, which a caller would
	 * otherwise have to parse. Driver-specific, so reach it through
	 * {@link Statement#unwrap}.
	 *
	 * @return the estimates, or an empty list when none were taken
	 * @since 3.2.0
	 */
	public List<QueryCostEstimate> getCostEstimates() {
		return costEstimates;
	}

	/**
	 * Attaches a cost estimate as a {@link SQLWarning}, and records it as a typed
	 * value, when {@code enableQueryCostEstimation} is on — for any statement,
	 * SELECT or DML.
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
	protected void estimateCostIfEnabled(String sql, List<QueryParameterValue> positionalParameters) {
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

			List<QueryCostEstimate> updated = new ArrayList<>(costEstimates);
			updated.add(estimate);
			costEstimates = List.copyOf(updated);

			logger.debug("Dry-run estimate: {}", message);
		} catch (Exception e) {
			// Logged with the throwable, not just its message: an NPE here used to
			// surface as "Dry-run estimation failed: null", which said nothing about
			// where it came from.
			//
			// The access token case is called out because it is the one where the
			// failure is a property of the credential rather than of the statement,
			// and so will repeat for every statement until the setting is changed. A
			// dry run creates a job, which a read-only token has no scope for.
			if (properties.authType() instanceof AccessTokenAuth) {
				logger.warn("Dry-run estimation failed. The connection authenticates with a pre-generated access "
						+ "token; if it is read-only it cannot create the job a dry run needs, and "
						+ "enableQueryCostEstimation will not produce estimates on this connection.", e);
			} else {
				logger.warn("Dry-run estimation failed", e);
			}
		}
	}

	/**
	 * The warnings and cost estimates a single execution produced.
	 *
	 * <p>
	 * Exists so a JDBC call that runs several jobs can accumulate diagnostics
	 * across them. {@code executeBatch} is the only such call: it runs one job per
	 * chunk, and each job clears the previous one's diagnostics, so without
	 * capturing them a caller would see only the last chunk's estimate.
	 *
	 * @param warnings
	 *            the head of the warning chain, or null
	 * @param estimates
	 *            the cost estimates, oldest first
	 */
	protected record Diagnostics(SQLWarning warnings, List<QueryCostEstimate> estimates) {

		/** Diagnostics from a run that produced none. */
		public static final Diagnostics NONE = new Diagnostics(null, List.of());

		/**
		 * Appends another run's diagnostics to these.
		 *
		 * @param next
		 *            the later run's diagnostics
		 * @return the combined diagnostics
		 */
		public Diagnostics andThen(Diagnostics next) {
			SQLWarning combined = warnings;
			if (combined == null) {
				combined = next.warnings;
			} else if (next.warnings != null) {
				SQLWarning tail = combined;
				while (tail.getNextWarning() != null) {
					tail = tail.getNextWarning();
				}
				tail.setNextWarning(next.warnings);
			}
			List<QueryCostEstimate> merged = new ArrayList<>(estimates);
			merged.addAll(next.estimates);
			return new Diagnostics(combined, List.copyOf(merged));
		}
	}

	/**
	 * Reads off the diagnostics of the execution that just finished.
	 *
	 * @return the current warnings and cost estimates
	 */
	protected Diagnostics currentDiagnostics() {
		return new Diagnostics(queryWarnings, costEstimates);
	}

	/**
	 * Replaces the statement's diagnostics with an accumulated set, so a multi-job
	 * JDBC call reports every job's estimate rather than the last one's.
	 *
	 * @param diagnostics
	 *            the diagnostics to publish
	 */
	protected void publishDiagnostics(Diagnostics diagnostics) {
		queryWarnings = diagnostics.warnings();
		costEstimates = diagnostics.estimates();
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
	protected QueryCostEstimate runDryRun(String sql, List<QueryParameterValue> positionalParameters)
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

			// The rate is the connection's, not the driver's: unset means the estimate
			// carries bytes and no money, which is the honest answer for a customer
			// whose contract the driver cannot see.
			return QueryCostEstimate.of(bytesProcessed, estimatedBytes, bytesBilled, properties.queryPricePerTiB());

		} catch (BigQueryException e) {
			throw new BQSQLException("Dry-run failed: " + e.getMessage(), sqlStateFor(e), e);
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

		// A script's parent job carries only its last statement's result, so
		// returning pair.result would answer the wrong statement and leave the rest
		// unreachable. Step onto the first one instead; getMoreResults() walks the
		// remainder.
		if (ScriptResults.isScript(pair.job)) {
			ResultSet first = beginScript(pair.job);
			if (first != null) {
				return first;
			}
			// The script's first statement produced no rows (a DDL or DML opener).
			// executeQuery must still hand back a ResultSet, so the parent's empty
			// one stands in and getMoreResults() carries on from statement one.
			currentResultSet = createResultSet(pair.result, pair.job);
			return currentResultSet;
		}

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
			throw new BQSQLException("Query execution failed: " + e.getMessage(), sqlStateFor(e), e);
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
				throw new BQSQLException(bqe.getMessage(), sqlStateFor(bqe), bqe);
			}
			if (cause instanceof RuntimeException) {
				throw new BQSQLException(cause.getMessage(), BQSQLException.SQLSTATE_GENERAL_ERROR, cause);
			}
			throw new BQSQLException("Query execution failed: " + cause.getMessage(), cause);
		} catch (BigQueryException e) {
			throw new BQSQLException("Query execution failed: " + e.getMessage(), sqlStateFor(e), e);
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
	protected long executeDmlInternal(String sql, List<QueryParameterValue> positionalParameters) throws SQLException {
		return executeDmlInternal(sql, positionalParameters, true);
	}

	/**
	 * As {@link #executeDmlInternal(String, List)}, with control over cost
	 * estimation.
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
	protected long executeDmlInternal(String sql, List<QueryParameterValue> positionalParameters, boolean estimateCost)
			throws SQLException {
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

	/**
	 * The statements of the script currently being walked, or null when the last
	 * execution was a single statement.
	 */
	private ScriptResults scriptResults;

	/**
	 * Positions on a script's first statement.
	 *
	 * @param parent
	 *            the completed script job
	 * @return the first statement's ResultSet, or null when it produced none
	 */
	// Clearing scriptResults is what puts the statement back on the single-result
	// path; a sentinel cursor object would be a second way to mean "no script".
	@SuppressWarnings("PMD.NullAssignment")
	private ResultSet beginScript(Job parent) throws SQLException {
		scriptResults = ScriptResults.of(connection.getBigQuery(), parent);
		logger.debug("{} ran a script of {} statement(s)", getLogPrefix(), scriptResults.size());
		if (!scriptResults.advance()) {
			// A SCRIPT whose children could not be listed. Falling back to the
			// parent's own result keeps the caller no worse off than before.
			scriptResults = null;
			return null;
		}
		return applyCurrentScriptStatement();
	}

	/**
	 * Makes the cursor's statement the statement's current result.
	 *
	 * @return its ResultSet, or null when it produced an update count instead
	 */
	@SuppressWarnings("PMD.NullAssignment") // getResultSet() must return null for a non-ResultSet step
	private ResultSet applyCurrentScriptStatement() throws SQLException {
		Job statement = scriptResults.current();
		currentUpdateCount = readDmlAffectedRows(statement);

		if (!ScriptResults.producesResultSet(statement)) {
			currentResultSet = null;
			// A DDL statement reports neither rows nor affected rows. JDBC has no
			// third answer, and 0 is the conventional one — -1 is reserved for "no
			// more results", which would end the walk at the first CREATE.
			if (currentUpdateCount < 0) {
				currentUpdateCount = 0L;
			}
			// Deliberately no result fetch: a non-SELECT statement has nothing to
			// read, so this also saves an API call per DDL or DML step.
			return null;
		}

		try {
			int fetchSize = getEffectiveFetchSize();
			TableResult result = fetchSize > 0
					? statement.getQueryResults(BigQuery.QueryResultsOption.pageSize(fetchSize))
					: statement.getQueryResults();
			currentResultSet = createResultSet(result, statement);
			return currentResultSet;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new BQSQLException("Interrupted reading script statement results",
					BQSQLException.SQLSTATE_OPERATION_CANCELED, e);
		} catch (BigQueryException e) {
			throw new BQSQLException("Could not read a script statement's results", e);
		}
	}

	@Override
	public boolean getMoreResults() throws SQLException {
		return getMoreResults(CLOSE_CURRENT_RESULT);
	}

	/**
	 * Moves to the next result of a multi-statement script.
	 *
	 * <p>
	 * For a single statement there is never a second result, so this reports none
	 * and leaves {@code getUpdateCount()} at -1, as the contract requires.
	 *
	 * @param current
	 *            one of {@link java.sql.Statement#CLOSE_CURRENT_RESULT},
	 *            {@link java.sql.Statement#KEEP_CURRENT_RESULT} or
	 *            {@link java.sql.Statement#CLOSE_ALL_RESULTS}
	 * @return true when the next result is a ResultSet
	 * @throws SQLException
	 *             if the statement is closed, {@code current} is not one of the
	 *             three constants, or the next result cannot be read
	 */
	@Override
	@SuppressWarnings("PMD.NullAssignment") // getResultSet() must return null once results are exhausted
	public boolean getMoreResults(int current) throws SQLException {
		checkClosed();

		if (current != CLOSE_CURRENT_RESULT && current != KEEP_CURRENT_RESULT && current != CLOSE_ALL_RESULTS) {
			throw new BQSQLException("Invalid argument to getMoreResults: " + current,
					BQSQLException.SQLSTATE_INVALID_PARAMETER_VALUE);
		}

		// KEEP_CURRENT_RESULT is the only one that does not close, and the driver
		// holds one ResultSet at a time, so CLOSE_ALL_RESULTS and
		// CLOSE_CURRENT_RESULT do the same thing here rather than differing in a way
		// that is not observable.
		if (current != KEEP_CURRENT_RESULT && currentResultSet != null) {
			currentResultSet.close();
		}
		currentResultSet = null;

		if (scriptResults == null || !scriptResults.advance()) {
			// Per the JDBC contract, once there are no more results
			// getUpdateCount() must report -1.
			scriptResults = null;
			currentUpdateCount = -1L;
			return false;
		}

		return applyCurrentScriptStatement() != null;
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
	 * SQLState for a {@link BigQueryException}, including the client-side failures
	 * that never reached BigQuery.
	 *
	 * <p>
	 * BigQuery's own error reason decides whenever there is one — that is what
	 * keeps a 403 on a table reported as {@code 42501} rather than as an
	 * authentication failure. Only when there is no reason at all, which is the
	 * signature of a credential that could not be minted or refreshed, does the
	 * cause chain get a say.
	 *
	 * <p>
	 * The distinction is not cosmetic. Connection pools and BI tools branch on the
	 * SQLState class: {@code 28} means "invalid authorization specification" and
	 * triggers a re-authenticate, where {@code HY000} says only that something went
	 * wrong — so an expired or ungranted credential was retried to the ceiling
	 * instead of being reported.
	 *
	 * @param exception
	 *            the failure to classify
	 * @return the SQLState to report
	 */
	static String sqlStateFor(BigQueryException exception) {
		String state = sqlStateFor(exception == null ? null : exception.getError());
		if (BQSQLException.SQLSTATE_GENERAL_ERROR.equals(state)
				&& ServiceErrorDetail.isAuthenticationFailure(exception)) {
			return BQSQLException.SQLSTATE_AUTH_FAILED;
		}
		return state;
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
		// A cursor left over from a previous script would let getMoreResults()
		// keep walking that script's statements after an unrelated execution.
		scriptResults = null;
		queryWarnings = null;
		costEstimates = List.of();
		currentUpdateCount = -1L;
	}

	/**
	 * Applies the connection-level settings every job carries: default dataset,
	 * labels, the billing ceiling, and the session property when a session is
	 * active. Entering a session also opens the transaction if the connection is in
	 * manual-commit mode.
	 *
	 * <p>
	 * This is the single point where connection-level job settings are applied. The
	 * query and DML paths both route through it, so a setting added here reaches
	 * every job the driver submits rather than only the path it was written for.
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
		// BigQuery fails the job outright when the estimate exceeds this, before any
		// bytes are billed, so it is a spend ceiling rather than a hint. Applied here
		// so it covers DML and batch-rewritten INSERTs, not just SELECT.
		if (properties.maxBillingBytes() != null) {
			configBuilder.setMaximumBytesBilled(properties.maxBillingBytes());
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
		// Every query and DML job the driver runs passes through here, which makes it
		// the one place worth timing: instrumenting the callers instead would mean
		// several sites to keep in step, and would still miss whichever one was added
		// next. See DriverMetrics.
		long startNanos = System.nanoTime();
		boolean succeeded = false;
		String failureSqlState = null;
		Throwable failure = null;

		// Counted before the call, not after it returns: this is what makes
		// queriesInFlight() meaningful. Incrementing it alongside the terminal
		// counters would make it their sum by construction and in-flight always zero.
		DriverMetrics.recordQuerySubmitted();

		// The span sits alongside the counters and at the same choke point, for the
		// one thing they cannot express: which BigQuery job made this request slow.
		// No-op unless the host supplied the OpenTelemetry API and registered an SDK.
		try (QuerySpan span = DriverTracing.start("BigQuery." + operation.toLowerCase(Locale.ROOT),
				properties.enableTracing())) {
			span.setAttribute("db.system.name", "bigquery");
			span.setAttribute("db.namespace", properties.projectId());
			span.setAttribute("db.operation.name", operation);
			try {
				// No JobId is supplied, deliberately. BigQuery job ids are location-scoped
				// and the client builds one that carries the connection's location:
				// BigQueryImpl.create() generates JobId.of().setLocation(options.location),
				// and getJob/cancel fall back to that same location for an id without one.
				// Passing our own id would take this off the library's random-id path,
				// which is what lets a create RPC that fails after BigQuery accepted the
				// job recover by fetching it rather than reporting an error for a job that
				// is running and billing. RealCrossRegionJobTest holds the round-trip.
				Job job = bigquery.create(JobInfo.of(config));

				// currentJob is volatile: this single reference write is already visible
				// to cancel() on another thread. It used to also hold the monitor, which
				// added nothing and implied an invariant that does not exist.
				this.currentJob = job;

				logger.debug("{} job created: {}", getLogPrefix(), job.getJobId());
				// The attribute the whole span exists for: it is what turns a slow
				// application request into a job someone can look up in the console or in
				// INFORMATION_SCHEMA.JOBS.
				//
				// Null-guarded because getJobId() can be null, which the debug line above
				// tolerates by formatting and this would not. Telemetry must never be the
				// reason a query fails.
				if (job.getJobId() != null) {
					span.setAttribute("bigquery.job_id", job.getJobId().getJob());
				}

				job = job.waitFor();

				if (job == null) {
					throw new QueryJobFailure("Job no longer exists", BQSQLException.SQLSTATE_GENERAL_ERROR, null);
				}

				JobStatus status = job.getStatus();
				if (status.getError() != null) {
					BigQueryError error = status.getError();
					throw new QueryJobFailure(
							operation + " failed (job: " + job.getJobId() + "): " + error.getMessage(),
							sqlStateFor(error), null);
				}

				succeeded = true;
				return job;

			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				failureSqlState = BQSQLException.SQLSTATE_OPERATION_CANCELED;
				failure = e;
				throw new QueryJobFailure(operation + " interrupted", BQSQLException.SQLSTATE_OPERATION_CANCELED, e);
			} catch (QueryJobFailure e) {
				// Caught only to give the span the SQLState the caller will see, then
				// rethrown unchanged. Classification stays where it was.
				failureSqlState = e.getSQLState();
				failure = e;
				throw e;
			} catch (RuntimeException e) {
				failure = e;
				throw e;
			} finally {
				// In a finally block so a job that fails, times out or is cancelled is
				// counted too. A workload whose queries fail slowly is precisely the shape
				// worth being able to see, and recording only the successes would hide it.
				long elapsedNanos = System.nanoTime() - startNanos;
				if (succeeded) {
					DriverMetrics.recordQuerySucceeded(elapsedNanos);
				} else {
					DriverMetrics.recordQueryFailed(elapsedNanos);
					span.recordFailure(failureSqlState, failure);
				}
				logger.debug("{} {} {} in {} ms", getLogPrefix(), operation, succeeded ? "completed" : "failed",
						TimeUnit.NANOSECONDS.toMillis(elapsedNanos));
			}
		}
	}

}

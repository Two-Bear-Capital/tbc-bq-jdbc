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
package vc.tbc.bq.jdbc.metadata;

import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.Dataset;
import com.google.cloud.bigquery.DatasetId;
import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.FieldValue;
import com.google.cloud.bigquery.FieldValueList;
import com.google.cloud.bigquery.JobInfo;
import com.google.cloud.bigquery.JobStatistics;
import com.google.cloud.bigquery.MaterializedViewDefinition;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.Schema;
import com.google.cloud.bigquery.StandardSQLTypeName;
import com.google.cloud.bigquery.Table;
import com.google.cloud.bigquery.TableDefinition;
import com.google.cloud.bigquery.TableResult;
import com.google.cloud.bigquery.ViewDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vc.tbc.bq.jdbc.BQConnection;
import vc.tbc.bq.jdbc.DriverVersion;
import vc.tbc.bq.jdbc.TypeMapper;
import vc.tbc.bq.jdbc.base.BaseJdbcWrapper;
import vc.tbc.bq.jdbc.config.ConnectionProperties;
import vc.tbc.bq.jdbc.config.MetadataCache;
import vc.tbc.bq.jdbc.exception.BQSQLException;
import vc.tbc.bq.jdbc.exception.BQSQLFeatureNotSupportedException;
import vc.tbc.bq.jdbc.util.BigQueryIdentifiers;
import vc.tbc.bq.jdbc.util.SqlStringLiterals;

import java.sql.*;
import java.util.Locale;

/**
 * JDBC DatabaseMetaData implementation for BigQuery.
 *
 * <p>
 * This implementation provides metadata about the BigQuery database and its
 * capabilities.
 *
 * @since 1.0.0
 */
public class BQDatabaseMetaData extends BaseJdbcWrapper implements DatabaseMetaData {

	private static final Logger logger = LoggerFactory.getLogger(BQDatabaseMetaData.class);

	/**
	 * Ceiling on INFORMATION_SCHEMA queries issued at once when loading metadata
	 * across datasets. Virtual threads make the tasks themselves cheap, but each
	 * one is a BigQuery query, and BigQuery caps concurrent queries per project.
	 */
	private static final int MAX_CONCURRENT_METADATA_QUERIES = 16;
	private static final int STATS_LOG_INTERVAL = 10; // Log stats every N cache operations

	/**
	 * Static cache shared across all connections to the same project. Cache key
	 * format: "projectId:ttlSeconds" This allows the cache to persist across
	 * connection open/close cycles, which is critical for IntelliJ IDEA that
	 * frequently reopens connections.
	 */
	private static final java.util.concurrent.ConcurrentHashMap<String, MetadataCache> SHARED_CACHES = new java.util.concurrent.ConcurrentHashMap<>();

	private final BQConnection connection;
	private final MetadataCache cache;
	private final String cacheKey;
	private int cacheHits = 0;
	private int cacheMisses = 0;

	/**
	 * Creates a new BigQuery DatabaseMetaData.
	 *
	 * @param connection
	 *            the connection
	 */
	public BQDatabaseMetaData(BQConnection connection) {
		this.connection = connection;
		ConnectionProperties properties = connection.getProperties();

		// Initialize cache if enabled
		if (properties.metadataCacheEnabled()) {
			// Cache key covers every setting that changes the cache's behaviour, not
			// just the project. Caches are shared statically and created once, so a
			// key that omitted the row ceiling would hand a connection asking for one
			// bound a cache already built with another - first caller silently wins.
			this.cacheKey = properties.projectId() + ":" + properties.metadataCacheTtl() + ":"
					+ properties.metadataCacheMaxRows();
			this.cache = getOrCreateSharedCache(cacheKey, java.time.Duration.ofSeconds(properties.metadataCacheTtl()),
					properties.projectId(), properties.metadataCacheMaxRows());
			logger.debug("Using shared metadata cache for project: {} (cache instances: {})", properties.projectId(),
					SHARED_CACHES.size());
		} else {
			this.cache = null;
			this.cacheKey = null;
			logger.debug("Metadata cache disabled");
		}
	}

	@Override
	public boolean allProceduresAreCallable() throws SQLException {
		return false;
	}

	@Override
	public boolean allTablesAreSelectable() throws SQLException {
		return true;
	}

	@Override
	public String getURL() throws SQLException {
		return "jdbc:bigquery://" + connection.getProperties().projectId();
	}

	@Override
	public String getUserName() throws SQLException {
		// BigQuery doesn't have a traditional username concept
		// Return null as per JDBC spec when username is not applicable
		return null;
	}

	@Override
	public boolean isReadOnly() throws SQLException {
		return connection.isReadOnly();
	}

	@Override
	public boolean nullsAreSortedHigh() throws SQLException {
		return false;
	}

	@Override
	public boolean nullsAreSortedLow() throws SQLException {
		return true;
	}

	@Override
	public boolean nullsAreSortedAtStart() throws SQLException {
		return false;
	}

	@Override
	public boolean nullsAreSortedAtEnd() throws SQLException {
		return false;
	}

	@Override
	public String getDatabaseProductName() throws SQLException {
		// Use a distinct name to avoid IntelliJ's built-in BigQuery dialect
		// which has bugs in its introspector (null helper NPE)
		// IntelliJ will use a generic SQL dialect instead, which works better
		return "BigQuery (TBC Driver)";
	}

	@Override
	public String getDatabaseProductVersion() throws SQLException {
		return "2.0";
	}

	@Override
	public String getDriverName() throws SQLException {
		return "Two Bear Capital BigQuery JDBC Driver";
	}

	@Override
	public String getDriverVersion() throws SQLException {
		return DriverVersion.getVersionString();
	}

	@Override
	public int getDriverMajorVersion() {
		return DriverVersion.getMajorVersion();
	}

	@Override
	public int getDriverMinorVersion() {
		return DriverVersion.getMinorVersion();
	}

	@Override
	public boolean usesLocalFiles() throws SQLException {
		return false;
	}

	@Override
	public boolean usesLocalFilePerTable() throws SQLException {
		return false;
	}

	@Override
	public boolean supportsMixedCaseIdentifiers() throws SQLException {
		return false;
	}

	@Override
	public boolean storesUpperCaseIdentifiers() throws SQLException {
		return false;
	}

	@Override
	public boolean storesLowerCaseIdentifiers() throws SQLException {
		return false;
	}

	@Override
	public boolean storesMixedCaseIdentifiers() throws SQLException {
		return true;
	}

	@Override
	public boolean supportsMixedCaseQuotedIdentifiers() throws SQLException {
		return true;
	}

	@Override
	public boolean storesUpperCaseQuotedIdentifiers() throws SQLException {
		return false;
	}

	@Override
	public boolean storesLowerCaseQuotedIdentifiers() throws SQLException {
		return false;
	}

	@Override
	public boolean storesMixedCaseQuotedIdentifiers() throws SQLException {
		return true;
	}

	@Override
	public String getIdentifierQuoteString() throws SQLException {
		return "`";
	}

	@Override
	public String getSQLKeywords() throws SQLException {
		return "STRUCT,ARRAY,UNNEST,CROSS,APPLY";
	}

	@Override
	public String getNumericFunctions() throws SQLException {
		return "ABS,ACOS,ACOSH,ASIN,ASINH,ATAN,ATAN2,ATANH,CEIL,CEILING,COS,COSH,COT,COTH,CSC,CSCH,DIV,EXP,FLOOR,LN,LOG,LOG10,MOD,PI,POW,POWER,ROUND,SAFE_DIVIDE,SEC,SECH,SIGN,SIN,SINH,SQRT,TAN,TANH,TRUNC";
	}

	@Override
	public String getStringFunctions() throws SQLException {
		return "CONCAT,CONTAINS_SUBSTR,ENDS_WITH,FORMAT,FROM_BASE32,FROM_BASE64,FROM_HEX,LENGTH,LOWER,LPAD,LTRIM,NORMALIZE,NORMALIZE_AND_CASEFOLD,REGEXP_CONTAINS,REGEXP_EXTRACT,REGEXP_EXTRACT_ALL,REGEXP_REPLACE,REPEAT,REPLACE,REVERSE,RPAD,RTRIM,SAFE_CONVERT_BYTES_TO_STRING,SPLIT,STARTS_WITH,STRPOS,SUBSTR,TO_BASE32,TO_BASE64,TO_CODE_POINTS,TO_HEX,TRIM,UPPER";
	}

	@Override
	public String getSystemFunctions() throws SQLException {
		return "CURRENT_DATE,CURRENT_DATETIME,CURRENT_TIME,CURRENT_TIMESTAMP";
	}

	@Override
	public String getTimeDateFunctions() throws SQLException {
		return "DATE,DATETIME,TIME,TIMESTAMP,DATE_ADD,DATE_SUB,DATE_DIFF,DATE_TRUNC,DATETIME_ADD,DATETIME_SUB,DATETIME_DIFF,DATETIME_TRUNC,TIME_ADD,TIME_SUB,TIME_DIFF,TIME_TRUNC,TIMESTAMP_ADD,TIMESTAMP_SUB,TIMESTAMP_DIFF,TIMESTAMP_TRUNC,FORMAT_DATE,FORMAT_DATETIME,FORMAT_TIME,FORMAT_TIMESTAMP,PARSE_DATE,PARSE_DATETIME,PARSE_TIME,PARSE_TIMESTAMP,EXTRACT";
	}

	@Override
	public String getSearchStringEscape() throws SQLException {
		return "\\";
	}

	@Override
	public String getExtraNameCharacters() throws SQLException {
		return "";
	}

	@Override
	public boolean supportsAlterTableWithAddColumn() throws SQLException {
		return true;
	}

	@Override
	public boolean supportsAlterTableWithDropColumn() throws SQLException {
		return true;
	}

	@Override
	public boolean supportsColumnAliasing() throws SQLException {
		return true;
	}

	@Override
	public boolean nullPlusNonNullIsNull() throws SQLException {
		return true;
	}

	@Override
	public boolean supportsConvert() throws SQLException {
		return true;
	}

	@Override
	public boolean supportsConvert(int fromType, int toType) throws SQLException {
		return false;
	}

	@Override
	public boolean supportsTableCorrelationNames() throws SQLException {
		return true;
	}

	@Override
	public boolean supportsDifferentTableCorrelationNames() throws SQLException {
		return false;
	}

	@Override
	public boolean supportsExpressionsInOrderBy() throws SQLException {
		return true;
	}

	@Override
	public boolean supportsOrderByUnrelated() throws SQLException {
		return true;
	}

	@Override
	public boolean supportsGroupBy() throws SQLException {
		return true;
	}

	@Override
	public boolean supportsGroupByUnrelated() throws SQLException {
		return true;
	}

	@Override
	public boolean supportsGroupByBeyondSelect() throws SQLException {
		return true;
	}

	@Override
	public boolean supportsLikeEscapeClause() throws SQLException {
		return true;
	}

	@Override
	public boolean supportsMultipleResultSets() throws SQLException {
		return false;
	}

	@Override
	public boolean supportsMultipleTransactions() throws SQLException {
		return false;
	}

	@Override
	public boolean supportsNonNullableColumns() throws SQLException {
		return true;
	}

	@Override
	public boolean supportsMinimumSQLGrammar() throws SQLException {
		return true;
	}

	@Override
	public boolean supportsCoreSQLGrammar() throws SQLException {
		return true;
	}

	@Override
	public boolean supportsExtendedSQLGrammar() throws SQLException {
		return false;
	}

	@Override
	public boolean supportsANSI92EntryLevelSQL() throws SQLException {
		return true;
	}

	@Override
	public boolean supportsANSI92IntermediateSQL() throws SQLException {
		return false;
	}

	@Override
	public boolean supportsANSI92FullSQL() throws SQLException {
		return false;
	}

	@Override
	public boolean supportsIntegrityEnhancementFacility() throws SQLException {
		return false;
	}

	@Override
	public boolean supportsOuterJoins() throws SQLException {
		return true;
	}

	@Override
	public boolean supportsFullOuterJoins() throws SQLException {
		return true;
	}

	@Override
	public boolean supportsLimitedOuterJoins() throws SQLException {
		return true;
	}

	@Override
	public String getSchemaTerm() throws SQLException {
		return "dataset";
	}

	@Override
	public String getProcedureTerm() throws SQLException {
		return "procedure";
	}

	@Override
	public String getCatalogTerm() throws SQLException {
		return "project";
	}

	@Override
	public boolean isCatalogAtStart() throws SQLException {
		return true;
	}

	@Override
	public String getCatalogSeparator() throws SQLException {
		return ".";
	}

	@Override
	public boolean supportsSchemasInDataManipulation() throws SQLException {
		return true;
	}

	@Override
	public boolean supportsSchemasInProcedureCalls() throws SQLException {
		return false;
	}

	@Override
	public boolean supportsSchemasInTableDefinitions() throws SQLException {
		return true;
	}

	@Override
	public boolean supportsSchemasInIndexDefinitions() throws SQLException {
		return false;
	}

	@Override
	public boolean supportsSchemasInPrivilegeDefinitions() throws SQLException {
		return false;
	}

	@Override
	public boolean supportsCatalogsInDataManipulation() throws SQLException {
		return true;
	}

	@Override
	public boolean supportsCatalogsInProcedureCalls() throws SQLException {
		return false;
	}

	@Override
	public boolean supportsCatalogsInTableDefinitions() throws SQLException {
		return true;
	}

	@Override
	public boolean supportsCatalogsInIndexDefinitions() throws SQLException {
		return false;
	}

	@Override
	public boolean supportsCatalogsInPrivilegeDefinitions() throws SQLException {
		return false;
	}

	@Override
	public boolean supportsPositionedDelete() throws SQLException {
		return false;
	}

	@Override
	public boolean supportsPositionedUpdate() throws SQLException {
		return false;
	}

	@Override
	public boolean supportsSelectForUpdate() throws SQLException {
		return false;
	}

	@Override
	public boolean supportsStoredProcedures() throws SQLException {
		return true;
	}

	@Override
	public boolean supportsSubqueriesInComparisons() throws SQLException {
		return true;
	}

	@Override
	public boolean supportsSubqueriesInExists() throws SQLException {
		return true;
	}

	@Override
	public boolean supportsSubqueriesInIns() throws SQLException {
		return true;
	}

	@Override
	public boolean supportsSubqueriesInQuantifieds() throws SQLException {
		return true;
	}

	@Override
	public boolean supportsCorrelatedSubqueries() throws SQLException {
		return true;
	}

	@Override
	public boolean supportsUnion() throws SQLException {
		return true;
	}

	@Override
	public boolean supportsUnionAll() throws SQLException {
		return true;
	}

	@Override
	public boolean supportsOpenCursorsAcrossCommit() throws SQLException {
		return false;
	}

	@Override
	public boolean supportsOpenCursorsAcrossRollback() throws SQLException {
		return false;
	}

	@Override
	public boolean supportsOpenStatementsAcrossCommit() throws SQLException {
		return false;
	}

	@Override
	public boolean supportsOpenStatementsAcrossRollback() throws SQLException {
		return false;
	}

	@Override
	public int getMaxBinaryLiteralLength() throws SQLException {
		return 0;
	}

	@Override
	public int getMaxCharLiteralLength() throws SQLException {
		return 0;
	}

	@Override
	public int getMaxColumnNameLength() throws SQLException {
		return 300;
	}

	@Override
	public int getMaxColumnsInGroupBy() throws SQLException {
		return 0;
	}

	@Override
	public int getMaxColumnsInIndex() throws SQLException {
		return 0;
	}

	@Override
	public int getMaxColumnsInOrderBy() throws SQLException {
		return 0;
	}

	@Override
	public int getMaxColumnsInSelect() throws SQLException {
		return 0;
	}

	@Override
	public int getMaxColumnsInTable() throws SQLException {
		return 10000;
	}

	@Override
	public int getMaxConnections() throws SQLException {
		return 0;
	}

	@Override
	public int getMaxCursorNameLength() throws SQLException {
		return 0;
	}

	@Override
	public int getMaxIndexLength() throws SQLException {
		return 0;
	}

	@Override
	public int getMaxSchemaNameLength() throws SQLException {
		return 1024;
	}

	@Override
	public int getMaxProcedureNameLength() throws SQLException {
		return 256;
	}

	@Override
	public int getMaxCatalogNameLength() throws SQLException {
		return 1024;
	}

	@Override
	public int getMaxRowSize() throws SQLException {
		return 0;
	}

	@Override
	public boolean doesMaxRowSizeIncludeBlobs() throws SQLException {
		return true;
	}

	@Override
	public int getMaxStatementLength() throws SQLException {
		return 1024 * 1024;
	}

	@Override
	public int getMaxStatements() throws SQLException {
		return 0;
	}

	@Override
	public int getMaxTableNameLength() throws SQLException {
		return 1024;
	}

	@Override
	public int getMaxTablesInSelect() throws SQLException {
		return 0;
	}

	@Override
	public int getMaxUserNameLength() throws SQLException {
		return 0;
	}

	/**
	 * Returns the isolation level BigQuery transactions run at.
	 *
	 * <p>
	 * BigQuery multi-statement transactions provide snapshot isolation: every read
	 * in the transaction sees a consistent snapshot of the referenced tables. JDBC
	 * has no snapshot constant, so this is reported as the closest standard level,
	 * {@link Connection#TRANSACTION_REPEATABLE_READ} — snapshot isolation prevents
	 * dirty and non-repeatable reads but is weaker than serializable.
	 *
	 * @return {@link Connection#TRANSACTION_REPEATABLE_READ}
	 */
	@Override
	public int getDefaultTransactionIsolation() throws SQLException {
		return Connection.TRANSACTION_REPEATABLE_READ;
	}

	/**
	 * Returns {@code true}: transactions are supported through BigQuery sessions.
	 *
	 * <p>
	 * The driver starts a session on demand when {@code setAutoCommit(false)} is
	 * called, so no connection property is required. Savepoints and alternative
	 * isolation levels remain unsupported.
	 *
	 * @return {@code true}
	 */
	@Override
	public boolean supportsTransactions() throws SQLException {
		return true;
	}

	@Override
	public boolean supportsTransactionIsolationLevel(int level) throws SQLException {
		// REPEATABLE_READ is BigQuery's actual (snapshot) behavior; NONE is accepted
		// for tools that ask to run without transactions
		return level == Connection.TRANSACTION_REPEATABLE_READ || level == Connection.TRANSACTION_NONE;
	}

	@Override
	public boolean supportsDataDefinitionAndDataManipulationTransactions() throws SQLException {
		// DDL that creates or drops permanent entities is rejected inside a
		// BigQuery transaction
		return false;
	}

	@Override
	public boolean supportsDataManipulationTransactionsOnly() throws SQLException {
		// DML plus temporary-entity DDL (CREATE TEMP TABLE / FUNCTION)
		return true;
	}

	@Override
	public boolean dataDefinitionCausesTransactionCommit() throws SQLException {
		return false;
	}

	@Override
	public boolean dataDefinitionIgnoredInTransactions() throws SQLException {
		return false;
	}

	@Override
	public ResultSet getProcedures(String catalog, String schemaPattern, String procedureNamePattern)
			throws SQLException {
		checkClosed();

		logger.debug("getProcedures() called - catalog: [{}], schemaPattern: [{}], procedureNamePattern: [{}]", catalog,
				schemaPattern, procedureNamePattern);

		String cacheKey = "procedures:" + catalog + ":" + schemaPattern + ":" + procedureNamePattern;
		return getCachedOrExecute(cacheKey, () -> executeGetProcedures(catalog, schemaPattern, procedureNamePattern));
	}

	private ResultSet executeGetProcedures(String catalog, String schemaPattern, String procedureNamePattern)
			throws SQLException {
		String projectId = catalog != null ? catalog : connection.getCurrentCatalog();
		BigQuery bigquery = connection.getBigQuery();

		java.util.List<String> datasetIds = listDatasetsForProject(bigquery, projectId, schemaPattern);
		java.util.List<Object[]> rows = executeInParallel(datasetIds,
				datasetId -> queryProceduresForDataset(projectId, datasetId, procedureNamePattern),
				"Error querying procedures in parallel");

		logger.debug("getProcedures() returning {} routine(s)", rows.size());
		return createResultSet(MetadataColumns.Procedures.COLUMN_NAMES, MetadataColumns.Procedures.COLUMN_TYPES, rows);
	}

	/**
	 * Runs one {@code INFORMATION_SCHEMA} query against a dataset and maps its
	 * rows.
	 *
	 * <p>
	 * Captures the shape shared by the per-dataset metadata reads: reject unsafe
	 * identifiers, run a single query, map each row, and let one unreadable dataset
	 * contribute no rows rather than sinking the whole call. The cause is logged at
	 * WARN so a permissions problem is not mistaken for a dataset that simply has
	 * nothing to report.
	 *
	 * <p>
	 * Two nearby reads deliberately do <b>not</b> use this and should not be folded
	 * in, because their error handling is the part that differs:
	 * {@code queryColumnsViaInformationSchema} propagates so its caller can fall
	 * back to the {@code getTable()} API, and {@code queryConstraintsForDataset}
	 * returns an {@code Optional} so a failed read is distinguishable from a
	 * dataset declaring no constraints — only the latter may be cached.
	 *
	 * @param projectId
	 *            the project holding the dataset
	 * @param datasetId
	 *            the dataset to read
	 * @param purpose
	 *            what is being read, for the identifier-rejection log
	 * @param view
	 *            the {@code INFORMATION_SCHEMA} view name, for the failure log
	 * @param sql
	 *            the query to run
	 * @param rowMapper
	 *            maps one result row to a JDBC row, or returns null to skip it —
	 *            which is how the callers apply their name-pattern filters
	 * @return the rows mapped, or empty if the dataset could not be read
	 */
	private java.util.List<Object[]> queryInformationSchema(String projectId, String datasetId, String purpose,
			String view, String sql, java.util.function.Function<FieldValueList, Object[]> rowMapper) {
		if (rejectsUnsafeIdentifiers(projectId, datasetId, purpose)) {
			return java.util.List.of();
		}

		java.util.List<Object[]> rows = new java.util.ArrayList<>();
		try {
			BigQuery bigquery = connection.getBigQuery();
			QueryJobConfiguration config = QueryJobConfiguration.newBuilder(sql).build();
			TableResult result = bigquery.query(config);
			for (FieldValueList row : result.iterateAll()) {
				Object[] mapped = rowMapper.apply(row);
				if (mapped != null) {
					rows.add(mapped);
				}
			}
		} catch (InterruptedException e) {
			// Swallowing this along with everything else would leave the thread's
			// interrupt flag cleared, and these run on the virtual threads of the
			// parallel scan — whoever asked for the cancellation would never see it.
			Thread.currentThread().interrupt();
			logger.warn("Interrupted reading {} for dataset {}.{}", view, projectId, datasetId);
			// The interrupt can only come from bigquery.query(), which is before the
			// first row is read, so there is nothing partial to discard here — the
			// dataset contributes nothing, like any other dataset that failed to read.
			return java.util.List.of();
		} catch (Exception e) {
			logger.warn("Could not query {} for dataset {}: {}", view, datasetId, e.getMessage());
		}
		return rows;
	}

	/**
	 * The {@code routine_type} BigQuery reports for a stored procedure. The others
	 * it reports are {@code FUNCTION} and {@code TABLE FUNCTION}, both of which are
	 * functions and belong to {@link #getFunctions} — note these are BigQuery's
	 * spellings, not the ANSI {@code SCALAR_FUNCTION} /
	 * {@code TABLE_VALUED_FUNCTION}.
	 */
	private static final String ROUTINE_TYPE_PROCEDURE = "PROCEDURE";

	/**
	 * The {@code routine_type} for a function that returns a table rather than a
	 * scalar, which JDBC reports as {@code functionReturnsTable}.
	 */
	private static final String ROUTINE_TYPE_TABLE_FUNCTION = "TABLE FUNCTION";

	/**
	 * The routine read behind {@code getProcedures} and {@code getFunctions}.
	 *
	 * <p>
	 * {@code routine_definition} is the body. It is what an IDE shows when you ask
	 * to see a procedure — JetBrains
	 * <a href="https://youtrack.jetbrains.com/issue/DBE-12785">DBE-12785</a> is
	 * that request — and the driver was discarding it while already reading the row
	 * it sits on.
	 */
	private String routinesQuery(String projectId, String datasetId) {
		return String.format(
				"SELECT routine_name, routine_type, routine_definition " + "FROM `%s`.`%s`.INFORMATION_SCHEMA.ROUTINES",
				projectId, datasetId);
	}

	/**
	 * The routine body, for {@code REMARKS}.
	 *
	 * <p>
	 * {@code ROUTINES} has no description column — a routine's description lives in
	 * {@code ROUTINE_OPTIONS} under {@code option_name = 'description'} — so
	 * {@code REMARKS} was always null and the definition displaces nothing.
	 */
	private static String routineDefinition(FieldValueList row) {
		FieldValue definition = row.get("routine_definition");
		return definition.isNull() ? null : definition.getStringValue();
	}

	private java.util.List<Object[]> queryProceduresForDataset(String projectId, String datasetId,
			String procedureNamePattern) {
		String sql = routinesQuery(projectId, datasetId);
		return queryInformationSchema(projectId, datasetId, "procedures", "INFORMATION_SCHEMA.ROUTINES", sql, row -> {
			if (!isProcedure(row)) {
				return null;
			}
			String routineName = row.get("routine_name").getStringValue();
			if (procedureNamePattern != null && !matchesPattern(routineName, procedureNamePattern)) {
				return null;
			}
			return buildProcedureRow(projectId, datasetId, routineName, routineDefinition(row));
		});
	}

	/**
	 * Whether a {@code ROUTINES} row describes a stored procedure rather than a
	 * function. A null {@code routine_type} is treated as a procedure, which keeps
	 * a routine visible somewhere rather than dropping it from both methods.
	 */
	private static boolean isProcedure(FieldValueList row) {
		FieldValue routineType = row.get("routine_type");
		return routineType.isNull() || ROUTINE_TYPE_PROCEDURE.equalsIgnoreCase(routineType.getStringValue());
	}

	private Object[] buildProcedureRow(String projectId, String datasetId, String routineName, String remarks) {
		return new Object[]{projectId, // PROCEDURE_CAT
				datasetId, // PROCEDURE_SCHEM
				routineName, // PROCEDURE_NAME
				null, // reserved1
				null, // reserved2
				remarks, // REMARKS
				(short) DatabaseMetaData.procedureResultUnknown, // PROCEDURE_TYPE
				routineName // SPECIFIC_NAME
		};
	}

	@Override
	public ResultSet getProcedureColumns(String catalog, String schemaPattern, String procedureNamePattern,
			String columnNamePattern) throws SQLException {
		checkClosed();

		logger.debug(
				"getProcedureColumns() called - catalog: [{}], schemaPattern: [{}], procedureNamePattern: [{}], columnNamePattern: [{}]",
				catalog, schemaPattern, procedureNamePattern, columnNamePattern);

		String cacheKey = "procedureColumns:" + catalog + ":" + schemaPattern + ":" + procedureNamePattern + ":"
				+ columnNamePattern;
		return getCachedOrExecute(cacheKey,
				() -> executeGetProcedureColumns(catalog, schemaPattern, procedureNamePattern, columnNamePattern));
	}

	private ResultSet executeGetProcedureColumns(String catalog, String schemaPattern, String procedureNamePattern,
			String columnNamePattern) throws SQLException {
		String projectId = catalog != null ? catalog : connection.getCurrentCatalog();
		BigQuery bigquery = connection.getBigQuery();

		java.util.List<String> datasetIds = listDatasetsForProject(bigquery, projectId, schemaPattern);
		java.util.List<Object[]> rows = executeInParallel(datasetIds,
				datasetId -> queryProcedureColumnsForDataset(projectId, datasetId, procedureNamePattern,
						columnNamePattern),
				"Error querying procedure columns in parallel");

		logger.debug("getProcedureColumns() returning {} column(s)", rows.size());
		return createResultSet(MetadataColumns.ProcedureColumns.COLUMN_NAMES,
				MetadataColumns.ProcedureColumns.COLUMN_TYPES, rows);
	}

	/**
	 * The parameter read behind both {@code getProcedureColumns} and
	 * {@code getFunctionColumns}.
	 *
	 * <p>
	 * {@code PARAMETERS} carries no {@code routine_type}, so it is joined to
	 * {@code ROUTINES} to tell a procedure's parameters from a function's. Without
	 * the join each method would report the other's parameters as well as its own.
	 *
	 * @param wantProcedures
	 *            true for the parameters of stored procedures, false for those of
	 *            functions and table functions
	 */
	private String parametersQuery(String projectId, String datasetId, boolean wantProcedures) {
		// IFNULL mirrors isProcedure(): a routine with no routine_type is treated as
		// a procedure, so it stays visible under one method rather than neither.
		String predicate = wantProcedures ? "=" : "!=";
		return String.format("SELECT p.specific_name AS specific_name, p.ordinal_position AS ordinal_position, "
				+ "p.parameter_name AS parameter_name, p.parameter_mode AS parameter_mode, "
				+ "p.is_result AS is_result, p.data_type AS data_type "
				+ "FROM `%1$s`.`%2$s`.INFORMATION_SCHEMA.PARAMETERS p "
				+ "JOIN `%1$s`.`%2$s`.INFORMATION_SCHEMA.ROUTINES r " + "ON r.specific_name = p.specific_name "
				+ "WHERE IFNULL(r.routine_type, '%3$s') %4$s '%3$s' " + "ORDER BY p.specific_name, p.ordinal_position",
				projectId, datasetId, ROUTINE_TYPE_PROCEDURE, predicate);
	}

	private java.util.List<Object[]> queryProcedureColumnsForDataset(String projectId, String datasetId,
			String procedureNamePattern, String columnNamePattern) {
		String sql = parametersQuery(projectId, datasetId, true);
		return queryInformationSchema(projectId, datasetId, "procedure columns", "INFORMATION_SCHEMA.PARAMETERS", sql,
				row -> {
					String routineName = row.get("specific_name").getStringValue();
					if (procedureNamePattern != null && !matchesPattern(routineName, procedureNamePattern)) {
						return null;
					}
					String paramName = row.get("parameter_name").isNull()
							? ""
							: row.get("parameter_name").getStringValue();
					if (columnNamePattern != null && !matchesPattern(paramName, columnNamePattern)) {
						return null;
					}
					String dataType = row.get("data_type").isNull() ? "STRING" : row.get("data_type").getStringValue();
					TypeMapper.InfoSchemaTypeInfo typeInfo = TypeMapper.parseInfoSchemaTypeInfo(dataType);
					String paramMode = row.get("parameter_mode").isNull()
							? "IN"
							: row.get("parameter_mode").getStringValue();
					short columnType = switch (paramMode.toUpperCase(Locale.ROOT)) {
						case "IN" -> (short) DatabaseMetaData.procedureColumnIn;
						case "OUT" -> (short) DatabaseMetaData.procedureColumnOut;
						case "INOUT" -> (short) DatabaseMetaData.procedureColumnInOut;
						default -> (short) DatabaseMetaData.procedureColumnUnknown;
					};
					return buildProcedureColumnRow(projectId, datasetId, routineName, paramName, columnType, typeInfo,
							dataType);
				});
	}

	private Object[] buildProcedureColumnRow(String projectId, String datasetId, String routineName, String paramName,
			short columnType, TypeMapper.InfoSchemaTypeInfo typeInfo, String typeName) {
		return new Object[]{projectId, // PROCEDURE_CAT
				datasetId, // PROCEDURE_SCHEM
				routineName, // PROCEDURE_NAME
				paramName, // COLUMN_NAME
				columnType, // COLUMN_TYPE
				typeInfo.jdbcType(), // DATA_TYPE
				typeName, // TYPE_NAME
				typeInfo.columnSize(), // PRECISION
				typeInfo.columnSize(), // LENGTH
				(short) typeInfo.decimalDigits(), // SCALE
				(short) 10, // RADIX
				(short) DatabaseMetaData.procedureNullable, // NULLABLE
				null // REMARKS
		};
	}

	/**
	 * Retrieves table metadata for the specified catalog, schema, and table
	 * patterns.
	 *
	 * <p>
	 * This method returns a ResultSet with the following columns:
	 * <ol>
	 * <li>TABLE_CAT (String) - Project ID
	 * <li>TABLE_SCHEM (String) - Dataset ID
	 * <li>TABLE_NAME (String) - Table name
	 * <li>TABLE_TYPE (String) - "TABLE", "VIEW", or "MATERIALIZED VIEW"
	 * <li>REMARKS (String) - Table description
	 * <li>TYPE_CAT (String) - null (not used)
	 * <li>TYPE_SCHEM (String) - null (not used)
	 * <li>TYPE_NAME (String) - null (not used)
	 * <li>SELF_REFERENCING_COL_NAME (String) - null (not used)
	 * <li>REF_GENERATION (String) - null (not used)
	 * </ol>
	 *
	 * <p>
	 * <b>Pattern Matching:</b> Patterns support SQL LIKE syntax with wildcards:
	 * <ul>
	 * <li>{@code %} - matches any sequence of zero or more characters
	 * <li>{@code _} - matches any single character
	 * <li>{@code null} - matches all (no filtering)
	 * </ul>
	 *
	 * <p>
	 * <b>Performance:</b> Uses virtual threads to parallelize dataset fetching for
	 * projects with many datasets. Results are cached based on
	 * {@code metadataCacheTtl} connection property (default: 300 seconds).
	 *
	 * <p>
	 * <b>Lazy Loading:</b> If {@code metadataLazyLoad=true} and no patterns are
	 * specified, returns an empty ResultSet to improve IntelliJ IDEA performance.
	 * IntelliJ will load tables on-demand as users expand the database tree.
	 *
	 * @param catalog
	 *            project ID (null = current project)
	 * @param schemaPattern
	 *            dataset pattern (supports SQL LIKE: % and _)
	 * @param tableNamePattern
	 *            table name pattern (supports SQL LIKE: % and _)
	 * @param types
	 *            array of table types to include (e.g., ["TABLE", "VIEW"]) or null
	 *            for all types
	 * @return ResultSet with table metadata, sorted by TABLE_TYPE, TABLE_SCHEM, and
	 *         TABLE_NAME
	 * @throws SQLException
	 *             if connection is closed or query fails
	 */
	@Override
	public ResultSet getTables(String catalog, String schemaPattern, String tableNamePattern, String[] types)
			throws SQLException {
		checkClosed();

		String typesKey = types != null ? java.util.Arrays.toString(types) : "null";
		String cacheKey = "tables:" + catalog + ":" + schemaPattern + ":" + tableNamePattern + ":" + typesKey;

		return getCachedOrExecute(cacheKey, () -> executeGetTables(catalog, schemaPattern, tableNamePattern, types));
	}

	private ResultSet executeGetTables(String catalog, String schemaPattern, String tableNamePattern, String[] types)
			throws SQLException {
		String projectId = catalog != null ? catalog : connection.getCurrentCatalog();

		BigQuery bigquery = connection.getBigQuery();
		boolean lazyLoad = connection.getProperties().metadataLazyLoad();

		// Enhanced logging to debug IntelliJ introspection
		logger.debug(
				"getTables() called - catalog: [{}], schemaPattern: [{}], tableNamePattern: [{}], types: [{}], lazyLoad: {}",
				catalog, schemaPattern, tableNamePattern, types != null ? java.util.Arrays.toString(types) : "null",
				lazyLoad);

		// Lazy loading: If enabled and no specific patterns, return empty result
		// This allows IntelliJ to load the tree structure quickly without fetching all
		// tables
		if (lazyLoad && schemaPattern == null && tableNamePattern == null) {
			logger.debug("Lazy loading enabled: returning empty table list (no patterns specified) - catalog: [{}]",
					catalog);
			return createResultSet(MetadataColumns.Tables.COLUMN_NAMES, MetadataColumns.Tables.COLUMN_TYPES,
					new java.util.ArrayList<>());
		}

		// Get datasets matching schema pattern
		java.util.List<String> datasetIds = listDatasetsForProject(bigquery, projectId, schemaPattern);

		// Guarded, unlike the other debug calls here: the third argument concatenates
		// a sublist into a String, and an argument is evaluated before the logging
		// call whatever the level is. Parameterised logging only defers formatting,
		// not the expressions you hand it.
		if (logger.isDebugEnabled()) {
			logger.debug("Found {} dataset(s) matching pattern [{}]: {}", datasetIds.size(), schemaPattern,
					datasetIds.size() <= 10 ? datasetIds : datasetIds.subList(0, 10) + "...");
		}

		// Always use parallel loading for better performance with BigQuery API
		logger.debug("Using parallel loading for {} datasets", datasetIds.size());
		java.util.List<Object[]> rows = queryTablesParallel(projectId, datasetIds, tableNamePattern, types);

		rows.addAll(informationSchemaTableRows(projectId, datasetIds, schemaPattern, tableNamePattern, types));

		logger.debug("getTables() returning {} table(s)", rows.size());

		return createResultSet(MetadataColumns.Tables.COLUMN_NAMES, MetadataColumns.Tables.COLUMN_TYPES, rows);
	}

	/**
	 * Whether this connection asked for {@code INFORMATION_SCHEMA} to be listed.
	 */
	private boolean includesInformationSchema() {
		return connection.getProperties().includeInformationSchema();
	}

	/**
	 * The {@code getTables()} rows for {@code INFORMATION_SCHEMA}, at both scopes.
	 *
	 * <p>
	 * Built from a static list, so this issues no BigQuery query and cannot fail
	 * the call. The two scopes are disjoint — see {@link InformationSchemaViews} —
	 * so the project-scoped views appear once under the synthetic schema and the
	 * dataset-scoped ones once per dataset already being listed.
	 *
	 * @param projectId
	 *            the project being listed
	 * @param datasetIds
	 *            the datasets the caller's schema pattern already selected
	 * @param schemaPattern
	 *            the caller's schema pattern, which also decides whether the
	 *            synthetic schema is in scope
	 * @param tableNamePattern
	 *            the caller's table pattern
	 * @param types
	 *            the caller's type filter
	 * @return the rows to append, possibly empty
	 */
	private java.util.List<Object[]> informationSchemaTableRows(String projectId, java.util.List<String> datasetIds,
			String schemaPattern, String tableNamePattern, String[] types) {
		if (!includesInformationSchema()) {
			return java.util.List.of();
		}
		// Applied once here rather than per row: these are all one type, so a filter
		// that excludes it excludes every row this method could produce.
		if (types != null && !java.util.Arrays.asList(types).contains(InformationSchemaViews.TABLE_TYPE)) {
			return java.util.List.of();
		}

		java.util.List<Object[]> rows = new java.util.ArrayList<>();

		if (schemaPattern == null || matchesPattern(InformationSchemaViews.SCHEMA_NAME, schemaPattern)) {
			for (String view : InformationSchemaViews.PROJECT_SCOPED) {
				if (tableNamePattern == null || matchesPattern(view, tableNamePattern)) {
					rows.add(informationSchemaTableRow(projectId, InformationSchemaViews.SCHEMA_NAME, view));
				}
			}
		}

		for (String datasetId : datasetIds) {
			for (String view : InformationSchemaViews.DATASET_SCOPED) {
				String tableName = InformationSchemaViews.datasetTableName(view);
				if (tableNamePattern == null || matchesPattern(tableName, tableNamePattern)) {
					rows.add(informationSchemaTableRow(projectId, datasetId, tableName));
				}
			}
		}

		return rows;
	}

	/** One {@code getTables()} row for an {@code INFORMATION_SCHEMA} view. */
	private static Object[] informationSchemaTableRow(String projectId, String schema, String tableName) {
		return new Object[]{projectId, // TABLE_CAT
				schema, // TABLE_SCHEM
				tableName, // TABLE_NAME
				InformationSchemaViews.TABLE_TYPE, // TABLE_TYPE
				"", // REMARKS
				null, // TYPE_CAT
				null, // TYPE_SCHEM
				null, // TYPE_NAME
				null, // SELF_REFERENCING_COL_NAME
				null // REF_GENERATION
		};
	}

	/**
	 * Query tables from multiple datasets in parallel using virtual threads.
	 *
	 * <p>
	 * This significantly improves performance for projects with many datasets
	 * (e.g., 90+). Addresses JetBrains issue DBE-22088.
	 */
	private java.util.List<Object[]> queryTablesParallel(String projectId, java.util.List<String> datasetIds,
			String tableNamePattern, String[] types) throws SQLException {
		BigQuery bigquery = connection.getBigQuery();
		return executeInParallel(datasetIds,
				datasetId -> queryTablesForDataset(bigquery, projectId, datasetId, tableNamePattern, types),
				"Error querying tables in parallel");
	}

	/** Query tables for a single dataset. */
	private java.util.List<Object[]> queryTablesForDataset(BigQuery bigquery, String projectId, String datasetId,
			String tableNamePattern, String[] types) throws SQLException {
		java.util.List<Object[]> rows = new java.util.ArrayList<>();

		// List tables in dataset
		var tables = bigquery.listTables(DatasetId.of(projectId, datasetId));

		for (Table table : tables.iterateAll()) {
			String tableName = table.getTableId().getTable();

			// Apply table name pattern filter
			if (tableNamePattern != null && !matchesTableNameFilter(tableName, tableNamePattern)) {
				continue;
			}

			// Map BigQuery table type to JDBC type
			String tableType;
			TableDefinition def = table.getDefinition();
			if (def instanceof ViewDefinition) {
				tableType = "VIEW";
			} else if (def instanceof MaterializedViewDefinition) {
				tableType = "MATERIALIZED VIEW";
			} else {
				tableType = "TABLE";
			}

			// Apply type filter
			if (types != null && !java.util.Arrays.asList(types).contains(tableType)) {
				continue;
			}

			// Always empty in practice: tables.list does not return description, so
			// this is filled in by fillInRemarks below. Kept because a future listing
			// that does carry one should win over the INFORMATION_SCHEMA read.
			String remarks = table.getDescription() != null ? table.getDescription() : "";

			rows.add(new Object[]{projectId, // TABLE_CAT
					datasetId, // TABLE_SCHEM
					tableName, // TABLE_NAME
					tableType, // TABLE_TYPE
					remarks, // REMARKS
					null, // TYPE_CAT
					null, // TYPE_SCHEM
					null, // TYPE_NAME
					null, // SELF_REFERENCING_COL_NAME
					null // REF_GENERATION
			});
		}

		fillInRemarks(projectId, datasetId, rows);
		return collapseShards(rows);
	}

	/**
	 * Applies the caller's table-name filter, additionally letting a wildcard name
	 * stand for the shards it collapses.
	 *
	 * <p>
	 * {@code getTables(…, "events_*")} would otherwise match nothing: {@code *} is
	 * not a JDBC pattern character, and the {@code _} in it is a single-character
	 * wildcard, so the pattern reads as "events, any character, a literal
	 * asterisk". Asking for the name the driver just reported has to work.
	 *
	 * <p>
	 * A shard's own name still matches itself, so an exact lookup of
	 * {@code events_20260101} keeps returning that one table even with collapsing
	 * on. Only the unfiltered listing loses the individual rows, which is where the
	 * 365-rows-per-year problem actually lives.
	 */
	private boolean matchesTableNameFilter(String tableName, String tableNamePattern) {
		if (matchesPattern(tableName, tableNamePattern)) {
			return true;
		}
		if (!collapsesShards()) {
			return false;
		}
		String prefix = ShardedTables.prefixOf(tableNamePattern);
		return prefix != null && prefix.equals(ShardedTables.shardPrefix(tableName));
	}

	/** Whether this connection asked for date-sharded tables to be collapsed. */
	private boolean collapsesShards() {
		return connection.getProperties().collapseShardedTables();
	}

	/**
	 * Replaces each date-sharded set in {@code rows} with one {@code prefix_*} row.
	 *
	 * <p>
	 * A year of daily shards is 365 rows in a database tree for what its users
	 * think of as one table, and it fills the metadata cache against
	 * {@code metadataCacheMaxRows} for no benefit — JetBrains
	 * <a href="https://youtrack.jetbrains.com/issue/DBE-10947">DBE-10947</a> and
	 * <a href="https://youtrack.jetbrains.com/issue/DBE-12807">DBE-12807</a>.
	 *
	 * <p>
	 * The collapsed row takes the first shard's position, so a set does not jump to
	 * the end of the listing, and its {@code REMARKS} says what it stands for —
	 * otherwise a wildcard name is the only clue that rows were removed. Any
	 * description the shards carried is dropped with them: it belonged to one
	 * shard, and the summary is the more useful thing to say about a set.
	 *
	 * <p>
	 * Views and materialized views are grouped too if they are named this way.
	 * Nothing about the convention is specific to tables, and a set of sharded
	 * views has the same problem.
	 *
	 * @param rows
	 *            the dataset's table rows
	 * @return the rows with each shard set replaced by a single entry
	 */
	private java.util.List<Object[]> collapseShards(java.util.List<Object[]> rows) {
		if (!collapsesShards()) {
			return rows;
		}

		java.util.List<String> names = rows.stream().map(row -> (String) row[TABLE_ROW_NAME]).toList();
		java.util.Map<String, java.util.List<String>> sets = ShardedTables.shardSets(names);
		if (sets.isEmpty()) {
			return rows;
		}

		java.util.Set<String> collapsed = new java.util.HashSet<>();
		java.util.List<Object[]> result = new java.util.ArrayList<>(rows.size());
		for (Object[] row : rows) {
			String name = (String) row[TABLE_ROW_NAME];
			String prefix = ShardedTables.shardPrefix(name);
			if (prefix == null || !sets.containsKey(prefix)) {
				result.add(row);
				continue;
			}
			if (collapsed.add(prefix)) {
				Object[] wildcard = row.clone();
				wildcard[TABLE_ROW_NAME] = ShardedTables.wildcardName(prefix);
				wildcard[TABLE_ROW_REMARKS] = ShardedTables.describe(sets.get(prefix));
				result.add(wildcard);
			}
		}
		logger.debug("Collapsed {} sharded set(s) into wildcard entries", sets.size());
		return result;
	}

	/** Index of {@code TABLE_TYPE} in a {@link MetadataColumns.Tables} row. */
	private static final int TABLE_ROW_TYPE = 3;

	/** Index of {@code REMARKS} in a {@link MetadataColumns.Tables} row. */
	private static final int TABLE_ROW_REMARKS = 4;

	/** Index of {@code TABLE_NAME} in a {@link MetadataColumns.Tables} row. */
	private static final int TABLE_ROW_NAME = 2;

	/**
	 * Fills the {@code REMARKS} of {@code rows} from {@code INFORMATION_SCHEMA}:
	 * the author's description where there is one, and for a view the defining SQL
	 * where there is not.
	 *
	 * <p>
	 * Neither is on the {@link Table} objects {@code listTables} returns. That
	 * response carries {@code view.useLegacySql} and nothing else of the view, so
	 * {@code ViewDefinition.getQuery()} is null; and it omits {@code description}
	 * entirely, so {@code Table.getDescription()} is null for every table and
	 * {@code REMARKS} was uniformly the empty string. Only {@code tables.get}
	 * returns either, which would be one API call per table.
	 *
	 * <p>
	 * Both answer a JetBrains request: an IDE that cannot show a view's SQL sends
	 * you to the BigQuery console
	 * (<a href="https://youtrack.jetbrains.com/issue/DBE-12630">DBE-12630</a>), and
	 * a table comment that is always blank is indistinguishable from a table that
	 * has none.
	 *
	 * <p>
	 * <b>One query per dataset serves both.</b> The descriptions live in
	 * {@code TABLE_OPTIONS} and the DDL in {@code TABLES}, so a join reads them
	 * together rather than twice. That matters because the two have different
	 * reach: the definitions are wanted only for datasets that contributed a view,
	 * while descriptions apply to every table, so folding them into one read is
	 * what keeps a dataset of plain tables at one query rather than adding a second
	 * to the datasets that already paid. This is the path IntelliJ walks on every
	 * refresh — DBE-22088 — which is also why {@code metadataIncludeDescriptions}
	 * exists to turn the description half off, falling back to the narrower
	 * view-only read.
	 *
	 * <p>
	 * A description wins over a definition: it is what the author wrote for this
	 * column, and BigQuery emits it inside the DDL's {@code OPTIONS} anyway, so
	 * nothing is lost by preferring it.
	 *
	 * <p>
	 * {@code ddl} rather than {@code VIEWS.view_definition} because
	 * {@code MATERIALIZED_VIEWS} has no definition column at all, so the DDL is the
	 * only form that answers for both. The DDL is selected only for views, since
	 * for a large table it is a long string nothing here reads.
	 */
	private void fillInRemarks(String projectId, String datasetId, java.util.List<Object[]> rows) {
		java.util.List<Object[]> undescribedViews = rows.stream()
				.filter(row -> "VIEW".equals(row[TABLE_ROW_TYPE]) || "MATERIALIZED VIEW".equals(row[TABLE_ROW_TYPE]))
				.filter(BQDatabaseMetaData::isBlankRemarks).toList();

		boolean readDescriptions = connection.getProperties().metadataIncludeDescriptions();
		if (!readDescriptions && undescribedViews.isEmpty()) {
			return;
		}

		java.util.Map<String, String> descriptions = new java.util.HashMap<>();
		java.util.Map<String, String> definitions = new java.util.HashMap<>();
		String sql = readDescriptions ? remarksSql(projectId, datasetId) : viewDefinitionSql(projectId, datasetId);

		queryInformationSchema(projectId, datasetId, "table remarks", "INFORMATION_SCHEMA.TABLES", sql, row -> {
			String tableName = row.get("table_name").getStringValue();
			if (!row.get("ddl").isNull()) {
				definitions.put(tableName, row.get("ddl").getStringValue());
			}
			if (readDescriptions && !row.get("description").isNull()) {
				// TABLE_OPTIONS reports an option as the SQL that would set it, so the
				// value arrives quoted and escaped.
				descriptions.put(tableName, SqlStringLiterals.unquote(row.get("description").getStringValue()));
			}
			// Collected into the maps above rather than returned as rows: this read
			// annotates rows that already exist instead of producing its own.
			return null;
		});

		// Descriptions first, for every kind of table.
		for (Object[] row : rows) {
			String description = descriptions.get((String) row[TABLE_ROW_NAME]);
			if (description != null && !description.isEmpty()) {
				row[TABLE_ROW_REMARKS] = description;
			}
		}
		// Then definitions, for the views the pass above left empty. A view with a
		// description shows the description; a view without one still shows its SQL,
		// which is what #219 put here and this must not take away.
		for (Object[] row : undescribedViews) {
			if (!isBlankRemarks(row)) {
				continue;
			}
			String definition = definitions.get((String) row[TABLE_ROW_NAME]);
			if (definition != null) {
				row[TABLE_ROW_REMARKS] = definition;
			}
		}
	}

	/** Whether a table row's {@code REMARKS} is still unset. */
	private static boolean isBlankRemarks(Object[] row) {
		return row[TABLE_ROW_REMARKS] == null || ((String) row[TABLE_ROW_REMARKS]).isEmpty();
	}

	/**
	 * Reads descriptions for every table and DDL for the views, in one query.
	 *
	 * <p>
	 * A {@code LEFT JOIN} rather than an inner one: a table with no description
	 * still has to appear, because it may be a view whose DDL is wanted.
	 */
	private static String remarksSql(String projectId, String datasetId) {
		return String.format("SELECT t.table_name AS table_name, "
				+ "IF(t.table_type IN ('VIEW', 'MATERIALIZED VIEW'), t.ddl, NULL) AS ddl, "
				+ "o.option_value AS description " + "FROM `%1$s`.`%2$s`.INFORMATION_SCHEMA.TABLES t "
				+ "LEFT JOIN `%1$s`.`%2$s`.INFORMATION_SCHEMA.TABLE_OPTIONS o "
				+ "ON o.table_name = t.table_name AND o.option_name = 'description'", projectId, datasetId);
	}

	/**
	 * The narrower read used when {@code metadataIncludeDescriptions} is off:
	 * definitions only, and only for the views that need them.
	 *
	 * <p>
	 * Selects a null {@code description} so the row mapper reads one shape either
	 * way.
	 */
	private static String viewDefinitionSql(String projectId, String datasetId) {
		return String.format("SELECT table_name, ddl, CAST(NULL AS STRING) AS description "
				+ "FROM `%s`.`%s`.INFORMATION_SCHEMA.TABLES " + "WHERE table_type IN ('VIEW', 'MATERIALIZED VIEW')",
				projectId, datasetId);
	}

	@Override
	public ResultSet getSchemas() throws SQLException {
		return getSchemas(null, null);
	}

	@Override
	public ResultSet getCatalogs() throws SQLException {
		checkClosed();

		return getCachedOrExecute("catalogs", () -> {
			// Catalogs are projects. The connection's own is always reported; the
			// rest are the ones the caller named, because there is no cheap way to
			// discover them — listing every project a credential can see is a
			// Resource Manager call, is slow on a large organisation, and returns
			// mostly projects that have no BigQuery data at all.
			java.util.List<String> projects = new java.util.ArrayList<>();
			projects.add(connection.getProperties().projectId());
			projects.addAll(connection.getProperties().additionalProjects());
			// Sorted because JDBC specifies getCatalogs() ordered by TABLE_CAT, and
			// the configured order is whatever the URL happened to say.
			projects.sort(String::compareTo);

			java.util.List<Object[]> rows = new java.util.ArrayList<>(projects.size());
			for (String project : projects) {
				rows.add(new Object[]{project});
			}

			return createResultSet(MetadataColumns.Catalogs.COLUMN_NAMES, MetadataColumns.Catalogs.COLUMN_TYPES, rows);
		});
	}

	@Override
	public ResultSet getTableTypes() throws SQLException {
		checkClosed();

		return getCachedOrExecute("tableTypes", () -> {
			java.util.List<Object[]> rows = new java.util.ArrayList<>();
			rows.add(new Object[]{"TABLE"});
			rows.add(new Object[]{"VIEW"});
			rows.add(new Object[]{"MATERIALIZED VIEW"});
			if (includesInformationSchema()) {
				// Listed only when it can occur. A type nothing is reported under is a
				// filter that silently returns nothing, which reads as "no such tables"
				// rather than "that type does not exist here".
				rows.add(new Object[]{InformationSchemaViews.TABLE_TYPE});
			}

			return createResultSet(MetadataColumns.TableTypes.COLUMN_NAMES, MetadataColumns.TableTypes.COLUMN_TYPES,
					rows);
		});
	}

	/**
	 * Retrieves column metadata for tables matching the specified patterns.
	 *
	 * <p>
	 * This method returns a ResultSet with the following columns:
	 * <ol>
	 * <li>TABLE_CAT (String) - Project ID
	 * <li>TABLE_SCHEM (String) - Dataset ID
	 * <li>TABLE_NAME (String) - Table name
	 * <li>COLUMN_NAME (String) - Column name
	 * <li>DATA_TYPE (int) - SQL type from {@link java.sql.Types}
	 * <li>TYPE_NAME (String) - BigQuery type name (e.g., "INT64", "STRING",
	 * "ARRAY&lt;STRING&gt;")
	 * <li>COLUMN_SIZE (int) - Column size (precision for numeric types)
	 * <li>BUFFER_LENGTH (int) - null (not used)
	 * <li>DECIMAL_DIGITS (int) - Decimal digits (scale for numeric types)
	 * <li>NUM_PREC_RADIX (int) - 10 (radix for numeric types)
	 * <li>NULLABLE (int) - {@link DatabaseMetaData#columnNoNulls} or
	 * {@link DatabaseMetaData#columnNullable}
	 * <li>REMARKS (String) - Column description
	 * <li>COLUMN_DEF (String) - null (default value not supported)
	 * <li>SQL_DATA_TYPE (int) - null (not used)
	 * <li>SQL_DATETIME_SUB (int) - null (not used)
	 * <li>CHAR_OCTET_LENGTH (int) - Maximum bytes for character types
	 * <li>ORDINAL_POSITION (int) - Column index starting at 1
	 * <li>IS_NULLABLE (String) - "YES" or "NO"
	 * <li>SCOPE_CATALOG (String) - null (not used)
	 * <li>SCOPE_SCHEMA (String) - null (not used)
	 * <li>SCOPE_TABLE (String) - null (not used)
	 * <li>SOURCE_DATA_TYPE (short) - null (not used)
	 * <li>IS_AUTOINCREMENT (String) - "NO" (BigQuery doesn't support
	 * auto-increment)
	 * <li>IS_GENERATEDCOLUMN (String) - "NO" (generated columns not yet supported)
	 * </ol>
	 *
	 * <p>
	 * <b>Pattern Matching:</b> All pattern parameters support SQL LIKE syntax:
	 * <ul>
	 * <li>{@code %} - matches any sequence of zero or more characters
	 * <li>{@code _} - matches any single character
	 * <li>{@code null} - matches all (no filtering)
	 * </ul>
	 *
	 * <p>
	 * <b>Performance:</b> Uses {@code INFORMATION_SCHEMA.COLUMNS} (one query per
	 * dataset) instead of individual {@code getTable()} calls, with parallel
	 * execution across datasets via virtual threads. Falls back to
	 * {@code getTable()}-per-table if {@code INFORMATION_SCHEMA} is unavailable.
	 * Results are cached based on {@code metadataCacheTtl} connection property
	 * (default: 300 seconds).
	 *
	 * <p>
	 * <b>Lazy Loading:</b> If {@code metadataLazyLoad=true} and no tableNamePattern
	 * is specified, returns an empty ResultSet to improve IntelliJ IDEA
	 * performance. IntelliJ will load columns on-demand as users expand the table
	 * nodes.
	 *
	 * @param catalog
	 *            project ID (null = current project)
	 * @param schemaPattern
	 *            dataset pattern (supports SQL LIKE: % and _)
	 * @param tableNamePattern
	 *            table name pattern (supports SQL LIKE: % and _)
	 * @param columnNamePattern
	 *            column name pattern (supports SQL LIKE: % and _)
	 * @return ResultSet with column metadata, sorted by TABLE_CAT, TABLE_SCHEM,
	 *         TABLE_NAME, and ORDINAL_POSITION
	 * @throws SQLException
	 *             if connection is closed or query fails
	 */
	@Override
	public ResultSet getColumns(String catalog, String schemaPattern, String tableNamePattern, String columnNamePattern)
			throws SQLException {
		checkClosed();

		String cacheKey = "columns:" + catalog + ":" + schemaPattern + ":" + tableNamePattern + ":" + columnNamePattern;

		return getCachedOrExecute(cacheKey,
				() -> executeGetColumns(catalog, schemaPattern, tableNamePattern, columnNamePattern));
	}

	/**
	 * Query columns from multiple datasets in parallel using virtual threads.
	 *
	 * <p>
	 * Each dataset runs its own {@code INFORMATION_SCHEMA.COLUMNS} query
	 * concurrently via a virtual thread.
	 */
	private java.util.List<Object[]> queryColumnsParallel(String projectId, java.util.List<String> datasetIds,
			String tableNamePattern, String columnNamePattern) throws SQLException {
		BigQuery bigquery = connection.getBigQuery();
		return executeInParallel(datasetIds, datasetId -> queryColumnsForDataset(bigquery, projectId, datasetId,
				tableNamePattern, columnNamePattern), "Error querying columns in parallel");
	}

	/**
	 * Query columns for a single dataset.
	 *
	 * <p>
	 * Uses {@code INFORMATION_SCHEMA.COLUMNS} for performance: one query per
	 * dataset instead of one {@code getTable()} API call per table. Falls back to
	 * the legacy {@code getTable()} approach if the query fails — because the
	 * endpoint does not implement {@code INFORMATION_SCHEMA}, or because the caller
	 * lacks permission on the dataset. The failure is logged at WARN so a genuine
	 * query defect is not mistaken for an endpoint limitation.
	 */
	private java.util.List<Object[]> queryColumnsForDataset(BigQuery bigquery, String projectId, String datasetId,
			String tableNamePattern, String columnNamePattern) throws SQLException {
		// Unlike the other metadata queries, this one has a non-SQL route to the same
		// answer, so a name that cannot be safely interpolated does not have to cost
		// the caller their columns — it costs them the fast path instead.
		if (rejectsUnsafeIdentifiers(projectId, datasetId, "columns")) {
			return queryColumnsViaGetTable(bigquery, projectId, datasetId, tableNamePattern, columnNamePattern);
		}

		try {
			return collapseShardColumns(queryColumnsViaInformationSchema(bigquery, projectId, datasetId,
					tableNamePattern, columnNamePattern));
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new BQSQLException("INFORMATION_SCHEMA query interrupted for dataset: " + datasetId, e);
		} catch (Exception e) {
			// Logged at WARN, not debug: the fallback is correct but slower, and a
			// malformed INFORMATION_SCHEMA query would otherwise look like a normal
			// endpoint limitation
			logger.warn("INFORMATION_SCHEMA.COLUMNS query failed for {}.{}, falling back to the getTable() API: {}",
					projectId, datasetId, e.getMessage());
			return collapseShardColumns(
					queryColumnsViaGetTable(bigquery, projectId, datasetId, tableNamePattern, columnNamePattern));
		}
	}

	/**
	 * Reports one set of columns per shard set, taken from its newest shard and
	 * reported under the wildcard name.
	 *
	 * <p>
	 * Without this a collapsed {@code events_*} node has no columns at all — the
	 * name matches no table — so an IDE renders the entry and then cannot expand
	 * it. It also removes the other half of the row explosion: a year of daily
	 * shards is 365 × N column rows, which is what actually fills the metadata
	 * cache.
	 *
	 * <p>
	 * The newest shard because shards drift. A column added last month exists in
	 * recent shards and not in the first, and a query through {@code events_*} can
	 * select it, so reporting the oldest schema would hide columns that work.
	 *
	 * <p>
	 * A set of one is not a set, so an exact lookup of {@code events_20260101}
	 * still reports that shard under its own name — the same rule
	 * {@link #collapseShards} applies to the listing.
	 *
	 * @param rows
	 *            column rows for one dataset
	 * @return the rows with each shard set reduced to its newest member, renamed
	 */
	private java.util.List<Object[]> collapseShardColumns(java.util.List<Object[]> rows) {
		if (!collapsesShards()) {
			return rows;
		}

		java.util.List<String> names = rows.stream().map(row -> (String) row[COLUMN_ROW_TABLE_NAME]).distinct()
				.toList();
		java.util.Map<String, java.util.List<String>> sets = ShardedTables.shardSets(names);
		if (sets.isEmpty()) {
			return rows;
		}

		java.util.Map<String, String> newestByPrefix = new java.util.HashMap<>();
		sets.forEach((prefix, shards) -> newestByPrefix.put(prefix, ShardedTables.newestShard(shards)));

		java.util.List<Object[]> result = new java.util.ArrayList<>(rows.size());
		for (Object[] row : rows) {
			String tableName = (String) row[COLUMN_ROW_TABLE_NAME];
			String prefix = ShardedTables.shardPrefix(tableName);
			if (prefix == null || !sets.containsKey(prefix)) {
				result.add(row);
				continue;
			}
			if (tableName.equals(newestByPrefix.get(prefix))) {
				Object[] renamed = row.clone();
				renamed[COLUMN_ROW_TABLE_NAME] = ShardedTables.wildcardName(prefix);
				result.add(renamed);
			}
		}
		return result;
	}

	/** Index of {@code TABLE_NAME} in a {@link MetadataColumns.Columns} row. */
	private static final int COLUMN_ROW_TABLE_NAME = 2;

	/**
	 * Query columns via a single {@code INFORMATION_SCHEMA.COLUMNS} query per
	 * dataset.
	 *
	 * <p>
	 * Replaces N individual {@code getTable()} API calls (one per table) with a
	 * single metadata query, reducing API round-trips by ~50x for typical datasets.
	 */
	private java.util.List<Object[]> queryColumnsViaInformationSchema(BigQuery bigquery, String projectId,
			String datasetId, String tableNamePattern, String columnNamePattern) throws InterruptedException {
		String sql = "SELECT table_name, column_name, ordinal_position, is_nullable, data_type" + " FROM `" + projectId
				+ "." + datasetId + ".INFORMATION_SCHEMA.COLUMNS`" + " ORDER BY table_name, ordinal_position";

		QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(sql).setUseLegacySql(false).build();

		TableResult results = bigquery.query(queryConfig);

		java.util.List<Object[]> rows = new java.util.ArrayList<>();
		for (FieldValueList row : results.iterateAll()) {
			String tableName = row.get("table_name").getStringValue();
			if (tableNamePattern != null && !matchesTableNameFilter(tableName, tableNamePattern)) {
				continue;
			}
			String columnName = row.get("column_name").getStringValue();
			if (columnNamePattern != null && !matchesPattern(columnName, columnNamePattern)) {
				continue;
			}

			int ordinalPosition = (int) row.get("ordinal_position").getLongValue();
			boolean isNullable = "YES".equalsIgnoreCase(row.get("is_nullable").getStringValue());
			String dataType = row.get("data_type").getStringValue();

			TypeMapper.InfoSchemaTypeInfo typeInfo = TypeMapper.parseInfoSchemaTypeInfo(dataType);
			int nullable = isNullable ? DatabaseMetaData.columnNullable : DatabaseMetaData.columnNoNulls;

			// REMARKS not available in INFORMATION_SCHEMA.COLUMNS
			rows.add(buildColumnRow(projectId, datasetId, tableName, columnName, typeInfo.jdbcType(), dataType,
					typeInfo.columnSize(), typeInfo.decimalDigits(), nullable, ordinalPosition, null));
		}
		return rows;
	}

	/**
	 * Fallback: query columns via {@code listTables()} + {@code getTable()} per
	 * table.
	 *
	 * <p>
	 * Used when the {@code INFORMATION_SCHEMA.COLUMNS} query fails.
	 */
	private java.util.List<Object[]> queryColumnsViaGetTable(BigQuery bigquery, String projectId, String datasetId,
			String tableNamePattern, String columnNamePattern) throws SQLException {
		var tables = bigquery.listTables(DatasetId.of(projectId, datasetId));
		java.util.List<Table> tablesToQuery = new java.util.ArrayList<>();
		for (Table table : tables.iterateAll()) {
			String tableName = table.getTableId().getTable();
			if (tableNamePattern == null || matchesTableNameFilter(tableName, tableNamePattern)) {
				tablesToQuery.add(table);
			}
		}
		return fetchAndProcessTablesParallel(bigquery, projectId, datasetId, tablesToQuery, columnNamePattern);
	}

	/**
	 * Builds a single JDBC {@code getColumns()} result row.
	 *
	 * <p>
	 * Centralises the 24-column layout so both the {@code INFORMATION_SCHEMA} fast
	 * path and the {@code getTable()} fallback path stay in sync.
	 */
	private static Object[] buildColumnRow(String projectId, String datasetId, String tableName, String columnName,
			int jdbcType, String typeName, int columnSize, int decimalDigits, int nullable, int ordinalPosition,
			String remarks) {
		return new Object[]{projectId, // TABLE_CAT
				datasetId, // TABLE_SCHEM
				tableName, // TABLE_NAME
				columnName, // COLUMN_NAME
				jdbcType, // DATA_TYPE
				typeName, // TYPE_NAME
				columnSize, // COLUMN_SIZE
				null, // BUFFER_LENGTH (not used)
				decimalDigits, // DECIMAL_DIGITS
				10, // NUM_PREC_RADIX
				nullable, // NULLABLE
				remarks, // REMARKS
				null, // COLUMN_DEF
				null, // SQL_DATA_TYPE (not used)
				null, // SQL_DATETIME_SUB (not used)
				columnSize, // CHAR_OCTET_LENGTH
				ordinalPosition, // ORDINAL_POSITION
				nullable == DatabaseMetaData.columnNullable ? "YES" : "NO", // IS_NULLABLE
				null, // SCOPE_CATALOG
				null, // SCOPE_SCHEMA
				null, // SCOPE_TABLE
				null, // SOURCE_DATA_TYPE
				"NO", // IS_AUTOINCREMENT
				"NO" // IS_GENERATEDCOLUMN
		};
	}

	/**
	 * Fetch and process tables in parallel within a dataset using virtual threads.
	 *
	 * <p>
	 * This provides nested parallelization: parallel across datasets AND parallel
	 * across tables within each dataset.
	 */
	private java.util.List<Object[]> fetchAndProcessTablesParallel(BigQuery bigquery, String projectId,
			String datasetId, java.util.List<Table> tablesToQuery, String columnNamePattern) throws SQLException {
		return executeInParallel(tablesToQuery,
				table -> processTableColumns(bigquery, projectId, datasetId, table, columnNamePattern),
				"Error fetching table columns in parallel");
	}

	/**
	 * Process columns for a single table.
	 *
	 * @return list of column rows for this table
	 */
	private java.util.List<Object[]> processTableColumns(BigQuery bigquery, String projectId, String datasetId,
			Table table, String columnNamePattern) throws SQLException {
		java.util.List<Object[]> rows = new java.util.ArrayList<>();

		String tableName = table.getTableId().getTable();

		// Get full table with schema
		Table fullTable = bigquery.getTable(table.getTableId());
		if (fullTable == null) {
			return rows;
		}

		Schema schema = fullTable.getDefinition().getSchema();
		if (schema == null) {
			return rows;
		}

		int ordinalPosition = 1;
		for (Field field : schema.getFields()) {
			String columnName = field.getName();

			if (columnNamePattern != null && !matchesPattern(columnName, columnNamePattern)) {
				continue;
			}

			StandardSQLTypeName type = field.getType().getStandardType();
			int jdbcType = TypeMapper.toJdbcType(field); // Use field to detect REPEATED mode
			String typeName = TypeMapper.getTypeName(field); // Use utility method for type name

			int columnSize = TypeMapper.getColumnSize(type);
			int decimalDigits = TypeMapper.getDecimalDigits(type);
			int nullable = field.getMode() == Field.Mode.REQUIRED
					? DatabaseMetaData.columnNoNulls
					: DatabaseMetaData.columnNullable;

			rows.add(buildColumnRow(projectId, datasetId, tableName, columnName, jdbcType, typeName, columnSize,
					decimalDigits, nullable, ordinalPosition, field.getDescription()));

			ordinalPosition++;
		}

		return rows;
	}

	private ResultSet executeGetColumns(String catalog, String schemaPattern, String tableNamePattern,
			String columnNamePattern) throws SQLException {
		String projectId = catalog != null ? catalog : connection.getCurrentCatalog();

		BigQuery bigquery = connection.getBigQuery();
		boolean lazyLoad = connection.getProperties().metadataLazyLoad();

		// Enhanced logging to debug IntelliJ introspection
		logger.debug(
				"getColumns() called - catalog: [{}], schemaPattern: [{}], tableNamePattern: [{}], columnNamePattern: [{}], lazyLoad: {}",
				catalog, schemaPattern, tableNamePattern, columnNamePattern, lazyLoad);

		// Lazy loading: If enabled and no specific table pattern, return empty result
		// This allows IntelliJ to load the tree structure quickly without fetching all
		// columns
		if (lazyLoad && tableNamePattern == null) {
			logger.debug(
					"Lazy loading enabled: returning empty column list (no table pattern specified) - catalog: [{}], schemaPattern: [{}]",
					catalog, schemaPattern);
			return createResultSet(MetadataColumns.Columns.COLUMN_NAMES, MetadataColumns.Columns.COLUMN_TYPES,
					new java.util.ArrayList<>());
		}

		// Get datasets matching schema pattern
		java.util.List<String> datasetIds = listDatasetsForProject(bigquery, projectId, schemaPattern);

		// Always use parallel loading for better performance with BigQuery API
		logger.debug("Using parallel loading for columns in {} datasets", datasetIds.size());
		java.util.List<Object[]> rows = queryColumnsParallel(projectId, datasetIds, tableNamePattern,
				columnNamePattern);

		rows.addAll(
				informationSchemaColumnRows(projectId, datasetIds, schemaPattern, tableNamePattern, columnNamePattern));

		return createResultSet(MetadataColumns.Columns.COLUMN_NAMES, MetadataColumns.Columns.COLUMN_TYPES, rows);
	}

	/**
	 * Per-view result schemas, resolved once and reused for the connection's life.
	 *
	 * <p>
	 * Keyed by bare view name, not by dataset: {@code sales.INFORMATION_SCHEMA
	 * .TABLES} and {@code marketing.INFORMATION_SCHEMA.TABLES} have identical
	 * columns, so resolving per dataset would multiply the cost by the dataset
	 * count for the same answer. An empty value records a view that could not be
	 * resolved, so a project without permission on {@code JOBS} does not re-probe
	 * it on every introspection pass.
	 */
	private final java.util.concurrent.ConcurrentHashMap<String, java.util.Optional<Schema>> viewSchemas = new java.util.concurrent.ConcurrentHashMap<>();

	/**
	 * The {@code getColumns()} rows for the {@code INFORMATION_SCHEMA} views in
	 * scope.
	 *
	 * <p>
	 * Unlike the listing, this cannot come from a static table: the views have
	 * dozens of columns each and Google adds to them, so a hard-coded set would be
	 * wrong within a release. Each view's schema comes instead from a <b>dry
	 * run</b> of {@code SELECT * FROM <view>}, which returns the exact current
	 * columns, creates no job and bills nothing.
	 *
	 * <p>
	 * Only views the caller's pattern selects are resolved, and each is resolved at
	 * most once per connection. Asking for every column of every table therefore
	 * costs one dry run per view the first time and none afterwards.
	 *
	 * @return the rows to append, possibly empty
	 */
	private java.util.List<Object[]> informationSchemaColumnRows(String projectId, java.util.List<String> datasetIds,
			String schemaPattern, String tableNamePattern, String columnNamePattern) throws SQLException {
		if (!includesInformationSchema()) {
			return java.util.List.of();
		}

		record Target(String schema, String tableName, String view, boolean datasetScoped) {
		}

		java.util.List<Target> targets = new java.util.ArrayList<>();
		if (schemaPattern == null || matchesPattern(InformationSchemaViews.SCHEMA_NAME, schemaPattern)) {
			for (String view : InformationSchemaViews.PROJECT_SCOPED) {
				if (tableNamePattern == null || matchesPattern(view, tableNamePattern)) {
					targets.add(new Target(InformationSchemaViews.SCHEMA_NAME, view, view, false));
				}
			}
		}
		for (String datasetId : datasetIds) {
			for (String view : InformationSchemaViews.DATASET_SCOPED) {
				String tableName = InformationSchemaViews.datasetTableName(view);
				if (tableNamePattern == null || matchesPattern(tableName, tableNamePattern)) {
					targets.add(new Target(datasetId, tableName, view, true));
				}
			}
		}
		if (targets.isEmpty()) {
			return java.util.List.of();
		}

		// One dataset stands in for all of them, because the schema does not vary by
		// dataset. Resolution needs *a* dataset only to name something that exists.
		String sampleDataset = datasetIds.isEmpty() ? null : datasetIds.get(0);

		return executeInParallel(targets, target -> {
			Schema schema = resolveViewSchema(projectId, sampleDataset, target.view(), target.datasetScoped());
			if (schema == null) {
				return java.util.List.<Object[]>of();
			}
			return columnRowsFor(projectId, target.schema(), target.tableName(), schema, columnNamePattern);
		}, "Error resolving INFORMATION_SCHEMA columns in parallel");
	}

	/**
	 * Resolves one view's columns with a dry run, memoised across calls.
	 *
	 * @return the view's schema, or null when it could not be resolved — which is
	 *         ordinary for the job and session views, whose project-level roles
	 *         most callers do not hold
	 */
	private Schema resolveViewSchema(String projectId, String sampleDataset, String view, boolean datasetScoped) {
		if (datasetScoped && sampleDataset == null) {
			return null;
		}
		return viewSchemas.computeIfAbsent(view, key -> {
			String fqn = datasetScoped
					? InformationSchemaViews.datasetScopedName(projectId, sampleDataset, key)
					: InformationSchemaViews.projectScopedName(projectId, key);
			try {
				QueryJobConfiguration config = QueryJobConfiguration.newBuilder("SELECT * FROM " + fqn).setDryRun(true)
						.setUseQueryCache(false).build();
				JobStatistics.QueryStatistics statistics = connection.getBigQuery().create(JobInfo.of(config))
						.getStatistics();
				return java.util.Optional.ofNullable(statistics.getSchema());
			} catch (RuntimeException e) {
				// Debug, not warn: a caller without bigquery.jobs.listAll cannot read
				// JOBS, and that is the normal case rather than a defect. The view is
				// still listed by getTables — it exists, it just cannot be described.
				logger.debug("Could not resolve columns for {}: {}", fqn, e.getMessage());
				return java.util.Optional.empty();
			}
		}).orElse(null);
	}

	/** Maps a BigQuery schema to {@code getColumns()} rows for one table. */
	private java.util.List<Object[]> columnRowsFor(String projectId, String schemaName, String tableName, Schema schema,
			String columnNamePattern) {
		java.util.List<Object[]> rows = new java.util.ArrayList<>();
		int ordinalPosition = 1;
		for (Field field : schema.getFields()) {
			String columnName = field.getName();
			if (columnNamePattern != null && !matchesPattern(columnName, columnNamePattern)) {
				ordinalPosition++;
				continue;
			}
			StandardSQLTypeName type = field.getType().getStandardType();
			int nullable = field.getMode() == Field.Mode.REQUIRED
					? DatabaseMetaData.columnNoNulls
					: DatabaseMetaData.columnNullable;
			rows.add(buildColumnRow(projectId, schemaName, tableName, columnName, TypeMapper.toJdbcType(field),
					TypeMapper.getTypeName(field), TypeMapper.getColumnSize(type), TypeMapper.getDecimalDigits(type),
					nullable, ordinalPosition, field.getDescription()));
			ordinalPosition++;
		}
		return rows;
	}

	@Override
	public ResultSet getColumnPrivileges(String catalog, String schema, String table, String columnNamePattern)
			throws SQLException {
		checkClosed();
		logger.debug("getColumnPrivileges() called - not applicable to BigQuery (uses IAM), returning empty result");
		return createResultSet(MetadataColumns.ColumnPrivileges.COLUMN_NAMES,
				MetadataColumns.ColumnPrivileges.COLUMN_TYPES, new java.util.ArrayList<>());
	}

	@Override
	public ResultSet getTablePrivileges(String catalog, String schemaPattern, String tableNamePattern)
			throws SQLException {
		checkClosed();
		logger.debug("getTablePrivileges() called - not applicable to BigQuery (uses IAM), returning empty result");
		return createResultSet(MetadataColumns.TablePrivileges.COLUMN_NAMES,
				MetadataColumns.TablePrivileges.COLUMN_TYPES, new java.util.ArrayList<>());
	}

	@Override
	public ResultSet getBestRowIdentifier(String catalog, String schema, String table, int scope, boolean nullable)
			throws SQLException {
		checkClosed();
		// Not "BigQuery has no primary keys" — since #84 it does, and getPrimaryKeys()
		// reports them. A best row identifier is a column set that uniquely identifies
		// a row, and BigQuery never enforces its keys, so nothing here can be promised
		// to be unique. Reporting a candidate that turns out to be duplicated is worse
		// than reporting none.
		logger.debug("getBestRowIdentifier() called - BigQuery enforces no uniqueness, returning empty result");
		return createResultSet(MetadataColumns.BestRowIdentifier.COLUMN_NAMES,
				MetadataColumns.BestRowIdentifier.COLUMN_TYPES, new java.util.ArrayList<>());
	}

	@Override
	public ResultSet getVersionColumns(String catalog, String schema, String table) throws SQLException {
		checkClosed();
		logger.debug(
				"getVersionColumns() called - not applicable to BigQuery (no row versioning), returning empty result");
		return createResultSet(MetadataColumns.VersionColumns.COLUMN_NAMES, MetadataColumns.VersionColumns.COLUMN_TYPES,
				new java.util.ArrayList<>());
	}

	/**
	 * Retrieves the primary key of a table.
	 *
	 * <p>
	 * BigQuery primary keys are declared {@code NOT ENFORCED} and are never
	 * validated — see {@link KeyConstraints} for what that means for a caller. They
	 * are read from the dataset's {@code INFORMATION_SCHEMA.TABLE_CONSTRAINTS} and
	 * {@code KEY_COLUMN_USAGE} views.
	 *
	 * <p>
	 * {@code PK_NAME} is BigQuery's own constraint name, which it qualifies with
	 * the table and which is always {@code 
	 * 
	<table>
	 * .pk$} — BigQuery does not accept a name for a primary key.
	 *
	 * <p>
	 * Per the JDBC contract, {@code schema} and {@code table} are names rather than
	 * patterns and are matched exactly; {@code _} in a name is a literal
	 * underscore, not a wildcard. A null {@code schema} searches every dataset in
	 * the project.
	 *
	 * @param catalog
	 *            project ID (null = current project)
	 * @param schema
	 *            dataset name (null = all datasets)
	 * @param table
	 *            table name (null = all tables)
	 * @return ResultSet with primary key columns, ordered by TABLE_SCHEM,
	 *         TABLE_NAME, COLUMN_NAME
	 * @throws SQLException
	 *             if the connection is closed or the metadata query fails
	 */
	@Override
	public ResultSet getPrimaryKeys(String catalog, String schema, String table) throws SQLException {
		checkClosed();

		logger.debug("getPrimaryKeys() called - catalog: [{}], schema: [{}], table: [{}]", catalog, schema, table);

		String projectId = catalog != null ? catalog : connection.getCurrentCatalog();
		java.util.List<KeyConstraints.Constraint> constraints = loadConstraints(projectId,
				datasetsToScan(projectId, schema));

		java.util.List<Object[]> rows = KeyConstraints.primaryKeyRows(projectId,
				constraints.stream().filter(c -> c.primaryKey() && matchesName(c.table(), table)).toList());

		logger.debug("getPrimaryKeys() returning {} column(s)", rows.size());
		return createResultSet(MetadataColumns.PrimaryKeys.COLUMN_NAMES, MetadataColumns.PrimaryKeys.COLUMN_TYPES,
				rows);
	}

	/**
	 * Retrieves the foreign keys declared on a table, describing the tables it
	 * references.
	 *
	 * <p>
	 * BigQuery foreign keys are declared {@code NOT ENFORCED} and are never
	 * validated; {@code UPDATE_RULE}, {@code DELETE_RULE} and {@code DEFERRABILITY}
	 * reflect that. See {@link KeyConstraints}.
	 *
	 * <p>
	 * A foreign key may reference a table in another dataset. The parent's primary
	 * key is fetched from that dataset when needed, so composite keys are paired
	 * correctly across datasets.
	 *
	 * @param catalog
	 *            project ID (null = current project)
	 * @param schema
	 *            dataset holding the referencing table (null = all datasets)
	 * @param table
	 *            the referencing table (null = all tables)
	 * @return ResultSet ordered by PKTABLE_CAT, PKTABLE_SCHEM, PKTABLE_NAME,
	 *         KEY_SEQ
	 * @throws SQLException
	 *             if the connection is closed or the metadata query fails
	 */
	@Override
	public ResultSet getImportedKeys(String catalog, String schema, String table) throws SQLException {
		checkClosed();

		logger.debug("getImportedKeys() called - catalog: [{}], schema: [{}], table: [{}]", catalog, schema, table);

		String projectId = catalog != null ? catalog : connection.getCurrentCatalog();
		java.util.List<KeyConstraints.Constraint> constraints = loadConstraints(projectId,
				datasetsToScan(projectId, schema));

		java.util.List<KeyConstraints.Constraint> foreignKeys = constraints.stream()
				.filter(c -> !c.primaryKey() && matchesName(c.table(), table)).toList();

		return foreignKeyResult(projectId, constraints, foreignKeys, false);
	}

	/**
	 * Retrieves the foreign keys that reference a table.
	 *
	 * <p>
	 * A foreign key is recorded only in the {@code INFORMATION_SCHEMA} views of the
	 * dataset holding the <em>referencing</em> table, so answering this means
	 * reading every dataset in the project rather than just the one named by
	 * {@code schema}, which identifies the referenced table. Per-dataset results
	 * are cached, so the cost is paid once per dataset per TTL window rather than
	 * once per table asked about.
	 *
	 * <p>
	 * Foreign keys declared in <em>other projects</em> are not found: the scan
	 * covers one project's datasets, and BigQuery offers no cross-project index of
	 * constraints.
	 *
	 * @param catalog
	 *            project ID (null = current project)
	 * @param schema
	 *            dataset holding the referenced table (null = all datasets)
	 * @param table
	 *            the referenced table (null = all tables)
	 * @return ResultSet ordered by FKTABLE_CAT, FKTABLE_SCHEM, FKTABLE_NAME,
	 *         KEY_SEQ
	 * @throws SQLException
	 *             if the connection is closed or the metadata query fails
	 */
	@Override
	public ResultSet getExportedKeys(String catalog, String schema, String table) throws SQLException {
		checkClosed();

		logger.debug("getExportedKeys() called - catalog: [{}], schema: [{}], table: [{}]", catalog, schema, table);

		String projectId = catalog != null ? catalog : connection.getCurrentCatalog();
		java.util.List<KeyConstraints.Constraint> constraints = loadConstraints(projectId, allDatasets(projectId));

		java.util.List<KeyConstraints.Constraint> foreignKeys = constraints.stream().filter(c -> !c.primaryKey()
				&& matchesName(c.referencedSchema(), schema) && matchesName(c.referencedTable(), table)).toList();

		return foreignKeyResult(projectId, constraints, foreignKeys, true);
	}

	/**
	 * Retrieves the foreign keys on one table that reference another.
	 *
	 * <p>
	 * The constraint is recorded in the foreign-key side's dataset, so
	 * {@code foreignSchema} narrows which datasets are read while
	 * {@code parentSchema} and {@code parentTable} filter the referenced side.
	 *
	 * @param parentCatalog
	 *            project of the referenced table (unused; BigQuery reports the
	 *            referenced table's own project)
	 * @param parentSchema
	 *            dataset of the referenced table (null = any)
	 * @param parentTable
	 *            the referenced table (null = any)
	 * @param foreignCatalog
	 *            project of the referencing table (null = current project)
	 * @param foreignSchema
	 *            dataset of the referencing table (null = all datasets)
	 * @param foreignTable
	 *            the referencing table (null = all tables)
	 * @return ResultSet ordered by FKTABLE_CAT, FKTABLE_SCHEM, FKTABLE_NAME,
	 *         KEY_SEQ
	 * @throws SQLException
	 *             if the connection is closed or the metadata query fails
	 */
	@Override
	public ResultSet getCrossReference(String parentCatalog, String parentSchema, String parentTable,
			String foreignCatalog, String foreignSchema, String foreignTable) throws SQLException {
		checkClosed();

		logger.debug("getCrossReference() called - parent: [{}].[{}].[{}], foreign: [{}].[{}].[{}]", parentCatalog,
				parentSchema, parentTable, foreignCatalog, foreignSchema, foreignTable);

		String projectId = foreignCatalog != null ? foreignCatalog : connection.getCurrentCatalog();
		java.util.List<KeyConstraints.Constraint> constraints = loadConstraints(projectId,
				datasetsToScan(projectId, foreignSchema));

		java.util.List<KeyConstraints.Constraint> foreignKeys = constraints.stream()
				.filter(c -> !c.primaryKey() && matchesName(c.table(), foreignTable)
						&& matchesName(c.referencedSchema(), parentSchema)
						&& matchesName(c.referencedTable(), parentTable))
				.toList();

		return foreignKeyResult(projectId, constraints, foreignKeys, true);
	}

	/**
	 * Shared tail of the three foreign-key methods: resolve the referenced columns,
	 * then shape the rows.
	 *
	 * @param projectId
	 *            project the foreign-key side lives in
	 * @param scanned
	 *            every constraint read during the scan, the source of the primary
	 *            keys already in hand
	 * @param foreignKeys
	 *            the foreign keys the caller asked about
	 * @param orderByForeignKeyTable
	 *            true for the {@code getExportedKeys}/{@code getCrossReference}
	 *            ordering, false for {@code getImportedKeys}
	 */
	private ResultSet foreignKeyResult(String projectId, java.util.List<KeyConstraints.Constraint> scanned,
			java.util.List<KeyConstraints.Constraint> foreignKeys, boolean orderByForeignKeyTable) throws SQLException {
		KeyConstraints.PrimaryKeyIndex index = new KeyConstraints.PrimaryKeyIndex();
		index.addAll(projectId, scanned);
		resolveReferencedPrimaryKeys(foreignKeys, index);

		java.util.List<Object[]> rows = KeyConstraints.foreignKeyRows(projectId, foreignKeys, index,
				orderByForeignKeyTable);

		logger.debug("Returning {} foreign key column(s)", rows.size());
		return createResultSet(MetadataColumns.ForeignKeys.COLUMN_NAMES, MetadataColumns.ForeignKeys.COLUMN_TYPES,
				rows);
	}

	/**
	 * Loads the primary keys of any referenced table the scan did not already
	 * cover.
	 *
	 * <p>
	 * A foreign key may point into a dataset — or a project — that was never
	 * scanned, and its parent's primary key is what gives the referenced column
	 * names their order. Without this, every cross-dataset foreign key would fall
	 * back to the single-column special case and composite ones would report a null
	 * {@code PKCOLUMN_NAME}.
	 */
	private void resolveReferencedPrimaryKeys(java.util.List<KeyConstraints.Constraint> foreignKeys,
			KeyConstraints.PrimaryKeyIndex index) throws SQLException {
		// LinkedHashMap keyed by project so each missing dataset is fetched once even
		// when a dozen foreign keys point at the same parent.
		java.util.Map<String, java.util.Set<String>> missing = new java.util.LinkedHashMap<>();
		for (KeyConstraints.Constraint fk : foreignKeys) {
			String parent = fk.qualifiedReferencedTable();
			if (parent == null || index.covers(parent) || fk.referencedSchema() == null) {
				continue;
			}
			String parentProject = fk.referencedCatalog() != null
					? fk.referencedCatalog()
					: connection.getCurrentCatalog();
			missing.computeIfAbsent(parentProject, ignored -> new java.util.LinkedHashSet<>())
					.add(fk.referencedSchema());
		}

		for (java.util.Map.Entry<String, java.util.Set<String>> entry : missing.entrySet()) {
			String parentProject = entry.getKey();
			java.util.List<KeyConstraints.Constraint> parents = loadConstraints(parentProject,
					java.util.List.copyOf(entry.getValue()));
			index.addAll(parentProject, parents);
		}
	}

	/**
	 * Datasets to read for a call whose {@code schema} argument names the dataset
	 * holding the constraint.
	 *
	 * <p>
	 * A named schema is used directly rather than matched against a listing. JDBC
	 * specifies these arguments as names, not patterns, so an underscore in a
	 * dataset name is a literal — and BigQuery dataset names are full of
	 * underscores. Taking the name at face value is both correct and one API call
	 * cheaper.
	 *
	 * <p>
	 * An empty string means "without a schema", which no BigQuery table can be, so
	 * nothing is read.
	 */
	private java.util.List<String> datasetsToScan(String projectId, String schema) {
		if (schema == null) {
			return allDatasets(projectId);
		}
		if (schema.isEmpty()) {
			return java.util.List.of();
		}
		return java.util.List.of(schema);
	}

	private java.util.List<String> allDatasets(String projectId) {
		return listDatasetsForProject(connection.getBigQuery(), projectId, null);
	}

	/** Exact, case-sensitive match; a null filter matches everything. */
	private static boolean matchesName(String value, String filter) {
		return filter == null || filter.equals(value);
	}

	/**
	 * Reads the key constraints of several datasets, one query each, in parallel.
	 *
	 * @param projectId
	 *            the project owning the datasets
	 * @param datasetIds
	 *            the datasets to read
	 * @return every constraint found; datasets that could not be read contribute
	 *         nothing
	 * @throws SQLException
	 *             if the parallel scan itself fails
	 */
	private java.util.List<KeyConstraints.Constraint> loadConstraints(String projectId,
			java.util.List<String> datasetIds) throws SQLException {
		if (datasetIds.isEmpty()) {
			return java.util.List.of();
		}
		java.util.List<Object[]> rows = executeInParallel(datasetIds,
				datasetId -> loadConstraintSnapshot(projectId, datasetId),
				"Error querying key constraints in parallel");
		return KeyConstraints.assemble(rows);
	}

	/**
	 * The cached, per-dataset constraint snapshot.
	 *
	 * <p>
	 * Caching is keyed by dataset rather than by the arguments of the call that
	 * asked, because all four key methods are answered from the same read. An IDE
	 * introspecting a dataset calls {@code getPrimaryKeys} and
	 * {@code getImportedKeys} once per table; keyed by table, that would be two
	 * BigQuery queries per table instead of one per dataset.
	 *
	 * <p>
	 * This goes to {@link MetadataCache} directly rather than through
	 * {@link #getCachedOrExecute}, which runs on the calling thread and keeps
	 * non-atomic hit/miss counters. Snapshots are loaded from the parallel scan, so
	 * they would race those counters; {@code MetadataCache} itself is thread-safe
	 * and still records the hit or miss in {@code DriverMetrics}.
	 *
	 * <p>
	 * It uses the cache's row-level API rather than its {@code ResultSet} one. A
	 * snapshot is reshaped into four different JDBC result sets and is never handed
	 * to a caller as-is, so wrapping it would create a closeable that nobody owns,
	 * opens or closes on either the read or the write path.
	 */
	private java.util.List<Object[]> loadConstraintSnapshot(String projectId, String datasetId) {
		String key = "constraints:" + projectId + ":" + datasetId;

		if (cache != null) {
			java.util.Optional<java.util.List<Object[]>> cached = cache.getRows(key);
			if (cached.isPresent()) {
				return cached.get();
			}
		}

		java.util.Optional<java.util.List<Object[]>> rows = queryConstraintsForDataset(projectId, datasetId);

		// Only a successful read is cached. A dataset that genuinely declares no
		// constraints still caches its empty snapshot — that is the common case and
		// what makes repeat introspection free — but a read that failed must not
		// install "this dataset has no keys" for the rest of the TTL window.
		if (cache != null && rows.isPresent()) {
			cache.putRows(key, KeyConstraints.SNAPSHOT_COLUMN_NAMES, KeyConstraints.SNAPSHOT_COLUMN_TYPES, rows.get());
		}
		return rows.orElseGet(java.util.List::of);
	}

	/**
	 * Reads one dataset's key constraints.
	 *
	 * <p>
	 * Degrades per dataset like the {@code ROUTINES} and {@code PARAMETERS} queries
	 * above: a dataset the caller cannot read, or an endpoint without the
	 * constraint views, contributes no rows instead of failing the whole call. The
	 * cause is logged rather than presented as an expected limitation.
	 *
	 * @return the rows read, or empty if the read failed — which is not the same
	 *         answer as a dataset that declares no constraints, and only the latter
	 *         may be cached
	 */
	private java.util.Optional<java.util.List<Object[]>> queryConstraintsForDataset(String projectId,
			String datasetId) {
		if (rejectsUnsafeIdentifiers(projectId, datasetId, "key constraints")) {
			return java.util.Optional.empty();
		}

		java.util.List<Object[]> rows = new java.util.ArrayList<>();
		try {
			BigQuery bigquery = connection.getBigQuery();
			QueryJobConfiguration config = QueryJobConfiguration
					.newBuilder(KeyConstraints.constraintQuery(projectId, datasetId)).build();
			TableResult result = bigquery.query(config);
			for (FieldValueList row : result.iterateAll()) {
				rows.add(KeyConstraints.snapshotRow(row, datasetId));
			}
		} catch (InterruptedException e) {
			// Swallowing this along with everything else would leave the thread's
			// interrupt flag cleared, and these run on the virtual threads of the
			// parallel scan — whoever asked for the cancellation would never see it.
			Thread.currentThread().interrupt();
			logger.warn("Interrupted reading key constraints for dataset {}.{}", projectId, datasetId);
			return java.util.Optional.empty();
		} catch (Exception e) {
			logger.warn("Could not query key constraints for dataset {}.{}: {}", projectId, datasetId, e.getMessage());
			return java.util.Optional.empty();
		}
		return java.util.Optional.of(rows);
	}

	@Override
	public ResultSet getTypeInfo() throws SQLException {
		checkClosed();

		logger.debug("getTypeInfo() called");

		java.util.List<Object[]> rows = new java.util.ArrayList<>();

		// Add BigQuery data types
		// Format: TYPE_NAME, DATA_TYPE, PRECISION, LITERAL_PREFIX, LITERAL_SUFFIX,
		// CREATE_PARAMS, NULLABLE, CASE_SENSITIVE, SEARCHABLE, UNSIGNED_ATTRIBUTE,
		// FIXED_PREC_SCALE, AUTO_INCREMENT, LOCAL_TYPE_NAME, MINIMUM_SCALE,
		// MAXIMUM_SCALE,
		// SQL_DATA_TYPE, SQL_DATETIME_SUB, NUM_PREC_RADIX

		// BOOL
		rows.add(
				createTypeInfoRow("BOOL", java.sql.Types.BOOLEAN, 1, null, null, null, true, false, true, null, false));

		// INT64
		rows.add(createTypeInfoRow("INT64", java.sql.Types.BIGINT, 19, null, null, null, true, false, true, false,
				false));

		// FLOAT64
		rows.add(createTypeInfoRow("FLOAT64", java.sql.Types.DOUBLE, 15, null, null, null, true, false, true, false,
				false));

		// NUMERIC
		rows.add(createTypeInfoRow("NUMERIC", java.sql.Types.DECIMAL, 38, null, null, "precision,scale", true, false,
				true, false, false));

		// BIGNUMERIC
		rows.add(createTypeInfoRow("BIGNUMERIC", java.sql.Types.DECIMAL, 76, null, null, "precision,scale", true, false,
				true, false, false));

		// STRING
		rows.add(createTypeInfoRow("STRING", java.sql.Types.VARCHAR, 1024 * 1024, "'", "'", "length", true, true, true,
				null, false));

		// BYTES
		rows.add(createTypeInfoRow("BYTES", java.sql.Types.VARBINARY, 1024 * 1024, "B'", "'", "length", true, true,
				true, null, false));

		// DATE
		rows.add(createTypeInfoRow("DATE", java.sql.Types.DATE, 10, "'", "'", null, true, false, true, null, false));

		// DATETIME
		rows.add(createTypeInfoRow("DATETIME", java.sql.Types.TIMESTAMP, 27, "'", "'", null, true, false, true, null,
				false));

		// TIME
		rows.add(createTypeInfoRow("TIME", java.sql.Types.TIME, 15, "'", "'", null, true, false, true, null, false));

		// TIMESTAMP
		rows.add(createTypeInfoRow("TIMESTAMP", java.sql.Types.TIMESTAMP, 27, "'", "'", null, true, false, true, null,
				false));

		// GEOGRAPHY
		rows.add(createTypeInfoRow("GEOGRAPHY", java.sql.Types.VARCHAR, 1024 * 1024, "'", "'", null, true, true, true,
				null, false));

		// JSON
		rows.add(createTypeInfoRow("JSON", java.sql.Types.VARCHAR, 1024 * 1024, "'", "'", null, true, true, true, null,
				false));

		// ARRAY
		rows.add(createTypeInfoRow("ARRAY", java.sql.Types.ARRAY, 1024 * 1024, "[", "]", "element_type", true, true,
				true, null, false));

		// STRUCT
		rows.add(createTypeInfoRow("STRUCT", java.sql.Types.STRUCT, 1024 * 1024, "STRUCT(", ")", "field_list", true,
				true, true, null, false));

		logger.debug("getTypeInfo() returning {} type(s)", rows.size());

		return createResultSet(
				new String[]{"TYPE_NAME", "DATA_TYPE", "PRECISION", "LITERAL_PREFIX", "LITERAL_SUFFIX", "CREATE_PARAMS",
						"NULLABLE", "CASE_SENSITIVE", "SEARCHABLE", "UNSIGNED_ATTRIBUTE", "FIXED_PREC_SCALE",
						"AUTO_INCREMENT", "LOCAL_TYPE_NAME", "MINIMUM_SCALE", "MAXIMUM_SCALE", "SQL_DATA_TYPE",
						"SQL_DATETIME_SUB", "NUM_PREC_RADIX"},
				new int[]{java.sql.Types.VARCHAR, // TYPE_NAME
						java.sql.Types.INTEGER, // DATA_TYPE
						java.sql.Types.INTEGER, // PRECISION
						java.sql.Types.VARCHAR, // LITERAL_PREFIX
						java.sql.Types.VARCHAR, // LITERAL_SUFFIX
						java.sql.Types.VARCHAR, // CREATE_PARAMS
						java.sql.Types.SMALLINT, // NULLABLE
						java.sql.Types.BOOLEAN, // CASE_SENSITIVE
						java.sql.Types.SMALLINT, // SEARCHABLE
						java.sql.Types.BOOLEAN, // UNSIGNED_ATTRIBUTE
						java.sql.Types.BOOLEAN, // FIXED_PREC_SCALE
						java.sql.Types.BOOLEAN, // AUTO_INCREMENT
						java.sql.Types.VARCHAR, // LOCAL_TYPE_NAME
						java.sql.Types.SMALLINT, // MINIMUM_SCALE
						java.sql.Types.SMALLINT, // MAXIMUM_SCALE
						java.sql.Types.INTEGER, // SQL_DATA_TYPE
						java.sql.Types.INTEGER, // SQL_DATETIME_SUB
						java.sql.Types.INTEGER // NUM_PREC_RADIX
				}, rows);
	}

	/**
	 * Helper method to create a type info row.
	 *
	 * @param typeName
	 *            the SQL type name
	 * @param dataType
	 *            the JDBC data type
	 * @param precision
	 *            the maximum precision
	 * @param literalPrefix
	 *            prefix for literals
	 * @param literalSuffix
	 *            suffix for literals
	 * @param createParams
	 *            parameters for creation
	 * @param nullable
	 *            whether nullable
	 * @param caseSensitive
	 *            whether case sensitive
	 * @param searchable
	 *            whether searchable
	 * @param unsigned
	 *            whether unsigned
	 * @param fixedPrecScale
	 *            whether fixed precision/scale
	 * @return row data
	 */
	private Object[] createTypeInfoRow(String typeName, int dataType, int precision, String literalPrefix,
			String literalSuffix, String createParams, boolean nullable, boolean caseSensitive, boolean searchable,
			Boolean unsigned, boolean fixedPrecScale) {
		return new Object[]{typeName, // TYPE_NAME
				dataType, // DATA_TYPE
				precision, // PRECISION
				literalPrefix, // LITERAL_PREFIX
				literalSuffix, // LITERAL_SUFFIX
				createParams, // CREATE_PARAMS
				nullable ? DatabaseMetaData.typeNullable : DatabaseMetaData.typeNoNulls, // NULLABLE
				caseSensitive, // CASE_SENSITIVE
				searchable ? DatabaseMetaData.typeSearchable : DatabaseMetaData.typePredNone, // SEARCHABLE
				unsigned, // UNSIGNED_ATTRIBUTE
				fixedPrecScale, // FIXED_PREC_SCALE
				false, // AUTO_INCREMENT
				typeName, // LOCAL_TYPE_NAME
				(short) 0, // MINIMUM_SCALE
				(short) 9, // MAXIMUM_SCALE
				null, // SQL_DATA_TYPE
				null, // SQL_DATETIME_SUB
				10 // NUM_PREC_RADIX
		};
	}

	@Override
	public ResultSet getIndexInfo(String catalog, String schema, String table, boolean unique, boolean approximate)
			throws SQLException {
		checkClosed();
		logger.debug("getIndexInfo() called - not applicable to BigQuery (no indexes), returning empty result");
		return createResultSet(MetadataColumns.IndexInfo.COLUMN_NAMES, MetadataColumns.IndexInfo.COLUMN_TYPES,
				new java.util.ArrayList<>());
	}

	@Override
	public boolean supportsResultSetType(int type) throws SQLException {
		return type == ResultSet.TYPE_FORWARD_ONLY;
	}

	@Override
	public boolean supportsResultSetConcurrency(int type, int concurrency) throws SQLException {
		return type == ResultSet.TYPE_FORWARD_ONLY && concurrency == ResultSet.CONCUR_READ_ONLY;
	}

	@Override
	public boolean ownUpdatesAreVisible(int type) throws SQLException {
		return false;
	}

	@Override
	public boolean ownDeletesAreVisible(int type) throws SQLException {
		return false;
	}

	@Override
	public boolean ownInsertsAreVisible(int type) throws SQLException {
		return false;
	}

	@Override
	public boolean othersUpdatesAreVisible(int type) throws SQLException {
		return false;
	}

	@Override
	public boolean othersDeletesAreVisible(int type) throws SQLException {
		return false;
	}

	@Override
	public boolean othersInsertsAreVisible(int type) throws SQLException {
		return false;
	}

	@Override
	public boolean updatesAreDetected(int type) throws SQLException {
		return false;
	}

	@Override
	public boolean deletesAreDetected(int type) throws SQLException {
		return false;
	}

	@Override
	public boolean insertsAreDetected(int type) throws SQLException {
		return false;
	}

	/**
	 * Returns {@code true}: the driver supports JDBC batch updates.
	 *
	 * <p>
	 * {@code PreparedStatement} batches over simple parameterized INSERTs are
	 * collapsed into multi-row {@code INSERT ... VALUES (...), (...)} query jobs;
	 * other batched statements execute sequentially, one job per entry.
	 */
	@Override
	public boolean supportsBatchUpdates() throws SQLException {
		return true;
	}

	@Override
	public ResultSet getUDTs(String catalog, String schemaPattern, String typeNamePattern, int[] types)
			throws SQLException {
		checkClosed();
		logger.debug("getUDTs() called - not applicable to BigQuery, returning empty result");
		return createResultSet(MetadataColumns.UDTs.COLUMN_NAMES, MetadataColumns.UDTs.COLUMN_TYPES,
				new java.util.ArrayList<>());
	}

	@Override
	public Connection getConnection() throws SQLException {
		return connection;
	}

	@Override
	public boolean supportsSavepoints() throws SQLException {
		return false;
	}

	@Override
	public boolean supportsNamedParameters() throws SQLException {
		// PreparedStatement binds positional ? placeholders only, and there is no
		// CallableStatement, so a tool that trusted this would generate SQL the
		// driver cannot execute.
		return false;
	}

	@Override
	public boolean supportsMultipleOpenResults() throws SQLException {
		return false;
	}

	@Override
	public boolean supportsGetGeneratedKeys() throws SQLException {
		return false;
	}

	@Override
	public ResultSet getSuperTypes(String catalog, String schemaPattern, String typeNamePattern) throws SQLException {
		checkClosed();
		logger.debug("getSuperTypes() called - not applicable to BigQuery, returning empty result");
		return createResultSet(MetadataColumns.SuperTypes.COLUMN_NAMES, MetadataColumns.SuperTypes.COLUMN_TYPES,
				new java.util.ArrayList<>());
	}

	@Override
	public ResultSet getSuperTables(String catalog, String schemaPattern, String tableNamePattern) throws SQLException {
		checkClosed();
		logger.debug("getSuperTables() called - not applicable to BigQuery, returning empty result");
		return createResultSet(MetadataColumns.SuperTables.COLUMN_NAMES, MetadataColumns.SuperTables.COLUMN_TYPES,
				new java.util.ArrayList<>());
	}

	/**
	 * Returns an empty result set: BigQuery has no user-defined types, so there are
	 * no attributes to describe.
	 *
	 * <p>
	 * Empty is the answer JDBC specifies here, not a failure. This used to throw
	 * {@code SQLFeatureNotSupportedException}, which tools that call it during
	 * connection setup read as a broken driver rather than an absent feature.
	 *
	 * @since 3.1.0
	 */
	@Override
	public ResultSet getAttributes(String catalog, String schemaPattern, String typeNamePattern,
			String attributeNamePattern) throws SQLException {
		checkClosed();
		logger.debug("getAttributes() called - BigQuery has no user-defined types, returning empty result");
		return createResultSet(MetadataColumns.Attributes.COLUMN_NAMES, MetadataColumns.Attributes.COLUMN_TYPES,
				new java.util.ArrayList<>());
	}

	@Override
	public boolean supportsResultSetHoldability(int holdability) throws SQLException {
		return holdability == ResultSet.CLOSE_CURSORS_AT_COMMIT;
	}

	@Override
	public int getResultSetHoldability() throws SQLException {
		return ResultSet.CLOSE_CURSORS_AT_COMMIT;
	}

	@Override
	public int getDatabaseMajorVersion() throws SQLException {
		return 2;
	}

	@Override
	public int getDatabaseMinorVersion() throws SQLException {
		return 0;
	}

	@Override
	public int getJDBCMajorVersion() throws SQLException {
		return 4;
	}

	@Override
	public int getJDBCMinorVersion() throws SQLException {
		return 3;
	}

	@Override
	public int getSQLStateType() throws SQLException {
		return sqlStateSQL;
	}

	@Override
	public boolean locatorsUpdateCopy() throws SQLException {
		return false;
	}

	@Override
	public boolean supportsStatementPooling() throws SQLException {
		return false;
	}

	@Override
	public RowIdLifetime getRowIdLifetime() throws SQLException {
		return RowIdLifetime.ROWID_UNSUPPORTED;
	}

	/**
	 * Retrieves schema (dataset) metadata for the specified catalog and pattern.
	 *
	 * <p>
	 * In BigQuery terminology, schemas correspond to datasets. This method returns
	 * a ResultSet with the following columns:
	 * <ol>
	 * <li>TABLE_SCHEM (String) - Dataset ID
	 * <li>TABLE_CATALOG (String) - Project ID
	 * </ol>
	 *
	 * <p>
	 * <b>Pattern Matching:</b> The schemaPattern supports SQL LIKE syntax:
	 * <ul>
	 * <li>{@code %} - matches any sequence of zero or more characters
	 * <li>{@code _} - matches any single character
	 * <li>{@code null} - matches all datasets (no filtering)
	 * </ul>
	 *
	 * <p>
	 * <b>Performance:</b> Results are cached based on {@code metadataCacheTtl}
	 * connection property (default: 300 seconds). The cache is shared across
	 * connections to the same project and persists across connection open/close
	 * cycles.
	 *
	 * <p>
	 * <b>Example:</b>
	 *
	 * <pre>{@code
	 * ResultSet rs = metadata.getSchemas(null, "prod_%");
	 * while (rs.next()) {
	 * 	String datasetId = rs.getString("TABLE_SCHEM");
	 * 	String projectId = rs.getString("TABLE_CATALOG");
	 * }
	 * }</pre>
	 *
	 * @param catalog
	 *            project ID (null = current project)
	 * @param schemaPattern
	 *            dataset pattern (supports SQL LIKE: % and _)
	 * @return ResultSet with schema metadata, sorted by TABLE_CATALOG and
	 *         TABLE_SCHEM
	 * @throws SQLException
	 *             if connection is closed or query fails
	 */
	@Override
	public ResultSet getSchemas(String catalog, String schemaPattern) throws SQLException {
		checkClosed();

		logger.debug("getSchemas() called - catalog: [{}], schemaPattern: [{}]", catalog, schemaPattern);

		String cacheKey = "schemas:" + catalog + ":" + schemaPattern;

		return getCachedOrExecute(cacheKey, () -> {
			String projectId = catalog != null ? catalog : connection.getCurrentCatalog();
			BigQuery bigquery = connection.getBigQuery();

			java.util.List<String> datasetIds = listDatasetsForProject(bigquery, projectId, schemaPattern);
			java.util.List<Object[]> rows = new java.util.ArrayList<>(datasetIds.size() + 1);
			for (String datasetId : datasetIds) {
				rows.add(new Object[]{datasetId, projectId});
			}

			// Appended rather than merged into the loop above: listDatasets does not
			// report it and neither does BigQuery's own INFORMATION_SCHEMA.SCHEMATA, so
			// it is not a dataset that happened to be missed — it is a schema this
			// driver injects, and keeping it out of the dataset path makes that obvious.
			if (includesInformationSchema()
					&& (schemaPattern == null || matchesPattern(InformationSchemaViews.SCHEMA_NAME, schemaPattern))) {
				rows.add(new Object[]{InformationSchemaViews.SCHEMA_NAME, projectId});
			}

			// Register schemas for adaptive pre-warming: fires speculative IS queries
			// in the background so subsequent IntelliJ introspection passes hit cache.
			if (cache != null && "%".equals(schemaPattern)) {
				cache.registerKnownSchemas(datasetIds);
			}

			logger.debug("getSchemas() returning {} schema(s)", rows.size());

			return createResultSet(MetadataColumns.Schemas.COLUMN_NAMES, MetadataColumns.Schemas.COLUMN_TYPES, rows);
		});
	}

	@Override
	public boolean supportsStoredFunctionsUsingCallSyntax() throws SQLException {
		return false;
	}

	@Override
	public boolean autoCommitFailureClosesAllResultSets() throws SQLException {
		return false;
	}

	/**
	 * Returns an empty result set: the driver supports no client-info properties.
	 *
	 * <p>
	 * That is an empty result, not an unsupported operation —
	 * {@code Connection.setClientInfo} accordingly ignores whatever it is handed
	 * and {@code getClientInfo} reports nothing back. This used to throw
	 * {@code SQLFeatureNotSupportedException}, and tools call it while opening a
	 * connection, so the driver looked broken before the first query ran.
	 *
	 * @since 3.1.0
	 */
	@Override
	public ResultSet getClientInfoProperties() throws SQLException {
		checkClosed();
		logger.debug("getClientInfoProperties() called - no client info properties supported, returning empty result");
		return createResultSet(MetadataColumns.ClientInfoProperties.COLUMN_NAMES,
				MetadataColumns.ClientInfoProperties.COLUMN_TYPES, new java.util.ArrayList<>());
	}

	/**
	 * Retrieves the user-defined functions available in the given catalog.
	 *
	 * <p>
	 * BigQuery reports both persistent SQL UDFs and JavaScript UDFs in
	 * {@code INFORMATION_SCHEMA.ROUTINES} alongside stored procedures, told apart
	 * by {@code routine_type}. A {@code TABLE FUNCTION} is reported as
	 * {@link DatabaseMetaData#functionReturnsTable}, everything else as
	 * {@link DatabaseMetaData#functionNoTable}.
	 *
	 * <p>
	 * {@code REMARKS} is null: {@code ROUTINES} has no description column, and a
	 * routine's description lives in {@code ROUTINE_OPTIONS} — the same reason
	 * {@link #getProcedures} reports null there.
	 *
	 * @since 3.1.0
	 */
	@Override
	public ResultSet getFunctions(String catalog, String schemaPattern, String functionNamePattern)
			throws SQLException {
		checkClosed();

		logger.debug("getFunctions() called - catalog: [{}], schemaPattern: [{}], functionNamePattern: [{}]", catalog,
				schemaPattern, functionNamePattern);

		String cacheKey = "functions:" + catalog + ":" + schemaPattern + ":" + functionNamePattern;
		return getCachedOrExecute(cacheKey, () -> executeGetFunctions(catalog, schemaPattern, functionNamePattern));
	}

	private ResultSet executeGetFunctions(String catalog, String schemaPattern, String functionNamePattern)
			throws SQLException {
		String projectId = catalog != null ? catalog : connection.getCurrentCatalog();
		BigQuery bigquery = connection.getBigQuery();

		java.util.List<String> datasetIds = listDatasetsForProject(bigquery, projectId, schemaPattern);
		java.util.List<Object[]> rows = executeInParallel(datasetIds,
				datasetId -> queryFunctionsForDataset(projectId, datasetId, functionNamePattern),
				"Error querying functions in parallel");

		logger.debug("getFunctions() returning {} function(s)", rows.size());
		return createResultSet(MetadataColumns.Functions.COLUMN_NAMES, MetadataColumns.Functions.COLUMN_TYPES, rows);
	}

	private java.util.List<Object[]> queryFunctionsForDataset(String projectId, String datasetId,
			String functionNamePattern) {
		String sql = routinesQuery(projectId, datasetId);
		return queryInformationSchema(projectId, datasetId, "functions", "INFORMATION_SCHEMA.ROUTINES", sql, row -> {
			if (isProcedure(row)) {
				return null;
			}
			String routineName = row.get("routine_name").getStringValue();
			if (functionNamePattern != null && !matchesPattern(routineName, functionNamePattern)) {
				return null;
			}
			short functionType = ROUTINE_TYPE_TABLE_FUNCTION.equalsIgnoreCase(row.get("routine_type").getStringValue())
					? (short) DatabaseMetaData.functionReturnsTable
					: (short) DatabaseMetaData.functionNoTable;
			return new Object[]{projectId, // FUNCTION_CAT
					datasetId, // FUNCTION_SCHEM
					routineName, // FUNCTION_NAME
					routineDefinition(row), // REMARKS
					functionType, // FUNCTION_TYPE
					routineName // SPECIFIC_NAME
			};
		});
	}

	/**
	 * Retrieves the parameters and return value of the given user-defined
	 * functions.
	 *
	 * <p>
	 * BigQuery reports a function's return value as a {@code PARAMETERS} row with
	 * {@code is_result = 'YES'} at {@code ordinal_position} 0 and no parameter
	 * name, which is exactly JDBC's {@link DatabaseMetaData#functionReturn}. Its
	 * arguments carry no {@code parameter_mode} — that column is only populated for
	 * procedures — so they are reported as
	 * {@link DatabaseMetaData#functionColumnIn}.
	 *
	 * @since 3.1.0
	 */
	@Override
	public ResultSet getFunctionColumns(String catalog, String schemaPattern, String functionNamePattern,
			String columnNamePattern) throws SQLException {
		checkClosed();

		logger.debug(
				"getFunctionColumns() called - catalog: [{}], schemaPattern: [{}], functionNamePattern: [{}], columnNamePattern: [{}]",
				catalog, schemaPattern, functionNamePattern, columnNamePattern);

		String cacheKey = "functionColumns:" + catalog + ":" + schemaPattern + ":" + functionNamePattern + ":"
				+ columnNamePattern;
		return getCachedOrExecute(cacheKey,
				() -> executeGetFunctionColumns(catalog, schemaPattern, functionNamePattern, columnNamePattern));
	}

	private ResultSet executeGetFunctionColumns(String catalog, String schemaPattern, String functionNamePattern,
			String columnNamePattern) throws SQLException {
		String projectId = catalog != null ? catalog : connection.getCurrentCatalog();
		BigQuery bigquery = connection.getBigQuery();

		java.util.List<String> datasetIds = listDatasetsForProject(bigquery, projectId, schemaPattern);
		java.util.List<Object[]> rows = executeInParallel(datasetIds,
				datasetId -> queryFunctionColumnsForDataset(projectId, datasetId, functionNamePattern,
						columnNamePattern),
				"Error querying function columns in parallel");

		logger.debug("getFunctionColumns() returning {} column(s)", rows.size());
		return createResultSet(MetadataColumns.FunctionColumns.COLUMN_NAMES,
				MetadataColumns.FunctionColumns.COLUMN_TYPES, rows);
	}

	private java.util.List<Object[]> queryFunctionColumnsForDataset(String projectId, String datasetId,
			String functionNamePattern, String columnNamePattern) {
		String sql = parametersQuery(projectId, datasetId, false);
		return queryInformationSchema(projectId, datasetId, "function columns", "INFORMATION_SCHEMA.PARAMETERS", sql,
				row -> {
					String routineName = row.get("specific_name").getStringValue();
					if (functionNamePattern != null && !matchesPattern(routineName, functionNamePattern)) {
						return null;
					}
					// The return row has no parameter_name. Reported as "" rather than
					// null so the column-name pattern has something to match, which is
					// what getProcedureColumns does with an unnamed parameter.
					String paramName = row.get("parameter_name").isNull()
							? ""
							: row.get("parameter_name").getStringValue();
					if (columnNamePattern != null && !matchesPattern(paramName, columnNamePattern)) {
						return null;
					}
					String dataType = row.get("data_type").isNull() ? "STRING" : row.get("data_type").getStringValue();
					TypeMapper.InfoSchemaTypeInfo typeInfo = TypeMapper.parseInfoSchemaTypeInfo(dataType);
					boolean isResult = !row.get("is_result").isNull()
							&& "YES".equalsIgnoreCase(row.get("is_result").getStringValue());
					short columnType = isResult
							? (short) DatabaseMetaData.functionReturn
							: (short) DatabaseMetaData.functionColumnIn;
					int ordinal = row.get("ordinal_position").isNull()
							? 0
							: (int) row.get("ordinal_position").getLongValue();
					return new Object[]{projectId, // FUNCTION_CAT
							datasetId, // FUNCTION_SCHEM
							routineName, // FUNCTION_NAME
							paramName, // COLUMN_NAME
							columnType, // COLUMN_TYPE
							typeInfo.jdbcType(), // DATA_TYPE
							dataType, // TYPE_NAME
							typeInfo.columnSize(), // PRECISION
							typeInfo.columnSize(), // LENGTH
							(short) typeInfo.decimalDigits(), // SCALE
							(short) 10, // RADIX
							(short) DatabaseMetaData.functionNullable, // NULLABLE
							null, // REMARKS
							typeInfo.columnSize(), // CHAR_OCTET_LENGTH
							ordinal, // ORDINAL_POSITION
							"YES", // IS_NULLABLE
							routineName // SPECIFIC_NAME
					};
				});
	}

	/**
	 * Retrieves the pseudo columns of the matching tables.
	 *
	 * <p>
	 * BigQuery's are the ingestion-time partitioning columns: {@code
	 * _PARTITIONTIME} on every ingestion-time partitioned table, and {@code
	 * _PARTITIONDATE} on those partitioned by day. Both are queryable — and are
	 * what a partition filter is written against — but neither is declared in the
	 * table's schema, which is the gap this method exists to fill.
	 *
	 * <p>
	 * {@code _PARTITIONDATE} is reported only for daily partitioning because that
	 * is the only granularity BigQuery exposes it at: on an hourly, monthly or
	 * yearly table, selecting it fails with "Unrecognized name". Announcing a
	 * column that cannot be queried would be worse than announcing none, so the
	 * granularity is read rather than assumed.
	 *
	 * <p>
	 * {@code COLUMN_USAGE} is
	 * {@link java.sql.PseudoColumnUsage#NO_USAGE_RESTRICTIONS}: these can be used
	 * in both the select list and predicates, and {@code PseudoColumnUsage.SELECT}
	 * would deny the second — which is the one that matters, since filtering on
	 * {@code _PARTITIONTIME} is how partition pruning is expressed. No enum value
	 * captures the real restriction, that BigQuery will not accept a pseudo column
	 * as a DML target.
	 *
	 * <p>
	 * Note that {@code _PARTITIONTIME} also appears in {@link #getColumns} today,
	 * because the {@code INFORMATION_SCHEMA} read there does not filter hidden
	 * columns. Removing it from there would change an existing result and is left
	 * alone here.
	 *
	 * @since 3.1.0
	 */
	@Override
	public ResultSet getPseudoColumns(String catalog, String schemaPattern, String tableNamePattern,
			String columnNamePattern) throws SQLException {
		checkClosed();

		logger.debug(
				"getPseudoColumns() called - catalog: [{}], schemaPattern: [{}], tableNamePattern: [{}], columnNamePattern: [{}]",
				catalog, schemaPattern, tableNamePattern, columnNamePattern);

		String cacheKey = "pseudoColumns:" + catalog + ":" + schemaPattern + ":" + tableNamePattern + ":"
				+ columnNamePattern;
		return getCachedOrExecute(cacheKey,
				() -> executeGetPseudoColumns(catalog, schemaPattern, tableNamePattern, columnNamePattern));
	}

	private ResultSet executeGetPseudoColumns(String catalog, String schemaPattern, String tableNamePattern,
			String columnNamePattern) throws SQLException {
		String projectId = catalog != null ? catalog : connection.getCurrentCatalog();
		BigQuery bigquery = connection.getBigQuery();

		java.util.List<String> datasetIds = listDatasetsForProject(bigquery, projectId, schemaPattern);
		java.util.List<Object[]> rows = executeInParallel(datasetIds,
				datasetId -> queryPseudoColumnsForDataset(projectId, datasetId, tableNamePattern, columnNamePattern),
				"Error querying pseudo columns in parallel");
		rows = new java.util.ArrayList<>(rows);
		rows.addAll(executeInParallel(datasetIds,
				datasetId -> tableSuffixPseudoColumns(projectId, datasetId, tableNamePattern, columnNamePattern),
				"Error querying wildcard pseudo columns in parallel"));

		logger.debug("getPseudoColumns() returning {} pseudo column(s)", rows.size());
		return createResultSet(MetadataColumns.PseudoColumns.COLUMN_NAMES, MetadataColumns.PseudoColumns.COLUMN_TYPES,
				rows);
	}

	/**
	 * Reports {@code _TABLE_SUFFIX} for each collapsed wildcard entry in a dataset.
	 *
	 * <p>
	 * {@code _TABLE_SUFFIX} is the pseudo column that makes a wildcard table usable
	 * — it holds the part of each row's table name that {@code *} matched, and is
	 * how you restrict a scan to a date range instead of reading every shard. An
	 * {@code events_*} entry whose only distinguishing column is undiscoverable is
	 * a listing improvement and nothing more, which is why #184 named this as the
	 * pairing.
	 *
	 * <p>
	 * A separate read from the ingestion-time pseudo columns above rather than a
	 * clause added to that query. That one is deliberately narrow —
	 * {@code is_system_defined = 'YES'} — and a wildcard set is not required to be
	 * partitioned at all, so the two select disjoint things. Folding them together
	 * would mean loosening a query whose shape is load-bearing.
	 *
	 * <p>
	 * Costs nothing unless {@code collapseShardedTables} is on, since without it
	 * there are no wildcard entries to describe.
	 */
	private java.util.List<Object[]> tableSuffixPseudoColumns(String projectId, String datasetId,
			String tableNamePattern, String columnNamePattern) {
		if (!collapsesShards()) {
			return java.util.List.of();
		}
		if (columnNamePattern != null && !matchesPattern("_TABLE_SUFFIX", columnNamePattern)) {
			return java.util.List.of();
		}

		String sql = String.format("SELECT table_name FROM `%s`.`%s`.INFORMATION_SCHEMA.TABLES", projectId, datasetId);
		java.util.List<Object[]> names = queryInformationSchema(projectId, datasetId, "wildcard pseudo columns",
				"INFORMATION_SCHEMA.TABLES", sql, row -> new Object[]{row.get("table_name").getStringValue()});

		java.util.List<String> tableNames = names.stream().map(row -> (String) row[0]).sorted().toList();
		java.util.List<Object[]> rows = new java.util.ArrayList<>();
		ShardedTables.shardSets(tableNames).forEach((prefix, shards) -> {
			String wildcard = ShardedTables.wildcardName(prefix);
			if (tableNamePattern != null && !matchesTableNameFilter(wildcard, tableNamePattern)
					&& !matchesPattern(wildcard, tableNamePattern)) {
				return;
			}
			rows.add(new Object[]{projectId, // TABLE_CAT
					datasetId, // TABLE_SCHEM
					wildcard, // TABLE_NAME
					"_TABLE_SUFFIX", // COLUMN_NAME
					java.sql.Types.VARCHAR, // DATA_TYPE
					Integer.MAX_VALUE, // COLUMN_SIZE
					null, // DECIMAL_DIGITS
					10, // NUM_PREC_RADIX
					java.sql.PseudoColumnUsage.NO_USAGE_RESTRICTIONS.name(), // COLUMN_USAGE
					"Wildcard table suffix, e.g. " + ShardedTables.newestShard(shards).substring(prefix.length() + 1), // REMARKS
					null, // CHAR_OCTET_LENGTH
					"NO" // IS_NULLABLE
			});
		});
		return rows;
	}

	private java.util.List<Object[]> queryPseudoColumnsForDataset(String projectId, String datasetId,
			String tableNamePattern, String columnNamePattern) {
		// One row per pseudo column rather than per table, so the row mapper stays
		// one-row-in one-row-out. The UNNEST is what expands a daily-partitioned
		// table into its two.
		//
		// The daily test is on the DDL because INFORMATION_SCHEMA cannot answer it
		// otherwise: an hourly and a daily ingestion-time table are identical in
		// COLUMNS, both showing only _PARTITIONTIME as system-defined. BigQuery
		// normalises the clause, so every daily table reads back as
		// "PARTITION BY DATE(_PARTITIONTIME)" whichever spelling created it, while
		// the coarser granularities keep TIMESTAMP_TRUNC(_PARTITIONTIME, <unit>).
		// REGEXP_CONTAINS runs server-side so the DDL itself never crosses the wire.
		String sql = String.format("SELECT c.table_name AS table_name, pseudo_column AS column_name, "
				+ "IF(pseudo_column = '_PARTITIONDATE', 'DATE', 'TIMESTAMP') AS data_type "
				+ "FROM `%1$s`.`%2$s`.INFORMATION_SCHEMA.COLUMNS c "
				+ "JOIN `%1$s`.`%2$s`.INFORMATION_SCHEMA.TABLES t ON t.table_name = c.table_name "
				+ "CROSS JOIN UNNEST(IF(REGEXP_CONTAINS(t.ddl, r'PARTITION BY DATE\\(_PARTITIONTIME\\)'), "
				+ "['_PARTITIONTIME', '_PARTITIONDATE'], ['_PARTITIONTIME'])) AS pseudo_column "
				+ "WHERE c.is_system_defined = 'YES' AND c.column_name = '_PARTITIONTIME' "
				+ "ORDER BY c.table_name, pseudo_column", projectId, datasetId);
		return queryInformationSchema(projectId, datasetId, "pseudo columns", "INFORMATION_SCHEMA.COLUMNS", sql,
				row -> {
					String tableName = row.get("table_name").getStringValue();
					if (tableNamePattern != null && !matchesPattern(tableName, tableNamePattern)) {
						return null;
					}
					String columnName = row.get("column_name").getStringValue();
					if (columnNamePattern != null && !matchesPattern(columnName, columnNamePattern)) {
						return null;
					}
					String dataType = row.get("data_type").getStringValue();
					TypeMapper.InfoSchemaTypeInfo typeInfo = TypeMapper.parseInfoSchemaTypeInfo(dataType);
					return new Object[]{projectId, // TABLE_CAT
							datasetId, // TABLE_SCHEM
							tableName, // TABLE_NAME
							columnName, // COLUMN_NAME
							typeInfo.jdbcType(), // DATA_TYPE
							typeInfo.columnSize(), // COLUMN_SIZE
							typeInfo.decimalDigits(), // DECIMAL_DIGITS
							10, // NUM_PREC_RADIX
							java.sql.PseudoColumnUsage.NO_USAGE_RESTRICTIONS.name(), // COLUMN_USAGE
							"Ingestion-time partitioning pseudo column", // REMARKS
							null, // CHAR_OCTET_LENGTH
							"YES" // IS_NULLABLE
					};
				});
	}

	@Override
	public boolean generatedKeyAlwaysReturned() throws SQLException {
		return false;
	}

	private void checkClosed() throws SQLException {
		if (connection.isClosed()) {
			throw new BQSQLException("Connection is closed", BQSQLException.SQLSTATE_CONNECTION_CLOSED);
		}
	}

	/**
	 * Executes a metadata query with caching support.
	 *
	 * @param cacheKey
	 *            the cache key
	 * @param supplier
	 *            the function to execute if cache miss
	 * @return the ResultSet (either from cache or freshly generated)
	 * @throws SQLException
	 *             if query execution fails
	 */
	/**
	 * Distinguishes cache entries by the connection settings that change what a
	 * metadata call returns.
	 *
	 * <p>
	 * The metadata cache is shared statically between every connection to a project
	 * — deliberately, because IntelliJ reopens connections constantly and the cache
	 * surviving that is the point. But the call-site keys describe only the
	 * arguments, so two connections to one project that disagree about how metadata
	 * should be shaped were served each other's rows: whichever connected first
	 * decided, for the whole TTL, whether sharded tables collapsed, whether REMARKS
	 * was populated, and whether {@code INFORMATION_SCHEMA} was listed.
	 *
	 * <p>
	 * Prefixed here rather than at the dozen call sites for the usual reason: a new
	 * cached method cannot forget a step it does not perform. A new result-shaping
	 * property must be added to this string.
	 *
	 * @return a compact encoding of the result-shaping settings
	 */
	private String metadataShapeKey() {
		ConnectionProperties properties = connection.getProperties();
		return (properties.includeInformationSchema() ? "i" : "-") + (properties.collapseShardedTables() ? "s" : "-")
				+ (properties.metadataIncludeDescriptions() ? "d" : "-")
				// The current catalog belongs here for the same reason: a null
				// catalog argument resolves against it, so two connections pointed at
				// different projects build identical call-site keys for different
				// answers. setCatalog moving it mid-connection has the same effect.
				+ "@" + connection.getCurrentCatalog()
				// And the configured projects, because getCatalogs() is built from
				// them and its call-site key is the constant "catalogs" — without
				// this, the first connection to ask decides what every later one sees.
				+ "+" + properties.additionalProjects() + "|";
	}

	private ResultSet getCachedOrExecute(String rawKey, SqlSupplier<ResultSet> supplier) throws SQLException {
		// Only built when there is a cache to key: with caching off the string is
		// never read, and reading the settings to compose it would be pure work.
		String cacheKey = cache == null ? rawKey : metadataShapeKey() + rawKey;

		// Check cache if enabled
		if (cache != null) {
			java.util.Optional<ResultSet> cached = cache.get(cacheKey);
			if (cached.isPresent()) {
				cacheHits++;
				logger.trace("Cache hit for: {}", cacheKey);
				logCacheStatsIfNeeded();
				return cached.get();
			}
		}

		// Execute query
		cacheMisses++;
		logger.trace("Cache miss for: {}", cacheKey);
		ResultSet result = supplier.get();

		// Store in cache if enabled
		if (cache != null && result instanceof MetadataResultSet) {
			cache.put(cacheKey, result);
			logger.trace("Cached result for: {}", cacheKey);
		}

		logCacheStatsIfNeeded();
		return result;
	}

	/**
	 * Logs cache statistics periodically based on operation count.
	 */
	private void logCacheStatsIfNeeded() {
		int totalOps = cacheHits + cacheMisses;
		// isDebugEnabled first, and it matters more here than anywhere else in this
		// class: MetadataCache.getStats() walks every entry to count expired ones and
		// sum their rows. As an argument it ran on schedule whether or not anything
		// was listening, so a driver with logging off still paid for a diagnostic it
		// then discarded. String.format on the hit rate was eager for the same reason.
		if (logger.isDebugEnabled() && totalOps > 0 && totalOps % STATS_LOG_INTERVAL == 0) {
			double hitRate = (double) cacheHits / totalOps * 100;
			logger.debug("Metadata cache performance: {} hits, {} misses, {}% hit rate, {}", cacheHits, cacheMisses,
					String.format(java.util.Locale.ROOT, "%.1f", hitRate),
					cache != null ? cache.getStats() : "disabled");
		}
	}

	/** Functional interface for SQL operations that can throw SQLException. */
	@FunctionalInterface
	private interface SqlSupplier<T> {
		T get() throws SQLException;
	}

	/**
	 * Functional interface for operations that process items and can throw
	 * SQLException.
	 */
	@FunctionalInterface
	private interface SqlFunction<T, R> {
		R apply(T item) throws SQLException;
	}

	/**
	 * Generic parallel execution helper using virtual threads.
	 *
	 * <p>
	 * Executes a function on each item in parallel and combines results. Follows
	 * DRY principle for all parallel metadata operations.
	 *
	 * @param <T>
	 *            the input item type
	 * @param items
	 *            the collection of items to process
	 * @param processor
	 *            the function to apply to each item
	 * @param errorMessage
	 *            the error message prefix for exceptions
	 * @return combined list of results
	 * @throws SQLException
	 *             if any processing fails
	 */
	private <T> java.util.List<Object[]> executeInParallel(java.util.Collection<T> items,
			SqlFunction<T, java.util.List<Object[]>> processor, String errorMessage) throws SQLException {
		// Use virtual threads for concurrent execution
		try (java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors
				.newVirtualThreadPerTaskExecutor()) {

			// One task per dataset, but only MAX_CONCURRENT_METADATA_QUERIES in flight:
			// a project with hundreds of datasets would otherwise fire hundreds of
			// INFORMATION_SCHEMA queries at once and run into BigQuery's concurrency
			// limits, turning a metadata refresh into a wall of quota errors
			java.util.concurrent.Semaphore permits = new java.util.concurrent.Semaphore(
					MAX_CONCURRENT_METADATA_QUERIES);

			java.util.List<java.util.concurrent.CompletableFuture<java.util.List<Object[]>>> futures = items.stream()
					.map(item -> java.util.concurrent.CompletableFuture.supplyAsync(() -> {
						try {
							permits.acquire();
						} catch (InterruptedException e) {
							Thread.currentThread().interrupt();
							throw new RuntimeException(e);
						}
						try {
							return processor.apply(item);
						} catch (SQLException e) {
							throw new RuntimeException(e);
						} finally {
							permits.release();
						}
					}, executor)).toList();

			// Combine all results
			java.util.List<Object[]> allRows = new java.util.ArrayList<>();
			for (java.util.concurrent.CompletableFuture<java.util.List<Object[]>> future : futures) {
				try {
					allRows.addAll(future.join());
				} catch (java.util.concurrent.CompletionException e) {
					if (e.getCause() instanceof RuntimeException && e.getCause().getCause() instanceof SQLException) {
						// Unwrap to the original SQLException; preserve the CompletionException chain
						SQLException sqlEx = (SQLException) e.getCause().getCause();
						sqlEx.addSuppressed(e);
						throw sqlEx;
					}
					throw new SQLException(errorMessage, e);
				}
			}

			return allRows;
		}
	}

	/**
	 * Guards every {@code INFORMATION_SCHEMA} query this class builds by
	 * concatenation.
	 *
	 * <p>
	 * BigQuery cannot parameterise the table path of a query, so these names have
	 * to be interpolated, and several of them arrive from the caller — the JDBC
	 * metadata methods take {@code catalog} and {@code schema} as literal names. A
	 * name containing a backtick would close the quoting around it and turn the
	 * rest into SQL.
	 *
	 * <p>
	 * In most of these methods the value has already been through
	 * {@code BigQuery.listDatasets()}, which rejects anything that is not a
	 * well-formed project ID — so today this check does not fire. That is the point
	 * of stating it: the protection was a side effect of an API call made for
	 * another reason, invisible to anyone reading the query, and lost by any path
	 * that skips the listing. {@code getPrimaryKeys} already takes such a path when
	 * the caller names a schema.
	 *
	 * @param projectId
	 *            the project about to be interpolated
	 * @param datasetId
	 *            the dataset about to be interpolated
	 * @param what
	 *            what the caller was reading, for the log message
	 * @return true if the caller must <em>not</em> build the query
	 */
	private static boolean rejectsUnsafeIdentifiers(String projectId, String datasetId, String what) {
		if (BigQueryIdentifiers.areSafe(projectId, datasetId)) {
			return false;
		}
		logger.warn("Skipping {} for [{}].[{}]: not a valid BigQuery project or dataset name", what, projectId,
				datasetId);
		return true;
	}

	/**
	 * Create a ResultSet from column names, types, and data rows.
	 *
	 * @param columnNames
	 *            array of column names
	 * @param columnTypes
	 *            array of JDBC type constants
	 * @param rows
	 *            list of data rows (each row is an Object array)
	 * @return ResultSet containing the data
	 * @throws SQLException
	 *             if result set creation fails
	 */
	private ResultSet createResultSet(String[] columnNames, int[] columnTypes, java.util.List<Object[]> rows)
			throws SQLException {
		return new MetadataResultSet(columnNames, columnTypes, rows);
	}

	/**
	 * Lists all datasets for a project that match the given schema pattern.
	 *
	 * @param projectId
	 *            the project ID
	 * @param schemaPattern
	 *            the schema pattern to match (or null for all)
	 * @return list of dataset IDs matching the pattern
	 */
	private java.util.List<String> listDatasetsForProject(BigQuery bigQuery, String projectId, String schemaPattern) {
		var datasets = bigQuery.listDatasets(projectId);
		java.util.List<String> datasetIds = new java.util.ArrayList<>();

		for (Dataset dataset : datasets.iterateAll()) {
			String datasetId = dataset.getDatasetId().getDataset();

			// Apply schema pattern filter
			if (schemaPattern == null || matchesPattern(datasetId, schemaPattern)) {
				datasetIds.add(datasetId);
			}
		}

		return datasetIds;
	}

	/**
	 * Convert SQL LIKE pattern to Java regex pattern.
	 *
	 * <p>
	 * SQL LIKE patterns support: - % matches any sequence of characters - _ matches
	 * any single character - \ is the escape character (e.g., \_ matches literal
	 * underscore, \% matches literal percent)
	 *
	 * @param value
	 *            the value to match
	 * @param pattern
	 *            the SQL LIKE pattern
	 * @return true if value matches pattern
	 */
	/**
	 * Compiled JDBC name patterns, keyed by the pattern as supplied.
	 *
	 * <p>
	 * {@link #matchesPattern} runs once per row — per table in {@code getTables()},
	 * per column in {@code getColumns()} — so on a project with many datasets it is
	 * called hundreds of thousands of times, always with the same handful of
	 * patterns. Translating the pattern and compiling the regex on each call made
	 * metadata listing markedly slower for exactly the large projects this driver
	 * exists to speed up.
	 */
	private static final java.util.Map<String, java.util.regex.Pattern> PATTERN_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

	private boolean matchesPattern(String value, String pattern) {
		if (pattern == null) {
			return true;
		}

		return PATTERN_CACHE.computeIfAbsent(pattern, p -> java.util.regex.Pattern.compile(translateLikePattern(p)))
				.matcher(value).matches();
	}

	/**
	 * Translates a JDBC/SQL {@code LIKE} pattern into a regular expression.
	 *
	 * @param pattern
	 *            the SQL pattern, with {@code %} and {@code _} wildcards
	 * @return the equivalent anchored regex
	 */
	private static String translateLikePattern(String pattern) {

		// Handle SQL LIKE escape sequences before converting to regex
		// Use unique Unicode placeholders that won't be affected by string replacements
		String escapedUnderscore = "\u0001\u0002\u0003"; // Placeholder for \_
		String escapedPercent = "\u0004\u0005\u0006"; // Placeholder for \%
		String escapedBackslash = "\u0007\u0008\t"; // Placeholder for \\

		// Step 1: Replace escaped sequences with placeholders
		String processed = pattern;
		processed = processed.replace("\\\\", escapedBackslash); // \\ -> literal \
		processed = processed.replace("\\_", escapedUnderscore); // \_ -> literal _
		processed = processed.replace("\\%", escapedPercent); // \% -> literal %

		// Step 2: Escape regex special characters (except our wildcards)
		processed = processed.replace(".", "\\.");
		processed = processed.replace("^", "\\^");
		processed = processed.replace("$", "\\$");
		processed = processed.replace("|", "\\|");
		processed = processed.replace("(", "\\(");
		processed = processed.replace(")", "\\)");
		processed = processed.replace("[", "\\[");
		processed = processed.replace("]", "\\]");
		processed = processed.replace("{", "\\{");
		processed = processed.replace("}", "\\}");
		processed = processed.replace("+", "\\+");
		processed = processed.replace("*", "\\*");
		processed = processed.replace("?", "\\?");

		// Step 3: Convert SQL LIKE wildcards to regex
		processed = processed.replace("%", ".*"); // % -> any sequence
		processed = processed.replace("_", "."); // _ -> any single char

		// Step 4: Replace placeholders with literal characters (regex-escaped where
		// needed)
		processed = processed.replace(escapedUnderscore, "_"); // Literal underscore (no escaping needed)
		processed = processed.replace(escapedPercent, "%"); // Literal percent (no escaping needed)
		processed = processed.replace(escapedBackslash, "\\\\"); // Literal backslash (needs escaping)

		// Step 5: Build final regex with anchors
		return "^" + processed + "$";
	}

	/**
	 * Clears the metadata cache for this project.
	 *
	 * <p>
	 * This method clears the shared cache for the current project, affecting all
	 * connections to this project. Use this to force a refresh of metadata after
	 * schema changes (DDL operations).
	 *
	 * <p>
	 * Note: The cache is shared across connections and persists based on TTL. This
	 * method should only be called when you need to force an immediate cache
	 * refresh, not during normal connection close operations.
	 */
	public void clearCache() {
		if (cache != null) {
			cache.clear();
			logger.debug("Shared metadata cache cleared for project (cache key: {})", cacheKey);
		}
	}

	/**
	 * Invalidates cache entries matching the specified prefix.
	 *
	 * <p>
	 * This is useful for targeted cache invalidation when specific metadata
	 * changes, such as after DDL operations. For example:
	 * <ul>
	 * <li>After creating/dropping a table: invalidate("tables:project.dataset")
	 * <li>After modifying a dataset: invalidate("tables:project.dataset") or
	 * invalidate("schemas:project.dataset")
	 * <li>After altering columns: invalidate("columns:project.dataset.table")
	 * </ul>
	 *
	 * @param keyPrefix
	 *            the cache key prefix to invalidate
	 */
	public void invalidateCache(String keyPrefix) {
		if (cache != null) {
			cache.invalidate(keyPrefix);
		}
	}

	/**
	 * Gets cache statistics for monitoring and debugging.
	 *
	 * @return cache statistics string, or null if cache is disabled
	 */
	public String getCacheStats() {
		return cache != null ? cache.getStats() : null;
	}

	/**
	 * Returns the shared {@link MetadataCache} for the given cache key, creating a
	 * new instance if none exists yet.
	 *
	 * <p>
	 * The cache is keyed by {@code "projectId:ttlSeconds"} so that connections to
	 * different projects or with different TTL settings each get their own cache.
	 *
	 * @param cacheKey
	 *            the cache key ({@code "projectId:ttlSeconds:maxRows"})
	 * @param ttl
	 *            the time-to-live for cache entries (used only when creating a new
	 *            cache)
	 * @param projectId
	 *            the project ID, used only for the creation log message
	 * @return the shared {@link MetadataCache} instance
	 */
	public static MetadataCache getOrCreateSharedCache(String cacheKey, java.time.Duration ttl, String projectId) {
		return getOrCreateSharedCache(cacheKey, ttl, projectId, MetadataCache.DEFAULT_MAX_ROWS);
	}

	/**
	 * Returns the shared {@link MetadataCache} for the given cache key, creating a
	 * new instance with the given row ceiling if none exists yet.
	 *
	 * @param cacheKey
	 *            the cache key ({@code "projectId:ttlSeconds:maxRows"})
	 * @param ttl
	 *            the time-to-live for cache entries (used only when creating a new
	 *            cache)
	 * @param projectId
	 *            the project ID, used only for the creation log message
	 * @param maxRows
	 *            ceiling on total cached rows (used only when creating a new cache)
	 * @return the shared {@link MetadataCache} instance
	 */
	public static MetadataCache getOrCreateSharedCache(String cacheKey, java.time.Duration ttl, String projectId,
			int maxRows) {
		return SHARED_CACHES.computeIfAbsent(cacheKey, k -> {
			logger.debug("Creating new shared metadata cache for project: {} with TTL: {}, max rows: {}", projectId,
					ttl, maxRows);
			return new MetadataCache(ttl, maxRows);
		});
	}

	public static void clearAllSharedCaches() {
		int clearedCount = 0;
		for (MetadataCache cache : SHARED_CACHES.values()) {
			cache.clear();
			clearedCount++;
		}
		logger.debug("Cleared {} shared metadata cache instance(s)", clearedCount);
	}

	/**
	 * Gets the number of shared cache instances currently active.
	 *
	 * <p>
	 * This is primarily useful for monitoring and debugging to understand how many
	 * distinct project caches are being maintained.
	 *
	 * @return the number of shared cache instances
	 */
	public static int getSharedCacheCount() {
		return SHARED_CACHES.size();
	}

}

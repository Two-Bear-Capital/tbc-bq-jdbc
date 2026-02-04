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
package com.twobearcapital.bigquery.jdbc.metadata;

import com.twobearcapital.bigquery.jdbc.BQConnection;
import com.twobearcapital.bigquery.jdbc.TypeMapper;
import com.twobearcapital.bigquery.jdbc.base.BaseJdbcWrapper;
import com.twobearcapital.bigquery.jdbc.config.ConnectionProperties;
import com.twobearcapital.bigquery.jdbc.config.MetadataCache;
import com.twobearcapital.bigquery.jdbc.exception.BQSQLException;
import com.twobearcapital.bigquery.jdbc.exception.BQSQLFeatureNotSupportedException;
import java.sql.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
	private static final int STATS_LOG_INTERVAL = 10; // Log stats every N cache operations

	/**
	 * Static cache shared across all connections to the same project.
	 * Cache key format: "projectId:ttlSeconds"
	 * This allows the cache to persist across connection open/close cycles,
	 * which is critical for IntelliJ IDEA that frequently reopens connections.
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
			java.time.Duration cacheTtl = java.time.Duration.ofSeconds(properties.metadataCacheTtl());

			// Create cache key based on project and TTL
			this.cacheKey = properties.projectId() + ":" + properties.metadataCacheTtl();

			// Get or create shared cache instance
			this.cache = SHARED_CACHES.computeIfAbsent(cacheKey, k -> {
				logger.info("Creating new shared metadata cache for project: {} with TTL: {}",
					properties.projectId(), cacheTtl);
				return new MetadataCache(cacheTtl);
			});

			logger.debug("Using shared metadata cache for project: {} (cache instances: {})",
				properties.projectId(), SHARED_CACHES.size());
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
		return com.twobearcapital.bigquery.jdbc.DriverVersion.getVersionString();
	}

	@Override
	public int getDriverMajorVersion() {
		return com.twobearcapital.bigquery.jdbc.DriverVersion.getMajorVersion();
	}

	@Override
	public int getDriverMinorVersion() {
		return com.twobearcapital.bigquery.jdbc.DriverVersion.getMinorVersion();
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

	@Override
	public int getDefaultTransactionIsolation() throws SQLException {
		return Connection.TRANSACTION_NONE;
	}

	@Override
	public boolean supportsTransactions() throws SQLException {
		return false;
	}

	@Override
	public boolean supportsTransactionIsolationLevel(int level) throws SQLException {
		return level == Connection.TRANSACTION_NONE;
	}

	@Override
	public boolean supportsDataDefinitionAndDataManipulationTransactions() throws SQLException {
		return false;
	}

	@Override
	public boolean supportsDataManipulationTransactionsOnly() throws SQLException {
		return false;
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
		throw new BQSQLFeatureNotSupportedException("getProcedures not yet implemented");
	}

	@Override
	public ResultSet getProcedureColumns(String catalog, String schemaPattern, String procedureNamePattern,
			String columnNamePattern) throws SQLException {
		throw new BQSQLFeatureNotSupportedException("getProcedureColumns not yet implemented");
	}

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
		String projectId = catalog != null ? catalog : connection.getProperties().projectId();

		com.google.cloud.bigquery.BigQuery bigquery = connection.getBigQuery();
		boolean lazyLoad = connection.getProperties().metadataLazyLoad();

		// Enhanced logging to debug IntelliJ introspection
		logger.info(
				"getTables() called - catalog: [{}], schemaPattern: [{}], tableNamePattern: [{}], types: [{}], lazyLoad: {}",
				catalog, schemaPattern, tableNamePattern, types != null ? java.util.Arrays.toString(types) : "null",
				lazyLoad);

		// Lazy loading: If enabled and no specific patterns, return empty result
		// This allows IntelliJ to load the tree structure quickly without fetching all
		// tables
		if (lazyLoad && schemaPattern == null && tableNamePattern == null) {
			logger.info("Lazy loading enabled: returning empty table list (no patterns specified) - catalog: [{}]",
					catalog);
			return createResultSet(MetadataColumns.Tables.COLUMN_NAMES, MetadataColumns.Tables.COLUMN_TYPES,
					new java.util.ArrayList<>());
		}

		// Get datasets matching schema pattern
		java.util.List<String> datasetIds = listDatasetsForProject(bigquery, projectId, schemaPattern);

		logger.info("Found {} dataset(s) matching pattern [{}]: {}", datasetIds.size(), schemaPattern,
				datasetIds.size() <= 10 ? datasetIds : datasetIds.subList(0, 10) + "...");

		// Always use parallel loading for better performance with BigQuery API
		logger.info("Using parallel loading for {} datasets", datasetIds.size());
		java.util.List<Object[]> rows = queryTablesParallel(projectId, datasetIds, tableNamePattern, types);

		logger.info("getTables() returning {} table(s)", rows.size());

		return createResultSet(MetadataColumns.Tables.COLUMN_NAMES, MetadataColumns.Tables.COLUMN_TYPES, rows);
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
		com.google.cloud.bigquery.BigQuery bigquery = connection.getBigQuery();
		return executeInParallel(datasetIds,
				datasetId -> queryTablesForDataset(bigquery, projectId, datasetId, tableNamePattern, types),
				"Error querying tables in parallel");
	}

	/** Query tables for a single dataset. */
	private java.util.List<Object[]> queryTablesForDataset(com.google.cloud.bigquery.BigQuery bigquery,
			String projectId, String datasetId, String tableNamePattern, String[] types) throws SQLException {
		java.util.List<Object[]> rows = new java.util.ArrayList<>();

		// List tables in dataset
		var tables = bigquery.listTables(com.google.cloud.bigquery.DatasetId.of(projectId, datasetId));

		for (com.google.cloud.bigquery.Table table : tables.iterateAll()) {
			String tableName = table.getTableId().getTable();

			// Apply table name pattern filter
			if (tableNamePattern != null && !matchesPattern(tableName, tableNamePattern)) {
				continue;
			}

			// Map BigQuery table type to JDBC type
			String tableType;
			com.google.cloud.bigquery.TableDefinition def = table.getDefinition();
			if (def instanceof com.google.cloud.bigquery.ViewDefinition) {
				tableType = "VIEW";
			} else if (def instanceof com.google.cloud.bigquery.MaterializedViewDefinition) {
				tableType = "MATERIALIZED VIEW";
			} else {
				tableType = "TABLE";
			}

			// Apply type filter
			if (types != null && !java.util.Arrays.asList(types).contains(tableType)) {
				continue;
			}

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

		return rows;
	}

	@Override
	public ResultSet getSchemas() throws SQLException {
		return getSchemas(null, null);
	}

	@Override
	public ResultSet getCatalogs() throws SQLException {
		checkClosed();

		return getCachedOrExecute("catalogs", () -> {
			// BigQuery: Catalogs = Projects
			// Return the current project
			String projectId = connection.getProperties().projectId();

			java.util.List<Object[]> rows = new java.util.ArrayList<>();
			rows.add(new Object[]{projectId});

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

			return createResultSet(MetadataColumns.TableTypes.COLUMN_NAMES, MetadataColumns.TableTypes.COLUMN_TYPES,
					rows);
		});
	}

	@Override
	public ResultSet getColumns(String catalog, String schemaPattern, String tableNamePattern, String columnNamePattern)
			throws SQLException {
		checkClosed();

		String cacheKey = "columns:" + catalog + ":" + schemaPattern + ":" + tableNamePattern + ":" + columnNamePattern;

		return getCachedOrExecute(cacheKey,
				() -> executeGetColumns(catalog, schemaPattern, tableNamePattern, columnNamePattern));
	}

	/** Query columns from multiple datasets in parallel using virtual threads. */
	private java.util.List<Object[]> queryColumnsParallel(String projectId, java.util.List<String> datasetIds,
			String tableNamePattern, String columnNamePattern) throws SQLException {
		com.google.cloud.bigquery.BigQuery bigquery = connection.getBigQuery();
		return executeInParallel(datasetIds, datasetId -> queryColumnsForDataset(bigquery, projectId, datasetId,
				tableNamePattern, columnNamePattern), "Error querying columns in parallel");
	}

	/**
	 * Query columns for a single dataset with nested parallelization.
	 *
	 * <p>
	 * Always uses parallel table fetching for better performance with BigQuery API.
	 */
	private java.util.List<Object[]> queryColumnsForDataset(com.google.cloud.bigquery.BigQuery bigquery,
			String projectId, String datasetId, String tableNamePattern, String columnNamePattern) throws SQLException {
		var tables = bigquery.listTables(com.google.cloud.bigquery.DatasetId.of(projectId, datasetId));

		// Collect tables that match the pattern
		java.util.List<com.google.cloud.bigquery.Table> tablesToQuery = new java.util.ArrayList<>();
		for (com.google.cloud.bigquery.Table table : tables.iterateAll()) {
			String tableName = table.getTableId().getTable();
			if (tableNamePattern == null || matchesPattern(tableName, tableNamePattern)) {
				tablesToQuery.add(table);
			}
		}

		// Always use nested parallel loading for better performance
		logger.debug("Using nested parallel loading for {} tables in dataset {}", tablesToQuery.size(), datasetId);
		return fetchAndProcessTablesParallel(bigquery, projectId, datasetId, tablesToQuery, columnNamePattern);
	}

	/**
	 * Fetch and process tables in parallel within a dataset using virtual threads.
	 *
	 * <p>
	 * This provides nested parallelization: parallel across datasets AND parallel
	 * across tables within each dataset.
	 */
	private java.util.List<Object[]> fetchAndProcessTablesParallel(com.google.cloud.bigquery.BigQuery bigquery,
			String projectId, String datasetId, java.util.List<com.google.cloud.bigquery.Table> tablesToQuery,
			String columnNamePattern) throws SQLException {
		return executeInParallel(tablesToQuery,
				table -> processTableColumns(bigquery, projectId, datasetId, table, columnNamePattern),
				"Error fetching table columns in parallel");
	}

	/**
	 * Process columns for a single table.
	 *
	 * @return list of column rows for this table
	 */
	private java.util.List<Object[]> processTableColumns(com.google.cloud.bigquery.BigQuery bigquery, String projectId,
			String datasetId, com.google.cloud.bigquery.Table table, String columnNamePattern) throws SQLException {
		java.util.List<Object[]> rows = new java.util.ArrayList<>();

		String tableName = table.getTableId().getTable();

		// Get full table with schema
		com.google.cloud.bigquery.Table fullTable = bigquery.getTable(table.getTableId());
		if (fullTable == null) {
			return rows;
		}

		com.google.cloud.bigquery.Schema schema = fullTable.getDefinition().getSchema();
		if (schema == null) {
			return rows;
		}

		int ordinalPosition = 1;
		for (com.google.cloud.bigquery.Field field : schema.getFields()) {
			String columnName = field.getName();

			if (columnNamePattern != null && !matchesPattern(columnName, columnNamePattern)) {
				continue;
			}

			com.google.cloud.bigquery.StandardSQLTypeName type = field.getType().getStandardType();
			int jdbcType = TypeMapper.toJdbcType(type);
			String typeName = type != null ? type.name() : "UNKNOWN";

			int columnSize = TypeMapper.getColumnSize(type);
			int decimalDigits = TypeMapper.getDecimalDigits(type);
			int nullable = field.getMode() == com.google.cloud.bigquery.Field.Mode.REQUIRED
					? DatabaseMetaData.columnNoNulls
					: DatabaseMetaData.columnNullable;

			rows.add(new Object[]{projectId, // TABLE_CAT
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
					field.getDescription(), // REMARKS
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
			});

			ordinalPosition++;
		}

		return rows;
	}

	private ResultSet executeGetColumns(String catalog, String schemaPattern, String tableNamePattern,
			String columnNamePattern) throws SQLException {
		String projectId = catalog != null ? catalog : connection.getProperties().projectId();

		com.google.cloud.bigquery.BigQuery bigquery = connection.getBigQuery();
		boolean lazyLoad = connection.getProperties().metadataLazyLoad();

		// Enhanced logging to debug IntelliJ introspection
		logger.info(
				"getColumns() called - catalog: [{}], schemaPattern: [{}], tableNamePattern: [{}], columnNamePattern: [{}], lazyLoad: {}",
				catalog, schemaPattern, tableNamePattern, columnNamePattern, lazyLoad);

		// Lazy loading: If enabled and no specific table pattern, return empty result
		// This allows IntelliJ to load the tree structure quickly without fetching all
		// columns
		if (lazyLoad && tableNamePattern == null) {
			logger.info(
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

		return createResultSet(MetadataColumns.Columns.COLUMN_NAMES, MetadataColumns.Columns.COLUMN_TYPES, rows);
	}

	@Override
	public ResultSet getColumnPrivileges(String catalog, String schema, String table, String columnNamePattern)
			throws SQLException {
		throw new BQSQLFeatureNotSupportedException("getColumnPrivileges not supported");
	}

	@Override
	public ResultSet getTablePrivileges(String catalog, String schemaPattern, String tableNamePattern)
			throws SQLException {
		throw new BQSQLFeatureNotSupportedException("getTablePrivileges not supported");
	}

	@Override
	public ResultSet getBestRowIdentifier(String catalog, String schema, String table, int scope, boolean nullable)
			throws SQLException {
		throw new BQSQLFeatureNotSupportedException("getBestRowIdentifier not supported");
	}

	@Override
	public ResultSet getVersionColumns(String catalog, String schema, String table) throws SQLException {
		throw new BQSQLFeatureNotSupportedException("getVersionColumns not supported");
	}

	@Override
	public ResultSet getPrimaryKeys(String catalog, String schema, String table) throws SQLException {
		checkClosed();

		logger.info("getPrimaryKeys() called - catalog: [{}], schema: [{}], table: [{}]", catalog, schema, table);

		// BigQuery doesn't have traditional primary keys, return empty result set
		return createResultSet(MetadataColumns.PrimaryKeys.COLUMN_NAMES, MetadataColumns.PrimaryKeys.COLUMN_TYPES,
				new java.util.ArrayList<>());
	}

	@Override
	public ResultSet getImportedKeys(String catalog, String schema, String table) throws SQLException {
		checkClosed();

		logger.info("getImportedKeys() called - catalog: [{}], schema: [{}], table: [{}]", catalog, schema, table);

		// BigQuery doesn't have foreign keys, return empty result set
		return createResultSet(MetadataColumns.ForeignKeys.COLUMN_NAMES, MetadataColumns.ForeignKeys.COLUMN_TYPES,
				new java.util.ArrayList<>());
	}

	@Override
	public ResultSet getExportedKeys(String catalog, String schema, String table) throws SQLException {
		checkClosed();

		logger.info("getExportedKeys() called - catalog: [{}], schema: [{}], table: [{}]", catalog, schema, table);

		// BigQuery doesn't have foreign keys, return empty result set (same structure
		// as
		// getImportedKeys)
		return createResultSet(MetadataColumns.ForeignKeys.COLUMN_NAMES, MetadataColumns.ForeignKeys.COLUMN_TYPES,
				new java.util.ArrayList<>());
	}

	@Override
	public ResultSet getCrossReference(String parentCatalog, String parentSchema, String parentTable,
			String foreignCatalog, String foreignSchema, String foreignTable) throws SQLException {
		throw new BQSQLFeatureNotSupportedException("getCrossReference not supported");
	}

	@Override
	public ResultSet getTypeInfo() throws SQLException {
		checkClosed();

		logger.info("getTypeInfo() called");

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

		logger.info("getTypeInfo() returning {} type(s)", rows.size());

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
		throw new BQSQLFeatureNotSupportedException("getIndexInfo not supported");
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

	@Override
	public boolean supportsBatchUpdates() throws SQLException {
		return false;
	}

	@Override
	public ResultSet getUDTs(String catalog, String schemaPattern, String typeNamePattern, int[] types)
			throws SQLException {
		throw new BQSQLFeatureNotSupportedException("getUDTs not supported");
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
		return true;
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
		throw new BQSQLFeatureNotSupportedException("getSuperTypes not supported");
	}

	@Override
	public ResultSet getSuperTables(String catalog, String schemaPattern, String tableNamePattern) throws SQLException {
		throw new BQSQLFeatureNotSupportedException("getSuperTables not supported");
	}

	@Override
	public ResultSet getAttributes(String catalog, String schemaPattern, String typeNamePattern,
			String attributeNamePattern) throws SQLException {
		throw new BQSQLFeatureNotSupportedException("getAttributes not supported");
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

	@Override
	public ResultSet getSchemas(String catalog, String schemaPattern) throws SQLException {
		checkClosed();

		logger.info("getSchemas() called - catalog: [{}], schemaPattern: [{}]", catalog, schemaPattern);

		String cacheKey = "schemas:" + catalog + ":" + schemaPattern;

		return getCachedOrExecute(cacheKey, () -> {
			String projectId = catalog != null ? catalog : connection.getProperties().projectId();

			// Use BigQuery API to list datasets
			com.google.cloud.bigquery.BigQuery bigquery = connection.getBigQuery();
			var datasets = bigquery.listDatasets(projectId);

			java.util.List<Object[]> rows = new java.util.ArrayList<>();
			for (com.google.cloud.bigquery.Dataset dataset : datasets.iterateAll()) {
				String datasetId = dataset.getDatasetId().getDataset();

				// Apply schema pattern filter if specified
				if (schemaPattern == null || matchesPattern(datasetId, schemaPattern)) {
					rows.add(new Object[]{datasetId, projectId});
				}
			}

			logger.info("getSchemas() returning {} schema(s)", rows.size());

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

	@Override
	public ResultSet getClientInfoProperties() throws SQLException {
		throw new BQSQLFeatureNotSupportedException("getClientInfoProperties not supported");
	}

	@Override
	public ResultSet getFunctions(String catalog, String schemaPattern, String functionNamePattern)
			throws SQLException {
		throw new BQSQLFeatureNotSupportedException("getFunctions not yet implemented");
	}

	@Override
	public ResultSet getFunctionColumns(String catalog, String schemaPattern, String functionNamePattern,
			String columnNamePattern) throws SQLException {
		throw new BQSQLFeatureNotSupportedException("getFunctionColumns not yet implemented");
	}

	@Override
	public ResultSet getPseudoColumns(String catalog, String schemaPattern, String tableNamePattern,
			String columnNamePattern) throws SQLException {
		throw new BQSQLFeatureNotSupportedException("getPseudoColumns not supported");
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
	private ResultSet getCachedOrExecute(String cacheKey, SqlSupplier<ResultSet> supplier) throws SQLException {
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
		if (totalOps > 0 && totalOps % STATS_LOG_INTERVAL == 0) {
			double hitRate = (double) cacheHits / totalOps * 100;
			logger.info("Metadata cache performance: {} hits, {} misses, {}% hit rate, {}", cacheHits, cacheMisses,
					String.format("%.1f", hitRate), cache != null ? cache.getStats() : "disabled");
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

			java.util.List<java.util.concurrent.CompletableFuture<java.util.List<Object[]>>> futures = items.stream()
					.map(item -> java.util.concurrent.CompletableFuture.supplyAsync(() -> {
						try {
							return processor.apply(item);
						} catch (SQLException e) {
							throw new RuntimeException(e);
						}
					}, executor)).toList();

			// Combine all results
			java.util.List<Object[]> allRows = new java.util.ArrayList<>();
			for (java.util.concurrent.CompletableFuture<java.util.List<Object[]>> future : futures) {
				try {
					allRows.addAll(future.join());
				} catch (java.util.concurrent.CompletionException e) {
					if (e.getCause() instanceof RuntimeException && e.getCause().getCause() instanceof SQLException) {
						throw (SQLException) e.getCause().getCause();
					}
					throw new SQLException(errorMessage, e);
				}
			}

			return allRows;
		}
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
	private java.util.List<String> listDatasetsForProject(com.google.cloud.bigquery.BigQuery bigQuery, String projectId,
			String schemaPattern) {
		var datasets = bigQuery.listDatasets(projectId);
		java.util.List<String> datasetIds = new java.util.ArrayList<>();

		for (com.google.cloud.bigquery.Dataset dataset : datasets.iterateAll()) {
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
	private boolean matchesPattern(String value, String pattern) {
		if (pattern == null) {
			return true;
		}

		// Handle SQL LIKE escape sequences before converting to regex
		// Use unique Unicode placeholders that won't be affected by string replacements
		String escapedUnderscore = "\u0001\u0002\u0003"; // Placeholder for \_
		String escapedPercent = "\u0004\u0005\u0006"; // Placeholder for \%
		String escapedBackslash = "\u0007\u0008\u0009"; // Placeholder for \\

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
		String regex = "^" + processed + "$";

		return value.matches(regex);
	}

	/**
	 * Clears the metadata cache for this project.
	 *
	 * <p>
	 * This method clears the shared cache for the current project, affecting
	 * all connections to this project. Use this to force a refresh of metadata
	 * after schema changes (DDL operations).
	 *
	 * <p>
	 * Note: The cache is shared across connections and persists based on TTL.
	 * This method should only be called when you need to force an immediate
	 * cache refresh, not during normal connection close operations.
	 */
	public void clearCache() {
		if (cache != null) {
			cache.clear();
			logger.info("Shared metadata cache cleared for project (cache key: {})", cacheKey);
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
	 * Clears all shared metadata caches across all projects.
	 *
	 * <p>
	 * This static method clears all shared cache instances, affecting all
	 * connections to all projects. This is primarily useful for:
	 * <ul>
	 * <li>Testing - to ensure tests start with a clean cache state
	 * <li>Debugging - to force a complete metadata refresh
	 * <li>After major schema changes - when you want to refresh all cached metadata
	 * </ul>
	 *
	 * <p>
	 * Under normal operation, you should not need to call this method as the
	 * cache expires entries based on TTL automatically.
	 */
	public static void clearAllSharedCaches() {
		int clearedCount = 0;
		for (MetadataCache cache : SHARED_CACHES.values()) {
			cache.clear();
			clearedCount++;
		}
		logger.info("Cleared {} shared metadata cache instance(s)", clearedCount);
	}

	/**
	 * Gets the number of shared cache instances currently active.
	 *
	 * <p>
	 * This is primarily useful for monitoring and debugging to understand
	 * how many distinct project caches are being maintained.
	 *
	 * @return the number of shared cache instances
	 */
	public static int getSharedCacheCount() {
		return SHARED_CACHES.size();
	}

}

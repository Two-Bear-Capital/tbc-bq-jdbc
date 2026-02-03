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
package com.twobearcapital.bigquery.jdbc;

import java.sql.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JDBC DatabaseMetaData implementation for BigQuery.
 *
 * <p>This implementation provides metadata about the BigQuery database and its capabilities.
 *
 * @since 1.0.0
 */
public class BQDatabaseMetaData implements DatabaseMetaData {

  private static final Logger logger = LoggerFactory.getLogger(BQDatabaseMetaData.class);

  private final BQConnection connection;
  private final MetadataCache cache;

  /**
   * Creates a new BigQuery DatabaseMetaData.
   *
   * @param connection the connection
   */
  public BQDatabaseMetaData(BQConnection connection) {
    this.connection = connection;
    ConnectionProperties properties = connection.getProperties();

    // Initialize cache if enabled
    if (properties.metadataCacheEnabled()) {
      java.time.Duration cacheTtl = java.time.Duration.ofSeconds(properties.metadataCacheTtl());
      this.cache = new MetadataCache(cacheTtl);
      logger.debug("Metadata cache enabled with TTL: {}", cacheTtl);
    } else {
      this.cache = null;
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
    return "Google BigQuery";
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
    return "1.0.0";
  }

  @Override
  public int getDriverMajorVersion() {
    return 1;
  }

  @Override
  public int getDriverMinorVersion() {
    return 0;
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
  public ResultSet getProcedureColumns(
      String catalog, String schemaPattern, String procedureNamePattern, String columnNamePattern)
      throws SQLException {
    throw new BQSQLFeatureNotSupportedException("getProcedureColumns not yet implemented");
  }

  @Override
  public ResultSet getTables(
      String catalog, String schemaPattern, String tableNamePattern, String[] types)
      throws SQLException {
    checkClosed();

    String typesKey = types != null ? java.util.Arrays.toString(types) : "null";
    String cacheKey =
        "tables:" + catalog + ":" + schemaPattern + ":" + tableNamePattern + ":" + typesKey;

    return getCachedOrExecute(
        cacheKey, () -> executeGetTables(catalog, schemaPattern, tableNamePattern, types));
  }

  private ResultSet executeGetTables(
      String catalog, String schemaPattern, String tableNamePattern, String[] types)
      throws SQLException {
    String projectId = catalog != null ? catalog : connection.getProperties().projectId();

    com.google.cloud.bigquery.BigQuery bigquery = connection.getBigQuery();
    boolean lazyLoad = connection.getProperties().metadataLazyLoad();

    // Lazy loading: If enabled and no specific patterns, return empty result
    // This allows IntelliJ to load the tree structure quickly without fetching all tables
    if (lazyLoad && schemaPattern == null && tableNamePattern == null) {
      logger.debug("Lazy loading enabled: returning empty table list (no patterns specified)");
      return createResultSet(
          new String[] {
            "TABLE_CAT",
            "TABLE_SCHEM",
            "TABLE_NAME",
            "TABLE_TYPE",
            "REMARKS",
            "TYPE_CAT",
            "TYPE_SCHEM",
            "TYPE_NAME",
            "SELF_REFERENCING_COL_NAME",
            "REF_GENERATION"
          },
          new int[] {
            java.sql.Types.VARCHAR,
            java.sql.Types.VARCHAR,
            java.sql.Types.VARCHAR,
            java.sql.Types.VARCHAR,
            java.sql.Types.VARCHAR,
            java.sql.Types.VARCHAR,
            java.sql.Types.VARCHAR,
            java.sql.Types.VARCHAR,
            java.sql.Types.VARCHAR,
            java.sql.Types.VARCHAR
          },
          new java.util.ArrayList<>());
    }

    // Get datasets matching schema pattern
    var datasets = bigquery.listDatasets(projectId);
    java.util.List<String> datasetIds = new java.util.ArrayList<>();

    for (com.google.cloud.bigquery.Dataset dataset : datasets.iterateAll()) {
      String datasetId = dataset.getDatasetId().getDataset();

      // Apply schema pattern filter
      if (schemaPattern == null || matchesPattern(datasetId, schemaPattern)) {
        datasetIds.add(datasetId);
      }
    }

    // Use parallel loading if there are multiple datasets (5+)
    java.util.List<Object[]> rows;
    if (datasetIds.size() >= 5) {
      logger.debug("Using parallel loading for {} datasets", datasetIds.size());
      rows = queryTablesParallel(projectId, datasetIds, tableNamePattern, types);
    } else {
      logger.debug("Using sequential loading for {} datasets", datasetIds.size());
      rows = queryTablesSequential(projectId, datasetIds, tableNamePattern, types);
    }

    return createResultSet(
        new String[] {
          "TABLE_CAT",
          "TABLE_SCHEM",
          "TABLE_NAME",
          "TABLE_TYPE",
          "REMARKS",
          "TYPE_CAT",
          "TYPE_SCHEM",
          "TYPE_NAME",
          "SELF_REFERENCING_COL_NAME",
          "REF_GENERATION"
        },
        new int[] {
          java.sql.Types.VARCHAR,
          java.sql.Types.VARCHAR,
          java.sql.Types.VARCHAR,
          java.sql.Types.VARCHAR,
          java.sql.Types.VARCHAR,
          java.sql.Types.VARCHAR,
          java.sql.Types.VARCHAR,
          java.sql.Types.VARCHAR,
          java.sql.Types.VARCHAR,
          java.sql.Types.VARCHAR
        },
        rows);
  }

  /** Query tables from multiple datasets sequentially. */
  private java.util.List<Object[]> queryTablesSequential(
      String projectId, java.util.List<String> datasetIds, String tableNamePattern, String[] types)
      throws SQLException {
    java.util.List<Object[]> allRows = new java.util.ArrayList<>();
    com.google.cloud.bigquery.BigQuery bigquery = connection.getBigQuery();

    for (String datasetId : datasetIds) {
      allRows.addAll(
          queryTablesForDataset(bigquery, projectId, datasetId, tableNamePattern, types));
    }

    return allRows;
  }

  /**
   * Query tables from multiple datasets in parallel using virtual threads.
   *
   * <p>This significantly improves performance for projects with many datasets (e.g., 90+).
   * Addresses JetBrains issue DBE-22088.
   */
  private java.util.List<Object[]> queryTablesParallel(
      String projectId, java.util.List<String> datasetIds, String tableNamePattern, String[] types)
      throws SQLException {
    com.google.cloud.bigquery.BigQuery bigquery = connection.getBigQuery();

    // Use virtual threads for concurrent queries
    try (java.util.concurrent.ExecutorService executor =
        java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {

      java.util.List<java.util.concurrent.CompletableFuture<java.util.List<Object[]>>> futures =
          datasetIds.stream()
              .map(
                  datasetId ->
                      java.util.concurrent.CompletableFuture.supplyAsync(
                          () -> {
                            try {
                              return queryTablesForDataset(
                                  bigquery, projectId, datasetId, tableNamePattern, types);
                            } catch (SQLException e) {
                              throw new RuntimeException(e);
                            }
                          },
                          executor))
              .toList();

      // Combine all results
      java.util.List<Object[]> allRows = new java.util.ArrayList<>();
      for (java.util.concurrent.CompletableFuture<java.util.List<Object[]>> future : futures) {
        try {
          allRows.addAll(future.join());
        } catch (java.util.concurrent.CompletionException e) {
          if (e.getCause() instanceof RuntimeException
              && e.getCause().getCause() instanceof SQLException) {
            throw (SQLException) e.getCause().getCause();
          }
          throw new SQLException("Error querying tables in parallel", e);
        }
      }

      return allRows;
    }
  }

  /** Query tables for a single dataset. */
  private java.util.List<Object[]> queryTablesForDataset(
      com.google.cloud.bigquery.BigQuery bigquery,
      String projectId,
      String datasetId,
      String tableNamePattern,
      String[] types)
      throws SQLException {
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

      rows.add(
          new Object[] {
            projectId, // TABLE_CAT
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

    return getCachedOrExecute(
        "catalogs",
        () -> {
          // BigQuery: Catalogs = Projects
          // Return the current project
          String projectId = connection.getProperties().projectId();

          java.util.List<Object[]> rows = new java.util.ArrayList<>();
          rows.add(new Object[] {projectId});

          return createResultSet(
              new String[] {"TABLE_CAT"}, new int[] {java.sql.Types.VARCHAR}, rows);
        });
  }

  @Override
  public ResultSet getTableTypes() throws SQLException {
    checkClosed();

    return getCachedOrExecute(
        "tableTypes",
        () -> {
          java.util.List<Object[]> rows = new java.util.ArrayList<>();
          rows.add(new Object[] {"TABLE"});
          rows.add(new Object[] {"VIEW"});
          rows.add(new Object[] {"MATERIALIZED VIEW"});

          return createResultSet(
              new String[] {"TABLE_TYPE"}, new int[] {java.sql.Types.VARCHAR}, rows);
        });
  }

  @Override
  public ResultSet getColumns(
      String catalog, String schemaPattern, String tableNamePattern, String columnNamePattern)
      throws SQLException {
    checkClosed();

    String cacheKey =
        "columns:"
            + catalog
            + ":"
            + schemaPattern
            + ":"
            + tableNamePattern
            + ":"
            + columnNamePattern;

    return getCachedOrExecute(
        cacheKey,
        () -> executeGetColumns(catalog, schemaPattern, tableNamePattern, columnNamePattern));
  }

  /** Query columns from multiple datasets sequentially. */
  private java.util.List<Object[]> queryColumnsSequential(
      String projectId,
      java.util.List<String> datasetIds,
      String tableNamePattern,
      String columnNamePattern)
      throws SQLException {
    java.util.List<Object[]> allRows = new java.util.ArrayList<>();
    com.google.cloud.bigquery.BigQuery bigquery = connection.getBigQuery();

    for (String datasetId : datasetIds) {
      allRows.addAll(
          queryColumnsForDataset(
              bigquery, projectId, datasetId, tableNamePattern, columnNamePattern));
    }

    return allRows;
  }

  /** Query columns from multiple datasets in parallel using virtual threads. */
  private java.util.List<Object[]> queryColumnsParallel(
      String projectId,
      java.util.List<String> datasetIds,
      String tableNamePattern,
      String columnNamePattern)
      throws SQLException {
    com.google.cloud.bigquery.BigQuery bigquery = connection.getBigQuery();

    // Use virtual threads for concurrent queries
    try (java.util.concurrent.ExecutorService executor =
        java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {

      java.util.List<java.util.concurrent.CompletableFuture<java.util.List<Object[]>>> futures =
          datasetIds.stream()
              .map(
                  datasetId ->
                      java.util.concurrent.CompletableFuture.supplyAsync(
                          () -> {
                            try {
                              return queryColumnsForDataset(
                                  bigquery,
                                  projectId,
                                  datasetId,
                                  tableNamePattern,
                                  columnNamePattern);
                            } catch (SQLException e) {
                              throw new RuntimeException(e);
                            }
                          },
                          executor))
              .toList();

      // Combine all results
      java.util.List<Object[]> allRows = new java.util.ArrayList<>();
      for (java.util.concurrent.CompletableFuture<java.util.List<Object[]>> future : futures) {
        try {
          allRows.addAll(future.join());
        } catch (java.util.concurrent.CompletionException e) {
          if (e.getCause() instanceof RuntimeException
              && e.getCause().getCause() instanceof SQLException) {
            throw (SQLException) e.getCause().getCause();
          }
          throw new SQLException("Error querying columns in parallel", e);
        }
      }

      return allRows;
    }
  }

  /** Query columns for a single dataset. */
  private java.util.List<Object[]> queryColumnsForDataset(
      com.google.cloud.bigquery.BigQuery bigquery,
      String projectId,
      String datasetId,
      String tableNamePattern,
      String columnNamePattern)
      throws SQLException {
    java.util.List<Object[]> rows = new java.util.ArrayList<>();

    var tables = bigquery.listTables(com.google.cloud.bigquery.DatasetId.of(projectId, datasetId));

    for (com.google.cloud.bigquery.Table table : tables.iterateAll()) {
      String tableName = table.getTableId().getTable();

      if (tableNamePattern != null && !matchesPattern(tableName, tableNamePattern)) {
        continue;
      }

      // Get full table with schema
      com.google.cloud.bigquery.Table fullTable = bigquery.getTable(table.getTableId());
      if (fullTable == null) {
        continue;
      }

      com.google.cloud.bigquery.Schema schema = fullTable.getDefinition().getSchema();
      if (schema == null) {
        continue;
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
        int nullable =
            field.getMode() == com.google.cloud.bigquery.Field.Mode.REQUIRED
                ? DatabaseMetaData.columnNoNulls
                : DatabaseMetaData.columnNullable;

        rows.add(
            new Object[] {
              projectId, // TABLE_CAT
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
    }

    return rows;
  }

  private ResultSet executeGetColumns(
      String catalog, String schemaPattern, String tableNamePattern, String columnNamePattern)
      throws SQLException {
    String projectId = catalog != null ? catalog : connection.getProperties().projectId();

    com.google.cloud.bigquery.BigQuery bigquery = connection.getBigQuery();
    boolean lazyLoad = connection.getProperties().metadataLazyLoad();

    // Lazy loading: If enabled and no specific table pattern, return empty result
    // This allows IntelliJ to load the tree structure quickly without fetching all columns
    if (lazyLoad && tableNamePattern == null) {
      logger.debug(
          "Lazy loading enabled: returning empty column list (no table pattern specified)");
      return createResultSet(
          new String[] {
            "TABLE_CAT",
            "TABLE_SCHEM",
            "TABLE_NAME",
            "COLUMN_NAME",
            "DATA_TYPE",
            "TYPE_NAME",
            "COLUMN_SIZE",
            "BUFFER_LENGTH",
            "DECIMAL_DIGITS",
            "NUM_PREC_RADIX",
            "NULLABLE",
            "REMARKS",
            "COLUMN_DEF",
            "SQL_DATA_TYPE",
            "SQL_DATETIME_SUB",
            "CHAR_OCTET_LENGTH",
            "ORDINAL_POSITION",
            "IS_NULLABLE",
            "SCOPE_CATALOG",
            "SCOPE_SCHEMA",
            "SCOPE_TABLE",
            "SOURCE_DATA_TYPE",
            "IS_AUTOINCREMENT",
            "IS_GENERATEDCOLUMN"
          },
          new int[] {
            java.sql.Types.VARCHAR, // TABLE_CAT
            java.sql.Types.VARCHAR, // TABLE_SCHEM
            java.sql.Types.VARCHAR, // TABLE_NAME
            java.sql.Types.VARCHAR, // COLUMN_NAME
            java.sql.Types.INTEGER, // DATA_TYPE
            java.sql.Types.VARCHAR, // TYPE_NAME
            java.sql.Types.INTEGER, // COLUMN_SIZE
            java.sql.Types.INTEGER, // BUFFER_LENGTH
            java.sql.Types.INTEGER, // DECIMAL_DIGITS
            java.sql.Types.INTEGER, // NUM_PREC_RADIX
            java.sql.Types.INTEGER, // NULLABLE
            java.sql.Types.VARCHAR, // REMARKS
            java.sql.Types.VARCHAR, // COLUMN_DEF
            java.sql.Types.INTEGER, // SQL_DATA_TYPE
            java.sql.Types.INTEGER, // SQL_DATETIME_SUB
            java.sql.Types.INTEGER, // CHAR_OCTET_LENGTH
            java.sql.Types.INTEGER, // ORDINAL_POSITION
            java.sql.Types.VARCHAR, // IS_NULLABLE
            java.sql.Types.VARCHAR, // SCOPE_CATALOG
            java.sql.Types.VARCHAR, // SCOPE_SCHEMA
            java.sql.Types.VARCHAR, // SCOPE_TABLE
            java.sql.Types.SMALLINT, // SOURCE_DATA_TYPE
            java.sql.Types.VARCHAR, // IS_AUTOINCREMENT
            java.sql.Types.VARCHAR // IS_GENERATEDCOLUMN
          },
          new java.util.ArrayList<>());
    }

    // Get datasets matching schema pattern
    var datasets = bigquery.listDatasets(projectId);
    java.util.List<String> datasetIds = new java.util.ArrayList<>();

    for (com.google.cloud.bigquery.Dataset dataset : datasets.iterateAll()) {
      String datasetId = dataset.getDatasetId().getDataset();

      // Apply schema pattern filter
      if (schemaPattern == null || matchesPattern(datasetId, schemaPattern)) {
        datasetIds.add(datasetId);
      }
    }

    // Use parallel loading if there are multiple datasets (5+)
    java.util.List<Object[]> rows;
    if (datasetIds.size() >= 5) {
      logger.debug("Using parallel loading for columns in {} datasets", datasetIds.size());
      rows = queryColumnsParallel(projectId, datasetIds, tableNamePattern, columnNamePattern);
    } else {
      logger.debug("Using sequential loading for columns in {} datasets", datasetIds.size());
      rows = queryColumnsSequential(projectId, datasetIds, tableNamePattern, columnNamePattern);
    }

    return createResultSet(
        new String[] {
          "TABLE_CAT",
          "TABLE_SCHEM",
          "TABLE_NAME",
          "COLUMN_NAME",
          "DATA_TYPE",
          "TYPE_NAME",
          "COLUMN_SIZE",
          "BUFFER_LENGTH",
          "DECIMAL_DIGITS",
          "NUM_PREC_RADIX",
          "NULLABLE",
          "REMARKS",
          "COLUMN_DEF",
          "SQL_DATA_TYPE",
          "SQL_DATETIME_SUB",
          "CHAR_OCTET_LENGTH",
          "ORDINAL_POSITION",
          "IS_NULLABLE",
          "SCOPE_CATALOG",
          "SCOPE_SCHEMA",
          "SCOPE_TABLE",
          "SOURCE_DATA_TYPE",
          "IS_AUTOINCREMENT",
          "IS_GENERATEDCOLUMN"
        },
        new int[] {
          java.sql.Types.VARCHAR, // TABLE_CAT
          java.sql.Types.VARCHAR, // TABLE_SCHEM
          java.sql.Types.VARCHAR, // TABLE_NAME
          java.sql.Types.VARCHAR, // COLUMN_NAME
          java.sql.Types.INTEGER, // DATA_TYPE
          java.sql.Types.VARCHAR, // TYPE_NAME
          java.sql.Types.INTEGER, // COLUMN_SIZE
          java.sql.Types.INTEGER, // BUFFER_LENGTH
          java.sql.Types.INTEGER, // DECIMAL_DIGITS
          java.sql.Types.INTEGER, // NUM_PREC_RADIX
          java.sql.Types.INTEGER, // NULLABLE
          java.sql.Types.VARCHAR, // REMARKS
          java.sql.Types.VARCHAR, // COLUMN_DEF
          java.sql.Types.INTEGER, // SQL_DATA_TYPE
          java.sql.Types.INTEGER, // SQL_DATETIME_SUB
          java.sql.Types.INTEGER, // CHAR_OCTET_LENGTH
          java.sql.Types.INTEGER, // ORDINAL_POSITION
          java.sql.Types.VARCHAR, // IS_NULLABLE
          java.sql.Types.VARCHAR, // SCOPE_CATALOG
          java.sql.Types.VARCHAR, // SCOPE_SCHEMA
          java.sql.Types.VARCHAR, // SCOPE_TABLE
          java.sql.Types.SMALLINT, // SOURCE_DATA_TYPE
          java.sql.Types.VARCHAR, // IS_AUTOINCREMENT
          java.sql.Types.VARCHAR // IS_GENERATEDCOLUMN
        },
        rows);
  }

  @Override
  public ResultSet getColumnPrivileges(
      String catalog, String schema, String table, String columnNamePattern) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("getColumnPrivileges not supported");
  }

  @Override
  public ResultSet getTablePrivileges(String catalog, String schemaPattern, String tableNamePattern)
      throws SQLException {
    throw new BQSQLFeatureNotSupportedException("getTablePrivileges not supported");
  }

  @Override
  public ResultSet getBestRowIdentifier(
      String catalog, String schema, String table, int scope, boolean nullable)
      throws SQLException {
    throw new BQSQLFeatureNotSupportedException("getBestRowIdentifier not supported");
  }

  @Override
  public ResultSet getVersionColumns(String catalog, String schema, String table)
      throws SQLException {
    throw new BQSQLFeatureNotSupportedException("getVersionColumns not supported");
  }

  @Override
  public ResultSet getPrimaryKeys(String catalog, String schema, String table) throws SQLException {
    throw new BQSQLFeatureNotSupportedException("getPrimaryKeys not supported");
  }

  @Override
  public ResultSet getImportedKeys(String catalog, String schema, String table)
      throws SQLException {
    throw new BQSQLFeatureNotSupportedException("getImportedKeys not supported");
  }

  @Override
  public ResultSet getExportedKeys(String catalog, String schema, String table)
      throws SQLException {
    throw new BQSQLFeatureNotSupportedException("getExportedKeys not supported");
  }

  @Override
  public ResultSet getCrossReference(
      String parentCatalog,
      String parentSchema,
      String parentTable,
      String foreignCatalog,
      String foreignSchema,
      String foreignTable)
      throws SQLException {
    throw new BQSQLFeatureNotSupportedException("getCrossReference not supported");
  }

  @Override
  public ResultSet getTypeInfo() throws SQLException {
    throw new BQSQLFeatureNotSupportedException("getTypeInfo not yet implemented");
  }

  @Override
  public ResultSet getIndexInfo(
      String catalog, String schema, String table, boolean unique, boolean approximate)
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
  public ResultSet getUDTs(
      String catalog, String schemaPattern, String typeNamePattern, int[] types)
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
  public ResultSet getSuperTypes(String catalog, String schemaPattern, String typeNamePattern)
      throws SQLException {
    throw new BQSQLFeatureNotSupportedException("getSuperTypes not supported");
  }

  @Override
  public ResultSet getSuperTables(String catalog, String schemaPattern, String tableNamePattern)
      throws SQLException {
    throw new BQSQLFeatureNotSupportedException("getSuperTables not supported");
  }

  @Override
  public ResultSet getAttributes(
      String catalog, String schemaPattern, String typeNamePattern, String attributeNamePattern)
      throws SQLException {
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

    String cacheKey = "schemas:" + catalog + ":" + schemaPattern;

    return getCachedOrExecute(
        cacheKey,
        () -> {
          String projectId = catalog != null ? catalog : connection.getProperties().projectId();

          // Use BigQuery API to list datasets
          com.google.cloud.bigquery.BigQuery bigquery = connection.getBigQuery();
          var datasets = bigquery.listDatasets(projectId);

          java.util.List<Object[]> rows = new java.util.ArrayList<>();
          for (com.google.cloud.bigquery.Dataset dataset : datasets.iterateAll()) {
            String datasetId = dataset.getDatasetId().getDataset();

            // Apply schema pattern filter if specified
            if (schemaPattern == null || matchesPattern(datasetId, schemaPattern)) {
              rows.add(new Object[] {datasetId, projectId});
            }
          }

          return createResultSet(
              new String[] {"TABLE_SCHEM", "TABLE_CATALOG"},
              new int[] {java.sql.Types.VARCHAR, java.sql.Types.VARCHAR},
              rows);
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
  public ResultSet getFunctionColumns(
      String catalog, String schemaPattern, String functionNamePattern, String columnNamePattern)
      throws SQLException {
    throw new BQSQLFeatureNotSupportedException("getFunctionColumns not yet implemented");
  }

  @Override
  public ResultSet getPseudoColumns(
      String catalog, String schemaPattern, String tableNamePattern, String columnNamePattern)
      throws SQLException {
    throw new BQSQLFeatureNotSupportedException("getPseudoColumns not supported");
  }

  @Override
  public boolean generatedKeyAlwaysReturned() throws SQLException {
    return false;
  }

  @Override
  public long getMaxLogicalLobSize() throws SQLException {
    return 0;
  }

  @Override
  public boolean supportsRefCursors() throws SQLException {
    return false;
  }

  @Override
  public boolean supportsSharding() throws SQLException {
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
   * @param cacheKey the cache key
   * @param supplier the function to execute if cache miss
   * @return the ResultSet (either from cache or freshly generated)
   * @throws SQLException if query execution fails
   */
  private ResultSet getCachedOrExecute(String cacheKey, SqlSupplier<ResultSet> supplier)
      throws SQLException {
    // Check cache if enabled
    if (cache != null) {
      java.util.Optional<ResultSet> cached = cache.get(cacheKey);
      if (cached.isPresent()) {
        logger.trace("Returning cached result for: {}", cacheKey);
        return cached.get();
      }
    }

    // Execute query
    ResultSet result = supplier.get();

    // Store in cache if enabled
    if (cache != null && result instanceof MetadataResultSet) {
      cache.put(cacheKey, result);
      logger.trace("Cached result for: {}", cacheKey);
    }

    return result;
  }

  /** Functional interface for SQL operations that can throw SQLException. */
  @FunctionalInterface
  private interface SqlSupplier<T> {
    T get() throws SQLException;
  }

  /**
   * Create a ResultSet from column names, types, and data rows.
   *
   * @param columnNames array of column names
   * @param columnTypes array of JDBC type constants
   * @param rows list of data rows (each row is an Object array)
   * @return ResultSet containing the data
   * @throws SQLException if result set creation fails
   */
  private ResultSet createResultSet(
      String[] columnNames, int[] columnTypes, java.util.List<Object[]> rows) throws SQLException {
    return new MetadataResultSet(columnNames, columnTypes, rows);
  }

  /**
   * Convert SQL LIKE pattern to Java regex pattern.
   *
   * @param value the value to match
   * @param pattern the SQL LIKE pattern (% = wildcard, _ = single char)
   * @return true if value matches pattern
   */
  private boolean matchesPattern(String value, String pattern) {
    if (pattern == null) {
      return true;
    }
    // Convert SQL LIKE pattern to regex: % -> .*, _ -> .
    String regex =
        "^"
            + pattern.replace("\\", "\\\\").replace(".", "\\.").replace("%", ".*").replace("_", ".")
            + "$";
    return value.matches(regex);
  }

  @Override
  public <T> T unwrap(Class<T> iface) throws SQLException {
    if (iface.isInstance(this)) {
      return iface.cast(this);
    }
    throw new SQLException("Cannot unwrap to " + iface.getName());
  }

  @Override
  public boolean isWrapperFor(Class<?> iface) {
    return iface.isInstance(this);
  }
}

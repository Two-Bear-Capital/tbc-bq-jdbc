# Compatibility

What works, what doesn't, and how to work around BigQuery's constraints.

**Specification:** JDBC 4.3 (Java 21+) · **Compliance:** partial. `Driver.jdbcCompliant()` returns `false`. BigQuery enforces no keys, has no indexes, and supports no savepoints, configurable isolation levels, updatable result sets, or `CallableStatement`.

**Legend** — used in every support table below:

| | |
|---|---|
| ✅ | Supported |
| ⚠️ | Partial or limited (see notes) |
| ❌ | Not supported |

---

## JDBC features

### Core JDBC

| Feature | Support | Notes |
|---------|:------:|-------|
| `DriverManager` registration | ✅ | Automatic via ServiceLoader |
| `Connection` lifecycle | ✅ | open, close, isValid |
| `Statement` execution | ✅ | executeQuery, executeUpdate, execute |
| `PreparedStatement` | ✅ | Positional parameters (`?`) |
| Batch updates (`addBatch()`, `executeBatch()`) | ✅ | See [Batch execution](#batch-execution) |
| Update counts | ✅ | `executeUpdate()` / `getUpdateCount()` return real affected-row counts from BigQuery DML statistics |
| `ResultSet` iteration | ✅ | Forward-only (`TYPE_FORWARD_ONLY`) |
| `Statement.setFetchSize()` | ✅ | Page size for that statement, overriding the connection's `pageSize`. `0` restores the connection default; `getFetchSize()` reports the effective value |
| `ResultSetMetaData` | ✅ | Column names, types, counts |
| `DatabaseMetaData` | ⚠️ | See [DatabaseMetaData](#databasemetadata) |
| `SQLException` hierarchy | ✅ | With SQLState codes. A parameter value BigQuery's client rejects arrives as a `SQLException` with SQLState `22023`, naming the parameter, rather than as an unchecked exception |
| Type conversions | ✅ | All BigQuery types; see [Advanced types](#advanced-types) for the JDBC types that do not apply |
| `NULL` handling | ✅ | `wasNull()` |
| `beginRequest()` / `endRequest()` (4.3) | ✅ | Connection-pooling hints |
| `enquoteLiteral()`, `enquoteIdentifier()`, `isSimpleIdentifier()` (4.3) | ✅ | SQL/identifier quoting & validation |

### Batch execution

`PreparedStatement.addBatch()` / `executeBatch()` is the recommended bulk-insert path:

```java
try (PreparedStatement ps = conn.prepareStatement(
        "INSERT INTO dataset.users (id, name) VALUES (?, ?)")) {
    for (User user : users) {
        ps.setLong(1, user.id());
        ps.setString(2, user.name());
        ps.addBatch();
    }
    int[] counts = ps.executeBatch();
}
```

When the SQL is a simple parameterized INSERT (`INSERT INTO t (...) VALUES (?, ...)`),
the driver collapses the batch into multi-row `INSERT ... VALUES (...), (...), ...`
statements, the equivalent of PostgreSQL's `reWriteBatchedInserts`. Large batches are
chunked to stay under BigQuery's per-query limits (10,000 query parameters, ~1 MB query
text), one query job per chunk.

Details:

- Statements that cannot be collapsed execute sequentially, one query job per parameter
  set — correct, but subject to BigQuery DML quotas and job latency. This covers
  UPDATE/DELETE/MERGE, `INSERT ... SELECT`, tuples mixing literals with placeholders,
  and any batch whose parameter sets do not all match the template's placeholder count.
- `Statement.addBatch(String)` (heterogeneous SQL batches) also executes sequentially.
- Set `batchLoadThreshold` to have large batches written by a single BigQuery **load job**
  instead of chunked DML — not bound by DML quotas, and far faster at volume. Off by
  default; see [Connection properties](CONNECTION_PROPERTIES.md#performance-tuning) for
  the conditions a batch must meet, including that load jobs cannot join a transaction.
- Update counts come from BigQuery DML statistics. On the sequential path each entry
  carries that statement's affected-row count. On the collapsed path a chunk reports
  `1` per row only when BigQuery confirms exactly the expected total; otherwise every
  entry in that chunk is `Statement.SUCCESS_NO_INFO`.
- Failures throw `BatchUpdateException` with update counts for the work completed
  before the failure.
- `addBatch()` snapshots the current parameter set and clears the working parameters,
  so set every parameter for each row.

### BigQuery-specific

| Feature | Support | Notes |
|---------|:------:|-------|
| Sessions | ✅ | `enableSessions=true`, or started on demand by `setAutoCommit(false)` |
| Temp tables | ✅ | Requires sessions to survive across statements |
| Multi-statement SQL | ✅ | Runs as a single job, with every statement's result reachable via `getMoreResults()` — [see below](#multi-statement-script-results). A session is needed only for temp entities or transactions spanning statements |
| Transactions | ⚠️ | Session-backed; no isolation levels or savepoints |
| Storage Read API | ⚠️ | Opt-in via `useStorageApi=true` (always) or `auto` (large results); covers scalars, ARRAY, STRUCT and INTERVAL, and needs `--add-opens=java.base/java.nio=ALL-UNNAMED`. A `RANGE` column sends the result to the standard path, as does anything else that makes the Storage API unavailable |
| Query labels | ✅ | Job labels for tracking |
| Location routing | ✅ | Multi-region support |
| Query timeout | ✅ | Hard timeout enforcement |
| Query cancellation | ✅ | `Statement.cancel()` |

---

## Transactions

| Feature | Support | Notes |
|---------|:------:|-------|
| `setAutoCommit(false)`, `commit()`, `rollback()` | ✅ | Runs in a BigQuery session |
| `Savepoint` | ❌ | Not supported |
| Transaction isolation levels | ⚠️ | Snapshot isolation only, reported as `TRANSACTION_REPEATABLE_READ` |
| Concurrent statements during a transaction | ❌ | BigQuery forbids concurrent queries in a session |
| Permanent DDL inside a transaction | ❌ | Only DML and temp-entity DDL are transactional |

BigQuery transactions only exist inside a session, but the driver starts one for you the
first time you disable auto-commit — no connection property required:

```java
Connection conn = DriverManager.getConnection("jdbc:bigquery:my-project/my_dataset?authType=ADC");
conn.setAutoCommit(false); // starts a session; BEGIN comes with the first statement
// ... execute statements ...
conn.commit();             // COMMIT; the next statement opens a new transaction
```

The next statement after a `commit()`/`rollback()` opens a new transaction, so periodic-commit
loops work as they do on other drivers. Calling either while auto-commit is enabled throws
`SQLException` (SQLState `25000`). Closing a connection with uncommitted work rolls it back.

Creating the session costs one query job. On a connection that has no session yet, the first
`setAutoCommit(false)` pays it; later toggles do not. Set `enableSessions=true` to pay it at
connection open instead. `BEGIN TRANSACTION` is deferred to the first statement, so disabling
auto-commit and then running nothing costs no additional job.

**Isolation.** BigQuery transactions provide snapshot isolation: every read in the transaction
sees a consistent snapshot of the tables it references. `getDefaultTransactionIsolation()` and
`Connection.getTransactionIsolation()` report `TRANSACTION_REPEATABLE_READ`, the closest
standard level. `setTransactionIsolation()` accepts `TRANSACTION_REPEATABLE_READ` and
`TRANSACTION_NONE` and rejects the others; the value is recorded but does not change engine
behavior. `supportsTransactions()` returns `true`; `supportsSavepoints()` returns `false`.

**Concurrency.** BigQuery rejects concurrent queries within a session, so statements on a
connection with an active session must run sequentially. The driver does not enforce this
itself — the error comes from BigQuery.

---

## Unsupported JDBC features

### ResultSet

| Feature | Support | Workaround |
|---------|:------:|------------|
| Scrollable result sets (`TYPE_SCROLL_*`) | ❌ | Cache rows in the application |
| Updatable result sets (`CONCUR_UPDATABLE`) | ❌ | Use DML statements |
| `updateRow()`, `deleteRow()`, `insertRow()` | ❌ | Use DML / INSERT statements |
| `beforeFirst()`, `absolute()`, `relative()` | ❌ | Forward-only iteration |
| `ResultSet.setFetchSize()` | ⚠️ | Recorded and reported by `getFetchSize()`, but the rows have already been paged — set it on the `Statement` before executing to change paging |
| Holdability | ⚠️ | Always `CLOSE_CURSORS_AT_COMMIT`; any other value throws |

BigQuery results are streaming and forward-only. Cache rows if you need random access:

```java
List<Row> cache = new ArrayList<>();
while (rs.next()) {
    cache.add(new Row(rs));
}
```

### Statement

| Feature | Support | Workaround |
|---------|:------:|------------|
| `CallableStatement`, stored procedures | ❌ | Use standard queries / scripting |
| Generated keys (`getGeneratedKeys()`) | ❌ | Query the table after INSERT |
| Named cursors | ❌ | Forward-only iteration |
| `getMoreResults()` / `getMoreResults(int)` | ✅ | Walks a multi-statement script's results — [see below](#multi-statement-script-results) |

#### Multi-statement script results

BigQuery runs a multi-statement script as one job with a **child job per executed
statement**. The parent job carries only the last statement's result, so the JDBC way to
reach the rest is `getMoreResults()`:

```java
boolean isResultSet = stmt.execute("SELECT 1 AS a; INSERT INTO t VALUES (2); SELECT 3 AS c;");
while (true) {
    if (isResultSet) {
        try (ResultSet rs = stmt.getResultSet()) { /* … */ }
    } else if (stmt.getUpdateCount() == -1) {
        break;                       // no more results
    }
    isResultSet = stmt.getMoreResults();
    if (!isResultSet && stmt.getUpdateCount() == -1) {
        break;
    }
}
```

- Results come back **in execution order**, and the first result is the first statement's.
- A `SELECT` step is a `ResultSet`; every other statement type is an update count — DML
  reports its affected rows, DDL reports 0.
- Only statements that **actually ran** appear. A `DECLARE` produces none, and neither does
  an untaken `IF` branch, so the sequence is the execution trace rather than the script text.
- `getMoreResults(KEEP_CURRENT_RESULT)` leaves the previous `ResultSet` open; the other two
  constants close it. Any other argument throws.
- Running anything else on the same `Statement` discards the walk.

> **Changed in 4.0.0.** `executeQuery()` on a script previously returned the parent job's
> result, which is the **last** statement's rows, and no other statement was reachable. It
> now returns the first statement's result, as JDBC specifies.

### Advanced types

| Feature | Support | Notes |
|---------|:------:|-------|
| `Array` | ✅ | `getObject()` returns a JSON string by default, or a `java.sql.Array` with `nativeComplexTypes=true`. `getArray()`, `setArray()` and `Connection.createArrayOf()` always work |
| `RANGE` | ⚠️ | `getString()` returns the BigQuery literal, e.g. `[2020-01-01, 2020-12-31)`, with `UNBOUNDED` for an absent bound. `getObject()` returns the client's `Range` object. A RANGE column sends the result to the standard path rather than the Storage Read API |
| `Struct` | ⚠️ | `getObject()` returns a JSON string by default, or a `java.sql.Struct` with `nativeComplexTypes=true`. Writable: `Connection.createStruct("STRUCT<a INT64, b STRING>", …)`, or pass a `Map<String, Object>` or `Struct` to `setObject()`. A `Struct` whose type name does not name its fields cannot be bound |
| `Blob`, `Clob`, `NClob` | ❌ | Use `byte[]` and `String` |
| `SQLXML` | ❌ | Use `String` with JSON |
| `Ref`, `RowId`, Sharding API | ❌ | Not applicable to BigQuery |
| Custom type maps | ❌ | `Connection.setTypeMap()` and `ResultSet.getObject(col, map)` with a populated map both throw `SQLFeatureNotSupportedException`; `getTypeMap()` returns an empty map. A null or empty map is accepted and returns the default mapping |

---

## DatabaseMetaData

Metadata is cached, and table and column lookups are loaded in parallel (see
[IntelliJ performance](INTELLIJ.md#performance-tuning)).

With `metadataLazyLoad=true`, `getTables()` and `getColumns()` return an **empty** result
when called with no schema or table pattern. This keeps IDE tree expansion fast on large
projects, but a tool that enumerates everything up front will see nothing.

| Method | Support | Notes |
|--------|:------:|-------|
| `getCatalogs()` | ✅ | The connection's project, plus any named by `additionalProjects`, ordered by `TABLE_CAT`. Cached — [see below](#browsing-more-than-one-project) |
| `getSchemas()` | ✅ | Datasets, with pattern filtering. Cached. Also reports a synthetic `INFORMATION_SCHEMA` schema unless `includeInformationSchema=false` |
| `getTables()` | ✅ | Tables, views, materialized views. Loaded in parallel, cached. `REMARKS` carries the table's description, falling back to the defining SQL for a view or materialized view that has none. Set `metadataIncludeDescriptions=false` to skip the description read. With `collapseShardedTables=true`, date-sharded sets report as one `events_*` entry. `INFORMATION_SCHEMA` views are included as `SYSTEM TABLE` — see below |
| `getColumns()` | ✅ | 24-column metadata with accurate precision/scale. Loaded in parallel, cached |
| `getTableTypes()` | ✅ | TABLE, VIEW, MATERIALIZED VIEW, EXTERNAL, SNAPSHOT, CLONE, and SYSTEM TABLE while `includeInformationSchema` is on — [see below](#table-types) |
| `getProcedures()` / `getProcedureColumns()` | ✅ | Stored procedures from `INFORMATION_SCHEMA`, cached. UDFs and table functions are reported by `getFunctions()` instead. `REMARKS` carries the routine body |
| `getTypeInfo()` | ✅ | BigQuery type information |
| Product info, JDBC version, SQL keyword and function lists | ✅ | JDBC version reports 4.3. `getDatabaseProductName()` is `BigQuery (TBC Driver)` and `getDatabaseProductVersion()` is `2.0` |
| `getFunctions()` / `getFunctionColumns()` | ✅ | Persistent UDFs and table functions from `INFORMATION_SCHEMA.ROUTINES`, cached. `REMARKS` carries the routine body. A table function reports `functionReturnsTable`; the return value is the `functionReturn` row at ordinal 0 |
| `getPseudoColumns()` | ✅ | Ingestion-time partitioning columns, cached. `_PARTITIONTIME` on every ingestion-time partitioned table, plus `_PARTITIONDATE` on those partitioned by day — BigQuery exposes that one at daily granularity only |
| `getPrimaryKeys()` | ✅ | Declared `PRIMARY KEY ... NOT ENFORCED` constraints, cached — [see below](#unenforced-primary-and-foreign-keys) |
| `getImportedKeys()`, `getExportedKeys()`, `getCrossReference()` | ✅ | Declared `FOREIGN KEY ... NOT ENFORCED` constraints, cached — [see below](#unenforced-primary-and-foreign-keys) |
| `getIndexInfo()` | ⚠️ | BigQuery has no indexes; returns empty |
| `getColumnPrivileges()`, `getTablePrivileges()` | ⚠️ | BigQuery uses IAM; returns empty |
| `getBestRowIdentifier()` | ⚠️ | BigQuery enforces no uniqueness, so no column set can be promised to identify a row; returns empty |
| `getUDTs()`, `getSuperTypes()`, `getSuperTables()`, `getAttributes()` | ⚠️ | BigQuery has no user-defined types; returns empty |
| `getClientInfoProperties()` | ⚠️ | The driver accepts no client info properties; returns empty |

### Table types

`getTables()` distinguishes the kinds of table BigQuery does, because they do not behave
alike — an external table cannot be the target of DML, and a snapshot is read-only and
point-in-time.

| `TABLE_TYPE` | BigQuery |
|---|---|
| `TABLE` | an ordinary table (`BASE TABLE`) |
| `VIEW` | a view |
| `MATERIALIZED VIEW` | a materialized view |
| `EXTERNAL` | a table over external data (GCS, Sheets, …) |
| `SNAPSHOT` | a table snapshot — read-only, point-in-time |
| `CLONE` | a table clone — writable, sharing storage with its base until diverged |
| `SYSTEM TABLE` | an `INFORMATION_SCHEMA` view, while `includeInformationSchema` is on |

JDBC standardises only `TABLE` and `VIEW`, so the rest are a driver convention. The strings
are BigQuery's own — the values `INFORMATION_SCHEMA.TABLES.table_type` reports — so what the
driver says and what you see in BigQuery are the same word.

> **Changed in 4.0.0.** External tables, snapshots and clones were previously reported as
> `TABLE`. A caller filtering `getTables(…, new String[]{"TABLE"})` no longer receives them.

**One caveat.** BigQuery's `tables.list` reports a clone as an ordinary table; only
`INFORMATION_SCHEMA` distinguishes it. The driver reads that view anyway for table
descriptions, so recognising clones costs no extra query — but with
`metadataIncludeDescriptions=false` there is no such read, and a clone is reported as
`TABLE`. Every other type comes from the listing itself and is unaffected.

### Browsing more than one project

Catalogs are projects. BigQuery queries across projects natively, and the metadata methods
have always honoured an explicit `catalog` argument — what was missing was **discovery**
and **switching**.

`additionalProjects` names further projects to report from `getCatalogs()`:

```
jdbc:bigquery:my-project/my_dataset?additionalProjects=other-project,third-project
```

They are not discovered automatically. Listing every project a credential can see is a
Resource Manager call, slow on a large organisation, and returns mostly projects with no
BigQuery data.

`setCatalog()` moves the project that a **null** `catalog` argument resolves to:

```java
conn.setCatalog("other-project");
conn.getMetaData().getSchemas();              // other-project's datasets
conn.getMetaData().getSchemas("my-project", null);  // an explicit argument still wins
conn.setCatalog(null);                        // back to the connection's own project
```

- An unusable project id is **rejected**, not ignored. `setCatalog()` used to be a silent
  no-op, so a caller had no way to tell a switch that did not happen from one that did.
- A project need not be in `additionalProjects` to be switched to — that property controls
  what is *listed*, not what is reachable.
- **Billing does not move.** The project that owns and is billed for jobs is fixed when the
  connection opens; `setCatalog()` changes only which project metadata and unqualified
  names default to. Querying another project's data bills the connection's project, which
  is how BigQuery cross-project access already works.
- `datasetProjectId` is unaffected and still points the *default dataset* at another
  project.

### Browsing INFORMATION_SCHEMA

BigQuery's `INFORMATION_SCHEMA` views are ordinary queryable views, but the datasets API
does not list them and neither does BigQuery's own `INFORMATION_SCHEMA.SCHEMATA`. The
driver reports them so they can be browsed and autocompleted. This costs no BigQuery
query — the view list is static.

BigQuery scopes the views in two places, and the two sets are disjoint:

| Scope | Queried as | Reported as | Views |
|-------|-----------|-------------|-------|
| Project | `` `project`.INFORMATION_SCHEMA.SCHEMATA `` | a schema named `INFORMATION_SCHEMA`, holding tables named `SCHEMATA`, `JOBS`, … | `SCHEMATA`, `SCHEMATA_OPTIONS`, `SCHEMATA_LINKS`, `JOBS`, `JOBS_BY_PROJECT`, `JOBS_BY_USER`, `JOBS_TIMELINE`, `JOBS_TIMELINE_BY_USER`, `SESSIONS_BY_PROJECT`, `SESSIONS_BY_USER`, `TABLE_STORAGE`, `TABLE_STORAGE_TIMELINE`, `OBJECT_PRIVILEGES`, `STREAMING_TIMELINE_BY_PROJECT`, `WRITE_API_TIMELINE_BY_PROJECT`, `SHARED_DATASET_USAGE`, `RECOMMENDATIONS`, `INSIGHTS` |
| Dataset | `` `project`.`dataset`.INFORMATION_SCHEMA.TABLES `` | tables of the dataset, named `INFORMATION_SCHEMA.TABLES`, … | `TABLES`, `TABLE_OPTIONS`, `TABLE_CONSTRAINTS`, `TABLE_SNAPSHOTS`, `COLUMNS`, `COLUMN_FIELD_PATHS`, `VIEWS`, `MATERIALIZED_VIEWS`, `ROUTINES`, `ROUTINE_OPTIONS`, `PARAMETERS`, `KEY_COLUMN_USAGE`, `CONSTRAINT_COLUMN_USAGE`, `PARTITIONS`, `SEARCH_INDEXES`, `SEARCH_INDEX_COLUMNS`, `VECTOR_INDEXES` |

A dataset-scoped view needs four name parts and JDBC has three, which is why the last two
are carried together in the table name. BigQuery accepts that name however a tool quotes
it:

```sql
SELECT table_name FROM `my-project`.`sales`.`INFORMATION_SCHEMA.TABLES`
SELECT table_name FROM `my-project`.`sales`.INFORMATION_SCHEMA.TABLES
```

All of them are reported with `TABLE_TYPE` of `SYSTEM TABLE`, so
`getTables(..., new String[]{"TABLE", "VIEW"})` excludes them.

`getColumns()` describes these views from the live service, so the column lists never go
stale. Resolving one costs a dry run — no job, no bytes billed — and each is resolved at
most once per connection.

Region-qualified views (`` `project`.`region-us`.INFORMATION_SCHEMA.JOBS ``) are not
reported. They need a region the connection does not necessarily know, and the ones unique
to that scope scan the whole organisation's job history.

Set `includeInformationSchema=false` to turn all of this off.

### Unenforced primary and foreign keys

BigQuery accepts declarative key constraints, but only as `NOT ENFORCED`:

```sql
CREATE TABLE shop.customers (
  id INT64 NOT NULL,
  email STRING,
  PRIMARY KEY (id) NOT ENFORCED
);

CREATE TABLE shop.orders (
  order_id INT64 NOT NULL,
  customer_id INT64,
  PRIMARY KEY (order_id) NOT ENFORCED,
  CONSTRAINT fk_customer FOREIGN KEY (customer_id)
    REFERENCES shop.customers(id) NOT ENFORCED
);
```

The driver reads these from `INFORMATION_SCHEMA` and reports them through the four
JDBC key methods, so ER diagrams, query builders and FK-aware data generators see the
relationships a schema author declared.

**BigQuery never validates them.** Nothing stops an `orders` row from carrying a
`customer_id` that no customer has. Treat these keys as a statement of intent about
the data, not a guarantee about it. `UPDATE_RULE` and `DELETE_RULE` are reported as
`importedKeyNoAction`, and `DEFERRABILITY` as `importedKeyNotDeferrable`.

Things worth knowing:

- **Constraint names are table-qualified.** A primary key is always `<table>.pk$`, and a
  named foreign key appears as `<table>.fk_customer`. The value is reported verbatim, so
  it joins back to `INFORMATION_SCHEMA`.
- **`schema` and `table` arguments are names, not patterns**, per the JDBC contract. An
  `_` is a literal underscore, which matters because BigQuery names are full of them.
- **`getExportedKeys()` scans every dataset in the project**, since a foreign key is
  recorded only in the dataset holding the *referencing* table. Foreign keys declared in
  *other projects* are not found.
- **Constraints follow the metadata cache.** Setting `metadataCacheEnabled=false` makes
  each call re-read from BigQuery.

---

## SQL operations

### Data manipulation (DML)

| Operation | Support | Notes |
|-----------|:------:|-------|
| `SELECT` | ✅ | All query features |
| `INSERT` | ✅ | Via DML or `executeUpdate()` |
| `UPDATE`, `DELETE` | ✅ | Via DML (`WHERE` required) |
| `MERGE` | ✅ | Via DML |
| `TRUNCATE` | ✅ | Via DDL |
| Row-by-row updates | ❌ | Must use DML |

### Schema (DDL)

| Operation | Support | Notes |
|-----------|:------:|-------|
| `CREATE TABLE` / `DROP TABLE` | ✅ | Via DDL |
| `CREATE VIEW` | ✅ | Via DDL |
| `ALTER TABLE` | ⚠️ | Limited to the column operations BigQuery supports |
| `CREATE PROCEDURE` / `CREATE FUNCTION` | ✅ | Executed as DDL like any other statement. `getProcedures()` lists the results; `CallableStatement` is not supported |
| `CREATE SEARCH INDEX` / `CREATE VECTOR INDEX` | ✅ | Executed as DDL. BigQuery has no B-tree indexes, and `getIndexInfo()` always returns empty |
| `CREATE TRIGGER` | ❌ | Not supported in BigQuery |

The driver passes SQL through unchanged (`nativeSQL()` is the identity function), so any
statement BigQuery accepts can be executed.

### Query limits

Set by BigQuery, not the driver. See
[BigQuery quotas and limits](https://cloud.google.com/bigquery/quotas) for current values.

| Limit | Value |
|-------|-------|
| Query text size | 1 MB |
| Default query timeout | 5 minutes (the `timeout` property) |
| Query parameters per query | 10,000 — the driver chunks batches to stay under this |

---

## Tooling & ecosystem

### Connection pools

| Pool | Support | Notes |
|------|:------:|-------|
| HikariCP | ✅ | Recommended |
| Apache DBCP, Tomcat JDBC, C3P0 | ✅ | Work well |

Use `isValid(timeout)` for validation, modest pool sizes (10–20), and generous timeouts:

```java
HikariConfig config = new HikariConfig();
config.setJdbcUrl("jdbc:bigquery:my-project/my_dataset?authType=ADC");
config.setMaximumPoolSize(10);
config.setConnectionTimeout(30000);
config.setMaxLifetime(1800000);
HikariDataSource ds = new HikariDataSource(config);
```

### BI & database tools

| Tool | Support | Notes |
|------|:------:|-------|
| DBeaver | ✅ | Full support |
| IntelliJ IDEA | ✅ | Primary use case — see [IntelliJ guide](INTELLIJ.md) |
| DbVisualizer, SQuirreL SQL, SQL Workbench/J | ✅ | Work well |
| Tableau, Power BI, Looker | ⚠️ | Prefer their native BigQuery connectors |

This driver is best for Java applications, ETL tools, custom apps, and developer tools (DBeaver, IntelliJ). For BI platforms with a native BigQuery connector, prefer that.

### Migrating from the Simba driver

The driver accepts Simba-format URLs
(`jdbc:bigquery://Host:Port;ProjectId=...;OAuthType=...`) alongside its own format, so
most existing connection strings work after swapping the JAR and driver class. Thirteen
Simba property names are translated; anything else is passed through and ignored, and
`OAuthType=2` is rejected. See
[Simba BigQuery Driver Format](CONNECTION_PROPERTIES.md#simba-bigquery-driver-format) for
the full mapping.

### Frameworks & ORMs

| Framework | Support | Notes |
|-----------|:------:|-------|
| Spring JDBC (`JdbcTemplate`) | ✅ | Recommended for writes |
| MyBatis | ✅ | Full support |
| Spring Data JDBC, jOOQ | ✅ | Basic features / code generation work |
| Hibernate, JPA | ⚠️ | Read-only recommended — no `@Id` generation, `@Version`, or cascading |

---

## IntelliJ IDEA

IntelliJ is the primary use case and is fully supported — fast schema introspection, complete metadata, safe STRUCT/ARRAY handling, and automatic token refresh.

- **Setup, configuration, and performance tuning:** [IntelliJ IDEA Integration Guide](INTELLIJ.md)
- **Why this driver beats JetBrains' built-in BigQuery driver:** [Why tbc-bq-jdbc](JETBRAINS_ISSUES.md)

---

## Performance expectations

Every statement runs as a BigQuery job, so each one pays the service's job-creation
latency before any data moves — even `SELECT 1`. That floor dominates small queries and
makes the driver a poor fit for high query-per-second workloads. Cache results in your
application, use a connection pool, and prefer fewer larger queries to many small ones.

For large result sets, enable the Storage Read API — see
[Performance Tuning](CONNECTION_PROPERTIES.md#performance-tuning).

---

## Requirements

Requires **Java 21+** (for records, sealed classes, pattern matching, and virtual threads); Java 17–20 and earlier are not supported.

All major BigQuery capabilities are supported: Standard SQL (GoogleSQL) and Legacy SQL (`useLegacySql=true`), scripting (with sessions), parameterized queries, user-defined functions, authorized views, row- and column-level security, and BigQuery ML. BigQuery GIS values are returned as WKT strings.

---

## See Also

- [Quick Start](QUICKSTART.md) — get started quickly
- [Connection Properties](CONNECTION_PROPERTIES.md) — configuration and performance options
- [Type Mapping](TYPE_MAPPING.md) — data type conversions

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
| `ResultSetMetaData` | ✅ | Column names, types, counts |
| `DatabaseMetaData` | ⚠️ | See [DatabaseMetaData](#databasemetadata) |
| `SQLException` hierarchy | ✅ | With SQLState codes |
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
| Multi-statement SQL | ✅ | Runs as a single job; a session is needed only for temp entities or transactions spanning statements |
| Transactions | ⚠️ | Session-backed; no isolation levels or savepoints |
| Storage Read API | ⚠️ | Opt-in via `useStorageApi=true` (always) or `auto` (large results); scalar columns only, and needs `--add-opens=java.base/java.nio=ALL-UNNAMED`. Falls back to the standard path when unavailable |
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
| `setFetchSize()` | ⚠️ | Accepted and ignored; `getFetchSize()` returns 0. Use the `pageSize` connection property |
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

### Advanced types

| Feature | Support | Notes |
|---------|:------:|-------|
| `Array` | ✅ | `getObject()` returns a JSON string by default, or a `java.sql.Array` with `nativeComplexTypes=true`. `getArray()`, `setArray()` and `Connection.createArrayOf()` always work |
| `Struct` | ⚠️ | `getObject()` returns a JSON string by default, or a `java.sql.Struct` with `nativeComplexTypes=true`. `Connection.createStruct()` and passing a `Struct` to `setObject()` are not supported |
| `Blob`, `Clob`, `NClob` | ❌ | Use `byte[]` and `String` |
| `SQLXML` | ❌ | Use `String` with JSON |
| `Ref`, `RowId`, custom type maps, Sharding API | ❌ | Not applicable to BigQuery |

---

## DatabaseMetaData

Metadata is cached, and table and column lookups are loaded in parallel (see
[IntelliJ performance](INTELLIJ.md#performance-tuning)).

With `metadataLazyLoad=true`, `getTables()` and `getColumns()` return an **empty** result
when called with no schema or table pattern. This keeps IDE tree expansion fast on large
projects, but a tool that enumerates everything up front will see nothing.

| Method | Support | Notes |
|--------|:------:|-------|
| `getCatalogs()` | ✅ | One row: the connection's project. Cached |
| `getSchemas()` | ✅ | Datasets, with pattern filtering. Cached |
| `getTables()` | ✅ | Tables, views, materialized views. Loaded in parallel, cached |
| `getColumns()` | ✅ | 24-column metadata with accurate precision/scale. Loaded in parallel, cached |
| `getTableTypes()` | ✅ | TABLE, VIEW, MATERIALIZED VIEW |
| `getProcedures()` / `getProcedureColumns()` | ✅ | From `INFORMATION_SCHEMA`, cached. Routine bodies are not returned |
| `getTypeInfo()` | ✅ | BigQuery type information |
| Product info, JDBC version, SQL keyword and function lists | ✅ | JDBC version reports 4.3. `getDatabaseProductName()` is `BigQuery (TBC Driver)` and `getDatabaseProductVersion()` is `2.0` |
| `getFunctions()`, `getFunctionColumns()`, `getPseudoColumns()` | ❌ | Throw `SQLFeatureNotSupportedException` |
| `getPrimaryKeys()` | ✅ | Declared `PRIMARY KEY ... NOT ENFORCED` constraints, cached — [see below](#unenforced-primary-and-foreign-keys) |
| `getImportedKeys()`, `getExportedKeys()`, `getCrossReference()` | ✅ | Declared `FOREIGN KEY ... NOT ENFORCED` constraints, cached — [see below](#unenforced-primary-and-foreign-keys) |
| `getIndexInfo()` | ⚠️ | BigQuery has no indexes; returns empty |
| `getColumnPrivileges()`, `getTablePrivileges()` | ⚠️ | BigQuery uses IAM; returns empty |
| `getBestRowIdentifier()` | ⚠️ | BigQuery enforces no uniqueness, so no column set can be promised to identify a row; returns empty |
| `getUDTs()`, `getSuperTypes()`, `getSuperTables()`, `getAttributes()` | ⚠️ | BigQuery has no user-defined types; returns empty |
| `getClientInfoProperties()` | ⚠️ | The driver accepts no client info properties; returns empty |

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

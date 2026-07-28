# Compatibility

What works, what doesn't, and how to work around BigQuery's constraints.

**Specification:** JDBC 4.3 (Java 21+) · **Compliance:** partial — `Driver.jdbcCompliant()` returns `false` because BigQuery enforces no keys, has no indexes, no savepoints or configurable isolation levels, no updatable result sets, and no stored procedures.

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
| Type conversions | ✅ | All standard JDBC types |
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
statements — the equivalent of PostgreSQL's `reWriteBatchedInserts` and the only DML
shape that performs well on BigQuery. Large batches are automatically chunked to stay
under BigQuery's per-query limits (10,000 query parameters, ~1 MB query text), one
query job per chunk.

Details:

- Statements that cannot be collapsed (UPDATE/DELETE/MERGE, `INSERT ... SELECT`,
  tuples mixing literals with placeholders) execute sequentially, one query job per
  parameter set — correct, but subject to BigQuery DML quotas and job latency.
- `Statement.addBatch(String)` (heterogeneous SQL batches) also executes sequentially.
- Update counts come from BigQuery DML statistics; entries report the affected-row
  count when available, otherwise `Statement.SUCCESS_NO_INFO`.
- Failures throw `BatchUpdateException` with update counts for the work completed
  before the failure.
- `addBatch()` snapshots the current parameter set and clears the working parameters,
  so set every parameter for each row.

### BigQuery-specific

| Feature | Support | Notes |
|---------|:------:|-------|
| Sessions | ✅ | `enableSessions=true`, or started on demand by `setAutoCommit(false)` |
| Temp tables | ✅ | Requires sessions |
| Multi-statement SQL | ✅ | Requires sessions |
| Transactions | ⚠️ | Session-backed; no isolation levels or savepoints |
| Storage Read API | ❌ | Not implemented; `useStorageApi` is accepted but ignored (defaults to `false`) |
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
conn.commit();             // COMMIT, then begins the next transaction
```

The next statement after a `commit()`/`rollback()` opens a new transaction, so periodic-commit
loops work as they do on other drivers, and toggling auto-commit without running anything costs
no query jobs. Calling them while auto-commit is enabled throws `SQLException`
(SQLState `25000`). Closing a connection with uncommitted work rolls it back. Set
`enableSessions=true` to create the session eagerly at connection open.

**Isolation.** BigQuery transactions provide snapshot isolation: every read in the transaction
sees a consistent snapshot of the tables it references. JDBC has no snapshot constant, so
`DatabaseMetaData.getDefaultTransactionIsolation()` and `Connection.getTransactionIsolation()`
report the closest standard level, `TRANSACTION_REPEATABLE_READ`.
`setTransactionIsolation()` accepts `TRANSACTION_REPEATABLE_READ` and `TRANSACTION_NONE`
(recorded, but the engine's behavior never changes) and rejects the others.
`DatabaseMetaData.supportsTransactions()` returns `true`; `supportsSavepoints()` returns `false`.

---

## Unsupported JDBC features

### ResultSet

| Feature | Support | Workaround |
|---------|:------:|------------|
| Scrollable result sets (`TYPE_SCROLL_*`) | ❌ | Cache rows in the application |
| Updatable result sets (`CONCUR_UPDATABLE`) | ❌ | Use DML statements |
| `updateRow()`, `deleteRow()`, `insertRow()` | ❌ | Use DML / INSERT statements |
| `beforeFirst()`, `absolute()`, `relative()` | ❌ | Forward-only iteration |

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
| `Array` | ✅ | Native `java.sql.Array` with `nativeComplexTypes=true`; JSON string by default |
| `Struct` | ✅ | Native `java.sql.Struct` with `nativeComplexTypes=true`; JSON string by default |
| `Blob`, `Clob`, `NClob` | ❌ | Use `byte[]` and `String` |
| `SQLXML` | ❌ | Use `String` with JSON |
| `Ref`, `RowId`, custom type maps, Sharding API | ❌ | Not applicable to BigQuery |

---

## DatabaseMetaData

Metadata is cached and loaded in parallel (see [IntelliJ performance](INTELLIJ.md#performance-tuning)).

| Method | Support | Notes |
|--------|:------:|-------|
| `getCatalogs()` | ✅ | Projects, cached |
| `getSchemas()` | ✅ | Datasets, pattern filtering + parallel loading |
| `getTables()` | ✅ | Tables, views, materialized views |
| `getColumns()` | ✅ | 24-column metadata with accurate precision/scale |
| `getTableTypes()` | ✅ | TABLE, VIEW, MATERIALIZED VIEW |
| `getProcedures()` / `getProcedureColumns()` | ✅ | From `INFORMATION_SCHEMA`, cached |
| `getTypeInfo()` | ✅ | BigQuery type information |
| Product info, JDBC version, SQL keywords, functions | ✅ | JDBC version reports 4.3 |
| `getPrimaryKeys()` | ✅ | Declared `PRIMARY KEY ... NOT ENFORCED` constraints, cached — [see below](#unenforced-primary-and-foreign-keys) |
| `getImportedKeys()`, `getExportedKeys()`, `getCrossReference()` | ✅ | Declared `FOREIGN KEY ... NOT ENFORCED` constraints, cached — [see below](#unenforced-primary-and-foreign-keys) |
| `getIndexInfo()` | ⚠️ | BigQuery has no indexes; returns empty |
| `getColumnPrivileges()`, `getTablePrivileges()` | ⚠️ | BigQuery uses IAM; returns empty |
| `getBestRowIdentifier()` | ⚠️ | BigQuery enforces no uniqueness, so no column set can be promised to identify a row; returns empty |
| `getUDTs()`, `getSuperTypes()`, `getSuperTables()` | ⚠️ | Not applicable; returns empty |

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
`importedKeyNoAction` and `DEFERRABILITY` as `importedKeyNotDeferrable` for the same
reason: there is no referential action to take and nothing to defer.

Things worth knowing:

- **Constraint names are table-qualified**, because that is how BigQuery stores them.
  A primary key is always `<table>.pk$` — BigQuery does not accept a name for one — and
  a named foreign key appears as `<table>.fk_customer`. Reported verbatim so the value
  joins back to `INFORMATION_SCHEMA`.
- **`schema` and `table` arguments are names, not patterns**, per the JDBC contract. An
  `_` is a literal underscore, which matters because BigQuery names are full of them.
- **`getExportedKeys()` scans every dataset in the project.** A foreign key is recorded
  only in the dataset holding the *referencing* table, so there is nowhere else to look.
  Results are cached per dataset, so the cost lands once per TTL window rather than once
  per table asked about. Foreign keys declared in *other projects* are not found.
- **Constraints are cached** with the rest of the metadata, one snapshot per dataset
  shared by all four methods. Setting `metadataCacheEnabled=false` makes each call
  re-read from BigQuery.

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
| `ALTER TABLE` | ⚠️ | Limited column operations |
| `CREATE PROCEDURE` | ⚠️ | Use routines (not via JDBC) |
| `CREATE INDEX` | ❌ | BigQuery auto-optimizes |
| `CREATE TRIGGER` | ❌ | Not supported in BigQuery |

### Query limits

| Limit | Value |
|-------|-------|
| Query text size | 1 MB |
| Query timeout | 6 hours max (default 5 minutes) |
| Concurrent queries | 100 per project (can be raised) |
| DML rows per statement | 10,000 (higher with partitioning) |

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

Every query incurs BigQuery's job-creation latency: roughly 200–500 ms for trivial queries, 500 ms–2 s for small ones, and longer for large scans. Cached and repeated queries are much faster. The driver is not optimized for high query-per-second workloads — cache results and use connection pooling.

---

## Requirements

Requires **Java 21+** (for records, sealed classes, pattern matching, and virtual threads); Java 17–20 and earlier are not supported.

All major BigQuery capabilities are supported: Standard SQL (GoogleSQL) and Legacy SQL (`useLegacySql=true`), scripting (with sessions), parameterized queries, user-defined functions, authorized views, row- and column-level security, and BigQuery ML. BigQuery GIS values are returned as WKT strings.

---

## See Also

- [Quick Start](QUICKSTART.md) — get started quickly
- [Connection Properties](CONNECTION_PROPERTIES.md) — configuration and performance options
- [Type Mapping](TYPE_MAPPING.md) — data type conversions

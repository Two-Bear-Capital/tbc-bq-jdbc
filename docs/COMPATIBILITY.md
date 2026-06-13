# Compatibility

What works, what doesn't, and how to work around BigQuery's constraints.

**Specification:** JDBC 4.3 (Java 21+) · **Compliance:** partial — `Driver.jdbcCompliant()` returns `false` because BigQuery has no primary/foreign keys, no indexes, session-only transactions, no updatable result sets, and no stored procedures.

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
| `ResultSet` iteration | ✅ | Forward-only (`TYPE_FORWARD_ONLY`) |
| `ResultSetMetaData` | ✅ | Column names, types, counts |
| `DatabaseMetaData` | ⚠️ | See [DatabaseMetaData](#databasemetadata) |
| `SQLException` hierarchy | ✅ | With SQLState codes |
| Type conversions | ✅ | All standard JDBC types |
| `NULL` handling | ✅ | `wasNull()` |
| `beginRequest()` / `endRequest()` (4.3) | ✅ | Connection-pooling hints |
| `enquoteLiteral()`, `enquoteIdentifier()`, `isSimpleIdentifier()` (4.3) | ✅ | SQL/identifier quoting & validation |

### BigQuery-specific

| Feature | Support | Notes |
|---------|:------:|-------|
| Sessions | ✅ | `enableSessions=true` |
| Temp tables | ✅ | Requires sessions |
| Multi-statement SQL | ✅ | Requires sessions |
| Transactions | ⚠️ | Requires sessions |
| Storage Read API | ⚠️ | Detection works; Arrow deserialization in progress |
| Query labels | ✅ | Job labels for tracking |
| Location routing | ✅ | Multi-region support |
| Query timeout | ✅ | Hard timeout enforcement |
| Query cancellation | ✅ | `Statement.cancel()` |

---

## Unsupported JDBC features

### Transactions (without sessions)

| Feature | Support | Workaround |
|---------|:------:|------------|
| `setAutoCommit(false)`, `commit()`, `rollback()` | ❌ | Enable sessions |
| `Savepoint` | ❌ | Not supported even with sessions |
| Transaction isolation levels | ❌ | Always `TRANSACTION_NONE` |

BigQuery is not a transactional database; transactions work only within a session:

```java
String url = "jdbc:bigquery:my-project/my_dataset?enableSessions=true";
Connection conn = DriverManager.getConnection(url);
conn.setAutoCommit(false); // now works
// ... execute statements ...
conn.commit();
```

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
| Batch updates (`addBatch()`, `executeBatch()`) | ❌ | Use multi-row DML |
| Generated keys (`getGeneratedKeys()`) | ❌ | Query the table after INSERT |
| Named cursors | ❌ | Forward-only iteration |

For batch inserts, use multi-row DML:

```sql
INSERT INTO table (id, name)
VALUES (1, 'Alice'), (2, 'Bob'), (3, 'Charlie')
```

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
| `getPrimaryKeys()`, `getBestRowIdentifier()` | ⚠️ | BigQuery has no primary keys; returns empty |
| `getIndexInfo()` | ⚠️ | BigQuery has no indexes; returns empty |
| `getColumnPrivileges()`, `getTablePrivileges()` | ⚠️ | BigQuery uses IAM; returns empty |
| `getCrossReference()`, `getUDTs()`, `getSuperTypes()`, `getSuperTables()` | ⚠️ | Not applicable; returns empty |
| `getForeignKeys()`, `getImportedKeys()`, `getExportedKeys()` | ❌ | BigQuery has no foreign keys; returns empty |

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

Every query incurs BigQuery's job-creation latency: roughly 200–500 ms for trivial queries, 500 ms–2 s for small ones, and longer for large scans. Cached and repeated queries are much faster, and ResultSet iteration reaches 100K+ rows/s with the Storage API. The driver is not optimized for high query-per-second workloads — cache results and use connection pooling.

---

## Requirements

Requires **Java 21+** (for records, sealed classes, pattern matching, and virtual threads); Java 17–20 and earlier are not supported.

All major BigQuery capabilities are supported: Standard SQL (GoogleSQL) and Legacy SQL (`useLegacySql=true`), scripting (with sessions), parameterized queries, user-defined functions, authorized views, row- and column-level security, and BigQuery ML. BigQuery GIS values are returned as WKT strings.

---

## See Also

- [Quick Start](QUICKSTART.md) — get started quickly
- [Connection Properties](CONNECTION_PROPERTIES.md) — configuration and performance options
- [Type Mapping](TYPE_MAPPING.md) — data type conversions

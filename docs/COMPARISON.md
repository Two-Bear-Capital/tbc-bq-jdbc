# Comparison with Google's BigQuery JDBC driver

Google published its own first-party BigQuery JDBC driver,
[`com.google.cloud:google-cloud-bigquery-jdbc`](https://github.com/googleapis/google-cloud-java/tree/main/java-bigquery-jdbc),
reaching 1.0.0 on 4 June 2026. This page compares it with `tbc-bq-jdbc` so you can pick the
right driver for your situation. It is a snapshot, not a running scoreboard.

**Compared:** `tbc-bq-jdbc` 4.2.0 · `google-cloud-bigquery-jdbc` 1.1.0 · Google's driver read
at **1.1.0 on 29 July 2026** and not re-read since

Google's driver is under heavy active development and this comparison will date quickly.
Both drivers are Apache 2.0.

> Not covered here: Google's older **Simba**-built driver, distributed separately as a ZIP from
> the [Cloud console](https://cloud.google.com/bigquery/docs/reference/odbc-jdbc-drivers). It is a
> different, closed-source product. Both drivers on this page accept Simba-style connection URLs
> to ease migration from it.

---

## Pick a driver

**Choose Google's driver if** you need Java 8–17, HTTP proxy support, a custom TLS truststore, the
optional JDBC pooling API (`ConnectionPoolDataSource`), `CallableStatement`, OpenTelemetry
tracing, or a first-party support relationship.

**Choose `tbc-bq-jdbc` if** you are on Java 21+ and want metadata caching for interactive SQL
tools, one BigQuery session per connection, cheap auto-commit toggling under a connection pool,
query cost estimation, or the batch-insert and metadata-shaping controls listed below.

---

## At a glance

| | `tbc-bq-jdbc` 4.2.0 | `google-cloud-bigquery-jdbc` 1.1.0 |
|---|---|---|
| Java baseline | 21+ | 8+ |
| JDBC spec | 4.3 | 4.2 |
| Maven Central | ❌ not published | ✅ `com.google.cloud:google-cloud-bigquery-jdbc` |
| Support | Community | Google, via the `google-cloud-java` issue tracker |
| Connection properties | 37 | 73 |
| Simba-style URLs | ⚠️ common subset translated | ✅ broad |
| Storage Read API (Arrow) | ✅ `useStorageApi` | ✅ `EnableHighThroughputAPI` |
| Storage Write API | ❌ (uses load jobs) | ✅ `EnableWriteAPI` |
| Metadata caching | ✅ TTL cache, shared across connections | ❌ none |
| Sessions | One per connection, reused | New session per statement in auto-commit |
| OpenTelemetry | ❌ (own counters instead) | ✅ traces + GCP exporters |
| HTTP proxy | ❌ | ✅ |
| `CallableStatement` | ❌ | ✅ |
| `javax.sql.DataSource` | ✅ | ✅ |
| `ConnectionPoolDataSource` | ❌ (pool with HikariCP) | ✅ |

---

## Where Google's driver is ahead

### Reach and packaging

| Area | Detail |
|---|---|
| **Java 8** | Runs on Java 8 through current. `tbc-bq-jdbc` requires Java 21 — it compiles at `release 21` and uses records and sealed interfaces. This is the single biggest practical gap for older estates and for embedding in tools on legacy JVMs. |
| **Maven Central** | Published, with thin, fat, and shaded jars, and a documented `Makefile`/Docker build. `tbc-bq-jdbc` is not on Maven Central. |
| **First-party support** | Filed issues reach the BigQuery drivers team, who also coordinate with the BigQuery backend and Simba teams. |

### Connectivity and enterprise networking

| Area | Detail |
|---|---|
| **HTTP proxy** | `ProxyHost`, `ProxyPort`, `ProxyUid`, `ProxyPwd`, propagated to the auth library as well as the API client, and exercised by a dockerised proxy integration suite. `tbc-bq-jdbc` has no proxy support at all. |
| **Custom TLS truststore** | `SSLTrustStore`, `SSLTrustStorePwd`, `SSLTrustStoreType`, `SSLTrustStoreProvider`, and it honours the standard JVM truststore properties. `tbc-bq-jdbc` has none of these. |
| **Private Service Connect** | `PrivateServiceConnectUris`, `EndpointOverrides`, `universeDomain`. `tbc-bq-jdbc` offers only a single `host`/`port` endpoint override. |

### JDBC surface

| Area | Detail |
|---|---|
| **`ConnectionPoolDataSource`** | Google implements the optional JDBC pooling API — `ConnectionPoolDataSource` and `PooledConnection`, with `ConnectionPoolSize` and `ListenerPoolSize`. `tbc-bq-jdbc` ships a [`DataSource`](DATASOURCE.md) but no pooling API, and relies on an external pool such as HikariCP. |
| **`CallableStatement`** | Implemented, for calling BigQuery stored procedures. `tbc-bq-jdbc` throws `SQLFeatureNotSupportedException`. |
| **Pre-generated access tokens** | `OAuthType=2` is supported. `tbc-bq-jdbc` rejects it, and supports JSON service-account keys only — Google also accepts P12 (`OAuthP12Password`). |

### Performance features we lack

| Area | Detail |
|---|---|
| **Short query optimization** | `JobCreationMode` sets `JOB_CREATION_OPTIONAL` for *every* query, letting BigQuery answer short ones without creating a job at all. `tbc-bq-jdbc` applies this to its own metadata reads (`metadataJobCreationOptional`, on by default) but not to statements you execute, which always create a job. |
| **Storage Write API inserts** | Bulk inserts can stream through `JsonStreamWriter` (`EnableWriteAPI`, `SWA_ActivationRowCount`, `SWA_AppendRowCount`), which avoids both DML statement quotas and load-job quotas. `tbc-bq-jdbc`'s largest-batch path is a load job. |
| **Adaptive Arrow activation** | Read API use is decided per result from `HighThroughputMinTableSize` and `HighThroughputActivationRatio`. `tbc-bq-jdbc`'s `auto` mode also declines a result that arrived complete in one page, but sizes what remains with a flat 10 MB estimate (`rows × 1 KB`) rather than configurable thresholds. |
| **Streaming metadata** | Metadata fetches are submitted to a connection-scoped executor and rows are consumed as they arrive, so large schemas start returning sooner. `MetaDataFetchThreadCount` is configurable; `tbc-bq-jdbc` hard-codes a cap of 16. |

### Observability and operational controls

| Area | Detail |
|---|---|
| **OpenTelemetry** | Traces and spans, GCP trace and log exporters, `useGlobalOpenTelemetry`, and trace/span IDs injected into local logs. `tbc-bq-jdbc` exposes JVM-global counters through `DriverMetrics` and no tracing. |
| **Per-connection log files** | `LogLevel`/`LogPath` with a per-connection file handler and MDC, matching Simba's diagnostic workflow. |
| **Job controls** | `KMSKeyName`, `RequestReason`, `PartnerToken`, `AllowLargeResults` with `LargeResultDataset`/`LargeResultTable`, destination table and dataset control, `RetryInitialDelay`/`RetryMaxDelay`, and `QueryDialect` for legacy SQL. `tbc-bq-jdbc` covers `labels`, `maxBillingBytes`, `retryCount` and `useLegacySql` only. |
| **Project discovery** | `EnableProjectDiscovery` finds projects automatically. `tbc-bq-jdbc` requires them to be listed in `additionalProjects`. |

---

## Where `tbc-bq-jdbc` is ahead

### Metadata for interactive tools

| Area | Detail |
|---|---|
| **Metadata caching** | Google's driver has none: every `getTables`/`getColumns` call re-queries the API. `tbc-bq-jdbc` has a TTL cache (`metadataCacheEnabled`, `metadataCacheTtl`, `metadataLazyLoad`) shared statically across all connections to a project and keyed by `projectId:ttlSeconds`. It is deliberately *not* cleared on connection close, so it survives the open/close cycling that IntelliJ IDEA and DBeaver do constantly. This is the largest difference for interactive SQL tools against a wide schema. |
| **Sharded table collapsing** | `collapseShardedTables` reports `events_20260101`, `events_20260102`, … as one `events_*` entry. No equivalent. |
| **`STRUCT` field expansion** | `includeStructFields` adds a `getColumns()` row per `STRUCT` field, named by dotted path. No equivalent. |
| **`INFORMATION_SCHEMA` browsing** | `includeInformationSchema` exposes `INFORMATION_SCHEMA` as a browsable schema per project. No equivalent. |
| **Table descriptions** | `metadataIncludeDescriptions` fills `REMARKS` from table descriptions. |

### Sessions and transactions

This is where the two designs differ most, and where Google's issue tracker shows users hitting
the difference.

| Area | Detail |
|---|---|
| **Session reuse** | With `EnableSession=1` and auto-commit on, Google's driver sets `createSession=true` on *every* query job and never reads the assigned ID back, so each statement gets a **new** session and temp tables do not survive between statements. This was reported as [#13787](https://github.com/googleapis/google-cloud-java/issues/13787) and closed as intended, with a manual `QueryProperties=session_id=…` workaround. `tbc-bq-jdbc` reads the ID from `JobStatistics.getSessionInfo()` once and attaches it as the `session_id` connection property on every later job, so one session serves the whole connection. |
| **Deferred `BEGIN TRANSACTION`** | Google's `setAutoCommit(false)` issues a real `BEGIN TRANSACTION` job immediately, and `commit()` issues `COMMIT` *and* a fresh `BEGIN` — so a commit costs two jobs, and a connection pool that toggles auto-commit on checkout pays a job every time. `tbc-bq-jdbc` defers `BEGIN` to the first statement that actually runs (`beginTransactionIfNeeded()`), so toggling auto-commit costs nothing and `commit()` with nothing in flight is a no-op. |
| **User-managed transactions** | Because Google's driver opens a transaction eagerly, a script that manages its own transaction fails on its own `BEGIN TRANSACTION` — reported as [#13788](https://github.com/googleapis/google-cloud-java/issues/13788) and closed as intended. `tbc-bq-jdbc` issues nothing until a statement runs, so in auto-commit mode such a script executes as written. |
| **Session cleanup** | `tbc-bq-jdbc` terminates the session with `CALL BQ.ABORT_SESSION()` on connection close, best-effort. Google's driver leaves sessions to BigQuery's 24-hour reaper; wanting to reclaim session temp-table storage sooner is what drives open issue [#13922](https://github.com/googleapis/google-cloud-java/issues/13922). |
| **Spec-compliant errors** | Google's `commit()`, `rollback()` and `setAutoCommit()` throw unchecked `IllegalStateException` when there is no transaction or no session, and `Statement.addBatch()` throws `IllegalArgumentException` for non-DML. `tbc-bq-jdbc` throws `SQLException` with SQLState `25000`, which is what pools and frameworks catch. |

### Result-path correctness

| Area | Detail |
|---|---|
| **REST/Arrow parity is test-enforced** | `tbc-bq-jdbc` re-encodes Arrow rows into `FieldValueList` so both paths share one set of getters and coercions, and `StorageApiParityTest` compares the two paths cell by cell for byte equality with no exemptions. Google's driver has separate coercion registries for its JSON and Read API paths, and reconciling their temporal semantics is still in progress — open PR [#13868](https://github.com/googleapis/google-cloud-java/pull/13868) (23 July 2026) reworks `DATETIME`/`TIME`/`TIMESTAMP` conversions on both paths, including changing `LocalDateTime → Timestamp` from UTC-based to local-based. |

### Batching

| Area | Detail |
|---|---|
| **Multi-row INSERT rewriting** | `tbc-bq-jdbc` collapses a batch of simple parameterized INSERTs into multi-row `INSERT … VALUES (…), (…)` statements — the equivalent of PostgreSQL's `reWriteBatchedInserts` — chunked under BigQuery's 10,000-parameter and ~1 MB query limits. Google's driver instead repeats the statement text once per parameter set in a single script job, and submits it at `BATCH` priority, which can queue. |
| **Load-job path** | `batchLoadThreshold` sends large batches through one load job, streamed as NDJSON into a `TableDataWriteChannel` with no GCS staging. Update counts are 1 per row only when the load's aggregate output matches the batch, otherwise `SUCCESS_NO_INFO` — never fabricated from the batch size. Google's Write API path fills the update-count array with 1s from the reported row count. |

### Cost and complex types

| Area | Detail |
|---|---|
| **Query cost estimation** | `enableQueryCostEstimation` runs a dry run before each query and attaches the estimate as a `SQLWarning`; `queryPricePerTiB` converts bytes to money in your own currency. Google's driver has no equivalent, and removes dry runs where it can. |
| **`ARRAY`/`STRUCT` presentation toggle** | Both are JSON strings by default, which keeps IntelliJ stable, and `nativeComplexTypes=true` switches to real JDBC `Array`/`Struct`. Both are writable, and `createStruct` accepts the same `STRUCT<id INT64, name STRING>` type name that `getObject()` reports, so a struct that was read can be bound straight back. |

---

## At parity

Neither driver has a meaningful edge here.

| Area | Detail |
|---|---|
| **Type coverage** | Both cover every BigQuery type, `RANGE`, `INTERVAL`, `JSON`, `GEOGRAPHY` and `BIGNUMERIC` included, in both the REST and Arrow paths, and both map `RANGE`/`GEOGRAPHY`/`INTERVAL` to `Types.OTHER` rendered as strings. |
| **Multi-statement scripts** | Both run a script as one parent job, enumerate its child jobs, order them oldest-first, and produce a `ResultSet` for a child only when its statement type is `SELECT`. Both walk the children through `getMoreResults()`. |
| **Storage Read API** | Both read Arrow over the Storage Read API and fall back to the REST path when the read session cannot be created or permission is denied. |
| **Key metadata** | Both report primary and foreign keys from BigQuery's table constraints. |
| **Authentication** | Both cover ADC, service-account keys, user OAuth refresh tokens, service-account impersonation, and workload/workforce identity federation. Google additionally supports P12 keys and pre-generated access tokens. |
| **Parallel metadata fan-out** | Both fan out per-dataset metadata queries across a bounded thread pool. `tbc-bq-jdbc` uses `INFORMATION_SCHEMA` queries; Google uses the REST list APIs. |
| **Cancellation and timeouts** | Both implement `Statement.cancel()` and query timeouts against the underlying job. |
| **Metadata completeness** | Both implement the full `DatabaseMetaData` surface including `getProcedures`, `getProcedureColumns`, `getFunctions` and `getFunctionColumns`. |
| **Unsupported by BigQuery** | Neither offers scrollable or updatable result sets, savepoints, or configurable transaction isolation — BigQuery does not support them. |

---

## Notes on reading this page

Every claim above was checked against the source of both drivers at the versions named, not
against either project's documentation. Google's driver moves fast — 60+ merged pull requests
between May and July 2026 — so re-verify before relying on any gap listed here.

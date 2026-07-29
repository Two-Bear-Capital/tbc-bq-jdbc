# Connection Properties

Complete reference for all JDBC connection URL properties.

## URL Formats

### Traditional Format

```
jdbc:bigquery:[project]/[dataset]?property1=value1&property2=value2
```

### Simba BigQuery Driver Format

tbc-bq-jdbc supports the Simba BigQuery JDBC driver URL format for seamless migration:

```
jdbc:bigquery://[Host]:[Port];ProjectId=[Project];OAuthType=[AuthValue];[Property1]=[Value1];...
```

**Example:**
```
jdbc:bigquery://https://www.googleapis.com/bigquery/v2:443;ProjectId=my-project;DefaultDataset=my_dataset;OAuthType=3
```

**Key Differences from Traditional Format:**

| Aspect | Traditional | Simba |
|--------|------------|-------|
| URL prefix | `jdbc:bigquery:` | `jdbc:bigquery://` |
| Project specification | Path segment | `ProjectId=` parameter |
| Dataset specification | Path segment | `DefaultDataset=` parameter |
| Parameter separator | `&` | `;` |
| Parameter prefix | `?` | None |

**Simba Property Mapping:**

All Simba properties are automatically mapped to tbc-bq-jdbc equivalents:

| Simba Property | tbc-bq-jdbc Property | Description |
|----------------|---------------------|-------------|
| `ProjectId` | `projectId` | Google Cloud project ID (required) |
| `DefaultDataset` | `datasetId` | Default dataset name |
| `OAuthType` | `authType` | Authentication type (see below) |
| `OAuthPvtKeyPath` | `credentials` | Service account key file path |
| `OAuthClientId` | `clientId` | OAuth 2.0 client ID |
| `OAuthClientSecret` | `clientSecret` | OAuth 2.0 client secret |
| `OAuthRefreshToken` | `refreshToken` | OAuth 2.0 refresh token |
| `Timeout` | `timeout` | Query timeout in seconds |
| `MaxResults` | `maxResults` | Maximum rows to fetch |
| `UseLegacySQL` | `useLegacySql` | Use legacy SQL dialect |
| `Location` | `location` | BigQuery location |
| `DatasetProjectId` | `datasetProjectId` | Cross-project dataset access |
| `EnableSessions` | `enableSessions` | Create a BigQuery session at connection open |

**OAuthType Values:**

| OAuthType | Authentication Type | tbc-bq-jdbc authType | Required Properties |
|-----------|-------------------|---------------------|-------------------|
| `0` | Service Account | `SERVICE_ACCOUNT` | `OAuthPvtKeyPath` |
| `1` | User OAuth | `USER_OAUTH` | `OAuthClientId`, `OAuthClientSecret`, `OAuthRefreshToken` |
| `2` | Pre-generated Token | ❌ Not supported | - |
| `3` | Application Default | `ADC` | None (recommended) |
| `4` | External Account | `WORKLOAD` | `credentialConfigFile` (via Properties) |

**Simba Format Examples:**

```java
// Application Default Credentials (OAuthType=3)
"jdbc:bigquery://https://www.googleapis.com/bigquery/v2:443;ProjectId=my-project;OAuthType=3"

// Service Account (OAuthType=0)
"jdbc:bigquery://https://www.googleapis.com/bigquery/v2:443;ProjectId=my-project;OAuthType=0;OAuthPvtKeyPath=/path/to/key.json"

// User OAuth (OAuthType=1)
"jdbc:bigquery://https://www.googleapis.com/bigquery/v2:443;ProjectId=my-project;OAuthType=1;OAuthClientId=id;OAuthClientSecret=secret;OAuthRefreshToken=token"

// With dataset and additional properties
"jdbc:bigquery://https://www.googleapis.com/bigquery/v2:443;ProjectId=my-project;DefaultDataset=my_dataset;OAuthType=3;Timeout=120;Location=EU"
```

**Migration from Simba:**

Replace the Simba JDBC driver with tbc-bq-jdbc and set the driver class to
`vc.tbc.bq.jdbc.BQDriver`. The thirteen Simba property names in the table above are
translated automatically, so most connection strings work unchanged. Two things to check:

- `OAuthType=2` (pre-generated access tokens) is rejected with an error. Use `0` or `3`.
- Any other property name is passed through untranslated. Native tbc-bq-jdbc property
  names therefore work in a Simba-format URL, but Simba-only options the driver has no
  equivalent for (`OAuthPvtKey`, `AllowLargeResults`, `LogLevel`, `ProxyHost`, …) are
  accepted and ignored rather than rejected.

**Host and port.** By default the driver talks to Google's BigQuery endpoints, and the
`https://www.googleapis.com/bigquery/v2:443` authority in a typical Simba URL changes
nothing. A *different* host is honoured: the driver directs the client at it, over
**HTTPS** unless you write an explicit `http://` scheme. Use `port` to set a non-default
port. A plaintext endpoint is logged as a warning, since credentials travel over it.

## Required Components

| Component | Description | Example |
|-----------|-------------|---------|
| `project` | Google Cloud project ID | `my-project` |
| `dataset` | Default dataset (optional) | `my_dataset` |

## Complete Property Reference

**→ [Full property table](generated/connection-properties.md)** — every property, with its
type, default and allowed values.

That table is generated from the driver's `Driver.getPropertyInfo()` and checked against it
on every build, so it stays in step with the code. The sections below add the usage
guidance, examples, and recommended configurations a flat table can't capture.

<!-- @include: generated/connection-properties.md -->

### Authentication Properties

> The `USER_OAUTH` properties `clientId`, `clientSecret`, and `refreshToken` are documented in the
> [Authentication Guide](AUTHENTICATION.md#user-oauth).

**Example:**
```
jdbc:bigquery:my-project/my_dataset?authType=SERVICE_ACCOUNT&credentials=/path/to/key.json
```

---

### Query Execution Properties

Covers `timeout`, `maxResults`, `useLegacySql`, `pageSize`, and `nativeComplexTypes` (see the
[generated table](generated/connection-properties.md) for defaults and allowed values).

**Example:**
```
jdbc:bigquery:my-project/my_dataset?authType=ADC&timeout=600&pageSize=5000
```

**Notes:**
- `timeout=0` means no timeout (wait indefinitely). Values are not range-checked, and a
  negative value behaves the same as `0`
- `pageSize` sets how many rows each `jobs.getQueryResults` call fetches, so it controls
  how many HTTP round trips a large result costs. The default of 50,000 balances round
  trips against per-page memory; raising it further yields little
- Lowering `pageSize` reduces peak memory per page but costs more round trips. BigQuery also
  caps a page by response bytes, so a very large `pageSize` stops adding rows per page once
  the payload hits that ceiling
- `Statement.setFetchSize(n)` overrides `pageSize` for that statement, so one connection can
  run a narrow lookup and a million-row scan at different page sizes. Set it before executing;
  `0` restores the connection's `pageSize`
- `maxResults` limits total rows returned, regardless of pagination
- `nativeComplexTypes` governs **only what `getObject()` returns** for ARRAY and STRUCT
  columns. With the default `false` it returns a JSON string, which keeps IntelliJ IDEA
  and other result grids stable; with `true` it returns a `java.sql.Array` or
  `java.sql.Struct`
- `rs.getArray()`, `PreparedStatement.setArray()` and `Connection.createArrayOf()` work
  regardless of the setting — an explicit typed call is never gated
- There is no write path for STRUCT: `Connection.createStruct()` is unsupported and
  passing a `java.sql.Struct` to `setObject()` throws. Build struct values in SQL

---

### Location and Routing

Covers `location` and `datasetProjectId` (see the
[generated table](generated/connection-properties.md) for defaults and allowed values).

**Example:**
```
jdbc:bigquery:my-project/my_dataset?authType=ADC&location=EU
```

**Notes:**
- If `location` is not set, BigQuery uses the dataset's location
- `datasetProjectId` allows querying datasets in other projects you have access to

---

### Session and Transaction Properties

Controlled by `enableSessions` (see the
[generated table](generated/connection-properties.md) for the default and allowed values).

**Example:**
```
jdbc:bigquery:my-project/my_dataset?authType=ADC&enableSessions=true
```

**When enabled:** the session is created when the connection opens, so from the first
statement you can:
- ✅ Use `CREATE TEMP TABLE`
- ✅ Use multi-statement SQL scripts
- ✅ Use `BEGIN TRANSACTION`, `COMMIT`, `ROLLBACK`
- ✅ Call `setAutoCommit(false)` on Connection

**When disabled (default):** no session exists until transaction semantics are requested.
Calling `setAutoCommit(false)` starts one on demand, so generic JDBC tooling (connection
pools, ORMs, data loaders) works without setting this property:
- ✅ `setAutoCommit(false)` starts a session; the transaction begins with the next statement
- ✅ `commit()` / `rollback()` work, each starting the next transaction
- ✅ Temp tables and multi-statement scripts work once the session exists
- ⚠️ Calling `commit()` / `rollback()` while auto-commit is enabled throws
  `SQLException` (SQLState `25000`), per the JDBC spec
- ⚠️ Sessions do not allow concurrent queries — with a session active, run statements
  on the connection sequentially

Set `enableSessions=true` when the very first statement needs the session (temp tables
or scripts before any transaction), or to make the session cost explicit at connect time.

---

### Performance Tuning

Covers `useStorageApi`, `metadataCacheEnabled`, `metadataCacheTtl`,
`metadataCacheMaxRows`, and `metadataLazyLoad` (see the
[generated table](generated/connection-properties.md) for defaults and allowed values).

**Example:**
```
jdbc:bigquery:my-project/my_dataset?authType=ADC&pageSize=50000&metadataCacheTtl=600
```

> **`connectionTimeout` and `retryCount` are accepted but not yet applied.** The driver
> parses them, but neither changes its behaviour today; retries and timeouts come from
> the Google Cloud client defaults. Use the `timeout` property for query timeouts.

**Storage API Modes:**

The BigQuery Storage Read API streams results as binary Arrow batches over gRPC
instead of paging them as JSON over HTTPS. It is dramatically faster on large result
sets — an order of magnitude on a million rows. On small ones it is *slower*, because
opening a read session costs a round trip before the first row arrives.

- `auto` - use the Storage API only for result sets estimated over 10 MB. The estimate is
  the row count times a nominal 1 KB per row, so in practice this is a threshold of about
  10,000 rows regardless of how wide they are
- `true` - always use it, even for small results where it will not pay off
- `false` (default) - always use the standard Jobs API path

**Two things to know before turning it on:**

1. **It needs a JVM flag.** Arrow cannot allocate memory without
   `--add-opens=java.base/java.nio=ALL-UNNAMED` on Java 16+. See
   [INTELLIJ.md](INTELLIJ.md) for where to put it in IntelliJ or DataGrip.
2. **It never breaks a query.** If Arrow is unusable, the query has column types
   the path does not cover (arrays, structs, `INTERVAL`), the job produced no
   destination table, or the read session cannot be opened, the driver logs once
   and falls back to the standard path. The cost of a missing flag is speed, not
   failure.

Reads of query results are billed as reads of a temporary table, which BigQuery
does not charge for — so enabling this does not add cost for ordinary queries.

**Results are byte-for-byte identical on both paths**, which
`StorageApiParityTest` verifies by running the same query down each and comparing
every cell. Switching this property on or off does not change the values your code sees.

**Metadata Caching (for IntelliJ/Database Tools):**

Improves schema introspection performance, especially for projects with many datasets.

**`metadataCacheEnabled`:**
- `true` (default) - Cache metadata queries (getCatalogs, getSchemas, getTables, getColumns)
- `false` - Always fetch fresh metadata from BigQuery API

**`metadataCacheTtl`:**
- Time in seconds to cache metadata results
- Default: `300` (5 minutes)
- Recommended: `600` (10 minutes) for large projects
- Set to `60` for frequently changing schemas

**`metadataCacheMaxRows`:**
- Ceiling on the **total rows** held across every cache entry
- Default: `50000`
- Set to `0` for no limit

The cache is shared by every connection to a project and outlives all of them, which is
what makes reopening a connection fast. Entries are removed only by expiry, so within a
TTL window each distinct metadata query holds its own entry containing every row of its
result. `metadataCacheMaxRows` bounds the total: once it is exceeded, the oldest entries
are dropped until it is not. A single entry larger than the whole ceiling is kept anyway.

Raise it for a very wide project with memory to spare; lower it when the driver runs
somewhere memory-constrained.

**`metadataLazyLoad`:**
- `true` - Defer loading until a specific schema or table is requested (best for very
  large projects). `getTables()` and `getColumns()` return an **empty** result when
  called with no schema or table pattern
- `false` (default) - Load all metadata upfront (better for immediate visibility)

**Performance impact:** on a project with many datasets, the first metadata load
dominates the cost and every subsequent one within the TTL is served from memory.
Lazy loading removes the upfront load entirely, at the cost of an empty result for
tools that enumerate everything without a pattern.

**Recommended Configurations:**

**Small Projects (< 10 datasets):**
```
jdbc:bigquery:my-project?authType=ADC
# Default settings work well
```

**Medium Projects (10-50 datasets):**
```
jdbc:bigquery:my-project?authType=ADC&metadataCacheEnabled=true
```

**Large Projects (50-200 datasets):**
```
jdbc:bigquery:my-project?authType=ADC&metadataCacheEnabled=true&metadataCacheTtl=600
```

**Very Large Projects (200+ datasets):**
```
jdbc:bigquery:my-project?authType=ADC&metadataCacheEnabled=true&metadataCacheTtl=600&metadataLazyLoad=true
```

See **[IntelliJ Integration Guide](INTELLIJ.md)** for complete setup instructions and troubleshooting.

---

### Job Configuration

Covers `labels`, `jobCreationMode`, and `maxBillingBytes` (see the
[generated table](generated/connection-properties.md) for defaults and allowed values).

**Example:**
```
jdbc:bigquery:my-project/my_dataset?authType=ADC&labels=env=prod,team=data&maxBillingBytes=1000000000
```

**Job Labels:**
Format: `key1=value1,key2=value2`
- Attached to every job the connection submits
- Used for tracking and billing
- Visible in BigQuery console and billing exports

**Max Billing Bytes:**
- `maxBillingBytes` caps how many bytes a single statement may be billed for. BigQuery
  rejects the job up front when its estimate exceeds the limit, so nothing is billed
- Applies to queries and DML alike, including batch-rewritten `INSERT`s
- Omit it for no limit; there is no value meaning "unlimited"
- For a ceiling across a whole project rather than per statement, use a
  [custom cost control](https://cloud.google.com/bigquery/docs/custom-quotas).
  To see an estimate before running, set `enableQueryCostEstimation=true`

> **`jobCreationMode` is accepted but not yet applied.** The driver parses it, but it is
> not currently sent to BigQuery.

---

## Property Examples by Use Case

### Local Development

```
jdbc:bigquery:my-project/my_dataset?authType=ADC&location=US
```

**Why:**
- ADC uses gcloud CLI credentials
- Simple, no key files needed
- Fast development iteration

---

### Production (High Performance)

```
jdbc:bigquery:my-project/my_dataset?\
  authType=SERVICE_ACCOUNT&\
  credentials=/etc/secrets/bigquery.json&\
  pageSize=50000&\
  timeout=3600&\
  location=US&\
  useStorageApi=auto&\
  labels=env=prod,service=analytics
```

**Why:**
- Service account for security
- Storage API on large result sets (needs `--add-opens=java.base/java.nio=ALL-UNNAMED`)
- Large page size for throughput
- Extended timeout for complex queries
- Labels for cost tracking

---

### Data Warehouse / ETL

```
jdbc:bigquery:my-project/staging_dataset?\
  authType=SERVICE_ACCOUNT&\
  credentials=/vault/keys/bigquery.json&\
  enableSessions=true&\
  timeout=7200&\
  maxBillingBytes=10737418240&\
  labels=pipeline=etl,stage=transform
```

**Why:**
- Sessions for temp tables and transactions
- Long timeout for complex ETL
- A 10 GB billing ceiling, so a runaway transform fails instead of scanning the warehouse
- Labels for pipeline tracking

---

### Interactive Analysis (Low Latency)

```
jdbc:bigquery:my-project/analytics?\
  authType=ADC&\
  pageSize=1000&\
  timeout=60&\
  maxResults=10000
```

**Why:**
- Small page size for quick first results
- Short timeout for fast feedback
- Result limit for preview queries

---

### Reporting / BI Tool

```
jdbc:bigquery:my-project/reporting?\
  authType=SERVICE_ACCOUNT&\
  credentials=/opt/bi-tool/bigquery-ro.json&\
  pageSize=100000&\
  timeout=600&\
  useStorageApi=auto&\
  labels=source=looker,type=dashboard
```

**Why:**
- Read-only service account
- Storage API on large result sets (needs `--add-opens=java.base/java.nio=ALL-UNNAMED`)
- Large pages for throughput
- Labels for usage tracking

---

## Property Validation

### Valid Values

The allowed values for each property (e.g. `authType`, `useStorageApi`, `jobCreationMode`,
`useLegacySql`) are listed in the **[generated property table](generated/connection-properties.md)**,
produced directly from the driver.

### What is validated

`projectId` must be present and non-blank, and `authType` must be one of the five
supported values. Each auth type's required properties are checked before the driver
contacts BigQuery — see [Authentication](AUTHENTICATION.md#common-authentication-errors)
for the exact messages.

Numeric properties are parsed but not range-checked. A `timeout` of `0` or any negative
value means "wait indefinitely"; an implausibly large value is accepted as given.

---

## Using environment variables

The driver does not expand environment variables inside a URL. Read them in your
application and build the URL yourself:

```java
String url = String.format(
    "jdbc:bigquery:my-project/my_dataset?authType=SERVICE_ACCOUNT&credentials=%s&timeout=%s",
    System.getenv("BQ_KEY_PATH"),
    System.getenv("BQ_TIMEOUT")
);
```

For Application Default Credentials, Google's client library reads
`GOOGLE_APPLICATION_CREDENTIALS` from the environment itself — no URL property needed.

---

## Properties Object API

Alternatively, use `java.util.Properties`:

```java
import java.util.Properties;

Properties props = new Properties();
props.setProperty("authType", "ADC");
props.setProperty("timeout", "600");
props.setProperty("enableSessions", "true");

String url = "jdbc:bigquery:my-project/my_dataset";
Connection conn = DriverManager.getConnection(url, props);
```

**Advantages:**
- Type-safe property setting
- Easier to build programmatically
- Cleaner for many properties

---

## Default Values Summary

Every property's default value is listed in the
**[generated property table](generated/connection-properties.md)** — produced directly from the
driver's `getPropertyInfo()`, so it never goes stale.

---

## Performance Impact

| Property | Impact on Performance | Impact on Cost |
|----------|----------------------|----------------|
| `useStorageApi` | Much faster on large result sets, slower on small ones | None |
| `pageSize` | Higher = fewer round trips, more memory per page | None |
| `timeout` | Higher allows longer queries | Indirectly (prevents partial work) |
| `maxResults` | Lower = faster completion | None — BigQuery still scans the full query |
| `maxBillingBytes` | None | Caps per-statement spend; over-limit statements fail before billing |
| `enableSessions` | One extra job at connection open | Minimal |
| `metadataCacheEnabled=true` | Repeated metadata queries served from memory | Lower (fewer API calls) |
| `metadataCacheTtl` | Higher = more cache hits, staler schema | Lower |
| `metadataLazyLoad=true` | No upfront metadata load | Lower (fewer API calls) |

---

## See Also

- [Authentication Guide](AUTHENTICATION.md) - Credential configuration
- [Quick Start](QUICKSTART.md) - Basic examples
- [Type Mapping](TYPE_MAPPING.md) - BigQuery ↔ JDBC type conversions
- [Compatibility Matrix](COMPATIBILITY.md) - JDBC features and limitations
- [IntelliJ Integration](INTELLIJ.md) - Database tool setup and optimization

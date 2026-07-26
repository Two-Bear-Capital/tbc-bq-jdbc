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
| `UseDestinationTables` | `useDestinationTables` | Write SELECT results to a temp destination table |

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

Simply replace your existing Simba JDBC driver with tbc-bq-jdbc - your connection strings will work without modification.

**Note:** The host and port in Simba URLs are validated but not used for actual connections (tbc-bq-jdbc always uses Google's BigQuery API endpoints).

## Required Components

| Component | Description | Example |
|-----------|-------------|---------|
| `project` | Google Cloud project ID | `my-project` |
| `dataset` | Default dataset (optional) | `my_dataset` |

## Complete Property Reference

The full property table is generated directly from the driver's `Driver.getPropertyInfo()` (see
[the generated reference](generated/connection-properties.md)), so it always matches what the driver
accepts. The sections after it add the usage guidance, examples, and recommended configurations that
a flat table can't capture.

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
- `timeout=0` means no timeout (wait indefinitely)
- Smaller `pageSize` reduces memory usage but may increase latency
- `maxResults` limits total rows returned, regardless of pagination
- `nativeComplexTypes=false` (default) returns ARRAY/STRUCT as JSON strings — safe for IntelliJ IDEA and tools that don't handle JDBC Array/Struct
- `nativeComplexTypes=true` enables `rs.getArray()` returning `java.sql.Array` and `rs.getObject()` returning `java.sql.Struct` for RECORD columns; also enables `PreparedStatement.setArray()` and `Connection.createArrayOf()`

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

Covers `useStorageApi`, `connectionTimeout`, `retryCount`, `metadataCacheEnabled`,
`metadataCacheTtl`, and `metadataLazyLoad` (see the
[generated table](generated/connection-properties.md) for defaults and allowed values).

**Example:**
```
jdbc:bigquery:my-project/my_dataset?authType=ADC&useStorageApi=true&retryCount=5
```

**Storage API Modes:**
- `auto` - Automatically use Storage API for result sets > 10MB
- `true` - Always use Storage API for reads
- `false` - Never use Storage API (use Jobs API only)

**Benefits of Storage API:**
- 🚀 Faster data access for large result sets
- 📊 Parallel stream reading
- 💰 Lower costs for large queries

**Metadata Caching (for IntelliJ/Database Tools):**

Dramatically improves schema introspection performance, especially for projects with many datasets.

**`metadataCacheEnabled`:**
- `true` (default) - Cache metadata queries (getCatalogs, getSchemas, getTables, getColumns)
- `false` - Always fetch fresh metadata from BigQuery API

**`metadataCacheTtl`:**
- Time in seconds to cache metadata results
- Default: `300` (5 minutes)
- Recommended: `600` (10 minutes) for large projects
- Set to `60` for frequently changing schemas

**`metadataLazyLoad`:**
- `true` - Only load metadata when user expands tree nodes (best for 200+ datasets)
- `false` (default) - Load all metadata upfront (better for immediate visibility)

**Performance Impact:**
- **Without caching** (90 datasets): ~90 seconds to load schema tree
- **With caching** (90 datasets): ~3 seconds first load, <10ms subsequent loads (900x faster)
- **With lazy loading**: Instant initial connection, loads data on-demand

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
- Used for tracking and billing
- Visible in BigQuery console
- Can be used in billing exports

**Max Billing Bytes:**
- Query fails if it would process more than this limit
- Prevents runaway query costs
- Set to `null` for no limit

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
  useStorageApi=auto&\
  pageSize=50000&\
  timeout=3600&\
  location=US&\
  labels=env=prod,service=analytics
```

**Why:**
- Service account for security
- Storage API for large queries
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
- Billing limit to prevent cost overruns
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
  useStorageApi=true&\
  pageSize=100000&\
  timeout=600&\
  labels=source=looker,type=dashboard
```

**Why:**
- Read-only service account
- Storage API for dashboard queries
- Large pages for throughput
- Labels for usage tracking

---

## Property Validation

### Valid Values

The allowed values for each property (e.g. `authType`, `useStorageApi`, `jobCreationMode`,
`useLegacySql`) are listed in the **[generated property table](generated/connection-properties.md)**,
produced directly from the driver.

### Invalid Combinations

❌ **Don't:**
```
authType=SERVICE_ACCOUNT (without credentials property)
```
**Error:** SQLException - credentials required

❌ **Don't:**
```
timeout=-1 or timeout=999999999
```
**Error:** IllegalArgumentException

---

## Environment Variable Substitution

You can reference environment variables in property values:

```java
// Set environment variable
System.setenv("BQ_KEY_PATH", "/secrets/bigquery.json");
System.setenv("BQ_TIMEOUT", "600");

// Use in URL (manual substitution)
String keyPath = System.getenv("BQ_KEY_PATH");
String timeout = System.getenv("BQ_TIMEOUT");
String url = String.format(
    "jdbc:bigquery:my-project/my_dataset?authType=SERVICE_ACCOUNT&credentials=%s&timeout=%s",
    keyPath, timeout
);
```

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
| `pageSize` | Higher = faster iteration | None |
| `useStorageApi=true` | Much faster for large results | Lower for large queries |
| `timeout` | Higher allows longer queries | Indirectly (prevents partial work) |
| `maxResults` | Lower = faster completion | Lower (less data processed) |
| `connectionTimeout` | Higher = more resilient | None |
| `enableSessions` | Slight overhead | Minimal |
| `retryCount` | Higher = more resilient | Higher (retried queries billed) |
| `metadataCacheEnabled=true` | 900x faster repeated metadata queries | None |
| `metadataCacheTtl` | Higher = more cache hits | None |
| `metadataLazyLoad=true` | Instant connection, load on-demand | Lower (fewer API calls) |

---

## See Also

- [Authentication Guide](AUTHENTICATION.md) - Credential configuration
- [Quick Start](QUICKSTART.md) - Basic examples
- [Type Mapping](TYPE_MAPPING.md) - BigQuery ↔ JDBC type conversions
- [Compatibility Matrix](COMPATIBILITY.md) - JDBC features and limitations
- [IntelliJ Integration](INTELLIJ.md) - Database tool setup and optimization

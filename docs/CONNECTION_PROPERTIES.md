# Connection Properties Reference

Complete reference for all JDBC connection URL properties.

## URL Format

```
jdbc:bigquery:[project]/[dataset]?property1=value1&property2=value2
```

## Required Components

| Component | Description | Example |
|-----------|-------------|---------|
| `project` | Google Cloud project ID | `my-project` |
| `dataset` | Default dataset (optional) | `my_dataset` |

## Complete Property Reference

### Authentication Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `authType` | String | `ADC` | Authentication method: `ADC`, `SERVICE_ACCOUNT`, `USER_OAUTH`, `WORKFORCE`, `WORKLOAD` |
| `credentials` | String | `null` | Path to credentials file (for SERVICE_ACCOUNT, WORKFORCE) |
| `clientId` | String | `null` | OAuth 2.0 client ID (for USER_OAUTH) |
| `clientSecret` | String | `null` | OAuth 2.0 client secret (for USER_OAUTH) |
| `refreshToken` | String | `null` | OAuth 2.0 refresh token (for USER_OAUTH) |

**Example:**
```
jdbc:bigquery:my-project/my_dataset?authType=SERVICE_ACCOUNT&credentials=/path/to/key.json
```

---

### Query Execution Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `timeout` | Integer | `300` | Query timeout in seconds |
| `maxResults` | Long | `null` | Maximum rows to fetch (null = unlimited) |
| `useLegacySql` | Boolean | `false` | Use legacy SQL dialect instead of standard SQL |
| `pageSize` | Integer | `10000` | Result page size for pagination |

**Example:**
```
jdbc:bigquery:my-project/my_dataset?authType=ADC&timeout=600&pageSize=5000
```

**Notes:**
- `timeout=0` means no timeout (wait indefinitely)
- Smaller `pageSize` reduces memory usage but may increase latency
- `maxResults` limits total rows returned, regardless of pagination

---

### Location and Routing

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `location` | String | `null` | BigQuery location (e.g., `US`, `EU`, `us-central1`) |
| `datasetProjectId` | String | `null` | Project ID for dataset if different from connection project |

**Example:**
```
jdbc:bigquery:my-project/my_dataset?authType=ADC&location=EU
```

**Notes:**
- If `location` is not set, BigQuery uses the dataset's location
- `datasetProjectId` allows querying datasets in other projects you have access to

---

### Session and Transaction Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enableSessions` | Boolean | `false` | Enable BigQuery sessions for temp tables and transactions |

**Example:**
```
jdbc:bigquery:my-project/my_dataset?authType=ADC&enableSessions=true
```

**When enabled:**
- ✅ Can use `CREATE TEMP TABLE`
- ✅ Can use multi-statement SQL scripts
- ✅ Can use `BEGIN TRANSACTION`, `COMMIT`, `ROLLBACK`
- ✅ Can call `setAutoCommit(false)` on Connection

**When disabled (default):**
- ❌ `setAutoCommit(false)` throws `SQLFeatureNotSupportedException`
- ❌ `commit()` and `rollback()` throw exceptions
- ❌ Temp tables not supported

---

### Performance Tuning

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `useStorageApi` | String | `auto` | Storage API mode: `auto`, `true`, `false` |
| `connectionTimeout` | Integer | `30` | Connection establishment timeout in seconds |
| `retryCount` | Integer | `3` | Retry attempts for transient errors |

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

---

### Job Configuration

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `labels` | String | `null` | Comma-separated key=value pairs for job labels |
| `jobCreationMode` | String | `REQUIRED` | Job creation mode: `REQUIRED`, `OPTIONAL` |
| `maxBillingBytes` | Long | `null` | Maximum bytes billed for queries (cost limit) |

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

**authType:**
- `ADC` (Application Default Credentials)
- `SERVICE_ACCOUNT`
- `USER_OAUTH`
- `WORKFORCE`
- `WORKLOAD`

**useStorageApi:**
- `auto` (default)
- `true`
- `false`

**jobCreationMode:**
- `REQUIRED` (default)
- `OPTIONAL`

**useLegacySql:**
- `true`
- `false` (default, recommended)

### Invalid Combinations

❌ **Don't:**
```
enableSessions=false + setAutoCommit(false)
```
**Error:** SQLFeatureNotSupportedException

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

| Property | Default | Notes |
|----------|---------|-------|
| authType | `ADC` | If not specified |
| timeout | `300` | 5 minutes |
| pageSize | `10000` | Rows per page |
| connectionTimeout | `30` | Seconds |
| retryCount | `3` | Transient error retries |
| useLegacySql | `false` | Use standard SQL |
| enableSessions | `false` | Sessions disabled |
| useStorageApi | `auto` | Auto-enable for large results |
| jobCreationMode | `REQUIRED` | Always create jobs |
| maxResults | `null` | No limit |
| maxBillingBytes | `null` | No cost limit |
| labels | `{}` | No labels |
| location | `null` | Use dataset location |

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

---

## See Also

- [Authentication Guide](AUTHENTICATION.md) - Credential configuration
- [Performance Tuning](PERFORMANCE.md) - Optimization strategies
- [Quick Start](QUICKSTART.md) - Basic examples
- [Troubleshooting](TROUBLESHOOTING.md) - Common configuration issues

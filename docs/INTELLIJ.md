# IntelliJ IDEA Integration Guide

A comprehensive guide for using **tbc-bq-jdbc** as a superior alternative to JetBrains' built-in BigQuery driver.

## Table of Contents

- [Why Use tbc-bq-jdbc with IntelliJ?](#why-use-tbc-bq-jdbc-with-intellij)
- [Quick Start](#quick-start)
- [Installation](#installation)
- [Configuration](#configuration)
- [Performance Tuning](#performance-tuning)
- [Feature Comparison](#feature-comparison)
- [Troubleshooting](#troubleshooting)
- [Best Practices](#best-practices)

---

## Why Use tbc-bq-jdbc with IntelliJ?

### Known Issues with JetBrains' Built-in Driver

JetBrains' built-in BigQuery driver has several documented issues that affect usability:

| Issue | JetBrains Driver | tbc-bq-jdbc |
|-------|------------------|-------------|
| **[DBE-22088]** Hangs with 90+ datasets | ❌ Hangs/freezes | ✅ 2-3 seconds (parallel loading) |
| **[DBE-18711]** Schema introspection failures | ❌ Unreliable | ✅ Complete JDBC compliance |
| **[DBE-12749]** Crashes on STRUCT types | ❌ Crashes | ✅ Safe JSON representation |
| **[DBE-19753]** Auth token expiration | ❌ Manual refresh | ✅ Automatic refresh |
| **[DBE-12954]** Metadata retrieval issues | ❌ Incomplete | ✅ Full metadata support |

[DBE-22088]: https://youtrack.jetbrains.com/issue/DBE-22088
[DBE-18711]: https://youtrack.jetbrains.com/issue/DBE-18711
[DBE-12749]: https://youtrack.jetbrains.com/issue/DBE-12749
[DBE-19753]: https://youtrack.jetbrains.com/issue/DBE-19753
[DBE-12954]: https://youtrack.jetbrains.com/issue/DBE-12954

### Key Advantages

✅ **Production-Grade Performance**
- Metadata caching (5-min default)
- Parallel dataset loading (6-9x faster)
- Lazy loading for large projects

✅ **Complete JDBC Compliance**
- All metadata methods implemented
- Full ResultSetMetaData support
- Proper type mapping

✅ **Modern Authentication**
- Application Default Credentials (ADC)
- Service Account with auto-refresh
- Workload Identity
- Workforce Identity

✅ **Robust Type Handling**
- Safe STRUCT/ARRAY handling
- JSON representation fallback
- No crashes on complex types

---

## Quick Start

### 1. Download the Driver

Download the latest shaded JAR from the [releases page](https://github.com/Two-Bear-Capital/tbc-bq-jdbc/releases):

```bash
# Example filename
tbc-bq-jdbc-1.0.31.jar
```

### 2. Add Driver to IntelliJ

1. Open **Settings** (Cmd+, on macOS, Ctrl+Alt+S on Windows/Linux)
2. Navigate to **Database → Drivers**
3. Click **+** to add a new driver
4. Name it "BigQuery (tbc-bq-jdbc)"
5. Add the downloaded JAR file
6. Set **Class**: `com.twobearcapital.bigquery.jdbc.BQDriver`
7. Click **OK**

### 3. Create a Data Source

1. Open **Database** tool window
2. Click **+** → **Data Source** → **BigQuery (tbc-bq-jdbc)**
3. Configure connection (see [Configuration](#configuration))
4. Click **Test Connection**
5. Click **OK**

---

## Installation

### Step 1: Download the Driver JAR

Choose one of these methods:

**Option A: Maven Central** (when available)
```xml
<dependency>
    <groupId>com.twobearcapital</groupId>
    <artifactId>tbc-bq-jdbc</artifactId>
    <version>1.0.31</version>
    <classifier>shaded</classifier>
</dependency>
```

**Option B: Build from Source**
```bash
git clone https://github.com/Two-Bear-Capital/tbc-bq-jdbc.git
cd tbc-bq-jdbc
./mvnw clean package
# JAR will be in target/tbc-bq-jdbc-1.0.31.jar
```

**Option C: Download Release**
- Visit [GitHub Releases](https://github.com/Two-Bear-Capital/tbc-bq-jdbc/releases)
- Download `tbc-bq-jdbc-X.Y.Z-shaded.jar`

### Step 2: Configure IntelliJ Driver

#### Detailed Configuration Steps

1. **Open Database Settings**
   - **macOS**: IntelliJ IDEA → Settings (Cmd+,)
   - **Windows/Linux**: File → Settings (Ctrl+Alt+S)

2. **Navigate to Drivers**
   - Database → Drivers

3. **Create New Driver**
   - Click the **+** button
   - Select **User Driver**

4. **Configure Driver Settings**
   - **Name**: `BigQuery (tbc-bq-jdbc)`
   - **Driver Files**: Click **+** → Add the downloaded JAR
   - **Class**: `com.twobearcapital.bigquery.jdbc.BQDriver`

5. **URL Template** (optional):
   ```
   jdbc:bigquery:{project}/{dataset}?authType=ADC
   ```

6. **Apply and Close**

### Step 3: Create Data Source

1. **Open Database Tool Window**
   - View → Tool Windows → Database
   - Or press Alt+1 (Windows/Linux) / Cmd+1 (macOS)

2. **Add Data Source**
   - Click **+** → Data Source → BigQuery (tbc-bq-jdbc)

3. **Configure Connection** (see next section)

---

## Configuration

### Connection URL Format

```
jdbc:bigquery:{project}[/{dataset}]?authType={type}[&option=value...]
```

### Configuration Examples

#### 1. Application Default Credentials (Recommended)

**Simplest Setup** - Uses your gcloud credentials:

```
jdbc:bigquery:my-project?authType=ADC
```

IntelliJ Configuration:
- **Host**: `my-project`
- **Database**: (leave empty or specify default dataset)
- **URL**: `jdbc:bigquery:my-project?authType=ADC`
- **Authentication**: None needed (uses ADC)

#### 2. Service Account

**Production Setup** - Uses a service account key file:

```
jdbc:bigquery:my-project?authType=SERVICE_ACCOUNT&credentials=/path/to/key.json
```

IntelliJ Configuration:
- **URL**: `jdbc:bigquery:my-project?authType=SERVICE_ACCOUNT&credentials=/Users/you/keys/bigquery-key.json`

#### 3. With Default Dataset

**Quick Querying** - Specifies a default dataset:

```
jdbc:bigquery:my-project/my_dataset?authType=ADC
```

IntelliJ Configuration:
- **Host**: `my-project`
- **Database**: `my_dataset`
- **URL**: `jdbc:bigquery:my-project/my_dataset?authType=ADC`

#### 4. Performance-Optimized (Large Projects)

**For 50+ Datasets**:

```
jdbc:bigquery:my-project?authType=ADC&metadataCacheTtl=600&metadataLazyLoad=true
```

- `metadataCacheTtl=600`: 10-minute cache
- `metadataLazyLoad=true`: Lazy load tables/columns

#### 5. Development Setup

**Frequent Schema Changes**:

```
jdbc:bigquery:my-project?authType=ADC&metadataCacheTtl=60
```

- `metadataCacheTtl=60`: 1-minute cache (shorter refresh)

---

## Performance Tuning

### Understanding Performance Settings

tbc-bq-jdbc includes three performance optimizations specifically for IntelliJ:

#### 1. Metadata Caching

**What it does**: Caches metadata query results to avoid repeated API calls

**Connection Properties**:
- `metadataCacheEnabled` (default: `true`)
- `metadataCacheTtl` (default: `300` = 5 minutes)

**When to adjust**:
- **Increase TTL** (e.g., `600` = 10 min): Stable production schemas
- **Decrease TTL** (e.g., `60` = 1 min): Active development with schema changes
- **Disable** (`metadataCacheEnabled=false`): Real-time schema reflection needed

**Example**:
```
jdbc:bigquery:my-project?authType=ADC&metadataCacheTtl=600
```

#### 2. Parallel Loading

**What it does**: Queries multiple datasets concurrently using virtual threads

**Behavior**:
- **Automatic**: Enables when ≥5 datasets detected
- **Performance**: 6-9x faster for projects with 90+ datasets
- **No configuration needed**: Works out of the box

**Performance Impact**:
- Sequential (old way): 90 datasets × 200ms = **18 seconds**
- Parallel (tbc-bq-jdbc): **2-3 seconds**

This directly addresses **JetBrains issue DBE-22088** (hangs with 90+ datasets).

#### 3. Lazy Loading

**What it does**: Returns empty results when no specific pattern is provided, loading data only when needed

**Connection Property**:
- `metadataLazyLoad` (default: `false`)

**When to enable**:
- Projects with 100+ datasets
- You don't need to see all tables immediately
- IntelliJ tree loads instantly, queries on-demand when you expand nodes

**Example**:
```
jdbc:bigquery:my-project?authType=ADC&metadataLazyLoad=true
```

**Trade-offs**:
- ✅ Instant initial connection
- ✅ Minimal API calls
- ❌ Must expand nodes to see contents
- ❌ Search/autocomplete won't see unexpanded items

### Recommended Configurations

#### Small Projects (< 10 datasets)

**Default settings work perfectly**:
```
jdbc:bigquery:my-project?authType=ADC
```

**Expected performance**:
- Initial connection: < 1 second
- Browse schema: < 2 seconds
- Cached queries: Instant

#### Medium Projects (10-50 datasets)

**Increase cache TTL**:
```
jdbc:bigquery:my-project?authType=ADC&metadataCacheTtl=600
```

**Expected performance**:
- Initial connection: 1-2 seconds
- Browse schema: 2-4 seconds (parallel)
- Cached queries: Instant

#### Large Projects (50-100 datasets)

**Increase cache + consider lazy loading**:
```
jdbc:bigquery:my-project?authType=ADC&metadataCacheTtl=600&metadataLazyLoad=true
```

**Expected performance**:
- Initial connection: < 1 second (lazy)
- Expand dataset: 1-2 seconds
- Cached queries: Instant

#### Very Large Projects (100+ datasets)

**Maximum optimization**:
```
jdbc:bigquery:my-project?authType=ADC&metadataCacheTtl=1800&metadataLazyLoad=true
```

- `metadataCacheTtl=1800`: 30-minute cache
- `metadataLazyLoad=true`: Lazy load everything

**Expected performance**:
- Initial connection: Instant
- Expand dataset: 1-2 seconds (first time)
- Expand dataset: Instant (cached)

---

## Feature Comparison

### Complete Feature Matrix

| Feature | JetBrains Driver | tbc-bq-jdbc |
|---------|------------------|-------------|
| **Metadata Retrieval** |
| List projects (catalogs) | ⚠️ Limited | ✅ Full support |
| List datasets (schemas) | ✅ Works | ✅ Works + cached |
| List tables | ⚠️ Slow (90+) | ✅ Fast (parallel) |
| List columns | ⚠️ Slow | ✅ Fast (parallel) |
| Table types | ✅ Works | ✅ Works |
| Column metadata | ⚠️ Incomplete | ✅ Complete |
| **Performance** |
| Metadata caching | ❌ None | ✅ 5-min default |
| Parallel loading | ❌ Sequential | ✅ Virtual threads |
| Lazy loading | ❌ None | ✅ Optional |
| **Type Support** |
| Standard types | ✅ Works | ✅ Works |
| STRUCT | ❌ Crashes | ✅ JSON representation |
| ARRAY | ⚠️ Limited | ✅ Full support |
| GEOGRAPHY | ⚠️ Limited | ✅ WKT string |
| JSON | ⚠️ Limited | ✅ Native support |
| **Authentication** |
| ADC | ✅ Works | ✅ Works |
| Service Account | ✅ Works | ✅ Works + auto-refresh |
| Token expiration | ❌ Manual refresh | ✅ Auto-refresh |
| Workload Identity | ❌ Not supported | ✅ Supported |
| Workforce Identity | ❌ Not supported | ✅ Supported |
| **Query Execution** |
| Standard SQL | ✅ Works | ✅ Works |
| Legacy SQL | ⚠️ Limited | ✅ Supported |
| Parameterized queries | ✅ Works | ✅ Works |
| Result pagination | ✅ Works | ✅ Works + Storage API |
| **IntelliJ Integration** |
| Database browser | ⚠️ Slow/hangs | ✅ Fast |
| Autocomplete | ✅ Works | ✅ Works |
| Query console | ✅ Works | ✅ Works |
| Result viewer | ⚠️ STRUCT crashes | ✅ All types work |

---

## Troubleshooting

### Connection Issues

#### Problem: "No suitable driver found"

**Symptoms**:
```
java.sql.SQLException: No suitable driver found for jdbc:bigquery:...
```

**Solution**:
1. Verify driver JAR is added to IntelliJ
2. Check driver class: `com.twobearcapital.bigquery.jdbc.BQDriver`
3. Ensure URL starts with `jdbc:bigquery:`

#### Problem: Authentication fails

**Symptoms**:
```
java.io.IOException: The Application Default Credentials are not available
```

**Solution**:
1. **For ADC**: Run `gcloud auth application-default login`
2. **For Service Account**: Verify path to JSON key file
3. Check file permissions: `chmod 600 /path/to/key.json`

#### Problem: "Project not found"

**Symptoms**:
```
SQLException: Project 'my-project' not found
```

**Solution**:
1. Verify project ID (not project name)
2. Check IAM permissions: Need `bigquery.jobs.create`
3. Ensure billing is enabled

### Performance Issues

#### Problem: Schema tree loads slowly

**Symptoms**: IntelliJ hangs when expanding database node

**Solutions**:

1. **Enable lazy loading** (if 50+ datasets):
   ```
   ?metadataLazyLoad=true
   ```

2. **Increase cache TTL**:
   ```
   ?metadataCacheTtl=600
   ```

3. **Check dataset count**:
   ```sql
   SELECT COUNT(*) FROM `my-project.__EMPTY__`;
   ```

#### Problem: Queries are slow

**Symptoms**: Query results take a long time

**This is not a driver issue** - This is BigQuery query execution time.

**Solutions**:
1. Optimize query (add WHERE clauses, avoid SELECT *)
2. Check query execution in BigQuery Console
3. Consider using cached tables

#### Problem: Out of memory

**Symptoms**:
```
java.lang.OutOfMemoryError: Java heap space
```

**Solutions**:

1. **Increase IntelliJ heap size**:
   - Help → Edit Custom VM Options
   - Add: `-Xmx4g` (4GB heap)

2. **Limit result size**:
   ```
   ?maxResults=10000
   ```

3. **Use pagination**:
   ```sql
   SELECT * FROM table LIMIT 1000 OFFSET 0
   ```

### Type Display Issues

#### Problem: STRUCT shows as "[object]"

**This is expected behavior** - STRUCT types show as JSON.

**To view STRUCT contents**:
1. Double-click the cell
2. Or use `TO_JSON_STRING()` in query:
   ```sql
   SELECT TO_JSON_STRING(struct_column) FROM table
   ```

#### Problem: ARRAY shows as "[1, 2, 3]"

**This is expected behavior** - ARRAY types show as JSON arrays.

**To work with arrays**:
1. Use `UNNEST()` to expand:
   ```sql
   SELECT element FROM table, UNNEST(array_column) AS element
   ```

### Caching Issues

#### Problem: Schema changes not reflected

**Symptoms**: Created new table but don't see it in IntelliJ

**Solution**:

1. **Wait for cache to expire** (default 5 minutes)

2. **Or reduce cache TTL**:
   ```
   ?metadataCacheTtl=60
   ```

3. **Or disable cache temporarily**:
   ```
   ?metadataCacheEnabled=false
   ```

4. **Or reconnect** to clear cache

---

## Best Practices

### 1. Use Application Default Credentials for Development

**Why**: Simplest setup, uses your personal credentials

**Setup**:
```bash
gcloud auth application-default login
gcloud config set project my-project
```

**Connection URL**:
```
jdbc:bigquery:my-project?authType=ADC
```

### 2. Use Service Accounts for Production/CI

**Why**: More secure, auditable, doesn't depend on personal credentials

**Setup**:
```bash
# Create service account
gcloud iam service-accounts create bigquery-reader

# Grant permissions
gcloud projects add-iam-policy-binding my-project \
  --member="serviceAccount:bigquery-reader@my-project.iam.gserviceaccount.com" \
  --role="roles/bigquery.user"

# Create key
gcloud iam service-accounts keys create key.json \
  --iam-account=bigquery-reader@my-project.iam.gserviceaccount.com
```

**Connection URL**:
```
jdbc:bigquery:my-project?authType=SERVICE_ACCOUNT&credentials=/path/to/key.json
```

### 3. Tune Performance for Your Project Size

| Project Size | Recommended Configuration |
|--------------|---------------------------|
| Small (< 10 datasets) | Default settings |
| Medium (10-50 datasets) | `metadataCacheTtl=600` |
| Large (50-100 datasets) | `metadataCacheTtl=600&metadataLazyLoad=true` |
| Very Large (100+ datasets) | `metadataCacheTtl=1800&metadataLazyLoad=true` |

### 4. Use Specific Datasets When Possible

**Instead of**:
```
jdbc:bigquery:my-project?authType=ADC
```

**Use**:
```
jdbc:bigquery:my-project/analytics?authType=ADC
```

**Benefits**:
- Faster autocomplete
- Shorter fully-qualified names
- Better query organization

### 5. Leverage Caching for Stable Schemas

**For production databases** with infrequent schema changes:
```
jdbc:bigquery:prod-project?authType=SERVICE_ACCOUNT&credentials=/keys/prod.json&metadataCacheTtl=1800
```

**For development databases** with frequent changes:
```
jdbc:bigquery:dev-project?authType=ADC&metadataCacheTtl=60
```

### 6. Monitor Your Queries

**Enable query logging** to see actual BigQuery API calls:
```
?enableQueryLogging=true
```

**Check query costs** in BigQuery Console:
- Navigation Menu → BigQuery → Job History
- Filter by user/service account

### 7. Use Connection Pooling for Applications

**For applications** (not IntelliJ):
```java
HikariConfig config = new HikariConfig();
config.setJdbcUrl("jdbc:bigquery:my-project?authType=ADC");
config.setMaximumPoolSize(10);
HikariDataSource ds = new HikariDataSource(config);
```

### 8. Secure Your Credentials

**Never commit credentials**:
```bash
# Add to .gitignore
*.json
*.key
```

**Use environment variables**:
```bash
export GOOGLE_APPLICATION_CREDENTIALS=/path/to/key.json
```

**Or use secret management**:
- Google Secret Manager
- HashiCorp Vault
- AWS Secrets Manager

---

## Additional Resources

- [GitHub Repository](https://github.com/Two-Bear-Capital/tbc-bq-jdbc)
- [JetBrains Issues Analysis](JETBRAINS_ISSUES.md)
- [Connection Properties Reference](CONNECTION_PROPERTIES.md)
- [Authentication Guide](AUTHENTICATION.md)
- [Type Mapping Reference](TYPE_MAPPING.md)
- [BigQuery Documentation](https://cloud.google.com/bigquery/docs)

---

## Getting Help

### Report Issues

Found a bug or have a feature request?

1. Check [existing issues](https://github.com/Two-Bear-Capital/tbc-bq-jdbc/issues)
2. Create a new issue with:
   - IntelliJ version
   - tbc-bq-jdbc version
   - Connection URL (redact credentials)
   - Error message/logs
   - Steps to reproduce

### Performance Questions

For introspection performance issues:
1. Include number of datasets in your project
2. Include connection properties used
3. Include timing information

### Community Support

- GitHub Discussions (coming soon)
- Stack Overflow: Tag with `tbc-bq-jdbc`

---

**Last Updated**: 2026-02-07
**Driver Version**: 1.0.31
**IntelliJ Version Tested**: 2025.3.x

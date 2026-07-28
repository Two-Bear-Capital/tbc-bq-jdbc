# TBC BigQuery JDBC Driver

[![Build](https://github.com/Two-Bear-Capital/tbc-bq-jdbc/actions/workflows/build.yml/badge.svg)](https://github.com/Two-Bear-Capital/tbc-bq-jdbc/actions/workflows/build.yml)
[![CodeQL](https://github.com/Two-Bear-Capital/tbc-bq-jdbc/actions/workflows/github-code-scanning/codeql/badge.svg)](https://github.com/Two-Bear-Capital/tbc-bq-jdbc/actions/workflows/github-code-scanning/codeql)
[![Dependabot Updates](https://github.com/Two-Bear-Capital/tbc-bq-jdbc/actions/workflows/dependabot/dependabot-updates/badge.svg)](https://github.com/Two-Bear-Capital/tbc-bq-jdbc/actions/workflows/dependabot/dependabot-updates)
[![Java](https://img.shields.io/badge/Java-21+-blue.svg)](https://openjdk.org/)
[![JDBC](https://img.shields.io/badge/JDBC-4.3-green.svg)](https://docs.oracle.com/en/java/javase/21/docs/api/java.sql/module-summary.html)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

Modern JDBC driver for Google BigQuery, optimized for development tools and database IDEs. Built from scratch for Java 21+ with JDBC 4.3 compliance and fast, high-quality metadata support.

## Features

🎯 **Database IDE Optimized**
- Fast, comprehensive metadata operations
- Parallel dataset loading with intelligent caching
- Addresses open JetBrains BigQuery issues (DBE-12808, DBE-12749, DBE-17806, DBE-14390, DBE-16410, DBE-18711)
- Metadata loaded in parallel and cached, so schema introspection stays fast on large projects

✨ **Modern Java 21+**
- Records, sealed classes, pattern matching
- Virtual thread support
- CompletableFuture-based async operations

🔐 **Comprehensive Authentication**
- Application Default Credentials (ADC)
- Service Account (JSON key)
- User OAuth 2.0
- Workforce Identity Federation
- Workload Identity Federation

📊 **BigQuery Sessions**
- Temporary tables (`CREATE TEMP TABLE`)
- Multi-statement SQL scripts
- Transaction support (`BEGIN`, `COMMIT`, `ROLLBACK`)

⚡ **Performance**
- Optional BigQuery Storage Read API for large result sets
- Batched `INSERT`s collapsed into multi-row statements
- Configurable result pagination
- Connection pooling compatible
- Query timeout enforcement with automatic cancellation

🎯 **Complete Type Support**
- All BigQuery primitive types
- Temporal types (TIMESTAMP, DATE, TIME, DATETIME)
- Numeric types (NUMERIC, BIGNUMERIC)
- Complex types (ARRAY, STRUCT, JSON, GEOGRAPHY)

## IntelliJ IDEA Integration

🚀 **Optimized for Database IDEs and Development Tools**

This driver addresses critical limitations in existing BigQuery JDBC drivers for IntelliJ IDEA and other database IDEs, with a focus on fast metadata operations and schema introspection:

✅ **Reliable Schema Introspection** - Complete `DatabaseMetaData` implementation ([DBE-18711](https://youtrack.jetbrains.com/issue/DBE-18711))

✅ **Fast on Large Projects** - Table and column metadata fetched in parallel and cached, so introspection cost lands once per cache window rather than on every tree expansion

✅ **Readable STRUCT/ARRAY** - JSON by default, keeping the result grid stable ([DBE-12749](https://youtrack.jetbrains.com/issue/DBE-12749), [DBE-17806](https://youtrack.jetbrains.com/issue/DBE-17806)); native `java.sql.Array`/`java.sql.Struct` via `nativeComplexTypes=true`

✅ **Query Cost Before You Run** - `enableQueryCostEstimation=true` reports bytes processed as a `SQLWarning` ([DBE-12808](https://youtrack.jetbrains.com/issue/DBE-12808))

✅ **Stable Authentication** - Credentials cached and refreshed, without repeated prompts ([DBE-14390](https://youtrack.jetbrains.com/issue/DBE-14390))

✅ **Temp Tables and Transactions** - Real BigQuery sessions ([DBE-16410](https://youtrack.jetbrains.com/issue/DBE-16410))

📖 **[Complete IntelliJ Setup Guide →](docs/INTELLIJ.md)**

### Quick Start for IntelliJ

1. **Download Driver JAR** — use the `with-logging` variant, which bundles every
   dependency plus a preconfigured Logback (see [Logging](docs/LOGGING.md)):
   ```bash
   wget https://github.com/Two-Bear-Capital/tbc-bq-jdbc/releases/latest/download/tbc-bq-jdbc-3.0.6-with-logging.jar
   ```

2. **Add Driver in IntelliJ**
   - Go to **Settings → Database → Drivers**
   - Click **+** to add new driver
   - Name: `BigQuery (tbc-bq-jdbc)`
   - Driver Files: Select downloaded JAR
   - Class: `vc.tbc.bq.jdbc.BQDriver`

3. **Connect to BigQuery**
   ```
   jdbc:bigquery:my-project/my_dataset?authType=ADC
   ```

4. **For Large Projects** (50+ datasets):
   ```
   jdbc:bigquery:my-project?authType=ADC&metadataCacheEnabled=true&metadataCacheTtl=600
   ```

See **[IntelliJ Integration Guide](docs/INTELLIJ.md)** for:
- Complete installation instructions
- Performance tuning for large projects
- Comparison with JetBrains driver
- Troubleshooting guide

## Quick Start

### Installation

#### Download a JAR (GitHub Releases)

Every release attaches all five artifacts. Pick the variant that matches how you run the
driver — see [Logging](docs/LOGGING.md#jar-variants) for the full comparison:

```bash
# Bundles all dependencies plus preconfigured logging — for IntelliJ, DBeaver, DataGrip
wget https://github.com/Two-Bear-Capital/tbc-bq-jdbc/releases/latest/download/tbc-bq-jdbc-3.0.6-with-logging.jar

# Bundles all dependencies, bring your own SLF4J binding — for standalone apps
wget https://github.com/Two-Bear-Capital/tbc-bq-jdbc/releases/latest/download/tbc-bq-jdbc-3.0.6-shaded.jar
```

Or browse [all releases](https://github.com/Two-Bear-Capital/tbc-bq-jdbc/releases).

#### Maven / Gradle

> **Not yet published to Maven Central.** The coordinates below are reserved for the
> first Central release; until then, use a GitHub Releases JAR as shown above (or
> `./mvnw clean install` to publish to your local repository).

```xml
<dependency>
    <groupId>vc.tbc</groupId>
    <artifactId>tbc-bq-jdbc</artifactId>
    <version>3.0.6</version>
</dependency>
```

```gradle
dependencies {
    implementation 'vc.tbc:tbc-bq-jdbc:3.0.6'
}
```

### Basic Usage

```java
import java.sql.*;

public class Example {
    public static void main(String[] args) throws SQLException {
        // Connect using Application Default Credentials
        String url = "jdbc:bigquery:my-project/my_dataset?authType=ADC";

        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT name, age FROM users LIMIT 10")) {

            while (rs.next()) {
                String name = rs.getString("name");
                int age = rs.getInt("age");
                System.out.printf("%s is %d years old%n", name, age);
            }
        }
    }
}
```

### Prepared Statements

```java
String url = "jdbc:bigquery:my-project/my_dataset?authType=ADC";

try (Connection conn = DriverManager.getConnection(url);
     PreparedStatement pstmt = conn.prepareStatement(
         "SELECT * FROM users WHERE age > ? AND active = ?")) {

    pstmt.setInt(1, 18);
    pstmt.setBoolean(2, true);

    try (ResultSet rs = pstmt.executeQuery()) {
        while (rs.next()) {
            System.out.println(rs.getString("name"));
        }
    }
}
```

### Using Transactions and Temp Tables

`setAutoCommit(false)` starts a BigQuery session on demand; `enableSessions=true`
creates it when the connection opens instead.

```java
String url = "jdbc:bigquery:my-project/my_dataset?" +
             "authType=ADC&enableSessions=true";

try (Connection conn = DriverManager.getConnection(url)) {
    conn.setAutoCommit(false); // Begin transaction

    try (Statement stmt = conn.createStatement()) {
        // Create temp table
        stmt.execute("CREATE TEMP TABLE temp_data AS SELECT 1 as id");

        // Use temp table
        ResultSet rs = stmt.executeQuery("SELECT * FROM temp_data");

        conn.commit(); // Commit transaction
    } catch (SQLException e) {
        conn.rollback(); // Rollback on error
        throw e;
    }
}
```

## Documentation

📚 **Complete Guides:**

- **[Quick Start](docs/QUICKSTART.md)** - Get started in 5 minutes
- **[Authentication Guide](docs/AUTHENTICATION.md)** - All authentication methods with examples
- **[Connection Properties](docs/CONNECTION_PROPERTIES.md)** - Complete configuration reference
- **[Type Mapping](docs/TYPE_MAPPING.md)** - BigQuery ↔ JDBC type conversions
- **[Compatibility Matrix](docs/COMPATIBILITY.md)** - JDBC features and limitations
- **[Logging](docs/LOGGING.md)** - JAR variants and logging configuration
- **[Observability](docs/OBSERVABILITY.md)** - Driver metrics for diagnosing your own workload
- **[Why tbc-bq-jdbc](docs/JETBRAINS_ISSUES.md)** - JetBrains driver issues this resolves
- **[Integration Tests](docs/contributing/INTEGRATION_TESTS.md)** - Running integration tests

## URL Format

### Traditional Format

```
jdbc:bigquery:[project]/[dataset]?property1=value1&property2=value2
```

**Examples:**

```java
// Application Default Credentials
"jdbc:bigquery:my-project/my_dataset?authType=ADC"

// Service Account
"jdbc:bigquery:my-project/my_dataset?authType=SERVICE_ACCOUNT&credentials=/path/to/key.json"

// With sessions and location
"jdbc:bigquery:my-project/my_dataset?authType=ADC&enableSessions=true&location=EU"

// With timeout and page size
"jdbc:bigquery:my-project/my_dataset?authType=ADC&timeout=600&pageSize=50000"
```

### Simba BigQuery Driver Compatibility

tbc-bq-jdbc supports **Simba BigQuery JDBC driver URL format** for easy migration from Simba-based applications. Use the same connection strings without modification:

```
jdbc:bigquery://[Host]:[Port];ProjectId=[Project];OAuthType=[AuthValue];[Property1]=[Value1];...
```

**Simba Format Examples:**

```java
// Application Default Credentials (OAuthType=3)
"jdbc:bigquery://https://www.googleapis.com/bigquery/v2:443;ProjectId=my-project;OAuthType=3"

// Service Account (OAuthType=0)
"jdbc:bigquery://https://www.googleapis.com/bigquery/v2:443;ProjectId=my-project;DefaultDataset=my_dataset;OAuthType=0;OAuthPvtKeyPath=/path/to/key.json"

// User OAuth (OAuthType=1)
"jdbc:bigquery://https://www.googleapis.com/bigquery/v2:443;ProjectId=my-project;OAuthType=1;OAuthClientId=id;OAuthClientSecret=secret;OAuthRefreshToken=token"

// With additional properties
"jdbc:bigquery://https://www.googleapis.com/bigquery/v2:443;ProjectId=my-project;DefaultDataset=my_dataset;OAuthType=3;Timeout=120;Location=EU"
```

**OAuthType Values:**

| OAuthType | Authentication Method | Notes |
|-----------|----------------------|-------|
| `0` | Service Account | Requires `OAuthPvtKeyPath` |
| `1` | User OAuth | Requires `OAuthClientId`, `OAuthClientSecret`, `OAuthRefreshToken` |
| `3` | Application Default | Recommended for most use cases |
| `4` | External Account | Requires `credentialConfigFile` via Properties |

**Property Mapping:**

Simba properties are automatically mapped to tbc-bq-jdbc equivalents:

| Simba Property | tbc-bq-jdbc Property |
|----------------|---------------------|
| `ProjectId` | `projectId` |
| `DefaultDataset` | `datasetId` |
| `OAuthPvtKeyPath` | `credentials` |
| `Timeout` | `timeout` |
| `MaxResults` | `maxResults` |
| `Location` | `location` |

See [Connection Properties](docs/CONNECTION_PROPERTIES.md) for complete property mapping and all available options.

## Connection Pooling

Works with all major connection pools:

### HikariCP (Recommended)

```java
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

HikariConfig config = new HikariConfig();
config.setJdbcUrl("jdbc:bigquery:my-project/my_dataset?authType=ADC");
config.setMaximumPoolSize(10);
config.setMinimumIdle(2);
config.setConnectionTimeout(30000);

HikariDataSource dataSource = new HikariDataSource(config);

// Use the pool
try (Connection conn = dataSource.getConnection()) {
    // Execute queries...
}
```

## Authentication

### Application Default Credentials (ADC)

**Best for:** Local development, Google Cloud environments

```bash
# Set up ADC
gcloud auth application-default login
```

```java
String url = "jdbc:bigquery:my-project/my_dataset?authType=ADC";
Connection conn = DriverManager.getConnection(url);
```

### Service Account

**Best for:** Production, automation

```java
String url = "jdbc:bigquery:my-project/my_dataset?" +
             "authType=SERVICE_ACCOUNT&" +
             "credentials=/path/to/service-account-key.json";
Connection conn = DriverManager.getConnection(url);
```

See [Authentication Guide](docs/AUTHENTICATION.md) for all methods.

## Building from Source

### Requirements

- Java 21 or later
- Maven 3.9+ (or use the bundled `./mvnw` wrapper)
- A Google Cloud project with BigQuery enabled, for the integration tests

### Build Commands

```bash
# Build slim JAR
./mvnw clean install

# Build shaded JAR (includes all dependencies)
./mvnw clean package

# Run unit tests
./mvnw test

# Run integration tests (requires BigQuery credentials)
export BQ_TEST_PROJECT=my-gcp-project
./mvnw verify -Preal-integration-tests
```

### Build Artifacts

`./mvnw clean package` produces all five, with approximate sizes:

| Artifact | Size | Contents |
|----------|------|----------|
| `target/tbc-bq-jdbc-3.0.6.jar` | ~220 KB | Driver classes only; dependencies must be on the classpath |
| `target/tbc-bq-jdbc-3.0.6-shaded.jar` | ~41 MB | All dependencies relocated; bring your own SLF4J binding |
| `target/tbc-bq-jdbc-3.0.6-with-logging.jar` | ~42 MB | Shaded, plus Logback preconfigured — the IDE variant |
| `target/tbc-bq-jdbc-3.0.6-sources.jar` | ~185 KB | Sources |
| `target/tbc-bq-jdbc-3.0.6-javadoc.jar` | ~610 KB | API reference |

## Testing

### Unit Tests

Unit tests cover:
- Driver registration and URL parsing
- Connection property validation
- Authentication configuration
- Type mapping
- Exception handling
- JDBC 4.3 methods

```bash
./mvnw test
```

### Real BigQuery Integration Tests

Integration tests run against a live BigQuery instance. There is no emulator tier: BigQuery's semantics cannot be reproduced faithfully enough for a test to mean anything.

**Prerequisites:**

```bash
# Authenticate with ADC
gcloud auth application-default login

# Set required env vars
export BQ_TEST_PROJECT=my-gcp-project
export BQ_TEST_DATASET=tbc_bq_jdbc_integration_tests   # optional, this is the default
```

```bash
./mvnw verify -Preal-integration-tests
```

Tests are **automatically skipped** when `BQ_TEST_PROJECT` is not set, so they never block local builds without credentials. In CI, they run via Workload Identity Federation.

See [Integration Tests Guide](docs/contributing/INTEGRATION_TESTS.md) for details.

### Benchmarks

JMH benchmarks for performance testing against a real BigQuery connection:

```bash
# Set BigQuery connection URL (required)
export BENCHMARK_JDBC_URL="jdbc:bigquery:my-project/my_dataset?authType=ADC"

# Run all benchmarks
./mvnw test-compile exec:exec -Pbenchmarks

# Run a specific benchmark class (glob pattern)
./mvnw test-compile exec:exec -Pbenchmarks -Dbenchmark.args="ResultSetIterationBenchmark"

# Thread-scaling sweep with a Markdown report
./mvnw test-compile exec:exec -Pbenchmark-scaling
```

**Available benchmarks:**
- `ResultSetIterationBenchmark` — throughput of `next()`, column access by name vs. index (100/1000/10000 rows)
- `QueryBenchmark` — latency of query execution and connection creation
- `PreparedStatementBenchmark` — parameterized query throughput
- `ThreadScalingBenchmark` — concurrent throughput across thread counts

Benchmarks use `exec:exec`, not `exec:java`: JMH forks a JVM and rebuilds its classpath,
which an in-process runner cannot supply. See
[Performance](docs/contributing/PERFORMANCE.md) for the full harness.

> **Note:** Benchmarks require a live BigQuery project and will submit real jobs. JMH forks separate JVMs per benchmark to avoid JIT bias — this is expected behavior.

## JDBC Compliance

**JDBC Version:** 4.3

**Compliance Level:** Partial (due to BigQuery limitations)

### ✅ Supported Features

- Connection lifecycle (open, close, isValid)
- Statement, PreparedStatement execution
- ResultSet forward iteration (TYPE_FORWARD_ONLY)
- ResultSetMetaData, DatabaseMetaData
- JDBC 4.3 methods (beginRequest, endRequest, enquoteLiteral, etc.)
- Sessions and transactions
- All BigQuery data types
- Query timeout and cancellation

### ❌ Unsupported Features

- Scrollable or updatable ResultSets
- CallableStatement and stored-procedure call syntax
- Savepoints and configurable transaction isolation

See [Compatibility Matrix](docs/COMPATIBILITY.md) for complete details.

## Performance

Every statement runs as a BigQuery job and pays the service's job-creation latency
before any data moves, so even trivial queries have a floor. The driver is not suited to
high query-per-second workloads.

### Optimization Tips

- Enable `useStorageApi` for large result sets, and tune `pageSize`
- Use connection pooling
- Cache frequently executed queries
- Set appropriate timeouts

See [Connection Properties - Performance Tuning](docs/CONNECTION_PROPERTIES.md#performance-tuning) for detailed optimization strategies.

## Known Limitations

### BigQuery Architecture

- **Transactions require a BigQuery session**, which `setAutoCommit(false)` starts for you
- **No indexes** (BigQuery auto-optimizes)
- **Primary/foreign keys are declarative only** — BigQuery accepts `PRIMARY KEY`/`FOREIGN KEY ... NOT ENFORCED` and never validates them. The driver reports them through `getPrimaryKeys()`, `getImportedKeys()`, `getExportedKeys()` and `getCrossReference()`, so ER diagrams and FK-aware tools work — but the constraints are a statement of intent, not a guarantee about the data. See [Compatibility](docs/COMPATIBILITY.md#unenforced-primary-and-foreign-keys).
- **No row-level locking**

### JDBC Limitations

- Forward-only ResultSets (no scrollable)
- Read-only ResultSets (no updatable)

See [Compatibility Matrix](docs/COMPATIBILITY.md) for complete list.

## Contributing

Contributions welcome! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

### Development Setup

1. Clone the repository
2. Install Java 21+
3. Run `./mvnw clean install` (Maven Wrapper included, no need to install Maven)

### Running Tests

```bash
# Unit tests
./mvnw test

# Integration tests (requires BigQuery credentials)
./mvnw verify -Preal-integration-tests

# Format code
./mvnw spotless:apply
```

## License & Disclaimer

Apache License 2.0 - see [LICENSE](LICENSE) file for details.

**Use at your own risk.** This software is provided "as is" without warranties of any kind. See LICENSE for complete disclaimer.

## Support

- 📖 [Documentation](docs/)
- 🐛 [Issue Tracker](https://github.com/Two-Bear-Capital/tbc-bq-jdbc/issues)

## Acknowledgments

- Architecture inspired by [looker-open-source/bqjdbc](https://github.com/looker-open-source/bqjdbc)
- Built for Java 21+ with modern features
- Uses Google Cloud BigQuery Client Library

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
- Fixes critical JetBrains driver issues (DBE-18711, DBE-12954, DBE-22088, DBE-12749, DBE-19753)
- 30x faster schema introspection for large projects

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

✅ **Reliable Schema Introspection** - Complete DatabaseMetaData implementation (fixes [DBE-18711](https://youtrack.jetbrains.com/issue/DBE-18711), [DBE-12954](https://youtrack.jetbrains.com/issue/DBE-12954))

✅ **High Performance with Large Projects** - Parallel loading + caching for 90+ datasets (fixes [DBE-22088](https://youtrack.jetbrains.com/issue/DBE-22088))
- JetBrains driver: Hangs or takes 90+ seconds
- tbc-bq-jdbc: 2-3 seconds (30x faster)

✅ **Safe STRUCT/ARRAY Handling** - JSON representation prevents crashes (fixes [DBE-12749](https://youtrack.jetbrains.com/issue/DBE-12749)); native `java.sql.Array`/`java.sql.Struct` objects available via `nativeComplexTypes=true`

✅ **Robust Authentication** - Automatic token refresh for long sessions (fixes [DBE-19753](https://youtrack.jetbrains.com/issue/DBE-19753))

📖 **[Complete IntelliJ Setup Guide →](docs/INTELLIJ.md)**

### Quick Start for IntelliJ

1. **Download Driver JAR**
   ```bash
   wget https://repo1.maven.org/maven2/vc/tbc/tbc-bq-jdbc/1.0.120/tbc-bq-jdbc-1.0.120.jar
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

#### Maven

```xml
<dependency>
    <groupId>vc.tbc</groupId>
    <artifactId>tbc-bq-jdbc</artifactId>
    <version>1.0.120</version>
</dependency>
```

#### Gradle

```gradle
dependencies {
    implementation 'vc.tbc:tbc-bq-jdbc:1.0.120'
}
```

#### Standalone (Fat JAR)

```bash
# Download shaded JAR with all dependencies included
wget https://repo1.maven.org/maven2/vc/tbc/tbc-bq-jdbc/1.0.120/tbc-bq-jdbc-1.0.120.jar
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
- Maven 3.9+
- Docker (for integration tests)

### Build Commands

```bash
# Build slim JAR
./mvnw clean install

# Build shaded JAR (includes all dependencies)
./mvnw clean package

# Run unit tests
./mvnw test

# Run integration tests (requires Docker)
./mvnw verify -Pintegration-tests
```

### Build Artifacts

After building:
- **Slim JAR:** `target/tbc-bq-jdbc-1.0.120.jar` (60K)
- **Shaded JAR:** `target/tbc-bq-jdbc-1.0.120-shaded.jar` (51M)
- **Sources JAR:** `target/tbc-bq-jdbc-1.0.120-sources.jar` (41K)
- **Javadoc JAR:** `target/tbc-bq-jdbc-1.0.120-javadoc.jar` (267K)

## Testing

### Unit Tests

904 unit tests covering:
- Driver registration and URL parsing
- Connection property validation
- Authentication configuration
- Type mapping
- Exception handling
- JDBC 4.3 methods

```bash
./mvnw test
```

### Emulator Integration Tests

Integration tests run against the [BigQuery emulator](https://github.com/recidiviz/bigquery-emulator) via Docker/Testcontainers — no real GCP credentials required. A single shared container is started once for all test classes.

Covers:
- Connection lifecycle
- Query execution
- Prepared statements
- Metadata operations
- Type conversions
- ResultSet operations

```bash
./mvnw verify -Pintegration-tests
```

### Real BigQuery Integration Tests

A separate suite runs against a live BigQuery instance to validate behavior that the emulator does not fully replicate (e.g., strict type enforcement, BigQuery-specific SQL constraints).

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
./mvnw test-compile exec:java -Pbenchmarks

# Run a specific benchmark class (glob pattern)
./mvnw test-compile exec:java -Pbenchmarks -Dexec.args="ResultSetIterationBenchmark"
./mvnw test-compile exec:java -Pbenchmarks -Dexec.args="QueryBenchmark"
./mvnw test-compile exec:java -Pbenchmarks -Dexec.args="PreparedStatementBenchmark"
```

**Available benchmarks:**
- `ResultSetIterationBenchmark` — throughput of `next()`, column access by name vs. index (100/1000/10000 rows)
- `QueryBenchmark` — latency of query execution and connection creation
- `PreparedStatementBenchmark` — parameterized query throughput

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
- Sessions and transactions (with `enableSessions=true`)
- All BigQuery data types
- Query timeout and cancellation

### ❌ Unsupported Features

- Traditional transactions (without sessions)
- Scrollable or updatable ResultSets
- CallableStatement

See [Compatibility Matrix](docs/COMPATIBILITY.md) for complete details.

## Performance

### Query Latency

| Query Type | Typical Latency |
|------------|-----------------|
| Small (SELECT 1) | 200-500ms |
| Medium (< 100MB) | 2-10s |
| Large (> 100MB) | 10s - minutes |

### Optimization Tips

- Use `pageSize` property for large results
- Use connection pooling
- Cache frequently executed queries
- Set appropriate timeouts

See [Connection Properties - Performance Tuning](docs/CONNECTION_PROPERTIES.md#performance-tuning) for detailed optimization strategies.

## Known Limitations

### BigQuery Architecture

- **No transactions** outside of sessions (use `enableSessions=true`)
- **No indexes** (BigQuery auto-optimizes)
- **No primary/foreign keys** (data warehouse, not OLTP)
- **No row-level locking**

### JDBC Limitations

- Forward-only ResultSets (no scrollable)
- Read-only ResultSets (no updatable)

See [Compatibility Matrix](docs/COMPATIBILITY.md) for complete list.

## Roadmap

### Version 1.0 (Current) - IDE Integration Focus

- ✅ Fast, comprehensive DatabaseMetaData implementation
- ✅ Parallel dataset loading with intelligent caching
- ✅ Core JDBC 4.3 implementation
- ✅ All authentication methods
- ✅ Session support
- ✅ Complete type mapping
- ✅ Native JDBC Array/Struct support (`nativeComplexTypes=true`)
- ✅ Routine (UDF) metadata via `getProcedures()` / `getProcedureColumns()`
- ✅ Enhanced DatabaseMetaData (9 formerly-unsupported methods now return compliant results)
- ✅ Extensive testing (unit, emulator, and real BigQuery integration tests)
- ✅ Comprehensive documentation

### Future Versions

- Complete Storage API Arrow deserialization
- Additional authentication methods

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

# Integration tests (requires Docker)
./mvnw verify -Pintegration-tests

# Format code
./mvnw spotless:apply
```

## License & Disclaimer

Apache License 2.0 - see [LICENSE](LICENSE) file for details.

**Use at your own risk.** This software is provided "as is" without warranties of any kind. See LICENSE for complete disclaimer.

## Support

- 📖 [Documentation](docs/)
- 🐛 [Issue Tracker](https://github.com/Two-Bear-Capital/tbc-bq-jdbc/issues)
- 💬 [Discussions](https://github.com/Two-Bear-Capital/tbc-bq-jdbc/discussions)

## Acknowledgments

- Architecture inspired by [looker-open-source/bqjdbc](https://github.com/looker-open-source/bqjdbc)
- Built for Java 21+ with modern features
- Uses Google Cloud BigQuery Client Library

## Project Status

**Status:** ✅ Version 1.0 Release - Optimized for Database IDEs

This initial release focuses on providing fast, high-quality database metadata for development tools like JetBrains IDEs, DataGrip, and other database clients. Comprehensive JDBC 4.3 implementation with extensive testing across unit, emulator-based integration, and real BigQuery integration test suites.

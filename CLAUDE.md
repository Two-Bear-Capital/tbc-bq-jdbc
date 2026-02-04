# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**tbc-bq-jdbc** is a modern JDBC 4.3 driver for Google BigQuery built from scratch for Java 21+. It serves as a production-ready alternative to JetBrains' built-in BigQuery driver, with significant performance improvements and comprehensive BigQuery feature support.

**Key Design Goals:**
- Modern Java 21+ features (records, sealed classes, pattern matching, virtual threads)
- Full JDBC 4.3 compliance (within BigQuery's architectural constraints)
- High performance with parallel loading and caching for large projects
- Simba BigQuery driver URL compatibility for easy migration
- IntelliJ IDEA integration as primary use case

## Build Commands

**Note:** This project uses the Maven Wrapper (`./mvnw`) to ensure consistent Maven version across environments. No local Maven installation required.

### Basic Build Operations
```bash
# Build slim JAR (requires dependencies on classpath)
./mvnw clean install

# Build all JARs including shaded variants
./mvnw clean package

# Run unit tests only (integration tests excluded by default)
./mvnw test

# Run integration tests (requires Docker for BigQuery emulator)
./mvnw verify -Pintegration-tests

# Run specific integration test
./mvnw verify -Pintegration-tests -Dit.test=BasicConnectionTest

# Check and apply code formatting (required before commits)
./mvnw spotless:check
./mvnw spotless:apply
```

### Build Artifacts
After `./mvnw clean package`, find these in `target/`:
- `tbc-bq-jdbc-1.0.13.jar` - Slim JAR (60K, requires dependencies)
- `tbc-bq-jdbc-1.0.13.jar` - Shaded JAR with all dependencies (51M)
- `tbc-bq-jdbc-1.0.13-with-logging.jar` - Shaded JAR + Logback for IntelliJ (52M)
- `tbc-bq-jdbc-1.0.13.jar` - Source JAR
- `tbc-bq-jdbc-1.0.13.jar` - Javadoc JAR

### Running Tests
```bash
# Unit tests only (fast, no Docker needed)
./mvnw test

# Integration tests (requires Docker, uses BigQuery emulator)
./mvnw verify -Pintegration-tests

# Skip tests during build
./mvnw clean install -DskipTests

# Run benchmarks (requires real BigQuery connection)
export BENCHMARK_JDBC_URL="jdbc:bigquery:my-project/my_dataset?authType=ADC"
./mvnw clean package
java -jar target/benchmarks.jar
```

### Code Quality
```bash
# Format code (REQUIRED before commits - enforced by CI)
./mvnw spotless:apply

# Check formatting without applying
./mvnw spotless:check

# Generate coverage report
./mvnw test
# Report: target/site/jacoco/index.html
```

## Architecture

### Core Package Structure
```
src/main/java/com/twobearcapital/bigquery/jdbc/
├── BQDriver.java              # JDBC Driver entry point, URL parsing
├── BQConnection.java          # Connection implementation with session management
├── BQStatement.java           # Statement execution
├── BQPreparedStatement.java   # Parameterized queries
├── BQResultSet.java           # Query result iteration
├── TypeMapper.java            # BigQuery ↔ JDBC type conversions
├── DriverVersion.java         # Version information from git.properties
│
├── auth/                      # Authentication implementations
│   ├── AuthType.java          # Enum: ADC, SERVICE_ACCOUNT, USER_OAUTH, etc.
│   ├── ApplicationDefaultAuth.java
│   ├── ServiceAccountAuth.java
│   ├── UserOAuthAuth.java
│   ├── WorkforceIdentityAuth.java
│   ├── WorkloadIdentityAuth.java
│   └── EmulatorAuth.java      # For testing with BigQuery emulator
│
├── base/                      # Abstract base classes (inheritance hierarchy)
│   ├── BaseCloseable.java            # Lifecycle management (isClosed)
│   ├── BaseJdbcWrapper.java          # JDBC wrapper pattern
│   ├── AbstractBQConnection.java     # Connection base with validation
│   ├── AbstractBQStatement.java      # Statement base with query execution
│   ├── AbstractBQPreparedStatement.java  # PreparedStatement parameter handling
│   ├── BaseReadOnlyResultSet.java    # ResultSet base implementation
│   └── ReadOnlyResultSetMixin.java   # Shared read-only behavior
│
├── config/                    # Configuration and connection management
│   ├── ConnectionProperties.java     # Immutable record with all settings
│   ├── ConnectionUrlParser.java      # Traditional + Simba format parsing
│   ├── JobCreationMode.java          # REQUIRED vs JOB_CREATION_OPTIONAL
│   ├── SessionManager.java           # BigQuery session lifecycle
│   └── MetadataCache.java            # TTL-based metadata caching
│
├── metadata/                  # JDBC metadata implementation
│   ├── BQDatabaseMetaData.java       # DatabaseMetaData with caching
│   ├── BQResultSetMetaData.java      # ResultSet column metadata
│   ├── BQParameterMetaData.java      # PreparedStatement parameter info
│   ├── MetadataResultSet.java        # In-memory ResultSet for metadata
│   └── MetadataColumns.java          # Column metadata builders
│
├── storage/                   # BigQuery Storage Read API
│   └── StorageReadResultSet.java     # Arrow-based result reading
│
├── exception/                 # Exception handling
│   ├── BQSQLException.java           # SQLException with SQL states
│   └── BQSQLFeatureNotSupportedException.java
│
└── util/                      # Utilities
    ├── ErrorMessages.java            # Centralized error messages
    ├── UnsupportedOperations.java    # Standard exceptions for unsupported ops
    ├── SQLStates.java                # SQL state constants
    └── NumberParser.java             # Safe numeric parsing
```

### Key Architectural Patterns

#### Inheritance Hierarchy
- **Base Classes:** All JDBC implementations extend abstract bases in `base/` package
- **Mixins:** `ReadOnlyResultSetMixin` provides shared behavior for read-only operations
- **Closeable Pattern:** `BaseCloseable` manages lifecycle state consistently
- **Wrapper Pattern:** `BaseJdbcWrapper` implements JDBC wrapper methods

#### Immutable Configuration
- `ConnectionProperties` is a Java record (immutable)
- All properties have defaults applied in the canonical constructor
- Labels map is defensively copied and made unmodifiable

#### Authentication Architecture
- `AuthType` enum with `toCredentials()` method converts to Google Credentials
- Each auth type has its own class implementing credential creation
- Strategy pattern allows easy addition of new auth types

#### Metadata Caching
- `MetadataCache` provides TTL-based caching with concurrent access
- **Cache is shared statically across all connections** to the same project (persists across connection open/close cycles)
- Cache instances keyed by `projectId:ttlSeconds` for isolation
- Cache is NOT cleared on connection close - only expires based on TTL
- This design is critical for IntelliJ IDEA which frequently reopens connections
- `BQDatabaseMetaData` reuses single instance per connection (fixes IntelliJ slowness)
- Configurable via `metadataCacheEnabled`, `metadataCacheTtl`, `metadataLazyLoad`
- Parallel dataset loading for projects with 50+ datasets
- Static methods: `clearAllSharedCaches()` and `getSharedCacheCount()` for testing/debugging

#### Exception Handling
- `BQSQLException` wraps BigQuery exceptions with appropriate SQL states
- `UnsupportedOperations` provides consistent error messages for unsupported JDBC features
- All unsupported operations throw `SQLFeatureNotSupportedException` with clear messages

#### Type Mapping Strategy
- `TypeMapper` handles BigQuery StandardSQLTypeName ↔ JDBC Types
- ARRAY/STRUCT returned as JSON strings (prevents IntelliJ crashes)
- NUMERIC/BIGNUMERIC handled via BigDecimal
- Temporal types use proper JDBC mapping (TIMESTAMP, DATE, TIME)

## Connection URL Formats

The driver supports two URL formats for compatibility:

### Traditional Format (Native)
```
jdbc:bigquery:[project]/[dataset]?property1=value1&property2=value2
```

### Simba Format (Compatibility)
```
jdbc:bigquery://[Host]:[Port];ProjectId=[Project];OAuthType=[AuthValue];[Property]=[Value];...
```

**Parser Implementation:** `ConnectionUrlParser` detects format and converts to `ConnectionProperties`

**OAuthType Mapping:**
- `0` → SERVICE_ACCOUNT (requires `OAuthPvtKeyPath`)
- `1` → USER_OAUTH (requires `OAuthClientId`, `OAuthClientSecret`, `OAuthRefreshToken`)
- `3` → APPLICATION_DEFAULT (ADC)
- `4` → EXTERNAL_ACCOUNT (workload/workforce identity)

## Testing Architecture

### Unit Tests (91 tests)
- Location: `src/test/java/com/twobearcapital/bigquery/jdbc/`
- Run: `./mvnw test`
- Coverage: URL parsing, properties, type mapping, exception handling
- No external dependencies (no Docker)

### Integration Tests (113 tests)
- Location: `src/test/java/com/twobearcapital/bigquery/jdbc/integration/`
- Run: `./mvnw verify -Pintegration-tests`
- Base class: `AbstractBigQueryIntegrationTest`
- Uses Testcontainers with `goccy/bigquery-emulator` Docker image
- Covers: connections, queries, prepared statements, metadata, result sets

**Test Structure:**
- `AbstractBigQueryIntegrationTest` - Base with helper methods (`createTestTable`, `insertTestData`)
- `BasicConnectionTest` - Connection lifecycle
- `SimpleQueryTest` - Query execution
- `ParameterizedQueryTest` - PreparedStatement parameters
- `MetadataTest` - DatabaseMetaData operations
- `TypeMappingTest` - Type conversions
- `ResultSetOperationsTest` - ResultSet navigation

## Code Style and Conventions

### Required Before Commits
```bash
./mvnw spotless:apply
```

### Style Guidelines
- **Formatting:** Google Java Format (enforced by Spotless)
- **Java Version:** Java 21+ features preferred (records, sealed classes, pattern matching)
- **License Header:** Apache 2.0 header required on all `.java` files (auto-added by Spotless)
- **Indentation:**
  - Java: tabs (displayed as 4 spaces)
  - XML: 4 spaces
  - YAML: 2 spaces
- **Logging:** Use SLF4J (`org.slf4j.Logger`, `LoggerFactory.getLogger()`)

### Naming Conventions
- Classes: `BQ` prefix for JDBC implementations (e.g., `BQConnection`, `BQStatement`)
- Base classes: `Abstract` or `Base` prefix
- Mixins: `Mixin` suffix
- Exceptions: Standard JDBC exceptions (`SQLException`, `SQLFeatureNotSupportedException`)

## Important Implementation Details

### BigQuery Sessions
- Enabled via `enableSessions=true` connection property
- Required for: transactions, temp tables, multi-statement SQL
- Managed by `SessionManager` class
- Session ID stored in connection, passed to all queries

### Query Execution
- `BQStatement` handles simple queries
- `BQPreparedStatement` handles parameterized queries (uses QueryParameterValue)
- Query timeout enforced by `queryJob.waitFor(timeoutSeconds)`
- Query cancellation via `queryJob.cancel()`

### Result Iteration
- `BQResultSet` wraps BigQuery TableResult
- Forward-only iteration (TYPE_FORWARD_ONLY)
- Pagination via `pageSize` property (default: 10000)
- Storage API optional for large results (>10MB)

### Metadata Performance
- **Critical for IntelliJ:** Metadata caching prevents 90+ second hangs with large projects
- Cache TTL default: 5 minutes
- Lazy loading option: `metadataLazyLoad=true`
- Parallel dataset loading in `BQDatabaseMetaData.getSchemas()`

### Unsupported JDBC Features (BigQuery Limitations)
- Scrollable ResultSets (no `previous()`, `absolute()`)
- Updatable ResultSets (no `updateRow()`, `insertRow()`)
- Batch operations (use BigQuery array syntax instead)
- Traditional transactions without sessions
- CallableStatement (limited UDF support)
- Full Array/Struct JDBC support (returned as JSON)

## Key Files to Understand

1. **BQDriver.java** - Entry point, URL acceptance, driver registration
2. **ConnectionUrlParser.java** - URL parsing logic for both formats
3. **ConnectionProperties.java** - All configuration options and defaults
4. **BQConnection.java** - Connection lifecycle, BigQuery client setup, session management
5. **BQDatabaseMetaData.java** - Metadata implementation with caching (critical for IntelliJ)
6. **TypeMapper.java** - Type conversion logic
7. **UnsupportedOperations.java** - Standard responses for unsupported JDBC features
8. **AbstractBigQueryIntegrationTest.java** - Base for adding integration tests

## Adding New Features

### Adding a New Connection Property
1. Add field to `ConnectionProperties` record
2. Add default value in canonical constructor if needed
3. Update `ConnectionUrlParser` to parse the property
4. Add Simba property mapping if applicable
5. Update `docs/CONNECTION_PROPERTIES.md`

### Adding a New Authentication Method
1. Create new class in `auth/` package
2. Add enum value to `AuthType`
3. Implement `toCredentials()` method
4. Update `ConnectionUrlParser` for URL property parsing
5. Add integration test
6. Update `docs/AUTHENTICATION.md`

### Adding an Integration Test
1. Extend `AbstractBigQueryIntegrationTest`
2. Use helper methods: `createTestTable()`, `insertTestData()`
3. Clean up test data in `@AfterEach`
4. Name test descriptively: `testFeatureDoesExpectedBehavior()`

## CI/CD

### GitHub Actions Workflow
- File: `.github/workflows/build.yml`
- Runs on: push to main/develop, PRs to main
- Steps: checkout, setup Java 21, format check, build, test
- **Critical:** `mvn spotless:check` must pass (CI uses `mvn` directly; locally use `./mvnw`)

### Release Process
- File: `.github/workflows/version-and-release.yml`
- Automatic version bumps and releases
- Generates changelog from commits

## Documentation

All user-facing documentation in `docs/`:
- `QUICKSTART.md` - Getting started guide
- `AUTHENTICATION.md` - All auth methods with examples
- `CONNECTION_PROPERTIES.md` - Complete property reference
- `TYPE_MAPPING.md` - BigQuery ↔ JDBC type conversions
- `COMPATIBILITY.md` - JDBC feature support matrix
- `INTEGRATION_TESTS.md` - Running and writing tests
- `INTELLIJ.md` - IntelliJ IDEA setup and optimization
- `LOGGING.md` - Logging configuration and JAR variants

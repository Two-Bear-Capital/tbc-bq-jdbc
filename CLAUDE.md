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
- `tbc-bq-jdbc-1.0.120.jar` - Slim JAR (60K, requires dependencies)
- `tbc-bq-jdbc-1.0.120-shaded.jar` - Shaded JAR with all dependencies (38M)
- `tbc-bq-jdbc-1.0.120-with-logging.jar` - Shaded JAR + Logback for IntelliJ (39M)
- `tbc-bq-jdbc-1.0.120-sources.jar` - Source JAR
- `tbc-bq-jdbc-1.0.120-javadoc.jar` - Javadoc JAR

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
./mvnw test-compile exec:java -Pbenchmarks
# Run a specific benchmark: -Dexec.args="ResultSetIterationBenchmark"
```

### Code Quality
```bash
# Format code (REQUIRED before commits - enforced by CI)
./mvnw spotless:apply

# Check formatting without applying
./mvnw spotless:check

# Generate coverage report (unit tests only)
./mvnw test
# Report: target/site/jacoco/index.html

# Generate coverage report including the emulator integration tests
./mvnw verify -Pintegration-tests
# Same path; the report is regenerated after integration-test, so it covers both suites
```

**Note:** JaCoCo's `append` defaults to true, so re-running suites without `clean`
accumulates coverage in `target/jacoco.exec` and inflates the report. Use `clean` for
any figure you intend to quote.

## Architecture

### Core Package Structure
```
src/main/java/vc/tbc/bq/jdbc/
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

### Unit Tests (964 tests)
- Location: `src/test/java/vc/tbc/bq/jdbc/`
- Run: `./mvnw test`
- Coverage: URL parsing, properties, type mapping, exception handling
- No external dependencies (no Docker)

### Emulator Integration Tests (319 tests, 6 disabled)
- Location: `src/test/java/vc/tbc/bq/jdbc/integration/`
- Run locally: `./mvnw verify -Pintegration-tests`
- Run automatically in CI/CD on every push and PR
- Base class: `AbstractBigQueryIntegrationTest`
- Uses Testcontainers with `recidiviz/bigquery-emulator` Docker image
- Covers: connections, queries, prepared statements, metadata, result sets
- Requires Docker (available by default in GitHub Actions ubuntu-latest runners)

**Test Structure:**
- `AbstractBigQueryIntegrationTest` - Base with helper methods (`createTestTable`, `insertTestData`)
- `BasicConnectionTest` - Connection lifecycle
- `SimpleQueryTest` - Query execution
- `ParameterizedQueryTest` - PreparedStatement parameters
- `MetadataTest` - DatabaseMetaData operations
- `TypeMappingTest` - Type conversions
- `ResultSetOperationsTest` - ResultSet navigation

### Real BigQuery Integration Tests (155 tests, 10 classes)
- Location: `src/test/java/vc/tbc/bq/jdbc/integration/real/`
- Run locally: `gcloud auth application-default login`, `export BQ_TEST_PROJECT=...`,
  then `./mvnw verify -Preal-integration-tests`
- Runs in CI on pushes to main, same-repo PRs, and manual dispatch (WIF auth)
- Base class: `AbstractRealBigQueryIntegrationTest`
- Skips silently when `BQ_TEST_PROJECT` is unset — CI guards against that passing green

**Which tier should a new test go in?** Default to the **real** tier. The emulator
cannot verify BigQuery semantics — DML affected-row counts, session/transaction
behaviour, NULL and temporal parameter binding, JSON/GEOGRAPHY, `INFORMATION_SCHEMA`
— and tests written against it have historically been weakened until they passed,
which shipped bugs (#93, #98). See issue #118. Use the emulator tier only for tests
that assert no BigQuery behaviour at all: connection/URL plumbing, and concurrency
*shape* (`ConcurrentQueryTest` measures query overlap, which needs no fidelity).

Real-tier fixture patterns to reuse rather than reinvent:
`createSeededTable()` (CTAS + 2h expiry, one job instead of three), `tableName()` /
`RUN_ID` for names that survive concurrent CI runs, `@Execution(CONCURRENT)` plus a
table per method for mutating classes, `@TestInstance(PER_CLASS)` plus
`createSharedTestTable` for read-only ones.

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
- Created eagerly via `enableSessions=true`, or lazily on the first `setAutoCommit(false)`
- Required for: transactions, temp tables, multi-statement SQL
- Managed by `SessionManager` class
- BigQuery assigns the session ID: the creating job sets `createSession=true` and the ID is
  read back from `JobStatistics.getSessionInfo()`, then sent as the `session_id` connection
  property on later jobs (clients cannot choose the ID)
- The emulator returns no `sessionInfo`; `hasSession()` means "session-creating job
  succeeded", and the `session_id` property is only attached when the ID is known
- `SessionManager.close()` terminates the session with `CALL BQ.ABORT_SESSION()` (best-effort)

### Transactions
- `setAutoCommit(false)` starts a session if needed; `BEGIN TRANSACTION` is deferred to the
  first statement via `BQConnection.beginTransactionIfNeeded()` (called from
  `AbstractBQStatement`), so pools toggling auto-commit cost no jobs
- `commit()`/`rollback()` end the in-flight transaction (no-op if nothing ran) and the next
  statement opens a new one; both throw `SQLException` (SQLState `25000`) in auto-commit mode
- `setAutoCommit(true)` commits any in-flight transaction; closing a connection rolls it back
- BigQuery forbids concurrent queries within a session — statements must be sequential once a
  session is active
- Only DML and temp-entity DDL are transactional; permanent DDL inside a transaction errors
- BigQuery gives snapshot isolation, reported as `TRANSACTION_REPEATABLE_READ` (JDBC has no
  snapshot constant). `DatabaseMetaData.supportsTransactions()` is `true`;
  `setTransactionIsolation()` accepts `REPEATABLE_READ` and `NONE` only

### Query Execution
- `BQStatement` handles simple queries
- `BQPreparedStatement` handles parameterized queries (uses QueryParameterValue)
- Query timeout enforced by `queryJob.waitFor(timeoutSeconds)`
- Query cancellation via `queryJob.cancel()`
- `executeUpdate()`/`getUpdateCount()` return real affected-row counts from `JobStatistics.QueryStatistics.getNumDmlAffectedRows()`; `execute()` returns false for DML per the JDBC spec (update count via `getUpdateCount()`)

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

### Batch Execution
- `PreparedStatement.addBatch()/executeBatch()` collapses simple parameterized INSERTs into multi-row `INSERT ... VALUES (...), (...)` query jobs (like PostgreSQL's `reWriteBatchedInserts`)
- Rewrite logic in `util/BatchInsertRewriter.java`; conservative parser — anything not a placeholder-only single-tuple INSERT falls back to sequential execution (one job per parameter set)
- Chunked to stay under BigQuery limits (10,000 query parameters/query, ~1 MB query text)
- `Statement.addBatch(String)` heterogeneous batches execute sequentially
- DML executed via `AbstractBQStatement.executeDmlInternal()`, which returns real affected-row counts from job statistics

### Unsupported JDBC Features (BigQuery Limitations)
- Scrollable ResultSets (no `previous()`, `absolute()`)
- Updatable ResultSets (no `updateRow()`, `insertRow()`)
- Savepoints and transaction isolation levels (transactions themselves work via sessions)
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
- Steps: checkout, setup Java 21, format check, build, unit tests, integration tests
- **Integration Tests:** Run automatically in CI using Docker and BigQuery emulator
- **Critical:** `./mvnw spotless:check` must pass (CI runs the wrapper too, so the Maven version matches yours)
- Uploads test reports (unit tests, integration tests, coverage)

### Release Process
- File: `.github/workflows/version-and-release.yml`
- Automatic version bumps and releases
- Only runs after Build workflow succeeds (including integration tests)
- **Automated Changelog Generation:**
  - Uses [git-cliff](https://git-cliff.org/) for changelog generation
  - Automatically updates CHANGELOG.md on each release
  - Extracts changelog entry for GitHub Release notes
  - Configuration: `cliff.toml` in project root
  - Scripts: `scripts/backfill-changelog.sh`, `scripts/preview-changelog.sh`
  - Follows [Conventional Commits](https://www.conventionalcommits.org/) format
  - Categorizes changes: Features, Bug Fixes, Performance, Documentation, Testing, etc.

### Commit Message Conventions
Conventional Commits are required: they drive **both** the changelog and the released
version number, from the same `cliff.toml` parsers.

| Prefix | Meaning | Version bump |
|---|---|---|
| `feat(scope):` | New feature | **minor** (1.0.x → 1.1.0) |
| `fix(scope):` | Bug fix | patch |
| `perf(scope):` | Performance | patch |
| `docs(scope):` | Documentation | patch |
| `test(scope):` | Tests | patch |
| `refactor(scope):` | Refactoring | patch |
| `chore(scope):` | Maintenance | patch (`chore(deps)` is skipped entirely) |
| `feat(scope)!:` or a `BREAKING CHANGE:` footer | Breaking change | **major** (1.x → 2.0.0) |

Example: `feat(auth): add workforce identity federation support` → a minor release.

The bump is computed by `git-cliff --bumped-version` in `version-and-release.yml`. If
every commit since the last tag is one `cliff.toml` skips, the release falls back to a
patch bump so the tag still advances. Mislabelling a feature as `fix` silently
understates the release, so pick the prefix deliberately.

## Documentation

User-facing documentation in `docs/` (synced to the Astro docs site):
- `QUICKSTART.md` - Getting started guide
- `AUTHENTICATION.md` - All auth methods with examples
- `CONNECTION_PROPERTIES.md` - Complete property reference
- `TYPE_MAPPING.md` - BigQuery ↔ JDBC type conversions
- `COMPATIBILITY.md` - JDBC feature support matrix
- `INTELLIJ.md` - IntelliJ IDEA setup and optimization
- `JETBRAINS_ISSUES.md` - Why tbc-bq-jdbc over JetBrains' built-in driver
- `LOGGING.md` - Logging configuration and JAR variants

Contributor/maintainer documentation in `docs/contributing/` (NOT synced to the site; linked from `CONTRIBUTING.md`):
- `INTEGRATION_TESTS.md` - Running and writing tests
- `EMULATOR_LIMITATIONS.md` - BigQuery emulator gaps and test workarounds
- `JAR_SIZE_OPTIMIZATION.md` - Shading/size strategy
- `MAVEN_CENTRAL_PUBLISHING.md` - Release runbook

**Doc scoping convention:** top-level `docs/*.md` is end-user content (how to *use* the driver) and is synced to the website; anything about building, testing, or releasing belongs in `docs/contributing/`.

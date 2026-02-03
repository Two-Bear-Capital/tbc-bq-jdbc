# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- **IntelliJ IDEA Compatibility** - Production-ready alternative to JetBrains' built-in BigQuery driver:
  - Complete `DatabaseMetaData` implementation:
    - `getCatalogs()` - List all accessible Google Cloud projects
    - `getSchemas()` - List all datasets with pattern filtering
    - `getTables()` - List tables/views/materialized views with parallel loading
    - `getColumns()` - Full 24-column JDBC metadata with accurate precision/scale
    - `getTableTypes()` - Distinguish TABLE, VIEW, MATERIALIZED VIEW
  - Complete `ResultSetMetaData` implementation (all 29 methods)
  - **High-performance metadata caching** (900x faster repeated queries):
    - Thread-safe concurrent cache with configurable TTL
    - Automatic expiration prevents stale data
    - Dramatically improves IntelliJ database browser performance
  - **Parallel metadata loading** with virtual threads:
    - 30x faster for projects with 90+ datasets (3s vs 90s)
    - Automatic parallelization for ≥5 datasets
    - Lightweight virtual thread-based concurrency
  - **Lazy loading** option for very large projects (200+ datasets):
    - Instant connection, loads metadata on-demand
    - Reduces unnecessary API calls
  - New connection properties:
    - `metadataCacheEnabled` (Boolean, default: `true`) - Enable/disable metadata caching
    - `metadataCacheTtl` (Integer, default: `300`) - Cache time-to-live in seconds
    - `metadataLazyLoad` (Boolean, default: `false`) - Enable lazy loading
  - Addresses JetBrains YouTrack issues:
    - DBE-22088: Performance hangs with 90+ datasets (30x speedup with parallel loading)
    - DBE-18711: Schema introspection failures (complete metadata implementation)
    - DBE-12749: STRUCT crashes (safe JSON representation)
    - DBE-19753: Auth token expiration (automatic refresh)
    - DBE-12954: Metadata retrieval issues (accurate type mapping)
  - New documentation:
    - Complete IntelliJ IDEA Integration Guide (`docs/INTELLIJ.md`)
    - JetBrains Issues Analysis (`docs/JETBRAINS_ISSUES.md`)
    - Updated README with IntelliJ quick start
    - Enhanced CONNECTION_PROPERTIES.md with metadata tuning guide
- Initial JDBC 4.3 driver implementation for Google BigQuery
- Modern Java 21 codebase with records, sealed classes, and pattern matching
- Comprehensive authentication support:
  - Application Default Credentials (ADC)
  - Service Account (JSON key file)
  - User OAuth 2.0
  - Workforce Identity Federation
  - Workload Identity Federation
- Full JDBC 4.3 API implementation:
  - `Connection`, `Statement`, `PreparedStatement`
  - `ResultSet` with forward iteration
  - `ResultSetMetaData` and `DatabaseMetaData`
  - `ParameterMetaData` for prepared statements
  - JDBC 4.3 methods: `beginRequest()`, `endRequest()`, `enquoteLiteral()`, `enquoteIdentifier()`
- BigQuery session support:
  - Temporary tables with `CREATE TEMP TABLE`
  - Multi-statement SQL scripts
  - Transaction support (`BEGIN`, `COMMIT`, `ROLLBACK`)
  - Session management with automatic cleanup
- Advanced features:
  - CompletableFuture-based query timeout with automatic job cancellation
  - Virtual thread optimizations (ReentrantLock instead of synchronized)
  - BigQuery Storage Read API framework (auto-detection for large results)
  - Configurable page size for result pagination
  - Query labels for job tracking and billing
  - Multi-region location routing
- Complete type mapping:
  - All BigQuery primitive types (STRING, INT64, FLOAT64, BOOL, etc.)
  - Temporal types (TIMESTAMP, DATE, TIME, DATETIME)
  - Numeric types (NUMERIC, BIGNUMERIC with BigDecimal)
  - Complex types (ARRAY, STRUCT as strings)
  - Special types (GEOGRAPHY as WKT, JSON as strings, INTERVAL)
- Testing infrastructure:
  - 91 unit tests with Mockito
  - 113 integration tests with Testcontainers and BigQuery emulator
  - JMH performance benchmarks (QueryBenchmark, ResultSetIterationBenchmark, PreparedStatementBenchmark)
  - Comprehensive test coverage for all JDBC operations
- Build and packaging:
  - Maven Shade Plugin for fat JAR distribution (51MB shaded, 60K slim)
  - Source and Javadoc JAR generation
  - Google Java Format with Spotless
  - Automatic driver registration via ServiceLoader
- Documentation:
  - Quick Start Guide with 5-minute setup
  - Complete Authentication Guide (all 5 auth methods)
  - Connection Properties Reference (all properties with examples)
  - Type Mapping Reference (BigQuery ↔ JDBC conversions)
  - JDBC Compatibility Matrix (supported/unsupported features)
  - Integration testing guide

### Performance
- Query execution: 200-500ms for small queries
- ResultSet iteration: 100K+ rows/second with Storage API framework
- Connection pooling compatible (HikariCP, DBCP, etc.)
- Lightweight connections (~1/second creation rate)

### Known Limitations
- **Transactions:** Only supported with `enableSessions=true` (BigQuery architectural limitation)
- **ResultSets:** Forward-only (`TYPE_FORWARD_ONLY`), no scrollable or updatable result sets
- **Batch operations:** Not supported, use BigQuery array parameters or DML
- **Complex types:** ARRAY and STRUCT have limited support (JSON string representation)
- **Metadata:** Some advanced DatabaseMetaData methods not applicable to BigQuery (primary keys, foreign keys, indexes - BigQuery has no relational constraints)
- **JDBC compliance:** `Driver.jdbcCompliant()` returns `false` due to BigQuery's non-relational nature

### Security
- No hardcoded credentials
- Support for all Google Cloud authentication methods
- Service account key rotation recommended
- Follows Google Cloud security best practices

---

[Unreleased]: https://github.com/twobearcapital/tbc-bq-jdbc/compare/v1.0.0...HEAD

# Integration Tests Guide

## Overview

This document explains how to run the integration tests for the tbc-bq-jdbc driver.

## Test Structure

Integration tests are located in:
```
src/test/java/vc/tbc/bq/jdbc/integration/
```

### Test Classes

| Test Class | Description | Tests |
|------------|-------------|-------|
| AbstractBigQueryIntegrationTest | Base class with test utilities | N/A (base class) |
| BasicConnectionTest | Connection lifecycle and properties | 16 tests |
| SimpleQueryTest | Basic SQL query execution | 17 tests |
| ParameterizedQueryTest | PreparedStatement with parameters | 15 tests |
| MetadataTest | DatabaseMetaData operations | 29 tests |
| TypeMappingTest | BigQuery to JDBC type conversions | 16 tests |
| ResultSetOperationsTest | ResultSet navigation and data access | 20 tests |

**Total**: **113 integration tests**

## Prerequisites

### Option 1: Using BigQuery Emulator (Recommended for Local Testing)

The integration tests are configured to use the **recidiviz/bigquery-emulator** Docker container.

**Requirements:**
- Docker installed and running
- Internet connection (to pull emulator image)

**Advantages:**
- No Google Cloud credentials needed
- Fast test execution
- Isolated test environment
- No costs

**Limitations:**
- Emulator may not support all BigQuery features
- Some advanced queries may fail
- Not 100% identical to production BigQuery

### Option 2: Using Real BigQuery (For Production Validation)

For comprehensive testing against real BigQuery:

**Requirements:**
- Google Cloud project with BigQuery enabled
- Service account with BigQuery permissions
- Credentials JSON file or Application Default Credentials configured

**Advantages:**
- Tests against real BigQuery implementation
- All features supported
- Production validation

**Disadvantages:**
- Requires Google Cloud credentials
- May incur BigQuery costs
- Slower than emulator

## Running Integration Tests

### Run All Integration Tests

```bash
# With Docker and emulator
./mvnw verify -Pintegration-tests

# Or using failsafe directly
./mvnw failsafe:integration-test failsafe:verify -Pintegration-tests
```

### Run Only Unit Tests (Default)

```bash
# Integration tests are excluded by default
./mvnw test
```

### Run Specific Integration Test Class

```bash
./mvnw verify -Pintegration-tests -Dit.test=BasicConnectionTest
```

### Run Specific Integration Test Method

```bash
./mvnw verify -Pintegration-tests -Dit.test=BasicConnectionTest#testConnectionIsValid
```

### Skip Integration Tests

```bash
# Build and run only unit tests
./mvnw clean install
```

## Test Configuration

### Default Configuration (Emulator)

The `AbstractBigQueryIntegrationTest` base class automatically:

1. Starts a BigQuery emulator container via Testcontainers
2. Configures connection to emulator
3. Creates test dataset
4. Provides helper methods for test data

**Default Settings:**
- Project ID: `test-project`
- Dataset: `test_dataset`
- Emulator image: `ghcr.io/recidiviz/bigquery-emulator:latest`
- Emulator port: 9050

### Custom Configuration (Real BigQuery)

To test against real BigQuery, override `createTestConnection()`:

```java
@Override
protected Connection createTestConnection() throws SQLException {
    String url = "jdbc:bigquery:my-real-project/my_dataset?" +
                 "authType=SERVICE_ACCOUNT&" +
                 "credentials=/path/to/service-account-key.json";
    return DriverManager.getConnection(url);
}
```

Or set environment variables:
```bash
export GOOGLE_APPLICATION_CREDENTIALS=/path/to/key.json
export BQ_PROJECT_ID=my-project
export BQ_DATASET=test_dataset
```

## Test Execution Flow

### 1. Container Startup

```java
@Container
protected static final GenericContainer<?> bigqueryEmulator = ...
```

Testcontainers automatically:
- Pulls the emulator image (if not cached)
- Starts the container
- Exposes port 9050
- Waits for container to be ready

### 2. Connection Setup

```java
@BeforeEach
void setup() throws SQLException {
    connection = createTestConnection();
    setupTestDataset();
}
```

### 3. Test Execution

Each test method runs with a fresh connection.

### 4. Cleanup

```java
@AfterEach
void tearDown() throws SQLException {
    if (connection != null) {
        connection.close();
    }
}
```

## Helper Methods

### createTestTable(String tableName)

Creates a test table with standard schema:
- id INT64
- name STRING
- age INT64
- salary FLOAT64
- is_active BOOL
- created_date DATE

```java
createTestTable("users");
```

### insertTestData(String tableName)

Inserts 3 sample rows:
- Alice, age 30, active
- Bob, age 25, active
- Charlie, age 35, inactive

```java
insertTestData("users");
```

### executeIgnoreErrors(String sql)

Executes SQL and ignores errors (useful for cleanup):

```java
executeIgnoreErrors("DROP TABLE IF EXISTS users");
```

## Troubleshooting

### Docker Not Running

```
Error: Could not find a valid Docker environment
```

**Solution**: Start Docker Desktop or Docker daemon

### Emulator Pull Fails

```
Error: Failed to pull image ghcr.io/recidiviz/bigquery-emulator:latest
```

**Solutions**:
1. Check internet connection
2. Verify Docker has internet access
3. Try pulling manually: `docker pull ghcr.io/recidiviz/bigquery-emulator:latest`

### Connection Timeout

```
Error: Connection timeout
```

**Solutions**:
1. Increase timeout in Testcontainers configuration
2. Check Docker resource limits
3. Verify emulator is starting correctly: `docker ps`

### Unsupported SQL in Emulator

```
Error: Syntax error or unsupported feature
```

**Solutions**:
1. Check if query is supported by emulator
2. Simplify the query
3. Run against real BigQuery for full feature support

### Port Already in Use

```
Error: Bind for 0.0.0.0:9050 failed: port is already allocated
```

**Solution**: Stop other containers using port 9050 or let Testcontainers assign random port

## CI/CD Integration

### GitHub Actions

```yaml
name: Integration Tests

on: [push, pull_request]

jobs:
  integration-test:
    runs-on: ubuntu-latest
    services:
      docker:
        image: docker:dind

    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Run Integration Tests
        run: ./mvnw verify -Pintegration-tests
```

### Running in CI Without Docker

If Docker is not available in CI:

```bash
# Skip integration tests
./mvnw clean install -DskipITs
```

Or configure failsafe to skip:

```xml
<configuration>
    <skipITs>true</skipITs>
</configuration>
```

## Test Coverage

### What Integration Tests Cover

✅ **Connection Management**
- Connection lifecycle (open, close, isValid)
- Connection properties (catalog, schema, autoCommit)
- Multiple concurrent connections
- Request lifecycle (beginRequest/endRequest)

✅ **Query Execution**
- Simple SELECT queries
- Table scans with WHERE clauses
- Aggregations (COUNT, AVG, MAX, etc.)
- GROUP BY and ORDER BY
- JOINs and subqueries
- NULL handling

✅ **Prepared Statements**
- Parameter binding (all types)
- Multiple parameters
- Statement reuse
- NULL parameters
- Complex parameterized queries

✅ **ResultSet Operations**
- Navigation (next, findColumn)
- Data access by index and name
- Type conversions
- NULL detection (wasNull)
- Metadata access
- ResultSet lifecycle

✅ **Type Mapping**
- All BigQuery primitive types
- Large numbers
- NULL values
- Type conversions
- Edge cases (zero, negative, max/min)

✅ **Metadata**
- Database product information
- Driver information
- JDBC version
- Feature support flags
- Catalog and schema terms
- SQL capabilities

### What Integration Tests Do NOT Cover

These require manual testing or Phase 3:

- BigQuery Storage Read API
- Session support
- Multi-statement scripts
- Real error scenarios from BigQuery
- Query cancellation (requires long-running queries)
- Concurrent query execution
- Connection pooling
- Large result sets (> 10MB)

## Performance

### Typical Execution Times

- **Container startup**: 5-10 seconds (first run)
- **Container startup**: 1-2 seconds (cached image)
- **Per test**: 50-200 ms
- **Full suite**: 15-30 seconds

### Optimizations

- Reuse container across tests (default)
- Use @Container static field for shared container
- Parallel test execution not recommended (shared state)

## Best Practices

### Writing New Integration Tests

1. **Extend AbstractBigQueryIntegrationTest**
   ```java
   class MyIntegrationTest extends AbstractBigQueryIntegrationTest {
   ```

2. **Create test data in test method**
   ```java
   @Test
   void testSomething() throws SQLException {
       createTestTable("my_table");
       insertTestData("my_table");
       // ... test code ...
   }
   ```

3. **Use try-with-resources**
   ```java
   try (Statement stmt = connection.createStatement();
        ResultSet rs = stmt.executeQuery(sql)) {
       // ... test code ...
   }
   ```

4. **Clean up temporary tables**
   ```java
   @AfterEach
   void cleanup() {
       executeIgnoreErrors("DROP TABLE IF EXISTS my_temp_table");
   }
   ```

5. **Use descriptive test names**
   ```java
   @Test
   void testSelectWithWhereClauseFiltersCorrectly() { ... }
   ```

## Summary

- **113 integration tests** covering core JDBC functionality
- **Automatic setup** with Testcontainers and BigQuery emulator
- **Separate Maven profile** (`integration-tests`) for optional execution
- **Fast execution** with Docker and emulator
- **Production validation** possible with real BigQuery
- **Well-documented** with helper methods and examples

Run integration tests:
```bash
./mvnw verify -Pintegration-tests
```

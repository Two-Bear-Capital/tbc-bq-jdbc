# Integration Tests Guide

## Overview

This document explains how to run the integration tests for the tbc-bq-jdbc driver.

The driver has two integration test tiers:

| Tier | Profile | Backend | When to Use |
|------|---------|---------|-------------|
| **Emulator** | `integration-tests` | BigQuery emulator via Docker | Local development, all CI PRs |
| **Real BigQuery** | `real-integration-tests` | Actual Google BigQuery | Post-merge CI, production validation |

## Test Structure

```
src/test/java/vc/tbc/bq/jdbc/integration/
├── AbstractBigQueryIntegrationTest.java    # Emulator base class
├── BasicConnectionTest.java
├── SimpleQueryTest.java
├── ParameterizedQueryTest.java
├── MetadataTest.java
├── TypeMappingTest.java
├── ResultSetOperationsTest.java
└── real/
    ├── AbstractRealBigQueryIntegrationTest.java  # Real BQ base class
    ├── RealBasicConnectionTest.java
    ├── RealSimpleQueryTest.java
    ├── RealParameterizedQueryTest.java           # Includes 2 re-enabled NULL tests
    ├── RealMetadataTest.java
    ├── RealTypeMappingTest.java
    └── RealResultSetOperationsTest.java
```

### Emulator Test Classes

| Test Class | Description |
|------------|-------------|
| AbstractBigQueryIntegrationTest | Base class with emulator container + test utilities |
| BasicConnectionTest | Connection lifecycle and properties |
| SimpleQueryTest | Basic SQL query execution |
| ParameterizedQueryTest | PreparedStatement with parameters |
| MetadataTest | DatabaseMetaData operations |
| TypeMappingTest | BigQuery to JDBC type conversions |
| ResultSetOperationsTest | ResultSet navigation and data access |

### Real BigQuery Test Classes

| Test Class | Description |
|------------|-------------|
| AbstractRealBigQueryIntegrationTest | Base class with ADC connection + test utilities |
| RealBasicConnectionTest | Connection lifecycle (mirrors BasicConnectionTest) |
| RealSimpleQueryTest | Simple queries (mirrors SimpleQueryTest) |
| RealParameterizedQueryTest | Parameterized queries; re-enables 2 emulator-disabled tests |
| RealMetadataTest | DatabaseMetaData (mirrors MetadataTest) |
| RealTypeMappingTest | Type mapping (mirrors TypeMappingTest) |
| RealResultSetOperationsTest | ResultSet operations (mirrors ResultSetOperationsTest) |

## Prerequisites

### Emulator Tests (Tier 1)

**Requirements:**
- Docker installed and running
- Internet connection (to pull emulator image)

**Advantages:**
- No Google Cloud credentials needed
- Fast test execution (15–30 seconds total)
- Isolated test environment
- No costs

**Limitations:**
- Not 100% identical to production BigQuery
- 2 NULL parameter tests are disabled due to emulator bugs (re-enabled in real BQ tier)

### Real BigQuery Tests (Tier 2)

**Requirements:**
- GCP project with BigQuery enabled and a test dataset created
- Application Default Credentials (ADC) configured locally, or Workload Identity Federation in CI
- `BQ_TEST_PROJECT` environment variable set

**Advantages:**
- Validates against actual BigQuery behavior
- Catches emulator compatibility gaps
- Re-enables emulator-disabled tests

**Cost:** Effectively zero — 3-row test tables with DROP TABLE cleanup are well within BigQuery's free tier.

## Running Integration Tests

### Emulator Tests (Tier 1)

```bash
# With Docker and emulator
./mvnw verify -Pintegration-tests

# Run specific test class
./mvnw verify -Pintegration-tests -Dit.test=BasicConnectionTest

# Run specific test method
./mvnw verify -Pintegration-tests -Dit.test=BasicConnectionTest#testConnectionIsValid
```

### Real BigQuery Tests (Tier 2)

```bash
# 1. Authenticate
gcloud auth application-default login

# 2. Set required environment variables
export BQ_TEST_PROJECT=my-gcp-project
export BQ_TEST_DATASET=tbc_bq_jdbc_integration_tests   # optional, this is the default

# 3. Run
./mvnw verify -Preal-integration-tests

# Run specific real BQ test class
./mvnw verify -Preal-integration-tests -Dit.test=RealBasicConnectionTest
```

**Note:** If `BQ_TEST_PROJECT` is not set, all real BQ tests are automatically skipped with a clear message — no failures.

### Run Only Unit Tests (Default)

```bash
# Integration tests are excluded by default
./mvnw test
```

### Skip Integration Tests

```bash
./mvnw clean install
```

## Test Configuration

### Emulator Configuration

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

### Real BigQuery Configuration

The `AbstractRealBigQueryIntegrationTest` base class:

1. Reads `BQ_TEST_PROJECT` and `BQ_TEST_DATASET` environment variables
2. Connects using ADC: `jdbc:bigquery:<project>/<dataset>?authType=ADC`
3. Skips all tests if `BQ_TEST_PROJECT` is not set
4. Provides the same helper methods (`createTestTable`, `insertTestData`, etc.)

**Environment Variables:**

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `BQ_TEST_PROJECT` | Yes | — | GCP project ID |
| `BQ_TEST_DATASET` | No | `tbc_bq_jdbc_integration_tests` | BigQuery dataset ID |

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

The build workflow (`.github/workflows/build.yml`) runs two parallel integration test jobs:

**Emulator tests** — Run on every push and PR (no credentials needed):
```yaml
- name: Run integration tests
  run: mvn verify -Pintegration-tests -B
```

**Real BigQuery tests** — Run on push to `main` only (requires WIF secrets):
```yaml
real-integration-tests:
  if: github.event_name == 'push' && github.ref == 'refs/heads/main'
  permissions:
    id-token: write
  steps:
    - uses: google-github-actions/auth@v2
      with:
        workload_identity_provider: ${{ secrets.WIF_PROVIDER }}
        service_account: ${{ secrets.WIF_SERVICE_ACCOUNT }}
    - run: mvn verify -Preal-integration-tests -B
      env:
        BQ_TEST_PROJECT: ${{ vars.BQ_TEST_PROJECT }}
        BQ_TEST_DATASET: ${{ vars.BQ_TEST_DATASET }}
```

### Why Real Tests Only Run on main

GitHub secrets are not available to fork PRs (GitHub's built-in protection for public repos). Running real BQ tests only on push to `main` ensures:
- Fork contributors can still run all emulator tests on their PRs
- Real BQ costs and credentials are only consumed after code is reviewed and merged
- No secrets leak to untrusted code

### Required GitHub Secrets and Variables

After Terraform provisioning, set these in the repository settings:

| Setting | Type | Description |
|---------|------|-------------|
| `WIF_PROVIDER` | Secret | Terraform output `wif_provider` |
| `WIF_SERVICE_ACCOUNT` | Secret | Terraform output `ci_service_account_email` |
| `GCP_TERRAFORM_SA_KEY` | Secret | Bootstrap SA key (used by `terraform.yml` only) |
| `BQ_TEST_PROJECT` | Variable | Terraform output `project_id` |
| `BQ_TEST_DATASET` | Variable | Terraform output `dataset_id` |

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

## Terraform Infrastructure Setup

The real BigQuery test infrastructure is managed via Terraform in the `terraform/` directory and applied exclusively through GitHub Actions — no local `terraform apply` is ever needed.

### One-Time Bootstrap (Manual)

1. Create a GCS bucket for Terraform state:
   ```bash
   gsutil mb -p YOUR_ORG_PROJECT gs://tbc-bq-jdbc-tfstate
   gsutil versioning set on gs://tbc-bq-jdbc-tfstate
   ```

2. Create a bootstrap service account with permissions to create projects and manage state:
   ```bash
   gcloud iam service-accounts create terraform-bootstrap \
     --project=YOUR_ORG_PROJECT \
     --display-name="Terraform Bootstrap SA"

   # Grant required roles (org-level)
   gcloud organizations add-iam-policy-binding YOUR_ORG_ID \
     --member="serviceAccount:terraform-bootstrap@YOUR_ORG_PROJECT.iam.gserviceaccount.com" \
     --role="roles/resourcemanager.projectCreator"

   gcloud organizations add-iam-policy-binding YOUR_ORG_ID \
     --member="serviceAccount:terraform-bootstrap@YOUR_ORG_PROJECT.iam.gserviceaccount.com" \
     --role="roles/billing.user"

   # Grant state bucket access
   gsutil iam ch \
     serviceAccount:terraform-bootstrap@YOUR_ORG_PROJECT.iam.gserviceaccount.com:roles/storage.objectAdmin \
     gs://tbc-bq-jdbc-tfstate
   ```

3. Download the bootstrap SA key and save it as `GCP_TERRAFORM_SA_KEY` GitHub secret.

4. Copy `terraform/terraform.tfvars.example` to `terraform/terraform.tfvars`, fill in values, and **do not commit** it (it is gitignored).

5. Merge the `terraform/` code to `main` — GitHub Actions' `terraform.yml` workflow applies infrastructure automatically.

6. After successful Terraform apply, copy the outputs to GitHub:
   - `wif_provider` → `WIF_PROVIDER` secret
   - `ci_service_account_email` → `WIF_SERVICE_ACCOUNT` secret
   - `project_id` → `BQ_TEST_PROJECT` variable
   - `dataset_id` → `BQ_TEST_DATASET` variable

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

**Tier 1 — Emulator tests (fast, no credentials):**
- Automatic setup with Testcontainers and BigQuery emulator
- Run on every push and PR in CI
- `./mvnw verify -Pintegration-tests`

**Tier 2 — Real BigQuery tests (full fidelity, requires credentials):**
- Validates against actual BigQuery behavior
- Re-enables 2 NULL parameter tests that are disabled in the emulator tier
- Run on push to `main` in CI via Workload Identity Federation
- `./mvnw verify -Preal-integration-tests` (requires `BQ_TEST_PROJECT` env var)

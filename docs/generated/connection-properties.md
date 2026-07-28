<!--
  GENERATED FILE — DO NOT EDIT BY HAND.
  Produced by vc.tbc.bq.jdbc.docgen.DocGen from the driver source of truth.
  Regenerate with: ./mvnw test-compile exec:java -Pdocs
-->

# Connection Properties

The following properties can be supplied as URL query parameters (traditional format) or `java.util.Properties` entries. This table is generated from the driver's own `Driver.getPropertyInfo()`, so it always matches what the driver actually accepts.

There are **26** connection properties.

| Property | Default | Allowed values | Description |
| --- | --- | --- | --- |
| `authType` | `ADC` | `ADC`, `SERVICE_ACCOUNT`, `USER_OAUTH`, `WORKFORCE`, `WORKLOAD` | Authentication method: ADC (Application Default Credentials), SERVICE_ACCOUNT, USER_OAUTH, WORKFORCE, WORKLOAD |
| `credentials` | _(none)_ | any | Path to service account JSON key file (required for SERVICE_ACCOUNT auth) |
| `credentialConfigFile` | _(none)_ | any | Path to external account credential config file (required for WORKFORCE or WORKLOAD auth) |
| `clientId` | _(none)_ | any | OAuth 2.0 client ID (required for USER_OAUTH auth) |
| `clientSecret` | _(none)_ | any | OAuth 2.0 client secret (required for USER_OAUTH auth) |
| `refreshToken` | _(none)_ | any | OAuth 2.0 refresh token (required for USER_OAUTH auth) |
| `location` | _(none)_ | any | BigQuery processing location (e.g., US, EU, us-central1). Leave blank to use the dataset's location. |
| `timeout` | `300` | any | Query execution timeout in seconds |
| `connectionTimeout` | `30` | any | Connection establishment timeout in seconds |
| `retryCount` | `3` | any | Number of retry attempts for transient errors |
| `pageSize` | `50000` | any | Number of rows to fetch per page when iterating large result sets |
| `metadataCacheEnabled` | `true` | `true`, `false` | Cache schema introspection results to speed up IntelliJ IDEA's database tree |
| `metadataCacheTtl` | `300` | any | How long (seconds) to keep metadata in the cache before re-fetching |
| `metadataCacheMaxRows` | `50000` | any | Ceiling on total rows held in the metadata cache; oldest entries are evicted above it. Set to 0 for no limit |
| `metadataLazyLoad` | `false` | `true`, `false` | Skip loading all columns on connect; IntelliJ loads them on-demand as you expand tables (faster initial connect for large projects) |
| `useStorageApi` | `false` | `auto`, `true`, `false` | BigQuery Storage Read API mode for large result sets: much faster on big results, but needs the JVM started with --add-opens=java.base/java.nio=ALL-UNNAMED and falls back to the standard path when unavailable |
| `enableSessions` | `false` | `true`, `false` | Enable BigQuery sessions to support transactions and temporary tables |
| `jobCreationMode` | `REQUIRED` | `REQUIRED`, `OPTIONAL` | REQUIRED always creates a query job; OPTIONAL may skip it for small queries |
| `useLegacySql` | `false` | `true`, `false` | Use BigQuery legacy SQL dialect instead of standard SQL (GoogleSQL) |
| `enableQueryCostEstimation` | `false` | `true`, `false` | Run a dry-run before each query and DML statement to estimate cost; estimates are attached as SQLWarnings. Sequential batches are not estimated (one job per entry already) |
| `maxResults` | _(none)_ | any | Maximum number of query result rows to return (blank = unlimited) |
| `maxBillingBytes` | _(none)_ | any | Maximum bytes billed per query; queries exceeding this limit are rejected (blank = unlimited) |
| `labels` | _(none)_ | any | Comma-separated BigQuery job labels in key=value format (e.g., env=prod,team=data) |
| `datasetId` | _(none)_ | any | Default dataset name used for unqualified table references in queries |
| `datasetProjectId` | _(none)_ | any | Project ID for the default dataset when it differs from the connection project |
| `nativeComplexTypes` | `false` | `true`, `false` | Return ARRAY and STRUCT as native JDBC Array/Struct objects instead of JSON strings |

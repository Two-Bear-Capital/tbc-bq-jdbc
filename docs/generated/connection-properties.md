<!--
  GENERATED FILE — DO NOT EDIT BY HAND.
  Produced by vc.tbc.bq.jdbc.docgen.DocGen from the driver source of truth.
  Regenerate with: ./mvnw test-compile exec:java -Pdocs
-->

# Connection Properties

The following properties can be supplied as URL query parameters (traditional format) or `java.util.Properties` entries. This table is generated from the driver's own `Driver.getPropertyInfo()`, so it always matches what the driver actually accepts.

There are **30** connection properties.

| Property | Default | Allowed values | Description |
| --- | --- | --- | --- |
| `authType` | `ADC` | `ADC`, `SERVICE_ACCOUNT`, `USER_OAUTH`, `WORKFORCE`, `WORKLOAD` | Authentication method: ADC (Application Default Credentials), SERVICE_ACCOUNT, USER_OAUTH, WORKFORCE, WORKLOAD |
| `credentials` | _(none)_ | any | Path to service account JSON key file (required for SERVICE_ACCOUNT auth) |
| `credentialConfigFile` | _(none)_ | any | Path to external account credential config file (required for WORKFORCE or WORKLOAD auth) |
| `clientId` | _(none)_ | any | OAuth 2.0 client ID (required for USER_OAUTH auth) |
| `clientSecret` | _(none)_ | any | OAuth 2.0 client secret (required for USER_OAUTH auth) |
| `refreshToken` | _(none)_ | any | OAuth 2.0 refresh token (required for USER_OAUTH auth) |
| `host` | _(none)_ | any | Alternative BigQuery endpoint, e.g. a proxy or Private Service Connect address. Defaults to https when no scheme is given; blank uses Google's endpoints |
| `port` | _(none)_ | any | Port for the alternative endpoint set by 'host' |
| `location` | _(none)_ | any | BigQuery processing location (e.g., US, EU, us-central1). Leave blank to use the dataset's location. |
| `timeout` | `300` | any | Query execution timeout in seconds |
| `connectionTimeout` | `30` | any | Timeout in seconds for establishing the HTTP connection (not query duration) |
| `retryCount` | `6` | any | Total attempts per BigQuery API call, including the first |
| `pageSize` | `50000` | any | Number of rows to fetch per page when iterating large result sets |
| `metadataCacheEnabled` | `true` | `true`, `false` | Cache schema introspection results to speed up IntelliJ IDEA's database tree |
| `metadataCacheTtl` | `300` | any | How long (seconds) to keep metadata in the cache before re-fetching |
| `metadataCacheMaxRows` | `50000` | any | Ceiling on total rows held in the metadata cache; oldest entries are evicted above it. Set to 0 for no limit |
| `metadataLazyLoad` | `false` | `true`, `false` | Skip loading all columns on connect; IntelliJ loads them on-demand as you expand tables (faster initial connect for large projects) |
| `collapseShardedTables` | `false` | `true`, `false` | Report date-sharded tables (events_20260101, events_20260102, ...) as a single events_* entry in getTables(), instead of one row per shard. getColumns() answers for the wildcard name using the newest shard's schema. Off by default: sharding is a naming convention, so a table legitimately ending in a date would otherwise disappear from listings |
| `metadataIncludeDescriptions` | `true` | `true`, `false` | Read table descriptions into getTables()' REMARKS column. Costs one INFORMATION_SCHEMA query per dataset scanned, cached for metadataCacheTtl; set false to skip it on projects with very many datasets |
| `useStorageApi` | `false` | `auto`, `true`, `false` | BigQuery Storage Read API mode for large result sets: much faster on big results, but needs the JVM started with --add-opens=java.base/java.nio=ALL-UNNAMED and falls back to the standard path when unavailable |
| `enableSessions` | `false` | `true`, `false` | Enable BigQuery sessions to support transactions and temporary tables |
| `useLegacySql` | `false` | `true`, `false` | Use BigQuery legacy SQL dialect instead of standard SQL (GoogleSQL) |
| `enableQueryCostEstimation` | `false` | `true`, `false` | Run a dry-run before each query and DML statement to estimate cost; estimates are attached as SQLWarnings and readable as typed values via BQStatement.getCostEstimates(). Sequential batches are not estimated (one job per entry already) |
| `queryPricePerTiB` | _(none)_ | any | Price of one tebibyte of billed query data, used to turn cost estimates into money (blank = report bytes only). Any currency; BigQuery's on-demand rate is 6.25 USD/TiB, but editions and negotiated contracts differ |
| `maxResults` | _(none)_ | any | Maximum number of query result rows to return (blank = unlimited) |
| `maxBillingBytes` | _(none)_ | any | Maximum bytes billed per query; queries exceeding this limit are rejected (blank = unlimited) |
| `labels` | _(none)_ | any | Comma-separated BigQuery job labels in key=value format (e.g., env=prod,team=data) |
| `datasetId` | _(none)_ | any | Default dataset name used for unqualified table references in queries |
| `datasetProjectId` | _(none)_ | any | Project ID for the default dataset when it differs from the connection project |
| `nativeComplexTypes` | `false` | `true`, `false` | Make getObject() return native JDBC Array/Struct for ARRAY and STRUCT columns instead of JSON strings |

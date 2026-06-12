# Why tbc-bq-jdbc?

tbc-bq-jdbc is a modern, from-scratch JDBC 4.3 driver for Google BigQuery. It was built to
resolve long-standing, documented problems in JetBrains' built-in BigQuery driver that make the
IDE slow or unreliable on real-world projects.

## At a glance

| JetBrains issue | Symptom | How tbc-bq-jdbc resolves it |
|-----------------|---------|------------------------------|
| [DBE-22088](https://youtrack.jetbrains.com/issue/DBE-22088) | Database browser hangs on projects with 90+ datasets | Parallel metadata loading + caching + optional lazy loading — ~30× faster (90s → 2–3s) |
| [DBE-18711](https://youtrack.jetbrains.com/issue/DBE-18711) | Schema introspection fails or returns incomplete results | Complete `DatabaseMetaData` implementation with robust error handling |
| [DBE-12749](https://youtrack.jetbrains.com/issue/DBE-12749) | IDE crashes or garbles output on STRUCT columns | STRUCT/ARRAY returned as safe JSON strings (native JDBC types opt-in) |
| [DBE-19753](https://youtrack.jetbrains.com/issue/DBE-19753) | Auth drops after ~1 hour, requiring manual reconnect | Automatic token refresh via the Google Cloud SDK credential layer |
| [DBE-12954](https://youtrack.jetbrains.com/issue/DBE-12954) | Wrong/incomplete column metadata (types, precision, nullability) | Accurate type mapping with precision/scale and table-type distinction |

## The issues, in detail

### DBE-22088 — Performance hangs with large projects

The built-in driver introspects metadata with sequential, uncached API calls, so a project with
90 datasets can take ~90 seconds (or never finish). tbc-bq-jdbc loads datasets concurrently on
virtual threads, caches metadata (5-minute TTL by default), and offers optional lazy loading for
very large projects — bringing a 90-dataset project down to 2–3 seconds. See
[Performance Tuning](INTELLIJ.md#performance-tuning) for the connection properties.

### DBE-18711 — Schema introspection failures

Users report missing tables, wrong column types, or an empty browser despite valid data.
tbc-bq-jdbc implements the full JDBC `DatabaseMetaData` surface (`getTables`, `getColumns`,
`getSchemas`, …) with consistent results and graceful error handling, so the IntelliJ database
tree reflects what's actually in BigQuery.

### DBE-12749 — STRUCT type handling crashes

Tables with STRUCT (nested/record) columns can crash the built-in result viewer. tbc-bq-jdbc
returns STRUCT and ARRAY values as safe JSON strings by default — readable in any tool and
crash-free. If you need native `java.sql.Struct`/`java.sql.Array` objects, opt in with
`nativeComplexTypes=true` (see [Type Mapping](TYPE_MAPPING.md)).

### DBE-19753 — Authentication token expiration

Long IntelliJ sessions lose authentication after ~1 hour, forcing manual reconnects.
tbc-bq-jdbc delegates credential handling to the Google Cloud SDK, which refreshes access tokens
automatically — long-running sessions keep working without intervention. See the
[Authentication guide](AUTHENTICATION.md).

### DBE-12954 — Metadata retrieval issues

The built-in driver can report wrong column types, omit numeric precision/scale, mislabel
nullability, or fail to distinguish tables from views. tbc-bq-jdbc maps BigQuery types to JDBC
types accurately — including precision/scale for `NUMERIC`/`BIGNUMERIC` and correct table-type
reporting — so code completion and query builders get correct information.

## How this is verified

These behaviors are covered by the driver's automated test suite (unit + emulator and real-BigQuery
integration tests) and JMH benchmarks. Representative benchmark targets:

- List 90 datasets: < 3 seconds
- List 100 tables in a dataset: < 2 seconds
- List 50 columns in a table: < 1 second
- Cached metadata queries: < 10 ms

## See also

- [IntelliJ Integration Guide](INTELLIJ.md) — setup, configuration, and performance tuning
- [Compatibility Matrix](COMPATIBILITY.md) — JDBC feature support and BigQuery limitations
- [Connection Properties](CONNECTION_PROPERTIES.md) · [Authentication](AUTHENTICATION.md) · [Type Mapping](TYPE_MAPPING.md)

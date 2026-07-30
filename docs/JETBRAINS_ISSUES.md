# Why tbc-bq-jdbc?

tbc-bq-jdbc is a modern, from-scratch JDBC 4.3 driver for Google BigQuery. It exists
because several long-standing gaps in JetBrains' BigQuery support make the IDE slow or
awkward on real projects.

Each entry below links a JetBrains YouTrack issue that is **open at the time of writing**
and describes what this driver does about it. Where the driver only sidesteps a problem
rather than solving it, that is stated.

## At a glance

| JetBrains issue | Votes | What users hit | What tbc-bq-jdbc does |
|-----------------|:-----:|----------------|------------------------|
| [DBE-12808](https://youtrack.jetbrains.com/issue/DBE-12808) | 32 | No way to see how much data a query will process before running it | `enableQueryCostEstimation=true` dry-runs each statement and attaches the estimate as a `SQLWarning`; `estimateCost()` prices one statement on demand |
| [DBE-12749](https://youtrack.jetbrains.com/issue/DBE-12749) | 14 | STRUCT columns arrive as one opaque string with no field names | Renders STRUCT as readable JSON by default, or real `java.sql.Struct` objects with `nativeComplexTypes=true` |
| [DBE-17806](https://youtrack.jetbrains.com/issue/DBE-17806) | 13 | Nested RECORD values land in the wrong columns when fields are NULL | Never flattens a struct across columns, so the misalignment cannot occur |
| [DBE-14390](https://youtrack.jetbrains.com/issue/DBE-14390) | 5 | Google user authentication re-prompts for a token constantly | Credentials are cached JVM-wide and refreshed by the Google auth library |
| [DBE-16410](https://youtrack.jetbrains.com/issue/DBE-16410) | 5 | `CREATE TEMPORARY TABLE` fails — "requires a script or session" | Real BigQuery sessions, so temp tables and multi-statement work persist |
| [DBE-18711](https://youtrack.jetbrains.com/issue/DBE-18711) | 3 | Schema introspection returns nothing, or fails outright | A complete `DatabaseMetaData` implementation, cached and loaded in parallel |

## The issues, in detail

### DBE-12808 — Estimating what a query will cost

BigQuery bills by bytes processed, and the IDE gives you no way to see that number before
you run something. Set `enableQueryCostEstimation=true` and the driver performs a dry run
before each statement, attaching the result as a `SQLWarning` on the statement:

```java
try (Statement stmt = conn.createStatement();
     ResultSet rs = stmt.executeQuery(sql)) {
    for (SQLWarning w = stmt.getWarnings(); w != null; w = w.getNextWarning()) {
        System.out.println(w.getMessage()); // bytes processed and estimated cost
    }
}
```

Outside the IDE, read the estimate as a value rather than as a sentence:

```java
var bq = stmt.unwrap(AbstractBQStatement.class);

// after executing with enableQueryCostEstimation=true
List<QueryCostEstimate> estimates = bq.getCostEstimates();

// or price one statement on demand, without running it and without the property
QueryCostEstimate estimate = bq.estimateCost("SELECT * FROM events");
```

Worth knowing before enabling it: each statement costs an extra dry-run job, which is why
`estimateCost` exists for the statements you actually want priced. Dry runs themselves are
free. The estimate reports bytes always and money only when `queryPricePerTiB` is set —
BigQuery's on-demand rate is 6.25 USD/TiB, but editions and negotiated contracts differ,
so the driver does not assume one. See
[Connection properties](CONNECTION_PROPERTIES.md#query-cost-estimation).

### DBE-12749 — STRUCT columns

The complaint on this issue is that struct columns come back as a long string with no
field names. By default this driver renders STRUCT and ARRAY as JSON, so you at least get
`{"id":1,"name":"Alice"}` rather than an opaque blob, and the IDE's result grid stays
stable. Set `nativeComplexTypes=true` to get `java.sql.Struct` and `java.sql.Array`
objects instead — see [Type Mapping](TYPE_MAPPING.md#complex-types).

This is an improvement, not a resolution: the JSON is a rendering, and the native
`Struct` does not carry field names. Projecting the fields you need in SQL
(`SELECT person.id, person.name`) remains the best experience.

### DBE-17806 — Nested records misaligned when fields are NULL

Reported as struct values appearing under the wrong columns once NULLs are involved. The
misalignment comes from flattening a nested record into a row of columns. This driver
never does that — a STRUCT is one column, rendered whole, with nulls preserved — so the
failure has nowhere to occur. The flattening belongs to the IDE, so this is avoidance
rather than a fix.

### DBE-14390 — Repeated authentication prompts

Long IDE sessions re-prompt for credentials. tbc-bq-jdbc caches credentials in a
JVM-wide cache shared by every connection, and delegates refresh to Google's auth
library, so a token refresh does not surface as a prompt. See the
[Authentication guide](AUTHENTICATION.md).

Cached credentials are reused for an hour, so a rotated service account key is picked up
without restarting the IDE. Set `-Dtbc.bq.jdbc.credentials.ttl.seconds` to change that
window, or `0` to cache for the life of the process.

### DBE-16410 — Temp tables need a session

BigQuery rejects `CREATE TEMP TABLE` outside a session or script. The driver creates a
real BigQuery session — eagerly with `enableSessions=true`, or on demand the first time
you call `setAutoCommit(false)` — and attaches the session to every subsequent job, so
temp tables, temp functions and multi-statement work persist across statements. See
[Compatibility → Transactions](COMPATIBILITY.md#transactions).

### DBE-18711 — Introspection returns nothing

Reported against the bundled driver as an empty schema list and broken completion, with a
`400` from the introspection query. tbc-bq-jdbc replaces that driver entirely and
implements the full `DatabaseMetaData` surface, reading from `INFORMATION_SCHEMA` with
per-dataset queries fanned out across virtual threads and cached.

The original ticket has no reproducible root cause attached, so treat this as "a different
implementation" rather than a fix for a specific defect.

## Where this driver does not help

Not everything on the tracker is a driver problem. SQL parsing, code completion,
formatting, and the shape of the result grid are all IDE-side, and no JDBC driver can
change them. Requests for a hierarchical viewer for nested data, or for editing nested
values in place, fall into that category.

The introspection gaps this section used to list have since been closed — sharded tables
collapse to one `events_*` entry, external tables, snapshots and clones report their own
types, `INFORMATION_SCHEMA` is browsable, STRUCT subfields appear as columns, and view and
routine source SQL reaches `REMARKS`. See the
[compatibility matrix](COMPATIBILITY.md#databasemetadata) for what each one does and which
of them are opt-in.

What remains genuinely undone in this driver, as a decision rather than a BigQuery limit:

- `CallableStatement` — BigQuery has stored procedures and `OUT` parameters, so this is
  buildable; it is
  [tracked and deliberately not started](https://github.com/Two-Bear-Capital/tbc-bq-jdbc/issues/199)

Anything else is on the
[issue list](https://github.com/Two-Bear-Capital/tbc-bq-jdbc/issues?q=is%3Aissue+is%3Aopen+label%3Aenhancement).

## See Also

- [IntelliJ Integration Guide](INTELLIJ.md) — setup, configuration, and performance tuning
- [Compatibility Matrix](COMPATIBILITY.md) — JDBC feature support and BigQuery limitations
- [Connection Properties](CONNECTION_PROPERTIES.md) · [Authentication](AUTHENTICATION.md) · [Type Mapping](TYPE_MAPPING.md)

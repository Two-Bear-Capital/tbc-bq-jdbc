# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

**Note:** This project uses the Maven Wrapper (`./mvnw`) to ensure consistent Maven version across environments. No local Maven installation required.

For running tests, benchmarks and coverage, see the `testing` skill
(`.claude/skills/testing/SKILL.md`).

## Architecture

#### Metadata Caching
- `MetadataCache` provides TTL-based caching with concurrent access
- **Cache is shared statically across all connections** to the same project (persists across connection open/close cycles)
- Cache instances keyed by `projectId:ttlSeconds` for isolation
- Cache is NOT cleared on connection close - only expires based on TTL
- This design is critical for IntelliJ IDEA which frequently reopens connections
- `BQDatabaseMetaData` reuses single instance per connection (fixes IntelliJ slowness)
- Configurable via `metadataCacheEnabled`, `metadataCacheTtl`, `metadataLazyLoad`
- Parallel `INFORMATION_SCHEMA` fan-out in `getTables`/`getColumns`/`getProcedures`, capped at 16 in flight (`getSchemas()` itself is a sequential dataset list)
- Static methods: `clearAllSharedCaches()` and `getSharedCacheCount()` for testing/debugging

#### Type Mapping Strategy
- ARRAY/STRUCT returned as JSON strings (prevents IntelliJ crashes)

## Code Style and Conventions

### Required Before Commits
```bash
./mvnw spotless:apply
```

CI runs `./mvnw spotless:check` and it must pass.

## Important Implementation Details

### BigQuery Sessions
- Created eagerly via `enableSessions=true`, or lazily on the first `setAutoCommit(false)`
- Required for: transactions, temp tables, multi-statement SQL
- Managed by `SessionManager` class
- BigQuery assigns the session ID: the creating job sets `createSession=true` and the ID is
  read back from `JobStatistics.getSessionInfo()`, then sent as the `session_id` connection
  property on later jobs (clients cannot choose the ID)
- `hasSession()` means "the session-creating job succeeded"; the `session_id`
  property is only attached once BigQuery reports the ID
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
- Query timeout enforced by `queryJob.waitFor(timeoutSeconds)`
- Query cancellation via `queryJob.cancel()`
- `executeUpdate()`/`getUpdateCount()` return real affected-row counts from `JobStatistics.QueryStatistics.getNumDmlAffectedRows()`; `execute()` returns false for DML per the JDBC spec (update count via `getUpdateCount()`)

### Storage Read API path
- Opt-in via `useStorageApi=auto|true`; default stays `false`
- 11.7x faster than the REST result path on a 1M-row result (#152); a raw-Arrow
  spike reached 19.6x, and the gap is the FieldValue re-encoding below
- **Rows are re-encoded into `FieldValueList` rather than read straight from Arrow.**
  That looks wasteful and is deliberate: every getter, coercion rule and error
  path is then inherited from `BQResultSet`, so the two paths cannot drift. The
  contract is that `ArrowRowConverter` reproduces BigQuery's REST encoding
  exactly — `StorageApiParityTest` compares both paths cell by cell to prove it
- `BQResultSet.fetchNextRow()` is the only seam; do not override `next()`
- **`getString` canonicalises FLOAT64 and TIMESTAMP** in `FieldValueConverter`,
  rendering both from the parsed value rather than the text BigQuery delivered.
  Without this the two paths disagree: REST prints these through a `double`
  (`-0.66666666666666663`, and a TIMESTAMP 0.1us short of the stored value) while
  Arrow carries the exact value. This is why `StorageApiParityTest` can assert byte
  equality with no exemptions — do not reintroduce one, fix the encoding instead
- Covers scalars, ARRAY, STRUCT and INTERVAL. Only `RANGE` still falls back to REST
- **ARRAY/STRUCT recurse against the BigQuery schema, never `vector.getObject()`.** Arrow
  hands back a `JsonStringArrayList`/`JsonStringHashMap` whose `toString()` looks like
  JSON; using it would render every nested scalar Arrow's way instead of this class's,
  and would take struct member order from a map rather than the schema
- INTERVAL arrives as an `IntervalMonthDayNanoVector` → `PeriodDuration`. Its three parts
  are signed independently (`-1-2 3 -4:5:6`), and REST pads fractional seconds to a whole
  number of milliseconds — 700000µs is `.700`, 10µs is `.000010`
- `isSupported` recurses: a `RANGE` nested inside `ARRAY<STRUCT<...>>` must disqualify the
  whole result, or it fails mid-ResultSet after rows have reached the caller
- **Needs `--add-opens=java.base/java.nio=ALL-UNNAMED`** or Arrow cannot allocate.
  `ArrowSupport` probes for this once per JVM (it must actually *allocate* — merely
  constructing a `RootAllocator` succeeds without the flag) and the driver falls
  back rather than failing. Failsafe sets the flag; a missing flag in CI would make
  the parity tests silently compare REST with itself

### Metadata and key constraints
See `src/main/java/vc/tbc/bq/jdbc/metadata/CLAUDE.md`.

### Observability
- `metrics/DriverMetrics` holds JVM-global `LongAdder` counters; `MetricsSnapshot` is
  the immutable reading, with `minus()` for windowed deltas
- Scope is the JVM, not the connection, because what it measures already is: the
  metadata cache and credentials cache are both static and shared
- Instrumented at four choke points only — `AbstractBQStatement.runJob()` (every query
  and DML job passes through it), `MetadataCache.get()`, `SessionManager`
  init/close, `CredentialsCache.forAuthType()`. Add counters there, not at callers
- On by default; `-Dtbc.bq.jdbc.metrics.enabled=false` or `setEnabled(false)` to stop
- No JMX MBean deliberately — a static accessor has no registration lifecycle to get
  wrong and forwards to Micrometer/Prometheus in a few lines
- `DriverMetrics.reset()` is global; tests using it must reset in both `@BeforeEach`
  and `@AfterEach` or they will corrupt unrelated tests in the same JVM

### Parameter binding
- **Every parameter goes through `BQPreparedStatement.setParameter(int, ParameterFactory)`.**
  It takes a factory, not a value, because `QueryParameterValue`'s factories validate
  client-side and throw `IllegalArgumentException` while *building* the value — taking a
  finished value could not wrap anything (#227)
- That wrapping is structural on purpose: a new setter cannot forget, because there is no
  other way to store a parameter. Array and struct elements are built inside the factory
  for the same reason
- `ParameterConverter` wraps its *own* conversions already, so a test asserting only
  `SQLException` proves nothing — the real-tier tests assert on the "Cannot bind parameter"
  wording, and were verified to fail without the fix

### Batch Execution
- `PreparedStatement.addBatch()/executeBatch()` collapses simple parameterized INSERTs into multi-row `INSERT ... VALUES (...), (...)` query jobs (like PostgreSQL's `reWriteBatchedInserts`)
- Rewrite logic in `util/BatchInsertRewriter.java`; conservative parser — anything not a placeholder-only single-tuple INSERT falls back to sequential execution (one job per parameter set)
- Chunked to stay under BigQuery limits (10,000 query parameters/query, ~1 MB query text)
- `Statement.addBatch(String)` heterogeneous batches execute sequentially
- DML executed via `AbstractBQStatement.executeDmlInternal()`, which returns real affected-row counts from job statistics
- `batchLoadThreshold` (opt-in) sends large batches through **one load job** instead, streamed
  as NDJSON into a `TableDataWriteChannel` — no GCS staging. `util/BatchLoadEncoder` owns the
  target parsing and the JSON
- **Every gate on that path exists because the DML path stays correct where the load path
  would not**, so each falls back rather than throwing: below the threshold, in a
  transaction or session (a load job cannot join one — rows would survive a rollback), no
  explicit column list, or a parameter type with no settled JSON form
- The channel's job **does not exist until the channel is closed**; `getJob()` returns null
  before that
- Update counts are 1 per row only when the load's aggregate output matches the batch,
  otherwise `SUCCESS_NO_INFO` — never fabricated from the batch size

### Error classification
- `AbstractBQStatement.sqlStateFor(BigQueryException)` classifies; the `BigQueryError`
  overload it delegates to handles everything BigQuery gave a reason for
- **BigQuery's reason always wins.** Only when there is none — the signature of a credential
  that could not be minted or refreshed, since the request never reached BigQuery — does the
  cause chain get a say, and a 401/403 there means `28000`
- That asymmetry is the whole point: a 403 from an auth endpoint is a rejected credential
  (`28000`, re-authenticate), a 403 from BigQuery is a missing grant (`42501`, surface it).
  Turning the second into the first sends a pool round a retry loop over a working credential
- `ServiceErrorDetail` owns the chain walk for both this and the message enrichment (#242),
  so there is one place that understands the shape the Google client libraries produce

### Multi-statement script results
- A script is one job with a **child job per executed statement**; the parent carries only
  the **last** statement's result. `executeQuery()` used to return that, so a script looked
  like it had answered the first statement while answering the last (#191)
- `util/ScriptResults` enumerates the children and is the cursor `getMoreResults()` walks
- **Ordering is by creation time.** The jobs API lists children newest-first, and the `_N`
  suffix on a child job id is undocumented
- **A statement produces a ResultSet iff its type is `SELECT`.** Both obvious alternatives
  are wrong and were tried against the service: a *listed* child carries no result schema
  at all, so "has columns" makes every SELECT an update count; and a *fetched* DDL/DML
  result carries the **destination table's** schema, so `CREATE TEMP TABLE t(id INT64)` and
  `INSERT INTO t` both look like one-column ResultSets
- Non-SELECT steps are not fetched at all, which also saves an API call each
- A DDL step reports update count **0**, never -1 — -1 means "no more results" and would
  end the walk at the first `CREATE`
- The cursor is cleared by `discardPreviousResult()`, so a new execution cannot resume a
  half-walked script

### Unsupported JDBC Features (BigQuery Limitations)
- Scrollable ResultSets (no `previous()`, `absolute()`)
- Updatable ResultSets (no `updateRow()`, `insertRow()`)
- Savepoints and transaction isolation levels (transactions themselves work via sessions)
- CallableStatement (limited UDF support)
- Array/Struct are returned as JSON by default (`nativeComplexTypes=true` for the JDBC
  types). Both are fully writable: `setArray`/`createArrayOf`, and `createStruct` or a
  `Map<String, Object>` through `setObject`. **BigQuery struct parameters are named**,
  so `createStruct` needs a type name that names its fields —
  `STRUCT<id INT64, name STRING>`, the same form `getObject()` reports — which is what
  lets a struct that was read be bound straight back (`util/StructTypeNames`)

## Adding New Features

See the `adding-features` skill (`.claude/skills/adding-features/SKILL.md`) for the
connection-property, authentication-method and integration-test checklists.

## CI/CD

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

**`@since` names the version a thing ships in, not the version in `pom.xml`.** The pom
holds the *last released* version and the bump happens after merge, so a `feat` added
while the pom says 3.0.5 is `@since 3.1.0` and a `fix` is `@since 3.0.6`. Copying the pom
is the easy mistake. Recent additions follow this — `ArrowSupport` is `@since 2.4.0` with
the pom at 2.3.0, `KeyConstraints` 2.2.0 at 2.1.3 — though a few older files were tagged
with whatever the pom said at the time.

The next version is only a *prediction* while other PRs are in flight: anything that
merges first bumps past you. Take the best guess from your own commit prefix, and do not
hold up a PR over it — `git tag --contains <sha>` gives the real answer afterwards if it
turns out to matter.

**Milestones track the release a change lands in, not a theme.** `3.1.0` is additive work,
`4.0.0` is breaking or result-changing. They are organisational only — `git-cliff` derives
the version from commit prefixes and never reads them.

The bump is computed by `git-cliff --bumped-version` in `version-and-release.yml`. If
every commit since the last tag is one `cliff.toml` skips, the release falls back to a
patch bump so the tag still advances. Mislabelling a feature as `fix` silently
understates the release, so pick the prefix deliberately.

### Milestone work goes on a release branch

**A release fires on every merge to `main`, so a milestone's PRs target
`release/<version>` and that branch merges to `main` once.** Eight PRs straight to
`main` would cut eight releases. Base feature PRs on the release branch, squash-merge
them into it, then open one PR to `main` — and merge **that** one with a merge commit,
never a squash, or `git-cliff` sees a single commit and the changelog and version bump
both collapse. Established for 3.1.0 (PRs #212–#219, merged as #221).

Read `docs/contributing/RELEASE_BRANCHES.md` before starting a milestone; it covers the
rest, including why `build.yml` must keep its `release/**` triggers and why `Closes #NNN`
does nothing until the work reaches `main`.

## Documentation
**Doc scoping convention:** top-level `docs/*.md` is end-user content (how to *use* the driver) and is synced to the website; anything about building, testing, or releasing belongs in `docs/contributing/`.

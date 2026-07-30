# Metadata package

### Metadata Performance
- **Critical for IntelliJ:** metadata caching keeps introspection off the hot path
- Cache TTL default: 5 minutes
- Lazy loading option: `metadataLazyLoad=true` — note `getTables`/`getColumns` return an
  **empty** result when called with no schema or table pattern
- Parallel loading lives in `getTables`/`getColumns`/`getProcedures` and the key-constraint
  scans, **not** in `getSchemas()`, which is a single sequential dataset listing

### Jobless metadata reads (`metadataJobCreationOptional`, #265)
- On by default. Every metadata read goes through `bigquery.query(...)`, never
  `bigquery.create(JobInfo)` — so they already take the `jobs.query` path, which is the
  only one with a `jobCreationMode` field. `metadataQuery(String)` is the single seam
  that builds these configs; **add a new metadata query through it, not by calling
  `QueryJobConfiguration.newBuilder` again**
- Scoped to metadata deliberately. The statement path's `cancel()`, Storage Read API
  destination table, script child jobs, DML update counts and `runJob()` metrics all
  need a job; none of them exists here, so this needed no eligibility gate and no
  fallback logic. Doing the same for user statements is a separate, much larger change
- **BigQuery owns the fallback.** `JOB_CREATION_OPTIONAL` is a request: the service
  answers small results inline and creates a job anyway above a size threshold of its
  own (measured between 3,065 and 5,526 `INFORMATION_SCHEMA.COLUMNS` rows). So a
  dataset too large to qualify behaves exactly as before, and the driver has nothing to
  detect
- Measured 19–37% off the p50 of a qualifying metadata read (121–305ms), against the
  integration dataset and public datasets spanning three orders of magnitude
- The client library would NPE on a jobless result that pages — `queryRpc` does
  `JobId.fromPb(results.getJobReference())` unguarded — but that combination could not be
  produced, including with `maxResults=1` forcing a page on a 10-row result. BigQuery
  creates a job whenever it must page, and `ConnectionImpl` depends on that too
- **Not in `metadataShapeKey()`, and must not be.** It does not shape results — the rows
  are identical either way — so two connections disagreeing about it can share cache
  entries safely. `RealMetadataJobCreationTest` is what holds that claim, and it disables
  the cache precisely so both modes actually reach BigQuery
- A jobless query still appears in `INFORMATION_SCHEMA.JOBS`, with `job_id` set to the
  query id, so this costs no audit visibility

### getTables REMARKS
- Neither the description nor a view's defining SQL is in the `tables.list` response
  `getTables()` lists with — only `tables.get` carries either, which would be one API
  call per table. Both come from one `INFORMATION_SCHEMA` read per dataset instead
- **One query serves both**, joining `TABLES` to `TABLE_OPTIONS`. Keeping them separate
  would add a second query to every dataset that already paid for the view read, and the
  descriptions apply to every table where the definitions apply only to views
- Precedence: description, then a view's DDL, then empty. `undescribedViews` is computed
  from the listing *before* the read, so the definition pass fills only rows the
  description pass left blank
- `TABLE_OPTIONS.option_value` is **the SQL that would set the option**, not the value:
  `"hello \"there\""`. `util/SqlStringLiterals.unquote` decodes it
- `metadataIncludeDescriptions=false` falls back to the narrower view-only read

### STRUCT subfields (`includeStructFields`, #186)
- Off by default: it changes the row count of every `getColumns()` call — a tool building an
  INSERT column list would treat a field as a column — and costs a second
  `INFORMATION_SCHEMA` query (`COLUMN_FIELD_PATHS`) per dataset
- **Paths below an ARRAY are excluded, and that is correctness not tidiness.** A struct path
  is a usable reference (`SELECT person.name` works); an array's is not (`SELECT items.n`
  fails, needs `UNNEST`). `isSelectablePath` requires every ancestor to be a `STRUCT`
- `RealStructFieldMetadataTest` selects every path the driver reports — the only check that
  separates a correct path list from a plausible one
- **Field rows must survive their parent being filtered out.** `getColumns(…, "person.%")`
  matches `person.name` but not `person`, so `rows` can be empty and there is no parent to
  attach to. Two cuts of this answered that query with nothing; the final sweep keys off the
  table name in the map key instead of a parent row
- Ordinals are renumbered per table as rows are spliced, so `ORDINAL_POSITION` stays
  contiguous
- Nullability is reported as nullable — `COLUMN_FIELD_PATHS` has no `is_nullable`

### Table types (#187)
- `TABLE`, `VIEW`, `MATERIALIZED VIEW`, `EXTERNAL`, `SNAPSHOT`, `CLONE`. The last three are
  a driver convention — JDBC standardises only the first two — and use BigQuery's own
  `INFORMATION_SCHEMA.TABLES.table_type` vocabulary
- All but `CLONE` come free from `TableDefinition` on the listing. **BigQuery's `tables.list`
  reports a clone as an ordinary table**; only `INFORMATION_SCHEMA` tells them apart, so the
  refinement rides on the description read `fillInRemarks` already does. With
  `metadataIncludeDescriptions=false` a clone is reported as `TABLE`, which is documented
- **The type filter must run after that refinement**, not in the listing loop. Filtering
  first let a clone through a `types={"TABLE"}` request and *then* relabelled it — the
  caller got a `CLONE` row it had excluded — while `types={"CLONE"}` matched nothing at all.
  `filterByType` runs between `fillInRemarks` and `collapseShards`

### Sharded tables (`collapseShardedTables`)
- Off by default, and must stay that way: sharding is a naming convention BigQuery never
  declares, so collapsing on by default would make a table legitimately ending in a date
  vanish from listings
- `ShardedTables` owns the recognition — greedy prefix, so `events_daily_20260101` groups
  under `events_daily`; the eight digits are range-checked so `metrics_12345678` is not a
  shard; a set needs **two** members
- The collapsed name `events_*` is BigQuery's wildcard syntax, not a display convention —
  it can be queried as-is
- **`getColumns` reports the newest shard's schema** under the wildcard name. Shards drift
  and a wildcard query can select a recently added column, so the oldest would under-report
- Filtering is via `matchesTableNameFilter`, which additionally lets `events_*` match its
  shards — `*` is not a JDBC pattern character, so the plain matcher never would. An exact
  shard name still matches itself, which is what keeps single-shard lookups working
- `getPseudoColumns` adds `_TABLE_SUFFIX` per collapsed entry via a **separate** dataset
  read. The ingestion-time query filters `is_system_defined = 'YES'`; a wildcard set need
  not be partitioned at all, so folding them together would mean loosening that query

### Key Constraints (PK/FK)
- BigQuery supports `PRIMARY KEY`/`FOREIGN KEY ... NOT ENFORCED` and never validates
  them; `getPrimaryKeys`/`getImportedKeys`/`getExportedKeys`/`getCrossReference` report
  them from `INFORMATION_SCHEMA.TABLE_CONSTRAINTS`, `KEY_COLUMN_USAGE` and
  `CONSTRAINT_COLUMN_USAGE` (#84)
- **Composite foreign keys pair via `position_in_unique_constraint`, never row order.**
  `CONSTRAINT_COLUMN_USAGE` carries no ordinal, so reading it positionally silently
  transposes `FOREIGN KEY (b, a) REFERENCES parent(p2, p1)` into a wrong join. BigQuery
  requires a FK to reference exactly the parent's PK columns, so that value indexes the
  parent's `KEY_COLUMN_USAGE.ordinal_position`
- Cached as **one snapshot per dataset**, shared by all four methods — keyed per call
  it would be one query per table introspected. `KeyConstraints` owns the query,
  assembly and row shaping; `BQDatabaseMetaData` owns dataset scanning and caching
- `schema`/`table` arguments are matched **exactly**, not as LIKE patterns: JDBC
  specifies them as names, and `_` is a literal in the underscore-heavy names BigQuery
  encourages
- `getExportedKeys` must scan every dataset — a FK is recorded only in the dataset of
  the referencing table. Cross-*project* FKs are not discoverable

### INFORMATION_SCHEMA browsing (`includeInformationSchema`)
- On by default, unlike `collapseShardedTables`: these views are real and queryable, so
  this fills in entries rather than removing or renaming them. It also costs no query —
  `InformationSchemaViews` is a static list
- **The two scopes are disjoint and that is the whole design.** BigQuery resolves each
  view at exactly one of them; a view listed at the wrong scope produces a well-formed
  `getTables` row that fails when clicked. `InformationSchemaViewsTest` holds disjointness,
  and `RealInformationSchemaMetadataTest` queries every advertised name — the only check
  that can tell a correct entry from a plausible one
- Project scope is a 3-part name and maps straight onto catalog/schema/table. Dataset
  scope is **4 parts**, one more than JDBC has, so the last two ride together in the table
  name (`INFORMATION_SCHEMA.TABLES`). BigQuery accepts that however a tool quotes it
- Reported as `SYSTEM TABLE`, and `getTableTypes()` advertises that type **only while the
  property is on** — a type nothing is reported under reads as "no such tables"
- **`getColumns` resolves each view with a dry run**, not a hard-coded column list: these
  views have ~25 columns each and Google adds to them. A dry run creates no job and bills
  nothing. Memoised per view name, *not* per dataset — every dataset's `TABLES` has the
  same columns, so keying by dataset would multiply the cost for one answer
- Region-qualified views are deliberately absent: they need a region the connection may
  not know, and the ones unique to that scope scan the org's whole job history

### Metadata cache keys carry the connection's result-shaping settings
- The cache is shared **statically** across connections to a project, but the call-site
  keys describe only the arguments. Two connections disagreeing about
  `includeInformationSchema`, `collapseShardedTables` or `metadataIncludeDescriptions`
  were served each other's rows — whichever connected first decided for the whole TTL
- Fixed by prefixing every key in `getCachedOrExecute`, the one seam all of them pass
  through. **A new result-shaping property must be added to `metadataShapeKey()`**
- Only built when a cache exists; with caching off the string is never read

# Metadata package

### Metadata Performance
- **Critical for IntelliJ:** metadata caching keeps introspection off the hot path
- Cache TTL default: 5 minutes
- Lazy loading option: `metadataLazyLoad=true` — note `getTables`/`getColumns` return an
  **empty** result when called with no schema or table pattern
- Parallel loading lives in `getTables`/`getColumns`/`getProcedures` and the key-constraint
  scans, **not** in `getSchemas()`, which is a single sequential dataset listing

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

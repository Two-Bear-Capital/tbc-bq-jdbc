---
name: adding-features
description: Step-by-step checklists for extending the driver — adding a connection property, an authentication method, a DatabaseMetaData method, or an integration test. Use when adding any of these to tbc-bq-jdbc, so no wiring step or doc update is missed.
---

# Adding features to tbc-bq-jdbc

Each of these touches several files that are easy to miss individually — the
parser, the docs, and the tests are all separate from the type that defines the
feature. Work the list in order.

## Adding a New Connection Property

1. Add field to `ConnectionProperties` record
2. Add default value in canonical constructor if needed
3. Update `ConnectionUrlParser` to parse the property
4. Add Simba property mapping if applicable
5. Update `docs/CONNECTION_PROPERTIES.md`

The property table in `docs/generated/connection-properties.md` is generated from
`Driver.getPropertyInfo()` — regenerate it with `./mvnw test-compile exec:java -Pdocs`
rather than editing it by hand, and note CI runs a drift check with
`-Dexec.args="--check"`.

## Adding a New Authentication Method

1. Create new class in `auth/` package
2. Add enum value to `AuthType`
3. Implement `toCredentials()` method
4. Update `ConnectionUrlParser` for URL property parsing
5. Add integration test
6. Update `docs/AUTHENTICATION.md`

## Adding a DatabaseMetaData Method

Most read one `INFORMATION_SCHEMA` view per dataset. **Use
`BQDatabaseMetaData.queryInformationSchema(...)` rather than writing the query
loop again** — it already handles rejecting unsafe identifiers, running the
query, mapping rows (return null from the mapper to filter one out), and
letting an unreadable dataset contribute no rows instead of failing the whole
call.

1. Add the row shape to `MetadataColumns` — JDBC fixes the column names, order
   and types for each method, so this is not a free choice
2. Read the data through `queryInformationSchema`, with a row mapper
3. Cache through `getCachedOrExecute` — do not add a second caching mechanism
4. Scan datasets in parallel via `executeInParallel` when the method is
   project-wide; concurrency is already capped
5. Add unit tests; use `TestResultSets` for result fixtures
6. Update the method's row in `docs/COMPATIBILITY.md` — several methods are
   listed there as unsupported and will need moving

Two nearby reads deliberately do **not** use the helper and should not be folded
in: `queryColumnsViaInformationSchema` propagates so its caller can fall back to
the `getTable()` API, and `queryConstraintsForDataset` returns `Optional` so a
failed read stays distinguishable from a dataset that declares no constraints.
Their error handling is the part that differs.

## Test Support

Two helpers under `src/test/java/vc/tbc/bq/jdbc/testsupport/`. Prefer them to
hand-rolling; both exist because the hand-rolled versions drifted.

- `TestConnectionProperties.props()` — a builder for the 23-component
  `ConnectionProperties` record. **Do not** use it in tests that assert what the
  canonical constructor itself does (defaults, validation, defensive copying);
  those are supposed to break when the record changes
- `TestResultSets.singleColumn(name, type, rawValue)` — a one-column, one-row
  `BQResultSet`, cursor not advanced. `tableResult(schema, rows)` when the
  `TableResult` itself is wanted. Its stubs are `lenient()` on purpose; strict
  stubs fail the caller that does not exercise all of them

## Adding an Integration Test

1. Extend `AbstractRealBigQueryIntegrationTest`
2. Use helper methods: `createSeededTable()`, `tableName()` / `RUN_ID` for names
   that survive concurrent CI runs
3. Clean up test data in `@AfterEach`
4. Name test descriptively: `testFeatureDoesExpectedBehavior()`

**Name the file `*Test.java`, not `*IT.java`.** Failsafe includes
`**/integration/real/**/*Test.java`, so an `IT` suffix is silently excluded from
the suite and only runs when named explicitly with `-Dit.test=`.

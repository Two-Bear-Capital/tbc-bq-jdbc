---
name: adding-features
description: Step-by-step checklists for extending the driver — adding a connection property, adding an authentication method, or adding an integration test. Use when adding any of these to tbc-bq-jdbc, so no wiring step or doc update is missed.
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

## Adding an Integration Test

1. Extend `AbstractRealBigQueryIntegrationTest`
2. Use helper methods: `createSeededTable()`, `tableName()` / `RUN_ID` for names
   that survive concurrent CI runs
3. Clean up test data in `@AfterEach`
4. Name test descriptively: `testFeatureDoesExpectedBehavior()`

**Name the file `*Test.java`, not `*IT.java`.** Failsafe includes
`**/integration/real/**/*Test.java`, so an `IT` suffix is silently excluded from
the suite and only runs when named explicitly with `-Dit.test=`.

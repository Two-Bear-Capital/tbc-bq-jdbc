---
name: testing
description: How to run and write tests for tbc-bq-jdbc — the unit/integration/scale tiers and what gates each, the benchmark and thread-scaling harnesses, coverage and test-count reporting hazards, and the real-tier fixture patterns to reuse. Use when running any test suite, adding a test, or reading a coverage or benchmark figure.
---

# Testing tbc-bq-jdbc

## Running tests

**Read Maven's own `Tests run:` summary line, and use `clean`.** Summing
`target/surefire-reports/*.txt` counts stale files from previous runs and will report a
green total while CI fails — the reports accumulate unless `clean` removes them.

```bash
# Unit tests only (fast, no Docker needed)
./mvnw test

# Integration tests (requires ADC credentials)
./mvnw verify -Preal-integration-tests

# One integration test class, without re-running the whole unit suite first
./mvnw verify -Preal-integration-tests -Dit.test=RealMetadataTest \
  -Dtest=NoSuchUnitTest -Dsurefire.failIfNoSpecifiedTests=false

# Skip tests during build
./mvnw clean install -DskipTests

# Run benchmarks (requires real BigQuery connection)
export BENCHMARK_JDBC_URL="jdbc:bigquery:my-project/my_dataset?authType=ADC"
./mvnw test-compile exec:exec -Pbenchmarks
# Run a specific benchmark: -Dbenchmark.args="ResultSetIterationBenchmark"

# Thread-scaling sweep (1,2,4,8,16 threads) with a Markdown report — ~25-30 min
./mvnw test-compile exec:exec -Pbenchmark-scaling
# Verify the harness without the wait: -Dbenchmark.args="--quick"

# Opt-in scale and load tests (slow, creates BigQuery datasets)
export BQ_TEST_PROJECT=bigquery-jdbc-driver-test BQ_SCALE_TESTS=true
./mvnw verify -Pscale-tests
```

**`-Dit.test=` alone does not skip the unit tests.** Failsafe runs after surefire in
the same lifecycle, so a targeted integration run still pays for the full unit suite —
and if a unit test is red, the build stops before failsafe ever starts, which reads as
"my integration test did not run" rather than as a unit failure. Filtering surefire to a
name that matches nothing is what skips it; `-Dsurefire.failIfNoSpecifiedTests=false`
stops that filter from being an error in itself.

**There is no `-DskipUTs`.** It is not a property this build defines, so it is accepted
and silently ignored, and the unit suite runs anyway. `-DskipTests` skips *both* suites,
which is not what you want when the point is to run one integration test.

**Benchmarks use `exec:exec`, not `exec:java`.** JMH's `@Fork` rebuilds the forked
JVM's classpath from `java.class.path`; `exec:java` runs in-process behind its own
classloader and the fork dies with `ClassNotFoundException` on `ForkedMain`. The JMH
annotation processor is also named explicitly in `annotationProcessorPaths` — javac
stopped discovering processors from the classpath in JDK 23, so without it no
`META-INF/BenchmarkList` is generated and JMH exits at startup.

**JaCoCo's `append` defaults to true**, so re-running suites without `clean`
accumulates coverage in `target/jacoco.exec` and inflates the report. Use `clean` for
any figure you intend to quote. Report path: `target/site/jacoco/index.html`; running
`./mvnw verify -Preal-integration-tests` regenerates it after `integration-test`, so it
covers both suites.

## Real BigQuery integration tests

- **`terraform/` is the source of truth for that project and its dataset**
  (`terraform/variables.tf`: `project_id`, `dataset_id`). It provisions the project,
  the `tbc_bq_jdbc_integration_tests` dataset and the CI Workload Identity
  Federation setup. Do not guess the project from `gcloud config`
- Run locally: `gcloud auth application-default login`, then
  `export BQ_TEST_PROJECT=bigquery-jdbc-driver-test`
- Runs in CI on pushes to main, same-repo PRs, and manual dispatch (WIF auth)
- Skips silently when `BQ_TEST_PROJECT` is unset — CI guards against that passing green

**There is one integration tier, and it runs against real BigQuery.** The emulator
tier was removed (#118): it could not verify BigQuery semantics, and tests written
against it were weakened until they passed, which shipped bugs — #93, #98, #121,
#123 and #129 all hid behind an "emulator limitation" comment, four of which turned
out to describe BigQuery or the driver rather than the emulator.

## Scale and load tests (opt-in, never in CI)

- Gated three ways — separate profile, separate package the default failsafe includes
  do not match, and the `BQ_SCALE_TESTS` variable. A gate that lives only in build
  config is one broadened include pattern away from firing.
- Covers: a million-row result set (throughput + heap does not grow with rows read),
  20 datasets / 300 tables (the #99 fan-out cap, which had never actually been
  reached), metadata cache steady state across TTL boundaries, and HikariCP pooled
  load (the #98 scenario).
- The `-Xmx512m` on this profile is load-bearing: it turns accidental full
  materialisation of a large result into an `OutOfMemoryError`. Do not raise it to
  make a failure go away.
- Fixtures are generated per run and dropped in `@AfterAll`; datasets have no
  BigQuery-side expiry, so a killed run strands `scale_<runId>_*` datasets.

## Fixture patterns to reuse rather than reinvent

`createSeededTable()` (CTAS + 2h expiry, one job instead of three), `tableName()` /
`RUN_ID` for names that survive concurrent CI runs, `@Execution(CONCURRENT)` plus a
table per method for mutating classes, `@TestInstance(PER_CLASS)` plus
`createSharedTestTable` for read-only ones.

See `docs/contributing/PERFORMANCE.md` for the whole instrumentation story (JFR, the
thread-scaling sweep and its baseline, scale tests, tuning variables).

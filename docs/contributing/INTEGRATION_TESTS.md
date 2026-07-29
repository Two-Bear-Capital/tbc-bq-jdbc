# Integration Tests Guide

How to run and write the integration tests.

## Overview

There is **one** integration tier and it runs against real BigQuery, via the
`real-integration-tests` Maven profile. It lives in
`src/test/java/vc/tbc/bq/jdbc/integration/real/`.

There used to be a second tier running against the `bigquery-emulator` Docker
image. It was removed in issue #118, and the reason matters for how you write
tests here: the emulator diverges from the service, tests written against it were
weakened until they passed, and the weakened tests then hid real defects. Issues
#93, #98, #121, #123 and #129 all sat behind a `(emulator limitation)` comment —
and four of those "limitations" turned out to describe BigQuery or the driver
rather than the emulator. Assertions that tolerate two answers are how that
happens, so don't write them.

Unit tests (`src/test/java/vc/tbc/bq/jdbc/`, run by `./mvnw test`) need no
credentials and cover parsing, type mapping, conversion errors and the timezone
arithmetic. Prefer a unit test whenever a behaviour can be asserted without a
BigQuery round-trip.

## Test structure

```
src/test/java/vc/tbc/bq/jdbc/integration/real/
├── AbstractRealBigQueryIntegrationTest.java   # base: ADC connection + fixtures
└── Real*Test.java                             # 18 classes
```

Run `ls src/test/java/vc/tbc/bq/jdbc/integration/real/` for the current
inventory — an explicit list here goes stale, as this document repeatedly has.

## Fixture helpers on the base class

| Helper | Use |
|---|---|
| `createSeededTable(...)` | `CREATE TABLE AS SELECT` + 2h expiry — one BigQuery job instead of three |
| `createTestTable(name)` | Empty fixture table, also expiring after 2h |
| `tableName(base)` | Appends `RUN_ID` so concurrent CI runs cannot collide |
| `createSharedTestTable` / `dropSharedTestTable` | Class-scoped fixture on its own connection, for `@BeforeAll` |
| `createTestRoutine` | UDF; tracked and dropped on JVM exit, since routines cannot expire |

Every test connection carries `maxBillingBytes`, so a query that accidentally
scans a large table fails instead of billing for it.

## Prerequisites

- Java 21+
- Application Default Credentials for a project you can create tables in
- `BQ_TEST_PROJECT` set (and optionally `BQ_TEST_DATASET`). The project and dataset
  are defined in [`terraform/variables.tf`](../../terraform/variables.tf) —
  `bigquery-jdbc-driver-test` and `tbc_bq_jdbc_integration_tests`. That is the
  source of truth; do not infer the project from `gcloud config`

No Docker, and no container image.

### Impersonation tests

`RealImpersonationTest` needs two more variables, and skips without them:

| Variable | From |
| --- | --- |
| `BQ_TEST_IMPERSONATE_SA` | Terraform output `impersonation_target_service_account` |
| `BQ_TEST_IMPERSONATE_DELEGATE` | Terraform output `impersonation_delegate_service_account` |

They gate independently — set only the first and the direct case runs while the
delegation-chain test skips.

The two service accounts are created by `terraform/main.tf`. To run these under
your own ADC you also need `roles/iam.serviceAccountTokenCreator` on both, which
means naming yourself in the `impersonation_source_principals` Terraform
variable — CI's service account already has the grant. Without it the tests fail
on the first statement with `Error requesting access token (HTTP 403: Permission
'iam.serviceAccounts.getAccessToken' denied …)`.

## Running

```bash
# 1. Authenticate
gcloud auth application-default login

# 2. Point at a project. Maintainers use the one Terraform provisions; anyone
#    without access to it can use any project where they can create datasets.
export BQ_TEST_PROJECT=bigquery-jdbc-driver-test
export BQ_TEST_DATASET=tbc_bq_jdbc_integration_tests   # optional, this is the default

# 3. Run
./mvnw verify -Preal-integration-tests

# A single class or method
./mvnw verify -Preal-integration-tests -Dit.test=RealMetadataTest
./mvnw verify -Preal-integration-tests -Dit.test='RealMetadataTest#testGetTables'
```

Unit tests only (the default — integration tests are excluded from `test`):

```bash
./mvnw test
```

### If `BQ_TEST_PROJECT` is unset

Every class is annotated `@EnabledIfEnvironmentVariable(named = "BQ_TEST_PROJECT", ...)`,
so the suite **skips silently** rather than failing. That is convenient locally
and dangerous in CI, which is why the workflow guards it — see below.

## Cost and isolation

Fixtures are three rows, so cost is effectively zero: BigQuery bills a 10 MB
minimum per query and the free tier is 1 TiB/month. Roughly 320 queries per run.

Isolation comes from `RUN_ID`, an 8-character suffix derived from
`System.nanoTime()` and appended to every table name, so concurrent CI runs
sharing one dataset do not collide. Tables carry a 2-hour
`expiration_timestamp`, so anything stranded by a cancelled run removes itself.

Test classes run concurrently at parallelism 8; methods within a class run
sequentially, because BigQuery forbids concurrent queries inside a session.
Classes that mutate their fixture take a table per method and opt into
`@Execution(CONCURRENT)`.

## CI

`.github/workflows/build.yml` has two jobs, both required status checks:

- **Build and Unit Tests** — compile, unit suite, spotless, SpotBugs, PMD,
  coverage, packaging. No credentials.
- **Real BigQuery Integration Tests** — the integration suite, authenticating
  with Workload Identity Federation.

```yaml
real-integration-tests:
  if: |
    (github.event_name == 'push' && github.ref == 'refs/heads/main') ||
    (github.event_name == 'pull_request' && github.event.pull_request.head.repo.full_name == github.repository) ||
    github.event_name == 'workflow_dispatch' ||
    (github.event_name == 'workflow_run' && github.event.workflow_run.conclusion == 'success')
  permissions:
    id-token: write
  steps:
    - uses: google-github-actions/auth@v3
      with:
        workload_identity_provider: ${{ secrets.WIF_PROVIDER }}
        service_account: ${{ secrets.WIF_SERVICE_ACCOUNT }}
    # Goal list, not `verify` — skips packaging, javadoc and the unit suite the
    # Build job already ran.
    - run: ./mvnw test-compile failsafe:integration-test failsafe:verify -Preal-integration-tests -B
      env:
        BQ_TEST_PROJECT: ${{ secrets.BQ_TEST_PROJECT }}
        BQ_TEST_DATASET: ${{ secrets.BQ_TEST_DATASET }}
        BQ_TEST_IMPERSONATE_SA: ${{ secrets.BQ_TEST_IMPERSONATE_SA }}
        BQ_TEST_IMPERSONATE_DELEGATE: ${{ secrets.BQ_TEST_IMPERSONATE_DELEGATE }}
```

Same-repo PRs — including Dependabot's — run the suite, so the gate is on the PR
that introduces a change rather than only after merge.

### Fork PRs cannot satisfy it

GitHub withholds secrets from fork PRs, so the `head.repo.full_name` check keeps
the job from starting and reporting a confusing auth failure. Because the job is
also a required check, **a fork PR cannot currently be merged without an admin
bypass.** No fork PR has ever been opened on this repository; if that changes,
this is the decision to revisit.

### The suite must not pass without testing anything

Because an unset `BQ_TEST_PROJECT` silently disables every test, two steps guard
it: a pre-flight check that the secret is non-empty, and a post-run check that
`failsafe-summary.xml` reports a non-zero completed count. Keep both if you touch
that job.

### Required GitHub secrets

| Secret | Source |
|---|---|
| `WIF_PROVIDER` | Terraform output `wif_provider` |
| `WIF_SERVICE_ACCOUNT` | Terraform output `ci_service_account_email` |
| `BQ_TEST_PROJECT` | Terraform output `project_id` |
| `BQ_TEST_DATASET` | Terraform output `dataset_id` |
| `GCP_TERRAFORM_SA_KEY` | Bootstrap SA key, used by `terraform.yml` only |

## Infrastructure

`terraform/` provisions a dedicated project (`bigquery-jdbc-driver-test`), one
dataset, a CI service account scoped to `roles/bigquery.dataEditor` on that
dataset plus project-level `roles/bigquery.jobUser`, and the Workload Identity
pool. The CI account deliberately **cannot** create or drop datasets — only
tables and routines inside the one dataset.

## Writing a new integration test

1. Extend `AbstractRealBigQueryIntegrationTest`.
2. Name tables with `tableName("...")` so concurrent runs cannot collide.
3. Use `createSeededTable` for a populated fixture, `createSharedTestTable` from
   `@BeforeAll` for a read-only one.
4. If the test mutates its fixture, give it a table per method and add
   `@Execution(ExecutionMode.CONCURRENT)` to the class.
5. **Assert one answer.** No `assertTrue(a || b)` where `a` is right and `b` is
   what a broken driver returns; no try/catch that logs and passes. If a
   behaviour genuinely cannot be asserted yet, `@Disabled` it with an issue
   reference so the gap is visible and re-enabling it is the fix's acceptance
   test.
6. If you can assert it without BigQuery, write a unit test instead.

## Troubleshooting

**Tests all skip.** `BQ_TEST_PROJECT` is unset. That is the designed behaviour.

**`Access Denied` / `403`.** Your ADC principal needs `bigquery.jobUser` on the
project and `bigquery.dataEditor` on the dataset.

**`Reauthentication failed` locally.** ADC tokens expire; re-run
`gcloud auth application-default login`.

**A table already exists.** Fixtures are `CREATE OR REPLACE` and `RUN_ID`-scoped,
so this usually means a hardcoded name crept in. Use `tableName(...)`.

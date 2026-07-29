# Performance Instrumentation

How to find out what the driver actually does at scale and under load, and how to
keep it honest.

## Why this exists

Every performance number this project had before came from reading CI test timings.

That is how [#98][98] was found: queries were dispatched to
`ForkJoinPool.commonPool()`, so concurrent queries serialized and a `SELECT 1` took
over 30 seconds with eight callers. It did not surface as a performance report. It
surfaced as a *test failure* while enabling parallel test execution, and only after
two rounds of widening a timeout that was treating the symptom. A JDBC driver's
concurrency collapsing under a connection pool should be caught by an instrument,
not by luck.

The follow-up audit ([#99][99]) found six more issues by reading code — a regex
compiled per metadata row, unbounded metadata fan-out, a cache that never evicted,
credentials rebuilt per connection, two data races, and a file-descriptor leak.
Reading code found them. Nothing measured them, and nothing would have noticed if
they came back in a different form.

Note what does *not* cover this: SpotBugs runs at max effort with `check` bound to
`verify` and reports **zero** findings on this codebase. It caught neither #98 nor
any of the six. Static analysis is worth keeping, but it does not cover this class
of defect and we should stop expecting it to.

## The instruments

| Instrument | Runs | Answers |
|---|---|---|
| JFR recording | Automatically, with the real integration suite | Where did the time go? |
| Thread-scaling benchmarks | `workflow_dispatch` only | Does throughput scale with threads, or flatten? |
| Scale tests | Opt-in, manual | Does it hold up at a million rows and hundreds of tables? |
| Pooled load test | Opt-in, manual | Does it work the way it is actually deployed? |
| Driver metrics | Always on, in-process | What is *my* workload doing? (see [Observability](../OBSERVABILITY.md)) |

None of these fails a build on a performance regression. BigQuery latency varies
enough between runs, regions and times of day that a threshold would flake more
often than it would catch anything — and a flaky assertion gets widened until it
means nothing, which is exactly how [#93][93] stayed hidden behind a tolerant
assertion. These report numbers a human reads.

The exception is the scale and load tests, which *do* assert. Those assertions are
set to catch a collapse, not to police variance: the pooled load test requires 2x
scaling across 8 threads where perfect would be 8x, because under #98 eight threads
were *slower* than one.

---

## 1. JFR recordings

Java Flight Recorder is enabled for the real BigQuery integration suite. Nothing to
turn on — every run of

```bash
./mvnw verify -Preal-integration-tests
```

writes `target/real-integration-tests-<pid>.jfr`. In CI it is uploaded as the
`real-integration-tests-jfr` artifact on every run of the **Build** workflow,
including failed ones.

That suite is the only tier that exercises the driver against real BigQuery under
concurrency (failsafe runs test classes at parallelism 8), so it is the only place a
recording is worth anything. The overhead is irrelevant there because the threads
spend nearly all their time blocked on the network.

The configuration is `settings=profile`, which is what makes it useful: it records
`jdk.ThreadPark` and `jdk.JavaMonitorEnter` events above a 10 ms threshold. That is
precisely the signature of #98 — a recording would have named the parking site
directly.

### Reading one

```bash
# What is in it
jfr summary target/real-integration-tests-*.jfr

# Threads parked over 10ms - the #98 shape
jfr print --events jdk.ThreadPark target/real-integration-tests-*.jfr | less

# Lock contention
jfr print --events jdk.JavaMonitorEnter target/real-integration-tests-*.jfr | less

# Where CPU went
jfr print --events jdk.ExecutionSample target/real-integration-tests-*.jfr | less
```

For anything more than a glance, open the file in [JDK Mission Control][jmc] — the
hot-methods, lock-instances and allocation views are far more useful than `jfr
print`.

The scale-test profile records too, to `target/scale-tests-<pid>.jfr`.

---

## 2. Thread-scaling benchmarks

The question these answer is not "how fast is it" but **does throughput scale with
threads, or flatten?** Flattening is the signature of the entire class of bug #98
belonged to. Absolute numbers against BigQuery are dominated by network latency and
vary with the day; the *shape* of the curve is the durable signal.

`ThreadScalingBenchmark` measures four operations, each with one connection per
worker thread (a pool handing a connection to each caller is the deployed shape, and
a shared `Connection` would serialize by construction and report flat scaling no
matter how good the driver was):

- `submitToFirstRow` — query submit through to the first row
- `iterateResultSet` — full iteration of a 50,000-row multi-page result
- `getTablesWarm` / `getColumnsWarm` — metadata against a warm cache

> **The two metadata numbers are only comparable across runs against the same
> project.** They measure the warm cache — building a fresh `ResultSet` from cached
> rows and iterating it — so throughput is inversely proportional to how many rows
> the project's metadata produces. A project with 600 tables and a project with 20
> will produce numbers an order of magnitude apart, and neither is "better". Record
> the baseline against the project you will re-measure against, and treat a large
> delta as a question about the project before it is a question about the driver.
> Cold fan-out is not measured here at all; that is `WideMetadataScaleTest`.

### Running

```bash
export BENCHMARK_JDBC_URL="jdbc:bigquery:my-project/my_dataset?authType=ADC"

# Full sweep over 1, 2, 4, 8, 16 threads. ~25-30 minutes.
./mvnw test-compile exec:exec -Pbenchmark-scaling

# Verify the harness works. Numbers are meaningless. ~2 minutes.
./mvnw test-compile exec:exec -Pbenchmark-scaling -Dbenchmark.args="--quick"

# Narrow the sweep
./mvnw test-compile exec:exec -Pbenchmark-scaling -Dbenchmark.args="--threads 1,8"
```

In CI, run the **Benchmarks** workflow from the Actions tab. It is
`workflow_dispatch` only — per-PR runs would add latency and BigQuery spend to every
change while measuring something that does not change between commits. The report is
published to the run summary and uploaded as an artifact.

The queries are `SELECT 1` and `GENERATE_ARRAY`, which scan no bytes, so a sweep
costs nothing beyond query slots.

### Reading the report

| Column | Meaning |
|---|---|
| Throughput | Operations per second, aggregated across all threads |
| Scaling | Throughput at N threads ÷ throughput at 1 thread. Perfect is Nx |
| Efficiency | Scaling ÷ N, as a percentage. Perfect is 100% |

What matters is that total throughput keeps *rising* as threads are added. A curve
where it stops rising — or falls — is the failure being watched for. Efficiency itself
is a diagnostic, not a score, and it lands in three regimes:

**Above 100% is normal for the query benchmarks, and not a bug in the measurement.**
`submitToFirstRow` and `iterateResultSet` are latency-bound: a single thread spends
nearly all of its time blocked on a BigQuery round trip, doing nothing. Adding threads
fills that dead time, and HTTP connection reuse amortises better under concurrency, so
throughput at N threads can exceed N times the single-threaded rate. The recorded
baseline shows 22x at 16 threads. Superlinear here means the single-threaded number is
mostly idle waiting — which it is.

**Well below 100% is expected for the metadata benchmarks, and the exact ceiling is
still unexplained.** `getTablesWarm` and `getColumnsWarm` serve from an in-memory
cache, so they are CPU-bound rather than network-bound and cannot exceed the core
count. They plateau around 4x on a 10-core machine, which is lower than that alone
accounts for.

One hypothesis has been tested and **refuted**, which is worth recording so it is not
retried. `MetadataResultSet.findColumn` used to lowercase the requested label on every
by-name lookup, allocating a `String` per row — over eleven thousand per
`getColumnsWarm` operation against a project with a few hundred tables.
Removing that allocation made these benchmarks roughly **three times faster at every
thread count** (see the baseline's delta column), so it was a large real cost. But the
scaling curve did not change shape at all: 1.71x / 2.79x / 3.69x / 4.02x after, against
1.81x / 2.85x / 3.71x / 4.10x before, both measured against the same project.
Allocation pressure was therefore not what limits the scaling.

Whatever the ceiling is, it survives a 3x change in per-operation cost, which points at
something structural rather than something on the hot path — plausibly memory bandwidth
or L3 contention, since every thread iterates the *same* shared row list. That is a
guess. The JFR recording from a scale run is the way to settle it, and nobody has done
that yet.

**Near 1x on a latency-bound benchmark is the failure.** That is #98: throughput flat
no matter how many callers.

> **Read the error column before reading the throughput column.** On the metadata
> benchmarks the JMH error can exceed the score itself (the baseline has 18578 ±
> 20480 at four threads). Those runs are noisy — short operations, GC interference,
> few samples — and a change of less than about 2x in them means nothing. The query
> benchmarks are far better behaved.

### The baseline

`--baseline <file>` adds a comparison column against a previously generated report.
The report is its own baseline format, so a run you trust can be committed and passed
back later. If `docs/contributing/benchmarks/thread-scaling-baseline.md` exists it is
picked up automatically.

To record or refresh one:

```bash
# Run the Benchmarks workflow, download the thread-scaling-report artifact, then:
cp thread-scaling.md docs/contributing/benchmarks/thread-scaling-baseline.md
git commit -m "perf(benchmarks): refresh the thread-scaling baseline"
```

Refresh it deliberately — after a change to the concurrency, dispatch or metadata
paths — not on a schedule. A baseline that tracks noise is not a baseline.

The committed baseline was recorded against the Terraform-managed integration
project (`bigquery-jdbc-driver-test`, the one `BQ_TEST_PROJECT` points at), on a
10-core Apple Silicon laptop rather than a CI runner. The report header records the
JVM, OS and core count for exactly this reason.

Two things therefore will not match it and should not be expected to: a sweep on
GitHub's 4-core `ubuntu-latest`, and a sweep against any other project. The second
matters more than it sounds — the metadata benchmarks scale inversely with how much
metadata the project has, so pointing `BENCHMARK_JDBC_URL` at a different project can
move them by an order of magnitude while the driver is unchanged. Compare like with
like, or re-record.

### Note on the JMH harness

Two things had to be fixed before any of this could run at all, and they applied to
the pre-existing `-Pbenchmarks` benchmarks too:

- **The annotation processor was not running.** JMH generates `META-INF/BenchmarkList`
  with an annotation processor discovered from the classpath. As of JDK 23 javac no
  longer does that discovery by default, so no descriptor was generated and JMH exited
  with `Unable to find the resource: /META-INF/BenchmarkList`. The processor is now
  named explicitly in `annotationProcessorPaths` on `default-testCompile`.
- **`exec:java` cannot fork.** Every benchmark declares `@Fork(1)`, and JMH's forked
  JVM rebuilds its classpath from `java.class.path`. `exec:java` runs in-process behind
  its own classloader and leaves that property pointing at Maven's classpath, so the
  fork died with `ClassNotFoundException` on JMH's own `ForkedMain`. Both benchmark
  profiles now use `exec:exec`, which spawns a real JVM with an explicit `-classpath`.

This is why the invocation is `exec:exec`, not `exec:java`, and why arguments are
passed as `-Dbenchmark.args` rather than `-Dexec.args`.

---

## 3. Scale tests

Opt-in, and gated three ways: a separate Maven profile, a separate source package the
default failsafe includes do not match, and the `BQ_SCALE_TESTS` environment
variable. That is deliberate. A gate that lives only in build configuration is one
broadened include pattern away from firing, and the failure mode — a PR run that
quietly grows by twenty minutes and a pile of BigQuery objects — is bad enough to be
worth a second lock.

```bash
gcloud auth application-default login
export BQ_TEST_PROJECT=my-gcp-project
export BQ_SCALE_TESTS=true

./mvnw verify -Pscale-tests

# One class
./mvnw verify -Pscale-tests -Dit.test=WideMetadataScaleTest
```

| Test | What it asserts |
|---|---|
| `LargeResultSetScaleTest` | A million rows iterate above a throughput floor, and retained heap does not grow with rows read |
| `WideMetadataScaleTest` | Metadata is complete and correct across 20 datasets / 300 tables, and the fan-out does not serialize |
| `MetadataCacheSteadyStateScaleTest` | The shared cache settles instead of growing across repeated TTL expiries |
| `PooledLoadScaleTest` | Throughput scales through HikariCP, `isValid()` stays responsive, nothing leaks |

### Fixtures are generated, not committed

Each test builds what it needs and tears it down. A committed fixture dataset would
need provisioning outside the repository, would drift from what the tests assume, and
would make the suite unrunnable against any project but one.

Everything created is named with a per-run ID and carries a BigQuery-side expiry
where the object type supports one. **Datasets are the exception** — BigQuery has no
dataset-level expiry — so `WideMetadataScaleTest` drops its 20 datasets in
`@AfterAll`. If a run is killed between fixture creation and teardown, they are left
behind:

```bash
# Find and remove stranded scale datasets
bq ls --project_id="$BQ_TEST_PROJECT" | grep '^ *scale_'
bq rm -r -f --dataset "$BQ_TEST_PROJECT:scale_<runId>_1"
```

### Why 20 datasets

[#99][99] capped concurrent `INFORMATION_SCHEMA` queries at 16 with a `Semaphore`,
because a project with hundreds of datasets would otherwise fire one query per
dataset at once and collect quota errors. Nothing had ever run it against more than a
handful, so the code path where a task *waits* on a permit had never executed. Twenty
datasets against a cap of sixteen forces at least four tasks to queue. A fixture of
ten would build a lot of BigQuery objects and still never test the thing.

### The heap assertion, and why `-Xmx512m` matters

`LargeResultSetScaleTest` asserts that retained heap does not grow with rows read — a
forward-only `ResultSet` over a paginated source should hold about one page, not the
whole result.

That measurement is soft. `System.gc()` is only a hint, and under a generous heap a
JVM may simply never collect, letting a driver that buffered everything pass. The
real guard is the `-Xmx512m` the `scale-tests` profile pins: full materialisation of a
million four-column rows would exhaust it and the test would die with
`OutOfMemoryError`, which is unambiguous. **Do not raise that heap to "fix" a
failure** — a failure there is the instrument working.

### Tuning

Every threshold is an environment variable, so the same test can be a quick check or
a deliberate hunt:

| Variable | Default | Test |
|---|---|---|
| `BQ_SCALE_ROWS` | 1,000,000 | Large result set |
| `BQ_SCALE_MIN_ROWS_PER_SEC` | 2,000 | Large result set |
| `BQ_SCALE_DATASETS` | 20 | Wide metadata |
| `BQ_SCALE_TABLES_PER_DATASET` | 15 | Wide metadata |
| `BQ_SCALE_METADATA_BUDGET_MS` | 180,000 | Wide metadata |
| `BQ_SCALE_CACHE_CYCLES` | 6 | Cache steady state |
| `BQ_SCALE_POOL_SIZE` | 8 | Pooled load |
| `BQ_SCALE_QUERIES_PER_THREAD` | 12 | Pooled load |
| `BQ_SCALE_MAX_IS_VALID_MS` | 5,000 | Pooled load |

---

## 4. The pooled load test

A connection pool is how this driver is deployed, and it is the scenario #98 broke.
That the fix works has been asserted but, until now, never demonstrated.

`SharedThreadPoolGuardTest` — the guard that came out of #98 — is a bytecode check for
executor-less `supplyAsync`, `runAsync` and `parallelStream`. It is a regression guard
for the exact shape of the old bug and nothing else. A *new* way to serialize
concurrent queries, through a lock or a bounded executor or a synchronized method,
would pass it cleanly. `PooledLoadScaleTest` asserts the property that actually
matters: throughput rises when callers are added.

It also asserts the credential cache hit rate stays above 50% across pooled
connections, which is the [#99][99] fix — without it every physical connection
separately probes the environment and fetches its own token.

---

## 5. Driver metrics

`DriverMetrics` exposes counters and timers the host application can read: query
counts and durations, metadata cache hit rate, session counts, credential cache hit
rate. Always on, JVM-scoped, no dependencies.

The scale tests consume them — `MetadataCacheSteadyStateScaleTest` asserts on real hit
and miss counts rather than inferring cache behaviour from timing.

User-facing documentation is in **[docs/OBSERVABILITY.md](../OBSERVABILITY.md)**.

---

## Open questions this does not settle

- **Is the Storage Read API path covered?** Partly, now. `NestedTypeReadBenchmark`
  compares it against the REST path across scalar and nested shapes (#232), and
  `StorageApiParityTest` covers correctness. What is still uncovered is the rest of
  #99's scrutiny — allocation profiles, thread scaling — and the scale tests still
  use the standard path.
- **Should a regression fail a build?** Currently no, by decision: reported numbers a
  human reads. Revisit if someone starts ignoring the reports.
- **Why do the metadata benchmarks plateau at 4x on ten cores?** Still unexplained —
  and now known *not* to be allocation pressure, see above. The first real question
  these instruments have raised rather than answered, which is what they are for.

## Storage Read API vs REST, by column shape

Measured with `NestedTypeReadBenchmark` at 1,000,000 rows, 5x60s iterations:

| Shape | Storage Read API | REST | Speedup |
|---|---|---|---|
| `SCALAR` (control) | 3,418 ± 622 ms | 19,134 ± 1,084 ms | **5.6x** |
| `ARRAY_STRUCT` | 4,639 ± 457 ms | 40,189 ± 5,928 ms | **8.7x** |

**The speedup is larger for nested data, not smaller.** Moving from scalar to nested
costs the Storage path 1.36x and the REST path 2.10x — re-encoding Arrow's nested
vectors into `FieldValue`s is cheaper than parsing the same structures out of JSON. So
`useStorageApi=auto` is choosing correctly for these shapes, which is what #232 asked.

Two things to know before re-running it:

- **Read the control first.** Every op re-executes its query, so each measurement
  includes BigQuery's scheduling and compute — seconds of variance, paid identically by
  both paths. At 1,000 and 50,000 rows that swamped the fetch difference entirely: error
  bars exceeded the scores and the control came out *slower* on the Storage path, which
  is the read session's fixed cost rather than a regression. A run whose control does not
  reproduce has measured nothing.
- **The control reads 5.6x here, not #152's 11.7x, and does not contradict it.** A
  constant paid by both paths compresses the ratio toward 1, so the fetch-only figure is
  higher than this measures.

The benchmark asserts which result-set implementation each connection produced and fails
otherwise. The Storage path falls back silently by design, so without that check the
comparison could be REST against REST with every number looking plausible.

## What the instruments found on their first run

**Fixed:** `MetadataResultSet.findColumn` lowercased the requested label on every
by-name column access, allocating a throwaway `String` per lookup — and by-name access
is per row, so a `getColumns()` over a project with a few hundred tables did it over
eleven thousand times per call. The
same shape as the per-row regex compilation #99 found. Resolving the exact match from a
map keyed by the declared name, and falling back to a linear `equalsIgnoreCase` scan
only on a miss, made `getColumnsWarm` **3.2x** faster and `getTablesWarm` **3.5x**
faster — measured before and after against the same project, with no change to the
query benchmarks, which is what tells you the gain is real and attributable rather
than measurement drift. The absolute figures scale with how many rows a
`getColumns()` returns, so the multiple is the durable part of that result, not the
ops/s.

**Fixed:** `MetadataCache` had **no size bound**. `evictExpired()` removed expired
entries only, so within a single TTL window every distinct query shape accumulated its
own entry holding every materialised row of its result — thousands of rows per entry on
a wide project, in a cache that is static and shared for the life of the process. It
now carries a ceiling on total cached rows (`metadataCacheMaxRows`, default 50,000),
evicting oldest-first once exceeded.

Eviction is oldest-first rather than LRU on purpose: LRU needs the read path to record
an access on every hit, which is a write to shared state on the one path that has to
stay concurrent. Ordering by expiry costs the read path nothing and is exactly
insertion order, since every entry in a cache takes the same TTL.

**Fixed:** `BQDatabaseMetaData` logged **31 times at INFO**, several on every
`getTables()` and `getColumns()`. Metadata methods are called constantly — an IDE
walks them on every tree refresh — so the driver shouted through the host
application's logs during ordinary operation. All 31 are now `debug`.

Two of them also evaluated their arguments eagerly, which parameterised logging does
*not* prevent: it defers formatting, not the expressions you hand it. One
concatenated a sublist into a `String`; the other called `MetadataCache.getStats()`,
which walks every entry to count expired ones and sum their rows. That one ran on
schedule whether or not anything was listening. Both now sit behind
`isDebugEnabled()`.

`MetadataLoggingLevelGuardTest` keeps it that way. The failure mode here is
copy-paste — every one of those 31 was added by matching the method next to it — and
nothing else catches it: it is not a bug, and SpotBugs and PMD say nothing. Lifecycle
and configuration events elsewhere in the driver (registration, session open, custom
endpoint) are legitimately INFO and out of the guard's scope; the line it draws is
per-call versus once-per-connection.

[93]: https://github.com/Two-Bear-Capital/tbc-bq-jdbc/issues/93
[98]: https://github.com/Two-Bear-Capital/tbc-bq-jdbc/issues/98
[99]: https://github.com/Two-Bear-Capital/tbc-bq-jdbc/issues/99
[jmc]: https://www.oracle.com/java/technologies/jdk-mission-control.html

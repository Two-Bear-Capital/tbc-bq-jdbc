# Observability

The driver keeps counters and timers describing what it is doing. Your application
can read them at any time, with no configuration and no extra dependencies.

Use them to answer questions about your own workload: why an IDE feels slow, whether a
pool is sharing credentials, whether a session is leaking.

## Reading them

```java
import vc.tbc.bq.jdbc.metrics.DriverMetrics;
import vc.tbc.bq.jdbc.metrics.MetricsSnapshot;

MetricsSnapshot metrics = DriverMetrics.snapshot();
System.out.println(metrics);
```

```
queries[submitted=1420 succeeded=1418 failed=2 inFlight=0 mean=214.3ms max=3011.0ms] metadataCache[hits=880 misses=44 hitRate=95.2%] sessions[created=3 closed=3 open=0] credentialCache[hits=63 misses=1 hitRate=98.4%]
```

`toString()` emits this as a single line; it is wrapped above for readability.

Counters are cumulative for the life of the JVM, which answers "what has this process
done". Usually the more useful question is "what happened during *this*", which is a
subtraction:

```java
MetricsSnapshot before = DriverMetrics.snapshot();

runTheWorkload();

MetricsSnapshot window = DriverMetrics.snapshot().minus(before);
System.out.printf("%d queries, mean %.0f ms, cache hit rate %.0f%%%n",
        window.queriesSucceeded(), window.meanQueryMillis(),
        100 * window.metadataCacheHitRate());
```

Derived values on a delta describe the window — `metadataCacheHitRate()` on a delta is
the hit rate *during* the workload, not since startup.

## What is measured

| Accessor | Meaning |
|---|---|
| `queriesSubmitted()` | Query and DML jobs dispatched to BigQuery |
| `queriesSucceeded()` / `queriesFailed()` | Terminal outcomes. Failures include cancellations and timeouts |
| `queriesInFlight()` | Dispatched but not yet finished |
| `meanQueryMillis()` / `maxQueryMillis()` | Wall-clock job duration, successes and failures alike |
| `metadataCacheHits()` / `metadataCacheMisses()` / `metadataCacheHitRate()` | Metadata lookups served from the shared cache. An expired entry counts as a miss — it costs a round trip either way |
| `sessionsCreated()` / `sessionsClosed()` / `sessionsOpen()` | BigQuery sessions, used for transactions, temp tables and multi-statement SQL |
| `credentialCacheHits()` / `credentialCacheMisses()` / `credentialCacheHitRate()` | Credentials served from the shared cache rather than rebuilt |

Everything is also logged at SLF4J `debug`: each completed job logs its duration and
outcome. See [LOGGING.md](LOGGING.md) for wiring up a logger.

## Diagnosing with them

**An IDE or tool feels slow browsing schemas** → look at `metadataCacheHitRate()`.
Near 100% is healthy. Near zero means the cache is being defeated: a
`metadataCacheTtl` shorter than your usage pattern, `metadataCacheEnabled=false`, or
every call arriving with a different pattern and therefore a different key. See
[CONNECTION_PROPERTIES.md](CONNECTION_PROPERTIES.md).

**Connections are slow to open under a pool** → look at `credentialCacheHitRate()`.
Under a pool this should approach 100%: every physical connection after the first
reuses one credentials object. A rate near zero means each connection is separately
probing the environment and fetching its own token.

**Queries feel slower than BigQuery reports** → compare `meanQueryMillis()` against
the durations in the BigQuery console. These are wall-clock from job creation to
terminal state, so a large gap points at the network or at contention on your side
rather than at BigQuery.

**Something is leaking sessions** → `sessionsOpen()` should sit near the number of
connections currently using transactions or temp tables. A number that only climbs is
a leak, and sessions are subject to a per-project limit.

## Scope, and what that means

Metrics are **JVM-wide, not per connection**. The things they measure already are: the
metadata cache is shared statically across every connection to a project and outlives
any of them, and the credentials cache is shared the same way. Per-connection counters
for those would each report a fraction of a shared reality and none would be right.

Query counters are aggregated to match, which is also what a pooled application wants
— the interesting number is the throughput of the pool, not of one borrowed
connection.

If you need per-connection attribution, take a snapshot before and after the work you
care about on a thread that is doing nothing else, or use BigQuery job labels (the
`labels` connection property) and query `INFORMATION_SCHEMA.JOBS`.

## Turning it off

Collection is on by default. Every counter is a `LongAdder` incremented once per
BigQuery round trip — a handful of nanoseconds against an operation measured in
hundreds of milliseconds — so the cost is not observable, and metrics you have to
switch on ahead of time are the metrics you do not have during the incident that
needed them.

It can still be disabled:

```bash
java -Dtbc.bq.jdbc.metrics.enabled=false -jar myapp.jar
```

```java
DriverMetrics.setEnabled(false);   // at runtime
```

Disabling does not discard what was already counted. `DriverMetrics.reset()` zeroes
everything — note that it is global, so prefer `minus()` if anything else in the JVM
is also reading these.

Metrics are controlled by a system property rather than a connection property, because
the registry is JVM-wide and shared by every connection in the process.

## Exporting

The driver exposes no JMX MBean and depends on no metrics library. `snapshot()` returns
an immutable record, which you forward to whatever you already use:

```java
// Micrometer
Gauge.builder("bq.metadata.cache.hit.rate",
        () -> DriverMetrics.snapshot().metadataCacheHitRate())
    .register(registry);

Gauge.builder("bq.queries.inflight",
        () -> DriverMetrics.snapshot().queriesInFlight())
    .register(registry);

```

```java
// A periodic log line
Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(
        () -> log.info("BigQuery driver: {}", DriverMetrics.snapshot()),
        1, 1, TimeUnit.MINUTES);
```

## A note on precision

Counters are read one after another rather than under a lock, so a snapshot taken during
active traffic may catch related counters an operation apart: `queriesSubmitted` can
exceed the sum of succeeded and failed by the number of queries genuinely in flight.
Derived counts are clamped at zero, so a skewed read never reports a negative.

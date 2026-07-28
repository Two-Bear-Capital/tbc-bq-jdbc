/*
 * Copyright 2026 Two Bear Capital
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package vc.tbc.bq.jdbc.metrics;

import java.util.concurrent.atomic.LongAccumulator;
import java.util.concurrent.atomic.LongAdder;

/**
 * Counters and timers describing what the driver is doing, readable by the host
 * application.
 *
 * <h2>Why this exists</h2>
 *
 * <p>
 * Before this, a user whose workload was slow could tell us "it is slow" and
 * nothing else, and we could not reproduce it. The driver kept no record of how
 * many queries it ran, how long they took, whether the metadata cache was
 * earning its keep, or how often credentials were rebuilt. Every performance
 * number this project had came from reading CI test timings.
 *
 * <p>
 * These counters let a user answer questions about their own workload without
 * filing a bug: a metadata cache hit rate near zero explains a slow IDE, a
 * credential cache hit rate near zero means connections are not sharing
 * credentials, and a mean query time far above the median request latency
 * points at BigQuery rather than at the driver.
 *
 * <h2>Scope is the JVM, not the connection</h2>
 *
 * <p>
 * Metrics are global because the things they measure already are. The metadata
 * cache is shared statically across every connection to a project and outlives
 * any one of them; the credentials cache is keyed by auth type and shared the
 * same way. Per-connection counters for those would each report a fraction of a
 * shared reality and none would be right. Query counters are aggregated to
 * match, which is also what a pooled application wants — the interesting
 * question is the throughput of the pool, not of one borrowed connection.
 *
 * <h2>Collection is on by default</h2>
 *
 * <p>
 * Every counter here is a {@link LongAdder} incremented once per BigQuery round
 * trip. That is a handful of nanoseconds against an operation measured in
 * hundreds of milliseconds, so the cost is not observable, and metrics that
 * must be switched on ahead of time are exactly the metrics nobody has during
 * the incident that needed them.
 *
 * <p>
 * It can still be turned off — set the system property
 * {@code tbc.bq.jdbc.metrics.enabled} to {@code false} at startup, or call
 * {@link #setEnabled(boolean)}. This is a system property rather than a
 * connection property because the registry is JVM-wide: a per-connection
 * setting could not meaningfully govern a cache shared with connections that
 * set it differently.
 *
 * <h2>Usage</h2>
 *
 * <pre>{@code
 * MetricsSnapshot before = DriverMetrics.snapshot();
 * // ... run the workload ...
 * MetricsSnapshot after = DriverMetrics.snapshot();
 *
 * System.out.println(after.minus(before));
 * }</pre>
 *
 * <p>
 * There is deliberately no JMX MBean. A static accessor returning an immutable
 * snapshot is testable, needs no registration lifecycle to get wrong on
 * connection close, and is trivially forwarded to whatever the host application
 * already uses — Micrometer, Prometheus, or a log line. Anyone who wants the
 * values on an MBean can register one over {@link #snapshot()} in a few lines.
 *
 * @since 2.0.0
 */
public final class DriverMetrics {

	/**
	 * Read once at class initialization. Defaults to enabled; only the exact string
	 * {@code false} disables, so a typo in the property leaves metrics on rather
	 * than silently off.
	 */
	private static volatile boolean enabled = !"false"
			.equalsIgnoreCase(System.getProperty("tbc.bq.jdbc.metrics.enabled", "true"));

	private static final LongAdder QUERIES_SUBMITTED = new LongAdder();
	private static final LongAdder QUERIES_SUCCEEDED = new LongAdder();
	private static final LongAdder QUERIES_FAILED = new LongAdder();
	private static final LongAdder QUERY_NANOS_TOTAL = new LongAdder();
	private static final LongAccumulator QUERY_NANOS_MAX = new LongAccumulator(Math::max, 0L);

	private static final LongAdder METADATA_CACHE_HITS = new LongAdder();
	private static final LongAdder METADATA_CACHE_MISSES = new LongAdder();

	private static final LongAdder SESSIONS_CREATED = new LongAdder();
	private static final LongAdder SESSIONS_CLOSED = new LongAdder();

	private static final LongAdder CREDENTIAL_CACHE_HITS = new LongAdder();
	private static final LongAdder CREDENTIAL_CACHE_MISSES = new LongAdder();

	private DriverMetrics() {
		throw new AssertionError("Utility class should not be instantiated");
	}

	/**
	 * Whether counters are currently being collected.
	 *
	 * @return true if collection is enabled
	 */
	public static boolean isEnabled() {
		return enabled;
	}

	/**
	 * Turns collection on or off at runtime.
	 *
	 * <p>
	 * Disabling does not reset anything: counters accumulated before the switch
	 * remain readable, which is what you want when turning collection off to rule
	 * it out as a cost. Call {@link #reset()} to zero them.
	 *
	 * @param value
	 *            true to collect, false to stop
	 */
	public static void setEnabled(boolean value) {
		enabled = value;
	}

	/**
	 * Records a query or DML job at the moment it is dispatched to BigQuery.
	 *
	 * <p>
	 * Must be called before the job is created, not after it finishes. Counting
	 * submissions at completion would make {@code queriesSubmitted} identical to
	 * {@code queriesSucceeded + queriesFailed} by construction, and
	 * {@link MetricsSnapshot#queriesInFlight()} permanently zero.
	 */
	public static void recordQuerySubmitted() {
		if (enabled) {
			QUERIES_SUBMITTED.increment();
		}
	}

	/**
	 * Records a query or DML job that completed successfully.
	 *
	 * @param elapsedNanos
	 *            wall-clock time from job creation to the job reaching a terminal
	 *            successful state
	 */
	public static void recordQuerySucceeded(long elapsedNanos) {
		if (!enabled) {
			return;
		}
		QUERIES_SUCCEEDED.increment();
		QUERY_NANOS_TOTAL.add(elapsedNanos);
		QUERY_NANOS_MAX.accumulate(elapsedNanos);
	}

	/**
	 * Records a query or DML job that failed, was cancelled, or timed out.
	 *
	 * <p>
	 * The elapsed time still counts toward the totals. A workload dominated by
	 * queries that fail slowly is exactly the shape worth seeing, and excluding
	 * them would make the mean flatter than the user's experience.
	 *
	 * @param elapsedNanos
	 *            wall-clock time from job creation to the failure
	 */
	public static void recordQueryFailed(long elapsedNanos) {
		if (!enabled) {
			return;
		}
		QUERIES_FAILED.increment();
		QUERY_NANOS_TOTAL.add(elapsedNanos);
		QUERY_NANOS_MAX.accumulate(elapsedNanos);
	}

	/** Records a metadata cache lookup that was served from the cache. */
	public static void recordMetadataCacheHit() {
		if (enabled) {
			METADATA_CACHE_HITS.increment();
		}
	}

	/**
	 * Records a metadata cache lookup that had to go to BigQuery — including one
	 * that found an entry but discarded it as expired.
	 */
	public static void recordMetadataCacheMiss() {
		if (enabled) {
			METADATA_CACHE_MISSES.increment();
		}
	}

	/** Records a BigQuery session being established. */
	public static void recordSessionCreated() {
		if (enabled) {
			SESSIONS_CREATED.increment();
		}
	}

	/** Records a BigQuery session being terminated. */
	public static void recordSessionClosed() {
		if (enabled) {
			SESSIONS_CLOSED.increment();
		}
	}

	/** Records credentials served from the shared credentials cache. */
	public static void recordCredentialCacheHit() {
		if (enabled) {
			CREDENTIAL_CACHE_HITS.increment();
		}
	}

	/** Records credentials that had to be built from scratch. */
	public static void recordCredentialCacheMiss() {
		if (enabled) {
			CREDENTIAL_CACHE_MISSES.increment();
		}
	}

	/**
	 * Takes an immutable point-in-time reading of every counter.
	 *
	 * <p>
	 * The counters are read one after another rather than under a lock, so a
	 * snapshot taken during active traffic may catch related counters an operation
	 * apart — {@code queriesSubmitted} may exceed the sum of succeeded and failed
	 * by the number of queries currently in flight. That is a true statement about
	 * the system rather than an inconsistency, and it is the right trade against
	 * putting a lock on a hot path to make a diagnostic exact.
	 *
	 * @return the current values
	 */
	public static MetricsSnapshot snapshot() {
		return new MetricsSnapshot(QUERIES_SUBMITTED.sum(), QUERIES_SUCCEEDED.sum(), QUERIES_FAILED.sum(),
				QUERY_NANOS_TOTAL.sum(), QUERY_NANOS_MAX.get(), METADATA_CACHE_HITS.sum(), METADATA_CACHE_MISSES.sum(),
				SESSIONS_CREATED.sum(), SESSIONS_CLOSED.sum(), CREDENTIAL_CACHE_HITS.sum(),
				CREDENTIAL_CACHE_MISSES.sum());
	}

	/**
	 * Zeroes every counter.
	 *
	 * <p>
	 * Intended for tests and for a host application that reports deltas over fixed
	 * windows. Prefer {@link MetricsSnapshot#minus(MetricsSnapshot)} when something
	 * else in the JVM may also be reading these — resetting is destructive and
	 * global.
	 */
	public static void reset() {
		QUERIES_SUBMITTED.reset();
		QUERIES_SUCCEEDED.reset();
		QUERIES_FAILED.reset();
		QUERY_NANOS_TOTAL.reset();
		QUERY_NANOS_MAX.reset();
		METADATA_CACHE_HITS.reset();
		METADATA_CACHE_MISSES.reset();
		SESSIONS_CREATED.reset();
		SESSIONS_CLOSED.reset();
		CREDENTIAL_CACHE_HITS.reset();
		CREDENTIAL_CACHE_MISSES.reset();
	}
}

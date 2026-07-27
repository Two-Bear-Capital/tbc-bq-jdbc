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

import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * An immutable reading of {@link DriverMetrics}.
 *
 * <p>
 * Raw counters are cumulative since JVM start (or since the last
 * {@link DriverMetrics#reset()}), so a single snapshot answers "what has this
 * JVM done", while {@link #minus(MetricsSnapshot)} of two snapshots answers
 * "what happened during this window" — usually the more useful question.
 *
 * @param queriesSubmitted
 *            query and DML jobs dispatched to BigQuery
 * @param queriesSucceeded
 *            jobs that reached a terminal successful state
 * @param queriesFailed
 *            jobs that failed, were cancelled, or timed out
 * @param queryNanosTotal
 *            summed wall-clock time of all completed jobs, successful or not
 * @param queryNanosMax
 *            the slowest single job observed
 * @param metadataCacheHits
 *            metadata lookups served from the shared cache
 * @param metadataCacheMisses
 *            metadata lookups that went to BigQuery, including expired entries
 * @param sessionsCreated
 *            BigQuery sessions established
 * @param sessionsClosed
 *            BigQuery sessions terminated
 * @param credentialCacheHits
 *            credentials served from the shared credentials cache
 * @param credentialCacheMisses
 *            credentials built from scratch
 *
 * @since 2.0.0
 */
public record MetricsSnapshot(long queriesSubmitted, long queriesSucceeded, long queriesFailed, long queryNanosTotal,
		long queryNanosMax, long metadataCacheHits, long metadataCacheMisses, long sessionsCreated, long sessionsClosed,
		long credentialCacheHits, long credentialCacheMisses) {

	/**
	 * Jobs dispatched but not yet finished at the moment of the snapshot.
	 *
	 * <p>
	 * Derived rather than counted, so it can read as slightly stale under active
	 * traffic — see {@link DriverMetrics#snapshot()}. Clamped at zero: on a
	 * snapshot taken between a completion counter and the submitted counter being
	 * read, the subtraction can go momentarily negative, and "-1 queries in flight"
	 * would be a worse answer than zero.
	 *
	 * @return jobs in flight, never negative
	 */
	public long queriesInFlight() {
		return Math.max(0, queriesSubmitted - queriesSucceeded - queriesFailed);
	}

	/**
	 * Mean wall-clock duration of a completed job, in milliseconds.
	 *
	 * @return the mean, or 0 if nothing has completed
	 */
	public double meanQueryMillis() {
		long completed = queriesSucceeded + queriesFailed;
		if (completed == 0) {
			return 0.0;
		}
		return (double) queryNanosTotal / completed / TimeUnit.MILLISECONDS.toNanos(1);
	}

	/**
	 * Slowest single job observed, in milliseconds.
	 *
	 * @return the maximum, or 0 if nothing has completed
	 */
	public double maxQueryMillis() {
		return (double) queryNanosMax / TimeUnit.MILLISECONDS.toNanos(1);
	}

	/**
	 * Fraction of metadata lookups served from cache, between 0 and 1.
	 *
	 * <p>
	 * The number to look at first when an IDE feels slow against a large project. A
	 * rate near zero means the cache is being defeated — by a TTL shorter than the
	 * usage pattern, by {@code metadataCacheEnabled=false}, or by every call
	 * arriving with a different pattern and so a different key.
	 *
	 * @return the hit rate, or 0 if there have been no lookups
	 */
	public double metadataCacheHitRate() {
		return rate(metadataCacheHits, metadataCacheMisses);
	}

	/**
	 * Fraction of credential requests served from cache, between 0 and 1.
	 *
	 * <p>
	 * Under a connection pool this should approach 1: every physical connection
	 * after the first reuses one credential object. A rate that stays near zero
	 * means connections are not sharing credentials, and each is separately probing
	 * the environment and fetching a token.
	 *
	 * @return the hit rate, or 0 if there have been no requests
	 */
	public double credentialCacheHitRate() {
		return rate(credentialCacheHits, credentialCacheMisses);
	}

	/**
	 * Sessions established but not yet terminated.
	 *
	 * <p>
	 * Should sit near the number of connections currently using transactions or
	 * temp tables. A number that only ever climbs is a session leak: sessions hold
	 * BigQuery-side state and are subject to a per-project limit.
	 *
	 * @return open sessions, never negative
	 */
	public long sessionsOpen() {
		return Math.max(0, sessionsCreated - sessionsClosed);
	}

	private static double rate(long hits, long misses) {
		long total = hits + misses;
		return total == 0 ? 0.0 : (double) hits / total;
	}

	/**
	 * Difference between this snapshot and an earlier one.
	 *
	 * <p>
	 * The usual way to read these counters: take one before a workload and one
	 * after, and subtract. Derived accessors on the result then describe the window
	 * rather than the life of the JVM — {@link #metadataCacheHitRate()} on a delta
	 * is the hit rate <em>during</em> the workload, which is almost always the
	 * question being asked.
	 *
	 * <p>
	 * {@link #queryNanosMax} is carried over from this snapshot rather than
	 * subtracted. A maximum is not additive: subtracting two maxima produces a
	 * number that is not the maximum of anything. Reading the delta's max as "the
	 * slowest query in the window" is therefore only right when the window
	 * contained the slowest query overall; otherwise it overstates.
	 *
	 * @param earlier
	 *            the earlier snapshot
	 * @return a snapshot whose counters are the differences
	 */
	public MetricsSnapshot minus(MetricsSnapshot earlier) {
		return new MetricsSnapshot(queriesSubmitted - earlier.queriesSubmitted,
				queriesSucceeded - earlier.queriesSucceeded, queriesFailed - earlier.queriesFailed,
				queryNanosTotal - earlier.queryNanosTotal, queryNanosMax, metadataCacheHits - earlier.metadataCacheHits,
				metadataCacheMisses - earlier.metadataCacheMisses, sessionsCreated - earlier.sessionsCreated,
				sessionsClosed - earlier.sessionsClosed, credentialCacheHits - earlier.credentialCacheHits,
				credentialCacheMisses - earlier.credentialCacheMisses);
	}

	@Override
	public String toString() {
		return String.format(Locale.ROOT,
				"queries[submitted=%d succeeded=%d failed=%d inFlight=%d mean=%.1fms max=%.1fms] "
						+ "metadataCache[hits=%d misses=%d hitRate=%.1f%%] " + "sessions[created=%d closed=%d open=%d] "
						+ "credentialCache[hits=%d misses=%d hitRate=%.1f%%]",
				queriesSubmitted, queriesSucceeded, queriesFailed, queriesInFlight(), meanQueryMillis(),
				maxQueryMillis(), metadataCacheHits, metadataCacheMisses, 100.0 * metadataCacheHitRate(),
				sessionsCreated, sessionsClosed, sessionsOpen(), credentialCacheHits, credentialCacheMisses,
				100.0 * credentialCacheHitRate());
	}
}

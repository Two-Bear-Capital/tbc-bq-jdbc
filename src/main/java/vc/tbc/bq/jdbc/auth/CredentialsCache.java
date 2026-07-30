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
package vc.tbc.bq.jdbc.auth;

import com.google.auth.Credentials;
import vc.tbc.bq.jdbc.metrics.DriverMetrics;
import vc.tbc.bq.jdbc.transport.DriverTransports;
import vc.tbc.bq.jdbc.transport.TransportConfig;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Reuses {@link Credentials} across connections that authenticate the same way.
 *
 * <p>
 * Building credentials is not free. Application Default Credentials probe the
 * environment and then fetch a token; a service account key is read from disk
 * and its private key parsed. Doing that in every
 * {@link vc.tbc.bq.jdbc.BQConnection} constructor means a connection pool pays
 * it on every physical connection it opens, and — because each credential
 * object carries its own token cache — the same token is fetched over and over
 * instead of being shared.
 *
 * <p>
 * Every {@link AuthType} is a record, so instances configured identically are
 * equal and make sound cache keys. Credentials refresh their own tokens, so a
 * shared instance stays valid indefinitely.
 *
 * <p>
 * <b>Rotation:</b> entries expire after {@value #DEFAULT_TTL_SECONDS} seconds,
 * so a rotated service account key is picked up without restarting the JVM. The
 * window is configurable with the {@code tbc.bq.jdbc.credentials.ttl.seconds}
 * system property; set it to {@code 0} to cache for the life of the process.
 * {@link #clear()} discards everything immediately.
 *
 * <p>
 * Expiry is unrelated to token refresh, which the credential object does for
 * itself. This governs only how long the driver keeps reusing a credential
 * built from a given source.
 *
 * @since 1.0.99
 */
public final class CredentialsCache {

	/** System property overriding how long a built credential is reused. */
	public static final String TTL_PROPERTY = "tbc.bq.jdbc.credentials.ttl.seconds";

	/** Default reuse window, in seconds. */
	public static final long DEFAULT_TTL_SECONDS = 3600;

	/** A cached credential and the time it was built. */
	private record Entry(Credentials credentials, long builtAtNanos) {
	}

	/**
	 * What makes two connections' credentials interchangeable.
	 *
	 * <p>
	 * The transport belongs in the key because a credential holds the one it
	 * refreshes over — its proxy and its trust anchors both. Keying on
	 * {@link AuthType} alone would let whichever connection opened first decide,
	 * for every later one sharing its authentication, whether token refresh goes
	 * through the proxy and what certificate authority it trusts — so the same URL
	 * would work or fail depending on what else the JVM had already connected to.
	 *
	 * @param authType
	 *            how the credential is obtained
	 * @param transport
	 *            the route it is obtained over
	 */
	private record Key(AuthType authType, TransportConfig transport) {
	}

	private static final ConcurrentHashMap<Key, Entry> CACHE = new ConcurrentHashMap<>();

	private CredentialsCache() {
		throw new AssertionError("Utility class should not be instantiated");
	}

	/**
	 * Returns credentials for the given authentication type, building them on first
	 * use.
	 *
	 * <p>
	 * Deliberately not {@code computeIfAbsent}: that would hold a bin lock on the
	 * map across the network and disk I/O of building credentials, blocking
	 * unrelated keys. Two threads racing here may each build credentials, and the
	 * loser's copy is simply discarded.
	 *
	 * @param authType
	 *            the authentication type
	 * @return credentials, shared with other connections using the same auth
	 * @throws IOException
	 *             if credentials cannot be created
	 */
	public static Credentials forAuthType(AuthType authType) throws IOException {
		return forAuthType(authType, TransportConfig.direct());
	}

	/**
	 * Returns credentials for the given authentication type, fetched over
	 * {@code transport}.
	 *
	 * @param authType
	 *            the authentication type
	 * @param transport
	 *            the route and trust settings to mint and refresh tokens over
	 * @return credentials, shared with other connections using the same auth and
	 *         the same transport
	 * @throws IOException
	 *             if credentials cannot be created, or a configured truststore
	 *             cannot be read
	 * @since 4.3.0
	 */
	public static Credentials forAuthType(AuthType authType, TransportConfig transport) throws IOException {
		Key key = new Key(authType, transport);
		Entry cached = CACHE.get(key);
		if (cached != null && !isExpired(cached)) {
			DriverMetrics.recordCredentialCacheHit();
			return cached.credentials();
		}

		// Counted as a miss whether the entry was absent or stale: both mean this
		// call paid to build credentials, which is the cost worth seeing.
		DriverMetrics.recordCredentialCacheMiss();
		Credentials created = authType.toCredentials(DriverTransports.forTransport(transport));
		// put rather than putIfAbsent: an expired entry must be replaced. Two threads
		// racing here each build credentials and the last write wins, which is
		// harmless — the discarded copy is equivalent.
		CACHE.put(key, new Entry(created, System.nanoTime()));
		return created;
	}

	/** Whether an entry has outlived the configured reuse window. */
	private static boolean isExpired(Entry entry) {
		return isExpired(entry.builtAtNanos(), System.nanoTime());
	}

	/**
	 * Whether something built at {@code builtAtNanos} is stale as of {@code now}.
	 *
	 * <p>
	 * Package-private and taking an explicit clock reading so the expiry rule can
	 * be tested without a sleep, and without needing an {@link AuthType} instance —
	 * that interface is sealed, so no test double can implement it.
	 *
	 * @param builtAtNanos
	 *            {@link System#nanoTime()} reading when the entry was built
	 * @param nowNanos
	 *            the current {@link System#nanoTime()} reading
	 * @return true when the entry should be rebuilt
	 */
	static boolean isExpired(long builtAtNanos, long nowNanos) {
		long ttlNanos = ttlNanos();
		if (ttlNanos <= 0) {
			return false;
		}
		return nowNanos - builtAtNanos >= ttlNanos;
	}

	/**
	 * Reads the configured reuse window, in nanoseconds.
	 *
	 * <p>
	 * Read per call rather than cached in a constant so the property can be changed
	 * at runtime. This runs once per connection, not per query, so the lookup cost
	 * is not worth avoiding. An unparseable value falls back to the default rather
	 * than failing a connection over a diagnostic setting.
	 *
	 * @return the window in nanoseconds, or 0 to never expire
	 */
	static long ttlNanos() {
		long seconds = DEFAULT_TTL_SECONDS;
		String configured = System.getProperty(TTL_PROPERTY);
		if (configured != null) {
			try {
				seconds = Long.parseLong(configured.trim());
			} catch (NumberFormatException e) {
				seconds = DEFAULT_TTL_SECONDS;
			}
		}
		return seconds <= 0 ? 0 : TimeUnit.SECONDS.toNanos(seconds);
	}

	/** Discards all cached credentials, so the next use rebuilds them. */
	public static void clear() {
		CACHE.clear();
	}

	/**
	 * Returns how many distinct authentication-and-route combinations are cached.
	 *
	 * @return the number of cached entries
	 */
	public static int size() {
		return CACHE.size();
	}
}

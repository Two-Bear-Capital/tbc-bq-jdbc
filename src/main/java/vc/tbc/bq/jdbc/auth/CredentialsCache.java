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

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

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
 * <b>Rotation:</b> entries live for the life of the JVM. Rotating a service
 * account key file on disk therefore requires a restart to take effect; call
 * {@link #clear()} to force credentials to be rebuilt.
 *
 * @since 1.0.99
 */
public final class CredentialsCache {

	private static final ConcurrentHashMap<AuthType, Credentials> CACHE = new ConcurrentHashMap<>();

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
		Credentials cached = CACHE.get(authType);
		if (cached != null) {
			DriverMetrics.recordCredentialCacheHit();
			return cached;
		}

		DriverMetrics.recordCredentialCacheMiss();
		Credentials created = authType.toCredentials();
		Credentials existing = CACHE.putIfAbsent(authType, created);
		// A racing thread may have won the putIfAbsent, in which case this call did
		// build credentials and is correctly counted as a miss even though the object
		// it hands back came from the cache. The alternative - counting the loser as a
		// hit - would understate exactly the duplicated work worth knowing about.
		return existing != null ? existing : created;
	}

	/** Discards all cached credentials, so the next use rebuilds them. */
	public static void clear() {
		CACHE.clear();
	}

	/**
	 * Returns how many distinct authentication types are cached.
	 *
	 * @return the number of cached entries
	 */
	public static int size() {
		return CACHE.size();
	}
}

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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the reuse window applied to cached credentials.
 *
 * <p>
 * Entries used to live for the life of the JVM, so rotating a service account
 * key on disk had no effect until a restart — on a long-lived IDE session,
 * days.
 *
 * <p>
 * The expiry rule is exercised through the package-private overload that takes
 * an explicit clock reading. {@link AuthType} is sealed, so no test double can
 * implement it, and driving this through {@code forAuthType} would mean either
 * real credentials or a one-second sleep.
 *
 * @since 3.0.0
 */
class CredentialsCacheTest {

	private String originalTtl;

	@BeforeEach
	void setUp() {
		originalTtl = System.getProperty(CredentialsCache.TTL_PROPERTY);
		CredentialsCache.clear();
	}

	@AfterEach
	void tearDown() {
		if (originalTtl == null) {
			System.clearProperty(CredentialsCache.TTL_PROPERTY);
		} else {
			System.setProperty(CredentialsCache.TTL_PROPERTY, originalTtl);
		}
		CredentialsCache.clear();
	}

	@Test
	void entriesInsideTheWindowAreReused() {
		// Given: a one-hour window
		System.setProperty(CredentialsCache.TTL_PROPERTY, "3600");
		long builtAt = 0L;

		// When: a minute has passed
		long now = TimeUnit.MINUTES.toNanos(1);

		// Then: still fresh, so a pool keeps sharing one credential rather than
		// re-probing ADC per physical connection
		assertFalse(CredentialsCache.isExpired(builtAt, now));
	}

	@Test
	void entriesPastTheWindowAreStale() {
		// Given: a one-hour window
		System.setProperty(CredentialsCache.TTL_PROPERTY, "3600");
		long builtAt = 0L;

		// When: just over an hour has passed — the case that picks up a rotated key
		long now = TimeUnit.MINUTES.toNanos(61);

		assertTrue(CredentialsCache.isExpired(builtAt, now));
	}

	@Test
	void expiryIsInclusiveAtTheBoundary() {
		System.setProperty(CredentialsCache.TTL_PROPERTY, "60");
		assertTrue(CredentialsCache.isExpired(0L, TimeUnit.SECONDS.toNanos(60)));
		assertFalse(CredentialsCache.isExpired(0L, TimeUnit.SECONDS.toNanos(59)));
	}

	@Test
	void zeroTtlMeansNeverExpire() {
		// Given: expiry switched off
		System.setProperty(CredentialsCache.TTL_PROPERTY, "0");

		// Then: nothing is ever stale, however long has passed
		assertFalse(CredentialsCache.isExpired(0L, TimeUnit.DAYS.toNanos(365)));
		assertEquals(0L, CredentialsCache.ttlNanos());
	}

	@Test
	void negativeTtlAlsoMeansNeverExpire() {
		System.setProperty(CredentialsCache.TTL_PROPERTY, "-1");
		assertFalse(CredentialsCache.isExpired(0L, TimeUnit.DAYS.toNanos(1)));
	}

	@Test
	void unsetPropertyUsesTheDefaultWindow() {
		// Given: no override
		System.clearProperty(CredentialsCache.TTL_PROPERTY);

		// Then: the documented default applies
		assertEquals(TimeUnit.SECONDS.toNanos(CredentialsCache.DEFAULT_TTL_SECONDS), CredentialsCache.ttlNanos());
	}

	@Test
	void unparseableTtlFallsBackToTheDefault() {
		// Given: a malformed setting, which must not fail a connection over what is
		// only a caching hint
		System.setProperty(CredentialsCache.TTL_PROPERTY, "not-a-number");

		assertEquals(TimeUnit.SECONDS.toNanos(CredentialsCache.DEFAULT_TTL_SECONDS), CredentialsCache.ttlNanos());
	}

	@Test
	void clearEmptiesTheCache() {
		CredentialsCache.clear();
		assertEquals(0, CredentialsCache.size());
	}
}

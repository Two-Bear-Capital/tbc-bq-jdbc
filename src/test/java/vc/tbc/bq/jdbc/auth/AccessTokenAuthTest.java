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

import com.google.auth.oauth2.GoogleCredentials;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vc.tbc.bq.jdbc.transport.TransportConfig;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the access-token credential, whose whole design problem is that it
 * cannot be refreshed.
 *
 * @since 4.4.0
 */
class AccessTokenAuthTest {

	@BeforeEach
	@AfterEach
	void clearCache() {
		CredentialsCache.clear();
	}

	@Test
	void theTokenBecomesABearerCredential() throws Exception {
		Instant expiry = Instant.now().plus(1, ChronoUnit.HOURS);
		AccessTokenAuth auth = new AccessTokenAuth("ya29.test-token", expiry);

		GoogleCredentials credentials = (GoogleCredentials) auth.toCredentials(null);

		assertEquals("ya29.test-token", credentials.getAccessToken().getTokenValue());
		// Truncated to milliseconds: AccessToken carries a Date, which has no
		// finer resolution than that
		assertEquals(expiry.truncatedTo(ChronoUnit.MILLIS),
				credentials.getAccessToken().getExpirationTime().toInstant().truncatedTo(ChronoUnit.MILLIS));
	}

	@Test
	void anUnknownExpiryIsAllowed() throws Exception {
		// A caller handed a token by someone else may genuinely not know
		GoogleCredentials credentials = (GoogleCredentials) new AccessTokenAuth("ya29.test-token").toCredentials(null);

		assertNull(credentials.getAccessToken().getExpirationTime());
	}

	@Test
	void aBlankTokenIsRejected() {
		assertThrows(IllegalArgumentException.class, () -> new AccessTokenAuth("  "));
		assertThrows(NullPointerException.class, () -> new AccessTokenAuth(null));
	}

	@Test
	void expiryIsReportedOnlyWhenKnownAndPast() {
		assertTrue(new AccessTokenAuth("t", Instant.now().minusSeconds(1)).isExpired());
		assertFalse(new AccessTokenAuth("t", Instant.now().plusSeconds(60)).isExpired());
		// Unknown is not the same as valid, but there is nothing to test against
		assertFalse(new AccessTokenAuth("t").isExpired());
	}

	@Test
	void theTokenIsNeverRendered() {
		// It is a bearer credential: whoever holds it can act as the user
		AccessTokenAuth auth = new AccessTokenAuth("ya29.super-secret", Instant.parse("2026-07-30T20:00:00Z"));

		assertFalse(auth.toString().contains("super-secret"), auth.toString());
		assertTrue(auth.toString().contains("2026-07-30T20:00:00Z"), auth.toString());
	}

	@Test
	void credentialsAreNotCached() throws Exception {
		// Unlike every other auth type. Building one costs nothing, and a cached
		// copy would go on being served after the token expired.
		AccessTokenAuth auth = new AccessTokenAuth("ya29.test-token");

		var first = CredentialsCache.forAuthType(auth, TransportConfig.direct());
		var second = CredentialsCache.forAuthType(auth, TransportConfig.direct());

		assertNotSame(first, second);
		assertEquals(0, CredentialsCache.size(), "nothing should have been stored");
	}

	@Test
	void otherAuthTypesAreStillCached() throws Exception {
		// Guards the bypass against being written too broadly
		ApplicationDefaultAuth adc = new ApplicationDefaultAuth();
		try {
			CredentialsCache.forAuthType(adc, TransportConfig.direct());
		} catch (Exception e) {
			// No ambient credentials on this machine; the cache is what is under
			// test, and a failed build simply stores nothing
			return;
		}
		assertEquals(1, CredentialsCache.size());
	}
}

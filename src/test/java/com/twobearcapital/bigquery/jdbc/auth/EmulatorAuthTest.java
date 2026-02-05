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
package com.twobearcapital.bigquery.jdbc.auth;

import static org.junit.jupiter.api.Assertions.*;

import com.google.auth.Credentials;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.IOException;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link EmulatorAuth}.
 *
 * @since 1.0.0
 */
class EmulatorAuthTest {

	@Test
	void testToCredentialsReturnsValidCredentials() throws IOException {
		// Given: An EmulatorAuth instance
		EmulatorAuth auth = new EmulatorAuth();

		// When: Converting to credentials
		Credentials credentials = auth.toCredentials();

		// Then: Should return GoogleCredentials
		assertNotNull(credentials, "Credentials should not be null");
		assertInstanceOf(GoogleCredentials.class, credentials, "Should be GoogleCredentials instance");
	}

	@Test
	void testToCredentialsHasFakeToken() throws IOException {
		// Given: An EmulatorAuth instance
		EmulatorAuth auth = new EmulatorAuth();

		// When: Converting to credentials and getting access token
		GoogleCredentials credentials = (GoogleCredentials) auth.toCredentials();
		AccessToken token = credentials.getAccessToken();

		// Then: Should have fake token
		assertNotNull(token, "Access token should not be null");
		assertEquals("emulator-fake-token", token.getTokenValue(), "Should have fake token value");
	}

	@Test
	void testToCredentialsTokenNeverExpires() throws IOException {
		// Given: An EmulatorAuth instance
		EmulatorAuth auth = new EmulatorAuth();

		// When: Converting to credentials and getting access token
		GoogleCredentials credentials = (GoogleCredentials) auth.toCredentials();
		AccessToken token = credentials.getAccessToken();

		// Then: Token should have far future expiration
		assertNotNull(token.getExpirationTime(), "Expiration time should not be null");
		assertTrue(token.getExpirationTime().getTime() > System.currentTimeMillis() + 1000L * 365 * 24 * 60 * 60,
				"Token should expire far in the future");
	}

	@Test
	void testRecordEquals() {
		// Given: Two EmulatorAuth instances
		EmulatorAuth auth1 = new EmulatorAuth();
		EmulatorAuth auth2 = new EmulatorAuth();

		// Then: They should be equal (record equality)
		assertEquals(auth1, auth2, "EmulatorAuth instances should be equal");
		assertEquals(auth1.hashCode(), auth2.hashCode(), "Hash codes should match");
	}

	@Test
	void testRecordToString() {
		// Given: An EmulatorAuth instance
		EmulatorAuth auth = new EmulatorAuth();

		// When: Converting to string
		String str = auth.toString();

		// Then: Should contain class name
		assertNotNull(str, "toString should not return null");
		assertTrue(str.contains("EmulatorAuth"), "toString should contain class name");
	}
}

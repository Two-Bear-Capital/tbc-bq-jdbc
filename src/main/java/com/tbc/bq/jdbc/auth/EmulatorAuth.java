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
package com.tbc.bq.jdbc.auth;

import com.google.auth.Credentials;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;

import java.io.IOException;
import java.util.Date;

/**
 * Emulator authentication (no real authentication).
 *
 * <p>
 * Used for connecting to BigQuery emulators that don't require authentication.
 *
 * @since 1.0.0
 */
public record EmulatorAuth() implements AuthType {

	@Override
	public Credentials toCredentials() throws IOException {
		// Return a GoogleCredentials instance with a fake access token that never
		// expires
		// The emulator doesn't validate credentials, so this is sufficient
		AccessToken fakeToken = new AccessToken("emulator-fake-token", new Date(Long.MAX_VALUE));
		return GoogleCredentials.create(fakeToken);
	}
}

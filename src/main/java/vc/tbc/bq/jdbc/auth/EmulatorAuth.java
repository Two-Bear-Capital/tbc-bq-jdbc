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
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;

import java.io.IOException;
import java.util.Date;

/**
 * Emulator authentication — a fabricated token, for endpoints that do not check
 * one.
 *
 * @deprecated Scheduled for removal in the next major release. The driver no
 *             longer tests against the BigQuery emulator: the emulator diverges
 *             from the service, and tests written against it were repeatedly
 *             weakened until they passed, which shipped defects — issues #93,
 *             #121, #123 and #129 each sat behind an "emulator limitation"
 *             comment, and four of those limitations turned out to describe
 *             BigQuery or this driver rather than the emulator. Continuing to
 *             advertise emulator support the project does not exercise would
 *             promise more than it can keep.
 *             <p>
 *             To connect to real BigQuery, use any other {@link AuthType} —
 *             {@code authType=ADC} is the usual choice. If you need to point
 *             the driver at a non-Google endpoint, the {@code host} property
 *             still works; supply real credentials for it.
 *
 * @since 1.0.0
 */
@Deprecated(since = "1.1.0", forRemoval = true)
public record EmulatorAuth() implements AuthType {

	private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(EmulatorAuth.class);

	/** One warning per JVM, not one per connection. */
	private static final java.util.concurrent.atomic.AtomicBoolean WARNED = new java.util.concurrent.atomic.AtomicBoolean(
			false);

	@Override
	public Credentials toCredentials() throws IOException {
		if (WARNED.compareAndSet(false, true)) {
			logger.warn("authType=EMULATOR is deprecated and will be removed in the next major release. "
					+ "The driver is no longer tested against the BigQuery emulator. "
					+ "Use authType=ADC (or another real credential type) against BigQuery.");
		}
		// Return a GoogleCredentials instance with a fake access token that never
		// expires
		// The emulator doesn't validate credentials, so this is sufficient
		AccessToken fakeToken = new AccessToken("emulator-fake-token", new Date(Long.MAX_VALUE));
		return GoogleCredentials.create(fakeToken);
	}
}

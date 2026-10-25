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

import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.auth.Credentials;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ImpersonatedCredentials;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLHandshakeException;
import java.io.EOFException;
import java.io.IOException;
import java.net.SocketException;
import java.security.cert.CertificateException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives a real {@code ImpersonatedCredentials} against a fake IAM endpoint, so
 * the failures are the exact shape google-auth produces (#337).
 */
class DroppedConnectionRetryingCredentialsTest {

	private static final List<Duration> NO_WAIT = List.of(Duration.ZERO, Duration.ZERO);

	/** One scripted outcome per IAM call: an exception to throw, or a status. */
	private static final class FakeIam extends MockHttpTransport {
		final Deque<Object> outcomes = new ArrayDeque<>();
		final AtomicInteger calls = new AtomicInteger();

		FakeIam then(Object outcome) {
			outcomes.add(outcome);
			return this;
		}

		@Override
		public LowLevelHttpRequest buildRequest(String method, String url) {
			return new MockLowLevelHttpRequest(url) {
				@Override
				public LowLevelHttpResponse execute() throws IOException {
					calls.incrementAndGet();
					Object outcome = outcomes.remove();
					if (outcome instanceof IOException failure) {
						throw failure;
					}
					int status = (Integer) outcome;
					String expiry = Instant.now().plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS).toString();
					return new MockLowLevelHttpResponse().setStatusCode(status).setContentType("application/json")
							.setContent(status == 200
									? "{\"accessToken\":\"minted\",\"expireTime\":\"" + expiry + "\"}"
									: "{\"error\":{\"code\":" + status + "}}");
				}
			};
		}
	}

	private static GoogleCredentials impersonating(FakeIam iam) {
		GoogleCredentials source = GoogleCredentials
				.create(new AccessToken("source", Date.from(Instant.now().plus(1, ChronoUnit.HOURS))));
		ImpersonatedCredentials impersonated = ImpersonatedCredentials.newBuilder().setSourceCredentials(source)
				.setTargetPrincipal("target@project.iam.gserviceaccount.com").setScopes(List.of("scope"))
				.setHttpTransportFactory(() -> iam).build();
		return new DroppedConnectionRetryingCredentials(impersonated, NO_WAIT);
	}

	@Test
	void aDroppedConnectionOnTheTokenRequestIsRetried() throws Exception {
		FakeIam iam = new FakeIam().then(new SocketException("Unexpected end of file from server")).then(200);

		GoogleCredentials credentials = impersonating(iam);

		assertEquals(List.of("Bearer minted"), credentials.getRequestMetadata().get("Authorization"));
		assertEquals(2, iam.calls.get());
	}

	@Test
	void retriesStopAfterTheBackoffRunsOut() {
		FakeIam iam = new FakeIam().then(new SocketException("1")).then(new SocketException("2"))
				.then(new SocketException("3")).then(200);

		IOException thrown = assertThrows(IOException.class, () -> impersonating(iam).refresh());

		assertEquals("Error requesting access token", thrown.getMessage());
		assertEquals(3, iam.calls.get());
	}

	@Test
	void anHttpErrorFromIamIsNotRetriedAndKeepsItsStatus() {
		FakeIam iam = new FakeIam().then(403).then(200);

		IOException thrown = assertThrows(IOException.class, () -> impersonating(iam).refresh());

		// ServiceErrorDetail classifies a rejected credential from this status
		assertEquals(403, assertInstanceOf(HttpResponseException.class, thrown.getCause()).getStatusCode());
		assertEquals(1, iam.calls.get());
	}

	@Test
	void onlyAConnectionDropCounts() {
		assertTrue(DroppedConnectionRetryingCredentials
				.isDroppedConnection(new IOException("Error requesting access token", new SocketException("reset"))));
		assertTrue(DroppedConnectionRetryingCredentials
				.isDroppedConnection(new SSLHandshakeException("connection closed")));
		assertTrue(DroppedConnectionRetryingCredentials
				.isDroppedConnection(new IOException(new EOFException("connection closed"))));

		SSLHandshakeException untrusted = new SSLHandshakeException("PKIX path building failed");
		untrusted.initCause(new CertificateException("untrusted"));
		assertFalse(DroppedConnectionRetryingCredentials.isDroppedConnection(untrusted));
		assertFalse(DroppedConnectionRetryingCredentials.isDroppedConnection(new IOException("Error parsing")));
	}

	@Test
	void impersonationIsBuiltWithTheRetry() throws Exception {
		Credentials built = new ImpersonatedAuth(new AccessTokenAuth("source"),
				"target@project.iam.gserviceaccount.com").toCredentials(null);

		assertInstanceOf(DroppedConnectionRetryingCredentials.class, built);
	}
}

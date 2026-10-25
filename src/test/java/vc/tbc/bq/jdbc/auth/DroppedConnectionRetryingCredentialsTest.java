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
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.auth.oauth2.UserCredentials;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.EOFException;
import java.io.IOException;
import java.net.SocketException;
import java.security.KeyPairGenerator;
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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives real google-auth credentials against a fake token endpoint, so the
 * failures are the exact shapes google-auth produces (#337).
 */
class DroppedConnectionRetryingCredentialsTest {

	private static final List<Duration> NO_WAIT = List.of(Duration.ZERO, Duration.ZERO);

	@BeforeEach
	@AfterEach
	void clearCache() {
		CredentialsCache.clear();
	}

	/** One scripted outcome per token call: an exception to throw, or a status. */
	private static final class FakeTokenEndpoint extends MockHttpTransport {
		final Deque<Object> outcomes = new ArrayDeque<>();
		final AtomicInteger calls = new AtomicInteger();

		FakeTokenEndpoint then(Object outcome) {
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
					// IAM and the OAuth token endpoint answer in different shapes
					String minted = url.contains("iamcredentials")
							? "{\"accessToken\":\"minted\",\"expireTime\":\"" + expiry + "\"}"
							: "{\"access_token\":\"minted\",\"expires_in\":3600,\"token_type\":\"Bearer\"}";
					return new MockLowLevelHttpResponse().setStatusCode(status).setContentType("application/json")
							.setContent(status == 200 ? minted : "{\"error\":{\"code\":" + status + "}}");
				}
			};
		}
	}

	private static GoogleCredentials impersonating(FakeTokenEndpoint iam) {
		GoogleCredentials source = GoogleCredentials
				.create(new AccessToken("source", Date.from(Instant.now().plus(1, ChronoUnit.HOURS))));
		ImpersonatedCredentials impersonated = ImpersonatedCredentials.newBuilder().setSourceCredentials(source)
				.setTargetPrincipal("target@project.iam.gserviceaccount.com").setScopes(List.of("scope"))
				.setHttpTransportFactory(() -> iam).build();
		return new DroppedConnectionRetryingCredentials(impersonated, NO_WAIT);
	}

	@Test
	void aDroppedConnectionOnTheTokenRequestIsRetried() throws Exception {
		FakeTokenEndpoint iam = new FakeTokenEndpoint().then(new SocketException("Unexpected end of file from server"))
				.then(200);

		GoogleCredentials credentials = impersonating(iam);

		assertEquals(List.of("Bearer minted"), credentials.getRequestMetadata().get("Authorization"));
		assertEquals(2, iam.calls.get());
	}

	@Test
	void retriesStopAfterTheBackoffRunsOut() {
		FakeTokenEndpoint iam = new FakeTokenEndpoint().then(new SocketException("1")).then(new SocketException("2"))
				.then(new SocketException("3")).then(200);

		IOException thrown = assertThrows(IOException.class, () -> impersonating(iam).refresh());

		assertEquals("Error requesting access token", thrown.getMessage());
		assertEquals(3, iam.calls.get());
	}

	@Test
	void anHttpErrorFromIamIsNotRetriedAndKeepsItsStatus() {
		FakeTokenEndpoint iam = new FakeTokenEndpoint().then(403).then(200);

		IOException thrown = assertThrows(IOException.class, () -> impersonating(iam).refresh());

		// ServiceErrorDetail classifies a rejected credential from this status
		assertEquals(403, assertInstanceOf(HttpResponseException.class, thrown.getCause()).getStatusCode());
		assertEquals(1, iam.calls.get());
	}

	private static GoogleCredentials user(FakeTokenEndpoint endpoint) {
		UserCredentials user = UserCredentials.newBuilder().setClientId("c").setClientSecret("s").setRefreshToken("r")
				.setQuotaProjectId("billing-project").setHttpTransportFactory(() -> endpoint).build();
		return new DroppedConnectionRetryingCredentials(user, NO_WAIT);
	}

	@Test
	void aUserRefreshTokenIsRetriedThoughGoogleAuthWrapsTheDrop() throws Exception {
		// UserCredentials turns the drop into a GoogleAuthException, which no
		// BigQuery retry rule recognises
		FakeTokenEndpoint endpoint = new FakeTokenEndpoint().then(new EOFException("connection closed")).then(200);

		GoogleCredentials credentials = user(endpoint);

		assertEquals(List.of("Bearer minted"), credentials.getRequestMetadata().get("Authorization"));
		assertEquals(2, endpoint.calls.get());
	}

	@Test
	void theQuotaProjectStillReachesTheRequest() throws Exception {
		GoogleCredentials credentials = user(new FakeTokenEndpoint().then(200));

		assertEquals(List.of("billing-project"), credentials.getRequestMetadata().get("x-goog-user-project"));
	}

	@Test
	void scopingReachesTheWrappedCredential() {
		// Unscoped impersonation asks to be scoped; BigQueryOptions acts on that
		GoogleCredentials unscoped = new DroppedConnectionRetryingCredentials(
				ImpersonatedCredentials.newBuilder()
						.setSourceCredentials(GoogleCredentials.create(new AccessToken("source", null)))
						.setTargetPrincipal("target@project.iam.gserviceaccount.com").setScopes(List.of()).build(),
				NO_WAIT);
		assertTrue(unscoped.createScopedRequired());

		GoogleCredentials scoped = unscoped.createScoped(List.of("scope"));

		assertInstanceOf(DroppedConnectionRetryingCredentials.class, scoped);
		assertFalse(scoped.createScopedRequired());
	}

	@Test
	void everyCachedCredentialGetsTheRetry() throws Exception {
		assertInstanceOf(DroppedConnectionRetryingCredentials.class,
				CredentialsCache.forAuthType(new UserOAuthAuth("c", "s", "r")));
		assertInstanceOf(DroppedConnectionRetryingCredentials.class, CredentialsCache.forAuthType(
				new ImpersonatedAuth(new AccessTokenAuth("source"), "target@project.iam.gserviceaccount.com")));
	}

	@Test
	void aServiceAccountKeyIsLeftAlone() throws Exception {
		ServiceAccountCredentials key = ServiceAccountCredentials.newBuilder()
				.setClientEmail("sa@project.iam.gserviceaccount.com")
				.setPrivateKey(KeyPairGenerator.getInstance("RSA").generateKeyPair().getPrivate()).build();

		assertSame(key, DroppedConnectionRetryingCredentials.wrap(key));
	}

	@Test
	void wrappingIsIdempotent() {
		Credentials once = DroppedConnectionRetryingCredentials
				.wrap(GoogleCredentials.create(new AccessToken("t", null)));

		assertSame(once, DroppedConnectionRetryingCredentials.wrap(once));
	}
}

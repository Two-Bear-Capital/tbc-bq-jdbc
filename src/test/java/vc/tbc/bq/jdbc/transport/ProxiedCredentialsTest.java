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
package vc.tbc.bq.jdbc.transport;

import com.google.auth.oauth2.GoogleCredentials;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import vc.tbc.bq.jdbc.auth.AuthType;
import vc.tbc.bq.jdbc.auth.CredentialsCache;
import vc.tbc.bq.jdbc.auth.ImpersonatedAuth;
import vc.tbc.bq.jdbc.auth.ServiceAccountAuth;
import vc.tbc.bq.jdbc.auth.UserOAuthAuth;
import vc.tbc.bq.jdbc.testsupport.RecordingProxyServer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Proves that minting and refreshing credentials goes through the configured
 * proxy, not just BigQuery calls.
 *
 * <p>
 * This is the half of proxy support that is easy to ship broken. Credentials
 * are built by {@code google-auth-library} over a transport it chooses for
 * itself unless one is handed to it, so a driver that configures only the API
 * client fails in {@code ServiceAccountCredentials.fromStream} — before any
 * BigQuery call — and every unit test asserting "the client has a proxy" still
 * passes. That is exactly how
 * <a href="https://github.com/googleapis/google-cloud-java/issues/13494">
 * googleapis/google-cloud-java#13494</a> reached a release.
 *
 * <p>
 * Each test therefore watches a real socket. The refresh is expected to fail —
 * {@link RecordingProxyServer} grants the tunnel and drops it — because what is
 * under test is where the request was addressed, not whether Google answered.
 *
 * @since 4.3.0
 */
class ProxiedCredentialsTest {

	private static final Duration TIMEOUT = Duration.ofSeconds(10);

	/** Where {@code google-auth-library} exchanges every kind of token. */
	private static final String TOKEN_ENDPOINT = "oauth2.googleapis.com:443";

	@AfterEach
	void releaseTransports() {
		// Each test proxies through a fresh ephemeral port, so the cached transport
		// from the last one points at a socket nobody is listening on any more.
		DriverTransports.clear();
		CredentialsCache.clear();
	}

	@Test
	void aUserOAuthRefreshIsTunnelledThroughTheProxy() throws Exception {
		try (RecordingProxyServer proxy = RecordingProxyServer.start()) {
			GoogleCredentials credentials = credentialsFor(
					new UserOAuthAuth("client-id", "client-secret", "refresh-token"), proxy);

			assertThrows(IOException.class, credentials::refresh);

			RecordingProxyServer.Connect connect = proxy.awaitConnect(TIMEOUT);
			assertNotNull(connect, "the token request never reached the proxy — it went direct");
			assertEquals(TOKEN_ENDPOINT, connect.target());
		}
	}

	@Test
	void aServiceAccountRefreshIsTunnelledThroughTheProxy() throws Exception {
		try (RecordingProxyServer proxy = RecordingProxyServer.start()) {
			GoogleCredentials credentials = credentialsFor(new ServiceAccountAuth(writeServiceAccountKey()), proxy);

			assertThrows(IOException.class, credentials::refresh);

			RecordingProxyServer.Connect connect = proxy.awaitConnect(TIMEOUT);
			assertNotNull(connect, "the token request never reached the proxy — it went direct");
			assertEquals(TOKEN_ENDPOINT, connect.target());
		}
	}

	@Test
	void impersonationTunnelsTheSourceIdentityToo() throws Exception {
		// Impersonation is two token exchanges. The first one — minting the source
		// identity's token — is the one a wrapper can forget to pass the transport to.
		try (RecordingProxyServer proxy = RecordingProxyServer.start()) {
			AuthType impersonated = new ImpersonatedAuth(new ServiceAccountAuth(writeServiceAccountKey()),
					"target@example.iam.gserviceaccount.com");
			GoogleCredentials credentials = credentialsFor(impersonated, proxy);

			assertThrows(IOException.class, credentials::refresh);

			RecordingProxyServer.Connect connect = proxy.awaitConnect(TIMEOUT);
			assertNotNull(connect, "the source identity's token request never reached the proxy");
			assertEquals(TOKEN_ENDPOINT, connect.target());
		}
	}

	@Test
	void anAuthenticatedProxyIsAnsweredWithProxyAuthorization() throws Exception {
		try (RecordingProxyServer proxy = RecordingProxyServer.requiringAuth("proxy-user", "proxy-secret")) {
			TransportConfig config = TransportConfig
					.of(new ProxyConfig("localhost", proxy.port(), "proxy-user", "proxy-secret"), null);
			GoogleCredentials credentials = (GoogleCredentials) CredentialsCache
					.forAuthType(new UserOAuthAuth("client-id", "client-secret", "refresh-token"), config);

			assertThrows(IOException.class, credentials::refresh);

			// The first attempt is anonymous — Basic is only offered once the proxy has
			// challenged for it, so the header appears on the retry.
			RecordingProxyServer.Connect unauthenticated = proxy.awaitConnect(TIMEOUT);
			assertNotNull(unauthenticated, "the token request never reached the proxy");
			assertNull(unauthenticated.basicCredentials());

			RecordingProxyServer.Connect authenticated = proxy.awaitConnect(TIMEOUT);
			assertNotNull(authenticated, "the client never answered the 407 challenge");
			assertEquals("proxy-user:proxy-secret", authenticated.basicCredentials());
			assertEquals(TOKEN_ENDPOINT, authenticated.target());
		}
	}

	/**
	 * Builds credentials routed through {@code proxy}, the way a connection does.
	 */
	private static GoogleCredentials credentialsFor(AuthType authType, RecordingProxyServer proxy) throws IOException {
		TransportConfig config = TransportConfig.of(new ProxyConfig("localhost", proxy.port(), null, null), null);
		return (GoogleCredentials) CredentialsCache.forAuthType(authType, config);
	}

	/**
	 * Writes a service account key file with a freshly generated private key.
	 *
	 * <p>
	 * A real key, because {@code ServiceAccountCredentials.fromStream} parses it
	 * while building the credential — a placeholder string fails there and the test
	 * would never reach the token request it is about.
	 */
	private static String writeServiceAccountKey() throws Exception {
		KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(2048);
		KeyPair keyPair = generator.generateKeyPair();
		String privateKey = "-----BEGIN PRIVATE KEY-----\\n"
				+ Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded())
				+ "\\n-----END PRIVATE KEY-----\\n";

		Path keyFile = Files.createTempFile("tbc-bq-jdbc-proxy-test", ".json");
		keyFile.toFile().deleteOnExit();
		Files.writeString(keyFile, """
				{
				  "type": "service_account",
				  "project_id": "test-project",
				  "private_key_id": "test-key-id",
				  "private_key": "%s",
				  "client_email": "test@test-project.iam.gserviceaccount.com",
				  "client_id": "123456789"
				}
				""".formatted(privateKey));
		return keyFile.toString();
	}
}

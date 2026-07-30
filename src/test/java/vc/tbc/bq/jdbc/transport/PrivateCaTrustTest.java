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

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vc.tbc.bq.jdbc.testsupport.RecordingProxyServer;
import vc.tbc.bq.jdbc.testsupport.TestCertificates;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the whole reason {@link TlsConfig} exists, against a real TLS
 * endpoint whose certificate the JDK has never heard of.
 *
 * <p>
 * The failure this prevents is {@code PKIX path building failed} on every
 * connection from a network that re-signs egress with a corporate CA. A test
 * that only asserted the transport carried an {@code SSLContext} would not
 * distinguish a working truststore from one wired to nothing, so each case here
 * completes — or fails — a genuine TLS handshake.
 *
 * @since 4.3.0
 */
class PrivateCaTrustTest {

	@TempDir
	static Path certificateDirectory;

	private static TestCertificates certificates;

	@BeforeAll
	static void issueCertificate() throws Exception {
		certificates = TestCertificates.generate(certificateDirectory);
	}

	@AfterEach
	void releaseTransports() {
		// Each test issues its own server, so a transport cached against the last
		// one would be reused against a socket nobody is listening on.
		DriverTransports.clear();
	}

	@Test
	void theDefaultTransportRejectsAPrivateCertificateAuthority() throws Exception {
		// The state of the world this feature exists to change
		try (TlsServer server = TlsServer.start(certificates)) {
			HttpTransport transport = DriverTransports.forTransport(TransportConfig.direct()).create();

			SSLHandshakeException e = assertThrows(SSLHandshakeException.class, () -> get(transport, server.url()));

			assertTrue(e.getMessage().contains("PKIX path building failed"),
					"expected the PKIX failure this feature is about, got: " + e.getMessage());
		}
	}

	@Test
	void aTrustStoreNamingTheAuthorityIsAccepted() throws Exception {
		try (TlsServer server = TlsServer.start(certificates)) {
			TlsConfig tls = new TlsConfig(certificates.trustStore().toString(), certificates.password(), "PKCS12",
					null);
			HttpTransport transport = DriverTransports.forTransport(TransportConfig.of(null, tls)).create();

			assertEquals("hello", get(transport, server.url()));
		}
	}

	@Test
	void theStoreTypeCanBeLeftToTheJvmDefault() throws Exception {
		// PKCS12 is the JVM default on a current JDK, so omitting trustStoreType is
		// a working configuration rather than a missing one — which is why, unlike
		// proxyPort, it is not required
		try (TlsServer server = TlsServer.start(certificates)) {
			TlsConfig tls = new TlsConfig(certificates.trustStore().toString(), certificates.password(), null, null);
			HttpTransport transport = DriverTransports.forTransport(TransportConfig.of(null, tls)).create();

			assertEquals("hello", get(transport, server.url()));
		}
	}

	@Test
	void aProxyAndAPrivateAuthorityComposeOnOneTransport() throws Exception {
		// The case the issue is actually about: a TLS-inspecting proxy needs both at
		// once. This also covers the Apache stack applying the SSLContext, which it
		// does through a different call than NetHttpTransport does.
		try (TlsServer server = TlsServer.start(certificates);
				RecordingProxyServer proxy = RecordingProxyServer.relaying()) {
			TlsConfig tls = new TlsConfig(certificates.trustStore().toString(), certificates.password(), "PKCS12",
					null);
			ProxyConfig proxyConfig = new ProxyConfig("localhost", proxy.port(), null, null);
			HttpTransport transport = DriverTransports.forTransport(TransportConfig.of(proxyConfig, tls)).create();

			assertEquals("hello", get(transport, server.url()));

			RecordingProxyServer.Connect connect = proxy.awaitConnect(Duration.ofSeconds(10));
			assertNotNull(connect, "the request bypassed the proxy");
			assertEquals("localhost:" + server.port(), connect.target());
		}
	}

	@Test
	void aProxiedRequestStillRejectsAnUntrustedAuthority() throws Exception {
		// Guards the inverse of the case above: a proxy must not become a way to
		// skip certificate verification
		try (TlsServer server = TlsServer.start(certificates);
				RecordingProxyServer proxy = RecordingProxyServer.relaying()) {
			ProxyConfig proxyConfig = new ProxyConfig("localhost", proxy.port(), null, null);
			HttpTransport transport = DriverTransports.forTransport(TransportConfig.of(proxyConfig, null)).create();

			assertThrows(SSLHandshakeException.class, () -> get(transport, server.url()));
		}
	}

	@Test
	void aMissingTrustStoreIsReportedWithItsPath() {
		TlsConfig tls = new TlsConfig(certificateDirectory.resolve("absent.p12").toString(), "changeit", "PKCS12",
				null);

		IOException e = assertThrows(IOException.class,
				() -> DriverTransports.forTransport(TransportConfig.of(null, tls)));

		assertTrue(e.getMessage().contains("absent.p12"), "the error should name the store: " + e.getMessage());
	}

	@Test
	void aWrongTrustStorePasswordDoesNotLeakIt() {
		TlsConfig tls = new TlsConfig(certificates.trustStore().toString(), "not-the-password", "PKCS12", null);

		IOException e = assertThrows(IOException.class,
				() -> DriverTransports.forTransport(TransportConfig.of(null, tls)));

		assertFalse(String.valueOf(e.getMessage()).contains("not-the-password"),
				"the password must not reach the message: " + e.getMessage());
	}

	/** Issues a GET through {@code transport} and returns the body. */
	private static String get(HttpTransport transport, GenericUrl url) throws IOException {
		HttpResponse response = transport.createRequestFactory().buildGetRequest(url).execute();
		try (InputStream content = response.getContent()) {
			return new String(content.readAllBytes(), StandardCharsets.UTF_8).trim();
		} finally {
			response.disconnect();
		}
	}

	/** An HTTPS server presenting the generated certificate. */
	private record TlsServer(HttpsServer server) implements AutoCloseable {

		static TlsServer start(TestCertificates certificates) throws Exception {
			KeyStore keyStore = KeyStore.getInstance("PKCS12");
			try (InputStream in = Files.newInputStream(certificates.serverKeyStore())) {
				keyStore.load(in, certificates.password().toCharArray());
			}
			KeyManagerFactory keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
			keyManagers.init(keyStore, certificates.password().toCharArray());

			SSLContext context = SSLContext.getInstance("TLS");
			context.init(keyManagers.getKeyManagers(), null, null);

			HttpsServer server = HttpsServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
			server.setHttpsConfigurator(new HttpsConfigurator(context));
			server.createContext("/", exchange -> {
				byte[] body = "hello".getBytes(StandardCharsets.UTF_8);
				exchange.sendResponseHeaders(200, body.length);
				try (var out = exchange.getResponseBody()) {
					out.write(body);
				}
			});
			server.start();
			return new TlsServer(server);
		}

		int port() {
			return server.getAddress().getPort();
		}

		GenericUrl url() {
			// "localhost" rather than the address, so hostname verification runs
			// against the certificate's SAN as it would in production
			return new GenericUrl("https://localhost:" + port() + "/");
		}

		@Override
		public void close() {
			server.stop(0);
		}
	}
}

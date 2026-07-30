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
package vc.tbc.bq.jdbc;

import com.google.auth.Credentials;
import com.google.cloud.bigquery.BigQueryOptions;
import com.google.cloud.http.HttpTransportOptions;
import org.junit.jupiter.api.Test;
import vc.tbc.bq.jdbc.config.ConnectionProperties;
import vc.tbc.bq.jdbc.transport.DriverTransports;
import vc.tbc.bq.jdbc.transport.ProxyConfig;
import vc.tbc.bq.jdbc.transport.TransportConfig;

import static vc.tbc.bq.jdbc.testsupport.TestConnectionProperties.props;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@code retryCount}, {@code connectionTimeout} and
 * {@code host}/{@code port} reach the BigQuery client rather than being parsed
 * and discarded.
 *
 * <p>
 * All three were advertised through {@code Driver.getPropertyInfo()} — so they
 * appeared in the generated property reference — while {@code retryCount} and
 * {@code connectionTimeout} had no read sites anywhere in {@code src/main}.
 * Nothing asserted the wiring, which is why that went unnoticed.
 *
 * <p>
 * Asserts against {@link BQConnection#buildOptions} rather than opening a
 * connection: a unit test must not depend on ambient credentials existing on
 * the machine running it.
 *
 * @since 3.0.0
 */
class ClientTuningPropertiesTest {

	/** A credential that performs no I/O. */
	private static final Credentials NO_OP_CREDENTIALS = new Credentials() {
		@Override
		public String getAuthenticationType() {
			return "test";
		}

		@Override
		public Map<String, List<String>> getRequestMetadata(URI uri) {
			return Map.of();
		}

		@Override
		public boolean hasRequestMetadata() {
			return false;
		}

		@Override
		public boolean hasRequestMetadataOnly() {
			return false;
		}

		@Override
		public void refresh() {
			// nothing to refresh
		}
	};

	/** Properties carrying only the fields under test. */
	private static ConnectionProperties propertiesWith(Integer retryCount, Integer connectionTimeout, String host,
			Integer port) {
		return props().retryCount(retryCount).connectionTimeout(connectionTimeout).host(host).port(port).build();
	}

	private static BigQueryOptions optionsFor(Integer retryCount, Integer connectionTimeout, String host, Integer port)
			throws IOException {
		return BQConnection.buildOptions(propertiesWith(retryCount, connectionTimeout, host, port), NO_OP_CREDENTIALS)
				.build();
	}

	@Test
	void retryCountBecomesMaxAttempts() throws IOException {
		// Given: a connection asking for 7 attempts
		BigQueryOptions options = optionsFor(7, null, null, null);

		// Then: the client is configured to make that many
		assertEquals(7, options.getRetrySettings().getMaxAttempts());
	}

	@Test
	void retryCountOfZeroStillAllowsOneAttempt() throws IOException {
		// Given: retryCount=0, which must not mean "never contact BigQuery"
		BigQueryOptions options = optionsFor(0, null, null, null);

		// Then: exactly one attempt — the request itself, with no retries
		assertEquals(1, options.getRetrySettings().getMaxAttempts());
	}

	@Test
	void defaultRetryCountMatchesTheClientLibrary() throws IOException {
		// Given: the driver's documented default
		BigQueryOptions options = optionsFor(ConnectionProperties.DEFAULT_RETRY_COUNT, null, null, null);

		// Then: applying it is a no-op, so a connection that never sets retryCount
		// keeps exactly the resilience it had before the property worked
		assertEquals(BigQueryOptions.getDefaultRetrySettings().getMaxAttempts(),
				options.getRetrySettings().getMaxAttempts());
	}

	@Test
	void connectionTimeoutBecomesConnectTimeoutInMillis() throws IOException {
		// Given: a 45-second connection timeout
		BigQueryOptions options = optionsFor(null, 45, null, null);

		// Then: the transport is configured in milliseconds
		HttpTransportOptions transport = (HttpTransportOptions) options.getTransportOptions();
		assertEquals(45_000, transport.getConnectTimeout());
	}

	@Test
	void connectionTimeoutDoesNotCapReadTimeout() throws IOException {
		// Given: a short connection timeout
		BigQueryOptions options = optionsFor(null, 5, null, null);
		HttpTransportOptions transport = (HttpTransportOptions) options.getTransportOptions();

		// Then: the read timeout is untouched, so a long-running query governed by
		// the `timeout` property is not severed by the connect setting
		assertNotEquals(5_000, transport.getReadTimeout());
		assertEquals(BigQueryOptions.getDefaultHttpTransportOptions().getReadTimeout(), transport.getReadTimeout());
	}

	@Test
	void aCustomHostDefaultsToHttps() throws IOException {
		// Given: a host with no scheme and an explicit port — the shape that used to
		// hard-code http:// and send credentials in the clear
		BigQueryOptions options = optionsFor(null, null, "bigquery.internal.example.com", 8443);

		assertEquals("https://bigquery.internal.example.com:8443", options.getHost());
	}

	@Test
	void anExplicitSchemeIsHonoured() throws IOException {
		// Given: a host that opts into plaintext deliberately
		BigQueryOptions options = optionsFor(null, null, "http://localhost", 9050);

		assertEquals("http://localhost:9050", options.getHost());
	}

	@Test
	void aHostWithoutAPortStillGetsAScheme() throws IOException {
		BigQueryOptions options = optionsFor(null, null, "bigquery.internal.example.com", null);

		assertTrue(options.getHost().startsWith("https://"), "expected https, got " + options.getHost());
	}

	@Test
	void aProxyReachesTheClientTransport() throws Exception {
		ConnectionProperties properties = props()
				.transport(TransportConfig.of(new ProxyConfig("proxy.example.com", 3128, null, null), null)).build();

		HttpTransportOptions transport = (HttpTransportOptions) BQConnection.buildOptions(properties, NO_OP_CREDENTIALS)
				.build().getTransportOptions();

		assertSame(DriverTransports.forTransport(properties.transport()), transport.getHttpTransportFactory(),
				"the client must use the same transport the connection's credentials were built on");
	}

	@Test
	void aProxyAndAConnectTimeoutComposeIntoOneTransportOptions() throws Exception {
		// They are two fields of the same object. Setting them through separate
		// setTransportOptions calls would mean the second silently discarding the
		// first, which is why buildOptions builds one HttpTransportOptions.
		ConnectionProperties properties = props().connectionTimeout(45)
				.transport(TransportConfig.of(new ProxyConfig("proxy.example.com", 3128, null, null), null)).build();

		HttpTransportOptions transport = (HttpTransportOptions) BQConnection.buildOptions(properties, NO_OP_CREDENTIALS)
				.build().getTransportOptions();

		assertEquals(45_000, transport.getConnectTimeout());
		assertSame(DriverTransports.forTransport(properties.transport()), transport.getHttpTransportFactory());
	}

	@Test
	void noProxyLeavesTheClientLibraryTransportAlone() throws Exception {
		// The unproxied path must keep the client's own default rather than an
		// equivalent-looking copy of it
		HttpTransportOptions transport = (HttpTransportOptions) optionsFor(null, 45, null, null).getTransportOptions();

		assertEquals(BigQueryOptions.getDefaultHttpTransportOptions().getHttpTransportFactory().getClass(),
				transport.getHttpTransportFactory().getClass());
	}
}

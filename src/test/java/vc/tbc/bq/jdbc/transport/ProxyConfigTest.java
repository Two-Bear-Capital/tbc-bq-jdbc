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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers how a proxy is resolved from properties, and what it refuses to guess.
 *
 * @since 4.3.0
 */
class ProxyConfigTest {

	@AfterEach
	void clearSystemProperties() {
		System.clearProperty(ProxyConfig.HOST_SYSTEM_PROPERTY);
		System.clearProperty(ProxyConfig.PORT_SYSTEM_PROPERTY);
		System.clearProperty(ProxyConfig.USER_SYSTEM_PROPERTY);
		System.clearProperty(ProxyConfig.PASSWORD_SYSTEM_PROPERTY);
	}

	@Test
	void nothingConfiguredMeansNoProxy() {
		assertNull(ProxyConfig.resolve(null, null, null, null));
	}

	@Test
	void aBlankHostIsTreatedAsUnset() {
		// A tool that writes every property it knows about emits proxyHost= for the
		// ones left empty; that must not be read as "proxy through the empty string"
		assertNull(ProxyConfig.resolve("   ", null, null, null));
	}

	@Test
	void hostAndPortResolveToAProxy() {
		ProxyConfig proxy = ProxyConfig.resolve("proxy.example.com", 3128, null, null);

		assertEquals("proxy.example.com", proxy.host());
		assertEquals(3128, proxy.port());
		assertFalse(proxy.isAuthenticated());
	}

	@Test
	void credentialsMakeTheProxyAuthenticated() {
		ProxyConfig proxy = ProxyConfig.resolve("proxy.example.com", 3128, "someone", "secret");

		assertTrue(proxy.isAuthenticated());
		assertEquals("someone", proxy.user());
		assertEquals("secret", proxy.password());
	}

	@Test
	void aHostWithoutAPortIsRejectedRatherThanGuessed() {
		// There is no conventional outbound proxy port. A guess would fail as a
		// connection refused against an unrelated port, naming nothing useful.
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
				() -> ProxyConfig.resolve("proxy.example.com", null, null, null));

		assertTrue(e.getMessage().contains("proxyPort"), e.getMessage());
	}

	@Test
	void proxyCredentialsWithoutAHostAreRejected() {
		// Ignoring them would connect direct, which is the one outcome the caller
		// who set a proxy username did not want.
		assertThrows(IllegalArgumentException.class, () -> ProxyConfig.resolve(null, null, "someone", "secret"));
	}

	@Test
	void aPortWithoutAHostIsRejected() {
		assertThrows(IllegalArgumentException.class, () -> ProxyConfig.resolve(null, 3128, null, null));
	}

	@Test
	void aPasswordWithoutAUserIsRejected() {
		// Proxy-Authorization carries both, so a lone password cannot be sent
		assertThrows(IllegalArgumentException.class,
				() -> ProxyConfig.resolve("proxy.example.com", 3128, null, "secret"));
	}

	@Test
	void aPortOutsideTheValidRangeIsRejected() {
		assertThrows(IllegalArgumentException.class, () -> ProxyConfig.resolve("proxy.example.com", 0, null, null));
		assertThrows(IllegalArgumentException.class, () -> ProxyConfig.resolve("proxy.example.com", 70000, null, null));
	}

	@Test
	void theJvmProxyPropertiesAreHonouredWhenNoneIsConfigured() {
		// grpc-java reads these for the Storage Read API path whatever the driver
		// does, so ignoring them would send the REST and gRPC halves to different
		// places on the same connection
		System.setProperty(ProxyConfig.HOST_SYSTEM_PROPERTY, "jvm-proxy.example.com");
		System.setProperty(ProxyConfig.PORT_SYSTEM_PROPERTY, "8080");

		ProxyConfig proxy = ProxyConfig.resolve(null, null, null, null);

		assertEquals("jvm-proxy.example.com", proxy.host());
		assertEquals(8080, proxy.port());
	}

	@Test
	void theJvmPortKeepsTheJdkDefaultWhenUnset() {
		// Unlike the driver's own proxyPort, which is required: a caller setting
		// https.proxyHost is relying on the JDK's documented contract
		System.setProperty(ProxyConfig.HOST_SYSTEM_PROPERTY, "jvm-proxy.example.com");

		assertEquals(ProxyConfig.DEFAULT_SYSTEM_PROPERTY_PORT, ProxyConfig.resolve(null, null, null, null).port());
	}

	@Test
	void jvmProxyCredentialsAreHonoured() {
		System.setProperty(ProxyConfig.HOST_SYSTEM_PROPERTY, "jvm-proxy.example.com");
		System.setProperty(ProxyConfig.PORT_SYSTEM_PROPERTY, "8080");
		System.setProperty(ProxyConfig.USER_SYSTEM_PROPERTY, "someone");
		System.setProperty(ProxyConfig.PASSWORD_SYSTEM_PROPERTY, "secret");

		ProxyConfig proxy = ProxyConfig.resolve(null, null, null, null);

		assertTrue(proxy.isAuthenticated());
		assertEquals("someone", proxy.user());
	}

	@Test
	void anExplicitProxyWinsOverTheJvmProperties() {
		System.setProperty(ProxyConfig.HOST_SYSTEM_PROPERTY, "jvm-proxy.example.com");
		System.setProperty(ProxyConfig.PORT_SYSTEM_PROPERTY, "8080");

		ProxyConfig proxy = ProxyConfig.resolve("explicit.example.com", 3128, null, null);

		assertEquals("explicit.example.com", proxy.host());
		assertEquals(3128, proxy.port());
	}

	@Test
	void anExplicitProxyDoesNotInheritJvmCredentials() {
		// Half a proxy from each source is a configuration nobody wrote
		System.setProperty(ProxyConfig.USER_SYSTEM_PROPERTY, "jvm-user");
		System.setProperty(ProxyConfig.PASSWORD_SYSTEM_PROPERTY, "jvm-secret");

		assertFalse(ProxyConfig.resolve("explicit.example.com", 3128, null, null).isAuthenticated());
	}

	@Test
	void anUnparseableJvmPortIsRejected() {
		System.setProperty(ProxyConfig.HOST_SYSTEM_PROPERTY, "jvm-proxy.example.com");
		System.setProperty(ProxyConfig.PORT_SYSTEM_PROPERTY, "not-a-port");

		assertThrows(IllegalArgumentException.class, () -> ProxyConfig.resolve(null, null, null, null));
	}

	@Test
	void theProxyPasswordIsNeverRendered() {
		// ConnectionProperties embeds this, and anything may log that record
		ProxyConfig proxy = new ProxyConfig("proxy.example.com", 3128, "someone", "hunter2");

		assertFalse(proxy.toString().contains("hunter2"), proxy.toString());
		assertTrue(proxy.toString().contains("proxy.example.com:3128"), proxy.toString());
	}

	@Test
	void proxiesDifferingOnlyByCredentialsAreDistinctCacheKeys() {
		// CredentialsCache keys on this, and a credential holds the transport it
		// refreshes over — so these must not collapse into one entry
		ProxyConfig anonymous = new ProxyConfig("proxy.example.com", 3128, null, null);
		ProxyConfig authenticated = new ProxyConfig("proxy.example.com", 3128, "someone", "secret");

		assertNotEquals(anonymous, authenticated);
		assertEquals(anonymous, new ProxyConfig("proxy.example.com", 3128, null, null));
	}
}

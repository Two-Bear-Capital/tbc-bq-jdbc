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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers how a truststore is resolved from properties, and what it refuses to
 * infer.
 *
 * @since 4.3.0
 */
class TlsConfigTest {

	@AfterEach
	void clearSystemProperties() {
		System.clearProperty(TlsConfig.PATH_SYSTEM_PROPERTY);
		System.clearProperty(TlsConfig.PASSWORD_SYSTEM_PROPERTY);
		System.clearProperty(TlsConfig.TYPE_SYSTEM_PROPERTY);
		System.clearProperty(TlsConfig.PROVIDER_SYSTEM_PROPERTY);
	}

	@Test
	void nothingConfiguredMeansTheJdkTrustStore() {
		assertNull(TlsConfig.resolve(null, null, null, null));
	}

	@Test
	void aBlankPathIsTreatedAsUnset() {
		assertNull(TlsConfig.resolve("  ", null, null, null));
	}

	@Test
	void aPathAloneIsEnough() {
		// Unlike proxyPort, the companions all have real JVM defaults, so a path on
		// its own is a working configuration rather than a half-written one
		TlsConfig tls = TlsConfig.resolve("/etc/pki/corp.p12", null, null, null);

		assertEquals("/etc/pki/corp.p12", tls.path());
		assertNull(tls.password());
		assertNull(tls.type());
		assertNull(tls.provider());
	}

	@Test
	void allFourPropertiesAreCarried() {
		TlsConfig tls = TlsConfig.resolve("/etc/pki/corp.p12", "secret", "PKCS12", "SunJSSE");

		assertEquals("secret", tls.password());
		assertEquals("PKCS12", tls.type());
		assertEquals("SunJSSE", tls.provider());
	}

	@Test
	void aCompanionWithoutAPathIsRejected() {
		// Silently verifying against the JDK's own store is the one outcome the
		// caller who named a store type did not want, and the PKIX error it
		// eventually produces says nothing about this
		assertThrows(IllegalArgumentException.class, () -> TlsConfig.resolve(null, "secret", null, null));
		assertThrows(IllegalArgumentException.class, () -> TlsConfig.resolve(null, null, "PKCS12", null));
		assertThrows(IllegalArgumentException.class, () -> TlsConfig.resolve(null, null, null, "SunJSSE"));
	}

	@Test
	void theJvmTrustStorePropertiesAreHonouredWhenNoneIsConfigured() {
		// So these properties are only needed to override a JVM that is already
		// configured, rather than to repeat it
		System.setProperty(TlsConfig.PATH_SYSTEM_PROPERTY, "/etc/pki/jvm.p12");
		System.setProperty(TlsConfig.PASSWORD_SYSTEM_PROPERTY, "jvm-secret");
		System.setProperty(TlsConfig.TYPE_SYSTEM_PROPERTY, "JKS");

		TlsConfig tls = TlsConfig.resolve(null, null, null, null);

		assertEquals("/etc/pki/jvm.p12", tls.path());
		assertEquals("jvm-secret", tls.password());
		assertEquals("JKS", tls.type());
	}

	@Test
	void anExplicitTrustStoreWinsOverTheJvmProperties() {
		System.setProperty(TlsConfig.PATH_SYSTEM_PROPERTY, "/etc/pki/jvm.p12");
		System.setProperty(TlsConfig.PASSWORD_SYSTEM_PROPERTY, "jvm-secret");

		TlsConfig tls = TlsConfig.resolve("/etc/pki/explicit.p12", null, null, null);

		assertEquals("/etc/pki/explicit.p12", tls.path());
		// Half a truststore from each source is a configuration nobody wrote
		assertNull(tls.password());
	}

	@Test
	void theStorePasswordIsNeverRendered() {
		TlsConfig tls = new TlsConfig("/etc/pki/corp.p12", "hunter2", "PKCS12", null);

		assertFalse(tls.toString().contains("hunter2"), tls.toString());
		assertTrue(tls.toString().contains("/etc/pki/corp.p12"), tls.toString());
	}

	@Test
	void storesDifferingOnlyByPasswordAreDistinctCacheKeys() {
		// DriverTransports and CredentialsCache both key on this
		assertNotEquals(new TlsConfig("/etc/pki/corp.p12", "one", null, null),
				new TlsConfig("/etc/pki/corp.p12", "two", null, null));
	}

	@Test
	void anUnconfiguredTransportIsTheSharedDefault() {
		// So properties.transport() is never null and callers need no null check
		assertSame(TransportConfig.direct(), TransportConfig.of(null, null));
		assertTrue(TransportConfig.direct().isDefault());
	}

	@Test
	void eitherHalfAloneIsNotTheDefault() {
		assertFalse(TransportConfig.of(new ProxyConfig("proxy.example.com", 3128, null, null), null).isDefault());
		assertFalse(TransportConfig.of(null, new TlsConfig("/etc/pki/corp.p12", null, null, null)).isDefault());
	}
}

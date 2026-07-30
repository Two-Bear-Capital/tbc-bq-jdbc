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

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Objects;

/**
 * A truststore naming the certificate authorities the driver will trust.
 *
 * <p>
 * The case this exists for is a TLS-inspecting middlebox: egress is
 * re-encrypted with a corporate CA that the JDK's own truststore has never
 * heard of, and every connection fails with {@code PKIX path building failed}.
 * Without this the only remedies are editing the JVM's global truststore or
 * passing {@code -Djavax.net.ssl.trustStore} for the whole process — neither
 * available to someone configuring a driver inside an IDE or a BI tool, which
 * is where this driver is usually configured.
 *
 * <p>
 * Only built by {@link #resolve(String, String, String, String)}, which returns
 * {@code null} when nothing asked for a custom truststore, so a non-null value
 * always means "verify against this instead of the default".
 *
 * <p>
 * <b>This replaces the default trust anchors rather than adding to them.</b>
 * That is how a {@link TrustManagerFactory} built from a {@link KeyStore}
 * behaves, and it matches every other JVM truststore setting; a store holding
 * only a corporate CA will not verify a public certificate. Anyone needing both
 * imports the public roots into their store, which is what the tooling that
 * produces these stores already does.
 *
 * @param path
 *            filesystem path to the truststore, never blank
 * @param password
 *            password protecting it, or null for an unprotected store
 * @param type
 *            store format, e.g. {@code JKS} or {@code PKCS12}; null means the
 *            JVM default
 * @param provider
 *            JCE provider name to load the store through, or null for the
 *            default search order
 * @since 4.3.0
 */
public record TlsConfig(String path, String password, String type, String provider) {

	/** System property naming a truststore, read when no property is set. */
	public static final String PATH_SYSTEM_PROPERTY = "javax.net.ssl.trustStore";

	/** System property for {@link #PATH_SYSTEM_PROPERTY}'s password. */
	public static final String PASSWORD_SYSTEM_PROPERTY = "javax.net.ssl.trustStorePassword";

	/** System property for {@link #PATH_SYSTEM_PROPERTY}'s format. */
	public static final String TYPE_SYSTEM_PROPERTY = "javax.net.ssl.trustStoreType";

	/** System property for {@link #PATH_SYSTEM_PROPERTY}'s JCE provider. */
	public static final String PROVIDER_SYSTEM_PROPERTY = "javax.net.ssl.trustStoreProvider";

	public TlsConfig {
		Objects.requireNonNull(path, "truststore path cannot be null");
		path = path.trim();
		if (path.isEmpty()) {
			throw new IllegalArgumentException("trustStore cannot be blank");
		}
		password = blankToNull(password);
		type = blankToNull(type);
		provider = blankToNull(provider);
	}

	/**
	 * Builds the truststore a connection should verify against, or {@code null} for
	 * the JDK default.
	 *
	 * <p>
	 * Explicit properties win outright. With no {@code trustStore} set, the
	 * standard JVM {@code javax.net.ssl.trustStore*} properties are read instead,
	 * and all four come from there rather than being mixed with driver properties.
	 * Honouring them means these properties are only needed to <em>override</em> a
	 * JVM that is already configured, rather than to repeat it.
	 *
	 * <p>
	 * Unlike {@code proxyPort}, none of the three companions is required: the JVM
	 * has a real default for each — {@link KeyStore#getDefaultType()} and the
	 * standard provider search — so leaving them unset is a working configuration
	 * rather than a missing one. Only the path has no default worth guessing.
	 *
	 * @param path
	 *            the {@code trustStore} property, or null
	 * @param password
	 *            the {@code trustStorePassword} property, or null
	 * @param type
	 *            the {@code trustStoreType} property, or null
	 * @param provider
	 *            the {@code trustStoreProvider} property, or null
	 * @return the truststore to use, or null to keep the JDK's own
	 * @throws IllegalArgumentException
	 *             if a companion property is set without a path
	 */
	public static TlsConfig resolve(String path, String password, String type, String provider) {
		String explicitPath = blankToNull(path);
		if (explicitPath != null) {
			return new TlsConfig(explicitPath, password, type, provider);
		}
		// Rejected rather than ignored: silently verifying against the JDK's
		// truststore is the one outcome the caller who named a store type did not
		// want, and it fails later as a PKIX error that says nothing about this.
		if (blankToNull(password) != null || blankToNull(type) != null || blankToNull(provider) != null) {
			throw new IllegalArgumentException(
					"trustStorePassword, trustStoreType and trustStoreProvider require trustStore");
		}
		return fromSystemProperties();
	}

	/**
	 * Reads a truststore from the standard JVM system properties, or returns null.
	 *
	 * @return the JVM-configured truststore, or null when none is set
	 */
	static TlsConfig fromSystemProperties() {
		String path = blankToNull(System.getProperty(PATH_SYSTEM_PROPERTY));
		if (path == null) {
			return null;
		}
		return new TlsConfig(path, System.getProperty(PASSWORD_SYSTEM_PROPERTY),
				System.getProperty(TYPE_SYSTEM_PROPERTY), System.getProperty(PROVIDER_SYSTEM_PROPERTY));
	}

	/**
	 * Builds an {@link SSLContext} trusting the authorities in this store.
	 *
	 * <p>
	 * One context serves both HTTP stacks: {@code NetHttpTransport} takes its
	 * socket factory and Apache HttpClient wraps it, so the trust decision cannot
	 * differ between a proxied and an unproxied connection.
	 *
	 * @return a context whose trust anchors are this store's
	 * @throws IOException
	 *             if the store cannot be read, or its password is wrong
	 * @throws GeneralSecurityException
	 *             if the store cannot be parsed or the provider is unknown
	 */
	public SSLContext toSslContext() throws IOException, GeneralSecurityException {
		KeyStore trustStore = newKeyStore();
		try (InputStream in = Files.newInputStream(Path.of(path))) {
			// A null password is meaningful, not a placeholder: it loads a store
			// without verifying its integrity check, which is how an unprotected
			// truststore is read. char[0] would be a wrong password instead.
			trustStore.load(in, password == null ? null : password.toCharArray());
		}
		TrustManagerFactory trustManagers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
		trustManagers.init(trustStore);

		SSLContext context = SSLContext.getInstance("TLS");
		// Null key managers and null SecureRandom keep the platform defaults; only
		// the trust decision is being replaced here.
		context.init(null, trustManagers.getTrustManagers(), null);
		return context;
	}

	/** Creates the empty store, honouring an explicit type and provider. */
	private KeyStore newKeyStore() throws GeneralSecurityException {
		String storeType = type == null ? KeyStore.getDefaultType() : type;
		if (provider == null) {
			return KeyStore.getInstance(storeType);
		}
		return KeyStore.getInstance(storeType, provider);
	}

	/**
	 * Renders the truststore for a log line, without the password.
	 *
	 * <p>
	 * Overridden for the same reason as {@link ProxyConfig#toString()}: a record
	 * prints every component, and this one is reachable from
	 * {@code ConnectionProperties}, which anything may log.
	 *
	 * @return the path, and the type if one was named
	 */
	@Override
	public String toString() {
		return type == null ? path : path + " (" + type + ")";
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}

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

import java.util.Objects;

/**
 * An HTTP proxy the driver routes BigQuery and OAuth traffic through.
 *
 * <p>
 * A value of this type is only ever built by
 * {@link #resolve(String, Integer, String, String)}, which returns {@code null}
 * when nothing asked for a proxy. A non-null {@code ProxyConfig} therefore
 * always means "proxy the connection", and no caller needs a second "is it
 * enabled" flag.
 *
 * <p>
 * <b>This is part of the {@code CredentialsCache} key.</b> Credentials are
 * shared across connections that authenticate the same way, but a credential
 * carries the transport it refreshes over, so two connections that agree on
 * authentication and disagree on the proxy must not share one. Being a record
 * is what makes that key work.
 *
 * @param host
 *            proxy hostname or address, never blank
 * @param port
 *            proxy port, 1-65535
 * @param user
 *            username for a proxy demanding {@code Proxy-Authorization}, or
 *            null
 * @param password
 *            password for {@code user}, or null
 * @since 4.3.0
 */
public record ProxyConfig(String host, int port, String user, String password) {

	/**
	 * System property naming a proxy for HTTPS traffic, read when no
	 * {@code proxyHost} property is set.
	 */
	public static final String HOST_SYSTEM_PROPERTY = "https.proxyHost";

	/** System property for {@link #HOST_SYSTEM_PROPERTY}'s port. */
	public static final String PORT_SYSTEM_PROPERTY = "https.proxyPort";

	/** System property for {@link #HOST_SYSTEM_PROPERTY}'s username. */
	public static final String USER_SYSTEM_PROPERTY = "https.proxyUser";

	/** System property for {@link #HOST_SYSTEM_PROPERTY}'s password. */
	public static final String PASSWORD_SYSTEM_PROPERTY = "https.proxyPassword";

	/**
	 * Port assumed for {@link #HOST_SYSTEM_PROPERTY} when
	 * {@link #PORT_SYSTEM_PROPERTY} is unset.
	 *
	 * <p>
	 * This is the JDK's documented default for {@code https.proxyPort}, and the
	 * same one grpc-java assumes. The driver's own {@code proxyPort} property has
	 * no default at all — see {@link #resolve}.
	 */
	public static final int DEFAULT_SYSTEM_PROPERTY_PORT = 443;

	public ProxyConfig {
		Objects.requireNonNull(host, "proxy host cannot be null");
		host = host.trim();
		if (host.isEmpty()) {
			throw new IllegalArgumentException("proxyHost cannot be blank");
		}
		if (port < 1 || port > 65535) {
			throw new IllegalArgumentException("proxyPort must be between 1 and 65535: " + port);
		}
		user = blankToNull(user);
		password = blankToNull(password);
		// A password with no username cannot be sent: Proxy-Authorization carries
		// both. Ignoring it would leave the caller believing the proxy is
		// authenticated when the driver is about to connect anonymously.
		if (user == null && password != null) {
			throw new IllegalArgumentException("proxyPassword requires proxyUser");
		}
	}

	/**
	 * Builds the proxy a connection should use, or {@code null} for none.
	 *
	 * <p>
	 * Explicit properties win outright. Only when {@code host} is unset are the
	 * standard JVM {@code https.proxy*} system properties consulted, and then all
	 * four are taken from there rather than mixed with driver properties — half a
	 * proxy from each source is a configuration nobody wrote.
	 *
	 * <p>
	 * Reading those properties is what keeps the two halves of the driver agreeing:
	 * the Storage Read API is gRPC, and grpc-java reads {@code https.proxyHost}
	 * itself. A driver that proxied only its own REST traffic would send the two
	 * paths to different places.
	 *
	 * <p>
	 * <b>An explicit {@code proxyHost} requires an explicit {@code proxyPort}.</b>
	 * There is no conventional port for an outbound proxy — 3128, 8080 and 8888 are
	 * all common — so a guess produces a connection refused against an unrelated
	 * port rather than an error naming what is missing. The JVM properties keep the
	 * JDK's own default because that is the contract a caller setting them is
	 * relying on.
	 *
	 * @param host
	 *            the {@code proxyHost} property, or null
	 * @param port
	 *            the {@code proxyPort} property, or null
	 * @param user
	 *            the {@code proxyUser} property, or null
	 * @param password
	 *            the {@code proxyPassword} property, or null
	 * @return the proxy to use, or null when neither the properties nor the JVM ask
	 *         for one
	 * @throws IllegalArgumentException
	 *             if the configuration is incomplete or out of range
	 */
	public static ProxyConfig resolve(String host, Integer port, String user, String password) {
		String explicitHost = blankToNull(host);
		if (explicitHost != null) {
			if (port == null) {
				throw new IllegalArgumentException("proxyPort is required when proxyHost is set");
			}
			return new ProxyConfig(explicitHost, port, user, password);
		}
		// Rejected rather than ignored, for the same reason as a password with no
		// user: these do nothing on their own, and connecting direct is the one
		// outcome the caller who set them did not want.
		if (blankToNull(user) != null || blankToNull(password) != null || port != null) {
			throw new IllegalArgumentException("proxyPort, proxyUser and proxyPassword require proxyHost");
		}
		return fromSystemProperties();
	}

	/**
	 * Reads a proxy from the standard JVM system properties, or returns null.
	 *
	 * @return the JVM-configured proxy, or null when {@code https.proxyHost} is
	 *         unset
	 */
	static ProxyConfig fromSystemProperties() {
		String host = blankToNull(System.getProperty(HOST_SYSTEM_PROPERTY));
		if (host == null) {
			return null;
		}
		int port = DEFAULT_SYSTEM_PROPERTY_PORT;
		String configuredPort = blankToNull(System.getProperty(PORT_SYSTEM_PROPERTY));
		if (configuredPort != null) {
			try {
				port = Integer.parseInt(configuredPort.trim());
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException(
						"Invalid " + PORT_SYSTEM_PROPERTY + " system property: " + configuredPort, e);
			}
		}
		return new ProxyConfig(host, port, System.getProperty(USER_SYSTEM_PROPERTY),
				System.getProperty(PASSWORD_SYSTEM_PROPERTY));
	}

	/**
	 * Whether this proxy demands {@code Proxy-Authorization}.
	 *
	 * @return true when a username was configured
	 */
	public boolean isAuthenticated() {
		return user != null;
	}

	/**
	 * Renders the proxy for a log line, without the password.
	 *
	 * <p>
	 * Overridden because a record's generated {@code toString} prints every
	 * component, and this one is embedded in {@code ConnectionProperties} — which
	 * anything may log. The password must not have a rendering that reaches a log
	 * file at all.
	 *
	 * @return {@code host:port}, noting only whether credentials are configured
	 */
	@Override
	public String toString() {
		return host + ":" + port + (isAuthenticated() ? " (authenticated as " + user + ")" : "");
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}

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

/**
 * Everything about how a connection's HTTP requests reach Google.
 *
 * <p>
 * The two settings travel together because they end up in the same object. A
 * proxy and a truststore are both properties of one {@code HttpTransport}, and
 * a TLS-inspecting proxy is precisely the case that needs both at once — so
 * they must compose on a single transport rather than each build one.
 *
 * <p>
 * <b>Never null, even when nothing is configured.</b> {@link #direct()} is the
 * "no proxy, JDK truststore" value, so {@code properties.transport().proxy()}
 * is always safe to call and no caller needs a null check before reaching a
 * component.
 *
 * <p>
 * This is half of the {@code CredentialsCache} key. Credentials carry the
 * transport they refresh over, so two connections agreeing on authentication
 * but differing here must not share one.
 *
 * @param proxy
 *            the proxy to route through, or null to connect directly
 * @param tls
 *            the truststore to verify against, or null for the JDK's own
 * @since 4.3.0
 */
public record TransportConfig(ProxyConfig proxy, TlsConfig tls) {

	private static final TransportConfig DIRECT = new TransportConfig(null, null);

	/**
	 * Returns the unconfigured transport: no proxy, and the JDK's own truststore.
	 *
	 * @return the shared default
	 */
	public static TransportConfig direct() {
		return DIRECT;
	}

	/**
	 * Builds a transport configuration, collapsing "nothing set" to
	 * {@link #direct()}.
	 *
	 * @param proxy
	 *            the proxy, or null
	 * @param tls
	 *            the truststore, or null
	 * @return the configuration, never null
	 */
	public static TransportConfig of(ProxyConfig proxy, TlsConfig tls) {
		if (proxy == null && tls == null) {
			return DIRECT;
		}
		return new TransportConfig(proxy, tls);
	}

	/**
	 * Whether this leaves both the route and the trust decision to the defaults.
	 *
	 * @return true when neither a proxy nor a truststore is configured
	 */
	public boolean isDefault() {
		return proxy == null && tls == null;
	}
}

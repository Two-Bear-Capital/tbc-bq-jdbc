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

import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.apache.v2.ApacheHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.auth.http.HttpTransportFactory;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.DefaultProxyRoutePlanner;

import javax.net.ssl.SSLContext;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Supplies the {@link HttpTransport} that BigQuery calls and OAuth token
 * requests travel over.
 *
 * <p>
 * The point of routing both through one factory is that they are the same
 * journey. Configuring only the API client leaves credentials being minted and
 * refreshed over a transport {@code google-auth-library} picks for itself, so
 * token requests keep going direct and a proxy-only network fails before any
 * BigQuery call is made — the shape of
 * <a href="https://github.com/googleapis/google-cloud-java/issues/13494">
 * googleapis/google-cloud-java#13494</a>. The same applies to a private CA: a
 * truststore reaching only the API client fails at token refresh with
 * {@code PKIX path building failed}.
 *
 * <p>
 * <b>A proxied connection uses Apache HttpClient, an unproxied one does
 * not.</b> BigQuery is HTTPS, so an authenticated proxy has to answer a
 * {@code CONNECT} tunnel with {@code Proxy-Authorization}.
 * {@link NetHttpTransport} is {@code HttpURLConnection}, which sources tunnel
 * credentials from the JVM-global {@link java.net.Authenticator} and, since
 * 8u111, refuses Basic there unless
 * {@code jdk.http.auth.tunneling.disabledSchemes} is cleared — neither a
 * driver's to set nor a user's to have to discover. Apache HttpClient
 * authenticates the tunnel natively, and is already on the classpath through
 * {@code google-http-client-apache-v2}, so this costs no dependency.
 *
 * <p>
 * A custom truststore does not force that choice: both stacks are configured
 * from the one {@link SSLContext} {@link TlsConfig#toSslContext()} builds, so
 * an unproxied connection with a private CA stays on {@code NetHttpTransport}
 * and the trust decision cannot differ between the two.
 *
 * @since 4.3.0
 */
public final class DriverTransports {

	/**
	 * One transport per distinct configuration, so connections share a connection
	 * pool rather than each opening their own — and, for a truststore, so the store
	 * is read once rather than per connection. Small and bounded in practice: a JVM
	 * configures one route, occasionally two.
	 */
	private static final ConcurrentHashMap<TransportConfig, HttpTransportFactory> CONFIGURED = new ConcurrentHashMap<>();

	/**
	 * The unconfigured transport, shared for the same reason the library shares its
	 * own.
	 */
	private static final HttpTransportFactory DIRECT = fixed(new NetHttpTransport());

	private DriverTransports() {
		throw new AssertionError("Utility class should not be instantiated");
	}

	/**
	 * Returns the transport factory for a connection.
	 *
	 * <p>
	 * Never null, so no caller has to decide what the library default is. An
	 * unconfigured transport is answered with a direct {@link NetHttpTransport},
	 * which is what {@code google-auth-library} would have chosen anyway.
	 *
	 * @param transport
	 *            the connection's route and trust settings, never null
	 * @return the factory to hand to the API client and to every credential
	 * @throws IOException
	 *             if a configured truststore cannot be read or parsed
	 */
	public static HttpTransportFactory forTransport(TransportConfig transport) throws IOException {
		if (transport.isDefault()) {
			return DIRECT;
		}
		// Not computeIfAbsent: building a truststore-backed transport reads a file,
		// and that would hold a bin lock across the I/O, blocking unrelated keys.
		// Two threads racing here each build one and the loser's copy is discarded,
		// which is harmless — they are equivalent.
		HttpTransportFactory cached = CONFIGURED.get(transport);
		if (cached != null) {
			return cached;
		}
		HttpTransportFactory created = build(transport);
		CONFIGURED.put(transport, created);
		return created;
	}

	/** Builds the transport described by {@code transport}. */
	private static HttpTransportFactory build(TransportConfig transport) throws IOException {
		SSLContext sslContext = sslContextFor(transport.tls());
		if (transport.proxy() == null) {
			return fixed(directTransport(sslContext));
		}
		return fixed(proxiedTransport(transport.proxy(), sslContext));
	}

	/**
	 * Builds the {@link SSLContext} for a truststore, or null to keep the JDK's.
	 *
	 * <p>
	 * Failures are reported as {@link IOException} naming the store, so a typo in
	 * the path surfaces as a connection error a caller can act on rather than as a
	 * {@link GeneralSecurityException} out of the middle of the transport. The
	 * password is deliberately absent from the message.
	 */
	private static SSLContext sslContextFor(TlsConfig tls) throws IOException {
		if (tls == null) {
			return null;
		}
		try {
			return tls.toSslContext();
		} catch (GeneralSecurityException e) {
			throw new IOException("Cannot use the truststore at " + tls.path() + ": " + e.getMessage(), e);
		}
	}

	/** An unproxied transport, on the same stack as a default connection. */
	private static HttpTransport directTransport(SSLContext sslContext) {
		if (sslContext == null) {
			return new NetHttpTransport();
		}
		return new NetHttpTransport.Builder().setSslSocketFactory(sslContext.getSocketFactory()).build();
	}

	/** An Apache-backed transport routed through {@code proxy}. */
	private static HttpTransport proxiedTransport(ProxyConfig proxy, SSLContext sslContext) {
		HttpHost proxyHost = new HttpHost(proxy.host(), proxy.port());
		// setRoutePlanner, not setProxy. HttpClientBuilder only derives a planner
		// from setProxy when no planner was set, and newDefaultHttpClientBuilder has
		// already set a SystemDefaultRoutePlanner — so setProxy here is a silent
		// no-op and every request goes direct.
		HttpClientBuilder builder = ApacheHttpTransport.newDefaultHttpClientBuilder()
				.setRoutePlanner(new DefaultProxyRoutePlanner(proxyHost));
		if (sslContext != null) {
			// setSSLSocketFactory, not setSSLContext, and for the same reason:
			// newDefaultHttpClientBuilder has already set a socket factory, which
			// build() prefers over any context set afterwards.
			builder.setSSLSocketFactory(new SSLConnectionSocketFactory(sslContext));
		}
		if (proxy.isAuthenticated()) {
			// Scoped to the proxy host: an AuthScope of ANY would offer these
			// credentials to BigQuery itself if it ever answered a 401.
			BasicCredentialsProvider credentials = new BasicCredentialsProvider();
			credentials.setCredentials(new AuthScope(proxyHost),
					new UsernamePasswordCredentials(proxy.user(), proxy.password()));
			builder.setDefaultCredentialsProvider(credentials);
		}
		return new ApacheHttpTransport(builder.build());
	}

	/** Wraps one transport as a factory that always returns it. */
	private static HttpTransportFactory fixed(HttpTransport transport) {
		return () -> transport;
	}

	/**
	 * Discards the cached transports.
	 *
	 * <p>
	 * For tests, which stand up a proxy or a certificate authority per case and
	 * would otherwise reuse a transport built against the last one.
	 */
	static void clear() {
		CONFIGURED.clear();
	}
}

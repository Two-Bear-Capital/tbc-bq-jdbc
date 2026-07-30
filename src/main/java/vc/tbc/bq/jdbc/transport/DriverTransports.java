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
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.DefaultProxyRoutePlanner;

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
 * googleapis/google-cloud-java#13494</a>.
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
 * @since 4.3.0
 */
public final class DriverTransports {

	/**
	 * One transport per distinct proxy, so connections share a connection pool
	 * rather than each opening their own. Small and bounded in practice: a JVM
	 * configures one proxy, occasionally two.
	 */
	private static final ConcurrentHashMap<ProxyConfig, HttpTransportFactory> PROXIED = new ConcurrentHashMap<>();

	/**
	 * The unproxied transport, shared for the same reason the library shares its
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
	 * Never null, so no caller has to decide what the library default is. A null
	 * {@code proxy} is answered with a direct {@link NetHttpTransport}, which is
	 * what {@code google-auth-library} would have chosen anyway.
	 *
	 * @param proxy
	 *            the connection's proxy, or null to connect directly
	 * @return the factory to hand to the API client and to every credential
	 */
	public static HttpTransportFactory forProxy(ProxyConfig proxy) {
		if (proxy == null) {
			return DIRECT;
		}
		return PROXIED.computeIfAbsent(proxy, DriverTransports::buildProxied);
	}

	/** Builds an Apache-backed transport routed through {@code proxy}. */
	private static HttpTransportFactory buildProxied(ProxyConfig proxy) {
		HttpHost proxyHost = new HttpHost(proxy.host(), proxy.port());
		// setRoutePlanner, not setProxy. HttpClientBuilder only derives a planner
		// from setProxy when no planner was set, and newDefaultHttpClientBuilder has
		// already set a SystemDefaultRoutePlanner — so setProxy here is a silent
		// no-op and every request goes direct.
		HttpClientBuilder builder = ApacheHttpTransport.newDefaultHttpClientBuilder()
				.setRoutePlanner(new DefaultProxyRoutePlanner(proxyHost));
		if (proxy.isAuthenticated()) {
			// Scoped to the proxy host: an AuthScope of ANY would offer these
			// credentials to BigQuery itself if it ever answered a 401.
			BasicCredentialsProvider credentials = new BasicCredentialsProvider();
			credentials.setCredentials(new AuthScope(proxyHost),
					new UsernamePasswordCredentials(proxy.user(), proxy.password()));
			builder.setDefaultCredentialsProvider(credentials);
		}
		return fixed(new ApacheHttpTransport(builder.build()));
	}

	/** Wraps one transport as a factory that always returns it. */
	private static HttpTransportFactory fixed(HttpTransport transport) {
		return () -> transport;
	}

	/**
	 * Discards the cached proxied transports.
	 *
	 * <p>
	 * For tests, which stand up a proxy per case and would otherwise reuse a
	 * transport pointing at a port nothing is listening on any more.
	 */
	static void clear() {
		PROXIED.clear();
	}
}

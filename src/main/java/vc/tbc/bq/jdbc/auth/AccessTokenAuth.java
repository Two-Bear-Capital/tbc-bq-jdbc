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
package vc.tbc.bq.jdbc.auth;

import com.google.auth.Credentials;
import com.google.auth.http.HttpTransportFactory;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;

import java.time.Instant;
import java.util.Date;
import java.util.Objects;

/**
 * Authentication with an OAuth 2.0 access token the caller already holds.
 *
 * <p>
 * Every other {@link AuthType} hands the driver something it can turn into
 * tokens repeatedly — a key file, a refresh token, an environment to probe.
 * This one hands over a finished token, which is what a host application has
 * when it ran the OAuth flow itself, or when it brokers per-user access and
 * mints a token per request. There is no way to express that with the others.
 *
 * <p>
 * <b>The token cannot be refreshed, so the connection outlives it only until it
 * expires.</b> That is inherent, not a limitation to work around: the driver
 * was given a token, not the means to obtain one. Supplying {@code expiry} lets
 * the driver say so plainly — an expired token is rejected when the connection
 * opens, with SQLState {@code 28000}, rather than reaching BigQuery and coming
 * back as a 401 one round trip later. Omitting it is allowed, and then the
 * token is used until BigQuery rejects it.
 *
 * <p>
 * <b>Not cached.</b> {@link CredentialsCache} exists to avoid re-reading a key
 * file or re-probing an environment; this credential is a wrapper around a
 * string and costs nothing to build. Caching it would buy nothing and risk
 * handing back a token that has since expired.
 *
 * <p>
 * <b>Usage:</b>
 *
 * <pre>{@code
 * String url = "jdbc:bigquery:my-project/my_dataset?authType=ACCESS_TOKEN&"
 * 		+ "accessToken=ya29.a0Af...&accessTokenExpiry=2026-07-30T20:00:00Z";
 * Connection conn = DriverManager.getConnection(url);
 * }</pre>
 *
 * @param token
 *            the OAuth 2.0 access token, sent as a bearer credential
 * @param expiry
 *            when the token stops being valid, or null if the caller does not
 *            know
 * @since 4.4.0
 */
public record AccessTokenAuth(String token, Instant expiry) implements AuthType {

	public AccessTokenAuth {
		Objects.requireNonNull(token, "token cannot be null");
		if (token.isBlank()) {
			throw new IllegalArgumentException("accessToken cannot be blank");
		}
		token = token.trim();
	}

	/**
	 * Creates an access token with no known expiry.
	 *
	 * @param token
	 *            the OAuth 2.0 access token
	 */
	public AccessTokenAuth(String token) {
		this(token, null);
	}

	/**
	 * Whether the token is known to have expired as of now.
	 *
	 * <p>
	 * False when no expiry was supplied — unknown is not the same as valid, but the
	 * driver has nothing to test against and BigQuery will say so shortly.
	 *
	 * @return true when an expiry was given and it has passed
	 */
	public boolean isExpired() {
		return expiry != null && !Instant.now().isBefore(expiry);
	}

	@Override
	public Credentials toCredentials(HttpTransportFactory transportFactory) {
		// The transport factory is accepted and unused, uniquely among the auth
		// types. Every other one fetches something over it; this one was handed the
		// finished token, so there is no request to route. It stays in the signature
		// because the interface is what guarantees a new auth type cannot forget the
		// transport — see AuthType#toCredentials.
		return GoogleCredentials.create(new AccessToken(token, expiry == null ? null : Date.from(expiry)));
	}

	/**
	 * Renders the authentication without the token.
	 *
	 * <p>
	 * Overridden because a record prints every component, and this component is a
	 * bearer credential: anything holding it can act as the user until it expires.
	 * It is reachable from {@code ConnectionProperties}, which anything may log.
	 *
	 * @return a description naming only the expiry
	 */
	@Override
	public String toString() {
		return "AccessTokenAuth[expiry=" + (expiry == null ? "unknown" : expiry) + "]";
	}
}

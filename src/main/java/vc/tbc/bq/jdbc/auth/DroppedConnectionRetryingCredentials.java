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
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vc.tbc.bq.jdbc.transport.ConnectionDrops;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Retries a token refresh that failed because the connection dropped.
 *
 * <p>
 * Of google-auth's token requests, only the service-account key path retries
 * I/O itself. The others either make the call with no retry or wrap the failure
 * in an exception the BigQuery client cannot recognise: google-cloud-core
 * decides retryability from the exception's own class, never its cause, so
 * {@code IOException("Error requesting access token")} from impersonation, or a
 * {@code GoogleAuthException} from a user refresh token, is fatal even when a
 * stale keep-alive connection caused it (#337). Measured against the real
 * client: user credentials, and workload identity with a service-account
 * impersonation URL, failed on every kind of drop, and every type failed on an
 * {@code EOFException}.
 *
 * <p>
 * Only a dropped connection is retried. An HTTP error from the token endpoint
 * (a 403 for a missing grant) fails on the first attempt and is rethrown
 * unchanged, so {@code ServiceErrorDetail} still classifies it. Minting a token
 * twice has no side effects, and no request has reached BigQuery yet, so
 * retrying is safe on every path.
 *
 * <p>
 * This delegates rather than subclassing, because several credential types have
 * private constructors, and lets {@code OAuth2Credentials} own the token cache.
 * What a delegate adds to a request beyond the token, its quota project, is
 * copied; scoping and the universe domain are forwarded.
 *
 * @since 4.5.4
 */
final class DroppedConnectionRetryingCredentials extends GoogleCredentials {

	private static final long serialVersionUID = 1L;

	private static final Logger logger = LoggerFactory.getLogger(DroppedConnectionRetryingCredentials.class);

	/** Three attempts in all; short, because the refresh blocks a query. */
	static final List<Duration> DEFAULT_BACKOFF = List.of(Duration.ofMillis(250), Duration.ofSeconds(1));

	private final GoogleCredentials delegate;
	private final List<Duration> backoff;

	DroppedConnectionRetryingCredentials(GoogleCredentials delegate, List<Duration> backoff) {
		super(GoogleCredentials.newBuilder()
				.setQuotaProjectId(Objects.requireNonNull(delegate, "delegate cannot be null").getQuotaProjectId()));
		this.delegate = delegate;
		this.backoff = List.copyOf(backoff);
	}

	/**
	 * Adds the retry to a credential whose token request lacks one.
	 *
	 * @param credentials
	 *            a freshly built credential
	 * @return the credential, wrapped unless it already retries
	 */
	static Credentials wrap(Credentials credentials) {
		// A service account key retries its own token request, and in self-signed
		// JWT mode it makes none: wrapping would replace that with a token fetch
		if (!(credentials instanceof GoogleCredentials google) || credentials instanceof ServiceAccountCredentials
				|| credentials instanceof DroppedConnectionRetryingCredentials) {
			return credentials;
		}
		return new DroppedConnectionRetryingCredentials(google, DEFAULT_BACKOFF);
	}

	@Override
	public AccessToken refreshAccessToken() throws IOException {
		for (int retry = 0;; retry++) {
			try {
				return delegate.refreshAccessToken();
			} catch (IOException e) {
				if (retry >= backoff.size() || !ConnectionDrops.isDroppedConnection(e)) {
					throw e;
				}
				logger.warn("Token refresh lost its connection ({}), retry {} of {}", rootCause(e), retry + 1,
						backoff.size());
				pause(backoff.get(retry), e);
			}
		}
	}

	@Override
	public String getUniverseDomain() throws IOException {
		return delegate.getUniverseDomain();
	}

	// BigQueryOptions scopes a credential only when it asks to be, and the
	// GoogleCredentials default says no: without these, a credential needing
	// scopes would mint a token with none
	@Override
	public boolean createScopedRequired() {
		return delegate.createScopedRequired();
	}

	@Override
	public GoogleCredentials createScoped(Collection<String> scopes) {
		return new DroppedConnectionRetryingCredentials(delegate.createScoped(scopes), backoff);
	}

	@Override
	public GoogleCredentials createScoped(Collection<String> scopes, Collection<String> defaultScopes) {
		return new DroppedConnectionRetryingCredentials(delegate.createScoped(scopes, defaultScopes), backoff);
	}

	// google-auth's own wrapper is always the outermost layer, so it says nothing
	private static String rootCause(Throwable failure) {
		Throwable root = failure;
		while (root.getCause() != null) {
			root = root.getCause();
		}
		return root.toString();
	}

	private static void pause(Duration delay, IOException failure) throws InterruptedIOException {
		try {
			Thread.sleep(delay);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			InterruptedIOException interrupted = new InterruptedIOException("Interrupted while retrying token refresh");
			interrupted.addSuppressed(failure);
			throw interrupted;
		}
	}

	// getClass(), not instanceof: GoogleCredentials' own equals would otherwise
	// accept a wrapper that this one rejects
	@Override
	public boolean equals(Object obj) {
		if (obj == null || obj.getClass() != getClass()) {
			return false;
		}
		DroppedConnectionRetryingCredentials other = (DroppedConnectionRetryingCredentials) obj;
		return delegate.equals(other.delegate) && backoff.equals(other.backoff);
	}

	@Override
	public int hashCode() {
		return Objects.hash(delegate, backoff);
	}

	@Override
	public String toString() {
		return "DroppedConnectionRetryingCredentials{" + delegate + "}";
	}
}

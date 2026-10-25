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

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLHandshakeException;
import java.io.EOFException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketException;
import java.security.cert.CertificateException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Retries a token refresh that failed because the connection dropped.
 *
 * <p>
 * google-auth's {@code ImpersonatedCredentials} makes its IAM
 * {@code generateAccessToken} call with no I/O retry, and wraps any failure as
 * {@code IOException("Error requesting access token")}. google-cloud-core
 * decides retryability from the exception's own class, never its cause, so the
 * BigQuery client's {@code RetrySettings} treat a stale keep-alive connection
 * on that call as fatal and the query fails (#337). google-auth's
 * service-account path does retry I/O on its token request, which is why this
 * wraps impersonation only.
 *
 * <p>
 * Only a dropped connection is retried. An HTTP error from IAM (a 403 for a
 * missing grant) fails on the first attempt and is rethrown unchanged, so
 * {@code ServiceErrorDetail} still classifies it. Minting a token twice has no
 * side effects, so retrying the POST is safe.
 *
 * <p>
 * This cannot subclass {@code ImpersonatedCredentials}: its constructor is
 * private. It delegates instead, and lets {@code OAuth2Credentials} own the
 * token cache, which {@code ImpersonatedCredentials} does not override.
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

	DroppedConnectionRetryingCredentials(GoogleCredentials delegate) {
		this(delegate, DEFAULT_BACKOFF);
	}

	DroppedConnectionRetryingCredentials(GoogleCredentials delegate, List<Duration> backoff) {
		this.delegate = Objects.requireNonNull(delegate, "delegate cannot be null");
		this.backoff = List.copyOf(backoff);
	}

	@Override
	public AccessToken refreshAccessToken() throws IOException {
		for (int retry = 0;; retry++) {
			try {
				return delegate.refreshAccessToken();
			} catch (IOException e) {
				if (retry >= backoff.size() || !isDroppedConnection(e)) {
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

	static boolean isDroppedConnection(Throwable failure) {
		boolean dropped = false;
		for (Throwable t = failure; t != null; t = t.getCause()) {
			// A handshake that failed on the certificate is a trust problem, and
			// retrying it would only delay the same error
			if (t instanceof CertificateException) {
				return false;
			}
			dropped |= t instanceof SocketException || t instanceof EOFException || t instanceof SSLHandshakeException;
		}
		return dropped;
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

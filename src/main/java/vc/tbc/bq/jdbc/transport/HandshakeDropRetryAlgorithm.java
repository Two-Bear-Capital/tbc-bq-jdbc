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

import com.google.api.gax.retrying.ResultRetryAlgorithm;
import com.google.api.gax.retrying.TimedAttemptSettings;

import java.util.Objects;

/**
 * The BigQuery client's own retry rules, plus a TLS handshake that dropped.
 *
 * <p>
 * The client's default handler retries {@code ConnectException},
 * {@code UnknownHostException} and {@code SocketException} by class.
 * {@code SSLHandshakeException} is none of those, so a connection closed
 * mid-handshake failed a query on its first attempt — including the
 * {@code jobs.insert} that starts it (#337, 2026-10-25 CI).
 *
 * <p>
 * Only the handshake is added. A handshake fails before any of the request is
 * written, so a retry cannot run a job twice; every {@code jobs.insert} retry
 * carries a fresh job id, which is exactly why a drop after the request was
 * sent must not be added here. Timing, attempt count and everything else stay
 * with the wrapped algorithm and the connection's {@code RetrySettings}.
 *
 * @since 4.5.5
 */
public final class HandshakeDropRetryAlgorithm implements ResultRetryAlgorithm<Object> {

	private final ResultRetryAlgorithm<Object> library;

	/**
	 * Extends the client library's algorithm.
	 *
	 * @param library
	 *            the algorithm the client would otherwise use, read from built
	 *            {@code BigQueryOptions} because the class holding the default is
	 *            package-private
	 */
	@SuppressWarnings("unchecked") // the client's handler is a ResultRetryAlgorithm<Object>; the getter erases it
	public HandshakeDropRetryAlgorithm(ResultRetryAlgorithm<?> library) {
		this.library = (ResultRetryAlgorithm<Object>) Objects.requireNonNull(library, "library cannot be null");
	}

	@Override
	public TimedAttemptSettings createNextAttempt(Throwable previousThrowable, Object previousResponse,
			TimedAttemptSettings previousSettings) {
		return library.createNextAttempt(previousThrowable, previousResponse, previousSettings);
	}

	@Override
	public boolean shouldRetry(Throwable previousThrowable, Object previousResponse) {
		return library.shouldRetry(previousThrowable, previousResponse)
				|| previousThrowable != null && ConnectionDrops.isHandshakeDrop(previousThrowable);
	}
}

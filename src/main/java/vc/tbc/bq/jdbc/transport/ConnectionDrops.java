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

import javax.net.ssl.SSLHandshakeException;
import java.io.EOFException;
import java.net.SocketException;
import java.security.cert.CertificateException;

/**
 * Recognises an HTTP request that failed because the connection dropped, as
 * opposed to one Google answered with an error.
 *
 * <p>
 * The two questions differ in what a retry can repeat. Any dropped connection
 * may have lost a request that already reached Google, which is harmless for a
 * token mint and not for a job insert. A failed TLS handshake happens before a
 * single byte of the request is written, so it is safe to retry anything.
 *
 * @since 4.5.5
 */
public final class ConnectionDrops {

	private ConnectionDrops() {
	}

	/**
	 * Whether the failure, or anything in its cause chain, is a dropped connection.
	 *
	 * @param failure
	 *            the failure to classify
	 * @return true for a socket error, an early end of stream, or a handshake that
	 *         did not fail on the certificate
	 */
	public static boolean isDroppedConnection(Throwable failure) {
		return isHandshakeDrop(failure) || !hasCause(failure, CertificateException.class)
				&& (hasCause(failure, SocketException.class) || hasCause(failure, EOFException.class));
	}

	/**
	 * Whether the connection dropped during the TLS handshake, before the request
	 * was sent.
	 *
	 * @param failure
	 *            the failure to classify
	 * @return true for a handshake failure whose chain holds no certificate error
	 */
	public static boolean isHandshakeDrop(Throwable failure) {
		// A handshake that failed on the certificate is a trust problem, and
		// retrying it would only delay the same error
		return hasCause(failure, SSLHandshakeException.class) && !hasCause(failure, CertificateException.class);
	}

	private static boolean hasCause(Throwable failure, Class<? extends Throwable> type) {
		for (Throwable t = failure; t != null; t = t.getCause()) {
			if (type.isInstance(t)) {
				return true;
			}
		}
		return false;
	}
}

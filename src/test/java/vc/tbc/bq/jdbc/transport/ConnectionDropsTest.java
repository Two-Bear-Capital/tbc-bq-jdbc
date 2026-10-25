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

import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLHandshakeException;
import java.io.EOFException;
import java.io.IOException;
import java.net.SocketException;
import java.security.cert.CertificateException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectionDropsTest {

	private static SSLHandshakeException untrustedCertificate() {
		SSLHandshakeException untrusted = new SSLHandshakeException("PKIX path building failed");
		untrusted.initCause(new CertificateException("untrusted"));
		return untrusted;
	}

	@Test
	void aDropAnywhereInTheChainIsADroppedConnection() {
		assertTrue(ConnectionDrops
				.isDroppedConnection(new IOException("Error requesting access token", new SocketException("reset"))));
		assertTrue(ConnectionDrops.isDroppedConnection(new IOException(new EOFException("connection closed"))));
		assertTrue(ConnectionDrops.isDroppedConnection(new SSLHandshakeException("connection closed")));
	}

	@Test
	void onlyAHandshakeDropHappensBeforeTheRequestIsSent() {
		assertTrue(ConnectionDrops.isHandshakeDrop(new SSLHandshakeException("connection closed")));
		assertTrue(ConnectionDrops.isHandshakeDrop(new IOException(new SSLHandshakeException("connection closed"))));
		// Mid-request drops may have delivered the request
		assertFalse(ConnectionDrops.isHandshakeDrop(new SocketException("Unexpected end of file from server")));
		assertFalse(ConnectionDrops.isHandshakeDrop(new EOFException("connection closed")));
	}

	@Test
	void aCertificateFailureIsNeverADrop() {
		assertFalse(ConnectionDrops.isHandshakeDrop(untrustedCertificate()));
		assertFalse(ConnectionDrops.isDroppedConnection(untrustedCertificate()));
		assertFalse(ConnectionDrops.isDroppedConnection(new IOException("Error parsing")));
	}
}

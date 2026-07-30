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
package vc.tbc.bq.jdbc.testsupport;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * A local HTTP proxy that records the {@code CONNECT} tunnels asked of it.
 *
 * <p>
 * Enough of a proxy to prove traffic reached one, and no more: it records the
 * tunnel request and then refuses it, so whatever was being sent fails with an
 * {@link IOException} instead of leaving the JVM. That is deliberate — a test
 * that relayed to Google would need credentials and a network, and this needs
 * neither while still proving the thing worth proving, which is that the
 * request was addressed to the proxy rather than to Google directly.
 *
 * <p>
 * The point of testing at this level is
 * <a href="https://github.com/googleapis/google-cloud-java/issues/13494">the
 * bug in Google's own driver</a>: unit tests asserting that the API client
 * carried a proxy setting all passed while credentials were still being
 * refreshed direct. Only something that watches the socket can tell the
 * difference.
 *
 * @since 4.3.0
 */
public final class RecordingProxyServer implements AutoCloseable {

	/**
	 * One tunnel request seen by the proxy.
	 *
	 * @param target
	 *            the {@code host:port} the client asked to reach
	 * @param proxyAuthorization
	 *            the {@code Proxy-Authorization} header, or null if absent
	 */
	public record Connect(String target, String proxyAuthorization) {

		/**
		 * Decodes a Basic {@code Proxy-Authorization} back to {@code user:password}.
		 *
		 * @return the decoded credentials, or null when the header was absent or not
		 *         Basic
		 */
		public String basicCredentials() {
			if (proxyAuthorization == null || !proxyAuthorization.regionMatches(true, 0, "Basic ", 0, 6)) {
				return null;
			}
			return new String(Base64.getDecoder().decode(proxyAuthorization.substring(6).trim()),
					StandardCharsets.UTF_8);
		}
	}

	private final ServerSocket serverSocket;
	private final BlockingQueue<Connect> connects = new LinkedBlockingQueue<>();
	private final String expectedCredentials;
	private volatile boolean running = true;

	private RecordingProxyServer(String expectedCredentials) throws IOException {
		this.expectedCredentials = expectedCredentials;
		this.serverSocket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress());
		Thread.ofVirtual().name("recording-proxy").start(this::acceptLoop);
	}

	/**
	 * Starts a proxy that tunnels for anyone.
	 *
	 * @return the running proxy
	 * @throws IOException
	 *             if the listening socket cannot be opened
	 */
	public static RecordingProxyServer start() throws IOException {
		return new RecordingProxyServer(null);
	}

	/**
	 * Starts a proxy that answers 407 until the client authenticates.
	 *
	 * @param user
	 *            the username it accepts
	 * @param password
	 *            the password it accepts
	 * @return the running proxy
	 * @throws IOException
	 *             if the listening socket cannot be opened
	 */
	public static RecordingProxyServer requiringAuth(String user, String password) throws IOException {
		return new RecordingProxyServer(user + ":" + password);
	}

	/**
	 * Returns the port the proxy is listening on.
	 *
	 * @return the ephemeral port
	 */
	public int port() {
		return serverSocket.getLocalPort();
	}

	/**
	 * Waits for the next tunnel request.
	 *
	 * @param timeout
	 *            how long to wait
	 * @return the request, or null if none arrived in time
	 * @throws InterruptedException
	 *             if the wait is interrupted
	 */
	public Connect awaitConnect(Duration timeout) throws InterruptedException {
		return connects.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
	}

	private void acceptLoop() {
		while (running) {
			try {
				Socket socket = serverSocket.accept();
				Thread.ofVirtual().start(() -> handle(socket));
			} catch (IOException e) {
				return; // the socket was closed; nothing left to accept
			}
		}
	}

	private void handle(Socket socket) {
		try (socket;
				BufferedReader in = new BufferedReader(
						new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1))) {
			OutputStream out = socket.getOutputStream();
			// Loops rather than handling one request: a proxy demanding credentials
			// answers the first CONNECT with 407 and the client retries on the same
			// connection, so both attempts arrive here.
			while (true) {
				String requestLine = in.readLine();
				if (requestLine == null) {
					return;
				}
				String proxyAuthorization = readHeaders(in);
				if (!requestLine.startsWith("CONNECT ")) {
					write(out, "HTTP/1.1 400 Bad Request\r\nContent-Length: 0\r\n\r\n");
					return;
				}
				connects.add(new Connect(requestLine.split(" ")[1], proxyAuthorization));

				if (expectedCredentials != null && !expectedCredentials.equals(decode(proxyAuthorization))) {
					// Content-Length keeps the connection reusable, so the authenticated
					// retry lands on this same socket rather than opening another.
					write(out, "HTTP/1.1 407 Proxy Authentication Required\r\n"
							+ "Proxy-Authenticate: Basic realm=\"recording-proxy\"\r\n" + "Content-Length: 0\r\n\r\n");
					continue;
				}
				// Refused rather than granted. Granting would leave the client starting a
				// TLS handshake into a socket about to close, which it spends seconds
				// discovering; a refusal fails it at once. Either way the recording above
				// has already captured what the test is asserting on.
				write(out, "HTTP/1.1 502 Bad Gateway\r\nContent-Length: 0\r\n\r\n");
				return;
			}
		} catch (IOException e) {
			// A client giving up mid-handshake is the expected ending here
		}
	}

	/** Reads to the blank line, returning the Proxy-Authorization value if any. */
	private static String readHeaders(BufferedReader in) throws IOException {
		String proxyAuthorization = null;
		String line;
		while ((line = in.readLine()) != null && !line.isEmpty()) {
			if (line.regionMatches(true, 0, "Proxy-Authorization:", 0, "Proxy-Authorization:".length())) {
				proxyAuthorization = line.substring("Proxy-Authorization:".length()).trim();
			}
		}
		return proxyAuthorization;
	}

	private static String decode(String proxyAuthorization) {
		return new Connect("", proxyAuthorization).basicCredentials();
	}

	private static void write(OutputStream out, String response) throws IOException {
		out.write(response.getBytes(StandardCharsets.ISO_8859_1));
		out.flush();
	}

	@Override
	public void close() throws IOException {
		running = false;
		serverSocket.close();
	}
}

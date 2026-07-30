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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Generates a throwaway certificate authority and the two keystores a TLS test
 * needs.
 *
 * <p>
 * Built by shelling out to the JDK's own {@code keytool} rather than by
 * assembling certificates in code. Java has no public API for issuing an X.509
 * certificate — the classes that can are in {@code sun.security.x509} and are
 * encapsulated on a modern JDK — so the alternatives are a test-scoped
 * BouncyCastle dependency or this. {@code keytool} ships with every JDK the
 * build already requires.
 *
 * <p>
 * The certificate is its own issuer, which is exactly the shape under test: an
 * authority the JDK's truststore has never heard of, so verifying against the
 * default anchors fails with {@code PKIX path building failed}.
 *
 * @param serverKeyStore
 *            PKCS#12 store holding the certificate and its private key, for the
 *            HTTPS server to present
 * @param trustStore
 *            PKCS#12 store holding only the certificate, for a client to verify
 *            against
 * @param password
 *            the password protecting both
 * @since 4.3.0
 */
public record TestCertificates(Path serverKeyStore, Path trustStore, String password) {

	private static final String PASSWORD = "changeit";

	/**
	 * Issues a certificate for {@code localhost} and returns the stores holding it.
	 *
	 * @param directory
	 *            where to write the stores, typically a JUnit temporary directory
	 * @return the generated stores
	 * @throws Exception
	 *             if keytool is unavailable or fails
	 */
	public static TestCertificates generate(Path directory) throws Exception {
		Path serverKeyStore = directory.resolve("server.p12");
		Path certificate = directory.resolve("server.pem");
		Path trustStore = directory.resolve("trust.p12");

		// SAN is not optional: without it the client rejects the certificate on
		// hostname verification instead of on the trust chain, and the test would
		// pass for the wrong reason — or fail even once the truststore is correct.
		keytool("-genkeypair", "-alias", "server", "-keyalg", "RSA", "-keysize", "2048", "-validity", "1", "-dname",
				"CN=localhost,OU=tbc-bq-jdbc tests,O=Two Bear Capital", "-ext", "SAN=dns:localhost,ip:127.0.0.1",
				"-keystore", serverKeyStore.toString(), "-storetype", "PKCS12", "-storepass", PASSWORD, "-keypass",
				PASSWORD);

		keytool("-exportcert", "-alias", "server", "-keystore", serverKeyStore.toString(), "-storepass", PASSWORD,
				"-rfc", "-file", certificate.toString());

		keytool("-importcert", "-noprompt", "-alias", "server", "-file", certificate.toString(), "-keystore",
				trustStore.toString(), "-storetype", "PKCS12", "-storepass", PASSWORD);

		return new TestCertificates(serverKeyStore, trustStore, PASSWORD);
	}

	private static void keytool(String... arguments) throws IOException, InterruptedException {
		Path keytool = Path.of(System.getProperty("java.home"), "bin", "keytool");
		List<String> command = new java.util.ArrayList<>();
		command.add(keytool.toString());
		command.addAll(List.of(arguments));

		Path output = Files.createTempFile("keytool", ".log");
		output.toFile().deleteOnExit();
		Process process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output.toFile()).start();
		if (!process.waitFor(60, TimeUnit.SECONDS)) {
			process.destroyForcibly();
			throw new IOException("keytool timed out: " + String.join(" ", command));
		}
		if (process.exitValue() != 0) {
			throw new IOException("keytool failed (" + process.exitValue() + "): " + Files.readString(output));
		}
	}
}

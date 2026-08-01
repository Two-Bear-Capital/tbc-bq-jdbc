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
package vc.tbc.bq.jdbc;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards every {@code authType=...} example in the shipped source and docs
 * against the set of values the parser actually accepts.
 *
 * <p>
 * {@link PropertyInfoCoverageTest} does this for property <em>names</em>.
 * Nothing did it for a property's <em>values</em>, and the two federation auth
 * types drifted: the class-level Javadoc on {@code WorkforceIdentityAuth} and
 * {@code WorkloadIdentityAuth} told readers to write
 * {@code authType=WORKFORCE_IDENTITY} and {@code authType=WORKLOAD_IDENTITY},
 * the class names rather than the URL tokens, so a developer copying either
 * example got "Unsupported authentication type". Both examples shipped from
 * 1.0.0.
 *
 * <p>
 * Nothing in the build could have caught it. Javadoc examples live inside
 * {@code {@code ...}} blocks, which the compiler does not parse, Spotless does
 * not read, doclint checks only for tag syntax, and CodeQL analyses the AST.
 * Prose is only testable if a test reads it, which is what this does.
 *
 * <p>
 * Like {@code PropertyInfoCoverageTest}, this reads the parser's source rather
 * than its behaviour: the accepted values exist only as {@code case} labels,
 * with no runtime registry to enumerate.
 *
 * @since 4.4.0
 */
class AuthTypeDocumentationTest {

	private static final Path PARSER_SOURCE = Path.of("src/main/java/vc/tbc/bq/jdbc/config/ConnectionUrlParser.java");

	/** Where a reader could copy an {@code authType} value from. */
	private static final List<Path> DOCUMENTED_ROOTS = List.of(Path.of("src/main/java"), Path.of("docs"),
			Path.of("README.md"));

	/**
	 * Finds {@code authType=VALUE} in Javadoc, Markdown prose and code fences
	 * alike.
	 */
	private static final Pattern USAGE = Pattern.compile("authType=([A-Za-z0-9_]+)");

	/**
	 * Extracts the {@code case} labels of
	 * {@code ConnectionUrlParser.parseAuthType}.
	 *
	 * <p>
	 * Scoped to that method rather than the whole file because the Simba
	 * {@code OAuthType} mapping is a second string switch in the same class; a
	 * file-wide scan would accept {@code authType=4}.
	 */
	private static Set<String> authTypesAcceptedByParser() throws IOException {
		String source = Files.readString(PARSER_SOURCE, StandardCharsets.UTF_8);

		int start = source.indexOf("private static AuthType parseAuthType(");
		assertTrue(start >= 0, "parseAuthType not found in " + PARSER_SOURCE + "; this scan needs updating");
		// The default branch closes the switch, and its message is asserted on below,
		// so a rename cannot quietly shrink the scanned region to nothing.
		int end = source.indexOf("Unsupported authentication type", start);
		assertTrue(end > start, "the parseAuthType default branch moved; this scan needs updating");

		Matcher m = Pattern.compile("case \"([A-Z][A-Z0-9_]*)\"").matcher(source.substring(start, end));
		Set<String> accepted = new TreeSet<>();
		while (m.find()) {
			accepted.add(m.group(1));
		}
		return accepted;
	}

	/** Every {@code authType=} example a reader could copy, with its location. */
	private static List<String> documentedUsages() throws IOException {
		List<String> usages = new ArrayList<>();
		for (Path root : DOCUMENTED_ROOTS) {
			if (!Files.exists(root)) {
				continue;
			}
			try (Stream<Path> tree = Files.walk(root)) {
				List<Path> files = tree.filter(Files::isRegularFile)
						.filter(p -> p.toString().endsWith(".java") || p.toString().endsWith(".md")).toList();
				for (Path file : files) {
					String text = Files.readString(file, StandardCharsets.UTF_8);
					Matcher m = USAGE.matcher(text);
					while (m.find()) {
						// Line number so a failure points at the example, not just the file
						long line = text.chars().limit(m.start()).filter(c -> c == '\n').count() + 1;
						usages.add(file + ":" + line + " -> " + m.group(1));
					}
				}
			}
		}
		return usages;
	}

	@Test
	void everyDocumentedAuthTypeIsAcceptedByTheParser() throws IOException {
		Set<String> accepted = authTypesAcceptedByParser();

		List<String> broken = new ArrayList<>();
		for (String usage : documentedUsages()) {
			String value = usage.substring(usage.lastIndexOf("-> ") + 3);
			// The parser upper-cases before switching, so case is not the defect here
			if (!accepted.contains(value.toUpperCase(Locale.ROOT))) {
				broken.add(usage);
			}
		}

		assertTrue(broken.isEmpty(),
				"These authType examples name a value the parser rejects, so a reader copying "
						+ "one gets \"Unsupported authentication type\". Accepted: " + accepted + "\n  "
						+ String.join("\n  ", broken));
	}

	@Test
	void everyAcceptedAuthTypeIsAdvertisedByGetPropertyInfo() throws IOException, SQLException {
		Set<String> advertised = new TreeSet<>();
		for (DriverPropertyInfo info : new BQDriver().getPropertyInfo(null, null)) {
			if ("authType".equals(info.name) && info.choices != null) {
				advertised.addAll(Arrays.asList(info.choices));
			}
		}

		// Both directions: an unadvertised value never reaches
		// docs/generated/connection-properties.md, and an advertised one the parser
		// does not accept is an IntelliJ dropdown entry that fails on connect.
		assertEquals(authTypesAcceptedByParser(), advertised,
				"getPropertyInfo()'s authType choices and ConnectionUrlParser.parseAuthType disagree");
	}

	@Test
	void theAuthTypeScansFindSomething() throws IOException {
		// Guards both tests above from passing vacuously if a regex stops matching
		assertTrue(authTypesAcceptedByParser().size() >= 5,
				"Expected at least the five documented auth types; the parser scan likely broke");
		assertTrue(documentedUsages().size() > 20,
				"Expected many authType examples across source and docs; the usage scan likely broke");
	}
}

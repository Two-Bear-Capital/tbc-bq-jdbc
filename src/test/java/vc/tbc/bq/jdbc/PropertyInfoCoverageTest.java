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
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the claim that {@code Driver.getPropertyInfo()} lists every property
 * the driver actually reads.
 *
 * <p>
 * The generated reference in {@code docs/generated/connection-properties.md} is
 * produced by reflecting over {@code getPropertyInfo()}, and the surrounding
 * prose tells readers it matches what the driver accepts. Nothing enforced
 * that: {@code host} and {@code port} were read by
 * {@link vc.tbc.bq.jdbc.config.ConnectionUrlParser} and applied to the client
 * while being absent from the advertised list, so they never reached the docs.
 *
 * <p>
 * This reads the parser's source rather than its behaviour because there is no
 * runtime registry of property names to inspect — the names appear only as
 * string literals at their lookup sites.
 *
 * @since 3.0.0
 */
class PropertyInfoCoverageTest {

	private static final Path PARSER_SOURCE = Path.of("src/main/java/vc/tbc/bq/jdbc/config/ConnectionUrlParser.java");

	/**
	 * Property names the parser looks up but which are intentionally not offered as
	 * connection properties.
	 */
	private static final Set<String> NOT_ADVERTISED = Set.of(
			// Supplied as the URL path, not as a property
			"projectId");

	/** Extracts the literal keys the parser reads out of the properties map. */
	private static Set<String> propertiesReadByParser() throws IOException {
		String source = Files.readString(PARSER_SOURCE, StandardCharsets.UTF_8);
		Pattern lookup = Pattern.compile("(?:properties\\.(?:get|remove)\\(\"([A-Za-z][A-Za-z0-9]*)\"\\)"
				+ "|parse(?:Integer|Long|BooleanObject|Boolean)\\(properties, \"([A-Za-z][A-Za-z0-9]*)\"\\))");
		Matcher m = lookup.matcher(source);
		Set<String> found = new TreeSet<>();
		while (m.find()) {
			found.add(m.group(1) != null ? m.group(1) : m.group(2));
		}
		found.removeAll(NOT_ADVERTISED);
		return found;
	}

	@Test
	void everyPropertyTheParserReadsIsAdvertised() throws SQLException, IOException {
		Set<String> advertised = java.util.Arrays.stream(new BQDriver().getPropertyInfo(null, null)).map(p -> p.name)
				.collect(Collectors.toCollection(TreeSet::new));

		Set<String> missing = new TreeSet<>(propertiesReadByParser());
		missing.removeAll(advertised);

		assertTrue(missing.isEmpty(),
				"These properties are read by ConnectionUrlParser but absent from getPropertyInfo(), "
						+ "so they never reach docs/generated/connection-properties.md: " + missing);
	}

	@Test
	void theParserLookupScanFindsSomething() throws IOException {
		// Guards the test above from silently passing if the regex stops matching —
		// an empty "read" set would make the assertion vacuously true
		assertTrue(propertiesReadByParser().size() > 10,
				"Expected the parser to read many properties; the source scan likely broke");
	}

	@Test
	void hostAndPortAreAdvertised() throws SQLException {
		Set<String> advertised = java.util.Arrays.stream(new BQDriver().getPropertyInfo(null, null)).map(p -> p.name)
				.collect(Collectors.toCollection(TreeSet::new));

		// The specific pair that motivated this guard
		assertTrue(advertised.contains("host"), "host should be advertised");
		assertTrue(advertised.contains("port"), "port should be advertised");
	}

	@Test
	void advertisedPropertiesHaveDescriptions() throws SQLException {
		for (DriverPropertyInfo info : new BQDriver().getPropertyInfo(null, null)) {
			assertTrue(info.description != null && !info.description.isBlank(),
					info.name + " has no description, so its row in the generated table would be empty");
		}
	}
}

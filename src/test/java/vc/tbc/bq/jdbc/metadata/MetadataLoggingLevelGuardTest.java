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
package vc.tbc.bq.jdbc.metadata;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fails if anything in the metadata package logs at INFO.
 *
 * <h2>Why this is a guard rather than a review note</h2>
 *
 * <p>
 * {@code BQDatabaseMetaData} accumulated <b>31</b> INFO calls, several of them
 * on every {@code getTables()} and {@code getColumns()}. Metadata methods are
 * called constantly — an IDE walks them every time it refreshes a database tree
 * — so this made a library shout through the host application's logs during
 * ordinary operation. INFO is for the operator of the application, and a JDBC
 * driver doing routine work has nothing to tell them.
 *
 * <p>
 * It is a guard because the failure mode is copy-paste. Every one of those 31
 * was added by someone matching the style of the method next to it, and the
 * next {@code getXxx()} would have done the same. Nothing else catches it: it
 * is not a bug, SpotBugs and PMD say nothing, and it is invisible in review
 * unless the reviewer already knows the history.
 *
 * <p>
 * Lifecycle and configuration events elsewhere in the driver — the driver
 * registering, a session opening, a custom endpoint being used — are
 * legitimately INFO and deliberately out of scope here. The distinction this
 * enforces is per-call versus once-per-connection, not "libraries must be
 * silent".
 *
 * <h2>Why source rather than bytecode</h2>
 *
 * <p>
 * The sibling {@code SharedThreadPoolGuardTest} matches method descriptors in
 * compiled classes, which works there because the descriptors it looks for are
 * distinctive. That approach does not transfer: a call to {@code Logger.info}
 * leaves the constant {@code "info"} in the pool, and a three-letter string
 * appears in unrelated constants often enough to make the check fire on
 * innocent code. Reading the source is exact.
 */
@DisplayName("Guard: metadata code does not log at INFO")
class MetadataLoggingLevelGuardTest {

	private static final Path METADATA_SOURCES = Path.of("src", "main", "java", "vc", "tbc", "bq", "jdbc", "metadata");

	@Test
	@DisplayName("no metadata class logs at INFO on a per-call path")
	void metadataCodeNeverLogsAtInfo() throws IOException {
		assertTrue(Files.isDirectory(METADATA_SOURCES),
				"metadata sources not found at " + METADATA_SOURCES.toAbsolutePath()
						+ " — if the package moved, update this guard rather than deleting it");

		List<String> offenders = new ArrayList<>();

		try (Stream<Path> sources = Files.walk(METADATA_SOURCES)) {
			for (Path source : sources.filter(p -> p.toString().endsWith(".java")).toList()) {
				List<String> lines = Files.readAllLines(source, StandardCharsets.UTF_8);
				for (int i = 0; i < lines.size(); i++) {
					// Matching the call rather than the bare word keeps javadoc and
					// comments that discuss INFO from tripping the guard.
					if (lines.get(i).contains("logger.info(")) {
						offenders.add(METADATA_SOURCES.relativize(source) + ":" + (i + 1));
					}
				}
			}
		}

		assertTrue(offenders.isEmpty(),
				() -> "metadata code logs at INFO, which puts driver chatter into the host application's logs "
						+ "on every schema refresh — use logger.debug instead:\n  " + String.join("\n  ", offenders));
	}

	@Test
	@DisplayName("expensive log arguments are guarded by a level check")
	void expensiveLogArgumentsAreGuarded() throws IOException {
		// Parameterised logging defers *formatting*, not the expressions handed to
		// it. Two calls here built a sublist-into-a-String and walked every cache
		// entry as arguments, so both ran in full with logging switched off. These
		// are the two known sites; the assertion is that they stayed guarded.
		String metaData = Files.readString(METADATA_SOURCES.resolve("BQDatabaseMetaData.java"), StandardCharsets.UTF_8);

		assertTrue(guardPrecedes(metaData, "datasetIds.subList(0, 10)"),
				"the dataset-sample log argument concatenates a String and must stay behind isDebugEnabled()");

		assertTrue(guardPrecedes(metaData, "cache.getStats()"),
				"MetadataCache.getStats() walks every entry to count expired ones and sum rows; as a log "
						+ "argument it must stay behind isDebugEnabled()");
	}

	/**
	 * Whether an {@code isDebugEnabled()} check appears close enough above the
	 * given expression to be guarding it.
	 *
	 * <p>
	 * Deliberately crude — a few hundred characters of lookback, not a parse. A
	 * guard test that needs a Java parser to express its rule is a guard test
	 * nobody will maintain, and the failure mode of being crude here is a false
	 * pass on very unusual formatting, not a false failure on ordinary code.
	 */
	private static boolean guardPrecedes(String source, String expression) {
		int at = source.indexOf(expression);
		if (at < 0) {
			// The expression is gone entirely, so there is nothing left to guard.
			return true;
		}
		int from = Math.max(0, at - 400);
		return source.substring(from, at).contains("isDebugEnabled()");
	}
}

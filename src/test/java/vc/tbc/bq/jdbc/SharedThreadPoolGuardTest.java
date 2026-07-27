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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fails if driver code dispatches work to
 * {@link java.util.concurrent.ForkJoinPool#commonPool()}.
 *
 * <p>
 * Every query used to run through
 * {@code CompletableFuture.supplyAsync(supplier)} with no executor, which lands
 * on the common pool: parallelism of {@code availableProcessors() - 1}, shared
 * with the host application, while each task blocks for a full BigQuery
 * round-trip. Concurrent queries queued behind one another — a {@code SELECT 1}
 * took over 30 seconds with eight callers on a four-core machine — and
 * unrelated parallel work in the same JVM was starved alongside them. A JDBC
 * driver must not do that, and neither SpotBugs nor PMD detects it.
 *
 * <p>
 * The check reads compiled classes and looks for the descriptors of the
 * executor-less overloads. A class only carries those strings if it calls them.
 */
class SharedThreadPoolGuardTest {

	private static final Path CLASSES = Path.of("target", "classes");

	/**
	 * Descriptors of async entry points that default to the common pool, paired
	 * with the call to use instead.
	 */
	private static final String[][] FORBIDDEN = {
			{"(Ljava/util/function/Supplier;)Ljava/util/concurrent/CompletableFuture;",
					"CompletableFuture.supplyAsync(supplier) — pass an explicit Executor"},
			{"(Ljava/lang/Runnable;)Ljava/util/concurrent/CompletableFuture;",
					"CompletableFuture.runAsync(runnable) — pass an explicit Executor"},
			{"parallelStream", "Collection.parallelStream() — runs on the common pool"},};

	@Test
	void driverCodeNeverUsesTheCommonPool() throws IOException {
		assertTrue(Files.isDirectory(CLASSES),
				"compile the driver before running this guard: " + CLASSES.toAbsolutePath());

		List<String> offenders = new ArrayList<>();
		try (Stream<Path> classFiles = Files.walk(CLASSES)) {
			for (Path classFile : classFiles.filter(p -> p.toString().endsWith(".class")).toList()) {
				String bytecode = new String(Files.readAllBytes(classFile), StandardCharsets.ISO_8859_1);
				for (String[] forbidden : FORBIDDEN) {
					if (bytecode.contains(forbidden[0])) {
						offenders.add(CLASSES.relativize(classFile) + " uses " + forbidden[1]);
					}
				}
			}
		}

		assertTrue(offenders.isEmpty(),
				"Driver code must dispatch blocking BigQuery work to its own executor, not ForkJoinPool.commonPool():\n  "
						+ String.join("\n  ", offenders));
	}
}

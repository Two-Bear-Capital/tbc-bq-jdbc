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
package vc.tbc.bq.jdbc.docgen;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverPropertyInfo;
import java.util.ArrayList;
import java.util.List;
import vc.tbc.bq.jdbc.BQDriver;
import vc.tbc.bq.jdbc.auth.AuthType;

/**
 * Generates reference documentation directly from the driver's own source of
 * truth, eliminating drift between code and docs.
 *
 * <p>
 * The content here is <em>derived</em>, never authored:
 *
 * <ul>
 * <li><b>Connection properties</b> come from {@link BQDriver#getPropertyInfo} —
 * the exact list the driver advertises to JDBC tools (name, default, allowed
 * values, description).
 * <li><b>Authentication types</b> come from the {@link AuthType} sealed
 * interface's permitted implementations plus the {@code authType} property's
 * declared choices.
 * </ul>
 *
 * <p>
 * Add a property to {@code getPropertyInfo()} or a permit to {@code AuthType}
 * and the docs update on the next generation — there is nowhere to forget to
 * edit.
 *
 * <h2>Usage</h2>
 *
 * <pre>{@code
 * # Generate Markdown into docs/generated/
 * ./mvnw test-compile exec:java -Pdocs
 *
 * # Write to a custom directory
 * ./mvnw test-compile exec:java -Pdocs -Dexec.args="path/to/out"
 *
 * # CI drift guard: exit 1 if committed docs are stale (regenerate-and-commit needed)
 * ./mvnw test-compile exec:java -Pdocs -Dexec.args="--check"
 * }</pre>
 *
 * @since 1.0.89
 */
public final class DocGen {

	/**
	 * Default output directory, relative to the project root (Maven's working
	 * directory).
	 */
	private static final String DEFAULT_OUTPUT_DIR = "docs/generated";

	private static final String DO_NOT_EDIT = """
			<!--
			  GENERATED FILE — DO NOT EDIT BY HAND.
			  Produced by vc.tbc.bq.jdbc.docgen.DocGen from the driver source of truth.
			  Regenerate with: ./mvnw test-compile exec:java -Pdocs
			-->
			""";

	private DocGen() {
		// utility
	}

	/**
	 * Entry point.
	 *
	 * @param args
	 *            optional {@code --check} flag and/or an output directory path
	 * @throws IOException
	 *             if files cannot be written or read
	 */
	public static void main(String[] args) throws IOException {
		boolean check = false;
		String outputDir = DEFAULT_OUTPUT_DIR;
		for (String arg : args) {
			if ("--check".equals(arg)) {
				check = true;
			} else if (!arg.isBlank()) {
				outputDir = arg;
			}
		}

		Path dir = Path.of(outputDir);
		List<GeneratedDoc> docs = List.of(connectionProperties(), authentication());

		if (check) {
			checkOrFail(dir, docs);
		} else {
			write(dir, docs);
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Document builders
	// ---------------------------------------------------------------------------------------------

	/**
	 * Builds the connection-properties reference from
	 * {@link BQDriver#getPropertyInfo}.
	 */
	private static GeneratedDoc connectionProperties() {
		DriverPropertyInfo[] props;
		try {
			props = new BQDriver().getPropertyInfo(null, null);
		} catch (java.sql.SQLException e) {
			throw new IllegalStateException("getPropertyInfo() should never throw for a null URL", e);
		}

		StringBuilder sb = new StringBuilder();
		sb.append(DO_NOT_EDIT).append('\n');
		sb.append("# Connection Properties\n\n");
		sb.append("The following properties can be supplied as URL query parameters (traditional format) ");
		sb.append("or `java.util.Properties` entries. This table is generated from the driver's own ");
		sb.append("`Driver.getPropertyInfo()`, so it always matches what the driver actually accepts.\n\n");
		sb.append(String.format("There are **%d** connection properties.%n%n", props.length));

		sb.append("| Property | Default | Allowed values | Description |\n");
		sb.append("| --- | --- | --- | --- |\n");
		for (DriverPropertyInfo p : props) {
			sb.append("| `").append(p.name).append("` | ").append(formatDefault(p.value)).append(" | ")
					.append(formatChoices(p.choices)).append(" | ").append(escape(p.description)).append(" |\n");
		}

		return new GeneratedDoc("connection-properties.md", sb.toString());
	}

	/**
	 * Builds the authentication reference from the {@link AuthType} sealed
	 * hierarchy and the {@code authType} property's declared choices.
	 */
	private static GeneratedDoc authentication() {
		StringBuilder sb = new StringBuilder();
		sb.append(DO_NOT_EDIT).append('\n');
		sb.append("# Authentication\n\n");
		sb.append("Authentication is selected with the `authType` connection property. The accepted ");
		sb.append("values below are read straight from the driver's advertised property choices.\n\n");

		String[] choices = authTypeChoices();
		sb.append("## `authType` values\n\n");
		sb.append("| Value | Description |\n");
		sb.append("| --- | --- |\n");
		for (String choice : choices) {
			sb.append("| `").append(choice).append("` | ").append(escape(authChoiceDescription(choice))).append(" |\n");
		}
		sb.append('\n');

		sb.append("## Implementations\n\n");
		sb.append("`AuthType` is a sealed interface; the following implementations are permitted. This ");
		sb.append("list is reflected from the sealed hierarchy, so it stays complete automatically.\n\n");
		sb.append("| Implementation | Notes |\n");
		sb.append("| --- | --- |\n");
		List<String> impls = new ArrayList<>();
		Class<?>[] permitted = AuthType.class.getPermittedSubclasses();
		if (permitted != null) {
			for (Class<?> impl : permitted) {
				impls.add(impl.getSimpleName());
			}
		}
		impls.sort(String::compareTo);
		for (String impl : impls) {
			String note = impl.contains("Emulator")
					? "**Deprecated** — selectable as `authType=EMULATOR`, removed in the next major release."
					: "";
			sb.append("| `").append(impl).append("` | ").append(note).append(" |\n");
		}

		return new GeneratedDoc("authentication.md", sb.toString());
	}

	/**
	 * Pulls the declared {@code authType} choices out of {@code getPropertyInfo()}.
	 */
	private static String[] authTypeChoices() {
		try {
			for (DriverPropertyInfo p : new BQDriver().getPropertyInfo(null, null)) {
				if ("authType".equals(p.name) && p.choices != null) {
					return p.choices;
				}
			}
		} catch (java.sql.SQLException e) {
			throw new IllegalStateException("getPropertyInfo() should never throw for a null URL", e);
		}
		return new String[0];
	}

	private static String authChoiceDescription(String choice) {
		return switch (choice) {
			case "ADC" -> "Application Default Credentials (gcloud, GCE/GKE metadata, env var).";
			case "SERVICE_ACCOUNT" -> "Service account JSON key file (set via `credentials`).";
			case "USER_OAUTH" -> "User OAuth flow (client id/secret/refresh token).";
			case "WORKFORCE" -> "Workforce identity federation (set via `credentialConfigFile`).";
			case "WORKLOAD" -> "Workload identity federation (set via `credentialConfigFile`).";
			default -> "";
		};
	}

	// ---------------------------------------------------------------------------------------------
	// Markdown helpers
	// ---------------------------------------------------------------------------------------------

	private static String formatDefault(String value) {
		return (value == null || value.isBlank()) ? "_(none)_" : "`" + value + "`";
	}

	private static String formatChoices(String[] choices) {
		if (choices == null || choices.length == 0) {
			return "any";
		}
		List<String> quoted = new ArrayList<>();
		for (String c : choices) {
			quoted.add("`" + c + "`");
		}
		return String.join(", ", quoted);
	}

	/** Escapes Markdown table-breaking characters in free text. */
	private static String escape(String text) {
		if (text == null) {
			return "";
		}
		return text.replace("|", "\\|");
	}

	// ---------------------------------------------------------------------------------------------
	// Write / check
	// ---------------------------------------------------------------------------------------------

	private static void write(Path dir, List<GeneratedDoc> docs) throws IOException {
		Files.createDirectories(dir);
		for (GeneratedDoc doc : docs) {
			Path target = dir.resolve(doc.fileName());
			Files.writeString(target, doc.content(), StandardCharsets.UTF_8);
			System.out.printf("Generated %s%n", target);
		}
	}

	private static void checkOrFail(Path dir, List<GeneratedDoc> docs) throws IOException {
		List<String> stale = new ArrayList<>();
		for (GeneratedDoc doc : docs) {
			Path target = dir.resolve(doc.fileName());
			String existing = Files.exists(target) ? Files.readString(target, StandardCharsets.UTF_8) : null;
			if (!doc.content().equals(existing)) {
				stale.add(target.toString());
			}
		}
		if (stale.isEmpty()) {
			System.out.printf("Generated docs are up to date (%d file(s)).%n", docs.size());
			return;
		}
		System.err.println("Generated docs are STALE — regenerate with: ./mvnw test-compile exec:java -Pdocs");
		stale.forEach(f -> System.err.printf("  out of date: %s%n", f));
		System.exit(1);
	}

	/** A generated document: target file name plus its full Markdown content. */
	private record GeneratedDoc(String fileName, String content) {
	}
}

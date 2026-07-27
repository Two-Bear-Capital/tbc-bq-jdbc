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
package vc.tbc.bq.jdbc.benchmark;

import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import vc.tbc.bq.jdbc.DriverVersion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Sweeps {@link ThreadScalingBenchmark} across a range of thread counts and
 * reports how throughput responds.
 *
 * <h2>Why a runner rather than an annotation</h2>
 *
 * <p>
 * JMH's {@code @Threads} takes a single {@code int}, not a set — there is no
 * annotation that sweeps concurrency. The sweep has to be driven
 * programmatically (or by invoking JMH once per {@code -t} value from a shell
 * loop). Doing it here keeps the whole measurement in one command that behaves
 * the same locally and in CI, and gives somewhere to compute the derived number
 * that actually matters: scaling efficiency.
 *
 * <h2>What the numbers mean</h2>
 *
 * <ul>
 * <li><b>Scaling</b> — throughput at N threads divided by throughput at 1
 * thread. Perfect scaling is Nx.</li>
 * <li><b>Efficiency</b> — scaling divided by N, as a percentage. 100% is
 * perfect; a number that falls away as threads rise is the signature of #98,
 * where concurrent work was funnelled through a shared pool and
 * serialized.</li>
 * </ul>
 *
 * <p>
 * Efficiency below 100% is normal and expected — BigQuery itself, the network,
 * and the runner's core count all impose ceilings this driver does not control.
 * A curve that <em>collapses</em> toward 1x total throughput is the failure
 * being watched for, not a curve that merely sags.
 *
 * <h2>Report format</h2>
 *
 * <p>
 * The report is a Markdown table, written to {@code --out} and printed to
 * stdout. It is also the baseline format: a committed report can be passed back
 * as {@code --baseline} to get delta columns. The first three columns are
 * always Benchmark, Threads and Throughput, and the parser depends on that —
 * columns may be appended but not reordered.
 *
 * <p>
 * Nothing here fails a build on a regression. BigQuery latency varies enough
 * between runs and regions that a threshold would flake more often than it
 * would catch anything; the report is a number a human reads.
 *
 * <h2>Usage</h2>
 *
 * <pre>{@code
 * export BENCHMARK_JDBC_URL="jdbc:bigquery:my-project/my_dataset?authType=ADC"
 * ./mvnw test-compile exec:java -Pbenchmark-scaling
 *
 * # Fast wiring check - meaningless numbers, ~2 minutes instead of ~25
 * ./mvnw test-compile exec:java -Pbenchmark-scaling -Dexec.args="--quick"
 *
 * # Compare against the committed baseline
 * ./mvnw test-compile exec:java -Pbenchmark-scaling \
 *     -Dexec.args="--baseline docs/contributing/benchmarks/thread-scaling-baseline.md"
 * }</pre>
 *
 * @since 2.0.0
 */
public final class ThreadScalingRunner {

	/**
	 * The concurrency sweep. Doubling steps rather than a linear range: the
	 * interesting signal is the shape of the curve over an order of magnitude, and
	 * every extra point costs a full warmup and measurement cycle against real
	 * BigQuery.
	 */
	private static final int[] DEFAULT_THREAD_COUNTS = {1, 2, 4, 8, 16};

	private static final Path DEFAULT_OUT = Path.of("target", "benchmarks", "thread-scaling.md");

	private static final String DEFAULT_BASELINE = "docs/contributing/benchmarks/thread-scaling-baseline.md";

	private ThreadScalingRunner() {
	}

	public static void main(String[] args) throws Exception {
		Args parsed = Args.parse(args);

		if (System.getenv("BENCHMARK_JDBC_URL") == null) {
			System.err.println("BENCHMARK_JDBC_URL must be set, e.g.");
			System.err.println("  export BENCHMARK_JDBC_URL=\"jdbc:bigquery:my-project/my_dataset?authType=ADC\"");
			System.exit(2);
		}

		// Benchmark label -> thread count -> measured score. TreeMap on the inner map
		// so thread counts read in ascending order regardless of sweep order.
		Map<String, Map<Integer, Score>> measured = new LinkedHashMap<>();

		for (int threads : parsed.threadCounts) {
			System.out.printf(Locale.ROOT, "%n=== Thread count: %d ===%n%n", threads);
			Collection<RunResult> results = new Runner(options(threads, parsed.quick)).run();
			for (RunResult result : results) {
				String label = label(result);
				measured.computeIfAbsent(label, k -> new TreeMap<>()).put(threads,
						new Score(result.getPrimaryResult().getScore(), result.getPrimaryResult().getScoreError()));
			}
		}

		Map<String, Double> baseline = parsed.baseline == null ? Map.of() : parseBaseline(parsed.baseline);
		String report = render(measured, baseline, parsed);

		Files.createDirectories(parsed.out.toAbsolutePath().getParent());
		Files.writeString(parsed.out, report);

		System.out.println();
		System.out.println(report);
		System.out.println("Report written to " + parsed.out.toAbsolutePath());
	}

	private static org.openjdk.jmh.runner.options.Options options(int threads, boolean quick) {
		OptionsBuilder builder = new OptionsBuilder();
		builder.include(ThreadScalingBenchmark.class.getSimpleName()).threads(threads).shouldFailOnError(true);

		if (quick) {
			// Deliberately too short to mean anything. This exists so the plumbing -
			// credentials, URL, report writing, baseline parsing - can be exercised
			// without a 25-minute round trip.
			builder.warmupIterations(1).warmupTime(TimeValue.seconds(2)).measurementIterations(1)
					.measurementTime(TimeValue.seconds(3));
		}

		return builder.build();
	}

	/**
	 * Builds the row label: the benchmark method name plus any JMH parameters.
	 * Package and class are dropped — every row comes from the same class, so the
	 * prefix is noise that would only make the table harder to read.
	 */
	private static String label(RunResult result) {
		String benchmark = result.getParams().getBenchmark();
		String method = benchmark.substring(benchmark.lastIndexOf('.') + 1);

		StringBuilder params = new StringBuilder();
		for (String key : result.getParams().getParamsKeys()) {
			params.append(params.isEmpty() ? "" : ", ").append(key).append('=')
					.append(result.getParams().getParam(key));
		}

		return params.isEmpty() ? method : method + " (" + params + ")";
	}

	private static String render(Map<String, Map<Integer, Score>> measured, Map<String, Double> baseline, Args args) {
		boolean withBaseline = !baseline.isEmpty();
		StringBuilder md = new StringBuilder();

		md.append("# Thread-scaling benchmark report\n\n");
		md.append("| Field | Value |\n| --- | --- |\n");
		md.append("| Generated | ").append(Instant.now()).append(" |\n");
		md.append("| Driver version | ").append(driverVersion()).append(" |\n");
		md.append("| JVM | ").append(System.getProperty("java.vm.name")).append(' ')
				.append(System.getProperty("java.version")).append(" |\n");
		md.append("| OS | ").append(System.getProperty("os.name")).append(' ').append(System.getProperty("os.arch"))
				.append(" |\n");
		md.append("| Available processors | ").append(Runtime.getRuntime().availableProcessors()).append(" |\n");
		md.append("| Mode | ").append(args.quick ? "**quick — numbers are not meaningful**" : "full").append(" |\n");
		md.append('\n');

		md.append("| Benchmark | Threads | Throughput (ops/s) | Error (±) | Scaling | Efficiency |");
		if (withBaseline) {
			md.append(" Baseline (ops/s) | Δ |");
		}
		md.append('\n');

		md.append("| --- | ---: | ---: | ---: | ---: | ---: |");
		if (withBaseline) {
			md.append(" ---: | ---: |");
		}
		md.append('\n');

		for (Map.Entry<String, Map<Integer, Score>> entry : measured.entrySet()) {
			String benchmark = entry.getKey();
			Map<Integer, Score> byThreads = entry.getValue();

			// Every derived figure is relative to the single-threaded score. Without it
			// there is nothing to divide by, so the columns are left blank rather than
			// filled with a number that would be quietly wrong.
			Score single = byThreads.get(1);

			for (Map.Entry<Integer, Score> point : byThreads.entrySet()) {
				int threads = point.getKey();
				Score score = point.getValue();

				md.append("| ").append(benchmark);
				md.append(" | ").append(threads);
				md.append(" | ").append(num(score.score()));
				md.append(" | ").append(Double.isNaN(score.error()) ? "n/a" : num(score.error()));

				if (single == null || single.score() == 0.0) {
					md.append(" | n/a | n/a");
				} else {
					double scaling = score.score() / single.score();
					md.append(" | ").append(String.format(Locale.ROOT, "%.2fx", scaling));
					md.append(" | ").append(String.format(Locale.ROOT, "%.0f%%", 100.0 * scaling / threads));
				}

				if (withBaseline) {
					Double base = baseline.get(baselineKey(benchmark, threads));
					if (base == null || base == 0.0) {
						md.append(" | n/a | n/a");
					} else {
						md.append(" | ").append(num(base));
						md.append(" | ")
								.append(String.format(Locale.ROOT, "%+.1f%%", 100.0 * (score.score() - base) / base));
					}
				}

				md.append(" |\n");
			}
		}

		md.append('\n');
		md.append("**Scaling** is throughput at N threads over throughput at 1 thread; **Efficiency** is that\n");
		md.append("divided by N. Efficiency well under 100% is normal — BigQuery, the network and the runner's\n");
		md.append("core count all cap it. The failure this watches for is a curve that *collapses*, where total\n");
		md.append("throughput stops rising with threads at all: that is the shape of #98.\n");

		return md.toString();
	}

	/**
	 * Formats to three significant-ish decimals under {@link Locale#ROOT}. The
	 * locale is not incidental: a committed baseline formatted in a locale that
	 * uses a decimal comma would be unparseable by {@link #parseBaseline}, and the
	 * failure would look like a mysteriously absent baseline column rather than a
	 * formatting bug.
	 */
	private static String num(double value) {
		return String.format(Locale.ROOT, "%.3f", value);
	}

	private static String baselineKey(String benchmark, int threads) {
		return benchmark + "@" + threads;
	}

	/**
	 * Version plus commit, so a committed baseline says what it was measured
	 * against. A baseline with no provenance is only marginally better than no
	 * baseline.
	 */
	private static String driverVersion() {
		return DriverVersion.getVersionString() + " (" + DriverVersion.getGitCommitIdShort() + ")";
	}

	/**
	 * Reads throughput out of a previously generated report.
	 *
	 * <p>
	 * The report is its own baseline format, so this parses the Markdown table it
	 * writes: any line starting with {@code |} whose second cell is an integer is a
	 * data row, and the first three cells are Benchmark, Threads, Throughput. The
	 * metadata table at the top of the report is skipped by that same rule — its
	 * second cell is never an integer.
	 */
	private static Map<String, Double> parseBaseline(Path path) throws IOException {
		Map<String, Double> baseline = new LinkedHashMap<>();

		for (String line : Files.readAllLines(path)) {
			String trimmed = line.strip();
			if (!trimmed.startsWith("|")) {
				continue;
			}

			String[] cells = trimmed.split("\\|", -1);
			// split on a leading delimiter yields an empty first element, so the data
			// cells start at index 1.
			if (cells.length < 4) {
				continue;
			}

			try {
				String benchmark = cells[1].strip();
				int threads = Integer.parseInt(cells[2].strip());
				double throughput = Double.parseDouble(cells[3].strip());
				baseline.put(baselineKey(benchmark, threads), throughput);
			} catch (NumberFormatException notADataRow) {
				// Header, separator, or the metadata table. Skipping is the whole point of
				// attempting the parse.
			}
		}

		if (baseline.isEmpty()) {
			System.err.println("Warning: no data rows parsed from baseline " + path
					+ " — the report will have no comparison columns.");
		}

		return baseline;
	}

	private record Score(double score, double error) {
	}

	private record Args(int[] threadCounts, boolean quick, Path out, Path baseline) {

		static Args parse(String[] argv) {
			int[] threadCounts = DEFAULT_THREAD_COUNTS;
			boolean quick = false;
			Path out = DEFAULT_OUT;
			Path baseline = null;

			for (int i = 0; i < argv.length; i++) {
				switch (argv[i]) {
					case "--quick" -> quick = true;
					case "--out" -> out = Path.of(require(argv, ++i, "--out"));
					case "--baseline" -> baseline = Path.of(require(argv, ++i, "--baseline"));
					case "--threads" -> threadCounts = parseThreadCounts(require(argv, ++i, "--threads"));
					default -> throw new IllegalArgumentException("Unknown argument: " + argv[i]
							+ "\nUsage: ThreadScalingRunner [--quick] [--threads 1,2,4] [--out FILE] [--baseline FILE]");
				}
			}

			// Resolving the default here rather than in the field initializer means an
			// absent baseline file is silently fine, while one named explicitly and
			// missing is an error the caller hears about.
			if (baseline == null && Files.exists(Path.of(DEFAULT_BASELINE))) {
				baseline = Path.of(DEFAULT_BASELINE);
			}

			return new Args(threadCounts, quick, out, baseline);
		}

		private static String require(String[] argv, int index, String flag) {
			if (index >= argv.length) {
				throw new IllegalArgumentException(flag + " requires a value");
			}
			return argv[index];
		}

		private static int[] parseThreadCounts(String value) {
			List<Integer> counts = new ArrayList<>();
			for (String part : value.split(",")) {
				counts.add(Integer.parseInt(part.strip()));
			}
			if (counts.isEmpty()) {
				throw new IllegalArgumentException("--threads requires at least one count");
			}
			return counts.stream().mapToInt(Integer::intValue).toArray();
		}
	}
}

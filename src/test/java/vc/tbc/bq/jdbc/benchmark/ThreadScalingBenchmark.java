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

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.TimeUnit;

/**
 * Throughput benchmarks measured across a sweep of thread counts.
 *
 * <h2>Why this exists</h2>
 *
 * <p>
 * The other benchmarks in this package are single-threaded latency
 * measurements. None of them varies concurrency, which means a total
 * concurrency collapse passes all of them — and that is exactly what happened
 * with #98: queries were dispatched to {@code ForkJoinPool.commonPool()}, so
 * concurrent callers serialized and a {@code SELECT 1} took over 30 seconds
 * with eight of them. It surfaced as a test timeout that was twice widened
 * before anyone looked at the cause.
 *
 * <p>
 * The question these benchmarks answer is not "how fast is it" but <em>does
 * throughput scale with threads, or flatten?</em> Flattening is the signature
 * of the whole class of defect #98 belonged to. Absolute numbers against
 * BigQuery are dominated by network latency and vary with the day; the
 * <em>shape</em> of the scaling curve is the durable signal.
 *
 * <h2>Connections are per thread, deliberately</h2>
 *
 * <p>
 * {@link Conn} is {@link Scope#Thread}, so every JMH worker opens its own
 * {@link Connection}. Sharing one connection would measure the wrong thing
 * twice over: a JDBC {@code Connection} is not intended for concurrent use, and
 * BigQuery forbids concurrent queries within a session, so a shared connection
 * would serialize by construction and report flat scaling no matter how good
 * the driver was. The deployed shape is a pool handing a connection to each
 * caller, which is what this models.
 *
 * <h2>Running</h2>
 *
 * <p>
 * Driven by {@link ThreadScalingRunner}, which sweeps the thread counts and
 * writes the report — do not invoke this class through plain {@code jmh.Main},
 * which would run it at a single thread count and tell you nothing:
 *
 * <pre>{@code
 * export BENCHMARK_JDBC_URL="jdbc:bigquery:my-project/my_dataset?authType=ADC"
 * ./mvnw test-compile exec:java -Pbenchmark-scaling
 * }</pre>
 *
 * @since 2.0.0
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Fork(value = 1, jvmArgs = {"-Xms2G", "-Xmx2G"})
public class ThreadScalingBenchmark {

	/**
	 * Rows iterated by {@link #iterateResultSet}. Above the 10,000 default page
	 * size on purpose, so the measurement covers pagination rather than a single
	 * page — pagination is where a per-row cost regression would show up.
	 */
	static final String ROW_COUNT = "50000";

	/**
	 * Trial-wide configuration, resolved once. Kept separate from {@link Conn} so
	 * the environment is validated a single time rather than once per worker
	 * thread, and so a missing URL fails with one clear message instead of N racing
	 * ones.
	 */
	@State(Scope.Benchmark)
	public static class Config {

		String jdbcUrl;

		@Setup(Level.Trial)
		public void resolve() throws Exception {
			jdbcUrl = System.getenv("BENCHMARK_JDBC_URL");
			if (jdbcUrl == null || jdbcUrl.isBlank()) {
				throw new IllegalStateException(
						"BENCHMARK_JDBC_URL environment variable must be set to run benchmarks");
			}
			Class.forName("vc.tbc.bq.jdbc.BQDriver");
		}
	}

	/**
	 * One connection per JMH worker thread, opened at trial level.
	 *
	 * <p>
	 * Trial rather than iteration: opening a BigQuery connection is expensive
	 * enough that per-iteration setup would show up as a scaling artifact of its
	 * own, and it is the steady-state throughput of an established connection that
	 * is under test here. Connection establishment cost is measured separately by
	 * {@code QueryBenchmark.benchmarkConnectionCreation}.
	 */
	@State(Scope.Thread)
	public static class Conn {

		Connection connection;
		DatabaseMetaData metaData;

		@Setup(Level.Trial)
		public void open(Config config) throws Exception {
			connection = DriverManager.getConnection(config.jdbcUrl);
			metaData = connection.getMetaData();
		}

		@TearDown(Level.Trial)
		public void close() throws Exception {
			if (connection != null && !connection.isClosed()) {
				connection.close();
			}
		}
	}

	/**
	 * Query submit through to the first row.
	 *
	 * <p>
	 * The narrowest measurement of the dispatch path, and the exact operation that
	 * collapsed under #98. {@code SELECT 1} scans no bytes, so this is free to run
	 * and isolates driver-side dispatch from BigQuery's own execution cost.
	 */
	@Benchmark
	@Warmup(iterations = 1, time = 10, timeUnit = TimeUnit.SECONDS)
	@Measurement(iterations = 3, time = 15, timeUnit = TimeUnit.SECONDS)
	public void submitToFirstRow(Conn conn, Blackhole blackhole) throws Exception {
		try (Statement stmt = conn.connection.createStatement();
				ResultSet rs = stmt.executeQuery("SELECT 1 AS value")) {
			if (rs.next()) {
				blackhole.consume(rs.getInt(1));
			}
		}
	}

	/**
	 * Full iteration of a multi-page result set.
	 *
	 * <p>
	 * Generated with {@code GENERATE_ARRAY} rather than read from a fixture table
	 * so the benchmark needs no seeded data and scans no billable bytes, while
	 * still producing enough rows to cross several pages.
	 */
	@Benchmark
	@Warmup(iterations = 1, time = 10, timeUnit = TimeUnit.SECONDS)
	@Measurement(iterations = 3, time = 15, timeUnit = TimeUnit.SECONDS)
	public void iterateResultSet(Conn conn, Blackhole blackhole) throws Exception {
		String query = "SELECT row_num, CONCAT('name_', CAST(row_num AS STRING)) AS name, "
				+ "row_num * 100 AS value, MOD(row_num, 2) = 0 AS is_even " + "FROM UNNEST(GENERATE_ARRAY(1, "
				+ ROW_COUNT + ")) AS row_num";

		try (Statement stmt = conn.connection.createStatement(); ResultSet rs = stmt.executeQuery(query)) {
			int count = 0;
			while (rs.next()) {
				blackhole.consume(rs.getLong(1));
				blackhole.consume(rs.getString(2));
				blackhole.consume(rs.getLong(3));
				blackhole.consume(rs.getBoolean(4));
				count++;
			}
			blackhole.consume(count);
		}
	}

	/**
	 * {@code getTables()} across the connection's project.
	 *
	 * <p>
	 * This measures the <em>warm</em> path: the metadata cache is shared statically
	 * across connections and survives the warmup, so after the first call every
	 * invocation is a cache hit. That is deliberate and it is the realistic shape —
	 * IntelliJ reopens connections constantly and hits this repeatedly — but it
	 * means a regression in the cold fan-out will not appear here. Cold fan-out is
	 * covered by the wide-metadata scale test instead. What this catches is
	 * contention on the shared cache: if the cache serialized its readers,
	 * throughput here would flatten while every other benchmark scaled.
	 */
	@Benchmark
	@Warmup(iterations = 1, time = 10, timeUnit = TimeUnit.SECONDS)
	@Measurement(iterations = 3, time = 15, timeUnit = TimeUnit.SECONDS)
	public void getTablesWarm(Conn conn, Blackhole blackhole) throws Exception {
		try (ResultSet rs = conn.metaData.getTables(null, null, "%", null)) {
			int count = 0;
			while (rs.next()) {
				blackhole.consume(rs.getString("TABLE_NAME"));
				count++;
			}
			blackhole.consume(count);
		}
	}

	/**
	 * {@code getColumns()} across the connection's project.
	 *
	 * <p>
	 * Same warm-cache caveat as {@link #getTablesWarm}. This one additionally
	 * covers the per-row pattern-matching path that #99 found compiling a regex for
	 * every metadata row; a return of that defect shows up as a throughput drop
	 * here rather than a scaling change.
	 */
	@Benchmark
	@Warmup(iterations = 1, time = 10, timeUnit = TimeUnit.SECONDS)
	@Measurement(iterations = 3, time = 15, timeUnit = TimeUnit.SECONDS)
	public void getColumnsWarm(Conn conn, Blackhole blackhole) throws Exception {
		try (ResultSet rs = conn.metaData.getColumns(null, null, "%", "%")) {
			int count = 0;
			while (rs.next()) {
				blackhole.consume(rs.getString("COLUMN_NAME"));
				count++;
			}
			blackhole.consume(count);
		}
	}
}

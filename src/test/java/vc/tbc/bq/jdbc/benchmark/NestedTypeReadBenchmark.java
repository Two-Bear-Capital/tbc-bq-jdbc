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
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.TimeUnit;

/**
 * Compares the Storage Read API path against the REST path on results
 * containing nested types.
 *
 * <p>
 * The driver's headline figure — 11.7x faster on a 1M-row result (#152) — was
 * measured on flat scalar data, which was all the Storage path supported at the
 * time. #193 extended it to ARRAY, STRUCT and INTERVAL, where the cost profile
 * differs: a nested value allocates a {@code FieldValue} per element per row
 * plus a {@code FieldValueList} per array and per struct, against one
 * allocation for a scalar.
 *
 * <p>
 * The REST path does comparable work parsing JSON into the same objects, so
 * Arrow probably still wins — but "probably" is what this benchmark exists to
 * replace. If it does not win, {@code useStorageApi=auto} is choosing the
 * slower path for exactly the shapes #193 set out to help (#232).
 *
 * <p>
 * <b>Both paths are driven from one {@code BENCHMARK_JDBC_URL}</b>, with
 * {@code useStorageApi} appended per parameter, so the two differ in nothing
 * else. The scalar shape is included as the control: it is the case the 11.7x
 * figure describes, so if it does not reproduce here the run says nothing about
 * the nested ones.
 *
 * <p>
 * <b>What it found</b>, at 1,000,000 rows with 5x60s measured iterations:
 *
 * <pre>
 * shape         Storage            REST               speedup
 * SCALAR         3418 +/- 622 ms   19134 +/- 1084 ms    5.6x
 * ARRAY_STRUCT   4639 +/- 457 ms   40189 +/- 5928 ms    8.7x
 * </pre>
 *
 * The speedup is <em>larger</em> for nested data, not smaller. Going from
 * scalar to nested costs the Storage path 1.36x and the REST path 2.10x, so
 * Arrow degrades more gracefully than JSON parsing does — re-encoding nested
 * values into {@code FieldValue}s is cheaper than parsing them out of JSON.
 * {@code useStorageApi=auto} is choosing correctly for these shapes and needs
 * no adjustment (#232).
 *
 * <p>
 * <b>Read the control before reading anything else.</b> Every op re-executes
 * its query, so each measurement includes BigQuery's job scheduling and
 * compute, which vary by seconds and are paid identically by both paths. On
 * short runs that variance swamps the fetch difference: a first pass at 1,000
 * and 50,000 rows produced error bars larger than the scores, and the scalar
 * control came out <em>slower</em> on the Storage path — the read session's
 * fixed cost, which is why {@code auto} has a row threshold at all. Treat a run
 * whose control does not reproduce as a run that measured nothing.
 *
 * <p>
 * That shared compute is also why the control reads 5.6x here against the 11.7x
 * of #152 rather than contradicting it: a constant paid by both paths
 * compresses the ratio toward 1, so the fetch-only figure is higher than what
 * this measures.
 *
 * @since 3.3.0
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Fork(value = 1, jvmArgs = {"-Xms2G", "-Xmx2G", "--add-opens=java.base/java.nio=ALL-UNNAMED"})
@Warmup(iterations = 2, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
public class NestedTypeReadBenchmark {

	private static final String JDBC_URL = System.getenv("BENCHMARK_JDBC_URL");

	/**
	 * Enough rows for the per-row cost to dominate the fixed cost of opening a read
	 * session, which is what the Storage path trades away.
	 */
	@Param({"50000"})
	private int rowCount;

	@Param({"true", "false"})
	private String useStorageApi;

	/**
	 * The column shapes under test. {@code SCALAR} is the control — the shape the
	 * existing 11.7x figure describes.
	 */
	@Param({"SCALAR", "ARRAY_INT", "ARRAY_STRUCT", "STRUCT_WIDE"})
	private String shape;

	private Connection connection;

	@Setup(Level.Trial)
	public void setupTrial() throws Exception {
		if (JDBC_URL == null) {
			throw new IllegalStateException("BENCHMARK_JDBC_URL environment variable must be set to run benchmarks");
		}
		Class.forName("vc.tbc.bq.jdbc.BQDriver");
	}

	@Setup(Level.Iteration)
	public void setupIteration() throws Exception {
		String separator = JDBC_URL.contains("?") ? "&" : "?";
		connection = DriverManager.getConnection(JDBC_URL + separator + "useStorageApi=" + useStorageApi);
		assertPathInUse();
	}

	/**
	 * Fails the run if the connection is not using the path its parameter names.
	 *
	 * <p>
	 * Without this the benchmark's central comparison could be REST against REST
	 * and every number would look plausible. The Storage path falls back silently
	 * and by design — Arrow cannot allocate without a JVM flag, the read session
	 * can be refused, a type can be unsupported — so "it fell back" is a normal
	 * outcome that must not be mistaken for "it was slower". The parity tests carry
	 * the same guard for the same reason.
	 */
	private void assertPathInUse() throws Exception {
		String expected = Boolean.parseBoolean(useStorageApi)
				? "vc.tbc.bq.jdbc.storage.StorageReadResultSet"
				: "vc.tbc.bq.jdbc.BQResultSet";
		String sql = "SELECT " + projection() + " FROM UNNEST(GENERATE_ARRAY(1, 10)) AS i";

		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
			String actual = rs.getClass().getName();
			if (!expected.equals(actual)) {
				throw new IllegalStateException("useStorageApi=" + useStorageApi + " with shape " + shape
						+ " produced a " + actual + ", not a " + expected
						+ ". Comparing a path against itself would make every result here meaningless.");
			}
		}
	}

	@TearDown(Level.Iteration)
	public void tearDownIteration() throws Exception {
		if (connection != null && !connection.isClosed()) {
			connection.close();
		}
	}

	/** The projection under test, one column plus the row number. */
	private String projection() {
		return switch (shape) {
			case "SCALAR" -> "i AS a, CONCAT('s', CAST(i AS STRING)) AS b";
			case "ARRAY_INT" -> "[i, i + 1, i + 2, i + 3] AS a";
			case "ARRAY_STRUCT" ->
				"[STRUCT(i AS n, CONCAT('s', CAST(i AS STRING)) AS s), " + "STRUCT(i + 1 AS n, 'x' AS s)] AS a";
			case "STRUCT_WIDE" -> "STRUCT(i AS n, CONCAT('s', CAST(i AS STRING)) AS s, i * 2 AS n2, "
					+ "CAST(i AS FLOAT64) / 3 AS f, MOD(i, 2) = 0 AS flag) AS a";
			default -> throw new IllegalArgumentException("Unknown shape: " + shape);
		};
	}

	/**
	 * Reads every row and every column as a string.
	 *
	 * <p>
	 * {@code getString} deliberately: it is what forces a nested value to be
	 * rendered, so the encoding work this benchmark is about actually happens. A
	 * benchmark that only called {@code next()} would measure transport and skip
	 * the part #193 changed.
	 */
	@Benchmark
	public void readAll(Blackhole blackhole) throws Exception {
		String sql = "SELECT " + projection() + " FROM UNNEST(GENERATE_ARRAY(1, " + rowCount + ")) AS i";

		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
			int columns = rs.getMetaData().getColumnCount();
			int rows = 0;
			while (rs.next()) {
				for (int i = 1; i <= columns; i++) {
					blackhole.consume(rs.getString(i));
				}
				rows++;
			}
			blackhole.consume(rows);
		}
	}
}

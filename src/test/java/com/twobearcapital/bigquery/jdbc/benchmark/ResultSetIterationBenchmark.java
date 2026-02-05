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
package com.twobearcapital.bigquery.jdbc.benchmark;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

/**
 * JMH benchmarks for ResultSet iteration throughput.
 *
 * <p>
 * Measures the performance of iterating through result sets of various sizes.
 *
 * @since 1.0.0
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Fork(value = 1, jvmArgs = {"-Xms2G", "-Xmx2G"})
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
public class ResultSetIterationBenchmark {

	private Connection connection;
	private static final String JDBC_URL = System.getenv("BENCHMARK_JDBC_URL");

	@Param({"100", "1000", "10000"})
	private int rowCount;

	@Setup(Level.Trial)
	public void setupTrial() throws Exception {
		if (JDBC_URL == null) {
			throw new IllegalStateException("BENCHMARK_JDBC_URL environment variable must be set to run benchmarks");
		}
		Class.forName("com.twobearcapital.bigquery.jdbc.BQDriver");
	}

	@Setup(Level.Iteration)
	public void setupIteration() throws Exception {
		connection = DriverManager.getConnection(JDBC_URL);
	}

	@TearDown(Level.Iteration)
	public void tearDownIteration() throws Exception {
		if (connection != null && !connection.isClosed()) {
			connection.close();
		}
	}

	/** Benchmark: Iterate through generated rows. */
	@Benchmark
	public void benchmarkResultSetIteration(Blackhole blackhole) throws Exception {
		String query = String.format(
				"SELECT row_num, " + "CONCAT('name_', CAST(row_num AS STRING)) as name, " + "row_num * 100 as value, "
						+ "MOD(row_num, 2) = 0 as is_even " + "FROM UNNEST(GENERATE_ARRAY(1, %d)) as row_num",
				rowCount);

		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(query)) {
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

	/** Benchmark: Column access by name vs index. */
	@Benchmark
	public void benchmarkColumnAccessByName(Blackhole blackhole) throws Exception {
		String query = "SELECT 1 as id, 'test' as name, 123.45 as value";

		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(query)) {
			while (rs.next()) {
				blackhole.consume(rs.getLong("id"));
				blackhole.consume(rs.getString("name"));
				blackhole.consume(rs.getDouble("value"));
			}
		}
	}

	/** Benchmark: Column access by index (faster). */
	@Benchmark
	public void benchmarkColumnAccessByIndex(Blackhole blackhole) throws Exception {
		String query = "SELECT 1 as id, 'test' as name, 123.45 as value";

		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(query)) {
			while (rs.next()) {
				blackhole.consume(rs.getLong(1));
				blackhole.consume(rs.getString(2));
				blackhole.consume(rs.getDouble(3));
			}
		}
	}
}

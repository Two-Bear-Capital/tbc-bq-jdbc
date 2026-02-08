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
package com.tbc.bq.jdbc.benchmark;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmarks for query execution latency.
 *
 * <p>
 * Run with: mvn clean package -DskipTests && java -jar target/benchmarks.jar
 * QueryBenchmark
 *
 * @since 1.0.0
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Fork(value = 1, jvmArgs = {"-Xms2G", "-Xmx2G"})
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
public class QueryBenchmark {

	private Connection connection;
	private static final String JDBC_URL = System.getenv("BENCHMARK_JDBC_URL");

	@Setup(Level.Trial)
	public void setupTrial() throws Exception {
		if (JDBC_URL == null) {
			throw new IllegalStateException("BENCHMARK_JDBC_URL environment variable must be set to run benchmarks");
		}
		// Load driver
		Class.forName("com.tbc.bq.jdbc.BQDriver");
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

	/** Baseline: SELECT 1 query to measure minimum latency. */
	@Benchmark
	public void benchmarkSelectOne(Blackhole blackhole) throws Exception {
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery("SELECT 1 as value")) {
			while (rs.next()) {
				blackhole.consume(rs.getInt(1));
			}
		}
	}

	/** Small query: SELECT with simple WHERE clause. */
	@Benchmark
	public void benchmarkSmallQuery(Blackhole blackhole) throws Exception {
		try (Statement stmt = connection.createStatement();
				ResultSet rs = stmt
						.executeQuery("SELECT name, value FROM UNNEST([STRUCT('test' AS name, 123 AS value)])")) {
			while (rs.next()) {
				blackhole.consume(rs.getString(1));
				blackhole.consume(rs.getInt(2));
			}
		}
	}

	/** Connection creation latency. */
	@Benchmark
	public void benchmarkConnectionCreation(Blackhole blackhole) throws Exception {
		try (Connection conn = DriverManager.getConnection(JDBC_URL)) {
			blackhole.consume(conn.isValid(5));
		}
	}
}

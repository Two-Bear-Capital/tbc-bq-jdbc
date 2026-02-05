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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

/**
 * JMH benchmarks for PreparedStatement parameter binding.
 *
 * <p>
 * Measures the overhead of parameter binding vs simple statements.
 *
 * @since 1.0.0
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Fork(value = 1, jvmArgs = {"-Xms2G", "-Xmx2G"})
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
public class PreparedStatementBenchmark {

	private Connection connection;
	private static final String JDBC_URL = System.getenv("BENCHMARK_JDBC_URL");

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

	/** Benchmark: Single parameter binding. */
	@Benchmark
	public void benchmarkSingleParameter(Blackhole blackhole) throws Exception {
		String sql = "SELECT ? as value";
		try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
			pstmt.setInt(1, 42);
			try (ResultSet rs = pstmt.executeQuery()) {
				while (rs.next()) {
					blackhole.consume(rs.getInt(1));
				}
			}
		}
	}

	/** Benchmark: Multiple parameter binding. */
	@Benchmark
	public void benchmarkMultipleParameters(Blackhole blackhole) throws Exception {
		String sql = "SELECT ? as id, ? as name, ? as value, ? as flag";
		try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
			pstmt.setLong(1, 123L);
			pstmt.setString(2, "test");
			pstmt.setDouble(3, 456.78);
			pstmt.setBoolean(4, true);
			try (ResultSet rs = pstmt.executeQuery()) {
				while (rs.next()) {
					blackhole.consume(rs.getLong(1));
					blackhole.consume(rs.getString(2));
					blackhole.consume(rs.getDouble(3));
					blackhole.consume(rs.getBoolean(4));
				}
			}
		}
	}

	/** Benchmark: PreparedStatement reuse. */
	@Benchmark
	public void benchmarkPreparedStatementReuse(Blackhole blackhole) throws Exception {
		String sql = "SELECT ? as value";
		try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
			for (int i = 0; i < 10; i++) {
				pstmt.setInt(1, i);
				try (ResultSet rs = pstmt.executeQuery()) {
					while (rs.next()) {
						blackhole.consume(rs.getInt(1));
					}
				}
			}
		}
	}

	/** Benchmark: Parameter metadata access. */
	@Benchmark
	public void benchmarkParameterMetaData(Blackhole blackhole) throws Exception {
		String sql = "SELECT ? as a, ? as b, ? as c";
		try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
			blackhole.consume(pstmt.getParameterMetaData().getParameterCount());
		}
	}
}

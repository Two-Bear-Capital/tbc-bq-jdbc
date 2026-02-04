/*
 * Copyright 2026 Two Bear Capital
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.twobearcapital.bigquery.jdbc.integration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for BigQuery integration tests using Testcontainers.
 *
 * <p>
 * This class sets up a BigQuery emulator container for integration testing.
 * Tests can be run against the emulator or against a real BigQuery instance if
 * credentials are provided.
 *
 * @since 1.0.0
 */
@Testcontainers
public abstract class AbstractBigQueryIntegrationTest {

	private static final Logger logger = LoggerFactory.getLogger(AbstractBigQueryIntegrationTest.class);

	protected static final String TEST_PROJECT_ID = "test-project";
	protected static final String TEST_DATASET = "test_dataset";

	@SuppressWarnings("resource") // Container lifecycle managed by JUnit @Container annotation
	@Container
	protected static final GenericContainer<?> bigqueryEmulator = new GenericContainer<>(
			DockerImageName.parse("ghcr.io/goccy/bigquery-emulator:latest")).withExposedPorts(9050)
			.withCommand("--project=" + TEST_PROJECT_ID, "--dataset=" + TEST_DATASET);

	protected Connection connection;

	@BeforeAll
	static void setupClass() {
		logger.info("BigQuery emulator started on port: {}", bigqueryEmulator.getMappedPort(9050));
	}

	@BeforeEach
	void setup() throws SQLException {
		logger.info("Connecting to BigQuery emulator at: {}:{}", bigqueryEmulator.getHost(),
				bigqueryEmulator.getMappedPort(9050));

		// For emulator, we use a simplified connection approach
		// In production tests, you would use proper authentication
		connection = createTestConnection();

		// Create test dataset if it doesn't exist
		setupTestDataset();
	}

	@AfterEach
	void tearDown() throws SQLException {
		if (connection != null && !connection.isClosed()) {
			connection.close();
		}
	}

	/**
	 * Creates a connection for testing.
	 *
	 * <p>
	 * Override this method to use real BigQuery credentials in production tests.
	 *
	 * @return a JDBC connection
	 * @throws SQLException
	 *             if connection fails
	 */
	protected Connection createTestConnection() throws SQLException {
		// For emulator testing, connect directly to the emulator
		// When a custom host is provided, authType automatically defaults to EMULATOR
		// UseDestinationTables=true is required for emulator compatibility
		String host = bigqueryEmulator.getHost();
		int port = bigqueryEmulator.getMappedPort(9050);
		String url = String.format("jdbc:bigquery://%s:%d;ProjectId=%s;DefaultDataset=%s;UseDestinationTables=true",
				host, port, TEST_PROJECT_ID, TEST_DATASET);
		return DriverManager.getConnection(url);
	}

	/**
	 * Sets up the test dataset.
	 *
	 * @throws SQLException
	 *             if setup fails
	 */
	protected void setupTestDataset() throws SQLException {
		// Dataset is created by the emulator
		logger.info("Test dataset ready: {}", TEST_DATASET);
	}

	/**
	 * Executes SQL and ignores errors (useful for cleanup).
	 *
	 * @param sql
	 *            the SQL to execute
	 */
	protected void executeIgnoreErrors(String sql) {
		try (Statement stmt = connection.createStatement()) {
			stmt.execute(sql);
		} catch (SQLException e) {
			logger.debug("Ignoring error executing: {} - {}", sql, e.getMessage());
		}
	}

	/**
	 * Creates a test table with sample data.
	 *
	 * @param tableName
	 *            the table name
	 * @throws SQLException
	 *             if creation fails
	 */
	protected void createTestTable(String tableName) throws SQLException {
		executeIgnoreErrors("DROP TABLE IF EXISTS " + tableName);

		String createTable = String.format("CREATE TABLE %s (" + "id INT64, " + "name STRING, " + "age INT64, "
				+ "salary FLOAT64, " + "is_active BOOL, " + "created_date DATE" + ")", tableName);

		try (Statement stmt = connection.createStatement()) {
			stmt.execute(createTable);
			logger.info("Created test table: {}", tableName);
		}
	}

	/**
	 * Inserts test data into a table.
	 *
	 * @param tableName
	 *            the table name
	 * @throws SQLException
	 *             if insert fails
	 */
	protected void insertTestData(String tableName) throws SQLException {
		String insert = String.format("INSERT INTO %s (id, name, age, salary, is_active, created_date) VALUES "
				+ "(1, 'Alice', 30, 75000.50, true, DATE '2024-01-15'), "
				+ "(2, 'Bob', 25, 60000.00, true, DATE '2024-02-20'), "
				+ "(3, 'Charlie', 35, 85000.75, false, DATE '2024-03-10')", tableName);

		try (Statement stmt = connection.createStatement()) {
			stmt.executeUpdate(insert);
			logger.info("Inserted test data into: {}", tableName);
		}
	}
}

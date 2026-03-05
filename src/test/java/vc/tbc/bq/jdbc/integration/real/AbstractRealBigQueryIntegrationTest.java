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
package vc.tbc.bq.jdbc.integration.real;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Base class for real BigQuery integration tests.
 *
 * <p>
 * Unlike the emulator-based
 * {@link vc.tbc.bq.jdbc.integration.AbstractBigQueryIntegrationTest}, this
 * class connects to a real Google BigQuery instance using Application Default
 * Credentials (ADC).
 *
 * <p>
 * Tests in this class are skipped automatically unless the
 * {@code BQ_TEST_PROJECT} environment variable is set. This ensures the tests
 * are only run when real BigQuery credentials are available.
 *
 * <h2>Local Development</h2>
 *
 * <pre>{@code
 * gcloud auth application-default login
 * export BQ_TEST_PROJECT=my-gcp-project
 * export BQ_TEST_DATASET=tbc_bq_jdbc_integration_tests
 * ./mvnw verify -Preal-integration-tests
 * }</pre>
 *
 * <h2>CI/CD</h2>
 *
 * <p>
 * In GitHub Actions, authentication is handled via Workload Identity Federation
 * (WIF). The {@code BQ_TEST_PROJECT} and {@code BQ_TEST_DATASET} environment
 * variables are set in the workflow configuration.
 *
 * @since 1.0.68
 */
@EnabledIfEnvironmentVariable(named = "BQ_TEST_PROJECT", matches = ".+", disabledReason = "BQ_TEST_PROJECT env var not set — skipping real BigQuery tests")
public abstract class AbstractRealBigQueryIntegrationTest {

	private static final Logger logger = LoggerFactory.getLogger(AbstractRealBigQueryIntegrationTest.class);

	private static final String DEFAULT_DATASET = "tbc_bq_jdbc_integration_tests";

	protected static final String TEST_PROJECT_ID = System.getenv().getOrDefault("BQ_TEST_PROJECT", "");
	protected static final String TEST_DATASET = System.getenv().getOrDefault("BQ_TEST_DATASET", DEFAULT_DATASET);

	protected Connection connection;

	@BeforeEach
	void setup() throws SQLException {
		logger.info("Connecting to real BigQuery project: {}, dataset: {}", TEST_PROJECT_ID, TEST_DATASET);
		connection = createTestConnection();
		setupTestDataset();
	}

	@AfterEach
	void tearDown() throws SQLException {
		if (connection != null && !connection.isClosed()) {
			connection.close();
		}
	}

	/**
	 * Creates a JDBC connection to real BigQuery using Application Default
	 * Credentials.
	 *
	 * @return a JDBC connection
	 * @throws SQLException
	 *             if connection fails
	 */
	protected Connection createTestConnection() throws SQLException {
		String url = String.format("jdbc:bigquery:%s/%s?authType=ADC", TEST_PROJECT_ID, TEST_DATASET);
		logger.debug("Connecting with URL: {}", url);
		return DriverManager.getConnection(url);
	}

	/**
	 * Sets up the test dataset, creating it if it does not already exist.
	 *
	 * @throws SQLException
	 *             if setup fails
	 */
	protected void setupTestDataset() throws SQLException {
		logger.info("Test dataset ready: {}.{}", TEST_PROJECT_ID, TEST_DATASET);
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
		String createTable = String.format("CREATE OR REPLACE TABLE %s (" + "id INT64, " + "name STRING, "
				+ "age INT64, " + "salary FLOAT64, " + "is_active BOOL, " + "created_date DATE" + ")", tableName);

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

	/**
	 * Creates a connection with {@code nativeComplexTypes=true} for testing native
	 * JDBC Array/Struct support against real BigQuery.
	 *
	 * @return a JDBC connection with native complex types enabled
	 * @throws SQLException
	 *             if connection fails
	 */
	protected Connection createNativeComplexTypesConnection() throws SQLException {
		String url = String.format("jdbc:bigquery:%s/%s?authType=ADC&nativeComplexTypes=true", TEST_PROJECT_ID,
				TEST_DATASET);
		logger.debug("Connecting with URL (native complex types): {}", url);
		return DriverManager.getConnection(url);
	}

	/**
	 * Creates a test routine (UDF) using the given DDL. Drops any existing routine
	 * with the same name first. Errors are ignored.
	 *
	 * @param routineName
	 *            the fully-qualified routine name
	 * @param sql
	 *            the {@code CREATE OR REPLACE FUNCTION} DDL
	 */
	protected void createTestRoutine(String routineName, String sql) {
		executeIgnoreErrors("DROP FUNCTION IF EXISTS " + routineName);
		executeIgnoreErrors(sql);
	}
}

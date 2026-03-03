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
package vc.tbc.bq.jdbc.integration;

import org.junit.jupiter.api.Test;

import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests verifying that Simba-format URL properties are correctly
 * applied when establishing a real connection to the BigQuery emulator.
 *
 * <p>
 * The base class {@link AbstractBigQueryIntegrationTest} already uses a Simba
 * URL in {@code createTestConnection()}. These tests extend that by exercising
 * specific pass-through connection properties and verifying they take effect.
 *
 * @since 1.0.64
 */
class SimbaUrlConnectionTest extends AbstractBigQueryIntegrationTest {

	// --- Helper ---

	/**
	 * Builds a Simba URL targeting the emulator with additional semicolon-separated
	 * parameters appended. The base URL already includes ProjectId, DefaultDataset,
	 * and UseDestinationTables=true (required for emulator compatibility).
	 */
	private String buildSimbaUrl(String extraParams) {
		String host = bigqueryEmulator.getHost();
		int port = bigqueryEmulator.getMappedPort(9050);
		String base = String.format("jdbc:bigquery://%s:%d;ProjectId=%s;DefaultDataset=%s;UseDestinationTables=true",
				host, port, TEST_PROJECT_ID, TEST_DATASET);
		return extraParams != null && !extraParams.isEmpty() ? base + ";" + extraParams : base;
	}

	private Connection connect(String extraParams) throws SQLException {
		return DriverManager.getConnection(buildSimbaUrl(extraParams));
	}

	// --- Connection validity with various properties ---

	@Test
	void testSimbaUrlWithPageSizeConnects() throws SQLException {
		// pageSize controls result pagination — verify connection is usable
		try (Connection conn = connect("pageSize=100")) {
			assertTrue(conn.isValid(5));
			assertEquals(TEST_PROJECT_ID, conn.getCatalog());
		}
	}

	@Test
	void testSimbaUrlWithTimeoutConnects() throws SQLException {
		// Explicit query timeout — verify connection is usable
		try (Connection conn = connect("Timeout=120")) {
			assertTrue(conn.isValid(5));
		}
	}

	@Test
	void testSimbaUrlWithConnectionTimeoutConnects() throws SQLException {
		// Connection establishment timeout — verify connection is usable
		try (Connection conn = connect("connectionTimeout=60")) {
			assertTrue(conn.isValid(5));
		}
	}

	@Test
	void testSimbaUrlWithRetryCountConnects() throws SQLException {
		// Retry count for transient errors — verify connection is usable
		try (Connection conn = connect("retryCount=2")) {
			assertTrue(conn.isValid(5));
		}
	}

	@Test
	void testSimbaUrlWithMetadataCacheDisabledConnects() throws SQLException {
		// Metadata cache disabled — verify queries still work
		try (Connection conn = connect("metadataCacheEnabled=false")) {
			assertTrue(conn.isValid(5));
			try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT 1 AS n")) {
				assertTrue(rs.next());
				assertEquals(1, rs.getInt("n"));
			}
		}
	}

	@Test
	void testSimbaUrlWithMetadataLazyLoadConnects() throws SQLException {
		// Lazy metadata loading — verify connection and metadata retrieval work
		try (Connection conn = connect("metadataLazyLoad=true")) {
			assertTrue(conn.isValid(5));
			assertNotNull(conn.getMetaData());
		}
	}

	@Test
	void testSimbaUrlWithUseStorageApiFalseConnects() throws SQLException {
		// Storage API disabled — verify queries still work
		try (Connection conn = connect("useStorageApi=false")) {
			assertTrue(conn.isValid(5));
			try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT 42 AS n")) {
				assertTrue(rs.next());
				assertEquals(42, rs.getInt("n"));
			}
		}
	}

	@Test
	void testSimbaUrlWithJobCreationModeOptionalConnects() throws SQLException {
		// JOB_CREATION_OPTIONAL mode — verify queries still work
		try (Connection conn = connect("jobCreationMode=OPTIONAL")) {
			assertTrue(conn.isValid(5));
			try (Statement stmt = conn.createStatement();
					ResultSet rs = stmt.executeQuery("SELECT 'hello' AS greeting")) {
				assertTrue(rs.next());
				assertEquals("hello", rs.getString("greeting"));
			}
		}
	}

	@Test
	void testSimbaUrlWithMaxResultsLimitsRows() throws SQLException {
		// MaxResults limits the number of rows returned
		createTestTable("simba_test_maxresults");
		insertTestData("simba_test_maxresults");
		try (Connection conn = connect("MaxResults=2");
				Statement stmt = conn.createStatement();
				ResultSet rs = stmt.executeQuery("SELECT id FROM simba_test_maxresults ORDER BY id")) {
			int count = 0;
			while (rs.next()) {
				count++;
			}
			// MaxResults=2 caps results at 2 even though 3 rows were inserted
			assertTrue(count <= 2, "Expected at most 2 rows but got " + count);
		} finally {
			executeIgnoreErrors("DROP TABLE IF EXISTS simba_test_maxresults");
		}
	}

	@Test
	void testSimbaUrlWithAllPassThroughPropertiesConnects() throws SQLException {
		// Smoke test: all pass-through properties combined should not prevent
		// connection
		String extras = "pageSize=500;connectionTimeout=45;retryCount=2"
				+ ";metadataCacheTtl=120;metadataCacheEnabled=true;metadataLazyLoad=false"
				+ ";useStorageApi=false;jobCreationMode=OPTIONAL";
		try (Connection conn = connect(extras)) {
			assertTrue(conn.isValid(5));
			assertEquals(TEST_PROJECT_ID, conn.getCatalog());
			assertEquals(TEST_DATASET, conn.getSchema());
		}
	}

	@Test
	void testSimbaUrlPropertiesViaInfoObjectConnect() throws SQLException {
		// Verify that pass-through properties supplied via Properties object also work
		String host = bigqueryEmulator.getHost();
		int port = bigqueryEmulator.getMappedPort(9050);
		String url = String.format("jdbc:bigquery://%s:%d;ProjectId=%s;UseDestinationTables=true", host, port,
				TEST_PROJECT_ID);

		java.util.Properties info = new java.util.Properties();
		info.setProperty("DefaultDataset", TEST_DATASET);
		info.setProperty("pageSize", "200");
		info.setProperty("connectionTimeout", "30");
		info.setProperty("metadataCacheEnabled", "false");

		try (Connection conn = DriverManager.getConnection(url, info)) {
			assertTrue(conn.isValid(5));
			assertEquals(TEST_PROJECT_ID, conn.getCatalog());
		}
	}
}

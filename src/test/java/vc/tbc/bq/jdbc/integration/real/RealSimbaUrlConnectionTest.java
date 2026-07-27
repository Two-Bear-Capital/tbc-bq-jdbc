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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Simba-format URL properties against real BigQuery.
 *
 * <p>
 * Ported from the retired hermetic tier, which relied on a host in a Simba URL
 * selecting a fabricated-credential auth type. That default is gone (a host no
 * longer changes how you authenticate), so the URL carries {@code OAuthType=3}
 * — ADC — and exercises the same parsing path against the real service.
 *
 * <p>
 * These assert plumbing — that a property named the Simba way reaches
 * {@code ConnectionProperties} and takes effect — rather than BigQuery
 * semantics. That is why they survived the #118 migration rather than being
 * deleted.
 *
 * @since 1.0.64
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RealSimbaUrlConnectionTest extends AbstractRealBigQueryIntegrationTest {

	/**
	 * The documented Simba endpoint; the driver validates it but always calls
	 * Google's API.
	 */
	private static final String SIMBA_HOST = "https://www.googleapis.com/bigquery/v2:443";

	private static final String TEST_TABLE = tableName("simba_url");

	@BeforeAll
	void createFixture() throws SQLException {
		createSharedTestTable(TEST_TABLE);
	}

	@AfterAll
	void dropFixture() {
		dropSharedTestTable(TEST_TABLE);
	}

	/** OAuthType=3 is Simba's spelling of Application Default Credentials. */
	private String buildSimbaUrl(String extraParams) {
		String base = String.format("jdbc:bigquery://%s;ProjectId=%s;DefaultDataset=%s;OAuthType=3", SIMBA_HOST,
				TEST_PROJECT_ID, TEST_DATASET);
		return extraParams == null || extraParams.isEmpty() ? base : base + ";" + extraParams;
	}

	private Connection connect(String extraParams) throws SQLException {
		return DriverManager.getConnection(buildSimbaUrl(extraParams));
	}

	private void assertUsable(Connection conn) throws SQLException {
		assertTrue(conn.isValid(30), "Connection should be valid");
		assertEquals(TEST_PROJECT_ID, conn.getCatalog(), "ProjectId should come through the Simba URL");
	}

	@Test
	void testSimbaUrlWithPageSizeConnects() throws SQLException {
		try (Connection conn = connect("pageSize=100")) {
			assertUsable(conn);
		}
	}

	@Test
	void testSimbaUrlWithTimeoutConnects() throws SQLException {
		try (Connection conn = connect("Timeout=120")) {
			assertUsable(conn);
		}
	}

	@Test
	void testSimbaUrlWithConnectionTimeoutConnects() throws SQLException {
		try (Connection conn = connect("connectionTimeout=45")) {
			assertUsable(conn);
		}
	}

	@Test
	void testSimbaUrlWithRetryCountConnects() throws SQLException {
		try (Connection conn = connect("retryCount=2")) {
			assertUsable(conn);
		}
	}

	@Test
	void testSimbaUrlWithMetadataCacheDisabledConnects() throws SQLException {
		try (Connection conn = connect("metadataCacheEnabled=false")) {
			assertUsable(conn);
		}
	}

	@Test
	void testSimbaUrlWithMetadataLazyLoadConnects() throws SQLException {
		try (Connection conn = connect("metadataLazyLoad=true")) {
			assertUsable(conn);
		}
	}

	@Test
	void testSimbaUrlWithUseStorageApiFalseConnects() throws SQLException {
		try (Connection conn = connect("useStorageApi=false")) {
			assertUsable(conn);
		}
	}

	@Test
	void testSimbaUrlWithJobCreationModeOptionalConnects() throws SQLException {
		try (Connection conn = connect("jobCreationMode=OPTIONAL")) {
			assertUsable(conn);
		}
	}

	@Test
	void testSimbaUrlWithDefaultDatasetSetsSchema() throws SQLException {
		try (Connection conn = connect(null)) {
			assertEquals(TEST_DATASET, conn.getSchema(), "DefaultDataset should become the schema");
		}
	}

	@Test
	void testSimbaUrlWithMaxResultsLimitsRows() throws SQLException {
		// The emulator version asserted `count <= 2`, which also passes when the
		// property is ignored and the query happens to return fewer rows. Against
		// real BigQuery the fixture is exactly three rows, so the cap is exact.
		try (Connection conn = connect("MaxResults=2");
				Statement stmt = conn.createStatement();
				ResultSet rs = stmt.executeQuery("SELECT id FROM " + TEST_TABLE + " ORDER BY id")) {
			int count = 0;
			while (rs.next()) {
				count++;
			}
			assertEquals(2, count, "MaxResults=2 should cap a three-row table at two");
		}
	}

	@Test
	void testSimbaUrlWithAllPassThroughPropertiesConnects() throws SQLException {
		String extras = "pageSize=500;connectionTimeout=45;retryCount=2"
				+ ";metadataCacheTtl=120;metadataCacheEnabled=true;metadataLazyLoad=false"
				+ ";useStorageApi=false;jobCreationMode=OPTIONAL";
		try (Connection conn = connect(extras)) {
			assertUsable(conn);
			assertEquals(TEST_DATASET, conn.getSchema());
			assertFalse(conn.isClosed());
		}
	}

	@Test
	void testSimbaUrlPropertiesViaInfoObjectConnect() throws SQLException {
		String url = String.format("jdbc:bigquery://%s;ProjectId=%s;OAuthType=3", SIMBA_HOST, TEST_PROJECT_ID);

		Properties info = new Properties();
		info.setProperty("DefaultDataset", TEST_DATASET);
		info.setProperty("pageSize", "200");
		info.setProperty("connectionTimeout", "30");
		info.setProperty("metadataCacheEnabled", "false");

		try (Connection conn = DriverManager.getConnection(url, info)) {
			assertUsable(conn);
			assertEquals(TEST_DATASET, conn.getSchema(), "DefaultDataset supplied via Properties should apply");
		}
	}
}

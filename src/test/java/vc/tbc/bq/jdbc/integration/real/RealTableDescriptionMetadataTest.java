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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Table descriptions in {@code getTables()}' {@code REMARKS} column (issue
 * #220).
 *
 * <p>
 * {@code getTables()} lists through {@code tables.list}, whose response omits
 * {@code description} entirely — only {@code tables.get} carries it. So
 * {@code Table.getDescription()} was null for every table in every project and
 * {@code REMARKS} was uniformly the empty string, with no error to say why.
 * These tests fail against that behaviour rather than merely tolerating it.
 *
 * @since 3.2.0
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RealTableDescriptionMetadataTest extends AbstractRealBigQueryIntegrationTest {

	private static final Logger logger = LoggerFactory.getLogger(RealTableDescriptionMetadataTest.class);

	private static final String DESCRIBED_TABLE = tableName("desc_table");
	private static final String PLAIN_TABLE = tableName("desc_plain");
	private static final String AWKWARD_TABLE = tableName("desc_awkward");

	private static final String DESCRIPTION = "Daily events, one row per session";

	/**
	 * A description that exercises the literal decoding. BigQuery reports an option
	 * as the SQL that would set it, so quotes and backslashes come back escaped.
	 */
	private static final String AWKWARD_DESCRIPTION = "He said \"hi\", then left\\done";

	private static final String EXPIRES_SOON = "OPTIONS(expiration_timestamp = "
			+ "TIMESTAMP_ADD(CURRENT_TIMESTAMP(), INTERVAL 2 HOUR)";

	@BeforeAll
	void createObjects() throws SQLException {
		try (Connection setup = createTestConnection(); Statement stmt = setup.createStatement()) {
			stmt.execute("CREATE OR REPLACE TABLE " + qualify(DESCRIBED_TABLE) + " " + EXPIRES_SOON
					+ ", description = '" + DESCRIPTION + "') AS SELECT 1 AS x");
			stmt.execute("CREATE OR REPLACE TABLE " + qualify(PLAIN_TABLE) + " " + EXPIRES_SOON + ") AS SELECT 1 AS x");
			// Single-quoted in SQL, so the embedded double quotes and backslash reach
			// BigQuery verbatim and come back escaped in option_value.
			stmt.execute("CREATE OR REPLACE TABLE " + qualify(AWKWARD_TABLE) + " " + EXPIRES_SOON
					+ ", description = 'He said \"hi\", then left\\\\done') AS SELECT 1 AS x");
		}
	}

	@AfterAll
	void dropObjects() {
		try (Connection cleanup = createTestConnection(); Statement stmt = cleanup.createStatement()) {
			stmt.execute("DROP TABLE IF EXISTS " + qualify(DESCRIBED_TABLE));
			stmt.execute("DROP TABLE IF EXISTS " + qualify(PLAIN_TABLE));
			stmt.execute("DROP TABLE IF EXISTS " + qualify(AWKWARD_TABLE));
		} catch (SQLException e) {
			logger.debug("Ignoring error dropping description fixtures for {}: {}", RUN_ID, e.getMessage());
		}
	}

	private static String qualify(String name) {
		return TEST_DATASET + "." + name;
	}

	/** Maps table name to REMARKS across the fixture set, on a given connection. */
	private Map<String, String> tableRemarks(Connection source) throws SQLException {
		DatabaseMetaData metaData = source.getMetaData();
		Map<String, String> remarks = new HashMap<>();
		try (ResultSet rs = metaData.getTables(TEST_PROJECT_ID, TEST_DATASET, "desc%_" + RUN_ID, null)) {
			while (rs.next()) {
				remarks.put(rs.getString("TABLE_NAME"), rs.getString("REMARKS"));
			}
		}
		return remarks;
	}

	@Test
	void tableDescriptionsReachRemarks() throws SQLException {
		String remarks = tableRemarks(connection).get(DESCRIBED_TABLE);

		assertNotNull(remarks, "the described table should have been listed");
		logger.info("getTables() REMARKS for {}: {}", DESCRIBED_TABLE, remarks);
		assertEquals(DESCRIPTION, remarks);
	}

	@Test
	void descriptionsAreUnquotedAndUnescaped() throws SQLException {
		// TABLE_OPTIONS reports option_value as a SQL literal: this description comes
		// back as "He said \"hi\", then left\\done", quotes and all. Handing that
		// straight to a caller shows them the escaping.
		String remarks = tableRemarks(connection).get(AWKWARD_TABLE);

		assertNotNull(remarks, "the awkwardly described table should have been listed");
		assertEquals(AWKWARD_DESCRIPTION, remarks);
	}

	@Test
	void tablesWithoutADescriptionStillReportEmptyRemarks() throws SQLException {
		assertEquals("", tableRemarks(connection).get(PLAIN_TABLE),
				"an absent description is the empty string, not null and not a stray value");
	}

	@Test
	void descriptionsCanBeTurnedOff() throws SQLException {
		// The opt-out for projects with enough datasets that one INFORMATION_SCHEMA
		// query each is felt. It must actually stop the read, which shows up as the
		// description no longer arriving.
		String url = String.format(
				"jdbc:bigquery:%s/%s?authType=ADC&metadataIncludeDescriptions=false" + "&metadataCacheEnabled=false",
				TEST_PROJECT_ID, TEST_DATASET);
		try (Connection plain = DriverManager.getConnection(url)) {
			assertEquals("", tableRemarks(plain).get(DESCRIBED_TABLE),
					"with the property off, REMARKS goes back to empty");
		}
	}

	@Test
	void filteringToOneTableStillCarriesItsDescription() throws SQLException {
		// The description read runs per dataset, after the name and type filters have
		// been applied to the listing. A narrowed call must still be annotated.
		DatabaseMetaData metaData = connection.getMetaData();

		try (ResultSet rs = metaData.getTables(TEST_PROJECT_ID, TEST_DATASET, DESCRIBED_TABLE, new String[]{"TABLE"})) {
			assertTrue(rs.next(), "the described table should have been listed");
			assertEquals("TABLE", rs.getString("TABLE_TYPE"));
			assertEquals(DESCRIPTION, rs.getString("REMARKS"));
		}
	}
}

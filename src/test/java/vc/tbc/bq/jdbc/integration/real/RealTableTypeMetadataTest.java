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
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Table types in {@code getTables()} against real BigQuery (#187).
 *
 * <p>
 * Snapshots and clones cannot be faked: they are created by dedicated DDL and
 * are the whole point of the issue. The fixtures here are real ones, and the
 * test asserts what the driver reports for each.
 *
 * <p>
 * <b>Enabling:</b> creating a snapshot needs
 * {@code bigquery.tables.createSnapshot} and {@code deleteSnapshot}, which
 * {@code roles/bigquery.dataEditor} does not carry — so this class is gated on
 * {@code BQ_TEST_SNAPSHOT_FIXTURES} rather than failing CI until Terraform has
 * upgraded the CI service account to {@code dataOwner} on the test dataset. A
 * maintainer running the suite under their own credentials already has it. See
 * #252.
 *
 * @since 4.0.0
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "BQ_TEST_SNAPSHOT_FIXTURES", matches = ".+", disabledReason = "BQ_TEST_SNAPSHOT_FIXTURES is not set — the caller may not be able to create table snapshots")
class RealTableTypeMetadataTest extends AbstractRealBigQueryIntegrationTest {

	private static final String BASE = tableName("types_base");
	private static final String SNAPSHOT = tableName("types_snapshot");
	private static final String CLONE = tableName("types_clone");
	private static final String VIEW = tableName("types_view");

	/** Two hours, matching the rest of the suite's fixtures. */
	private static final String EXPIRES = "OPTIONS(expiration_timestamp = "
			+ "TIMESTAMP_ADD(CURRENT_TIMESTAMP(), INTERVAL 2 HOUR))";

	@BeforeAll
	void createFixtures() throws SQLException {
		try (Connection setup = createTestConnection(); Statement stmt = setup.createStatement()) {
			stmt.execute("CREATE OR REPLACE TABLE " + BASE + " " + EXPIRES + " AS SELECT 1 AS id");
			stmt.execute("CREATE SNAPSHOT TABLE " + SNAPSHOT + " CLONE " + BASE + " " + EXPIRES);
			stmt.execute("CREATE TABLE " + CLONE + " CLONE " + BASE + " " + EXPIRES);
			stmt.execute("CREATE OR REPLACE VIEW " + VIEW + " AS SELECT 1 AS id");
		}
	}

	@AfterAll
	void dropFixtures() {
		try (Connection cleanup = createTestConnection(); Statement stmt = cleanup.createStatement()) {
			stmt.execute("DROP VIEW IF EXISTS " + VIEW);
			stmt.execute("DROP TABLE IF EXISTS " + CLONE);
			stmt.execute("DROP SNAPSHOT TABLE IF EXISTS " + SNAPSHOT);
			stmt.execute("DROP TABLE IF EXISTS " + BASE);
		} catch (SQLException e) {
			// Fixtures expire in two hours regardless.
		}
	}

	/** Table name to reported TABLE_TYPE, for this class's fixtures only. */
	private Map<String, String> reportedTypes(Connection conn) throws SQLException {
		Map<String, String> types = new HashMap<>();
		try (ResultSet rs = conn.getMetaData().getTables(null, TEST_DATASET, "types\\_%" + "_" + RUN_ID, null)) {
			while (rs.next()) {
				types.put(rs.getString("TABLE_NAME"), rs.getString("TABLE_TYPE"));
			}
		}
		return types;
	}

	@Test
	void testEachKindOfTableReportsItsOwnType() throws SQLException {
		// When: Listing the fixtures
		Map<String, String> types = reportedTypes(connection);

		// Then: Each should be distinguishable. Before #187 all four non-view rows
		// were reported as TABLE, so a caller could not tell that a snapshot is
		// read-only or that a clone shares storage with its base
		assertEquals("TABLE", types.get(BASE), types.toString());
		assertEquals("SNAPSHOT", types.get(SNAPSHOT), types.toString());
		assertEquals("CLONE", types.get(CLONE), types.toString());
		assertEquals("VIEW", types.get(VIEW), types.toString());
	}

	@Test
	void testGetTableTypesAdvertisesTheNewTypes() throws SQLException {
		// Then: A type nothing advertises cannot be filtered on
		try (ResultSet rs = connection.getMetaData().getTableTypes()) {
			List<String> types = new ArrayList<>();
			while (rs.next()) {
				types.add(rs.getString(1));
			}
			assertTrue(types.contains("SNAPSHOT"), types.toString());
			assertTrue(types.contains("CLONE"), types.toString());
			assertTrue(types.contains("EXTERNAL"), types.toString());
			assertTrue(types.contains("TABLE"), types.toString());
		}
	}

	@Test
	void testFilteringByTypeExcludesTheOthers() throws SQLException {
		// When: Asking only for plain tables, as a tool listing user tables does
		try (ResultSet rs = connection.getMetaData().getTables(null, TEST_DATASET, "types\\_%" + "_" + RUN_ID,
				new String[]{"TABLE"})) {
			List<String> names = new ArrayList<>();
			while (rs.next()) {
				names.add(rs.getString("TABLE_NAME"));
			}

			// Then: Only the base table. This is the behaviour change — a snapshot
			// and a clone used to answer to "TABLE"
			assertTrue(names.contains(BASE), names.toString());
			assertFalse(names.contains(SNAPSHOT), names.toString());
			assertFalse(names.contains(CLONE), names.toString());
		}
	}

	@Test
	void testFilteringByARefinedTypeFindsIt() throws SQLException {
		// When: Asking specifically for clones
		try (ResultSet rs = connection.getMetaData().getTables(null, TEST_DATASET, "types\\_%" + "_" + RUN_ID,
				new String[]{"CLONE"})) {
			List<String> names = new ArrayList<>();
			while (rs.next()) {
				names.add(rs.getString("TABLE_NAME"));
			}

			// Then: The clone, and only the clone. This is the other half of the
			// ordering bug — with the type filter applied before the refinement,
			// nothing was labelled CLONE yet and this matched nothing at all
			assertEquals(List.of(CLONE), names);
		}
	}

	@Test
	void testASnapshotIsIdentifiedWithoutTheDescriptionRead() throws SQLException {
		// Given: A connection that has turned off the INFORMATION_SCHEMA read
		String url = String.format(
				"jdbc:bigquery:%s/%s?authType=ADC&maxBillingBytes=1073741824&metadataIncludeDescriptions=false",
				TEST_PROJECT_ID, TEST_DATASET);

		try (Connection conn = DriverManager.getConnection(url)) {
			Map<String, String> types = reportedTypes(conn);

			// Then: A snapshot is still identified — it comes from the table
			// definition the listing already carries, so it costs nothing
			assertEquals("SNAPSHOT", types.get(SNAPSHOT), types.toString());
			assertEquals("VIEW", types.get(VIEW), types.toString());

			// But a clone is not. BigQuery's own tables.list reports a clone as an
			// ordinary table; only INFORMATION_SCHEMA distinguishes it, and this
			// connection asked for that read to be skipped. Documented in
			// COMPATIBILITY.md rather than left as a surprise
			assertEquals("TABLE", types.get(CLONE), types.toString());
		}
	}
}

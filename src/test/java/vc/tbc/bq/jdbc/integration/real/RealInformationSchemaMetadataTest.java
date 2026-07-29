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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code INFORMATION_SCHEMA} browsing against real BigQuery (#189).
 *
 * <p>
 * The point of this tier here is one assertion the unit tests cannot make:
 * <b>that a name the driver advertises can actually be queried.</b> The view
 * lists are static, so a wrong entry — a view at the wrong scope, a name
 * misspelled, a quoting form BigQuery rejects — produces a perfectly
 * well-formed {@code getTables()} row that fails the moment anyone clicks it.
 * Only the real service can tell the two apart.
 *
 * @since 4.0.0
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RealInformationSchemaMetadataTest extends AbstractRealBigQueryIntegrationTest {

	private static final String INFORMATION_SCHEMA = "INFORMATION_SCHEMA";

	private static List<String> columnValues(ResultSet rs, int column) throws SQLException {
		List<String> values = new ArrayList<>();
		while (rs.next()) {
			values.add(rs.getString(column));
		}
		return values;
	}

	@Test
	void testGetSchemasIncludesInformationSchema() throws SQLException {
		// When: Listing schemas
		try (ResultSet rs = connection.getMetaData().getSchemas()) {
			List<String> schemas = columnValues(rs, 1);

			// Then: The synthetic schema should sit alongside the real datasets
			assertTrue(schemas.contains(INFORMATION_SCHEMA), "Expected INFORMATION_SCHEMA in " + schemas);
			assertTrue(schemas.contains(TEST_DATASET), "Real datasets must still be listed: " + schemas);
		}
	}

	@Test
	void testGetTableTypesOffersSystemTable() throws SQLException {
		// When: Listing table types
		try (ResultSet rs = connection.getMetaData().getTableTypes()) {
			List<String> types = columnValues(rs, 1);

			// Then: The type these are reported under must be advertised, or a tool
			// cannot filter on it
			assertTrue(types.contains("SYSTEM TABLE"), types.toString());
			assertTrue(types.contains("TABLE"), types.toString());
		}
	}

	@Test
	void testProjectScopedViewsAppearUnderTheSyntheticSchema() throws SQLException {
		// When: Listing the synthetic schema's tables
		try (ResultSet rs = connection.getMetaData().getTables(null, INFORMATION_SCHEMA, null, null)) {
			List<String> names = new ArrayList<>();
			while (rs.next()) {
				assertEquals(INFORMATION_SCHEMA, rs.getString("TABLE_SCHEM"));
				assertEquals("SYSTEM TABLE", rs.getString("TABLE_TYPE"));
				names.add(rs.getString("TABLE_NAME"));
			}

			// Then: The project-scoped views should be there, under bare names
			assertTrue(names.contains("SCHEMATA"), names.toString());
			assertTrue(names.contains("JOBS"), names.toString());
			// And nothing dataset-scoped, which does not exist at this scope
			assertFalse(names.contains("TABLES"), "TABLES is dataset-scoped: " + names);
		}
	}

	@Test
	void testDatasetScopedViewsAppearInsideTheDataset() throws SQLException {
		// When: Listing the test dataset's tables
		try (ResultSet rs = connection.getMetaData().getTables(null, TEST_DATASET, "INFORMATION_SCHEMA.%", null)) {
			List<String> names = columnValues(rs, 3);

			// Then: The dataset-scoped views should appear under compound names
			assertTrue(names.contains("INFORMATION_SCHEMA.TABLES"), names.toString());
			assertTrue(names.contains("INFORMATION_SCHEMA.COLUMNS"), names.toString());
			// And nothing project-scoped, which does not exist at this scope
			assertFalse(names.contains("INFORMATION_SCHEMA.SCHEMATA"), "SCHEMATA is project-scoped: " + names);
		}
	}

	@Test
	void testAdvertisedDatasetViewNamesAreActuallyQueryable() throws SQLException {
		// Given: Every dataset-scoped name the driver reports
		List<String> names;
		try (ResultSet rs = connection.getMetaData().getTables(null, TEST_DATASET, "INFORMATION_SCHEMA.%", null)) {
			names = columnValues(rs, 3);
		}
		assertFalse(names.isEmpty(), "Nothing to check");

		// Then: Each must resolve against BigQuery. A view listed at the wrong
		// scope, or a name BigQuery spells differently, is indistinguishable from a
		// correct one until it is asked for
		for (String name : names) {
			String sql = String.format("SELECT * FROM `%s`.`%s`.`%s` LIMIT 0", TEST_PROJECT_ID, TEST_DATASET, name);
			try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
				assertFalse(rs.next(), "LIMIT 0 should return no rows");
			} catch (SQLException e) {
				throw new AssertionError("Advertised view is not queryable: " + name + " — " + e.getMessage(), e);
			}
		}
	}

	@Test
	void testGetColumnsDescribesADatasetScopedView() throws SQLException {
		// When: Asking for the columns of a view the driver advertises
		try (ResultSet rs = connection.getMetaData().getColumns(null, TEST_DATASET, "INFORMATION_SCHEMA.TABLES",
				null)) {
			List<String> columns = columnValues(rs, 4);

			// Then: They should be the view's real columns, read from the service
			// rather than a hard-coded list that would go stale
			assertTrue(columns.contains("table_name"), columns.toString());
			assertTrue(columns.contains("table_schema"), columns.toString());
			assertTrue(columns.contains("table_type"), columns.toString());
		}
	}

	@Test
	void testGetColumnsDescribesAProjectScopedView() throws SQLException {
		// When: Asking for a project-scoped view's columns
		try (ResultSet rs = connection.getMetaData().getColumns(null, INFORMATION_SCHEMA, "SCHEMATA", null)) {
			List<String> columns = columnValues(rs, 4);

			// Then: Likewise resolved from the service
			assertTrue(columns.contains("schema_name"), columns.toString());
			assertTrue(columns.contains("catalog_name"), columns.toString());
		}
	}

	@Test
	void testTypeFilterExcludesThem() throws SQLException {
		// When: Asking only for user objects, the way a tool listing tables does
		try (ResultSet rs = connection.getMetaData().getTables(null, TEST_DATASET, null,
				new String[]{"TABLE", "VIEW"})) {
			List<String> names = columnValues(rs, 3);

			// Then: None of these should be included — that is what the distinct
			// table type is for
			assertTrue(names.stream().noneMatch(n -> n.startsWith("INFORMATION_SCHEMA.")),
					"SYSTEM TABLE entries leaked into a TABLE/VIEW listing: " + names);
		}
	}

	@Test
	void testOptOutRemovesThemEntirely() throws SQLException {
		// Given: A second connection to the same project that opts out. The
		// metadata cache is shared statically between connections, so this also
		// covers the cache being keyed by the setting rather than by the project
		// alone — without that, this connection is served the other one's rows
		String url = String.format("jdbc:bigquery:%s/%s?authType=ADC&includeInformationSchema=false", TEST_PROJECT_ID,
				TEST_DATASET);

		try (Connection optedOut = DriverManager.getConnection(url)) {
			DatabaseMetaData metaData = optedOut.getMetaData();

			try (ResultSet rs = metaData.getSchemas()) {
				assertFalse(columnValues(rs, 1).contains(INFORMATION_SCHEMA), "Opted-out connection still sees it");
			}
			try (ResultSet rs = metaData.getTableTypes()) {
				assertFalse(columnValues(rs, 1).contains("SYSTEM TABLE"), "A type nothing is reported under");
			}
			try (ResultSet rs = metaData.getTables(null, TEST_DATASET, "INFORMATION_SCHEMA.%", null)) {
				assertTrue(columnValues(rs, 3).isEmpty(), "Opted-out connection still lists dataset views");
			}
		}

		// Then: This connection, which did not opt out, must be unaffected
		try (ResultSet rs = connection.getMetaData().getSchemas()) {
			assertTrue(columnValues(rs, 1).contains(INFORMATION_SCHEMA),
					"Opting out on one connection changed another");
		}
	}
}

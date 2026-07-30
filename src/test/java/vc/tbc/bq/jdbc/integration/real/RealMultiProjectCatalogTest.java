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
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Catalog discovery and switching against real BigQuery (#190).
 *
 * <p>
 * The project used as the "other" catalog is {@code bigquery-public-data},
 * which every credential can read and which is not this suite's project — so
 * switching to it and finding different datasets proves the switch took effect,
 * rather than proving a name was echoed back.
 *
 * @since 4.0.0
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RealMultiProjectCatalogTest extends AbstractRealBigQueryIntegrationTest {

	/** A project every credential can read, and not the one under test. */
	private static final String PUBLIC_DATA = "bigquery-public-data";

	private static List<String> columnValues(ResultSet rs, int column) throws SQLException {
		List<String> values = new ArrayList<>();
		while (rs.next()) {
			values.add(rs.getString(column));
		}
		return values;
	}

	private Connection openWith(String extraParams) throws SQLException {
		return DriverManager.getConnection(String.format("jdbc:bigquery:%s/%s?authType=ADC%s%s", TEST_PROJECT_ID,
				TEST_DATASET, TEST_CONNECTION_DEFAULTS, extraParams));
	}

	@Test
	void testGetCatalogsReportsOnlyTheConnectionProjectByDefault() throws SQLException {
		// Then: Nothing is discovered automatically, so an unconfigured connection
		// looks exactly as it did before
		try (ResultSet rs = connection.getMetaData().getCatalogs()) {
			assertEquals(List.of(TEST_PROJECT_ID), columnValues(rs, 1));
		}
	}

	@Test
	void testConfiguredProjectsAreDiscoverable() throws SQLException {
		// When: A second project is named
		try (Connection conn = openWith("&additionalProjects=" + PUBLIC_DATA)) {
			try (ResultSet rs = conn.getMetaData().getCatalogs()) {
				List<String> catalogs = columnValues(rs, 1);

				// Then: A tool can now see it exists, which is the half of #190 that
				// no amount of passing catalog arguments could solve
				assertTrue(catalogs.contains(PUBLIC_DATA), catalogs.toString());
				assertTrue(catalogs.contains(TEST_PROJECT_ID), catalogs.toString());
				assertEquals(catalogs.stream().sorted().toList(), catalogs,
						"getCatalogs() must be ordered by TABLE_CAT");
			}
		}
	}

	@Test
	void testSetCatalogChangesWhichProjectMetadataDefaultsTo() throws SQLException {
		try (Connection conn = openWith("&additionalProjects=" + PUBLIC_DATA)) {
			// Given: The connection's own project
			assertEquals(TEST_PROJECT_ID, conn.getCatalog());
			List<String> before;
			try (ResultSet rs = conn.getMetaData().getSchemas()) {
				before = columnValues(rs, 1);
			}
			assertTrue(before.contains(TEST_DATASET), before.toString());

			// When: Switching catalogs
			conn.setCatalog(PUBLIC_DATA);

			// Then: getSchemas() with no catalog argument should now answer for the
			// other project. Comparing the dataset lists is what proves the switch
			// took effect rather than the name merely being echoed back
			assertEquals(PUBLIC_DATA, conn.getCatalog());
			try (ResultSet rs = conn.getMetaData().getSchemas()) {
				List<String> after = columnValues(rs, 1);
				assertFalse(after.contains(TEST_DATASET), "Still listing the old project's datasets: " + after);
				assertFalse(after.isEmpty(), "The public data project has datasets");
			}
		}
	}

	@Test
	void testSetCatalogNullRestoresTheConnectionProject() throws SQLException {
		try (Connection conn = openWith("")) {
			conn.setCatalog(PUBLIC_DATA);
			assertEquals(PUBLIC_DATA, conn.getCatalog());

			// When: Asked for "no catalog"
			conn.setCatalog(null);

			// Then: Back to the project the connection was opened against
			assertEquals(TEST_PROJECT_ID, conn.getCatalog());
			try (ResultSet rs = conn.getMetaData().getSchemas()) {
				assertTrue(columnValues(rs, 1).contains(TEST_DATASET));
			}
		}
	}

	@Test
	void testSetCatalogRejectsAnUnusableProjectId() throws SQLException {
		// Then: Rejected rather than ignored. Ignoring is what this used to do, and
		// a caller had no way to tell a switch that did not happen from one that did
		try (Connection conn = openWith("")) {
			assertThrows(SQLException.class, () -> conn.setCatalog("not a project id!"));
			assertEquals(TEST_PROJECT_ID, conn.getCatalog(), "A rejected switch must not have moved anything");
		}
	}

	@Test
	void testAnExplicitCatalogArgumentStillWins() throws SQLException {
		try (Connection conn = openWith("")) {
			// Given: The connection switched away from its own project
			conn.setCatalog(PUBLIC_DATA);

			// When: A caller names the original project explicitly
			try (ResultSet rs = conn.getMetaData().getSchemas(TEST_PROJECT_ID, null)) {
				// Then: The argument wins over the current catalog
				assertTrue(columnValues(rs, 1).contains(TEST_DATASET));
			}
		}
	}

	@Test
	void testSwitchingCatalogsDoesNotServeTheOtherProjectFromCache() throws SQLException {
		// Given: One connection that has already cached its own project's schemas
		try (Connection conn = openWith("")) {
			try (ResultSet rs = conn.getMetaData().getSchemas()) {
				assertTrue(columnValues(rs, 1).contains(TEST_DATASET));
			}

			// When: The same connection switches and asks again. The metadata cache
			// is shared statically and keyed by the call arguments, which are
			// identical here — only the current catalog differs
			conn.setCatalog(PUBLIC_DATA);

			// Then: It must not be handed the first project's answer
			try (ResultSet rs = conn.getMetaData().getSchemas()) {
				assertFalse(columnValues(rs, 1).contains(TEST_DATASET), "Cached rows leaked across a catalog switch");
			}
		}
	}
}

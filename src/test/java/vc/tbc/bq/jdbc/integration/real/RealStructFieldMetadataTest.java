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
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code STRUCT} subfields in {@code getColumns()} against real BigQuery
 * (#186).
 *
 * <p>
 * The assertion that matters most is not that the paths appear — it is that
 * every path reported <b>can actually be selected</b>. BigQuery's
 * {@code COLUMN_FIELD_PATHS} lists paths below an {@code ARRAY} too, and those
 * cannot: {@code SELECT items.n} fails and needs {@code UNNEST}. Reporting one
 * would advertise a column no query can name.
 *
 * @since 4.0.0
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RealStructFieldMetadataTest extends AbstractRealBigQueryIntegrationTest {

	private static final String NESTED = tableName("struct_fields");

	@BeforeAll
	void createFixture() throws SQLException {
		try (Connection setup = createTestConnection(); Statement stmt = setup.createStatement()) {
			stmt.execute("CREATE OR REPLACE TABLE " + NESTED
					+ " OPTIONS(expiration_timestamp = TIMESTAMP_ADD(CURRENT_TIMESTAMP(), INTERVAL 2 HOUR)) AS "
					+ "SELECT 1 AS id, " + "STRUCT('a' AS name, STRUCT(7 AS zip, 'NY' AS state) AS addr) AS person, "
					+ "[STRUCT(1 AS n)] AS items, 'plain' AS label");
		}
	}

	@AfterAll
	void dropFixture() {
		dropSharedTestTable(NESTED);
	}

	private Connection openWith(boolean structFields) throws SQLException {
		return DriverManager.getConnection(String.format("jdbc:bigquery:%s/%s?authType=ADC&includeStructFields=%s%s",
				TEST_PROJECT_ID, TEST_DATASET, structFields, TEST_CONNECTION_DEFAULTS));
	}

	private List<String> columnNames(Connection conn) throws SQLException {
		List<String> names = new ArrayList<>();
		try (ResultSet rs = conn.getMetaData().getColumns(null, TEST_DATASET, NESTED, null)) {
			while (rs.next()) {
				names.add(rs.getString("COLUMN_NAME"));
			}
		}
		return names;
	}

	@Test
	void testStructFieldsAreNotReportedByDefault() throws SQLException {
		// Then: An unconfigured connection sees exactly the top-level columns, so
		// nothing that builds a column list from getColumns() changes behaviour
		try (Connection conn = openWith(false)) {
			assertEquals(List.of("id", "person", "items", "label"), columnNames(conn));
		}
	}

	@Test
	void testStructFieldsAppearAfterTheirColumn() throws SQLException {
		// When: The property asks for them
		try (Connection conn = openWith(true)) {
			List<String> names = columnNames(conn);

			// Then: Each field follows the column it belongs to, so a tree built in
			// order nests correctly
			assertEquals(List.of("id", "person", "person.addr", "person.addr.state", "person.addr.zip", "person.name",
					"items", "label"), names);
		}
	}

	@Test
	void testPathsBelowAnArrayAreExcluded() throws SQLException {
		// Then: COLUMN_FIELD_PATHS lists items.n, but SELECT items.n fails — it
		// needs UNNEST. Advertising it would name a column no query can use
		try (Connection conn = openWith(true)) {
			assertFalse(columnNames(conn).contains("items.n"), "An ARRAY descendant was reported");
			assertTrue(columnNames(conn).contains("items"), "The array column itself must still be reported");
		}
	}

	@Test
	void testEveryReportedFieldPathCanActuallyBeSelected() throws SQLException {
		// Given: Every name the driver reports for this table
		try (Connection conn = openWith(true)) {
			List<String> names = columnNames(conn);

			// Then: Each must be a usable reference. This is the check that
			// separates a correct path list from a plausible one
			for (String name : names) {
				String sql = "SELECT " + name + " FROM " + NESTED + " LIMIT 0";
				try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
					assertFalse(rs.next(), "LIMIT 0 should return no rows");
				} catch (SQLException e) {
					throw new AssertionError("Reported column is not selectable: " + name + " — " + e.getMessage(), e);
				}
			}
		}
	}

	@Test
	void testFieldRowsCarryTheirOwnType() throws SQLException {
		// When: Reading the metadata of a leaf and of an intermediate struct
		try (Connection conn = openWith(true);
				ResultSet rs = conn.getMetaData().getColumns(null, TEST_DATASET, NESTED, "person.%")) {
			List<String> described = new ArrayList<>();
			while (rs.next()) {
				described.add(rs.getString("COLUMN_NAME") + "=" + rs.getString("TYPE_NAME"));
			}

			// Then: A leaf reports its scalar type, not the enclosing struct's, and
			// an intermediate reports the struct it is
			assertTrue(described.contains("person.name=STRING"), described.toString());
			assertTrue(described.contains("person.addr.zip=INT64"), described.toString());
			assertTrue(described.stream().anyMatch(d -> d.startsWith("person.addr=STRUCT<")), described.toString());
		}
	}

	@Test
	void testOrdinalPositionsStayContiguous() throws SQLException {
		// Then: Splicing rows in must renumber, or ORDINAL_POSITION no longer means
		// "index of column in table"
		try (Connection conn = openWith(true);
				ResultSet rs = conn.getMetaData().getColumns(null, TEST_DATASET, NESTED, null)) {
			int expected = 1;
			while (rs.next()) {
				assertEquals(expected++, rs.getInt("ORDINAL_POSITION"), "at " + rs.getString("COLUMN_NAME"));
			}
			assertTrue(expected > 1, "The fixture should have columns");
		}
	}
}

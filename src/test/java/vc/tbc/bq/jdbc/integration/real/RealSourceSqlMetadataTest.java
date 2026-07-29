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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the source SQL surfaced through {@code REMARKS} for views,
 * materialized views and routines.
 *
 * <p>
 * The definition is not on the {@code Table} objects {@code listTables} returns
 * — that response carries {@code view.useLegacySql} and nothing else — so this
 * exercises the {@code INFORMATION_SCHEMA} read that supplies it, and the rule
 * that a description the author wrote wins over it.
 *
 * @since 3.1.0
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RealSourceSqlMetadataTest extends AbstractRealBigQueryIntegrationTest {

	private static final Logger logger = LoggerFactory.getLogger(RealSourceSqlMetadataTest.class);

	private static final String BASE_TABLE = tableName("src_base");
	private static final String VIEW = tableName("src_view");
	private static final String DESCRIBED_VIEW = tableName("src_described_view");
	private static final String MATERIALIZED_VIEW = tableName("src_mv");
	private static final String PROCEDURE = tableName("src_proc");
	private static final String FUNCTION = tableName("src_udf");

	private static final String VIEW_BODY = "SELECT 1 AS x, 'a' AS y";
	private static final String DESCRIPTION = "A description the author wrote";

	private static final String EXPIRES_SOON = "OPTIONS(expiration_timestamp = "
			+ "TIMESTAMP_ADD(CURRENT_TIMESTAMP(), INTERVAL 2 HOUR))";

	@BeforeAll
	void createObjects() throws SQLException {
		try (Connection setup = createTestConnection(); Statement stmt = setup.createStatement()) {
			stmt.execute("CREATE OR REPLACE TABLE " + qualify(BASE_TABLE) + " " + EXPIRES_SOON + " AS SELECT 1 AS x");
			stmt.execute("CREATE OR REPLACE VIEW " + qualify(VIEW) + " AS " + VIEW_BODY);
			stmt.execute("CREATE OR REPLACE VIEW " + qualify(DESCRIBED_VIEW) + " OPTIONS(description = '" + DESCRIPTION
					+ "') AS " + VIEW_BODY);
			stmt.execute("CREATE MATERIALIZED VIEW IF NOT EXISTS " + qualify(MATERIALIZED_VIEW)
					+ " AS SELECT x, COUNT(*) AS n FROM " + qualify(BASE_TABLE) + " GROUP BY x");
			stmt.execute(
					"CREATE OR REPLACE PROCEDURE " + qualify(PROCEDURE) + "(IN a INT64) BEGIN SELECT a AS echoed; END");
			stmt.execute("CREATE OR REPLACE FUNCTION " + qualify(FUNCTION) + "(x INT64) RETURNS INT64 AS (x * 7)");
		}
	}

	@AfterAll
	void dropObjects() {
		try (Connection cleanup = createTestConnection(); Statement stmt = cleanup.createStatement()) {
			stmt.execute("DROP MATERIALIZED VIEW IF EXISTS " + qualify(MATERIALIZED_VIEW));
			stmt.execute("DROP VIEW IF EXISTS " + qualify(VIEW));
			stmt.execute("DROP VIEW IF EXISTS " + qualify(DESCRIBED_VIEW));
			stmt.execute("DROP PROCEDURE IF EXISTS " + qualify(PROCEDURE));
			stmt.execute("DROP FUNCTION IF EXISTS " + qualify(FUNCTION));
			stmt.execute("DROP TABLE IF EXISTS " + qualify(BASE_TABLE));
		} catch (SQLException e) {
			logger.debug("Ignoring error dropping source-SQL fixtures for {}: {}", RUN_ID, e.getMessage());
		}
	}

	private static String qualify(String name) {
		return TEST_DATASET + "." + name;
	}

	/** Maps table name to REMARKS across the whole fixture set. */
	private Map<String, String> tableRemarks() throws SQLException {
		DatabaseMetaData metaData = connection.getMetaData();
		Map<String, String> remarks = new HashMap<>();
		try (ResultSet rs = metaData.getTables(TEST_PROJECT_ID, TEST_DATASET, "src%_" + RUN_ID, null)) {
			while (rs.next()) {
				remarks.put(rs.getString("TABLE_NAME"), rs.getString("REMARKS"));
			}
		}
		return remarks;
	}

	@Test
	void viewRemarksCarryTheDefiningSql() throws SQLException {
		Map<String, String> remarks = tableRemarks();

		String viewSql = remarks.get(VIEW);
		assertNotNull(viewSql, "the view should have been listed; found " + remarks.keySet());
		logger.info("getTables() REMARKS for {}: {}", VIEW, viewSql);
		assertTrue(viewSql.contains(VIEW_BODY), "expected the view body in REMARKS, got: " + viewSql);
		assertTrue(viewSql.startsWith("CREATE VIEW"), "expected the CREATE statement, got: " + viewSql);
	}

	/**
	 * {@code MATERIALIZED_VIEWS} has no definition column, which is why the
	 * definition is read from {@code TABLES.ddl} rather than
	 * {@code VIEWS.view_definition} — this is the case that would have been missed.
	 */
	@Test
	void materializedViewRemarksCarryTheDefiningSql() throws SQLException {
		String sql = tableRemarks().get(MATERIALIZED_VIEW);

		assertNotNull(sql, "the materialized view should have been listed");
		assertTrue(sql.startsWith("CREATE MATERIALIZED VIEW"), "expected the CREATE statement, got: " + sql);
		assertTrue(sql.contains("GROUP BY x"), "expected the materialized view body, got: " + sql);
	}

	/**
	 * A described view reports the description, not the DDL.
	 *
	 * <p>
	 * The precedence was written when this landed but could not fire: {@code
	 * tables.list} omits {@code description}, so every table looked undescribed and
	 * views uniformly got their DDL. Now that descriptions are read from {@code
	 * INFORMATION_SCHEMA}, the rule takes effect — what the author wrote for this
	 * column beats what the driver can reconstruct.
	 */
	@Test
	void aDescribedViewPrefersItsDescriptionOverItsDdl() throws SQLException {
		String remarks = tableRemarks().get(DESCRIBED_VIEW);

		assertNotNull(remarks, "the described view should have been listed");
		assertEquals(DESCRIPTION, remarks);
	}

	/**
	 * The control: a table with no description still reports empty REMARKS, so the
	 * description read has not started inventing values for rows that have none.
	 */
	@Test
	void plainTablesKeepEmptyRemarks() throws SQLException {
		assertEquals("", tableRemarks().get(BASE_TABLE), "a table with no description still reports empty REMARKS");
	}

	@Test
	void procedureRemarksCarryTheBody() throws SQLException {
		DatabaseMetaData metaData = connection.getMetaData();

		try (ResultSet rs = metaData.getProcedures(TEST_PROJECT_ID, TEST_DATASET, PROCEDURE)) {
			assertTrue(rs.next(), "the procedure should have been listed");
			String body = rs.getString("REMARKS");
			logger.info("getProcedures() REMARKS for {}: {}", PROCEDURE, body);
			assertNotNull(body, "REMARKS was null — the routine body was discarded");
			assertTrue(body.contains("SELECT a AS echoed"), "expected the procedure body, got: " + body);
		}
	}

	@Test
	void functionRemarksCarryTheBody() throws SQLException {
		DatabaseMetaData metaData = connection.getMetaData();

		try (ResultSet rs = metaData.getFunctions(TEST_PROJECT_ID, TEST_DATASET, FUNCTION)) {
			assertTrue(rs.next(), "the function should have been listed");
			String body = rs.getString("REMARKS");
			assertNotNull(body, "REMARKS was null — the routine body was discarded");
			assertTrue(body.contains("x * 7"), "expected the function body, got: " + body);
		}
	}

	/**
	 * A dataset listing that contributes no view must not pay for the definition
	 * read. Asserted through the answer rather than by counting jobs: filtering to
	 * the base table alone still returns it, with REMARKS untouched.
	 */
	@Test
	void listingOnlyTablesStillWorks() throws SQLException {
		DatabaseMetaData metaData = connection.getMetaData();

		try (ResultSet rs = metaData.getTables(TEST_PROJECT_ID, TEST_DATASET, BASE_TABLE, new String[]{"TABLE"})) {
			assertTrue(rs.next(), "the base table should have been listed");
			assertEquals("TABLE", rs.getString("TABLE_TYPE"));
			assertEquals("", rs.getString("REMARKS"));
		}
	}

	/**
	 * The type filter has to be respected before the definitions are fetched, or a
	 * caller asking only for tables would be charged for a view read it excluded.
	 */
	@Test
	void viewsAreStillReturnedWhenTheTypeFilterAsksForThem() throws SQLException {
		DatabaseMetaData metaData = connection.getMetaData();

		List<String> names = new java.util.ArrayList<>();
		try (ResultSet rs = metaData.getTables(TEST_PROJECT_ID, TEST_DATASET, "src%_" + RUN_ID, new String[]{"VIEW"})) {
			while (rs.next()) {
				names.add(rs.getString("TABLE_NAME"));
				assertEquals("VIEW", rs.getString("TABLE_TYPE"));
			}
		}

		assertTrue(names.contains(VIEW), "expected the view, found: " + names);
		assertTrue(names.contains(DESCRIBED_VIEW), "expected the described view, found: " + names);
	}
}

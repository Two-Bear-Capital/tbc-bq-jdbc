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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Collapsing date-sharded tables into one wildcard entry (issue #188).
 *
 * <p>
 * A date-sharded set is listed as one {@code getTables()} row per shard, so a
 * year of daily shards is 365 rows in a database tree for what its users think
 * of as one table — JetBrains DBE-10947 and DBE-12807.
 *
 * <p>
 * The fixture deliberately includes a shard whose schema differs from the
 * others and a decoy table that ends in eight digits which are not a date.
 *
 * @since 3.2.0
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RealShardedTableMetadataTest extends AbstractRealBigQueryIntegrationTest {

	private static final Logger logger = LoggerFactory.getLogger(RealShardedTableMetadataTest.class);

	/** Unique per run, so concurrent CI runs do not collide on shard names. */
	private static final String PREFIX = "shard_events_" + RUN_ID;

	private static final String OLDEST = PREFIX + "_20260101";
	private static final String MIDDLE = PREFIX + "_20260102";
	/** The newest shard, and the only one with the {@code extra} column. */
	private static final String NEWEST = PREFIX + "_20260103";

	/** Eight trailing digits that are not a date; must not be collapsed. */
	private static final String DECOY = "shard_decoy_" + RUN_ID + "_12345678";

	/** A lone shard: one date-suffixed table is not a set. */
	private static final String LONE = "shard_lone_" + RUN_ID + "_20260101";

	private static final String EXPIRES = "OPTIONS(expiration_timestamp = "
			+ "TIMESTAMP_ADD(CURRENT_TIMESTAMP(), INTERVAL 2 HOUR))";

	private Connection collapsing;

	@BeforeAll
	void createObjects() throws SQLException {
		try (Connection setup = createTestConnection(); Statement stmt = setup.createStatement()) {
			for (String name : List.of(OLDEST, MIDDLE)) {
				stmt.execute(
						"CREATE OR REPLACE TABLE " + qualify(name) + " " + EXPIRES + " AS SELECT 1 AS id, 'a' AS name");
			}
			// Schema drift: the newest shard has a column the earlier ones lack.
			stmt.execute("CREATE OR REPLACE TABLE " + qualify(NEWEST) + " " + EXPIRES
					+ " AS SELECT 1 AS id, 'a' AS name, TRUE AS extra");
			stmt.execute("CREATE OR REPLACE TABLE " + qualify(DECOY) + " " + EXPIRES + " AS SELECT 1 AS id");
			stmt.execute("CREATE OR REPLACE TABLE " + qualify(LONE) + " " + EXPIRES + " AS SELECT 1 AS id");
		}
		collapsing = DriverManager.getConnection(
				String.format("jdbc:bigquery:%s/%s?authType=ADC&collapseShardedTables=true&metadataCacheEnabled=false",
						TEST_PROJECT_ID, TEST_DATASET));
	}

	@AfterAll
	void dropObjects() {
		try {
			if (collapsing != null && !collapsing.isClosed()) {
				collapsing.close();
			}
		} catch (SQLException e) {
			logger.debug("Ignoring error closing the collapsing connection: {}", e.getMessage());
		}
		try (Connection cleanup = createTestConnection(); Statement stmt = cleanup.createStatement()) {
			for (String name : List.of(OLDEST, MIDDLE, NEWEST, DECOY, LONE)) {
				stmt.execute("DROP TABLE IF EXISTS " + qualify(name));
			}
		} catch (SQLException e) {
			logger.debug("Ignoring error dropping shard fixtures for {}: {}", RUN_ID, e.getMessage());
		}
	}

	private static String qualify(String name) {
		return TEST_DATASET + "." + name;
	}

	/** Table names reported for our fixtures, on a given connection. */
	private List<String> tableNames(Connection source, String pattern) throws SQLException {
		DatabaseMetaData metaData = source.getMetaData();
		List<String> names = new ArrayList<>();
		try (ResultSet rs = metaData.getTables(TEST_PROJECT_ID, TEST_DATASET, pattern, null)) {
			while (rs.next()) {
				names.add(rs.getString("TABLE_NAME"));
			}
		}
		return names;
	}

	@Test
	void shardsCollapseIntoASingleWildcardEntry() throws SQLException {
		List<String> names = tableNames(collapsing, "shard_events_" + RUN_ID + "%");

		assertEquals(List.of(PREFIX + "_*"), names, "three shards should report as one wildcard entry");
	}

	@Test
	void theCollapsedEntryDescribesWhatItStandsFor() throws SQLException {
		// A wildcard name is otherwise the only clue that rows were removed.
		DatabaseMetaData metaData = collapsing.getMetaData();
		try (ResultSet rs = metaData.getTables(TEST_PROJECT_ID, TEST_DATASET, PREFIX + "_*", null)) {
			assertTrue(rs.next(), "asking for the name the driver reported should work");
			assertEquals(PREFIX + "_*", rs.getString("TABLE_NAME"));
			String remarks = rs.getString("REMARKS");
			logger.info("collapsed REMARKS: {}", remarks);
			assertTrue(remarks.startsWith("3 date-sharded tables"), "expected a shard summary, got: " + remarks);
			assertTrue(remarks.contains(OLDEST) && remarks.contains(NEWEST), "expected the range, got: " + remarks);
		}
	}

	@Test
	void withoutThePropertyEveryShardIsStillListed() throws SQLException {
		List<String> names = tableNames(connection, "shard_events_" + RUN_ID + "%");

		assertEquals(3, names.size(), "collapsing is opt-in; the default must not remove rows");
		assertTrue(names.containsAll(List.of(OLDEST, MIDDLE, NEWEST)));
	}

	@Test
	void aTableEndingInEightNonDateDigitsIsNotCollapsed() throws SQLException {
		// The risk the property exists to bound: a legitimately named table must not
		// disappear into a set.
		List<String> names = tableNames(collapsing, "shard_decoy_" + RUN_ID + "%");

		assertEquals(List.of(DECOY), names);
	}

	@Test
	void aLoneShardKeepsItsOwnName() throws SQLException {
		List<String> names = tableNames(collapsing, "shard_lone_" + RUN_ID + "%");

		assertEquals(List.of(LONE), names, "one date-suffixed table is not a set");
	}

	@Test
	void anExactShardLookupStillReturnsThatShard() throws SQLException {
		// Collapsing applies to listings. Naming one shard explicitly must still
		// reach it, or the individual tables become unreachable through metadata.
		List<String> names = tableNames(collapsing, MIDDLE);

		assertEquals(List.of(MIDDLE), names);
	}

	@Test
	void getColumnsAnswersForTheWildcardNameUsingTheNewestShard() throws SQLException {
		// Without this the collapsed node renders and will not expand. The newest
		// shard because shards drift — `extra` exists only in the last one, and a
		// query through the wildcard can select it.
		DatabaseMetaData metaData = collapsing.getMetaData();
		Map<String, Integer> columns = new HashMap<>();
		try (ResultSet rs = metaData.getColumns(TEST_PROJECT_ID, TEST_DATASET, PREFIX + "_*", null)) {
			while (rs.next()) {
				assertEquals(PREFIX + "_*", rs.getString("TABLE_NAME"));
				columns.put(rs.getString("COLUMN_NAME"), rs.getInt("ORDINAL_POSITION"));
			}
		}

		assertEquals(3, columns.size(), "expected the newest shard's schema, got: " + columns.keySet());
		assertTrue(columns.containsKey("extra"), "the newest shard's added column should be reported");
		assertEquals(1, columns.get("id"));
	}

	@Test
	void getColumnsForAllTablesReportsEachSetOnce() throws SQLException {
		// The other half of the row explosion: a year of daily shards is 365 x N
		// column rows, which is what actually fills the metadata cache.
		DatabaseMetaData metaData = collapsing.getMetaData();
		List<String> shardTables = new ArrayList<>();
		try (ResultSet rs = metaData.getColumns(TEST_PROJECT_ID, TEST_DATASET, "shard_events_" + RUN_ID + "%", null)) {
			while (rs.next()) {
				String table = rs.getString("TABLE_NAME");
				if (!shardTables.contains(table)) {
					shardTables.add(table);
				}
			}
		}

		assertEquals(List.of(PREFIX + "_*"), shardTables);
	}

	@Test
	void getPseudoColumnsReportsTableSuffixForACollapsedEntry() throws SQLException {
		// _TABLE_SUFFIX is what makes a wildcard table usable — it is how you
		// restrict a scan to a date range instead of reading every shard.
		DatabaseMetaData metaData = collapsing.getMetaData();
		boolean found = false;
		try (ResultSet rs = metaData.getPseudoColumns(TEST_PROJECT_ID, TEST_DATASET, PREFIX + "_*", null)) {
			while (rs.next()) {
				if ("_TABLE_SUFFIX".equals(rs.getString("COLUMN_NAME"))) {
					found = true;
					assertEquals(PREFIX + "_*", rs.getString("TABLE_NAME"));
					assertEquals(java.sql.Types.VARCHAR, rs.getInt("DATA_TYPE"));
					assertNotNull(rs.getString("REMARKS"));
				}
			}
		}
		assertTrue(found, "_TABLE_SUFFIX should be reported for a wildcard entry");
	}

	@Test
	void getPseudoColumnsReportsNoTableSuffixWithoutTheProperty() throws SQLException {
		DatabaseMetaData metaData = connection.getMetaData();
		try (ResultSet rs = metaData.getPseudoColumns(TEST_PROJECT_ID, TEST_DATASET, "shard_events_" + RUN_ID + "%",
				null)) {
			while (rs.next()) {
				assertFalse("_TABLE_SUFFIX".equals(rs.getString("COLUMN_NAME")),
						"there are no wildcard entries without collapsing, so nothing to describe");
			}
		}
	}

	@Test
	void theWildcardNameIsUsableSql() throws SQLException {
		// The point of using BigQuery's own wildcard syntax rather than a display
		// convention: the reported name can be queried as-is.
		try (Statement stmt = collapsing.createStatement();
				ResultSet rs = stmt.executeQuery(
						"SELECT COUNT(*) AS n FROM `" + TEST_PROJECT_ID + "." + TEST_DATASET + "." + PREFIX + "_*`")) {
			assertTrue(rs.next());
			assertEquals(3, rs.getInt("n"), "one row per shard");
		}
	}
}

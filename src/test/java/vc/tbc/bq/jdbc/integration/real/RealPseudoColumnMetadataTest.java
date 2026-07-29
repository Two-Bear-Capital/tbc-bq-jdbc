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
import java.sql.PseudoColumnUsage;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers {@code getPseudoColumns()} against real partitioned tables.
 *
 * <p>
 * Four fixtures, because the answer differs for each and no two can be inferred
 * from the others: daily and hourly ingestion-time partitioning, column
 * partitioning, and none. {@code _PARTITIONDATE} exists only at daily
 * granularity — on the hourly table, selecting it fails with "Unrecognized
 * name" — and {@code INFORMATION_SCHEMA.COLUMNS} reports the daily and hourly
 * tables identically, so the driver reads the partitioning clause to tell them
 * apart. That is the part most likely to break silently, hence a fixture for
 * each.
 *
 * @since 3.1.0
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RealPseudoColumnMetadataTest extends AbstractRealBigQueryIntegrationTest {

	private static final Logger logger = LoggerFactory.getLogger(RealPseudoColumnMetadataTest.class);

	private static final String DAILY = tableName("pseudo_daily");
	private static final String HOURLY = tableName("pseudo_hourly");
	private static final String COLUMN_PARTITIONED = tableName("pseudo_colpart");
	private static final String UNPARTITIONED = tableName("pseudo_plain");

	/**
	 * Fixture tables delete themselves, so a cancelled run strands nothing in the
	 * shared dataset. Mirrors {@code EXPIRES_SOON} in the base class, which is
	 * private to its {@code CREATE TABLE AS SELECT} helper and cannot be reused for
	 * a partitioned {@code CREATE TABLE}.
	 */
	private static final String EXPIRES_SOON = "OPTIONS(expiration_timestamp = "
			+ "TIMESTAMP_ADD(CURRENT_TIMESTAMP(), INTERVAL 2 HOUR))";

	@BeforeAll
	void createPartitionedTables() throws SQLException {
		try (Connection setup = createTestConnection(); Statement stmt = setup.createStatement()) {
			stmt.execute("CREATE OR REPLACE TABLE " + qualify(DAILY) + " (a INT64) "
					+ "PARTITION BY DATE(_PARTITIONTIME) " + EXPIRES_SOON);
			stmt.execute("CREATE OR REPLACE TABLE " + qualify(HOURLY) + " (a INT64) "
					+ "PARTITION BY TIMESTAMP_TRUNC(_PARTITIONTIME, HOUR) " + EXPIRES_SOON);
			stmt.execute("CREATE OR REPLACE TABLE " + qualify(COLUMN_PARTITIONED) + " (a INT64, d DATE) "
					+ "PARTITION BY d " + EXPIRES_SOON);
			stmt.execute("CREATE OR REPLACE TABLE " + qualify(UNPARTITIONED) + " (a INT64) " + EXPIRES_SOON);
		}
	}

	@AfterAll
	void dropPartitionedTables() {
		try (Connection cleanup = createTestConnection(); Statement stmt = cleanup.createStatement()) {
			for (String table : List.of(DAILY, HOURLY, COLUMN_PARTITIONED, UNPARTITIONED)) {
				stmt.execute("DROP TABLE IF EXISTS " + qualify(table));
			}
		} catch (SQLException e) {
			logger.debug("Ignoring error dropping pseudo-column fixtures for {}: {}", RUN_ID, e.getMessage());
		}
	}

	private static String qualify(String table) {
		return TEST_DATASET + "." + table;
	}

	private List<String> pseudoColumnsOf(String table) throws SQLException {
		DatabaseMetaData metaData = connection.getMetaData();
		List<String> names = new ArrayList<>();
		try (ResultSet rs = metaData.getPseudoColumns(TEST_PROJECT_ID, TEST_DATASET, table, null)) {
			while (rs.next()) {
				names.add(rs.getString("COLUMN_NAME"));
			}
		}
		return names;
	}

	@Test
	void dailyIngestionPartitioningHasBothPseudoColumns() throws SQLException {
		List<String> columns = pseudoColumnsOf(DAILY);

		logger.info("getPseudoColumns() found {} for the daily table", columns);
		assertTrue(columns.contains("_PARTITIONTIME"), "expected _PARTITIONTIME, found: " + columns);
		assertTrue(columns.contains("_PARTITIONDATE"), "expected _PARTITIONDATE, found: " + columns);
		assertEquals(2, columns.size(), "expected exactly the two partition pseudo columns, found: " + columns);
	}

	/**
	 * The case a naive implementation gets wrong. {@code _PARTITIONTIME} is there,
	 * but {@code _PARTITIONDATE} is not — BigQuery only exposes it at daily
	 * granularity, so reporting it here would name a column that fails to resolve.
	 */
	@Test
	void hourlyIngestionPartitioningHasOnlyPartitionTime() throws SQLException {
		List<String> columns = pseudoColumnsOf(HOURLY);

		assertEquals(List.of("_PARTITIONTIME"), columns,
				"an hourly table exposes _PARTITIONTIME alone, found: " + columns);
	}

	/**
	 * The control that keeps the assertions above honest: a table with no
	 * ingestion-time partitioning has no pseudo columns, so the method is selecting
	 * rather than reporting the same pair for everything.
	 */
	@Test
	void columnPartitionedAndPlainTablesHaveNoPseudoColumns() throws SQLException {
		assertTrue(pseudoColumnsOf(COLUMN_PARTITIONED).isEmpty(), "a column-partitioned table has no pseudo column");
		assertTrue(pseudoColumnsOf(UNPARTITIONED).isEmpty(), "an unpartitioned table has no pseudo column");
	}

	@Test
	void pseudoColumnsCarryTypesAndUsage() throws SQLException {
		DatabaseMetaData metaData = connection.getMetaData();

		try (ResultSet rs = metaData.getPseudoColumns(TEST_PROJECT_ID, TEST_DATASET, DAILY, "_PARTITIONTIME")) {
			assertEquals(12, rs.getMetaData().getColumnCount());
			assertTrue(rs.next(), "the column-name pattern should still match _PARTITIONTIME");
			assertEquals(TEST_PROJECT_ID, rs.getString("TABLE_CAT"));
			assertEquals(TEST_DATASET, rs.getString("TABLE_SCHEM"));
			assertEquals(DAILY, rs.getString("TABLE_NAME"));
			assertEquals(Types.TIMESTAMP, rs.getInt("DATA_TYPE"));
			assertEquals(PseudoColumnUsage.NO_USAGE_RESTRICTIONS.name(), rs.getString("COLUMN_USAGE"));
			assertEquals("YES", rs.getString("IS_NULLABLE"));
			assertFalse(rs.next(), "the pattern should have excluded _PARTITIONDATE");
		}
	}

	@Test
	void partitionDateIsReportedAsADate() throws SQLException {
		DatabaseMetaData metaData = connection.getMetaData();

		try (ResultSet rs = metaData.getPseudoColumns(TEST_PROJECT_ID, TEST_DATASET, DAILY, "_PARTITIONDATE")) {
			assertTrue(rs.next());
			assertEquals(Types.DATE, rs.getInt("DATA_TYPE"),
					"_PARTITIONDATE is a DATE, not the TIMESTAMP its sibling is");
		}
	}

	/**
	 * The pseudo columns have to actually work, or describing them is worse than
	 * silence. Each reported column is selected back off the table it was reported
	 * for.
	 */
	@Test
	void everyReportedPseudoColumnIsQueryable() throws SQLException {
		for (String table : List.of(DAILY, HOURLY)) {
			for (String column : pseudoColumnsOf(table)) {
				try (Statement stmt = connection.createStatement();
						ResultSet rs = stmt.executeQuery("SELECT " + column + " FROM " + qualify(table))) {
					assertFalse(rs.next(), table + " is empty, so " + column + " should return no rows");
				}
			}
		}
	}
}

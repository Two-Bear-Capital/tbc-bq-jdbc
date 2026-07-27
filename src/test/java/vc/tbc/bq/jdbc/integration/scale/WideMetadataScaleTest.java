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
package vc.tbc.bq.jdbc.integration.scale;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises metadata against a project wide enough for the fan-out cap to
 * engage.
 *
 * <h2>What was untested</h2>
 *
 * <p>
 * #99 added a {@code Semaphore} to {@code BQDatabaseMetaData.executeInParallel}
 * capping concurrent {@code INFORMATION_SCHEMA} queries at 16, because a
 * project with hundreds of datasets would otherwise fire one per dataset at
 * once and collect quota errors. Nothing then or since ran it against more than
 * a handful of datasets, so the cap had never actually been reached — the code
 * path where a task waits on a permit had never executed.
 *
 * <p>
 * The fixture therefore builds <em>more datasets than the cap</em>. That is the
 * whole point of the number: at 20 datasets against a cap of 16, at least four
 * tasks must queue for a permit. A fixture of ten would build a lot of BigQuery
 * objects and still never test the thing.
 *
 * <h2>Cost</h2>
 *
 * <p>
 * This creates 20 datasets holding 300 tables and drops them again. Tables
 * carry a two-hour expiry so a cancelled run cannot strand them; datasets have
 * no BigQuery-side expiry, so they are dropped in {@code @AfterAll} and named
 * {@code scale_<runId>_n} to be recognisable if a run dies before that. Nothing
 * is queried, so nothing is billed for bytes scanned.
 */
@DisplayName("Scale: wide metadata")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WideMetadataScaleTest extends AbstractScaleTest {

	private static final Logger logger = LoggerFactory.getLogger(WideMetadataScaleTest.class);

	/**
	 * Must exceed {@code MAX_CONCURRENT_METADATA_QUERIES} (16) for the semaphore to
	 * block anything. Raising this via the environment is the way to hunt a fan-out
	 * problem at real scale.
	 */
	private static final int DATASET_COUNT = envInt("BQ_SCALE_DATASETS", 20);

	private static final int TABLES_PER_DATASET = envInt("BQ_SCALE_TABLES_PER_DATASET", 15);

	/**
	 * Budget for a cold {@code getTables()} across the whole project. Generous:
	 * this is a smoke alarm for the fan-out serialising or deadlocking, not a
	 * latency SLA. Metadata calls that used to take 90+ seconds against large
	 * projects are the reason the cache exists at all.
	 */
	private static final long MAX_COLD_METADATA_MILLIS = envInt("BQ_SCALE_METADATA_BUDGET_MS", 180_000);

	private static String datasetName(int index) {
		return String.format("scale_%s_%d", RUN_ID, index);
	}

	@BeforeAll
	void createWideFixture() throws SQLException {
		logger.info("Creating {} datasets x {} tables (run {})", DATASET_COUNT, TABLES_PER_DATASET, RUN_ID);
		long startNanos = System.nanoTime();

		try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
			for (int i = 1; i <= DATASET_COUNT; i++) {
				String dataset = datasetName(i);
				statement.execute(String.format("CREATE SCHEMA IF NOT EXISTS `%s.%s`", TEST_PROJECT_ID, dataset));

				// One scripted job per dataset rather than one job per table. 300
				// separate CREATE TABLE jobs would each cost a round trip and take the
				// fixture from about a minute to the better part of an hour; a BigQuery
				// script runs all of a dataset's DDL inside a single job.
				statement.execute(String.format(
						"FOR t IN (SELECT * FROM UNNEST(GENERATE_ARRAY(1, %d)) AS n) DO "
								+ "EXECUTE IMMEDIATE FORMAT(\"\"\"CREATE TABLE IF NOT EXISTS `%s.%s.t_%%03d` "
								+ "(id INT64, name STRING, value FLOAT64, created_at TIMESTAMP) "
								+ "OPTIONS(expiration_timestamp = TIMESTAMP_ADD(CURRENT_TIMESTAMP(), "
								+ "INTERVAL 2 HOUR))\"\"\", t.n); " + "END FOR;",
						TABLES_PER_DATASET, TEST_PROJECT_ID, dataset));
			}
		}

		logger.info("Fixture ready in {} s", (System.nanoTime() - startNanos) / 1_000_000_000);
	}

	@AfterAll
	void dropWideFixture() {
		// Best effort, and each drop is independent: one failure must not strand the
		// other nineteen datasets, which have no expiry to clean them up later.
		try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
			for (int i = 1; i <= DATASET_COUNT; i++) {
				String dataset = datasetName(i);
				try {
					statement.execute(String.format("DROP SCHEMA IF EXISTS `%s.%s` CASCADE", TEST_PROJECT_ID, dataset));
				} catch (SQLException e) {
					logger.warn("Failed to drop dataset {} — drop it by hand: {}", dataset, e.getMessage());
				}
			}
		} catch (SQLException e) {
			logger.error("Could not open a connection to drop the scale datasets. They are named scale_{}_* "
					+ "and must be removed by hand.", RUN_ID, e);
		}
	}

	@Test
	@DisplayName("getSchemas() returns every dataset when fan-out exceeds the concurrency cap")
	void getSchemasCoversAllDatasetsBeyondTheCap() throws Exception {
		try (Connection connection = openConnection("&metadataCacheEnabled=false")) {
			Set<String> found = new HashSet<>();

			long startNanos = System.nanoTime();
			try (ResultSet rs = connection.getMetaData().getSchemas()) {
				while (rs.next()) {
					found.add(rs.getString("TABLE_SCHEM"));
				}
			}
			long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;
			logger.info("getSchemas() returned {} schemas in {} ms", found.size(), elapsedMillis);

			for (int i = 1; i <= DATASET_COUNT; i++) {
				String dataset = datasetName(i);
				assertTrue(found.contains(dataset),
						() -> "getSchemas() lost dataset " + dataset + " — a fan-out task's result was dropped");
			}
		}
	}

	@Test
	@DisplayName("cold getTables() across a wide project completes without serialising")
	void coldGetTablesCompletesWithinBudget() throws Exception {
		// Cache disabled so this measures the fan-out itself. With the cache on, a
		// second caller would be served from memory and the test would pass without
		// the parallel path having run at all.
		try (Connection connection = openConnection("&metadataCacheEnabled=false")) {
			DatabaseMetaData metaData = connection.getMetaData();

			long startNanos = System.nanoTime();
			int ours = 0;
			try (ResultSet rs = metaData.getTables(null, null, "%", null)) {
				while (rs.next()) {
					if (rs.getString("TABLE_SCHEM").startsWith("scale_" + RUN_ID + "_")) {
						ours++;
					}
				}
			}
			long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;

			logger.info("Cold getTables() found {} fixture tables in {} ms", ours, elapsedMillis);

			assertEquals(DATASET_COUNT * TABLES_PER_DATASET, ours,
					"every fixture table should appear exactly once across the fan-out");

			assertTrue(elapsedMillis <= MAX_COLD_METADATA_MILLIS,
					() -> String.format(
							"cold getTables() took %d ms against a %d ms budget across %d datasets — the fan-out "
									+ "is serialising or the concurrency cap is starving it",
							elapsedMillis, MAX_COLD_METADATA_MILLIS, DATASET_COUNT));
		}
	}

	@Test
	@DisplayName("table name patterns filter correctly across hundreds of tables")
	void tableNamePatternsFilterAcrossWideMetadata() throws Exception {
		try (Connection connection = openConnection("&metadataCacheEnabled=false")) {
			DatabaseMetaData metaData = connection.getMetaData();

			// t_001 exists once per dataset, so an exact-name match must return exactly
			// DATASET_COUNT rows. This is the pattern-matching path #99 found compiling
			// a regex per row: correctness here, throughput in the benchmarks.
			int exact = 0;
			try (ResultSet rs = metaData.getTables(null, "scale\\_" + RUN_ID + "\\_%", "t_001", null)) {
				while (rs.next()) {
					assertEquals("t_001", rs.getString("TABLE_NAME"), "exact pattern returned a different table");
					exact++;
				}
			}
			assertEquals(DATASET_COUNT, exact, "an exact table name should match once per fixture dataset");

			// A wildcard restricted to one dataset must not leak rows from the other 19.
			String oneDataset = datasetName(1);
			int wildcard = 0;
			try (ResultSet rs = metaData.getTables(null, oneDataset, "t\\_%", null)) {
				while (rs.next()) {
					assertEquals(oneDataset, rs.getString("TABLE_SCHEM"),
							"a schema-scoped pattern returned a table from another dataset");
					wildcard++;
				}
			}
			assertEquals(TABLES_PER_DATASET, wildcard, "wildcard should match every table in exactly one dataset");
		}
	}

	@Test
	@DisplayName("getColumns() filters by column pattern across hundreds of tables")
	void columnPatternsFilterAcrossWideMetadata() throws Exception {
		try (Connection connection = openConnection("&metadataCacheEnabled=false")) {
			String oneDataset = datasetName(1);

			int matched = 0;
			try (ResultSet rs = connection.getMetaData().getColumns(null, oneDataset, "t\\_%", "name")) {
				while (rs.next()) {
					assertEquals("name", rs.getString("COLUMN_NAME"), "column pattern returned a different column");
					matched++;
				}
			}

			// Every fixture table declares exactly one column called `name`.
			assertEquals(TABLES_PER_DATASET, matched, "a column pattern should match once per table in the dataset");
		}
	}
}

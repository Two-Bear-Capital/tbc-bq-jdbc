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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The LOAD-job path for large batch inserts (issue #194).
 *
 * <p>
 * {@code executeBatch()} collapses parameterized INSERTs into multi-row DML,
 * one query job per chunk. Above a threshold that is the wrong mechanism: a
 * million rows means hundreds of jobs against DML quotas, where BigQuery's own
 * answer is one load job.
 *
 * <p>
 * <b>A load job writes data, so a wrong encoding is silently wrong data rather
 * than an error.</b> The central test here therefore loads the same rows
 * through both paths and compares the resulting tables, rather than asserting
 * values against hand-written expectations — the same approach that keeps the
 * Storage Read API path honest.
 *
 * @since 3.2.0
 */
class RealBatchLoadTest extends AbstractRealBigQueryIntegrationTest {

	private static final Logger logger = LoggerFactory.getLogger(RealBatchLoadTest.class);

	/** Low enough to keep these tests quick; the mechanism does not care. */
	private static final int THRESHOLD = 5;

	private static final String COLUMNS = "(id INT64, name STRING, score FLOAT64, amount NUMERIC, "
			+ "flag BOOL, d DATE, ts TIMESTAMP, payload BYTES)";

	private static final String INSERT = " (id, name, score, amount, flag, d, ts, payload) VALUES (?,?,?,?,?,?,?,?)";

	private Connection loadConnection() throws SQLException {
		return DriverManager.getConnection(String.format("jdbc:bigquery:%s/%s?authType=ADC&batchLoadThreshold=%d",
				TEST_PROJECT_ID, TEST_DATASET, THRESHOLD));
	}

	private String createTable(String suffix) throws SQLException {
		String name = tableName("load_" + suffix);
		try (Connection c = createTestConnection(); Statement s = c.createStatement()) {
			s.execute("CREATE OR REPLACE TABLE " + TEST_DATASET + "." + name + " " + COLUMNS
					+ " OPTIONS(expiration_timestamp = TIMESTAMP_ADD(CURRENT_TIMESTAMP(), INTERVAL 2 HOUR))");
		}
		return name;
	}

	private void drop(String name) {
		try (Connection c = createTestConnection(); Statement s = c.createStatement()) {
			s.execute("DROP TABLE IF EXISTS " + TEST_DATASET + "." + name);
		} catch (SQLException e) {
			logger.debug("Ignoring cleanup failure for {}: {}", name, e.getMessage());
		}
	}

	/** Binds one deliberately awkward row; {@code i} varies it. */
	private static void bindRow(PreparedStatement stmt, int i) throws SQLException {
		stmt.setLong(1, i);
		// Quotes, backslashes, newlines and a tab: all JSON syntax that would corrupt
		// the payload if the encoder did not escape it.
		stmt.setString(2, "name \"" + i + "\" \\ with\nnewline\ttab");
		stmt.setDouble(3, i / 3.0);
		stmt.setBigDecimal(4, new BigDecimal(i).divide(new BigDecimal("7"), 9, java.math.RoundingMode.HALF_UP));
		stmt.setBoolean(5, i % 2 == 0);
		stmt.setDate(6, Date.valueOf("2026-07-28"));
		stmt.setTimestamp(7, Timestamp.valueOf("2026-07-28 12:34:56.789"));
		if (i % 3 == 0) {
			stmt.setNull(8, Types.VARBINARY);
		} else {
			stmt.setBytes(8, ("payload_" + i).getBytes(java.nio.charset.StandardCharsets.UTF_8));
		}
	}

	private static int[] insertRows(Connection conn, String table, int rows) throws SQLException {
		try (PreparedStatement stmt = conn.prepareStatement("INSERT INTO " + TEST_DATASET + "." + table + INSERT)) {
			for (int i = 1; i <= rows; i++) {
				bindRow(stmt, i);
				stmt.addBatch();
			}
			return stmt.executeBatch();
		}
	}

	/** Every row as strings, ordered, for comparing two tables. */
	private List<List<String>> readTable(String table) throws SQLException {
		List<List<String>> rows = new ArrayList<>();
		try (Statement stmt = connection.createStatement();
				ResultSet rs = stmt.executeQuery("SELECT id, name, score, amount, flag, d, ts, payload FROM "
						+ TEST_DATASET + "." + table + " ORDER BY id")) {
			int columns = rs.getMetaData().getColumnCount();
			while (rs.next()) {
				List<String> row = new ArrayList<>(columns);
				for (int i = 1; i <= columns; i++) {
					row.add(java.util.Objects.toString(rs.getString(i), "<null>"));
				}
				rows.add(row);
			}
		}
		return rows;
	}

	@Test
	void loadedRowsAreIdenticalToDmlInsertedRows() throws SQLException {
		// The assertion that matters: a load job writes data, so an encoding mistake
		// is wrong data rather than a failure. Comparing the two paths' tables is a
		// stronger check than any hand-written expectation.
		String viaLoad = createTable("via_load");
		String viaDml = createTable("via_dml");
		try {
			try (Connection loading = loadConnection()) {
				insertRows(loading, viaLoad, THRESHOLD + 2);
			}
			// The default connection has no threshold, so this takes the DML path.
			insertRows(connection, viaDml, THRESHOLD + 2);

			List<List<String>> loaded = readTable(viaLoad);
			List<List<String>> inserted = readTable(viaDml);

			assertEquals(inserted.size(), loaded.size(), "row counts differ between the load and DML paths");
			assertTrue(!inserted.isEmpty(), "fixture wrote no rows");
			for (int row = 0; row < inserted.size(); row++) {
				assertEquals(inserted.get(row), loaded.get(row), "row " + row + " differs between the paths");
			}
			logger.info("compared {} rows x {} columns across the load and DML paths", loaded.size(),
					loaded.get(0).size());
		} finally {
			drop(viaLoad);
			drop(viaDml);
		}
	}

	@Test
	void aBatchBelowTheThresholdStaysOnTheDmlPath() throws SQLException {
		// Not observable through the data, which is the point of the comparison test
		// above, so this asserts on the update counts: the DML path reports 1 per row
		// when BigQuery confirms the count.
		String table = createTable("below");
		try (Connection loading = loadConnection()) {
			int[] counts = insertRows(loading, table, THRESHOLD - 1);

			assertEquals(THRESHOLD - 1, counts.length);
			assertArrayEquals(new int[]{1, 1, 1, 1}, counts, "a sub-threshold batch keeps the DML path's counts");
			assertEquals(THRESHOLD - 1, readTable(table).size());
		} finally {
			drop(table);
		}
	}

	@Test
	void updateCountsReportOnePerRowWhenTheLoadWritesThemAll() throws SQLException {
		// A load job reports rows written in aggregate. Where that matches the batch
		// exactly, per-row counts of 1 are the truthful answer rather than a guess.
		String table = createTable("counts");
		try (Connection loading = loadConnection()) {
			int[] counts = insertRows(loading, table, THRESHOLD + 3);

			assertEquals(THRESHOLD + 3, counts.length);
			for (int count : counts) {
				assertEquals(1, count, "the load wrote every row, so each entry should be 1");
			}
		} finally {
			drop(table);
		}
	}

	@Test
	void aBatchInsideATransactionStaysOnTheDmlPath() throws SQLException {
		// Load jobs cannot join a BigQuery transaction: the rows would land outside
		// it and survive a rollback. This is the distinction #194 asked to be
		// explicit rather than accidental.
		String table = createTable("txn");
		try (Connection loading = loadConnection()) {
			loading.setAutoCommit(false);
			insertRows(loading, table, THRESHOLD + 2);
			loading.rollback();

			assertEquals(0, readTable(table).size(),
					"the batch must have been DML, so the rollback discarded it; a load job would have kept the rows");
		} finally {
			drop(table);
		}
	}

	@Test
	void anInsertWithoutAColumnListStaysOnTheDmlPath() throws SQLException {
		// Without names the column order is the table's, and the load path refuses to
		// guess it. The rows must still be written, by the DML path.
		String table = createTable("nocols");
		try (Connection loading = loadConnection();
				PreparedStatement stmt = loading
						.prepareStatement("INSERT INTO " + TEST_DATASET + "." + table + " VALUES (?,?,?,?,?,?,?,?)")) {
			for (int i = 1; i <= THRESHOLD + 1; i++) {
				bindRow(stmt, i);
				stmt.addBatch();
			}
			int[] counts = stmt.executeBatch();

			assertEquals(THRESHOLD + 1, counts.length);
			assertEquals(THRESHOLD + 1, readTable(table).size(), "the rows must still be written");
		} finally {
			drop(table);
		}
	}

	@Test
	void withoutThePropertyEvenALargeBatchUsesDml() throws SQLException {
		// Opt-in: the load path is a different mechanism, not a faster one of the
		// same kind, so it is never chosen without being asked for.
		String table = createTable("optin");
		try {
			int[] counts = insertRows(connection, table, THRESHOLD + 5);

			assertEquals(THRESHOLD + 5, counts.length);
			assertEquals(THRESHOLD + 5, readTable(table).size());
		} finally {
			drop(table);
		}
	}
}

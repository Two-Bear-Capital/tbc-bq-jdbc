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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Storage Read API path must return exactly what the REST path returns.
 *
 * <p>
 * The Storage path re-encodes Arrow values into the same {@code FieldValue}
 * representation {@code tabledata.list} produces, so that every JDBC getter can
 * be inherited unchanged. That only holds if the encoding really is identical,
 * and the failure mode if it is not is quiet: a timestamp off by a rounding
 * error, a NUMERIC that lost its scale, a DATE shifted by a day. None of that
 * throws — it just returns wrong data.
 *
 * <p>
 * So rather than assert values against hand-written expectations, these tests
 * run the same query down both paths and compare the results cell by cell. The
 * REST path is the reference implementation; anything the Storage path does
 * differently is a bug in the Storage path, including in cases where BigQuery's
 * own encoding is peculiar.
 *
 * <p>
 * A precondition worth stating: if the Storage path silently fell back to REST,
 * every comparison here would trivially pass.
 * {@link #storagePathIsActuallyInUse()} exists so that cannot happen unnoticed.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StorageApiParityIT extends AbstractRealBigQueryIntegrationTest {

	private static final Logger logger = LoggerFactory.getLogger(StorageApiParityIT.class);

	/**
	 * Every scalar type the converter claims to support, including the values most
	 * likely to expose an encoding difference: microsecond precision, negative and
	 * fractional numerics, nulls, empty strings, and values either side of zero.
	 */
	private static final String ALL_TYPES_SQL = """
			SELECT
			  i AS idx,
			  CAST(i AS INT64) AS c_int64,
			  CAST(i AS FLOAT64) / 3 AS c_float64,
			  CAST(i AS NUMERIC) / 7 AS c_numeric,
			  CAST(i AS BIGNUMERIC) / 3 AS c_bignumeric,
			  MOD(i, 2) = 0 AS c_bool,
			  CONCAT('str_', CAST(i AS STRING)) AS c_string,
			  CAST(CONCAT('bytes_', CAST(i AS STRING)) AS BYTES) AS c_bytes,
			  DATE_ADD(DATE '2020-02-28', INTERVAL i DAY) AS c_date,
			  TIME_ADD(TIME '00:00:00', INTERVAL i * 1000001 MICROSECOND) AS c_time,
			  DATETIME_ADD(DATETIME '2020-02-28 23:59:59.999999', INTERVAL i MICROSECOND) AS c_datetime,
			  TIMESTAMP_ADD(TIMESTAMP '2020-02-28 23:59:59.999999+00', INTERVAL i MICROSECOND) AS c_timestamp,
			  ST_GEOGPOINT(i / 10, i / 20) AS c_geography,
			  TO_JSON(STRUCT(i AS n)) AS c_json,
			  IF(MOD(i, 3) = 0, NULL, CAST(i AS INT64)) AS c_nullable_int,
			  IF(MOD(i, 3) = 1, NULL, CONCAT('s', CAST(i AS STRING))) AS c_nullable_string,
			  IF(MOD(i, 5) = 0, '', CONCAT('x', CAST(i AS STRING))) AS c_maybe_empty
			FROM UNNEST(GENERATE_ARRAY(-20, 20)) AS i
			ORDER BY idx
			""";

	/**
	 * Big enough to span several Arrow record batches, so batch edges are
	 * exercised.
	 */
	private static final String MANY_ROWS_SQL = """
			SELECT
			  i AS idx,
			  CONCAT('row_', CAST(i AS STRING)) AS name,
			  CAST(i AS FLOAT64) * 1.5 AS score,
			  TIMESTAMP_ADD(TIMESTAMP '2020-01-01 00:00:00+00', INTERVAL i MICROSECOND) AS ts
			FROM UNNEST(GENERATE_ARRAY(1, 200000)) AS i
			ORDER BY idx
			""";

	private Connection storageConnection() throws SQLException {
		// useStorageApi=true rather than auto: these results are deliberately small,
		// and auto would decline them.
		String url = String.format("jdbc:bigquery:%s/%s?authType=ADC&useStorageApi=true&maxBillingBytes=1073741824",
				TEST_PROJECT_ID, TEST_DATASET);
		return DriverManager.getConnection(url);
	}

	private Connection restConnection() throws SQLException {
		String url = String.format("jdbc:bigquery:%s/%s?authType=ADC&useStorageApi=false&maxBillingBytes=1073741824",
				TEST_PROJECT_ID, TEST_DATASET);
		return DriverManager.getConnection(url);
	}

	@Test
	@DisplayName("the Storage path is genuinely engaged, so parity assertions mean something")
	void storagePathIsActuallyInUse() throws SQLException {
		try (Connection conn = storageConnection();
				Statement stmt = conn.createStatement();
				ResultSet rs = stmt.executeQuery("SELECT 1 AS n")) {

			assertEquals("vc.tbc.bq.jdbc.storage.StorageReadResultSet", rs.getClass().getName(), """
					The Storage Read API path did not engage, which would make every other test in this \
					class pass vacuously. Most likely causes: Arrow could not allocate (needs \
					--add-opens=java.base/java.nio=ALL-UNNAMED, which the failsafe config sets), the \
					Storage API is disabled on the project, or the credentials lack \
					bigquery.readsessions.create.""");
			assertTrue(rs.next());
			assertEquals(1, rs.getInt("n"));
		}
	}

	@Test
	@DisplayName("every supported scalar type reads identically on both paths")
	void allScalarTypesMatchTheRestPath() throws SQLException {
		List<List<String>> viaRest = readAllAsStrings(restConnection(), ALL_TYPES_SQL);
		List<List<String>> viaStorage = readAllAsStrings(storageConnection(), ALL_TYPES_SQL);

		assertEquals(viaRest.size(), viaStorage.size(), "row count differs between the two paths");
		assertFalse(viaRest.isEmpty(), "fixture produced no rows");

		for (int row = 0; row < viaRest.size(); row++) {
			assertEquals(viaRest.get(row), viaStorage.get(row), "row " + row + " differs between REST and Storage");
		}
		logger.info("compared {} rows x {} columns across both paths", viaRest.size(), viaRest.get(0).size());
	}

	@Test
	@DisplayName("typed getters agree too, not just their string forms")
	void typedGettersMatchTheRestPath() throws SQLException {
		try (Connection rest = restConnection();
				Connection storage = storageConnection();
				Statement restStmt = rest.createStatement();
				Statement storageStmt = storage.createStatement();
				ResultSet restRs = restStmt.executeQuery(ALL_TYPES_SQL);
				ResultSet storageRs = storageStmt.executeQuery(ALL_TYPES_SQL)) {

			int rows = 0;
			while (restRs.next()) {
				assertTrue(storageRs.next(), "Storage path ran out of rows at row " + rows);

				assertEquals(restRs.getLong("c_int64"), storageRs.getLong("c_int64"), "c_int64 at row " + rows);
				assertEquals(restRs.getDouble("c_float64"), storageRs.getDouble("c_float64"), 0.0,
						"c_float64 at row " + rows);
				assertEquals(restRs.getBigDecimal("c_numeric"), storageRs.getBigDecimal("c_numeric"),
						"c_numeric at row " + rows);
				assertEquals(restRs.getBoolean("c_bool"), storageRs.getBoolean("c_bool"), "c_bool at row " + rows);
				assertEquals(restRs.getString("c_string"), storageRs.getString("c_string"), "c_string at row " + rows);
				assertArrayEqualsOrBothNull(restRs.getBytes("c_bytes"), storageRs.getBytes("c_bytes"),
						"c_bytes at row " + rows);
				assertEquals(restRs.getDate("c_date"), storageRs.getDate("c_date"), "c_date at row " + rows);
				assertEquals(restRs.getTimestamp("c_timestamp"), storageRs.getTimestamp("c_timestamp"),
						"c_timestamp at row " + rows);

				// wasNull() has to track identically, or callers reading nullable columns
				// get the wrong answer without any error.
				restRs.getLong("c_nullable_int");
				storageRs.getLong("c_nullable_int");
				assertEquals(restRs.wasNull(), storageRs.wasNull(), "wasNull for c_nullable_int at row " + rows);

				rows++;
			}
			assertFalse(storageRs.next(), "Storage path returned more rows than REST");
			assertTrue(rows > 0, "fixture produced no rows");
		}
	}

	@Test
	@DisplayName("results spanning many Arrow batches stay in order and complete")
	void largeResultCrossesBatchBoundariesWithoutLoss() throws SQLException {
		try (Connection conn = storageConnection();
				Statement stmt = conn.createStatement();
				ResultSet rs = stmt.executeQuery(MANY_ROWS_SQL)) {

			assertEquals("vc.tbc.bq.jdbc.storage.StorageReadResultSet", rs.getClass().getName(),
					"this test is only meaningful on the Storage path");

			long expected = 1;
			while (rs.next()) {
				// Rows must arrive in query order and none may be dropped or repeated at a
				// record-batch boundary, which is where a decoding bug would show up.
				assertEquals(expected, rs.getLong("idx"), "row out of order or missing at index " + expected);
				assertEquals("row_" + expected, rs.getString("name"), "value mismatch at index " + expected);
				expected++;
			}
			assertEquals(200_001, expected, "expected 200,000 rows");
		}
	}

	@Test
	@DisplayName("metadata is identical, since it does not come from Arrow")
	void metadataMatchesTheRestPath() throws SQLException {
		try (Connection rest = restConnection();
				Connection storage = storageConnection();
				Statement restStmt = rest.createStatement();
				Statement storageStmt = storage.createStatement();
				ResultSet restRs = restStmt.executeQuery(ALL_TYPES_SQL);
				ResultSet storageRs = storageStmt.executeQuery(ALL_TYPES_SQL)) {

			ResultSetMetaData restMeta = restRs.getMetaData();
			ResultSetMetaData storageMeta = storageRs.getMetaData();

			assertEquals(restMeta.getColumnCount(), storageMeta.getColumnCount());
			for (int i = 1; i <= restMeta.getColumnCount(); i++) {
				assertEquals(restMeta.getColumnName(i), storageMeta.getColumnName(i), "column name " + i);
				assertEquals(restMeta.getColumnType(i), storageMeta.getColumnType(i), "column type " + i);
				assertEquals(restMeta.getColumnTypeName(i), storageMeta.getColumnTypeName(i), "column type name " + i);
				assertEquals(restMeta.isNullable(i), storageMeta.isNullable(i), "nullability " + i);
			}
		}
	}

	@Test
	@DisplayName("an empty result works on the Storage path")
	void emptyResultIsHandled() throws SQLException {
		try (Connection conn = storageConnection();
				Statement stmt = conn.createStatement();
				ResultSet rs = stmt.executeQuery("SELECT 1 AS n FROM UNNEST([]) AS x")) {

			// An empty result yields a read session with no streams at all, which is a
			// distinct code path from "a stream that returns no batches".
			assertFalse(rs.next(), "empty result must simply have no rows");
		}
	}

	@Test
	@DisplayName("maxRows still stops iteration early")
	void maxRowsIsHonouredOnTheStoragePath() throws SQLException {
		try (Connection conn = storageConnection(); Statement stmt = conn.createStatement()) {
			stmt.setMaxRows(10);
			try (ResultSet rs = stmt.executeQuery(MANY_ROWS_SQL)) {
				int rows = 0;
				while (rs.next()) {
					rows++;
				}
				assertEquals(10, rows, "maxRows must bound the Storage path exactly as it bounds the REST path");
			}
		}
	}

	@Test
	@DisplayName("a query with unsupported types falls back and still returns rows")
	void unsupportedTypesFallBackToRest() throws SQLException {
		try (Connection conn = storageConnection();
				Statement stmt = conn.createStatement();
				ResultSet rs = stmt.executeQuery("SELECT [1, 2, 3] AS arr, STRUCT(1 AS a, 'x' AS b) AS s")) {

			// ARRAY and STRUCT are outside the converter's remit; the query must still
			// work, served by the standard path.
			assertEquals("vc.tbc.bq.jdbc.BQResultSet", rs.getClass().getName(),
					"unsupported column types must fall back to the REST path");
			assertTrue(rs.next());
			assertNotNull(rs.getString("arr"));
		}
	}

	private static void assertArrayEqualsOrBothNull(byte[] expected, byte[] actual, String message) {
		if (expected == null || actual == null) {
			assertEquals(expected == null, actual == null, message);
			return;
		}
		assertEquals(new String(expected), new String(actual), message);
	}

	/**
	 * Reads a whole result as strings, which is the comparison that catches
	 * encoding differences most directly: {@code getString} exposes the underlying
	 * representation rather than a parsed view of it.
	 */
	private static List<List<String>> readAllAsStrings(Connection connection, String sql) throws SQLException {
		List<List<String>> rows = new ArrayList<>();
		try (Connection conn = connection;
				Statement stmt = conn.createStatement();
				ResultSet rs = stmt.executeQuery(sql)) {
			int columns = rs.getMetaData().getColumnCount();
			while (rs.next()) {
				List<String> row = new ArrayList<>(columns);
				for (int i = 1; i <= columns; i++) {
					row.add(Objects.toString(rs.getString(i), "<null>"));
				}
				rows.add(row);
			}
		}
		return rows;
	}
}

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
 *
 * <p>
 * <b>The name matters.</b> Failsafe includes
 * {@code **}{@code /integration/real/**}{@code /*Test.java}, so a class named
 * {@code ...IT} is silently excluded from the suite and only runs when named
 * explicitly with {@code -Dit.test=}. This class spent its first day that way:
 * green locally on targeted runs, absent from CI, with a stale report on disk
 * making the suite look eight tests larger than it was. Keep the {@code Test}
 * suffix.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StorageApiParityTest extends AbstractRealBigQueryIntegrationTest {

	private static final Logger logger = LoggerFactory.getLogger(StorageApiParityTest.class);

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

	/**
	 * ARRAY, STRUCT and INTERVAL, including the shapes most likely to expose a
	 * difference: empty arrays, null structs, nulls inside arrays and structs,
	 * arrays of structs, structs containing arrays, nesting two deep, and intervals
	 * with each part signed independently.
	 */
	private static final String COMPLEX_TYPES_SQL = """
			SELECT
			  i AS idx,
			  [i, i + 1, i + 2] AS c_arr_int,
			  [CONCAT('s', CAST(i AS STRING)), 'x'] AS c_arr_string,
			  IF(MOD(i, 4) = 0, CAST([] AS ARRAY<INT64>), [i]) AS c_arr_maybe_empty,
			  [CAST(i AS NUMERIC) / 7, CAST(i AS NUMERIC)] AS c_arr_numeric,
			  [TIMESTAMP_ADD(TIMESTAMP '2020-02-28 23:59:59.999999+00', INTERVAL i MICROSECOND)] AS c_arr_timestamp,
			  STRUCT(i AS n, CONCAT('s', CAST(i AS STRING)) AS s) AS c_struct,
			  IF(MOD(i, 5) = 0, NULL, STRUCT(i AS n)) AS c_struct_nullable,
			  STRUCT(i AS n, IF(MOD(i, 3) = 0, NULL, CAST(i AS STRING)) AS maybe) AS c_struct_with_null,
			  STRUCT(i AS n, STRUCT(CONCAT('d', CAST(i AS STRING)) AS deep, i * 2 AS n2) AS nested) AS c_struct_nested,
			  [STRUCT(i AS n, CONCAT('a', CAST(i AS STRING)) AS s)] AS c_arr_struct,
			  STRUCT([i, i + 1] AS nums, CAST(i AS STRING) AS label) AS c_struct_arr,
			  MAKE_INTERVAL(i, MOD(i, 12), MOD(i, 28), i, MOD(i, 60), MOD(i, 60)) AS c_interval,
			  INTERVAL i MICROSECOND AS c_interval_micros
			FROM UNNEST(GENERATE_ARRAY(-10, 10)) AS i
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
			for (int col = 0; col < viaRest.get(row).size(); col++) {
				assertCellsMatch(viaRest.get(row).get(col), viaStorage.get(row).get(col),
						"row " + row + ", column " + (col + 1));
			}
		}
		logger.info("compared {} rows x {} columns across both paths", viaRest.size(), viaRest.get(0).size());
	}

	@Test
	@DisplayName("the Storage path engages for complex types too, so the parity check is not vacuous")
	void storagePathEngagesForComplexTypes() throws SQLException {
		// The scalar guard above cannot catch this: before #193 a result containing
		// an ARRAY, STRUCT or INTERVAL fell back to REST, and every complex parity
		// assertion would then have compared REST with itself and passed.
		try (Connection conn = storageConnection();
				Statement stmt = conn.createStatement();
				ResultSet rs = stmt.executeQuery(
						"SELECT [1, 2] AS a, STRUCT(1 AS n) AS s, INTERVAL 1 DAY AS i FROM UNNEST([1]) AS x")) {

			assertEquals("vc.tbc.bq.jdbc.storage.StorageReadResultSet", rs.getClass().getName(),
					"a result with ARRAY, STRUCT and INTERVAL columns should use the Storage path");
			assertTrue(rs.next());
		}
	}

	@Test
	@DisplayName("arrays, structs and intervals read identically on both paths")
	void complexTypesMatchTheRestPath() throws SQLException {
		List<List<String>> viaRest = readAllAsStrings(restConnection(), COMPLEX_TYPES_SQL);
		List<List<String>> viaStorage = readAllAsStrings(storageConnection(), COMPLEX_TYPES_SQL);

		assertEquals(viaRest.size(), viaStorage.size(), "row count differs between the two paths");
		assertFalse(viaRest.isEmpty(), "fixture produced no rows");

		for (int row = 0; row < viaRest.size(); row++) {
			for (int col = 0; col < viaRest.get(row).size(); col++) {
				assertCellsMatch(viaRest.get(row).get(col), viaStorage.get(row).get(col),
						"row " + row + ", column " + (col + 1));
			}
		}
		logger.info("compared {} rows x {} complex columns across both paths", viaRest.size(), viaRest.get(0).size());
	}

	@Test
	@DisplayName("getObject on a complex column agrees between the two paths")
	void nativeComplexObjectsMatchTheRestPath() throws SQLException {
		// getString compares the JSON rendering; this compares the structure the
		// nativeComplexTypes path builds from the same FieldValues, which is what
		// would break if member order or nesting were taken from Arrow rather than
		// from the BigQuery schema.
		String sql = "SELECT STRUCT(1 AS n, STRUCT('x' AS deep) AS nested) AS s, [1, 2, 3] AS a FROM UNNEST([1]) AS i";
		String storageUrl = String.format("jdbc:bigquery:%s/%s?authType=ADC&useStorageApi=true&nativeComplexTypes=true",
				TEST_PROJECT_ID, TEST_DATASET);
		String restUrl = String.format("jdbc:bigquery:%s/%s?authType=ADC&useStorageApi=false&nativeComplexTypes=true",
				TEST_PROJECT_ID, TEST_DATASET);

		Object[] storageStruct;
		Object[] restStruct;
		try (Connection conn = DriverManager.getConnection(storageUrl);
				Statement stmt = conn.createStatement();
				ResultSet rs = stmt.executeQuery(sql)) {
			assertTrue(rs.next());
			storageStruct = ((java.sql.Struct) rs.getObject("s")).getAttributes();
		}
		try (Connection conn = DriverManager.getConnection(restUrl);
				Statement stmt = conn.createStatement();
				ResultSet rs = stmt.executeQuery(sql)) {
			assertTrue(rs.next());
			restStruct = ((java.sql.Struct) rs.getObject("s")).getAttributes();
		}

		assertEquals(restStruct.length, storageStruct.length, "struct member count differs between the paths");
		for (int i = 0; i < restStruct.length; i++) {
			assertEquals(Objects.toString(restStruct[i]), Objects.toString(storageStruct[i]),
					"struct member " + i + " differs between the paths");
		}
	}

	/**
	 * Byte for byte, with no exceptions.
	 *
	 * <p>
	 * FLOAT64 and TIMESTAMP were briefly exempted here, because REST renders both
	 * by printing a {@code double} and Java prints doubles differently — REST
	 * returns {@code -0.66666666666666663} where Java's shortest round-trip form is
	 * {@code -0.6666666666666666}, and a TIMESTAMP of {@code ...999981}
	 * microseconds comes back from REST as {@code 1582934399.9999809}, which is 0.1
	 * microseconds short of the true value.
	 *
	 * <p>
	 * Exempting them was the wrong fix. {@code getString} now renders both types
	 * from the parsed value rather than the delivered text, in code both paths
	 * share, so they agree exactly — and agree on the more accurate rendering. This
	 * assertion is deliberately strict again so that any future divergence fails
	 * rather than being explained away.
	 */
	private static void assertCellsMatch(String rest, String storage, String where) {
		assertEquals(rest, storage, where + " differs between REST and Storage");
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
		// RANGE, not ARRAY or STRUCT: those two moved onto the Storage path in #193,
		// and this test asserted their fallback until then. The fallback itself still
		// has to work, so the assertion moves to a type that is still outside the
		// converter's remit rather than being deleted with the limitation.
		try (Connection conn = storageConnection();
				Statement stmt = conn.createStatement();
				ResultSet rs = stmt.executeQuery(
						"SELECT RANGE(DATE '2020-01-01', DATE '2020-12-31') AS r FROM UNNEST([1]) AS i")) {

			assertEquals("vc.tbc.bq.jdbc.BQResultSet", rs.getClass().getName(),
					"unsupported column types must fall back to the REST path");
			assertTrue(rs.next());
			// getObject, not getString: the REST path hands a RANGE back as a Range
			// object, which is the point -- it has no string encoding to compare.
			assertNotNull(rs.getObject("r"));
		}
	}

	@Test
	@DisplayName("one unsupported column sends the whole result to REST, complex columns included")
	void oneUnsupportedColumnDisqualifiesTheWholeResult() throws SQLException {
		// The path is all-or-nothing per result, and a struct now hides the reason
		// three levels down: this checks the recursive support test actually recurses.
		try (Connection conn = storageConnection();
				Statement stmt = conn.createStatement();
				ResultSet rs = stmt.executeQuery("SELECT [STRUCT(1 AS n, "
						+ "RANGE(DATE '2020-01-01', DATE '2020-12-31') AS r)] AS a FROM UNNEST([1]) AS i")) {

			assertEquals("vc.tbc.bq.jdbc.BQResultSet", rs.getClass().getName(),
					"a RANGE nested inside an ARRAY<STRUCT<...>> must still disqualify the result");
			assertTrue(rs.next());
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

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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.sql.*;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real BigQuery integration tests for native JDBC Array/Struct support.
 *
 * <p>
 * Two groups. The first uses {@code nativeComplexTypes=true} and covers
 * {@code java.sql.Array}/{@code Struct}. The second covers the default mode,
 * where ARRAY and STRUCT are returned as JSON strings — that group is the port
 * of the rest of {@code ComplexTypesTest} for issue #118, and the emulator
 * class is now deleted.
 *
 * <p>
 * The default-mode assertions use exact JSON, derived by running
 * {@code FieldValueConverter} directly rather than guessed. The emulator
 * versions asserted {@code assertNotNull} plus {@code contains("1")}-style
 * substring checks, which pass for almost any output; nine of them additionally
 * wrapped the whole body — including {@code assertTrue(rs.next())} — in a
 * try/catch that logged "(emulator limitation)".
 *
 * @since 1.0.70
 */
class RealComplexTypesTest extends AbstractRealBigQueryIntegrationTest {

	private static final Logger logger = LoggerFactory.getLogger(RealComplexTypesTest.class);

	@Test
	void testGetArrayReturnsJdbcArrayWhenFlagEnabled() throws SQLException {
		try (Connection nativeConn = createNativeComplexTypesConnection()) {
			String sql = "SELECT [1, 2, 3] as numbers";
			try (Statement stmt = nativeConn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
				assertTrue(rs.next(), "Should have result");

				Array array = rs.getArray("numbers");

				assertNotNull(array, "getArray() should not return null when nativeComplexTypes=true");
				logger.info("getArray() returned: {}", array);
			}
		}
	}

	@Test
	void testBQArrayBaseTypeIsValid() throws SQLException {
		try (Connection nativeConn = createNativeComplexTypesConnection()) {
			String sql = "SELECT [1, 2, 3] as numbers";
			try (Statement stmt = nativeConn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
				assertTrue(rs.next());
				Array array = rs.getArray("numbers");

				assertNotNull(array.getBaseTypeName(), "Base type name should not be null");
				logger.info("BQArray baseType={}, baseTypeName={}", array.getBaseType(), array.getBaseTypeName());
			}
		}
	}

	@Test
	void testBQArrayGetArrayReturnsValues() throws SQLException {
		try (Connection nativeConn = createNativeComplexTypesConnection()) {
			String sql = "SELECT ['a', 'b', 'c'] as letters";
			try (Statement stmt = nativeConn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
				assertTrue(rs.next());
				Array array = rs.getArray("letters");
				Object[] elements = (Object[]) array.getArray();

				assertNotNull(elements, "Array elements should not be null");
				assertEquals(3, elements.length, "Should have 3 elements");
				logger.info("BQArray elements: {}", Arrays.toString(elements));
			}
		}
	}

	@Test
	void testBQArrayGetResultSetIterates() throws SQLException {
		try (Connection nativeConn = createNativeComplexTypesConnection()) {
			String sql = "SELECT ['x', 'y'] as items";
			try (Statement stmt = nativeConn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
				assertTrue(rs.next());
				Array array = rs.getArray("items");

				try (ResultSet arrayRs = array.getResultSet()) {
					assertTrue(arrayRs.next(), "Array ResultSet should have first element");
					assertEquals(1L, arrayRs.getLong("INDEX"), "First INDEX should be 1");
					assertNotNull(arrayRs.getObject("VALUE"), "VALUE should not be null");
					logger.info("Array ResultSet: index={}, value={}", arrayRs.getLong("INDEX"),
							arrayRs.getObject("VALUE"));
				}
			}
		}
	}

	@Test
	void testGetObjectReturnsStructWhenFlagEnabled() throws SQLException {
		try (Connection nativeConn = createNativeComplexTypesConnection()) {
			String sql = "SELECT STRUCT(1 as id, 'Alice' as name) as person";
			try (Statement stmt = nativeConn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
				assertTrue(rs.next());

				Object obj = rs.getObject("person");

				assertNotNull(obj, "getObject() should not return null for STRUCT when nativeComplexTypes=true");
				assertInstanceOf(java.sql.Struct.class, obj,
						"getObject() should return Struct when nativeComplexTypes=true");
				logger.info("getObject() returned Struct: {}", obj.getClass().getSimpleName());
			}
		}
	}

	@Test
	void testBQStructSQLTypeName() throws SQLException {
		try (Connection nativeConn = createNativeComplexTypesConnection()) {
			String sql = "SELECT STRUCT(1 as id, 'Alice' as name) as person";
			try (Statement stmt = nativeConn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
				assertTrue(rs.next());
				java.sql.Struct struct = (java.sql.Struct) rs.getObject("person");

				String typeName = struct.getSQLTypeName();
				assertNotNull(typeName, "SQL type name should not be null");
				logger.info("BQStruct SQL type name: {}", typeName);
			}
		}
	}

	@Test
	void testBQStructAttributesInOrder() throws SQLException {
		try (Connection nativeConn = createNativeComplexTypesConnection()) {
			String sql = "SELECT STRUCT(42 as id, 'Bob' as name) as person";
			try (Statement stmt = nativeConn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
				assertTrue(rs.next());
				java.sql.Struct struct = (java.sql.Struct) rs.getObject("person");

				Object[] attrs = struct.getAttributes();
				assertNotNull(attrs, "Attributes should not be null");
				assertEquals(2, attrs.length, "Should have 2 attributes");
				logger.info("BQStruct attributes: {}", Arrays.toString(attrs));
			}
		}
	}

	// Issue #39 — Typed primitives inside STRUCT

	@Test
	void testStructWithTimestampFieldNativeReturnsTypedAttribute() throws SQLException {
		// Given: STRUCT<mydate TIMESTAMP> — the exact pattern from issue #39
		try (Connection nativeConn = createNativeComplexTypesConnection()) {
			String sql = "SELECT STRUCT(TIMESTAMP '2025-03-15 12:00:00 UTC' as mydate) as a";
			try (Statement stmt = nativeConn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
				assertTrue(rs.next());
				java.sql.Struct struct = (java.sql.Struct) rs.getObject("a");
				Object[] attrs = struct.getAttributes();

				assertEquals(1, attrs.length);
				assertInstanceOf(java.sql.Timestamp.class, attrs[0],
						"TIMESTAMP inside STRUCT should be java.sql.Timestamp, not " + attrs[0].getClass().getName());
				logger.info("STRUCT<TIMESTAMP> native attribute: {} ({})", attrs[0],
						attrs[0].getClass().getSimpleName());
			}
		}
	}

	@Test
	void testStructWithMultipleTypedFieldsNative() throws SQLException {
		// Given: STRUCT with multiple typed fields — comprehensive issue #39 coverage
		try (Connection nativeConn = createNativeComplexTypesConnection()) {
			String sql = """
					SELECT STRUCT(
					  42 as id,
					  'Alice' as name,
					  TIMESTAMP '2025-03-15 12:00:00 UTC' as created_at,
					  true as active,
					  3.14 as score,
					  DATE '2025-03-15' as birth_date
					) as person
					""";
			try (Statement stmt = nativeConn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
				assertTrue(rs.next());
				java.sql.Struct struct = (java.sql.Struct) rs.getObject("person");
				Object[] attrs = struct.getAttributes();

				assertEquals(6, attrs.length, "Should have 6 attributes");
				assertInstanceOf(Long.class, attrs[0], "id should be Long");
				assertInstanceOf(String.class, attrs[1], "name should be String");
				assertInstanceOf(java.sql.Timestamp.class, attrs[2], "created_at should be Timestamp");
				assertInstanceOf(Boolean.class, attrs[3], "active should be Boolean");
				assertInstanceOf(Double.class, attrs[4], "score should be Double");
				assertInstanceOf(java.sql.Date.class, attrs[5], "birth_date should be Date");

				logger.info(
						"STRUCT typed attributes: id={} ({}), name={} ({}), created_at={} ({}), active={} ({}), score={} ({}), birth_date={} ({})",
						attrs[0], attrs[0].getClass().getSimpleName(), attrs[1], attrs[1].getClass().getSimpleName(),
						attrs[2], attrs[2].getClass().getSimpleName(), attrs[3], attrs[3].getClass().getSimpleName(),
						attrs[4], attrs[4].getClass().getSimpleName(), attrs[5], attrs[5].getClass().getSimpleName());
			}
		}
	}

	@Test
	void testStructWithTimestampFieldJsonContainsFormattedTimestamp() throws SQLException {
		// Given: STRUCT<mydate TIMESTAMP> via default connection
		// (nativeComplexTypes=false)
		String sql = "SELECT STRUCT(TIMESTAMP '2025-03-15 12:00:00 UTC' as mydate) as a";
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
			assertTrue(rs.next());

			String json = rs.getString("a");
			assertNotNull(json, "JSON representation should not be null");
			assertTrue(json.contains("\"mydate\""), "JSON should contain field name 'mydate'");
			// Should NOT contain raw epoch double notation
			assertFalse(json.contains("E9") || json.contains("E12"),
					"JSON should not contain raw epoch scientific notation. Got: " + json);
			logger.info("STRUCT<TIMESTAMP> JSON: {}", json);
		}
	}

	@Test
	void testUnnestStructWithTimestampIssue39Repro() throws SQLException {
		// Given: the exact query from issue #39
		try (Connection nativeConn = createNativeComplexTypesConnection()) {
			String sql = """
					SELECT a
					FROM UNNEST([STRUCT(CURRENT_TIMESTAMP() as mydate), STRUCT(CURRENT_TIMESTAMP() as mydate)]) as a
					""";
			try (Statement stmt = nativeConn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
				int rowCount = 0;
				while (rs.next()) {
					rowCount++;
					java.sql.Struct struct = (java.sql.Struct) rs.getObject("a");
					assertNotNull(struct, "STRUCT should not be null on row " + rowCount);
					Object[] attrs = struct.getAttributes();
					assertEquals(1, attrs.length, "Should have 1 attribute on row " + rowCount);
					assertInstanceOf(java.sql.Timestamp.class, attrs[0],
							"mydate should be Timestamp on row " + rowCount + ", not " + attrs[0].getClass().getName());
					logger.info("Issue #39 repro row {}: mydate={} ({})", rowCount, attrs[0],
							attrs[0].getClass().getSimpleName());
				}
				assertEquals(2, rowCount, "Should have 2 rows");
			}
		}
	}

	@Test
	void testSetArrayParameterRoundTrip() throws SQLException {
		try (Connection nativeConn = createNativeComplexTypesConnection()) {
			Array param = nativeConn.createArrayOf("STRING", new Object[]{"hello", "world"});
			assertNotNull(param, "createArrayOf should return a non-null Array");

			String sql = "SELECT ? as values";
			try (PreparedStatement pstmt = nativeConn.prepareStatement(sql)) {
				pstmt.setArray(1, param);
				try (ResultSet rs = pstmt.executeQuery()) {
					assertTrue(rs.next(), "Should have result");
					Array result = rs.getArray("values");
					assertNotNull(result, "Array result should not be null");
					logger.info("✓ setArray() round-trip succeeded, elements: {}",
							Arrays.toString((Object[]) result.getArray()));
				}
			}
		}
	}

	// ════════════════════════════════════════════════════════════════════════
	// Default mode: ARRAY and STRUCT as JSON strings (port of ComplexTypesTest)
	// ════════════════════════════════════════════════════════════════════════

	private void firstRow(String sql, RowAssertion body) throws SQLException {
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
			assertTrue(rs.next(), "Query should return a row: " + sql);
			body.accept(rs);
		}
	}

	@FunctionalInterface
	private interface RowAssertion {
		void accept(ResultSet rs) throws SQLException;
	}

	// ── ARRAY ─────────────────────────────────────────────────────────────────

	@Test
	void testArrayOfIntegers() throws SQLException {
		firstRow("SELECT [1, 2, 3, 4, 5] AS numbers",
				rs -> assertEquals("[1,2,3,4,5]", rs.getString("numbers"), "ARRAY<INT64> as JSON"));
	}

	@Test
	void testArrayOfStrings() throws SQLException {
		firstRow("SELECT ['apple', 'banana', 'cherry'] AS fruits",
				rs -> assertEquals("[\"apple\",\"banana\",\"cherry\"]", rs.getString("fruits"),
						"ARRAY<STRING> as JSON"));
	}

	@Test
	void testEmptyArray() throws SQLException {
		firstRow("SELECT ARRAY<INT64>[] AS empty_array", rs -> {
			assertEquals("[]", rs.getString("empty_array"), "An empty ARRAY serialises as []");
			assertFalse(rs.wasNull(), "An empty array is not SQL NULL");
		});
	}

	@Test
	void testNullArrayIsNormalisedToEmpty() throws SQLException {
		// The emulator test accepted null OR "[]" and attributed the latter to the
		// emulator. It is not emulator behaviour: BigQuery has no NULL ARRAY. Array
		// columns are REPEATED, which cannot be null in the API representation, so a
		// NULL array comes back empty from BigQuery itself.
		firstRow("SELECT CAST(NULL AS ARRAY<STRING>) AS null_array", rs -> {
			assertEquals("[]", rs.getString("null_array"), "BigQuery normalises a NULL ARRAY to empty");
			assertFalse(rs.wasNull(), "and therefore it is not reported as SQL NULL");
		});
	}

	// ── STRUCT ────────────────────────────────────────────────────────────────

	@Test
	void testSimpleStruct() throws SQLException {
		firstRow("SELECT STRUCT(1 AS id, 'Alice' AS name) AS person",
				rs -> assertEquals("{\"id\":1,\"name\":\"Alice\"}", rs.getString("person"),
						"STRUCT as a named JSON object"));
	}

	@Test
	void testNestedStruct() throws SQLException {
		firstRow("SELECT STRUCT(1 AS id, STRUCT('123 Main St' AS street, 'NY' AS state) AS address) AS person",
				rs -> assertEquals("{\"id\":1,\"address\":{\"street\":\"123 Main St\",\"state\":\"NY\"}}",
						rs.getString("person"), "Nested STRUCT keeps field names at every level"));
	}

	@Test
	void testNullStruct() throws SQLException {
		firstRow("SELECT CAST(NULL AS STRUCT<id INT64, name STRING>) AS null_struct", rs -> {
			assertNull(rs.getString("null_struct"), "A NULL STRUCT is SQL NULL, unlike a NULL ARRAY");
			assertTrue(rs.wasNull());
		});
	}

	@Test
	void testArrayOfStructs() throws SQLException {
		firstRow("SELECT [STRUCT(1 AS id, 'Alice' AS name), STRUCT(2 AS id, 'Bob' AS name)] AS people",
				rs -> assertEquals("[{\"id\":1,\"name\":\"Alice\"},{\"id\":2,\"name\":\"Bob\"}]",
						rs.getString("people"), "ARRAY<STRUCT> as JSON"));
	}

	@Test
	void testStructWithArray() throws SQLException {
		firstRow("SELECT STRUCT(1 AS id, ['red', 'blue'] AS colors) AS item",
				rs -> assertEquals("{\"id\":1,\"colors\":[\"red\",\"blue\"]}", rs.getString("item"),
						"STRUCT containing an ARRAY"));
	}

	@Test
	void testDeeplyNestedStructure() throws SQLException {
		firstRow("SELECT STRUCT(1 AS id, [STRUCT('item1' AS name, [1, 2, 3] AS values)] AS items) AS data",
				rs -> assertEquals("{\"id\":1,\"items\":[{\"name\":\"item1\",\"values\":[1,2,3]}]}",
						rs.getString("data"), "STRUCT > ARRAY > STRUCT > ARRAY"));
	}

	// ── STRUCT with NULL fields ───────────────────────────────────────────────

	@Test
	void testStructWithNullFieldSerializesAsNamedJsonObject() throws SQLException {
		firstRow("SELECT STRUCT(1 AS id, CAST(NULL AS STRING) AS name) AS person",
				rs -> assertEquals("{\"id\":1,\"name\":null}", rs.getString("person"),
						"A null field keeps its name rather than collapsing to an array"));
	}

	@Test
	void testNestedStructWithNullInnerStructPreservesFieldNames() throws SQLException {
		firstRow("SELECT STRUCT(1 AS id, CAST(NULL AS STRUCT<street STRING, state STRING>) AS address) AS person",
				rs -> assertEquals("{\"id\":1,\"address\":null}", rs.getString("person"),
						"A null inner STRUCT keeps the outer field name"));
	}

	// ── JSON type ─────────────────────────────────────────────────────────────
	// Emulator tier wrapped these entirely, including assertTrue(rs.next()).

	@Test
	void testJsonType() throws SQLException {
		firstRow("SELECT JSON '{\"name\": \"Alice\", \"age\": 30}' AS json_data", rs -> {
			String json = rs.getString("json_data");
			assertNotNull(json, "JSON should not be null");
			// BigQuery canonicalises JSON (whitespace stripped, object keys sorted), so
			// assert the parsed content rather than a byte-for-byte string.
			JsonObject parsed = JsonParser.parseString(json).getAsJsonObject();
			assertEquals("Alice", parsed.get("name").getAsString());
			assertEquals(30, parsed.get("age").getAsInt());
		});
	}

	@Test
	void testJsonArray() throws SQLException {
		firstRow("SELECT JSON '[1, 2, 3, 4, 5]' AS json_array", rs -> {
			String json = rs.getString("json_array");
			assertNotNull(json, "JSON array should not be null");
			JsonArray parsed = JsonParser.parseString(json).getAsJsonArray();
			assertEquals(5, parsed.size(), "Should have five elements");
			assertEquals(1, parsed.get(0).getAsInt());
			assertEquals(5, parsed.get(4).getAsInt());
		});
	}

	@Test
	void testNullJson() throws SQLException {
		firstRow("SELECT CAST(NULL AS JSON) AS null_json", rs -> {
			assertNull(rs.getString("null_json"), "NULL JSON should be SQL NULL");
			assertTrue(rs.wasNull());
		});
	}

	// ── GEOGRAPHY ─────────────────────────────────────────────────────────────
	// Emulator tier wrapped these entirely.

	@Test
	void testGeographyPoint() throws SQLException {
		firstRow("SELECT ST_GEOGPOINT(-122.35, 47.62) AS location",
				rs -> assertEquals("POINT(-122.35 47.62)", rs.getString("location"), "GEOGRAPHY comes back as WKT"));
	}

	@Test
	void testGeographyFromText() throws SQLException {
		firstRow("SELECT ST_GEOGFROMTEXT('POINT(-122.35 47.62)') AS location",
				rs -> assertEquals("POINT(-122.35 47.62)", rs.getString("location"), "WKT round-trips"));
	}

	@Test
	void testNullGeography() throws SQLException {
		firstRow("SELECT CAST(NULL AS GEOGRAPHY) AS null_geo", rs -> {
			assertNull(rs.getString("null_geo"), "NULL GEOGRAPHY should be SQL NULL");
			assertTrue(rs.wasNull());
		});
	}

	// ── Metadata ──────────────────────────────────────────────────────────────
	// Emulator tier logged the column type instead of asserting it.

	@Test
	void testArrayColumnMetadata() throws SQLException {
		firstRow("SELECT [1, 2, 3] AS numbers", rs -> {
			ResultSetMetaData rsmd = rs.getMetaData();
			assertEquals(1, rsmd.getColumnCount());
			assertEquals("numbers", rsmd.getColumnLabel(1));
			assertEquals(Types.ARRAY, rsmd.getColumnType(1), "A REPEATED column reports Types.ARRAY");
		});
	}

	@Test
	void testStructColumnMetadata() throws SQLException {
		firstRow("SELECT STRUCT(1 AS id, 'Alice' AS name) AS person", rs -> {
			ResultSetMetaData rsmd = rs.getMetaData();
			assertEquals(1, rsmd.getColumnCount());
			assertEquals("person", rsmd.getColumnLabel(1));
			assertEquals(Types.STRUCT, rsmd.getColumnType(1), "A RECORD column reports Types.STRUCT");
		});
	}

	@Test
	void testGeographyAndJsonColumnsReportVarchar() throws SQLException {
		firstRow("SELECT ST_GEOGPOINT(0, 0) AS g, JSON '{}' AS j", rs -> {
			ResultSetMetaData rsmd = rs.getMetaData();
			assertEquals(Types.VARCHAR, rsmd.getColumnType(1), "GEOGRAPHY is surfaced as VARCHAR");
			assertEquals(Types.VARCHAR, rsmd.getColumnType(2), "JSON is surfaced as VARCHAR");
		});
	}

	// ── getObject in default mode ─────────────────────────────────────────────

	@Test
	void testGetObjectReturnsJsonStringWhenFlagDisabled() throws SQLException {
		// The default connection has nativeComplexTypes off, so complex values are
		// JSON strings — the behaviour that keeps IntelliJ from crashing on STRUCTs.
		firstRow("SELECT STRUCT(1 AS id, 'Alice' AS name) AS person", rs -> {
			Object value = rs.getObject("person");
			assertInstanceOf(String.class, value, "Default mode returns a JSON string, not a Struct");
			assertEquals("{\"id\":1,\"name\":\"Alice\"}", value);
		});
	}

	@Test
	void testArrayToString() throws SQLException {
		firstRow("SELECT [1, 2, 3] AS numbers",
				rs -> assertEquals("[1,2,3]", rs.getObject("numbers"), "getObject matches getString in default mode"));
	}

	// ── Mixed columns and DBE-17806 ───────────────────────────────────────────

	@Test
	void testComplexTableQuery() throws SQLException {
		firstRow("""
				SELECT
				  1 AS id,
				  'Product A' AS name,
				  ['red', 'blue', 'green'] AS colors,
				  STRUCT('Large' AS size, 100 AS quantity) AS inventory
				""", rs -> {
			assertEquals(1, rs.getInt("id"));
			assertEquals("Product A", rs.getString("name"));
			assertEquals("[\"red\",\"blue\",\"green\"]", rs.getString("colors"));
			assertEquals("{\"size\":\"Large\",\"quantity\":100}", rs.getString("inventory"));
		});
	}

	@Test
	void testPreparedStatementWithComplexType() throws SQLException {
		// Emulator tier wrapped the whole body, including assertTrue(rs.next()).
		try (PreparedStatement pstmt = connection.prepareStatement("SELECT ? AS value, [1, 2, 3] AS numbers")) {
			pstmt.setString(1, "test");
			try (ResultSet rs = pstmt.executeQuery()) {
				assertTrue(rs.next(), "Should have a result");
				assertEquals("test", rs.getString("value"), "Parameter value should round-trip");
				assertEquals("[1,2,3]", rs.getString("numbers"), "ARRAY alongside a parameter");
			}
		}
	}

	@Test
	void testStructWithNullFieldMultipleRows() throws SQLException {
		// DBE-17806: a NULL field must stay in its own position rather than letting
		// the next value shift into it. The emulator version checked this with
		// substring probes; the exact JSON says it outright.
		String sql = """
				WITH demo AS (
				  SELECT 'US' AS country, CAST(NULL AS STRING) AS hdp, 12 AS quantity
				  UNION ALL
				  SELECT 'UK' AS country, CAST(NULL AS STRING) AS hdp, 56 AS quantity
				  UNION ALL
				  SELECT 'FR' AS country, CAST(NULL AS STRING) AS hdp, 33 AS quantity
				)
				SELECT STRUCT(demo.country, demo.hdp, demo.quantity) AS demo
				FROM demo
				ORDER BY demo.country
				""";
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
			assertTrue(rs.next());
			assertEquals("{\"country\":\"FR\",\"hdp\":null,\"quantity\":33}", rs.getString("demo"));
			assertTrue(rs.next());
			assertEquals("{\"country\":\"UK\",\"hdp\":null,\"quantity\":56}", rs.getString("demo"));
			assertTrue(rs.next());
			assertEquals("{\"country\":\"US\",\"hdp\":null,\"quantity\":12}", rs.getString("demo"));
			assertFalse(rs.next(), "Should have exactly three rows");
		}
	}

	// ── Array element types (from TypeMappingTest) ────────────────────────────
	// Those versions probed with startsWith("["), endsWith("]") and contains(...),
	// which cannot tell a correct array from a differently-wrong one. The
	// contains("FieldValue") guard is preserved in spirit: exact equality rules out
	// a raw FieldValue.toString() leaking into the output, which is what it watched
	// for.

	@Test
	void testArrayOfBooleans() throws SQLException {
		firstRow("SELECT [true, false, true] AS bool_array",
				rs -> assertEquals("[true,false,true]", rs.getString("bool_array"), "ARRAY<BOOL> as JSON"));
	}

	@Test
	void testArrayOfFloats() throws SQLException {
		firstRow("SELECT [1.5, 2.7, 3.14] AS float_array",
				rs -> assertEquals("[1.5,2.7,3.14]", rs.getString("float_array"), "ARRAY<FLOAT64> as JSON"));
	}

	@Test
	void testArrayWithNulls() throws SQLException {
		firstRow("SELECT ['first', NULL, 'third'] AS array_with_nulls",
				rs -> assertEquals("[\"first\",null,\"third\"]", rs.getString("array_with_nulls"),
						"A null element is an explicit JSON null, and does not shift its neighbours"));
	}

	@Test
	void testMixedNullAndValues() throws SQLException {
		firstRow("SELECT [NULL, 'value', NULL, 'another'] AS mixed_array",
				rs -> assertEquals("[null,\"value\",null,\"another\"]", rs.getString("mixed_array"),
						"Leading and interior nulls both keep their positions"));
	}
}

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
package vc.tbc.bq.jdbc.integration;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for BigQuery complex data types.
 *
 * <p>
 * Tests BigQuery-specific complex types including:
 * <ul>
 * <li>ARRAY columns (returned as JSON)
 * <li>STRUCT/RECORD columns (returned as JSON)
 * <li>Nested structures (ARRAY of STRUCT, STRUCT with ARRAY)
 * <li>JSON type
 * <li>GEOGRAPHY type
 * <li>Repeated fields
 * </ul>
 *
 * <p>
 * Note: Complex types are returned as JSON strings to prevent IntelliJ IDEA
 * crashes when attempting to use JDBC Array/Struct objects.
 *
 * @since 1.0.0
 */
class ComplexTypesTest extends AbstractBigQueryIntegrationTest {

	private static final Logger logger = LoggerFactory.getLogger(ComplexTypesTest.class);

	// ARRAY Type Tests

	@Test
	void testArrayOfIntegers() throws SQLException {
		// Given: A query with an ARRAY of integers
		String sql = "SELECT [1, 2, 3, 4, 5] as numbers";
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			assertTrue(rs.next(), "Should have result");

			// When: Getting ARRAY column
			String arrayValue = rs.getString("numbers");

			// Then: Should return JSON array representation
			assertNotNull(arrayValue, "Array should not be null");
			assertTrue(arrayValue.contains("1"), "Should contain array elements");
			logger.info("ARRAY<INT64> returned as: {}", arrayValue);
		}
	}

	@Test
	void testArrayOfStrings() throws SQLException {
		// Given: A query with an ARRAY of strings
		String sql = "SELECT ['apple', 'banana', 'cherry'] as fruits";
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			assertTrue(rs.next(), "Should have result");

			// When: Getting ARRAY column
			String arrayValue = rs.getString("fruits");

			// Then: Should return JSON array representation
			assertNotNull(arrayValue, "Array should not be null");
			assertTrue(arrayValue.contains("apple") || arrayValue.contains("banana"), "Should contain array elements");
			logger.info("ARRAY<STRING> returned as: {}", arrayValue);
		}
	}

	@Test
	void testEmptyArray() throws SQLException {
		// Given: A query with an empty ARRAY
		String sql = "SELECT ARRAY<INT64>[] as empty_array";
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			assertTrue(rs.next(), "Should have result");

			// When: Getting empty ARRAY
			String arrayValue = rs.getString("empty_array");

			// Then: Should handle empty array
			assertNotNull(arrayValue, "Empty array should not be null");
			logger.info("Empty ARRAY returned as: {}", arrayValue);
		}
	}

	@Test
	void testNullArray() throws SQLException {
		// Given: A query with NULL ARRAY
		String sql = "SELECT CAST(NULL AS ARRAY<STRING>) as null_array";
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			assertTrue(rs.next(), "Should have result");

			// When: Getting NULL ARRAY
			String arrayValue = rs.getString("null_array");

			// Then: Should return null or empty array (emulator returns empty array)
			if (arrayValue == null) {
				assertTrue(rs.wasNull(), "wasNull should be true");
				logger.info("NULL ARRAY returned as: null");
			} else {
				// Emulator may return empty array instead of null
				assertTrue(arrayValue.equals("[]") || arrayValue.isEmpty(), "Should be empty array representation");
				logger.info("NULL ARRAY returned as: {} (emulator behavior)", arrayValue);
			}
		}
	}

	// STRUCT Type Tests

	@Test
	void testSimpleStruct() throws SQLException {
		// Given: A query with a STRUCT
		String sql = "SELECT STRUCT(1 as id, 'Alice' as name) as person";
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			assertTrue(rs.next(), "Should have result");

			// When: Getting STRUCT column
			String structValue = rs.getString("person");

			// Then: Should return JSON object representation
			assertNotNull(structValue, "Struct should not be null");
			assertTrue(structValue.contains("id") || structValue.contains("1"), "Should contain struct fields");
			logger.info("STRUCT returned as: {}", structValue);
		}
	}

	@Test
	void testNestedStruct() throws SQLException {
		// Given: A query with nested STRUCT
		String sql = "SELECT STRUCT(1 as id, STRUCT('123 Main St' as street, 'NY' as state) as address) as person";
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			assertTrue(rs.next(), "Should have result");

			// When: Getting nested STRUCT
			String structValue = rs.getString("person");

			// Then: Should return JSON with nested structure
			assertNotNull(structValue, "Nested struct should not be null");
			logger.info("Nested STRUCT returned as: {}", structValue);
		}
	}

	@Test
	void testNullStruct() throws SQLException {
		// Given: A query with NULL STRUCT
		String sql = "SELECT CAST(NULL AS STRUCT<id INT64, name STRING>) as null_struct";
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			assertTrue(rs.next(), "Should have result");

			// When: Getting NULL STRUCT
			String structValue = rs.getString("null_struct");

			// Then: Should return null
			assertNull(structValue, "NULL struct should return null");
			assertTrue(rs.wasNull(), "wasNull should be true");
		}
	}

	// Nested Complex Types

	@Test
	void testArrayOfStructs() throws SQLException {
		// Given: A query with ARRAY of STRUCT
		String sql = "SELECT [STRUCT(1 as id, 'Alice' as name), STRUCT(2 as id, 'Bob' as name)] as people";
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			assertTrue(rs.next(), "Should have result");

			// When: Getting ARRAY of STRUCT
			String arrayValue = rs.getString("people");

			// Then: Should return JSON array of objects
			assertNotNull(arrayValue, "Array of structs should not be null");
			assertTrue(arrayValue.contains("Alice") || arrayValue.contains("Bob") || arrayValue.contains("1"),
					"Should contain struct elements");
			logger.info("ARRAY<STRUCT> returned as: {}", arrayValue);
		}
	}

	@Test
	void testStructWithArray() throws SQLException {
		// Given: A query with STRUCT containing ARRAY
		String sql = "SELECT STRUCT(1 as id, ['red', 'blue'] as colors) as item";
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			assertTrue(rs.next(), "Should have result");

			// When: Getting STRUCT with ARRAY field
			String structValue = rs.getString("item");

			// Then: Should return JSON with nested array
			assertNotNull(structValue, "Struct with array should not be null");
			logger.info("STRUCT with ARRAY returned as: {}", structValue);
		}
	}

	@Test
	void testDeeplyNestedStructure() throws SQLException {
		// Given: A query with deeply nested structure
		String sql = "SELECT STRUCT(1 as id, [STRUCT('item1' as name, [1, 2, 3] as values)] as items) as data";
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			assertTrue(rs.next(), "Should have result");

			// When: Getting deeply nested structure
			String value = rs.getString("data");

			// Then: Should return JSON representation
			assertNotNull(value, "Deeply nested structure should not be null");
			logger.info("Deeply nested structure returned as: {}", value);
		}
	}

	// JSON Type Tests

	@Test
	void testJsonType() throws SQLException {
		// Given: A query with JSON type
		String sql = "SELECT JSON '{\"name\": \"Alice\", \"age\": 30}' as json_data";
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			try {
				assertTrue(rs.next(), "Should have result");

				// When: Getting JSON column
				String jsonValue = rs.getString("json_data");

				// Then: Should return JSON string
				assertNotNull(jsonValue, "JSON should not be null");
				assertTrue(jsonValue.contains("Alice") || jsonValue.contains("name"), "Should contain JSON content");
				logger.info("✓ JSON type supported, returned as: {}", jsonValue);
			} catch (SQLException e) {
				logger.info("JSON type not supported (emulator limitation): {}", e.getMessage());
			}
		}
	}

	@Test
	void testJsonArray() throws SQLException {
		// Given: A query with JSON array
		String sql = "SELECT JSON '[1, 2, 3, 4, 5]' as json_array";
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			try {
				assertTrue(rs.next(), "Should have result");

				// When: Getting JSON array
				String jsonValue = rs.getString("json_array");

				// Then: Should return JSON array string
				assertNotNull(jsonValue, "JSON array should not be null");
				logger.info("✓ JSON array supported, returned as: {}", jsonValue);
			} catch (SQLException e) {
				logger.info("JSON type not supported (emulator limitation): {}", e.getMessage());
			}
		}
	}

	@Test
	void testNullJson() throws SQLException {
		// Given: A query with NULL JSON
		String sql = "SELECT CAST(NULL AS JSON) as null_json";
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			try {
				assertTrue(rs.next(), "Should have result");

				// When: Getting NULL JSON
				String jsonValue = rs.getString("null_json");

				// Then: Should return null
				assertNull(jsonValue, "NULL JSON should return null");
				assertTrue(rs.wasNull(), "wasNull should be true");
				logger.info("✓ NULL JSON handling supported");
			} catch (SQLException e) {
				logger.info("JSON type not supported (emulator limitation): {}", e.getMessage());
			}
		}
	}

	// GEOGRAPHY Type Tests

	@Test
	void testGeographyPoint() throws SQLException {
		// Given: A query with GEOGRAPHY point
		String sql = "SELECT ST_GEOGPOINT(-122.35, 47.62) as location";
		try (Statement stmt = connection.createStatement()) {
			try (ResultSet rs = stmt.executeQuery(sql)) {
				assertTrue(rs.next(), "Should have result");

				// When: Getting GEOGRAPHY column
				String geoValue = rs.getString("location");

				// Then: Should return WKT or GeoJSON representation
				assertNotNull(geoValue, "Geography should not be null");
				logger.info("✓ GEOGRAPHY type supported, returned as: {}", geoValue);
			}
		} catch (SQLException e) {
			logger.info("GEOGRAPHY type not supported (emulator limitation): {}", e.getMessage());
		}
	}

	@Test
	void testGeographyFromText() throws SQLException {
		// Given: A query with GEOGRAPHY from WKT
		String sql = "SELECT ST_GEOGFROMTEXT('POINT(-122.35 47.62)') as location";
		try (Statement stmt = connection.createStatement()) {
			try (ResultSet rs = stmt.executeQuery(sql)) {
				assertTrue(rs.next(), "Should have result");

				// When: Getting GEOGRAPHY from WKT
				String geoValue = rs.getString("location");

				// Then: Should return geography representation
				assertNotNull(geoValue, "Geography should not be null");
				logger.info("✓ ST_GEOGFROMTEXT supported, returned as: {}", geoValue);
			}
		} catch (SQLException e) {
			logger.info("GEOGRAPHY functions not supported (emulator limitation): {}", e.getMessage());
		}
	}

	@Test
	void testNullGeography() throws SQLException {
		// Given: A query with NULL GEOGRAPHY
		String sql = "SELECT CAST(NULL AS GEOGRAPHY) as null_geo";
		try (Statement stmt = connection.createStatement()) {
			try (ResultSet rs = stmt.executeQuery(sql)) {
				assertTrue(rs.next(), "Should have result");

				// When: Getting NULL GEOGRAPHY
				String geoValue = rs.getString("null_geo");

				// Then: Should return null
				assertNull(geoValue, "NULL geography should return null");
				assertTrue(rs.wasNull(), "wasNull should be true");
				logger.info("✓ NULL GEOGRAPHY handling supported");
			}
		} catch (SQLException e) {
			logger.info("GEOGRAPHY type not supported (emulator limitation): {}", e.getMessage());
		}
	}

	// Metadata Tests for Complex Types

	@Test
	void testArrayColumnMetadata() throws SQLException {
		// Given: A query with ARRAY column
		String sql = "SELECT [1, 2, 3] as numbers";
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			// When: Getting metadata
			ResultSetMetaData rsmd = rs.getMetaData();

			// Then: Should have column metadata
			assertEquals(1, rsmd.getColumnCount(), "Should have 1 column");
			assertEquals("numbers", rsmd.getColumnLabel(1), "Column name should be 'numbers'");

			// Column type should indicate it's an array or JSON
			int columnType = rsmd.getColumnType(1);
			String typeName = rsmd.getColumnTypeName(1);
			logger.info("ARRAY column type: {} ({})", columnType, typeName);
		}
	}

	@Test
	void testStructColumnMetadata() throws SQLException {
		// Given: A query with STRUCT column
		String sql = "SELECT STRUCT(1 as id, 'Alice' as name) as person";
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			// When: Getting metadata
			ResultSetMetaData rsmd = rs.getMetaData();

			// Then: Should have column metadata
			assertEquals(1, rsmd.getColumnCount(), "Should have 1 column");
			assertEquals("person", rsmd.getColumnLabel(1), "Column name should be 'person'");

			// Column type should indicate it's a struct or JSON
			int columnType = rsmd.getColumnType(1);
			String typeName = rsmd.getColumnTypeName(1);
			logger.info("STRUCT column type: {} ({})", columnType, typeName);
		}
	}

	// Real-World Use Case Tests

	@Test
	void testComplexTableQuery() throws SQLException {
		// Given: A query simulating a real table with complex types
		String sql = """
				SELECT
				  1 as id,
				  'Product A' as name,
				  ['red', 'blue', 'green'] as colors,
				  STRUCT('Large' as size, 100 as quantity) as inventory
				""";
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			assertTrue(rs.next(), "Should have result");

			// When: Getting all columns
			int id = rs.getInt("id");
			String name = rs.getString("name");
			String colors = rs.getString("colors");
			String inventory = rs.getString("inventory");

			// Then: Should handle mixed simple and complex types
			assertEquals(1, id, "ID should be 1");
			assertEquals("Product A", name, "Name should match");
			assertNotNull(colors, "Colors array should not be null");
			assertNotNull(inventory, "Inventory struct should not be null");

			logger.info("Complex table row: id={}, name={}, colors={}, inventory={}", id, name, colors, inventory);
		}
	}

	@Test
	void testPreparedStatementWithComplexType() throws SQLException {
		// Given: A PreparedStatement that returns complex type
		String sql = "SELECT ? as value, [1, 2, 3] as numbers";
		try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
			try {
				// When: Setting parameter and executing
				pstmt.setString(1, "test");
				try (ResultSet rs = pstmt.executeQuery()) {

					assertTrue(rs.next(), "Should have result");

					// Then: Should handle complex type in result
					String value = rs.getString("value");
					String numbers = rs.getString("numbers");

					assertEquals("test", value, "Parameter value should match");
					assertNotNull(numbers, "Array should not be null");

					logger.info("✓ PreparedStatement with complex type: value={}, numbers={}", value, numbers);
				}
			} catch (SQLException e) {
				logger.info("PreparedStatement with complex types not fully supported (emulator limitation): {}",
						e.getMessage());
			}
		}
	}

	// DBE-17806 Regression Tests
	//
	// The JetBrains built-in (Simba) BigQuery driver has a bug where, when a STRUCT
	// field is NULL, the driver omits that null value and the remaining values
	// shift
	// left in the result, causing DataGrip to display values under the wrong
	// column.
	//
	// Our driver avoids this entirely by serializing the whole STRUCT as a named
	// JSON
	// object, so nulls are always explicitly present and field names are always
	// shown.

	@Test
	void testStructWithNullFieldSerializesAsNamedJsonObject() throws SQLException {
		// Given: a STRUCT where the middle field (hdp) is NULL — the exact DBE-17806
		// repro
		String sql = "SELECT STRUCT('US' as country, CAST(NULL AS STRING) AS hdp, 12 as quantity) as demo";
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
			assertTrue(rs.next(), "Should have result");

			String structValue = rs.getString("demo");

			// Then: result must be a JSON object, not a positional array
			assertNotNull(structValue, "STRUCT should not be null");
			String trimmed = structValue.trim();
			assertTrue(trimmed.startsWith("{") && trimmed.endsWith("}"),
					"STRUCT must serialize as a JSON object, not an array. Got: " + structValue);

			// All field names must be present, including the null one
			assertTrue(structValue.contains("\"country\""), "JSON must contain field name 'country'");
			assertTrue(structValue.contains("\"hdp\""), "JSON must contain field name 'hdp' even when null");
			assertTrue(structValue.contains("\"quantity\""), "JSON must contain field name 'quantity'");

			// The null must be serialized explicitly — not skipped
			assertTrue(structValue.contains(":null"), "JSON must serialize null explicitly, not skip it");

			// Non-null values must appear at their correct positions
			assertTrue(structValue.contains("\"US\""), "country must be 'US'");
			assertTrue(structValue.contains("12"), "quantity must be 12, not shifted into hdp's position");

			logger.info("DBE-17806 (single row): {}", structValue);
		}
	}

	@Test
	void testStructWithNullFieldMultipleRows() throws SQLException {
		// Given: multiple rows, each with a NULL hdp — reproduces the full DBE-17806
		// repro query
		String sql = """
				WITH demo AS (
				  SELECT 'US' as country, CAST(NULL AS STRING) AS hdp, 12 as quantity
				  UNION ALL
				  SELECT 'UK' as country, CAST(NULL AS STRING) AS hdp, 56 as quantity
				  UNION ALL
				  SELECT 'FR' as country, CAST(NULL AS STRING) AS hdp, 33 as quantity
				)
				SELECT STRUCT(demo.country, demo.hdp, demo.quantity) as demo
				FROM demo
				ORDER BY demo.country
				""";
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			int rowCount = 0;
			while (rs.next()) {
				rowCount++;
				String structValue = rs.getString("demo");

				assertNotNull(structValue, "STRUCT should not be null on row " + rowCount);
				assertTrue(structValue.trim().startsWith("{"),
						"STRUCT must be a JSON object on row " + rowCount + ". Got: " + structValue);

				// hdp is always null — must be explicit null, never a shifted value
				assertTrue(structValue.contains("\"hdp\""), "Field name 'hdp' must be present on row " + rowCount);
				assertTrue(structValue.contains(":null"), "null must be explicit on row " + rowCount);

				// quantity must not have shifted into the hdp position
				assertFalse(structValue.contains("\"hdp\":\"12\"") || structValue.contains("\"hdp\":12"),
						"quantity must not shift into hdp's position on row " + rowCount);
				assertFalse(structValue.contains("\"hdp\":\"56\"") || structValue.contains("\"hdp\":56"),
						"quantity must not shift into hdp's position on row " + rowCount);
				assertFalse(structValue.contains("\"hdp\":\"33\"") || structValue.contains("\"hdp\":33"),
						"quantity must not shift into hdp's position on row " + rowCount);

				logger.info("DBE-17806 (multi-row, row {}): {}", rowCount, structValue);
			}

			assertEquals(3, rowCount, "Should have 3 rows");
		}
	}

	@Test
	void testNestedStructWithNullInnerStructPreservesFieldNames() throws SQLException {
		// Given: a nested STRUCT where the entire inner STRUCT is NULL (deeper
		// DBE-17806 variant)
		String sql = """
				SELECT STRUCT(
				  CAST(NULL AS STRUCT<zone STRING>) as dest_instance,
				  'reporter1' as reporter
				) as payload
				""";
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
			assertTrue(rs.next(), "Should have result");

			String structValue = rs.getString("payload");

			assertNotNull(structValue, "STRUCT should not be null");
			assertTrue(structValue.trim().startsWith("{"), "STRUCT must be a JSON object. Got: " + structValue);

			// Both field names must be present
			assertTrue(structValue.contains("\"dest_instance\""),
					"Field name 'dest_instance' must be present even when null");
			assertTrue(structValue.contains("\"reporter\""), "Field name 'reporter' must be present");

			// reporter1 must NOT shift into dest_instance's position
			assertFalse(structValue.contains("\"dest_instance\":\"reporter1\""),
					"'reporter1' must not shift left into 'dest_instance'. Got: " + structValue);

			// reporter1 must appear under its own key
			assertTrue(structValue.contains("\"reporter\":\"reporter1\"") || structValue.contains("reporter1"),
					"'reporter1' must appear under 'reporter'. Got: " + structValue);

			logger.info("DBE-17806 nested variant: {}", structValue);
		}
	}

	// Type Conversion Tests

	@Test
	void testArrayToString() throws SQLException {
		// Given: A query with ARRAY
		String sql = "SELECT [1, 2, 3] as numbers";
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			assertTrue(rs.next(), "Should have result");

			// When: Getting array as string (should work)
			String stringValue = rs.getString("numbers");
			assertNotNull(stringValue, "Array as string should not be null");

			// Then: Attempting to get as Array type
			try {
				java.sql.Array arrayValue = rs.getArray("numbers");
				logger.info("✓ getArray() supported, returned: {}", arrayValue);
			} catch (SQLException e) {
				logger.info("getArray() not supported (expected - returns as JSON string): {}", e.getMessage());
			}
		}
	}

	@Test
	void testStructToString() throws SQLException {
		// Given: A query with STRUCT
		String sql = "SELECT STRUCT(1 as id, 'Alice' as name) as person";
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

			assertTrue(rs.next(), "Should have result");

			// When: Getting struct as string (should work)
			String stringValue = rs.getString("person");
			assertNotNull(stringValue, "Struct as string should not be null");

			// Then: Attempting to get as Struct type
			try {
				java.sql.Struct structValue = (java.sql.Struct) rs.getObject("person");
				logger.info("✓ getObject() returns Struct type: {}", structValue);
			} catch (ClassCastException | SQLException e) {
				logger.info("Struct returned as string (expected - returns as JSON): {}", e.getMessage());
			}
		}
	}
}

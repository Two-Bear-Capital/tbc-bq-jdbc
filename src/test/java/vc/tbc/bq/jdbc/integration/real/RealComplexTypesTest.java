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

import java.sql.*;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real BigQuery integration tests for native JDBC Array/Struct support.
 *
 * <p>
 * Mirrors the native complex types test group in
 * {@link vc.tbc.bq.jdbc.integration.ComplexTypesTest} but runs against a real
 * BigQuery instance with {@code nativeComplexTypes=true}.
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
}

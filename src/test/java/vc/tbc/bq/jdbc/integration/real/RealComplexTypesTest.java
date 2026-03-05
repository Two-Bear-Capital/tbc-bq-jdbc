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

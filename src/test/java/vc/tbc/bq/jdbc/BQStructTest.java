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
package vc.tbc.bq.jdbc;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BQStruct.
 *
 * @since 1.0.0
 */
class BQStructTest {

	@Test
	void testGetSQLTypeName() throws SQLException {
		// Given: A BQStruct with a type name
		BQStruct struct = new BQStruct("STRUCT<id INT64, name STRING>", new Object[]{1L, "Alice"});

		// When: Getting the SQL type name
		String typeName = struct.getSQLTypeName();

		// Then: Should return the type name
		assertEquals("STRUCT<id INT64, name STRING>", typeName);
	}

	@Test
	void testGetAttributesInOrder() throws SQLException {
		// Given: A BQStruct with ordered attributes
		Object[] attrs = {42L, "Bob", true};
		BQStruct struct = new BQStruct("STRUCT<id INT64, name STRING, active BOOL>", attrs);

		// When: Getting attributes
		Object[] result = struct.getAttributes();

		// Then: Should return attributes in schema order
		assertEquals(3, result.length);
		assertEquals(42L, result[0]);
		assertEquals("Bob", result[1]);
		assertEquals(true, result[2]);
	}

	@Test
	void testGetAttributesWithMapDelegatesToNoMap() throws SQLException {
		// Given: A BQStruct
		Object[] attrs = {"x", "y"};
		BQStruct struct = new BQStruct("STRUCT<a STRING, b STRING>", attrs);

		// When: Getting attributes with map
		Object[] withMap = struct.getAttributes(null);
		Object[] withoutMap = struct.getAttributes();

		// Then: Should return equivalent results
		assertArrayEquals(withoutMap, withMap);
	}

	@Test
	void testGetAttributesReturnsDefensiveCopy() throws SQLException {
		// Given: A BQStruct
		Object[] original = {"original"};
		BQStruct struct = new BQStruct("STRUCT<val STRING>", original);

		// When: Getting attributes and mutating the returned array
		Object[] result = struct.getAttributes();
		result[0] = "modified";

		// Then: Original struct attributes should be unchanged
		assertEquals("original", struct.getAttributes()[0]);
	}

	@Test
	void testNullAttributes() throws SQLException {
		// Given: A BQStruct created with null attributes
		BQStruct struct = new BQStruct("STRUCT", null);

		// When: Getting attributes
		Object[] result = struct.getAttributes();

		// Then: Should return empty array (not null)
		assertNotNull(result);
		assertEquals(0, result.length);
	}
}

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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BQArray.
 *
 * @since 1.0.0
 */
class BQArrayTest {

	@Test
	void testGetBaseType() throws SQLException {
		// Given: A BQArray with INT64 elements
		BQArray array = new BQArray(List.of(1L, 2L, 3L), Types.BIGINT, "INT64");

		// When: Getting base type
		int baseType = array.getBaseType();

		// Then: Should return BIGINT
		assertEquals(Types.BIGINT, baseType);
	}

	@Test
	void testGetBaseTypeName() throws SQLException {
		// Given: A BQArray with STRING elements
		BQArray array = new BQArray(List.of("a", "b"), Types.VARCHAR, "STRING");

		// When: Getting base type name
		String name = array.getBaseTypeName();

		// Then: Should return STRING
		assertEquals("STRING", name);
	}

	@Test
	void testGetArrayReturnsObjectArray() throws SQLException {
		// Given: A BQArray with known elements
		BQArray array = new BQArray(List.of("x", "y", "z"), Types.VARCHAR, "STRING");

		// When: Getting the array
		Object[] result = (Object[]) array.getArray();

		// Then: Should contain all elements
		assertEquals(3, result.length);
		assertEquals("x", result[0]);
		assertEquals("y", result[1]);
		assertEquals("z", result[2]);
	}

	@Test
	void testGetArrayWithMapDelegatesToGetArray() throws SQLException {
		// Given: A BQArray
		BQArray array = new BQArray(List.of(10L, 20L), Types.BIGINT, "INT64");

		// When: Getting array with map (BigQuery doesn't use type map)
		Object[] result = (Object[]) array.getArray(null);

		// Then: Should return same as getArray()
		assertArrayEquals((Object[]) array.getArray(), result);
	}

	@Test
	void testGetArraySlice() throws SQLException {
		// Given: A BQArray with 5 elements
		BQArray array = new BQArray(List.of(1L, 2L, 3L, 4L, 5L), Types.BIGINT, "INT64");

		// When: Getting a slice (index 2, count 3) — JDBC uses 1-based indexing
		Object[] slice = (Object[]) array.getArray(2, 3);

		// Then: Should return elements 2, 3, 4
		assertEquals(3, slice.length);
		assertEquals(2L, slice[0]);
		assertEquals(3L, slice[1]);
		assertEquals(4L, slice[2]);
	}

	@Test
	void testGetResultSetIteratesElements() throws SQLException {
		// Given: A BQArray with 3 elements
		BQArray array = new BQArray(List.of("a", "b", "c"), Types.VARCHAR, "STRING");

		// When: Getting the result set
		try (ResultSet rs = array.getResultSet()) {
			// Then: Should have 3 rows with 1-based index and correct values
			assertTrue(rs.next());
			assertEquals(1L, rs.getLong("INDEX"));
			assertEquals("a", rs.getObject("VALUE"));

			assertTrue(rs.next());
			assertEquals(2L, rs.getLong("INDEX"));
			assertEquals("b", rs.getObject("VALUE"));

			assertTrue(rs.next());
			assertEquals(3L, rs.getLong("INDEX"));
			assertEquals("c", rs.getObject("VALUE"));

			assertFalse(rs.next());
		}
	}

	@Test
	void testGetResultSetSlice() throws SQLException {
		// Given: A BQArray with 5 elements
		BQArray array = new BQArray(List.of(10L, 20L, 30L, 40L, 50L), Types.BIGINT, "INT64");

		// When: Getting result set for slice (index=3, count=2)
		try (ResultSet rs = array.getResultSet(3, 2)) {
			// Then: Should have 2 rows starting at original index 3
			assertTrue(rs.next());
			assertEquals(3L, rs.getLong("INDEX"));
			assertEquals(30L, rs.getObject("VALUE"));

			assertTrue(rs.next());
			assertEquals(4L, rs.getLong("INDEX"));
			assertEquals(40L, rs.getObject("VALUE"));

			assertFalse(rs.next());
		}
	}

	@Test
	void testFreeIsNoOp() throws SQLException {
		// Given: A BQArray
		BQArray array = new BQArray(List.of("test"), Types.VARCHAR, "STRING");

		// When: Calling free() - should not throw
		assertDoesNotThrow(array::free);
	}

	@Test
	void testEmptyArray() throws SQLException {
		// Given: An empty BQArray
		BQArray array = new BQArray(List.of(), Types.VARCHAR, "STRING");

		// When: Getting array contents
		Object[] result = (Object[]) array.getArray();

		// Then: Should be empty
		assertEquals(0, result.length);
	}

	@Test
	void testNullElementsList() throws SQLException {
		// Given: A BQArray created with null list
		BQArray array = new BQArray(null, Types.VARCHAR, "STRING");

		// When: Getting array
		Object[] result = (Object[]) array.getArray();

		// Then: Should be empty
		assertEquals(0, result.length);
	}
}

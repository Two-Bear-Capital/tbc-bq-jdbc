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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Struct;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Binding STRUCT values as query parameters (issue #198).
 *
 * <p>
 * STRUCT could be read but not written: {@code createStruct} threw, and
 * {@code setObject} had no case for {@link Struct}, so it fell through to
 * "Unsupported parameter type". BigQuery itself supports struct parameters —
 * {@code QueryParameterValue.struct} takes named fields — so the gap was in the
 * driver.
 *
 * <p>
 * Only real BigQuery can confirm a parameter binds: the client-side validator
 * accepts shapes the service then rejects, which is why the timestamp and time
 * cases here matter.
 *
 * @since 3.2.0
 */
class RealStructParameterTest extends AbstractRealBigQueryIntegrationTest {

	@Test
	void testMapBindsAsAStruct() throws SQLException {
		Map<String, Object> person = new LinkedHashMap<>();
		person.put("id", 42L);
		person.put("name", "Alice");

		try (PreparedStatement stmt = connection.prepareStatement("SELECT ?.id AS id, ?.name AS name")) {
			stmt.setObject(1, person);
			stmt.setObject(2, person);
			try (ResultSet rs = stmt.executeQuery()) {
				assertTrue(rs.next());
				assertEquals(42L, rs.getLong("id"));
				assertEquals("Alice", rs.getString("name"));
			}
		}
	}

	@Test
	void testCreateStructBindsAsAStruct() throws SQLException {
		Struct person = connection.createStruct("STRUCT<id INT64, name STRING>", new Object[]{7L, "Bob"});

		try (PreparedStatement stmt = connection.prepareStatement("SELECT ?.id AS id")) {
			stmt.setObject(1, person);
			try (ResultSet rs = stmt.executeQuery()) {
				assertTrue(rs.next());
				assertEquals(7L, rs.getLong("id"));
			}
		}
	}

	@Test
	void testAStructReadBackOutCanBeBoundAgain() throws SQLException {
		// The round trip the type-name parsing exists for: getObject() hands back a
		// BQStruct whose type name carries the field names, so it can go straight
		// back in as a parameter without the caller taking it apart.
		try (Connection nativeTypes = createNativeComplexTypesConnection()) {
			Struct read;
			try (PreparedStatement select = nativeTypes
					.prepareStatement("SELECT STRUCT(1 AS id, 'Carol' AS name) AS s");
					ResultSet rs = select.executeQuery()) {
				assertTrue(rs.next());
				Object value = rs.getObject("s");
				assertNotNull(value, "nativeComplexTypes=true should give a Struct");
				assertTrue(value instanceof Struct, "expected a Struct, got: " + value.getClass());
				read = (Struct) value;
			}

			try (PreparedStatement stmt = nativeTypes.prepareStatement("SELECT ?.name AS name")) {
				stmt.setObject(1, read);
				try (ResultSet rs = stmt.executeQuery()) {
					assertTrue(rs.next());
					assertEquals("Carol", rs.getString("name"));
				}
			}
		}
	}

	@Test
	void testNestedStructAndArrayFields() throws SQLException {
		Map<String, Object> address = new LinkedHashMap<>();
		address.put("city", "Boston");
		address.put("zip", "02110");

		Map<String, Object> person = new LinkedHashMap<>();
		person.put("name", "Dana");
		person.put("address", address);
		person.put("tags", List.of("a", "b", "c"));

		try (PreparedStatement stmt = connection
				.prepareStatement("SELECT ?.address.city AS city, ARRAY_LENGTH(?.tags) AS tag_count")) {
			stmt.setObject(1, person);
			stmt.setObject(2, person);
			try (ResultSet rs = stmt.executeQuery()) {
				assertTrue(rs.next());
				assertEquals("Boston", rs.getString("city"));
				assertEquals(3, rs.getInt("tag_count"));
			}
		}
	}

	@Test
	void testTemporalFieldsBindThroughTheTypedFactories() throws SQLException {
		// QueryParameterValue rejects an ISO-8601 timestamp string client-side and
		// wants exactly six fractional digits on a TIME. Binding these through the
		// same factories setTimestamp and setTime use is what makes them work inside
		// a struct too.
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("ts", java.sql.Timestamp.valueOf("2026-07-28 12:34:56.789"));
		row.put("d", java.sql.Date.valueOf("2026-07-28"));
		row.put("t", java.sql.Time.valueOf("12:34:56"));

		try (PreparedStatement stmt = connection.prepareStatement("SELECT ?.ts AS ts, ?.d AS d, ?.t AS t")) {
			stmt.setObject(1, row);
			stmt.setObject(2, row);
			stmt.setObject(3, row);
			try (ResultSet rs = stmt.executeQuery()) {
				assertTrue(rs.next());
				assertEquals(java.sql.Timestamp.valueOf("2026-07-28 12:34:56.789"), rs.getTimestamp("ts"));
				assertEquals("2026-07-28", rs.getDate("d").toString());
				assertEquals("12:34:56", rs.getTime("t").toString());
			}
		}
	}

	@Test
	void testDeclaredTypesLetANullFieldBind() throws SQLException {
		// BigQuery rejects an untyped null parameter. A map has nothing to infer a
		// null field's type from, which is the reason createStruct is worth having
		// beyond the JDBC-standard argument.
		Struct person = connection.createStruct("STRUCT<id INT64, name STRING>", new Object[]{9L, null});

		try (PreparedStatement stmt = connection.prepareStatement("SELECT ?.id AS id, ?.name AS name")) {
			stmt.setObject(1, person);
			stmt.setObject(2, person);
			try (ResultSet rs = stmt.executeQuery()) {
				assertTrue(rs.next());
				assertEquals(9L, rs.getLong("id"));
				assertEquals(null, rs.getString("name"));
			}
		}
	}

	@Test
	void testCreateStructRejectsATypeNameWithoutFieldNames() throws SQLException {
		// STRUCT<INT64, STRING> is legal BigQuery but names nothing, and BigQuery
		// struct parameters are named. Caught at createStruct rather than one
		// statement later at bind time.
		SQLException e = assertThrows(SQLException.class,
				() -> connection.createStruct("STRUCT<INT64, STRING>", new Object[]{1L, "x"}));
		assertTrue(e.getMessage().contains("names its fields"), "message should say what is wrong: " + e.getMessage());
	}

	@Test
	void testCreateStructRejectsAnAttributeCountMismatch() throws SQLException {
		SQLException e = assertThrows(SQLException.class,
				() -> connection.createStruct("STRUCT<id INT64, name STRING>", new Object[]{1L}));
		assertTrue(e.getMessage().contains("declaring 2"), "message should give both counts: " + e.getMessage());
	}

	@Test
	void testMapWithANonStringKeyIsRejected() throws SQLException {
		Map<Object, Object> bad = new LinkedHashMap<>();
		bad.put(1, "one");

		try (PreparedStatement stmt = connection.prepareStatement("SELECT ?")) {
			SQLException e = assertThrows(SQLException.class, () -> stmt.setObject(1, bad));
			assertTrue(e.getMessage().contains("non-String key"), "message should name the problem: " + e.getMessage());
		}
	}
}

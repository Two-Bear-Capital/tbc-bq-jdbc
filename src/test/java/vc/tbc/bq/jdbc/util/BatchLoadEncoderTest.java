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
package vc.tbc.bq.jdbc.util;

import com.google.cloud.bigquery.QueryParameterValue;
import com.google.cloud.bigquery.StandardSQLTypeName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchLoadEncoderTest {

	private static BatchLoadEncoder.LoadTarget target(String... columns) {
		return new BatchLoadEncoder.LoadTarget("d.t", List.of(columns));
	}

	private static QueryParameterValue str(String value) {
		return QueryParameterValue.of(value, StandardSQLTypeName.STRING);
	}

	@Test
	void testParsesTableAndColumns() {
		Optional<BatchLoadEncoder.LoadTarget> parsed = BatchLoadEncoder
				.parseTarget("INSERT INTO my_dataset.events (id, name) VALUES ", 2);

		assertTrue(parsed.isPresent());
		assertEquals("my_dataset.events", parsed.get().tablePath());
		assertEquals(List.of("id", "name"), parsed.get().columns());
	}

	@Test
	void testStripsBackticksFromEveryPathSegment() {
		Optional<BatchLoadEncoder.LoadTarget> parsed = BatchLoadEncoder
				.parseTarget("INSERT INTO `my-project`.`my_dataset`.`events` (`id`, `name`) VALUES ", 2);

		assertTrue(parsed.isPresent());
		assertEquals("my-project.my_dataset.events", parsed.get().tablePath());
		assertEquals(List.of("id", "name"), parsed.get().columns());
	}

	@Test
	void testAcceptsInsertWithoutIntoAndOddSpacing() {
		Optional<BatchLoadEncoder.LoadTarget> parsed = BatchLoadEncoder.parseTarget("  insert  t   (  a ,b  )  values ",
				2);

		assertTrue(parsed.isPresent());
		assertEquals("t", parsed.get().tablePath());
		assertEquals(List.of("a", "b"), parsed.get().columns());
	}

	@Test
	void testRejectsAnInsertWithNoColumnList() {
		// Without names, column order is the table's, which means reading its schema
		// first -- and misattributing every value if the table changed meanwhile. The
		// DML path already handles this shape correctly.
		assertTrue(BatchLoadEncoder.parseTarget("INSERT INTO d.t VALUES ", 2).isEmpty());
	}

	@Test
	void testRejectsAColumnCountThatDisagreesWithThePlaceholders() {
		// BigQuery would reject the statement anyway, but pairing values with the
		// wrong names would write bad data rather than fail.
		assertTrue(BatchLoadEncoder.parseTarget("INSERT INTO d.t (a, b) VALUES ", 3).isEmpty());
		assertTrue(BatchLoadEncoder.parseTarget("INSERT INTO d.t (a, b, c) VALUES ", 2).isEmpty());
	}

	@Test
	void testRejectsMalformedTargets() {
		assertTrue(BatchLoadEncoder.parseTarget("INSERT INTO d.t (a, ) VALUES ", 2).isEmpty(), "empty column name");
		assertTrue(BatchLoadEncoder.parseTarget("UPDATE d.t SET a = ", 1).isEmpty(), "not an INSERT");
		assertTrue(BatchLoadEncoder.parseTarget(null, 1).isEmpty());
	}

	@Test
	void testEncodesScalarsAsJsonStrings() {
		// Quoted rather than bare: BigQuery's JSON loader coerces quoted numbers to
		// the column type, and it sidesteps the FLOAT64 values that are not legal
		// JSON numbers at all.
		String json = BatchLoadEncoder.toJsonRow(target("i", "s"),
				List.of(QueryParameterValue.of(42L, StandardSQLTypeName.INT64), str("hello")));

		assertEquals("{\"i\":\"42\",\"s\":\"hello\"}", json);
	}

	@Test
	void testEncodesBooleansUnquoted() {
		String json = BatchLoadEncoder.toJsonRow(target("t", "f"),
				List.of(QueryParameterValue.of(true, StandardSQLTypeName.BOOL),
						QueryParameterValue.of(false, StandardSQLTypeName.BOOL)));

		assertEquals("{\"t\":true,\"f\":false}", json);
	}

	@Test
	void testEncodesNullAsJsonNull() {
		String json = BatchLoadEncoder.toJsonRow(target("s"),
				List.of(QueryParameterValue.of(null, StandardSQLTypeName.STRING)));

		assertEquals("{\"s\":null}", json);
	}

	@Test
	void testEscapesJsonSyntaxInValuesAndColumnNames() {
		// A quote or backslash in a value would otherwise end the string early and
		// corrupt every remaining field on the line.
		String json = BatchLoadEncoder.toJsonRow(target("od\"d"), List.of(str("a\"b\\c\nd\te")));

		assertEquals("{\"od\\\"d\":\"a\\\"b\\\\c\\nd\\te\"}", json);
	}

	@Test
	void testEscapesControlCharacters() {
		// A raw control character is invalid JSON, and a load job rejects the whole
		// file rather than the row.
		String json = BatchLoadEncoder.toJsonRow(target("s"), List.of(str("ab")));

		assertEquals("{\"s\":\"a\\u0001b\"}", json);
	}

	@Test
	void testTemporalAndBinaryValuesUseTheClientsCanonicalText() {
		// getValue() already renders these the way BigQuery's JSON loader expects,
		// which is the whole reason these types are loadable and JSON/GEOGRAPHY are
		// not: base64 for BYTES, a space-separated offset timestamp.
		String json = BatchLoadEncoder
				.toJsonRow(target("b", "d"),
						List.of(QueryParameterValue.of("hi".getBytes(java.nio.charset.StandardCharsets.UTF_8),
								StandardSQLTypeName.BYTES),
								QueryParameterValue.of("2026-07-28", StandardSQLTypeName.DATE)));

		assertEquals("{\"b\":\"aGk=\",\"d\":\"2026-07-28\"}", json);
	}

	@Test
	void testCanEncodeAcceptsScalarTypes() {
		assertTrue(BatchLoadEncoder
				.canEncode(List.of(List.of(QueryParameterValue.of(1L, StandardSQLTypeName.INT64), str("a")),
						List.of(QueryParameterValue.of(2L, StandardSQLTypeName.INT64), str("b")))));
	}

	@Test
	void testCanEncodeRejectsTypesWithNoSettledJsonForm() {
		// Encoding one of these wrongly writes bad data rather than failing, so they
		// send the batch back to the DML path instead.
		for (StandardSQLTypeName type : List.of(StandardSQLTypeName.JSON, StandardSQLTypeName.GEOGRAPHY,
				StandardSQLTypeName.INTERVAL, StandardSQLTypeName.STRUCT, StandardSQLTypeName.ARRAY)) {
			assertFalse(BatchLoadEncoder.canEncode(List.of(List.of(QueryParameterValue.of(null, type)))),
					type + " has no settled JSON load form and must fall back");
		}
	}

	@Test
	void testCanEncodeChecksEveryRowNotJustTheFirst() {
		// A nullable column can be bound with a different type on a later row, and
		// finding out mid-stream would leave a partly-written load job.
		List<List<QueryParameterValue>> sets = List.of(List.of(str("ok")),
				List.of(QueryParameterValue.of(null, StandardSQLTypeName.GEOGRAPHY)));

		assertFalse(BatchLoadEncoder.canEncode(sets));
	}
}

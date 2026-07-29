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
package vc.tbc.bq.jdbc.storage;

import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.Schema;
import com.google.cloud.bigquery.StandardSQLTypeName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Eligibility is the guard that keeps the converter from ever meeting a type it
 * cannot encode.
 *
 * <p>
 * If {@code isSupported} lets something through that {@code encode} then cannot
 * handle, the failure lands in the middle of iterating a ResultSet — after rows
 * have already been returned to the caller, which is the worst possible moment.
 * These tests pin the boundary so the two stay in agreement.
 */
class ArrowRowConverterTest {

	@ParameterizedTest
	@EnumSource(value = StandardSQLTypeName.class, names = {"INT64", "FLOAT64", "NUMERIC", "BIGNUMERIC", "BOOL",
			"STRING", "BYTES", "DATE", "TIME", "DATETIME", "TIMESTAMP", "GEOGRAPHY", "JSON", "INTERVAL"})
	@DisplayName("scalar types are eligible")
	void scalarTypesAreSupported(StandardSQLTypeName type) {
		assertTrue(ArrowRowConverter.isSupported(Schema.of(Field.of("c", type))),
				type + " is a flat scalar and should use the Storage Read API path");
	}

	@Test
	@DisplayName("RANGE is rejected")
	void rangeIsRejected() {
		// The one type left outside: RANGE has no FieldValue encoding the REST path
		// agrees on. INTERVAL used to be here on the belief that the Storage Read API
		// could not carry it; it can, as an IntervalMonthDayNanoVector (#193).
		assertFalse(ArrowRowConverter.isSupported(Schema.of(Field.of("c", StandardSQLTypeName.RANGE))),
				"RANGE must fall back to the REST path");
	}

	@Test
	@DisplayName("a repeated column is eligible when its element type is")
	void repeatedColumnsAreSupported() {
		Field repeated = Field.newBuilder("c", StandardSQLTypeName.INT64).setMode(Field.Mode.REPEATED).build();
		assertTrue(ArrowRowConverter.isSupported(Schema.of(repeated)),
				"an ARRAY<INT64> is encoded by recursing into the list vector");
	}

	@Test
	@DisplayName("a repeated column is rejected when its element type is not")
	void repeatedColumnsOfUnsupportedTypeAreRejected() {
		Field repeated = Field.newBuilder("c", StandardSQLTypeName.RANGE).setMode(Field.Mode.REPEATED).build();
		assertFalse(ArrowRowConverter.isSupported(Schema.of(repeated)),
				"an ARRAY<RANGE<...>> is no more encodable than a bare RANGE");
	}

	@Test
	@DisplayName("a record with supported subfields is eligible")
	void nestedRecordsAreSupported() {
		Field nested = Field.newBuilder("c", StandardSQLTypeName.STRUCT, Field.of("member", StandardSQLTypeName.INT64))
				.build();
		assertTrue(ArrowRowConverter.isSupported(Schema.of(nested)),
				"a STRUCT is encoded by recursing into the struct vector's children");
	}

	@Test
	@DisplayName("support is decided recursively, however deep the bad type is")
	void unsupportedTypeNestedDeeplyIsRejected() {
		// The check has to recurse or a RANGE three levels down reaches the encoder,
		// where it fails mid-ResultSet — after rows have gone back to the caller.
		Field deep = Field.newBuilder("inner", StandardSQLTypeName.STRUCT, Field.of("r", StandardSQLTypeName.RANGE))
				.build();
		Field outer = Field.newBuilder("c", StandardSQLTypeName.STRUCT, Field.of("n", StandardSQLTypeName.INT64), deep)
				.setMode(Field.Mode.REPEATED).build();

		assertFalse(ArrowRowConverter.isSupported(Schema.of(outer)),
				"ARRAY<STRUCT<n INT64, inner STRUCT<r RANGE>>> must fall back to REST");
	}

	@Test
	@DisplayName("one bad column disqualifies the whole result")
	void mixedSchemaIsRejected() {
		Schema mixed = Schema.of(Field.of("ok", StandardSQLTypeName.INT64), Field.of("bad", StandardSQLTypeName.RANGE));
		assertFalse(ArrowRowConverter.isSupported(mixed),
				"the path is all-or-nothing per result: one unsupported column sends the whole query to REST");
	}

	@Test
	@DisplayName("a missing or empty schema is not eligible")
	void absentSchemaIsRejected() {
		assertFalse(ArrowRowConverter.isSupported(null), "no schema means no way to convert rows");
		assertFalse(ArrowRowConverter.isSupported(Schema.of()), "an empty schema has nothing to read");
	}
}

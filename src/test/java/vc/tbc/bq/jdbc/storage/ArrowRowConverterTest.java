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
			"STRING", "BYTES", "DATE", "TIME", "DATETIME", "TIMESTAMP", "GEOGRAPHY", "JSON"})
	@DisplayName("scalar types are eligible")
	void scalarTypesAreSupported(StandardSQLTypeName type) {
		assertTrue(ArrowRowConverter.isSupported(Schema.of(Field.of("c", type))),
				type + " is a flat scalar and should use the Storage Read API path");
	}

	@ParameterizedTest
	@EnumSource(value = StandardSQLTypeName.class, names = {"INTERVAL", "RANGE"})
	@DisplayName("scalar types the encoder does not cover are rejected")
	void unsupportedScalarTypesAreRejected(StandardSQLTypeName type) {
		// INTERVAL is not supported by the Storage Read API at all, and RANGE has no
		// REST encoding this converter reproduces.
		assertFalse(ArrowRowConverter.isSupported(Schema.of(Field.of("c", type))),
				type + " must fall back to the REST path");
	}

	// STRUCT and ARRAY are not covered here because the BigQuery client refuses to
	// build a bare Field for them at all — a RECORD demands sub-fields. They are
	// covered by nestedRecordsAreRejected() and repeatedColumnsAreRejected(), which
	// construct them the way BigQuery actually reports them.

	@Test
	@DisplayName("a repeated column is rejected even when its element type is scalar")
	void repeatedColumnsAreRejected() {
		Field repeated = Field.newBuilder("c", StandardSQLTypeName.INT64).setMode(Field.Mode.REPEATED).build();
		assertFalse(ArrowRowConverter.isSupported(Schema.of(repeated)),
				"a REPEATED INT64 is an ARRAY<INT64>, which the converter does not encode");
	}

	@Test
	@DisplayName("a record with subfields is rejected")
	void nestedRecordsAreRejected() {
		Field nested = Field.newBuilder("c", StandardSQLTypeName.STRUCT, Field.of("inner", StandardSQLTypeName.INT64))
				.build();
		assertFalse(ArrowRowConverter.isSupported(Schema.of(nested)), "nested structs must fall back to the REST path");
	}

	@Test
	@DisplayName("one bad column disqualifies the whole result")
	void mixedSchemaIsRejected() {
		Schema mixed = Schema.of(Field.of("ok", StandardSQLTypeName.INT64),
				Field.of("bad", StandardSQLTypeName.INTERVAL));
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

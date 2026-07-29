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

import com.google.cloud.bigquery.StandardSQLTypeName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructTypeNamesTest {

	@Test
	void testParsesTheFormTypeMapperEmits() {
		// This is exactly what BQStruct.getSQLTypeName() returns for a struct column
		// read through getObject(), which is what makes a read struct bindable.
		List<StructTypeNames.StructField> fields = StructTypeNames.parse("STRUCT<id INT64, name STRING>");

		assertEquals(2, fields.size());
		assertEquals("id", fields.get(0).name());
		assertEquals(StandardSQLTypeName.INT64, fields.get(0).type());
		assertEquals("name", fields.get(1).name());
		assertEquals(StandardSQLTypeName.STRING, fields.get(1).type());
	}

	@Test
	void testPreservesDeclarationOrder() {
		// Attributes are positional, so a reordering would pair every value with the
		// wrong name.
		List<StructTypeNames.StructField> fields = StructTypeNames.parse("STRUCT<z STRING, a INT64, m BOOL>");

		assertEquals(List.of("z", "a", "m"), fields.stream().map(StructTypeNames.StructField::name).toList());
	}

	@Test
	void testNestedStructIsOneField() {
		// The comma inside the nested struct must not split it into two fields, which
		// would shift every attribute after it onto the wrong name.
		List<StructTypeNames.StructField> fields = StructTypeNames
				.parse("STRUCT<a INT64, b STRUCT<c STRING, d INT64>, e BOOL>");

		assertEquals(3, fields.size());
		assertEquals("b", fields.get(1).name());
		assertEquals("STRUCT<c STRING, d INT64>", fields.get(1).typeText());
		assertEquals(StandardSQLTypeName.STRUCT, fields.get(1).type());
		assertEquals("e", fields.get(2).name());
	}

	@Test
	void testArrayFieldKeepsItsElementTypeInTheText() {
		List<StructTypeNames.StructField> fields = StructTypeNames.parse("STRUCT<tags ARRAY<STRING>, n INT64>");

		assertEquals(2, fields.size());
		assertEquals(StandardSQLTypeName.ARRAY, fields.get(0).type());
		assertEquals("ARRAY<STRING>", fields.get(0).typeText());
	}

	@Test
	void testParameterisedTypeHeadIsUsed() {
		// NUMERIC(38, 9) is a NUMERIC; the precision is not part of the parameter
		// type, and the parenthesised comma must not split the field either.
		List<StructTypeNames.StructField> fields = StructTypeNames
				.parse("STRUCT<amount NUMERIC(38, 9), s STRING(255)>");

		assertEquals(2, fields.size());
		assertEquals(StandardSQLTypeName.NUMERIC, fields.get(0).type());
		assertEquals(StandardSQLTypeName.STRING, fields.get(1).type());
	}

	@Test
	void testUnnamedFieldsRejectTheWholeName() {
		// STRUCT<INT64, STRING> is legal BigQuery but leaves nothing to bind by name.
		// Rejecting all of it beats binding some fields correctly and some not.
		assertTrue(StructTypeNames.parse("STRUCT<INT64, STRING>").isEmpty());
		assertTrue(StructTypeNames.parse("STRUCT<a INT64, STRING>").isEmpty());
	}

	@Test
	void testNonStructNamesYieldNothing() {
		assertTrue(StructTypeNames.parse("INT64").isEmpty());
		assertTrue(StructTypeNames.parse("ARRAY<STRING>").isEmpty());
		assertTrue(StructTypeNames.parse(null).isEmpty());
		assertTrue(StructTypeNames.parse("").isEmpty());
	}

	@Test
	void testBareStructYieldsNothing() {
		// TypeMapper emits a bare "STRUCT" when the schema carried no sub-fields.
		assertTrue(StructTypeNames.parse("STRUCT").isEmpty());
		assertTrue(StructTypeNames.parse("STRUCT<>").isEmpty());
	}

	@Test
	void testUnknownTypeLeavesTheFieldUntyped() {
		// The name is still usable; only the null-typing hint is lost.
		List<StructTypeNames.StructField> fields = StructTypeNames.parse("STRUCT<x SOMETHING_NEW>");

		assertEquals(1, fields.size());
		assertEquals("x", fields.get(0).name());
		assertNull(fields.get(0).type());
		assertEquals("SOMETHING_NEW", fields.get(0).typeText());
	}

	@Test
	void testLowercaseAndExtraWhitespace() {
		List<StructTypeNames.StructField> fields = StructTypeNames.parse("struct<  id   int64 ,  name  string  >");

		assertEquals(2, fields.size());
		assertEquals("id", fields.get(0).name());
		assertEquals(StandardSQLTypeName.INT64, fields.get(0).type());
		assertEquals("name", fields.get(1).name());
	}
}

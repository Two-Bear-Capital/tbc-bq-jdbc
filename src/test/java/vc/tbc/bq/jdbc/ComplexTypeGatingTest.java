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

import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.FieldValue;
import com.google.cloud.bigquery.FieldValueList;
import com.google.cloud.bigquery.LegacySQLTypeName;
import org.junit.jupiter.api.Test;
import vc.tbc.bq.jdbc.util.FieldValueConverter;

import java.sql.Array;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Pins down what {@code nativeComplexTypes} does and does not govern.
 *
 * <p>
 * The gate used to be asymmetric: {@code getArray()} threw unless the property
 * was set, while {@code setArray()} and {@code Connection.createArrayOf()} were
 * never gated at all — so an application could write an array natively and then
 * fail to read it back without a property it had not needed for the write. The
 * property now governs exactly one thing: what {@code getObject()} returns.
 *
 * @since 3.0.0
 */
class ComplexTypeGatingTest {

	private static final Field ARRAY_FIELD = Field.newBuilder("nums", LegacySQLTypeName.INTEGER)
			.setMode(Field.Mode.REPEATED).build();

	/** A REPEATED INT64 value holding [1, 2]. */
	private static FieldValue repeatedValue() {
		return FieldValue.of(FieldValue.Attribute.REPEATED,
				FieldValueList.of(List.of(FieldValue.of(FieldValue.Attribute.PRIMITIVE, "1"),
						FieldValue.of(FieldValue.Attribute.PRIMITIVE, "2"))));
	}

	@Test
	void arrayConversionNeedsNoProperty() throws SQLException {
		// Given: an ARRAY column, with nativeComplexTypes left at its default
		Array array = FieldValueConverter.toBQArray(repeatedValue(), ARRAY_FIELD);

		// Then: a java.sql.Array is still produced. getArray() is an explicit typed
		// request, so it is not gated — matching setArray() and createArrayOf()
		assertNotNull(array);
		assertEquals(2, ((Object[]) array.getArray()).length);
	}

	@Test
	void getObjectStillReturnsJsonByDefault() {
		// Given: the default path, which is what a database IDE reads for every cell
		Object value = FieldValueConverter.toObject(repeatedValue(), ARRAY_FIELD);

		// Then: a JSON string, so result grids stay safe
		assertInstanceOf(String.class, value);
		assertEquals("[1,2]", value);
	}

	@Test
	void constructedArraysCarryTheirElementType() throws SQLException {
		// Given: an Array built the way Connection.createArrayOf() builds one
		Array array = new BQArray(List.of(1L, 2L), java.sql.Types.BIGINT, "INT64");

		// Then: it is usable without any property being set
		assertEquals(2, ((Object[]) array.getArray()).length);
		assertEquals(java.sql.Types.BIGINT, array.getBaseType());
	}
}

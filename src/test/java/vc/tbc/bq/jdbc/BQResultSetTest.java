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
import com.google.cloud.bigquery.Schema;
import com.google.cloud.bigquery.StandardSQLTypeName;
import com.google.cloud.bigquery.TableResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for BQResultSet type conversion behaviour.
 *
 * <p>
 * These tests focus on numeric getter methods (getInt, getLong, etc.) when the
 * underlying BigQuery column type does not match the requested Java type — for
 * example calling {@code getInt()} on a FLOAT64 column. BigQuery's {@code /}
 * operator always returns FLOAT64, so {@code SELECT 100/4} produces the string
 * {@code "25.0"} in the raw FieldValue, which previously caused a
 * {@link NumberFormatException} inside {@code FieldValue.getLongValue()}.
 *
 * @since 1.0.68
 */
@ExtendWith(MockitoExtension.class)
class BQResultSetTest {

	@Mock
	private TableResult mockTableResult;

	private BQResultSet createResultSet(Schema schema, FieldValueList row) throws SQLException {
		when(mockTableResult.getSchema()).thenReturn(schema);
		when(mockTableResult.iterateAll()).thenReturn(List.of(row));
		return new BQResultSet(null, mockTableResult);
	}

	// ── getInt ────────────────────────────────────────────────────────────────

	@Test
	void testGetIntByLabelOnFloat64WholeValueReturnsInt() throws SQLException {
		// Given: FLOAT64 column returning "25.0" (e.g. SELECT 100/4 in BigQuery)
		Schema schema = Schema.of(Field.of("quotient", StandardSQLTypeName.FLOAT64));
		FieldValueList row = FieldValueList.of(List.of(FieldValue.of(FieldValue.Attribute.PRIMITIVE, "25.0")),
				schema.getFields());

		BQResultSet rs = createResultSet(schema, row);
		assertTrue(rs.next());

		// When / Then: should not throw NumberFormatException
		assertEquals(25, rs.getInt("quotient"));
	}

	@Test
	void testGetIntByIndexOnFloat64WholeValueReturnsInt() throws SQLException {
		Schema schema = Schema.of(Field.of("quotient", StandardSQLTypeName.FLOAT64));
		FieldValueList row = FieldValueList.of(List.of(FieldValue.of(FieldValue.Attribute.PRIMITIVE, "25.0")),
				schema.getFields());

		BQResultSet rs = createResultSet(schema, row);
		assertTrue(rs.next());

		assertEquals(25, rs.getInt(1));
	}

	@Test
	void testGetIntByLabelOnFloat64DecimalTruncatesPerJdbcSpec() throws SQLException {
		// JDBC spec: getInt() on a fractional value truncates toward zero
		Schema schema = Schema.of(Field.of("val", StandardSQLTypeName.FLOAT64));
		FieldValueList row = FieldValueList.of(List.of(FieldValue.of(FieldValue.Attribute.PRIMITIVE, "25.7")),
				schema.getFields());

		BQResultSet rs = createResultSet(schema, row);
		assertTrue(rs.next());

		assertEquals(25, rs.getInt("val"));
	}

	@Test
	void testGetIntByLabelOnInt64ReturnsCorrectValue() throws SQLException {
		// Regression: INT64 columns must still work correctly
		Schema schema = Schema.of(Field.of("age", StandardSQLTypeName.INT64));
		FieldValueList row = FieldValueList.of(List.of(FieldValue.of(FieldValue.Attribute.PRIMITIVE, "30")),
				schema.getFields());

		BQResultSet rs = createResultSet(schema, row);
		assertTrue(rs.next());

		assertEquals(30, rs.getInt("age"));
	}

	@Test
	void testGetIntByLabelOnFloat64NullReturnsZero() throws SQLException {
		Schema schema = Schema.of(Field.of("val", StandardSQLTypeName.FLOAT64));
		FieldValueList row = FieldValueList.of(List.of(FieldValue.of(FieldValue.Attribute.PRIMITIVE, null)),
				schema.getFields());

		BQResultSet rs = createResultSet(schema, row);
		assertTrue(rs.next());

		assertEquals(0, rs.getInt("val"));
		assertTrue(rs.wasNull());
	}

	// ── getLong ───────────────────────────────────────────────────────────────

	@Test
	void testGetLongByLabelOnFloat64WholeValueReturnsLong() throws SQLException {
		Schema schema = Schema.of(Field.of("quotient", StandardSQLTypeName.FLOAT64));
		FieldValueList row = FieldValueList.of(List.of(FieldValue.of(FieldValue.Attribute.PRIMITIVE, "25.0")),
				schema.getFields());

		BQResultSet rs = createResultSet(schema, row);
		assertTrue(rs.next());

		assertEquals(25L, rs.getLong("quotient"));
	}

	@Test
	void testGetLongByIndexOnFloat64WholeValueReturnsLong() throws SQLException {
		Schema schema = Schema.of(Field.of("quotient", StandardSQLTypeName.FLOAT64));
		FieldValueList row = FieldValueList.of(List.of(FieldValue.of(FieldValue.Attribute.PRIMITIVE, "25.0")),
				schema.getFields());

		BQResultSet rs = createResultSet(schema, row);
		assertTrue(rs.next());

		assertEquals(25L, rs.getLong(1));
	}

	// ── getShort ──────────────────────────────────────────────────────────────

	@Test
	void testGetShortByLabelOnFloat64WholeValueReturnsShort() throws SQLException {
		Schema schema = Schema.of(Field.of("val", StandardSQLTypeName.FLOAT64));
		FieldValueList row = FieldValueList.of(List.of(FieldValue.of(FieldValue.Attribute.PRIMITIVE, "10.0")),
				schema.getFields());

		BQResultSet rs = createResultSet(schema, row);
		assertTrue(rs.next());

		assertEquals((short) 10, rs.getShort("val"));
	}

	// ── getByte ───────────────────────────────────────────────────────────────

	@Test
	void testGetByteByLabelOnFloat64WholeValueReturnsByte() throws SQLException {
		Schema schema = Schema.of(Field.of("val", StandardSQLTypeName.FLOAT64));
		FieldValueList row = FieldValueList.of(List.of(FieldValue.of(FieldValue.Attribute.PRIMITIVE, "10.0")),
				schema.getFields());

		BQResultSet rs = createResultSet(schema, row);
		assertTrue(rs.next());

		assertEquals((byte) 10, rs.getByte("val"));
	}
}
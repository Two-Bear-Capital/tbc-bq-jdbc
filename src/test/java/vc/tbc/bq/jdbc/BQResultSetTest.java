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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

	// ── null row iterator ─────────────────────────────────────────────────────

	/**
	 * A subclass that uses the null-TableResult constructor but forgets to override
	 * {@code next()} — which is exactly what {@code StorageReadResultSet} did.
	 */
	private static final class IterationlessResultSet extends BQResultSet {
		private IterationlessResultSet() {
			super(null, null, true);
		}
	}

	@Test
	void testNextOnNullRowIteratorThrowsSqlExceptionNotNpe() {
		// Given: a subclass constructed without a TableResult that does not
		// override next() — rowIterator is null
		BQResultSet rs = new IterationlessResultSet();

		// When / Then: next() must report a checked SQLException. Before this guard
		// it dereferenced the null iterator and threw NullPointerException, which
		// surfaced to callers as an unchecked crash mid-iteration.
		SQLException thrown = assertThrows(SQLException.class, rs::next);
		assertTrue(thrown.getMessage().contains("no row iterator"),
				"message should explain the cause, was: " + thrown.getMessage());
	}

	// ── Conversion failures report SQLException (#129) ────────────────────────
	// The BigQuery client signals an uninterpretable value with unchecked
	// exceptions. Leaking those meant a caller's catch (SQLException) never fired.
	// The old integration test caught `IllegalStateException | SQLException` by
	// name, which is how it stayed hidden.

	private BQResultSet stringColumnContaining(String raw) throws SQLException {
		Schema schema = Schema.of(Field.of("val", StandardSQLTypeName.STRING));
		FieldValueList row = FieldValueList.of(List.of(FieldValue.of(FieldValue.Attribute.PRIMITIVE, raw)),
				schema.getFields());
		BQResultSet rs = createResultSet(schema, row);
		assertTrue(rs.next());
		return rs;
	}

	@Test
	void testGetBigDecimalOnUnparseableTextThrowsSqlException() throws SQLException {
		BQResultSet rs = stringColumnContaining("hello");
		SQLException thrown = assertThrows(SQLException.class, () -> rs.getBigDecimal("val"));
		assertTrue(thrown.getMessage().contains("val"), "Message should name the column: " + thrown.getMessage());
	}

	@Test
	void testGetIntOnUnparseableTextThrowsSqlException() throws SQLException {
		BQResultSet rs = stringColumnContaining("hello");
		assertThrows(SQLException.class, () -> rs.getInt("val"));
	}

	@Test
	void testGetDoubleOnUnparseableTextThrowsSqlException() throws SQLException {
		BQResultSet rs = stringColumnContaining("hello");
		assertThrows(SQLException.class, () -> rs.getDouble("val"));
	}

	@Test
	void testGetBooleanOnUnparseableTextThrowsSqlException() throws SQLException {
		BQResultSet rs = stringColumnContaining("banana");
		assertThrows(SQLException.class, () -> rs.getBoolean("val"));
	}

	@Test
	void testGetBytesOnNonBytesColumnThrowsSqlException() throws SQLException {
		BQResultSet rs = stringColumnContaining("hello");
		assertThrows(SQLException.class, () -> rs.getBytes("val"));
	}

	// ── getBoolean coercion per the JDBC conversion table ─────────────────────

	private BQResultSet singleValue(StandardSQLTypeName type, String raw) throws SQLException {
		Schema schema = Schema.of(Field.of("flag", type));
		FieldValueList row = FieldValueList.of(List.of(FieldValue.of(FieldValue.Attribute.PRIMITIVE, raw)),
				schema.getFields());
		BQResultSet rs = createResultSet(schema, row);
		assertTrue(rs.next());
		return rs;
	}

	@ParameterizedTest
	@CsvSource({"INT64,1,true", "INT64,0,false", "INT64,-5,true", "FLOAT64,1.5,true", "FLOAT64,0.0,false",
			"NUMERIC,2.5,true", "NUMERIC,0,false", "BOOL,true,true", "BOOL,false,false", "STRING,true,true",
			"STRING,false,false", "STRING,1,true", "STRING,0,false"})
	void testGetBooleanCoercesPerJdbcConversionTable(String type, String raw, boolean expected) throws SQLException {
		BQResultSet rs = singleValue(StandardSQLTypeName.valueOf(type), raw);
		assertEquals(expected, rs.getBoolean("flag"), type + " '" + raw + "' should read as " + expected);
	}

	@Test
	void testGetBooleanOnNullReturnsFalse() throws SQLException {
		BQResultSet rs = singleValue(StandardSQLTypeName.INT64, null);
		assertFalse(rs.getBoolean("flag"), "SQL NULL reads as false per the JDBC spec");
		assertTrue(rs.wasNull());
	}
}

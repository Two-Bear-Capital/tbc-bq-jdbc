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
package com.tbc.bq.jdbc.metadata;

import com.google.cloud.bigquery.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * Tests for BQResultSetMetaData.
 *
 * @since 1.0.15
 */
@ExtendWith(MockitoExtension.class)
class BQResultSetMetaDataTest {

	@Mock
	private Schema schema;

	@Mock
	private FieldList fieldList;

	@Mock
	private Field field;

	@Mock
	private LegacySQLTypeName legacyType;

	private BQResultSetMetaData createMetaDataWithField(StandardSQLTypeName type, Field.Mode mode) {
		lenient().when(field.getName()).thenReturn("test_column");
		lenient().when(field.getType()).thenReturn(legacyType);
		lenient().when(legacyType.getStandardType()).thenReturn(type);
		lenient().when(field.getMode()).thenReturn(mode);
		lenient().when(schema.getFields()).thenReturn(fieldList);
		lenient().when(fieldList.size()).thenReturn(1);
		lenient().when(fieldList.getFirst()).thenReturn(field);
		lenient().when(fieldList.get(0)).thenReturn(field); // Add this for getField(1) to work
		return new BQResultSetMetaData(schema);
	}

	// Construction Tests

	@Test
	void testConstructionWithValidSchema() {
		// Given: Valid schema
		lenient().when(schema.getFields()).thenReturn(fieldList);

		// When: Creating metadata
		BQResultSetMetaData metaData = new BQResultSetMetaData(schema);

		// Then: Should be created successfully
		assertNotNull(metaData);
	}

	@Test
	void testConstructionWithNullSchemaThrows() {
		// Then: Null schema should throw NullPointerException
		assertThrows(NullPointerException.class, () -> new BQResultSetMetaData(null));
	}

	// Column Count Tests

	@Test
	void testGetColumnCount() throws SQLException {
		// Given: Schema with 3 fields
		lenient().when(schema.getFields()).thenReturn(fieldList);
		lenient().when(fieldList.size()).thenReturn(3);
		BQResultSetMetaData metaData = new BQResultSetMetaData(schema);

		// When: Getting column count
		int count = metaData.getColumnCount();

		// Then: Should return 3
		assertEquals(3, count);
	}

	@Test
	void testGetColumnCountWithNoColumns() throws SQLException {
		// Given: Schema with no fields
		lenient().when(schema.getFields()).thenReturn(fieldList);
		lenient().when(fieldList.size()).thenReturn(0);
		BQResultSetMetaData metaData = new BQResultSetMetaData(schema);

		// When: Getting column count
		int count = metaData.getColumnCount();

		// Then: Should return 0
		assertEquals(0, count);
	}

	// Column Properties Tests

	@Test
	void testIsAutoIncrement() throws SQLException {
		// Given: Metadata with a field
		BQResultSetMetaData metaData = createMetaDataWithField(StandardSQLTypeName.INT64, Field.Mode.NULLABLE);

		// When: Checking if auto-increment
		boolean autoIncrement = metaData.isAutoIncrement(1);

		// Then: Should always return false
		assertFalse(autoIncrement);
	}

	@Test
	void testIsCaseSensitiveForString() throws SQLException {
		// Given: STRING field
		BQResultSetMetaData metaData = createMetaDataWithField(StandardSQLTypeName.STRING, Field.Mode.NULLABLE);

		// When: Checking case sensitivity
		boolean caseSensitive = metaData.isCaseSensitive(1);

		// Then: Should return true
		assertTrue(caseSensitive);
	}

	@Test
	void testIsCaseSensitiveForBytes() throws SQLException {
		// Given: BYTES field
		BQResultSetMetaData metaData = createMetaDataWithField(StandardSQLTypeName.BYTES, Field.Mode.NULLABLE);

		// When: Checking case sensitivity
		boolean caseSensitive = metaData.isCaseSensitive(1);

		// Then: Should return true
		assertTrue(caseSensitive);
	}

	@Test
	void testIsCaseSensitiveForGeography() throws SQLException {
		// Given: GEOGRAPHY field
		BQResultSetMetaData metaData = createMetaDataWithField(StandardSQLTypeName.GEOGRAPHY, Field.Mode.NULLABLE);

		// When: Checking case sensitivity
		boolean caseSensitive = metaData.isCaseSensitive(1);

		// Then: Should return true
		assertTrue(caseSensitive);
	}

	@Test
	void testIsCaseSensitiveForJson() throws SQLException {
		// Given: JSON field
		BQResultSetMetaData metaData = createMetaDataWithField(StandardSQLTypeName.JSON, Field.Mode.NULLABLE);

		// When: Checking case sensitivity
		boolean caseSensitive = metaData.isCaseSensitive(1);

		// Then: Should return true
		assertTrue(caseSensitive);
	}

	@Test
	void testIsCaseSensitiveForNumeric() throws SQLException {
		// Given: NUMERIC field (not case-sensitive)
		BQResultSetMetaData metaData = createMetaDataWithField(StandardSQLTypeName.NUMERIC, Field.Mode.NULLABLE);

		// When: Checking case sensitivity
		boolean caseSensitive = metaData.isCaseSensitive(1);

		// Then: Should return false
		assertFalse(caseSensitive);
	}

	@Test
	void testIsSearchable() throws SQLException {
		// Given: Metadata with a field
		BQResultSetMetaData metaData = createMetaDataWithField(StandardSQLTypeName.STRING, Field.Mode.NULLABLE);

		// When: Checking if searchable
		boolean searchable = metaData.isSearchable(1);

		// Then: Should always return true
		assertTrue(searchable);
	}

	@Test
	void testIsCurrency() throws SQLException {
		// Given: Metadata with a field
		BQResultSetMetaData metaData = createMetaDataWithField(StandardSQLTypeName.NUMERIC, Field.Mode.NULLABLE);

		// When: Checking if currency
		boolean currency = metaData.isCurrency(1);

		// Then: Should always return false
		assertFalse(currency);
	}

	// Nullability Tests

	@Test
	void testIsNullableWithRequiredField() throws SQLException {
		// Given: REQUIRED field
		BQResultSetMetaData metaData = createMetaDataWithField(StandardSQLTypeName.STRING, Field.Mode.REQUIRED);

		// When: Checking nullability
		int nullable = metaData.isNullable(1);

		// Then: Should return columnNoNulls
		assertEquals(ResultSetMetaData.columnNoNulls, nullable);
	}

	@Test
	void testIsNullableWithNullableField() throws SQLException {
		// Given: NULLABLE field
		BQResultSetMetaData metaData = createMetaDataWithField(StandardSQLTypeName.STRING, Field.Mode.NULLABLE);

		// When: Checking nullability
		int nullable = metaData.isNullable(1);

		// Then: Should return columnNullable
		assertEquals(ResultSetMetaData.columnNullable, nullable);
	}

	@Test
	void testIsNullableWithRepeatedField() throws SQLException {
		// Given: REPEATED field
		BQResultSetMetaData metaData = createMetaDataWithField(StandardSQLTypeName.STRING, Field.Mode.REPEATED);

		// When: Checking nullability
		int nullable = metaData.isNullable(1);

		// Then: Should return columnNullable (not REQUIRED)
		assertEquals(ResultSetMetaData.columnNullable, nullable);
	}

	// Signed Tests

	@Test
	void testIsSignedForInt64() throws SQLException {
		// Given: INT64 field
		BQResultSetMetaData metaData = createMetaDataWithField(StandardSQLTypeName.INT64, Field.Mode.NULLABLE);

		// When: Checking if signed
		boolean signed = metaData.isSigned(1);

		// Then: Should return true
		assertTrue(signed);
	}

	@Test
	void testIsSignedForFloat64() throws SQLException {
		// Given: FLOAT64 field
		BQResultSetMetaData metaData = createMetaDataWithField(StandardSQLTypeName.FLOAT64, Field.Mode.NULLABLE);

		// When: Checking if signed
		boolean signed = metaData.isSigned(1);

		// Then: Should return true
		assertTrue(signed);
	}

	@Test
	void testIsSignedForNumeric() throws SQLException {
		// Given: NUMERIC field
		BQResultSetMetaData metaData = createMetaDataWithField(StandardSQLTypeName.NUMERIC, Field.Mode.NULLABLE);

		// When: Checking if signed
		boolean signed = metaData.isSigned(1);

		// Then: Should return true
		assertTrue(signed);
	}

	@Test
	void testIsSignedForBigNumeric() throws SQLException {
		// Given: BIGNUMERIC field
		BQResultSetMetaData metaData = createMetaDataWithField(StandardSQLTypeName.BIGNUMERIC, Field.Mode.NULLABLE);

		// When: Checking if signed
		boolean signed = metaData.isSigned(1);

		// Then: Should return true
		assertTrue(signed);
	}

	@Test
	void testIsSignedForString() throws SQLException {
		// Given: STRING field
		BQResultSetMetaData metaData = createMetaDataWithField(StandardSQLTypeName.STRING, Field.Mode.NULLABLE);

		// When: Checking if signed
		boolean signed = metaData.isSigned(1);

		// Then: Should return false
		assertFalse(signed);
	}

	// Display Size Tests

	@Test
	void testGetColumnDisplaySizeForBool() throws SQLException {
		// Given: BOOL field
		BQResultSetMetaData metaData = createMetaDataWithField(StandardSQLTypeName.BOOL, Field.Mode.NULLABLE);

		// When: Getting display size
		int size = metaData.getColumnDisplaySize(1);

		// Then: Should return 5 ("false")
		assertEquals(5, size);
	}

	@Test
	void testGetColumnDisplaySizeForInt64() throws SQLException {
		// Given: INT64 field
		BQResultSetMetaData metaData = createMetaDataWithField(StandardSQLTypeName.INT64, Field.Mode.NULLABLE);

		// When: Getting display size
		int size = metaData.getColumnDisplaySize(1);

		// Then: Should return 20
		assertEquals(20, size);
	}

	@Test
	void testGetColumnDisplaySizeForFloat64() throws SQLException {
		// Given: FLOAT64 field
		BQResultSetMetaData metaData = createMetaDataWithField(StandardSQLTypeName.FLOAT64, Field.Mode.NULLABLE);

		// When: Getting display size
		int size = metaData.getColumnDisplaySize(1);

		// Then: Should return 24
		assertEquals(24, size);
	}

	@Test
	void testGetColumnDisplaySizeForNumeric() throws SQLException {
		// Given: NUMERIC field
		BQResultSetMetaData metaData = createMetaDataWithField(StandardSQLTypeName.NUMERIC, Field.Mode.NULLABLE);

		// When: Getting display size
		int size = metaData.getColumnDisplaySize(1);

		// Then: Should return 47
		assertEquals(47, size);
	}

	@Test
	void testGetColumnDisplaySizeForBigNumeric() throws SQLException {
		// Given: BIGNUMERIC field
		BQResultSetMetaData metaData = createMetaDataWithField(StandardSQLTypeName.BIGNUMERIC, Field.Mode.NULLABLE);

		// When: Getting display size
		int size = metaData.getColumnDisplaySize(1);

		// Then: Should return 117
		assertEquals(117, size);
	}

	@Test
	void testGetColumnDisplaySizeForDate() throws SQLException {
		// Given: DATE field
		BQResultSetMetaData metaData = createMetaDataWithField(StandardSQLTypeName.DATE, Field.Mode.NULLABLE);

		// When: Getting display size
		int size = metaData.getColumnDisplaySize(1);

		// Then: Should return 10 (YYYY-MM-DD)
		assertEquals(10, size);
	}

	@Test
	void testGetColumnDisplaySizeForTime() throws SQLException {
		// Given: TIME field
		BQResultSetMetaData metaData = createMetaDataWithField(StandardSQLTypeName.TIME, Field.Mode.NULLABLE);

		// When: Getting display size
		int size = metaData.getColumnDisplaySize(1);

		// Then: Should return 15
		assertEquals(15, size);
	}

	@Test
	void testGetColumnDisplaySizeForDateTime() throws SQLException {
		// Given: DATETIME field
		BQResultSetMetaData metaData = createMetaDataWithField(StandardSQLTypeName.DATETIME, Field.Mode.NULLABLE);

		// When: Getting display size
		int size = metaData.getColumnDisplaySize(1);

		// Then: Should return 26
		assertEquals(26, size);
	}

	@Test
	void testGetColumnDisplaySizeForTimestamp() throws SQLException {
		// Given: TIMESTAMP field
		BQResultSetMetaData metaData = createMetaDataWithField(StandardSQLTypeName.TIMESTAMP, Field.Mode.NULLABLE);

		// When: Getting display size
		int size = metaData.getColumnDisplaySize(1);

		// Then: Should return 32
		assertEquals(32, size);
	}

	// Column Name Tests

	@Test
	void testGetColumnLabel() throws SQLException {
		// Given: Field with name
		BQResultSetMetaData metaData = createMetaDataWithField(StandardSQLTypeName.STRING, Field.Mode.NULLABLE);

		// When: Getting column label
		String label = metaData.getColumnLabel(1);

		// Then: Should return field name
		assertEquals("test_column", label);
	}

	@Test
	void testGetColumnName() throws SQLException {
		// Given: Field with name
		BQResultSetMetaData metaData = createMetaDataWithField(StandardSQLTypeName.STRING, Field.Mode.NULLABLE);

		// When: Getting column name
		String name = metaData.getColumnName(1);

		// Then: Should return field name
		assertEquals("test_column", name);
	}

	@Test
	void testGetSchemaName() throws SQLException {
		// Given: Metadata
		BQResultSetMetaData metaData = createMetaDataWithField(StandardSQLTypeName.STRING, Field.Mode.NULLABLE);

		// When: Getting schema name
		String schemaName = metaData.getSchemaName(1);

		// Then: Should return empty string
		assertEquals("", schemaName);
	}

	@Test
	void testGetTableName() throws SQLException {
		// Given: Metadata
		BQResultSetMetaData metaData = createMetaDataWithField(StandardSQLTypeName.STRING, Field.Mode.NULLABLE);

		// When: Getting table name
		String tableName = metaData.getTableName(1);

		// Then: Should return empty string
		assertEquals("", tableName);
	}

	@Test
	void testGetCatalogName() throws SQLException {
		// Given: Metadata
		BQResultSetMetaData metaData = createMetaDataWithField(StandardSQLTypeName.STRING, Field.Mode.NULLABLE);

		// When: Getting catalog name
		String catalogName = metaData.getCatalogName(1);

		// Then: Should return empty string
		assertEquals("", catalogName);
	}

	// Precision and Scale Tests

	@Test
	void testGetPrecisionForNumeric() throws SQLException {
		// Given: NUMERIC field
		BQResultSetMetaData metaData = createMetaDataWithField(StandardSQLTypeName.NUMERIC, Field.Mode.NULLABLE);

		// When: Getting precision
		int precision = metaData.getPrecision(1);

		// Then: Should return 38
		assertEquals(38, precision);
	}

	@Test
	void testGetPrecisionForBigNumeric() throws SQLException {
		// Given: BIGNUMERIC field
		BQResultSetMetaData metaData = createMetaDataWithField(StandardSQLTypeName.BIGNUMERIC, Field.Mode.NULLABLE);

		// When: Getting precision
		int precision = metaData.getPrecision(1);

		// Then: Should return 76
		assertEquals(76, precision);
	}

	@Test
	void testGetPrecisionForInt64() throws SQLException {
		// Given: INT64 field
		BQResultSetMetaData metaData = createMetaDataWithField(StandardSQLTypeName.INT64, Field.Mode.NULLABLE);

		// When: Getting precision
		int precision = metaData.getPrecision(1);

		// Then: Should return 19
		assertEquals(19, precision);
	}

	@Test
	void testGetPrecisionForFloat64() throws SQLException {
		// Given: FLOAT64 field
		BQResultSetMetaData metaData = createMetaDataWithField(StandardSQLTypeName.FLOAT64, Field.Mode.NULLABLE);

		// When: Getting precision
		int precision = metaData.getPrecision(1);

		// Then: Should return 15
		assertEquals(15, precision);
	}

	@Test
	void testGetPrecisionForString() throws SQLException {
		// Given: STRING field (no precision)
		BQResultSetMetaData metaData = createMetaDataWithField(StandardSQLTypeName.STRING, Field.Mode.NULLABLE);

		// When: Getting precision
		int precision = metaData.getPrecision(1);

		// Then: Should return 0
		assertEquals(0, precision);
	}

	// Type Information Tests

	@Test
	void testGetColumnTypeNameForStruct() throws SQLException {
		// Given: STRUCT field with subfields
		Field subField1 = mock(Field.class);
		Field subField2 = mock(Field.class);
		LegacySQLTypeName subType1 = mock(LegacySQLTypeName.class);
		LegacySQLTypeName subType2 = mock(LegacySQLTypeName.class);
		FieldList subFields = mock(FieldList.class);

		lenient().when(field.getName()).thenReturn("struct_col");
		lenient().when(field.getType()).thenReturn(legacyType);
		lenient().when(legacyType.getStandardType()).thenReturn(StandardSQLTypeName.STRUCT);
		lenient().when(field.getSubFields()).thenReturn(subFields);
		lenient().when(subFields.isEmpty()).thenReturn(false);
		lenient().when(subFields.size()).thenReturn(2);
		lenient().when(subFields.get(0)).thenReturn(subField1);
		lenient().when(subFields.get(1)).thenReturn(subField2);
		lenient().when(subField1.getName()).thenReturn("field1");
		lenient().when(subField1.getType()).thenReturn(subType1);
		lenient().when(subType1.getStandardType()).thenReturn(StandardSQLTypeName.INT64);
		lenient().when(subField2.getName()).thenReturn("field2");
		lenient().when(subField2.getType()).thenReturn(subType2);
		lenient().when(subType2.getStandardType()).thenReturn(StandardSQLTypeName.STRING);

		lenient().when(schema.getFields()).thenReturn(fieldList);
		lenient().when(fieldList.size()).thenReturn(1);
		lenient().when(fieldList.getFirst()).thenReturn(field);
		lenient().when(fieldList.get(0)).thenReturn(field);

		BQResultSetMetaData metaData = new BQResultSetMetaData(schema);

		// When: Getting type name
		String typeName = metaData.getColumnTypeName(1);

		// Then: Should return STRUCT definition
		assertEquals("STRUCT<field1 INT64, field2 STRING>", typeName);
	}

	@Test
	void testGetColumnTypeNameForArray() throws SQLException {
		// Given: ARRAY field
		Field elementField = mock(Field.class);
		LegacySQLTypeName elementType = mock(LegacySQLTypeName.class);
		FieldList subFields = mock(FieldList.class);

		lenient().when(field.getName()).thenReturn("array_col");
		lenient().when(field.getType()).thenReturn(legacyType);
		lenient().when(legacyType.getStandardType()).thenReturn(StandardSQLTypeName.ARRAY);
		lenient().when(field.getSubFields()).thenReturn(subFields);
		lenient().when(subFields.getFirst()).thenReturn(elementField);
		lenient().when(elementField.getType()).thenReturn(elementType);
		lenient().when(elementType.getStandardType()).thenReturn(StandardSQLTypeName.STRING);

		lenient().when(schema.getFields()).thenReturn(fieldList);
		lenient().when(fieldList.size()).thenReturn(1);
		lenient().when(fieldList.getFirst()).thenReturn(field);
		lenient().when(fieldList.get(0)).thenReturn(field);

		BQResultSetMetaData metaData = new BQResultSetMetaData(schema);

		// When: Getting type name
		String typeName = metaData.getColumnTypeName(1);

		// Then: Should return ARRAY definition
		assertEquals("ARRAY<STRING>", typeName);
	}

	@Test
	void testGetColumnTypeNameForSimpleType() throws SQLException {
		// Given: STRING field
		BQResultSetMetaData metaData = createMetaDataWithField(StandardSQLTypeName.STRING, Field.Mode.NULLABLE);

		// When: Getting type name
		String typeName = metaData.getColumnTypeName(1);

		// Then: Should return type name
		assertEquals("STRING", typeName);
	}

	// Read-Only Tests

	@Test
	void testIsReadOnly() throws SQLException {
		// Given: Metadata
		BQResultSetMetaData metaData = createMetaDataWithField(StandardSQLTypeName.STRING, Field.Mode.NULLABLE);

		// When: Checking if read-only
		boolean readOnly = metaData.isReadOnly(1);

		// Then: Should always return true
		assertTrue(readOnly);
	}

	@Test
	void testIsWritable() throws SQLException {
		// Given: Metadata
		BQResultSetMetaData metaData = createMetaDataWithField(StandardSQLTypeName.STRING, Field.Mode.NULLABLE);

		// When: Checking if writable
		boolean writable = metaData.isWritable(1);

		// Then: Should always return false
		assertFalse(writable);
	}

	@Test
	void testIsDefinitelyWritable() throws SQLException {
		// Given: Metadata
		BQResultSetMetaData metaData = createMetaDataWithField(StandardSQLTypeName.STRING, Field.Mode.NULLABLE);

		// When: Checking if definitely writable
		boolean definitelyWritable = metaData.isDefinitelyWritable(1);

		// Then: Should always return false
		assertFalse(definitelyWritable);
	}

	// Index Validation Tests

	@Test
	void testGetFieldWithInvalidIndexThrows() {
		// Given: Metadata with 1 field
		BQResultSetMetaData metaData = createMetaDataWithField(StandardSQLTypeName.STRING, Field.Mode.NULLABLE);

		// Then: Invalid index should throw SQLException
		assertThrows(SQLException.class, () -> metaData.getColumnName(0));
		assertThrows(SQLException.class, () -> metaData.getColumnName(2));
	}

	// Wrapper Tests

	@Test
	void testUnwrap() throws SQLException {
		// Given: Metadata
		BQResultSetMetaData metaData = createMetaDataWithField(StandardSQLTypeName.STRING, Field.Mode.NULLABLE);

		// When: Unwrapping to BQResultSetMetaData
		BQResultSetMetaData unwrapped = metaData.unwrap(BQResultSetMetaData.class);

		// Then: Should return same instance
		assertSame(metaData, unwrapped);
	}

	@Test
	void testIsWrapperFor() throws SQLException {
		// Given: Metadata
		BQResultSetMetaData metaData = createMetaDataWithField(StandardSQLTypeName.STRING, Field.Mode.NULLABLE);

		// Then: Should return true for compatible types
		assertTrue(metaData.isWrapperFor(BQResultSetMetaData.class));
		assertTrue(metaData.isWrapperFor(ResultSetMetaData.class));

		// And: Should return false for incompatible types
		assertFalse(metaData.isWrapperFor(String.class));
	}
}

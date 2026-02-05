/*
 * Copyright 2026 Two Bear Capital
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.twobearcapital.bigquery.jdbc;

import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.LegacySQLTypeName;
import com.google.cloud.bigquery.StandardSQLTypeName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.sql.Types;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;

/**
 * Tests for TypeMapper utility class.
 *
 * @since 1.0.15
 */
@ExtendWith(MockitoExtension.class)
class TypeMapperTest {

	@Mock
	private Field field;

	@Mock
	private LegacySQLTypeName legacyType;

	// toJdbcType(StandardSQLTypeName) Tests

	@Test
	void testToJdbcTypeWithString() {
		// When: Mapping STRING type
		int jdbcType = TypeMapper.toJdbcType(StandardSQLTypeName.STRING);

		// Then: Should map to VARCHAR
		assertEquals(Types.VARCHAR, jdbcType);
	}

	@Test
	void testToJdbcTypeWithBytes() {
		// When: Mapping BYTES type
		int jdbcType = TypeMapper.toJdbcType(StandardSQLTypeName.BYTES);

		// Then: Should map to VARBINARY
		assertEquals(Types.VARBINARY, jdbcType);
	}

	@Test
	void testToJdbcTypeWithInt64() {
		// When: Mapping INT64 type
		int jdbcType = TypeMapper.toJdbcType(StandardSQLTypeName.INT64);

		// Then: Should map to BIGINT
		assertEquals(Types.BIGINT, jdbcType);
	}

	@Test
	void testToJdbcTypeWithFloat64() {
		// When: Mapping FLOAT64 type
		int jdbcType = TypeMapper.toJdbcType(StandardSQLTypeName.FLOAT64);

		// Then: Should map to DOUBLE
		assertEquals(Types.DOUBLE, jdbcType);
	}

	@Test
	void testToJdbcTypeWithNumeric() {
		// When: Mapping NUMERIC type
		int jdbcType = TypeMapper.toJdbcType(StandardSQLTypeName.NUMERIC);

		// Then: Should map to NUMERIC
		assertEquals(Types.NUMERIC, jdbcType);
	}

	@Test
	void testToJdbcTypeWithBigNumeric() {
		// When: Mapping BIGNUMERIC type
		int jdbcType = TypeMapper.toJdbcType(StandardSQLTypeName.BIGNUMERIC);

		// Then: Should map to NUMERIC
		assertEquals(Types.NUMERIC, jdbcType);
	}

	@Test
	void testToJdbcTypeWithBool() {
		// When: Mapping BOOL type
		int jdbcType = TypeMapper.toJdbcType(StandardSQLTypeName.BOOL);

		// Then: Should map to BOOLEAN
		assertEquals(Types.BOOLEAN, jdbcType);
	}

	@Test
	void testToJdbcTypeWithTimestamp() {
		// When: Mapping TIMESTAMP type
		int jdbcType = TypeMapper.toJdbcType(StandardSQLTypeName.TIMESTAMP);

		// Then: Should map to TIMESTAMP
		assertEquals(Types.TIMESTAMP, jdbcType);
	}

	@Test
	void testToJdbcTypeWithDateTime() {
		// When: Mapping DATETIME type
		int jdbcType = TypeMapper.toJdbcType(StandardSQLTypeName.DATETIME);

		// Then: Should map to TIMESTAMP
		assertEquals(Types.TIMESTAMP, jdbcType);
	}

	@Test
	void testToJdbcTypeWithDate() {
		// When: Mapping DATE type
		int jdbcType = TypeMapper.toJdbcType(StandardSQLTypeName.DATE);

		// Then: Should map to DATE
		assertEquals(Types.DATE, jdbcType);
	}

	@Test
	void testToJdbcTypeWithTime() {
		// When: Mapping TIME type
		int jdbcType = TypeMapper.toJdbcType(StandardSQLTypeName.TIME);

		// Then: Should map to TIME
		assertEquals(Types.TIME, jdbcType);
	}

	@Test
	void testToJdbcTypeWithGeography() {
		// When: Mapping GEOGRAPHY type
		int jdbcType = TypeMapper.toJdbcType(StandardSQLTypeName.GEOGRAPHY);

		// Then: Should map to VARCHAR (represented as WKT)
		assertEquals(Types.VARCHAR, jdbcType);
	}

	@Test
	void testToJdbcTypeWithJson() {
		// When: Mapping JSON type
		int jdbcType = TypeMapper.toJdbcType(StandardSQLTypeName.JSON);

		// Then: Should map to VARCHAR (represented as JSON string)
		assertEquals(Types.VARCHAR, jdbcType);
	}

	@Test
	void testToJdbcTypeWithInterval() {
		// When: Mapping INTERVAL type
		int jdbcType = TypeMapper.toJdbcType(StandardSQLTypeName.INTERVAL);

		// Then: Should map to VARCHAR (represented as string)
		assertEquals(Types.VARCHAR, jdbcType);
	}

	@Test
	void testToJdbcTypeWithStruct() {
		// When: Mapping STRUCT type
		int jdbcType = TypeMapper.toJdbcType(StandardSQLTypeName.STRUCT);

		// Then: Should map to STRUCT
		assertEquals(Types.STRUCT, jdbcType);
	}

	@Test
	void testToJdbcTypeWithArray() {
		// When: Mapping ARRAY type
		int jdbcType = TypeMapper.toJdbcType(StandardSQLTypeName.ARRAY);

		// Then: Should map to ARRAY
		assertEquals(Types.ARRAY, jdbcType);
	}

	@Test
	void testToJdbcTypeWithNull() {
		// When: Mapping null type
		int jdbcType = TypeMapper.toJdbcType((StandardSQLTypeName) null);

		// Then: Should return OTHER
		assertEquals(Types.OTHER, jdbcType);
	}

	// toJdbcType(Field) Tests

	@Test
	void testToJdbcTypeWithFieldString() {
		// Given: A field with STRING type
		lenient().when(legacyType.getStandardType()).thenReturn(StandardSQLTypeName.STRING);
		lenient().when(field.getType()).thenReturn(legacyType);

		// When: Mapping field to JDBC type
		int jdbcType = TypeMapper.toJdbcType(field);

		// Then: Should map to VARCHAR
		assertEquals(Types.VARCHAR, jdbcType);
	}

	@Test
	void testToJdbcTypeWithFieldInt64() {
		// Given: A field with INT64 type
		lenient().when(legacyType.getStandardType()).thenReturn(StandardSQLTypeName.INT64);
		lenient().when(field.getType()).thenReturn(legacyType);

		// When: Mapping field to JDBC type
		int jdbcType = TypeMapper.toJdbcType(field);

		// Then: Should map to BIGINT
		assertEquals(Types.BIGINT, jdbcType);
	}

	@Test
	void testToJdbcTypeWithNullField() {
		// When: Mapping null field
		int jdbcType = TypeMapper.toJdbcType((Field) null);

		// Then: Should return OTHER
		assertEquals(Types.OTHER, jdbcType);
	}

	// toJavaClassName(StandardSQLTypeName) Tests

	@Test
	void testToJavaClassNameWithString() {
		// When: Mapping STRING type
		String className = TypeMapper.toJavaClassName(StandardSQLTypeName.STRING);

		// Then: Should return String class name
		assertEquals(String.class.getName(), className);
	}

	@Test
	void testToJavaClassNameWithBytes() {
		// When: Mapping BYTES type
		String className = TypeMapper.toJavaClassName(StandardSQLTypeName.BYTES);

		// Then: Should return byte array class name
		assertEquals(byte[].class.getName(), className);
	}

	@Test
	void testToJavaClassNameWithInt64() {
		// When: Mapping INT64 type
		String className = TypeMapper.toJavaClassName(StandardSQLTypeName.INT64);

		// Then: Should return Long class name
		assertEquals(Long.class.getName(), className);
	}

	@Test
	void testToJavaClassNameWithFloat64() {
		// When: Mapping FLOAT64 type
		String className = TypeMapper.toJavaClassName(StandardSQLTypeName.FLOAT64);

		// Then: Should return Double class name
		assertEquals(Double.class.getName(), className);
	}

	@Test
	void testToJavaClassNameWithNumeric() {
		// When: Mapping NUMERIC type
		String className = TypeMapper.toJavaClassName(StandardSQLTypeName.NUMERIC);

		// Then: Should return BigDecimal class name
		assertEquals(java.math.BigDecimal.class.getName(), className);
	}

	@Test
	void testToJavaClassNameWithBigNumeric() {
		// When: Mapping BIGNUMERIC type
		String className = TypeMapper.toJavaClassName(StandardSQLTypeName.BIGNUMERIC);

		// Then: Should return BigDecimal class name
		assertEquals(java.math.BigDecimal.class.getName(), className);
	}

	@Test
	void testToJavaClassNameWithBool() {
		// When: Mapping BOOL type
		String className = TypeMapper.toJavaClassName(StandardSQLTypeName.BOOL);

		// Then: Should return Boolean class name
		assertEquals(Boolean.class.getName(), className);
	}

	@Test
	void testToJavaClassNameWithTimestamp() {
		// When: Mapping TIMESTAMP type
		String className = TypeMapper.toJavaClassName(StandardSQLTypeName.TIMESTAMP);

		// Then: Should return Timestamp class name
		assertEquals(java.sql.Timestamp.class.getName(), className);
	}

	@Test
	void testToJavaClassNameWithDateTime() {
		// When: Mapping DATETIME type
		String className = TypeMapper.toJavaClassName(StandardSQLTypeName.DATETIME);

		// Then: Should return Timestamp class name
		assertEquals(java.sql.Timestamp.class.getName(), className);
	}

	@Test
	void testToJavaClassNameWithDate() {
		// When: Mapping DATE type
		String className = TypeMapper.toJavaClassName(StandardSQLTypeName.DATE);

		// Then: Should return Date class name
		assertEquals(java.sql.Date.class.getName(), className);
	}

	@Test
	void testToJavaClassNameWithTime() {
		// When: Mapping TIME type
		String className = TypeMapper.toJavaClassName(StandardSQLTypeName.TIME);

		// Then: Should return Time class name
		assertEquals(java.sql.Time.class.getName(), className);
	}

	@Test
	void testToJavaClassNameWithGeography() {
		// When: Mapping GEOGRAPHY type
		String className = TypeMapper.toJavaClassName(StandardSQLTypeName.GEOGRAPHY);

		// Then: Should return String class name (WKT representation)
		assertEquals(String.class.getName(), className);
	}

	@Test
	void testToJavaClassNameWithJson() {
		// When: Mapping JSON type
		String className = TypeMapper.toJavaClassName(StandardSQLTypeName.JSON);

		// Then: Should return String class name
		assertEquals(String.class.getName(), className);
	}

	@Test
	void testToJavaClassNameWithInterval() {
		// When: Mapping INTERVAL type
		String className = TypeMapper.toJavaClassName(StandardSQLTypeName.INTERVAL);

		// Then: Should return String class name
		assertEquals(String.class.getName(), className);
	}

	@Test
	void testToJavaClassNameWithStruct() {
		// When: Mapping STRUCT type
		String className = TypeMapper.toJavaClassName(StandardSQLTypeName.STRUCT);

		// Then: Should return Map class name
		assertEquals(java.util.Map.class.getName(), className);
	}

	@Test
	void testToJavaClassNameWithArray() {
		// When: Mapping ARRAY type
		String className = TypeMapper.toJavaClassName(StandardSQLTypeName.ARRAY);

		// Then: Should return List class name
		assertEquals(java.util.List.class.getName(), className);
	}

	@Test
	void testToJavaClassNameWithNull() {
		// When: Mapping null type
		String className = TypeMapper.toJavaClassName(null);

		// Then: Should return Object class name
		assertEquals(Object.class.getName(), className);
	}

	// getColumnSize(StandardSQLTypeName) Tests

	@Test
	void testGetColumnSizeWithString() {
		// When: Getting column size for STRING
		int size = TypeMapper.getColumnSize(StandardSQLTypeName.STRING);

		// Then: Should return 2MB max
		assertEquals(2097152, size);
	}

	@Test
	void testGetColumnSizeWithBytes() {
		// When: Getting column size for BYTES
		int size = TypeMapper.getColumnSize(StandardSQLTypeName.BYTES);

		// Then: Should return 10MB max
		assertEquals(10485760, size);
	}

	@Test
	void testGetColumnSizeWithInt64() {
		// When: Getting column size for INT64
		int size = TypeMapper.getColumnSize(StandardSQLTypeName.INT64);

		// Then: Should return 19 (digits in Long.MAX_VALUE)
		assertEquals(19, size);
	}

	@Test
	void testGetColumnSizeWithFloat64() {
		// When: Getting column size for FLOAT64
		int size = TypeMapper.getColumnSize(StandardSQLTypeName.FLOAT64);

		// Then: Should return 15 (precision)
		assertEquals(15, size);
	}

	@Test
	void testGetColumnSizeWithNumeric() {
		// When: Getting column size for NUMERIC
		int size = TypeMapper.getColumnSize(StandardSQLTypeName.NUMERIC);

		// Then: Should return 38 (precision)
		assertEquals(38, size);
	}

	@Test
	void testGetColumnSizeWithBigNumeric() {
		// When: Getting column size for BIGNUMERIC
		int size = TypeMapper.getColumnSize(StandardSQLTypeName.BIGNUMERIC);

		// Then: Should return 76 (precision)
		assertEquals(76, size);
	}

	@Test
	void testGetColumnSizeWithBool() {
		// When: Getting column size for BOOL
		int size = TypeMapper.getColumnSize(StandardSQLTypeName.BOOL);

		// Then: Should return 1
		assertEquals(1, size);
	}

	@Test
	void testGetColumnSizeWithDate() {
		// When: Getting column size for DATE
		int size = TypeMapper.getColumnSize(StandardSQLTypeName.DATE);

		// Then: Should return 10 (YYYY-MM-DD)
		assertEquals(10, size);
	}

	@Test
	void testGetColumnSizeWithTime() {
		// When: Getting column size for TIME
		int size = TypeMapper.getColumnSize(StandardSQLTypeName.TIME);

		// Then: Should return 12 (HH:MM:SS.FFF)
		assertEquals(12, size);
	}

	@Test
	void testGetColumnSizeWithDateTime() {
		// When: Getting column size for DATETIME
		int size = TypeMapper.getColumnSize(StandardSQLTypeName.DATETIME);

		// Then: Should return 26 (YYYY-MM-DD HH:MM:SS.FFFFFF)
		assertEquals(26, size);
	}

	@Test
	void testGetColumnSizeWithTimestamp() {
		// When: Getting column size for TIMESTAMP
		int size = TypeMapper.getColumnSize(StandardSQLTypeName.TIMESTAMP);

		// Then: Should return 26 (YYYY-MM-DD HH:MM:SS.FFFFFF)
		assertEquals(26, size);
	}

	@Test
	void testGetColumnSizeWithGeography() {
		// When: Getting column size for GEOGRAPHY
		int size = TypeMapper.getColumnSize(StandardSQLTypeName.GEOGRAPHY);

		// Then: Should return 2MB max (string size)
		assertEquals(2097152, size);
	}

	@Test
	void testGetColumnSizeWithJson() {
		// When: Getting column size for JSON
		int size = TypeMapper.getColumnSize(StandardSQLTypeName.JSON);

		// Then: Should return 2MB max (string size)
		assertEquals(2097152, size);
	}

	@Test
	void testGetColumnSizeWithInterval() {
		// When: Getting column size for INTERVAL
		int size = TypeMapper.getColumnSize(StandardSQLTypeName.INTERVAL);

		// Then: Should return 2MB max (string size)
		assertEquals(2097152, size);
	}

	@Test
	void testGetColumnSizeWithNull() {
		// When: Getting column size for null type
		int size = TypeMapper.getColumnSize(null);

		// Then: Should return 0
		assertEquals(0, size);
	}

	@Test
	void testGetColumnSizeWithStructReturnsZero() {
		// When: Getting column size for STRUCT
		int size = TypeMapper.getColumnSize(StandardSQLTypeName.STRUCT);

		// Then: Should return 0 (default)
		assertEquals(0, size);
	}

	@Test
	void testGetColumnSizeWithArrayReturnsZero() {
		// When: Getting column size for ARRAY
		int size = TypeMapper.getColumnSize(StandardSQLTypeName.ARRAY);

		// Then: Should return 0 (default)
		assertEquals(0, size);
	}

	// getDecimalDigits(StandardSQLTypeName) Tests

	@Test
	void testGetDecimalDigitsWithNumeric() {
		// When: Getting decimal digits for NUMERIC
		int digits = TypeMapper.getDecimalDigits(StandardSQLTypeName.NUMERIC);

		// Then: Should return 9
		assertEquals(9, digits);
	}

	@Test
	void testGetDecimalDigitsWithBigNumeric() {
		// When: Getting decimal digits for BIGNUMERIC
		int digits = TypeMapper.getDecimalDigits(StandardSQLTypeName.BIGNUMERIC);

		// Then: Should return 38
		assertEquals(38, digits);
	}

	@Test
	void testGetDecimalDigitsWithFloat64() {
		// When: Getting decimal digits for FLOAT64
		int digits = TypeMapper.getDecimalDigits(StandardSQLTypeName.FLOAT64);

		// Then: Should return 15
		assertEquals(15, digits);
	}

	@Test
	void testGetDecimalDigitsWithNull() {
		// When: Getting decimal digits for null type
		int digits = TypeMapper.getDecimalDigits(null);

		// Then: Should return 0
		assertEquals(0, digits);
	}

	@Test
	void testGetDecimalDigitsWithNonDecimalType() {
		// When: Getting decimal digits for STRING (non-decimal type)
		int digits = TypeMapper.getDecimalDigits(StandardSQLTypeName.STRING);

		// Then: Should return 0
		assertEquals(0, digits);
	}

	@Test
	void testGetDecimalDigitsWithInt64() {
		// When: Getting decimal digits for INT64 (integer type)
		int digits = TypeMapper.getDecimalDigits(StandardSQLTypeName.INT64);

		// Then: Should return 0
		assertEquals(0, digits);
	}

	@Test
	void testGetDecimalDigitsWithBool() {
		// When: Getting decimal digits for BOOL
		int digits = TypeMapper.getDecimalDigits(StandardSQLTypeName.BOOL);

		// Then: Should return 0
		assertEquals(0, digits);
	}

	@Test
	void testGetDecimalDigitsWithDate() {
		// When: Getting decimal digits for DATE
		int digits = TypeMapper.getDecimalDigits(StandardSQLTypeName.DATE);

		// Then: Should return 0
		assertEquals(0, digits);
	}

	// Class structure tests

	@Test
	void testClassIsFinal() {
		// When: Checking class modifiers
		boolean isFinal = Modifier.isFinal(TypeMapper.class.getModifiers());

		// Then: Class should be final
		assertTrue(isFinal, "TypeMapper class should be final");
	}

	@Test
	void testPrivateConstructor() throws Exception {
		// When: Getting constructor
		Constructor<TypeMapper> constructor = TypeMapper.class.getDeclaredConstructor();

		// Then: Constructor should be private
		assertTrue(Modifier.isPrivate(constructor.getModifiers()));

		// And: Should be able to invoke it (no exception thrown)
		constructor.setAccessible(true);
		assertDoesNotThrow(() -> constructor.newInstance());
	}
}

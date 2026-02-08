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

import org.junit.jupiter.api.Test;

import java.sql.ParameterMetaData;
import java.sql.SQLException;
import java.sql.Types;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for BQParameterMetaData.
 *
 * @since 1.0.15
 */
class BQParameterMetaDataTest {

	@Test
	void testConstructionAndGetParameterCount() throws SQLException {
		// Given: Parameter metadata with 5 parameters
		BQParameterMetaData metaData = new BQParameterMetaData(5);

		// When: Getting parameter count
		int count = metaData.getParameterCount();

		// Then: Should return correct count
		assertEquals(5, count);
	}

	@Test
	void testConstructionWithZeroParameters() throws SQLException {
		// Given: Parameter metadata with 0 parameters
		BQParameterMetaData metaData = new BQParameterMetaData(0);

		// When: Getting parameter count
		int count = metaData.getParameterCount();

		// Then: Should return 0
		assertEquals(0, count);
	}

	@Test
	void testIsNullable() throws SQLException {
		// Given: Parameter metadata with parameters
		BQParameterMetaData metaData = new BQParameterMetaData(3);

		// When: Checking if nullable
		int nullable = metaData.isNullable(1);

		// Then: Should return parameterNullableUnknown
		assertEquals(ParameterMetaData.parameterNullableUnknown, nullable);
	}

	@Test
	void testIsNullableWithInvalidIndexThrows() {
		// Given: Parameter metadata with 3 parameters
		BQParameterMetaData metaData = new BQParameterMetaData(3);

		// Then: Invalid index should throw SQLException
		assertThrows(SQLException.class, () -> metaData.isNullable(0));
		assertThrows(SQLException.class, () -> metaData.isNullable(4));
	}

	@Test
	void testIsSigned() throws SQLException {
		// Given: Parameter metadata with parameters
		BQParameterMetaData metaData = new BQParameterMetaData(3);

		// When: Checking if signed
		boolean signed = metaData.isSigned(1);

		// Then: Should return false (unknown at compile time)
		assertFalse(signed);
	}

	@Test
	void testIsSignedWithInvalidIndexThrows() {
		// Given: Parameter metadata with 3 parameters
		BQParameterMetaData metaData = new BQParameterMetaData(3);

		// Then: Invalid index should throw SQLException
		assertThrows(SQLException.class, () -> metaData.isSigned(0));
		assertThrows(SQLException.class, () -> metaData.isSigned(4));
	}

	@Test
	void testGetPrecision() throws SQLException {
		// Given: Parameter metadata with parameters
		BQParameterMetaData metaData = new BQParameterMetaData(3);

		// When: Getting precision
		int precision = metaData.getPrecision(2);

		// Then: Should return 0 (unknown at compile time)
		assertEquals(0, precision);
	}

	@Test
	void testGetPrecisionWithInvalidIndexThrows() {
		// Given: Parameter metadata with 3 parameters
		BQParameterMetaData metaData = new BQParameterMetaData(3);

		// Then: Invalid index should throw SQLException
		assertThrows(SQLException.class, () -> metaData.getPrecision(0));
		assertThrows(SQLException.class, () -> metaData.getPrecision(4));
	}

	@Test
	void testGetScale() throws SQLException {
		// Given: Parameter metadata with parameters
		BQParameterMetaData metaData = new BQParameterMetaData(3);

		// When: Getting scale
		int scale = metaData.getScale(2);

		// Then: Should return 0 (unknown at compile time)
		assertEquals(0, scale);
	}

	@Test
	void testGetScaleWithInvalidIndexThrows() {
		// Given: Parameter metadata with 3 parameters
		BQParameterMetaData metaData = new BQParameterMetaData(3);

		// Then: Invalid index should throw SQLException
		assertThrows(SQLException.class, () -> metaData.getScale(0));
		assertThrows(SQLException.class, () -> metaData.getScale(4));
	}

	@Test
	void testGetParameterType() throws SQLException {
		// Given: Parameter metadata with parameters
		BQParameterMetaData metaData = new BQParameterMetaData(3);

		// When: Getting parameter type
		int type = metaData.getParameterType(1);

		// Then: Should return Types.OTHER
		assertEquals(Types.OTHER, type);
	}

	@Test
	void testGetParameterTypeWithInvalidIndexThrows() {
		// Given: Parameter metadata with 3 parameters
		BQParameterMetaData metaData = new BQParameterMetaData(3);

		// Then: Invalid index should throw SQLException
		assertThrows(SQLException.class, () -> metaData.getParameterType(0));
		assertThrows(SQLException.class, () -> metaData.getParameterType(4));
	}

	@Test
	void testGetParameterTypeName() throws SQLException {
		// Given: Parameter metadata with parameters
		BQParameterMetaData metaData = new BQParameterMetaData(3);

		// When: Getting parameter type name
		String typeName = metaData.getParameterTypeName(1);

		// Then: Should return "OTHER"
		assertEquals("OTHER", typeName);
	}

	@Test
	void testGetParameterTypeNameWithInvalidIndexThrows() {
		// Given: Parameter metadata with 3 parameters
		BQParameterMetaData metaData = new BQParameterMetaData(3);

		// Then: Invalid index should throw SQLException
		assertThrows(SQLException.class, () -> metaData.getParameterTypeName(0));
		assertThrows(SQLException.class, () -> metaData.getParameterTypeName(4));
	}

	@Test
	void testGetParameterClassName() throws SQLException {
		// Given: Parameter metadata with parameters
		BQParameterMetaData metaData = new BQParameterMetaData(3);

		// When: Getting parameter class name
		String className = metaData.getParameterClassName(1);

		// Then: Should return Object class name
		assertEquals(Object.class.getName(), className);
	}

	@Test
	void testGetParameterClassNameWithInvalidIndexThrows() {
		// Given: Parameter metadata with 3 parameters
		BQParameterMetaData metaData = new BQParameterMetaData(3);

		// Then: Invalid index should throw SQLException
		assertThrows(SQLException.class, () -> metaData.getParameterClassName(0));
		assertThrows(SQLException.class, () -> metaData.getParameterClassName(4));
	}

	@Test
	void testGetParameterMode() throws SQLException {
		// Given: Parameter metadata with parameters
		BQParameterMetaData metaData = new BQParameterMetaData(3);

		// When: Getting parameter mode
		int mode = metaData.getParameterMode(1);

		// Then: Should return parameterModeIn
		assertEquals(ParameterMetaData.parameterModeIn, mode);
	}

	@Test
	void testGetParameterModeWithInvalidIndexThrows() {
		// Given: Parameter metadata with 3 parameters
		BQParameterMetaData metaData = new BQParameterMetaData(3);

		// Then: Invalid index should throw SQLException
		assertThrows(SQLException.class, () -> metaData.getParameterMode(0));
		assertThrows(SQLException.class, () -> metaData.getParameterMode(4));
	}

	@Test
	void testUnwrap() throws SQLException {
		// Given: Parameter metadata
		BQParameterMetaData metaData = new BQParameterMetaData(3);

		// When: Unwrapping to BQParameterMetaData
		BQParameterMetaData unwrapped = metaData.unwrap(BQParameterMetaData.class);

		// Then: Should return the same instance
		assertSame(metaData, unwrapped);
	}

	@Test
	void testUnwrapToParameterMetaData() throws SQLException {
		// Given: Parameter metadata
		BQParameterMetaData metaData = new BQParameterMetaData(3);

		// When: Unwrapping to ParameterMetaData interface
		ParameterMetaData unwrapped = metaData.unwrap(ParameterMetaData.class);

		// Then: Should return the same instance
		assertSame(metaData, unwrapped);
	}

	@Test
	void testUnwrapWithInvalidClassThrows() {
		// Given: Parameter metadata
		BQParameterMetaData metaData = new BQParameterMetaData(3);

		// Then: Unwrapping to incompatible class should throw SQLException
		assertThrows(SQLException.class, () -> metaData.unwrap(String.class));
	}

	@Test
	void testIsWrapperFor() throws SQLException {
		// Given: Parameter metadata
		BQParameterMetaData metaData = new BQParameterMetaData(3);

		// Then: Should return true for compatible types
		assertTrue(metaData.isWrapperFor(BQParameterMetaData.class));
		assertTrue(metaData.isWrapperFor(ParameterMetaData.class));

		// And: Should return false for incompatible types
		assertFalse(metaData.isWrapperFor(String.class));
	}
}

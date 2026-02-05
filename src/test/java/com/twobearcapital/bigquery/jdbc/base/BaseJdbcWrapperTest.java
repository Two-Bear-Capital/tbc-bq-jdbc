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
package com.twobearcapital.bigquery.jdbc.base;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.Wrapper;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for BaseJdbcWrapper.
 *
 * @since 1.0.15
 */
class BaseJdbcWrapperTest {

	private TestWrapper wrapper;

	/**
	 * Concrete test implementation of BaseJdbcWrapper.
	 */
	private static class TestWrapper extends BaseJdbcWrapper {
		// Simple test implementation
	}

	@BeforeEach
	void setUp() {
		wrapper = new TestWrapper();
	}

	// unwrap() Tests

	@Test
	void testUnwrapWithMatchingClass() throws SQLException {
		// When: Unwrapping to the actual class
		TestWrapper unwrapped = wrapper.unwrap(TestWrapper.class);

		// Then: Should return the same instance
		assertSame(wrapper, unwrapped);
	}

	@Test
	void testUnwrapWithBaseClass() throws SQLException {
		// When: Unwrapping to base class
		BaseJdbcWrapper unwrapped = wrapper.unwrap(BaseJdbcWrapper.class);

		// Then: Should return the same instance
		assertSame(wrapper, unwrapped);
	}

	@Test
	void testUnwrapWithWrapperInterface() throws SQLException {
		// When: Unwrapping to Wrapper interface
		Wrapper unwrapped = wrapper.unwrap(Wrapper.class);

		// Then: Should return the same instance
		assertSame(wrapper, unwrapped);
	}

	@Test
	void testUnwrapWithIncompatibleClassThrows() {
		// Then: Unwrapping to incompatible class should throw SQLException
		SQLException ex = assertThrows(SQLException.class, () -> wrapper.unwrap(String.class));

		// And: Error message should be descriptive
		assertTrue(ex.getMessage().contains("Unable to unwrap"));
		assertTrue(ex.getMessage().contains("String"));
		assertTrue(ex.getMessage().contains("TestWrapper"));
	}

	@Test
	void testUnwrapWithNullThrows() {
		// Then: Null interface should throw SQLException
		SQLException ex = assertThrows(SQLException.class, () -> wrapper.unwrap(null));
		assertTrue(ex.getMessage().contains("cannot be null"));
	}

	// isWrapperFor() Tests

	@Test
	void testIsWrapperForWithMatchingClass() throws SQLException {
		// When: Checking if wrapper for actual class
		boolean result = wrapper.isWrapperFor(TestWrapper.class);

		// Then: Should return true
		assertTrue(result);
	}

	@Test
	void testIsWrapperForWithBaseClass() throws SQLException {
		// When: Checking if wrapper for base class
		boolean result = wrapper.isWrapperFor(BaseJdbcWrapper.class);

		// Then: Should return true
		assertTrue(result);
	}

	@Test
	void testIsWrapperForWithWrapperInterface() throws SQLException {
		// When: Checking if wrapper for Wrapper interface
		boolean result = wrapper.isWrapperFor(Wrapper.class);

		// Then: Should return true
		assertTrue(result);
	}

	@Test
	void testIsWrapperForWithIncompatibleClass() throws SQLException {
		// When: Checking if wrapper for incompatible class
		boolean result = wrapper.isWrapperFor(String.class);

		// Then: Should return false
		assertFalse(result);
	}

	@Test
	void testIsWrapperForWithNullThrows() {
		// Then: Null interface should throw SQLException
		SQLException ex = assertThrows(SQLException.class, () -> wrapper.isWrapperFor(null));
		assertTrue(ex.getMessage().contains("cannot be null"));
	}

	// Multiple interface hierarchy test

	interface CustomInterface {
	}

	private static class CustomWrapper extends BaseJdbcWrapper implements CustomInterface {
	}

	@Test
	void testUnwrapWithCustomInterface() throws SQLException {
		// Given: A wrapper that implements custom interface
		CustomWrapper customWrapper = new CustomWrapper();

		// When: Unwrapping to custom interface
		CustomInterface unwrapped = customWrapper.unwrap(CustomInterface.class);

		// Then: Should return the same instance
		assertSame(customWrapper, unwrapped);
	}

	@Test
	void testIsWrapperForWithCustomInterface() throws SQLException {
		// Given: A wrapper that implements custom interface
		CustomWrapper customWrapper = new CustomWrapper();

		// When: Checking if wrapper for custom interface
		boolean result = customWrapper.isWrapperFor(CustomInterface.class);

		// Then: Should return true
		assertTrue(result);
	}
}

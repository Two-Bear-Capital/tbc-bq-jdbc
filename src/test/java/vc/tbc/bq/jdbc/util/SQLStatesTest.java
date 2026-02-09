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

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SQLStates utility class.
 *
 * @since 1.0.15
 */
class SQLStatesTest {

	// Connection exceptions (08xxx)

	@Test
	void testConnectionClosedState() {
		// When: Accessing connection closed SQL state
		String state = SQLStates.CONNECTION_CLOSED;

		// Then: Should be valid SQL state
		assertNotNull(state);
		assertEquals(5, state.length());
		assertEquals("08006", state);
	}

	@Test
	void testConnectionClosedStartsWith08() {
		// When: Accessing connection closed SQL state
		String state = SQLStates.CONNECTION_CLOSED;

		// Then: Should start with "08" (connection exception category)
		assertTrue(state.startsWith("08"));
	}

	// Feature not supported (0Axxx)

	@Test
	void testFeatureNotSupportedState() {
		// When: Accessing feature not supported SQL state
		String state = SQLStates.FEATURE_NOT_SUPPORTED;

		// Then: Should be valid SQL state
		assertNotNull(state);
		assertEquals(5, state.length());
		assertEquals("0A000", state);
	}

	@Test
	void testFeatureNotSupportedStartsWith0A() {
		// When: Accessing feature not supported SQL state
		String state = SQLStates.FEATURE_NOT_SUPPORTED;

		// Then: Should start with "0A" (feature not supported category)
		assertTrue(state.startsWith("0A"));
	}

	@Test
	void testSavepointNotSupportedState() {
		// When: Accessing savepoint not supported SQL state
		String state = SQLStates.SAVEPOINT_NOT_SUPPORTED;

		// Then: Should be valid SQL state
		assertNotNull(state);
		assertEquals(5, state.length());
		assertEquals("0A001", state);
	}

	@Test
	void testSavepointNotSupportedStartsWith0A() {
		// When: Accessing savepoint not supported SQL state
		String state = SQLStates.SAVEPOINT_NOT_SUPPORTED;

		// Then: Should start with "0A" (feature not supported category)
		assertTrue(state.startsWith("0A"));
	}

	// SQL state format validation

	@Test
	void testAllStatesAreNonNull() {
		// Then: All SQL state constants should be non-null
		assertNotNull(SQLStates.CONNECTION_CLOSED);
		assertNotNull(SQLStates.FEATURE_NOT_SUPPORTED);
		assertNotNull(SQLStates.SAVEPOINT_NOT_SUPPORTED);
	}

	@Test
	void testAllStatesAreExactlyFiveCharacters() {
		// Then: All SQL states should be exactly 5 characters (SQL:2003 standard)
		assertEquals(5, SQLStates.CONNECTION_CLOSED.length());
		assertEquals(5, SQLStates.FEATURE_NOT_SUPPORTED.length());
		assertEquals(5, SQLStates.SAVEPOINT_NOT_SUPPORTED.length());
	}

	@Test
	void testAllStatesAreNonEmpty() {
		// Then: All SQL state constants should be non-empty
		assertFalse(SQLStates.CONNECTION_CLOSED.isEmpty());
		assertFalse(SQLStates.FEATURE_NOT_SUPPORTED.isEmpty());
		assertFalse(SQLStates.SAVEPOINT_NOT_SUPPORTED.isEmpty());
	}

	@Test
	void testConnectionExceptionsFollowStandard() {
		// When: Checking connection exception SQL states
		// Then: Connection exceptions should follow "08xxx" pattern
		assertTrue(SQLStates.CONNECTION_CLOSED.matches("08\\d{3}"));
	}

	@Test
	void testFeatureNotSupportedExceptionsFollowStandard() {
		// When: Checking feature not supported SQL states
		// Then: Feature not supported exceptions should follow "0Axxx" pattern
		assertTrue(SQLStates.FEATURE_NOT_SUPPORTED.matches("0A[0-9A-F]{3}"));
		assertTrue(SQLStates.SAVEPOINT_NOT_SUPPORTED.matches("0A[0-9A-F]{3}"));
	}

	// Class structure tests

	@Test
	void testClassIsFinal() {
		// When: Checking class modifiers
		boolean isFinal = Modifier.isFinal(SQLStates.class.getModifiers());

		// Then: Class should be final
		assertTrue(isFinal, "SQLStates class should be final");
	}

	@Test
	void testPrivateConstructor() throws Exception {
		// When: Getting constructor
		Constructor<SQLStates> constructor = SQLStates.class.getDeclaredConstructor();

		// Then: Constructor should be private
		assertTrue(Modifier.isPrivate(constructor.getModifiers()));

		// And: Invoking constructor should throw InvocationTargetException wrapping
		// AssertionError
		constructor.setAccessible(true);
		var exception = assertThrows(java.lang.reflect.InvocationTargetException.class, constructor::newInstance);
		assertInstanceOf(AssertionError.class, exception.getCause());
	}

	@Test
	void testPrivateConstructorErrorMessage() throws Exception {
		// When: Getting constructor and trying to instantiate
		Constructor<SQLStates> constructor = SQLStates.class.getDeclaredConstructor();
		constructor.setAccessible(true);

		// Then: Should throw InvocationTargetException with AssertionError cause
		var exception = assertThrows(java.lang.reflect.InvocationTargetException.class, constructor::newInstance);
		assertInstanceOf(AssertionError.class, exception.getCause());
		assertEquals("Utility class should not be instantiated", exception.getCause().getMessage());
	}

	// SQL state uniqueness

	@Test
	void testAllStatesAreUnique() {
		// Then: All SQL state values should be unique
		assertNotEquals(SQLStates.CONNECTION_CLOSED, SQLStates.FEATURE_NOT_SUPPORTED);
		assertNotEquals(SQLStates.CONNECTION_CLOSED, SQLStates.SAVEPOINT_NOT_SUPPORTED);
		assertNotEquals(SQLStates.FEATURE_NOT_SUPPORTED, SQLStates.SAVEPOINT_NOT_SUPPORTED);
	}
}

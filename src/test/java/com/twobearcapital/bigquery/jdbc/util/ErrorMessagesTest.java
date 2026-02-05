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
package com.twobearcapital.bigquery.jdbc.util;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ErrorMessages utility class.
 *
 * @since 1.0.15
 */
class ErrorMessagesTest {

	// Connection-related messages

	@Test
	void testConnectionClosedMessage() {
		// When: Accessing connection closed message
		String message = ErrorMessages.CONNECTION_CLOSED;

		// Then: Should not be null or empty
		assertNotNull(message);
		assertFalse(message.isEmpty());
		assertEquals("Connection is closed", message);
	}

	@Test
	void testStatementClosedMessage() {
		// When: Accessing statement closed message
		String message = ErrorMessages.STATEMENT_CLOSED;

		// Then: Should not be null or empty
		assertNotNull(message);
		assertFalse(message.isEmpty());
		assertEquals("Statement is closed", message);
	}

	@Test
	void testResultSetClosedMessage() {
		// When: Accessing ResultSet closed message
		String message = ErrorMessages.RESULTSET_CLOSED;

		// Then: Should not be null or empty
		assertNotNull(message);
		assertFalse(message.isEmpty());
		assertEquals("ResultSet is closed", message);
	}

	// Unsupported operation messages

	@Test
	void testResultSetUpdatesNotSupportedMessage() {
		// When: Accessing ResultSet updates not supported message
		String message = ErrorMessages.RESULTSET_UPDATES_NOT_SUPPORTED;

		// Then: Should not be null or empty
		assertNotNull(message);
		assertFalse(message.isEmpty());
		assertEquals("ResultSet updates not supported", message);
	}

	@Test
	void testBatchUpdatesNotSupportedMessage() {
		// When: Accessing batch updates not supported message
		String message = ErrorMessages.BATCH_UPDATES_NOT_SUPPORTED;

		// Then: Should not be null or empty
		assertNotNull(message);
		assertFalse(message.isEmpty());
		assertEquals("Batch updates not supported", message);
	}

	@Test
	void testCallableStatementsNotSupportedMessage() {
		// When: Accessing callable statements not supported message
		String message = ErrorMessages.CALLABLE_STATEMENTS_NOT_SUPPORTED;

		// Then: Should not be null or empty
		assertNotNull(message);
		assertFalse(message.isEmpty());
		assertEquals("Callable statements not supported", message);
	}

	@Test
	void testSavepointsNotSupportedMessage() {
		// When: Accessing savepoints not supported message
		String message = ErrorMessages.SAVEPOINTS_NOT_SUPPORTED;

		// Then: Should not be null or empty
		assertNotNull(message);
		assertFalse(message.isEmpty());
		assertEquals("Savepoints not supported", message);
	}

	@Test
	void testGeneratedKeysNotSupportedMessage() {
		// When: Accessing generated keys not supported message
		String message = ErrorMessages.GENERATED_KEYS_NOT_SUPPORTED;

		// Then: Should not be null or empty
		assertNotNull(message);
		assertFalse(message.isEmpty());
		assertEquals("Generated keys not supported", message);
	}

	@Test
	void testCursorsNotSupportedMessage() {
		// When: Accessing cursors not supported message
		String message = ErrorMessages.CURSORS_NOT_SUPPORTED;

		// Then: Should not be null or empty
		assertNotNull(message);
		assertFalse(message.isEmpty());
		assertEquals("Named cursors not supported", message);
	}

	@Test
	void testHoldabilityNotSupportedMessage() {
		// When: Accessing holdability not supported message
		String message = ErrorMessages.HOLDABILITY_NOT_SUPPORTED;

		// Then: Should not be null or empty
		assertNotNull(message);
		assertFalse(message.isEmpty());
		assertEquals("Result set holdability configuration not supported", message);
	}

	// Parameter validation messages

	@Test
	void testInvalidParameterIndexMessage() {
		// When: Accessing invalid parameter index message
		String message = ErrorMessages.INVALID_PARAMETER_INDEX;

		// Then: Should contain format placeholder
		assertNotNull(message);
		assertFalse(message.isEmpty());
		assertEquals("Invalid parameter index: %d", message);
		assertTrue(message.contains("%d"));
	}

	@Test
	void testInvalidParameterIndexFormatting() {
		// When: Formatting invalid parameter index message
		String formatted = String.format(ErrorMessages.INVALID_PARAMETER_INDEX, 42);

		// Then: Should format correctly
		assertEquals("Invalid parameter index: 42", formatted);
	}

	@Test
	void testNegativeTimeoutMessage() {
		// When: Accessing negative timeout message
		String message = ErrorMessages.NEGATIVE_TIMEOUT;

		// Then: Should not be null or empty
		assertNotNull(message);
		assertFalse(message.isEmpty());
		assertEquals("Timeout value must be non-negative", message);
	}

	// Type conversion messages

	@Test
	void testValueOutOfRangeMessage() {
		// When: Accessing value out of range message
		String message = ErrorMessages.VALUE_OUT_OF_RANGE;

		// Then: Should contain format placeholders
		assertNotNull(message);
		assertFalse(message.isEmpty());
		assertEquals("Value out of range for type %s: %s", message);
		assertTrue(message.contains("%s"));
	}

	@Test
	void testValueOutOfRangeFormatting() {
		// When: Formatting value out of range message
		String formatted = String.format(ErrorMessages.VALUE_OUT_OF_RANGE, "INTEGER", "9999999999");

		// Then: Should format correctly
		assertEquals("Value out of range for type INTEGER: 9999999999", formatted);
	}

	// Class structure tests

	@Test
	void testClassIsFinal() {
		// When: Checking class modifiers
		boolean isFinal = Modifier.isFinal(ErrorMessages.class.getModifiers());

		// Then: Class should be final
		assertTrue(isFinal, "ErrorMessages class should be final");
	}

	@Test
	void testPrivateConstructor() throws Exception {
		// When: Getting constructor
		Constructor<ErrorMessages> constructor = ErrorMessages.class.getDeclaredConstructor();

		// Then: Constructor should be private
		assertTrue(Modifier.isPrivate(constructor.getModifiers()));

		// And: Invoking constructor should throw InvocationTargetException wrapping
		// AssertionError
		constructor.setAccessible(true);
		var exception = assertThrows(java.lang.reflect.InvocationTargetException.class, constructor::newInstance);
		assertTrue(exception.getCause() instanceof AssertionError);
	}

	@Test
	void testPrivateConstructorErrorMessage() throws Exception {
		// When: Getting constructor and trying to instantiate
		Constructor<ErrorMessages> constructor = ErrorMessages.class.getDeclaredConstructor();
		constructor.setAccessible(true);

		// Then: Should throw InvocationTargetException with AssertionError cause
		var exception = assertThrows(java.lang.reflect.InvocationTargetException.class, constructor::newInstance);
		assertTrue(exception.getCause() instanceof AssertionError);
		assertEquals("Utility class should not be instantiated", exception.getCause().getMessage());
	}

	// Comprehensive validation

	@Test
	void testAllMessagesAreNonNull() {
		// Then: All error message constants should be non-null
		assertNotNull(ErrorMessages.CONNECTION_CLOSED);
		assertNotNull(ErrorMessages.STATEMENT_CLOSED);
		assertNotNull(ErrorMessages.RESULTSET_CLOSED);
		assertNotNull(ErrorMessages.RESULTSET_UPDATES_NOT_SUPPORTED);
		assertNotNull(ErrorMessages.BATCH_UPDATES_NOT_SUPPORTED);
		assertNotNull(ErrorMessages.CALLABLE_STATEMENTS_NOT_SUPPORTED);
		assertNotNull(ErrorMessages.SAVEPOINTS_NOT_SUPPORTED);
		assertNotNull(ErrorMessages.GENERATED_KEYS_NOT_SUPPORTED);
		assertNotNull(ErrorMessages.CURSORS_NOT_SUPPORTED);
		assertNotNull(ErrorMessages.HOLDABILITY_NOT_SUPPORTED);
		assertNotNull(ErrorMessages.INVALID_PARAMETER_INDEX);
		assertNotNull(ErrorMessages.NEGATIVE_TIMEOUT);
		assertNotNull(ErrorMessages.VALUE_OUT_OF_RANGE);
	}

	@Test
	void testAllMessagesAreNonEmpty() {
		// Then: All error message constants should be non-empty
		assertFalse(ErrorMessages.CONNECTION_CLOSED.isEmpty());
		assertFalse(ErrorMessages.STATEMENT_CLOSED.isEmpty());
		assertFalse(ErrorMessages.RESULTSET_CLOSED.isEmpty());
		assertFalse(ErrorMessages.RESULTSET_UPDATES_NOT_SUPPORTED.isEmpty());
		assertFalse(ErrorMessages.BATCH_UPDATES_NOT_SUPPORTED.isEmpty());
		assertFalse(ErrorMessages.CALLABLE_STATEMENTS_NOT_SUPPORTED.isEmpty());
		assertFalse(ErrorMessages.SAVEPOINTS_NOT_SUPPORTED.isEmpty());
		assertFalse(ErrorMessages.GENERATED_KEYS_NOT_SUPPORTED.isEmpty());
		assertFalse(ErrorMessages.CURSORS_NOT_SUPPORTED.isEmpty());
		assertFalse(ErrorMessages.HOLDABILITY_NOT_SUPPORTED.isEmpty());
		assertFalse(ErrorMessages.INVALID_PARAMETER_INDEX.isEmpty());
		assertFalse(ErrorMessages.NEGATIVE_TIMEOUT.isEmpty());
		assertFalse(ErrorMessages.VALUE_OUT_OF_RANGE.isEmpty());
	}
}

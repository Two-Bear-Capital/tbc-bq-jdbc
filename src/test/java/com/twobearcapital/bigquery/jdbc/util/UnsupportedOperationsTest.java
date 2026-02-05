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
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for UnsupportedOperations utility class.
 *
 * @since 1.0.15
 */
class UnsupportedOperationsTest {

	// ResultSet updates

	@Test
	void testResultSetUpdatesReturnsException() {
		// When: Creating ResultSet updates exception
		SQLException exception = UnsupportedOperations.resultSetUpdates();

		// Then: Should return SQLFeatureNotSupportedException
		assertNotNull(exception);
		assertTrue(exception instanceof SQLFeatureNotSupportedException);
	}

	@Test
	void testResultSetUpdatesHasCorrectMessage() {
		// When: Creating ResultSet updates exception
		SQLException exception = UnsupportedOperations.resultSetUpdates();

		// Then: Should have correct error message
		assertEquals(ErrorMessages.RESULTSET_UPDATES_NOT_SUPPORTED, exception.getMessage());
		assertEquals("ResultSet updates not supported", exception.getMessage());
	}

	@Test
	void testResultSetUpdatesHasCorrectSQLState() {
		// When: Creating ResultSet updates exception
		SQLException exception = UnsupportedOperations.resultSetUpdates();

		// Then: Should have correct SQL state
		assertEquals(SQLStates.FEATURE_NOT_SUPPORTED, exception.getSQLState());
		assertEquals("0A000", exception.getSQLState());
	}

	// Batch updates

	@Test
	void testBatchUpdatesReturnsException() {
		// When: Creating batch updates exception
		SQLException exception = UnsupportedOperations.batchUpdates();

		// Then: Should return SQLFeatureNotSupportedException
		assertNotNull(exception);
		assertTrue(exception instanceof SQLFeatureNotSupportedException);
	}

	@Test
	void testBatchUpdatesHasCorrectMessage() {
		// When: Creating batch updates exception
		SQLException exception = UnsupportedOperations.batchUpdates();

		// Then: Should have correct error message
		assertEquals(ErrorMessages.BATCH_UPDATES_NOT_SUPPORTED, exception.getMessage());
		assertEquals("Batch updates not supported", exception.getMessage());
	}

	@Test
	void testBatchUpdatesHasCorrectSQLState() {
		// When: Creating batch updates exception
		SQLException exception = UnsupportedOperations.batchUpdates();

		// Then: Should have correct SQL state
		assertEquals(SQLStates.FEATURE_NOT_SUPPORTED, exception.getSQLState());
		assertEquals("0A000", exception.getSQLState());
	}

	// Callable statements

	@Test
	void testCallableStatementsReturnsException() {
		// When: Creating callable statements exception
		SQLException exception = UnsupportedOperations.callableStatements();

		// Then: Should return SQLFeatureNotSupportedException
		assertNotNull(exception);
		assertTrue(exception instanceof SQLFeatureNotSupportedException);
	}

	@Test
	void testCallableStatementsHasCorrectMessage() {
		// When: Creating callable statements exception
		SQLException exception = UnsupportedOperations.callableStatements();

		// Then: Should have correct error message
		assertEquals(ErrorMessages.CALLABLE_STATEMENTS_NOT_SUPPORTED, exception.getMessage());
		assertEquals("Callable statements not supported", exception.getMessage());
	}

	@Test
	void testCallableStatementsHasCorrectSQLState() {
		// When: Creating callable statements exception
		SQLException exception = UnsupportedOperations.callableStatements();

		// Then: Should have correct SQL state
		assertEquals(SQLStates.FEATURE_NOT_SUPPORTED, exception.getSQLState());
		assertEquals("0A000", exception.getSQLState());
	}

	// Savepoints

	@Test
	void testSavepointsReturnsException() {
		// When: Creating savepoints exception
		SQLException exception = UnsupportedOperations.savepoints();

		// Then: Should return SQLFeatureNotSupportedException
		assertNotNull(exception);
		assertTrue(exception instanceof SQLFeatureNotSupportedException);
	}

	@Test
	void testSavepointsHasCorrectMessage() {
		// When: Creating savepoints exception
		SQLException exception = UnsupportedOperations.savepoints();

		// Then: Should have correct error message
		assertEquals(ErrorMessages.SAVEPOINTS_NOT_SUPPORTED, exception.getMessage());
		assertEquals("Savepoints not supported", exception.getMessage());
	}

	@Test
	void testSavepointsHasCorrectSQLState() {
		// When: Creating savepoints exception
		SQLException exception = UnsupportedOperations.savepoints();

		// Then: Should have correct SQL state (specific savepoint state)
		assertEquals(SQLStates.SAVEPOINT_NOT_SUPPORTED, exception.getSQLState());
		assertEquals("0A001", exception.getSQLState());
	}

	// Generated keys

	@Test
	void testGeneratedKeysReturnsException() {
		// When: Creating generated keys exception
		SQLException exception = UnsupportedOperations.generatedKeys();

		// Then: Should return SQLFeatureNotSupportedException
		assertNotNull(exception);
		assertTrue(exception instanceof SQLFeatureNotSupportedException);
	}

	@Test
	void testGeneratedKeysHasCorrectMessage() {
		// When: Creating generated keys exception
		SQLException exception = UnsupportedOperations.generatedKeys();

		// Then: Should have correct error message
		assertEquals(ErrorMessages.GENERATED_KEYS_NOT_SUPPORTED, exception.getMessage());
		assertEquals("Generated keys not supported", exception.getMessage());
	}

	@Test
	void testGeneratedKeysHasCorrectSQLState() {
		// When: Creating generated keys exception
		SQLException exception = UnsupportedOperations.generatedKeys();

		// Then: Should have correct SQL state
		assertEquals(SQLStates.FEATURE_NOT_SUPPORTED, exception.getSQLState());
		assertEquals("0A000", exception.getSQLState());
	}

	// Named cursors

	@Test
	void testNamedCursorsReturnsException() {
		// When: Creating named cursors exception
		SQLException exception = UnsupportedOperations.namedCursors();

		// Then: Should return SQLFeatureNotSupportedException
		assertNotNull(exception);
		assertTrue(exception instanceof SQLFeatureNotSupportedException);
	}

	@Test
	void testNamedCursorsHasCorrectMessage() {
		// When: Creating named cursors exception
		SQLException exception = UnsupportedOperations.namedCursors();

		// Then: Should have correct error message
		assertEquals(ErrorMessages.CURSORS_NOT_SUPPORTED, exception.getMessage());
		assertEquals("Named cursors not supported", exception.getMessage());
	}

	@Test
	void testNamedCursorsHasCorrectSQLState() {
		// When: Creating named cursors exception
		SQLException exception = UnsupportedOperations.namedCursors();

		// Then: Should have correct SQL state
		assertEquals(SQLStates.FEATURE_NOT_SUPPORTED, exception.getSQLState());
		assertEquals("0A000", exception.getSQLState());
	}

	// Holdability

	@Test
	void testHoldabilityReturnsException() {
		// When: Creating holdability exception
		SQLException exception = UnsupportedOperations.holdability();

		// Then: Should return SQLFeatureNotSupportedException
		assertNotNull(exception);
		assertTrue(exception instanceof SQLFeatureNotSupportedException);
	}

	@Test
	void testHoldabilityHasCorrectMessage() {
		// When: Creating holdability exception
		SQLException exception = UnsupportedOperations.holdability();

		// Then: Should have correct error message
		assertEquals(ErrorMessages.HOLDABILITY_NOT_SUPPORTED, exception.getMessage());
		assertEquals("Result set holdability configuration not supported", exception.getMessage());
	}

	@Test
	void testHoldabilityHasCorrectSQLState() {
		// When: Creating holdability exception
		SQLException exception = UnsupportedOperations.holdability();

		// Then: Should have correct SQL state
		assertEquals(SQLStates.FEATURE_NOT_SUPPORTED, exception.getSQLState());
		assertEquals("0A000", exception.getSQLState());
	}

	// Class structure tests

	@Test
	void testClassIsFinal() {
		// When: Checking class modifiers
		boolean isFinal = Modifier.isFinal(UnsupportedOperations.class.getModifiers());

		// Then: Class should be final
		assertTrue(isFinal, "UnsupportedOperations class should be final");
	}

	@Test
	void testPrivateConstructor() throws Exception {
		// When: Getting constructor
		Constructor<UnsupportedOperations> constructor = UnsupportedOperations.class.getDeclaredConstructor();

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
		Constructor<UnsupportedOperations> constructor = UnsupportedOperations.class.getDeclaredConstructor();
		constructor.setAccessible(true);

		// Then: Should throw InvocationTargetException with AssertionError cause
		var exception = assertThrows(java.lang.reflect.InvocationTargetException.class, constructor::newInstance);
		assertTrue(exception.getCause() instanceof AssertionError);
		assertEquals("Utility class should not be instantiated", exception.getCause().getMessage());
	}

	// Factory method consistency tests

	@Test
	void testAllMethodsReturnNewInstances() {
		// When: Creating multiple exceptions from same method
		SQLException ex1 = UnsupportedOperations.resultSetUpdates();
		SQLException ex2 = UnsupportedOperations.resultSetUpdates();

		// Then: Should return different instances
		assertNotSame(ex1, ex2);
	}

	@Test
	void testAllMethodsReturnSQLFeatureNotSupportedException() {
		// Then: All factory methods should return SQLFeatureNotSupportedException
		assertTrue(UnsupportedOperations.resultSetUpdates() instanceof SQLFeatureNotSupportedException);
		assertTrue(UnsupportedOperations.batchUpdates() instanceof SQLFeatureNotSupportedException);
		assertTrue(UnsupportedOperations.callableStatements() instanceof SQLFeatureNotSupportedException);
		assertTrue(UnsupportedOperations.savepoints() instanceof SQLFeatureNotSupportedException);
		assertTrue(UnsupportedOperations.generatedKeys() instanceof SQLFeatureNotSupportedException);
		assertTrue(UnsupportedOperations.namedCursors() instanceof SQLFeatureNotSupportedException);
		assertTrue(UnsupportedOperations.holdability() instanceof SQLFeatureNotSupportedException);
	}
}

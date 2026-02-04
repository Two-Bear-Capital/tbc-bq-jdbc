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

import static org.junit.jupiter.api.Assertions.*;

import com.twobearcapital.bigquery.jdbc.exception.BQSQLException;
import java.io.IOException;
import org.junit.jupiter.api.Test;

/**
 * Tests for BQSQLException.
 *
 * @since 1.0.0
 */
class BQSQLExceptionTest {

	@Test
	void testExceptionWithMessage() {
		// Given: An exception message
		String message = "Query failed";

		// When: Creating exception
		BQSQLException ex = new BQSQLException(message);

		// Then: Message should be set
		assertEquals(message, ex.getMessage());
		assertNull(ex.getSQLState());
	}

	@Test
	void testExceptionWithMessageAndSQLState() {
		// Given: A message and SQLState
		String message = "Syntax error";
		String sqlState = BQSQLException.SQLSTATE_SYNTAX_ERROR;

		// When: Creating exception
		BQSQLException ex = new BQSQLException(message, sqlState);

		// Then: Both should be set
		assertEquals(message, ex.getMessage());
		assertEquals(sqlState, ex.getSQLState());
	}

	@Test
	void testExceptionWithMessageSQLStateAndCause() {
		// Given: A message, SQLState, and cause
		String message = "Connection failed";
		String sqlState = BQSQLException.SQLSTATE_CONNECTION_ERROR;
		IOException cause = new IOException("Network error");

		// When: Creating exception
		BQSQLException ex = new BQSQLException(message, sqlState, cause);

		// Then: All should be set
		assertEquals(message, ex.getMessage());
		assertEquals(sqlState, ex.getSQLState());
		assertEquals(cause, ex.getCause());
	}

	@Test
	void testExceptionWithMessageAndCause() {
		// Given: A message and cause
		String message = "Authentication failed";
		IOException cause = new IOException("Invalid credentials");

		// When: Creating exception
		BQSQLException ex = new BQSQLException(message, cause);

		// Then: Both should be set
		assertEquals(message, ex.getMessage());
		assertEquals(cause, ex.getCause());
	}

	@Test
	void testSQLStateConstants() {
		// Then: SQLState constants should have expected values
		assertEquals("42000", BQSQLException.SQLSTATE_SYNTAX_ERROR);
		assertEquals("42S02", BQSQLException.SQLSTATE_TABLE_NOT_FOUND);
		assertEquals("28000", BQSQLException.SQLSTATE_AUTH_FAILED);
		assertEquals("08000", BQSQLException.SQLSTATE_CONNECTION_ERROR);
		assertEquals("08006", BQSQLException.SQLSTATE_CONNECTION_CLOSED);
		assertEquals("0A000", BQSQLException.SQLSTATE_FEATURE_NOT_SUPPORTED);
	}

	@Test
	void testExceptionIsSQLException() {
		// Given: A BQSQLException
		BQSQLException ex = new BQSQLException("Test");

		// Then: Should be instance of SQLException
		assertInstanceOf(java.sql.SQLException.class, ex);
	}

	@Test
	void testFeatureNotSupportedException() {
		// Given: A feature not supported message
		String message = "Transactions not supported";

		// When: Creating exception
		BQSQLFeatureNotSupportedException ex = new BQSQLFeatureNotSupportedException(message);

		// Then: Message and SQLState should be set
		assertEquals(message, ex.getMessage());
		assertEquals(BQSQLException.SQLSTATE_FEATURE_NOT_SUPPORTED, ex.getSQLState());
	}

	@Test
	void testFeatureNotSupportedExceptionWithCause() {
		// Given: A message and cause
		String message = "Savepoints not supported";
		Exception cause = new UnsupportedOperationException();

		// When: Creating exception
		BQSQLFeatureNotSupportedException ex = new BQSQLFeatureNotSupportedException(message, cause);

		// Then: Both should be set
		assertEquals(message, ex.getMessage());
		assertEquals(cause, ex.getCause());
		assertEquals(BQSQLException.SQLSTATE_FEATURE_NOT_SUPPORTED, ex.getSQLState());
	}

	@Test
	void testFeatureNotSupportedExceptionIsSQLFeatureNotSupportedException() {
		// Given: A BQSQLFeatureNotSupportedException
		BQSQLFeatureNotSupportedException ex = new BQSQLFeatureNotSupportedException("Not supported");

		// Then: Should be instance of SQLFeatureNotSupportedException
		assertInstanceOf(java.sql.SQLFeatureNotSupportedException.class, ex);
	}
}

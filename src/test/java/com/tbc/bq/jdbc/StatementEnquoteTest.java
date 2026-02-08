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
package com.tbc.bq.jdbc;

import com.google.cloud.bigquery.BigQuery;
import com.tbc.bq.jdbc.auth.ApplicationDefaultAuth;
import com.tbc.bq.jdbc.config.ConnectionProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Tests for JDBC 4.3 enquote methods in BQStatement.
 *
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class StatementEnquoteTest {

	@Mock
	private BQConnection connection;

	@Mock
	private BigQuery bigquery;

	private BQStatement statement;

	@BeforeEach
	void setUp() {
		when(connection.getBigQuery()).thenReturn(bigquery);
		when(connection.getProperties()).thenReturn(
				new ConnectionProperties("test-project", null, null, new ApplicationDefaultAuth(), null, null, null,
						null, false, null, null, null, null, null, false, null, null, null, null, null, null, null));

		statement = new BQStatement(connection);
	}

	@Test
	void testEnquoteLiteralSimpleString() throws SQLException {
		// Given: A simple string
		String value = "hello";

		// When: Enquoting as literal
		String result = statement.enquoteLiteral(value);

		// Then: Should be wrapped in single quotes
		assertEquals("'hello'", result);
	}

	@Test
	void testEnquoteLiteralWithSingleQuote() throws SQLException {
		// Given: A string with single quote
		String value = "O'Reilly";

		// When: Enquoting as literal
		String result = statement.enquoteLiteral(value);

		// Then: Single quote should be escaped
		assertEquals("'O\\'Reilly'", result);
	}

	@Test
	void testEnquoteLiteralWithBackslash() throws SQLException {
		// Given: A string with backslash
		String value = "C:\\path\\to\\file";

		// When: Enquoting as literal
		String result = statement.enquoteLiteral(value);

		// Then: Backslashes should be escaped
		assertEquals("'C:\\\\path\\\\to\\\\file'", result);
	}

	@Test
	void testEnquoteLiteralWithBothQuoteAndBackslash() throws SQLException {
		// Given: A string with both single quote and backslash
		String value = "It's a\\test";

		// When: Enquoting as literal
		String result = statement.enquoteLiteral(value);

		// Then: Both should be escaped
		assertEquals("'It\\'s a\\\\test'", result);
	}

	@Test
	void testEnquoteIdentifierSimple() throws SQLException {
		// Given: A simple valid identifier
		String identifier = "my_table";

		// When: Enquoting without always quote
		String result = statement.enquoteIdentifier(identifier, false);

		// Then: Should return as-is (valid unquoted)
		assertEquals("my_table", result);
	}

	@Test
	void testEnquoteIdentifierWithSpecialChars() throws SQLException {
		// Given: An identifier with special characters
		String identifier = "my-table";

		// When: Enquoting without always quote
		String result = statement.enquoteIdentifier(identifier, false);

		// Then: Should be wrapped in backticks
		assertEquals("`my-table`", result);
	}

	@Test
	void testEnquoteIdentifierWithBacktick() throws SQLException {
		// Given: An identifier with backtick
		String identifier = "my`table";

		// When: Enquoting
		String result = statement.enquoteIdentifier(identifier, false);

		// Then: Backtick should be escaped
		assertEquals("`my\\`table`", result);
	}

	@Test
	void testEnquoteIdentifierAlwaysQuote() throws SQLException {
		// Given: A simple valid identifier
		String identifier = "my_table";

		// When: Enquoting with always quote
		String result = statement.enquoteIdentifier(identifier, true);

		// Then: Should be quoted even though valid
		assertEquals("`my_table`", result);
	}

	@Test
	void testEnquoteIdentifierStartsWithNumber() throws SQLException {
		// Given: An identifier starting with number
		String identifier = "123table";

		// When: Enquoting without always quote
		String result = statement.enquoteIdentifier(identifier, false);

		// Then: Should be quoted (invalid unquoted identifier)
		assertEquals("`123table`", result);
	}

	@Test
	void testEnquoteIdentifierWithSpaces() throws SQLException {
		// Given: An identifier with spaces
		String identifier = "my table";

		// When: Enquoting without always quote
		String result = statement.enquoteIdentifier(identifier, false);

		// Then: Should be quoted
		assertEquals("`my table`", result);
	}

	@Test
	void testIsSimpleIdentifierValid() throws SQLException {
		// Given: Valid identifiers
		assertTrue(statement.isSimpleIdentifier("table_name"));
		assertTrue(statement.isSimpleIdentifier("_table"));
		assertTrue(statement.isSimpleIdentifier("table123"));
		assertTrue(statement.isSimpleIdentifier("TableName"));
	}

	@Test
	void testIsSimpleIdentifierInvalid() throws SQLException {
		// Given: Invalid identifiers
		assertFalse(statement.isSimpleIdentifier("123table")); // starts with number
		assertFalse(statement.isSimpleIdentifier("my-table")); // contains hyphen
		assertFalse(statement.isSimpleIdentifier("my table")); // contains space
		assertFalse(statement.isSimpleIdentifier("")); // empty
		assertFalse(statement.isSimpleIdentifier(null)); // null
	}

	@Test
	void testEnquoteNCharLiteral() throws SQLException {
		// Given: A string
		String value = "test";

		// When: Enquoting as NChar literal
		String result = statement.enquoteNCharLiteral(value);

		// Then: Should be same as regular literal (BigQuery doesn't distinguish)
		assertEquals("'test'", result);
	}

	@Test
	void testEnquoteOnClosedStatementThrowsException() throws SQLException {
		// Given: A closed statement
		statement.close();

		// Then: enquoteLiteral should throw SQLException
		assertThrows(SQLException.class, () -> statement.enquoteLiteral("test"));
	}

	@Test
	void testEnquoteIdentifierOnClosedStatementThrowsException() throws SQLException {
		// Given: A closed statement
		statement.close();

		// Then: enquoteIdentifier should throw SQLException
		assertThrows(SQLException.class, () -> statement.enquoteIdentifier("test", false));
	}
}

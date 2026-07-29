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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SqlStringLiteralsTest {

	@Test
	void testUnquotesTheFormBigQueryActuallyEmits() {
		// Measured against real BigQuery: a table created with
		// OPTIONS(description='hello "there", friend') reports its option_value as
		// this exact string, quotes and backslashes included.
		assertEquals("hello \"there\", friend", SqlStringLiterals.unquote("\"hello \\\"there\\\", friend\""));
	}

	@Test
	void testUnquotesASimpleValue() {
		assertEquals("hello there", SqlStringLiterals.unquote("\"hello there\""));
	}

	@Test
	void testUnquotesSingleQuotedValues() {
		assertEquals("hello there", SqlStringLiterals.unquote("'hello there'"));
	}

	@Test
	void testUnquotesTripleQuotedValues() {
		assertEquals("line one\nline two", SqlStringLiterals.unquote("\"\"\"line one\nline two\"\"\""));
		assertEquals("quoted \"inside\"", SqlStringLiterals.unquote("'''quoted \"inside\"'''"));
	}

	@Test
	void testResolvesTheStandardEscapes() {
		assertEquals("a\nb", SqlStringLiterals.unquote("\"a\\nb\""));
		assertEquals("a\tb", SqlStringLiterals.unquote("\"a\\tb\""));
		assertEquals("a\rb", SqlStringLiterals.unquote("\"a\\rb\""));
		assertEquals("a\\b", SqlStringLiterals.unquote("\"a\\\\b\""));
		assertEquals("it's", SqlStringLiterals.unquote("\"it\\'s\""));
	}

	@Test
	void testRawStringsKeepTheirBackslashes() {
		// r'C:\path' means the backslash is literal, so decoding it as an escape
		// would silently eat it.
		assertEquals("C:\\path", SqlStringLiterals.unquote("r'C:\\path'"));
		assertEquals("a\\nb", SqlStringLiterals.unquote("R\"a\\nb\""));
	}

	@Test
	void testUnrecognisedEscapeYieldsTheCharacterItEscaped() {
		// Lenient on purpose: a description is display text, never re-executed, so a
		// readable approximation beats dropping the character or refusing the value.
		assertEquals("aqb", SqlStringLiterals.unquote("\"a\\qb\""));
	}

	@Test
	void testTrailingBackslashIsKept() {
		// Guards the index arithmetic: reading the escaped character past the end of
		// the string would throw.
		assertEquals("a\\", SqlStringLiterals.unquote("\"a\\\""));
	}

	@Test
	void testUnquotedValuesArePassedThrough() {
		// Non-string options -- numbers, booleans -- are not quoted. Returning them
		// unchanged is closer to the value than stripping their first and last
		// characters would be.
		assertEquals("true", SqlStringLiterals.unquote("true"));
		assertEquals("42", SqlStringLiterals.unquote("42"));
		assertEquals("", SqlStringLiterals.unquote(""));
	}

	@Test
	void testEmptyStringLiteral() {
		assertEquals("", SqlStringLiterals.unquote("\"\""));
	}

	@Test
	void testNullIsNull() {
		assertNull(SqlStringLiterals.unquote(null));
	}
}

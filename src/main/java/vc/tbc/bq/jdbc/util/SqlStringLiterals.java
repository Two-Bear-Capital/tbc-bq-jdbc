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

/**
 * Recovers the text of a GoogleSQL string literal.
 *
 * <p>
 * {@code INFORMATION_SCHEMA.TABLE_OPTIONS} and its neighbours report every
 * option as the SQL that would set it, not as its value. A table created with
 * {@code OPTIONS(description='hello "there"')} reports an {@code option_value}
 * of:
 *
 * <pre>
 * "hello \"there\""
 * </pre>
 *
 * <p>
 * Handing that to a caller as a table comment shows them the quotes and the
 * backslashes. This turns it back into what the author wrote.
 *
 * <p>
 * <b>Lenient by design.</b> A description is display text: showing a slightly
 * wrong comment beats showing none, and a comment is never re-executed as SQL,
 * so a decoding mistake cannot become an injection. Anything unrecognised is
 * therefore passed through rather than rejected — an unquoted value returns
 * unchanged, and an unknown escape yields the character it escaped.
 *
 * @since 3.2.0
 */
public final class SqlStringLiterals {

	private SqlStringLiterals() {
	}

	/**
	 * Decodes a quoted GoogleSQL string literal.
	 *
	 * <p>
	 * Recognises the quoting forms BigQuery emits for option values: double or
	 * single quotes, tripled or not, and the raw-string {@code r} prefix, in which
	 * backslashes are literal.
	 *
	 * @param literal
	 *            the literal as BigQuery reported it, or null
	 * @return the text it denotes, or null if {@code literal} was null
	 */
	public static String unquote(String literal) {
		if (literal == null) {
			return null;
		}
		String body = literal;

		// r'...' / R"..." — a raw string, where backslashes stand for themselves.
		boolean raw = false;
		if (body.length() > 1 && (body.charAt(0) == 'r' || body.charAt(0) == 'R')) {
			char next = body.charAt(1);
			if (next == '"' || next == '\'') {
				raw = true;
				body = body.substring(1);
			}
		}

		String stripped = stripQuotes(body);
		if (stripped == null) {
			// Not quoted at all: a numeric or boolean option, or something new. The
			// caller asked for the value, and this is the closest thing to it.
			return literal;
		}
		return raw ? stripped : unescape(stripped);
	}

	/**
	 * Removes the surrounding quotes, tripled or single, or returns null when the
	 * value is not a quoted string at all.
	 */
	private static String stripQuotes(String value) {
		for (String quote : new String[]{"\"\"\"", "'''", "\"", "'"}) {
			if (value.length() >= 2 * quote.length() && value.startsWith(quote) && value.endsWith(quote)) {
				return value.substring(quote.length(), value.length() - quote.length());
			}
		}
		return null;
	}

	/** Resolves backslash escapes within an already-unquoted literal body. */
	private static String unescape(String body) {
		if (body.indexOf('\\') < 0) {
			return body;
		}
		StringBuilder decoded = new StringBuilder(body.length());
		for (int i = 0; i < body.length(); i++) {
			char c = body.charAt(i);
			if (c != '\\' || i == body.length() - 1) {
				decoded.append(c);
				continue;
			}
			char escaped = body.charAt(++i);
			switch (escaped) {
				case 'n' -> decoded.append('\n');
				case 'r' -> decoded.append('\r');
				case 't' -> decoded.append('\t');
				case 'b' -> decoded.append('\b');
				case 'f' -> decoded.append('\f');
				case '0' -> decoded.append('\0');
				// Covers \\ \" \' and anything else: an escape whose meaning is the
				// character itself. Falling through to the character keeps an
				// unrecognised escape readable instead of dropping it.
				default -> decoded.append(escaped);
			}
		}
		return decoded.toString();
	}
}

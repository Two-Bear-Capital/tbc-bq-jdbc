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

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rewrites a single-row parameterized {@code INSERT ... VALUES (?, ...)}
 * statement into a multi-row {@code INSERT ... VALUES (...), (...), ...}
 * statement for JDBC batch execution.
 *
 * <p>
 * This is the moral equivalent of PostgreSQL's {@code reWriteBatchedInserts}:
 * instead of executing one BigQuery query job per batched parameter set (slow
 * and quota-hostile), the accumulated parameter sets are collapsed into a
 * single multi-row DML statement executed as one job. Multi-row INSERT is also
 * the only DML shape that performs acceptably on BigQuery, so the rewrite
 * encodes the performance best practice while providing the standard JDBC batch
 * surface.
 *
 * <p>
 * A statement is rewritable when it is a simple {@code INSERT [INTO] table
 * [(columns)] VALUES (...)} whose VALUES tuple holds at least one positional
 * placeholder and ends the statement. The tuple may wrap its placeholders in
 * type-construction expressions — {@code PARSE_JSON(?)},
 * {@code ST_GEOGFROMTEXT(?)}, {@code CAST(? AS DATETIME)} — which is the only
 * way to write BigQuery's {@code JSON}, {@code GEOGRAPHY}, {@code INTERVAL},
 * {@code BIGNUMERIC} and {@code DATETIME} columns, since none of them has a
 * parameter binding and GoogleSQL will not coerce a {@code STRING} or
 * {@code TIMESTAMP} parameter into them. Repeating such a tuple stays correct:
 * each repetition gets its own parameters.
 *
 * <p>
 * Two restrictions keep this safe. First, what may appear inside the tuple is
 * limited (see {@link #isTupleCharacter(char)}) to placeholders, identifiers,
 * digits, {@code . _ $ < >}, commas, balanced parentheses and whitespace.
 * Quotes and comments are excluded outright, which is what keeps
 * {@link #countPlaceholders(String)} exact — with no string literal possible, a
 * raw {@code ?} scan cannot mistake {@code 'what?'} for a parameter and bind
 * the whole batch one placeholder out of step.
 *
 * <p>
 * Second, every top-level element of the tuple must contain a placeholder, so
 * each element varies per row by construction. That is what keeps a constant
 * element like {@code VALUES (?, 1)} — and, more to the point, a volatile one
 * like {@code VALUES (?, CURRENT_TIMESTAMP())} — off this path: collapsing the
 * latter would evaluate the function once for the whole statement instead of
 * once per row, quietly changing what the caller's batch means.
 *
 * <p>
 * Anything else — non-INSERT DML, {@code INSERT ... SELECT}, string literals,
 * multiple tuples — is rejected and the caller falls back to sequential per-row
 * execution, which is always correct.
 *
 * @since 1.0.94
 */
public final class BatchInsertRewriter {

	/**
	 * BigQuery's documented limit on query parameters per query. See
	 * <a href="https://cloud.google.com/bigquery/quotas#query_jobs">BigQuery query
	 * job quotas</a>.
	 */
	public static final int MAX_PARAMETERS_PER_QUERY = 10_000;

	/**
	 * Conservative cap on generated SQL length, staying safely under BigQuery's
	 * 1024K-character limit for unresolved GoogleSQL query length.
	 */
	public static final int MAX_QUERY_LENGTH_CHARS = 900_000;

	/**
	 * Matches everything up to and including the {@code VALUES} keyword of a simple
	 * INSERT: {@code INSERT [INTO] table[.path] [(col, ...)] VALUES}.
	 *
	 * <p>
	 * Table path segments may be backtick-quoted (including fully-qualified
	 * {@code `project.dataset.table`} references) or unquoted; unquoted first
	 * segments may contain hyphens (GoogleSQL allows unquoted dashed project IDs in
	 * table paths). The optional column list must not contain placeholders or
	 * nested parentheses.
	 *
	 * <p>
	 * Requiring {@code VALUES} is also what rejects {@code INSERT ... SELECT}. The
	 * tuple itself is scanned rather than matched, because a regular expression
	 * cannot balance parentheses.
	 */
	private static final Pattern INSERT_PREFIX = Pattern.compile("^\\s*INSERT\\s+(?:INTO\\s+)?"
			+ "(?:`[^`]+`|[\\p{L}_][\\p{L}\\p{N}_$\\-]*)(?:\\s*\\.\\s*(?:`[^`]+`|[\\p{L}_][\\p{L}\\p{N}_$]*))*"
			+ "\\s*(?:\\([^()?]*\\))?" + "\\s*VALUES\\s*", Pattern.CASE_INSENSITIVE);

	private BatchInsertRewriter() {
		// Utility class
	}

	/**
	 * Attempts to parse the given SQL as a rewritable single-row parameterized
	 * INSERT statement.
	 *
	 * @param sql
	 *            the SQL template to inspect (may be null)
	 * @return the parsed insert if the statement can be collapsed into a multi-row
	 *         INSERT, or {@link Optional#empty()} if the caller should fall back to
	 *         sequential execution
	 */
	public static Optional<RewritableInsert> parse(String sql) {
		if (sql == null) {
			return Optional.empty();
		}
		Matcher matcher = INSERT_PREFIX.matcher(sql);
		if (!matcher.lookingAt()) {
			return Optional.empty();
		}
		int tupleStart = matcher.end();
		int tupleEnd = scanTuple(sql, tupleStart);
		if (tupleEnd < 0 || !isStatementEnd(sql, tupleEnd)) {
			return Optional.empty();
		}
		String tuple = sql.substring(tupleStart, tupleEnd);
		if (!everyElementIsParameterized(tuple)) {
			return Optional.empty();
		}
		int parametersPerRow = countPlaceholders(tuple);
		if (parametersPerRow == 0 || parametersPerRow > MAX_PARAMETERS_PER_QUERY) {
			return Optional.empty();
		}
		return Optional.of(new RewritableInsert(sql.substring(0, tupleStart), tuple, parametersPerRow));
	}

	/**
	 * Scans a single parenthesised VALUES tuple starting at {@code start}.
	 *
	 * @param sql
	 *            the full statement
	 * @param start
	 *            the index expected to hold the tuple's opening parenthesis
	 * @return the index just past the tuple's closing parenthesis, or {@code -1} if
	 *         the tuple is absent, unbalanced, or holds a character outside the
	 *         permitted set
	 */
	private static int scanTuple(String sql, int start) {
		if (start >= sql.length() || sql.charAt(start) != '(') {
			return -1;
		}
		int depth = 0;
		for (int i = start; i < sql.length(); i++) {
			char c = sql.charAt(i);
			if (c == '(') {
				depth++;
			} else if (c == ')') {
				depth--;
				if (depth == 0) {
					return i + 1;
				}
			} else if (!isTupleCharacter(c)) {
				return -1;
			}
		}
		return -1;
	}

	/**
	 * Whether a character may appear inside a VALUES tuple.
	 *
	 * <p>
	 * Enough for a placeholder wrapped in type construction —
	 * {@code CAST(? AS NUMERIC(10, 2))}, {@code PARSE_JSON(?)},
	 * {@code CAST(? AS ARRAY<INT64>)} — and nothing more. Quote characters are
	 * excluded so no string literal can exist to hide a {@code ?} from
	 * {@link #countPlaceholders(String)}; operators are excluded because nothing in
	 * the supported shapes needs them, and a narrower set is a smaller thing to be
	 * wrong about.
	 */
	private static boolean isTupleCharacter(char c) {
		return c == '?' || c == ',' || c == '.' || c == '_' || c == '$' || c == '<' || c == '>'
				|| Character.isLetterOrDigit(c) || Character.isWhitespace(c);
	}

	/**
	 * Whether every top-level element of the tuple contains a placeholder.
	 *
	 * <p>
	 * An element without one is the same value on every row, which is precisely
	 * where a volatile function would hide: collapsing
	 * {@code VALUES (?, CURRENT_TIMESTAMP())} evaluates the function once for the
	 * statement rather than once per row. Requiring a placeholder per element keeps
	 * those statements on the sequential path without needing to know which
	 * functions are deterministic.
	 *
	 * @param tuple
	 *            the tuple text, including its outer parentheses
	 * @return true if every comma-separated element at depth one holds a {@code ?}
	 */
	private static boolean everyElementIsParameterized(String tuple) {
		int depth = 0;
		boolean elementHasPlaceholder = false;
		for (int i = 0; i < tuple.length(); i++) {
			char c = tuple.charAt(i);
			if (c == '(') {
				depth++;
			} else if (c == ')') {
				depth--;
				if (depth == 0) {
					return elementHasPlaceholder;
				}
			} else if (c == ',' && depth == 1) {
				if (!elementHasPlaceholder) {
					return false;
				}
				elementHasPlaceholder = false;
			} else if (c == '?') {
				elementHasPlaceholder = true;
			}
		}
		return false;
	}

	/** Whether only whitespace and at most one statement terminator remain. */
	private static boolean isStatementEnd(String sql, int index) {
		boolean terminatorSeen = false;
		for (int i = index; i < sql.length(); i++) {
			char c = sql.charAt(i);
			if (Character.isWhitespace(c)) {
				continue;
			}
			if (c == ';' && !terminatorSeen) {
				terminatorSeen = true;
				continue;
			}
			return false;
		}
		return true;
	}

	/**
	 * Counts positional placeholders in a tuple.
	 *
	 * <p>
	 * A raw character scan is exact here only because {@link #isTupleCharacter}
	 * admits no quote character, so the tuple cannot contain a string literal.
	 */
	private static int countPlaceholders(String tuple) {
		int count = 0;
		for (int i = 0; i < tuple.length(); i++) {
			if (tuple.charAt(i) == '?') {
				count++;
			}
		}
		return count;
	}

	/**
	 * A parsed single-row parameterized INSERT that can be collapsed into a
	 * multi-row INSERT.
	 *
	 * @param insertPrefix
	 *            everything up to (but excluding) the VALUES tuple, e.g.
	 *            {@code "INSERT INTO t (a, b) VALUES "}
	 * @param valuesTuple
	 *            the single-row tuple, e.g. {@code "(?, ?)"} or
	 *            {@code "(?, PARSE_JSON(?))"}
	 * @param parametersPerRow
	 *            the number of placeholders per row
	 */
	public record RewritableInsert(String insertPrefix, String valuesTuple, int parametersPerRow) {

		/**
		 * Whether the tuple is nothing but placeholders, so a parameter's bound value
		 * reaches its column unchanged.
		 *
		 * <p>
		 * The load path needs this. It writes parameter values straight to NDJSON and
		 * never sees the SQL, so a tuple that wraps a placeholder in
		 * {@code PARSE_JSON(?)} or {@code CAST(? AS DATETIME)} would have that wrapping
		 * silently dropped — not a failure, a table full of wrong data. Callers must
		 * check this before choosing a load job over DML.
		 *
		 * @return true if every parameter binds directly to its column
		 * @since 4.4.0
		 */
		public boolean placeholderOnlyTuple() {
			for (int i = 0; i < valuesTuple.length(); i++) {
				char c = valuesTuple.charAt(i);
				if (c != '(' && c != ')' && c != '?' && c != ',' && !Character.isWhitespace(c)) {
					return false;
				}
			}
			return true;
		}

		/**
		 * Computes the maximum number of rows that can be collapsed into a single query
		 * job while respecting BigQuery's per-query parameter count and query text
		 * length limits. Always at least 1.
		 *
		 * @return the maximum rows per collapsed chunk
		 */
		public int maxRowsPerChunk() {
			int byParameterCount = MAX_PARAMETERS_PER_QUERY / parametersPerRow;
			int perRowChars = valuesTuple.length() + 2; // ", " separator
			int byQueryLength = (MAX_QUERY_LENGTH_CHARS - insertPrefix.length()) / perRowChars;
			return Math.max(1, Math.min(byParameterCount, byQueryLength));
		}

		/**
		 * Builds the multi-row INSERT SQL for the given number of rows by repeating the
		 * placeholder tuple. Positional parameters for all rows must be supplied in row
		 * order, flattened into a single list.
		 *
		 * @param rowCount
		 *            the number of rows to include (must be at least 1)
		 * @return the multi-row INSERT SQL
		 */
		public String buildSql(int rowCount) {
			if (rowCount < 1) {
				throw new IllegalArgumentException("rowCount must be at least 1: " + rowCount);
			}
			StringBuilder sql = new StringBuilder(insertPrefix.length() + rowCount * (valuesTuple.length() + 2));
			sql.append(insertPrefix);
			for (int i = 0; i < rowCount; i++) {
				if (i > 0) {
					sql.append(", ");
				}
				sql.append(valuesTuple);
			}
			return sql.toString();
		}
	}
}

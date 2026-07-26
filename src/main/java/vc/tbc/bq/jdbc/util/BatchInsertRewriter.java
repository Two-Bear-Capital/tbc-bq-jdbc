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
 * The parser is intentionally conservative: a statement is only considered
 * rewritable when it is a simple {@code INSERT [INTO] 
 * 
<table>
 *  [(columns)]
 * VALUES (?, ?, ...)} whose VALUES tuple consists exclusively of positional
 * placeholders. Anything else (non-INSERT DML, {@code INSERT ... SELECT},
 * literals or expressions mixed into the tuple, multiple tuples) is rejected
 * and the caller falls back to sequential per-row execution, which is always
 * correct.
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
	 * Matches a simple single-row parameterized INSERT:
	 * {@code INSERT [INTO] table[.path] [(col, ...)] VALUES (?, ?, ...)}.
	 *
	 * <p>
	 * Table path segments may be backtick-quoted (including fully-qualified
	 * {@code `project.dataset.table`} references) or unquoted; unquoted first
	 * segments may contain hyphens (GoogleSQL allows unquoted dashed project IDs in
	 * table paths). The optional column list must not contain placeholders or
	 * nested parentheses. The VALUES tuple must contain only {@code ?}
	 * placeholders. Group 1 captures the tuple.
	 */
	private static final Pattern SIMPLE_INSERT_VALUES = Pattern.compile("^\\s*INSERT\\s+(?:INTO\\s+)?"
			+ "(?:`[^`]+`|[\\p{L}_][\\p{L}\\p{N}_$\\-]*)(?:\\s*\\.\\s*(?:`[^`]+`|[\\p{L}_][\\p{L}\\p{N}_$]*))*"
			+ "\\s*(?:\\([^()?]*\\))?" + "\\s*VALUES\\s*" + "(\\(\\s*\\?\\s*(?:,\\s*\\?\\s*)*\\))" + "\\s*;?\\s*$",
			Pattern.CASE_INSENSITIVE);

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
		Matcher matcher = SIMPLE_INSERT_VALUES.matcher(sql);
		if (!matcher.matches()) {
			return Optional.empty();
		}
		String tuple = matcher.group(1);
		int parametersPerRow = countPlaceholders(tuple);
		if (parametersPerRow == 0 || parametersPerRow > MAX_PARAMETERS_PER_QUERY) {
			return Optional.empty();
		}
		String prefix = sql.substring(0, matcher.start(1));
		return Optional.of(new RewritableInsert(prefix, tuple, parametersPerRow));
	}

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
	 *            the single-row tuple of placeholders, e.g. {@code "(?, ?)"}
	 * @param parametersPerRow
	 *            the number of placeholders per row
	 */
	public record RewritableInsert(String insertPrefix, String valuesTuple, int parametersPerRow) {

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

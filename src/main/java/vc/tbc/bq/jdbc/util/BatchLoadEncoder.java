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

import com.google.cloud.bigquery.QueryParameterValue;
import com.google.cloud.bigquery.StandardSQLTypeName;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a batched {@code INSERT ... VALUES (?, …)} into newline-delimited JSON
 * for a BigQuery load job.
 *
 * <p>
 * Above a certain size, chunked multi-row DML is the wrong mechanism: a million
 * rows means hundreds of query jobs against DML quotas, where BigQuery's own
 * answer is one load job. This class produces the payload for that job.
 *
 * <p>
 * <b>Everything here is conservative by design.</b> A load job writes data; a
 * value encoded wrongly is not an error, it is silently wrong data in a table.
 * So an INSERT qualifies only when its shape is unambiguous — an explicit
 * column list, a table this class can resolve, and parameters whose types it is
 * sure of. Anything else returns empty and the caller falls back to the DML
 * path, which is always correct.
 *
 * @since 3.2.0
 */
public final class BatchLoadEncoder {

	/**
	 * Types whose {@link QueryParameterValue#getValue()} is already the canonical
	 * text BigQuery's JSON loader accepts.
	 *
	 * <p>
	 * Verified against the client rather than assumed: a TIMESTAMP renders as
	 * {@code 2026-07-28 16:34:56.789000+00:00}, BYTES as base64, NUMERIC at its
	 * declared scale. ARRAY, STRUCT, JSON, GEOGRAPHY and INTERVAL are left out —
	 * each needs a nested or bespoke JSON form, and getting one wrong writes bad
	 * data rather than failing.
	 */
	private static final Set<StandardSQLTypeName> LOADABLE = EnumSet.of(StandardSQLTypeName.STRING,
			StandardSQLTypeName.INT64, StandardSQLTypeName.FLOAT64, StandardSQLTypeName.NUMERIC,
			StandardSQLTypeName.BIGNUMERIC, StandardSQLTypeName.BOOL, StandardSQLTypeName.BYTES,
			StandardSQLTypeName.DATE, StandardSQLTypeName.TIME, StandardSQLTypeName.DATETIME,
			StandardSQLTypeName.TIMESTAMP);

	/**
	 * Extracts the table path and the explicit column list from the prefix
	 * {@link BatchInsertRewriter} already validated as a simple INSERT.
	 *
	 * <p>
	 * Re-parsed from the prefix rather than captured during that parse so this
	 * feature owns its own requirements: the DML path is happy without a column
	 * list, and this one is not.
	 */
	private static final Pattern INSERT_TARGET = Pattern
			.compile("^\\s*INSERT\\s+(?:INTO\\s+)?(?<table>(?:`[^`]+`|[\\p{L}_][\\p{L}\\p{N}_$\\-]*)"
					+ "(?:\\s*\\.\\s*(?:`[^`]+`|[\\p{L}_][\\p{L}\\p{N}_$]*))*)"
					+ "\\s*\\((?<columns>[^()?]*)\\)\\s*VALUES\\s*$", Pattern.CASE_INSENSITIVE);

	private BatchLoadEncoder() {
	}

	/**
	 * Where a batch of rows should be loaded, and under what column names.
	 *
	 * @param tablePath
	 *            the table as written, with any backticks removed, e.g.
	 *            {@code my_dataset.events}
	 * @param columns
	 *            the explicit column names, in the order the placeholders bind
	 */
	public record LoadTarget(String tablePath, List<String> columns) {
	}

	/**
	 * Parses the load target out of an INSERT prefix.
	 *
	 * <p>
	 * Requires an explicit column list. Without one the column order is the
	 * table's, which means reading its schema before knowing how to name the fields
	 * — a metadata call, a cache to keep fresh, and a silent misattribution if the
	 * table is altered between the two. The DML path handles that case correctly
	 * already.
	 *
	 * @param insertPrefix
	 *            everything up to the VALUES tuple, from
	 *            {@link BatchInsertRewriter.RewritableInsert#insertPrefix()}
	 * @param parametersPerRow
	 *            placeholders per row, which must match the column count
	 * @return the target, or empty if this INSERT cannot be loaded
	 */
	public static Optional<LoadTarget> parseTarget(String insertPrefix, int parametersPerRow) {
		if (insertPrefix == null) {
			return Optional.empty();
		}
		Matcher matcher = INSERT_TARGET.matcher(insertPrefix);
		if (!matcher.matches()) {
			return Optional.empty();
		}
		List<String> columns = new ArrayList<>();
		for (String column : matcher.group("columns").split(",", -1)) {
			String name = unquote(column.trim());
			if (name.isEmpty()) {
				return Optional.empty();
			}
			columns.add(name);
		}
		// A mismatch means the statement would have been rejected by BigQuery anyway,
		// but pairing values with the wrong names would corrupt data rather than fail.
		if (columns.size() != parametersPerRow) {
			return Optional.empty();
		}
		return Optional.of(new LoadTarget(unquotePath(matcher.group("table").trim()), columns));
	}

	/** Removes backticks from each segment of a dotted table path. */
	private static String unquotePath(String path) {
		String[] segments = path.split("\\.");
		StringBuilder result = new StringBuilder(path.length());
		for (int i = 0; i < segments.length; i++) {
			if (i > 0) {
				result.append('.');
			}
			result.append(unquote(segments[i].trim()));
		}
		return result.toString();
	}

	private static String unquote(String token) {
		String trimmed = token.trim();
		if (trimmed.length() >= 2 && trimmed.charAt(0) == '`' && trimmed.charAt(trimmed.length() - 1) == '`') {
			return trimmed.substring(1, trimmed.length() - 1);
		}
		return trimmed;
	}

	/**
	 * Whether every parameter in every set can be encoded.
	 *
	 * <p>
	 * Checked across the whole batch, not the first row: a nullable column can be
	 * bound with a different type on a later row, and discovering that halfway
	 * through writing the payload would leave a partly-streamed load job.
	 *
	 * @param parameterSets
	 *            the batch
	 * @return true if the load path can encode all of it
	 */
	public static boolean canEncode(List<List<QueryParameterValue>> parameterSets) {
		for (List<QueryParameterValue> set : parameterSets) {
			for (QueryParameterValue value : set) {
				if (value == null || value.getType() == null || !LOADABLE.contains(value.getType())) {
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * Encodes one parameter set as a single NDJSON object.
	 *
	 * <p>
	 * Every value is written as a JSON string except booleans and nulls. BigQuery's
	 * JSON loader coerces quoted numbers, dates and timestamps to their column
	 * types, and quoting sidesteps the values that are not legal JSON numbers at
	 * all — {@code Infinity} and {@code NaN} in a FLOAT64 column.
	 *
	 * @param target
	 *            the column names to write under
	 * @param values
	 *            one row's parameters, in column order
	 * @return one line of newline-delimited JSON, without the newline
	 */
	public static String toJsonRow(LoadTarget target, List<QueryParameterValue> values) {
		StringBuilder json = new StringBuilder(64);
		json.append('{');
		for (int i = 0; i < values.size(); i++) {
			if (i > 0) {
				json.append(',');
			}
			appendString(json, target.columns().get(i));
			json.append(':');
			appendValue(json, values.get(i));
		}
		return json.append('}').toString();
	}

	private static void appendValue(StringBuilder json, QueryParameterValue value) {
		String text = value.getValue();
		if (text == null) {
			json.append("null");
			return;
		}
		if (value.getType() == StandardSQLTypeName.BOOL) {
			json.append(Boolean.parseBoolean(text) ? "true" : "false");
			return;
		}
		appendString(json, text);
	}

	/**
	 * Appends a JSON string literal.
	 *
	 * <p>
	 * Hand-rolled rather than pulled from a JSON library: this is the only place
	 * the driver writes JSON, and the alternative is a dependency whose whole
	 * surface would be used for one method. Control characters are escaped as
	 * {@code \\u00XX} because a raw one in a string is invalid JSON, and a load job
	 * would reject the file rather than the row.
	 */
	private static void appendString(StringBuilder json, String text) {
		json.append('"');
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			switch (c) {
				case '"' -> json.append("\\\"");
				case '\\' -> json.append("\\\\");
				case '\n' -> json.append("\\n");
				case '\r' -> json.append("\\r");
				case '\t' -> json.append("\\t");
				case '\b' -> json.append("\\b");
				case '\f' -> json.append("\\f");
				default -> {
					if (c < 0x20) {
						json.append(String.format("\\u%04x", (int) c));
					} else {
						json.append(c);
					}
				}
			}
		}
		json.append('"');
	}
}

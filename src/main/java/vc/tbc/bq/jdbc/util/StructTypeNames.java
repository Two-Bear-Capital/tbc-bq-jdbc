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

import com.google.cloud.bigquery.StandardSQLTypeName;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reads the field names and types out of a {@code STRUCT<...>} type name.
 *
 * <p>
 * <b>This is what bridges the two struct models.</b> A {@link java.sql.Struct}
 * carries positional attributes and a type name, with no field names; a
 * BigQuery struct parameter is a map of named fields. The names have to come
 * from somewhere, and for a struct the driver itself produced they are already
 * in the type name: {@link java.sql.ResultSet#getObject} hands back a
 * {@code BQStruct} whose {@code getSQLTypeName()} is
 * {@code STRUCT<id INT64, name STRING>}. Parsing that pairs each attribute with
 * its name and lets a struct that was read be bound straight back.
 *
 * <p>
 * The declared type also answers what an attribute's own class cannot: the type
 * of a {@code null}. BigQuery rejects an untyped null parameter, so a struct
 * built from a type name can bind one where a bare map cannot.
 *
 * @since 3.2.0
 */
public final class StructTypeNames {

	private StructTypeNames() {
	}

	/**
	 * One field of a struct type.
	 *
	 * @param name
	 *            the field name, never blank
	 * @param type
	 *            the field's BigQuery type, or null when the declaration named
	 *            something this driver does not recognise
	 * @param typeText
	 *            the type exactly as it was declared, e.g. {@code ARRAY<INT64>}
	 */
	public record StructField(String name, StandardSQLTypeName type, String typeText) {
	}

	/**
	 * Parses the fields of a {@code STRUCT<...>} type name.
	 *
	 * <p>
	 * Returns an empty list rather than throwing when the name is not a struct
	 * declaration, or declares its fields without names — the caller decides
	 * whether that is fatal, and for binding it is, with a message that can say
	 * what was wrong with the name it was given.
	 *
	 * @param typeName
	 *            the type name, e.g. {@code STRUCT<id INT64, name STRING>}
	 * @return the fields in declaration order, or an empty list
	 */
	public static List<StructField> parse(String typeName) {
		String body = structBody(typeName);
		if (body == null || body.isBlank()) {
			return List.of();
		}

		List<StructField> fields = new ArrayList<>();
		for (String declaration : splitTopLevel(body)) {
			StructField field = parseField(declaration.trim());
			if (field == null) {
				// An unnamed field -- STRUCT<INT64, STRING> is legal BigQuery -- leaves
				// nothing to bind by name, and a partially named struct would bind some
				// fields to the wrong values. Reject the whole name.
				return List.of();
			}
			fields.add(field);
		}
		return List.copyOf(fields);
	}

	/**
	 * Extracts the text between the angle brackets of a {@code STRUCT<...>} name.
	 *
	 * @return the body, or null when this is not a struct declaration
	 */
	private static String structBody(String typeName) {
		if (typeName == null) {
			return null;
		}
		String trimmed = typeName.trim();
		if (!trimmed.toUpperCase(Locale.ROOT).startsWith("STRUCT")) {
			return null;
		}
		int open = trimmed.indexOf('<');
		int close = trimmed.lastIndexOf('>');
		if (open < 0 || close < open) {
			// A bare "STRUCT" with no field list, which TypeMapper emits when the
			// schema had no sub-fields.
			return null;
		}
		return trimmed.substring(open + 1, close);
	}

	/**
	 * Splits a field list on commas that are not nested inside another type, so
	 * {@code a INT64, b STRUCT<c STRING, d INT64>} yields two fields rather than
	 * four.
	 *
	 * <p>
	 * Parentheses count as well as angle brackets: a field declared
	 * {@code amount NUMERIC(38, 9)} carries a comma of its own, and splitting on it
	 * would pair every later attribute with the wrong name.
	 */
	private static List<String> splitTopLevel(String body) {
		List<String> parts = new ArrayList<>();
		int depth = 0;
		int start = 0;
		for (int i = 0; i < body.length(); i++) {
			char c = body.charAt(i);
			if (c == '<' || c == '(') {
				depth++;
			} else if (c == '>' || c == ')') {
				depth--;
			} else if (c == ',' && depth == 0) {
				parts.add(body.substring(start, i));
				start = i + 1;
			}
		}
		parts.add(body.substring(start));
		return parts;
	}

	/**
	 * Parses one {@code name TYPE} declaration.
	 *
	 * @return the field, or null when it declares no name
	 */
	private static StructField parseField(String declaration) {
		int split = declaration.indexOf(' ');
		if (split <= 0) {
			return null;
		}
		String name = declaration.substring(0, split).trim();
		String typeText = declaration.substring(split + 1).trim();
		if (name.isEmpty() || typeText.isEmpty()) {
			return null;
		}
		// A name that is itself a type -- "INT64 " could only arise from malformed
		// input -- is still taken at face value: this is display text the driver
		// produced, not a general SQL parser.
		return new StructField(name, toStandardType(typeText), typeText);
	}

	/**
	 * Maps a declared type to a BigQuery type, or null when it is not one this
	 * driver can name.
	 *
	 * <p>
	 * A parameterised type keeps only its head — {@code ARRAY<INT64>} is an
	 * {@code ARRAY} — because that is what a {@code QueryParameterValue} needs.
	 */
	private static StandardSQLTypeName toStandardType(String typeText) {
		int bracket = typeText.indexOf('<');
		String head = (bracket < 0 ? typeText : typeText.substring(0, bracket)).trim();
		// NUMERIC(38, 9) and STRING(255) carry a precision the type name does not.
		int paren = head.indexOf('(');
		if (paren >= 0) {
			head = head.substring(0, paren).trim();
		}
		try {
			return StandardSQLTypeName.valueOf(head.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}

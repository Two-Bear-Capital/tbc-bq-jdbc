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
package com.tbc.bq.jdbc.util;

import com.google.cloud.bigquery.FieldValue;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Utility class for converting BigQuery FieldValue objects to appropriate Java
 * types.
 *
 * <p>
 * This class handles conversion of complex types (ARRAY, STRUCT) to JSON
 * strings, which prevents issues with JDBC clients that don't properly handle
 * JDBC Array and Struct objects.
 *
 * @since 1.0.25
 */
public final class FieldValueConverter {

	private static final Gson GSON = new GsonBuilder().serializeNulls().create();

	private FieldValueConverter() {
		// Utility class
	}

	/**
	 * Converts a FieldValue to a String representation. For primitive types,
	 * returns the string value. For complex types (ARRAY, STRUCT), returns a JSON
	 * string.
	 *
	 * @param value
	 *            the FieldValue to convert
	 * @return String representation, or null if the value is null
	 */
	public static String toString(FieldValue value) {
		if (value == null || value.isNull()) {
			return null;
		}

		return switch (value.getAttribute()) {
			case REPEATED -> arrayToJson(value);
			case RECORD -> recordToJson(value);
			default -> value.getStringValue();
		};
	}

	/**
	 * Converts a BigQuery ARRAY (REPEATED) field to a JSON array string.
	 *
	 * @param arrayValue
	 *            the FieldValue with REPEATED attribute
	 * @return JSON array string representation
	 */
	private static String arrayToJson(FieldValue arrayValue) {
		List<FieldValue> elements = arrayValue.getRepeatedValue();
		if (elements == null || elements.isEmpty()) {
			return "[]";
		}

		// Convert each FieldValue to its actual value
		List<Object> values = elements.stream().map(FieldValueConverter::extractValue).collect(Collectors.toList());

		return GSON.toJson(values);
	}

	/**
	 * Converts a BigQuery STRUCT (RECORD) field to a JSON object string.
	 *
	 * @param recordValue
	 *            the FieldValue with RECORD attribute
	 * @return JSON object string representation
	 */
	private static String recordToJson(FieldValue recordValue) {
		List<FieldValue> fields = recordValue.getRecordValue();
		if (fields == null || fields.isEmpty()) {
			return "{}";
		}

		// Convert each field to its actual value
		List<Object> values = fields.stream().map(FieldValueConverter::extractValue).collect(Collectors.toList());

		return GSON.toJson(values);
	}

	/**
	 * Extracts the actual value from a FieldValue, handling nested arrays and
	 * records recursively.
	 *
	 * @param fieldValue
	 *            the FieldValue to extract from
	 * @return the extracted value (String, Number, Boolean, List, or null)
	 */
	private static Object extractValue(FieldValue fieldValue) {
		if (fieldValue.isNull()) {
			return null;
		}

		return switch (fieldValue.getAttribute()) {
			case REPEATED -> {
				// Nested array - recursively extract values
				List<FieldValue> elements = fieldValue.getRepeatedValue();
				yield elements.stream().map(FieldValueConverter::extractValue).collect(Collectors.toList());
			}
			case RECORD -> {
				// Nested struct - recursively extract values
				List<FieldValue> fields = fieldValue.getRecordValue();
				yield fields.stream().map(FieldValueConverter::extractValue).collect(Collectors.toList());
			}
			default -> // For primitive values, use getStringValue() which always works
				// Gson will handle proper JSON encoding (numbers stay unquoted, strings are
				// quoted)
				fieldValue.getStringValue();
		};
	}
}

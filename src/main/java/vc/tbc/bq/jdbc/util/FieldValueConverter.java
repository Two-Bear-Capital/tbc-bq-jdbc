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

import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.FieldList;
import com.google.cloud.bigquery.FieldValue;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
	 * string without field name information.
	 *
	 * @param value
	 *            the FieldValue to convert
	 * @return String representation, or null if the value is null
	 */
	public static String toString(FieldValue value) {
		return toString(value, null);
	}

	/**
	 * Converts a FieldValue to a String representation using schema field
	 * information for named STRUCT fields.
	 *
	 * <p>
	 * When a {@link Field} is provided, STRUCT values are serialized as JSON
	 * objects with field names (e.g.
	 * {@code {"country":"US","hdp":null,"quantity":12}}). Without schema info, they
	 * fall back to a JSON array.
	 *
	 * @param value
	 *            the FieldValue to convert
	 * @param field
	 *            the schema Field for this value, used to resolve STRUCT field
	 *            names; may be null
	 * @return String representation, or null if the value is null
	 */
	public static String toString(FieldValue value, Field field) {
		if (value == null || value.isNull()) {
			return null;
		}

		return switch (value.getAttribute()) {
			case REPEATED -> arrayToJson(value, field);
			case RECORD -> recordToJson(value, field);
			default -> value.getStringValue();
		};
	}

	/**
	 * Converts a BigQuery ARRAY (REPEATED) field to a JSON array string.
	 *
	 * @param arrayValue
	 *            the FieldValue with REPEATED attribute
	 * @param field
	 *            the schema Field for the array elements; may be null
	 * @return JSON array string representation
	 */
	private static String arrayToJson(FieldValue arrayValue, Field field) {
		List<FieldValue> elements = arrayValue.getRepeatedValue();
		if (elements == null || elements.isEmpty()) {
			return "[]";
		}

		List<Object> values = elements.stream().map(fv -> extractValue(fv, field)).collect(Collectors.toList());

		return GSON.toJson(values);
	}

	/**
	 * Converts a BigQuery STRUCT (RECORD) field to a JSON object string.
	 *
	 * <p>
	 * When {@code field} is provided and its sub-fields match the record's value
	 * count, produces a named JSON object. Otherwise falls back to a JSON array.
	 *
	 * @param recordValue
	 *            the FieldValue with RECORD attribute
	 * @param field
	 *            the schema Field whose sub-fields supply the field names; may be
	 *            null
	 * @return JSON object string representation
	 */
	private static String recordToJson(FieldValue recordValue, Field field) {
		List<FieldValue> values = recordValue.getRecordValue();
		if (values == null || values.isEmpty()) {
			return "{}";
		}
		return GSON.toJson(recordToObject(values, field));
	}

	/**
	 * Converts a list of RECORD field values and their schema into either a named
	 * {@link Map} (when schema is available) or a positional {@link List}
	 * (fallback). Used by both {@link #recordToJson} and {@link #extractValue} to
	 * avoid duplicating the same branching logic.
	 *
	 * @param values
	 *            the field values from a RECORD
	 * @param field
	 *            the schema Field whose sub-fields supply the field names; may be
	 *            null
	 * @return a {@code Map<String, Object>} when schema is available, otherwise a
	 *         {@code List<Object>}
	 */
	private static Object recordToObject(List<FieldValue> values, Field field) {
		FieldList subFields = (field != null) ? field.getSubFields() : null;

		if (subFields != null && subFields.size() == values.size()) {
			Map<String, Object> map = new LinkedHashMap<>();
			for (int i = 0; i < values.size(); i++) {
				Field subField = subFields.get(i);
				map.put(subField.getName(), extractValue(values.get(i), subField));
			}
			return map;
		}

		// Fallback: no schema info available, produce positional list
		return values.stream().map(fv -> extractValue(fv, null)).collect(Collectors.toList());
	}

	/**
	 * Extracts the actual value from a FieldValue, handling nested arrays and
	 * records recursively.
	 *
	 * @param fieldValue
	 *            the FieldValue to extract from
	 * @param field
	 *            the schema Field for this value, used to resolve nested STRUCT
	 *            field names; may be null
	 * @return the extracted value (String, Map, List, or null)
	 */
	private static Object extractValue(FieldValue fieldValue, Field field) {
		if (fieldValue.isNull()) {
			return null;
		}

		return switch (fieldValue.getAttribute()) {
			case REPEATED -> {
				List<FieldValue> elements = fieldValue.getRepeatedValue();
				yield elements.stream().map(fv -> extractValue(fv, field)).collect(Collectors.toList());
			}
			case RECORD -> recordToObject(fieldValue.getRecordValue(), field);
			default -> // For primitive values, use getStringValue() which always works
				// Gson will handle proper JSON encoding (numbers stay unquoted, strings are
				// quoted)
				fieldValue.getStringValue();
		};
	}
}

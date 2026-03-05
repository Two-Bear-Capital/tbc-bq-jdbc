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
import com.google.cloud.bigquery.StandardSQLTypeName;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import vc.tbc.bq.jdbc.BQArray;
import vc.tbc.bq.jdbc.BQStruct;
import vc.tbc.bq.jdbc.TypeMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
	 * Converts a BigQuery FieldValue to an appropriate Java object for JDBC.
	 *
	 * <p>
	 * Complex types (ARRAY, STRUCT) are serialised to a JSON string via
	 * {@link #toString(FieldValue, Field)}. Primitive types are converted to their
	 * natural Java equivalents (Boolean, Long, Double, etc.) using the provided
	 * schema Field for type information.
	 *
	 * <p>
	 * The caller is responsible for null-checking {@code value} and
	 * {@code value.isNull()} before invoking this method.
	 *
	 * @param value
	 *            the FieldValue to convert (must not be null, must not be isNull())
	 * @param field
	 *            the schema Field providing type information; may be null
	 * @return the Java object representation
	 */
	public static Object toObject(FieldValue value, Field field) {
		// Complex types (ARRAY / STRUCT) → JSON string
		if (value.getAttribute() == FieldValue.Attribute.REPEATED
				|| value.getAttribute() == FieldValue.Attribute.RECORD) {
			return toString(value, field);
		}

		if (field != null) {
			StandardSQLTypeName type = field.getType().getStandardType();
			return switch (type) {
				case BOOL -> value.getBooleanValue();
				case INT64 -> value.getLongValue();
				case FLOAT64 -> value.getDoubleValue();
				case NUMERIC, BIGNUMERIC -> value.getNumericValue();
				case STRING -> value.getStringValue();
				case BYTES -> value.getBytesValue();
				case DATE -> java.sql.Date.valueOf(value.getStringValue());
				case TIME -> java.sql.Time.valueOf(value.getStringValue());
				case DATETIME, TIMESTAMP -> new java.sql.Timestamp(value.getTimestampValue() / 1000);
				default -> value.getValue();
			};
		}

		return value.getValue();
	}

	/**
	 * Converts a BigQuery REPEATED FieldValue to a {@link BQArray} for native JDBC
	 * Array support.
	 *
	 * <p>
	 * Delegates element extraction to {@link #extractList(List, Field)} and type
	 * mapping to {@link TypeMapper#toJdbcType(StandardSQLTypeName)}.
	 *
	 * @param value
	 *            the FieldValue with REPEATED attribute (must not be null)
	 * @param field
	 *            the schema Field providing element type information; may be null
	 * @return a {@link BQArray} containing the extracted elements
	 */
	public static BQArray toBQArray(FieldValue value, Field field) {
		List<Object> elements = extractList(value.getRepeatedValue(), field);
		StandardSQLTypeName elemType = null;
		if (field != null) {
			FieldList subFields = field.getSubFields();
			if (subFields != null && !subFields.isEmpty()) {
				elemType = subFields.getFirst().getType().getStandardType();
			} else {
				elemType = field.getType().getStandardType();
			}
		}
		int baseType = TypeMapper.toJdbcType(elemType);
		String baseTypeName = elemType != null ? elemType.name() : "UNKNOWN";
		return new BQArray(elements, baseType, baseTypeName);
	}

	/**
	 * Converts a BigQuery RECORD FieldValue to a {@link BQStruct} for native JDBC
	 * Struct support.
	 *
	 * <p>
	 * Delegates field extraction to {@link #recordToObject(List, Field)} and type
	 * name resolution to {@link TypeMapper#getTypeName(Field)}.
	 *
	 * @param value
	 *            the FieldValue with RECORD attribute (must not be null)
	 * @param field
	 *            the schema Field providing sub-field names; may be null
	 * @return a {@link BQStruct} containing the extracted field values in schema
	 *         order
	 */
	public static BQStruct toBQStruct(FieldValue value, Field field) {
		Object result = recordToObject(value.getRecordValue(), field);
		Object[] attributes = switch (result) {
			case Map<?, ?> m -> m.values().toArray();
			case List<?> l -> l.toArray();
			default -> new Object[]{result};
		};
		String typeName = field != null ? TypeMapper.getTypeName(field) : "STRUCT";
		return new BQStruct(typeName, attributes);
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
		return GSON.toJson(extractList(elements, field));
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
		List<Object> list = new ArrayList<>(values.size());
		for (FieldValue fv : values) {
			list.add(extractValue(fv, null));
		}
		return list;
	}

	/**
	 * Builds a pre-sized {@link List} by extracting each element from
	 * {@code elements}. Used by both {@link #arrayToJson} and {@link #extractValue}
	 * to avoid duplicating the same loop pattern.
	 *
	 * @param elements
	 *            the list of FieldValues to convert
	 * @param field
	 *            the schema Field for element type information; may be null
	 * @return a new List containing the extracted values
	 */
	private static List<Object> extractList(List<FieldValue> elements, Field field) {
		List<Object> list = new ArrayList<>(elements.size());
		for (FieldValue fv : elements) {
			list.add(extractValue(fv, field));
		}
		return list;
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
			case REPEATED -> extractList(fieldValue.getRepeatedValue(), field);
			case RECORD -> recordToObject(fieldValue.getRecordValue(), field);
			default -> // For primitive values, use getStringValue() which always works
				// Gson will handle proper JSON encoding (numbers stay unquoted, strings are
				// quoted)
				fieldValue.getStringValue();
		};
	}
}

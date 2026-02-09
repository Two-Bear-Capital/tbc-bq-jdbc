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

import com.google.cloud.bigquery.FieldValue;
import com.google.cloud.bigquery.FieldValueList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for FieldValueConverter utility class.
 *
 * <p>
 * Verifies JSON conversion logic for BigQuery ARRAY and STRUCT types.
 *
 * @since 1.0.37
 */
@ExtendWith(MockitoExtension.class)
class FieldValueConverterTest {

	// Helper methods

	/**
	 * Creates a mock FieldValue with null value.
	 */
	private FieldValue createNullFieldValue() {
		FieldValue mock = mock(FieldValue.class);
		when(mock.isNull()).thenReturn(true);
		return mock;
	}

	/**
	 * Creates a mock FieldValue with PRIMITIVE attribute and string value.
	 */
	private FieldValue createPrimitiveValue(String value) {
		FieldValue mock = mock(FieldValue.class);
		when(mock.isNull()).thenReturn(false);
		when(mock.getAttribute()).thenReturn(FieldValue.Attribute.PRIMITIVE);
		when(mock.getStringValue()).thenReturn(value);
		return mock;
	}

	/**
	 * Creates a mock FieldValue with REPEATED attribute (array).
	 */
	private FieldValue createArrayValue(List<FieldValue> elements) {
		FieldValue mock = mock(FieldValue.class);
		when(mock.isNull()).thenReturn(false);
		when(mock.getAttribute()).thenReturn(FieldValue.Attribute.REPEATED);

		// getRepeatedValue() returns FieldValueList which implements List<FieldValue>
		// We need to mock a FieldValueList that behaves like our List
		FieldValueList mockList = mock(FieldValueList.class);
		// Use lenient() to avoid UnnecessaryStubbingException when methods aren't all
		// called
		org.mockito.Mockito.lenient().when(mockList.isEmpty()).thenReturn(elements.isEmpty());
		org.mockito.Mockito.lenient().when(mockList.stream()).thenAnswer(inv -> elements.stream());

		when(mock.getRepeatedValue()).thenReturn(mockList);
		return mock;
	}

	/**
	 * Creates a mock FieldValue with RECORD attribute (struct).
	 */
	private FieldValue createRecordValue(List<FieldValue> fields) {
		FieldValue mock = mock(FieldValue.class);
		when(mock.isNull()).thenReturn(false);
		when(mock.getAttribute()).thenReturn(FieldValue.Attribute.RECORD);

		// getRecordValue() returns FieldValueList which implements List<FieldValue>
		// We need to mock a FieldValueList that behaves like our List
		FieldValueList mockList = mock(FieldValueList.class);
		// Use lenient() to avoid UnnecessaryStubbingException when methods aren't all
		// called
		org.mockito.Mockito.lenient().when(mockList.isEmpty()).thenReturn(fields.isEmpty());
		org.mockito.Mockito.lenient().when(mockList.stream()).thenAnswer(inv -> fields.stream());

		when(mock.getRecordValue()).thenReturn(mockList);
		return mock;
	}

	// Group 1: Null and Primitive Handling (6 tests)

	@Test
	void testToStringWithNull() {
		// When: Converting null FieldValue
		String result = FieldValueConverter.toString(null);

		// Then: Should return null
		assertNull(result, "Null FieldValue should return null");
	}

	@Test
	void testToStringWithNullFieldValue() {
		// Given: FieldValue with isNull() = true
		FieldValue nullValue = createNullFieldValue();

		// When: Converting
		String result = FieldValueConverter.toString(nullValue);

		// Then: Should return null
		assertNull(result, "FieldValue.isNull() should return null");
	}

	@Test
	void testToStringWithPrimitiveString() {
		// Given: Primitive string value
		FieldValue primitiveValue = createPrimitiveValue("hello");

		// When: Converting
		String result = FieldValueConverter.toString(primitiveValue);

		// Then: Should return string value
		assertEquals("hello", result, "Primitive string should return getStringValue()");
	}

	@Test
	void testToStringWithPrimitiveInteger() {
		// Given: Primitive integer value (as string)
		FieldValue primitiveValue = createPrimitiveValue("42");

		// When: Converting
		String result = FieldValueConverter.toString(primitiveValue);

		// Then: Should return numeric string
		assertEquals("42", result, "Primitive integer should return string representation");
	}

	@Test
	void testToStringWithPrimitiveBoolean() {
		// Given: Primitive boolean value
		FieldValue primitiveValue = createPrimitiveValue("true");

		// When: Converting
		String result = FieldValueConverter.toString(primitiveValue);

		// Then: Should return boolean string
		assertEquals("true", result, "Primitive boolean should return string representation");
	}

	@Test
	void testToStringWithPrimitiveFloat() {
		// Given: Primitive float value
		FieldValue primitiveValue = createPrimitiveValue("3.14");

		// When: Converting
		String result = FieldValueConverter.toString(primitiveValue);

		// Then: Should return float string
		assertEquals("3.14", result, "Primitive float should return string representation");
	}

	// Group 2: Array Conversion (8 tests)

	@Test
	void testArrayToJsonWithSingleElement() {
		// Given: Array with single string element
		List<FieldValue> elements = Collections.singletonList(createPrimitiveValue("apple"));
		FieldValue arrayValue = createArrayValue(elements);

		// When: Converting
		String result = FieldValueConverter.toString(arrayValue);

		// Then: Should return JSON array
		assertEquals("[\"apple\"]", result, "Single element array should be valid JSON");
	}

	@Test
	void testArrayToJsonWithMultipleElements() {
		// Given: Array with multiple integer elements
		List<FieldValue> elements = Arrays.asList(createPrimitiveValue("1"), createPrimitiveValue("2"),
				createPrimitiveValue("3"));
		FieldValue arrayValue = createArrayValue(elements);

		// When: Converting
		String result = FieldValueConverter.toString(arrayValue);

		// Then: Should return JSON array
		assertEquals("[\"1\",\"2\",\"3\"]", result, "Multiple element array should be valid JSON");
	}

	@Test
	void testArrayToJsonWithEmptyArray() {
		// Given: Empty array
		FieldValue arrayValue = createArrayValue(Collections.emptyList());

		// When: Converting
		String result = FieldValueConverter.toString(arrayValue);

		// Then: Should return empty JSON array
		assertEquals("[]", result, "Empty array should return []");
	}

	@Test
	void testArrayToJsonWithNullElements() {
		// Given: Array containing null values
		List<FieldValue> elements = Arrays.asList(createNullFieldValue(), createPrimitiveValue("value"),
				createNullFieldValue());
		FieldValue arrayValue = createArrayValue(elements);

		// When: Converting
		String result = FieldValueConverter.toString(arrayValue);

		// Then: Should include nulls in JSON
		assertEquals("[null,\"value\",null]", result, "Array with nulls should serialize nulls in JSON");
	}

	@Test
	void testArrayToJsonWithMixedTypes() {
		// Given: Array with strings, numbers, booleans
		List<FieldValue> elements = Arrays.asList(createPrimitiveValue("text"), createPrimitiveValue("123"),
				createPrimitiveValue("true"), createPrimitiveValue("3.14"));
		FieldValue arrayValue = createArrayValue(elements);

		// When: Converting
		String result = FieldValueConverter.toString(arrayValue);

		// Then: Should return all values as strings in JSON
		assertEquals("[\"text\",\"123\",\"true\",\"3.14\"]", result, "Mixed type array should be valid JSON");
	}

	@Test
	void testArrayWithAllNulls() {
		// Given: Array with all null elements
		List<FieldValue> elements = Arrays.asList(createNullFieldValue(), createNullFieldValue(),
				createNullFieldValue());
		FieldValue arrayValue = createArrayValue(elements);

		// When: Converting
		String result = FieldValueConverter.toString(arrayValue);

		// Then: Should return array of nulls
		assertEquals("[null,null,null]", result, "Array of all nulls should serialize correctly");
	}

	@Test
	void testVeryLargeArray() {
		// Given: Array with 1000+ elements (performance test)
		List<FieldValue> elements = new ArrayList<>();
		for (int i = 0; i < 1500; i++) {
			elements.add(createPrimitiveValue(String.valueOf(i)));
		}
		FieldValue arrayValue = createArrayValue(elements);

		// When: Converting
		String result = FieldValueConverter.toString(arrayValue);

		// Then: Should handle large arrays without error
		assertNotNull(result, "Large array should convert successfully");
		assertTrue(result.startsWith("["), "Result should be JSON array");
		assertTrue(result.endsWith("]"), "Result should close JSON array");
		assertTrue(result.contains("\"0\""), "Should contain first element");
		assertTrue(result.contains("\"1499\""), "Should contain last element");
	}

	@Test
	void testArrayWithSingleNull() {
		// Given: Array with single null element
		List<FieldValue> elements = Collections.singletonList(createNullFieldValue());
		FieldValue arrayValue = createArrayValue(elements);

		// When: Converting
		String result = FieldValueConverter.toString(arrayValue);

		// Then: Should return [null]
		assertEquals("[null]", result, "Array with single null should be [null]");
	}

	// Group 3: Struct/Record Conversion (6 tests)

	@Test
	void testRecordToJsonWithSingleField() {
		// Given: Struct with single field
		List<FieldValue> fields = Collections.singletonList(createPrimitiveValue("Alice"));
		FieldValue recordValue = createRecordValue(fields);

		// When: Converting
		String result = FieldValueConverter.toString(recordValue);

		// Then: Should return JSON array (BigQuery represents structs as field value
		// arrays)
		assertEquals("[\"Alice\"]", result, "Single field struct should return JSON array");
	}

	@Test
	void testRecordToJsonWithMultipleFields() {
		// Given: Struct with id, name fields
		List<FieldValue> fields = Arrays.asList(createPrimitiveValue("1"), createPrimitiveValue("Bob"));
		FieldValue recordValue = createRecordValue(fields);

		// When: Converting
		String result = FieldValueConverter.toString(recordValue);

		// Then: Should return JSON array
		assertEquals("[\"1\",\"Bob\"]", result, "Multiple field struct should be valid JSON array");
	}

	@Test
	void testRecordToJsonWithEmptyStruct() {
		// Given: Empty struct
		FieldValue recordValue = createRecordValue(Collections.emptyList());

		// When: Converting
		String result = FieldValueConverter.toString(recordValue);

		// Then: Should return empty JSON object
		assertEquals("{}", result, "Empty struct should return {}");
	}

	@Test
	void testRecordToJsonWithNullFields() {
		// Given: Struct with null field values
		List<FieldValue> fields = Arrays.asList(createNullFieldValue(), createPrimitiveValue("value"),
				createNullFieldValue());
		FieldValue recordValue = createRecordValue(fields);

		// When: Converting
		String result = FieldValueConverter.toString(recordValue);

		// Then: Should include nulls
		assertEquals("[null,\"value\",null]", result, "Struct with null fields should serialize nulls");
	}

	@Test
	void testStructWithAllNullFields() {
		// Given: Struct with all null fields
		List<FieldValue> fields = Arrays.asList(createNullFieldValue(), createNullFieldValue());
		FieldValue recordValue = createRecordValue(fields);

		// When: Converting
		String result = FieldValueConverter.toString(recordValue);

		// Then: Should return array of nulls
		assertEquals("[null,null]", result, "Struct with all nulls should serialize correctly");
	}

	@Test
	void testStructWithMixedNullAndValues() {
		// Given: Struct with mix of nulls and values
		List<FieldValue> fields = Arrays.asList(createPrimitiveValue("first"), createNullFieldValue(),
				createPrimitiveValue("third"), createNullFieldValue(), createPrimitiveValue("fifth"));
		FieldValue recordValue = createRecordValue(fields);

		// When: Converting
		String result = FieldValueConverter.toString(recordValue);

		// Then: Should preserve order and nulls
		assertEquals("[\"first\",null,\"third\",null,\"fifth\"]", result, "Should preserve field order and nulls");
	}

	// Group 4: Nested Structures (8 tests)

	@Test
	void testNestedArrayInArray() {
		// Given: Array containing arrays [[1, 2], [3, 4]]
		List<FieldValue> innerArray1 = Arrays.asList(createPrimitiveValue("1"), createPrimitiveValue("2"));
		List<FieldValue> innerArray2 = Arrays.asList(createPrimitiveValue("3"), createPrimitiveValue("4"));
		List<FieldValue> outerArray = Arrays.asList(createArrayValue(innerArray1), createArrayValue(innerArray2));
		FieldValue nestedArray = createArrayValue(outerArray);

		// When: Converting
		String result = FieldValueConverter.toString(nestedArray);

		// Then: Should return nested JSON arrays
		assertEquals("[[\"1\",\"2\"],[\"3\",\"4\"]]", result, "Nested arrays should serialize correctly");
	}

	@Test
	void testNestedStructInArray() {
		// Given: Array of structs [{id: 1}, {id: 2}]
		List<FieldValue> struct1 = Collections.singletonList(createPrimitiveValue("1"));
		List<FieldValue> struct2 = Collections.singletonList(createPrimitiveValue("2"));
		List<FieldValue> arrayElements = Arrays.asList(createRecordValue(struct1), createRecordValue(struct2));
		FieldValue arrayOfStructs = createArrayValue(arrayElements);

		// When: Converting
		String result = FieldValueConverter.toString(arrayOfStructs);

		// Then: Should return array of arrays (structs represented as arrays)
		assertEquals("[[\"1\"],[\"2\"]]", result, "Array of structs should serialize correctly");
	}

	@Test
	void testNestedArrayInStruct() {
		// Given: Struct containing an array {items: [1, 2, 3]}
		List<FieldValue> arrayElements = Arrays.asList(createPrimitiveValue("1"), createPrimitiveValue("2"),
				createPrimitiveValue("3"));
		FieldValue array = createArrayValue(arrayElements);
		List<FieldValue> structFields = Collections.singletonList(array);
		FieldValue structWithArray = createRecordValue(structFields);

		// When: Converting
		String result = FieldValueConverter.toString(structWithArray);

		// Then: Should return struct with embedded array
		assertEquals("[[\"1\",\"2\",\"3\"]]", result, "Struct containing array should serialize correctly");
	}

	@Test
	void testNestedStructInStruct() {
		// Given: Struct containing struct {user: {name: "Alice"}}
		List<FieldValue> innerStruct = Collections.singletonList(createPrimitiveValue("Alice"));
		FieldValue innerRecord = createRecordValue(innerStruct);
		List<FieldValue> outerStruct = Collections.singletonList(innerRecord);
		FieldValue outerRecord = createRecordValue(outerStruct);

		// When: Converting
		String result = FieldValueConverter.toString(outerRecord);

		// Then: Should return nested structure
		assertEquals("[[\"Alice\"]]", result, "Nested struct should serialize correctly");
	}

	@Test
	void testDeeplyNestedStructure() {
		// Given: 3+ levels of nesting
		List<FieldValue> level3 = Collections.singletonList(createPrimitiveValue("deep"));
		FieldValue level3Record = createRecordValue(level3);
		List<FieldValue> level2 = Collections.singletonList(level3Record);
		FieldValue level2Record = createRecordValue(level2);
		List<FieldValue> level1 = Collections.singletonList(level2Record);
		FieldValue level1Record = createRecordValue(level1);

		// When: Converting
		String result = FieldValueConverter.toString(level1Record);

		// Then: Should handle deep nesting
		assertEquals("[[[\"deep\"]]]", result, "Deeply nested structures should serialize correctly");
	}

	@Test
	void testArrayOfStructsWithArrays() {
		// Given: [{items: [1, 2]}, {items: [3, 4]}]
		List<FieldValue> array1Elements = Arrays.asList(createPrimitiveValue("1"), createPrimitiveValue("2"));
		FieldValue array1 = createArrayValue(array1Elements);
		List<FieldValue> struct1 = Collections.singletonList(array1);
		FieldValue record1 = createRecordValue(struct1);

		List<FieldValue> array2Elements = Arrays.asList(createPrimitiveValue("3"), createPrimitiveValue("4"));
		FieldValue array2 = createArrayValue(array2Elements);
		List<FieldValue> struct2 = Collections.singletonList(array2);
		FieldValue record2 = createRecordValue(struct2);

		List<FieldValue> outerArray = Arrays.asList(record1, record2);
		FieldValue result = createArrayValue(outerArray);

		// When: Converting
		String json = FieldValueConverter.toString(result);

		// Then: Should handle complex nesting
		assertEquals("[[[\"1\",\"2\"]],[[\"3\",\"4\"]]]", json, "Complex nested structure should serialize correctly");
	}

	@Test
	void testEmptyNestedCollections() {
		// Given: Struct with empty array {items: []}
		FieldValue emptyArray = createArrayValue(Collections.emptyList());
		List<FieldValue> structFields = Collections.singletonList(emptyArray);
		FieldValue structWithEmptyArray = createRecordValue(structFields);

		// When: Converting
		String result = FieldValueConverter.toString(structWithEmptyArray);

		// Then: Should handle empty nested collections
		assertEquals("[[]]", result, "Struct with empty array should serialize correctly");
	}

	@Test
	void testNullsInNestedStructures() {
		// Given: Nested structure with nulls at various levels
		List<FieldValue> innerArray = Arrays.asList(createNullFieldValue(), createPrimitiveValue("val"));
		FieldValue array = createArrayValue(innerArray);
		List<FieldValue> structFields = Arrays.asList(array, createNullFieldValue());
		FieldValue struct = createRecordValue(structFields);

		// When: Converting
		String result = FieldValueConverter.toString(struct);

		// Then: Should preserve nulls at all levels
		assertEquals("[[null,\"val\"],null]", result, "Nulls in nested structures should be preserved");
	}

	// Group 5: Special Characters and Edge Cases (6 tests)

	@Test
	void testSpecialCharactersInString() {
		// Given: String with quotes, backslashes, unicode
		FieldValue specialValue = createPrimitiveValue("He said \"Hello\\World\" \u2764");

		// When: Converting
		String result = FieldValueConverter.toString(specialValue);

		// Then: Should return original string (Gson handles escaping when array/struct
		// serialized)
		assertEquals("He said \"Hello\\World\" \u2764", result, "Primitive should return original string value");
	}

	@Test
	void testJsonSpecialCharactersEscaping() {
		// Given: Array with strings containing JSON special characters
		List<FieldValue> elements = Arrays.asList(createPrimitiveValue("quote:\""), createPrimitiveValue("slash:\\"),
				createPrimitiveValue("newline:\n"), createPrimitiveValue("tab:\t"));
		FieldValue arrayValue = createArrayValue(elements);

		// When: Converting
		String result = FieldValueConverter.toString(arrayValue);

		// Then: Gson should properly escape special characters
		assertNotNull(result, "Should handle special characters");
		assertTrue(result.contains("\\\""), "Should escape quotes");
		assertTrue(result.contains("\\\\"), "Should escape backslashes");
		assertTrue(result.contains("\\n"), "Should escape newlines");
		assertTrue(result.contains("\\t"), "Should escape tabs");
	}

	@Test
	void testEmptyStringsInArray() {
		// Given: Array with empty strings
		List<FieldValue> elements = Arrays.asList(createPrimitiveValue(""), createPrimitiveValue(""),
				createPrimitiveValue(""));
		FieldValue arrayValue = createArrayValue(elements);

		// When: Converting
		String result = FieldValueConverter.toString(arrayValue);

		// Then: Should serialize empty strings
		assertEquals("[\"\",\"\",\"\"]", result, "Empty strings should serialize as empty JSON strings");
	}

	@Test
	void testVeryLongString() {
		// Given: Very long string (10K+ characters)
		StringBuilder longString = new StringBuilder();
		for (int i = 0; i < 10000; i++) {
			longString.append("A");
		}
		FieldValue longValue = createPrimitiveValue(longString.toString());

		// When: Converting
		String result = FieldValueConverter.toString(longValue);

		// Then: Should handle long strings
		assertNotNull(result, "Should handle very long strings");
		assertEquals(10000, result.length(), "Length should be preserved");
	}

	@Test
	void testNumericEdgeCases() {
		// Given: Very large numbers, scientific notation
		List<FieldValue> elements = Arrays.asList(createPrimitiveValue("999999999999999999"),
				createPrimitiveValue("1.23e-45"), createPrimitiveValue("-9.87E+123"),
				createPrimitiveValue("0.00000000001"));
		FieldValue arrayValue = createArrayValue(elements);

		// When: Converting
		String result = FieldValueConverter.toString(arrayValue);

		// Then: Should preserve numeric string representations
		assertNotNull(result, "Should handle numeric edge cases");
		assertTrue(result.contains("999999999999999999"), "Should preserve large integers");
		assertTrue(result.contains("1.23e-45"), "Should preserve scientific notation");
	}

	@Test
	void testBooleanInMixedContext() {
		// Given: Boolean values mixed with other types in struct
		List<FieldValue> fields = Arrays.asList(createPrimitiveValue("true"), createPrimitiveValue("false"),
				createPrimitiveValue("TRUE"), createPrimitiveValue("FALSE"));
		FieldValue recordValue = createRecordValue(fields);

		// When: Converting
		String result = FieldValueConverter.toString(recordValue);

		// Then: Should preserve boolean string values
		assertEquals("[\"true\",\"false\",\"TRUE\",\"FALSE\"]", result, "Boolean strings should be preserved as-is");
	}
}

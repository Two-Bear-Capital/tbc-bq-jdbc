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
package vc.tbc.bq.jdbc.testsupport;

import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.FieldValue;
import com.google.cloud.bigquery.FieldValueList;
import com.google.cloud.bigquery.Schema;
import com.google.cloud.bigquery.StandardSQLTypeName;
import com.google.cloud.bigquery.TableResult;
import vc.tbc.bq.jdbc.BQResultSet;

import java.util.List;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * Builds the small BigQuery result fixtures that value-level tests need.
 *
 * <p>
 * Standing a {@code BQResultSet} over one value takes four lines by hand — a
 * {@link Schema}, a {@link FieldValue} wrapped in a {@link FieldValueList}
 * against that schema's fields, a mocked {@link TableResult}, then the result
 * set. The verbosity buries what the test is actually about, which is usually a
 * single raw string and the getter it is read through.
 *
 * @since 3.0.6
 */
public final class TestResultSets {

	private TestResultSets() {
		throw new AssertionError("Utility class should not be instantiated");
	}

	/**
	 * A one-column, one-row result set over a single raw value.
	 *
	 * <p>
	 * The cursor is <b>not</b> advanced: callers assert on {@code next()} as part
	 * of the behaviour under test, so moving it here would hide that.
	 *
	 * @param columnName
	 *            the column's name, as the test will address it
	 * @param type
	 *            the BigQuery type to report for the column
	 * @param rawValue
	 *            the value exactly as BigQuery's REST encoding delivers it — a
	 *            string, or null for SQL NULL
	 * @return a result set positioned before the first row
	 */
	public static BQResultSet singleColumn(String columnName, StandardSQLTypeName type, String rawValue) {
		return singleColumn(Field.of(columnName, type), rawValue);
	}

	/**
	 * A one-column, one-row result set over a single raw value, for callers that
	 * need to configure the {@link Field} themselves — a legacy type name, or a
	 * mode such as REPEATED.
	 *
	 * @param field
	 *            the column definition
	 * @param rawValue
	 *            the value as BigQuery's REST encoding delivers it
	 * @return a result set positioned before the first row
	 */
	public static BQResultSet singleColumn(Field field, String rawValue) {
		Schema schema = Schema.of(field);
		FieldValueList row = FieldValueList.of(List.of(FieldValue.of(FieldValue.Attribute.PRIMITIVE, rawValue)),
				schema.getFields());
		return new BQResultSet(null, tableResult(schema, List.of(row)));
	}

	/**
	 * A mocked {@link TableResult}, for tests that need the table result itself
	 * rather than a result set over it.
	 *
	 * <p>
	 * {@code getTotalRows()} is stubbed from the row count so callers exercising
	 * size-dependent paths — the Storage Read API threshold, the metadata cache row
	 * ceiling — get a consistent answer without stubbing it separately.
	 *
	 * @param schema
	 *            the result's schema
	 * @param rows
	 *            the rows to iterate
	 * @return a mock returning exactly those
	 */
	public static TableResult tableResult(Schema schema, List<FieldValueList> rows) {
		TableResult result = mock(TableResult.class);
		// Lenient because a fixture builder cannot know which of these the caller
		// will exercise: a value-level test iterates and never asks the row count,
		// while a size-threshold test does the reverse. Under MockitoExtension's
		// strict stubs the unused one would fail the test it was provided for.
		lenient().when(result.getSchema()).thenReturn(schema);
		lenient().when(result.iterateAll()).thenReturn(rows);
		lenient().when(result.getTotalRows()).thenReturn((long) rows.size());
		return result;
	}
}

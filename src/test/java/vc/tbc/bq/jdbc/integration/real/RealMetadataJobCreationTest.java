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
package vc.tbc.bq.jdbc.integration.real;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * {@code metadataJobCreationOptional} against real BigQuery (issue #265).
 *
 * <p>
 * The driver's {@code INFORMATION_SCHEMA} reads ask BigQuery to answer without
 * creating a job. BigQuery decides per request and creates one anyway above a
 * result size of its own choosing, so the claim under test is not "no job is
 * created" — that is the service's call and it moves — but that <em>the rows do
 * not depend on the answer</em>.
 *
 * <p>
 * Every connection here disables the metadata cache. The cache is shared
 * statically per project and its key deliberately excludes this property, since
 * the property does not shape results; left on, the second connection would be
 * served the first one's rows and the comparison would pass without either mode
 * having been exercised twice.
 *
 * @since 4.2.0
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RealMetadataJobCreationTest extends AbstractRealBigQueryIntegrationTest {

	private static final String TABLE = tableName("jobless_meta");

	private Connection optional;
	private Connection required;

	@BeforeAll
	void createFixture() throws SQLException {
		createSharedTestTable(TABLE);
		optional = openWith(true);
		required = openWith(false);
	}

	@AfterAll
	void dropFixture() throws SQLException {
		if (optional != null) {
			optional.close();
		}
		if (required != null) {
			required.close();
		}
		dropSharedTestTable(TABLE);
	}

	private Connection openWith(boolean jobCreationOptional) throws SQLException {
		String url = String.format(
				"jdbc:bigquery:%s/%s?authType=ADC&metadataCacheEnabled=false&metadataJobCreationOptional=%s%s",
				TEST_PROJECT_ID, TEST_DATASET, jobCreationOptional, TEST_CONNECTION_DEFAULTS);
		return DriverManager.getConnection(url);
	}

	@Test
	void testGetTablesAgreesAcrossBothModes() throws SQLException {
		assertEquals(rowsOf(required.getMetaData().getTables(TEST_PROJECT_ID, TEST_DATASET, TABLE, null)),
				rowsOf(optional.getMetaData().getTables(TEST_PROJECT_ID, TEST_DATASET, TABLE, null)),
				"getTables() rows differ depending on whether BigQuery created a job");
	}

	@Test
	void testGetColumnsAgreesAcrossBothModes() throws SQLException {
		List<List<String>> withJob = rowsOf(
				required.getMetaData().getColumns(TEST_PROJECT_ID, TEST_DATASET, TABLE, null));
		List<List<String>> withoutJob = rowsOf(
				optional.getMetaData().getColumns(TEST_PROJECT_ID, TEST_DATASET, TABLE, null));

		// A shared fixture table always has columns, so an empty comparison would
		// otherwise pass while both reads had silently failed.
		assertFalse(withJob.isEmpty(), "the fixture table reported no columns, so this comparison proves nothing");
		assertEquals(withJob, withoutJob, "getColumns() rows differ depending on whether BigQuery created a job");
	}

	/**
	 * The widest read the driver issues, and the one most likely to cross whatever
	 * size threshold BigQuery uses to decide it needs a job after all.
	 *
	 * <p>
	 * Both calls scan the whole dataset, but only this class's own fixture rows are
	 * compared. The integration dataset is shared and other test classes create and
	 * drop tables in it throughout the run, so comparing the two scans whole would
	 * fail whenever a table appeared or vanished between them — a race in the test,
	 * not a disagreement between the modes.
	 */
	@Test
	void testWholeDatasetColumnScanAgreesAcrossBothModes() throws SQLException {
		List<List<String>> withJob = ownRows(
				rowsOf(required.getMetaData().getColumns(TEST_PROJECT_ID, TEST_DATASET, null, null)));
		List<List<String>> withoutJob = ownRows(
				rowsOf(optional.getMetaData().getColumns(TEST_PROJECT_ID, TEST_DATASET, null, null)));

		assertFalse(withJob.isEmpty(), "the dataset-wide scan did not report the fixture table's columns");
		assertEquals(withJob, withoutJob, "a dataset-wide getColumns() differs between the two modes");
	}

	/**
	 * Keeps only the rows describing this class's fixture table. {@code getColumns}
	 * reports {@code TABLE_NAME} in column 3.
	 */
	private static List<List<String>> ownRows(List<List<String>> rows) {
		return rows.stream().filter(row -> TABLE.equals(row.get(2))).toList();
	}

	@Test
	void testKeyConstraintReadAgreesAcrossBothModes() throws SQLException {
		// Reads INFORMATION_SCHEMA through the constraint snapshot rather than the
		// column path, which is a separate call site and so a separate config.
		DatabaseMetaData withJob = required.getMetaData();
		DatabaseMetaData withoutJob = optional.getMetaData();

		assertEquals(rowsOf(withJob.getPrimaryKeys(TEST_PROJECT_ID, TEST_DATASET, TABLE)),
				rowsOf(withoutJob.getPrimaryKeys(TEST_PROJECT_ID, TEST_DATASET, TABLE)),
				"getPrimaryKeys() rows differ between the two modes");
		assertEquals(rowsOf(withJob.getImportedKeys(TEST_PROJECT_ID, TEST_DATASET, TABLE)),
				rowsOf(withoutJob.getImportedKeys(TEST_PROJECT_ID, TEST_DATASET, TABLE)),
				"getImportedKeys() rows differ between the two modes");
	}

	/** Renders a ResultSet as strings so two reads compare by value. */
	private static List<List<String>> rowsOf(ResultSet rs) throws SQLException {
		List<List<String>> rows = new ArrayList<>();
		try (rs) {
			ResultSetMetaData meta = rs.getMetaData();
			int columns = meta.getColumnCount();
			while (rs.next()) {
				List<String> row = new ArrayList<>(columns);
				for (int i = 1; i <= columns; i++) {
					row.add(rs.getString(i));
				}
				rows.add(row);
			}
		}
		return rows;
	}
}

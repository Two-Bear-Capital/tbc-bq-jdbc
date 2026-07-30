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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import vc.tbc.bq.jdbc.storage.StorageReadResultSet;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Which path {@code useStorageApi=auto} actually chooses, against real BigQuery
 * (issue #264).
 *
 * <p>
 * {@code auto} sized the result and nothing else, so a result large enough to
 * clear the 10 MB estimate but small enough to arrive in a single page was sent
 * to the Storage Read API — which then fetched rows the driver was already
 * holding. Between the estimate's ~10,240-row trigger and the 50,000-row
 * default {@code pageSize}, {@code auto} was strictly slower than
 * {@code useStorageApi=false}.
 *
 * <p>
 * The two tests here differ only in {@code pageSize}, which is what decides
 * whether the same 20,000 rows arrive in one page or several. Asserting on the
 * concrete {@code ResultSet} type is the only way to see the decision from
 * outside — the rows are identical either way, which is the whole point of the
 * Storage path.
 *
 * @since 4.2.0
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RealStorageApiAutoModeTest extends AbstractRealBigQueryIntegrationTest {

	/**
	 * Rows enough to clear auto's 10 MB estimate (20,000 × a nominal 1 KB ≈ 20 MB)
	 * while still fitting inside the default 50,000-row page. Generated rather than
	 * read from a table so the test needs no fixture and scans nothing.
	 */
	private static final int ROWS = 20_000;

	private static final String QUERY = "SELECT n, CAST(n AS STRING) AS s FROM UNNEST(GENERATE_ARRAY(1, " + ROWS
			+ ")) AS n";

	private Connection autoConnection(String extraParams) throws SQLException {
		String url = String.format("jdbc:bigquery:%s/%s?authType=ADC&useStorageApi=auto%s%s", TEST_PROJECT_ID,
				TEST_DATASET, extraParams, TEST_CONNECTION_DEFAULTS);
		return DriverManager.getConnection(url);
	}

	/**
	 * The #264 regression, end to end. At the default page size the whole result
	 * comes back in the first page, so auto must serve it from there.
	 */
	@Test
	void testAutoStaysOnTheRestPathWhenTheResultArrivedInOnePage() throws SQLException {
		try (Connection conn = autoConnection("");
				Statement stmt = conn.createStatement();
				ResultSet rs = stmt.executeQuery(QUERY)) {

			assertFalse(rs instanceof StorageReadResultSet,
					"auto opened a read session for a result the driver had already fetched in full");
			assertEquals(ROWS, count(rs), "the REST path must still return every row");
		}
	}

	/**
	 * The positive control. Without it the test above would pass just as well if
	 * the guard had disabled {@code auto} altogether, which is the more likely way
	 * to get this wrong.
	 */
	@Test
	void testAutoStillUsesStorageWhenTheResultSpansPages() throws SQLException {
		try (Connection conn = autoConnection("&pageSize=1000");
				Statement stmt = conn.createStatement();
				ResultSet rs = stmt.executeQuery(QUERY)) {

			assertInstanceOf(StorageReadResultSet.class, rs,
					"auto should still engage when the first page did not hold the whole result");
			assertEquals(ROWS, count(rs), "the Storage path must return every row");
		}
	}

	private static int count(ResultSet rs) throws SQLException {
		int rows = 0;
		while (rs.next()) {
			rows++;
		}
		return rows;
	}
}

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

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Date;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Advanced PreparedStatement behaviour against real BigQuery.
 *
 * <p>
 * Port of {@code PreparedStatementAdvancedTest} (issue #118). Thirteen of that
 * class's tests could not fail: six wrapped the call in a try/catch that logged
 * "(emulator limitation)" and asserted nothing in the failure branch — so
 * {@code setBytes}, {@code setFloat}, {@code setTimestamp(Calendar)} and
 * {@code setTime(Calendar)} passed with the driver throwing on every invocation
 * — and others asserted {@code paramCount >= 0} on an {@code int} or branched
 * on whether a value came back NULL. Every one of them asserts a concrete value
 * here.
 *
 * <p>
 * Two behaviours the emulator tier attributed to the emulator turn out to be
 * the driver's own, and are pinned below rather than tolerated:
 * {@code getMetaData()} always returns {@code null} before execution, and
 * {@code getParameterMetaData().getParameterCount()} counts parameters that
 * have been <em>set</em>, not placeholders in the SQL.
 *
 * <p>
 * The fixture is read-only, so it is created once for the class; the two batch
 * tests write, so they use their own tables.
 *
 * @since 1.0.111
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RealPreparedStatementAdvancedTest extends AbstractRealBigQueryIntegrationTest {

	private static final String TEST_TABLE = tableName("prep_advanced");

	@BeforeAll
	void createFixture() throws SQLException {
		createSharedTestTable(TEST_TABLE);
	}

	@AfterAll
	void dropFixture() {
		dropSharedTestTable(TEST_TABLE);
	}

	// ── Calendar-based temporal parameters ────────────────────────────────────
	// The emulator tier swallowed these entirely: try/catch around the call, a log
	// line reading "(emulator limitation)", and no assertion in the failure branch.
	// Asserting instead of logging is what surfaced #121.

	@Test
	void testSetTimestampWithCalendarBindsSuccessfully() throws SQLException {
		// Smoke check that the call completes and binds a usable TIMESTAMP rather than
		// throwing (#123). The swallowed emulator version passed either way.
		Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
		Timestamp ts = Timestamp.valueOf("2024-02-01 12:34:56");

		try (PreparedStatement pstmt = connection.prepareStatement("SELECT ? AS ts")) {
			pstmt.setTimestamp(1, ts, utc);

			try (ResultSet rs = pstmt.executeQuery()) {
				assertTrue(rs.next(), "Should have a result");
				assertNotNull(rs.getTimestamp(1), "A TIMESTAMP parameter should come back non-null");
				assertFalse(rs.wasNull(), "The bound value is not SQL NULL");
			}
		}
	}

	@Test
	void testSetTimestampWithCalendarUsesTheCalendarZone() throws SQLException {
		// 12:34:56 in Tokyo is 03:34:56Z. Before #121 the driver returned 21:34:56Z —
		// the offset applied with the wrong sign.
		Calendar tokyo = Calendar.getInstance(TimeZone.getTimeZone("Asia/Tokyo"));
		Timestamp ts = Timestamp.valueOf("2024-02-01 12:34:56");
		Instant expected = LocalDateTime.of(2024, 2, 1, 12, 34, 56).atZone(ZoneId.of("Asia/Tokyo")).toInstant();

		try (PreparedStatement pstmt = connection.prepareStatement("SELECT ? AS ts")) {
			pstmt.setTimestamp(1, ts, tokyo);

			try (ResultSet rs = pstmt.executeQuery()) {
				assertTrue(rs.next(), "Should have a result");
				assertEquals(expected, rs.getTimestamp(1).toInstant(),
						"The Calendar's zone should determine the instant");
			}
		}
	}

	@Test
	void testSetTimestampWithCalendarRoundTrips() throws SQLException {
		// Round-tripping through one Calendar must be identity in any JVM zone. Before
		// #121 it drifted by twice the offset, which is zero — and so invisible — when
		// the JVM default is UTC, as it is on CI runners.
		Calendar tokyo = Calendar.getInstance(TimeZone.getTimeZone("Asia/Tokyo"));
		Timestamp ts = Timestamp.valueOf("2024-02-01 12:34:56");

		try (PreparedStatement pstmt = connection.prepareStatement("SELECT ? AS ts")) {
			pstmt.setTimestamp(1, ts, tokyo);

			try (ResultSet rs = pstmt.executeQuery()) {
				assertTrue(rs.next(), "Should have a result");
				assertEquals(ts, rs.getTimestamp(1, tokyo), "Round-trip through one Calendar should be identity");
			}
		}
	}

	@Test
	void testSetDateWithCalendar() throws SQLException {
		// The cutoff sits well before every fixture row, so the result is the same
		// whether or not #121 shifts the DATE by a day. A boundary date here would
		// couple this test to that bug.
		Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("America/New_York"));
		Date date = Date.valueOf("2023-06-01");

		try (PreparedStatement pstmt = connection
				.prepareStatement("SELECT name FROM " + TEST_TABLE + " WHERE created_date >= ? ORDER BY name")) {
			pstmt.setDate(1, date, cal);

			try (ResultSet rs = pstmt.executeQuery()) {
				assertTrue(rs.next(), "Should have results");
				assertEquals("Alice", rs.getString("name"));
				assertTrue(rs.next());
				assertEquals("Bob", rs.getString("name"));
				assertTrue(rs.next());
				assertEquals("Charlie", rs.getString("name"));
				assertFalse(rs.next(), "All three fixture rows are after the cutoff");
			}
		}
	}

	@Test
	void testSetTimeWithCalendarBindsSuccessfully() throws SQLException {
		// Smoke check that the call completes and binds a usable TIME (#123).
		Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
		Time time = Time.valueOf("10:30:00");

		try (PreparedStatement pstmt = connection.prepareStatement("SELECT ? AS time_value")) {
			pstmt.setTime(1, time, utc);

			try (ResultSet rs = pstmt.executeQuery()) {
				assertTrue(rs.next(), "Should have a result");
				assertNotNull(rs.getTime(1), "A TIME parameter should come back non-null");
				assertFalse(rs.wasNull(), "The bound value is not SQL NULL");
			}
		}
	}

	// ── setObject with a target type ──────────────────────────────────────────

	@Test
	void testSetObjectWithTargetSqlType() throws SQLException {
		try (PreparedStatement pstmt = connection
				.prepareStatement("SELECT name FROM " + TEST_TABLE + " WHERE age = ?")) {
			pstmt.setObject(1, Integer.valueOf(30), Types.INTEGER);

			try (ResultSet rs = pstmt.executeQuery()) {
				assertTrue(rs.next(), "Should find matching row");
				assertEquals("Alice", rs.getString("name"));
			}
		}
	}

	@Test
	void testSetObjectStringToInteger() throws SQLException {
		try (PreparedStatement pstmt = connection
				.prepareStatement("SELECT name FROM " + TEST_TABLE + " WHERE age = ?")) {
			pstmt.setObject(1, "30", Types.INTEGER);

			try (ResultSet rs = pstmt.executeQuery()) {
				assertTrue(rs.next(), "Should find matching row");
				assertEquals("Alice", rs.getString("name"));
			}
		}
	}

	@Test
	void testSetObjectWithScaleParameter() throws SQLException {
		// Emulator tier logged and passed on any SQLException here.
		try (PreparedStatement pstmt = connection.prepareStatement("SELECT ? AS numeric_value")) {
			pstmt.setObject(1, new BigDecimal("123.456"), Types.NUMERIC, 2);

			try (ResultSet rs = pstmt.executeQuery()) {
				assertTrue(rs.next(), "Should have a result");
				assertEquals(0, new BigDecimal("123.456").compareTo(rs.getBigDecimal(1)),
						"NUMERIC parameter should round-trip its value");
			}
		}
	}

	@Test
	void testSetObjectStringToLong() throws SQLException {
		try (PreparedStatement pstmt = connection.prepareStatement("SELECT ? AS value")) {
			pstmt.setObject(1, "999999999", Types.BIGINT);

			try (ResultSet rs = pstmt.executeQuery()) {
				assertTrue(rs.next(), "Should have a result");
				assertEquals(999999999L, rs.getLong(1));
			}
		}
	}

	@Test
	void testSetObjectStringToDouble() throws SQLException {
		try (PreparedStatement pstmt = connection.prepareStatement("SELECT ? AS value")) {
			pstmt.setObject(1, "123.456", Types.DOUBLE);

			try (ResultSet rs = pstmt.executeQuery()) {
				assertTrue(rs.next(), "Should have a result");
				assertEquals(123.456, rs.getDouble(1), 0.000001);
			}
		}
	}

	@Test
	void testSetObjectStringToBoolean() throws SQLException {
		try (PreparedStatement pstmt = connection.prepareStatement("SELECT ? AS value")) {
			pstmt.setObject(1, "true", Types.BOOLEAN);

			try (ResultSet rs = pstmt.executeQuery()) {
				assertTrue(rs.next(), "Should have a result");
				assertTrue(rs.getBoolean(1));
			}
		}
	}

	@Test
	void testSetObjectNumberToString() throws SQLException {
		try (PreparedStatement pstmt = connection.prepareStatement("SELECT ? AS value")) {
			pstmt.setObject(1, Integer.valueOf(42), Types.VARCHAR);

			try (ResultSet rs = pstmt.executeQuery()) {
				assertTrue(rs.next(), "Should have a result");
				assertEquals("42", rs.getString(1));
			}
		}
	}

	@Test
	void testSetObjectStringToDate() throws SQLException {
		try (PreparedStatement pstmt = connection.prepareStatement("SELECT ? AS value")) {
			pstmt.setObject(1, "2024-01-15", Types.DATE);

			try (ResultSet rs = pstmt.executeQuery()) {
				assertTrue(rs.next(), "Should have a result");
				assertEquals(Date.valueOf("2024-01-15"), rs.getDate(1));
			}
		}
	}

	@Test
	void testSetObjectStringToTimestamp() throws SQLException {
		// Emulator tier logged and passed on any SQLException here.
		try (PreparedStatement pstmt = connection.prepareStatement("SELECT ? AS value")) {
			pstmt.setObject(1, "2024-01-15 10:30:00", Types.TIMESTAMP);

			try (ResultSet rs = pstmt.executeQuery()) {
				assertTrue(rs.next(), "Should have a result");
				Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
				assertEquals(Timestamp.valueOf("2024-01-15 10:30:00"), rs.getTimestamp(1, utc),
						"String should convert to the same TIMESTAMP");
			}
		}
	}

	@Test
	void testSetObjectIntegerToBoolean() throws SQLException {
		try (PreparedStatement pstmt = connection.prepareStatement("SELECT ? AS value")) {
			pstmt.setObject(1, Integer.valueOf(1), Types.BOOLEAN);

			try (ResultSet rs = pstmt.executeQuery()) {
				assertTrue(rs.next(), "Should have a result");
				assertTrue(rs.getBoolean(1));
			}
		}
	}

	@Test
	void testSetObjectWithNullAndTargetType() throws SQLException {
		// Emulator tier logged whichever way this came back and asserted nothing.
		try (PreparedStatement pstmt = connection.prepareStatement("SELECT ? AS value")) {
			pstmt.setObject(1, null, Types.INTEGER);

			try (ResultSet rs = pstmt.executeQuery()) {
				assertTrue(rs.next(), "Should have a result");
				assertEquals(0, rs.getInt(1), "getInt() on SQL NULL returns 0 per the JDBC spec");
				assertTrue(rs.wasNull(), "wasNull() must report the value was SQL NULL");
			}
		}
	}

	@Test
	void testSetObjectStringToBigDecimal() throws SQLException {
		try (PreparedStatement pstmt = connection.prepareStatement("SELECT ? AS value")) {
			pstmt.setObject(1, "123.456", Types.NUMERIC);

			try (ResultSet rs = pstmt.executeQuery()) {
				assertTrue(rs.next(), "Should have a result");
				assertEquals(0, new BigDecimal("123.456").compareTo(rs.getBigDecimal(1)));
			}
		}
	}

	// ── Binary data ───────────────────────────────────────────────────────────

	@Test
	void testSetBytes() throws SQLException {
		// Emulator tier: no assertion at all in the failure branch.
		byte[] data = "Hello, BigQuery!".getBytes(StandardCharsets.UTF_8);

		try (PreparedStatement pstmt = connection.prepareStatement("SELECT ? AS binary_data")) {
			pstmt.setBytes(1, data);

			try (ResultSet rs = pstmt.executeQuery()) {
				assertTrue(rs.next(), "Should have a result");
				assertArrayEquals(data, rs.getBytes(1), "BYTES parameter should round-trip unchanged");
			}
		}
	}

	@Test
	void testSetBytesEmpty() throws SQLException {
		try (PreparedStatement pstmt = connection.prepareStatement("SELECT ? AS binary_data")) {
			pstmt.setBytes(1, new byte[0]);

			try (ResultSet rs = pstmt.executeQuery()) {
				assertTrue(rs.next(), "Should have a result");
				assertArrayEquals(new byte[0], rs.getBytes(1), "Empty BYTES should round-trip as empty, not NULL");
				assertFalse(rs.wasNull(), "Empty BYTES is not SQL NULL");
			}
		}
	}

	// ── NULL handling ─────────────────────────────────────────────────────────

	@Test
	void testSetNullWithSqlType() throws SQLException {
		try (PreparedStatement pstmt = connection
				.prepareStatement("SELECT name FROM " + TEST_TABLE + " WHERE salary = ?")) {
			pstmt.setNull(1, Types.DOUBLE);

			try (ResultSet rs = pstmt.executeQuery()) {
				assertFalse(rs.next(), "Comparison against NULL matches no rows");
			}
		}
	}

	@Test
	void testSetNullWithTypeName() throws SQLException {
		// Emulator tier logged whichever way this came back and asserted nothing.
		try (PreparedStatement pstmt = connection.prepareStatement("SELECT ? AS null_value")) {
			pstmt.setNull(1, Types.VARCHAR, "STRING");

			try (ResultSet rs = pstmt.executeQuery()) {
				assertTrue(rs.next(), "Should have a result");
				assertNull(rs.getString(1), "setNull() must produce SQL NULL, not an empty string");
				assertTrue(rs.wasNull(), "wasNull() must report the value was SQL NULL");
			}
		}
	}

	// ── Metadata ──────────────────────────────────────────────────────────────

	@Test
	void testGetParameterMetaDataCountsParametersThatHaveBeenSet() throws SQLException {
		// The emulator tier asserted `paramCount >= 0` — true of any int — and logged
		// "(emulator limitation, expected 2)" when it saw 0. That is not the emulator:
		// BQPreparedStatement.getParameterMetaData() returns parameters.size(), the
		// number of parameters *set so far*, so 0 before any setter runs is the
		// driver's own behaviour. Pinned here so a change is visible.
		//
		// NOTE: JDBC specifies the number of parameters in the statement (2 here), so
		// the pre-set value is a spec deviation. Tracked separately — this test
		// records what the driver does, it does not endorse it.
		try (PreparedStatement pstmt = connection
				.prepareStatement("SELECT name FROM " + TEST_TABLE + " WHERE age = ? AND salary > ?")) {

			ParameterMetaData pmd = pstmt.getParameterMetaData();
			assertNotNull(pmd, "ParameterMetaData should not be null");
			assertEquals(0, pmd.getParameterCount(), "Driver counts parameters set, and none have been set yet");

			pstmt.setInt(1, 30);
			pstmt.setDouble(2, 1000.0);

			assertEquals(2, pstmt.getParameterMetaData().getParameterCount(),
					"After setting both parameters the count reflects them");
		}
	}

	@Test
	void testGetMetaDataIsNullBeforeExecutionAndPresentAfter() throws SQLException {
		// The emulator tier wrapped this in `if (rsmd != null)` and logged otherwise,
		// which hid a definite contract: BQPreparedStatement.getMetaData() returns the
		// current ResultSet's metadata, so it is always null until something executes.
		try (PreparedStatement pstmt = connection
				.prepareStatement("SELECT name, age, salary FROM " + TEST_TABLE + " WHERE age > ?")) {

			assertNull(pstmt.getMetaData(), "getMetaData() is null before execution — BigQuery does not prepare");

			pstmt.setInt(1, 20);
			try (ResultSet rs = pstmt.executeQuery()) {
				assertTrue(rs.next());
				ResultSetMetaData rsmd = pstmt.getMetaData();
				assertNotNull(rsmd, "getMetaData() should be available once a ResultSet exists");
				assertEquals(3, rsmd.getColumnCount(), "SELECT of three columns");
			}
		}
	}

	// ── Execution methods ─────────────────────────────────────────────────────

	@Test
	void testExecuteReturnsTrueForSelect() throws SQLException {
		try (PreparedStatement pstmt = connection
				.prepareStatement("SELECT name FROM " + TEST_TABLE + " WHERE age = ?")) {
			pstmt.setInt(1, 30);

			assertTrue(pstmt.execute(), "execute() returns true for SELECT");

			try (ResultSet rs = pstmt.getResultSet()) {
				assertNotNull(rs, "ResultSet should be available");
				assertTrue(rs.next(), "Should have results");
				assertEquals("Alice", rs.getString("name"));
			}
		}
	}

	@Test
	void testExecuteUpdateWithSelectReturnsZero() throws SQLException {
		try (PreparedStatement pstmt = connection.prepareStatement("SELECT name FROM " + TEST_TABLE + " LIMIT 1")) {
			assertEquals(0, pstmt.executeUpdate(), "executeUpdate on SELECT reports no affected rows");
		}
	}

	// ── Parameter reuse ───────────────────────────────────────────────────────

	@Test
	void testClearParametersAndReexecute() throws SQLException {
		try (PreparedStatement pstmt = connection
				.prepareStatement("SELECT name FROM " + TEST_TABLE + " WHERE age = ?")) {
			pstmt.setInt(1, 30);
			try (ResultSet rs = pstmt.executeQuery()) {
				assertTrue(rs.next(), "Should have first result");
				assertEquals("Alice", rs.getString("name"));
			}

			pstmt.clearParameters();
			pstmt.setInt(1, 25);

			try (ResultSet rs = pstmt.executeQuery()) {
				assertTrue(rs.next(), "Should have second result");
				assertEquals("Bob", rs.getString("name"));
			}
		}
	}

	@Test
	void testMultipleExecutionsWithDifferentParameters() throws SQLException {
		try (PreparedStatement pstmt = connection
				.prepareStatement("SELECT name FROM " + TEST_TABLE + " WHERE age = ? ORDER BY name")) {

			int[] ages = {30, 25, 35};
			String[] expected = {"Alice", "Bob", "Charlie"};

			for (int i = 0; i < ages.length; i++) {
				pstmt.setInt(1, ages[i]);
				try (ResultSet rs = pstmt.executeQuery()) {
					assertTrue(rs.next(), "Should have a result for age " + ages[i]);
					assertEquals(expected[i], rs.getString("name"), "Wrong row for age " + ages[i]);
				}
			}
		}
	}

	// ── Batch ─────────────────────────────────────────────────────────────────
	// The emulator tier caught SQLFeatureNotSupportedException and logged
	// "not supported (expected)" — but the driver does support batches, so those
	// tests would have passed if the feature had been removed entirely.

	@Test
	void testAddBatchExecutesEveryParameterSet() throws SQLException {
		String table = tableName("prep_batch_add");
		try (Statement ddl = connection.createStatement()) {
			ddl.execute("CREATE OR REPLACE TABLE " + table + " (id INT64, label STRING) "
					+ "OPTIONS(expiration_timestamp = TIMESTAMP_ADD(CURRENT_TIMESTAMP(), INTERVAL 2 HOUR))");
		}
		try {
			try (PreparedStatement pstmt = connection
					.prepareStatement("INSERT INTO " + table + " (id, label) VALUES (?, ?)")) {
				pstmt.setInt(1, 100);
				pstmt.setString(2, "first");
				pstmt.addBatch();

				pstmt.setInt(1, 200);
				pstmt.setString(2, "second");
				pstmt.addBatch();

				int[] counts = pstmt.executeBatch();
				assertEquals(2, counts.length, "One entry per batched parameter set");
				for (int count : counts) {
					assertTrue(count == 1 || count == Statement.SUCCESS_NO_INFO,
							"Each entry should report one affected row or SUCCESS_NO_INFO, was " + count);
				}
			}

			try (Statement stmt = connection.createStatement();
					ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + table)) {
				assertTrue(rs.next());
				assertEquals(2, rs.getInt(1), "Both batched rows should be present");
			}
		} finally {
			executeIgnoreErrors("DROP TABLE IF EXISTS " + table);
		}
	}

	@Test
	void testClearBatchDiscardsPendingParameterSets() throws SQLException {
		String table = tableName("prep_batch_clear");
		try (Statement ddl = connection.createStatement()) {
			ddl.execute("CREATE OR REPLACE TABLE " + table + " (id INT64) "
					+ "OPTIONS(expiration_timestamp = TIMESTAMP_ADD(CURRENT_TIMESTAMP(), INTERVAL 2 HOUR))");
		}
		try {
			try (PreparedStatement pstmt = connection.prepareStatement("INSERT INTO " + table + " (id) VALUES (?)")) {
				pstmt.setInt(1, 1);
				pstmt.addBatch();
				pstmt.setInt(1, 2);
				pstmt.addBatch();

				pstmt.clearBatch();

				assertEquals(0, pstmt.executeBatch().length, "Cleared batch executes nothing");
			}

			try (Statement stmt = connection.createStatement();
					ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + table)) {
				assertTrue(rs.next());
				assertEquals(0, rs.getInt(1), "clearBatch() must discard the pending rows, not queue them");
			}
		} finally {
			executeIgnoreErrors("DROP TABLE IF EXISTS " + table);
		}
	}

	// ── Numeric parameter types ───────────────────────────────────────────────

	@Test
	void testSetByteParameter() throws SQLException {
		try (PreparedStatement pstmt = connection.prepareStatement("SELECT ? AS byte_value")) {
			pstmt.setByte(1, (byte) 42);

			try (ResultSet rs = pstmt.executeQuery()) {
				assertTrue(rs.next(), "Should have a result");
				assertEquals((byte) 42, rs.getByte(1));
			}
		}
	}

	@Test
	void testSetShortParameter() throws SQLException {
		try (PreparedStatement pstmt = connection.prepareStatement("SELECT ? AS short_value")) {
			pstmt.setShort(1, (short) 1000);

			try (ResultSet rs = pstmt.executeQuery()) {
				assertTrue(rs.next(), "Should have a result");
				assertEquals((short) 1000, rs.getShort(1));
			}
		}
	}

	@Test
	void testSetFloatParameter() throws SQLException {
		// Emulator tier: no assertion at all in the failure branch.
		try (PreparedStatement pstmt = connection.prepareStatement("SELECT ? AS float_value")) {
			pstmt.setFloat(1, 3.14f);

			try (ResultSet rs = pstmt.executeQuery()) {
				assertTrue(rs.next(), "Should have a result");
				assertEquals(3.14f, rs.getFloat(1), 0.0001f);
			}
		}
	}

	// ── Edge cases ────────────────────────────────────────────────────────────

	@Test
	void testParameterIndexBelowOneIsRejected() throws SQLException {
		// The emulator tier tried index 0 and index 2, logged whichever happened, and
		// asserted nothing. The driver validates only `index < 1`
		// (BQPreparedStatement.validateParameterIndex).
		try (PreparedStatement pstmt = connection
				.prepareStatement("SELECT name FROM " + TEST_TABLE + " WHERE age = ?")) {

			assertThrows(SQLException.class, () -> pstmt.setInt(0, 30), "Parameter index 0 is invalid in JDBC");
			assertThrows(SQLException.class, () -> pstmt.setInt(-1, 30), "Negative parameter index is invalid");

			// An index beyond the placeholder count is accepted at set time — the driver
			// grows its parameter list and BigQuery rejects the mismatch at execution.
			pstmt.setInt(2, 30);
			assertEquals(2, pstmt.getParameterMetaData().getParameterCount(),
					"Setting index 2 grows the parameter list even though the SQL has one placeholder");
		}
	}

	@Test
	void testExecuteWithoutSettingAllParameters() throws SQLException {
		try (PreparedStatement pstmt = connection
				.prepareStatement("SELECT name FROM " + TEST_TABLE + " WHERE age = ? AND salary > ?")) {
			pstmt.setInt(1, 30);

			assertThrows(SQLException.class, pstmt::executeQuery, "Executing with an unset parameter should throw");
		}
	}

	@Test
	void testReuseAfterClose() throws SQLException {
		PreparedStatement pstmt = connection.prepareStatement("SELECT name FROM " + TEST_TABLE + " WHERE age = ?");
		pstmt.setInt(1, 30);
		pstmt.close();

		assertThrows(SQLException.class, pstmt::executeQuery, "Executing a closed PreparedStatement should throw");
		assertThrows(SQLException.class, () -> pstmt.setInt(1, 25),
				"Setting a parameter on a closed PreparedStatement should throw");
	}

	@Test
	void testGetConnection() throws SQLException {
		try (PreparedStatement pstmt = connection.prepareStatement("SELECT ? AS value")) {
			Connection conn = pstmt.getConnection();
			assertSame(connection, conn, "Should return the connection that created it");
		}
	}
}

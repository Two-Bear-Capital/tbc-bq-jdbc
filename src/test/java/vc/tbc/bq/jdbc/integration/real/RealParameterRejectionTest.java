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
import vc.tbc.bq.jdbc.exception.BQSQLException;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A value BigQuery's client rejects must arrive as a {@link SQLException}
 * (issue #227).
 *
 * <p>
 * {@code QueryParameterValue}'s factories validate client-side and signal a bad
 * value with {@link IllegalArgumentException}. Nothing wrapped that, so it
 * escaped a JDBC method declaring {@code throws SQLException} — a caller's
 * {@code catch (SQLException)} did not catch it, and the message named neither
 * the parameter nor the driver.
 *
 * <p>
 * These run in the real tier because the rejections come from the BigQuery
 * client's own validators, and pinning them means using values it actually
 * refuses rather than values a mock pretends to.
 *
 * @since 3.2.0
 */
class RealParameterRejectionTest extends AbstractRealBigQueryIntegrationTest {

	/**
	 * An array element bound against a temporal base type reaches the client's own
	 * validator: {@code setArray} converts every element with {@code toString()}
	 * and hands it to {@code QueryParameterValue.of(String, TIME)}, which parses
	 * it.
	 *
	 * <p>
	 * This is the path that still reaches an unwrapped rejection after #226 fixed
	 * the scalar TIMESTAMP and TIME setters, which is why the tests here use it
	 * rather than a plain setter. The assertion is on this driver's own wording:
	 * {@code ParameterConverter} already wrapped its own conversions, so a test
	 * that only checked for {@code SQLException} would have passed before the fix.
	 */
	private static java.sql.Array badTimeArray(java.sql.Connection conn) throws SQLException {
		return conn.createArrayOf("TIME", new Object[]{"not-a-time"});
	}

	@Test
	void aRejectedArrayElementIsASqlExceptionNotAnIllegalArgument() throws SQLException {
		try (PreparedStatement stmt = connection.prepareStatement("SELECT ? AS v")) {
			SQLException thrown = assertThrows(SQLException.class, () -> stmt.setArray(1, badTimeArray(connection)));

			assertTrue(thrown.getMessage().contains("Cannot bind parameter 1"),
					"expected this driver's wrapping, not ParameterConverter's: " + thrown.getMessage());
		}
	}

	@Test
	void theWrappedExceptionKeepsItsCauseAndSqlState() throws SQLException {
		try (PreparedStatement stmt = connection.prepareStatement("SELECT ? AS v")) {
			SQLException thrown = assertThrows(SQLException.class, () -> stmt.setArray(1, badTimeArray(connection)));

			assertEquals(BQSQLException.SQLSTATE_INVALID_PARAMETER_VALUE, thrown.getSQLState(),
					"a rejected value is an invalid parameter value");
			assertTrue(thrown.getCause() instanceof IllegalArgumentException,
					"the original rejection should survive as the cause, got: " + thrown.getCause());
		}
	}

	@Test
	void aRejectedValueDoesNotLeaveTheParameterHalfSet() throws SQLException {
		// The failed bind must not corrupt the statement: setting the parameter to
		// something valid afterwards still works.
		try (PreparedStatement stmt = connection.prepareStatement("SELECT ? AS v")) {
			assertThrows(SQLException.class, () -> stmt.setArray(1, badTimeArray(connection)));

			stmt.setString(1, "recovered");
			try (ResultSet rs = stmt.executeQuery()) {
				assertTrue(rs.next());
				assertEquals("recovered", rs.getString("v"));
			}
		}
	}

	@Test
	void goodValuesAreUnaffected() throws SQLException {
		// The wrapping must not swallow or alter anything on the normal path.
		assertDoesNotThrow(() -> {
			try (PreparedStatement stmt = connection.prepareStatement("SELECT ? AS a, ? AS b, ? AS c")) {
				stmt.setString(1, "x");
				stmt.setTimestamp(2, java.sql.Timestamp.valueOf("2026-07-28 12:34:56.789"));
				stmt.setTime(3, java.sql.Time.valueOf("12:34:56"));
				try (ResultSet rs = stmt.executeQuery()) {
					assertTrue(rs.next());
					assertEquals("x", rs.getString("a"));
				}
			}
		});
	}
}

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code getString} on a RANGE column.
 *
 * <p>
 * A RANGE is the one type whose {@code FieldValue} holds something other than a
 * string — the client hands back a {@code Range} object — so
 * {@code getStringValue()} threw {@code ClassCastException}: an unchecked
 * exception straight out of {@code getString}, past any
 * {@code catch (SQLException)}.
 *
 * <p>
 * Nothing could have depended on the old behaviour, because it never returned a
 * value. {@code getObject} still hands back the {@code Range}; changing that is
 * a result change and belongs with the rest of the RANGE work in 4.0.0 (#231).
 *
 * @since 3.3.0
 */
class RealRangeStringTest extends AbstractRealBigQueryIntegrationTest {

	private static final Logger logger = LoggerFactory.getLogger(RealRangeStringTest.class);

	private String readOne(String expression) throws SQLException {
		try (Statement stmt = connection.createStatement();
				ResultSet rs = stmt.executeQuery("SELECT " + expression + " AS r FROM UNNEST([1]) AS i")) {
			assertTrue(rs.next());
			String text = rs.getString("r");
			logger.info("{} -> {}", expression, text);
			return text;
		}
	}

	@Test
	void aDateRangeRendersAsItsLiteral() throws SQLException {
		// BigQuery's own half-open form, so the text can be pasted back into a query
		// rather than being a display convention of this driver's.
		assertEquals("[2020-01-01, 2020-12-31)", readOne("RANGE(DATE '2020-01-01', DATE '2020-12-31')"));
	}

	@Test
	void aDatetimeRangeRendersAsItsLiteral() throws SQLException {
		assertEquals("[2020-01-01T00:00:00, 2020-12-31T23:59:59)",
				readOne("RANGE(DATETIME '2020-01-01 00:00:00', DATETIME '2020-12-31 23:59:59')"));
	}

	@Test
	void anUnboundedEndReadsAsUnbounded() throws SQLException {
		// The absent bound is a null FieldValue inside the Range, which would
		// otherwise render as the string "null".
		assertEquals("[2020-01-01, UNBOUNDED)", readOne("RANGE<DATE> '[2020-01-01, UNBOUNDED)'"));
	}

	@Test
	void anUnboundedStartReadsAsUnbounded() throws SQLException {
		assertEquals("[UNBOUNDED, 2020-12-31)", readOne("RANGE<DATE> '[UNBOUNDED, 2020-12-31)'"));
	}

	@Test
	void aTimestampRangeUsesTheSameRenderingAsATimestampColumn() throws SQLException {
		// The Range carries raw epoch seconds, which is not what a TIMESTAMP column
		// reads as. Letting the two disagree would be exactly the drift
		// FieldValueConverter exists to prevent, so the endpoints go through the same
		// canonicalisation.
		String range = readOne("RANGE(TIMESTAMP '2020-01-01 00:00:00+00', TIMESTAMP '2020-12-31 23:59:59+00')");
		String start;
		try (Statement stmt = connection.createStatement();
				ResultSet rs = stmt.executeQuery("SELECT TIMESTAMP '2020-01-01 00:00:00+00' AS t")) {
			assertTrue(rs.next());
			start = rs.getString("t");
		}

		assertNotNull(range);
		assertTrue(range.startsWith("[" + start + ", "),
				"the range's start should read exactly as the same TIMESTAMP does alone: " + range + " vs " + start);
	}

	@Test
	void getStringNoLongerThrowsAnUncheckedException() throws SQLException {
		// The defect itself: a ClassCastException out of a method declaring
		// throws SQLException, which a caller's catch could not have caught.
		assertDoesNotThrow(() -> readOne("RANGE(DATE '2020-01-01', DATE '2020-12-31')"));
	}

	@Test
	void getObjectStillReturnsTheRange() throws SQLException {
		// Deliberately unchanged here. Returning a String instead is a result change
		// and belongs in 4.0.0 with the rest of #231; this test pins that the fix was
		// scoped to getString so the scope cannot drift unnoticed.
		try (Statement stmt = connection.createStatement();
				ResultSet rs = stmt.executeQuery(
						"SELECT RANGE(DATE '2020-01-01', DATE '2020-12-31') AS r FROM UNNEST([1]) AS i")) {
			assertTrue(rs.next());
			Object value = rs.getObject("r");
			assertNotNull(value);
			assertTrue(value.getClass().getName().contains("Range"),
					"getObject should still hand back the client's Range, got: " + value.getClass().getName());
		}
	}
}

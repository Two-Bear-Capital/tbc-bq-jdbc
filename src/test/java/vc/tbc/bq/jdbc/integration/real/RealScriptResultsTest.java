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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Multi-statement script results against real BigQuery (#191).
 *
 * <p>
 * Only the real service produces the shape this walks: a script is one job with
 * a child job per executed statement, and every fact the implementation rests
 * on — that children list newest-first, that a listed child carries a statement
 * type but no result schema, that a DDL child's fetched result carries the
 * destination table's schema — is observable nowhere else.
 *
 * @since 4.0.0
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RealScriptResultsTest extends AbstractRealBigQueryIntegrationTest {

	/** One step of a walk: either a ResultSet's first row, or an update count. */
	private record Step(boolean isResultSet, long updateCount, List<String> values) {
	}

	/**
	 * Walks every result of a statement the JDBC way, exactly as a tool would.
	 */
	private List<Step> walk(Statement stmt, boolean first) throws SQLException {
		List<Step> steps = new ArrayList<>();
		boolean isResultSet = first;
		while (true) {
			if (isResultSet) {
				List<String> values = new ArrayList<>();
				ResultSet rs = stmt.getResultSet();
				int columns = rs.getMetaData().getColumnCount();
				while (rs.next()) {
					for (int i = 1; i <= columns; i++) {
						values.add(rs.getString(i));
					}
				}
				steps.add(new Step(true, -1, values));
			} else {
				long count = stmt.getUpdateCount();
				if (count == -1) {
					break;
				}
				steps.add(new Step(false, count, List.of()));
			}
			isResultSet = stmt.getMoreResults();
			if (!isResultSet && stmt.getUpdateCount() == -1) {
				break;
			}
		}
		return steps;
	}

	@Test
	void testEveryStatementOfAScriptIsReachableInOrder() throws SQLException {
		// When: Running a three-statement script
		try (Statement stmt = connection.createStatement()) {
			List<Step> steps = walk(stmt, stmt.execute("SELECT 1 AS a; SELECT 'x' AS b; SELECT 3 AS c;"));

			// Then: All three should be reachable, in the order written. The parent
			// job carries only the last statement's result, so before #191 the first
			// call handed back 3 and the other two were unreachable
			assertEquals(3, steps.size(), "Expected one result per statement, got " + steps);
			assertTrue(steps.stream().allMatch(Step::isResultSet), steps.toString());
			assertEquals(List.of("1"), steps.get(0).values());
			assertEquals(List.of("x"), steps.get(1).values());
			assertEquals(List.of("3"), steps.get(2).values());
		}
	}

	@Test
	void testDmlAndDdlStepsReportUpdateCounts() throws SQLException {
		// When: Running a script that mixes DDL, DML and a query
		try (Statement stmt = connection.createStatement()) {
			List<Step> steps = walk(stmt, stmt.execute("CREATE TEMP TABLE s(id INT64); "
					+ "INSERT INTO s VALUES (7),(8); " + "SELECT COUNT(*) AS n FROM s;"));

			// Then: The DDL and DML steps are update counts, not empty ResultSets —
			// their fetched results carry the destination table's schema, which is
			// what makes "has columns" the wrong question
			assertEquals(3, steps.size(), steps.toString());
			assertFalse(steps.get(0).isResultSet(), "CREATE should be an update count");
			assertEquals(0, steps.get(0).updateCount());
			assertFalse(steps.get(1).isResultSet(), "INSERT should be an update count");
			assertEquals(2, steps.get(1).updateCount(), "INSERT affected two rows");
			assertTrue(steps.get(2).isResultSet());
			assertEquals(List.of("2"), steps.get(2).values());
		}
	}

	@Test
	void testOnlyExecutedStatementsAppear() throws SQLException {
		// When: Running a script whose control flow skips a branch
		try (Statement stmt = connection.createStatement()) {
			List<Step> steps = walk(stmt, stmt.execute("DECLARE x INT64 DEFAULT 5; "
					+ "IF x > 3 THEN SELECT 'big' AS v; ELSE SELECT 'small' AS v; END IF; " + "SELECT x AS final;"));

			// Then: The DECLARE and the untaken branch produce no result at all —
			// the sequence is the execution trace, not the script text
			assertEquals(2, steps.size(), steps.toString());
			assertEquals(List.of("big"), steps.get(0).values());
			assertEquals(List.of("5"), steps.get(1).values());
		}
	}

	@Test
	void testSingleStatementHasExactlyOneResult() throws SQLException {
		// When: Running an ordinary single statement
		try (Statement stmt = connection.createStatement()) {
			assertTrue(stmt.execute("SELECT 42 AS only"));

			// Then: There is one result and no second one, and getUpdateCount()
			// reports -1 afterwards as the contract requires
			assertFalse(stmt.getMoreResults(), "A single statement has no second result");
			assertEquals(-1, stmt.getUpdateCount());
			assertNull(stmt.getResultSet());
		}
	}

	@Test
	void testGetMoreResultsClosesThePreviousResultSetByDefault() throws SQLException {
		// Given: The first result of a two-statement script
		try (Statement stmt = connection.createStatement()) {
			assertTrue(stmt.execute("SELECT 1 AS a; SELECT 2 AS b;"));
			ResultSet first = stmt.getResultSet();

			// When: Advancing without asking to keep it
			assertTrue(stmt.getMoreResults());

			// Then: The previous ResultSet should be closed, per the default
			assertTrue(first.isClosed(), "CLOSE_CURRENT_RESULT should have closed it");
		}
	}

	@Test
	void testKeepCurrentResultLeavesThePreviousResultSetOpen() throws SQLException {
		// Given: The first result of a two-statement script
		try (Statement stmt = connection.createStatement()) {
			assertTrue(stmt.execute("SELECT 1 AS a; SELECT 2 AS b;"));
			ResultSet first = stmt.getResultSet();

			// When: Advancing with KEEP_CURRENT_RESULT
			assertTrue(stmt.getMoreResults(Statement.KEEP_CURRENT_RESULT));

			// Then: The caller keeps its handle on the earlier result
			assertFalse(first.isClosed(), "KEEP_CURRENT_RESULT should have left it open");
			assertTrue(first.next());
			assertEquals("1", first.getString(1));
			first.close();
		}
	}

	@Test
	void testInvalidGetMoreResultsArgumentIsRejected() throws SQLException {
		// Then: JDBC names three constants, and a value that is none of them is a
		// caller error rather than something to guess at
		try (Statement stmt = connection.createStatement()) {
			stmt.execute("SELECT 1 AS a");
			assertThrows(SQLException.class, () -> stmt.getMoreResults(-999));
		}
	}

	@Test
	void testANewExecutionDoesNotResumeThePreviousScript() throws SQLException {
		// Given: A script left half-walked
		try (Statement stmt = connection.createStatement()) {
			assertTrue(stmt.execute("SELECT 1 AS a; SELECT 2 AS b; SELECT 3 AS c;"));

			// When: The same Statement runs something else
			assertTrue(stmt.execute("SELECT 99 AS fresh"));

			// Then: The old script's remaining statements must not surface
			ResultSet rs = stmt.getResultSet();
			assertTrue(rs.next());
			assertEquals("99", rs.getString(1));
			assertFalse(stmt.getMoreResults(), "The previous script's cursor leaked into a new execution");
		}
	}
}

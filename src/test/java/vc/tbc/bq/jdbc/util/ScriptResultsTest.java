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

import com.google.api.gax.paging.Page;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.Job;
import com.google.cloud.bigquery.JobId;
import com.google.cloud.bigquery.JobStatistics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link ScriptResults}.
 *
 * @since 4.0.0
 */
@ExtendWith(MockitoExtension.class)
class ScriptResultsTest {

	/** A child job with the given creation time and statement type. */
	private static Job childJob(String id, long creationTime,
			JobStatistics.QueryStatistics.StatementType statementType) {
		Job job = mock(Job.class);
		JobStatistics.QueryStatistics statistics = mock(JobStatistics.QueryStatistics.class);
		lenient().when(statistics.getCreationTime()).thenReturn(creationTime);
		lenient().when(statistics.getStatementType()).thenReturn(statementType);
		lenient().when(job.getStatistics()).thenReturn(statistics);
		lenient().when(job.getJobId()).thenReturn(JobId.of(id));
		return job;
	}

	private static ScriptResults resultsOf(List<Job> children) {
		BigQuery bigquery = mock(BigQuery.class);
		@SuppressWarnings("unchecked")
		Page<Job> page = mock(Page.class);
		when(page.iterateAll()).thenReturn(children);
		when(bigquery.listJobs(any(BigQuery.JobListOption.class))).thenReturn(page);

		Job parent = mock(Job.class);
		when(parent.getJobId()).thenReturn(JobId.of("parent"));
		return ScriptResults.of(bigquery, parent);
	}

	@Test
	void testStatementsAreOrderedByCreationTimeNotListingOrder() {
		// Given: The order the jobs API actually returns children in — newest first
		List<Job> newestFirst = List.of(childJob("c2", 300L, JobStatistics.QueryStatistics.StatementType.SELECT),
				childJob("c1", 200L, JobStatistics.QueryStatistics.StatementType.SELECT),
				childJob("c0", 100L, JobStatistics.QueryStatistics.StatementType.SELECT));

		// When: Building the cursor
		ScriptResults results = resultsOf(newestFirst);

		// Then: It should walk them in execution order. Taking the listing order
		// would hand the caller the script's statements backwards
		assertEquals(3, results.size());
		assertTrue(results.advance());
		assertEquals("c0", results.current().getJobId().getJob());
		assertTrue(results.advance());
		assertEquals("c1", results.current().getJobId().getJob());
		assertTrue(results.advance());
		assertEquals("c2", results.current().getJobId().getJob());
	}

	@Test
	void testCursorStartsBeforeTheFirstStatement() {
		// Given: A cursor that has not been advanced
		ScriptResults results = resultsOf(
				List.of(childJob("c0", 100L, JobStatistics.QueryStatistics.StatementType.SELECT)));

		// Then: There is no current statement until it is
		assertNull(results.current());
	}

	@Test
	void testAdvanceKeepsReturningFalseOnceExhausted() {
		// Given: A one-statement script, walked to the end
		ScriptResults results = resultsOf(
				List.of(childJob("c0", 100L, JobStatistics.QueryStatistics.StatementType.SELECT)));
		assertTrue(results.advance());

		// Then: Repeated calls past the end must stay false and not walk off the
		// list — a caller loops on this
		assertFalse(results.advance());
		assertFalse(results.advance());
		assertFalse(results.advance());
		assertNull(results.current());
	}

	@Test
	void testEmptyChildListing() {
		// Given: A script whose children could not be listed
		ScriptResults results = resultsOf(List.of());

		// Then: The cursor is immediately exhausted rather than failing
		assertEquals(0, results.size());
		assertFalse(results.advance());
		assertNull(results.current());
	}

	@Test
	void testOnlySelectProducesAResultSet() {
		// Then: SELECT is the one type whose result a caller can read. The others
		// report an update count — a DDL child's fetched result carries the
		// destination table's schema, so "has columns" would say ResultSet for
		// CREATE TABLE and INSERT alike
		assertTrue(
				ScriptResults.producesResultSet(childJob("s", 1L, JobStatistics.QueryStatistics.StatementType.SELECT)));
		assertFalse(
				ScriptResults.producesResultSet(childJob("i", 1L, JobStatistics.QueryStatistics.StatementType.INSERT)));
		assertFalse(ScriptResults
				.producesResultSet(childJob("c", 1L, JobStatistics.QueryStatistics.StatementType.CREATE_TABLE)));
		assertFalse(ScriptResults.producesResultSet(childJob("u", 1L, null)));
		assertFalse(ScriptResults.producesResultSet(null));
	}

	@Test
	void testIsScriptDistinguishesAScriptFromASingleStatement() {
		// Then: Only a SCRIPT parent has child statements to walk
		assertTrue(ScriptResults.isScript(childJob("p", 1L, JobStatistics.QueryStatistics.StatementType.SCRIPT)));
		assertFalse(ScriptResults.isScript(childJob("q", 1L, JobStatistics.QueryStatistics.StatementType.SELECT)));
		assertFalse(ScriptResults.isScript(childJob("r", 1L, null)));
		assertFalse(ScriptResults.isScript(null));
	}
}

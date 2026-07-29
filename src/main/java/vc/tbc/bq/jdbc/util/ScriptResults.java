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

import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.Job;
import com.google.cloud.bigquery.JobStatistics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The statements a BigQuery script ran, in order, as a cursor.
 *
 * <p>
 * A multi-statement script is submitted as one job, and BigQuery runs each
 * statement as a <b>child job</b> of it. The parent carries only the last
 * statement's result, which is why walking the children is the only way to
 * reach the rest — and why {@code executeQuery()} on a script used to hand back
 * the <i>last</i> statement's rows while looking like it had returned the
 * first.
 *
 * <p>
 * Only statements that actually ran appear. A {@code DECLARE} produces no child
 * job at all, and an untaken {@code IF} branch produces none either, so the
 * sequence is the execution trace rather than the text.
 *
 * <p>
 * <b>Ordering is by creation time, not by listing order.</b> The jobs API
 * returns children newest-first, which is exactly backwards, and the {@code _N}
 * suffix BigQuery puts on a child job id is an undocumented implementation
 * detail. Creation time is the property that means what it says.
 *
 * @since 4.0.0
 */
public final class ScriptResults {

	private final List<Job> statements;
	private int index = -1;

	private ScriptResults(List<Job> statements) {
		this.statements = statements;
	}

	/**
	 * Whether a completed job ran a multi-statement script.
	 *
	 * @param job
	 *            the job to inspect, or null
	 * @return true when the job's statement type is {@code SCRIPT}
	 */
	public static boolean isScript(Job job) {
		if (job == null || !(job.getStatistics() instanceof JobStatistics.QueryStatistics statistics)) {
			return false;
		}
		return JobStatistics.QueryStatistics.StatementType.SCRIPT.equals(statistics.getStatementType());
	}

	/**
	 * Enumerates a script's statements, oldest first.
	 *
	 * <p>
	 * One {@code jobs.list} call. Results are not fetched here — a script of twenty
	 * statements would mean twenty result reads for a caller that looks at one, so
	 * each is read only when the cursor reaches it.
	 *
	 * @param bigquery
	 *            the client to list with
	 * @param parent
	 *            the script's parent job
	 * @return the statements in execution order, empty if none could be listed
	 */
	public static ScriptResults of(BigQuery bigquery, Job parent) {
		List<Job> children = new ArrayList<>();
		for (Job child : bigquery.listJobs(BigQuery.JobListOption.parentJobId(parent.getJobId().getJob()))
				.iterateAll()) {
			children.add(child);
		}
		children.sort(Comparator.comparingLong(ScriptResults::creationTime));
		return new ScriptResults(children);
	}

	/** Creation time, or 0 for a job whose statistics cannot be read. */
	private static long creationTime(Job job) {
		if (job.getStatistics() == null || job.getStatistics().getCreationTime() == null) {
			return 0L;
		}
		return job.getStatistics().getCreationTime();
	}

	/**
	 * Moves to the next statement.
	 *
	 * @return true when a statement is now current, false when exhausted
	 */
	public boolean advance() {
		if (index >= statements.size() - 1) {
			// Parked one past the end rather than at it, so repeated calls after
			// exhaustion keep returning false instead of walking off the list.
			index = statements.size();
			return false;
		}
		index++;
		return true;
	}

	/**
	 * The statement the cursor is on.
	 *
	 * @return the current child job, or null before the first {@link #advance()} or
	 *         after exhaustion
	 */
	public Job current() {
		return index < 0 || index >= statements.size() ? null : statements.get(index);
	}

	/**
	 * How many statements ran.
	 *
	 * @return the statement count
	 */
	public int size() {
		return statements.size();
	}

	/**
	 * Whether a statement's result is a ResultSet rather than an update count.
	 *
	 * <p>
	 * Read from the statement type, which is the only signal that survives. The two
	 * obvious alternatives are both wrong, and both look right until tried against
	 * the service:
	 *
	 * <ul>
	 * <li>The child job's <b>result schema</b> is absent on jobs enumerated by
	 * {@code jobs.list}, which carry only partial statistics. Asking the job would
	 * report "no columns" for every statement, turning three SELECTs into three
	 * update counts.
	 * <li>The <b>fetched result's</b> schema is present but describes the
	 * destination table, not a row shape the caller can read. {@code CREATE TEMP
	 * TABLE t(id INT64)} and {@code INSERT INTO t …} both come back with a
	 * one-field schema named {@code id} and zero rows, so "has columns" reports an
	 * empty ResultSet where an update count belongs.
	 * </ul>
	 *
	 * <p>
	 * Anything that is not a {@code SELECT} is treated as an update count. A future
	 * BigQuery statement type that returns rows would be reported as an update
	 * count of zero rather than misbehaving.
	 *
	 * @param job
	 *            a child job from {@link #of}
	 * @return true when the statement's result is a ResultSet
	 */
	public static boolean producesResultSet(Job job) {
		if (job == null || !(job.getStatistics() instanceof JobStatistics.QueryStatistics statistics)) {
			return false;
		}
		return JobStatistics.QueryStatistics.StatementType.SELECT.equals(statistics.getStatementType());
	}
}

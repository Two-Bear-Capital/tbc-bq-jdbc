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

import java.util.regex.Pattern;

/**
 * Validates project and dataset names before they are interpolated into query
 * text.
 *
 * <p>
 * BigQuery offers no parameter binding for the table path of a query, so a
 * driver that builds {@code INFORMATION_SCHEMA} queries has to concatenate
 * these names in. Several of them arrive from the caller —
 * {@link java.sql.DatabaseMetaData#getColumns} and its neighbours take
 * {@code catalog} and {@code schema} as literal names — and a name containing a
 * backtick closes the quoting around it and turns the rest into SQL.
 *
 * <p>
 * <b>This is a stated invariant, not a rediscovered one.</b> Most metadata
 * methods happen to be safe today because they pass {@code catalog} to
 * {@code BigQuery.listDatasets()} before building any SQL, and that API rejects
 * anything that is not a well-formed project ID. That is protection by
 * accident: nothing in the code says the value was checked, and any path that
 * skips the listing — a reasonable optimisation, and one {@code getPrimaryKeys}
 * already takes when the caller names a schema — loses it silently. Callers
 * validate here instead, next to the concatenation the check exists to protect.
 *
 * @since 2.2.1
 */
public final class BigQueryIdentifiers {

	/**
	 * The characters a BigQuery project or dataset name may contain.
	 *
	 * <p>
	 * Deliberately a character allowlist rather than a faithful transcription of
	 * either naming rule. Project IDs are lowercase, 6-63 characters, and may carry
	 * a domain prefix separated by a colon; dataset names are letters, digits and
	 * underscores. Encoding all of that would reject names BigQuery accepts the day
	 * either rule is relaxed, and the guard's job is not to predict what BigQuery
	 * will accept — it is to guarantee that whatever is interpolated cannot escape
	 * the backticks around it. Every name either service permits is a subset of
	 * this.
	 */
	private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[A-Za-z0-9_-]+");

	private BigQueryIdentifiers() {
	}

	/**
	 * Whether a name is safe to interpolate into backtick-quoted query text.
	 *
	 * @param identifier
	 *            a project or dataset name; {@code null} and empty are not safe
	 * @return true if the name contains only characters BigQuery permits in one,
	 *         and so cannot terminate the quoting around it
	 */
	public static boolean isSafe(String identifier) {
		return identifier != null && SAFE_IDENTIFIER.matcher(identifier).matches();
	}

	/**
	 * Whether a project and dataset name are both safe to interpolate.
	 *
	 * @param projectId
	 *            the project name
	 * @param datasetId
	 *            the dataset name
	 * @return true if both are safe
	 */
	public static boolean areSafe(String projectId, String datasetId) {
		return isSafe(projectId) && isSafe(datasetId);
	}
}

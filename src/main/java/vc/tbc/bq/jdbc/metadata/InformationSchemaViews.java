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
package vc.tbc.bq.jdbc.metadata;

import java.util.List;
import java.util.Locale;

/**
 * The {@code INFORMATION_SCHEMA} views the driver reports, and where each one
 * lives.
 *
 * <p>
 * BigQuery scopes these views in two places, and the two sets are
 * <b>disjoint</b> — no view exists at both. That is the fact this class
 * encodes, and it is why one list would be wrong:
 *
 * <ul>
 * <li><b>Project-scoped</b> — {@code project.INFORMATION_SCHEMA.SCHEMATA}, a
 * three-part name that maps exactly onto JDBC's catalog/schema/table. These are
 * reported as tables of a synthetic {@link #SCHEMA_NAME} schema.
 * <li><b>Dataset-scoped</b> —
 * {@code project.dataset.INFORMATION_SCHEMA.TABLES}, four parts, one more than
 * JDBC has room for. These are reported as tables of the dataset itself, under
 * the compound name {@code INFORMATION_SCHEMA.TABLES} — which BigQuery accepts
 * however a tool quotes it, whether as
 * {@code `dataset`.`INFORMATION_SCHEMA.TABLES`} or with every part quoted
 * separately.
 * </ul>
 *
 * <p>
 * The lists are static because BigQuery documents them and there is no API that
 * enumerates them. They were verified against the live service by resolving
 * every name at both scopes: the project list is the set that exists only under
 * a project, the dataset list the set that exists only under a dataset. A view
 * added by Google is missing until this list is updated, which is the cost of
 * not paying a query to discover something that changes a few times a year.
 *
 * <p>
 * Region-qualified views ({@code project.`region-us`.INFORMATION_SCHEMA.JOBS})
 * are deliberately absent. They are a superset of both lists, they need a
 * region the connection does not necessarily know, and the ones unique to that
 * scope scan the organisation's whole job history — one accidental expansion in
 * an IDE would bill for tens of gigabytes.
 *
 * @since 4.0.0
 */
public final class InformationSchemaViews {

	/** The synthetic schema name project-scoped views are reported under. */
	public static final String SCHEMA_NAME = "INFORMATION_SCHEMA";

	/**
	 * JDBC table type reported for every one of these views.
	 *
	 * <p>
	 * {@code SYSTEM TABLE} rather than {@code VIEW} so a caller can filter them out
	 * the standard way — they are catalog metadata, not user objects, and a tool
	 * listing "the views in this dataset" should not be handed seventeen of these.
	 */
	public static final String TABLE_TYPE = "SYSTEM TABLE";

	/**
	 * Views addressed as {@code project.INFORMATION_SCHEMA.<view>}.
	 *
	 * <p>
	 * Note {@code JOBS} and the other job and session views: they exist here, and
	 * reading them needs a project-level role most callers do not hold. They are
	 * still listed, because a view the caller cannot read is not the same as one
	 * that does not exist, and hiding it would need a permission probe per view.
	 */
	public static final List<String> PROJECT_SCOPED = List.of("INSIGHTS", "JOBS", "JOBS_BY_PROJECT", "JOBS_BY_USER",
			"JOBS_TIMELINE", "JOBS_TIMELINE_BY_USER", "OBJECT_PRIVILEGES", "RECOMMENDATIONS", "SCHEMATA",
			"SCHEMATA_LINKS", "SCHEMATA_OPTIONS", "SESSIONS_BY_PROJECT", "SESSIONS_BY_USER", "SHARED_DATASET_USAGE",
			"STREAMING_TIMELINE_BY_PROJECT", "TABLE_STORAGE", "TABLE_STORAGE_TIMELINE",
			"WRITE_API_TIMELINE_BY_PROJECT");

	/** Views addressed as {@code project.dataset.INFORMATION_SCHEMA.<view>}. */
	public static final List<String> DATASET_SCOPED = List.of("COLUMNS", "COLUMN_FIELD_PATHS",
			"CONSTRAINT_COLUMN_USAGE", "KEY_COLUMN_USAGE", "MATERIALIZED_VIEWS", "PARAMETERS", "PARTITIONS", "ROUTINES",
			"ROUTINE_OPTIONS", "SEARCH_INDEXES", "SEARCH_INDEX_COLUMNS", "TABLES", "TABLE_CONSTRAINTS", "TABLE_OPTIONS",
			"TABLE_SNAPSHOTS", "VECTOR_INDEXES", "VIEWS");

	private InformationSchemaViews() {
		throw new AssertionError("Utility class should not be instantiated");
	}

	/**
	 * The table name a dataset-scoped view is reported under.
	 *
	 * @param view
	 *            the bare view name, e.g. {@code TABLES}
	 * @return the compound name, e.g. {@code INFORMATION_SCHEMA.TABLES}
	 */
	public static String datasetTableName(String view) {
		return SCHEMA_NAME + "." + view;
	}

	/**
	 * Whether a table name is one this class reports inside a dataset.
	 *
	 * @param tableName
	 *            a table name from a JDBC call, or null
	 * @return true when it names a dataset-scoped view
	 */
	public static boolean isDatasetView(String tableName) {
		return tableName != null && DATASET_SCOPED.stream().anyMatch(view -> datasetTableName(view).equals(tableName));
	}

	/**
	 * Whether a schema name is the synthetic one.
	 *
	 * <p>
	 * Case-insensitive: BigQuery resolves {@code information_schema} as readily as
	 * the upper-case spelling, and a caller echoing back a name it lower-cased must
	 * still find it.
	 *
	 * @param schemaName
	 *            a schema name from a JDBC call, or null
	 * @return true when it names the synthetic schema
	 */
	public static boolean isInformationSchema(String schemaName) {
		return schemaName != null && SCHEMA_NAME.equalsIgnoreCase(schemaName.trim());
	}

	/**
	 * The fully-qualified BigQuery name of a project-scoped view.
	 *
	 * @param projectId
	 *            the project
	 * @param view
	 *            the bare view name
	 * @return a quoted, queryable name
	 */
	public static String projectScopedName(String projectId, String view) {
		return String.format("`%s`.%s.%s", projectId, SCHEMA_NAME, view.toUpperCase(Locale.ROOT));
	}

	/**
	 * The fully-qualified BigQuery name of a dataset-scoped view.
	 *
	 * @param projectId
	 *            the project
	 * @param datasetId
	 *            the dataset
	 * @param view
	 *            the bare view name
	 * @return a quoted, queryable name
	 */
	public static String datasetScopedName(String projectId, String datasetId, String view) {
		return String.format("`%s`.`%s`.%s.%s", projectId, datasetId, SCHEMA_NAME, view.toUpperCase(Locale.ROOT));
	}
}

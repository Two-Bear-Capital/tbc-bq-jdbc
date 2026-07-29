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

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link InformationSchemaViews}.
 *
 * @since 4.0.0
 */
class InformationSchemaViewsTest {

	@Test
	void testTheTwoScopesAreDisjoint() {
		// Given: The two lists
		Set<String> both = new HashSet<>(InformationSchemaViews.PROJECT_SCOPED);
		both.retainAll(InformationSchemaViews.DATASET_SCOPED);

		// Then: No view may appear in both. BigQuery resolves each name at exactly
		// one scope, so a view in both lists would be reported at a scope where it
		// does not exist and fail when queried
		assertTrue(both.isEmpty(), "A view cannot be both project- and dataset-scoped: " + both);
	}

	@Test
	void testNeitherScopeIsEmpty() {
		// Then: An empty list would silently disable half the feature
		assertFalse(InformationSchemaViews.PROJECT_SCOPED.isEmpty());
		assertFalse(InformationSchemaViews.DATASET_SCOPED.isEmpty());
	}

	@Test
	void testTheViewsUsersAskForArePresent() {
		// Then: These are the ones the JetBrains reports name, and the reason the
		// dataset scope is covered at all rather than the project scope alone
		assertTrue(InformationSchemaViews.DATASET_SCOPED.contains("TABLES"));
		assertTrue(InformationSchemaViews.DATASET_SCOPED.contains("COLUMNS"));
		assertTrue(InformationSchemaViews.DATASET_SCOPED.contains("VIEWS"));
		assertTrue(InformationSchemaViews.PROJECT_SCOPED.contains("JOBS"));
		assertTrue(InformationSchemaViews.PROJECT_SCOPED.contains("SCHEMATA"));
	}

	@Test
	void testNoDuplicatesWithinAScope() {
		// Then: A duplicate would produce two identical getTables rows
		assertEquals(InformationSchemaViews.PROJECT_SCOPED.size(),
				new HashSet<>(InformationSchemaViews.PROJECT_SCOPED).size());
		assertEquals(InformationSchemaViews.DATASET_SCOPED.size(),
				new HashSet<>(InformationSchemaViews.DATASET_SCOPED).size());
	}

	@Test
	void testDatasetTableNameIsCompound() {
		// Then: The dataset scope needs four name parts and JDBC has three, so the
		// last two are carried in the table name
		assertEquals("INFORMATION_SCHEMA.TABLES", InformationSchemaViews.datasetTableName("TABLES"));
	}

	@Test
	void testIsDatasetViewRecognisesOnlyReportedNames() {
		// Then: The compound name is a dataset view; the bare one is not, and
		// neither is an ordinary table
		assertTrue(InformationSchemaViews.isDatasetView("INFORMATION_SCHEMA.COLUMNS"));
		assertFalse(InformationSchemaViews.isDatasetView("COLUMNS"));
		assertFalse(InformationSchemaViews.isDatasetView("orders"));
		assertFalse(InformationSchemaViews.isDatasetView(null));
		// A project-scoped view is not reachable inside a dataset
		assertFalse(InformationSchemaViews.isDatasetView("INFORMATION_SCHEMA.JOBS"));
	}

	@Test
	void testIsInformationSchemaIgnoresCaseAndSurroundingSpace() {
		// Then: BigQuery resolves either spelling, so a caller echoing back a name
		// it lower-cased must still be understood
		assertTrue(InformationSchemaViews.isInformationSchema("INFORMATION_SCHEMA"));
		assertTrue(InformationSchemaViews.isInformationSchema("information_schema"));
		assertTrue(InformationSchemaViews.isInformationSchema("  INFORMATION_SCHEMA  "));
		assertFalse(InformationSchemaViews.isInformationSchema("INFORMATION_SCHEMAS"));
		assertFalse(InformationSchemaViews.isInformationSchema(null));
	}

	@Test
	void testQualifiedNamesMatchWhatBigQueryAccepts() {
		// Then: The project scope is three parts and the dataset scope four —
		// these exact strings were resolved against the live service
		assertEquals("`my-project`.INFORMATION_SCHEMA.SCHEMATA",
				InformationSchemaViews.projectScopedName("my-project", "SCHEMATA"));
		assertEquals("`my-project`.`sales`.INFORMATION_SCHEMA.TABLES",
				InformationSchemaViews.datasetScopedName("my-project", "sales", "TABLES"));
	}

	@Test
	void testTableTypeIsFilterable() {
		// Then: A caller listing user objects must be able to exclude these, which
		// needs a type distinct from TABLE and VIEW
		assertEquals("SYSTEM TABLE", InformationSchemaViews.TABLE_TYPE);
		assertFalse("VIEW".equals(InformationSchemaViews.TABLE_TYPE));
	}
}

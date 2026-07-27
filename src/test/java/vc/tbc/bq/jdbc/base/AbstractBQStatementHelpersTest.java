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
package vc.tbc.bq.jdbc.base;

import com.google.cloud.bigquery.BigQueryError;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import vc.tbc.bq.jdbc.base.AbstractBQStatement.IsSchemaMatch;
import vc.tbc.bq.jdbc.exception.BQSQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the pure helpers behind INFORMATION_SCHEMA pre-warming and
 * BigQuery error classification.
 */
class AbstractBQStatementHelpersTest {

	@Nested
	@DisplayName("substituteSchema")
	class SubstituteSchema {

		@Test
		void replacesUnquotedSchema() {
			String sql = "SELECT * FROM proj.ds_a.INFORMATION_SCHEMA.COLUMNS";
			IsSchemaMatch from = AbstractBQStatement.extractIsSchema(sql);

			assertEquals("ds_a", from.rawName());
			assertFalse(from.backtickQuoted());
			assertEquals("SELECT * FROM proj.ds_b.INFORMATION_SCHEMA.COLUMNS",
					AbstractBQStatement.substituteSchema(sql, from, "ds_b"));
		}

		@Test
		void preservesBacktickQuoting() {
			String sql = "SELECT * FROM `proj`.`ds_a`.INFORMATION_SCHEMA.TABLES";
			IsSchemaMatch from = AbstractBQStatement.extractIsSchema(sql);

			assertTrue(from.backtickQuoted());
			assertEquals("SELECT * FROM `proj`.`ds_b`.INFORMATION_SCHEMA.TABLES",
					AbstractBQStatement.substituteSchema(sql, from, "ds_b"));
		}

		@Test
		@DisplayName("substitutes a lowercase information_schema token")
		void isCaseInsensitiveOnTheInformationSchemaToken() {
			String sql = "SELECT * FROM proj.ds_a.information_schema.columns";
			IsSchemaMatch from = AbstractBQStatement.extractIsSchema(sql);

			// A case-sensitive replace would return the SQL unchanged here, silently
			// disabling pre-warming for every lowercase IS query.
			assertEquals("SELECT * FROM proj.ds_b.information_schema.columns",
					AbstractBQStatement.substituteSchema(sql, from, "ds_b"));
		}

		@Test
		@DisplayName("re-emits the token verbatim so cache keys still line up")
		void preservesOriginalTokenCasing() {
			String sql = "SELECT * FROM proj.ds_a.Information_Schema.COLUMNS";
			IsSchemaMatch from = AbstractBQStatement.extractIsSchema(sql);

			assertEquals("SELECT * FROM proj.ds_b.Information_Schema.COLUMNS",
					AbstractBQStatement.substituteSchema(sql, from, "ds_b"));
		}

		@Test
		void replacesEveryOccurrenceForSelfJoins() {
			String sql = "SELECT * FROM proj.ds_a.INFORMATION_SCHEMA.COLUMNS c "
					+ "JOIN proj.ds_a.INFORMATION_SCHEMA.TABLES t USING (table_name)";
			IsSchemaMatch from = AbstractBQStatement.extractIsSchema(sql);

			assertEquals(
					"SELECT * FROM proj.ds_b.INFORMATION_SCHEMA.COLUMNS c "
							+ "JOIN proj.ds_b.INFORMATION_SCHEMA.TABLES t USING (table_name)",
					AbstractBQStatement.substituteSchema(sql, from, "ds_b"));
		}

		@Test
		void leavesOtherDatasetsAlone() {
			String sql = "SELECT * FROM proj.ds_a.INFORMATION_SCHEMA.COLUMNS c "
					+ "JOIN proj.ds_other.INFORMATION_SCHEMA.TABLES t USING (table_name)";
			IsSchemaMatch from = AbstractBQStatement.extractIsSchema(sql);

			assertEquals(
					"SELECT * FROM proj.ds_b.INFORMATION_SCHEMA.COLUMNS c "
							+ "JOIN proj.ds_other.INFORMATION_SCHEMA.TABLES t USING (table_name)",
					AbstractBQStatement.substituteSchema(sql, from, "ds_b"));
		}

		@Test
		void treatsSchemaNameAsCaseSensitive() {
			String sql = "SELECT * FROM proj.ds_a.INFORMATION_SCHEMA.COLUMNS c "
					+ "JOIN proj.DS_A.INFORMATION_SCHEMA.TABLES t USING (table_name)";
			IsSchemaMatch from = AbstractBQStatement.extractIsSchema(sql);

			// BigQuery dataset names are case-sensitive: ds_a and DS_A are different
			// datasets and only the extracted one may be rewritten.
			assertEquals(
					"SELECT * FROM proj.ds_b.INFORMATION_SCHEMA.COLUMNS c "
							+ "JOIN proj.DS_A.INFORMATION_SCHEMA.TABLES t USING (table_name)",
					AbstractBQStatement.substituteSchema(sql, from, "ds_b"));
		}

		@Test
		void returnsNullWhenNoSchemaQualifiedReferenceIsPresent() {
			assertNull(AbstractBQStatement.extractIsSchema("SELECT * FROM INFORMATION_SCHEMA.SCHEMATA"));
		}
	}

	@Nested
	@DisplayName("sqlStateFor")
	class SqlStateFor {

		private static BigQueryError error(String reason) {
			return new BigQueryError(reason, null, "message");
		}

		@Test
		void mapsQuerySyntaxFailures() {
			assertEquals(BQSQLException.SQLSTATE_SYNTAX_ERROR, AbstractBQStatement.sqlStateFor(error("invalidQuery")));
			assertEquals(BQSQLException.SQLSTATE_SYNTAX_ERROR, AbstractBQStatement.sqlStateFor(error("invalid")));
		}

		@Test
		void mapsObjectResolutionFailures() {
			assertEquals(BQSQLException.SQLSTATE_TABLE_NOT_FOUND, AbstractBQStatement.sqlStateFor(error("notFound")));
			assertEquals(BQSQLException.SQLSTATE_TABLE_ALREADY_EXISTS,
					AbstractBQStatement.sqlStateFor(error("duplicate")));
		}

		@Test
		void mapsAccessAndResourceFailures() {
			assertEquals(BQSQLException.SQLSTATE_INSUFFICIENT_PRIVILEGE,
					AbstractBQStatement.sqlStateFor(error("accessDenied")));
			assertEquals(BQSQLException.SQLSTATE_INSUFFICIENT_RESOURCES,
					AbstractBQStatement.sqlStateFor(error("quotaExceeded")));
			assertEquals(BQSQLException.SQLSTATE_INSUFFICIENT_RESOURCES,
					AbstractBQStatement.sqlStateFor(error("rateLimitExceeded")));
			assertEquals(BQSQLException.SQLSTATE_INSUFFICIENT_RESOURCES,
					AbstractBQStatement.sqlStateFor(error("resourcesExceeded")));
			assertEquals(BQSQLException.SQLSTATE_OPERATION_CANCELED, AbstractBQStatement.sqlStateFor(error("stopped")));
		}

		@Test
		@DisplayName("falls back to a general error rather than claiming a syntax error")
		void mapsUnknownAndMissingReasonsToGeneralError() {
			assertEquals(BQSQLException.SQLSTATE_GENERAL_ERROR, AbstractBQStatement.sqlStateFor(error("backendError")));
			assertEquals(BQSQLException.SQLSTATE_GENERAL_ERROR, AbstractBQStatement.sqlStateFor(error(null)));
			assertEquals(BQSQLException.SQLSTATE_GENERAL_ERROR, AbstractBQStatement.sqlStateFor(null));
		}
	}
}

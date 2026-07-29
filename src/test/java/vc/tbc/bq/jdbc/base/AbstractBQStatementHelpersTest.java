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

import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpResponseException;
import com.google.cloud.bigquery.BigQueryError;
import com.google.cloud.bigquery.BigQueryException;
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
			assertEquals(BQSQLException.SQLSTATE_GENERAL_ERROR, AbstractBQStatement.sqlStateFor((BigQueryError) null));
		}

		/** An HTTP failure of the given status, as a cause chain. */
		private static HttpResponseException httpError(int status) {
			return new HttpResponseException.Builder(status, "denied", new HttpHeaders())
					.setContent("{\"error\":{\"message\":\"nope\"}}").build();
		}

		@Test
		@DisplayName("a rejected credential reports 28000, not a general error")
		void mapsCredentialRejectionToAuthenticationFailure() {
			// Given: The shape a credential failure takes — no BigQuery error
			// reason, because the request never reached BigQuery
			BigQueryException failure = new BigQueryException(0, "Error requesting access token",
					new java.io.IOException("Error requesting access token", httpError(403)));

			// Then: A pool branching on the SQLState class can re-authenticate
			assertEquals(BQSQLException.SQLSTATE_AUTH_FAILED, AbstractBQStatement.sqlStateFor(failure));
		}

		@Test
		void mapsAnExpiredCredentialTo28000() {
			BigQueryException failure = new BigQueryException(0, "Unauthorized",
					new java.io.IOException("token expired", httpError(401)));
			assertEquals(BQSQLException.SQLSTATE_AUTH_FAILED, AbstractBQStatement.sqlStateFor(failure));
		}

		@Test
		@DisplayName("BigQuery's own 403 stays 42501 — it is authorisation, not authentication")
		void doesNotTurnATablePermissionFailureIntoAnAuthenticationFailure() {
			// Given: A 403 that BigQuery itself sent, so it carries a reason
			BigQueryException failure = new BigQueryException(403, "Access Denied: Table …", error("accessDenied"));

			// Then: The reason wins. Reporting 28000 would send a pool off to
			// re-authenticate a credential that is working, instead of surfacing a
			// missing grant
			assertEquals(BQSQLException.SQLSTATE_INSUFFICIENT_PRIVILEGE, AbstractBQStatement.sqlStateFor(failure));
		}

		@Test
		void leavesFailuresWithNoHttpStatusAsGeneralErrors() {
			// Given: A client-side failure with nothing in the chain to classify
			BigQueryException failure = new BigQueryException(0, "connection reset",
					new java.io.IOException("connection reset"));

			// Then: Unchanged — guessing 28000 from "something failed" would be
			// worse than admitting the driver does not know
			assertEquals(BQSQLException.SQLSTATE_GENERAL_ERROR, AbstractBQStatement.sqlStateFor(failure));
			assertEquals(BQSQLException.SQLSTATE_GENERAL_ERROR,
					AbstractBQStatement.sqlStateFor((BigQueryException) null));
		}
	}
}

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
package vc.tbc.bq.jdbc;

import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.FieldValue;
import com.google.cloud.bigquery.FieldValueList;
import com.google.cloud.bigquery.Schema;
import com.google.cloud.bigquery.StandardSQLTypeName;
import com.google.cloud.bigquery.TableResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import vc.tbc.bq.jdbc.auth.ApplicationDefaultAuth;
import vc.tbc.bq.jdbc.config.ConnectionProperties;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * The Storage Read API path is an optimisation, never a requirement.
 *
 * <p>
 * It can be unavailable for several reasons a caller cannot control — Arrow
 * needing a JVM flag, no destination table on the job, an unsupported column
 * type, a read session that will not open. In every one of those cases a query
 * must still return rows, via the standard {@link BQResultSet}.
 *
 * <p>
 * This matters more than it looks. The predecessor of this test existed because
 * {@code useStorageApi} once defaulted to {@code auto} and routed large results
 * into a class that could not iterate, producing a NullPointerException on a
 * default connection string (#116). The lesson kept here is that the fallback
 * is the feature: a query that would have worked must never fail because an
 * optimisation was unavailable.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StorageApiFallbackTest {

	@Mock
	private BQConnection mockConnection;

	@Mock
	private TableResult mockTableResult;

	private static final Schema SCHEMA = Schema.of(Field.of("n", StandardSQLTypeName.INT64));

	/** Comfortably past auto mode's threshold (rows x 1 KB vs 10 MB). */
	private static final long ROWS_OVER_THRESHOLD = 50_000L;

	private static ConnectionProperties propertiesWith(String useStorageApi) {
		return new ConnectionProperties("my-project", null, null, new ApplicationDefaultAuth(), null, null, null, null,
				false, null, null, null, useStorageApi, false, null, null, null, null, null, null, null, null);
	}

	/** Exposes the inherited protected factory so the test can call it. */
	private static final class ExposedStatement extends BQStatement {
		private ExposedStatement(BQConnection connection) {
			super(connection);
		}

		private ResultSet resultSetFor(TableResult result) {
			// A null Job means no destination table, which is one of the ways the
			// Storage path becomes unavailable.
			return createResultSet(result, null);
		}
	}

	private ExposedStatement statementWith(String useStorageApi) {
		when(mockConnection.getProperties()).thenReturn(propertiesWith(useStorageApi));
		when(mockConnection.getBigQuery()).thenReturn(null);
		return new ExposedStatement(mockConnection);
	}

	private void stubLargeResult() {
		FieldValueList row = FieldValueList.of(List.of(FieldValue.of(FieldValue.Attribute.PRIMITIVE, "1")),
				SCHEMA.getFields());
		when(mockTableResult.getTotalRows()).thenReturn(ROWS_OVER_THRESHOLD);
		when(mockTableResult.getSchema()).thenReturn(SCHEMA);
		when(mockTableResult.iterateAll()).thenReturn(List.of(row));
	}

	@ParameterizedTest
	@ValueSource(strings = {"false", "auto", "true"})
	@DisplayName("every useStorageApi setting yields a ResultSet that iterates")
	void everySettingStillReturnsAnIterableResultSet(String setting) throws SQLException {
		stubLargeResult();
		ExposedStatement statement = statementWith(setting);

		ResultSet rs = statement.resultSetFor(mockTableResult);

		assertTrue(rs.next(), "ResultSet must iterate for useStorageApi=" + setting);
		assertEquals(1, rs.getInt("n"));
	}

	@Test
	@DisplayName("a job with no destination table falls back rather than throwing")
	void noDestinationTableFallsBackToTheStandardResultSet() throws SQLException {
		stubLargeResult();
		ExposedStatement statement = statementWith("true");

		ResultSet rs = statement.resultSetFor(mockTableResult);

		// Without a destination table there is nothing to open a read session on.
		// The query still has to work.
		assertEquals(BQResultSet.class, rs.getClass(),
				"expected the standard ResultSet when no destination table is available");
		assertTrue(rs.next());
	}

	@Test
	@DisplayName("the default stays opt-in")
	void defaultPropertiesDoNotRequestTheStorageApi() {
		// Opt-in for now: the path is new, and enabling it by default would change
		// how every large query is fetched. Flipping this to "auto" is a deliberate
		// follow-up, not something to inherit by accident.
		assertEquals("false", propertiesWith(null).useStorageApi());
	}
}

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import vc.tbc.bq.jdbc.auth.ApplicationDefaultAuth;
import vc.tbc.bq.jdbc.config.ConnectionProperties;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Guards the fix for the {@code useStorageApi} NullPointerException.
 *
 * <p>
 * {@code useStorageApi} defaulted to {@code auto}, which routed any result over
 * ~10,240 rows into {@code StorageReadResultSet}. That class passes a
 * {@code null} {@code TableResult} to {@code super} and never overrides
 * {@code next()}, so the first row fetch dereferenced a null iterator and threw
 * NullPointerException — on a default connection string, with no opt-in. The
 * existing fallback in {@code createResultSet} caught only {@code SQLException}
 * thrown during construction, so it did not help.
 *
 * <p>
 * Until the Storage Read API path actually decodes rows, asking for it must
 * still yield a ResultSet that iterates.
 *
 * <p>
 * {@link #createResultSetNeverConstructsTheUnimplementedStub()} is the load
 * bearing check. The two behavioural tests below pin the contract but cannot
 * fail on the unfixed code: {@code StorageReadResultSet}'s constructor calls
 * {@code BigQueryReadClient.create()}, which throws without credentials, and
 * the old code caught that {@code SQLException} and fell back — so the NPE only
 * reproduced where the Storage client could actually be built. The bytecode
 * check has no such dependency. (The NPE itself is covered directly by
 * {@code BQResultSetTest.testNextOnNullRowIteratorThrowsSqlExceptionNotNpe}.)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StorageApiNotWiredUpTest {

	@Mock
	private BQConnection mockConnection;

	@Mock
	private TableResult mockTableResult;

	private static final Schema SCHEMA = Schema.of(Field.of("n", StandardSQLTypeName.INT64));

	/**
	 * Rows above the auto-mode threshold: {@code shouldUseStorageApi} multiplies
	 * the row count by 1 KB and compares against 10 MB, so this is well past it.
	 */
	private static final long ROWS_OVER_THRESHOLD = 50_000L;

	private static ConnectionProperties propertiesWith(String useStorageApi) {
		return new ConnectionProperties("my-project", null, null, new ApplicationDefaultAuth(), null, null, null, null,
				false, null, null, null, null, useStorageApi, false, null, null, null, null, null, null, null, null);
	}

	/** Exposes the inherited protected factory so the test can call it. */
	private static final class ExposedStatement extends BQStatement {
		private ExposedStatement(BQConnection connection) {
			super(connection);
		}

		private ResultSet resultSetFor(TableResult result) {
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

	@Test
	void autoModeOverThresholdStillReturnsAnIterableResultSet() throws SQLException {
		stubLargeResult();
		ExposedStatement statement = statementWith("auto");

		ResultSet rs = statement.resultSetFor(mockTableResult);

		// The bug: this threw NullPointerException instead of returning a row.
		assertTrue(rs.next(), "ResultSet must iterate");
		assertEquals(1, rs.getInt("n"));
		assertSame(BQResultSet.class, rs.getClass(), "must be the standard ResultSet, not the unimplemented stub");
	}

	@Test
	void explicitTrueStillReturnsAnIterableResultSet() throws SQLException {
		stubLargeResult();
		ExposedStatement statement = statementWith("true");

		ResultSet rs = statement.resultSetFor(mockTableResult);

		assertTrue(rs.next(), "ResultSet must iterate");
		assertSame(BQResultSet.class, rs.getClass(), "must be the standard ResultSet, not the unimplemented stub");
	}

	/**
	 * Descriptor of {@code StorageReadResultSet(BQStatement, TableId)}. A class
	 * only carries this string in its constant pool if it references that
	 * constructor, so its absence proves nothing constructs the stub.
	 */
	private static final String STUB_CONSTRUCTOR_DESCRIPTOR = "(Lvc/tbc/bq/jdbc/BQStatement;"
			+ "Lcom/google/cloud/bigquery/TableId;)V";

	@Test
	void createResultSetNeverConstructsTheUnimplementedStub() throws IOException {
		Path classes = Path.of("target", "classes");
		assertTrue(Files.isDirectory(classes),
				"compile the driver before running this guard: " + classes.toAbsolutePath());

		Path stub = classes.resolve("vc/tbc/bq/jdbc/storage/StorageReadResultSet.class");
		List<String> offenders = new ArrayList<>();
		try (Stream<Path> classFiles = Files.walk(classes)) {
			for (Path classFile : classFiles.filter(p -> p.toString().endsWith(".class")).toList()) {
				// The stub declares the constructor; everything else must ignore it.
				if (classFile.equals(stub)) {
					continue;
				}
				String bytecode = new String(Files.readAllBytes(classFile), StandardCharsets.ISO_8859_1);
				if (bytecode.contains(STUB_CONSTRUCTOR_DESCRIPTOR)) {
					offenders.add(classes.relativize(classFile).toString());
				}
			}
		}

		assertTrue(offenders.isEmpty(),
				"StorageReadResultSet cannot iterate — it passes a null TableResult to super and never overrides "
						+ "next(). Nothing may construct it until Arrow decoding is implemented. Offenders:\n  "
						+ String.join("\n  ", offenders));
	}

	@Test
	void defaultPropertiesDoNotRequestTheStorageApi() {
		// The default moved from "auto" to "false" so the unimplemented path is not
		// reached implicitly. Restore "auto" when StorageReadResultSet decodes rows.
		assertEquals("false", propertiesWith(null).useStorageApi());
	}
}

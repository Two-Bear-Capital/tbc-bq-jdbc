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

import com.google.api.gax.paging.Page;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.Dataset;
import com.google.cloud.bigquery.DatasetId;
import com.google.cloud.bigquery.FieldValueList;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.Table;
import com.google.cloud.bigquery.TableResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vc.tbc.bq.jdbc.BQConnection;
import vc.tbc.bq.jdbc.config.ConnectionProperties;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Tests for BQDatabaseMetaData focusing on non-API-dependent methods.
 *
 * @since 1.0.15
 */
@ExtendWith(MockitoExtension.class)
class BQDatabaseMetaDataTest {

	@Mock
	private BQConnection connection;

	@Mock
	private ConnectionProperties properties;

	@Mock
	private BigQuery bigQuery;

	@Mock
	private Page<Dataset> emptyDatasetPage;

	@Mock
	private Page<Dataset> datasetPage;

	@Mock
	private Dataset dataset;

	@Mock
	private Page<Table> emptyTablePage;

	@Mock
	private TableResult tableResult;

	private BQDatabaseMetaData metaData;

	@BeforeEach
	void setUp() throws SQLException {
		lenient().when(connection.getProperties()).thenReturn(properties);
		lenient().when(properties.projectId()).thenReturn("test-project");
		// The connection resolves a null catalog argument through this, so a mock
		// that does not model it makes every metadata call list a null project.
		lenient().when(connection.getCurrentCatalog()).thenReturn("test-project");
		lenient().when(properties.metadataCacheEnabled()).thenReturn(false);
		metaData = new BQDatabaseMetaData(connection);
	}

	// Product Information Tests

	@Test
	void testGetDatabaseProductName() throws SQLException {
		// When: Getting product name
		String name = metaData.getDatabaseProductName();

		// Then: Should return BigQuery (TBC Driver)
		assertEquals("BigQuery (TBC Driver)", name);
	}

	@Test
	void testGetDatabaseProductVersion() throws SQLException {
		// When: Getting product version
		String version = metaData.getDatabaseProductVersion();

		// Then: Should return a version string
		assertNotNull(version);
		assertFalse(version.isEmpty());
	}

	@Test
	void testGetDriverName() throws SQLException {
		// When: Getting driver name
		String name = metaData.getDriverName();

		// Then: Should return driver name
		assertEquals("Two Bear Capital BigQuery JDBC Driver", name);
	}

	@Test
	void testGetDriverVersion() throws SQLException {
		// When: Getting driver version
		String version = metaData.getDriverVersion();

		// Then: Should return a version string
		assertNotNull(version);
	}

	@Test
	void testGetDriverMajorVersion() throws SQLException {
		// When: Getting major version
		int major = metaData.getDriverMajorVersion();

		// Then: It should agree with the version string. The previous assertion
		// was `major >= 0` on an int, which no implementation can fail.
		assertEquals(Integer.parseInt(metaData.getDriverVersion().split("\\.")[0]), major);
	}

	@Test
	void testGetDriverMinorVersion() throws SQLException {
		// When: Getting minor version
		int minor = metaData.getDriverMinorVersion();

		// Then: It should agree with the version string (was `minor >= 0`).
		assertEquals(Integer.parseInt(metaData.getDriverVersion().split("\\.")[1]), minor);
	}

	// JDBC Compliance Tests

	@Test
	void testGetJDBCMajorVersion() throws SQLException {
		// When: Getting JDBC major version
		int version = metaData.getJDBCMajorVersion();

		// Then: Should return 4
		assertEquals(4, version);
	}

	@Test
	void testGetJDBCMinorVersion() throws SQLException {
		// When: Getting JDBC minor version
		int version = metaData.getJDBCMinorVersion();

		// Then: Should return 3
		assertEquals(3, version);
	}

	// Boolean Capability Tests

	@Test
	void testIsReadOnly() throws SQLException {
		// When: Checking if read-only
		boolean readOnly = metaData.isReadOnly();

		// Then: Should return false (BigQuery supports DML)
		assertFalse(readOnly);
	}

	@Test
	void testSupportsTransactions() throws SQLException {
		// When: Checking transaction support
		boolean supported = metaData.supportsTransactions();

		// Then: Should return true (session-backed transactions)
		assertTrue(supported);
	}

	@Test
	void testSupportsDataManipulationTransactionsOnly() throws SQLException {
		// Then: DML (plus temp-entity DDL) is transactional; permanent DDL is not
		assertTrue(metaData.supportsDataManipulationTransactionsOnly());
		assertFalse(metaData.supportsDataDefinitionAndDataManipulationTransactions());
	}

	@Test
	void testSupportsNamedParameters() throws SQLException {
		// Then: false. PreparedStatement binds positional ? placeholders only and
		// there is no CallableStatement, so a tool that trusted a true here would
		// generate SQL the driver cannot execute.
		assertFalse(metaData.supportsNamedParameters());
	}

	@Test
	void testSupportsResultSetTypeForwardOnly() throws SQLException {
		// When: Checking TYPE_FORWARD_ONLY support
		boolean supported = metaData.supportsResultSetType(ResultSet.TYPE_FORWARD_ONLY);

		// Then: Should return true
		assertTrue(supported);
	}

	@Test
	void testSupportsResultSetTypeScrollInsensitive() throws SQLException {
		// When: Checking TYPE_SCROLL_INSENSITIVE support
		boolean supported = metaData.supportsResultSetType(ResultSet.TYPE_SCROLL_INSENSITIVE);

		// Then: Should return false
		assertFalse(supported);
	}

	@Test
	void testSupportsResultSetConcurrencyReadOnly() throws SQLException {
		// When: Checking CONCUR_READ_ONLY support
		boolean supported = metaData.supportsResultSetConcurrency(ResultSet.TYPE_FORWARD_ONLY,
				ResultSet.CONCUR_READ_ONLY);

		// Then: Should return true
		assertTrue(supported);
	}

	@Test
	void testSupportsResultSetConcurrencyUpdatable() throws SQLException {
		// When: Checking CONCUR_UPDATABLE support
		boolean supported = metaData.supportsResultSetConcurrency(ResultSet.TYPE_FORWARD_ONLY,
				ResultSet.CONCUR_UPDATABLE);

		// Then: Should return false
		assertFalse(supported);
	}

	@Test
	void testSupportsUnion() throws SQLException {
		// When: Checking UNION support
		boolean supported = metaData.supportsUnion();

		// Then: Should return true
		assertTrue(supported);
	}

	@Test
	void testSupportsUnionAll() throws SQLException {
		// When: Checking UNION ALL support
		boolean supported = metaData.supportsUnionAll();

		// Then: Should return true
		assertTrue(supported);
	}

	@Test
	void testSupportsGroupBy() throws SQLException {
		// When: Checking GROUP BY support
		boolean supported = metaData.supportsGroupBy();

		// Then: Should return true
		assertTrue(supported);
	}

	@Test
	void testSupportsOrderByUnrelated() throws SQLException {
		// When: Checking ORDER BY unrelated support
		boolean supported = metaData.supportsOrderByUnrelated();

		// Then: Should return true
		assertTrue(supported);
	}

	@Test
	void testSupportsLikeEscapeClause() throws SQLException {
		// When: Checking LIKE escape clause support
		boolean supported = metaData.supportsLikeEscapeClause();

		// Then: Should return true
		assertTrue(supported);
	}

	@Test
	void testSupportsMultipleResultSets() throws SQLException {
		// When: Checking multiple result sets support
		boolean supported = metaData.supportsMultipleResultSets();

		// Then: Should return false
		assertFalse(supported);
	}

	@Test
	void testSupportsBatchUpdates() throws SQLException {
		// When: Checking batch updates support
		boolean supported = metaData.supportsBatchUpdates();

		// Then: Should return true (multi-row INSERT collapse / sequential fallback)
		assertTrue(supported);
	}

	// NULL Sorting Tests

	@Test
	void testNullsAreSortedHigh() throws SQLException {
		// When: Checking NULL sort behavior
		boolean sortedHigh = metaData.nullsAreSortedHigh();

		// Then: Should return false
		assertFalse(sortedHigh);
	}

	@Test
	void testNullsAreSortedLow() throws SQLException {
		// When: Checking NULL sort behavior
		boolean sortedLow = metaData.nullsAreSortedLow();

		// Then: Should return true
		assertTrue(sortedLow);
	}

	@Test
	void testNullsAreSortedAtStart() throws SQLException {
		// When: Checking NULL sort behavior
		boolean atStart = metaData.nullsAreSortedAtStart();

		// Then: Should return false
		assertFalse(atStart);
	}

	@Test
	void testNullsAreSortedAtEnd() throws SQLException {
		// When: Checking NULL sort behavior
		boolean atEnd = metaData.nullsAreSortedAtEnd();

		// Then: Should return false
		assertFalse(atEnd);
	}

	// Identifier Case Tests

	@Test
	void testStoresUpperCaseIdentifiers() throws SQLException {
		// When: Checking case storage
		boolean storesUpper = metaData.storesUpperCaseIdentifiers();

		// Then: Should return false
		assertFalse(storesUpper);
	}

	@Test
	void testStoresLowerCaseIdentifiers() throws SQLException {
		// When: Checking case storage
		boolean storesLower = metaData.storesLowerCaseIdentifiers();

		// Then: Should return false (BigQuery is case-sensitive)
		assertFalse(storesLower);
	}

	@Test
	void testStoresMixedCaseIdentifiers() throws SQLException {
		// When: Checking case storage
		boolean storesMixed = metaData.storesMixedCaseIdentifiers();

		// Then: Should return true
		assertTrue(storesMixed);
	}

	@Test
	void testSupportsMixedCaseIdentifiers() throws SQLException {
		// When: Checking case support
		boolean supports = metaData.supportsMixedCaseIdentifiers();

		// Then: Should return false (BigQuery doesn't support unquoted mixed case)
		assertFalse(supports);
	}

	@Test
	void testSupportsMixedCaseQuotedIdentifiers() throws SQLException {
		// When: Checking quoted identifier support
		boolean supports = metaData.supportsMixedCaseQuotedIdentifiers();

		// Then: Should return true
		assertTrue(supports);
	}

	// SQL Keyword and Limit Tests

	@Test
	void testGetMaxTableNameLength() throws SQLException {
		// When: Getting max table name length
		int max = metaData.getMaxTableNameLength();

		// Then: Should return 1024
		assertEquals(1024, max);
	}

	@Test
	void testGetMaxColumnNameLength() throws SQLException {
		// When: Getting max column name length
		int max = metaData.getMaxColumnNameLength();

		// Then: Should return 300
		assertEquals(300, max);
	}

	@Test
	void testGetMaxColumnsInTable() throws SQLException {
		// When: Getting max columns in table
		int max = metaData.getMaxColumnsInTable();

		// Then: Should return 10000
		assertEquals(10000, max);
	}

	@Test
	void testGetIdentifierQuoteString() throws SQLException {
		// When: Getting quote string
		String quote = metaData.getIdentifierQuoteString();

		// Then: Should return backtick
		assertEquals("`", quote);
	}

	@Test
	void testGetSearchStringEscape() throws SQLException {
		// When: Getting search string escape
		String escape = metaData.getSearchStringEscape();

		// Then: Should return backslash
		assertEquals("\\", escape);
	}

	@Test
	void testGetExtraNameCharacters() throws SQLException {
		// When: Getting extra name characters
		String extra = metaData.getExtraNameCharacters();

		// Then: Should return empty string
		assertEquals("", extra);
	}

	// Note: Pattern matching tests removed as matchesPattern() is a private helper
	// method
	// Pattern matching is tested indirectly through integration tests that call
	// getTables(), getColumns(), etc. with pattern parameters

	// Connection Tests

	@Test
	void testGetConnection() throws SQLException {
		// When: Getting connection
		BQConnection conn = (BQConnection) metaData.getConnection();

		// Then: Should return the connection
		assertSame(connection, conn);
	}

	// Wrapper Tests

	@Test
	void testUnwrap() throws SQLException {
		// When: Unwrapping to BQDatabaseMetaData
		BQDatabaseMetaData unwrapped = metaData.unwrap(BQDatabaseMetaData.class);

		// Then: Should return same instance
		assertSame(metaData, unwrapped);
	}

	@Test
	void testUnwrapToDatabaseMetaData() throws SQLException {
		// When: Unwrapping to DatabaseMetaData interface
		DatabaseMetaData unwrapped = metaData.unwrap(DatabaseMetaData.class);

		// Then: Should return same instance
		assertSame(metaData, unwrapped);
	}

	@Test
	void testUnwrapWithInvalidClassThrows() {
		// Then: Unwrapping to incompatible class should throw SQLException
		assertThrows(SQLException.class, () -> metaData.unwrap(String.class));
	}

	@Test
	void testIsWrapperFor() throws SQLException {
		// Then: Should return true for compatible types
		assertTrue(metaData.isWrapperFor(BQDatabaseMetaData.class));
		assertTrue(metaData.isWrapperFor(DatabaseMetaData.class));

		// And: Should return false for incompatible types
		assertFalse(metaData.isWrapperFor(String.class));
	}

	// Transaction Isolation Tests

	@Test
	void testSupportsTransactionIsolationLevel() throws SQLException {
		// Then: Snapshot isolation (reported as REPEATABLE_READ) and NONE only
		assertTrue(metaData.supportsTransactionIsolationLevel(java.sql.Connection.TRANSACTION_REPEATABLE_READ));
		assertTrue(metaData.supportsTransactionIsolationLevel(java.sql.Connection.TRANSACTION_NONE));
		assertFalse(metaData.supportsTransactionIsolationLevel(java.sql.Connection.TRANSACTION_READ_COMMITTED));
		assertFalse(metaData.supportsTransactionIsolationLevel(java.sql.Connection.TRANSACTION_READ_UNCOMMITTED));
		assertFalse(metaData.supportsTransactionIsolationLevel(java.sql.Connection.TRANSACTION_SERIALIZABLE));
	}

	@Test
	void testGetDefaultTransactionIsolation() throws SQLException {
		// When: Getting default isolation level
		int isolation = metaData.getDefaultTransactionIsolation();

		// Then: BigQuery's snapshot isolation maps to REPEATABLE_READ
		assertEquals(java.sql.Connection.TRANSACTION_REPEATABLE_READ, isolation);
	}

	// Result Set Holdability Tests

	@Test
	void testSupportsResultSetHoldability() throws SQLException {
		// When: Checking holdability support
		boolean closeSupported = metaData.supportsResultSetHoldability(ResultSet.CLOSE_CURSORS_AT_COMMIT);
		boolean holdSupported = metaData.supportsResultSetHoldability(ResultSet.HOLD_CURSORS_OVER_COMMIT);

		// Then: Should support close cursors at commit
		assertTrue(closeSupported);
		assertFalse(holdSupported);
	}

	@Test
	void testGetResultSetHoldability() throws SQLException {
		// When: Getting default holdability
		int holdability = metaData.getResultSetHoldability();

		// Then: Should return CLOSE_CURSORS_AT_COMMIT
		assertEquals(ResultSet.CLOSE_CURSORS_AT_COMMIT, holdability);
	}

	// Group A: formerly-throwing methods now return empty ResultSets

	@Test
	void testGetColumnPrivilegesReturnsEmpty() throws SQLException {
		// Given: connection is open
		lenient().when(connection.isClosed()).thenReturn(false);

		// When: calling getColumnPrivileges
		ResultSet rs = metaData.getColumnPrivileges(null, null, "table", null);

		// Then: should not throw, and result set should be empty with 8 columns
		assertNotNull(rs);
		assertEquals(8, rs.getMetaData().getColumnCount());
		assertFalse(rs.next());
	}

	@Test
	void testGetTablePrivilegesReturnsEmpty() throws SQLException {
		lenient().when(connection.isClosed()).thenReturn(false);
		ResultSet rs = metaData.getTablePrivileges(null, null, null);
		assertNotNull(rs);
		assertEquals(7, rs.getMetaData().getColumnCount());
		assertFalse(rs.next());
	}

	@Test
	void testGetBestRowIdentifierReturnsEmpty() throws SQLException {
		lenient().when(connection.isClosed()).thenReturn(false);
		ResultSet rs = metaData.getBestRowIdentifier(null, null, "table", 0, true);
		assertNotNull(rs);
		assertEquals(8, rs.getMetaData().getColumnCount());
		assertFalse(rs.next());
	}

	@Test
	void testGetVersionColumnsReturnsEmpty() throws SQLException {
		lenient().when(connection.isClosed()).thenReturn(false);
		ResultSet rs = metaData.getVersionColumns(null, null, "table");
		assertNotNull(rs);
		assertEquals(8, rs.getMetaData().getColumnCount());
		assertFalse(rs.next());
	}

	/**
	 * The key methods scan datasets, so with a project that has none they must
	 * still produce a well-formed, empty result rather than failing. The rows
	 * themselves need real constraint data and are covered by
	 * {@link KeyConstraintsTest} and the real-BigQuery integration tier.
	 */
	@Test
	void testGetCrossReferenceReturnsEmptyWhenProjectHasNoDatasets() throws SQLException {
		lenient().when(connection.isClosed()).thenReturn(false);
		lenient().when(connection.getBigQuery()).thenReturn(bigQuery);
		lenient().when(bigQuery.listDatasets(anyString())).thenReturn(emptyDatasetPage);
		lenient().when(emptyDatasetPage.iterateAll()).thenReturn(java.util.List.of());

		ResultSet rs = metaData.getCrossReference(null, null, "parent", null, null, "child");

		assertNotNull(rs);
		assertEquals(14, rs.getMetaData().getColumnCount());
		assertFalse(rs.next());
	}

	@Test
	void testGetPrimaryKeysReturnsEmptyWhenProjectHasNoDatasets() throws SQLException {
		lenient().when(connection.isClosed()).thenReturn(false);
		lenient().when(connection.getBigQuery()).thenReturn(bigQuery);
		lenient().when(bigQuery.listDatasets(anyString())).thenReturn(emptyDatasetPage);
		lenient().when(emptyDatasetPage.iterateAll()).thenReturn(java.util.List.of());

		ResultSet rs = metaData.getPrimaryKeys(null, null, "orders");

		assertNotNull(rs);
		assertEquals(6, rs.getMetaData().getColumnCount());
		assertFalse(rs.next());
	}

	/**
	 * An empty schema means "without a schema", which no BigQuery table can be, so
	 * the answer is empty — and reaching it must cost no dataset listing and no
	 * BigQuery query at all.
	 */
	@Test
	void testGetImportedKeysWithEmptySchemaQueriesNothing() throws SQLException {
		lenient().when(connection.isClosed()).thenReturn(false);

		ResultSet rs = metaData.getImportedKeys(null, "", "orders");

		assertNotNull(rs);
		assertFalse(rs.next());
		verifyNoInteractions(bigQuery);
	}

	/**
	 * BigQuery cannot parameterise the table path of a query, so the metadata
	 * methods interpolate {@code catalog} into {@code INFORMATION_SCHEMA} query
	 * text. A catalog carrying a backtick would close the quoting around it and
	 * append its own SQL.
	 *
	 * <p>
	 * In production these methods are shielded by an accident:
	 * {@code BigQuery.listDatasets()} runs first and rejects anything that is not a
	 * well-formed project ID. Stubbing the listing is what makes the guard
	 * reachable — and is the whole reason this test exists, since it reproduces
	 * exactly the condition a future caller that skips the listing would create.
	 */
	@ParameterizedTest
	@ValueSource(strings = {"proj`.`ds`.INFORMATION_SCHEMA.ROUTINES WHERE FALSE UNION ALL SELECT 'x', 'y' -- ",
			"proj`; DROP TABLE t; --"})
	void testUnsafeCatalogNeverReachesQueryText(String maliciousCatalog) throws Exception {
		stubOneDataset();

		metaData.getProcedures(maliciousCatalog, "shop", null).close();
		metaData.getProcedureColumns(maliciousCatalog, "shop", null, null).close();
		metaData.getFunctions(maliciousCatalog, "shop", null).close();
		metaData.getFunctionColumns(maliciousCatalog, "shop", null, null).close();
		metaData.getPseudoColumns(maliciousCatalog, "shop", null, null).close();

		verify(bigQuery, never()).query(any(QueryJobConfiguration.class));
	}

	/**
	 * The positive control for the test above. Without this, the guard could be
	 * deleted and the assertion would still pass for the wrong reason — because
	 * nothing was querying at all.
	 */
	@Test
	void testSafeCatalogDoesReachQueryText() throws Exception {
		stubOneDataset();
		lenient().when(bigQuery.query(any(QueryJobConfiguration.class))).thenReturn(tableResult);
		lenient().when(tableResult.iterateAll()).thenReturn(java.util.List.of());

		metaData.getProcedures("safe-project", "shop", null).close();

		verify(bigQuery).query(any(QueryJobConfiguration.class));
	}

	/**
	 * Both used to throw {@code SQLFeatureNotSupportedException}. The rows come
	 * from real datasets, so a mocked-empty project can only pin the shape — the
	 * routine split itself is covered against real routines in
	 * {@code RealMetadataEnhancedTest}.
	 */
	@Test
	void testGetFunctionsReturnsTheJdbcShape() throws Exception {
		lenient().when(connection.isClosed()).thenReturn(false);
		lenient().when(connection.getBigQuery()).thenReturn(bigQuery);
		lenient().when(bigQuery.listDatasets(anyString())).thenReturn(emptyDatasetPage);
		lenient().when(emptyDatasetPage.iterateAll()).thenReturn(java.util.List.of());

		try (ResultSet functions = metaData.getFunctions(null, null, null);
				ResultSet columns = metaData.getFunctionColumns(null, null, null, null)) {
			assertEquals(6, functions.getMetaData().getColumnCount());
			assertEquals("FUNCTION_CAT", functions.getMetaData().getColumnName(1));
			assertEquals(17, columns.getMetaData().getColumnCount());
			assertEquals("SPECIFIC_NAME", columns.getMetaData().getColumnName(17));
		}
	}

	/**
	 * Which pseudo columns a table has depends on its partitioning, so a mocked
	 * project can only pin the shape — {@code RealPseudoColumnMetadataTest} covers
	 * the selection against real partitioned tables.
	 */
	@Test
	void testGetPseudoColumnsReturnsTheJdbcShape() throws Exception {
		lenient().when(connection.isClosed()).thenReturn(false);
		lenient().when(connection.getBigQuery()).thenReturn(bigQuery);
		lenient().when(bigQuery.listDatasets(anyString())).thenReturn(emptyDatasetPage);
		lenient().when(emptyDatasetPage.iterateAll()).thenReturn(java.util.List.of());

		try (ResultSet rs = metaData.getPseudoColumns(null, null, null, null)) {
			assertEquals(12, rs.getMetaData().getColumnCount());
			assertEquals("TABLE_CAT", rs.getMetaData().getColumnName(1));
			assertEquals("COLUMN_USAGE", rs.getMetaData().getColumnName(9));
			assertEquals("IS_NULLABLE", rs.getMetaData().getColumnName(12));
			assertFalse(rs.next());
		}
	}

	/**
	 * An interrupt reaching a per-dataset read must leave the thread's interrupt
	 * flag set, or whoever asked for the cancellation never learns it landed.
	 *
	 * <p>
	 * The helper is invoked directly rather than through {@code getProcedures()}
	 * because in production it only ever runs on the virtual threads of the
	 * parallel scan: by the time the scan returns, the thread carrying the flag has
	 * terminated and the flag is gone. Driving it on the test's own thread is the
	 * only way to observe the thing being fixed.
	 */
	@Test
	void testInterruptedDatasetReadRestoresTheInterruptFlag() throws Exception {
		lenient().when(connection.getBigQuery()).thenReturn(bigQuery);
		lenient().when(bigQuery.query(any(QueryJobConfiguration.class)))
				.thenThrow(new InterruptedException("cancelled"));

		try {
			Object rows = invokeQueryInformationSchema();

			assertEquals(java.util.List.of(), rows, "an interrupted read contributes no rows");
			assertTrue(Thread.currentThread().isInterrupted(),
					"the interrupt flag must outlive the swallowed exception");
		} finally {
			// Clear it so the flag cannot leak into unrelated tests on this thread.
			Thread.interrupted();
		}
	}

	/**
	 * The control for the test above: only an interrupt sets the flag. Without
	 * this, catching {@code Exception} first and interrupting unconditionally would
	 * still pass.
	 */
	@Test
	void testFailedDatasetReadLeavesTheInterruptFlagAlone() throws Exception {
		lenient().when(connection.getBigQuery()).thenReturn(bigQuery);
		lenient().when(bigQuery.query(any(QueryJobConfiguration.class)))
				.thenThrow(new IllegalStateException("permission denied"));

		try {
			Object rows = invokeQueryInformationSchema();

			assertEquals(java.util.List.of(), rows, "an unreadable dataset contributes no rows");
			assertFalse(Thread.currentThread().isInterrupted(), "a plain failure is not a cancellation");
		} finally {
			Thread.interrupted();
		}
	}

	/**
	 * Calls the private per-dataset read helper on the current thread. The
	 * identifiers are deliberately safe ones so the call reaches the query rather
	 * than the identifier guard.
	 */
	private Object invokeQueryInformationSchema() throws Exception {
		java.lang.reflect.Method helper = BQDatabaseMetaData.class.getDeclaredMethod("queryInformationSchema",
				String.class, String.class, String.class, String.class, String.class,
				java.util.function.Function.class);
		helper.setAccessible(true);
		java.util.function.Function<FieldValueList, Object[]> rowMapper = row -> new Object[0];
		return helper.invoke(metaData, "test-project", "shop", "procedures", "INFORMATION_SCHEMA.ROUTINES",
				"SELECT routine_name FROM `test-project`.`shop`.INFORMATION_SCHEMA.ROUTINES", rowMapper);
	}

	/**
	 * {@code getColumns} has a second route to the same answer that uses the
	 * BigQuery API rather than SQL, so a name it cannot safely interpolate costs
	 * the caller the fast path, not their columns.
	 */
	@Test
	void testUnsafeCatalogFallsBackToTheApiForColumns() throws Exception {
		stubOneDataset();
		lenient().when(bigQuery.listTables(any(DatasetId.class))).thenReturn(emptyTablePage);
		lenient().when(emptyTablePage.iterateAll()).thenReturn(java.util.List.of());

		metaData.getColumns("proj`.`ds`.INFORMATION_SCHEMA.COLUMNS` WHERE FALSE -- ", "shop", null, null).close();

		verify(bigQuery, never()).query(any(QueryJobConfiguration.class));
		verify(bigQuery).listTables(any(DatasetId.class));
	}

	/**
	 * Makes {@code listDatasetsForProject} yield one dataset, whatever the project.
	 */
	private void stubOneDataset() {
		lenient().when(connection.getBigQuery()).thenReturn(bigQuery);
		lenient().when(bigQuery.listDatasets(anyString())).thenReturn(datasetPage);
		lenient().when(datasetPage.iterateAll()).thenReturn(java.util.List.of(dataset));
		lenient().when(dataset.getDatasetId()).thenReturn(DatasetId.of("test-project", "shop"));
	}

	/**
	 * The reason the constraint snapshot is cached per dataset rather than per
	 * call: an IDE asks these questions once per table, so a cache keyed by the
	 * arguments would turn introspecting a dataset of N tables into N BigQuery
	 * queries. All four key methods are answered from one read per dataset.
	 */
	@Test
	void testKeyLookupsShareOneQueryPerDataset() throws Exception {
		lenient().when(connection.isClosed()).thenReturn(false);
		lenient().when(connection.getBigQuery()).thenReturn(bigQuery);
		lenient().when(properties.metadataCacheEnabled()).thenReturn(true);
		lenient().when(properties.metadataCacheTtl()).thenReturn(300);
		lenient().when(properties.metadataCacheMaxRows()).thenReturn(50_000);
		lenient().when(bigQuery.query(any(QueryJobConfiguration.class))).thenReturn(tableResult);
		lenient().when(tableResult.iterateAll()).thenReturn(java.util.List.of());

		// A cache instance is shared statically per project, so a key nobody else uses
		// keeps this test independent of whatever else ran in this JVM.
		lenient().when(properties.projectId()).thenReturn("cache-sharing-project");
		BQDatabaseMetaData.clearAllSharedCaches();
		BQDatabaseMetaData cached = new BQDatabaseMetaData(connection);

		cached.getPrimaryKeys(null, "shop", "orders").close();
		cached.getPrimaryKeys(null, "shop", "customers").close();
		cached.getImportedKeys(null, "shop", "orders").close();
		cached.getCrossReference(null, "shop", "customers", null, "shop", "orders").close();

		verify(bigQuery, times(1)).query(any(QueryJobConfiguration.class));
	}

	@Test
	void testGetIndexInfoReturnsEmpty() throws SQLException {
		lenient().when(connection.isClosed()).thenReturn(false);
		ResultSet rs = metaData.getIndexInfo(null, null, "table", false, false);
		assertNotNull(rs);
		assertEquals(13, rs.getMetaData().getColumnCount());
		assertFalse(rs.next());
	}

	@Test
	void testGetUDTsReturnsEmpty() throws SQLException {
		lenient().when(connection.isClosed()).thenReturn(false);
		ResultSet rs = metaData.getUDTs(null, null, null, null);
		assertNotNull(rs);
		assertEquals(6, rs.getMetaData().getColumnCount());
		assertFalse(rs.next());
	}

	@Test
	void testGetSuperTypesReturnsEmpty() throws SQLException {
		lenient().when(connection.isClosed()).thenReturn(false);
		ResultSet rs = metaData.getSuperTypes(null, null, null);
		assertNotNull(rs);
		assertEquals(6, rs.getMetaData().getColumnCount());
		assertFalse(rs.next());
	}

	@Test
	void testGetSuperTablesReturnsEmpty() throws SQLException {
		lenient().when(connection.isClosed()).thenReturn(false);
		ResultSet rs = metaData.getSuperTables(null, null, null);
		assertNotNull(rs);
		assertEquals(4, rs.getMetaData().getColumnCount());
		assertFalse(rs.next());
	}

	/**
	 * BigQuery has no user-defined types, so empty is the correct answer rather
	 * than a failure. Both of these used to throw, which tools calling them during
	 * connection setup read as a broken driver.
	 */
	@Test
	void testGetAttributesReturnsEmpty() throws SQLException {
		lenient().when(connection.isClosed()).thenReturn(false);
		ResultSet rs = metaData.getAttributes(null, null, null, null);
		assertNotNull(rs);
		assertEquals(21, rs.getMetaData().getColumnCount());
		assertFalse(rs.next());
	}

	@Test
	void testGetClientInfoPropertiesReturnsEmpty() throws SQLException {
		lenient().when(connection.isClosed()).thenReturn(false);
		ResultSet rs = metaData.getClientInfoProperties();
		assertNotNull(rs);
		assertEquals(4, rs.getMetaData().getColumnCount());
		assertFalse(rs.next());
	}

	/**
	 * The columns are the point: a caller reading an empty result by column name
	 * still has to find the names JDBC specifies, so getting the shape wrong fails
	 * differently from finding no rows.
	 */
	@Test
	void testGetAttributesReportsTheJdbcColumnNames() throws SQLException {
		lenient().when(connection.isClosed()).thenReturn(false);
		try (ResultSet rs = metaData.getAttributes(null, null, null, null)) {
			java.sql.ResultSetMetaData md = rs.getMetaData();
			assertEquals("TYPE_CAT", md.getColumnName(1));
			assertEquals("ATTR_NAME", md.getColumnName(4));
			assertEquals("DATA_TYPE", md.getColumnName(5));
			assertEquals("ORDINAL_POSITION", md.getColumnName(16));
			assertEquals("SOURCE_DATA_TYPE", md.getColumnName(21));
		}
	}

	@Test
	void testGetClientInfoPropertiesReportsTheJdbcColumnNames() throws SQLException {
		lenient().when(connection.isClosed()).thenReturn(false);
		try (ResultSet rs = metaData.getClientInfoProperties()) {
			java.sql.ResultSetMetaData md = rs.getMetaData();
			assertEquals("NAME", md.getColumnName(1));
			assertEquals("MAX_LEN", md.getColumnName(2));
			assertEquals("DEFAULT_VALUE", md.getColumnName(3));
			assertEquals("DESCRIPTION", md.getColumnName(4));
		}
	}

	/**
	 * Both go through {@code checkClosed()} like every other metadata method, so a
	 * closed connection still fails rather than quietly answering empty.
	 */
	@Test
	void testEmptyMetadataResultsStillRejectAClosedConnection() throws SQLException {
		lenient().when(connection.isClosed()).thenReturn(true);
		assertThrows(SQLException.class, () -> metaData.getAttributes(null, null, null, null));
		assertThrows(SQLException.class, () -> metaData.getClientInfoProperties());
	}

	// Multi-project catalogs (#190)

	@Test
	void testGetCatalogsReportsTheConnectionProject() throws SQLException {
		// Given: No additional projects configured
		lenient().when(properties.additionalProjects()).thenReturn(java.util.List.of());

		// When: Listing catalogs
		try (ResultSet rs = metaData.getCatalogs()) {
			// Then: Just the connection's own project
			assertTrue(rs.next());
			assertEquals("test-project", rs.getString("TABLE_CAT"));
			assertFalse(rs.next());
		}
	}

	@Test
	void testGetCatalogsReportsConfiguredProjectsInOrder() throws SQLException {
		// Given: Two further projects, named out of order
		lenient().when(properties.additionalProjects()).thenReturn(java.util.List.of("zeta-project", "alpha-project"));

		// When: Listing catalogs
		try (ResultSet rs = metaData.getCatalogs()) {
			java.util.List<String> catalogs = new java.util.ArrayList<>();
			while (rs.next()) {
				catalogs.add(rs.getString("TABLE_CAT"));
			}

			// Then: All three, sorted — JDBC specifies getCatalogs() ordered by
			// TABLE_CAT, and the configured order is whatever the URL happened to say
			assertEquals(java.util.List.of("alpha-project", "test-project", "zeta-project"), catalogs);
		}
	}
}

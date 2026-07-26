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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vc.tbc.bq.jdbc.BQConnection;
import vc.tbc.bq.jdbc.config.ConnectionProperties;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;

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

	private BQDatabaseMetaData metaData;

	@BeforeEach
	void setUp() throws SQLException {
		lenient().when(connection.getProperties()).thenReturn(properties);
		lenient().when(properties.projectId()).thenReturn("test-project");
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
	void testGetDriverMajorVersion() {
		// When: Getting major version
		int major = metaData.getDriverMajorVersion();

		// Then: Should return positive number
		assertTrue(major >= 0);
	}

	@Test
	void testGetDriverMinorVersion() {
		// When: Getting minor version
		int minor = metaData.getDriverMinorVersion();

		// Then: Should return positive number
		assertTrue(minor >= 0);
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

	@Test
	void testGetCrossReferenceReturnsEmpty() throws SQLException {
		lenient().when(connection.isClosed()).thenReturn(false);
		ResultSet rs = metaData.getCrossReference(null, null, "parent", null, null, "child");
		assertNotNull(rs);
		assertFalse(rs.next());
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
}

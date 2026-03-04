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
package vc.tbc.bq.jdbc.integration.real;

import org.junit.jupiter.api.Test;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real BigQuery integration tests for DatabaseMetaData.
 *
 * <p>
 * Mirrors {@link vc.tbc.bq.jdbc.integration.MetadataTest} but runs against a
 * real BigQuery instance.
 *
 * @since 1.0.68
 */
class RealMetadataTest extends AbstractRealBigQueryIntegrationTest {

	@Test
	void testGetDatabaseProductName() throws SQLException {
		DatabaseMetaData metaData = connection.getMetaData();
		String productName = metaData.getDatabaseProductName();
		assertEquals("BigQuery (TBC Driver)", productName);
	}

	@Test
	void testGetDatabaseProductVersion() throws SQLException {
		DatabaseMetaData metaData = connection.getMetaData();
		String version = metaData.getDatabaseProductVersion();
		assertNotNull(version);
		assertFalse(version.isEmpty());
	}

	@Test
	void testGetDriverName() throws SQLException {
		DatabaseMetaData metaData = connection.getMetaData();
		String driverName = metaData.getDriverName();
		assertEquals("Two Bear Capital BigQuery JDBC Driver", driverName);
	}

	@Test
	void testGetDriverVersion() throws SQLException {
		DatabaseMetaData metaData = connection.getMetaData();
		String version = metaData.getDriverVersion();
		assertNotNull(version);
		assertTrue(version.startsWith("1."));
	}

	@Test
	void testGetDriverMajorMinorVersion() throws SQLException {
		DatabaseMetaData metaData = connection.getMetaData();
		assertEquals(1, metaData.getDriverMajorVersion());
		assertEquals(0, metaData.getDriverMinorVersion());
	}

	@Test
	void testGetJDBCVersion() throws SQLException {
		DatabaseMetaData metaData = connection.getMetaData();
		assertEquals(4, metaData.getJDBCMajorVersion());
		assertEquals(3, metaData.getJDBCMinorVersion());
	}

	@Test
	void testGetURL() throws SQLException {
		DatabaseMetaData metaData = connection.getMetaData();
		String url = metaData.getURL();
		assertNotNull(url);
		assertTrue(url.contains(TEST_PROJECT_ID));
	}

	@Test
	void testGetUserName() throws SQLException {
		DatabaseMetaData metaData = connection.getMetaData();
		String userName = metaData.getUserName();
		// BigQuery doesn't have a traditional username concept; null is acceptable
		assertNull(userName);
	}

	@Test
	void testSupportsTransactions() throws SQLException {
		DatabaseMetaData metaData = connection.getMetaData();
		assertFalse(metaData.supportsTransactions());
	}

	@Test
	void testSupportsSavepoints() throws SQLException {
		DatabaseMetaData metaData = connection.getMetaData();
		assertFalse(metaData.supportsSavepoints());
	}

	@Test
	void testSupportsResultSetTypes() throws SQLException {
		DatabaseMetaData metaData = connection.getMetaData();
		assertTrue(metaData.supportsResultSetType(ResultSet.TYPE_FORWARD_ONLY));
		assertFalse(metaData.supportsResultSetType(ResultSet.TYPE_SCROLL_INSENSITIVE));
		assertFalse(metaData.supportsResultSetType(ResultSet.TYPE_SCROLL_SENSITIVE));
	}

	@Test
	void testSupportsResultSetConcurrency() throws SQLException {
		DatabaseMetaData metaData = connection.getMetaData();
		assertTrue(metaData.supportsResultSetConcurrency(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY));
		assertFalse(metaData.supportsResultSetConcurrency(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_UPDATABLE));
	}

	@Test
	void testGetIdentifierQuoteString() throws SQLException {
		DatabaseMetaData metaData = connection.getMetaData();
		String quoteString = metaData.getIdentifierQuoteString();
		assertEquals("`", quoteString);
	}

	@Test
	void testGetSQLKeywords() throws SQLException {
		DatabaseMetaData metaData = connection.getMetaData();
		String keywords = metaData.getSQLKeywords();
		assertNotNull(keywords);
	}

	@Test
	void testGetCatalogTerm() throws SQLException {
		DatabaseMetaData metaData = connection.getMetaData();
		String term = metaData.getCatalogTerm();
		assertEquals("project", term);
	}

	@Test
	void testGetSchemaTerm() throws SQLException {
		DatabaseMetaData metaData = connection.getMetaData();
		String term = metaData.getSchemaTerm();
		assertEquals("dataset", term);
	}

	@Test
	void testSupportsCatalogsInDataManipulation() throws SQLException {
		DatabaseMetaData metaData = connection.getMetaData();
		assertTrue(metaData.supportsCatalogsInDataManipulation());
	}

	@Test
	void testSupportsSchemasInDataManipulation() throws SQLException {
		DatabaseMetaData metaData = connection.getMetaData();
		assertTrue(metaData.supportsSchemasInDataManipulation());
	}

	@Test
	void testGetMaxConnections() throws SQLException {
		DatabaseMetaData metaData = connection.getMetaData();
		int maxConnections = metaData.getMaxConnections();
		assertTrue(maxConnections == 0 || maxConnections > 100);
	}

	@Test
	void testGetMaxColumnsInTable() throws SQLException {
		DatabaseMetaData metaData = connection.getMetaData();
		int maxColumns = metaData.getMaxColumnsInTable();
		assertTrue(maxColumns > 0);
	}

	@Test
	void testSupportsUnion() throws SQLException {
		DatabaseMetaData metaData = connection.getMetaData();
		assertTrue(metaData.supportsUnion());
		assertTrue(metaData.supportsUnionAll());
	}

	@Test
	void testSupportsGroupBy() throws SQLException {
		DatabaseMetaData metaData = connection.getMetaData();
		assertTrue(metaData.supportsGroupBy());
	}

	@Test
	void testSupportsOrderBy() throws SQLException {
		DatabaseMetaData metaData = connection.getMetaData();
		assertTrue(metaData.supportsOrderByUnrelated());
	}

	@Test
	void testSupportsSubqueries() throws SQLException {
		DatabaseMetaData metaData = connection.getMetaData();
		assertTrue(metaData.supportsSubqueriesInComparisons());
		assertTrue(metaData.supportsSubqueriesInExists());
		assertTrue(metaData.supportsSubqueriesInIns());
	}

	@Test
	void testSupportsJoins() throws SQLException {
		DatabaseMetaData metaData = connection.getMetaData();
		assertTrue(metaData.supportsOuterJoins());
		assertTrue(metaData.supportsFullOuterJoins());
	}

	@Test
	void testIsReadOnly() throws SQLException {
		DatabaseMetaData metaData = connection.getMetaData();
		assertEquals(connection.isReadOnly(), metaData.isReadOnly());
	}
}

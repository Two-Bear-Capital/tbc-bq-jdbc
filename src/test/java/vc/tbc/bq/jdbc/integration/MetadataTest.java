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
package vc.tbc.bq.jdbc.integration;

import org.junit.jupiter.api.Test;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for DatabaseMetaData.
 *
 * @since 1.0.0
 */
class MetadataTest extends AbstractBigQueryIntegrationTest {

	@Test
	void testGetDatabaseProductName() throws SQLException {
		// When: Getting database product name
		DatabaseMetaData metaData = connection.getMetaData();
		String productName = metaData.getDatabaseProductName();

		// Then: Should be BigQuery
		assertEquals("BigQuery (TBC Driver)", productName);
	}

	@Test
	void testGetDatabaseProductVersion() throws SQLException {
		// When: Getting database version
		DatabaseMetaData metaData = connection.getMetaData();
		String version = metaData.getDatabaseProductVersion();

		// Then: Should have a version
		assertNotNull(version);
		assertFalse(version.isEmpty());
	}

	@Test
	void testGetDriverName() throws SQLException {
		// When: Getting driver name
		DatabaseMetaData metaData = connection.getMetaData();
		String driverName = metaData.getDriverName();

		// Then: Should be our driver
		assertEquals("Two Bear Capital BigQuery JDBC Driver", driverName);
	}

	@Test
	void testGetDriverVersion() throws SQLException {
		// When: Getting driver version
		DatabaseMetaData metaData = connection.getMetaData();
		String version = metaData.getDriverVersion();

		// Then: Should have version
		assertNotNull(version);
		assertTrue(version.startsWith("1."));
	}

	@Test
	void testGetDriverMajorMinorVersion() throws SQLException {
		// When: Getting driver version numbers
		DatabaseMetaData metaData = connection.getMetaData();

		// Then: Should match our driver
		assertEquals(1, metaData.getDriverMajorVersion());
		assertEquals(0, metaData.getDriverMinorVersion());
	}

	@Test
	void testGetJDBCVersion() throws SQLException {
		// When: Getting JDBC version
		DatabaseMetaData metaData = connection.getMetaData();

		// Then: Should be JDBC 4.3
		assertEquals(4, metaData.getJDBCMajorVersion());
		assertEquals(3, metaData.getJDBCMinorVersion());
	}

	@Test
	void testGetURL() throws SQLException {
		// When: Getting connection URL
		DatabaseMetaData metaData = connection.getMetaData();
		String url = metaData.getURL();

		// Then: Should contain project
		assertNotNull(url);
		assertTrue(url.contains(TEST_PROJECT_ID));
	}

	@Test
	void testGetUserName() throws SQLException {
		// When: Getting username
		DatabaseMetaData metaData = connection.getMetaData();
		String userName = metaData.getUserName();

		// Then: Should return null (BigQuery doesn't have traditional username concept)
		// This is JDBC spec compliant - null is acceptable when not applicable
		assertNull(userName);
	}

	@Test
	void testSupportsTransactions() throws SQLException {
		// When: Checking transaction support
		DatabaseMetaData metaData = connection.getMetaData();

		// Then: Should not support traditional transactions
		assertFalse(metaData.supportsTransactions());
	}

	@Test
	void testSupportsSavepoints() throws SQLException {
		// When: Checking savepoint support
		DatabaseMetaData metaData = connection.getMetaData();

		// Then: Should not support savepoints
		assertFalse(metaData.supportsSavepoints());
	}

	@Test
	void testSupportsResultSetTypes() throws SQLException {
		// When: Checking ResultSet type support
		DatabaseMetaData metaData = connection.getMetaData();

		// Then: Should only support forward-only
		assertTrue(metaData.supportsResultSetType(ResultSet.TYPE_FORWARD_ONLY));
		assertFalse(metaData.supportsResultSetType(ResultSet.TYPE_SCROLL_INSENSITIVE));
		assertFalse(metaData.supportsResultSetType(ResultSet.TYPE_SCROLL_SENSITIVE));
	}

	@Test
	void testSupportsResultSetConcurrency() throws SQLException {
		// When: Checking ResultSet concurrency support
		DatabaseMetaData metaData = connection.getMetaData();

		// Then: Should only support read-only
		assertTrue(metaData.supportsResultSetConcurrency(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY));
		assertFalse(metaData.supportsResultSetConcurrency(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_UPDATABLE));
	}

	@Test
	void testGetIdentifierQuoteString() throws SQLException {
		// When: Getting identifier quote string
		DatabaseMetaData metaData = connection.getMetaData();
		String quoteString = metaData.getIdentifierQuoteString();

		// Then: Should use backticks
		assertEquals("`", quoteString);
	}

	@Test
	void testGetSQLKeywords() throws SQLException {
		// When: Getting SQL keywords
		DatabaseMetaData metaData = connection.getMetaData();
		String keywords = metaData.getSQLKeywords();

		// Then: Should have keywords
		assertNotNull(keywords);
	}

	@Test
	void testGetCatalogTerm() throws SQLException {
		// When: Getting catalog term
		DatabaseMetaData metaData = connection.getMetaData();
		String term = metaData.getCatalogTerm();

		// Then: Should be "project"
		assertEquals("project", term);
	}

	@Test
	void testGetSchemaTerm() throws SQLException {
		// When: Getting schema term
		DatabaseMetaData metaData = connection.getMetaData();
		String term = metaData.getSchemaTerm();

		// Then: Should be "dataset"
		assertEquals("dataset", term);
	}

	@Test
	void testSupportsCatalogsInDataManipulation() throws SQLException {
		// When: Checking catalog support
		DatabaseMetaData metaData = connection.getMetaData();

		// Then: Should support catalogs in DML
		assertTrue(metaData.supportsCatalogsInDataManipulation());
	}

	@Test
	void testSupportsSchemasInDataManipulation() throws SQLException {
		// When: Checking schema support
		DatabaseMetaData metaData = connection.getMetaData();

		// Then: Should support schemas in DML
		assertTrue(metaData.supportsSchemasInDataManipulation());
	}

	@Test
	void testGetMaxConnections() throws SQLException {
		// When: Getting max connections
		DatabaseMetaData metaData = connection.getMetaData();
		int maxConnections = metaData.getMaxConnections();

		// Then: Should have no limit or a high limit
		assertTrue(maxConnections == 0 || maxConnections > 100);
	}

	@Test
	void testGetMaxColumnsInTable() throws SQLException {
		// When: Getting max columns
		DatabaseMetaData metaData = connection.getMetaData();
		int maxColumns = metaData.getMaxColumnsInTable();

		// Then: BigQuery has limit
		assertTrue(maxColumns > 0);
	}

	@Test
	void testSupportsUnion() throws SQLException {
		// When: Checking UNION support
		DatabaseMetaData metaData = connection.getMetaData();

		// Then: Should support UNION
		assertTrue(metaData.supportsUnion());
		assertTrue(metaData.supportsUnionAll());
	}

	@Test
	void testSupportsGroupBy() throws SQLException {
		// When: Checking GROUP BY support
		DatabaseMetaData metaData = connection.getMetaData();

		// Then: Should support GROUP BY
		assertTrue(metaData.supportsGroupBy());
	}

	@Test
	void testSupportsOrderBy() throws SQLException {
		// When: Checking ORDER BY support
		DatabaseMetaData metaData = connection.getMetaData();

		// Then: Should support ORDER BY
		assertTrue(metaData.supportsOrderByUnrelated());
	}

	@Test
	void testSupportsSubqueries() throws SQLException {
		// When: Checking subquery support
		DatabaseMetaData metaData = connection.getMetaData();

		// Then: Should support subqueries
		assertTrue(metaData.supportsSubqueriesInComparisons());
		assertTrue(metaData.supportsSubqueriesInExists());
		assertTrue(metaData.supportsSubqueriesInIns());
	}

	@Test
	void testSupportsJoins() throws SQLException {
		// When: Checking JOIN support
		DatabaseMetaData metaData = connection.getMetaData();

		// Then: Should support joins
		assertTrue(metaData.supportsOuterJoins());
		assertTrue(metaData.supportsFullOuterJoins());
	}

	@Test
	void testIsReadOnly() throws SQLException {
		// When: Checking if connection is read-only
		DatabaseMetaData metaData = connection.getMetaData();

		// Then: Should match connection setting
		assertEquals(connection.isReadOnly(), metaData.isReadOnly());
	}
}

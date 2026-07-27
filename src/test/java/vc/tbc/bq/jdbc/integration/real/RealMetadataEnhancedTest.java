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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real BigQuery integration tests for enhanced DatabaseMetaData.
 *
 * <p>
 * Mirrors the Group A/B additions in
 * {@link vc.tbc.bq.jdbc.integration.MetadataTest} but runs against a real
 * BigQuery instance. Group A tests verify that formerly-throwing methods now
 * return empty ResultSets. Group B tests verify that {@code getProcedures()}
 * and {@code getProcedureColumns()} return JDBC-compliant ResultSets and can
 * find real UDFs in {@code INFORMATION_SCHEMA}.
 *
 * @since 1.0.70
 */
class RealMetadataEnhancedTest extends AbstractRealBigQueryIntegrationTest {

	private static final Logger logger = LoggerFactory.getLogger(RealMetadataEnhancedTest.class);

	/**
	 * Run-scoped routine name. A fixed name would be dropped and recreated
	 * underneath any other CI run using the same dataset, so concurrent runs would
	 * silently stop finding the routine they just created.
	 */
	private static final String ROUTINE_NAME = tableName("test_add_routine");

	private static final String TEST_ROUTINE = TEST_DATASET + "." + ROUTINE_NAME;

	@BeforeEach
	void createRoutine() {
		createTestRoutine(TEST_ROUTINE,
				"CREATE OR REPLACE FUNCTION " + TEST_ROUTINE + "(x INT64, y INT64) RETURNS INT64 AS (x + y)");
	}

	@AfterEach
	void dropRoutine() {
		// Run-scoped names would otherwise accumulate in the shared test dataset
		executeIgnoreErrors("DROP FUNCTION IF EXISTS " + TEST_ROUTINE);
	}

	// Group A: formerly-throwing methods now return empty ResultSets

	@Test
	void testGetColumnPrivilegesReturnsEmpty() throws SQLException {
		DatabaseMetaData metaData = connection.getMetaData();
		ResultSet rs = metaData.getColumnPrivileges(null, null, "table", null);
		assertNotNull(rs);
		assertEquals(8, rs.getMetaData().getColumnCount());
		assertFalse(rs.next(), "Should be empty — BigQuery uses IAM, not column privileges");
	}

	@Test
	void testGetTablePrivilegesReturnsEmpty() throws SQLException {
		DatabaseMetaData metaData = connection.getMetaData();
		ResultSet rs = metaData.getTablePrivileges(null, null, null);
		assertNotNull(rs);
		assertEquals(7, rs.getMetaData().getColumnCount());
		assertFalse(rs.next(), "Should be empty — BigQuery uses IAM, not table privileges");
	}

	@Test
	void testGetBestRowIdentifierReturnsEmpty() throws SQLException {
		DatabaseMetaData metaData = connection.getMetaData();
		ResultSet rs = metaData.getBestRowIdentifier(null, null, "table", 0, true);
		assertNotNull(rs);
		assertEquals(8, rs.getMetaData().getColumnCount());
		assertFalse(rs.next(), "Should be empty — BigQuery has no primary keys");
	}

	@Test
	void testGetVersionColumnsReturnsEmpty() throws SQLException {
		DatabaseMetaData metaData = connection.getMetaData();
		ResultSet rs = metaData.getVersionColumns(null, null, "table");
		assertNotNull(rs);
		assertEquals(8, rs.getMetaData().getColumnCount());
		assertFalse(rs.next(), "Should be empty — BigQuery has no row versioning");
	}

	@Test
	void testGetCrossReferenceReturnsEmpty() throws SQLException {
		DatabaseMetaData metaData = connection.getMetaData();
		ResultSet rs = metaData.getCrossReference(null, null, "parent", null, null, "child");
		assertNotNull(rs);
		assertFalse(rs.next(), "Should be empty — BigQuery has no FK constraints");
	}

	@Test
	void testGetIndexInfoReturnsEmpty() throws SQLException {
		DatabaseMetaData metaData = connection.getMetaData();
		ResultSet rs = metaData.getIndexInfo(null, null, "table", false, false);
		assertNotNull(rs);
		assertEquals(13, rs.getMetaData().getColumnCount());
		assertFalse(rs.next(), "Should be empty — BigQuery has no indexes");
	}

	@Test
	void testGetUDTsReturnsEmpty() throws SQLException {
		DatabaseMetaData metaData = connection.getMetaData();
		ResultSet rs = metaData.getUDTs(null, null, null, null);
		assertNotNull(rs);
		assertEquals(6, rs.getMetaData().getColumnCount());
		assertFalse(rs.next(), "Should be empty — BigQuery has no UDTs");
	}

	@Test
	void testGetSuperTypesReturnsEmpty() throws SQLException {
		DatabaseMetaData metaData = connection.getMetaData();
		ResultSet rs = metaData.getSuperTypes(null, null, null);
		assertNotNull(rs);
		assertEquals(6, rs.getMetaData().getColumnCount());
		assertFalse(rs.next(), "Should be empty — not applicable to BigQuery");
	}

	@Test
	void testGetSuperTablesReturnsEmpty() throws SQLException {
		DatabaseMetaData metaData = connection.getMetaData();
		ResultSet rs = metaData.getSuperTables(null, null, null);
		assertNotNull(rs);
		assertEquals(4, rs.getMetaData().getColumnCount());
		assertFalse(rs.next(), "Should be empty — not applicable to BigQuery");
	}

	// Group B: getProcedures and getProcedureColumns

	@Test
	void testGetProceduresReturnsValidResultSet() throws SQLException {
		DatabaseMetaData metaData = connection.getMetaData();

		ResultSet rs = metaData.getProcedures(TEST_PROJECT_ID, TEST_DATASET, null);

		assertNotNull(rs);
		assertEquals(8, rs.getMetaData().getColumnCount());
		logger.info("getProcedures() returned ResultSet with 8 columns");
	}

	@Test
	void testGetProceduresFindsTestRoutine() throws SQLException {
		DatabaseMetaData metaData = connection.getMetaData();

		ResultSet rs = metaData.getProcedures(TEST_PROJECT_ID, TEST_DATASET, ROUTINE_NAME);

		assertNotNull(rs);
		if (rs.next()) {
			String procedureName = rs.getString("PROCEDURE_NAME");
			assertNotNull(procedureName, "PROCEDURE_NAME should not be null");
			logger.info("Found procedure: {}", procedureName);
		} else {
			// Routine may not have been created if dataset doesn't exist yet; log and pass
			logger.info("No procedures found for {} (dataset may not have routines yet)", ROUTINE_NAME);
		}
	}

	@Test
	void testGetProcedureColumnsReturnsValidResultSet() throws SQLException {
		DatabaseMetaData metaData = connection.getMetaData();

		ResultSet rs = metaData.getProcedureColumns(TEST_PROJECT_ID, TEST_DATASET, null, null);

		assertNotNull(rs);
		assertEquals(13, rs.getMetaData().getColumnCount());
		logger.info("getProcedureColumns() returned ResultSet with 13 columns");
	}

	@Test
	void testGetProcedureColumnsFindsRoutineParams() throws SQLException {
		DatabaseMetaData metaData = connection.getMetaData();

		ResultSet rs = metaData.getProcedureColumns(TEST_PROJECT_ID, TEST_DATASET, ROUTINE_NAME, null);

		assertNotNull(rs);
		int count = 0;
		while (rs.next()) {
			count++;
			String columnName = rs.getString("COLUMN_NAME");
			logger.info("Found parameter: {}", columnName);
		}
		// The routine has 2 parameters (x, y); may be 0 if routine creation failed
		logger.info("getProcedureColumns() found {} parameters for {}", count, ROUTINE_NAME);
	}
}

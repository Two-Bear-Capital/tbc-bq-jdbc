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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real BigQuery integration tests for enhanced DatabaseMetaData.
 *
 * <p>
 * Mirrors the Group A/B additions in
 * {@link vc.tbc.bq.jdbc.integration.MetadataTest} but runs against a real
 * BigQuery instance. Group A tests verify that formerly-throwing methods now
 * return empty ResultSets. Group B covers the routine methods against real
 * routines in {@code INFORMATION_SCHEMA} — a stored procedure, a scalar UDF and
 * a table function, so the split between {@code getProcedures()} and
 * {@code getFunctions()} is exercised in both directions rather than asserted
 * from one side.
 *
 * @since 1.0.70
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RealMetadataEnhancedTest extends AbstractRealBigQueryIntegrationTest {

	private static final Logger logger = LoggerFactory.getLogger(RealMetadataEnhancedTest.class);

	/**
	 * Run-scoped routine name. A fixed name would be dropped and recreated
	 * underneath any other CI run using the same dataset, so concurrent runs would
	 * silently stop finding the routine they just created.
	 */
	private static final String ROUTINE_NAME = tableName("test_add_routine");

	private static final String TEST_ROUTINE = TEST_DATASET + "." + ROUTINE_NAME;

	/**
	 * A stored procedure, so {@code getProcedures()} has something of its own to
	 * find. It used to be asserted against {@link #ROUTINE_NAME}, which is a
	 * function — that passed only because {@code getProcedures()} returned every
	 * routine regardless of {@code routine_type}.
	 */
	private static final String PROCEDURE_NAME = tableName("test_proc_routine");

	private static final String TEST_PROCEDURE = TEST_DATASET + "." + PROCEDURE_NAME;

	/** A table function, which JDBC reports as {@code functionReturnsTable}. */
	private static final String TABLE_FUNCTION_NAME = tableName("test_tvf_routine");

	private static final String TEST_TABLE_FUNCTION = TEST_DATASET + "." + TABLE_FUNCTION_NAME;

	@BeforeAll
	void createRoutine() throws SQLException {
		// Once per class, not per test: no test here mutates the routines, and the
		// drop/create pair was costing two BigQuery jobs for each of 13 tests
		try (Connection setup = createTestConnection(); Statement stmt = setup.createStatement()) {
			stmt.execute("CREATE OR REPLACE FUNCTION " + TEST_ROUTINE + "(x INT64, y INT64) RETURNS INT64 AS (x + y)");
			stmt.execute("CREATE OR REPLACE PROCEDURE " + TEST_PROCEDURE
					+ "(IN a INT64, OUT b STRING) BEGIN SET b = CAST(a AS STRING); END");
			stmt.execute("CREATE OR REPLACE TABLE FUNCTION " + TEST_TABLE_FUNCTION + "(n INT64) AS (SELECT n AS v)");
		}
	}

	@AfterAll
	void dropRoutine() {
		// Run-scoped names would otherwise accumulate in the shared test dataset
		try (Connection cleanup = createTestConnection(); Statement stmt = cleanup.createStatement()) {
			stmt.execute("DROP FUNCTION IF EXISTS " + TEST_ROUTINE);
			stmt.execute("DROP PROCEDURE IF EXISTS " + TEST_PROCEDURE);
			stmt.execute("DROP TABLE FUNCTION IF EXISTS " + TEST_TABLE_FUNCTION);
		} catch (SQLException e) {
			logger.debug("Ignoring error dropping routines for {}: {}", RUN_ID, e.getMessage());
		}
	}

	/** Collects one column of a result set, closing it. */
	private static java.util.List<String> collect(ResultSet rs, String column) throws SQLException {
		java.util.List<String> values = new java.util.ArrayList<>();
		try (ResultSet open = rs) {
			while (open.next()) {
				values.add(open.getString(column));
			}
		}
		return values;
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

	// Group B: routines — getProcedures/getProcedureColumns and
	// getFunctions/getFunctionColumns

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

		ResultSet rs = metaData.getProcedures(TEST_PROJECT_ID, TEST_DATASET, PROCEDURE_NAME);

		// Assert rather than tolerate: treating "not found" as a pass let
		// getProcedures() return zero rows for every real connection unnoticed
		assertNotNull(rs);
		assertTrue(rs.next(), "getProcedures() should find the procedure created in @BeforeAll: " + PROCEDURE_NAME);
		assertEquals(PROCEDURE_NAME, rs.getString("PROCEDURE_NAME"));
		assertEquals(TEST_DATASET, rs.getString("PROCEDURE_SCHEM"));
		assertEquals(PROCEDURE_NAME, rs.getString("SPECIFIC_NAME"));
	}

	/**
	 * The split, from the procedure side. This assertion used to be the opposite:
	 * the test asserted {@code getProcedures()} found {@link #ROUTINE_NAME}, which
	 * is a scalar UDF, and passed because the query filtered on nothing.
	 */
	@Test
	void testGetProceduresExcludesFunctions() throws SQLException {
		DatabaseMetaData metaData = connection.getMetaData();

		java.util.List<String> procedures = collect(metaData.getProcedures(TEST_PROJECT_ID, TEST_DATASET, null),
				"PROCEDURE_NAME");

		assertTrue(procedures.contains(PROCEDURE_NAME), "the procedure should be listed, found: " + procedures);
		assertFalse(procedures.contains(ROUTINE_NAME), "a scalar UDF is not a procedure, found: " + procedures);
		assertFalse(procedures.contains(TABLE_FUNCTION_NAME), "a table function is not a procedure: " + procedures);
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

		java.util.List<String> parameters = collect(
				metaData.getProcedureColumns(TEST_PROJECT_ID, TEST_DATASET, PROCEDURE_NAME, null), "COLUMN_NAME");

		logger.info("getProcedureColumns() found parameters {} for {}", parameters, PROCEDURE_NAME);
		assertTrue(parameters.contains("a"), "Should find parameter a, found: " + parameters);
		assertTrue(parameters.contains("b"), "Should find parameter b, found: " + parameters);
	}

	/**
	 * A procedure's parameters carry {@code parameter_mode}; a function's do not.
	 * BigQuery reports the {@code OUT} on the procedure, so this pins that the mode
	 * survives the join to {@code ROUTINES} rather than being flattened to
	 * {@code IN}.
	 */
	@Test
	void testGetProcedureColumnsReportsParameterMode() throws SQLException {
		DatabaseMetaData metaData = connection.getMetaData();

		java.util.Map<String, Integer> modes = new java.util.HashMap<>();
		try (ResultSet rs = metaData.getProcedureColumns(TEST_PROJECT_ID, TEST_DATASET, PROCEDURE_NAME, null)) {
			while (rs.next()) {
				modes.put(rs.getString("COLUMN_NAME"), (int) rs.getShort("COLUMN_TYPE"));
			}
		}

		assertEquals(DatabaseMetaData.procedureColumnIn, modes.get("a"), "a is declared IN");
		assertEquals(DatabaseMetaData.procedureColumnOut, modes.get("b"), "b is declared OUT");
	}

	@Test
	void testGetFunctionsReturnsValidResultSet() throws SQLException {
		DatabaseMetaData metaData = connection.getMetaData();

		try (ResultSet rs = metaData.getFunctions(TEST_PROJECT_ID, TEST_DATASET, null)) {
			assertNotNull(rs);
			assertEquals(6, rs.getMetaData().getColumnCount());
		}
	}

	/**
	 * The split, from the function side: both a scalar UDF and a table function are
	 * functions, and the procedure is not.
	 */
	@Test
	void testGetFunctionsFindsUdfsButNotProcedures() throws SQLException {
		DatabaseMetaData metaData = connection.getMetaData();

		java.util.List<String> functions = collect(metaData.getFunctions(TEST_PROJECT_ID, TEST_DATASET, null),
				"FUNCTION_NAME");

		logger.info("getFunctions() found {}", functions);
		assertTrue(functions.contains(ROUTINE_NAME), "the scalar UDF should be listed, found: " + functions);
		assertTrue(functions.contains(TABLE_FUNCTION_NAME), "the table function should be listed: " + functions);
		assertFalse(functions.contains(PROCEDURE_NAME), "a procedure is not a function, found: " + functions);
	}

	@Test
	void testGetFunctionsReportsWhetherAFunctionReturnsATable() throws SQLException {
		DatabaseMetaData metaData = connection.getMetaData();

		java.util.Map<String, Integer> types = new java.util.HashMap<>();
		try (ResultSet rs = metaData.getFunctions(TEST_PROJECT_ID, TEST_DATASET, null)) {
			while (rs.next()) {
				types.put(rs.getString("FUNCTION_NAME"), (int) rs.getShort("FUNCTION_TYPE"));
			}
		}

		assertEquals(DatabaseMetaData.functionNoTable, types.get(ROUTINE_NAME), "a scalar UDF returns no table");
		assertEquals(DatabaseMetaData.functionReturnsTable, types.get(TABLE_FUNCTION_NAME),
				"a TABLE FUNCTION returns a table");
	}

	@Test
	void testGetFunctionColumnsReturnsValidResultSet() throws SQLException {
		DatabaseMetaData metaData = connection.getMetaData();

		try (ResultSet rs = metaData.getFunctionColumns(TEST_PROJECT_ID, TEST_DATASET, null, null)) {
			assertNotNull(rs);
			assertEquals(17, rs.getMetaData().getColumnCount());
		}
	}

	/**
	 * BigQuery reports a function's return value as a {@code PARAMETERS} row with
	 * {@code is_result = 'YES'} at ordinal 0 and no name — which is what JDBC calls
	 * {@code functionReturn}. Getting that mapping wrong would report the return
	 * type as another argument, so it is asserted rather than assumed.
	 */
	@Test
	void testGetFunctionColumnsFindsArgumentsAndTheReturnValue() throws SQLException {
		DatabaseMetaData metaData = connection.getMetaData();

		java.util.Map<String, Short> columnTypes = new java.util.HashMap<>();
		java.util.Map<String, Integer> ordinals = new java.util.HashMap<>();
		try (ResultSet rs = metaData.getFunctionColumns(TEST_PROJECT_ID, TEST_DATASET, ROUTINE_NAME, null)) {
			while (rs.next()) {
				String name = rs.getString("COLUMN_NAME");
				columnTypes.put(name, rs.getShort("COLUMN_TYPE"));
				ordinals.put(name, rs.getInt("ORDINAL_POSITION"));
			}
		}

		logger.info("getFunctionColumns() found {} for {}", columnTypes, ROUTINE_NAME);
		assertEquals((short) DatabaseMetaData.functionColumnIn, columnTypes.get("x"), "x is an argument");
		assertEquals((short) DatabaseMetaData.functionColumnIn, columnTypes.get("y"), "y is an argument");
		assertEquals((short) DatabaseMetaData.functionReturn, columnTypes.get(""),
				"the unnamed row is the return value");
		assertEquals(0, ordinals.get(""), "the return value sits at ordinal 0");
		assertEquals(1, ordinals.get("x"));
	}

	/**
	 * The parameter join must cut both ways: a procedure's parameters have no
	 * business in {@code getFunctionColumns()}.
	 */
	@Test
	void testGetFunctionColumnsExcludesProcedureParameters() throws SQLException {
		DatabaseMetaData metaData = connection.getMetaData();

		java.util.List<String> functionNames = collect(
				metaData.getFunctionColumns(TEST_PROJECT_ID, TEST_DATASET, null, null), "FUNCTION_NAME");

		assertTrue(functionNames.contains(ROUTINE_NAME), "the UDF's parameters should be listed: " + functionNames);
		assertFalse(functionNames.contains(PROCEDURE_NAME),
				"the procedure's parameters should not be: " + functionNames);
	}
}

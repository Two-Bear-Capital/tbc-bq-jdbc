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

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.sql.Types;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for MetadataColumns.
 *
 * @since 1.0.15
 */
class MetadataColumnsTest {

	@Test
	void testUtilityClassCannotBeInstantiated() throws Exception {
		// Given: MetadataColumns is a utility class
		Constructor<MetadataColumns> constructor = MetadataColumns.class.getDeclaredConstructor();

		// Then: Constructor should be private
		assertTrue(Modifier.isPrivate(constructor.getModifiers()));

		// When: Trying to instantiate via reflection
		constructor.setAccessible(true);
		constructor.newInstance();

		// Then: Should not throw (private constructor exists)
	}

	// Tables Tests

	@Test
	void testTablesColumnCount() {
		// Given: Tables column definitions
		// When: Checking array lengths
		int columnCount = MetadataColumns.Tables.COLUMN_NAMES.length;
		int typeCount = MetadataColumns.Tables.COLUMN_TYPES.length;

		// Then: Should have 10 columns
		assertEquals(10, columnCount);
		assertEquals(10, typeCount);
	}

	@Test
	void testTablesColumnNamesAndTypes() {
		// Given: Tables column definitions
		String[] names = MetadataColumns.Tables.COLUMN_NAMES;
		int[] types = MetadataColumns.Tables.COLUMN_TYPES;

		// Then: Should match expected definitions
		assertEquals("TABLE_CAT", names[0]);
		assertEquals("TABLE_SCHEM", names[1]);
		assertEquals("TABLE_NAME", names[2]);
		assertEquals("TABLE_TYPE", names[3]);
		assertEquals("REMARKS", names[4]);
		assertEquals("TYPE_CAT", names[5]);
		assertEquals("TYPE_SCHEM", names[6]);
		assertEquals("TYPE_NAME", names[7]);
		assertEquals("SELF_REFERENCING_COL_NAME", names[8]);
		assertEquals("REF_GENERATION", names[9]);

		// And: All types should be VARCHAR
		for (int type : types) {
			assertEquals(Types.VARCHAR, type);
		}
	}

	// Columns Tests

	@Test
	void testColumnsColumnCount() {
		// Given: Columns column definitions
		// When: Checking array lengths
		int columnCount = MetadataColumns.Columns.COLUMN_NAMES.length;
		int typeCount = MetadataColumns.Columns.COLUMN_TYPES.length;

		// Then: Should have 24 columns
		assertEquals(24, columnCount);
		assertEquals(24, typeCount);
	}

	@Test
	void testColumnsColumnNamesAndTypes() {
		// Given: Columns column definitions
		String[] names = MetadataColumns.Columns.COLUMN_NAMES;

		// Then: Should match expected definitions
		assertEquals("TABLE_CAT", names[0]);
		assertEquals("TABLE_SCHEM", names[1]);
		assertEquals("TABLE_NAME", names[2]);
		assertEquals("COLUMN_NAME", names[3]);
		assertEquals("DATA_TYPE", names[4]);
		assertEquals("TYPE_NAME", names[5]);
		assertEquals("COLUMN_SIZE", names[6]);
		assertEquals("BUFFER_LENGTH", names[7]);
		assertEquals("DECIMAL_DIGITS", names[8]);
		assertEquals("NUM_PREC_RADIX", names[9]);
		assertEquals("NULLABLE", names[10]);
		assertEquals("REMARKS", names[11]);
		assertEquals("COLUMN_DEF", names[12]);
		assertEquals("SQL_DATA_TYPE", names[13]);
		assertEquals("SQL_DATETIME_SUB", names[14]);
		assertEquals("CHAR_OCTET_LENGTH", names[15]);
		assertEquals("ORDINAL_POSITION", names[16]);
		assertEquals("IS_NULLABLE", names[17]);
		assertEquals("SCOPE_CATALOG", names[18]);
		assertEquals("SCOPE_SCHEMA", names[19]);
		assertEquals("SCOPE_TABLE", names[20]);
		assertEquals("SOURCE_DATA_TYPE", names[21]);
		assertEquals("IS_AUTOINCREMENT", names[22]);
		assertEquals("IS_GENERATEDCOLUMN", names[23]);
	}

	@Test
	void testColumnsTypeMapping() {
		// Given: Columns type definitions
		int[] types = MetadataColumns.Columns.COLUMN_TYPES;

		// Then: Should have correct type mapping
		assertEquals(Types.VARCHAR, types[0]); // TABLE_CAT
		assertEquals(Types.VARCHAR, types[1]); // TABLE_SCHEM
		assertEquals(Types.VARCHAR, types[2]); // TABLE_NAME
		assertEquals(Types.VARCHAR, types[3]); // COLUMN_NAME
		assertEquals(Types.INTEGER, types[4]); // DATA_TYPE
		assertEquals(Types.VARCHAR, types[5]); // TYPE_NAME
		assertEquals(Types.INTEGER, types[6]); // COLUMN_SIZE
		assertEquals(Types.INTEGER, types[7]); // BUFFER_LENGTH
		assertEquals(Types.INTEGER, types[8]); // DECIMAL_DIGITS
		assertEquals(Types.INTEGER, types[9]); // NUM_PREC_RADIX
		assertEquals(Types.INTEGER, types[10]); // NULLABLE
		assertEquals(Types.VARCHAR, types[11]); // REMARKS
		assertEquals(Types.VARCHAR, types[12]); // COLUMN_DEF
		assertEquals(Types.INTEGER, types[13]); // SQL_DATA_TYPE
		assertEquals(Types.INTEGER, types[14]); // SQL_DATETIME_SUB
		assertEquals(Types.INTEGER, types[15]); // CHAR_OCTET_LENGTH
		assertEquals(Types.INTEGER, types[16]); // ORDINAL_POSITION
		assertEquals(Types.VARCHAR, types[17]); // IS_NULLABLE
		assertEquals(Types.VARCHAR, types[18]); // SCOPE_CATALOG
		assertEquals(Types.VARCHAR, types[19]); // SCOPE_SCHEMA
		assertEquals(Types.VARCHAR, types[20]); // SCOPE_TABLE
		assertEquals(Types.SMALLINT, types[21]); // SOURCE_DATA_TYPE
		assertEquals(Types.VARCHAR, types[22]); // IS_AUTOINCREMENT
		assertEquals(Types.VARCHAR, types[23]); // IS_GENERATEDCOLUMN
	}

	// Schemas Tests

	@Test
	void testSchemasColumnCount() {
		// Given: Schemas column definitions
		// When: Checking array lengths
		int columnCount = MetadataColumns.Schemas.COLUMN_NAMES.length;
		int typeCount = MetadataColumns.Schemas.COLUMN_TYPES.length;

		// Then: Should have 2 columns
		assertEquals(2, columnCount);
		assertEquals(2, typeCount);
	}

	@Test
	void testSchemasColumnNamesAndTypes() {
		// Given: Schemas column definitions
		String[] names = MetadataColumns.Schemas.COLUMN_NAMES;
		int[] types = MetadataColumns.Schemas.COLUMN_TYPES;

		// Then: Should match expected definitions
		assertEquals("TABLE_SCHEM", names[0]);
		assertEquals("TABLE_CATALOG", names[1]);

		// And: All types should be VARCHAR
		assertEquals(Types.VARCHAR, types[0]);
		assertEquals(Types.VARCHAR, types[1]);
	}

	// Catalogs Tests

	@Test
	void testCatalogsColumnCount() {
		// Given: Catalogs column definitions
		// When: Checking array lengths
		int columnCount = MetadataColumns.Catalogs.COLUMN_NAMES.length;
		int typeCount = MetadataColumns.Catalogs.COLUMN_TYPES.length;

		// Then: Should have 1 column
		assertEquals(1, columnCount);
		assertEquals(1, typeCount);
	}

	@Test
	void testCatalogsColumnNameAndType() {
		// Given: Catalogs column definitions
		String[] names = MetadataColumns.Catalogs.COLUMN_NAMES;
		int[] types = MetadataColumns.Catalogs.COLUMN_TYPES;

		// Then: Should match expected definition
		assertEquals("TABLE_CAT", names[0]);
		assertEquals(Types.VARCHAR, types[0]);
	}

	// TableTypes Tests

	@Test
	void testTableTypesColumnCount() {
		// Given: TableTypes column definitions
		// When: Checking array lengths
		int columnCount = MetadataColumns.TableTypes.COLUMN_NAMES.length;
		int typeCount = MetadataColumns.TableTypes.COLUMN_TYPES.length;

		// Then: Should have 1 column
		assertEquals(1, columnCount);
		assertEquals(1, typeCount);
	}

	@Test
	void testTableTypesColumnNameAndType() {
		// Given: TableTypes column definitions
		String[] names = MetadataColumns.TableTypes.COLUMN_NAMES;
		int[] types = MetadataColumns.TableTypes.COLUMN_TYPES;

		// Then: Should match expected definition
		assertEquals("TABLE_TYPE", names[0]);
		assertEquals(Types.VARCHAR, types[0]);
	}

	// PrimaryKeys Tests

	@Test
	void testPrimaryKeysColumnCount() {
		// Given: PrimaryKeys column definitions
		// When: Checking array lengths
		int columnCount = MetadataColumns.PrimaryKeys.COLUMN_NAMES.length;
		int typeCount = MetadataColumns.PrimaryKeys.COLUMN_TYPES.length;

		// Then: Should have 6 columns
		assertEquals(6, columnCount);
		assertEquals(6, typeCount);
	}

	@Test
	void testPrimaryKeysColumnNamesAndTypes() {
		// Given: PrimaryKeys column definitions
		String[] names = MetadataColumns.PrimaryKeys.COLUMN_NAMES;
		int[] types = MetadataColumns.PrimaryKeys.COLUMN_TYPES;

		// Then: Should match expected definitions
		assertEquals("TABLE_CAT", names[0]);
		assertEquals("TABLE_SCHEM", names[1]);
		assertEquals("TABLE_NAME", names[2]);
		assertEquals("COLUMN_NAME", names[3]);
		assertEquals("KEY_SEQ", names[4]);
		assertEquals("PK_NAME", names[5]);

		// And: Should have correct types
		assertEquals(Types.VARCHAR, types[0]);
		assertEquals(Types.VARCHAR, types[1]);
		assertEquals(Types.VARCHAR, types[2]);
		assertEquals(Types.VARCHAR, types[3]);
		assertEquals(Types.SMALLINT, types[4]);
		assertEquals(Types.VARCHAR, types[5]);
	}

	// ForeignKeys Tests

	@Test
	void testForeignKeysColumnCount() {
		// Given: ForeignKeys column definitions
		// When: Checking array lengths
		int columnCount = MetadataColumns.ForeignKeys.COLUMN_NAMES.length;
		int typeCount = MetadataColumns.ForeignKeys.COLUMN_TYPES.length;

		// Then: Should have 14 columns
		assertEquals(14, columnCount);
		assertEquals(14, typeCount);
	}

	@Test
	void testForeignKeysColumnNamesAndTypes() {
		// Given: ForeignKeys column definitions
		String[] names = MetadataColumns.ForeignKeys.COLUMN_NAMES;
		int[] types = MetadataColumns.ForeignKeys.COLUMN_TYPES;

		// Then: Should match expected definitions
		assertEquals("PKTABLE_CAT", names[0]);
		assertEquals("PKTABLE_SCHEM", names[1]);
		assertEquals("PKTABLE_NAME", names[2]);
		assertEquals("PKCOLUMN_NAME", names[3]);
		assertEquals("FKTABLE_CAT", names[4]);
		assertEquals("FKTABLE_SCHEM", names[5]);
		assertEquals("FKTABLE_NAME", names[6]);
		assertEquals("FKCOLUMN_NAME", names[7]);
		assertEquals("KEY_SEQ", names[8]);
		assertEquals("UPDATE_RULE", names[9]);
		assertEquals("DELETE_RULE", names[10]);
		assertEquals("FK_NAME", names[11]);
		assertEquals("PK_NAME", names[12]);
		assertEquals("DEFERRABILITY", names[13]);

		// And: Should have correct types
		assertEquals(Types.VARCHAR, types[0]);
		assertEquals(Types.VARCHAR, types[1]);
		assertEquals(Types.VARCHAR, types[2]);
		assertEquals(Types.VARCHAR, types[3]);
		assertEquals(Types.VARCHAR, types[4]);
		assertEquals(Types.VARCHAR, types[5]);
		assertEquals(Types.VARCHAR, types[6]);
		assertEquals(Types.VARCHAR, types[7]);
		assertEquals(Types.SMALLINT, types[8]);
		assertEquals(Types.SMALLINT, types[9]);
		assertEquals(Types.SMALLINT, types[10]);
		assertEquals(Types.VARCHAR, types[11]);
		assertEquals(Types.VARCHAR, types[12]);
		assertEquals(Types.SMALLINT, types[13]);
	}

	// New Inner Class Tests (added in plan)

	@Test
	void testColumnPrivilegesColumnCount() {
		assertEquals(8, MetadataColumns.ColumnPrivileges.COLUMN_NAMES.length);
		assertEquals(8, MetadataColumns.ColumnPrivileges.COLUMN_TYPES.length);
	}

	@Test
	void testTablePrivilegesColumnCount() {
		assertEquals(7, MetadataColumns.TablePrivileges.COLUMN_NAMES.length);
		assertEquals(7, MetadataColumns.TablePrivileges.COLUMN_TYPES.length);
	}

	@Test
	void testBestRowIdentifierColumnCount() {
		assertEquals(8, MetadataColumns.BestRowIdentifier.COLUMN_NAMES.length);
		assertEquals(8, MetadataColumns.BestRowIdentifier.COLUMN_TYPES.length);
	}

	@Test
	void testVersionColumnsColumnCount() {
		assertEquals(8, MetadataColumns.VersionColumns.COLUMN_NAMES.length);
		assertEquals(8, MetadataColumns.VersionColumns.COLUMN_TYPES.length);
	}

	@Test
	void testIndexInfoColumnCount() {
		assertEquals(13, MetadataColumns.IndexInfo.COLUMN_NAMES.length);
		assertEquals(13, MetadataColumns.IndexInfo.COLUMN_TYPES.length);
	}

	@Test
	void testUDTsColumnCount() {
		assertEquals(6, MetadataColumns.UDTs.COLUMN_NAMES.length);
		assertEquals(6, MetadataColumns.UDTs.COLUMN_TYPES.length);
	}

	@Test
	void testSuperTypesColumnCount() {
		assertEquals(6, MetadataColumns.SuperTypes.COLUMN_NAMES.length);
		assertEquals(6, MetadataColumns.SuperTypes.COLUMN_TYPES.length);
	}

	@Test
	void testSuperTablesColumnCount() {
		assertEquals(4, MetadataColumns.SuperTables.COLUMN_NAMES.length);
		assertEquals(4, MetadataColumns.SuperTables.COLUMN_TYPES.length);
	}

	@Test
	void testAttributesColumnCount() {
		assertEquals(21, MetadataColumns.Attributes.COLUMN_NAMES.length);
		assertEquals(21, MetadataColumns.Attributes.COLUMN_TYPES.length);
	}

	/**
	 * The result is always empty, so the columns are the only thing a caller can
	 * read — spot-checks the two ends and the two that are not VARCHAR.
	 */
	@Test
	void testAttributesColumnNamesAndTypes() {
		String[] names = MetadataColumns.Attributes.COLUMN_NAMES;
		int[] types = MetadataColumns.Attributes.COLUMN_TYPES;

		assertEquals("TYPE_CAT", names[0]);
		assertEquals("DATA_TYPE", names[4]);
		assertEquals("IS_NULLABLE", names[16]);
		assertEquals("SOURCE_DATA_TYPE", names[20]);
		assertEquals(Types.INTEGER, types[4]);
		assertEquals(Types.SMALLINT, types[20]);
	}

	@Test
	void testClientInfoPropertiesColumnCount() {
		assertEquals(4, MetadataColumns.ClientInfoProperties.COLUMN_NAMES.length);
		assertEquals(4, MetadataColumns.ClientInfoProperties.COLUMN_TYPES.length);
	}

	@Test
	void testClientInfoPropertiesColumnNamesAndTypes() {
		String[] names = MetadataColumns.ClientInfoProperties.COLUMN_NAMES;
		int[] types = MetadataColumns.ClientInfoProperties.COLUMN_TYPES;

		assertEquals("NAME", names[0]);
		assertEquals("MAX_LEN", names[1]);
		assertEquals("DEFAULT_VALUE", names[2]);
		assertEquals("DESCRIPTION", names[3]);
		assertEquals(Types.INTEGER, types[1]);
	}

	@Test
	void testFunctionsColumnCount() {
		assertEquals(6, MetadataColumns.Functions.COLUMN_NAMES.length);
		assertEquals(6, MetadataColumns.Functions.COLUMN_TYPES.length);
	}

	@Test
	void testFunctionsColumnNamesAndTypes() {
		String[] names = MetadataColumns.Functions.COLUMN_NAMES;
		int[] types = MetadataColumns.Functions.COLUMN_TYPES;

		assertEquals("FUNCTION_CAT", names[0]);
		assertEquals("FUNCTION_SCHEM", names[1]);
		assertEquals("FUNCTION_NAME", names[2]);
		assertEquals("REMARKS", names[3]);
		assertEquals("FUNCTION_TYPE", names[4]);
		assertEquals("SPECIFIC_NAME", names[5]);
		assertEquals(Types.SMALLINT, types[4]);
	}

	@Test
	void testFunctionColumnsColumnCount() {
		assertEquals(17, MetadataColumns.FunctionColumns.COLUMN_NAMES.length);
		assertEquals(17, MetadataColumns.FunctionColumns.COLUMN_TYPES.length);
	}

	/**
	 * Four columns wider than {@code ProcedureColumns} and ordered differently, so
	 * the two shapes cannot be shared — the tail is where they diverge.
	 */
	@Test
	void testFunctionColumnsColumnNamesAndTypes() {
		String[] names = MetadataColumns.FunctionColumns.COLUMN_NAMES;
		int[] types = MetadataColumns.FunctionColumns.COLUMN_TYPES;

		assertEquals("FUNCTION_CAT", names[0]);
		assertEquals("COLUMN_TYPE", names[4]);
		assertEquals("REMARKS", names[12]);
		assertEquals("CHAR_OCTET_LENGTH", names[13]);
		assertEquals("ORDINAL_POSITION", names[14]);
		assertEquals("IS_NULLABLE", names[15]);
		assertEquals("SPECIFIC_NAME", names[16]);
		assertEquals(Types.SMALLINT, types[4]);
		assertEquals(Types.INTEGER, types[14]);
	}

	@Test
	void testProceduresColumnCount() {
		assertEquals(8, MetadataColumns.Procedures.COLUMN_NAMES.length);
		assertEquals(8, MetadataColumns.Procedures.COLUMN_TYPES.length);
	}

	@Test
	void testProcedureColumnsColumnCount() {
		assertEquals(13, MetadataColumns.ProcedureColumns.COLUMN_NAMES.length);
		assertEquals(13, MetadataColumns.ProcedureColumns.COLUMN_TYPES.length);
	}

	// Inner Class Constructor Tests

	@Test
	void testTablesInnerClassPrivateConstructor() throws Exception {
		// Given: Tables inner class
		Constructor<MetadataColumns.Tables> constructor = MetadataColumns.Tables.class.getDeclaredConstructor();

		// Then: Constructor should be private
		assertTrue(Modifier.isPrivate(constructor.getModifiers()));
	}

	@Test
	void testColumnsInnerClassPrivateConstructor() throws Exception {
		// Given: Columns inner class
		Constructor<MetadataColumns.Columns> constructor = MetadataColumns.Columns.class.getDeclaredConstructor();

		// Then: Constructor should be private
		assertTrue(Modifier.isPrivate(constructor.getModifiers()));
	}

	@Test
	void testSchemasInnerClassPrivateConstructor() throws Exception {
		// Given: Schemas inner class
		Constructor<MetadataColumns.Schemas> constructor = MetadataColumns.Schemas.class.getDeclaredConstructor();

		// Then: Constructor should be private
		assertTrue(Modifier.isPrivate(constructor.getModifiers()));
	}

	@Test
	void testCatalogsInnerClassPrivateConstructor() throws Exception {
		// Given: Catalogs inner class
		Constructor<MetadataColumns.Catalogs> constructor = MetadataColumns.Catalogs.class.getDeclaredConstructor();

		// Then: Constructor should be private
		assertTrue(Modifier.isPrivate(constructor.getModifiers()));
	}

	@Test
	void testTableTypesInnerClassPrivateConstructor() throws Exception {
		// Given: TableTypes inner class
		Constructor<MetadataColumns.TableTypes> constructor = MetadataColumns.TableTypes.class.getDeclaredConstructor();

		// Then: Constructor should be private
		assertTrue(Modifier.isPrivate(constructor.getModifiers()));
	}

	@Test
	void testPrimaryKeysInnerClassPrivateConstructor() throws Exception {
		// Given: PrimaryKeys inner class
		Constructor<MetadataColumns.PrimaryKeys> constructor = MetadataColumns.PrimaryKeys.class
				.getDeclaredConstructor();

		// Then: Constructor should be private
		assertTrue(Modifier.isPrivate(constructor.getModifiers()));
	}

	@Test
	void testForeignKeysInnerClassPrivateConstructor() throws Exception {
		// Given: ForeignKeys inner class
		Constructor<MetadataColumns.ForeignKeys> constructor = MetadataColumns.ForeignKeys.class
				.getDeclaredConstructor();

		// Then: Constructor should be private
		assertTrue(Modifier.isPrivate(constructor.getModifiers()));
	}
}

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
package com.twobearcapital.bigquery.jdbc.metadata;

import java.sql.Types;

/**
 * Centralized definitions for JDBC metadata result set columns.
 *
 * <p>
 * This class provides constants for column names, types, and definitions used
 * in DatabaseMetaData result sets. Consolidates duplicate definitions across
 * BQDatabaseMetaData methods.
 * </p>
 *
 * @since 1.0.0
 */
public final class MetadataColumns {

	private MetadataColumns() {
		// Utility class
	}

	/**
	 * Column definitions for getTables() result set.
	 */
	public static final class Tables {
		public static final String[] COLUMN_NAMES = {"TABLE_CAT", // String => table catalog (may be null)
				"TABLE_SCHEM", // String => table schema (may be null)
				"TABLE_NAME", // String => table name
				"TABLE_TYPE", // String => table type
				"REMARKS", // String => explanatory comment
				"TYPE_CAT", // String => types catalog (may be null)
				"TYPE_SCHEM", // String => types schema (may be null)
				"TYPE_NAME", // String => type name (may be null)
				"SELF_REFERENCING_COL_NAME", // String => name of designated identifier column
				"REF_GENERATION" // String => how values in SELF_REFERENCING_COL_NAME created
		};

		public static final int[] COLUMN_TYPES = {Types.VARCHAR, // TABLE_CAT
				Types.VARCHAR, // TABLE_SCHEM
				Types.VARCHAR, // TABLE_NAME
				Types.VARCHAR, // TABLE_TYPE
				Types.VARCHAR, // REMARKS
				Types.VARCHAR, // TYPE_CAT
				Types.VARCHAR, // TYPE_SCHEM
				Types.VARCHAR, // TYPE_NAME
				Types.VARCHAR, // SELF_REFERENCING_COL_NAME
				Types.VARCHAR // REF_GENERATION
		};

		private Tables() {
		}
	}

	/**
	 * Column definitions for getColumns() result set.
	 */
	public static final class Columns {
		public static final String[] COLUMN_NAMES = {"TABLE_CAT", // String => table catalog (may be null)
				"TABLE_SCHEM", // String => table schema (may be null)
				"TABLE_NAME", // String => table name
				"COLUMN_NAME", // String => column name
				"DATA_TYPE", // int => SQL type from java.sql.Types
				"TYPE_NAME", // String => Data source dependent type name
				"COLUMN_SIZE", // int => column size
				"BUFFER_LENGTH", // not used
				"DECIMAL_DIGITS", // int => number of fractional digits
				"NUM_PREC_RADIX", // int => Radix (typically 2 or 10)
				"NULLABLE", // int => is NULL allowed
				"REMARKS", // String => comment describing column
				"COLUMN_DEF", // String => default value for the column
				"SQL_DATA_TYPE", // int => unused
				"SQL_DATETIME_SUB", // int => unused
				"CHAR_OCTET_LENGTH", // int => for char types max bytes in column
				"ORDINAL_POSITION", // int => index of column in table (starting at 1)
				"IS_NULLABLE", // String => ISO rules for nullability
				"SCOPE_CATALOG", // String => catalog of table that is scope of ref
				"SCOPE_SCHEMA", // String => schema of table that is scope of ref
				"SCOPE_TABLE", // String => table name that is scope of reference
				"SOURCE_DATA_TYPE", // short => source type of distinct/user-generated Ref
				"IS_AUTOINCREMENT", // String => is column auto-incremented
				"IS_GENERATEDCOLUMN" // String => is column generated
		};

		public static final int[] COLUMN_TYPES = {Types.VARCHAR, // TABLE_CAT
				Types.VARCHAR, // TABLE_SCHEM
				Types.VARCHAR, // TABLE_NAME
				Types.VARCHAR, // COLUMN_NAME
				Types.INTEGER, // DATA_TYPE
				Types.VARCHAR, // TYPE_NAME
				Types.INTEGER, // COLUMN_SIZE
				Types.INTEGER, // BUFFER_LENGTH
				Types.INTEGER, // DECIMAL_DIGITS
				Types.INTEGER, // NUM_PREC_RADIX
				Types.INTEGER, // NULLABLE
				Types.VARCHAR, // REMARKS
				Types.VARCHAR, // COLUMN_DEF
				Types.INTEGER, // SQL_DATA_TYPE
				Types.INTEGER, // SQL_DATETIME_SUB
				Types.INTEGER, // CHAR_OCTET_LENGTH
				Types.INTEGER, // ORDINAL_POSITION
				Types.VARCHAR, // IS_NULLABLE
				Types.VARCHAR, // SCOPE_CATALOG
				Types.VARCHAR, // SCOPE_SCHEMA
				Types.VARCHAR, // SCOPE_TABLE
				Types.SMALLINT, // SOURCE_DATA_TYPE
				Types.VARCHAR, // IS_AUTOINCREMENT
				Types.VARCHAR // IS_GENERATEDCOLUMN
		};

		private Columns() {
		}
	}

	/**
	 * Column definitions for getSchemas() result set.
	 */
	public static final class Schemas {
		public static final String[] COLUMN_NAMES = {"TABLE_SCHEM", // String => schema name
				"TABLE_CATALOG" // String => catalog name (may be null)
		};

		public static final int[] COLUMN_TYPES = {Types.VARCHAR, // TABLE_SCHEM
				Types.VARCHAR // TABLE_CATALOG
		};

		private Schemas() {
		}
	}

	/**
	 * Column definitions for getCatalogs() result set.
	 */
	public static final class Catalogs {
		public static final String[] COLUMN_NAMES = {"TABLE_CAT" // String => catalog name
		};

		public static final int[] COLUMN_TYPES = {Types.VARCHAR // TABLE_CAT
		};

		private Catalogs() {
		}
	}

	/**
	 * Column definitions for getTableTypes() result set.
	 */
	public static final class TableTypes {
		public static final String[] COLUMN_NAMES = {"TABLE_TYPE" // String => table type
		};

		public static final int[] COLUMN_TYPES = {Types.VARCHAR // TABLE_TYPE
		};

		private TableTypes() {
		}
	}

	/**
	 * Column definitions for getPrimaryKeys() result set.
	 */
	public static final class PrimaryKeys {
		public static final String[] COLUMN_NAMES = {"TABLE_CAT", // String => table catalog (may be null)
				"TABLE_SCHEM", // String => table schema (may be null)
				"TABLE_NAME", // String => table name
				"COLUMN_NAME", // String => column name
				"KEY_SEQ", // short => sequence number within primary key
				"PK_NAME" // String => primary key name (may be null)
		};

		public static final int[] COLUMN_TYPES = {Types.VARCHAR, // TABLE_CAT
				Types.VARCHAR, // TABLE_SCHEM
				Types.VARCHAR, // TABLE_NAME
				Types.VARCHAR, // COLUMN_NAME
				Types.SMALLINT, // KEY_SEQ
				Types.VARCHAR // PK_NAME
		};

		private PrimaryKeys() {
		}
	}

	/**
	 * Column definitions for getImportedKeys() and getExportedKeys() result sets.
	 */
	public static final class ForeignKeys {
		public static final String[] COLUMN_NAMES = {"PKTABLE_CAT", // String => primary key table catalog (may be null)
				"PKTABLE_SCHEM", // String => primary key table schema (may be null)
				"PKTABLE_NAME", // String => primary key table name
				"PKCOLUMN_NAME", // String => primary key column name
				"FKTABLE_CAT", // String => foreign key table catalog (may be null)
				"FKTABLE_SCHEM", // String => foreign key table schema (may be null)
				"FKTABLE_NAME", // String => foreign key table name
				"FKCOLUMN_NAME", // String => foreign key column name
				"KEY_SEQ", // short => sequence number within foreign key
				"UPDATE_RULE", // short => what happens to FK when PK is updated
				"DELETE_RULE", // short => what happens to FK when PK is deleted
				"FK_NAME", // String => foreign key name (may be null)
				"PK_NAME", // String => primary key name (may be null)
				"DEFERRABILITY" // short => can evaluation of FK constraint be deferred
		};

		public static final int[] COLUMN_TYPES = {Types.VARCHAR, // PKTABLE_CAT
				Types.VARCHAR, // PKTABLE_SCHEM
				Types.VARCHAR, // PKTABLE_NAME
				Types.VARCHAR, // PKCOLUMN_NAME
				Types.VARCHAR, // FKTABLE_CAT
				Types.VARCHAR, // FKTABLE_SCHEM
				Types.VARCHAR, // FKTABLE_NAME
				Types.VARCHAR, // FKCOLUMN_NAME
				Types.SMALLINT, // KEY_SEQ
				Types.SMALLINT, // UPDATE_RULE
				Types.SMALLINT, // DELETE_RULE
				Types.VARCHAR, // FK_NAME
				Types.VARCHAR, // PK_NAME
				Types.SMALLINT // DEFERRABILITY
		};

		private ForeignKeys() {
		}
	}
}

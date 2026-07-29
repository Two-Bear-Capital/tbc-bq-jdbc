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
@SuppressWarnings("PMD.MissingStaticMethodInNonInstantiatableClass") // utility class: provides constants via static
																		// nested classes
public final class MetadataColumns {

	private MetadataColumns() {
		// Utility class
	}

	/**
	 * Column definitions for getTables() result set.
	 */
	public static final class Tables {
		static final String[] COLUMN_NAMES = {"TABLE_CAT", // String => table catalog (may be null)
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

		static final int[] COLUMN_TYPES = {Types.VARCHAR, // TABLE_CAT
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
		static final String[] COLUMN_NAMES = {"TABLE_CAT", // String => table catalog (may be null)
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

		static final int[] COLUMN_TYPES = {Types.VARCHAR, // TABLE_CAT
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
		static final String[] COLUMN_NAMES = {"TABLE_SCHEM", // String => schema name
				"TABLE_CATALOG" // String => catalog name (may be null)
		};

		static final int[] COLUMN_TYPES = {Types.VARCHAR, // TABLE_SCHEM
				Types.VARCHAR // TABLE_CATALOG
		};

		private Schemas() {
		}
	}

	/**
	 * Column definitions for getCatalogs() result set.
	 */
	public static final class Catalogs {
		static final String[] COLUMN_NAMES = {"TABLE_CAT" // String => catalog name
		};

		static final int[] COLUMN_TYPES = {Types.VARCHAR // TABLE_CAT
		};

		private Catalogs() {
		}
	}

	/**
	 * Column definitions for getTableTypes() result set.
	 */
	public static final class TableTypes {
		static final String[] COLUMN_NAMES = {"TABLE_TYPE" // String => table type
		};

		static final int[] COLUMN_TYPES = {Types.VARCHAR // TABLE_TYPE
		};

		private TableTypes() {
		}
	}

	/**
	 * Column definitions for getPrimaryKeys() result set.
	 */
	public static final class PrimaryKeys {
		static final String[] COLUMN_NAMES = {"TABLE_CAT", // String => table catalog (may be null)
				"TABLE_SCHEM", // String => table schema (may be null)
				"TABLE_NAME", // String => table name
				"COLUMN_NAME", // String => column name
				"KEY_SEQ", // short => sequence number within primary key
				"PK_NAME" // String => primary key name (may be null)
		};

		static final int[] COLUMN_TYPES = {Types.VARCHAR, // TABLE_CAT
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
	 * Column definitions for getColumnPrivileges() result set.
	 */
	public static final class ColumnPrivileges {
		static final String[] COLUMN_NAMES = {"TABLE_CAT", // String => table catalog (may be null)
				"TABLE_SCHEM", // String => table schema (may be null)
				"TABLE_NAME", // String => table name
				"COLUMN_NAME", // String => column name
				"GRANTOR", // String => grantor of access (may be null)
				"GRANTEE", // String => grantee of access
				"PRIVILEGE", // String => name of access
				"IS_GRANTABLE" // String => "YES" if grantee can grant; "NO" if not
		};

		static final int[] COLUMN_TYPES = {Types.VARCHAR, // TABLE_CAT
				Types.VARCHAR, // TABLE_SCHEM
				Types.VARCHAR, // TABLE_NAME
				Types.VARCHAR, // COLUMN_NAME
				Types.VARCHAR, // GRANTOR
				Types.VARCHAR, // GRANTEE
				Types.VARCHAR, // PRIVILEGE
				Types.VARCHAR // IS_GRANTABLE
		};

		private ColumnPrivileges() {
		}
	}

	/**
	 * Column definitions for getTablePrivileges() result set.
	 */
	public static final class TablePrivileges {
		static final String[] COLUMN_NAMES = {"TABLE_CAT", // String => table catalog (may be null)
				"TABLE_SCHEM", // String => table schema (may be null)
				"TABLE_NAME", // String => table name
				"GRANTOR", // String => grantor of access (may be null)
				"GRANTEE", // String => grantee of access
				"PRIVILEGE", // String => name of access
				"IS_GRANTABLE" // String => "YES" if grantee can grant; "NO" if not
		};

		static final int[] COLUMN_TYPES = {Types.VARCHAR, // TABLE_CAT
				Types.VARCHAR, // TABLE_SCHEM
				Types.VARCHAR, // TABLE_NAME
				Types.VARCHAR, // GRANTOR
				Types.VARCHAR, // GRANTEE
				Types.VARCHAR, // PRIVILEGE
				Types.VARCHAR // IS_GRANTABLE
		};

		private TablePrivileges() {
		}
	}

	/**
	 * Column definitions for getBestRowIdentifier() result set.
	 */
	public static final class BestRowIdentifier {
		static final String[] COLUMN_NAMES = {"SCOPE", // short => actual scope of result
				"COLUMN_NAME", // String => column name
				"DATA_TYPE", // int => SQL data type from java.sql.Types
				"TYPE_NAME", // String => Data source dependent type name
				"COLUMN_SIZE", // int => column precision
				"BUFFER_LENGTH", // int => not used
				"DECIMAL_DIGITS", // short => scale
				"PSEUDO_COLUMN" // short => is this a pseudo column
		};

		static final int[] COLUMN_TYPES = {Types.SMALLINT, // SCOPE
				Types.VARCHAR, // COLUMN_NAME
				Types.INTEGER, // DATA_TYPE
				Types.VARCHAR, // TYPE_NAME
				Types.INTEGER, // COLUMN_SIZE
				Types.INTEGER, // BUFFER_LENGTH
				Types.SMALLINT, // DECIMAL_DIGITS
				Types.SMALLINT // PSEUDO_COLUMN
		};

		private BestRowIdentifier() {
		}
	}

	/**
	 * Column definitions for getVersionColumns() result set.
	 */
	public static final class VersionColumns {
		static final String[] COLUMN_NAMES = {"SCOPE", // short => is not used
				"COLUMN_NAME", // String => column name
				"DATA_TYPE", // int => SQL data type from java.sql.Types
				"TYPE_NAME", // String => Data source dependent type name
				"COLUMN_SIZE", // int => precision
				"BUFFER_LENGTH", // int => length of column value in bytes
				"DECIMAL_DIGITS", // short => scale
				"PSEUDO_COLUMN" // short => whether this is pseudo column
		};

		static final int[] COLUMN_TYPES = {Types.SMALLINT, // SCOPE
				Types.VARCHAR, // COLUMN_NAME
				Types.INTEGER, // DATA_TYPE
				Types.VARCHAR, // TYPE_NAME
				Types.INTEGER, // COLUMN_SIZE
				Types.INTEGER, // BUFFER_LENGTH
				Types.SMALLINT, // DECIMAL_DIGITS
				Types.SMALLINT // PSEUDO_COLUMN
		};

		private VersionColumns() {
		}
	}

	/**
	 * Column definitions for getIndexInfo() result set.
	 */
	public static final class IndexInfo {
		static final String[] COLUMN_NAMES = {"TABLE_CAT", // String => table catalog (may be null)
				"TABLE_SCHEM", // String => table schema (may be null)
				"TABLE_NAME", // String => table name
				"NON_UNIQUE", // boolean => index values can be non-unique
				"INDEX_QUALIFIER", // String => index catalog (may be null)
				"INDEX_NAME", // String => index name
				"TYPE", // short => index type
				"ORDINAL_POSITION", // short => column sequence number within index (starts at 1)
				"COLUMN_NAME", // String => column name
				"ASC_OR_DESC", // String => column sort sequence
				"CARDINALITY", // long => number of unique values in the index
				"PAGES", // long => pages used for current table / index
				"FILTER_CONDITION" // String => filter condition, if any
		};

		static final int[] COLUMN_TYPES = {Types.VARCHAR, // TABLE_CAT
				Types.VARCHAR, // TABLE_SCHEM
				Types.VARCHAR, // TABLE_NAME
				Types.BOOLEAN, // NON_UNIQUE
				Types.VARCHAR, // INDEX_QUALIFIER
				Types.VARCHAR, // INDEX_NAME
				Types.SMALLINT, // TYPE
				Types.SMALLINT, // ORDINAL_POSITION
				Types.VARCHAR, // COLUMN_NAME
				Types.VARCHAR, // ASC_OR_DESC
				Types.BIGINT, // CARDINALITY
				Types.BIGINT, // PAGES
				Types.VARCHAR // FILTER_CONDITION
		};

		private IndexInfo() {
		}
	}

	/**
	 * Column definitions for getUDTs() result set.
	 */
	public static final class UDTs {
		static final String[] COLUMN_NAMES = {"TYPE_CAT", // String => type catalog (may be null)
				"TYPE_SCHEM", // String => type schema (may be null)
				"TYPE_NAME", // String => type name
				"CLASS_NAME", // String => Java class name
				"DATA_TYPE", // int => type value from java.sql.Types
				"REMARKS" // String => explanatory comment
		};

		static final int[] COLUMN_TYPES = {Types.VARCHAR, // TYPE_CAT
				Types.VARCHAR, // TYPE_SCHEM
				Types.VARCHAR, // TYPE_NAME
				Types.VARCHAR, // CLASS_NAME
				Types.INTEGER, // DATA_TYPE
				Types.VARCHAR // REMARKS
		};

		private UDTs() {
		}
	}

	/**
	 * Column definitions for getSuperTypes() result set.
	 */
	public static final class SuperTypes {
		static final String[] COLUMN_NAMES = {"TYPE_CAT", // String => type catalog (may be null)
				"TYPE_SCHEM", // String => type schema (may be null)
				"TYPE_NAME", // String => type name
				"SUPERTYPE_CAT", // String => direct super type catalog (may be null)
				"SUPERTYPE_SCHEM", // String => direct super type schema (may be null)
				"SUPERTYPE_NAME" // String => direct super type name
		};

		static final int[] COLUMN_TYPES = {Types.VARCHAR, // TYPE_CAT
				Types.VARCHAR, // TYPE_SCHEM
				Types.VARCHAR, // TYPE_NAME
				Types.VARCHAR, // SUPERTYPE_CAT
				Types.VARCHAR, // SUPERTYPE_SCHEM
				Types.VARCHAR // SUPERTYPE_NAME
		};

		private SuperTypes() {
		}
	}

	/**
	 * Column definitions for getAttributes() result set.
	 *
	 * <p>
	 * Attributes describe the fields of a user-defined type. BigQuery has no
	 * user-defined types, so the result is always empty — but the shape still has
	 * to be right, because a tool reading it by column name fails differently from
	 * one that finds no rows.
	 *
	 * @since 3.1.0
	 */
	public static final class Attributes {
		static final String[] COLUMN_NAMES = {"TYPE_CAT", // String => type catalog (may be null)
				"TYPE_SCHEM", // String => type schema (may be null)
				"TYPE_NAME", // String => type name
				"ATTR_NAME", // String => attribute name
				"DATA_TYPE", // int => attribute type from java.sql.Types
				"ATTR_TYPE_NAME", // String => data source dependent type name
				"ATTR_SIZE", // int => column size
				"DECIMAL_DIGITS", // int => fractional digits (null if not applicable)
				"NUM_PREC_RADIX", // int => radix
				"NULLABLE", // int => whether NULL is allowed
				"REMARKS", // String => comment describing the attribute (may be null)
				"ATTR_DEF", // String => default value (may be null)
				"SQL_DATA_TYPE", // int => unused
				"SQL_DATETIME_SUB", // int => unused
				"CHAR_OCTET_LENGTH", // int => max bytes for a char attribute
				"ORDINAL_POSITION", // int => index within the type, starting at 1
				"IS_NULLABLE", // String => YES, NO or empty when unknown
				"SCOPE_CATALOG", // String => catalog of the referenced table (REF only)
				"SCOPE_SCHEMA", // String => schema of the referenced table (REF only)
				"SCOPE_TABLE", // String => name of the referenced table (REF only)
				"SOURCE_DATA_TYPE" // short => source type of a DISTINCT or REF type
		};

		static final int[] COLUMN_TYPES = {Types.VARCHAR, // TYPE_CAT
				Types.VARCHAR, // TYPE_SCHEM
				Types.VARCHAR, // TYPE_NAME
				Types.VARCHAR, // ATTR_NAME
				Types.INTEGER, // DATA_TYPE
				Types.VARCHAR, // ATTR_TYPE_NAME
				Types.INTEGER, // ATTR_SIZE
				Types.INTEGER, // DECIMAL_DIGITS
				Types.INTEGER, // NUM_PREC_RADIX
				Types.INTEGER, // NULLABLE
				Types.VARCHAR, // REMARKS
				Types.VARCHAR, // ATTR_DEF
				Types.INTEGER, // SQL_DATA_TYPE
				Types.INTEGER, // SQL_DATETIME_SUB
				Types.INTEGER, // CHAR_OCTET_LENGTH
				Types.INTEGER, // ORDINAL_POSITION
				Types.VARCHAR, // IS_NULLABLE
				Types.VARCHAR, // SCOPE_CATALOG
				Types.VARCHAR, // SCOPE_SCHEMA
				Types.VARCHAR, // SCOPE_TABLE
				Types.SMALLINT // SOURCE_DATA_TYPE
		};

		private Attributes() {
		}
	}

	/**
	 * Column definitions for getClientInfoProperties() result set.
	 *
	 * <p>
	 * The driver accepts no client-info properties, so the result is always empty.
	 *
	 * @since 3.1.0
	 */
	public static final class ClientInfoProperties {
		static final String[] COLUMN_NAMES = {"NAME", // String => the client info property name
				"MAX_LEN", // int => maximum length of the value
				"DEFAULT_VALUE", // String => default value
				"DESCRIPTION" // String => description of the property
		};

		static final int[] COLUMN_TYPES = {Types.VARCHAR, // NAME
				Types.INTEGER, // MAX_LEN
				Types.VARCHAR, // DEFAULT_VALUE
				Types.VARCHAR // DESCRIPTION
		};

		private ClientInfoProperties() {
		}
	}

	/**
	 * Column definitions for getSuperTables() result set.
	 */
	public static final class SuperTables {
		static final String[] COLUMN_NAMES = {"TABLE_CAT", // String => type catalog (may be null)
				"TABLE_SCHEM", // String => type schema (may be null)
				"TABLE_NAME", // String => type name
				"SUPERTABLE_NAME" // String => direct super table name
		};

		static final int[] COLUMN_TYPES = {Types.VARCHAR, // TABLE_CAT
				Types.VARCHAR, // TABLE_SCHEM
				Types.VARCHAR, // TABLE_NAME
				Types.VARCHAR // SUPERTABLE_NAME
		};

		private SuperTables() {
		}
	}

	/**
	 * Column definitions for getProcedures() result set.
	 */
	public static final class Procedures {
		static final String[] COLUMN_NAMES = {"PROCEDURE_CAT", // String => procedure catalog (may be null)
				"PROCEDURE_SCHEM", // String => procedure schema (may be null)
				"PROCEDURE_NAME", // String => procedure name
				"reserved1", // reserved for future use
				"reserved2", // reserved for future use
				"REMARKS", // String => explanatory comment
				"PROCEDURE_TYPE", // short => kind of procedure
				"SPECIFIC_NAME" // String => unique name for this procedure
		};

		static final int[] COLUMN_TYPES = {Types.VARCHAR, // PROCEDURE_CAT
				Types.VARCHAR, // PROCEDURE_SCHEM
				Types.VARCHAR, // PROCEDURE_NAME
				Types.VARCHAR, // reserved1
				Types.VARCHAR, // reserved2
				Types.VARCHAR, // REMARKS
				Types.SMALLINT, // PROCEDURE_TYPE
				Types.VARCHAR // SPECIFIC_NAME
		};

		private Procedures() {
		}
	}

	/**
	 * Column definitions for getProcedureColumns() result set.
	 */
	public static final class ProcedureColumns {
		static final String[] COLUMN_NAMES = {"PROCEDURE_CAT", // String => procedure catalog (may be null)
				"PROCEDURE_SCHEM", // String => procedure schema (may be null)
				"PROCEDURE_NAME", // String => procedure name
				"COLUMN_NAME", // String => column/parameter name
				"COLUMN_TYPE", // Short => kind of column/parameter
				"DATA_TYPE", // int => SQL type from java.sql.Types
				"TYPE_NAME", // String => SQL type name
				"PRECISION", // int => precision
				"LENGTH", // int => length in bytes of data
				"SCALE", // Short => scale
				"RADIX", // Short => radix
				"NULLABLE", // Short => can it contain NULL
				"REMARKS" // String => comment describing parameter/column
		};

		static final int[] COLUMN_TYPES = {Types.VARCHAR, // PROCEDURE_CAT
				Types.VARCHAR, // PROCEDURE_SCHEM
				Types.VARCHAR, // PROCEDURE_NAME
				Types.VARCHAR, // COLUMN_NAME
				Types.SMALLINT, // COLUMN_TYPE
				Types.INTEGER, // DATA_TYPE
				Types.VARCHAR, // TYPE_NAME
				Types.INTEGER, // PRECISION
				Types.INTEGER, // LENGTH
				Types.SMALLINT, // SCALE
				Types.SMALLINT, // RADIX
				Types.SMALLINT, // NULLABLE
				Types.VARCHAR // REMARKS
		};

		private ProcedureColumns() {
		}
	}

	/**
	 * Column definitions for getImportedKeys() and getExportedKeys() result sets.
	 */
	public static final class ForeignKeys {
		static final String[] COLUMN_NAMES = {"PKTABLE_CAT", // String => primary key table catalog (may be null)
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

		static final int[] COLUMN_TYPES = {Types.VARCHAR, // PKTABLE_CAT
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

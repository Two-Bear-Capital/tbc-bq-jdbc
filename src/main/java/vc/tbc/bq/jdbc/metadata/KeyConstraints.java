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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.DatabaseMetaData;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Reads BigQuery's declarative key constraints and reshapes them into the
 * result sets {@link DatabaseMetaData#getPrimaryKeys},
 * {@link DatabaseMetaData#getImportedKeys},
 * {@link DatabaseMetaData#getExportedKeys} and
 * {@link DatabaseMetaData#getCrossReference} are specified to return.
 *
 * <p>
 * BigQuery supports {@code PRIMARY KEY ... NOT ENFORCED} and
 * {@code FOREIGN KEY ... NOT ENFORCED} declarations. It never validates them —
 * they exist so the query planner and external tools can exploit the
 * relationships the schema author asserts. Every constraint this class reports
 * is therefore unenforced, which JDBC has no way to express; callers that need
 * to know should treat BigQuery keys as documentation of intent rather than a
 * guarantee about the data.
 *
 * <h2>Where the data comes from</h2>
 *
 * <p>
 * Three per-dataset {@code INFORMATION_SCHEMA} views describe the constraints,
 * and all three are needed:
 *
 * <ul>
 * <li>{@code TABLE_CONSTRAINTS} — one row per constraint, giving its name, its
 * table, and whether it is a {@code PRIMARY KEY} or a {@code FOREIGN KEY}.
 * <li>{@code KEY_COLUMN_USAGE} — one row per constrained column on the
 * <em>declaring</em> table, with {@code ordinal_position} (its place in the
 * key) and, for foreign keys, {@code position_in_unique_constraint}.
 * <li>{@code CONSTRAINT_COLUMN_USAGE} — for a foreign key, the columns of the
 * <em>referenced</em> table. It identifies the parent table but carries no
 * ordinal, so it cannot by itself say which foreign-key column pairs with which
 * referenced column.
 * </ul>
 *
 * <h2>Pairing a composite foreign key with its parent</h2>
 *
 * <p>
 * The pairing comes from {@code position_in_unique_constraint}, not from the
 * order rows happen to arrive in. BigQuery requires a foreign key to reference
 * exactly the primary-key columns of the parent table — declaring one against
 * any other column fails at DDL time — so
 * {@code position_in_unique_constraint = k} means "this column references the
 * parent's primary-key column at {@code ordinal_position} k". Reading
 * {@code CONSTRAINT_COLUMN_USAGE} in row order instead would silently transpose
 * the columns of a foreign key declared out of primary-key order, such as
 * {@code FOREIGN KEY (b, a) REFERENCES parent(p2, p1)}, and produce join
 * conditions that are wrong rather than absent.
 *
 * @since 2.2.0
 */
final class KeyConstraints {

	private static final Logger logger = LoggerFactory.getLogger(KeyConstraints.class);

	/**
	 * Identifiers accepted for interpolation into an {@code INFORMATION_SCHEMA}
	 * query.
	 *
	 * <p>
	 * Dataset names reach {@link #constraintQuery} straight from the caller —
	 * {@code getPrimaryKeys(catalog, schema, table)} takes {@code schema} as a
	 * literal name — and BigQuery has no parameter binding for the table path, so
	 * the name has to be interpolated. Anything a project or dataset may legally be
	 * called matches this pattern, and nothing matching it can close the
	 * surrounding backticks.
	 */
	private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[A-Za-z0-9_-]+");

	/** {@code TABLE_CONSTRAINTS.constraint_type} for a primary key. */
	private static final String PRIMARY_KEY = "PRIMARY KEY";

	/**
	 * Column layout of the per-dataset constraint snapshot.
	 *
	 * <p>
	 * This is an internal shape, not a JDBC one. It exists because the snapshot is
	 * held in {@link vc.tbc.bq.jdbc.config.MetadataCache}, which stores
	 * {@link MetadataResultSet}s, and because all four key methods are built from
	 * the same per-dataset read — caching the JDBC-shaped answers instead would
	 * mean one BigQuery query per table asked about, which is precisely the
	 * introspection cost this driver exists to avoid.
	 */
	static final String[] SNAPSHOT_COLUMN_NAMES = {"TABLE_SCHEM", "TABLE_NAME", "CONSTRAINT_NAME", "CONSTRAINT_TYPE",
			"COLUMN_NAME", "ORDINAL_POSITION", "REF_ORDINAL", "REF_CATALOG", "REF_SCHEM", "REF_TABLE", "REF_COLUMN",
			"REF_COLUMN_COUNT"};

	static final int[] SNAPSHOT_COLUMN_TYPES = {Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR,
			Types.VARCHAR, Types.BIGINT, Types.BIGINT, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR,
			Types.BIGINT};

	private static final int IDX_SCHEM = 0;
	private static final int IDX_TABLE = 1;
	private static final int IDX_CONSTRAINT_NAME = 2;
	private static final int IDX_CONSTRAINT_TYPE = 3;
	private static final int IDX_COLUMN_NAME = 4;
	private static final int IDX_ORDINAL = 5;
	private static final int IDX_REF_ORDINAL = 6;
	private static final int IDX_REF_CATALOG = 7;
	private static final int IDX_REF_SCHEM = 8;
	private static final int IDX_REF_TABLE = 9;
	private static final int IDX_REF_COLUMN = 10;
	private static final int IDX_REF_COLUMN_COUNT = 11;

	private KeyConstraints() {
	}

	/**
	 * One column of a key, as the declaring table sees it.
	 *
	 * @param columnName
	 *            the constrained column
	 * @param ordinal
	 *            1-based position within this key
	 * @param referencedOrdinal
	 *            for a foreign key, the 1-based position of the parent primary-key
	 *            column this one references; {@code null} for a primary key
	 */
	record KeyColumn(String columnName, int ordinal, Integer referencedOrdinal) {
	}

	/**
	 * A primary- or foreign-key constraint on one table.
	 *
	 * @param schema
	 *            dataset holding the declaring table
	 * @param table
	 *            the declaring table
	 * @param name
	 *            BigQuery's constraint name, reported verbatim. BigQuery qualifies
	 *            it with the table, so a primary key appears as {@code orders.pk$}
	 *            (primary keys cannot be named) and a named foreign key as
	 *            {@code orders.fk_customer}. Reporting it unchanged keeps it
	 *            joinable back to {@code INFORMATION_SCHEMA}.
	 * @param primaryKey
	 *            whether this is the table's primary key
	 * @param columns
	 *            constrained columns, ordered by {@link KeyColumn#ordinal()}
	 * @param referencedCatalog
	 *            project of the referenced table ({@code null} for a primary key)
	 * @param referencedSchema
	 *            dataset of the referenced table ({@code null} for a primary key)
	 * @param referencedTable
	 *            the referenced table ({@code null} for a primary key)
	 * @param soleReferencedColumn
	 *            an arbitrary referenced column name, usable only when
	 *            {@code referencedColumnCount} is 1
	 * @param referencedColumnCount
	 *            how many columns the referenced key spans
	 */
	record Constraint(String schema, String table, String name, boolean primaryKey, List<KeyColumn> columns,
			String referencedCatalog, String referencedSchema, String referencedTable, String soleReferencedColumn,
			int referencedColumnCount) {

		/** Fully-qualified name of the table this constraint is declared on. */
		String qualifiedTable(String catalog) {
			return key(catalog, schema, table);
		}

		/**
		 * Fully-qualified name of the referenced table, or null if not a foreign key.
		 */
		String qualifiedReferencedTable() {
			return referencedTable == null ? null : key(referencedCatalog, referencedSchema, referencedTable);
		}
	}

	/**
	 * The primary keys of every table seen so far, so a foreign key can be resolved
	 * to the parent's column names and constraint name.
	 */
	static final class PrimaryKeyIndex {

		private final Map<String, List<String>> columnsByTable = new HashMap<>();
		private final Map<String, String> nameByTable = new HashMap<>();

		void add(String catalog, Constraint pk) {
			String tableKey = pk.qualifiedTable(catalog);
			columnsByTable.put(tableKey, pk.columns().stream().map(KeyColumn::columnName).toList());
			nameByTable.put(tableKey, pk.name());
		}

		/** Registers every primary key in {@code constraints}. */
		void addAll(String catalog, List<Constraint> constraints) {
			for (Constraint constraint : constraints) {
				if (constraint.primaryKey()) {
					add(catalog, constraint);
				}
			}
		}

		boolean covers(String qualifiedTable) {
			return columnsByTable.containsKey(qualifiedTable);
		}

		/**
		 * The parent primary-key column a foreign-key column references.
		 *
		 * @param qualifiedTable
		 *            the parent table
		 * @param ordinal
		 *            1-based position within the parent's primary key
		 * @return the column name, or {@code null} if the parent's primary key is
		 *         unknown or does not extend that far
		 */
		String columnAt(String qualifiedTable, Integer ordinal) {
			if (ordinal == null) {
				return null;
			}
			List<String> columns = columnsByTable.get(qualifiedTable);
			if (columns == null || ordinal < 1 || ordinal > columns.size()) {
				return null;
			}
			return columns.get(ordinal - 1);
		}

		String constraintName(String qualifiedTable) {
			return nameByTable.get(qualifiedTable);
		}
	}

	/**
	 * Whether an identifier is safe to interpolate into a query.
	 *
	 * @param identifier
	 *            a project or dataset name
	 * @return true if the name is non-null and contains only characters BigQuery
	 *         permits in one
	 */
	static boolean isSafeIdentifier(String identifier) {
		return identifier != null && SAFE_IDENTIFIER.matcher(identifier).matches();
	}

	/**
	 * Builds the single query that reads every key constraint in one dataset.
	 *
	 * <p>
	 * {@code CONSTRAINT_COLUMN_USAGE} is collapsed to one row per constraint before
	 * the join. Left as-is it holds a row per referenced column, which would
	 * multiply the {@code KEY_COLUMN_USAGE} rows of a composite key by its own
	 * width; the referenced <em>columns</em> are recovered from the parent's
	 * primary key instead, so all that is wanted here is the parent's identity.
	 *
	 * @param projectId
	 *            the project owning the dataset; must be a safe identifier
	 * @param datasetId
	 *            the dataset to read; must be a safe identifier
	 * @return the SQL text
	 */
	static String constraintQuery(String projectId, String datasetId) {
		String path = "`" + projectId + "`.`" + datasetId + "`.INFORMATION_SCHEMA.";
		return "WITH ccu AS (SELECT constraint_name, "
				+ "ANY_VALUE(table_catalog) AS ref_catalog, ANY_VALUE(table_schema) AS ref_schema, "
				+ "ANY_VALUE(table_name) AS ref_table, ANY_VALUE(column_name) AS ref_column, "
				+ "COUNT(*) AS ref_column_count FROM " + path + "CONSTRAINT_COLUMN_USAGE GROUP BY constraint_name) "
				+ "SELECT tc.table_schema, tc.table_name, tc.constraint_name, tc.constraint_type, "
				+ "kcu.column_name, kcu.ordinal_position, kcu.position_in_unique_constraint, "
				+ "ccu.ref_catalog, ccu.ref_schema, ccu.ref_table, ccu.ref_column, ccu.ref_column_count " + "FROM "
				+ path + "TABLE_CONSTRAINTS tc " + "JOIN " + path + "KEY_COLUMN_USAGE kcu "
				+ "ON tc.constraint_catalog = kcu.constraint_catalog "
				+ "AND tc.constraint_schema = kcu.constraint_schema " + "AND tc.constraint_name = kcu.constraint_name "
				+ "LEFT JOIN ccu ON ccu.constraint_name = tc.constraint_name "
				+ "ORDER BY tc.table_name, tc.constraint_name, kcu.ordinal_position";
	}

	/**
	 * Builds one snapshot row from a {@code constraintQuery} result row.
	 *
	 * @param row
	 *            the BigQuery row
	 * @param defaultSchema
	 *            dataset to record when the view omits {@code table_schema}
	 * @return the snapshot row, in {@link #SNAPSHOT_COLUMN_NAMES} order
	 */
	static Object[] snapshotRow(com.google.cloud.bigquery.FieldValueList row, String defaultSchema) {
		return new Object[]{stringOr(row, "table_schema", defaultSchema), string(row, "table_name"),
				string(row, "constraint_name"), string(row, "constraint_type"), string(row, "column_name"),
				longValue(row, "ordinal_position"), longValue(row, "position_in_unique_constraint"),
				string(row, "ref_catalog"), string(row, "ref_schema"), string(row, "ref_table"),
				string(row, "ref_column"), longValue(row, "ref_column_count")};
	}

	private static String string(com.google.cloud.bigquery.FieldValueList row, String field) {
		com.google.cloud.bigquery.FieldValue value = row.get(field);
		return value == null || value.isNull() ? null : value.getStringValue();
	}

	private static String stringOr(com.google.cloud.bigquery.FieldValueList row, String field, String fallback) {
		String value = string(row, field);
		return value == null ? fallback : value;
	}

	private static Long longValue(com.google.cloud.bigquery.FieldValueList row, String field) {
		com.google.cloud.bigquery.FieldValue value = row.get(field);
		return value == null || value.isNull() ? null : value.getLongValue();
	}

	/**
	 * Groups snapshot rows into constraints, one per distinct constraint name.
	 *
	 * <p>
	 * Rows arrive one per constrained column. Columns are sorted by ordinal here
	 * rather than trusted to arrive in order, so a snapshot restored from cache or
	 * merged across datasets is ordered the same way a freshly-read one is.
	 *
	 * @param rows
	 *            snapshot rows in {@link #SNAPSHOT_COLUMN_NAMES} order
	 * @return the constraints they describe
	 */
	static List<Constraint> assemble(List<Object[]> rows) {
		record Pending(String schema, String table, String name, String type, List<KeyColumn> columns,
				String refCatalog, String refSchema, String refTable, String refColumn, int refColumnCount) {
		}

		Map<String, Pending> byConstraint = new LinkedHashMap<>();
		for (Object[] row : rows) {
			String schema = asString(row[IDX_SCHEM]);
			String table = asString(row[IDX_TABLE]);
			String name = asString(row[IDX_CONSTRAINT_NAME]);
			if (name == null) {
				continue;
			}
			// Keyed by table as well as name: names are unique within a dataset, but a
			// scan spanning datasets merges snapshots that were each unique only
			// locally, and two datasets both holding an "orders.pk$" is the norm.
			String groupKey = schema + " " + table + " " + name;
			Pending pending = byConstraint.computeIfAbsent(groupKey,
					ignored -> new Pending(schema, table, name, asString(row[IDX_CONSTRAINT_TYPE]), new ArrayList<>(),
							asString(row[IDX_REF_CATALOG]), asString(row[IDX_REF_SCHEM]), asString(row[IDX_REF_TABLE]),
							asString(row[IDX_REF_COLUMN]), (int) asLong(row[IDX_REF_COLUMN_COUNT], 0)));

			String columnName = asString(row[IDX_COLUMN_NAME]);
			if (columnName != null) {
				pending.columns().add(new KeyColumn(columnName, (int) asLong(row[IDX_ORDINAL], 0),
						row[IDX_REF_ORDINAL] == null ? null : (int) asLong(row[IDX_REF_ORDINAL], 0)));
			}
		}

		List<Constraint> constraints = new ArrayList<>(byConstraint.size());
		for (Pending pending : byConstraint.values()) {
			List<KeyColumn> columns = new ArrayList<>(pending.columns());
			columns.sort(Comparator.comparingInt(KeyColumn::ordinal));
			boolean isPrimaryKey = PRIMARY_KEY.equalsIgnoreCase(pending.type());
			constraints.add(new Constraint(pending.schema(), pending.table(), pending.name(), isPrimaryKey,
					List.copyOf(columns), isPrimaryKey ? null : pending.refCatalog(),
					isPrimaryKey ? null : pending.refSchema(), isPrimaryKey ? null : pending.refTable(),
					isPrimaryKey ? null : pending.refColumn(), isPrimaryKey ? 0 : pending.refColumnCount()));
		}
		return constraints;
	}

	private static String asString(Object value) {
		return value == null ? null : value.toString();
	}

	private static long asLong(Object value, long fallback) {
		if (value instanceof Number number) {
			return number.longValue();
		}
		if (value instanceof String text && !text.isEmpty()) {
			try {
				return Long.parseLong(text);
			} catch (NumberFormatException e) {
				return fallback;
			}
		}
		return fallback;
	}

	/**
	 * Builds {@code getPrimaryKeys} rows.
	 *
	 * <p>
	 * Ordered by {@code TABLE_SCHEM, TABLE_NAME, COLUMN_NAME}: the JDBC contract
	 * asks for {@code COLUMN_NAME} order, and the table qualifiers keep that
	 * meaningful when the call was not narrowed to a single table. {@code KEY_SEQ}
	 * still carries the position within the key.
	 *
	 * @param catalog
	 *            project to report as {@code TABLE_CAT}
	 * @param constraints
	 *            constraints to draw the primary keys from
	 * @return rows in {@link MetadataColumns.PrimaryKeys} order
	 */
	static List<Object[]> primaryKeyRows(String catalog, List<Constraint> constraints) {
		List<Object[]> rows = new ArrayList<>();
		for (Constraint constraint : constraints) {
			if (!constraint.primaryKey()) {
				continue;
			}
			for (KeyColumn column : constraint.columns()) {
				rows.add(new Object[]{catalog, // TABLE_CAT
						constraint.schema(), // TABLE_SCHEM
						constraint.table(), // TABLE_NAME
						column.columnName(), // COLUMN_NAME
						(short) column.ordinal(), // KEY_SEQ
						constraint.name() // PK_NAME
				});
			}
		}
		rows.sort(Comparator.comparing((Object[] row) -> text(row[1])).thenComparing(row -> text(row[2]))
				.thenComparing(row -> text(row[3])));
		return rows;
	}

	/**
	 * Builds {@code getImportedKeys} / {@code getExportedKeys} /
	 * {@code getCrossReference} rows.
	 *
	 * <p>
	 * {@code UPDATE_RULE} and {@code DELETE_RULE} are {@code importedKeyNoAction}
	 * and {@code DEFERRABILITY} is {@code importedKeyNotDeferrable}, which is not a
	 * default but a description: BigQuery never enforces these constraints, so
	 * there is no referential action to take and nothing to defer. Its
	 * {@code TABLE_CONSTRAINTS} view confirms it, reporting {@code is_deferrable}
	 * and {@code initially_deferred} as {@code NO} for every constraint.
	 *
	 * @param catalog
	 *            project the foreign-key side lives in
	 * @param constraints
	 *            constraints to draw the foreign keys from
	 * @param index
	 *            primary keys of the referenced tables
	 * @param orderByForeignKeyTable
	 *            order by the foreign-key table, as {@code getExportedKeys} and
	 *            {@code getCrossReference} specify; false orders by the primary-key
	 *            table, as {@code getImportedKeys} specifies
	 * @return rows in {@link MetadataColumns.ForeignKeys} order
	 */
	static List<Object[]> foreignKeyRows(String catalog, List<Constraint> constraints, PrimaryKeyIndex index,
			boolean orderByForeignKeyTable) {
		List<Object[]> rows = new ArrayList<>();
		for (Constraint constraint : constraints) {
			if (constraint.primaryKey()) {
				continue;
			}
			String parentTable = constraint.qualifiedReferencedTable();
			for (KeyColumn column : constraint.columns()) {
				rows.add(new Object[]{constraint.referencedCatalog() != null ? constraint.referencedCatalog() : catalog, // PKTABLE_CAT
						constraint.referencedSchema(), // PKTABLE_SCHEM
						constraint.referencedTable(), // PKTABLE_NAME
						referencedColumn(constraint, column, index, parentTable), // PKCOLUMN_NAME
						catalog, // FKTABLE_CAT
						constraint.schema(), // FKTABLE_SCHEM
						constraint.table(), // FKTABLE_NAME
						column.columnName(), // FKCOLUMN_NAME
						(short) column.ordinal(), // KEY_SEQ
						(short) DatabaseMetaData.importedKeyNoAction, // UPDATE_RULE
						(short) DatabaseMetaData.importedKeyNoAction, // DELETE_RULE
						constraint.name(), // FK_NAME
						index.constraintName(parentTable), // PK_NAME
						(short) DatabaseMetaData.importedKeyNotDeferrable // DEFERRABILITY
				});
			}
		}

		int firstSortColumn = orderByForeignKeyTable ? 4 : 0;
		rows.sort(Comparator.comparing((Object[] row) -> text(row[firstSortColumn]))
				.thenComparing(row -> text(row[firstSortColumn + 1]))
				.thenComparing(row -> text(row[firstSortColumn + 2])).thenComparing(row -> (Short) row[8]));
		return rows;
	}

	/**
	 * The parent column a foreign-key column points at.
	 *
	 * <p>
	 * Normally this is the parent's primary-key column at
	 * {@code position_in_unique_constraint}. When the parent's primary key could
	 * not be read — a foreign key into a dataset the caller cannot list, most
	 * likely — a single-column key still has an unambiguous answer, because
	 * {@code CONSTRAINT_COLUMN_USAGE} named exactly one referenced column and there
	 * is only one way to pair them. A composite key in that situation has no sound
	 * answer, so it reports null rather than guessing at an ordering that would
	 * produce a wrong join instead of a visibly incomplete one.
	 */
	private static String referencedColumn(Constraint constraint, KeyColumn column, PrimaryKeyIndex index,
			String parentTable) {
		String resolved = index.columnAt(parentTable, column.referencedOrdinal());
		if (resolved != null) {
			return resolved;
		}
		if (constraint.columns().size() == 1 && constraint.referencedColumnCount() == 1) {
			return constraint.soleReferencedColumn();
		}
		logger.warn(
				"Cannot resolve the referenced column for foreign key {} on {}.{}: the primary key of {} is unavailable, "
						+ "so PKCOLUMN_NAME is reported as null",
				constraint.name(), constraint.schema(), constraint.table(), parentTable);
		return null;
	}

	/** Null-safe key for sorting, so a null qualifier does not blow up the sort. */
	private static String text(Object value) {
		return value == null ? "" : value.toString();
	}

	/**
	 * Fully-qualified table name used as a map key.
	 *
	 * <p>
	 * Case is preserved. BigQuery dataset and table names are case-sensitive, so
	 * folding them would merge distinct tables.
	 */
	static String key(String catalog, String schema, String table) {
		return catalog + "." + schema + "." + table;
	}
}

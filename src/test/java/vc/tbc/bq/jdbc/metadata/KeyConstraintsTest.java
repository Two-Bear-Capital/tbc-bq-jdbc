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

import java.sql.DatabaseMetaData;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the constraint reshaping that backs {@code getPrimaryKeys},
 * {@code getImportedKeys}, {@code getExportedKeys} and
 * {@code getCrossReference}.
 *
 * <p>
 * The snapshot rows these tests build are transcribed from what BigQuery
 * actually returns for the fixture schema in
 * {@code RealKeyConstraintMetadataTest}: a {@code parent(p1, p2)} with a
 * composite primary key, a {@code child} declaring both a single-column and a
 * composite foreign key into it, and a {@code child_rev} whose foreign key
 * lists the parent's key columns in the opposite order.
 *
 * @since 2.2.0
 */
class KeyConstraintsTest {

	private static final String PROJECT = "test-project";
	private static final String DATASET = "shop";

	/** Column indices into a {@link MetadataColumns.ForeignKeys} row. */
	private static final int PKTABLE_SCHEM = 1;
	private static final int PKTABLE_NAME = 2;
	private static final int PKCOLUMN_NAME = 3;
	private static final int FKTABLE_NAME = 6;
	private static final int FKCOLUMN_NAME = 7;
	private static final int KEY_SEQ = 8;
	private static final int UPDATE_RULE = 9;
	private static final int DELETE_RULE = 10;
	private static final int FK_NAME = 11;
	private static final int PK_NAME = 12;
	private static final int DEFERRABILITY = 13;

	private final List<Object[]> snapshot = new ArrayList<>();

	private void primaryKeyColumn(String table, String column, int ordinal) {
		snapshot.add(new Object[]{DATASET, table, table + ".pk$", "PRIMARY KEY", column, (long) ordinal, null, PROJECT,
				DATASET, table, column, 1L});
	}

	private void foreignKeyColumn(String table, String constraint, String column, int ordinal, Integer parentOrdinal,
			String parentSchema, String parentTable, String someParentColumn, int parentWidth) {
		snapshot.add(new Object[]{DATASET, table, table + "." + constraint, "FOREIGN KEY", column, (long) ordinal,
				parentOrdinal == null ? null : (long) parentOrdinal, PROJECT, parentSchema, parentTable,
				someParentColumn, (long) parentWidth});
	}

	private List<KeyConstraints.Constraint> assemble() {
		return KeyConstraints.assemble(snapshot);
	}

	private KeyConstraints.PrimaryKeyIndex indexOf(List<KeyConstraints.Constraint> constraints) {
		KeyConstraints.PrimaryKeyIndex index = new KeyConstraints.PrimaryKeyIndex();
		index.addAll(PROJECT, constraints);
		return index;
	}

	// Assembly

	@Test
	void assembleGroupsRowsIntoOneConstraintPerKey() {
		primaryKeyColumn("parent", "p1", 1);
		primaryKeyColumn("parent", "p2", 2);
		foreignKeyColumn("child", "fk_parent", "f1", 1, 1, DATASET, "parent", "p1", 2);
		foreignKeyColumn("child", "fk_parent", "f2", 2, 2, DATASET, "parent", "p1", 2);

		List<KeyConstraints.Constraint> constraints = assemble();

		assertEquals(2, constraints.size());
		assertTrue(constraints.get(0).primaryKey());
		assertEquals(List.of("p1", "p2"), constraints.get(0).columns().stream().map(c -> c.columnName()).toList());
		assertFalse(constraints.get(1).primaryKey());
		assertEquals("parent", constraints.get(1).referencedTable());
	}

	/**
	 * A snapshot restored from cache or merged across datasets need not arrive in
	 * ordinal order, and the composite pairing depends on it.
	 */
	@Test
	void assembleOrdersColumnsByOrdinalRegardlessOfRowOrder() {
		primaryKeyColumn("parent", "p2", 2);
		primaryKeyColumn("parent", "p1", 1);

		List<KeyConstraints.Constraint> constraints = assemble();

		assertEquals(List.of("p1", "p2"), constraints.get(0).columns().stream().map(c -> c.columnName()).toList());
	}

	/**
	 * A scan spanning datasets merges snapshots that were each unique only within
	 * their own dataset, and two datasets both holding an {@code orders.pk$} is the
	 * norm rather than the exception.
	 */
	@Test
	void assembleKeepsSameNamedConstraintsInDifferentDatasetsApart() {
		snapshot.add(new Object[]{"sales", "orders", "orders.pk$", "PRIMARY KEY", "id", 1L, null, PROJECT, "sales",
				"orders", "id", 1L});
		snapshot.add(new Object[]{"archive", "orders", "orders.pk$", "PRIMARY KEY", "order_key", 1L, null, PROJECT,
				"archive", "orders", "order_key", 1L});

		List<KeyConstraints.Constraint> constraints = assemble();

		assertEquals(2, constraints.size());
		assertEquals("id", constraints.get(0).columns().get(0).columnName());
		assertEquals("order_key", constraints.get(1).columns().get(0).columnName());
	}

	// Primary keys

	@Test
	void primaryKeyRowsCarryKeySeqAndConstraintName() {
		primaryKeyColumn("parent", "p1", 1);
		primaryKeyColumn("parent", "p2", 2);

		List<Object[]> rows = KeyConstraints.primaryKeyRows(PROJECT, assemble());

		assertEquals(2, rows.size());
		assertArrayEquals(new Object[]{PROJECT, DATASET, "parent", "p1", (short) 1, "parent.pk$"}, rows.get(0));
		assertArrayEquals(new Object[]{PROJECT, DATASET, "parent", "p2", (short) 2, "parent.pk$"}, rows.get(1));
	}

	/** JDBC specifies {@code getPrimaryKeys} results in COLUMN_NAME order. */
	@Test
	void primaryKeyRowsAreOrderedByColumnName() {
		primaryKeyColumn("parent", "zebra", 1);
		primaryKeyColumn("parent", "alpha", 2);

		List<Object[]> rows = KeyConstraints.primaryKeyRows(PROJECT, assemble());

		assertEquals("alpha", rows.get(0)[3]);
		assertEquals("zebra", rows.get(1)[3]);
		assertEquals((short) 2, rows.get(0)[4], "KEY_SEQ still reports position within the key");
	}

	@Test
	void primaryKeyRowsIgnoreForeignKeys() {
		foreignKeyColumn("child", "fk_parent", "f1", 1, 1, DATASET, "parent", "p1", 1);

		assertTrue(KeyConstraints.primaryKeyRows(PROJECT, assemble()).isEmpty());
	}

	// Foreign keys

	@Test
	void foreignKeyRowsPairCompositeColumnsByPositionInUniqueConstraint() {
		primaryKeyColumn("parent", "p1", 1);
		primaryKeyColumn("parent", "p2", 2);
		foreignKeyColumn("child", "fk_parent", "f1", 1, 1, DATASET, "parent", "p1", 2);
		foreignKeyColumn("child", "fk_parent", "f2", 2, 2, DATASET, "parent", "p1", 2);

		List<KeyConstraints.Constraint> constraints = assemble();
		List<Object[]> rows = KeyConstraints.foreignKeyRows(PROJECT, foreignKeysOf(constraints), indexOf(constraints),
				false);

		assertEquals(2, rows.size());
		assertEquals("f1", rows.get(0)[FKCOLUMN_NAME]);
		assertEquals("p1", rows.get(0)[PKCOLUMN_NAME]);
		assertEquals("f2", rows.get(1)[FKCOLUMN_NAME]);
		assertEquals("p2", rows.get(1)[PKCOLUMN_NAME]);
	}

	/**
	 * The case that makes {@code position_in_unique_constraint} load-bearing.
	 * {@code FOREIGN KEY (g2, g1) REFERENCES parent(p2, p1)} pairs g2 with p2, and
	 * reading the referenced columns in any positional order would silently
	 * transpose them into a wrong join rather than a missing one.
	 */
	@Test
	void foreignKeyRowsRespectReversedReferenceOrder() {
		primaryKeyColumn("parent", "p1", 1);
		primaryKeyColumn("parent", "p2", 2);
		foreignKeyColumn("child_rev", "fk_rev", "g2", 1, 2, DATASET, "parent", "p2", 2);
		foreignKeyColumn("child_rev", "fk_rev", "g1", 2, 1, DATASET, "parent", "p2", 2);

		List<KeyConstraints.Constraint> constraints = assemble();
		List<Object[]> rows = KeyConstraints.foreignKeyRows(PROJECT, foreignKeysOf(constraints), indexOf(constraints),
				false);

		assertEquals("g2", rows.get(0)[FKCOLUMN_NAME]);
		assertEquals("p2", rows.get(0)[PKCOLUMN_NAME]);
		assertEquals("g1", rows.get(1)[FKCOLUMN_NAME]);
		assertEquals("p1", rows.get(1)[PKCOLUMN_NAME]);
	}

	@Test
	void foreignKeyRowsReportNoActionAndNotDeferrable() {
		primaryKeyColumn("parent", "p1", 1);
		foreignKeyColumn("child", "fk_parent", "f1", 1, 1, DATASET, "parent", "p1", 1);

		List<KeyConstraints.Constraint> constraints = assemble();
		Object[] row = KeyConstraints.foreignKeyRows(PROJECT, foreignKeysOf(constraints), indexOf(constraints), false)
				.get(0);

		assertEquals((short) DatabaseMetaData.importedKeyNoAction, row[UPDATE_RULE]);
		assertEquals((short) DatabaseMetaData.importedKeyNoAction, row[DELETE_RULE]);
		assertEquals((short) DatabaseMetaData.importedKeyNotDeferrable, row[DEFERRABILITY]);
		assertEquals((short) 1, row[KEY_SEQ]);
		assertEquals("child.fk_parent", row[FK_NAME]);
		assertEquals("parent.pk$", row[PK_NAME]);
	}

	/**
	 * When the parent's primary key was never read — a foreign key into a dataset
	 * the caller cannot list — a single-column key is still unambiguous, because
	 * {@code CONSTRAINT_COLUMN_USAGE} named exactly one referenced column.
	 */
	@Test
	void foreignKeyRowsFallBackToTheSoleReferencedColumnWhenTheParentIsUnknown() {
		foreignKeyColumn("child", "fk_remote", "r_ref", 1, 1, "other_dataset", "remote_parent", "rp_id", 1);

		List<KeyConstraints.Constraint> constraints = assemble();
		Object[] row = KeyConstraints.foreignKeyRows(PROJECT, foreignKeysOf(constraints), indexOf(constraints), false)
				.get(0);

		assertEquals("other_dataset", row[PKTABLE_SCHEM]);
		assertEquals("remote_parent", row[PKTABLE_NAME]);
		assertEquals("rp_id", row[PKCOLUMN_NAME]);
		assertNull(row[PK_NAME], "the parent's constraint name is genuinely unknown");
	}

	/**
	 * A composite key with an unreadable parent has no sound answer. Reporting null
	 * keeps the relationship visible while making the gap obvious; guessing an
	 * order would produce a join that is wrong rather than incomplete.
	 */
	@Test
	void foreignKeyRowsReportNullColumnForCompositeKeyWithUnknownParent() {
		foreignKeyColumn("child", "fk_remote", "r1", 1, 1, "other_dataset", "remote_parent", "rp1", 2);
		foreignKeyColumn("child", "fk_remote", "r2", 2, 2, "other_dataset", "remote_parent", "rp1", 2);

		List<KeyConstraints.Constraint> constraints = assemble();
		List<Object[]> rows = KeyConstraints.foreignKeyRows(PROJECT, foreignKeysOf(constraints), indexOf(constraints),
				false);

		assertEquals(2, rows.size());
		assertNull(rows.get(0)[PKCOLUMN_NAME]);
		assertNull(rows.get(1)[PKCOLUMN_NAME]);
		assertEquals("r1", rows.get(0)[FKCOLUMN_NAME], "the relationship is still reported");
	}

	@Test
	void foreignKeyRowsOrderByPrimaryKeyTableForImportedKeys() {
		primaryKeyColumn("alpha", "a", 1);
		primaryKeyColumn("zulu", "z", 1);
		foreignKeyColumn("child", "fk_zulu", "z_ref", 1, 1, DATASET, "zulu", "z", 1);
		foreignKeyColumn("child", "fk_alpha", "a_ref", 1, 1, DATASET, "alpha", "a", 1);

		List<KeyConstraints.Constraint> constraints = assemble();
		List<Object[]> rows = KeyConstraints.foreignKeyRows(PROJECT, foreignKeysOf(constraints), indexOf(constraints),
				false);

		assertEquals("alpha", rows.get(0)[PKTABLE_NAME]);
		assertEquals("zulu", rows.get(1)[PKTABLE_NAME]);
	}

	@Test
	void foreignKeyRowsOrderByForeignKeyTableForExportedKeys() {
		primaryKeyColumn("parent", "p1", 1);
		foreignKeyColumn("zulu_child", "fk_parent", "f", 1, 1, DATASET, "parent", "p1", 1);
		foreignKeyColumn("alpha_child", "fk_parent", "f", 1, 1, DATASET, "parent", "p1", 1);

		List<KeyConstraints.Constraint> constraints = assemble();
		List<Object[]> rows = KeyConstraints.foreignKeyRows(PROJECT, foreignKeysOf(constraints), indexOf(constraints),
				true);

		assertEquals("alpha_child", rows.get(0)[FKTABLE_NAME]);
		assertEquals("zulu_child", rows.get(1)[FKTABLE_NAME]);
	}

	// Query construction

	@Test
	void constraintQueryReadsAllThreeConstraintViewsForTheDataset() {
		String sql = KeyConstraints.constraintQuery(PROJECT, DATASET);

		assertTrue(sql.contains("`test-project`.`shop`.INFORMATION_SCHEMA.TABLE_CONSTRAINTS"));
		assertTrue(sql.contains("`test-project`.`shop`.INFORMATION_SCHEMA.KEY_COLUMN_USAGE"));
		assertTrue(sql.contains("`test-project`.`shop`.INFORMATION_SCHEMA.CONSTRAINT_COLUMN_USAGE"));
		assertTrue(sql.contains("position_in_unique_constraint"));
	}

	/**
	 * {@code schema} arrives from the caller and has to be interpolated, since
	 * BigQuery cannot parameterise a table path.
	 */
	@Test
	void unsafeIdentifiersAreRejected() {
		assertTrue(KeyConstraints.isSafeIdentifier("my_dataset-1"));
		assertFalse(KeyConstraints.isSafeIdentifier("shop`.INFORMATION_SCHEMA.TABLES UNION ALL SELECT 1 --"));
		assertFalse(KeyConstraints.isSafeIdentifier("has space"));
		assertFalse(KeyConstraints.isSafeIdentifier(""));
		assertFalse(KeyConstraints.isSafeIdentifier(null));
	}

	private static List<KeyConstraints.Constraint> foreignKeysOf(List<KeyConstraints.Constraint> constraints) {
		return constraints.stream().filter(c -> !c.primaryKey()).toList();
	}
}

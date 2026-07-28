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
import vc.tbc.bq.jdbc.metadata.BQDatabaseMetaData;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real BigQuery integration tests for the key-metadata methods added in #84:
 * {@code getPrimaryKeys}, {@code getImportedKeys}, {@code getExportedKeys} and
 * {@code getCrossReference}.
 *
 * <p>
 * These have to run against real BigQuery to be worth anything. The whole
 * feature rests on the exact shape of three {@code INFORMATION_SCHEMA} views
 * and on {@code position_in_unique_constraint} meaning what the implementation
 * assumes it means; a mock would only assert that the driver agrees with
 * itself.
 *
 * <p>
 * The fixture is a small star: a {@code parent} with a two-column primary key,
 * a {@code child} declaring both a single-column and a composite foreign key
 * into it, and a {@code child_rev} whose foreign key names the parent's key
 * columns in the opposite order — the case that distinguishes reading
 * {@code position_in_unique_constraint} from trusting row order.
 *
 * <p>
 * Everything lives in one dataset. Foreign keys that cross datasets resolve
 * through a separate code path, exercised by the unit tests; covering it here
 * would mean creating and dropping a second dataset per run, and a cancelled
 * run would strand it.
 *
 * @since 2.2.0
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RealKeyConstraintMetadataTest extends AbstractRealBigQueryIntegrationTest {

	private static final Logger logger = LoggerFactory.getLogger(RealKeyConstraintMetadataTest.class);

	private static final String PARENT = tableName("keys_parent");
	private static final String OTHER_PARENT = tableName("keys_other_parent");
	private static final String CHILD = tableName("keys_child");
	private static final String CHILD_REV = tableName("keys_child_rev");

	private static String qualified(String table) {
		return "`" + TEST_PROJECT_ID + "`.`" + TEST_DATASET + "`.`" + table + "`";
	}

	@BeforeAll
	void createConstraintFixture() throws SQLException {
		// Once per class: no test here mutates the schema, and each CREATE is its own
		// BigQuery job.
		try (Connection setup = createTestConnection(); Statement stmt = setup.createStatement()) {
			stmt.execute("CREATE OR REPLACE TABLE " + qualified(PARENT) + " (p1 INT64 NOT NULL, p2 STRING NOT NULL, "
					+ "label STRING, PRIMARY KEY (p1, p2) NOT ENFORCED) " + expiresSoon());
			stmt.execute("CREATE OR REPLACE TABLE " + qualified(OTHER_PARENT)
					+ " (o1 INT64 NOT NULL, name STRING, PRIMARY KEY (o1) NOT ENFORCED) " + expiresSoon());
			stmt.execute("CREATE OR REPLACE TABLE " + qualified(CHILD)
					+ " (c_id INT64 NOT NULL, f1 INT64, f2 STRING, o_ref INT64, PRIMARY KEY (c_id) NOT ENFORCED, "
					+ "CONSTRAINT fk_child_parent FOREIGN KEY (f1, f2) REFERENCES " + qualified(PARENT)
					+ "(p1, p2) NOT ENFORCED, " + "CONSTRAINT fk_child_other FOREIGN KEY (o_ref) REFERENCES "
					+ qualified(OTHER_PARENT) + "(o1) NOT ENFORCED) " + expiresSoon());
			stmt.execute(
					"CREATE OR REPLACE TABLE " + qualified(CHILD_REV) + " (r_id INT64 NOT NULL, g1 INT64, g2 STRING, "
							+ "CONSTRAINT fk_rev FOREIGN KEY (g2, g1) REFERENCES " + qualified(PARENT)
							+ "(p2, p1) NOT ENFORCED) " + expiresSoon());
		}

		// The constraint snapshot is cached per dataset in a static, process-wide
		// cache. Without this, a snapshot read before these tables existed would
		// still be live and every assertion below would see a dataset with no keys.
		BQDatabaseMetaData.clearAllSharedCaches();
	}

	@AfterAll
	void dropConstraintFixture() {
		// Children first: BigQuery does not enforce these constraints, so the order is
		// not required, but it keeps the intent of the schema readable.
		for (String table : List.of(CHILD_REV, CHILD, PARENT, OTHER_PARENT)) {
			try (Connection cleanup = createTestConnection(); Statement stmt = cleanup.createStatement()) {
				stmt.execute("DROP TABLE IF EXISTS " + qualified(table));
			} catch (SQLException e) {
				logger.debug("Ignoring error dropping {}: {}", table, e.getMessage());
			}
		}
		BQDatabaseMetaData.clearAllSharedCaches();
	}

	/**
	 * Fixture tables delete themselves, so a cancelled run leaves nothing behind in
	 * the shared dataset for {@link #dropConstraintFixture()} to have missed.
	 */
	private static String expiresSoon() {
		return "OPTIONS(expiration_timestamp = TIMESTAMP_ADD(CURRENT_TIMESTAMP(), INTERVAL 2 HOUR))";
	}

	// getPrimaryKeys

	@Test
	void testGetPrimaryKeysReturnsCompositeKeyInOrder() throws SQLException {
		DatabaseMetaData metaData = connection.getMetaData();

		List<String[]> keys = new ArrayList<>();
		try (ResultSet rs = metaData.getPrimaryKeys(null, TEST_DATASET, PARENT)) {
			assertEquals(6, rs.getMetaData().getColumnCount());
			while (rs.next()) {
				keys.add(new String[]{rs.getString("COLUMN_NAME"), String.valueOf(rs.getShort("KEY_SEQ")),
						rs.getString("PK_NAME"), rs.getString("TABLE_SCHEM"), rs.getString("TABLE_NAME"),
						rs.getString("TABLE_CAT")});
			}
		}

		assertEquals(2, keys.size(), "parent declares PRIMARY KEY (p1, p2)");
		assertEquals("p1", keys.get(0)[0]);
		assertEquals("1", keys.get(0)[1]);
		assertEquals("p2", keys.get(1)[0]);
		assertEquals("2", keys.get(1)[1]);
		assertEquals(PARENT + ".pk$", keys.get(0)[2], "BigQuery qualifies the constraint name with its table");
		assertEquals(TEST_DATASET, keys.get(0)[3]);
		assertEquals(PARENT, keys.get(0)[4]);
		assertEquals(TEST_PROJECT_ID, keys.get(0)[5]);
	}

	@Test
	void testGetPrimaryKeysReturnsNothingForATableWithoutOne() throws SQLException {
		DatabaseMetaData metaData = connection.getMetaData();
		String plainTable = tableName("keys_no_pk");
		createSeededTable(TEST_DATASET + "." + plainTable);

		try (ResultSet rs = metaData.getPrimaryKeys(null, TEST_DATASET, plainTable)) {
			assertFalse(rs.next(), "the seeded fixture declares no primary key");
		} finally {
			executeIgnoreErrors("DROP TABLE IF EXISTS " + qualified(plainTable));
		}
	}

	@Test
	void testGetPrimaryKeysMatchesTableNamesExactly() throws SQLException {
		DatabaseMetaData metaData = connection.getMetaData();

		// The fixture names contain underscores. Were the table argument treated as a
		// LIKE pattern, "_" would be a wildcard and this would match the real table.
		String nearMiss = PARENT.replace('_', 'x');
		try (ResultSet rs = metaData.getPrimaryKeys(null, TEST_DATASET, nearMiss)) {
			assertFalse(rs.next(), "table is a name, not a pattern: " + nearMiss + " must not match " + PARENT);
		}
	}

	// getImportedKeys

	@Test
	void testGetImportedKeysResolvesCompositeForeignKey() throws SQLException {
		DatabaseMetaData metaData = connection.getMetaData();

		List<String> pairs = new ArrayList<>();
		String fkName = null;
		try (ResultSet rs = metaData.getImportedKeys(null, TEST_DATASET, CHILD)) {
			assertEquals(14, rs.getMetaData().getColumnCount());
			while (rs.next()) {
				if (PARENT.equals(rs.getString("PKTABLE_NAME"))) {
					pairs.add(rs.getString("FKCOLUMN_NAME") + "->" + rs.getString("PKCOLUMN_NAME") + "@"
							+ rs.getShort("KEY_SEQ"));
					fkName = rs.getString("FK_NAME");
				}
			}
		}

		assertEquals(List.of("f1->p1@1", "f2->p2@2"), pairs);
		assertEquals(CHILD + ".fk_child_parent", fkName);
	}

	@Test
	void testGetImportedKeysReturnsEveryForeignKeyOnTheTable() throws SQLException {
		DatabaseMetaData metaData = connection.getMetaData();

		List<String> parents = new ArrayList<>();
		try (ResultSet rs = metaData.getImportedKeys(null, TEST_DATASET, CHILD)) {
			while (rs.next()) {
				parents.add(rs.getString("PKTABLE_NAME"));
			}
		}

		// Ordered by PKTABLE_CAT, PKTABLE_SCHEM, PKTABLE_NAME, KEY_SEQ, and
		// "keys_other_parent" sorts before "keys_parent".
		assertEquals(List.of(OTHER_PARENT, PARENT, PARENT), parents);
	}

	/**
	 * The case the implementation exists for. {@code fk_rev} is declared
	 * {@code FOREIGN KEY (g2, g1) REFERENCES parent(p2, p1)}, so pairing by
	 * anything other than {@code position_in_unique_constraint} yields g2-&gt;p1
	 * and g1-&gt;p2 — a join that is wrong rather than missing.
	 */
	@Test
	void testGetImportedKeysRespectsReversedReferenceOrder() throws SQLException {
		DatabaseMetaData metaData = connection.getMetaData();

		List<String> pairs = new ArrayList<>();
		try (ResultSet rs = metaData.getImportedKeys(null, TEST_DATASET, CHILD_REV)) {
			while (rs.next()) {
				pairs.add(rs.getString("FKCOLUMN_NAME") + "->" + rs.getString("PKCOLUMN_NAME"));
			}
		}

		assertEquals(List.of("g2->p2", "g1->p1"), pairs);
	}

	@Test
	void testGetImportedKeysReportsNoActionAndNotDeferrable() throws SQLException {
		DatabaseMetaData metaData = connection.getMetaData();

		try (ResultSet rs = metaData.getImportedKeys(null, TEST_DATASET, CHILD)) {
			assertTrue(rs.next());
			// BigQuery never enforces these constraints, so there is no referential
			// action to take and nothing to defer.
			assertEquals(DatabaseMetaData.importedKeyNoAction, rs.getShort("UPDATE_RULE"));
			assertEquals(DatabaseMetaData.importedKeyNoAction, rs.getShort("DELETE_RULE"));
			assertEquals(DatabaseMetaData.importedKeyNotDeferrable, rs.getShort("DEFERRABILITY"));
			assertEquals(rs.getString("PKTABLE_NAME") + ".pk$", rs.getString("PK_NAME"),
					"PK_NAME names the referenced table's primary key");
		}
	}

	@Test
	void testGetImportedKeysReturnsNothingForATableWithoutForeignKeys() throws SQLException {
		DatabaseMetaData metaData = connection.getMetaData();

		try (ResultSet rs = metaData.getImportedKeys(null, TEST_DATASET, PARENT)) {
			assertFalse(rs.next(), "parent references nothing");
		}
	}

	// getExportedKeys

	@Test
	void testGetExportedKeysFindsBothReferencingTables() throws SQLException {
		DatabaseMetaData metaData = connection.getMetaData();

		List<String> referencing = new ArrayList<>();
		try (ResultSet rs = metaData.getExportedKeys(null, TEST_DATASET, PARENT)) {
			assertEquals(14, rs.getMetaData().getColumnCount());
			while (rs.next()) {
				referencing.add(rs.getString("FKTABLE_NAME") + "." + rs.getString("FKCOLUMN_NAME"));
				assertEquals(PARENT, rs.getString("PKTABLE_NAME"));
			}
		}

		// Ordered by FKTABLE_CAT, FKTABLE_SCHEM, FKTABLE_NAME, KEY_SEQ; "keys_child"
		// sorts before "keys_child_rev".
		assertEquals(List.of(CHILD + ".f1", CHILD + ".f2", CHILD_REV + ".g2", CHILD_REV + ".g1"), referencing);
	}

	@Test
	void testGetExportedKeysReturnsNothingForAnUnreferencedTable() throws SQLException {
		DatabaseMetaData metaData = connection.getMetaData();

		try (ResultSet rs = metaData.getExportedKeys(null, TEST_DATASET, CHILD_REV)) {
			assertFalse(rs.next(), "nothing references child_rev");
		}
	}

	// getCrossReference

	@Test
	void testGetCrossReferenceNarrowsToOnePairOfTables() throws SQLException {
		DatabaseMetaData metaData = connection.getMetaData();

		List<String> pairs = new ArrayList<>();
		try (ResultSet rs = metaData.getCrossReference(null, TEST_DATASET, PARENT, null, TEST_DATASET, CHILD)) {
			while (rs.next()) {
				pairs.add(rs.getString("FKCOLUMN_NAME") + "->" + rs.getString("PKCOLUMN_NAME"));
			}
		}

		assertEquals(List.of("f1->p1", "f2->p2"), pairs,
				"the child's other foreign key points at other_parent and must be excluded");
	}

	@Test
	void testGetCrossReferenceReturnsNothingForUnrelatedTables() throws SQLException {
		DatabaseMetaData metaData = connection.getMetaData();

		try (ResultSet rs = metaData.getCrossReference(null, TEST_DATASET, OTHER_PARENT, null, TEST_DATASET,
				CHILD_REV)) {
			assertFalse(rs.next(), "child_rev does not reference other_parent");
		}
	}

}

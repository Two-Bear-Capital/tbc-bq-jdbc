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
import vc.tbc.bq.jdbc.BQConnection;
import vc.tbc.bq.jdbc.auth.EmulatorAuth;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the driver's emulator support works.
 *
 * <p>
 * This is deliberately not a test of BigQuery. The driver ships emulator
 * support as a feature — {@link EmulatorAuth}, and a Simba-format URL with a
 * host and port that selects it — so that feature needs a test, and this is it.
 *
 * <p>
 * What it must never do is assert BigQuery <em>semantics</em>. Issue #118
 * catalogued what happens when it does: the emulator diverges from the service,
 * tests get weakened until they pass, and the weakened tests then hide real
 * defects (#93, #98, #121, #123, #129). Everything that asserts how BigQuery
 * behaves now lives in {@code integration/real}. This class was
 * {@code BasicConnectionTest}, 19 tests; the 17 that made claims about BigQuery
 * moved there, and what remains is connection plumbing that needs an endpoint
 * but not fidelity.
 *
 * <p>
 * The other two classes in this tier follow the same rule:
 * {@code ConcurrentQueryTest} measures query overlap, and
 * {@code SimbaUrlConnectionTest} checks that URL properties take effect.
 *
 * @since 1.0.0
 */
class EmulatorSupportTest extends AbstractBigQueryIntegrationTest {

	@Test
	void testEmulatorUrlSelectsEmulatorAuth() throws SQLException {
		// A Simba URL carrying a host defaults authType to EMULATOR
		// (ConnectionUrlParser), which is the mechanism this whole tier rests on.
		BQConnection bq = connection.unwrap(BQConnection.class);
		assertInstanceOf(EmulatorAuth.class, bq.getProperties().authType(),
				"A host in the URL should select emulator authentication");
	}

	@Test
	void testConnectionIsUsable() throws SQLException {
		assertTrue(connection.isValid(5), "Connection should be valid");
		assertFalse(connection.isClosed());
	}

	@Test
	void testCatalogAndSchemaComeFromTheUrl() throws SQLException {
		assertEquals(TEST_PROJECT_ID, connection.getCatalog(), "Catalog is the project from the URL");
		assertEquals(TEST_DATASET, connection.getSchema(), "Schema is the default dataset from the URL");
	}

	@Test
	void testStatementExecutesAgainstTheEmulator() throws SQLException {
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery("SELECT 1 AS value")) {
			assertTrue(rs.next(), "Should have a result");
			assertEquals(1, rs.getInt("value"));
		}
	}

	@Test
	void testPreparedStatementExecutesAgainstTheEmulator() throws SQLException {
		try (PreparedStatement pstmt = connection.prepareStatement("SELECT ? AS value")) {
			pstmt.setInt(1, 42);
			try (ResultSet rs = pstmt.executeQuery()) {
				assertTrue(rs.next(), "Should have a result");
				assertEquals(42, rs.getInt("value"));
			}
		}
	}

	@Test
	void testMetaDataIsAvailable() throws SQLException {
		assertNotNull(connection.getMetaData(), "DatabaseMetaData should be available");
		assertNotNull(connection.getMetaData().getURL(), "and should report the URL it connected with");
	}

	@Test
	void testCloseMakesTheConnectionUnusable() throws SQLException {
		java.sql.Connection conn = createTestConnection();
		assertFalse(conn.isClosed());

		conn.close();

		assertTrue(conn.isClosed());
		assertThrows(SQLException.class, conn::createStatement, "A closed connection should reject new statements");
	}
}

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
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Service account impersonation against real BigQuery (#197).
 *
 * <p>
 * The only tier that can prove impersonation works. Nothing below this one
 * reaches the IAM {@code generateAccessToken} call, so a unit test can show the
 * driver builds an {@code ImpersonatedCredentials} and still tell you nothing
 * about whether the resulting token is accepted — which is the entire feature.
 *
 * <p>
 * <b>Fixture:</b> two service accounts created by {@code terraform/main.tf},
 * where only the target holds BigQuery roles. That asymmetry is deliberate: the
 * delegated test would pass just as well against a chain that quietly resolved
 * to the delegate if the delegate could also run queries.
 *
 * <p>
 * <b>Enabling:</b> set {@code BQ_TEST_IMPERSONATE_SA} to the target email
 * (Terraform's {@code impersonation_target_service_account} output), and
 * {@code BQ_TEST_IMPERSONATE_DELEGATE} to the delegate's. The caller — your ADC
 * identity locally, the CI service account in Actions — needs
 * {@code roles/iam.serviceAccountTokenCreator} on them; locally that means
 * naming yourself in the {@code impersonation_source_principals} Terraform
 * variable. Both variables gate independently, so the direct case can run
 * before the chain is provisioned.
 *
 * @since 4.0.0
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "BQ_TEST_IMPERSONATE_SA", matches = ".+", disabledReason = "BQ_TEST_IMPERSONATE_SA is not set — the impersonation fixture has not been provisioned")
class RealImpersonationTest extends AbstractRealBigQueryIntegrationTest {

	private static final String TARGET_SA = System.getenv().getOrDefault("BQ_TEST_IMPERSONATE_SA", "");
	private static final String DELEGATE_SA = System.getenv().getOrDefault("BQ_TEST_IMPERSONATE_DELEGATE", "");

	private static final String TEST_TABLE = tableName("impersonation");

	@BeforeAll
	void createFixture() throws SQLException {
		createSharedTestTable(TEST_TABLE);
	}

	@AfterAll
	void dropFixture() {
		dropSharedTestTable(TEST_TABLE);
	}

	/**
	 * Builds a connection URL impersonating {@link #TARGET_SA} over the default ADC
	 * source, with an optional delegation chain.
	 */
	private String impersonatingUrl(String delegates) {
		String url = String.format("jdbc:bigquery:%s/%s?impersonateServiceAccount=%s&maxBillingBytes=1073741824",
				TEST_PROJECT_ID, TEST_DATASET, TARGET_SA);
		return delegates == null ? url : url + "&impersonateDelegates=" + delegates;
	}

	@Test
	void testImpersonatedConnectionRunsQueryAsTargetServiceAccount() throws SQLException {
		// When: Connecting with impersonation and asking BigQuery who is calling
		try (Connection conn = DriverManager.getConnection(impersonatingUrl(null));
				Statement stmt = conn.createStatement();
				ResultSet rs = stmt.executeQuery("SELECT SESSION_USER() AS caller")) {

			// Then: The job should run as the target, not as the source identity
			assertTrue(rs.next(), "SESSION_USER() should return a row");
			assertEquals(TARGET_SA, rs.getString("caller"),
					"The query should be billed and authorized as the impersonated service account");
		}
	}

	@Test
	void testImpersonatedConnectionReadsFixtureTable() throws SQLException {
		// When: Reading a real table through the impersonated credential
		try (Connection conn = DriverManager.getConnection(impersonatingUrl(null));
				Statement stmt = conn.createStatement();
				ResultSet rs = stmt.executeQuery("SELECT COUNT(*) AS row_count FROM " + TEST_TABLE)) {

			// Then: The target's dataViewer grant should carry the read
			assertTrue(rs.next(), "COUNT(*) should return a row");
			assertEquals(3, rs.getInt("row_count"));
		}
	}

	@Test
	void testImpersonationChangesTheEffectiveIdentity() throws SQLException {
		// Given: The identity the suite normally runs as
		String direct;
		try (Connection conn = createTestConnection();
				Statement stmt = conn.createStatement();
				ResultSet rs = stmt.executeQuery("SELECT SESSION_USER() AS caller")) {
			assertTrue(rs.next());
			direct = rs.getString("caller");
		}

		// When: The same query runs through impersonation
		String impersonated;
		try (Connection conn = DriverManager.getConnection(impersonatingUrl(null));
				Statement stmt = conn.createStatement();
				ResultSet rs = stmt.executeQuery("SELECT SESSION_USER() AS caller")) {
			assertTrue(rs.next());
			impersonated = rs.getString("caller");
		}

		// Then: They must differ, or the test above proves nothing — it would pass
		// unchanged if the driver ignored the property and used the source identity
		assertNotEquals(direct, impersonated, "Impersonation should change who BigQuery sees");
		assertEquals(TARGET_SA, impersonated);
	}

	@Test
	@EnabledIfEnvironmentVariable(named = "BQ_TEST_IMPERSONATE_DELEGATE", matches = ".+", disabledReason = "BQ_TEST_IMPERSONATE_DELEGATE is not set")
	void testDelegatedImpersonationReachesTargetThroughDelegate() throws SQLException {
		// When: Reaching the target through an intermediate that cannot query itself
		try (Connection conn = DriverManager.getConnection(impersonatingUrl(DELEGATE_SA));
				Statement stmt = conn.createStatement();
				ResultSet rs = stmt.executeQuery("SELECT SESSION_USER() AS caller")) {

			// Then: The token should still be the target's — the delegate only signs
			// for it, and holds no BigQuery role of its own
			assertTrue(rs.next(), "SESSION_USER() should return a row");
			assertEquals(TARGET_SA, rs.getString("caller"));
		}
	}

	@Test
	void testImpersonatingAnUngrantedServiceAccountFails() {
		// Given: A target the caller holds no serviceAccountTokenCreator role on
		String url = String.format(
				"jdbc:bigquery:%s/%s?impersonateServiceAccount=no-such-sa@%s.iam.gserviceaccount.com", TEST_PROJECT_ID,
				TEST_DATASET, TEST_PROJECT_ID);

		// Then: The failure should surface rather than silently falling back to the
		// source identity, which would run the query with more access than asked for
		SQLException e = assertThrows(SQLException.class, () -> {
			try (Connection conn = DriverManager.getConnection(url);
					Statement stmt = conn.createStatement();
					ResultSet rs = stmt.executeQuery("SELECT 1")) {
				rs.next();
			}
		});
		assertTrue(e.getMessage() != null && !e.getMessage().isBlank(), "The failure should carry a message");
	}
}

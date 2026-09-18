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
package vc.tbc.bq.jdbc;

import org.junit.jupiter.api.Test;
import vc.tbc.bq.jdbc.auth.*;
import vc.tbc.bq.jdbc.config.ConnectionProperties;

import static vc.tbc.bq.jdbc.testsupport.TestConnectionProperties.props;

import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for authentication types.
 *
 * @since 1.0.0
 */
class AuthenticationTest {

	private static final String TARGET = "etl@my-project.iam.gserviceaccount.com";

	@Test
	void testServiceAccountAuthConstruction() {
		// Given: A service account key path
		String keyPath = "/path/to/key.json";

		// When: Creating ServiceAccountAuth
		ServiceAccountAuth auth = new ServiceAccountAuth(keyPath);

		// Then: Should store the key path
		assertEquals(keyPath, auth.jsonKeyPath());
	}

	@Test
	void testServiceAccountAuthNullPathThrowsException() {
		// Then: Null key path should throw NPE
		assertThrows(NullPointerException.class, () -> new ServiceAccountAuth(null));
	}

	@Test
	void testServiceAccountAuthBlankPathThrowsException() {
		// Then: Blank key path should throw IllegalArgumentException
		assertThrows(IllegalArgumentException.class, () -> new ServiceAccountAuth(""));
		assertThrows(IllegalArgumentException.class, () -> new ServiceAccountAuth("   "));
	}

	@Test
	void testApplicationDefaultAuthConstruction() {
		// When: Creating ApplicationDefaultAuth
		ApplicationDefaultAuth auth = new ApplicationDefaultAuth();

		// Then: Should be created successfully
		assertNotNull(auth);
	}

	@Test
	void testUserOAuthAuthConstruction() {
		// Given: OAuth parameters
		String clientId = "client-id";
		String clientSecret = "client-secret";
		String refreshToken = "refresh-token";

		// When: Creating UserOAuthAuth
		UserOAuthAuth auth = new UserOAuthAuth(clientId, clientSecret, refreshToken);

		// Then: Should store all parameters
		assertEquals(clientId, auth.clientId());
		assertEquals(clientSecret, auth.clientSecret());
		assertEquals(refreshToken, auth.refreshToken());
	}

	@Test
	void testUserOAuthAuthNullClientIdThrowsException() {
		// Then: Null clientId should throw NPE
		assertThrows(NullPointerException.class, () -> new UserOAuthAuth(null, "secret", "refresh"));
	}

	@Test
	void testUserOAuthAuthNullClientSecretThrowsException() {
		// Then: Null clientSecret should throw NPE
		assertThrows(NullPointerException.class, () -> new UserOAuthAuth("client", null, "refresh"));
	}

	@Test
	void testUserOAuthAuthNullRefreshTokenThrowsException() {
		// Then: Null refreshToken should throw NPE
		assertThrows(NullPointerException.class, () -> new UserOAuthAuth("client", "secret", null));
	}

	@Test
	void testWorkforceIdentityAuthConstruction() {
		// Given: A credential config file path
		String configPath = "/path/to/config.json";

		// When: Creating WorkforceIdentityAuth
		WorkforceIdentityAuth auth = new WorkforceIdentityAuth(configPath);

		// Then: Should store the config path
		assertEquals(configPath, auth.credentialConfigFile());
	}

	@Test
	void testWorkforceIdentityAuthNullPathThrowsException() {
		// Then: Null config path should throw NPE
		assertThrows(NullPointerException.class, () -> new WorkforceIdentityAuth(null));
	}

	@Test
	void testWorkforceIdentityAuthBlankPathThrowsException() {
		// Then: Blank config path should throw IllegalArgumentException
		assertThrows(IllegalArgumentException.class, () -> new WorkforceIdentityAuth(""));
		assertThrows(IllegalArgumentException.class, () -> new WorkforceIdentityAuth("   "));
	}

	@Test
	void testWorkloadIdentityAuthConstruction() {
		// Given: A credential config file path
		String configPath = "/path/to/config.json";

		// When: Creating WorkloadIdentityAuth
		WorkloadIdentityAuth auth = new WorkloadIdentityAuth(configPath);

		// Then: Should store the config path
		assertEquals(configPath, auth.credentialConfigFile());
	}

	@Test
	void testWorkloadIdentityAuthNullPathThrowsException() {
		// Then: Null config path should throw NPE
		assertThrows(NullPointerException.class, () -> new WorkloadIdentityAuth(null));
	}

	@Test
	void testWorkloadIdentityAuthBlankPathThrowsException() {
		// Then: Blank config path should throw IllegalArgumentException
		assertThrows(IllegalArgumentException.class, () -> new WorkloadIdentityAuth(""));
		assertThrows(IllegalArgumentException.class, () -> new WorkloadIdentityAuth("   "));
	}

	@Test
	void testAuthTypeRecordEquality() {
		// Given: Two identical ServiceAccountAuth instances
		ServiceAccountAuth auth1 = new ServiceAccountAuth("/path/to/key.json");
		ServiceAccountAuth auth2 = new ServiceAccountAuth("/path/to/key.json");

		// Then: They should be equal
		assertEquals(auth1, auth2);
		assertEquals(auth1.hashCode(), auth2.hashCode());
	}

	@Test
	void testAuthTypeRecordInequality() {
		// Given: Two different ServiceAccountAuth instances
		ServiceAccountAuth auth1 = new ServiceAccountAuth("/path/to/key1.json");
		ServiceAccountAuth auth2 = new ServiceAccountAuth("/path/to/key2.json");

		// Then: They should not be equal
		assertNotEquals(auth1, auth2);
	}

	@Test
	void testDifferentAuthTypesNotEqual() {
		// Given: Different auth type instances
		ServiceAccountAuth serviceAccount = new ServiceAccountAuth("/path/to/key.json");
		ApplicationDefaultAuth adc = new ApplicationDefaultAuth();

		// Then: They should not be equal
		assertNotEquals(serviceAccount, adc);
	}

	@Test
	void testAuthTypeSealedInterfaceImplementations() {
		// Given: All auth type implementations
		AuthType serviceAccount = new ServiceAccountAuth("/path/to/key.json");
		AuthType adc = new ApplicationDefaultAuth();
		AuthType userOAuth = new UserOAuthAuth("client", "secret", "refresh");
		AuthType workforce = new WorkforceIdentityAuth("/path/to/config.json");
		AuthType workload = new WorkloadIdentityAuth("/path/to/config.json");
		AuthType impersonated = new ImpersonatedAuth(adc, TARGET);

		// Then: All should be instances of AuthType
		assertInstanceOf(AuthType.class, serviceAccount);
		assertInstanceOf(AuthType.class, adc);
		assertInstanceOf(AuthType.class, userOAuth);
		assertInstanceOf(AuthType.class, workforce);
		assertInstanceOf(AuthType.class, workload);
		assertInstanceOf(AuthType.class, impersonated);
	}

	@Test
	void testApplicationDefaultAuthEquality() {
		// Given: Two ApplicationDefaultAuth instances
		ApplicationDefaultAuth auth1 = new ApplicationDefaultAuth();
		ApplicationDefaultAuth auth2 = new ApplicationDefaultAuth();

		// Then: They should be equal (no state)
		assertEquals(auth1, auth2);
		assertEquals(auth1.hashCode(), auth2.hashCode());
	}

	@Test
	void testUserOAuthAuthToString() {
		// Given: UserOAuthAuth
		UserOAuthAuth auth = new UserOAuthAuth("client-id", "client-secret", "refresh-token");

		// When: Converting to string
		String str = auth.toString();

		// Then: Should contain field names (but not necessarily values for security)
		assertTrue(str.contains("UserOAuthAuth"));
	}

	@Test
	void testImpersonatedAuthConstruction() {
		// Given: A source auth type and a delegation chain
		AuthType source = new ApplicationDefaultAuth();
		List<String> delegates = List.of("mid1@my-project.iam.gserviceaccount.com");

		// When: Creating ImpersonatedAuth
		ImpersonatedAuth auth = new ImpersonatedAuth(source, TARGET, delegates);

		// Then: Should store all three components
		assertEquals(source, auth.source());
		assertEquals(TARGET, auth.targetPrincipal());
		assertEquals(delegates, auth.delegates());
	}

	@Test
	void testImpersonatedAuthDefaultsToNoDelegates() {
		// When: Creating ImpersonatedAuth without a chain, either way
		ImpersonatedAuth convenience = new ImpersonatedAuth(new ApplicationDefaultAuth(), TARGET);
		ImpersonatedAuth explicitNull = new ImpersonatedAuth(new ApplicationDefaultAuth(), TARGET, null);

		// Then: Both should hold an empty chain, and be equal
		assertEquals(List.of(), convenience.delegates());
		assertEquals(List.of(), explicitNull.delegates());
		assertEquals(convenience, explicitNull);
	}

	@Test
	void testImpersonatedAuthDelegatesAreImmutable() {
		// Given: A mutable list handed to the constructor
		List<String> delegates = new ArrayList<>(List.of("mid1@my-project.iam.gserviceaccount.com"));
		ImpersonatedAuth auth = new ImpersonatedAuth(new ApplicationDefaultAuth(), TARGET, delegates);

		// When: The caller's list is mutated afterwards
		delegates.add("mid2@my-project.iam.gserviceaccount.com");

		// Then: The record should be unaffected, and reject mutation of its own copy
		assertEquals(1, auth.delegates().size());
		assertThrows(UnsupportedOperationException.class, () -> auth.delegates().add("late@example.com"));
	}

	@Test
	void testImpersonatedAuthNullComponentsThrowException() {
		// Then: Neither the source nor the target may be null
		assertThrows(NullPointerException.class, () -> new ImpersonatedAuth(null, TARGET));
		assertThrows(NullPointerException.class, () -> new ImpersonatedAuth(new ApplicationDefaultAuth(), null));
	}

	@Test
	void testImpersonatedAuthBlankTargetThrowsException() {
		// Then: A blank target is a missing target
		assertThrows(IllegalArgumentException.class, () -> new ImpersonatedAuth(new ApplicationDefaultAuth(), ""));
		assertThrows(IllegalArgumentException.class, () -> new ImpersonatedAuth(new ApplicationDefaultAuth(), "   "));
	}

	@Test
	void testImpersonatedAuthBlankDelegateThrowsException() {
		// Then: A blank link in the chain is a typo, not an empty chain
		List<String> delegates = Arrays.asList("mid1@my-project.iam.gserviceaccount.com", "  ");
		assertThrows(IllegalArgumentException.class,
				() -> new ImpersonatedAuth(new ApplicationDefaultAuth(), TARGET, delegates));
	}

	@Test
	void testImpersonatedAuthRejectsNestedImpersonation() {
		// Given: An already-impersonated source
		ImpersonatedAuth inner = new ImpersonatedAuth(new ApplicationDefaultAuth(),
				"mid@my-project.iam.gserviceaccount.com");

		// Then: Wrapping it again should point the caller at delegates instead
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
				() -> new ImpersonatedAuth(inner, TARGET));
		assertTrue(e.getMessage().contains("delegates"), "Message should name the alternative: " + e.getMessage());
	}

	@Test
	void testImpersonatedAuthEqualityIncludesSource() {
		// Given: The same target reached from two different source identities
		ImpersonatedAuth fromAdc = new ImpersonatedAuth(new ApplicationDefaultAuth(), TARGET);
		ImpersonatedAuth fromKey = new ImpersonatedAuth(new ServiceAccountAuth("/path/to/key.json"), TARGET);

		// Then: They must not be equal — CredentialsCache keys on AuthType, so
		// treating these as one entry would hand a caller the wrong credentials
		assertNotEquals(fromAdc, fromKey);
		assertEquals(fromAdc, new ImpersonatedAuth(new ApplicationDefaultAuth(), TARGET));
		assertEquals(fromAdc.hashCode(), new ImpersonatedAuth(new ApplicationDefaultAuth(), TARGET).hashCode());
	}

	/**
	 * An expired token is refused as the connection opens, not on the first
	 * statement. Reaches no network: the check runs before any credential is built.
	 */
	@Test
	void testAnExpiredAccessTokenIsRefusedWhenTheConnectionOpens() {
		ConnectionProperties properties = props()
				.authType(new AccessTokenAuth("ya29.stale", Instant.now().minusSeconds(60))).build();

		SQLException e = assertThrows(SQLException.class, () -> new BQConnection(properties));

		assertEquals("28000", e.getSQLState(), "an expired credential is 're-authenticate', not a connection error");
		assertTrue(e.getMessage().contains("expired"), e.getMessage());
		assertTrue(e.getMessage().contains("cannot be refreshed"), e.getMessage());
	}

	@Test
	void testATokenWithNoExpiryIsNotRefusedUpFront() {
		// Unknown is not the same as expired; BigQuery remains the judge. This gets
		// past the expiry gate and fails later for want of a reachable service,
		// which is exactly the point being asserted.
		ConnectionProperties properties = props().authType(new AccessTokenAuth("ya29.unknown-expiry")).build();

		try {
			new BQConnection(properties).close();
		} catch (SQLException e) {
			assertNotEquals("28000", e.getSQLState(), "must not be refused as expired: " + e.getMessage());
		}
	}
}

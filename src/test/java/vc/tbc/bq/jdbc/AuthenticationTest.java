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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for authentication types.
 *
 * @since 1.0.0
 */
class AuthenticationTest {

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

		// Then: All should be instances of AuthType
		assertInstanceOf(AuthType.class, serviceAccount);
		assertInstanceOf(AuthType.class, adc);
		assertInstanceOf(AuthType.class, userOAuth);
		assertInstanceOf(AuthType.class, workforce);
		assertInstanceOf(AuthType.class, workload);
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
}

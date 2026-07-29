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
package vc.tbc.bq.jdbc.storage;

import com.google.auth.Credentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryOptions;
import org.junit.jupiter.api.Test;
import vc.tbc.bq.jdbc.BQConnection;

import java.security.KeyPairGenerator;
import java.security.PrivateKey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The Storage Read API path must authenticate with the same credential as the
 * REST path (#243).
 *
 * <p>
 * A key here is generated locally and never used to authenticate. Scoping is
 * entirely a client-side operation, so it can be asserted without credentials
 * and without a network call — which is what makes this a unit test rather than
 * something only the real tier could reach.
 *
 * @since 4.0.0
 */
class StorageCredentialScopingTest {

	/**
	 * A credential shaped like the one a service account key file produces:
	 * {@code ServiceAccountCredentials.fromStream()} carries no scopes, so it
	 * reports {@code createScopedRequired()}.
	 */
	private static ServiceAccountCredentials unscopedServiceAccount() throws Exception {
		KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(2048);
		PrivateKey key = generator.generateKeyPair().getPrivate();
		return ServiceAccountCredentials.newBuilder().setClientEmail("probe@example.iam.gserviceaccount.com")
				.setPrivateKey(key).setPrivateKeyId("kid").setProjectId("test-project").build();
	}

	@Test
	void testTheFixtureReallyIsUnscoped() throws Exception {
		// Then: If this ever stops being true the rest of the class proves nothing
		assertTrue(unscopedServiceAccount().createScopedRequired(),
				"A key-file service account credential should need scoping");
	}

	@Test
	void testStorageUsesTheScopedCredentialTheRestPathUses() throws Exception {
		// Given: A connection whose client was built with an unscoped credential,
		// exactly as BQConnection does
		ServiceAccountCredentials unscoped = unscopedServiceAccount();
		BigQuery bigquery = BigQueryOptions.newBuilder().setProjectId("test-project").setCredentials(unscoped).build()
				.getService();
		BQConnection connection = mock(BQConnection.class);
		when(connection.getBigQuery()).thenReturn(bigquery);

		// When: The Storage path asks for its credential
		Credentials storageCredentials = StorageReadResultSet.scopedCredentials(connection);

		// Then: It must be the scoped copy, not the original. Handing the unscoped
		// one to FixedCredentialsProvider — which scopes nothing — is what made
		// useStorageApi decide whether a connection authenticated correctly
		assertNotSame(unscoped, storageCredentials, "The Storage path was handed the unscoped original");
		GoogleCredentials scoped = assertInstanceOf(GoogleCredentials.class, storageCredentials);
		assertFalse(scoped.createScopedRequired(), "The credential handed to the Storage client still needs scoping");
	}

	@Test
	void testItIsTheSameCredentialTheRestClientHolds() throws Exception {
		// Given: One connection
		BigQueryOptions options = BigQueryOptions.newBuilder().setProjectId("test-project")
				.setCredentials(unscopedServiceAccount()).build();
		BQConnection connection = mock(BQConnection.class);
		when(connection.getBigQuery()).thenReturn(options.getService());

		// Then: Both paths resolve to the same credential. Asserting equality
		// rather than "is scoped" is the actual invariant — the two paths must not
		// be able to drift, whatever the client's default scopes become
		GoogleCredentials restCredentials = assertInstanceOf(GoogleCredentials.class, options.getScopedCredentials());
		assertFalse(restCredentials.createScopedRequired());
		assertEquals(restCredentials, StorageReadResultSet.scopedCredentials(connection),
				"The Storage and REST paths must authenticate identically");
	}
}

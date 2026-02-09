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
package com.tbc.bq.jdbc.auth;

import com.google.auth.Credentials;
import com.google.auth.oauth2.ExternalAccountCredentials;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Objects;

/**
 * Workload Identity Federation authentication for external identity providers.
 *
 * <p>
 * Workload Identity Federation allows applications running outside of Google
 * Cloud (e.g., on AWS, Azure, on-premises) to authenticate to Google Cloud
 * services without using service account keys. This is more secure as it
 * eliminates the need to store and manage long-lived credentials.
 *
 * <p>
 * <b>Supported Identity Providers:</b>
 * <ul>
 * <li>AWS (using IAM roles)
 * <li>Azure (using managed identities)
 * <li>On-premises Active Directory Federation Services (ADFS)
 * <li>Okta, Azure AD, and other OIDC providers
 * </ul>
 *
 * <p>
 * <b>Setting Up Workload Identity Federation:</b>
 * <ol>
 * <li>Create a workload identity pool in Google Cloud Console
 * <li>Add an identity provider (AWS, Azure, OIDC, etc.)
 * <li>Grant the workload identity pool access to BigQuery
 * <li>Generate a credential configuration file:
 * 
 * <pre>{@code
 * gcloud iam workload-identity-pools create-cred-config \
 *   projects/PROJECT_NUMBER/locations/global/workloadIdentityPools/POOL_ID/providers/PROVIDER_ID \
 *   --service-account=SERVICE_ACCOUNT_EMAIL \
 *   --credential-source-file=TOKEN_PATH \
 *   --output-file=credential-config.json
 *     }</pre>
 * </ol>
 *
 * <p>
 * <b>Security Benefits:</b>
 * <ul>
 * <li>No long-lived service account keys to manage
 * <li>Automatic credential rotation via external identity provider
 * <li>Centralized access control through IAM
 * <li>Audit trail of external identities accessing Google Cloud
 * </ul>
 *
 * <p>
 * <b>Usage Example:</b>
 * 
 * <pre>{@code
 * String url = "jdbc:bigquery:my-project/my_dataset?" + "authType=WORKLOAD_IDENTITY&"
 * 		+ "credentialConfigFile=/path/to/credential-config.json";
 * Connection conn = DriverManager.getConnection(url);
 * }</pre>
 *
 * @param credentialConfigFile
 *            path to the workload identity credential configuration file (must
 *            exist and be readable)
 * @since 1.0.0
 */
public record WorkloadIdentityAuth(String credentialConfigFile) implements AuthType {

	public WorkloadIdentityAuth {
		Objects.requireNonNull(credentialConfigFile, "credentialConfigFile cannot be null");
		if (credentialConfigFile.isBlank()) {
			throw new IllegalArgumentException("credentialConfigFile cannot be blank");
		}
	}

	@Override
	public Credentials toCredentials() throws IOException {
		return ExternalAccountCredentials.fromStream(new FileInputStream(credentialConfigFile));
	}
}

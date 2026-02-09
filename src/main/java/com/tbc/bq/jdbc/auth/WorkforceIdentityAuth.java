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
 * Workforce Identity Federation authentication for human users.
 *
 * <p>
 * Workforce Identity Federation allows employees to access Google Cloud
 * resources using their existing corporate identity (e.g., Azure AD, Okta)
 * without requiring separate Google accounts. This is ideal for enterprise
 * environments where users need direct access to BigQuery through JDBC clients
 * like IntelliJ IDEA, DBeaver, or custom applications.
 *
 * <p>
 * <b>Key Differences from Workload Identity:</b>
 * <ul>
 * <li><b>Workforce Identity:</b> For human users authenticating with corporate
 * identities
 * <li><b>Workload Identity:</b> For applications/services running outside
 * Google Cloud
 * </ul>
 *
 * <p>
 * <b>Supported Identity Providers:</b>
 * <ul>
 * <li>Azure Active Directory (Azure AD)
 * <li>Okta
 * <li>Active Directory Federation Services (ADFS)
 * <li>Any SAML 2.0 or OIDC-compliant provider
 * </ul>
 *
 * <p>
 * <b>Setting Up Workforce Identity Federation:</b>
 * <ol>
 * <li>Create a workforce identity pool in Google Cloud Console
 * <li>Configure an identity provider (Azure AD, Okta, etc.)
 * <li>Grant the workforce pool access to BigQuery resources
 * <li>Generate a credential configuration file:
 * 
 * <pre>{@code
 * gcloud iam workforce-pools create-cred-config \
 *   locations/global/workforcePools/POOL_ID/providers/PROVIDER_ID \
 *   --subject-token-type=urn:ietf:params:oauth:token-type:id_token \
 *   --credential-source-file=TOKEN_PATH \
 *   --workforce-pool-user-project=PROJECT_ID \
 *   --output-file=credential-config.json
 *     }</pre>
 * </ol>
 *
 * <p>
 * <b>Security Benefits:</b>
 * <ul>
 * <li>Users authenticate with existing corporate credentials
 * <li>No need to create and manage Google accounts for employees
 * <li>Centralized access control through corporate identity provider
 * <li>Automatic access revocation when users leave the organization
 * <li>Audit trail linking corporate identities to Google Cloud access
 * </ul>
 *
 * <p>
 * <b>Usage Example:</b>
 * 
 * <pre>{@code
 * String url = "jdbc:bigquery:my-project/my_dataset?" + "authType=WORKFORCE_IDENTITY&"
 * 		+ "credentialConfigFile=/path/to/workforce-credential-config.json";
 * Connection conn = DriverManager.getConnection(url);
 * }</pre>
 *
 * @param credentialConfigFile
 *            path to the workforce identity credential configuration file (must
 *            exist and be readable)
 * @since 1.0.0
 */
public record WorkforceIdentityAuth(String credentialConfigFile) implements AuthType {

	public WorkforceIdentityAuth {
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

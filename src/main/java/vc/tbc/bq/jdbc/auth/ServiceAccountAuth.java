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
package vc.tbc.bq.jdbc.auth;

import com.google.auth.Credentials;
import com.google.auth.oauth2.ServiceAccountCredentials;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Objects;

/**
 * Service account authentication using a JSON key file.
 *
 * <p>
 * This authentication method requires a service account JSON key file
 * downloaded from Google Cloud Console. The file must contain:
 * <ul>
 * <li>type: "service_account"
 * <li>project_id: Google Cloud project ID
 * <li>private_key_id: Key identifier
 * <li>private_key: RSA private key
 * <li>client_email: Service account email
 * </ul>
 *
 * <p>
 * <b>Obtaining Credentials:</b>
 * <ol>
 * <li>Navigate to Google Cloud Console &rarr; IAM &amp; Admin &rarr; Service
 * Accounts
 * <li>Create or select a service account
 * <li>Grant BigQuery permissions (e.g., "BigQuery Data Editor" role)
 * <li>Create a JSON key: Keys tab &rarr; Add Key &rarr; Create new key &rarr;
 * JSON
 * <li>Download and securely store the JSON file
 * </ol>
 *
 * <p>
 * <b>Security Best Practices:</b>
 * <ul>
 * <li>Protect key files with appropriate file permissions (e.g.,
 * {@code chmod 600})
 * <li>Never commit key files to version control (add to .gitignore)
 * <li>Use environment variables or secure vaults to store key paths
 * <li>Rotate keys regularly and revoke unused keys
 * </ul>
 *
 * <p>
 * <b>Usage Example:</b>
 *
 * <pre>{@code
 * String url = "jdbc:bigquery:my-project/my_dataset?" + "authType=SERVICE_ACCOUNT&"
 * 		+ "serviceAccountKeyFile=/path/to/service-account-key.json";
 * Connection conn = DriverManager.getConnection(url);
 * }</pre>
 *
 * @param jsonKeyPath
 *            path to the service account JSON key file (must exist and be
 *            readable)
 * @since 1.0.0
 */
public record ServiceAccountAuth(String jsonKeyPath) implements AuthType {

	public ServiceAccountAuth {
		Objects.requireNonNull(jsonKeyPath, "jsonKeyPath cannot be null");
		if (jsonKeyPath.isBlank()) {
			throw new IllegalArgumentException("jsonKeyPath cannot be blank");
		}
	}

	@Override
	public Credentials toCredentials() throws IOException {
		return ServiceAccountCredentials.fromStream(new FileInputStream(jsonKeyPath));
	}
}

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
import com.google.auth.oauth2.UserCredentials;

import java.io.IOException;
import java.util.Objects;

/**
 * User OAuth 2.0 authentication with refresh token.
 *
 * <p>
 * This authentication method uses OAuth 2.0 credentials obtained through the
 * OAuth consent flow. It requires three components:
 * <ul>
 * <li><b>Client ID:</b> OAuth 2.0 client identifier from Google Cloud Console
 * <li><b>Client Secret:</b> OAuth 2.0 client secret (confidential)
 * <li><b>Refresh Token:</b> Long-lived token for obtaining access tokens
 * </ul>
 *
 * <p>
 * <b>Obtaining Credentials:</b>
 * <ol>
 * <li>Navigate to Google Cloud Console &rarr; APIs &amp; Services &rarr;
 * Credentials
 * <li>Create OAuth 2.0 Client ID (Desktop app or Web application)
 * <li>Download client configuration JSON
 * <li>Run OAuth consent flow using gcloud or OAuth 2.0 Playground:
 * 
 * <pre>{@code
 * gcloud auth application-default login --client-id-file=client_secrets.json
 *     }</pre>
 * 
 * <li>Extract refresh_token from
 * {@code ~/.config/gcloud/application_default_credentials.json}
 * </ol>
 *
 * <p>
 * <b>Security Best Practices:</b>
 * <ul>
 * <li>Never hardcode client secrets or refresh tokens in source code
 * <li>Store credentials in environment variables or secure vaults
 * <li>Use OAuth scopes that grant only necessary permissions
 * <li>Regenerate refresh tokens if compromised
 * </ul>
 *
 * <p>
 * <b>Usage Example:</b>
 * 
 * <pre>{@code
 * String url = "jdbc:bigquery:my-project/my_dataset?" + "authType=USER_OAUTH&"
 * 		+ "oauthClientId=123456789.apps.googleusercontent.com&" + "oauthClientSecret=GOCSPX-abcdefghijk&"
 * 		+ "oauthRefreshToken=1//0abcdefghijk";
 * Connection conn = DriverManager.getConnection(url);
 * }</pre>
 *
 * @param clientId
 *            OAuth 2.0 client ID from Google Cloud Console
 * @param clientSecret
 *            OAuth 2.0 client secret (treat as sensitive)
 * @param refreshToken
 *            OAuth 2.0 refresh token for obtaining access tokens
 * @since 1.0.0
 */
public record UserOAuthAuth(String clientId, String clientSecret, String refreshToken) implements AuthType {

	public UserOAuthAuth {
		Objects.requireNonNull(clientId, "clientId cannot be null");
		Objects.requireNonNull(clientSecret, "clientSecret cannot be null");
		Objects.requireNonNull(refreshToken, "refreshToken cannot be null");
	}

	@Override
	public Credentials toCredentials() throws IOException {
		return UserCredentials.newBuilder().setClientId(clientId).setClientSecret(clientSecret)
				.setRefreshToken(refreshToken).build();
	}
}

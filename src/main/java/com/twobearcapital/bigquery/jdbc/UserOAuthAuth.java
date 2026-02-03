/*
 * Copyright 2026 Two Bear Capital
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.twobearcapital.bigquery.jdbc;

import com.google.auth.Credentials;
import com.google.auth.oauth2.UserCredentials;
import java.io.IOException;
import java.util.Objects;

/**
 * User OAuth authentication.
 *
 * @param clientId
 *            OAuth client ID
 * @param clientSecret
 *            OAuth client secret
 * @param refreshToken
 *            OAuth refresh token
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

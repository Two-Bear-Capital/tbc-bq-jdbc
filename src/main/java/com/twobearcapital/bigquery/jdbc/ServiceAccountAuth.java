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
import com.google.auth.oauth2.ServiceAccountCredentials;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Objects;

/**
 * Service account authentication using a JSON key file.
 *
 * @param jsonKeyPath
 *            path to the service account JSON key file
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

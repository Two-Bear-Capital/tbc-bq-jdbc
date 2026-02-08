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
import com.google.auth.oauth2.GoogleCredentials;

import java.io.IOException;

/**
 * Application Default Credentials (ADC) authentication.
 *
 * <p>
 * This will use credentials from:
 *
 * <ol>
 * <li>GOOGLE_APPLICATION_CREDENTIALS environment variable
 * <li>Google Cloud SDK credentials (gcloud auth application-default login)
 * <li>Compute Engine/GKE metadata server
 * </ol>
 *
 * @since 1.0.0
 */
public record ApplicationDefaultAuth() implements AuthType {

	@Override
	public Credentials toCredentials() throws IOException {
		return GoogleCredentials.getApplicationDefault();
	}
}

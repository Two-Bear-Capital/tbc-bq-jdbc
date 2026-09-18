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
import com.google.auth.http.HttpTransportFactory;
import vc.tbc.bq.jdbc.transport.DriverTransports;
import vc.tbc.bq.jdbc.transport.TransportConfig;

import java.io.IOException;

/**
 * Authentication type for BigQuery connections.
 *
 * <p>
 * This sealed interface defines all supported authentication methods for
 * connecting to BigQuery.
 *
 * <p>
 * All but one map to an {@code authType} connection property value.
 * {@link ImpersonatedAuth} is the exception: it wraps another {@code AuthType}
 * rather than replacing it, so a switch over this hierarchy that means "which
 * credential source is this" must look through it.
 *
 * @since 1.0.0
 */
public sealed interface AuthType permits ServiceAccountAuth, ApplicationDefaultAuth, UserOAuthAuth,
		WorkforceIdentityAuth, WorkloadIdentityAuth, ImpersonatedAuth, AccessTokenAuth {

	/**
	 * Converts this authentication type to Google Cloud credentials, connecting
	 * directly.
	 *
	 * @return the Google Cloud credentials
	 * @throws IOException
	 *             if credentials cannot be created
	 */
	default Credentials toCredentials() throws IOException {
		return toCredentials(DriverTransports.forTransport(TransportConfig.direct()));
	}

	/**
	 * Converts this authentication type to Google Cloud credentials fetched over
	 * {@code transportFactory}.
	 *
	 * <p>
	 * The factory is not optional and not a detail an implementation may ignore: a
	 * credential holds the transport it refreshes its token over, so one built
	 * without it goes direct even when the rest of the connection is proxied. That
	 * failure surfaces while minting the credential, before any BigQuery call, and
	 * is invisible to a test that only asserts the API client was configured.
	 *
	 * @param transportFactory
	 *            the transport to fetch and refresh tokens over, from
	 *            {@link DriverTransports#forTransport}
	 * @return the Google Cloud credentials
	 * @throws IOException
	 *             if credentials cannot be created
	 * @since 4.3.0
	 */
	Credentials toCredentials(HttpTransportFactory transportFactory) throws IOException;
}

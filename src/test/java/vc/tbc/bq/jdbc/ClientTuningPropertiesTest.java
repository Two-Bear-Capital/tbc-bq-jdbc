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

import com.google.cloud.bigquery.BigQueryOptions;
import com.google.cloud.http.HttpTransportOptions;
import vc.tbc.bq.jdbc.config.ConnectionProperties;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Verifies that {@code retryCount} and {@code connectionTimeout} reach the
 * BigQuery client rather than being parsed and discarded.
 *
 * <p>
 * Both were advertised through {@code Driver.getPropertyInfo()} — so they
 * appeared in the generated property reference — while having no read sites
 * anywhere in {@code src/main}. Nothing asserted the wiring, which is why that
 * went unnoticed. These tests read the settings back off the constructed
 * client.
 *
 * <p>
 * No BigQuery calls are made: building the options object is enough, and
 * {@code authType=ADC} resolves against whatever ambient credentials exist
 * without contacting the service.
 *
 * @since 2.4.3
 */
class ClientTuningPropertiesTest {

	private static final String BASE = "jdbc:bigquery:test-project/test_dataset?authType=ADC";

	/** Opens a connection and hands back the client's effective options. */
	private static BigQueryOptions optionsFor(String url) throws SQLException {
		try (Connection conn = new BQDriver().connect(url, null)) {
			return (BigQueryOptions) ((BQConnection) conn).getBigQuery().getOptions();
		}
	}

	@Test
	void retryCountBecomesMaxAttempts() throws SQLException {
		// Given: a connection asking for 7 attempts
		BigQueryOptions options = optionsFor(BASE + "&retryCount=7");

		// Then: the client is configured to make that many
		assertEquals(7, options.getRetrySettings().getMaxAttempts());
	}

	@Test
	void retryCountOfZeroStillAllowsOneAttempt() throws SQLException {
		// Given: retryCount=0, which must not mean "never contact BigQuery"
		BigQueryOptions options = optionsFor(BASE + "&retryCount=0");

		// Then: exactly one attempt is made — the request itself, with no retries
		assertEquals(1, options.getRetrySettings().getMaxAttempts());
	}

	@Test
	void connectionTimeoutBecomesConnectTimeoutInMillis() throws SQLException {
		// Given: a 45-second connection timeout
		BigQueryOptions options = optionsFor(BASE + "&connectionTimeout=45");

		// Then: the transport is configured in milliseconds
		HttpTransportOptions transport = (HttpTransportOptions) options.getTransportOptions();
		assertEquals(45_000, transport.getConnectTimeout());
	}

	@Test
	void connectionTimeoutDoesNotCapReadTimeout() throws SQLException {
		// Given: a short connection timeout
		BigQueryOptions options = optionsFor(BASE + "&connectionTimeout=5");
		HttpTransportOptions transport = (HttpTransportOptions) options.getTransportOptions();

		// Then: the read timeout is untouched, so a long-running query configured via
		// the `timeout` property is not severed by the connect setting
		assertNotEquals(5_000, transport.getReadTimeout());
		assertEquals(BigQueryOptions.getDefaultHttpTransportOptions().getReadTimeout(), transport.getReadTimeout());
	}

	@Test
	void defaultRetryCountMatchesTheClientLibrary() throws SQLException {
		// Given: a connection setting neither property
		BigQueryOptions options = optionsFor(BASE);

		// Then: applying our default is a no-op, so a connection that never sets
		// retryCount keeps exactly the resilience it had before the property worked
		assertEquals(BigQueryOptions.getDefaultRetrySettings().getMaxAttempts(),
				options.getRetrySettings().getMaxAttempts());
		assertEquals(ConnectionProperties.DEFAULT_RETRY_COUNT, options.getRetrySettings().getMaxAttempts());
	}
}

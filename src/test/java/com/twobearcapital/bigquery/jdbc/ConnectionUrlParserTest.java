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
package com.twobearcapital.bigquery.jdbc;

import static org.junit.jupiter.api.Assertions.*;

import com.twobearcapital.bigquery.jdbc.auth.*;
import com.twobearcapital.bigquery.jdbc.config.ConnectionProperties;
import com.twobearcapital.bigquery.jdbc.config.ConnectionUrlParser;
import com.twobearcapital.bigquery.jdbc.config.JobCreationMode;
import java.sql.SQLException;
import java.util.Properties;
import org.junit.jupiter.api.Test;

/**
 * Tests for ConnectionUrlParser.
 *
 * @since 1.0.0
 */
class ConnectionUrlParserTest {

	@Test
	void testParseMinimalUrl() throws SQLException {
		// Given: A minimal URL with just project
		String url = "jdbc:bigquery:my-project";

		// When: Parsing the URL
		ConnectionProperties props = ConnectionUrlParser.parse(url, null);

		// Then: Project should be set, dataset should be null, default auth type
		assertEquals("my-project", props.projectId());
		assertNull(props.datasetId());
		assertInstanceOf(ApplicationDefaultAuth.class, props.authType());
	}

	@Test
	void testParseUrlWithDataset() throws SQLException {
		// Given: A URL with project and dataset
		String url = "jdbc:bigquery:my-project/my_dataset";

		// When: Parsing the URL
		ConnectionProperties props = ConnectionUrlParser.parse(url, null);

		// Then: Both project and dataset should be set
		assertEquals("my-project", props.projectId());
		assertEquals("my_dataset", props.datasetId());
	}

	@Test
	void testParseUrlWithAdcAuth() throws SQLException {
		// Given: A URL with ADC authentication
		String url = "jdbc:bigquery:my-project/my_dataset?authType=ADC";

		// When: Parsing the URL
		ConnectionProperties props = ConnectionUrlParser.parse(url, null);

		// Then: Auth type should be ADC
		assertInstanceOf(ApplicationDefaultAuth.class, props.authType());
	}

	@Test
	void testParseUrlWithServiceAccountAuth() throws SQLException {
		// Given: A URL with service account authentication
		String url = "jdbc:bigquery:my-project/my_dataset?authType=SERVICE_ACCOUNT&credentials=/path/to/key.json";

		// When: Parsing the URL
		ConnectionProperties props = ConnectionUrlParser.parse(url, null);

		// Then: Auth type should be ServiceAccount with correct path
		assertInstanceOf(ServiceAccountAuth.class, props.authType());
		ServiceAccountAuth auth = (ServiceAccountAuth) props.authType();
		assertEquals("/path/to/key.json", auth.jsonKeyPath());
	}

	@Test
	void testParseUrlWithUserOAuthAuth() throws SQLException {
		// Given: A URL with user OAuth authentication
		String url = "jdbc:bigquery:my-project?authType=USER_OAUTH&clientId=abc&clientSecret=def&refreshToken=xyz";

		// When: Parsing the URL
		ConnectionProperties props = ConnectionUrlParser.parse(url, null);

		// Then: Auth type should be UserOAuth with correct values
		assertInstanceOf(UserOAuthAuth.class, props.authType());
		UserOAuthAuth auth = (UserOAuthAuth) props.authType();
		assertEquals("abc", auth.clientId());
		assertEquals("def", auth.clientSecret());
		assertEquals("xyz", auth.refreshToken());
	}

	@Test
	void testParseUrlWithTimeout() throws SQLException {
		// Given: A URL with timeout parameter
		String url = "jdbc:bigquery:my-project?timeout=120";

		// When: Parsing the URL
		ConnectionProperties props = ConnectionUrlParser.parse(url, null);

		// Then: Timeout should be set
		assertEquals(120, props.timeoutSeconds());
	}

	@Test
	void testParseUrlWithMaxResults() throws SQLException {
		// Given: A URL with maxResults parameter
		String url = "jdbc:bigquery:my-project?maxResults=1000";

		// When: Parsing the URL
		ConnectionProperties props = ConnectionUrlParser.parse(url, null);

		// Then: Max results should be set
		assertEquals(1000L, props.maxResults());
	}

	@Test
	void testParseUrlWithUseLegacySql() throws SQLException {
		// Given: A URL with useLegacySql parameter
		String url = "jdbc:bigquery:my-project?useLegacySql=true";

		// When: Parsing the URL
		ConnectionProperties props = ConnectionUrlParser.parse(url, null);

		// Then: Legacy SQL should be enabled
		assertTrue(props.useLegacySql());
	}

	@Test
	void testParseUrlWithLocation() throws SQLException {
		// Given: A URL with location parameter
		String url = "jdbc:bigquery:my-project?location=EU";

		// When: Parsing the URL
		ConnectionProperties props = ConnectionUrlParser.parse(url, null);

		// Then: Location should be set
		assertEquals("EU", props.location());
	}

	@Test
	void testParseUrlWithLabels() throws SQLException {
		// Given: A URL with labels
		String url = "jdbc:bigquery:my-project?labels=env=prod,team=data";

		// When: Parsing the URL
		ConnectionProperties props = ConnectionUrlParser.parse(url, null);

		// Then: Labels should be parsed
		assertEquals(2, props.labels().size());
		assertEquals("prod", props.labels().get("env"));
		assertEquals("data", props.labels().get("team"));
	}

	@Test
	void testParseUrlWithJobCreationMode() throws SQLException {
		// Given: A URL with jobCreationMode
		String url = "jdbc:bigquery:my-project?jobCreationMode=OPTIONAL";

		// When: Parsing the URL
		ConnectionProperties props = ConnectionUrlParser.parse(url, null);

		// Then: Job creation mode should be set
		assertEquals(JobCreationMode.OPTIONAL, props.jobCreationMode());
	}

	@Test
	void testParseUrlWithMultipleParameters() throws SQLException {
		// Given: A URL with multiple parameters
		String url = "jdbc:bigquery:my-project/my_dataset?authType=ADC&timeout=60&maxResults=5000&useLegacySql=false&location=US";

		// When: Parsing the URL
		ConnectionProperties props = ConnectionUrlParser.parse(url, null);

		// Then: All parameters should be set
		assertEquals("my-project", props.projectId());
		assertEquals("my_dataset", props.datasetId());
		assertInstanceOf(ApplicationDefaultAuth.class, props.authType());
		assertEquals(60, props.timeoutSeconds());
		assertEquals(5000L, props.maxResults());
		assertFalse(props.useLegacySql());
		assertEquals("US", props.location());
	}

	@Test
	void testParseUrlWithPropertiesObject() throws SQLException {
		// Given: A URL and a Properties object
		String url = "jdbc:bigquery:my-project";
		Properties info = new Properties();
		info.setProperty("timeout", "90");
		info.setProperty("location", "EU");

		// When: Parsing the URL with properties
		ConnectionProperties props = ConnectionUrlParser.parse(url, info);

		// Then: Properties should override URL parameters
		assertEquals(90, props.timeoutSeconds());
		assertEquals("EU", props.location());
	}

	@Test
	void testParseUrlWithUrlEncodedValues() throws SQLException {
		// Given: A URL with encoded values
		String url = "jdbc:bigquery:my-project?labels=key%20with%20space=value%20with%20space";

		// When: Parsing the URL
		ConnectionProperties props = ConnectionUrlParser.parse(url, null);

		// Then: Values should be decoded
		assertEquals("value with space", props.labels().get("key with space"));
	}

	@Test
	void testParseInvalidUrlThrowsException() {
		// Given: Invalid URLs
		String invalidUrl1 = "jdbc:postgresql://localhost/db";
		String invalidUrl2 = "not-a-jdbc-url";
		String invalidUrl3 = null;

		// Then: Should throw SQLException
		assertThrows(SQLException.class, () -> ConnectionUrlParser.parse(invalidUrl1, null));
		assertThrows(SQLException.class, () -> ConnectionUrlParser.parse(invalidUrl2, null));
		assertThrows(SQLException.class, () -> ConnectionUrlParser.parse(invalidUrl3, null));
	}

	@Test
	void testParseMissingServiceAccountCredentials() {
		// Given: SERVICE_ACCOUNT auth without credentials
		String url = "jdbc:bigquery:my-project?authType=SERVICE_ACCOUNT";

		// Then: Should throw SQLException
		SQLException ex = assertThrows(SQLException.class, () -> ConnectionUrlParser.parse(url, null));
		assertTrue(ex.getMessage().contains("credentials"));
	}

	@Test
	void testParseMissingUserOAuthParameters() {
		// Given: USER_OAUTH auth without required parameters
		String url = "jdbc:bigquery:my-project?authType=USER_OAUTH&clientId=abc";

		// Then: Should throw SQLException
		SQLException ex = assertThrows(SQLException.class, () -> ConnectionUrlParser.parse(url, null));
		assertTrue(ex.getMessage().contains("clientSecret") || ex.getMessage().contains("refreshToken"));
	}

	@Test
	void testParseInvalidTimeout() {
		// Given: Invalid timeout value
		String url = "jdbc:bigquery:my-project?timeout=not-a-number";

		// Then: Should throw SQLException
		SQLException ex = assertThrows(SQLException.class, () -> ConnectionUrlParser.parse(url, null));
		assertTrue(ex.getMessage().contains("timeout"));
	}

	@Test
	void testParseInvalidJobCreationMode() {
		// Given: Invalid job creation mode
		String url = "jdbc:bigquery:my-project?jobCreationMode=INVALID";

		// Then: Should throw SQLException
		SQLException ex = assertThrows(SQLException.class, () -> ConnectionUrlParser.parse(url, null));
		assertTrue(ex.getMessage().contains("jobCreationMode"));
	}

	@Test
	void testParseDefaultValues() throws SQLException {
		// Given: A URL with no optional parameters
		String url = "jdbc:bigquery:my-project?authType=ADC";

		// When: Parsing the URL
		ConnectionProperties props = ConnectionUrlParser.parse(url, null);

		// Then: Default values should be set
		assertEquals(ConnectionProperties.DEFAULT_TIMEOUT_SECONDS, props.timeoutSeconds());
		assertEquals(ConnectionProperties.DEFAULT_PAGE_SIZE, props.pageSize());
		assertEquals(ConnectionProperties.DEFAULT_CONNECTION_TIMEOUT, props.connectionTimeout());
		assertEquals(ConnectionProperties.DEFAULT_RETRY_COUNT, props.retryCount());
		assertFalse(props.useLegacySql());
		assertFalse(props.enableSessions());
		assertEquals("auto", props.useStorageApi());
		assertEquals(JobCreationMode.REQUIRED, props.jobCreationMode());
	}

	@Test
	void testParseDatasetProjectId() throws SQLException {
		// Given: A URL with datasetProjectId
		String url = "jdbc:bigquery:my-project/my_dataset?datasetProjectId=other-project";

		// When: Parsing the URL
		ConnectionProperties props = ConnectionUrlParser.parse(url, null);

		// Then: Dataset project ID should be set
		assertEquals("my-project", props.projectId());
		assertEquals("my_dataset", props.datasetId());
		assertEquals("other-project", props.datasetProjectId());
	}

	@Test
	void testParseCaseInsensitiveAuthType() throws SQLException {
		// Given: URLs with different case auth types
		String url1 = "jdbc:bigquery:my-project?authType=adc";
		String url2 = "jdbc:bigquery:my-project?authType=Adc";
		String url3 = "jdbc:bigquery:my-project?authType=ADC";

		// When: Parsing the URLs
		ConnectionProperties props1 = ConnectionUrlParser.parse(url1, null);
		ConnectionProperties props2 = ConnectionUrlParser.parse(url2, null);
		ConnectionProperties props3 = ConnectionUrlParser.parse(url3, null);

		// Then: All should parse to ApplicationDefaultAuth
		assertInstanceOf(ApplicationDefaultAuth.class, props1.authType());
		assertInstanceOf(ApplicationDefaultAuth.class, props2.authType());
		assertInstanceOf(ApplicationDefaultAuth.class, props3.authType());
	}
}

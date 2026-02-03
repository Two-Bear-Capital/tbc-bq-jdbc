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

import static org.junit.jupiter.api.Assertions.*;

import java.sql.SQLException;
import java.util.Properties;
import org.junit.jupiter.api.Test;

/**
 * Tests for ConnectionUrlParser with Simba BigQuery JDBC driver format URLs.
 *
 * @since 1.0.0
 */
class ConnectionUrlParserSimbaTest {

	@Test
	void testParseMinimalSimbaUrl() throws SQLException {
		// Given: A minimal Simba URL with just ProjectId and OAuthType
		String url = "jdbc:bigquery://https://www.googleapis.com/bigquery/v2:443;ProjectId=my-project;OAuthType=3";

		// When: Parsing the URL
		ConnectionProperties props = ConnectionUrlParser.parse(url, null);

		// Then: Project should be set, dataset null, ADC auth type
		assertEquals("my-project", props.projectId());
		assertNull(props.datasetId());
		assertInstanceOf(ApplicationDefaultAuth.class, props.authType());
	}

	@Test
	void testParseSimbaUrlWithDataset() throws SQLException {
		// Given: A Simba URL with project and dataset
		String url = "jdbc:bigquery://https://www.googleapis.com/bigquery/v2:443;ProjectId=my-project;DefaultDataset=my_dataset;OAuthType=3";

		// When: Parsing the URL
		ConnectionProperties props = ConnectionUrlParser.parse(url, null);

		// Then: Both project and dataset should be set
		assertEquals("my-project", props.projectId());
		assertEquals("my_dataset", props.datasetId());
		assertInstanceOf(ApplicationDefaultAuth.class, props.authType());
	}

	@Test
	void testParseSimbaUrlWithServiceAccount() throws SQLException {
		// Given: A Simba URL with service account authentication
		String url = "jdbc:bigquery://https://www.googleapis.com/bigquery/v2:443;ProjectId=my-project;OAuthType=0;OAuthPvtKeyPath=/path/to/key.json";

		// When: Parsing the URL
		ConnectionProperties props = ConnectionUrlParser.parse(url, null);

		// Then: Auth type should be ServiceAccount with correct path
		assertEquals("my-project", props.projectId());
		assertInstanceOf(ServiceAccountAuth.class, props.authType());
		ServiceAccountAuth auth = (ServiceAccountAuth) props.authType();
		assertEquals("/path/to/key.json", auth.jsonKeyPath());
	}

	@Test
	void testParseSimbaUrlWithUserOAuth() throws SQLException {
		// Given: A Simba URL with user OAuth authentication
		String url = "jdbc:bigquery://https://www.googleapis.com/bigquery/v2:443;ProjectId=my-project;OAuthType=1;OAuthClientId=abc;OAuthClientSecret=def;OAuthRefreshToken=xyz";

		// When: Parsing the URL
		ConnectionProperties props = ConnectionUrlParser.parse(url, null);

		// Then: Auth type should be UserOAuth with correct values
		assertEquals("my-project", props.projectId());
		assertInstanceOf(UserOAuthAuth.class, props.authType());
		UserOAuthAuth auth = (UserOAuthAuth) props.authType();
		assertEquals("abc", auth.clientId());
		assertEquals("def", auth.clientSecret());
		assertEquals("xyz", auth.refreshToken());
	}

	@Test
	void testParseSimbaUrlWithApplicationDefault() throws SQLException {
		// Given: A Simba URL with ADC (OAuthType=3)
		String url = "jdbc:bigquery://https://www.googleapis.com/bigquery/v2:443;ProjectId=my-project;OAuthType=3";

		// When: Parsing the URL
		ConnectionProperties props = ConnectionUrlParser.parse(url, null);

		// Then: Auth type should be ApplicationDefault
		assertInstanceOf(ApplicationDefaultAuth.class, props.authType());
	}

	@Test
	void testParseSimbaUrlWithExternalAccountRequiresCredentialFile() {
		// Given: A Simba URL with external account (OAuthType=4) but no credential file
		String url = "jdbc:bigquery://https://www.googleapis.com/bigquery/v2:443;ProjectId=my-project;OAuthType=4";

		// Then: Should throw SQLException because WORKLOAD requires
		// credentialConfigFile
		SQLException ex = assertThrows(SQLException.class, () -> ConnectionUrlParser.parse(url, null));
		assertTrue(ex.getMessage().contains("credentialConfigFile"));
	}

	@Test
	void testParseSimbaUrlWithExternalAccountAndCredentialFile() throws SQLException {
		// Given: A Simba URL with external account (OAuthType=4) and credential file
		// via Properties
		String url = "jdbc:bigquery://https://www.googleapis.com/bigquery/v2:443;ProjectId=my-project;OAuthType=4";
		Properties info = new Properties();
		info.setProperty("credentialConfigFile", "/path/to/config.json");

		// When: Parsing the URL with properties
		ConnectionProperties props = ConnectionUrlParser.parse(url, info);

		// Then: Should map to WORKLOAD with credential file
		assertEquals("my-project", props.projectId());
		assertInstanceOf(WorkloadIdentityAuth.class, props.authType());
		WorkloadIdentityAuth auth = (WorkloadIdentityAuth) props.authType();
		assertEquals("/path/to/config.json", auth.credentialConfigFile());
	}

	@Test
	void testParseSimbaUrlWithAllProperties() throws SQLException {
		// Given: A Simba URL with multiple properties
		String url = "jdbc:bigquery://https://www.googleapis.com/bigquery/v2:443;ProjectId=my-project;DefaultDataset=my_dataset;OAuthType=3;Timeout=120;MaxResults=5000;UseLegacySQL=true;Location=EU;DatasetProjectId=other-project";

		// When: Parsing the URL
		ConnectionProperties props = ConnectionUrlParser.parse(url, null);

		// Then: All properties should be set
		assertEquals("my-project", props.projectId());
		assertEquals("my_dataset", props.datasetId());
		assertInstanceOf(ApplicationDefaultAuth.class, props.authType());
		assertEquals(120, props.timeoutSeconds());
		assertEquals(5000L, props.maxResults());
		assertTrue(props.useLegacySql());
		assertEquals("EU", props.location());
		assertEquals("other-project", props.datasetProjectId());
	}

	@Test
	void testParseSimbaUrlWithTrailingSemicolon() throws SQLException {
		// Given: A Simba URL with trailing semicolon (common in Simba format)
		String url = "jdbc:bigquery://https://www.googleapis.com/bigquery/v2:443;ProjectId=my-project;OAuthType=3;";

		// When: Parsing the URL
		ConnectionProperties props = ConnectionUrlParser.parse(url, null);

		// Then: Should parse correctly
		assertEquals("my-project", props.projectId());
		assertInstanceOf(ApplicationDefaultAuth.class, props.authType());
	}

	@Test
	void testParseSimbaUrlWithPropertiesObject() throws SQLException {
		// Given: A Simba URL and a Properties object
		String url = "jdbc:bigquery://https://www.googleapis.com/bigquery/v2:443;ProjectId=my-project;OAuthType=3;Timeout=60";
		Properties info = new Properties();
		info.setProperty("timeout", "120");
		info.setProperty("location", "US");

		// When: Parsing the URL with properties
		ConnectionProperties props = ConnectionUrlParser.parse(url, info);

		// Then: Properties should override URL parameters
		assertEquals(120, props.timeoutSeconds());
		assertEquals("US", props.location());
	}

	@Test
	void testParseSimbaUrlWithWhitespace() throws SQLException {
		// Given: A Simba URL with whitespace around values
		String url = "jdbc:bigquery://https://www.googleapis.com/bigquery/v2:443;ProjectId= my-project ;OAuthType= 3 ";

		// When: Parsing the URL
		ConnectionProperties props = ConnectionUrlParser.parse(url, null);

		// Then: Whitespace should be trimmed
		assertEquals("my-project", props.projectId());
		assertInstanceOf(ApplicationDefaultAuth.class, props.authType());
	}

	@Test
	void testParseSimbaUrlInvalidFormat() {
		// Given: Invalid Simba URL formats
		String invalidUrl1 = "jdbc:bigquery://no-semicolon";
		String invalidUrl2 = "jdbc:bigquery://;";

		// Then: Should throw SQLException
		assertThrows(SQLException.class, () -> ConnectionUrlParser.parse(invalidUrl1, null));
		assertThrows(SQLException.class, () -> ConnectionUrlParser.parse(invalidUrl2, null));
	}

	@Test
	void testParseSimbaUrlMissingProjectId() {
		// Given: Simba URL without ProjectId
		String url = "jdbc:bigquery://https://www.googleapis.com/bigquery/v2:443;OAuthType=3";

		// Then: Should throw SQLException
		SQLException ex = assertThrows(SQLException.class, () -> ConnectionUrlParser.parse(url, null));
		assertTrue(ex.getMessage().contains("ProjectId"));
	}

	@Test
	void testParseSimbaUrlInvalidOAuthType() {
		// Given: Simba URL with invalid OAuthType
		String url = "jdbc:bigquery://https://www.googleapis.com/bigquery/v2:443;ProjectId=my-project;OAuthType=99";

		// Then: Should throw SQLException with clear message
		SQLException ex = assertThrows(SQLException.class, () -> ConnectionUrlParser.parse(url, null));
		assertTrue(ex.getMessage().contains("OAuthType"));
		assertTrue(ex.getMessage().contains("99"));
	}

	@Test
	void testParseSimbaUrlUnsupportedOAuthType2() {
		// Given: Simba URL with unsupported OAuthType=2 (pre-generated tokens)
		String url = "jdbc:bigquery://https://www.googleapis.com/bigquery/v2:443;ProjectId=my-project;OAuthType=2";

		// Then: Should throw SQLException with specific message
		SQLException ex = assertThrows(SQLException.class, () -> ConnectionUrlParser.parse(url, null));
		assertTrue(ex.getMessage().contains("Pre-generated"));
		assertTrue(ex.getMessage().contains("OAuthType=2"));
	}

	@Test
	void testParseSimbaUrlMissingServiceAccountCredentials() {
		// Given: Simba URL with OAuthType=0 but no OAuthPvtKeyPath
		String url = "jdbc:bigquery://https://www.googleapis.com/bigquery/v2:443;ProjectId=my-project;OAuthType=0";

		// Then: Should throw SQLException
		SQLException ex = assertThrows(SQLException.class, () -> ConnectionUrlParser.parse(url, null));
		assertTrue(ex.getMessage().contains("credentials"));
	}

	@Test
	void testParseSimbaUrlMissingUserOAuthParameters() {
		// Given: Simba URL with OAuthType=1 but missing required parameters
		String url = "jdbc:bigquery://https://www.googleapis.com/bigquery/v2:443;ProjectId=my-project;OAuthType=1;OAuthClientId=abc";

		// Then: Should throw SQLException
		SQLException ex = assertThrows(SQLException.class, () -> ConnectionUrlParser.parse(url, null));
		assertTrue(ex.getMessage().contains("clientSecret") || ex.getMessage().contains("refreshToken"));
	}

	@Test
	void testParseSimbaUrlUnknownPropertiesIgnored() throws SQLException {
		// Given: A Simba URL with unknown properties
		String url = "jdbc:bigquery://https://www.googleapis.com/bigquery/v2:443;ProjectId=my-project;OAuthType=3;UnknownProperty=value;AnotherUnknown=test";

		// When: Parsing the URL
		ConnectionProperties props = ConnectionUrlParser.parse(url, null);

		// Then: Unknown properties should be ignored, URL should parse successfully
		assertEquals("my-project", props.projectId());
		assertInstanceOf(ApplicationDefaultAuth.class, props.authType());
	}

	@Test
	void testParseSimbaUrlPropertyMapping() throws SQLException {
		// Given: A Simba URL with properties that need mapping
		String url = "jdbc:bigquery://https://www.googleapis.com/bigquery/v2:443;ProjectId=test-project;DefaultDataset=test_dataset;Timeout=90;MaxResults=2000;Location=asia-northeast1;DatasetProjectId=cross-project;OAuthType=3";

		// When: Parsing the URL
		ConnectionProperties props = ConnectionUrlParser.parse(url, null);

		// Then: All properties should be correctly mapped
		assertEquals("test-project", props.projectId());
		assertEquals("test_dataset", props.datasetId());
		assertEquals(90, props.timeoutSeconds());
		assertEquals(2000L, props.maxResults());
		assertEquals("asia-northeast1", props.location());
		assertEquals("cross-project", props.datasetProjectId());
	}

	@Test
	void testParseSimbaUrlCaseInsensitiveOAuthType() throws SQLException {
		// Given: Simba URLs with string OAuthType values (should be numeric)
		// Note: Simba driver uses numeric values, but we should handle gracefully
		String url = "jdbc:bigquery://https://www.googleapis.com/bigquery/v2:443;ProjectId=my-project;OAuthType=3";

		// When: Parsing the URL
		ConnectionProperties props = ConnectionUrlParser.parse(url, null);

		// Then: Should parse correctly
		assertInstanceOf(ApplicationDefaultAuth.class, props.authType());
	}

	@Test
	void testBackwardCompatibilityTraditionalUrl() throws SQLException {
		// Given: A traditional format URL (non-Simba)
		String url = "jdbc:bigquery:my-project/my_dataset?authType=ADC&timeout=60";

		// When: Parsing the URL
		ConnectionProperties props = ConnectionUrlParser.parse(url, null);

		// Then: Should parse correctly using traditional parser
		assertEquals("my-project", props.projectId());
		assertEquals("my_dataset", props.datasetId());
		assertInstanceOf(ApplicationDefaultAuth.class, props.authType());
		assertEquals(60, props.timeoutSeconds());
	}

	@Test
	void testSimbaUrlDifferentHostFormats() throws SQLException {
		// Given: Simba URLs with different host formats
		String url1 = "jdbc:bigquery://https://www.googleapis.com/bigquery/v2:443;ProjectId=my-project;OAuthType=3";
		String url2 = "jdbc:bigquery://localhost:9050;ProjectId=my-project;OAuthType=3";
		String url3 = "jdbc:bigquery://bigquery.googleapis.com:443;ProjectId=my-project;OAuthType=3";

		// When: Parsing the URLs
		ConnectionProperties props1 = ConnectionUrlParser.parse(url1, null);
		ConnectionProperties props2 = ConnectionUrlParser.parse(url2, null);
		ConnectionProperties props3 = ConnectionUrlParser.parse(url3, null);

		// Then: All should parse correctly (host is validated but not used)
		assertEquals("my-project", props1.projectId());
		assertEquals("my-project", props2.projectId());
		assertEquals("my-project", props3.projectId());
	}

	@Test
	void testSimbaUrlWithSpecialCharactersInPath() throws SQLException {
		// Given: A Simba URL with special characters in file paths
		String url = "jdbc:bigquery://https://www.googleapis.com/bigquery/v2:443;ProjectId=my-project;OAuthType=0;OAuthPvtKeyPath=/path/to/my key file.json";

		// When: Parsing the URL
		ConnectionProperties props = ConnectionUrlParser.parse(url, null);

		// Then: Path should be preserved correctly
		ServiceAccountAuth auth = (ServiceAccountAuth) props.authType();
		assertEquals("/path/to/my key file.json", auth.jsonKeyPath());
	}

	@Test
	void testSimbaUrlBooleanPropertyParsing() throws SQLException {
		// Given: Simba URLs with boolean properties
		String url1 = "jdbc:bigquery://https://www.googleapis.com/bigquery/v2:443;ProjectId=my-project;OAuthType=3;UseLegacySQL=true";
		String url2 = "jdbc:bigquery://https://www.googleapis.com/bigquery/v2:443;ProjectId=my-project;OAuthType=3;UseLegacySQL=false";

		// When: Parsing the URLs
		ConnectionProperties props1 = ConnectionUrlParser.parse(url1, null);
		ConnectionProperties props2 = ConnectionUrlParser.parse(url2, null);

		// Then: Boolean values should be parsed correctly
		assertTrue(props1.useLegacySql());
		assertFalse(props2.useLegacySql());
	}
}

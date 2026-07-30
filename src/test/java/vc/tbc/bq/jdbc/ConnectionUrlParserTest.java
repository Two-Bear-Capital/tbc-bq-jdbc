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

import org.junit.jupiter.api.Test;
import vc.tbc.bq.jdbc.auth.ApplicationDefaultAuth;
import vc.tbc.bq.jdbc.auth.ImpersonatedAuth;
import vc.tbc.bq.jdbc.auth.ServiceAccountAuth;
import vc.tbc.bq.jdbc.auth.UserOAuthAuth;
import vc.tbc.bq.jdbc.config.ConnectionProperties;
import vc.tbc.bq.jdbc.config.ConnectionUrlParser;
import vc.tbc.bq.jdbc.config.MetadataCache;

import java.sql.SQLException;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

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
		assertEquals("false", props.useStorageApi());
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

	// --- Properties not covered by existing tests ---

	@Test
	void testParseUrlWithEnableSessions() throws SQLException {
		String url = "jdbc:bigquery:my-project?enableSessions=true";
		ConnectionProperties props = ConnectionUrlParser.parse(url, null);
		assertTrue(props.enableSessions());
	}

	@Test
	void testParseUrlWithConnectionTimeout() throws SQLException {
		String url = "jdbc:bigquery:my-project?connectionTimeout=60";
		ConnectionProperties props = ConnectionUrlParser.parse(url, null);
		assertEquals(60, props.connectionTimeout());
	}

	@Test
	void testParseUrlWithRetryCount() throws SQLException {
		String url = "jdbc:bigquery:my-project?retryCount=5";
		ConnectionProperties props = ConnectionUrlParser.parse(url, null);
		assertEquals(5, props.retryCount());
	}

	@Test
	void testParseUrlWithMaxBillingBytes() throws SQLException {
		String url = "jdbc:bigquery:my-project?maxBillingBytes=1000000";
		ConnectionProperties props = ConnectionUrlParser.parse(url, null);
		assertEquals(1_000_000L, props.maxBillingBytes());
	}

	@Test
	void testParseUrlWithPageSize() throws SQLException {
		String url = "jdbc:bigquery:my-project?pageSize=500";
		ConnectionProperties props = ConnectionUrlParser.parse(url, null);
		assertEquals(500, props.pageSize());
	}

	@Test
	void testParseUrlWithUseStorageApi() throws SQLException {
		String url = "jdbc:bigquery:my-project?useStorageApi=true";
		ConnectionProperties props = ConnectionUrlParser.parse(url, null);
		assertEquals("true", props.useStorageApi());
	}

	@Test
	void testParseUrlWithMetadataCacheTtl() throws SQLException {
		String url = "jdbc:bigquery:my-project?metadataCacheTtl=600";
		ConnectionProperties props = ConnectionUrlParser.parse(url, null);
		assertEquals(600, props.metadataCacheTtl());
	}

	@Test
	void testParseUrlWithMetadataCacheMaxRows() throws SQLException {
		String url = "jdbc:bigquery:my-project?metadataCacheMaxRows=1000";
		ConnectionProperties props = ConnectionUrlParser.parse(url, null);
		assertEquals(1000, props.metadataCacheMaxRows());
	}

	@Test
	void testMetadataCacheMaxRowsDefaultsToABoundedValue() throws SQLException {
		// The cache was unbounded before this property existed, so the default
		// mattering is the whole point - an omitted property must not mean "no
		// limit".
		String url = "jdbc:bigquery:my-project";
		ConnectionProperties props = ConnectionUrlParser.parse(url, null);
		assertEquals(MetadataCache.DEFAULT_MAX_ROWS, props.metadataCacheMaxRows());
		assertTrue(props.metadataCacheMaxRows() > 0, "the default must actually bound the cache");
	}

	@Test
	void testParseUrlWithMetadataCacheUnbounded() throws SQLException {
		// Zero is the documented opt-out for anyone who wants the old behaviour.
		String url = "jdbc:bigquery:my-project?metadataCacheMaxRows=0";
		ConnectionProperties props = ConnectionUrlParser.parse(url, null);
		assertEquals(0, props.metadataCacheMaxRows());
	}

	@Test
	void testParseUrlWithMetadataIncludeDescriptions() throws SQLException {
		String url = "jdbc:bigquery:my-project?metadataIncludeDescriptions=false";
		ConnectionProperties props = ConnectionUrlParser.parse(url, null);
		assertFalse(props.metadataIncludeDescriptions());
	}

	@Test
	void testMetadataIncludeDescriptionsDefaultsToOn() throws SQLException {
		// REMARKS is empty for every table without this read, so a default of off
		// would leave the gap in place for everyone who never finds the property.
		String url = "jdbc:bigquery:my-project";
		ConnectionProperties props = ConnectionUrlParser.parse(url, null);
		assertTrue(props.metadataIncludeDescriptions());
	}

	@Test
	void testParseUrlWithQueryPricePerTiB() throws SQLException {
		String url = "jdbc:bigquery:my-project?queryPricePerTiB=6.25";
		ConnectionProperties props = ConnectionUrlParser.parse(url, null);
		assertEquals(new java.math.BigDecimal("6.25"), props.queryPricePerTiB());
	}

	@Test
	void testQueryPricePerTiBIsUnsetByDefault() throws SQLException {
		// No default rate: one the driver invented would be wrong for every customer
		// not on on-demand pricing, and would go stale silently. Unset means cost
		// estimates report bytes and no money.
		String url = "jdbc:bigquery:my-project";
		ConnectionProperties props = ConnectionUrlParser.parse(url, null);
		assertNull(props.queryPricePerTiB());
	}

	@Test
	void testBlankQueryPricePerTiBIsUnsetRatherThanZero() throws SQLException {
		// "queryPricePerTiB=" reads as "I did not set this". Parsing it as zero would
		// price every query at nothing, which looks like a working estimate.
		String url = "jdbc:bigquery:my-project?queryPricePerTiB=";
		ConnectionProperties props = ConnectionUrlParser.parse(url, null);
		assertNull(props.queryPricePerTiB());
	}

	@Test
	void testInvalidQueryPricePerTiBIsRejected() {
		String url = "jdbc:bigquery:my-project?queryPricePerTiB=six-dollars";
		SQLException e = assertThrows(SQLException.class, () -> ConnectionUrlParser.parse(url, null));
		assertTrue(e.getMessage().contains("queryPricePerTiB"), "the message should name the property: " + e);
	}

	@Test
	void testParseUrlWithCollapseShardedTables() throws SQLException {
		String url = "jdbc:bigquery:my-project?collapseShardedTables=true";
		ConnectionProperties props = ConnectionUrlParser.parse(url, null);
		assertTrue(props.collapseShardedTables());
	}

	@Test
	void testCollapseShardedTablesDefaultsToOff() throws SQLException {
		// Opt-in: collapsing removes rows on the evidence of a naming convention, so
		// a table legitimately ending in a date must not vanish by default.
		String url = "jdbc:bigquery:my-project";
		ConnectionProperties props = ConnectionUrlParser.parse(url, null);
		assertFalse(props.collapseShardedTables());
	}

	@Test
	void testParseUrlWithMetadataCacheDisabled() throws SQLException {
		String url = "jdbc:bigquery:my-project?metadataCacheEnabled=false";
		ConnectionProperties props = ConnectionUrlParser.parse(url, null);
		assertFalse(props.metadataCacheEnabled());
	}

	@Test
	void testParseUrlWithMetadataLazyLoad() throws SQLException {
		String url = "jdbc:bigquery:my-project?metadataLazyLoad=true";
		ConnectionProperties props = ConnectionUrlParser.parse(url, null);
		assertTrue(props.metadataLazyLoad());
	}

	// ── A host no longer selects an auth type (#144) ──────────────────────────

	@Test
	void testHostWithoutAuthTypeDefaultsToAdc() throws SQLException {
		// A host used to imply the emulator's fabricated credentials, so anyone
		// pointing the driver at a proxy or a private endpoint silently got a token
		// that could not authenticate. It now means "reach BigQuery here".
		ConnectionProperties props = ConnectionUrlParser.parse("jdbc:bigquery:my-project?host=bq.internal", null);

		assertInstanceOf(ApplicationDefaultAuth.class, props.authType());
		assertEquals("bq.internal", props.host(), "The host is still honoured as an endpoint");
	}

	@Test
	void testSimbaHostWithoutOAuthTypeDefaultsToAdc() throws SQLException {
		ConnectionProperties props = ConnectionUrlParser.parse("jdbc:bigquery://bq.internal:9050;ProjectId=my-project",
				null);

		assertInstanceOf(ApplicationDefaultAuth.class, props.authType());
		assertEquals("bq.internal", props.host());
		assertEquals(9050, props.port());
	}

	@Test
	void testExplicitAuthTypeStillWinsOverTheDefault() throws SQLException {
		ConnectionProperties props = ConnectionUrlParser.parse(
				"jdbc:bigquery:my-project?host=bq.internal&authType=SERVICE_ACCOUNT&credentials=/key.json", null);

		assertInstanceOf(ServiceAccountAuth.class, props.authType());
	}

	@Test
	void testEmulatorAuthTypeIsRejected() {
		// Removed in 2.0.0 (#144). A URL that still asks for it must fail loudly
		// rather than fall back to real credentials it was never meant to use.
		SQLException thrown = assertThrows(SQLException.class,
				() -> ConnectionUrlParser.parse("jdbc:bigquery:my-project?authType=EMULATOR", null));
		assertTrue(thrown.getMessage().contains("Unsupported authentication type"), "Was: " + thrown.getMessage());
	}

	@Test
	void testUseDestinationTablesIsNoLongerAProperty() throws SQLException {
		// Also removed in 2.0.0. Unknown properties are ignored rather than
		// rejected, so the check is that parsing still succeeds and nothing about
		// the connection changes.
		ConnectionProperties props = ConnectionUrlParser.parse("jdbc:bigquery:my-project?useDestinationTables=true",
				null);

		assertEquals("my-project", props.projectId());
	}

	@Test
	void testParseUrlWithEnableQueryCostEstimation() throws SQLException {
		String url = "jdbc:bigquery:my-project?enableQueryCostEstimation=true";
		ConnectionProperties props = ConnectionUrlParser.parse(url, null);
		assertTrue(props.enableQueryCostEstimation());
	}

	@Test
	void testParseUrlWithNativeComplexTypesTrue() throws SQLException {
		String url = "jdbc:bigquery:my-project?nativeComplexTypes=true";
		ConnectionProperties props = ConnectionUrlParser.parse(url, null);
		assertTrue(props.nativeComplexTypes());
	}

	@Test
	void testParseUrlWithNativeComplexTypesFalse() throws SQLException {
		String url = "jdbc:bigquery:my-project?nativeComplexTypes=false";
		ConnectionProperties props = ConnectionUrlParser.parse(url, null);
		assertFalse(props.nativeComplexTypes());
	}

	@Test
	void testNativeComplexTypesDefaultIsFalse() throws SQLException {
		// nativeComplexTypes defaults to false when not specified
		String url = "jdbc:bigquery:my-project";
		ConnectionProperties props = ConnectionUrlParser.parse(url, null);
		assertFalse(props.nativeComplexTypes());
	}

	@Test
	void testParseUrlAllProperties() throws SQLException {
		// Given: A URL with every supported property set
		String url = "jdbc:bigquery:my-project/my_dataset"
				+ "?authType=ADC&timeout=120&maxResults=2000&useLegacySql=false&location=EU"
				+ "&labels=env=prod,team=data&datasetProjectId=other-project"
				+ "&pageSize=500&useStorageApi=false&enableSessions=true&connectionTimeout=45"
				+ "&retryCount=2&maxBillingBytes=5000000&metadataCacheTtl=120&metadataCacheEnabled=false"
				+ "&metadataLazyLoad=true&enableQueryCostEstimation=true" + "&nativeComplexTypes=true";

		ConnectionProperties props = ConnectionUrlParser.parse(url, null);

		assertEquals("my-project", props.projectId());
		assertEquals("my_dataset", props.datasetId());
		assertInstanceOf(ApplicationDefaultAuth.class, props.authType());
		assertEquals(120, props.timeoutSeconds());
		assertEquals(2000L, props.maxResults());
		assertFalse(props.useLegacySql());
		assertEquals("EU", props.location());
		assertEquals(2, props.labels().size());
		assertEquals("prod", props.labels().get("env"));
		assertEquals("other-project", props.datasetProjectId());
		assertEquals(500, props.pageSize());
		assertEquals("false", props.useStorageApi());
		assertTrue(props.enableSessions());
		assertEquals(45, props.connectionTimeout());
		assertEquals(2, props.retryCount());
		assertEquals(5_000_000L, props.maxBillingBytes());
		assertEquals(120, props.metadataCacheTtl());
		assertFalse(props.metadataCacheEnabled());
		assertTrue(props.metadataLazyLoad());
		assertTrue(props.enableQueryCostEstimation());
		assertTrue(props.nativeComplexTypes());
	}

	@Test
	void testParseUrlWithImpersonationOverDefaultAuth() throws SQLException {
		// Given: A URL naming only a target, with no authType
		String url = "jdbc:bigquery:my-project/my_dataset"
				+ "?impersonateServiceAccount=etl@my-project.iam.gserviceaccount.com";

		// When: Parsing the URL
		ConnectionProperties props = ConnectionUrlParser.parse(url, null);

		// Then: The default ADC auth should be wrapped, not replaced
		assertInstanceOf(ImpersonatedAuth.class, props.authType());
		ImpersonatedAuth auth = (ImpersonatedAuth) props.authType();
		assertInstanceOf(ApplicationDefaultAuth.class, auth.source());
		assertEquals("etl@my-project.iam.gserviceaccount.com", auth.targetPrincipal());
		assertEquals(List.of(), auth.delegates());
	}

	@Test
	void testParseUrlWithImpersonationOverServiceAccountAuth() throws SQLException {
		// Given: A URL pairing impersonation with an explicit source auth type
		String url = "jdbc:bigquery:my-project/my_dataset"
				+ "?authType=SERVICE_ACCOUNT&credentials=/keys/bootstrap.json"
				+ "&impersonateServiceAccount=etl@my-project.iam.gserviceaccount.com";

		// When: Parsing the URL
		ConnectionProperties props = ConnectionUrlParser.parse(url, null);

		// Then: The key file should be the source identity
		ImpersonatedAuth auth = assertInstanceOf(ImpersonatedAuth.class, props.authType());
		ServiceAccountAuth source = assertInstanceOf(ServiceAccountAuth.class, auth.source());
		assertEquals("/keys/bootstrap.json", source.jsonKeyPath());
	}

	@Test
	void testParseUrlWithImpersonationDelegates() throws SQLException {
		// Given: A URL with a two-hop delegation chain, with incidental whitespace
		String url = "jdbc:bigquery:my-project" + "?impersonateServiceAccount=etl@my-project.iam.gserviceaccount.com"
				+ "&impersonateDelegates=mid1@my-project.iam.gserviceaccount.com,"
				+ "%20mid2@my-project.iam.gserviceaccount.com";

		// When: Parsing the URL
		ConnectionProperties props = ConnectionUrlParser.parse(url, null);

		// Then: The chain should be trimmed and kept in source-first order
		ImpersonatedAuth auth = assertInstanceOf(ImpersonatedAuth.class, props.authType());
		assertEquals(List.of("mid1@my-project.iam.gserviceaccount.com", "mid2@my-project.iam.gserviceaccount.com"),
				auth.delegates());
	}

	@Test
	void testParseUrlWithoutImpersonationLeavesAuthTypeAlone() throws SQLException {
		// Given: A URL with no impersonation properties at all
		String url = "jdbc:bigquery:my-project?authType=ADC";

		// When: Parsing the URL
		ConnectionProperties props = ConnectionUrlParser.parse(url, null);

		// Then: The auth type should not be wrapped
		assertInstanceOf(ApplicationDefaultAuth.class, props.authType());
	}

	@Test
	void testParseUrlWithBlankImpersonationTargetIsNotImpersonation() throws SQLException {
		// Given: A URL where the target is present but empty
		String url = "jdbc:bigquery:my-project?impersonateServiceAccount=";

		// When: Parsing the URL
		ConnectionProperties props = ConnectionUrlParser.parse(url, null);

		// Then: It should read as "not set", like any other empty property
		assertInstanceOf(ApplicationDefaultAuth.class, props.authType());
	}

	@Test
	void testParseUrlWithDelegatesButNoTargetThrowsException() {
		// Given: A URL with a chain but nothing to reach
		String url = "jdbc:bigquery:my-project" + "?impersonateDelegates=mid1@my-project.iam.gserviceaccount.com";

		// Then: Parsing should fail rather than silently connect as the source
		SQLException e = assertThrows(SQLException.class, () -> ConnectionUrlParser.parse(url, null));
		assertTrue(e.getMessage().contains("impersonateServiceAccount"), e.getMessage());
	}

	@Test
	void testParseUrlWithImpersonationInProperties() throws SQLException {
		// Given: Impersonation supplied through the Properties object instead
		Properties info = new Properties();
		info.setProperty("impersonateServiceAccount", "etl@my-project.iam.gserviceaccount.com");

		// When: Parsing a URL that does not mention it
		ConnectionProperties props = ConnectionUrlParser.parse("jdbc:bigquery:my-project", info);

		// Then: It should apply the same way
		ImpersonatedAuth auth = assertInstanceOf(ImpersonatedAuth.class, props.authType());
		assertEquals("etl@my-project.iam.gserviceaccount.com", auth.targetPrincipal());
	}

	@Test
	void testParseAdditionalProjects() throws SQLException {
		// Given: A URL naming two further projects, with incidental whitespace
		String url = "jdbc:bigquery:my-project?additionalProjects=other-project,%20third-project";

		// When: Parsing the URL
		ConnectionProperties props = ConnectionUrlParser.parse(url, null);

		// Then: Both should be trimmed and kept
		assertEquals(List.of("other-project", "third-project"), props.additionalProjects());
	}

	@Test
	void testAdditionalProjectsDefaultsToEmpty() throws SQLException {
		// Then: Unset means no extra catalogs, not null
		assertEquals(List.of(), ConnectionUrlParser.parse("jdbc:bigquery:my-project", null).additionalProjects());
	}

	@Test
	void testAdditionalProjectsDropsTheConnectionProjectAndDuplicates() throws SQLException {
		// Given: A list that repeats itself and names the connection's own project
		String url = "jdbc:bigquery:my-project?additionalProjects=my-project,other,other,,%20";

		// When: Parsing the URL
		ConnectionProperties props = ConnectionUrlParser.parse(url, null);

		// Then: The connection's project is always reported anyway, so listing it
		// here would duplicate a catalog row
		assertEquals(List.of("other"), props.additionalProjects());
	}

	@Test
	void testAdditionalProjectsAreImmutable() throws SQLException {
		// Then: The record must not hand out a list a caller can grow
		ConnectionProperties props = ConnectionUrlParser.parse("jdbc:bigquery:p?additionalProjects=q", null);
		assertThrows(UnsupportedOperationException.class, () -> props.additionalProjects().add("late"));
	}

	// ------------------------------------------------------------------
	// Configuration with no URL (BQDataSource), and path overrides
	// ------------------------------------------------------------------

	@Test
	void testFromPropertiesMatchesTheEquivalentUrl() throws SQLException {
		// Given: The same settings expressed as properties and as a URL
		Properties info = new Properties();
		info.setProperty("projectId", "my-project");
		info.setProperty("datasetId", "my_dataset");
		info.setProperty("authType", "ADC");
		info.setProperty("timeout", "60");

		// When: Parsing each
		ConnectionProperties fromProperties = ConnectionUrlParser.fromProperties(info);
		ConnectionProperties fromUrl = ConnectionUrlParser
				.parse("jdbc:bigquery:my-project/my_dataset?authType=ADC&timeout=60", null);

		// Then: Neither path may apply a default or a rule the other does not
		assertEquals(fromUrl, fromProperties);
	}

	@Test
	void testFromPropertiesRequiresAProjectId() {
		// Given: Properties with everything but the project
		Properties info = new Properties();
		info.setProperty("authType", "ADC");

		// Then: There is no URL path to fall back on
		SQLException e = assertThrows(SQLException.class, () -> ConnectionUrlParser.fromProperties(info));
		assertTrue(e.getMessage().contains("projectId"));
	}

	@Test
	void testFromPropertiesAppliesDefaults() throws SQLException {
		// Given: Only the required property
		Properties info = new Properties();
		info.setProperty("projectId", "my-project");

		// When: Parsing with no URL
		ConnectionProperties props = ConnectionUrlParser.fromProperties(info);

		// Then: The same defaults a bare URL gets
		assertEquals(ConnectionProperties.DEFAULT_TIMEOUT_SECONDS, props.timeoutSeconds());
		assertEquals(ConnectionProperties.DEFAULT_PAGE_SIZE, props.pageSize());
		assertInstanceOf(ApplicationDefaultAuth.class, props.authType());
		assertNull(props.datasetId());
	}

	@Test
	void testExplicitDatasetIdOverridesTheUrlPath() throws SQLException {
		// Given: A URL naming one dataset and a property naming another
		Properties info = new Properties();
		info.setProperty("datasetId", "other_dataset");

		// When: Parsing
		ConnectionProperties props = ConnectionUrlParser.parse("jdbc:bigquery:my-project/url_dataset", info);

		// Then: The property wins, as it does for every other property, and as the
		// Simba format already does — both come from the property map there
		assertEquals("my-project", props.projectId());
		assertEquals("other_dataset", props.datasetId());
	}

	@Test
	void testExplicitProjectIdOverridesTheUrlPath() throws SQLException {
		// Given: A URL naming one project and a property naming another
		Properties info = new Properties();
		info.setProperty("projectId", "other-project");

		// When: Parsing
		ConnectionProperties props = ConnectionUrlParser.parse("jdbc:bigquery:url-project/my_dataset", info);

		// Then: The property wins and the dataset is untouched
		assertEquals("other-project", props.projectId());
		assertEquals("my_dataset", props.datasetId());
	}

	@Test
	void testBlankPathOverridesAreIgnored() throws SQLException {
		// Given: Properties set to blank, which reads as "not set" rather than as
		// "clear the URL's value"
		Properties info = new Properties();
		info.setProperty("projectId", "  ");
		info.setProperty("datasetId", "");

		// When: Parsing
		ConnectionProperties props = ConnectionUrlParser.parse("jdbc:bigquery:my-project/my_dataset", info);

		// Then: The URL path stands
		assertEquals("my-project", props.projectId());
		assertEquals("my_dataset", props.datasetId());
	}
}

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
package vc.tbc.bq.jdbc.config;

import vc.tbc.bq.jdbc.auth.*;
import vc.tbc.bq.jdbc.transport.ProxyConfig;
import vc.tbc.bq.jdbc.transport.TlsConfig;
import vc.tbc.bq.jdbc.transport.TransportConfig;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses BigQuery JDBC connection URLs, and is also the single place that turns
 * a property bag into {@link ConnectionProperties} — see
 * {@link #fromProperties(Properties)}, which {@code BQDataSource} uses so that
 * a bean and a URL cannot disagree about what a property means.
 *
 * <p>
 * Supports two URL formats:
 *
 * <p>
 * <b>Traditional tbc-bq-jdbc Format:</b>
 *
 * <pre>{@code jdbc:bigquery:[project]/[dataset]?property1=value1&property2=value2}</pre>
 *
 * <p>
 * Examples:
 *
 * <ul>
 * <li>{@code jdbc:bigquery:my-project/my_dataset?authType=ADC}
 * <li>{@code jdbc:bigquery:my-project?authType=SERVICE_ACCOUNT&credentials=/path/to/key.json}
 * <li>{@code jdbc:bigquery:my-project/my_dataset?timeout=60&useLegacySql=false}
 * </ul>
 *
 * <p>
 * <b>Simba BigQuery Driver Format:</b>
 *
 * <pre>{@code
 * jdbc:bigquery://[Host]:[Port];ProjectId=[Project];OAuthType=[AuthValue];[Property1]=[Value1];...
 * }</pre>
 *
 * <p>
 * Examples:
 *
 * <ul>
 * <li>{@code
 *       jdbc:bigquery://https://www.googleapis.com/bigquery/v2:443;ProjectId=my-project;OAuthType=3}
 * <li>{@code
 *       jdbc:bigquery://https://www.googleapis.com/bigquery/v2:443;ProjectId=my-project;DefaultDataset=my_dataset;OAuthType=0;OAuthPvtKeyPath=/path/to/key.json}
 * <li>{@code
 *       jdbc:bigquery://https://www.googleapis.com/bigquery/v2:443;ProjectId=my-project;OAuthType=1;OAuthClientId=id;OAuthClientSecret=secret;OAuthRefreshToken=token}
 * </ul>
 *
 * @since 1.0.0
 */
public final class ConnectionUrlParser {

	private static final String URL_PREFIX = "jdbc:bigquery:";
	private static final String SIMBA_URL_PREFIX = "jdbc:bigquery://";
	private static final Pattern URL_PATTERN = Pattern.compile("^jdbc:bigquery:([^/?]+)(?:/([^?]+))?(?:\\?(.*))?$");
	private static final Pattern SIMBA_URL_PATTERN = Pattern.compile("^jdbc:bigquery://([^;]+)(?:;(.*))?$");

	private ConnectionUrlParser() {
		// Utility class
	}

	/**
	 * Parses a JDBC URL and connection info into ConnectionProperties.
	 *
	 * @param url
	 *            the JDBC URL
	 * @param info
	 *            additional connection properties
	 * @return the parsed connection properties
	 * @throws SQLException
	 *             if the URL is invalid or required properties are missing
	 */
	public static ConnectionProperties parse(String url, Properties info) throws SQLException {
		if (url == null || !url.startsWith(URL_PREFIX)) {
			throw new SQLException("Invalid BigQuery JDBC URL: " + url);
		}

		// Determine format and dispatch to appropriate parser
		if (isSimbaFormat(url)) {
			return parseSimbaUrl(url, info);
		} else {
			return parseTraditionalUrl(url, info);
		}
	}

	/**
	 * Builds connection properties from a property bag alone, with no URL.
	 *
	 * <p>
	 * This is the entry point for {@code javax.sql.DataSource}, which is configured
	 * by JavaBean setters rather than by a URL. {@code projectId} and
	 * {@code datasetId} are read from the bag instead of from a URL path;
	 * everything else is parsed exactly as it is for a URL, so the two
	 * configuration styles cannot drift in what a property means or defaults to.
	 *
	 * @param info
	 *            the connection properties, keyed by the names
	 *            {@code Driver.getPropertyInfo()} advertises
	 * @return the parsed connection properties
	 * @throws SQLException
	 *             if {@code projectId} is missing or a property is invalid
	 * @since 4.2.0
	 */
	public static ConnectionProperties fromProperties(Properties info) throws SQLException {
		Map<String, String> properties = new HashMap<>();
		if (info != null) {
			for (String key : info.stringPropertyNames()) {
				properties.put(key, info.getProperty(key));
			}
		}

		String projectId = properties.remove("projectId");
		if (projectId == null || projectId.isBlank()) {
			throw new SQLException("projectId is required: set it on the DataSource, or supply a URL");
		}
		String datasetId = blankToNull(properties.remove("datasetId"));

		return buildConnectionProperties(projectId.trim(), datasetId, properties);
	}

	/**
	 * Determines if the URL is in Simba format.
	 *
	 * @param url
	 *            the JDBC URL
	 * @return true if the URL is in Simba format, false otherwise
	 */
	private static boolean isSimbaFormat(String url) {
		return url != null && url.startsWith(SIMBA_URL_PREFIX);
	}

	/**
	 * Parses a traditional tbc-bq-jdbc format URL.
	 *
	 * @param url
	 *            the JDBC URL
	 * @param info
	 *            additional connection properties
	 * @return the parsed connection properties
	 * @throws SQLException
	 *             if the URL is invalid or required properties are missing
	 */
	private static ConnectionProperties parseTraditionalUrl(String url, Properties info) throws SQLException {
		Matcher matcher = URL_PATTERN.matcher(url);
		if (!matcher.matches()) {
			throw new SQLException("Invalid BigQuery JDBC URL format: " + url);
		}

		String projectId = matcher.group(1);
		String datasetId = matcher.group(2);
		String queryString = matcher.group(3);

		Map<String, String> properties = new HashMap<>();

		// Parse query string parameters
		if (queryString != null && !queryString.isEmpty()) {
			for (String param : queryString.split("&")) {
				int idx = param.indexOf('=');
				if (idx > 0) {
					String key = URLDecoder.decode(param.substring(0, idx), StandardCharsets.UTF_8);
					String value = URLDecoder.decode(param.substring(idx + 1), StandardCharsets.UTF_8);
					properties.put(key, value);
				}
			}
		}

		// Merge with Properties object (Properties override URL params)
		if (info != null) {
			for (String key : info.stringPropertyNames()) {
				properties.put(key, info.getProperty(key));
			}
		}

		// The project and dataset are the URL path, but an explicit property still
		// overrides them — the same rule as every other property above, and the same
		// rule the Simba path already applies, where both come from the property map
		// to begin with. Without this a caller holding a URL and a property bag (an
		// application server, a DataSource) could not change the dataset at all: the
		// key was read on one path and silently dropped on the other.
		String projectOverride = blankToNull(properties.remove("projectId"));
		if (projectOverride != null) {
			projectId = projectOverride.trim();
		}
		String datasetOverride = blankToNull(properties.remove("datasetId"));
		if (datasetOverride != null) {
			datasetId = datasetOverride;
		}

		return buildConnectionProperties(projectId, datasetId, properties);
	}

	/** Treats an unset property and a property set to whitespace alike. */
	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value;
	}

	/**
	 * Parses a Simba BigQuery JDBC driver format URL.
	 *
	 * @param url
	 *            the JDBC URL in Simba format
	 * @param info
	 *            additional connection properties
	 * @return the parsed connection properties
	 * @throws SQLException
	 *             if the URL is invalid or required properties are missing
	 */
	private static ConnectionProperties parseSimbaUrl(String url, Properties info) throws SQLException {
		Matcher matcher = SIMBA_URL_PATTERN.matcher(url);
		if (!matcher.matches()) {
			throw new SQLException(
					"Invalid Simba BigQuery JDBC URL format. Expected: jdbc:bigquery://host:port;ProjectId=...");
		}

		// Extract host:port
		String hostPort = matcher.group(1);
		String paramString = matcher.group(2);

		Map<String, String> simbaProperties = new HashMap<>();

		// Parse host and port
		String host = null;
		Integer port = null;
		if (hostPort != null && !hostPort.isEmpty()) {
			int colonIdx = hostPort.lastIndexOf(':');
			if (colonIdx > 0) {
				host = hostPort.substring(0, colonIdx);
				try {
					port = Integer.parseInt(hostPort.substring(colonIdx + 1));
				} catch (NumberFormatException e) {
					throw new SQLException("Invalid port number in URL: " + hostPort.substring(colonIdx + 1), e);
				}
			} else {
				host = hostPort;
			}
		}

		// Parse semicolon-separated parameters
		if (paramString != null && !paramString.isEmpty()) {
			// Remove trailing semicolon if present
			String params = paramString.endsWith(";")
					? paramString.substring(0, paramString.length() - 1)
					: paramString;

			for (String param : params.split(";")) {
				int idx = param.indexOf('=');
				if (idx > 0) {
					String key = param.substring(0, idx).trim();
					String value = param.substring(idx + 1).trim();
					simbaProperties.put(key, value);
				}
			}
		}

		// Map Simba properties to tbc-bq-jdbc properties
		Map<String, String> properties = mapSimbaProperties(simbaProperties);

		// Add host and port to properties only for non-standard endpoints. Standard
		// Simba URLs use "https://www.googleapis.com/bigquery/v2:443", which is the
		// Google BigQuery API — the SDK manages that connection natively.
		if (host != null && !host.startsWith("http://") && !host.startsWith("https://")) {
			properties.put("host", host);
			if (port != null) {
				properties.put("port", String.valueOf(port));
			}
		}

		// Merge with Properties object (Properties override URL params).
		// Apply Simba property mapping so IntelliJ-style info properties (e.g.
		// ProjectId,
		// OAuthType) are correctly translated to native names.
		if (info != null) {
			Map<String, String> infoProps = new HashMap<>();
			for (String key : info.stringPropertyNames()) {
				infoProps.put(key, info.getProperty(key));
			}
			properties.putAll(mapSimbaProperties(infoProps));
		}

		// Extract projectId and datasetId from properties
		String projectId = properties.remove("projectId");
		if (projectId == null) {
			throw new SQLException("Missing required property 'ProjectId' in Simba URL");
		}
		String datasetId = properties.remove("datasetId");

		return buildConnectionProperties(projectId, datasetId, properties);
	}

	/**
	 * Maps Simba driver property names to tbc-bq-jdbc property names.
	 *
	 * @param simbaProperties
	 *            the Simba properties map
	 * @return the mapped tbc-bq-jdbc properties
	 * @throws SQLException
	 *             if property mapping fails or required properties are missing
	 */
	private static Map<String, String> mapSimbaProperties(Map<String, String> simbaProperties) throws SQLException {
		Map<String, String> properties = new HashMap<>();

		for (Map.Entry<String, String> entry : simbaProperties.entrySet()) {
			String key = entry.getKey();
			String value = entry.getValue();

			switch (key) {
				case "ProjectId" -> properties.put("projectId", value);
				case "DefaultDataset" -> properties.put("datasetId", value);
				case "OAuthType" -> {
					String authType = parseOAuthType(value);
					properties.put("authType", authType);
				}
				case "OAuthPvtKeyPath" -> properties.put("credentials", value);
				case "OAuthClientId" -> properties.put("clientId", value);
				case "OAuthClientSecret" -> properties.put("clientSecret", value);
				case "OAuthRefreshToken" -> properties.put("refreshToken", value);
				case "OAuthAccessToken" -> properties.put("accessToken", value);
				case "Timeout" -> properties.put("timeout", value);
				case "MaxResults" -> properties.put("maxResults", value);
				case "UseLegacySQL" -> properties.put("useLegacySql", value);
				case "Location" -> properties.put("location", value);
				case "DatasetProjectId" -> properties.put("datasetProjectId", value);
				case "EnableSessions" -> properties.put("enableSessions", value);
				// Named as Simba names them, because someone migrating already has
				// these four in a connection string and a proxy is the one setting they
				// cannot work around by editing the driver's own properties.
				case "ProxyHost" -> properties.put("proxyHost", value);
				case "ProxyPort" -> properties.put("proxyPort", value);
				case "ProxyUid" -> properties.put("proxyUser", value);
				case "ProxyPwd" -> properties.put("proxyPassword", value);
				case "SSLTrustStore" -> properties.put("trustStore", value);
				case "SSLTrustStorePwd" -> properties.put("trustStorePassword", value);
				case "SSLTrustStoreType" -> properties.put("trustStoreType", value);
				case "SSLTrustStoreProvider" -> properties.put("trustStoreProvider", value);
				default -> properties.put(key, value); // Pass through — handles native tbc-bq-jdbc property names
			}
		}

		return properties;
	}

	/**
	 * Converts Simba OAuthType numeric value to tbc-bq-jdbc authType string.
	 *
	 * <p>
	 * Note: OAuthType=4 (External Account) is mapped to WORKLOAD identity
	 * federation. Users requiring WORKFORCE identity should override via
	 * Properties: {@code info.setProperty("authType", "WORKFORCE")}
	 *
	 * @param oauthType
	 *            the Simba OAuthType value
	 * @return the tbc-bq-jdbc authType string
	 * @throws SQLException
	 *             if the OAuthType is invalid or unsupported
	 */
	private static String parseOAuthType(String oauthType) throws SQLException {
		return switch (oauthType) {
			case "0" -> "SERVICE_ACCOUNT"; // Service Account
			case "1" -> "USER_OAUTH"; // User Account
			case "2" -> "ACCESS_TOKEN"; // Pre-generated access token
			case "3" -> "ADC"; // Application Default Credentials
			case "4" -> // External Account - could be WORKFORCE or WORKLOAD
				// Default to WORKLOAD for now; users can override via Properties object if
				// needed
				"WORKLOAD";
			default -> throw new SQLException("Invalid OAuthType value '" + oauthType
					+ "'. Supported: 0 (Service Account), 1 (User), 3 (ADC), 4 (External Account)");
		};
	}

	private static ConnectionProperties buildConnectionProperties(String projectId, String datasetId,
			Map<String, String> properties) throws SQLException {

		// Parse host and port (a non-default BigQuery endpoint, e.g. a proxy or
		// Private Service Connect)
		String host = properties.get("host");
		Integer port = parseInteger(properties, "port");

		// Parse authType (required)
		String authTypeStr = properties.get("authType");
		if (authTypeStr == null) {
			// A host no longer changes the default. It used to select a
			// fabricated-token auth type (removed in 2.0.0, see #144), so anyone
			// pointing the driver at a proxy or a private endpoint silently got that
			// instead of their own credentials, and only found out when the request
			// was rejected. A host now means "reach BigQuery here", not
			// "authenticate differently".
			authTypeStr = "ADC";
		}

		AuthType authType = applyImpersonation(parseAuthType(authTypeStr, properties), properties);

		// Parse optional properties
		Integer timeoutSeconds = parseInteger(properties, "timeout");
		Long maxResults = parseLong(properties, "maxResults");
		boolean useLegacySql = parseBoolean(properties, "useLegacySql", false);
		String location = properties.get("location");
		Map<String, String> labels = parseLabels(properties.get("labels"));
		Integer pageSize = parseInteger(properties, "pageSize");
		String useStorageApi = properties.get("useStorageApi");
		boolean enableSessions = parseBoolean(properties, "enableSessions", false);
		Integer connectionTimeout = parseInteger(properties, "connectionTimeout");
		Integer retryCount = parseInteger(properties, "retryCount");
		Long maxBillingBytes = parseLong(properties, "maxBillingBytes");
		String datasetProjectId = properties.get("datasetProjectId");
		Integer metadataCacheTtl = parseInteger(properties, "metadataCacheTtl");
		Boolean metadataCacheEnabled = parseBooleanObject(properties, "metadataCacheEnabled");
		Boolean metadataLazyLoad = parseBooleanObject(properties, "metadataLazyLoad");
		Boolean enableQueryCostEstimation = parseBooleanObject(properties, "enableQueryCostEstimation");
		Boolean nativeComplexTypes = parseBooleanObject(properties, "nativeComplexTypes");
		Integer metadataCacheMaxRows = parseInteger(properties, "metadataCacheMaxRows");
		BigDecimal queryPricePerTiB = parseBigDecimal(properties, "queryPricePerTiB");
		Boolean metadataIncludeDescriptions = parseBooleanObject(properties, "metadataIncludeDescriptions");
		Boolean collapseShardedTables = parseBooleanObject(properties, "collapseShardedTables");
		Integer batchLoadThreshold = parseInteger(properties, "batchLoadThreshold");
		Boolean includeInformationSchema = parseBooleanObject(properties, "includeInformationSchema");
		List<String> additionalProjects = parseProjectList(properties.get("additionalProjects"));
		Boolean includeStructFields = parseBooleanObject(properties, "includeStructFields");
		Boolean metadataJobCreationOptional = parseBooleanObject(properties, "metadataJobCreationOptional");
		TransportConfig transport = parseTransport(properties);

		return new ConnectionProperties(projectId, datasetId, datasetProjectId, authType, host, port, timeoutSeconds,
				maxResults, useLegacySql, location, labels, pageSize, useStorageApi, enableSessions, connectionTimeout,
				retryCount, maxBillingBytes, metadataCacheTtl, metadataCacheEnabled, metadataLazyLoad,
				enableQueryCostEstimation, nativeComplexTypes, metadataCacheMaxRows, queryPricePerTiB,
				metadataIncludeDescriptions, collapseShardedTables, batchLoadThreshold, includeInformationSchema,
				additionalProjects, includeStructFields, metadataJobCreationOptional, transport);
	}

	/**
	 * Resolves how a connection reaches Google: its proxy and its truststore.
	 *
	 * <p>
	 * Both resolvers throw {@link IllegalArgumentException} for a configuration
	 * that cannot be honoured — a port outside the range, a password with no
	 * username, a store password with no store. Those are all connection-string
	 * mistakes, so they surface here as {@link SQLException} like every other bad
	 * property rather than as an unchecked exception out of
	 * {@code DriverManager.getConnection}.
	 *
	 * <p>
	 * A truststore that cannot be <em>read</em> is a different matter and is not
	 * checked here: that is I/O, it happens when the transport is built, and
	 * failing at parse time would mean reading the file once per parse.
	 */
	private static TransportConfig parseTransport(Map<String, String> properties) throws SQLException {
		Integer proxyPort = parseInteger(properties, "proxyPort");
		try {
			ProxyConfig proxy = ProxyConfig.resolve(properties.get("proxyHost"), proxyPort, properties.get("proxyUser"),
					properties.get("proxyPassword"));
			TlsConfig tls = TlsConfig.resolve(properties.get("trustStore"), properties.get("trustStorePassword"),
					properties.get("trustStoreType"), properties.get("trustStoreProvider"));
			return TransportConfig.of(proxy, tls);
		} catch (IllegalArgumentException e) {
			throw new SQLException("Invalid transport configuration: " + e.getMessage(), e);
		}
	}

	private static AuthType parseAuthType(String authTypeStr, Map<String, String> properties) throws SQLException {
		return switch (authTypeStr.toUpperCase(Locale.ROOT)) {
			case "SERVICE_ACCOUNT" -> {
				String credentials = properties.get("credentials");
				if (credentials == null) {
					throw new SQLException("credentials property required for SERVICE_ACCOUNT authentication");
				}
				yield new ServiceAccountAuth(credentials);
			}
			case "ADC" -> new ApplicationDefaultAuth();
			case "ACCESS_TOKEN" -> {
				String accessToken = properties.get("accessToken");
				if (accessToken == null || accessToken.isBlank()) {
					throw new SQLException("accessToken property required for ACCESS_TOKEN authentication");
				}
				yield new AccessTokenAuth(accessToken, parseInstant(properties, "accessTokenExpiry"));
			}
			case "USER_OAUTH" -> {
				String clientId = properties.get("clientId");
				String clientSecret = properties.get("clientSecret");
				String refreshToken = properties.get("refreshToken");
				if (clientId == null || clientSecret == null || refreshToken == null) {
					throw new SQLException(
							"clientId, clientSecret, and refreshToken required for USER_OAUTH authentication");
				}
				yield new UserOAuthAuth(clientId, clientSecret, refreshToken);
			}
			case "WORKFORCE" -> {
				String credentialConfigFile = properties.get("credentialConfigFile");
				if (credentialConfigFile == null) {
					throw new SQLException("credentialConfigFile required for WORKFORCE authentication");
				}
				yield new WorkforceIdentityAuth(credentialConfigFile);
			}
			case "WORKLOAD" -> {
				String credentialConfigFile = properties.get("credentialConfigFile");
				if (credentialConfigFile == null) {
					throw new SQLException("credentialConfigFile required for WORKLOAD authentication");
				}
				yield new WorkloadIdentityAuth(credentialConfigFile);
			}
			default -> throw new SQLException("Unsupported authentication type: " + authTypeStr);
		};
	}

	/**
	 * Wraps the resolved authentication in service account impersonation, when
	 * {@code impersonateServiceAccount} asks for it.
	 *
	 * <p>
	 * Impersonation is an orthogonal property rather than an {@code authType} value
	 * because it always needs a source identity: expressing it as a sixth
	 * {@code authType} would need a second {@code sourceAuthType} dimension to say
	 * what that identity is. Layering it composes with all five for free, and
	 * matches {@code gcloud --impersonate-service-account}.
	 *
	 * @param authType
	 *            the authentication providing the source identity
	 * @param properties
	 *            the parsed connection properties
	 * @return {@code authType} wrapped for impersonation, or unchanged when no
	 *         target was named
	 * @throws SQLException
	 *             if the impersonation properties are invalid
	 */
	private static AuthType applyImpersonation(AuthType authType, Map<String, String> properties) throws SQLException {
		String target = properties.get("impersonateServiceAccount");
		String delegatesStr = properties.get("impersonateDelegates");

		if (target == null || target.isBlank()) {
			// Rejected rather than ignored: a delegation chain with no target does
			// nothing, and silently connecting as the source identity is the one
			// outcome the caller who set this did not want.
			if (delegatesStr != null && !delegatesStr.isBlank()) {
				throw new SQLException("impersonateDelegates requires impersonateServiceAccount");
			}
			return authType;
		}

		List<String> delegates = new ArrayList<>();
		if (delegatesStr != null) {
			for (String delegate : delegatesStr.split(",")) {
				String trimmed = delegate.trim();
				if (!trimmed.isEmpty()) {
					delegates.add(trimmed);
				}
			}
		}

		try {
			return new ImpersonatedAuth(authType, target.trim(), delegates);
		} catch (IllegalArgumentException e) {
			throw new SQLException("Invalid impersonation configuration: " + e.getMessage(), e);
		}
	}

	/**
	 * Parses an ISO-8601 instant, such as {@code 2026-07-30T20:00:00Z}.
	 *
	 * <p>
	 * ISO-8601 rather than epoch seconds because a connection string is read by
	 * people: a wrong instant is obvious where a wrong epoch is not. It also
	 * carries its own offset, so there is no ambiguity about whose clock it names.
	 * Neither form needs URL-encoding in either URL format.
	 */
	private static Instant parseInstant(Map<String, String> properties, String key) throws SQLException {
		String value = properties.get(key);
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return Instant.parse(value.trim());
		} catch (DateTimeParseException e) {
			throw new SQLException(
					"Invalid ISO-8601 instant for " + key + ": " + value + " (expected e.g. 2026-07-30T20:00:00Z)", e);
		}
	}

	private static Integer parseInteger(Map<String, String> properties, String key) throws SQLException {
		String value = properties.get(key);
		if (value == null) {
			return null;
		}
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException e) {
			throw new SQLException("Invalid integer value for " + key + ": " + value, e);
		}
	}

	private static Long parseLong(Map<String, String> properties, String key) throws SQLException {
		String value = properties.get(key);
		if (value == null) {
			return null;
		}
		try {
			return Long.parseLong(value);
		} catch (NumberFormatException e) {
			throw new SQLException("Invalid long value for " + key + ": " + value, e);
		}
	}

	/**
	 * Parses a decimal property, rejecting a blank value rather than treating it as
	 * zero: {@code queryPricePerTiB=} reads as "I did not set this", and a zero
	 * rate would price every query at nothing.
	 */
	private static BigDecimal parseBigDecimal(Map<String, String> properties, String key) throws SQLException {
		String value = properties.get(key);
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return new BigDecimal(value.trim());
		} catch (NumberFormatException e) {
			throw new SQLException("Invalid decimal value for " + key + ": " + value, e);
		}
	}

	private static boolean parseBoolean(Map<String, String> properties, String key, boolean defaultValue) {
		String value = properties.get(key);
		if (value == null) {
			return defaultValue;
		}
		return Boolean.parseBoolean(value);
	}

	private static Boolean parseBooleanObject(Map<String, String> properties, String key) {
		String value = properties.get(key);
		if (value == null) {
			return null;
		}
		return Boolean.parseBoolean(value);
	}

	/**
	 * Splits a comma-separated project list, ignoring blank entries.
	 *
	 * <p>
	 * Names are not validated here. A project id the driver would reject is worth
	 * an error when it is used, not when it is listed alongside working ones —
	 * {@code getCatalogs()} reporting a name nothing can query is a smaller problem
	 * than a connection that will not open.
	 */
	private static List<String> parseProjectList(String value) {
		if (value == null || value.isBlank()) {
			return List.of();
		}
		List<String> projects = new ArrayList<>();
		for (String project : value.split(",")) {
			String trimmed = project.trim();
			if (!trimmed.isEmpty()) {
				projects.add(trimmed);
			}
		}
		return projects;
	}

	private static Map<String, String> parseLabels(String labelsStr) {
		if (labelsStr == null || labelsStr.isEmpty()) {
			return Map.of();
		}
		Map<String, String> labels = new HashMap<>();
		for (String label : labelsStr.split(",")) {
			int idx = label.indexOf('=');
			if (idx > 0) {
				String key = label.substring(0, idx).trim();
				String value = label.substring(idx + 1).trim();
				labels.put(key, value);
			}
		}
		return labels;
	}
}

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
package com.twobearcapital.bigquery.jdbc.config;

import com.twobearcapital.bigquery.jdbc.auth.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses BigQuery JDBC connection URLs.
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
	private static final Pattern SIMBA_URL_PATTERN = Pattern.compile("^jdbc:bigquery://([^;]+);(.*)$");

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

		return buildConnectionProperties(projectId, datasetId, properties);
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
					throw new SQLException("Invalid port number in URL: " + hostPort.substring(colonIdx + 1));
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

		// Add host and port to properties
		if (host != null) {
			properties.put("host", host);
		}
		if (port != null) {
			properties.put("port", String.valueOf(port));
		}

		// Merge with Properties object (Properties override URL params)
		if (info != null) {
			for (String key : info.stringPropertyNames()) {
				properties.put(key, info.getProperty(key));
			}
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
					String authType = parseOAuthType(value, simbaProperties);
					properties.put("authType", authType);
				}
				case "OAuthPvtKeyPath" -> properties.put("credentials", value);
				case "OAuthClientId" -> properties.put("clientId", value);
				case "OAuthClientSecret" -> properties.put("clientSecret", value);
				case "OAuthRefreshToken" -> properties.put("refreshToken", value);
				case "Timeout" -> properties.put("timeout", value);
				case "MaxResults" -> properties.put("maxResults", value);
				case "UseLegacySQL" -> properties.put("useLegacySql", value);
				case "Location" -> properties.put("location", value);
				case "DatasetProjectId" -> properties.put("datasetProjectId", value);
				default -> {
					// Unknown properties are ignored for forward compatibility
					// Could add DEBUG logging here if needed
				}
			}
		}

		return properties;
	}

	/**
	 * Converts Simba OAuthType numeric value to tbc-bq-jdbc authType string.
	 *
	 * @param oauthType
	 *            the Simba OAuthType value
	 * @param simbaProperties
	 *            all Simba properties (for context-dependent mapping)
	 * @return the tbc-bq-jdbc authType string
	 * @throws SQLException
	 *             if the OAuthType is invalid or unsupported
	 */
	private static String parseOAuthType(String oauthType, Map<String, String> simbaProperties) throws SQLException {
		return switch (oauthType) {
			case "0" -> "SERVICE_ACCOUNT"; // Service Account
			case "1" -> "USER_OAUTH"; // User Account
			case "2" -> throw new SQLException(
					"Pre-generated access tokens (OAuthType=2) not supported. Use Service Account (0) or ADC (3)");
			case "3" -> "ADC"; // Application Default Credentials
			case "4" -> {
				// External Account - could be WORKFORCE or WORKLOAD
				// Default to WORKLOAD for now; users can override via Properties object if
				// needed
				yield "WORKLOAD";
			}
			default -> throw new SQLException("Invalid OAuthType value '" + oauthType
					+ "'. Supported: 0 (Service Account), 1 (User), 3 (ADC), 4 (External Account)");
		};
	}

	private static ConnectionProperties buildConnectionProperties(String projectId, String datasetId,
			Map<String, String> properties) throws SQLException {

		// Parse host and port (for emulator support)
		String host = properties.get("host");
		Integer port = parseInteger(properties, "port");

		// Parse authType (required)
		String authTypeStr = properties.get("authType");
		if (authTypeStr == null) {
			// If host is specified, default to EMULATOR auth, otherwise ADC
			authTypeStr = (host != null) ? "EMULATOR" : "ADC";
		}

		AuthType authType = parseAuthType(authTypeStr, properties);

		// Parse optional properties
		Integer timeoutSeconds = parseInteger(properties, "timeout");
		Long maxResults = parseLong(properties, "maxResults");
		boolean useLegacySql = parseBoolean(properties, "useLegacySql", false);
		String location = properties.get("location");
		Map<String, String> labels = parseLabels(properties.get("labels"));
		JobCreationMode jobCreationMode = parseJobCreationMode(properties.get("jobCreationMode"));
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

		return new ConnectionProperties(projectId, datasetId, datasetProjectId, authType, host, port, timeoutSeconds,
				maxResults, useLegacySql, location, labels, jobCreationMode, pageSize, useStorageApi, enableSessions,
				connectionTimeout, retryCount, maxBillingBytes, metadataCacheTtl, metadataCacheEnabled,
				metadataLazyLoad);
	}

	private static AuthType parseAuthType(String authTypeStr, Map<String, String> properties) throws SQLException {
		return switch (authTypeStr.toUpperCase()) {
			case "EMULATOR" -> new EmulatorAuth();
			case "SERVICE_ACCOUNT" -> {
				String credentials = properties.get("credentials");
				if (credentials == null) {
					throw new SQLException("credentials property required for SERVICE_ACCOUNT authentication");
				}
				yield new ServiceAccountAuth(credentials);
			}
			case "ADC" -> new ApplicationDefaultAuth();
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

	private static JobCreationMode parseJobCreationMode(String mode) throws SQLException {
		if (mode == null) {
			return null; // Use default from ConnectionProperties
		}
		try {
			return JobCreationMode.valueOf(mode.toUpperCase());
		} catch (IllegalArgumentException e) {
			throw new SQLException("Invalid jobCreationMode: " + mode, e);
		}
	}
}

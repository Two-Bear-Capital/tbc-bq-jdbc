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
 * <p>URL format: {@code jdbc:bigquery:[project]/[dataset]?property1=value1&property2=value2}
 *
 * <p>Examples:
 *
 * <ul>
 *   <li>{@code jdbc:bigquery:my-project/my_dataset?authType=ADC}
 *   <li>{@code jdbc:bigquery:my-project?authType=SERVICE_ACCOUNT&credentials=/path/to/key.json}
 *   <li>{@code jdbc:bigquery:my-project/my_dataset?timeout=60&useLegacySql=false}
 * </ul>
 *
 * @since 1.0.0
 */
public final class ConnectionUrlParser {

  private static final String URL_PREFIX = "jdbc:bigquery:";
  private static final Pattern URL_PATTERN =
      Pattern.compile("^jdbc:bigquery:([^/?]+)(?:/([^?]+))?(?:\\?(.*))?$");

  private ConnectionUrlParser() {
    // Utility class
  }

  /**
   * Parses a JDBC URL and connection info into ConnectionProperties.
   *
   * @param url the JDBC URL
   * @param info additional connection properties
   * @return the parsed connection properties
   * @throws SQLException if the URL is invalid or required properties are missing
   */
  public static ConnectionProperties parse(String url, Properties info) throws SQLException {
    if (url == null || !url.startsWith(URL_PREFIX)) {
      throw new SQLException("Invalid BigQuery JDBC URL: " + url);
    }

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

  private static ConnectionProperties buildConnectionProperties(
      String projectId, String datasetId, Map<String, String> properties) throws SQLException {

    // Parse authType (required)
    String authTypeStr = properties.get("authType");
    if (authTypeStr == null) {
      authTypeStr = "ADC"; // Default to Application Default Credentials
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

    return new ConnectionProperties(
        projectId,
        datasetId,
        datasetProjectId,
        authType,
        timeoutSeconds,
        maxResults,
        useLegacySql,
        location,
        labels,
        jobCreationMode,
        pageSize,
        useStorageApi,
        enableSessions,
        connectionTimeout,
        retryCount,
        maxBillingBytes);
  }

  private static AuthType parseAuthType(String authTypeStr, Map<String, String> properties)
      throws SQLException {
    return switch (authTypeStr.toUpperCase()) {
      case "SERVICE_ACCOUNT" -> {
        String credentials = properties.get("credentials");
        if (credentials == null) {
          throw new SQLException(
              "credentials property required for SERVICE_ACCOUNT authentication");
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

  private static Integer parseInteger(Map<String, String> properties, String key)
      throws SQLException {
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

  private static boolean parseBoolean(
      Map<String, String> properties, String key, boolean defaultValue) {
    String value = properties.get(key);
    if (value == null) {
      return defaultValue;
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

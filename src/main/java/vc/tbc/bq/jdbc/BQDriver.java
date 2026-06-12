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

import org.slf4j.LoggerFactory;
import vc.tbc.bq.jdbc.config.ConnectionProperties;
import vc.tbc.bq.jdbc.config.ConnectionUrlParser;

import java.sql.*;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * JDBC driver for Google BigQuery.
 *
 * <p>
 * Supports two URL formats:
 *
 * <p>
 * <b>Traditional Format:</b>
 *
 * <pre>{@code
 * jdbc:bigquery:[project]/[dataset]?property1=value1&property2=value2
 * }</pre>
 *
 * <p>
 * Example:
 *
 * <pre>{@code
 * String url = "jdbc:bigquery:my-project/my_dataset?authType=ADC";
 * Connection conn = DriverManager.getConnection(url);
 * }</pre>
 *
 * <p>
 * <b>Simba Format:</b>
 *
 * <pre>{@code
 * jdbc:bigquery://[Host]:[Port];ProjectId=[Project];OAuthType=[AuthValue];...
 * }</pre>
 *
 * <p>
 * Example:
 *
 * <pre>{@code
 * String url = "jdbc:bigquery://https://www.googleapis.com/bigquery/v2:443;ProjectId=my-project;OAuthType=3";
 * Connection conn = DriverManager.getConnection(url);
 * }</pre>
 *
 * @since 1.0.0
 */
public final class BQDriver implements Driver {

	private static final org.slf4j.Logger logger = LoggerFactory.getLogger(BQDriver.class);
	private static final String URL_PREFIX = "jdbc:bigquery:";

	static {
		try {
			DriverManager.registerDriver(new BQDriver());
			logger.info("BigQuery JDBC Driver registered: {}", DriverVersion.getFullVersionInfo());
		} catch (SQLException e) {
			logger.error("Failed to register BigQuery JDBC Driver", e);
			throw new RuntimeException("Failed to register BigQuery JDBC Driver", e);
		}
	}

	/** Default constructor. */
	public BQDriver() {
		// Required for ServiceLoader
	}

	@Override
	public Connection connect(String url, Properties info) throws SQLException {
		if (!acceptsURL(url)) {
			return null;
		}

		logger.debug("Connecting to BigQuery with URL: {}", url);

		try {
			ConnectionProperties properties = ConnectionUrlParser.parse(url, info);
			return new BQConnection(properties);
		} catch (SQLException e) {
			logger.error("Failed to create BigQuery connection", e);
			throw e;
		}
	}

	@Override
	public boolean acceptsURL(String url) {
		return url != null && url.startsWith(URL_PREFIX);
	}

	@Override
	public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
		java.util.List<DriverPropertyInfo> props = new java.util.ArrayList<>();

		props.add(prop(info, "authType", "ADC",
				"Authentication method: ADC (Application Default Credentials), SERVICE_ACCOUNT, USER_OAUTH, WORKFORCE, WORKLOAD",
				false, new String[]{"ADC", "SERVICE_ACCOUNT", "USER_OAUTH", "WORKFORCE", "WORKLOAD"}));

		props.add(prop(info, "credentials", "",
				"Path to service account JSON key file (required for SERVICE_ACCOUNT auth)", false, null));

		props.add(prop(info, "credentialConfigFile", "",
				"Path to external account credential config file (required for WORKFORCE or WORKLOAD auth)", false,
				null));

		props.add(prop(info, "clientId", "", "OAuth 2.0 client ID (required for USER_OAUTH auth)", false, null));

		props.add(
				prop(info, "clientSecret", "", "OAuth 2.0 client secret (required for USER_OAUTH auth)", false, null));

		props.add(
				prop(info, "refreshToken", "", "OAuth 2.0 refresh token (required for USER_OAUTH auth)", false, null));

		props.add(prop(info, "location", "",
				"BigQuery processing location (e.g., US, EU, us-central1). Leave blank to use the dataset's location.",
				false, null));

		props.add(prop(info, "timeout", String.valueOf(ConnectionProperties.DEFAULT_TIMEOUT_SECONDS),
				"Query execution timeout in seconds", false, null));

		props.add(prop(info, "connectionTimeout", String.valueOf(ConnectionProperties.DEFAULT_CONNECTION_TIMEOUT),
				"Connection establishment timeout in seconds", false, null));

		props.add(prop(info, "retryCount", String.valueOf(ConnectionProperties.DEFAULT_RETRY_COUNT),
				"Number of retry attempts for transient errors", false, null));

		props.add(prop(info, "pageSize", String.valueOf(ConnectionProperties.DEFAULT_PAGE_SIZE),
				"Number of rows to fetch per page when iterating large result sets", false, null));

		props.add(prop(info, "metadataCacheEnabled", "true",
				"Cache schema introspection results to speed up IntelliJ IDEA's database tree", false,
				new String[]{"true", "false"}));

		props.add(prop(info, "metadataCacheTtl", String.valueOf(ConnectionProperties.DEFAULT_METADATA_CACHE_TTL),
				"How long (seconds) to keep metadata in the cache before re-fetching", false, null));

		props.add(prop(info, "metadataLazyLoad", "false",
				"Skip loading all columns on connect; IntelliJ loads them on-demand as you expand tables (faster initial connect for large projects)",
				false, new String[]{"true", "false"}));

		props.add(prop(info, "useStorageApi", "auto", "BigQuery Storage Read API mode for large result sets", false,
				new String[]{"auto", "true", "false"}));

		props.add(prop(info, "enableSessions", "false",
				"Enable BigQuery sessions to support transactions and temporary tables", false,
				new String[]{"true", "false"}));

		props.add(prop(info, "jobCreationMode", "REQUIRED",
				"REQUIRED always creates a query job; OPTIONAL may skip it for small queries", false,
				new String[]{"REQUIRED", "OPTIONAL"}));

		props.add(prop(info, "useLegacySql", "false",
				"Use BigQuery legacy SQL dialect instead of standard SQL (GoogleSQL)", false,
				new String[]{"true", "false"}));

		props.add(prop(info, "enableQueryCostEstimation", "false",
				"Run a dry-run before each query to estimate cost; estimates are attached as SQLWarnings", false,
				new String[]{"true", "false"}));

		props.add(prop(info, "maxResults", "", "Maximum number of query result rows to return (blank = unlimited)",
				false, null));

		props.add(prop(info, "maxBillingBytes", "",
				"Maximum bytes billed per query; queries exceeding this limit are rejected (blank = unlimited)", false,
				null));

		props.add(prop(info, "labels", "",
				"Comma-separated BigQuery job labels in key=value format (e.g., env=prod,team=data)", false, null));

		props.add(prop(info, "datasetId", "", "Default dataset name used for unqualified table references in queries",
				false, null));

		props.add(prop(info, "datasetProjectId", "",
				"Project ID for the default dataset when it differs from the connection project", false, null));

		props.add(prop(info, "useDestinationTables", "false",
				"Write SELECT query results to destination tables (useful for BigQuery emulator compatibility)", false,
				new String[]{"true", "false"}));

		props.add(prop(info, "nativeComplexTypes", "false",
				"Return ARRAY and STRUCT as native JDBC Array/Struct objects instead of JSON strings", false,
				new String[]{"true", "false"}));

		return props.toArray(new DriverPropertyInfo[0]);
	}

	private static DriverPropertyInfo prop(Properties info, String name, String defaultValue, String description,
			boolean required, String[] choices) {
		String value = (info != null && info.containsKey(name)) ? info.getProperty(name) : defaultValue;
		DriverPropertyInfo p = new DriverPropertyInfo(name, value);
		p.description = description;
		p.required = required;
		p.choices = choices;
		return p;
	}

	@Override
	public int getMajorVersion() {
		return DriverVersion.getMajorVersion();
	}

	@Override
	public int getMinorVersion() {
		return DriverVersion.getMinorVersion();
	}

	@Override
	public boolean jdbcCompliant() {
		// BigQuery has limitations that prevent full JDBC compliance:
		// - No traditional transaction support outside of sessions
		// - Limited DML operations
		// - No UPDATE/DELETE with traditional syntax (requires DML)
		// - No stored procedures
		// - No savepoints
		return false;
	}

	@Override
	public Logger getParentLogger() throws SQLFeatureNotSupportedException {
		throw new SQLFeatureNotSupportedException("getParentLogger not supported");
	}
}

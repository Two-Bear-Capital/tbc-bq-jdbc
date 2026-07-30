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

import vc.tbc.bq.jdbc.base.BaseJdbcWrapper;
import vc.tbc.bq.jdbc.config.ConnectionProperties;
import vc.tbc.bq.jdbc.config.ConnectionUrlParser;

import javax.naming.NamingException;
import javax.naming.Reference;
import javax.naming.Referenceable;
import javax.naming.StringRefAddr;
import javax.sql.DataSource;

import java.io.PrintWriter;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * A {@link DataSource} for BigQuery, configured by JavaBean setters instead of
 * by a JDBC URL.
 *
 * <p>
 * This is what Spring's {@code DataSourceAutoConfiguration}, JPA/Hibernate,
 * JNDI lookups in an application server, and connection pools such as HikariCP
 * expect to be handed. Every setting reachable through a connection URL is
 * reachable here, under the same name:
 *
 * <pre>{@code
 * BQDataSource ds = new BQDataSource();
 * ds.setProjectId("my-project");
 * ds.setDatasetId("my_dataset");
 * ds.setAuthType("ADC");
 *
 * try (Connection conn = ds.getConnection()) {
 *     ...
 * }
 * }</pre>
 *
 * <p>
 * A URL may be used instead of, or alongside, the setters. Any property set
 * explicitly overrides the same property in the URL:
 *
 * <pre>{@code
 * BQDataSource ds = new BQDataSource("jdbc:bigquery:my-project/my_dataset?authType=ADC");
 * ds.setTimeout(60);
 * }</pre>
 *
 * <p>
 * <b>Setters accumulate into a property bag, they do not each hold a field.</b>
 * {@link ConnectionUrlParser} remains the only code that knows a property's
 * type, default and validation rules, so the URL and the bean cannot disagree
 * about what {@code metadataCacheTtl} defaults to or which values
 * {@code useStorageApi} accepts. A setter here is a rename, not a second
 * implementation. {@code BQDataSourcePropertyCoverageTest} fails if a property
 * the driver advertises has no setter, which is what keeps the list in step by
 * hand rather than by generation.
 *
 * <p>
 * Values are validated when {@link #getConnection()} is called, not when a
 * setter runs — a half-configured bean is normal while a container is still
 * populating it, and a setter that threw would depend on the order the
 * container chose.
 *
 * <p>
 * <b>Pooling is deliberately not implemented.</b> There is no
 * {@code ConnectionPoolDataSource} or {@code PooledConnection}: HikariCP,
 * Tomcat JDBC and Spring all pool {@link Connection} directly, and the driver's
 * deferred {@code BEGIN TRANSACTION} exists precisely so that an external pool
 * toggling auto-commit costs no BigQuery jobs. Wrap this class in HikariCP
 * rather than looking for a pool inside it.
 *
 * <p>
 * Instances are {@link Serializable} and {@link Referenceable}, so an
 * application server can bind one into JNDI; see {@link BQDataSourceFactory}.
 * Configure the bean before sharing it across threads, as JavaBeans convention
 * assumes.
 *
 * @since 4.2.0
 */
public class BQDataSource extends BaseJdbcWrapper implements DataSource, Serializable, Referenceable {

	@Serial
	private static final long serialVersionUID = 1L;

	/**
	 * Properties whose values are credentials and must not appear in
	 * {@link #toString()}.
	 */
	private static final Set<String> SECRETS = Set.of("credentials", "credentialConfigFile", "clientSecret",
			"refreshToken", "accessToken", "proxyPassword", "trustStorePassword");

	/**
	 * Settings, keyed by the names {@code Driver.getPropertyInfo()} advertises.
	 *
	 * <p>
	 * Stored as strings because that is what the parser consumes; a typed setter
	 * only renders its argument. {@code Properties} is also what a JDBC URL merges
	 * with, so {@link #getConnection()} hands the parser the same shape either way.
	 */
	private final Properties properties = new Properties();

	private volatile String url;

	private transient volatile PrintWriter logWriter;

	/** Creates a data source with no properties set. */
	public BQDataSource() {
		// JavaBean: a container instantiates and then populates
	}

	/**
	 * Creates a data source configured from a JDBC URL.
	 *
	 * @param url
	 *            a {@code jdbc:bigquery:} URL in either supported format
	 */
	public BQDataSource(String url) {
		this.url = url;
	}

	// ------------------------------------------------------------------
	// DataSource
	// ------------------------------------------------------------------

	@Override
	public Connection getConnection() throws SQLException {
		return new BQConnection(resolveProperties());
	}

	/**
	 * Opens a connection, rejecting a user name or password.
	 *
	 * <p>
	 * BigQuery authenticates by credential — a service account key, Application
	 * Default Credentials, an OAuth refresh token, an external account config —
	 * none of which is a user name and password pair. Rather than reinterpret the
	 * two arguments as some credential the caller did not write, this rejects them.
	 *
	 * <p>
	 * Both arguments being absent is not a rejection: pools and application servers
	 * routinely call this overload with nulls when no credentials are configured,
	 * and that request is exactly {@link #getConnection()}.
	 *
	 * @param username
	 *            must be null or blank
	 * @param password
	 *            must be null or blank
	 * @return a connection, when neither argument was supplied
	 * @throws SQLFeatureNotSupportedException
	 *             if either argument is supplied
	 * @throws SQLException
	 *             if the connection cannot be established
	 */
	@Override
	public Connection getConnection(String username, String password) throws SQLException {
		if (isBlank(username) && isBlank(password)) {
			return getConnection();
		}
		throw new SQLFeatureNotSupportedException(
				"BigQuery authenticates by credential, not by user name and password. "
						+ "Set authType and its credential property (credentials, credentialConfigFile, "
						+ "or clientId/clientSecret/refreshToken) and call getConnection() instead.",
				"0A000");
	}

	/**
	 * Returns the log writer previously set, or null.
	 *
	 * <p>
	 * The driver logs through SLF4J and never writes to this writer; it is stored
	 * only so that a container which sets and reads it back sees what it set. See
	 * {@code docs/LOGGING.md} for how to route the driver's logging.
	 *
	 * @return the writer, or null if none was set
	 */
	@Override
	public PrintWriter getLogWriter() {
		return logWriter;
	}

	/**
	 * Stores a log writer, which the driver does not use.
	 *
	 * @param out
	 *            the writer, or null
	 * @see #getLogWriter()
	 */
	@Override
	public void setLogWriter(PrintWriter out) {
		this.logWriter = out;
	}

	/**
	 * Sets the connection-establishment timeout, in seconds.
	 *
	 * <p>
	 * This is the same setting as {@link #setConnectionTimeout(Integer)}; the
	 * {@link DataSource} contract gives it a second name. Zero means "no timeout
	 * configured" per that contract, and clears the property so the driver's
	 * default applies.
	 *
	 * @param seconds
	 *            the timeout in seconds, or 0 for the driver default
	 */
	@Override
	public void setLoginTimeout(int seconds) {
		setConnectionTimeout(seconds <= 0 ? null : seconds);
	}

	/**
	 * Returns the connection-establishment timeout in seconds, or 0 if none is
	 * configured and the driver's default applies.
	 *
	 * @return the timeout in seconds, or 0
	 */
	@Override
	public int getLoginTimeout() {
		Integer configured = getConnectionTimeout();
		return configured == null ? 0 : configured;
	}

	@Override
	public Logger getParentLogger() throws SQLFeatureNotSupportedException {
		// Same answer as BQDriver: the driver logs through SLF4J, so there is no
		// java.util.logging parent to hand back.
		throw new SQLFeatureNotSupportedException("getParentLogger not supported");
	}

	// ------------------------------------------------------------------
	// Referenceable (JNDI)
	// ------------------------------------------------------------------

	/**
	 * Builds the JNDI reference an application server stores in its naming
	 * directory.
	 *
	 * <p>
	 * Every property, including credentials, is written as a string address, which
	 * is what makes the binding reconstructible. Bind a data source into a
	 * directory only where its contents are as protected as the credentials
	 * themselves.
	 *
	 * @return a reference naming {@link BQDataSourceFactory} as its factory
	 * @throws NamingException
	 *             never; declared by the interface
	 */
	@Override
	public Reference getReference() throws NamingException {
		Reference reference = new Reference(getClass().getName(), BQDataSourceFactory.class.getName(), null);
		if (url != null) {
			reference.add(new StringRefAddr("url", url));
		}
		for (String name : new TreeSet<>(properties.stringPropertyNames())) {
			reference.add(new StringRefAddr(name, properties.getProperty(name)));
		}
		return reference;
	}

	// ------------------------------------------------------------------
	// URL and untyped property access
	// ------------------------------------------------------------------

	/**
	 * Returns the JDBC URL, or null if the data source is configured by setters
	 * alone.
	 *
	 * @return the URL, or null
	 */
	public String getUrl() {
		return url;
	}

	/**
	 * Sets a JDBC URL to read settings from. Properties set on this bean override
	 * the URL's.
	 *
	 * @param url
	 *            a {@code jdbc:bigquery:} URL, or null to configure by setters
	 *            alone
	 */
	public void setUrl(String url) {
		this.url = url;
	}

	/**
	 * Sets a property by name, for a setting with no typed setter.
	 *
	 * <p>
	 * The name is one of those {@code Driver.getPropertyInfo()} advertises. An
	 * unrecognised name is ignored by the parser rather than rejected, and a value
	 * of the wrong shape is reported when {@link #getConnection()} runs, so this
	 * escape hatch trades the compiler's checking for reach. Prefer the typed
	 * setter where one exists.
	 *
	 * @param name
	 *            the property name
	 * @param value
	 *            the value, or null to unset the property
	 */
	public void setProperty(String name, String value) {
		set(name, value);
	}

	/**
	 * Returns a property's raw string value.
	 *
	 * @param name
	 *            the property name
	 * @return the value, or null if unset
	 */
	public String getProperty(String name) {
		return properties.getProperty(name);
	}

	/**
	 * Returns a copy of every property set on this data source.
	 *
	 * <p>
	 * A copy, so that mutating the result cannot reconfigure a live data source.
	 * The URL, if any, is not included.
	 *
	 * @return the properties, keyed by advertised property name
	 */
	public Properties getProperties() {
		Properties copy = new Properties();
		copy.putAll(properties);
		return copy;
	}

	/**
	 * Resolves the configured URL and properties into the connection settings a
	 * {@link BQConnection} is built from.
	 *
	 * <p>
	 * Exposed for callers that want the settings without opening a connection —
	 * validating configuration at startup, for instance.
	 *
	 * @return the resolved settings
	 * @throws SQLException
	 *             if the configuration is incomplete or invalid
	 */
	public ConnectionProperties resolveProperties() throws SQLException {
		Properties snapshot = getProperties();
		return url == null ? ConnectionUrlParser.fromProperties(snapshot) : ConnectionUrlParser.parse(url, snapshot);
	}

	// ------------------------------------------------------------------
	// Target: project, dataset, endpoint
	// ------------------------------------------------------------------

	/**
	 * Returns the Google Cloud project the connection bills and runs jobs against.
	 *
	 * @return the project id, or null if unset
	 */
	public String getProjectId() {
		return get("projectId");
	}

	/**
	 * Sets the Google Cloud project. Required unless a URL supplies it.
	 *
	 * @param projectId
	 *            the project id
	 */
	public void setProjectId(String projectId) {
		set("projectId", projectId);
	}

	/**
	 * Returns the default dataset for unqualified table references.
	 *
	 * @return the dataset name, or null if unset
	 */
	public String getDatasetId() {
		return get("datasetId");
	}

	/**
	 * Sets the default dataset used for unqualified table references in queries.
	 *
	 * @param datasetId
	 *            the dataset name, or null
	 */
	public void setDatasetId(String datasetId) {
		set("datasetId", datasetId);
	}

	/**
	 * Returns the project owning the default dataset, when it differs from the
	 * connection project.
	 *
	 * @return the project id, or null if unset
	 */
	public String getDatasetProjectId() {
		return get("datasetProjectId");
	}

	/**
	 * Sets the project owning the default dataset, when it differs from the
	 * connection project.
	 *
	 * @param datasetProjectId
	 *            the project id, or null
	 */
	public void setDatasetProjectId(String datasetProjectId) {
		set("datasetProjectId", datasetProjectId);
	}

	/**
	 * Returns the BigQuery processing location.
	 *
	 * @return the location (for example {@code US}, {@code EU}), or null if unset
	 */
	public String getLocation() {
		return get("location");
	}

	/**
	 * Sets the BigQuery processing location. Leave unset to use the dataset's
	 * location.
	 *
	 * @param location
	 *            the location (for example {@code US}, {@code EU}), or null
	 */
	public void setLocation(String location) {
		set("location", location);
	}

	/**
	 * Returns the alternative BigQuery endpoint.
	 *
	 * @return the host, or null to use Google's endpoints
	 */
	public String getHost() {
		return get("host");
	}

	/**
	 * Sets an alternative BigQuery endpoint, such as a proxy or a Private Service
	 * Connect address.
	 *
	 * @param host
	 *            the host, or null to use Google's endpoints
	 */
	public void setHost(String host) {
		set("host", host);
	}

	/**
	 * Returns the port for the endpoint set by {@link #setHost(String)}.
	 *
	 * @return the port, or null if unset
	 */
	public Integer getPort() {
		return getInteger("port");
	}

	/**
	 * Sets the port for the endpoint set by {@link #setHost(String)}.
	 *
	 * @param port
	 *            the port, or null
	 */
	public void setPort(Integer port) {
		set("port", port);
	}

	/**
	 * Returns the pre-generated access token.
	 *
	 * @return the access token, or null if unset
	 * @since 4.4.0
	 */
	public String getAccessToken() {
		return get("accessToken");
	}

	/**
	 * Sets a pre-generated OAuth 2.0 access token to authenticate with.
	 *
	 * <p>
	 * For a host application that already holds a token. The driver cannot refresh
	 * it, so the connection stops working once it expires; see
	 * {@link #setAccessTokenExpiry(String)}.
	 *
	 * @param accessToken
	 *            the access token, or null
	 * @since 4.4.0
	 */
	public void setAccessToken(String accessToken) {
		set("accessToken", accessToken);
	}

	/**
	 * Returns the expiry of the token set by {@link #setAccessToken(String)}.
	 *
	 * @return an ISO-8601 instant, or null if unset
	 * @since 4.4.0
	 */
	public String getAccessTokenExpiry() {
		return get("accessTokenExpiry");
	}

	/**
	 * Sets when the token given to {@link #setAccessToken(String)} expires, as an
	 * ISO-8601 instant such as {@code 2026-07-30T20:00:00Z}.
	 *
	 * <p>
	 * Optional. Supplying it makes an expired token fail as the connection opens,
	 * naming the expiry, rather than as a 401 on the first statement.
	 *
	 * @param accessTokenExpiry
	 *            the expiry instant, or null if unknown
	 * @since 4.4.0
	 */
	public void setAccessTokenExpiry(String accessTokenExpiry) {
		set("accessTokenExpiry", accessTokenExpiry);
	}

	/**
	 * Returns the HTTP proxy host.
	 *
	 * @return the proxy hostname, or null if unset
	 * @since 4.3.0
	 */
	public String getProxyHost() {
		return get("proxyHost");
	}

	/**
	 * Sets an HTTP proxy to route BigQuery calls and OAuth token requests through.
	 *
	 * <p>
	 * Distinct from {@link #setHost(String)}, which says where BigQuery is rather
	 * than how the driver reaches it. Leaving this unset falls back to the JVM's
	 * {@code https.proxyHost}.
	 *
	 * @param proxyHost
	 *            the proxy hostname, or null
	 * @since 4.3.0
	 */
	public void setProxyHost(String proxyHost) {
		set("proxyHost", proxyHost);
	}

	/**
	 * Returns the port of the proxy set by {@link #setProxyHost(String)}.
	 *
	 * @return the proxy port, or null if unset
	 * @since 4.3.0
	 */
	public Integer getProxyPort() {
		return getInteger("proxyPort");
	}

	/**
	 * Sets the port of the proxy set by {@link #setProxyHost(String)}, which is
	 * required whenever that is set.
	 *
	 * @param proxyPort
	 *            the proxy port, or null
	 * @since 4.3.0
	 */
	public void setProxyPort(Integer proxyPort) {
		set("proxyPort", proxyPort);
	}

	/**
	 * Returns the username sent to a proxy demanding authentication.
	 *
	 * @return the proxy username, or null if unset
	 * @since 4.3.0
	 */
	public String getProxyUser() {
		return get("proxyUser");
	}

	/**
	 * Sets the username for a proxy that demands {@code Proxy-Authorization}.
	 *
	 * @param proxyUser
	 *            the proxy username, or null for an anonymous proxy
	 * @since 4.3.0
	 */
	public void setProxyUser(String proxyUser) {
		set("proxyUser", proxyUser);
	}

	/**
	 * Returns the password for the user set by {@link #setProxyUser(String)}.
	 *
	 * @return the proxy password, or null if unset
	 * @since 4.3.0
	 */
	public String getProxyPassword() {
		return get("proxyPassword");
	}

	/**
	 * Sets the password for the user set by {@link #setProxyUser(String)}.
	 *
	 * @param proxyPassword
	 *            the proxy password, or null
	 * @since 4.3.0
	 */
	public void setProxyPassword(String proxyPassword) {
		set("proxyPassword", proxyPassword);
	}

	/**
	 * Returns the truststore TLS is verified against.
	 *
	 * @return the truststore path, or null if unset
	 * @since 4.3.0
	 */
	public String getTrustStore() {
		return get("trustStore");
	}

	/**
	 * Sets a truststore holding the certificate authorities to verify TLS against.
	 *
	 * <p>
	 * For a network whose egress is re-signed by a private CA, which otherwise
	 * fails with {@code PKIX path building failed}. Replaces the JDK's trust
	 * anchors rather than adding to them. Leaving this unset falls back to the
	 * JVM's {@code javax.net.ssl.trustStore}.
	 *
	 * @param trustStore
	 *            the truststore path, or null to use the JDK's own
	 * @since 4.3.0
	 */
	public void setTrustStore(String trustStore) {
		set("trustStore", trustStore);
	}

	/**
	 * Returns the password for the store set by {@link #setTrustStore(String)}.
	 *
	 * @return the truststore password, or null if unset
	 * @since 4.3.0
	 */
	public String getTrustStorePassword() {
		return get("trustStorePassword");
	}

	/**
	 * Sets the password protecting the store set by {@link #setTrustStore(String)}.
	 *
	 * @param trustStorePassword
	 *            the truststore password, or null for an unprotected store
	 * @since 4.3.0
	 */
	public void setTrustStorePassword(String trustStorePassword) {
		set("trustStorePassword", trustStorePassword);
	}

	/**
	 * Returns the format of the store set by {@link #setTrustStore(String)}.
	 *
	 * @return the truststore type, or null if unset
	 * @since 4.3.0
	 */
	public String getTrustStoreType() {
		return get("trustStoreType");
	}

	/**
	 * Sets the format of the store set by {@link #setTrustStore(String)}, such as
	 * {@code JKS} or {@code PKCS12}.
	 *
	 * @param trustStoreType
	 *            the truststore type, or null for the JVM default
	 * @since 4.3.0
	 */
	public void setTrustStoreType(String trustStoreType) {
		set("trustStoreType", trustStoreType);
	}

	/**
	 * Returns the JCE provider the store set by {@link #setTrustStore(String)} is
	 * loaded through.
	 *
	 * @return the provider name, or null if unset
	 * @since 4.3.0
	 */
	public String getTrustStoreProvider() {
		return get("trustStoreProvider");
	}

	/**
	 * Sets the JCE provider to load the store set by {@link #setTrustStore(String)}
	 * through.
	 *
	 * @param trustStoreProvider
	 *            the provider name, or null for the standard search order
	 * @since 4.3.0
	 */
	public void setTrustStoreProvider(String trustStoreProvider) {
		set("trustStoreProvider", trustStoreProvider);
	}

	/**
	 * Returns the additional projects reported by {@code getCatalogs()}.
	 *
	 * @return a comma-separated project list, or null if unset
	 */
	public String getAdditionalProjects() {
		return get("additionalProjects");
	}

	/**
	 * Sets further project ids to report from {@code getCatalogs()}, so a tool can
	 * discover and switch to them. Cross-project queries work without this.
	 *
	 * @param additionalProjects
	 *            a comma-separated project list, or null
	 */
	public void setAdditionalProjects(String additionalProjects) {
		set("additionalProjects", additionalProjects);
	}

	// ------------------------------------------------------------------
	// Authentication
	// ------------------------------------------------------------------

	/**
	 * Returns the authentication method.
	 *
	 * @return one of {@code ADC}, {@code SERVICE_ACCOUNT}, {@code USER_OAUTH},
	 *         {@code WORKFORCE}, {@code WORKLOAD}, or null for the default
	 *         ({@code ADC})
	 */
	public String getAuthType() {
		return get("authType");
	}

	/**
	 * Sets the authentication method.
	 *
	 * @param authType
	 *            one of {@code ADC}, {@code SERVICE_ACCOUNT}, {@code USER_OAUTH},
	 *            {@code WORKFORCE}, {@code WORKLOAD}, or null for {@code ADC}
	 */
	public void setAuthType(String authType) {
		set("authType", authType);
	}

	/**
	 * Returns the service account key file path.
	 *
	 * @return the path, or null if unset
	 */
	public String getCredentials() {
		return get("credentials");
	}

	/**
	 * Sets the path to a service account JSON key file, required for
	 * {@code SERVICE_ACCOUNT} authentication.
	 *
	 * @param credentials
	 *            the file path, or null
	 */
	public void setCredentials(String credentials) {
		set("credentials", credentials);
	}

	/**
	 * Returns the external account credential config file path.
	 *
	 * @return the path, or null if unset
	 */
	public String getCredentialConfigFile() {
		return get("credentialConfigFile");
	}

	/**
	 * Sets the path to an external account credential config file, required for
	 * {@code WORKFORCE} and {@code WORKLOAD} authentication.
	 *
	 * @param credentialConfigFile
	 *            the file path, or null
	 */
	public void setCredentialConfigFile(String credentialConfigFile) {
		set("credentialConfigFile", credentialConfigFile);
	}

	/**
	 * Returns the OAuth 2.0 client id.
	 *
	 * @return the client id, or null if unset
	 */
	public String getClientId() {
		return get("clientId");
	}

	/**
	 * Sets the OAuth 2.0 client id, required for {@code USER_OAUTH} authentication.
	 *
	 * @param clientId
	 *            the client id, or null
	 */
	public void setClientId(String clientId) {
		set("clientId", clientId);
	}

	/**
	 * Returns the OAuth 2.0 client secret.
	 *
	 * @return the client secret, or null if unset
	 */
	public String getClientSecret() {
		return get("clientSecret");
	}

	/**
	 * Sets the OAuth 2.0 client secret, required for {@code USER_OAUTH}
	 * authentication.
	 *
	 * @param clientSecret
	 *            the client secret, or null
	 */
	public void setClientSecret(String clientSecret) {
		set("clientSecret", clientSecret);
	}

	/**
	 * Returns the OAuth 2.0 refresh token.
	 *
	 * @return the refresh token, or null if unset
	 */
	public String getRefreshToken() {
		return get("refreshToken");
	}

	/**
	 * Sets the OAuth 2.0 refresh token, required for {@code USER_OAUTH}
	 * authentication.
	 *
	 * @param refreshToken
	 *            the refresh token, or null
	 */
	public void setRefreshToken(String refreshToken) {
		set("refreshToken", refreshToken);
	}

	/**
	 * Returns the service account to impersonate.
	 *
	 * @return the service account email, or null if unset
	 */
	public String getImpersonateServiceAccount() {
		return get("impersonateServiceAccount");
	}

	/**
	 * Sets a service account to impersonate, using the configured {@code authType}
	 * as the source identity.
	 *
	 * @param impersonateServiceAccount
	 *            the service account email, or null
	 */
	public void setImpersonateServiceAccount(String impersonateServiceAccount) {
		set("impersonateServiceAccount", impersonateServiceAccount);
	}

	/**
	 * Returns the delegation chain used for impersonation.
	 *
	 * @return a comma-separated, source-first list of service account emails, or
	 *         null if unset
	 */
	public String getImpersonateDelegates() {
		return get("impersonateDelegates");
	}

	/**
	 * Sets the intermediate service accounts of a delegated impersonation chain,
	 * source first. Requires {@link #setImpersonateServiceAccount(String)}.
	 *
	 * @param impersonateDelegates
	 *            a comma-separated list of service account emails, or null
	 */
	public void setImpersonateDelegates(String impersonateDelegates) {
		set("impersonateDelegates", impersonateDelegates);
	}

	// ------------------------------------------------------------------
	// Query execution
	// ------------------------------------------------------------------

	/**
	 * Returns the query execution timeout in seconds.
	 *
	 * @return the timeout, or null for the driver default
	 */
	public Integer getTimeout() {
		return getInteger("timeout");
	}

	/**
	 * Sets the query execution timeout in seconds.
	 *
	 * @param timeout
	 *            the timeout, or null for the driver default
	 */
	public void setTimeout(Integer timeout) {
		set("timeout", timeout);
	}

	/**
	 * Returns the HTTP connection-establishment timeout in seconds.
	 *
	 * @return the timeout, or null for the driver default
	 */
	public Integer getConnectionTimeout() {
		return getInteger("connectionTimeout");
	}

	/**
	 * Sets the HTTP connection-establishment timeout in seconds. This is the
	 * setting {@link #setLoginTimeout(int)} also writes.
	 *
	 * @param connectionTimeout
	 *            the timeout, or null for the driver default
	 */
	public void setConnectionTimeout(Integer connectionTimeout) {
		set("connectionTimeout", connectionTimeout);
	}

	/**
	 * Returns the total attempts made per BigQuery API call, including the first.
	 *
	 * @return the attempt count, or null for the driver default
	 */
	public Integer getRetryCount() {
		return getInteger("retryCount");
	}

	/**
	 * Sets the total attempts per BigQuery API call, including the first.
	 *
	 * @param retryCount
	 *            the attempt count, or null for the driver default
	 */
	public void setRetryCount(Integer retryCount) {
		set("retryCount", retryCount);
	}

	/**
	 * Returns the number of rows fetched per result page.
	 *
	 * @return the page size, or null for the driver default
	 */
	public Integer getPageSize() {
		return getInteger("pageSize");
	}

	/**
	 * Sets the number of rows fetched per page when iterating large result sets.
	 *
	 * @param pageSize
	 *            the page size, or null for the driver default
	 */
	public void setPageSize(Integer pageSize) {
		set("pageSize", pageSize);
	}

	/**
	 * Returns the ceiling on rows returned by a query.
	 *
	 * @return the row limit, or null for unlimited
	 */
	public Long getMaxResults() {
		return getLong("maxResults");
	}

	/**
	 * Sets the maximum number of query result rows to return.
	 *
	 * @param maxResults
	 *            the row limit, or null for unlimited
	 */
	public void setMaxResults(Long maxResults) {
		set("maxResults", maxResults);
	}

	/**
	 * Returns the per-query billed-bytes ceiling.
	 *
	 * @return the byte ceiling, or null for unlimited
	 */
	public Long getMaxBillingBytes() {
		return getLong("maxBillingBytes");
	}

	/**
	 * Sets the maximum bytes billed per query; queries exceeding it are rejected.
	 *
	 * @param maxBillingBytes
	 *            the byte ceiling, or null for unlimited
	 */
	public void setMaxBillingBytes(Long maxBillingBytes) {
		set("maxBillingBytes", maxBillingBytes);
	}

	/**
	 * Returns whether the legacy SQL dialect is used.
	 *
	 * @return true for legacy SQL, or null for the default (GoogleSQL)
	 */
	public Boolean getUseLegacySql() {
		return getBoolean("useLegacySql");
	}

	/**
	 * Sets whether to use the BigQuery legacy SQL dialect instead of GoogleSQL.
	 *
	 * @param useLegacySql
	 *            true for legacy SQL, or null for the default
	 */
	public void setUseLegacySql(Boolean useLegacySql) {
		set("useLegacySql", useLegacySql);
	}

	/**
	 * Returns the Storage Read API mode.
	 *
	 * @return {@code auto}, {@code true} or {@code false}, or null for the default
	 */
	public String getUseStorageApi() {
		return get("useStorageApi");
	}

	/**
	 * Sets the BigQuery Storage Read API mode for large result sets. Needs the JVM
	 * started with {@code --add-opens=java.base/java.nio=ALL-UNNAMED}; the driver
	 * falls back to the standard path when that is missing.
	 *
	 * @param useStorageApi
	 *            {@code auto}, {@code true} or {@code false}, or null for the
	 *            default
	 */
	public void setUseStorageApi(String useStorageApi) {
		set("useStorageApi", useStorageApi);
	}

	/**
	 * Returns whether a BigQuery session is created eagerly.
	 *
	 * @return true if sessions are enabled, or null for the default
	 */
	public Boolean getEnableSessions() {
		return getBoolean("enableSessions");
	}

	/**
	 * Sets whether to create a BigQuery session eagerly, which transactions,
	 * temporary tables and multi-statement SQL need. A session is also created
	 * lazily on the first {@code setAutoCommit(false)}.
	 *
	 * @param enableSessions
	 *            true to enable sessions, or null for the default
	 */
	public void setEnableSessions(Boolean enableSessions) {
		set("enableSessions", enableSessions);
	}

	/**
	 * Returns the job labels.
	 *
	 * @return comma-separated {@code key=value} pairs, or null if unset
	 */
	public String getLabels() {
		return get("labels");
	}

	/**
	 * Sets BigQuery job labels, as comma-separated {@code key=value} pairs (for
	 * example {@code env=prod,team=data}).
	 *
	 * @param labels
	 *            the labels, or null
	 */
	public void setLabels(String labels) {
		set("labels", labels);
	}

	/**
	 * Returns whether ARRAY and STRUCT map to native JDBC types.
	 *
	 * @return true for native {@code Array}/{@code Struct}, or null for the default
	 *         (JSON strings)
	 */
	public Boolean getNativeComplexTypes() {
		return getBoolean("nativeComplexTypes");
	}

	/**
	 * Sets whether {@code getObject()} returns native JDBC {@code Array} and
	 * {@code Struct} for ARRAY and STRUCT columns instead of JSON strings.
	 *
	 * @param nativeComplexTypes
	 *            true for native types, or null for the default
	 */
	public void setNativeComplexTypes(Boolean nativeComplexTypes) {
		set("nativeComplexTypes", nativeComplexTypes);
	}

	/**
	 * Returns the row count at which a batch switches to a load job.
	 *
	 * @return the threshold, or null to never use a load job
	 */
	public Integer getBatchLoadThreshold() {
		return getInteger("batchLoadThreshold");
	}

	/**
	 * Sets the row count at or above which {@code PreparedStatement.executeBatch()}
	 * submits one BigQuery load job instead of chunked INSERT DML. Only simple
	 * INSERTs with an explicit column list and scalar parameters qualify; anything
	 * else, and any batch inside a transaction or session, uses the DML path.
	 *
	 * @param batchLoadThreshold
	 *            the threshold, or null to never use a load job
	 */
	public void setBatchLoadThreshold(Integer batchLoadThreshold) {
		set("batchLoadThreshold", batchLoadThreshold);
	}

	// ------------------------------------------------------------------
	// Cost estimation
	// ------------------------------------------------------------------

	/**
	 * Returns whether each statement is dry-run to estimate its cost.
	 *
	 * @return true if cost estimation is on, or null for the default
	 */
	public Boolean getEnableQueryCostEstimation() {
		return getBoolean("enableQueryCostEstimation");
	}

	/**
	 * Sets whether to dry-run each query and DML statement to estimate its cost.
	 * Estimates are attached as {@code SQLWarning}s and readable as typed values
	 * from {@code BQStatement.getCostEstimates()}.
	 *
	 * @param enableQueryCostEstimation
	 *            true to estimate cost, or null for the default
	 */
	public void setEnableQueryCostEstimation(Boolean enableQueryCostEstimation) {
		set("enableQueryCostEstimation", enableQueryCostEstimation);
	}

	/**
	 * Returns the price of one tebibyte of billed query data.
	 *
	 * @return the price, or null to report bytes only
	 */
	public BigDecimal getQueryPricePerTiB() {
		return getBigDecimal("queryPricePerTiB");
	}

	/**
	 * Sets the price of one tebibyte of billed query data, which turns cost
	 * estimates into money. Any currency; BigQuery's on-demand rate is 6.25
	 * USD/TiB, but editions and negotiated contracts differ.
	 *
	 * @param queryPricePerTiB
	 *            the price, or null to report bytes only
	 */
	public void setQueryPricePerTiB(BigDecimal queryPricePerTiB) {
		set("queryPricePerTiB", queryPricePerTiB);
	}

	// ------------------------------------------------------------------
	// Metadata
	// ------------------------------------------------------------------

	/**
	 * Returns whether schema introspection results are cached.
	 *
	 * @return true if the metadata cache is on, or null for the default (on)
	 */
	public Boolean getMetadataCacheEnabled() {
		return getBoolean("metadataCacheEnabled");
	}

	/**
	 * Sets whether to cache schema introspection results, which is what makes
	 * IntelliJ IDEA's database tree usable on large projects.
	 *
	 * @param metadataCacheEnabled
	 *            true to cache, or null for the default
	 */
	public void setMetadataCacheEnabled(Boolean metadataCacheEnabled) {
		set("metadataCacheEnabled", metadataCacheEnabled);
	}

	/**
	 * Returns how long metadata is cached, in seconds.
	 *
	 * @return the TTL, or null for the driver default
	 */
	public Integer getMetadataCacheTtl() {
		return getInteger("metadataCacheTtl");
	}

	/**
	 * Sets how long, in seconds, metadata is kept before being re-fetched.
	 *
	 * @param metadataCacheTtl
	 *            the TTL, or null for the driver default
	 */
	public void setMetadataCacheTtl(Integer metadataCacheTtl) {
		set("metadataCacheTtl", metadataCacheTtl);
	}

	/**
	 * Returns the ceiling on rows held in the metadata cache.
	 *
	 * @return the row ceiling, or null for the driver default
	 */
	public Integer getMetadataCacheMaxRows() {
		return getInteger("metadataCacheMaxRows");
	}

	/**
	 * Sets the ceiling on total rows held in the metadata cache; oldest entries are
	 * evicted above it. Zero means no limit.
	 *
	 * @param metadataCacheMaxRows
	 *            the row ceiling, or null for the driver default
	 */
	public void setMetadataCacheMaxRows(Integer metadataCacheMaxRows) {
		set("metadataCacheMaxRows", metadataCacheMaxRows);
	}

	/**
	 * Returns whether columns are loaded on demand rather than on connect.
	 *
	 * @return true for lazy loading, or null for the default
	 */
	public Boolean getMetadataLazyLoad() {
		return getBoolean("metadataLazyLoad");
	}

	/**
	 * Sets whether to skip loading all columns on connect, letting a tool load them
	 * as it expands tables. Faster initial connect on large projects.
	 *
	 * @param metadataLazyLoad
	 *            true for lazy loading, or null for the default
	 */
	public void setMetadataLazyLoad(Boolean metadataLazyLoad) {
		set("metadataLazyLoad", metadataLazyLoad);
	}

	/**
	 * Returns whether table descriptions are read into {@code REMARKS}.
	 *
	 * @return true if descriptions are read, or null for the default (on)
	 */
	public Boolean getMetadataIncludeDescriptions() {
		return getBoolean("metadataIncludeDescriptions");
	}

	/**
	 * Sets whether {@code getTables()} reads table descriptions into its
	 * {@code REMARKS} column. Costs one {@code INFORMATION_SCHEMA} query per
	 * dataset scanned, cached for the metadata TTL.
	 *
	 * @param metadataIncludeDescriptions
	 *            true to read descriptions, or null for the default
	 */
	public void setMetadataIncludeDescriptions(Boolean metadataIncludeDescriptions) {
		set("metadataIncludeDescriptions", metadataIncludeDescriptions);
	}

	/**
	 * Returns whether metadata reads ask BigQuery to skip job creation.
	 *
	 * @return true if job creation is optional, or null for the default (on)
	 */
	public Boolean getMetadataJobCreationOptional() {
		return getBoolean("metadataJobCreationOptional");
	}

	/**
	 * Sets whether the driver's own {@code INFORMATION_SCHEMA} reads ask BigQuery
	 * to answer without creating a job, which takes job creation out of the latency
	 * of schema introspection. Applies only to metadata queries, never to
	 * statements the caller executes. BigQuery creates a job anyway for larger
	 * results, so this cannot change the rows returned.
	 *
	 * @param metadataJobCreationOptional
	 *            false to make every metadata read create a job, or null for the
	 *            default
	 */
	public void setMetadataJobCreationOptional(Boolean metadataJobCreationOptional) {
		set("metadataJobCreationOptional", metadataJobCreationOptional);
	}

	/**
	 * Returns whether date-sharded tables collapse to one wildcard entry.
	 *
	 * @return true if shards collapse, or null for the default (off)
	 */
	public Boolean getCollapseShardedTables() {
		return getBoolean("collapseShardedTables");
	}

	/**
	 * Sets whether date-sharded tables ({@code events_20260101},
	 * {@code events_20260102}, …) are reported as a single {@code events_*} entry
	 * by {@code getTables()}.
	 *
	 * @param collapseShardedTables
	 *            true to collapse shards, or null for the default
	 */
	public void setCollapseShardedTables(Boolean collapseShardedTables) {
		set("collapseShardedTables", collapseShardedTables);
	}

	/**
	 * Returns whether {@code getColumns()} adds a row per STRUCT field.
	 *
	 * @return true if struct fields are listed, or null for the default (off)
	 */
	public Boolean getIncludeStructFields() {
		return getBoolean("includeStructFields");
	}

	/**
	 * Sets whether {@code getColumns()} adds a row per STRUCT field, named by its
	 * dotted path. Costs one extra {@code INFORMATION_SCHEMA} query per dataset.
	 *
	 * @param includeStructFields
	 *            true to list struct fields, or null for the default
	 */
	public void setIncludeStructFields(Boolean includeStructFields) {
		set("includeStructFields", includeStructFields);
	}

	/**
	 * Returns whether {@code INFORMATION_SCHEMA} is browsable.
	 *
	 * @return true if it is listed, or null for the default (on)
	 */
	public Boolean getIncludeInformationSchema() {
		return getBoolean("includeInformationSchema");
	}

	/**
	 * Sets whether {@code INFORMATION_SCHEMA} is browsable as a synthetic schema
	 * per project and as tables inside each dataset. Costs no BigQuery query — the
	 * view list is static.
	 *
	 * @param includeInformationSchema
	 *            true to list it, or null for the default
	 */
	public void setIncludeInformationSchema(Boolean includeInformationSchema) {
		set("includeInformationSchema", includeInformationSchema);
	}

	// ------------------------------------------------------------------
	// Internals
	// ------------------------------------------------------------------

	/** Renders a value into the bag, treating null as "unset this property". */
	private void set(String name, Object value) {
		if (value == null) {
			properties.remove(name);
		} else {
			properties.setProperty(name, String.valueOf(value));
		}
	}

	private String get(String name) {
		return properties.getProperty(name);
	}

	/**
	 * @throws IllegalArgumentException
	 *             if the value was stored through {@link #setProperty} and is not
	 *             an integer; a typed setter cannot produce one
	 */
	private Integer getInteger(String name) {
		String value = get(name);
		return value == null ? null : parsed(name, value, Integer::valueOf);
	}

	/**
	 * @throws IllegalArgumentException
	 *             if the value was stored through {@link #setProperty} and is not a
	 *             long
	 */
	private Long getLong(String name) {
		String value = get(name);
		return value == null ? null : parsed(name, value, Long::valueOf);
	}

	/**
	 * @throws IllegalArgumentException
	 *             if the value was stored through {@link #setProperty} and is not a
	 *             decimal
	 */
	private BigDecimal getBigDecimal(String name) {
		String value = get(name);
		return value == null ? null : parsed(name, value, BigDecimal::new);
	}

	/**
	 * Parses a stored value, reporting a malformed one by name.
	 *
	 * <p>
	 * A bare {@link NumberFormatException} says only {@code For input string:
	 * "abc"} — not which of the driver's properties held it, and not that a
	 * {@code BQDataSource} was involved at all. That stack trace is often the only
	 * thing an operator sees, because the way a bad value gets stored is a
	 * container reading an untyped deployment descriptor through
	 * {@link BQDataSourceFactory} and {@link #setProperty}. The typed setters
	 * cannot produce one; the compiler stops them.
	 *
	 * <p>
	 * This does not weaken the rule that setters never throw. That rule exists
	 * because a container populates a bean in its own order, so a validating setter
	 * would fail or not depending on ordering. A getter has no such problem: the
	 * value is already stored, and reading it back is what a container is expected
	 * to do. {@code getConnection()} remains where a bad value is reported for
	 * anyone who never calls the getter.
	 *
	 * @param <T>
	 *            the parsed type
	 * @param name
	 *            the property name, for the message
	 * @param value
	 *            the stored value, parsed after trimming
	 * @param parser
	 *            the conversion, which signals failure by
	 *            {@link NumberFormatException}
	 * @return the parsed value
	 */
	private static <T> T parsed(String name, String value, Function<String, T> parser) {
		try {
			return parser.apply(value.trim());
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException(
					"Invalid value for BQDataSource property '" + name + "': " + value.trim(), e);
		}
	}

	private Boolean getBoolean(String name) {
		String value = get(name);
		// Boolean.valueOf, matching the parser: anything but "true" reads as false
		return value == null ? null : Boolean.valueOf(value.trim());
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	/**
	 * Describes the data source without disclosing any credential.
	 *
	 * @return a description naming the configured properties, with secret values
	 *         redacted
	 */
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("BQDataSource[");
		boolean first = true;
		if (url != null) {
			sb.append("url=").append(url);
			first = false;
		}
		for (String name : new TreeSet<>(properties.stringPropertyNames())) {
			if (!first) {
				sb.append(", ");
			}
			first = false;
			sb.append(name).append('=').append(SECRETS.contains(name) ? "<redacted>" : properties.getProperty(name));
		}
		return sb.append(']').toString();
	}
}

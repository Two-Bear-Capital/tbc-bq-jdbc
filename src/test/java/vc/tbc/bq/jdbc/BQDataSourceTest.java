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
import vc.tbc.bq.jdbc.auth.ServiceAccountAuth;
import vc.tbc.bq.jdbc.config.ConnectionProperties;

import javax.naming.Reference;
import javax.sql.DataSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link BQDataSource}.
 *
 * <p>
 * These assert on {@link BQDataSource#resolveProperties()} rather than on
 * {@code getConnection()}: everything this class contributes happens before a
 * connection is opened, and opening one would need credentials and a project.
 *
 * @since 4.2.0
 */
class BQDataSourceTest {

	@Test
	void settersProduceTheSameConnectionPropertiesAsAUrl() throws SQLException {
		BQDataSource ds = new BQDataSource();
		ds.setProjectId("my-project");
		ds.setDatasetId("my_dataset");
		ds.setAuthType("ADC");
		ds.setTimeout(60);

		ConnectionProperties fromBean = ds.resolveProperties();
		ConnectionProperties fromUrl = vc.tbc.bq.jdbc.config.ConnectionUrlParser
				.parse("jdbc:bigquery:my-project/my_dataset?authType=ADC&timeout=60", null);

		assertEquals(fromUrl, fromBean);
	}

	@Test
	void aProjectIdIsRequiredWhenThereIsNoUrl() {
		BQDataSource ds = new BQDataSource();
		ds.setAuthType("ADC");

		SQLException e = assertThrows(SQLException.class, ds::resolveProperties);
		assertTrue(e.getMessage().contains("projectId"), e.getMessage());
	}

	@Test
	void everyPropertyRoundTripsThroughItsTypedAccessor() throws SQLException {
		BQDataSource ds = new BQDataSource();
		ds.setProjectId("my-project");
		ds.setPort(8443);
		ds.setMaxResults(1_000L);
		ds.setNativeComplexTypes(true);
		ds.setQueryPricePerTiB(new BigDecimal("6.25"));

		assertEquals(8443, ds.getPort());
		assertEquals(1_000L, ds.getMaxResults());
		assertEquals(Boolean.TRUE, ds.getNativeComplexTypes());
		assertEquals(new BigDecimal("6.25"), ds.getQueryPricePerTiB());

		ConnectionProperties props = ds.resolveProperties();
		assertEquals(8443, props.port());
		assertEquals(1_000L, props.maxResults());
		assertEquals(Boolean.TRUE, props.nativeComplexTypes());
		assertEquals(new BigDecimal("6.25"), props.queryPricePerTiB());
	}

	@Test
	void nullUnsetsAPropertySoTheDriverDefaultApplies() throws SQLException {
		BQDataSource ds = new BQDataSource();
		ds.setProjectId("my-project");
		ds.setTimeout(60);
		ds.setTimeout(null);

		assertNull(ds.getTimeout());
		assertNull(ds.getProperty("timeout"));
		assertEquals(ConnectionProperties.DEFAULT_TIMEOUT_SECONDS, ds.resolveProperties().timeoutSeconds());
	}

	@Test
	void aSetPropertyOverridesTheSamePropertyInTheUrl() throws SQLException {
		BQDataSource ds = new BQDataSource("jdbc:bigquery:url-project/url_dataset?timeout=10");
		ds.setDatasetId("bean_dataset");
		ds.setTimeout(99);

		ConnectionProperties props = ds.resolveProperties();
		// The project came from the URL and was not overridden
		assertEquals("url-project", props.projectId());
		assertEquals("bean_dataset", props.datasetId());
		assertEquals(99, props.timeoutSeconds());
	}

	@Test
	void aUrlAloneConfiguresTheDataSource() throws SQLException {
		BQDataSource ds = new BQDataSource("jdbc:bigquery:my-project/my_dataset?authType=ADC");

		ConnectionProperties props = ds.resolveProperties();
		assertEquals("my-project", props.projectId());
		assertEquals("my_dataset", props.datasetId());
		assertInstanceOf(ApplicationDefaultAuth.class, props.authType());
	}

	@Test
	void authPropertiesReachTheResolvedAuthType() throws SQLException {
		BQDataSource ds = new BQDataSource();
		ds.setProjectId("my-project");
		ds.setAuthType("SERVICE_ACCOUNT");
		ds.setCredentials("/path/to/key.json");

		assertInstanceOf(ServiceAccountAuth.class, ds.resolveProperties().authType());
	}

	@Test
	void anInvalidValueIsReportedWhenResolvingRatherThanWhenSetting() {
		BQDataSource ds = new BQDataSource();
		ds.setProjectId("my-project");
		// A setter never throws: a container populates a bean in its own order, and a
		// half-configured bean is normal until it is asked for a connection
		ds.setAuthType("NOT_AN_AUTH_TYPE");

		SQLException e = assertThrows(SQLException.class, ds::resolveProperties);
		assertTrue(e.getMessage().contains("NOT_AN_AUTH_TYPE"), e.getMessage());
	}

	// ------------------------------------------------------------------
	// getConnection(user, password)
	// ------------------------------------------------------------------

	@Test
	void getConnectionWithACredentialArgumentIsRejected() {
		BQDataSource ds = new BQDataSource();
		ds.setProjectId("my-project");

		SQLFeatureNotSupportedException e = assertThrows(SQLFeatureNotSupportedException.class,
				() -> ds.getConnection("alice", "hunter2"));
		assertEquals("0A000", e.getSQLState());
		assertTrue(e.getMessage().contains("authType"), e.getMessage());

		// A password alone is rejected too
		assertThrows(SQLFeatureNotSupportedException.class, () -> ds.getConnection(null, "hunter2"));
		assertThrows(SQLFeatureNotSupportedException.class, () -> ds.getConnection("alice", null));
	}

	@Test
	void getConnectionWithNoCredentialArgumentsIsNotRejected() {
		BQDataSource ds = new BQDataSource();
		// Deliberately unconfigured: this asserts that the two-argument form defers to
		// getConnection() rather than rejecting the call, so the failure that surfaces
		// must be the missing project and not SQLFeatureNotSupportedException. Pools
		// and application servers routinely call this overload with nulls.
		SQLException e = assertThrows(SQLException.class, () -> ds.getConnection(null, null));
		assertFalse(e instanceof SQLFeatureNotSupportedException, "should not reject a call that supplied nothing");
		assertTrue(e.getMessage().contains("projectId"), e.getMessage());

		SQLException blank = assertThrows(SQLException.class, () -> ds.getConnection("", "  "));
		assertFalse(blank instanceof SQLFeatureNotSupportedException);
	}

	// ------------------------------------------------------------------
	// DataSource plumbing
	// ------------------------------------------------------------------

	@Test
	void loginTimeoutIsTheConnectionTimeoutUnderAnotherName() {
		BQDataSource ds = new BQDataSource();
		assertEquals(0, ds.getLoginTimeout(), "unset reads as 0, meaning the driver default applies");

		ds.setLoginTimeout(45);
		assertEquals(45, ds.getLoginTimeout());
		assertEquals(45, ds.getConnectionTimeout());

		ds.setConnectionTimeout(15);
		assertEquals(15, ds.getLoginTimeout());

		ds.setLoginTimeout(0);
		assertEquals(0, ds.getLoginTimeout());
		assertNull(ds.getConnectionTimeout(), "zero clears the property rather than setting a zero timeout");
	}

	@Test
	void theLogWriterRoundTripsButIsNotUsed() {
		BQDataSource ds = new BQDataSource();
		assertNull(ds.getLogWriter());

		PrintWriter writer = new PrintWriter(new StringWriter());
		ds.setLogWriter(writer);
		assertSame(writer, ds.getLogWriter());
	}

	@Test
	void getParentLoggerIsNotSupported() {
		assertThrows(SQLFeatureNotSupportedException.class, new BQDataSource()::getParentLogger);
	}

	@Test
	void unwrapsToDataSource() throws SQLException {
		BQDataSource ds = new BQDataSource();
		assertTrue(ds.isWrapperFor(DataSource.class));
		assertSame(ds, ds.unwrap(DataSource.class));
		assertFalse(ds.isWrapperFor(java.sql.Connection.class));
	}

	@Test
	void getPropertiesReturnsACopy() {
		BQDataSource ds = new BQDataSource();
		ds.setProjectId("my-project");

		Properties copy = ds.getProperties();
		copy.setProperty("projectId", "someone-elses-project");

		assertEquals("my-project", ds.getProjectId());
	}

	@Test
	void setPropertyReachesSettingsWithNoTypedSetter() throws SQLException {
		BQDataSource ds = new BQDataSource();
		ds.setProperty("projectId", "my-project");
		ds.setProperty("labels", "env=prod,team=data");

		assertEquals("prod", ds.resolveProperties().labels().get("env"));
	}

	@Test
	void toStringRedactsCredentials() {
		BQDataSource ds = new BQDataSource();
		ds.setProjectId("my-project");
		ds.setAuthType("USER_OAUTH");
		ds.setClientId("client-id");
		ds.setClientSecret("shhh");
		ds.setRefreshToken("also-shhh");
		ds.setCredentials("/path/to/key.json");
		ds.setCredentialConfigFile("/path/to/config.json");

		String rendered = ds.toString();
		assertTrue(rendered.contains("my-project"), rendered);
		assertTrue(rendered.contains("client-id"), "a client id is not a secret: " + rendered);
		assertFalse(rendered.contains("shhh"), rendered);
		assertFalse(rendered.contains("also-shhh"), rendered);
		assertFalse(rendered.contains("key.json"), rendered);
		assertFalse(rendered.contains("config.json"), rendered);
	}

	@Test
	void toStringOfAUrlOnlyDataSourceIsWellFormed() {
		assertEquals("BQDataSource[url=jdbc:bigquery:my-project]",
				new BQDataSource("jdbc:bigquery:my-project").toString());
		assertEquals("BQDataSource[]", new BQDataSource().toString());
	}

	// ------------------------------------------------------------------
	// JNDI and serialization
	// ------------------------------------------------------------------

	@Test
	void aJndiReferenceRebuildsAnEquivalentDataSource() throws Exception {
		BQDataSource original = new BQDataSource("jdbc:bigquery:my-project");
		original.setDatasetId("my_dataset");
		original.setAuthType("ADC");
		original.setMetadataCacheTtl(600);

		Reference reference = original.getReference();
		assertEquals(BQDataSourceFactory.class.getName(), reference.getFactoryClassName());

		Object rebuilt = new BQDataSourceFactory().getObjectInstance(reference, null, null, null);
		assertInstanceOf(BQDataSource.class, rebuilt);

		BQDataSource ds = (BQDataSource) rebuilt;
		assertEquals(original.getUrl(), ds.getUrl());
		assertEquals(original.getProperties(), ds.getProperties());
		assertEquals(original.resolveProperties(), ds.resolveProperties());
	}

	@Test
	void theFactoryDeclinesAnythingItDidNotProduce() throws Exception {
		BQDataSourceFactory factory = new BQDataSourceFactory();
		assertNull(factory.getObjectInstance("not a reference", null, null, null));
		assertNull(factory.getObjectInstance(new Reference("com.example.SomeOtherDataSource"), null, null, null));
	}

	@Test
	void theFactoryAcceptsAContainerDeclaredResource() throws Exception {
		// Tomcat and friends build the Reference themselves, typed as the interface and
		// carrying their own bookkeeping addresses alongside the connection properties
		Reference reference = new Reference(DataSource.class.getName(), BQDataSourceFactory.class.getName(), null);
		reference.add(new javax.naming.StringRefAddr("factory", BQDataSourceFactory.class.getName()));
		reference.add(new javax.naming.StringRefAddr("scope", "Shareable"));
		reference.add(new javax.naming.StringRefAddr("projectId", "my-project"));
		reference.add(new javax.naming.StringRefAddr("authType", "ADC"));

		BQDataSource ds = (BQDataSource) new BQDataSourceFactory().getObjectInstance(reference, null, null, null);
		assertNotNull(ds);
		assertEquals("my-project", ds.getProjectId());
		assertNull(ds.getProperty("factory"), "container bookkeeping must not become a connection property");
		assertNull(ds.getProperty("scope"));
		assertInstanceOf(ApplicationDefaultAuth.class, ds.resolveProperties().authType());
	}

	@Test
	void serializationPreservesTheConfiguration() throws Exception {
		BQDataSource original = new BQDataSource("jdbc:bigquery:my-project");
		original.setDatasetId("my_dataset");
		original.setPageSize(1234);
		// Not serializable and not part of the configuration; must not break the write
		original.setLogWriter(new PrintWriter(new StringWriter()));

		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
			out.writeObject(original);
		}
		BQDataSource restored;
		try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
			restored = (BQDataSource) in.readObject();
		}

		assertEquals(original.getUrl(), restored.getUrl());
		assertEquals(original.getProperties(), restored.getProperties());
		assertEquals(original.resolveProperties(), restored.resolveProperties());
		assertNull(restored.getLogWriter());
	}

	/**
	 * A malformed value can only be stored through {@code setProperty}, and the way
	 * that happens in practice is a container reading an untyped deployment
	 * descriptor through {@code BQDataSourceFactory}. The stack trace from the
	 * getter is often all an operator sees, so it has to say which of the driver's
	 * ~41 properties is wrong.
	 */
	@Test
	void aMalformedIntegerNamesThePropertyAndTheValue() {
		BQDataSource ds = new BQDataSource();
		ds.setProperty("timeout", "abc");

		IllegalArgumentException e = assertThrows(IllegalArgumentException.class, ds::getTimeout);

		assertTrue(e.getMessage().contains("timeout"), e.getMessage());
		assertTrue(e.getMessage().contains("abc"), e.getMessage());
		assertTrue(e.getMessage().contains("BQDataSource"), e.getMessage());
		// The cause is kept, so anything already catching NumberFormatException by
		// walking the chain still finds it
		assertInstanceOf(NumberFormatException.class, e.getCause());
	}

	@Test
	void aMalformedLongNamesTheProperty() {
		BQDataSource ds = new BQDataSource();
		ds.setProperty("maxBillingBytes", "not-a-long");

		IllegalArgumentException e = assertThrows(IllegalArgumentException.class, ds::getMaxBillingBytes);

		assertTrue(e.getMessage().contains("maxBillingBytes"), e.getMessage());
	}

	@Test
	void aMalformedDecimalNamesTheProperty() {
		// getBigDecimal has the same shape as the other two and is reachable the
		// same way, though only the integer and long getters were flagged
		BQDataSource ds = new BQDataSource();
		ds.setProperty("queryPricePerTiB", "six-dollars");

		IllegalArgumentException e = assertThrows(IllegalArgumentException.class, ds::getQueryPricePerTiB);

		assertTrue(e.getMessage().contains("queryPricePerTiB"), e.getMessage());
	}

	@Test
	void aMalformedBooleanIsStillFalseRatherThanAnError() {
		// Boolean.valueOf never throws, and the URL parser reads anything but
		// "true" as false — so the bean must not start rejecting what a URL accepts
		BQDataSource ds = new BQDataSource();
		ds.setProperty("useLegacySql", "yes-please");

		assertEquals(Boolean.FALSE, ds.getUseLegacySql());
	}
}

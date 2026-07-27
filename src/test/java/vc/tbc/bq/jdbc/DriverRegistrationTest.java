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

import java.sql.Driver;
import java.sql.DriverManager;
import java.util.Enumeration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for JDBC driver registration.
 *
 * @since 1.0.0
 */
class DriverRegistrationTest {

	@Test
	void testDriverRegisteredViaServiceLoader() {
		// Given: The BQDriver static initializer should have registered the driver
		// When: We enumerate all registered drivers
		Enumeration<Driver> drivers = DriverManager.getDrivers();

		// Then: BQDriver should be in the list
		boolean found = false;
		while (drivers.hasMoreElements()) {
			Driver driver = drivers.nextElement();
			if (driver instanceof BQDriver) {
				found = true;
				break;
			}
		}

		assertTrue(found, "BQDriver should be registered with DriverManager");
	}

	@Test
	void testDriverAcceptsValidUrl() throws Exception {
		// Given: A BQDriver instance
		BQDriver driver = new BQDriver();

		// Then: It should accept BigQuery URLs
		assertTrue(driver.acceptsURL("jdbc:bigquery:my-project/my-dataset"));
		assertTrue(driver.acceptsURL("jdbc:bigquery:my-project"));
		assertTrue(driver.acceptsURL("jdbc:bigquery:my-project/my-dataset?authType=ADC"));
		assertTrue(driver.acceptsURL(
				"jdbc:bigquery:my-project/my-dataset?authType=SERVICE_ACCOUNT&credentials=/path/to/key.json"));
	}

	@Test
	void testDriverRejectsInvalidUrl() throws Exception {
		// Given: A BQDriver instance
		BQDriver driver = new BQDriver();

		// Then: It should reject non-BigQuery URLs
		assertFalse(driver.acceptsURL("jdbc:postgresql://localhost/db"));
		assertFalse(driver.acceptsURL("jdbc:mysql://localhost/db"));
		assertFalse(driver.acceptsURL("jdbc:oracle:thin:@localhost:1521:xe"));
		assertFalse(driver.acceptsURL("https://www.google.com"));
		assertFalse(driver.acceptsURL(null));
	}

	@Test
	void testDriverVersion() {
		// Given: A BQDriver instance
		BQDriver driver = new BQDriver();

		// Then: The reported components must agree with the version the build
		// stamped in. Pinning literals here (this asserted minor == 0) breaks on
		// every minor release rather than on a real defect.
		String[] parts = DriverVersion.getVersionString().split("\\.");
		assertEquals(Integer.parseInt(parts[0]), driver.getMajorVersion(),
				"Major version should match " + DriverVersion.getVersionString());
		assertEquals(Integer.parseInt(parts[1]), driver.getMinorVersion(),
				"Minor version should match " + DriverVersion.getVersionString());
		assertTrue(driver.getMajorVersion() >= 1, "A released driver has a major version of at least 1");
	}

	@Test
	void testDriverNotJdbcCompliant() {
		// Given: A BQDriver instance
		BQDriver driver = new BQDriver();

		// Then: Should not claim JDBC compliance due to BigQuery limitations
		assertFalse(driver.jdbcCompliant(),
				"Driver should not be JDBC compliant due to BigQuery transaction limitations");
	}

	@Test
	void testGetParentLoggerThrowsException() {
		// Given: A BQDriver instance
		BQDriver driver = new BQDriver();

		// Then: getParentLogger should throw SQLFeatureNotSupportedException
		assertThrows(java.sql.SQLFeatureNotSupportedException.class, driver::getParentLogger,
				"getParentLogger should throw SQLFeatureNotSupportedException");
	}

	@Test
	void testGetPropertyInfoReturnsDriverProperties() throws Exception {
		// Given: A BQDriver instance
		BQDriver driver = new BQDriver();

		// When: Getting property info
		var propertyInfo = driver.getPropertyInfo("jdbc:bigquery:my-project/my-dataset", new java.util.Properties());

		// Then: Should return all supported properties with defaults
		assertNotNull(propertyInfo, "Property info should not be null");
		assertTrue(propertyInfo.length > 0, "Property info should not be empty");

		// authType must be present, required, with the correct default
		var authTypeProp = java.util.Arrays.stream(propertyInfo).filter(p -> "authType".equals(p.name)).findFirst();
		assertTrue(authTypeProp.isPresent(), "authType property must be present");
		assertFalse(authTypeProp.get().required, "authType should not be required (has default ADC)");
		assertEquals("ADC", authTypeProp.get().value, "authType default should be ADC");
		assertNotNull(authTypeProp.get().choices, "authType should have choices");

		// metadataCacheEnabled must be present with default true
		var cacheProp = java.util.Arrays.stream(propertyInfo).filter(p -> "metadataCacheEnabled".equals(p.name))
				.findFirst();
		assertTrue(cacheProp.isPresent(), "metadataCacheEnabled property must be present");
		assertEquals("true", cacheProp.get().value, "metadataCacheEnabled default should be true");
	}

	@Test
	void testAcceptsSimbaFormatUrl() throws Exception {
		// Given: A BQDriver instance
		BQDriver driver = new BQDriver();

		// Then: It should accept Simba format URLs
		assertTrue(driver.acceptsURL(
				"jdbc:bigquery://https://www.googleapis.com/bigquery/v2:443;ProjectId=my-project;OAuthType=3"));
	}
}

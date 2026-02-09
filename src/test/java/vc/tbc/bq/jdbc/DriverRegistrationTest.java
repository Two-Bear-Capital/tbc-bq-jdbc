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

		// Then: Version should be set
		assertEquals(1, driver.getMajorVersion());
		assertEquals(0, driver.getMinorVersion());
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
	void testGetPropertyInfoReturnsEmptyArray() throws Exception {
		// Given: A BQDriver instance
		BQDriver driver = new BQDriver();

		// When: Getting property info
		var propertyInfo = driver.getPropertyInfo("jdbc:bigquery:my-project/my-dataset", new java.util.Properties());

		// Then: Should return empty array
		assertNotNull(propertyInfo, "Property info should not be null");
		assertEquals(0, propertyInfo.length, "Property info should be empty");
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

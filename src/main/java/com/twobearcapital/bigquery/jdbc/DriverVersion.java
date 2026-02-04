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

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class to load and parse the driver version information from the
 * Maven-filtered properties file.
 *
 * <p>
 * The version is injected at build time from the Maven POM version using
 * resource filtering.
 *
 * @since 1.0.0
 */
public final class DriverVersion {

	private static final Logger logger = LoggerFactory.getLogger(DriverVersion.class);
	private static final String VERSION_PROPERTIES = "/driver-version.properties";

	private static final String VERSION_STRING;
	private static final int MAJOR_VERSION;
	private static final int MINOR_VERSION;
	private static final int PATCH_VERSION;

	static {
		Properties props = new Properties();
		try (InputStream in = DriverVersion.class.getResourceAsStream(VERSION_PROPERTIES)) {
			if (in == null) {
				logger.warn("Could not find {} in classpath, using default version 0.0.0", VERSION_PROPERTIES);
				VERSION_STRING = "0.0.0";
				MAJOR_VERSION = 0;
				MINOR_VERSION = 0;
				PATCH_VERSION = 0;
			} else {
				props.load(in);
				VERSION_STRING = parseVersionString(props.getProperty("driver.version", "0.0.0"));
				String[] parts = parseVersionParts(VERSION_STRING);
				MAJOR_VERSION = Integer.parseInt(parts[0]);
				MINOR_VERSION = Integer.parseInt(parts[1]);
				PATCH_VERSION = Integer.parseInt(parts[2]);
				logger.debug("Loaded driver version: {}", VERSION_STRING);
			}
		} catch (IOException | NumberFormatException e) {
			logger.error("Failed to load driver version from {}", VERSION_PROPERTIES, e);
			throw new RuntimeException("Failed to load driver version", e);
		}
	}

	/**
	 * Parse version string and strip any qualifiers (-alpha, -SNAPSHOT, etc).
	 *
	 * @param rawVersion
	 *            the raw version from properties
	 * @return cleaned version string (e.g., "1.0.0-alpha" -> "1.0.0")
	 */
	private static String parseVersionString(String rawVersion) {
		if (rawVersion == null || rawVersion.isEmpty()) {
			return "0.0.0";
		}

		// Strip any qualifiers (-alpha, -SNAPSHOT, -beta, etc.)
		int dashIndex = rawVersion.indexOf('-');
		return dashIndex > 0 ? rawVersion.substring(0, dashIndex) : rawVersion;
	}

	/**
	 * Parse version string into [major, minor, patch] parts.
	 *
	 * @param version
	 *            the version string
	 * @return array of [major, minor, patch] as strings
	 */
	private static String[] parseVersionParts(String version) {
		String[] parts = version.split("\\.");

		// Ensure we have at least 3 parts (major.minor.patch)
		if (parts.length < 3) {
			String[] result = {"0", "0", "0"};
			System.arraycopy(parts, 0, result, 0, parts.length);
			return result;
		}

		return new String[]{parts[0], parts[1], parts[2]};
	}

	/** Private constructor to prevent instantiation. */
	private DriverVersion() {
	}

	/**
	 * Get the full version string (e.g., "1.2.3").
	 *
	 * @return version string
	 */
	public static String getVersionString() {
		return VERSION_STRING;
	}

	/**
	 * Get the major version number.
	 *
	 * @return major version
	 */
	public static int getMajorVersion() {
		return MAJOR_VERSION;
	}

	/**
	 * Get the minor version number.
	 *
	 * @return minor version
	 */
	public static int getMinorVersion() {
		return MINOR_VERSION;
	}

	/**
	 * Get the patch version number.
	 *
	 * @return patch version
	 */
	public static int getPatchVersion() {
		return PATCH_VERSION;
	}
}

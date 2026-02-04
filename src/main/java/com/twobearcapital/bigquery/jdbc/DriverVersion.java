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

import com.twobearcapital.bigquery.jdbc.util.NumberParser;

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
 * resource filtering. Git commit information is also included for precise build
 * identification.
 *
 * @since 1.0.0
 */
public final class DriverVersion {

	private static final Logger logger = LoggerFactory.getLogger(DriverVersion.class);
	private static final String VERSION_PROPERTIES = "/driver-version.properties";
	private static final String GIT_PROPERTIES = "/git.properties";

	private static final String VERSION_STRING;
	private static final int MAJOR_VERSION;
	private static final int MINOR_VERSION;
	private static final int PATCH_VERSION;

	// Git build information
	private static final String GIT_COMMIT_ID;
	private static final String GIT_COMMIT_ID_SHORT;
	private static final String GIT_COMMIT_TIME;
	private static final String GIT_COMMIT_MESSAGE_SHORT;
	private static final String GIT_BRANCH;
	private static final String GIT_DIRTY;
	private static final String BUILD_TIME;
	private static final String BUILD_VERSION;

	static {
		// Load version information
		Properties versionProps = loadVersionProperties();
		VERSION_STRING = parseVersionString(versionProps.getProperty("driver.version", "0.0.0"));
		String[] parts = parseVersionParts(VERSION_STRING);
		MAJOR_VERSION = NumberParser.parseInt(parts[0], 0);
		MINOR_VERSION = NumberParser.parseInt(parts[1], 0);
		PATCH_VERSION = NumberParser.parseInt(parts[2], 0);
		logger.debug("Loaded driver version: {}", VERSION_STRING);

		// Load Git build information
		Properties gitProps = loadGitProperties();
		GIT_COMMIT_ID = gitProps.getProperty("git.commit.id.full", "unknown");
		GIT_COMMIT_ID_SHORT = gitProps.getProperty("git.commit.id.abbrev", "unknown");
		GIT_COMMIT_TIME = gitProps.getProperty("git.commit.time", "unknown");
		GIT_COMMIT_MESSAGE_SHORT = gitProps.getProperty("git.commit.message.short", "unknown");
		GIT_BRANCH = gitProps.getProperty("git.branch", "unknown");
		GIT_DIRTY = gitProps.getProperty("git.dirty", "unknown");
		BUILD_TIME = gitProps.getProperty("git.build.time", "unknown");
		BUILD_VERSION = gitProps.getProperty("git.build.version", "unknown");
		if (!"unknown".equals(GIT_COMMIT_ID_SHORT)) {
			logger.debug("Loaded Git commit information: {} ({})", GIT_COMMIT_ID_SHORT, GIT_BRANCH);
		}
	}

	/**
	 * Load version properties from the Maven-filtered properties file.
	 *
	 * @return Properties object (empty if file not found or error occurs)
	 */
	private static Properties loadVersionProperties() {
		Properties props = new Properties();
		try (InputStream in = DriverVersion.class.getResourceAsStream(VERSION_PROPERTIES)) {
			if (in == null) {
				logger.warn("Could not find {} in classpath, using default version 0.0.0", VERSION_PROPERTIES);
			} else {
				props.load(in);
			}
		} catch (IOException e) {
			logger.error("Failed to load driver version from {}", VERSION_PROPERTIES, e);
		}
		return props;
	}

	/**
	 * Load Git properties from the generated git.properties file.
	 *
	 * @return Properties object (empty if file not found or error occurs)
	 */
	private static Properties loadGitProperties() {
		Properties props = new Properties();
		try (InputStream in = DriverVersion.class.getResourceAsStream(GIT_PROPERTIES)) {
			if (in == null) {
				logger.debug("Git properties not found, using default values");
			} else {
				props.load(in);
			}
		} catch (IOException e) {
			logger.warn("Failed to load Git properties from {}, using default values", GIT_PROPERTIES, e);
		}
		return props;
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

	/**
	 * Get the full Git commit SHA.
	 *
	 * @return Git commit ID (full SHA)
	 */
	public static String getGitCommitId() {
		return GIT_COMMIT_ID;
	}

	/**
	 * Get the abbreviated Git commit SHA (typically 7 characters).
	 *
	 * @return Git commit ID (short)
	 */
	public static String getGitCommitIdShort() {
		return GIT_COMMIT_ID_SHORT;
	}

	/**
	 * Get the Git commit timestamp.
	 *
	 * @return Git commit time
	 */
	public static String getGitCommitTime() {
		return GIT_COMMIT_TIME;
	}

	/**
	 * Get the short commit message.
	 *
	 * @return Git commit message (first line)
	 */
	public static String getGitCommitMessageShort() {
		return GIT_COMMIT_MESSAGE_SHORT;
	}

	/**
	 * Get the Git branch name.
	 *
	 * @return Git branch
	 */
	public static String getGitBranch() {
		return GIT_BRANCH;
	}

	/**
	 * Check if the build had uncommitted changes.
	 *
	 * @return "true" if working directory was dirty, "false" if clean
	 */
	public static String getGitDirty() {
		return GIT_DIRTY;
	}

	/**
	 * Get the build timestamp.
	 *
	 * @return build time
	 */
	public static String getBuildTime() {
		return BUILD_TIME;
	}

	/**
	 * Get the build version (same as VERSION_STRING).
	 *
	 * @return build version
	 */
	public static String getBuildVersion() {
		return BUILD_VERSION;
	}

	/**
	 * Get a comprehensive version string including Git information.
	 *
	 * <p>
	 * Example: "1.0.1 (commit abc1234 on main, built 2026-02-04T10:30:00-0500)"
	 *
	 * @return detailed version string
	 */
	public static String getFullVersionInfo() {
		StringBuilder sb = new StringBuilder();
		sb.append(VERSION_STRING);
		sb.append(" (commit ").append(GIT_COMMIT_ID_SHORT);
		if (!"unknown".equals(GIT_BRANCH)) {
			sb.append(" on ").append(GIT_BRANCH);
		}
		if ("true".equals(GIT_DIRTY)) {
			sb.append(" [dirty]");
		}
		if (!"unknown".equals(BUILD_TIME)) {
			sb.append(", built ").append(BUILD_TIME);
		}
		sb.append(")");
		return sb.toString();
	}
}

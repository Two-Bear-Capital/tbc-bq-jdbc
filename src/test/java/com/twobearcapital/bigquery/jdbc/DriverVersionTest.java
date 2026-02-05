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
package com.twobearcapital.bigquery.jdbc;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DriverVersion utility class.
 *
 * @since 1.0.15
 */
class DriverVersionTest {

	// Version string tests

	@Test
	void testGetVersionString() {
		// When: Getting version string
		String version = DriverVersion.getVersionString();

		// Then: Should not be null or empty
		assertNotNull(version);
		assertFalse(version.isEmpty());
	}

	@Test
	void testVersionStringFormat() {
		// When: Getting version string
		String version = DriverVersion.getVersionString();

		// Then: Should match semantic version format (major.minor.patch)
		assertTrue(version.matches("\\d+\\.\\d+\\.\\d+"), "Version should match pattern X.Y.Z: " + version);
	}

	@Test
	void testVersionStringHasThreeParts() {
		// When: Getting version string
		String version = DriverVersion.getVersionString();

		// Then: Should have exactly 3 parts (major.minor.patch)
		String[] parts = version.split("\\.");
		assertEquals(3, parts.length, "Version should have 3 parts: " + version);
	}

	// Version number tests

	@Test
	void testGetMajorVersion() {
		// When: Getting major version
		int major = DriverVersion.getMajorVersion();

		// Then: Should be non-negative
		assertTrue(major >= 0, "Major version should be non-negative");
	}

	@Test
	void testGetMinorVersion() {
		// When: Getting minor version
		int minor = DriverVersion.getMinorVersion();

		// Then: Should be non-negative
		assertTrue(minor >= 0, "Minor version should be non-negative");
	}

	@Test
	void testGetPatchVersion() {
		// When: Getting patch version
		int patch = DriverVersion.getPatchVersion();

		// Then: Should be non-negative
		assertTrue(patch >= 0, "Patch version should be non-negative");
	}

	@Test
	void testVersionNumbersMatchVersionString() {
		// When: Getting version information
		String versionString = DriverVersion.getVersionString();
		int major = DriverVersion.getMajorVersion();
		int minor = DriverVersion.getMinorVersion();
		int patch = DriverVersion.getPatchVersion();

		// Then: Version numbers should match version string
		String expectedVersion = major + "." + minor + "." + patch;
		assertEquals(expectedVersion, versionString);
	}

	// Git information tests

	@Test
	void testGetGitCommitId() {
		// When: Getting Git commit ID
		String commitId = DriverVersion.getGitCommitId();

		// Then: Should not be null
		assertNotNull(commitId);
	}

	@Test
	void testGetGitCommitIdShort() {
		// When: Getting short Git commit ID
		String commitIdShort = DriverVersion.getGitCommitIdShort();

		// Then: Should not be null
		assertNotNull(commitIdShort);
	}

	@Test
	void testGetGitCommitTime() {
		// When: Getting Git commit time
		String commitTime = DriverVersion.getGitCommitTime();

		// Then: Should not be null
		assertNotNull(commitTime);
	}

	@Test
	void testGetGitCommitMessageShort() {
		// When: Getting Git commit message
		String message = DriverVersion.getGitCommitMessageShort();

		// Then: Should not be null
		assertNotNull(message);
	}

	@Test
	void testGetGitBranch() {
		// When: Getting Git branch
		String branch = DriverVersion.getGitBranch();

		// Then: Should not be null
		assertNotNull(branch);
	}

	@Test
	void testGetGitDirty() {
		// When: Getting Git dirty flag
		String dirty = DriverVersion.getGitDirty();

		// Then: Should not be null
		assertNotNull(dirty);
	}

	@Test
	void testGetBuildTime() {
		// When: Getting build time
		String buildTime = DriverVersion.getBuildTime();

		// Then: Should not be null
		assertNotNull(buildTime);
	}

	@Test
	void testGetBuildVersion() {
		// When: Getting build version
		String buildVersion = DriverVersion.getBuildVersion();

		// Then: Should not be null
		assertNotNull(buildVersion);
	}

	// Full version info tests

	@Test
	void testGetFullVersionInfo() {
		// When: Getting full version info
		String fullInfo = DriverVersion.getFullVersionInfo();

		// Then: Should not be null or empty
		assertNotNull(fullInfo);
		assertFalse(fullInfo.isEmpty());
	}

	@Test
	void testFullVersionInfoContainsVersionString() {
		// When: Getting full version info
		String fullInfo = DriverVersion.getFullVersionInfo();
		String version = DriverVersion.getVersionString();

		// Then: Should contain version string
		assertTrue(fullInfo.contains(version), "Full version info should contain version string");
	}

	@Test
	void testFullVersionInfoContainsCommitKeyword() {
		// When: Getting full version info
		String fullInfo = DriverVersion.getFullVersionInfo();

		// Then: Should contain 'commit' keyword
		assertTrue(fullInfo.contains("commit"), "Full version info should contain 'commit'");
	}

	@Test
	void testFullVersionInfoContainsCommitId() {
		// When: Getting full version info
		String fullInfo = DriverVersion.getFullVersionInfo();
		String commitIdShort = DriverVersion.getGitCommitIdShort();

		// Then: Should contain short commit ID
		assertTrue(fullInfo.contains(commitIdShort), "Full version info should contain short commit ID");
	}

	@Test
	void testFullVersionInfoFormat() {
		// When: Getting full version info
		String fullInfo = DriverVersion.getFullVersionInfo();

		// Then: Should start with version and contain parentheses
		assertTrue(fullInfo.contains("("), "Full version info should contain opening parenthesis");
		assertTrue(fullInfo.contains(")"), "Full version info should contain closing parenthesis");
	}

	// Class structure tests

	@Test
	void testClassIsFinal() {
		// When: Checking class modifiers
		boolean isFinal = Modifier.isFinal(DriverVersion.class.getModifiers());

		// Then: Class should be final
		assertTrue(isFinal, "DriverVersion class should be final");
	}

	@Test
	void testPrivateConstructor() throws Exception {
		// When: Getting constructor
		Constructor<DriverVersion> constructor = DriverVersion.class.getDeclaredConstructor();

		// Then: Constructor should be private
		assertTrue(Modifier.isPrivate(constructor.getModifiers()));

		// And: Should be able to invoke it (no exception thrown)
		constructor.setAccessible(true);
		assertDoesNotThrow(() -> constructor.newInstance());
	}

	// Consistency tests

	@Test
	void testVersionStringShouldNotContainQualifiers() {
		// When: Getting version string
		String version = DriverVersion.getVersionString();

		// Then: Should not contain qualifiers like -SNAPSHOT, -alpha, etc.
		assertFalse(version.contains("-"), "Version string should not contain qualifiers");
		assertFalse(version.contains("SNAPSHOT"), "Version string should not contain SNAPSHOT");
		assertFalse(version.contains("alpha"), "Version string should not contain alpha");
		assertFalse(version.contains("beta"), "Version string should not contain beta");
	}

	@Test
	void testAllVersionComponentsAreNumeric() {
		// When: Getting version string
		String version = DriverVersion.getVersionString();
		String[] parts = version.split("\\.");

		// Then: All parts should be numeric
		for (String part : parts) {
			assertTrue(part.matches("\\d+"), "Version part should be numeric: " + part);
		}
	}

	@Test
	void testGitInformationIsConsistent() {
		// When: Getting Git information
		String commitIdFull = DriverVersion.getGitCommitId();
		String commitIdShort = DriverVersion.getGitCommitIdShort();

		// Then: If both are not "unknown", short should be prefix of full (or different
		// format)
		if (!"unknown".equals(commitIdFull) && !"unknown".equals(commitIdShort)) {
			// Either short is prefix of full, or both are valid Git hashes
			assertTrue(commitIdFull.startsWith(commitIdShort) || commitIdShort.matches("[0-9a-f]{7}"),
					"Short commit ID should be consistent with full commit ID");
		}
	}

	@Test
	void testBuildVersionConsistency() {
		// When: Getting build version and version string
		String buildVersion = DriverVersion.getBuildVersion();
		String versionString = DriverVersion.getVersionString();

		// Then: Build version should either match version string or be "unknown"
		assertTrue(buildVersion.equals(versionString) || buildVersion.equals("unknown"),
				"Build version should match version string or be 'unknown'");
	}
}

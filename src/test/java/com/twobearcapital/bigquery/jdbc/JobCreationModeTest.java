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

import static org.junit.jupiter.api.Assertions.*;

import com.twobearcapital.bigquery.jdbc.config.JobCreationMode;
import org.junit.jupiter.api.Test;

/**
 * Tests for JobCreationMode enum.
 *
 * @since 1.0.0
 */
class JobCreationModeTest {

	@Test
	void testEnumValues() {
		// Then: Should have REQUIRED and OPTIONAL values
		JobCreationMode[] values = JobCreationMode.values();
		assertEquals(2, values.length);
		assertArrayEquals(new JobCreationMode[]{JobCreationMode.REQUIRED, JobCreationMode.OPTIONAL}, values);
	}

	@Test
	void testValueOf() {
		// When: Getting enum by name
		JobCreationMode required = JobCreationMode.valueOf("REQUIRED");
		JobCreationMode optional = JobCreationMode.valueOf("OPTIONAL");

		// Then: Should return correct values
		assertEquals(JobCreationMode.REQUIRED, required);
		assertEquals(JobCreationMode.OPTIONAL, optional);
	}

	@Test
	void testValueOfInvalidThrowsException() {
		// Then: Invalid name should throw IllegalArgumentException
		assertThrows(IllegalArgumentException.class, () -> JobCreationMode.valueOf("INVALID"));
	}

	@Test
	void testEnumEquality() {
		// Given: Same enum values
		JobCreationMode mode1 = JobCreationMode.REQUIRED;
		JobCreationMode mode2 = JobCreationMode.REQUIRED;

		// Then: Should be equal
		assertSame(mode1, mode2);
		assertEquals(mode1, mode2);
	}

	@Test
	void testEnumToString() {
		// When: Converting to string
		String requiredStr = JobCreationMode.REQUIRED.toString();
		String optionalStr = JobCreationMode.OPTIONAL.toString();

		// Then: Should return enum name
		assertEquals("REQUIRED", requiredStr);
		assertEquals("OPTIONAL", optionalStr);
	}
}

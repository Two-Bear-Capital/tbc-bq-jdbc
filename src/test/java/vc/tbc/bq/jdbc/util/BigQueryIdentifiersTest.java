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
package vc.tbc.bq.jdbc.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the identifier guard on interpolated {@code INFORMATION_SCHEMA}
 * query text.
 *
 * @since 2.2.1
 */
class BigQueryIdentifiersTest {

	@ParameterizedTest
	@ValueSource(strings = {"my-project", "mythical-module-444615-h2", "my_dataset", "tbc_bq_jdbc_integration_tests",
			"ds1", "A", "_leading_underscore", "trailing-dash-not-at-end9"})
	void acceptsNamesBigQueryPermits(String identifier) {
		assertTrue(BigQueryIdentifiers.isSafe(identifier), identifier + " is a legal BigQuery name");
	}

	/**
	 * The payloads that matter. Each one, interpolated into
	 * {@code FROM `<project>`.`<dataset>`.INFORMATION_SCHEMA.ROUTINES}, closes the
	 * backticks around it and appends attacker-chosen SQL.
	 */
	@ParameterizedTest
	@ValueSource(strings = {"proj`.`ds`.INFORMATION_SCHEMA.ROUTINES WHERE FALSE UNION ALL SELECT 1, 2 -- ",
			"proj`; DROP TABLE x; --", "proj`", "`", "proj\\`escaped"})
	void rejectsNamesThatCanEscapeTheQuoting(String payload) {
		assertFalse(BigQueryIdentifiers.isSafe(payload), "must not interpolate: " + payload);
	}

	/**
	 * Whitespace and newlines cannot appear in a BigQuery name and are how a
	 * payload separates its injected clauses.
	 */
	@ParameterizedTest
	@ValueSource(strings = {"has space", "has\ttab", "has\nnewline", "has;semicolon", "has.dot", "has:colon",
			"has'quote", "has\"doublequote", "has(paren)", "has*star"})
	void rejectsNamesWithCharactersBigQueryDoesNotPermit(String identifier) {
		assertFalse(BigQueryIdentifiers.isSafe(identifier), "must not interpolate: " + identifier);
	}

	@Test
	void rejectsNullAndEmpty() {
		assertFalse(BigQueryIdentifiers.isSafe(null));
		assertFalse(BigQueryIdentifiers.isSafe(""));
	}

	@Test
	void areSafeRequiresBothNames() {
		assertTrue(BigQueryIdentifiers.areSafe("my-project", "my_dataset"));
		assertFalse(BigQueryIdentifiers.areSafe("my-project", "bad`dataset"));
		assertFalse(BigQueryIdentifiers.areSafe("bad`project", "my_dataset"));
		assertFalse(BigQueryIdentifiers.areSafe(null, "my_dataset"));
		assertFalse(BigQueryIdentifiers.areSafe("my-project", null));
	}
}

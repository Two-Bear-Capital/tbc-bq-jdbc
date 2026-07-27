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
package vc.tbc.bq.jdbc.integration.scale;

import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Base for the opt-in scale suite.
 *
 * <h2>What this suite is for</h2>
 *
 * <p>
 * The real integration suite proves the driver is <em>correct</em> against
 * BigQuery, on fixtures of three rows. Nothing proved it stayed correct or
 * usable at size: no test iterated a large result, none exercised a project
 * wide enough for the metadata fan-out cap added in #99 to engage, and nothing
 * watched the shared metadata cache over enough time to see whether it reaches
 * a steady state or simply grows.
 *
 * <h2>Fixtures are generated, not committed</h2>
 *
 * <p>
 * Each test builds what it needs and tears it down. The alternative — a
 * committed fixture dataset — would need provisioning outside the repository,
 * would drift from what the tests assume, and would make the suite unrunnable
 * against any project but one. Generating costs a few minutes at the start of a
 * run that already takes minutes.
 *
 * <p>
 * Everything created is named with {@link #RUN_ID} and carries a BigQuery-side
 * expiry wherever the object type supports one, so a cancelled run cannot leave
 * anything permanent behind. Datasets are the exception — BigQuery has no
 * dataset-level expiry — so those are dropped explicitly and named
 * recognisably.
 *
 * @see RequiresScaleTestOptIn
 */
@ExtendWith(RequiresScaleTestOptIn.class)
abstract class AbstractScaleTest {

	private static final Logger logger = LoggerFactory.getLogger(AbstractScaleTest.class);

	private static final String DEFAULT_DATASET = "tbc_bq_jdbc_integration_tests";

	protected static final String TEST_PROJECT_ID = System.getenv().getOrDefault("BQ_TEST_PROJECT", "");
	protected static final String TEST_DATASET = System.getenv().getOrDefault("BQ_TEST_DATASET", DEFAULT_DATASET);

	/**
	 * Unique suffix for this JVM run, so concurrent runs against the same project
	 * cannot collide and so a stranded object can be traced back to a run.
	 */
	protected static final String RUN_ID = Long.toHexString(System.nanoTime() & 0xFFFFFFFFL);

	/**
	 * Cost ceiling applied to every connection, mirroring the real integration
	 * suite.
	 *
	 * <p>
	 * It matters more here than there. These tests deal in millions of rows and
	 * hundreds of tables, so a query that accidentally resolves to a real table
	 * would scan something large, repeatedly. Every query this suite issues is
	 * generated with {@code GENERATE_ARRAY} or reads empty fixture tables, so
	 * nothing legitimate approaches 1 GB.
	 */
	protected static final String COST_CEILING = "&maxBillingBytes=1073741824";

	/**
	 * Opens a connection to the default test dataset.
	 *
	 * @return a JDBC connection
	 * @throws SQLException
	 *             if the connection cannot be opened
	 */
	protected static Connection openConnection() throws SQLException {
		return openConnection("");
	}

	/**
	 * Opens a connection with extra URL properties appended.
	 *
	 * @param extraProperties
	 *            additional URL properties, each prefixed with {@code &}, or an
	 *            empty string
	 * @return a JDBC connection
	 * @throws SQLException
	 *             if the connection cannot be opened
	 */
	protected static Connection openConnection(String extraProperties) throws SQLException {
		String url = String.format("jdbc:bigquery:%s/%s?authType=ADC%s%s", TEST_PROJECT_ID, TEST_DATASET, COST_CEILING,
				extraProperties);
		logger.debug("Scale test connecting with URL: {}", url);
		return DriverManager.getConnection(url);
	}

	/**
	 * Reads an integer from the environment, falling back to a default.
	 *
	 * <p>
	 * Scale is parameterised rather than fixed so the same test can be a
	 * three-minute check on a laptop and a much harder one when someone is
	 * deliberately hunting a scaling problem.
	 *
	 * @param name
	 *            the environment variable
	 * @param defaultValue
	 *            the value to use when unset or unparseable
	 * @return the configured value
	 */
	protected static int envInt(String name, int defaultValue) {
		String raw = System.getenv(name);
		if (raw == null || raw.isBlank()) {
			return defaultValue;
		}
		try {
			return Integer.parseInt(raw.strip());
		} catch (NumberFormatException e) {
			logger.warn("{} is not an integer ({}) — using default {}", name, raw, defaultValue);
			return defaultValue;
		}
	}

	/**
	 * Best-effort estimate of live heap in bytes.
	 *
	 * <p>
	 * {@code System.gc()} is a hint the JVM is free to ignore, so this is a soft
	 * number and no assertion should turn on a small difference. It is called twice
	 * because a single collection often leaves objects that only became unreachable
	 * during that collection, and the second pass reclaims them — which matters
	 * here, where the question is whether retained heap grows with rows read.
	 *
	 * <p>
	 * The real guard against accidental full materialisation is not this
	 * measurement but the bounded {@code -Xmx} the scale-tests profile sets: a
	 * driver that buffered a million rows would fail by running out of memory, not
	 * by nudging a threshold.
	 *
	 * @return approximate live heap in bytes
	 */
	protected static long usedHeapBytes() {
		Runtime runtime = Runtime.getRuntime();
		System.gc();
		System.gc();
		return runtime.totalMemory() - runtime.freeMemory();
	}
}

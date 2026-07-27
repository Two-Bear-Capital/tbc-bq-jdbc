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
package vc.tbc.bq.jdbc.integration.real;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Disables the real BigQuery suite when {@code BQ_TEST_PROJECT} is not set.
 *
 * <p>
 * This replaces a class-level {@code @EnabledIfEnvironmentVariable} on
 * {@link AbstractRealBigQueryIntegrationTest}, which did not reach subclasses:
 * that annotation is not {@code @Inherited}, so without the variable every test
 * errored on a malformed URL built from an empty project id instead of
 * skipping. An extension registered with {@code @ExtendWith} <em>is</em>
 * inherited, and — unlike an assumption in {@code @BeforeEach} — it also
 * prevents {@code @BeforeAll} fixtures from running and failing first.
 *
 * <p>
 * CI guards separately against the suite passing while skipping everything: the
 * workflow checks the secret is non-empty before running, and checks a non-zero
 * completed count afterwards.
 */
class RequiresBigQueryCredentials implements ExecutionCondition {

	@Override
	public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
		String project = System.getenv("BQ_TEST_PROJECT");
		if (project == null || project.isBlank()) {
			return ConditionEvaluationResult
					.disabled("BQ_TEST_PROJECT is not set — skipping the real BigQuery integration tests");
		}
		return ConditionEvaluationResult.enabled("BQ_TEST_PROJECT is set");
	}
}

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

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Disables the scale suite unless it has been explicitly asked for.
 *
 * <p>
 * Two gates, because they answer different questions. {@code BQ_TEST_PROJECT}
 * says <em>can</em> these run — there is a project to run them against.
 * {@code BQ_SCALE_TESTS} says <em>should</em> they — these tests build datasets
 * with hundreds of tables and iterate a million rows, which takes minutes and
 * leaves BigQuery-side state behind. Credentials alone must not be enough to
 * trigger that, or anyone running the ordinary integration suite would pick it
 * up by accident.
 *
 * <p>
 * The {@code scale-tests} Maven profile is a third, independent gate: the
 * default failsafe includes do not match this package at all. Belt and braces
 * is deliberate. A gate that lives only in build configuration is one broadened
 * include pattern away from firing, and the failure mode — a PR run that
 * quietly grows by twenty minutes and a pile of BigQuery objects — is bad
 * enough to be worth a second lock.
 *
 * <p>
 * Unlike the real integration suite, nothing in CI guards against these
 * skipping silently, because nothing in CI runs them. They are for a human
 * asking a scale question deliberately.
 */
class RequiresScaleTestOptIn implements ExecutionCondition {

	@Override
	public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
		String project = System.getenv("BQ_TEST_PROJECT");
		if (project == null || project.isBlank()) {
			return ConditionEvaluationResult.disabled("BQ_TEST_PROJECT is not set — skipping the scale suite");
		}

		String optIn = System.getenv("BQ_SCALE_TESTS");
		if (optIn == null || !optIn.equalsIgnoreCase("true")) {
			return ConditionEvaluationResult
					.disabled("BQ_SCALE_TESTS is not \"true\" — skipping the scale suite (it is slow and "
							+ "creates BigQuery datasets)");
		}

		return ConditionEvaluationResult.enabled("BQ_TEST_PROJECT and BQ_SCALE_TESTS are both set");
	}
}

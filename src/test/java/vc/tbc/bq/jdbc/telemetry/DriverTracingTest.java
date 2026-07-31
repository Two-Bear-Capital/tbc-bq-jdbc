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
package vc.tbc.bq.jdbc.telemetry;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Holds the claim the whole design rests on: the driver works on a classpath
 * with no OpenTelemetry API.
 *
 * <p>
 * The API is a {@code provided} dependency, so it is present when these tests
 * compile and absent for most people who use the driver. That asymmetry is
 * exactly the kind a test suite normally fails to notice — everything passes in
 * a build where the API is on the classpath, and the first person without it
 * gets a {@link NoClassDefFoundError} on their first query.
 *
 * <p>
 * {@link #theDriverLoadsAndTracesNothingWithoutTheOpenTelemetryApi()}
 * reproduces the missing-API classpath directly, by loading the driver's
 * telemetry classes through a loader that refuses to hand out
 * {@code io.opentelemetry}.
 *
 * @since 4.4.0
 */
class DriverTracingTest {

	@Test
	void tracingIsAvailableWhenTheApiIsOnTheClasspath() {
		// The build has it at provided scope, so this is the "host supplied it" case
		assertTrue(DriverTracing.isAvailable());
	}

	@Test
	void aDisabledConnectionGetsTheNoopSpan() {
		// enableTracing=false must not depend on the classpath at all
		assertSame(QuerySpan.NOOP, DriverTracing.start("BigQuery.query", false));
	}

	@Test
	void spansAreUsableWithNoSdkRegistered() {
		// No SDK is registered in this suite, so OpenTelemetry hands back its own
		// no-op span. It must still be safe to use and to close.
		try (QuerySpan span = DriverTracing.start("BigQuery.query", true)) {
			assertNotNull(span);
			assertFalse(span.isRecording(), "nothing should record without a registered SDK");
			// All of these are no-ops, and none may throw
			span.setAttribute("bigquery.job_id", "job_123");
			span.setAttribute("bigquery.job_id", null);
			span.recordFailure("42000", new IllegalStateException("boom"));
		}
	}

	@Test
	void theNoopSpanAnswersEveryMethod() {
		QuerySpan span = QuerySpan.NOOP;

		assertFalse(span.isRecording());
		span.setAttribute("k", "v");
		span.recordFailure(null, null);
		span.close();
	}

	/**
	 * Loads {@link DriverTracing} in a class loader that hides
	 * {@code io.opentelemetry}, and drives it.
	 *
	 * <p>
	 * A plain unit test cannot express this: the API is on the test classpath, so
	 * the branch that matters to most users is otherwise never executed. If a
	 * future change adds an {@code io.opentelemetry} import to
	 * {@code DriverTracing} or {@code QuerySpan}, this fails with
	 * {@link NoClassDefFoundError} — which is the point.
	 */
	@Test
	void theDriverLoadsAndTracesNothingWithoutTheOpenTelemetryApi() throws Exception {
		try (URLClassLoader hidden = new HidingClassLoader(classpath(), "io.opentelemetry.")) {
			Class<?> tracing = Class.forName("vc.tbc.bq.jdbc.telemetry.DriverTracing", true, hidden);

			assertNotSameLoader(tracing, hidden);
			assertEquals(Boolean.FALSE, tracing.getMethod("isAvailable").invoke(null),
					"tracing must report itself unavailable when the API is hidden");

			Object span = tracing.getMethod("start", String.class, boolean.class).invoke(null, "BigQuery.query", true);
			assertNotNull(span, "a no-op span is still a span");
			// Exercised through reflection because the interface loaded here is a
			// different class than the one this test was compiled against
			Class<?> querySpan = Class.forName("vc.tbc.bq.jdbc.telemetry.QuerySpan", true, hidden);
			assertEquals(Boolean.FALSE, querySpan.getMethod("isRecording").invoke(span));
			querySpan.getMethod("setAttribute", String.class, String.class).invoke(span, "bigquery.job_id", "job_123");
			querySpan.getMethod("close").invoke(span);
		}
	}

	private static void assertNotSameLoader(Class<?> loaded, ClassLoader expected) {
		assertSame(expected, loaded.getClassLoader(), "the test must exercise the isolated copy, not the ambient one");
	}

	/** The current test classpath, as URLs. */
	private static URL[] classpath() throws Exception {
		List<URL> urls = new ArrayList<>();
		for (String entry : System.getProperty("java.class.path").split(java.io.File.pathSeparator)) {
			urls.add(Path.of(entry).toUri().toURL());
		}
		return urls.toArray(new URL[0]);
	}

	/**
	 * A loader that pretends a package does not exist.
	 *
	 * <p>
	 * Parent-last for everything else, so the driver's own classes are loaded
	 * afresh here rather than inherited from the application loader — inheriting
	 * them would resolve their OpenTelemetry references against the real API and
	 * defeat the test.
	 */
	private static final class HidingClassLoader extends URLClassLoader {

		private final String hiddenPrefix;

		HidingClassLoader(URL[] urls, String hiddenPrefix) {
			super(urls, ClassLoader.getPlatformClassLoader());
			this.hiddenPrefix = hiddenPrefix;
		}

		@Override
		protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
			if (name.startsWith(hiddenPrefix)) {
				throw new ClassNotFoundException(name + " is hidden by this test");
			}
			return super.loadClass(name, resolve);
		}

		@Override
		public InputStream getResourceAsStream(String name) {
			if (name.replace('/', '.').startsWith(hiddenPrefix)) {
				return null;
			}
			return super.getResourceAsStream(name);
		}
	}
}

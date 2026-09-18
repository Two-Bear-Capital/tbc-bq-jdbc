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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decides, once per JVM, whether the driver can emit OpenTelemetry spans here.
 *
 * <p>
 * A span carrying a BigQuery job id is the one thing {@code DriverMetrics}
 * structurally cannot express: counters say how much the driver is doing, and a
 * span says which job made <em>this</em> request slow. That single capability
 * is why tracing exists here at all.
 *
 * <p>
 * <b>The OpenTelemetry API is not shipped with the driver.</b> It is a
 * {@code provided} dependency, so a host that wants traces supplies it and a
 * host that does not is unaffected — a JDBC driver dropped into an IDE should
 * not drag in an observability stack. Two consequences follow, and both shape
 * this class:
 *
 * <ul>
 * <li><b>Nothing here may reference {@code io.opentelemetry}.</b> Loading a
 * class triggers verification of the types it names, so a single import would
 * make this class unloadable exactly when the API is missing — which is the
 * case it exists to survive. The references live in {@link OpenTelemetrySpans},
 * which is only loaded after the probe below succeeds. This is the
 * {@code ArrowSupport} pattern.
 * <li><b>The API must not be relocated</b> by the shaded jars. A relocated copy
 * would resolve to a different {@code GlobalOpenTelemetry} than the host's, so
 * the driver's spans would be emitted into a global nobody is reading and would
 * silently fail to join the host's traces.
 * </ul>
 *
 * <p>
 * <b>A registered provider is the switch.</b> With the API present but no SDK
 * registered, the tracer is OpenTelemetry's own no-op and nothing is emitted;
 * that is what makes tracing safe to leave on. The tracer is resolved once and
 * reused: {@code GlobalOpenTelemetry.getTracer} returns a lazy handle that
 * picks up an SDK registered later, so caching it does not pin an early
 * decision — which matters because a host commonly configures OpenTelemetry
 * after the driver has been loaded.
 *
 * @since 4.4.0
 */
public final class DriverTracing {

	private static final Logger logger = LoggerFactory.getLogger(DriverTracing.class);

	/** The class whose presence decides whether the API is usable. */
	private static final String PROBE_CLASS = "io.opentelemetry.api.GlobalOpenTelemetry";

	/**
	 * The span factory, resolved once. Never null: absent OpenTelemetry yields a
	 * factory that hands back {@link QuerySpan#NOOP}.
	 */
	private static final SpanFactory FACTORY = resolveFactory();

	private DriverTracing() {
		throw new AssertionError("Utility class should not be instantiated");
	}

	/**
	 * Whether the OpenTelemetry API is on the classpath.
	 *
	 * <p>
	 * True does not mean spans are being recorded — that additionally needs an SDK
	 * registered, which this deliberately does not test for, because the answer can
	 * change after the driver loads.
	 *
	 * @return true when spans can be emitted at all
	 */
	public static boolean isAvailable() {
		return FACTORY != SpanFactory.NONE;
	}

	/**
	 * Starts a span, or returns a no-op one.
	 *
	 * @param name
	 *            the span name
	 * @param enabled
	 *            false to force tracing off for this connection, whatever the
	 *            classpath holds
	 * @return a span that must be closed, never null
	 */
	public static QuerySpan start(String name, boolean enabled) {
		if (!enabled) {
			return QuerySpan.NOOP;
		}
		return FACTORY.start(name);
	}

	/** Loads the OpenTelemetry-backed factory, or falls back to no-op. */
	private static SpanFactory resolveFactory() {
		try {
			Class.forName(PROBE_CLASS, false, DriverTracing.class.getClassLoader());
		} catch (ClassNotFoundException | LinkageError e) {
			logger.debug("OpenTelemetry API not on the classpath; driver tracing is disabled");
			return SpanFactory.NONE;
		}
		try {
			// Loaded reflectively rather than referenced directly, so that this class
			// never names a type from the API. A direct reference would be resolved
			// when this class is verified, which is precisely what must not happen
			// when the API is absent.
			return (SpanFactory) Class
					.forName("vc.tbc.bq.jdbc.telemetry.OpenTelemetrySpans", true, DriverTracing.class.getClassLoader())
					.getDeclaredConstructor().newInstance();
		} catch (ReflectiveOperationException | LinkageError e) {
			// The API answered the probe but could not be used — a partial or
			// mismatched copy on the classpath. Degrade rather than fail a connection
			// over telemetry.
			logger.warn("OpenTelemetry API is present but unusable; driver tracing is disabled", e);
			return SpanFactory.NONE;
		}
	}

	/**
	 * Creates spans. Implemented against the OpenTelemetry API in
	 * {@link OpenTelemetrySpans}, and by {@link #NONE} when it is absent.
	 */
	interface SpanFactory {

		/** The factory used when no OpenTelemetry API is available. */
		SpanFactory NONE = name -> QuerySpan.NOOP;

		/**
		 * Starts a span.
		 *
		 * @param name
		 *            the span name
		 * @return the started span
		 */
		QuerySpan start(String name);
	}
}

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

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import vc.tbc.bq.jdbc.DriverVersion;

/**
 * The only class in the driver that names an OpenTelemetry type.
 *
 * <p>
 * Kept alone so that {@link DriverTracing} can decide whether to load it. Every
 * other instrumentation site holds {@link QuerySpan}, which names nothing from
 * the API, and therefore loads on a classpath without it. Adding an
 * {@code io.opentelemetry} import anywhere else would undo that.
 *
 * <p>
 * Instantiated reflectively, so the no-argument constructor is load-bearing
 * despite appearing unused.
 *
 * @since 4.4.0
 */
final class OpenTelemetrySpans implements DriverTracing.SpanFactory {

	/**
	 * The instrumentation scope, which is how a host filters the driver's spans
	 * from its own. Named for the package rather than the product, per
	 * OpenTelemetry's convention that a scope names the instrumenting library.
	 */
	private static final String INSTRUMENTATION_SCOPE = "vc.tbc.bq.jdbc";

	/**
	 * Resolved once. {@code GlobalOpenTelemetry.getTracer} hands back a lazy handle
	 * that resolves an SDK registered later, so this does not freeze a decision
	 * made before the host finished configuring itself.
	 */
	private final Tracer tracer;

	OpenTelemetrySpans() {
		this.tracer = GlobalOpenTelemetry.getTracer(INSTRUMENTATION_SCOPE, DriverVersion.getVersionString());
	}

	@Override
	public QuerySpan start(String name) {
		// CLIENT: from the host application's point of view this is a call out to a
		// remote service, which is what makes the span join its trace in the right
		// place rather than appearing as unrelated internal work.
		return new OtelQuerySpan(tracer.spanBuilder(name).setSpanKind(SpanKind.CLIENT).startSpan());
	}

	/** A started span, ended on close. */
	private record OtelQuerySpan(Span span) implements QuerySpan {

		@Override
		public void setAttribute(String key, String value) {
			if (value != null) {
				span.setAttribute(key, value);
			}
		}

		@Override
		public void recordFailure(String sqlState, Throwable error) {
			span.setStatus(StatusCode.ERROR);
			if (sqlState != null) {
				span.setAttribute("db.response.status_code", sqlState);
			}
			if (error != null) {
				span.recordException(error);
			}
		}

		@Override
		public boolean isRecording() {
			return span.isRecording();
		}

		@Override
		public void close() {
			span.end();
		}
	}
}

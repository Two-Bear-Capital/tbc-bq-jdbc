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

/**
 * One unit of driver work, reported to whatever tracing the host configured.
 *
 * <p>
 * Deliberately not an OpenTelemetry type. Call sites in the driver hold this
 * interface, so they compile and run whether or not the OpenTelemetry API is on
 * the classpath — see {@link DriverTracing}. It carries only what the driver
 * actually records, rather than wrapping the full {@code Span} surface.
 *
 * <p>
 * Every method is safe to call on {@link #NOOP}, so no caller needs a null
 * check or a guard around instrumentation.
 *
 * @since 4.4.0
 */
public interface QuerySpan extends AutoCloseable {

	/** The span used when tracing is unavailable or switched off. */
	QuerySpan NOOP = new QuerySpan() {

		@Override
		public void setAttribute(String key, String value) {
			// nothing is recording
		}

		@Override
		public void recordFailure(String sqlState, Throwable error) {
			// nothing is recording
		}

		@Override
		public boolean isRecording() {
			return false;
		}

		@Override
		public void close() {
			// nothing to end
		}
	};

	/**
	 * Records an attribute on the span.
	 *
	 * @param key
	 *            the attribute name
	 * @param value
	 *            the value; ignored when null
	 */
	void setAttribute(String key, String value);

	/**
	 * Marks the span as failed.
	 *
	 * @param sqlState
	 *            the SQLState the driver will report, or null
	 * @param error
	 *            the cause, or null when the failure carries no exception
	 */
	void recordFailure(String sqlState, Throwable error);

	/**
	 * Whether anything is actually collecting this span.
	 *
	 * <p>
	 * Worth testing before computing an attribute value that costs something. An
	 * attribute that is merely a field read is cheaper to set unconditionally than
	 * to guard.
	 *
	 * @return true when the span is recorded
	 */
	boolean isRecording();

	/** Ends the span. Never throws. */
	@Override
	void close();
}

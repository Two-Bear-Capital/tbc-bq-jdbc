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
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Asserts what a span actually carries, against a real in-memory SDK.
 *
 * <p>
 * Everything else about tracing can pass while emitting empty spans. The job id
 * is the whole reason this feature exists — it is what turns "this request was
 * slow" into a job someone can look up — so it is worth verifying that it
 * arrives, rather than only that a span was started.
 *
 * @since 4.4.0
 */
class SpanContentTest {

	private static final AttributeKey<String> JOB_ID = AttributeKey.stringKey("bigquery.job_id");
	private static final AttributeKey<String> STATUS = AttributeKey.stringKey("db.response.status_code");

	private InMemorySpanExporter exporter;
	private OpenTelemetrySdk sdk;

	@BeforeEach
	void registerSdk() {
		// GlobalOpenTelemetry holds one registration per JVM, so it is reset around
		// each case rather than left for the next test to inherit
		GlobalOpenTelemetry.resetForTest();
		exporter = InMemorySpanExporter.create();
		sdk = OpenTelemetrySdk.builder()
				.setTracerProvider(
						SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(exporter)).build())
				.buildAndRegisterGlobal();
	}

	@AfterEach
	void unregisterSdk() {
		sdk.close();
		GlobalOpenTelemetry.resetForTest();
	}

	@Test
	void aSpanCarriesItsNameKindAndAttributes() {
		try (QuerySpan span = newSpans().start("BigQuery.query")) {
			assertTrue(span.isRecording(), "a registered SDK should be recording");
			span.setAttribute("bigquery.job_id", "job_abc123");
		}

		SpanData recorded = onlySpan();
		assertEquals("BigQuery.query", recorded.getName());
		// CLIENT is what makes the driver's work nest under the host's request span
		// as a call out, rather than appearing as unrelated internal work
		assertEquals(SpanKind.CLIENT, recorded.getKind());
		assertEquals("job_abc123", recorded.getAttributes().get(JOB_ID));
		assertEquals(StatusCode.UNSET, recorded.getStatus().getStatusCode());
	}

	@Test
	void aNullAttributeIsDroppedRatherThanRecordedAsNull() {
		// runJob sets the job id from a value that can legitimately be absent
		try (QuerySpan span = newSpans().start("BigQuery.query")) {
			span.setAttribute("bigquery.job_id", null);
		}

		assertNull(onlySpan().getAttributes().get(JOB_ID), "the attribute must be absent, not present and null");
	}

	@Test
	void aFailureRecordsTheStatusSqlStateAndException() {
		try (QuerySpan span = newSpans().start("BigQuery.query")) {
			span.recordFailure("42501", new IllegalStateException("permission denied"));
		}

		SpanData recorded = onlySpan();
		assertEquals(StatusCode.ERROR, recorded.getStatus().getStatusCode());
		assertEquals("42501", recorded.getAttributes().get(STATUS));
		assertEquals(1, recorded.getEvents().size(), "the exception should be recorded as an event");
	}

	@Test
	void aFailureWithNoSqlStateStillMarksTheSpanFailed() {
		try (QuerySpan span = newSpans().start("BigQuery.query")) {
			span.recordFailure(null, null);
		}

		assertEquals(StatusCode.ERROR, onlySpan().getStatus().getStatusCode());
	}

	/**
	 * A factory bound to the SDK registered for this test.
	 *
	 * <p>
	 * Constructed directly rather than through {@link DriverTracing}, whose factory
	 * is resolved once per JVM and so would have captured a tracer from whichever
	 * test ran first.
	 */
	private static DriverTracing.SpanFactory newSpans() {
		return new OpenTelemetrySpans();
	}

	private SpanData onlySpan() {
		List<SpanData> spans = exporter.getFinishedSpanItems();
		assertEquals(1, spans.size(), "expected exactly one span, got " + spans);
		return spans.get(0);
	}
}

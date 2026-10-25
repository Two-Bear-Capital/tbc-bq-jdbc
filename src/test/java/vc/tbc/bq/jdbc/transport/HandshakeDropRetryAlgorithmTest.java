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
package vc.tbc.bq.jdbc.transport;

import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.api.gax.retrying.ResultRetryAlgorithm;
import com.google.cloud.NoCredentials;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryOptions;
import com.google.cloud.bigquery.JobInfo;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.http.HttpTransportOptions;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLHandshakeException;
import java.io.EOFException;
import java.io.IOException;
import java.net.SocketException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HandshakeDropRetryAlgorithmTest {

	private static BigQueryOptions.Builder options() {
		return BigQueryOptions.newBuilder().setProjectId("p").setCredentials(NoCredentials.getInstance());
	}

	private static ResultRetryAlgorithm<?> library() {
		return options().build().getResultRetryAlgorithm();
	}

	@Test
	void theClientLibraryDoesNotRetryAHandshakeDropOnItsOwn() {
		// The reason this class exists. If a client upgrade starts retrying it,
		// this fails and the class can go.
		@SuppressWarnings("unchecked") // the client's handler is a ResultRetryAlgorithm<Object>
		ResultRetryAlgorithm<Object> library = (ResultRetryAlgorithm<Object>) library();

		assertFalse(library.shouldRetry(new SSLHandshakeException("connection closed"), null));
	}

	@Test
	void addsTheHandshakeAndKeepsEverythingElse() {
		HandshakeDropRetryAlgorithm algorithm = new HandshakeDropRetryAlgorithm(library());

		assertTrue(algorithm.shouldRetry(new SSLHandshakeException("connection closed"), null));
		assertTrue(algorithm.shouldRetry(new SocketException("reset"), null), "the library's own rule");
		// Could have delivered the insert, and a retry carries a new job id
		assertFalse(algorithm.shouldRetry(new EOFException("connection closed"), null));
	}

	@Test
	void aJobInsertSurvivesADroppedHandshake() {
		AtomicInteger inserts = new AtomicInteger();
		MockHttpTransport bigQueryApi = new MockHttpTransport() {
			@Override
			public LowLevelHttpRequest buildRequest(String method, String url) {
				return new MockLowLevelHttpRequest(url) {
					@Override
					public LowLevelHttpResponse execute() throws IOException {
						// After a failed insert the client looks the job up by id, and
						// finding it would pass this test without any retry
						if (!"POST".equals(method)) {
							return new MockLowLevelHttpResponse().setStatusCode(404);
						}
						if (inserts.incrementAndGet() == 1) {
							throw new SSLHandshakeException("connection closed");
						}
						return new MockLowLevelHttpResponse().setContentType("application/json").setContent(
								"{\"jobReference\":{\"projectId\":\"p\",\"jobId\":\"j\"},\"status\":{\"state\":\"DONE\"}}");
					}
				};
			}
		};
		BigQueryOptions.Builder builder = options()
				.setTransportOptions(
						HttpTransportOptions.newBuilder().setHttpTransportFactory(() -> bigQueryApi).build())
				.setRetrySettings(BigQueryOptions.getDefaultRetrySettings().toBuilder()
						.setInitialRetryDelayDuration(Duration.ofMillis(1)).build());
		BigQuery bigquery = builder
				.setResultRetryAlgorithm(new HandshakeDropRetryAlgorithm(builder.build().getResultRetryAlgorithm()))
				.build().getService();

		bigquery.create(JobInfo.of(QueryJobConfiguration.of("SELECT 1")));

		assertEquals(2, inserts.get());
	}
}

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
package vc.tbc.bq.jdbc.exception;

import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpResponseException;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link ServiceErrorDetail}.
 *
 * @since 4.0.0
 */
class ServiceErrorDetailTest {

	/**
	 * The body Google's IAM Credentials API returns for a missing
	 * {@code serviceAccountTokenCreator} grant, trimmed of the fields this does not
	 * read. Captured from a real 403 rather than invented, so a change in what the
	 * parser looks for shows up here.
	 */
	private static final String IAM_DENIED_BODY = """
			{
			  "error": {
			    "code": 403,
			    "message": "Permission 'iam.serviceAccounts.getAccessToken' denied on resource (or it may not exist).",
			    "errors": [ { "message": "denied", "domain": "global", "reason": "forbidden" } ],
			    "status": "PERMISSION_DENIED"
			  }
			}""";

	private static final String IAM_DENIED_TEXT = "Permission 'iam.serviceAccounts.getAccessToken' denied on resource (or it may not exist).";

	private static HttpResponseException httpError(int status, String statusMessage, String body) {
		return new HttpResponseException.Builder(status, statusMessage, new HttpHeaders()).setContent(body).build();
	}

	@Test
	void testNullCauseLeavesMessageUnchanged() {
		// Then: There is nothing to look through
		assertEquals("Query execution failed", ServiceErrorDetail.appendTo("Query execution failed", null));
	}

	@Test
	void testCauseWithoutHttpFailureLeavesMessageUnchanged() {
		// Given: A chain carrying no HTTP response at all
		Throwable cause = new IllegalStateException("boom", new IOException("inner"));

		// Then: The message should pass through untouched
		assertEquals("Query execution failed", ServiceErrorDetail.appendTo("Query execution failed", cause));
	}

	@Test
	void testServiceExplanationIsAppended() {
		// Given: The shape a missing impersonation grant actually arrives in
		Throwable cause = new IOException("Error requesting access token",
				httpError(403, "Forbidden", IAM_DENIED_BODY));

		// When: Building the message a JDBC caller sees
		String message = ServiceErrorDetail.appendTo("Error requesting access token", cause);

		// Then: It should name the permission, which is the only actionable part
		assertEquals("Error requesting access token (HTTP 403: " + IAM_DENIED_TEXT + ")", message);
	}

	@Test
	void testBodyWithoutAnExplanationAddsNothing() {
		// Given: An HTTP failure whose body carries no error message — the shape a
		// failed BigQuery job takes, where the message already says what was wrong
		Throwable cause = new IOException("Syntax error: SELECT list must not be empty at [1:8]",
				httpError(400, "Bad Request", null));

		// Then: Appending "(HTTP 400: Bad Request)" would be pure noise, so the
		// message must be left alone
		assertEquals("Syntax error: SELECT list must not be empty at [1:8]",
				ServiceErrorDetail.appendTo("Syntax error: SELECT list must not be empty at [1:8]", cause));
	}

	@Test
	void testUnparseableBodyAddsNothing() {
		// Given: A body that is not JSON at all, e.g. an HTML error page from a proxy
		Throwable cause = new IOException("Bad gateway", httpError(502, "Bad Gateway", "<html>502</html>"));

		// Then: A parse failure must not become the reported error
		assertEquals("Bad gateway", ServiceErrorDetail.appendTo("Bad gateway", cause));
	}

	@Test
	void testJsonWithoutAnErrorObjectAddsNothing() {
		// Given: Valid JSON of an unexpected shape
		Throwable cause = new IOException("Unavailable",
				httpError(503, "Unavailable", "{\"other\":{\"message\":\"x\"}}"));

		// Then: The message should be unchanged rather than reporting a mismatch
		assertEquals("Unavailable", ServiceErrorDetail.appendTo("Unavailable", cause));
	}

	@Test
	void testExplanationAlreadyInTheMessageIsNotDuplicated() {
		// Given: A message that already restates the service's explanation
		Throwable cause = new IOException("wrapped", httpError(403, "Forbidden", IAM_DENIED_BODY));
		String message = "Failed: " + IAM_DENIED_TEXT;

		// Then: It should not be appended a second time
		assertEquals(message, ServiceErrorDetail.appendTo(message, cause));
	}

	@Test
	void testExplanationIsFoundDeepInTheChain() {
		// Given: The four-layer chain the Google client libraries actually build
		Throwable cause = new IllegalStateException("outer",
				new IOException("middle", new IOException("inner", httpError(403, "Forbidden", IAM_DENIED_BODY))));

		// Then: Depth should not hide it
		assertTrue(ServiceErrorDetail.appendTo("Query execution failed", cause).contains(IAM_DENIED_TEXT));
	}

	@Test
	void testDeepestHttpFailureWins() {
		// Given: One HTTP failure wrapping another, as a retrying client can produce
		HttpResponseException inner = httpError(403, "Forbidden", IAM_DENIED_BODY);
		HttpResponseException outer = httpError(500, "Server Error",
				"{\"error\":{\"message\":\"observed on the way out\"}}");
		outer.initCause(inner);

		// When: Building the message
		String message = ServiceErrorDetail.appendTo("Failed", outer);

		// Then: The innermost failure is the one that was not merely observed
		assertTrue(message.contains(IAM_DENIED_TEXT), message);
		assertFalse(message.contains("observed on the way out"), message);
	}

	@Test
	void testCircularCauseChainTerminates() {
		// Given: A chain that loops back on itself
		Throwable[] pair = new Throwable[2];
		pair[0] = new Throwable("a") {
			private static final long serialVersionUID = 1L;

			@Override
			public synchronized Throwable getCause() {
				return pair[1];
			}
		};
		pair[1] = new Throwable("b") {
			private static final long serialVersionUID = 1L;

			@Override
			public synchronized Throwable getCause() {
				return pair[0];
			}
		};

		// Then: The walk should end rather than hang, adding nothing
		assertEquals("Failed", ServiceErrorDetail.appendTo("Failed", pair[0]));
	}

	@Test
	void testLongExplanationIsTruncated() {
		// Given: An explanation past the 512-character bound
		String longText = "x".repeat(900);
		Throwable cause = httpError(400, "Bad Request", "{\"error\":{\"message\":\"" + longText + "\"}}");

		// When: Building the message
		String message = ServiceErrorDetail.appendTo("Failed", cause);

		// Then: It should be cut with an ellipsis rather than carried whole
		assertTrue(message.endsWith("…)"), message);
		assertTrue(message.length() < longText.length(), "Expected truncation, got " + message.length() + " chars");
	}

	@Test
	void testBlankMessageBecomesTheExplanation() {
		// Given: No message of the driver's own
		Throwable cause = httpError(403, "Forbidden", IAM_DENIED_BODY);

		// Then: The explanation should stand alone rather than be parenthesised onto
		// nothing
		assertEquals("HTTP 403: " + IAM_DENIED_TEXT, ServiceErrorDetail.appendTo("", cause));
		assertEquals("HTTP 403: " + IAM_DENIED_TEXT, ServiceErrorDetail.appendTo(null, cause));
	}

	@Test
	void testBQSQLExceptionAppliesItToBothCauseConstructors() {
		// Given: The failure shape, thrown the two ways the driver wraps it
		Throwable cause = new IOException("Error requesting access token",
				httpError(403, "Forbidden", IAM_DENIED_BODY));

		BQSQLException withState = new BQSQLException("Error requesting access token",
				BQSQLException.SQLSTATE_GENERAL_ERROR, cause);
		BQSQLException withoutState = new BQSQLException("Error requesting access token", cause);

		// Then: Neither throw site can omit the detail, because neither performs the
		// step
		assertTrue(withState.getMessage().contains(IAM_DENIED_TEXT), withState.getMessage());
		assertTrue(withoutState.getMessage().contains(IAM_DENIED_TEXT), withoutState.getMessage());
		assertSame(cause, withState.getCause());
		assertEquals(BQSQLException.SQLSTATE_GENERAL_ERROR, withState.getSQLState());
	}

	@Test
	void testCauselessConstructorsAreUnaffected() {
		// Then: Nothing to look through, and nothing added
		assertEquals("Connection closed", new BQSQLException("Connection closed").getMessage());
		assertEquals("Connection closed",
				new BQSQLException("Connection closed", BQSQLException.SQLSTATE_CONNECTION_CLOSED).getMessage());
	}

	@Test
	void testAuthenticationFailureIsRecognisedFromTheStatus() {
		// Then: 401 and 403 are the statuses an auth endpoint rejects with
		assertTrue(ServiceErrorDetail
				.isAuthenticationFailure(new IOException("x", httpError(403, "Forbidden", IAM_DENIED_BODY))));
		assertTrue(ServiceErrorDetail.isAuthenticationFailure(httpError(401, "Unauthorized", null)));
	}

	@Test
	void testOtherStatusesAreNotAuthenticationFailures() {
		// Then: A 404 or a 500 says nothing about the credential
		assertFalse(ServiceErrorDetail.isAuthenticationFailure(httpError(404, "Not Found", null)));
		assertFalse(ServiceErrorDetail.isAuthenticationFailure(httpError(500, "Server Error", null)));
		assertFalse(ServiceErrorDetail.isAuthenticationFailure(new IOException("no http here")));
		assertFalse(ServiceErrorDetail.isAuthenticationFailure(null));
	}
}

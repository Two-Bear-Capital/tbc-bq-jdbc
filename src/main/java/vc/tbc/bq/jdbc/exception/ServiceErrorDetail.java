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

import com.google.api.client.http.HttpResponseException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Lifts the explanation a Google Cloud service gave out of the cause chain and
 * into the message a JDBC caller actually sees.
 *
 * <p>
 * The Google client libraries nest a failure several layers deep, and each
 * layer restates the outermost summary rather than the detail. A missing
 * impersonation grant arrives as
 * {@code BigQueryException: Error requesting access token} wrapping
 * {@code IOException: Error requesting access token} wrapping an
 * {@code HttpResponseException} whose body says which permission was denied on
 * which resource. Only the last of those is actionable, and it is the only one
 * a tool showing {@code SQLException.getMessage()} does not display.
 *
 * <p>
 * This does not parse to classify — nothing branches on what it finds. It reads
 * one field so the message can carry it, and every failure to read anything
 * leaves the message exactly as it was. A driver must not turn a service error
 * into a parsing error.
 *
 * @since 4.0.0
 */
public final class ServiceErrorDetail {

	/**
	 * Longest explanation appended to a message. Google's IAM errors run to about
	 * 300 characters including a troubleshooter URL, which is worth keeping whole;
	 * this bounds the pathological case without truncating the real one.
	 */
	private static final int MAX_DETAIL_LENGTH = 512;

	/**
	 * Depth limit on the cause walk. Chains this deep do not occur; the limit is
	 * here so a self-referencing or circular chain cannot hang a connection.
	 */
	private static final int MAX_CAUSE_DEPTH = 16;

	private ServiceErrorDetail() {
		throw new AssertionError("Utility class should not be instantiated");
	}

	/**
	 * Returns {@code message} with the service's own explanation appended, when the
	 * cause chain holds one.
	 *
	 * @param message
	 *            the message the driver would otherwise report
	 * @param cause
	 *            the failure being wrapped, or null
	 * @return the message, extended only when there is something to add
	 */
	public static String appendTo(String message, Throwable cause) {
		HttpResponseException http = findHttpResponse(cause);
		if (http == null) {
			return message;
		}

		// Only the body's own explanation is worth adding. Falling back to the
		// status line instead would append "(HTTP 404: Not Found)" to every missing
		// table and "(HTTP 400: Bad Request)" to every syntax error — messages that
		// already say precisely what was wrong. Noise on the common path is a worse
		// trade than silence on the rare one.
		String explanation = explanationFrom(http.getContent());
		if (explanation == null || explanation.isBlank()) {
			return message;
		}
		explanation = explanation.trim();

		// A layer that already restated the explanation must not have it appended
		// twice; this runs once per wrap, and a BQSQLException can wrap another.
		if (message != null && message.contains(explanation)) {
			return message;
		}

		String detail = "HTTP " + http.getStatusCode() + ": " + truncate(explanation);
		return message == null || message.isBlank() ? detail : message + " (" + detail + ")";
	}

	/**
	 * Walks to the deepest {@link HttpResponseException} in the chain.
	 *
	 * <p>
	 * Deepest rather than first: a retrying client can wrap one HTTP failure in
	 * another, and the innermost is the one that was not merely observed on the way
	 * out.
	 */
	private static HttpResponseException findHttpResponse(Throwable cause) {
		HttpResponseException found = null;
		Throwable current = cause;
		for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
			if (current instanceof HttpResponseException http) {
				found = http;
			}
			Throwable next = current.getCause();
			if (next == current) {
				break;
			}
			current = next;
		}
		return found;
	}

	/**
	 * Reads {@code error.message} out of a Google JSON error body.
	 *
	 * <p>
	 * Returns null for anything unexpected — a body that is not JSON, not an
	 * object, or shaped differently — so the caller falls back to the status line
	 * rather than reporting a parse failure in place of the service's error.
	 *
	 * @param body
	 *            the raw response body, or null
	 * @return the service's human-readable message, or null
	 */
	private static String explanationFrom(String body) {
		if (body == null || body.isBlank()) {
			return null;
		}
		try {
			JsonObject root = JsonParser.parseString(body).getAsJsonObject();
			JsonObject error = root.getAsJsonObject("error");
			if (error == null) {
				return null;
			}
			return error.get("message").getAsString();
		} catch (RuntimeException e) {
			// Gson signals every shape mismatch with an unchecked exception of a
			// different type (JsonSyntaxException, IllegalStateException,
			// ClassCastException, NullPointerException on an absent field). Catching
			// the supertype keeps a body Google changes the shape of from turning a
			// permission error into a driver error.
			return null;
		}
	}

	private static String truncate(String detail) {
		return detail.length() <= MAX_DETAIL_LENGTH ? detail : detail.substring(0, MAX_DETAIL_LENGTH) + "…";
	}
}

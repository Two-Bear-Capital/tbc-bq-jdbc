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
package vc.tbc.bq.jdbc.util;

/**
 * Centralized error message constants for consistent exception messages.
 */
public final class ErrorMessages {

	// Connection-related messages
	public static final String CONNECTION_CLOSED = "Connection is closed";
	public static final String STATEMENT_CLOSED = "Statement is closed";
	public static final String RESULTSET_CLOSED = "ResultSet is closed";

	// Unsupported operation messages
	public static final String RESULTSET_UPDATES_NOT_SUPPORTED = "ResultSet updates not supported";
	public static final String BATCH_UPDATES_NOT_SUPPORTED = "Batch updates not supported";
	public static final String CALLABLE_STATEMENTS_NOT_SUPPORTED = "Callable statements not supported";
	public static final String SAVEPOINTS_NOT_SUPPORTED = "Savepoints not supported";
	public static final String GENERATED_KEYS_NOT_SUPPORTED = "Generated keys not supported";
	public static final String CURSORS_NOT_SUPPORTED = "Named cursors not supported";
	public static final String HOLDABILITY_NOT_SUPPORTED = "Result set holdability configuration not supported";

	// Parameter validation messages
	public static final String INVALID_PARAMETER_INDEX = "Invalid parameter index: %d";
	public static final String NEGATIVE_TIMEOUT = "Timeout value must be non-negative";

	// Type conversion messages
	public static final String VALUE_OUT_OF_RANGE = "Value out of range for type %s: %s";

	private ErrorMessages() {
		throw new AssertionError("Utility class should not be instantiated");
	}
}

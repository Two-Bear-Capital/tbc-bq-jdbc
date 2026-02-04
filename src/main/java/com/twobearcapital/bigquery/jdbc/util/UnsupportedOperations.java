/*
 * Copyright 2026 Two Bear Capital
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.twobearcapital.bigquery.jdbc.util;

import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;

/**
 * Factory methods for common unsupported operation exceptions. Consolidates
 * repeated exception-throwing stubs across JDBC classes.
 */
public final class UnsupportedOperations {

	/**
	 * Creates exception for unsupported ResultSet update operations.
	 */
	public static SQLException resultSetUpdates() {
		return new SQLFeatureNotSupportedException(ErrorMessages.RESULTSET_UPDATES_NOT_SUPPORTED,
				SQLStates.FEATURE_NOT_SUPPORTED);
	}

	/**
	 * Creates exception for unsupported batch update operations.
	 */
	public static SQLException batchUpdates() {
		return new SQLFeatureNotSupportedException(ErrorMessages.BATCH_UPDATES_NOT_SUPPORTED,
				SQLStates.FEATURE_NOT_SUPPORTED);
	}

	/**
	 * Creates exception for unsupported callable statement operations.
	 */
	public static SQLException callableStatements() {
		return new SQLFeatureNotSupportedException(ErrorMessages.CALLABLE_STATEMENTS_NOT_SUPPORTED,
				SQLStates.FEATURE_NOT_SUPPORTED);
	}

	/**
	 * Creates exception for unsupported savepoint operations.
	 */
	public static SQLException savepoints() {
		return new SQLFeatureNotSupportedException(ErrorMessages.SAVEPOINTS_NOT_SUPPORTED,
				SQLStates.SAVEPOINT_NOT_SUPPORTED);
	}

	/**
	 * Creates exception for unsupported generated keys operations.
	 */
	public static SQLException generatedKeys() {
		return new SQLFeatureNotSupportedException(ErrorMessages.GENERATED_KEYS_NOT_SUPPORTED,
				SQLStates.FEATURE_NOT_SUPPORTED);
	}

	/**
	 * Creates exception for unsupported named cursors.
	 */
	public static SQLException namedCursors() {
		return new SQLFeatureNotSupportedException(ErrorMessages.CURSORS_NOT_SUPPORTED,
				SQLStates.FEATURE_NOT_SUPPORTED);
	}

	/**
	 * Creates exception for unsupported result set holdability.
	 */
	public static SQLException holdability() {
		return new SQLFeatureNotSupportedException(ErrorMessages.HOLDABILITY_NOT_SUPPORTED,
				SQLStates.FEATURE_NOT_SUPPORTED);
	}

	private UnsupportedOperations() {
		throw new AssertionError("Utility class should not be instantiated");
	}
}

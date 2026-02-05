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
package com.twobearcapital.bigquery.jdbc.util;

/**
 * Centralized SQLState constants for JDBC exceptions. SQLState codes follow the
 * SQL:2003 standard.
 */
public final class SQLStates {

	// Connection exceptions (08xxx)
	public static final String CONNECTION_CLOSED = "08006";

	// Feature not supported (0Axxx)
	public static final String FEATURE_NOT_SUPPORTED = "0A000";
	public static final String SAVEPOINT_NOT_SUPPORTED = "0A001";

	private SQLStates() {
		throw new AssertionError("Utility class should not be instantiated");
	}
}

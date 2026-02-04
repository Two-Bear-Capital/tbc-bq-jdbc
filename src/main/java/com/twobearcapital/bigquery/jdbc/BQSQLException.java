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
package com.twobearcapital.bigquery.jdbc;

import java.sql.SQLException;

/**
 * SQLException specific to BigQuery operations.
 *
 * @since 1.0.0
 */
public class BQSQLException extends SQLException {

	/** SQLState for syntax errors. */
	public static final String SQLSTATE_SYNTAX_ERROR = "42000";

	/** SQLState for table not found. */
	public static final String SQLSTATE_TABLE_NOT_FOUND = "42S02";

	/** SQLState for authentication failure. */
	public static final String SQLSTATE_AUTH_FAILED = "28000";

	/** SQLState for connection error. */
	public static final String SQLSTATE_CONNECTION_ERROR = "08000";

	/** SQLState for connection closed. */
	public static final String SQLSTATE_CONNECTION_CLOSED = "08006";

	/** SQLState for feature not supported. */
	public static final String SQLSTATE_FEATURE_NOT_SUPPORTED = "0A000";

	/** SQLState for invalid parameter value. */
	public static final String SQLSTATE_INVALID_PARAMETER_VALUE = "22023";

	/** SQLState for numeric value out of range. */
	public static final String SQLSTATE_NUMERIC_VALUE_OUT_OF_RANGE = "22003";

	public BQSQLException(String reason) {
		super(reason);
	}

	public BQSQLException(String reason, String sqlState) {
		super(reason, sqlState);
	}

	public BQSQLException(String reason, String sqlState, Throwable cause) {
		super(reason, sqlState, cause);
	}

	public BQSQLException(String reason, Throwable cause) {
		super(reason, cause);
	}
}

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

import java.sql.SQLException;

/**
 * SQLException specific to BigQuery operations.
 *
 * <p>
 * <b>Every constructor taking a cause runs it through
 * {@link ServiceErrorDetail}</b>, so a message reaching a JDBC caller carries
 * whatever the service said rather than the client library's summary of it.
 * That is done here rather than at the throw sites deliberately: there are a
 * dozen of them across statement execution, session management and connection
 * setup, and a new one cannot forget a step it does not perform.
 *
 * @since 1.0.0
 */
public class BQSQLException extends SQLException {

	private static final long serialVersionUID = 1L;

	/** SQLState for syntax errors. */
	public static final String SQLSTATE_SYNTAX_ERROR = "42000";

	/** SQLState for table not found. */
	public static final String SQLSTATE_TABLE_NOT_FOUND = "42S02";

	/** SQLState for an object that already exists. */
	public static final String SQLSTATE_TABLE_ALREADY_EXISTS = "42S01";

	/** SQLState for insufficient privilege on the target object. */
	public static final String SQLSTATE_INSUFFICIENT_PRIVILEGE = "42501";

	/** SQLState for exhausted quota, rate limit, or query resources. */
	public static final String SQLSTATE_INSUFFICIENT_RESOURCES = "53000";

	/** SQLState for an operation cancelled before it completed. */
	public static final String SQLSTATE_OPERATION_CANCELED = "HY008";

	/**
	 * SQLState for a failure with no more specific mapping. Used in preference to a
	 * misleading specific state such as {@link #SQLSTATE_SYNTAX_ERROR}.
	 */
	public static final String SQLSTATE_GENERAL_ERROR = "HY000";

	/** SQLState for authentication failure. */
	public static final String SQLSTATE_AUTH_FAILED = "28000";

	/** SQLState for connection error. */
	public static final String SQLSTATE_CONNECTION_ERROR = "08000";

	/** SQLState for connection closed. */
	public static final String SQLSTATE_CONNECTION_CLOSED = "08006";

	/** SQLState for feature not supported. */
	public static final String SQLSTATE_FEATURE_NOT_SUPPORTED = "0A000";

	/** SQLState for an operation attempted in an invalid transaction state. */
	public static final String SQLSTATE_INVALID_TRANSACTION_STATE = "25000";

	/** SQLState for invalid parameter value. */
	public static final String SQLSTATE_INVALID_PARAMETER_VALUE = "22023";

	/** SQLState for numeric value out of range. */
	public static final String SQLSTATE_NUMERIC_VALUE_OUT_OF_RANGE = "22003";

	/**
	 * Creates an exception with no underlying cause.
	 *
	 * @param reason
	 *            the message
	 */
	public BQSQLException(String reason) {
		super(reason);
	}

	/**
	 * Creates an exception with no underlying cause.
	 *
	 * @param reason
	 *            the message
	 * @param sqlState
	 *            the SQLState
	 */
	public BQSQLException(String reason, String sqlState) {
		super(reason, sqlState);
	}

	/**
	 * Creates an exception wrapping a cause, appending the service's own
	 * explanation to {@code reason} when the cause chain carries one.
	 *
	 * @param reason
	 *            the message
	 * @param sqlState
	 *            the SQLState
	 * @param cause
	 *            the underlying failure
	 */
	public BQSQLException(String reason, String sqlState, Throwable cause) {
		super(ServiceErrorDetail.appendTo(reason, cause), sqlState, cause);
	}

	/**
	 * Creates an exception wrapping a cause, appending the service's own
	 * explanation to {@code reason} when the cause chain carries one.
	 *
	 * @param reason
	 *            the message
	 * @param cause
	 *            the underlying failure
	 */
	public BQSQLException(String reason, Throwable cause) {
		super(ServiceErrorDetail.appendTo(reason, cause), cause);
	}
}

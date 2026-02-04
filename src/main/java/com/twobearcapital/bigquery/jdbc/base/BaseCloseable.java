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
package com.twobearcapital.bigquery.jdbc.base;

import com.twobearcapital.bigquery.jdbc.util.SQLStates;

import java.sql.SQLException;

/**
 * Base class for closeable JDBC objects (Connection, Statement, ResultSet).
 * Provides thread-safe close semantics with volatile flag and consistent
 * closed-state checking across all JDBC objects.
 */
public abstract class BaseCloseable extends BaseJdbcWrapper {

	/**
	 * Volatile flag ensures visibility across threads. Fixes thread-safety issue in
	 * BQStatement:42.
	 */
	protected volatile boolean closed = false;

	/**
	 * Checks if this object is closed and throws SQLException if it is.
	 *
	 * @throws SQLException
	 *             if the object is closed
	 */
	protected void checkClosed() throws SQLException {
		if (closed) {
			throw new SQLException(getClosedErrorMessage(), SQLStates.CONNECTION_CLOSED);
		}
	}

	/**
	 * Returns the error message to use when the object is closed. Subclasses should
	 * return specific messages like "Connection is closed", "Statement is closed",
	 * etc.
	 *
	 * @return the error message for closed state
	 */
	protected abstract String getClosedErrorMessage();

	/**
	 * Performs the actual close operation. Subclasses implement this to release
	 * resources. Called only once per object lifecycle.
	 *
	 * @throws SQLException
	 *             if an error occurs during close
	 */
	protected abstract void doClose() throws SQLException;

	/**
	 * Closes this object and releases its resources. Uses double-checked locking
	 * pattern for thread-safety. Idempotent - safe to call multiple times.
	 */
	public void close() throws SQLException {
		if (closed) {
			return; // Already closed, no-op
		}

		synchronized (this) {
			if (closed) {
				return; // Double-check after acquiring lock
			}

			try {
				doClose();
			} finally {
				closed = true; // Always mark as closed, even if doClose throws
			}
		}
	}

	/**
	 * Returns whether this object is closed.
	 *
	 * @return true if closed, false otherwise
	 */
	public boolean isClosed() throws SQLException {
		return closed;
	}
}

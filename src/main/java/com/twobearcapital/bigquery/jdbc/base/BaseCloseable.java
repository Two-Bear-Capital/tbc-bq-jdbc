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
     * Volatile flag ensures visibility across threads.
     * Fixes thread-safety issue in BQStatement:42.
     */
    protected volatile boolean closed = false;

    /**
     * Checks if this object is closed and throws SQLException if it is.
     *
     * @throws SQLException if the object is closed
     */
    protected void checkClosed() throws SQLException {
        if (closed) {
            throw new SQLException(getClosedErrorMessage(), SQLStates.CONNECTION_CLOSED);
        }
    }

    /**
     * Returns the error message to use when the object is closed.
     * Subclasses should return specific messages like "Connection is closed",
     * "Statement is closed", etc.
     *
     * @return the error message for closed state
     */
    protected abstract String getClosedErrorMessage();

    /**
     * Performs the actual close operation.
     * Subclasses implement this to release resources.
     * Called only once per object lifecycle.
     *
     * @throws SQLException if an error occurs during close
     */
    protected abstract void doClose() throws SQLException;

    /**
     * Closes this object and releases its resources.
     * Uses double-checked locking pattern for thread-safety.
     * Idempotent - safe to call multiple times.
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

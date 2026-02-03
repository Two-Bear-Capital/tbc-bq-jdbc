package com.twobearcapital.bigquery.jdbc.util;

import com.twobearcapital.bigquery.jdbc.BQSQLException;

import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;

/**
 * Factory methods for common unsupported operation exceptions.
 * Consolidates repeated exception-throwing stubs across JDBC classes.
 */
public final class UnsupportedOperations {

    /**
     * Creates exception for unsupported ResultSet update operations.
     */
    public static SQLException resultSetUpdates() {
        return new SQLFeatureNotSupportedException(
            ErrorMessages.RESULTSET_UPDATES_NOT_SUPPORTED,
            SQLStates.FEATURE_NOT_SUPPORTED
        );
    }

    /**
     * Creates exception for unsupported batch update operations.
     */
    public static SQLException batchUpdates() {
        return new SQLFeatureNotSupportedException(
            ErrorMessages.BATCH_UPDATES_NOT_SUPPORTED,
            SQLStates.FEATURE_NOT_SUPPORTED
        );
    }

    /**
     * Creates exception for unsupported callable statement operations.
     */
    public static SQLException callableStatements() {
        return new SQLFeatureNotSupportedException(
            ErrorMessages.CALLABLE_STATEMENTS_NOT_SUPPORTED,
            SQLStates.FEATURE_NOT_SUPPORTED
        );
    }

    /**
     * Creates exception for unsupported savepoint operations.
     */
    public static SQLException savepoints() {
        return new SQLFeatureNotSupportedException(
            ErrorMessages.SAVEPOINTS_NOT_SUPPORTED,
            SQLStates.SAVEPOINT_NOT_SUPPORTED
        );
    }

    /**
     * Creates exception for unsupported generated keys operations.
     */
    public static SQLException generatedKeys() {
        return new SQLFeatureNotSupportedException(
            ErrorMessages.GENERATED_KEYS_NOT_SUPPORTED,
            SQLStates.FEATURE_NOT_SUPPORTED
        );
    }

    /**
     * Creates exception for unsupported multiple result sets.
     */
    public static SQLException multipleResultSets() {
        return new SQLFeatureNotSupportedException(
            ErrorMessages.MULTIPLE_RESULT_SETS_NOT_SUPPORTED,
            SQLStates.FEATURE_NOT_SUPPORTED
        );
    }

    /**
     * Creates exception for unsupported named cursors.
     */
    public static SQLException namedCursors() {
        return new SQLFeatureNotSupportedException(
            ErrorMessages.CURSORS_NOT_SUPPORTED,
            SQLStates.FEATURE_NOT_SUPPORTED
        );
    }

    /**
     * Creates exception for unsupported result set holdability.
     */
    public static SQLException holdability() {
        return new SQLFeatureNotSupportedException(
            ErrorMessages.HOLDABILITY_NOT_SUPPORTED,
            SQLStates.FEATURE_NOT_SUPPORTED
        );
    }

    private UnsupportedOperations() {
        throw new AssertionError("Utility class should not be instantiated");
    }
}

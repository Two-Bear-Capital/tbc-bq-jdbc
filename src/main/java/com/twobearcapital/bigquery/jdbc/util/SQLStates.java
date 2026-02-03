package com.twobearcapital.bigquery.jdbc.util;

/**
 * Centralized SQLState constants for JDBC exceptions.
 * SQLState codes follow the SQL:2003 standard.
 */
public final class SQLStates {

    // Connection exceptions (08xxx)
    public static final String CONNECTION_EXCEPTION = "08000";
    public static final String CONNECTION_CLOSED = "08006";
    public static final String CONNECTION_FAILURE = "08006";

    // SQL syntax errors (42xxx)
    public static final String SYNTAX_ERROR = "42000";
    public static final String INVALID_AUTHORIZATION_SPECIFICATION = "42P01";

    // Data exceptions (22xxx)
    public static final String DATA_EXCEPTION = "22000";
    public static final String NUMERIC_VALUE_OUT_OF_RANGE = "22003";
    public static final String INVALID_DATETIME_FORMAT = "22007";
    public static final String INVALID_PARAMETER_VALUE = "22023";

    // Invalid transaction state (25xxx)
    public static final String INVALID_TRANSACTION_STATE = "25000";

    // Feature not supported (0Axxx)
    public static final String FEATURE_NOT_SUPPORTED = "0A000";
    public static final String SAVEPOINT_NOT_SUPPORTED = "0A001";

    // Invalid cursor state (24xxx)
    public static final String INVALID_CURSOR_STATE = "24000";

    // General errors (HYxxx - driver-specific)
    public static final String GENERAL_ERROR = "HY000";
    public static final String INVALID_ARGUMENT = "HY009";
    public static final String TIMEOUT_EXPIRED = "HYT00";

    private SQLStates() {
        throw new AssertionError("Utility class should not be instantiated");
    }
}

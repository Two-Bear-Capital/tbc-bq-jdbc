package com.twobearcapital.bigquery.jdbc.base;

import java.sql.SQLException;
import java.sql.Wrapper;

/**
 * Base implementation for JDBC Wrapper interface.
 * Provides standard unwrap/isWrapperFor implementation that eliminates duplication
 * across 14 JDBC classes.
 */
public abstract class BaseJdbcWrapper implements Wrapper {

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface == null) {
            throw new SQLException("Interface argument cannot be null");
        }

        if (iface.isInstance(this)) {
            return iface.cast(this);
        }

        throw new SQLException(
            "Unable to unwrap to " + iface.getName() +
            " (this object is " + this.getClass().getName() + ")"
        );
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        if (iface == null) {
            throw new SQLException("Interface argument cannot be null");
        }

        return iface.isInstance(this);
    }
}

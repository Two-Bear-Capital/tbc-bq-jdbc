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

import java.sql.SQLException;
import java.sql.Wrapper;

/**
 * Base implementation for JDBC Wrapper interface. Provides standard
 * unwrap/isWrapperFor implementation that eliminates duplication across 14 JDBC
 * classes.
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
				"Unable to unwrap to " + iface.getName() + " (this object is " + this.getClass().getName() + ")");
	}

	@Override
	public boolean isWrapperFor(Class<?> iface) throws SQLException {
		if (iface == null) {
			throw new SQLException("Interface argument cannot be null");
		}

		return iface.isInstance(this);
	}
}

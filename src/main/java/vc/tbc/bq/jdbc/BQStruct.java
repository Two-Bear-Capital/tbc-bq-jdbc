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
package vc.tbc.bq.jdbc;

import java.sql.SQLException;
import java.util.Map;

/**
 * JDBC Struct implementation for BigQuery STRUCT (RECORD) types.
 *
 * <p>
 * Returned by {@link BQResultSet#getObject(int)} when
 * {@code nativeComplexTypes=true} and the column is a RECORD/STRUCT type.
 *
 * @since 1.0.0
 */
public final class BQStruct implements java.sql.Struct {

	private final String sqlTypeName;
	private final Object[] attributes;

	/**
	 * Creates a new BQStruct.
	 *
	 * @param sqlTypeName
	 *            the full SQL type name (e.g., "STRUCT&lt;id INT64, name
	 *            STRING&gt;")
	 * @param attributes
	 *            the field values in schema order
	 */
	public BQStruct(String sqlTypeName, Object[] attributes) {
		this.sqlTypeName = sqlTypeName;
		this.attributes = attributes != null ? attributes.clone() : new Object[0];
	}

	@Override
	public String getSQLTypeName() throws SQLException {
		return sqlTypeName;
	}

	@Override
	public Object[] getAttributes() throws SQLException {
		return attributes.clone();
	}

	@Override
	public Object[] getAttributes(Map<String, Class<?>> map) throws SQLException {
		return getAttributes();
	}
}

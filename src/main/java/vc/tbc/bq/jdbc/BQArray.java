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

import vc.tbc.bq.jdbc.metadata.MetadataResultSet;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * JDBC Array implementation for BigQuery ARRAY types.
 *
 * <p>
 * Returned by {@link BQResultSet#getArray(int)} when
 * {@code nativeComplexTypes=true}.
 *
 * @since 1.0.0
 */
public final class BQArray implements java.sql.Array {

	private static final String[] RESULT_SET_COLUMN_NAMES = {"INDEX", "VALUE"};
	private static final int[] RESULT_SET_COLUMN_TYPES = {Types.BIGINT, Types.OTHER};

	private final List<Object> elements;
	private final int baseType;
	private final String baseTypeName;

	/**
	 * Creates a new BQArray.
	 *
	 * @param elements
	 *            the array elements as Java objects
	 * @param baseType
	 *            the JDBC type code for elements (from {@link java.sql.Types})
	 * @param baseTypeName
	 *            the BigQuery type name of elements (e.g., "INT64", "STRING")
	 */
	public BQArray(List<Object> elements, int baseType, String baseTypeName) {
		this.elements = elements != null ? List.copyOf(elements) : List.of();
		this.baseType = baseType;
		this.baseTypeName = baseTypeName;
	}

	@Override
	public String getBaseTypeName() throws SQLException {
		return baseTypeName;
	}

	@Override
	public int getBaseType() throws SQLException {
		return baseType;
	}

	@Override
	public Object getArray() throws SQLException {
		return elements.toArray();
	}

	@Override
	public Object getArray(Map<String, Class<?>> map) throws SQLException {
		return getArray();
	}

	@Override
	public Object getArray(long index, int count) throws SQLException {
		int start = (int) (index - 1); // JDBC uses 1-based indexing
		int end = Math.min(start + count, elements.size());
		if (start < 0 || start > elements.size()) {
			throw new SQLException("Invalid index: " + index);
		}
		return elements.subList(start, end).toArray();
	}

	@Override
	public Object getArray(long index, int count, Map<String, Class<?>> map) throws SQLException {
		return getArray(index, count);
	}

	@Override
	public ResultSet getResultSet() throws SQLException {
		List<Object[]> rows = new ArrayList<>(elements.size());
		for (int i = 0; i < elements.size(); i++) {
			rows.add(new Object[]{(long) (i + 1), elements.get(i)});
		}
		return new MetadataResultSet(RESULT_SET_COLUMN_NAMES, RESULT_SET_COLUMN_TYPES, rows);
	}

	@Override
	public ResultSet getResultSet(Map<String, Class<?>> map) throws SQLException {
		return getResultSet();
	}

	@Override
	public ResultSet getResultSet(long index, int count) throws SQLException {
		int start = (int) (index - 1); // JDBC uses 1-based indexing
		int end = Math.min(start + count, elements.size());
		if (start < 0 || start > elements.size()) {
			throw new SQLException("Invalid index: " + index);
		}
		List<Object> slice = elements.subList(start, end);
		List<Object[]> rows = new ArrayList<>(slice.size());
		for (int i = 0; i < slice.size(); i++) {
			rows.add(new Object[]{(long) (start + i + 1), slice.get(i)});
		}
		return new MetadataResultSet(RESULT_SET_COLUMN_NAMES, RESULT_SET_COLUMN_TYPES, rows);
	}

	@Override
	public ResultSet getResultSet(long index, int count, Map<String, Class<?>> map) throws SQLException {
		return getResultSet(index, count);
	}

	@Override
	public void free() throws SQLException {
		// No-op: elements list is immutable
	}
}

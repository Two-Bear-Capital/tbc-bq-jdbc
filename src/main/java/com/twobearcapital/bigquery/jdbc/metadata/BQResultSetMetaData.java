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
package com.twobearcapital.bigquery.jdbc.metadata;

import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.Schema;
import com.google.cloud.bigquery.StandardSQLTypeName;
import com.twobearcapital.bigquery.jdbc.TypeMapper;
import com.twobearcapital.bigquery.jdbc.exception.BQSQLException;
import com.twobearcapital.bigquery.jdbc.base.BaseJdbcWrapper;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Objects;

/**
 * JDBC ResultSetMetaData implementation for BigQuery.
 *
 * <p>
 * This implementation provides metadata about the columns in a BigQuery result
 * set.
 *
 * @since 1.0.0
 */
public final class BQResultSetMetaData extends BaseJdbcWrapper implements ResultSetMetaData {

	private final Schema schema;

	/**
	 * Creates a new BigQuery ResultSetMetaData.
	 *
	 * @param schema
	 *            the BigQuery schema
	 */
	public BQResultSetMetaData(Schema schema) {
		this.schema = Objects.requireNonNull(schema, "schema cannot be null");
	}

	private Field getField(int column) throws SQLException {
		if (column < 1 || column > schema.getFields().size()) {
			throw new BQSQLException("Invalid column index: " + column);
		}
		return schema.getFields().get(column - 1);
	}

	@Override
	public int getColumnCount() throws SQLException {
		return schema.getFields().size();
	}

	@Override
	public boolean isAutoIncrement(int column) throws SQLException {
		return false;
	}

	@Override
	public boolean isCaseSensitive(int column) throws SQLException {
		Field field = getField(column);
		StandardSQLTypeName type = field.getType().getStandardType();
		return type == StandardSQLTypeName.STRING || type == StandardSQLTypeName.BYTES
				|| type == StandardSQLTypeName.GEOGRAPHY || type == StandardSQLTypeName.JSON;
	}

	@Override
	public boolean isSearchable(int column) throws SQLException {
		return true;
	}

	@Override
	public boolean isCurrency(int column) throws SQLException {
		return false;
	}

	@Override
	public int isNullable(int column) throws SQLException {
		Field field = getField(column);
		return field.getMode() == Field.Mode.REQUIRED
				? ResultSetMetaData.columnNoNulls
				: ResultSetMetaData.columnNullable;
	}

	@Override
	public boolean isSigned(int column) throws SQLException {
		Field field = getField(column);
		StandardSQLTypeName type = field.getType().getStandardType();
		return type == StandardSQLTypeName.INT64 || type == StandardSQLTypeName.FLOAT64
				|| type == StandardSQLTypeName.NUMERIC || type == StandardSQLTypeName.BIGNUMERIC;
	}

	@Override
	public int getColumnDisplaySize(int column) throws SQLException {
		Field field = getField(column);
		StandardSQLTypeName type = field.getType().getStandardType();

		return switch (type) {
			case BOOL -> 5; // "false"
			case INT64 -> 20; // "-9223372036854775808"
			case FLOAT64 -> 24; // Scientific notation
			case NUMERIC -> 47; // Sign + 38 digits + decimal + 9 decimals
			case BIGNUMERIC -> 117; // Sign + 76 digits + decimal + 38 decimals
			case DATE -> 10; // YYYY-MM-DD
			case TIME -> 15; // HH:MM:SS.FFFFFF
			case DATETIME -> 26; // YYYY-MM-DD HH:MM:SS.FFFFFF
			case TIMESTAMP -> 32; // YYYY-MM-DD HH:MM:SS.FFFFFF+00:00
			default -> TypeMapper.getColumnSize(type);
		};
	}

	@Override
	public String getColumnLabel(int column) throws SQLException {
		return getField(column).getName();
	}

	@Override
	public String getColumnName(int column) throws SQLException {
		return getField(column).getName();
	}

	@Override
	public String getSchemaName(int column) throws SQLException {
		// BigQuery doesn't expose dataset name in schema
		return "";
	}

	@Override
	public int getPrecision(int column) throws SQLException {
		Field field = getField(column);
		StandardSQLTypeName type = field.getType().getStandardType();

		return switch (type) {
			case NUMERIC -> 38;
			case BIGNUMERIC -> 76;
			case INT64 -> 19;
			case FLOAT64 -> 15;
			default -> 0;
		};
	}

	@Override
	public int getScale(int column) throws SQLException {
		return TypeMapper.getDecimalDigits(getField(column).getType().getStandardType());
	}

	@Override
	public String getTableName(int column) throws SQLException {
		// BigQuery doesn't expose table name in schema
		return "";
	}

	@Override
	public String getCatalogName(int column) throws SQLException {
		// BigQuery doesn't expose project name in schema
		return "";
	}

	@Override
	public int getColumnType(int column) throws SQLException {
		return TypeMapper.toJdbcType(getField(column));
	}

	@Override
	public String getColumnTypeName(int column) throws SQLException {
		Field field = getField(column);
		StandardSQLTypeName type = field.getType().getStandardType();

		if (type == StandardSQLTypeName.STRUCT) {
			// For STRUCT, return the full type definition
			return "STRUCT<" + formatStructFields(field.getSubFields()) + ">";
		} else if (type == StandardSQLTypeName.ARRAY) {
			// For ARRAY, return the element type
			Field elementField = field.getSubFields().getFirst();
			return "ARRAY<" + elementField.getType().getStandardType().name() + ">";
		}

		return type != null ? type.name() : "UNKNOWN";
	}

	private String formatStructFields(com.google.cloud.bigquery.FieldList fields) {
		if (fields == null || fields.isEmpty()) {
			return "";
		}

		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < fields.size(); i++) {
			if (i > 0) {
				sb.append(", ");
			}
			Field field = fields.get(i);
			sb.append(field.getName()).append(" ").append(field.getType().getStandardType().name());
		}
		return sb.toString();
	}

	@Override
	public boolean isReadOnly(int column) throws SQLException {
		return true;
	}

	@Override
	public boolean isWritable(int column) throws SQLException {
		return false;
	}

	@Override
	public boolean isDefinitelyWritable(int column) throws SQLException {
		return false;
	}

	@Override
	public String getColumnClassName(int column) throws SQLException {
		Field field = getField(column);
		return TypeMapper.toJavaClassName(field.getType().getStandardType());
	}

}

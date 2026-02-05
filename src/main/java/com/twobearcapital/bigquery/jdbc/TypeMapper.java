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
package com.twobearcapital.bigquery.jdbc;

import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.StandardSQLTypeName;
import java.sql.Types;

/**
 * Utility class for mapping between BigQuery types and JDBC types.
 *
 * @since 1.0.0
 */
public final class TypeMapper {

	private TypeMapper() {
		// Utility class
	}

	/**
	 * Maps a BigQuery StandardSQLTypeName to a JDBC type constant.
	 *
	 * @param bqType
	 *            the BigQuery type
	 * @return the JDBC type constant
	 */
	public static int toJdbcType(StandardSQLTypeName bqType) {
		if (bqType == null) {
			return Types.OTHER;
		}

		return switch (bqType) {
			case STRING -> Types.VARCHAR;
			case BYTES -> Types.VARBINARY;
			case INT64 -> Types.BIGINT;
			case FLOAT64 -> Types.DOUBLE;
			case NUMERIC, BIGNUMERIC -> Types.NUMERIC;
			case BOOL -> Types.BOOLEAN;
			case TIMESTAMP, DATETIME -> Types.TIMESTAMP;
			case DATE -> Types.DATE;
			case TIME -> Types.TIME;
			case GEOGRAPHY, JSON, INTERVAL -> Types.VARCHAR; // Represented as WKT/JSON/string
			case STRUCT -> Types.STRUCT;
			case ARRAY -> Types.ARRAY;
			default -> Types.OTHER;
		};
	}

	/**
	 * Maps a BigQuery Field to a JDBC type constant.
	 *
	 * @param field
	 *            the BigQuery field
	 * @return the JDBC type constant
	 */
	public static int toJdbcType(Field field) {
		if (field == null) {
			return Types.OTHER;
		}
		return toJdbcType(field.getType().getStandardType());
	}

	/**
	 * Maps a BigQuery type to a Java class name.
	 *
	 * @param bqType
	 *            the BigQuery type
	 * @return the Java class name
	 */
	public static String toJavaClassName(StandardSQLTypeName bqType) {
		if (bqType == null) {
			return Object.class.getName();
		}

		return switch (bqType) {
			case STRING, GEOGRAPHY, JSON, INTERVAL -> String.class.getName();
			case BYTES -> byte[].class.getName();
			case INT64 -> Long.class.getName();
			case FLOAT64 -> Double.class.getName();
			case NUMERIC, BIGNUMERIC -> java.math.BigDecimal.class.getName();
			case BOOL -> Boolean.class.getName();
			case TIMESTAMP, DATETIME -> java.sql.Timestamp.class.getName();
			case DATE -> java.sql.Date.class.getName();
			case TIME -> java.sql.Time.class.getName();
			case STRUCT -> java.util.Map.class.getName();
			case ARRAY -> java.util.List.class.getName();
			default -> Object.class.getName();
		};
	}

	/**
	 * Gets the column size for a BigQuery type.
	 *
	 * @param bqType
	 *            the BigQuery type
	 * @return the column size
	 */
	public static int getColumnSize(StandardSQLTypeName bqType) {
		if (bqType == null) {
			return 0;
		}

		return switch (bqType) {
			case STRING -> 2097152; // 2MB max
			case BYTES -> 10485760; // 10MB max
			case INT64 -> 19; // digits in Long.MAX_VALUE
			case FLOAT64 -> 15; // precision
			case NUMERIC -> 38; // precision
			case BIGNUMERIC -> 76; // precision
			case BOOL -> 1;
			case DATE -> 10; // YYYY-MM-DD
			case TIME -> 12; // HH:MM:SS.FFF
			case DATETIME, TIMESTAMP -> 26; // YYYY-MM-DD HH:MM:SS.FFFFFF
			case GEOGRAPHY, JSON, INTERVAL -> 2097152; // Max string size
			default -> 0;
		};
	}

	/**
	 * Gets the decimal digits (scale) for a BigQuery type.
	 *
	 * @param bqType
	 *            the BigQuery type
	 * @return the decimal digits
	 */
	public static int getDecimalDigits(StandardSQLTypeName bqType) {
		if (bqType == null) {
			return 0;
		}

		return switch (bqType) {
			case NUMERIC -> 9;
			case BIGNUMERIC -> 38;
			case FLOAT64 -> 15;
			default -> 0;
		};
	}

}

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
package vc.tbc.bq.jdbc.storage;

import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.FieldList;
import com.google.cloud.bigquery.FieldValue;
import com.google.cloud.bigquery.FieldValueList;
import com.google.cloud.bigquery.Schema;
import com.google.cloud.bigquery.StandardSQLTypeName;
import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.VectorSchemaRoot;
import vc.tbc.bq.jdbc.exception.BQSQLException;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Turns one row of an Arrow record batch into the {@link FieldValueList} the
 * rest of the driver already knows how to read.
 *
 * <p>
 * <b>Why go through {@code FieldValue} rather than reading Arrow vectors
 * straight into the JDBC getters?</b> Because the getters carry a lot of
 * hard-won behaviour — {@code getInt} on a FLOAT64 truncates, NUMERIC goes
 * through {@link BigDecimal}, out-of-range values raise a specific SQLState,
 * conversion failures are wrapped rather than leaked. Re-implementing that
 * against Arrow would duplicate roughly twenty-five accessors and invite the
 * two paths to drift apart, which is exactly the class of bug a driver can
 * least afford. By producing the same {@code FieldValue} representation the
 * REST path produces, the Storage Read API path inherits all of it and cannot
 * diverge by construction.
 *
 * <p>
 * The contract that makes that work is narrow and strict: <b>every string
 * produced here must match what {@code tabledata.list} would have returned for
 * the same value</b>, byte for byte, with one carefully bounded exception noted
 * below. Where BigQuery's REST encoding is merely odd, this class reproduces
 * the oddity rather than improving on it — a value that reads a given way over
 * REST must read the same way here. {@code StorageApiParityTest} enforces that
 * by running the same query down both paths and comparing every cell.
 *
 * <p>
 * Two encodings are easy to get wrong, and both were caught by that parity test
 * rather than by reading documentation:
 *
 * <ul>
 * <li><b>NUMERIC</b> arrives from Arrow at its full declared scale
 * ({@code -2.000000000}) where REST strips trailing zeros ({@code -2}).
 * <li><b>TIMESTAMP</b> is fractional epoch seconds, not microseconds.
 * </ul>
 *
 * <p>
 * FLOAT64 and TIMESTAMP used to be a documented exception to byte parity,
 * because REST renders both by printing a {@code double} and does not print it
 * the way Java does. That is no longer true: {@code getString} now renders both
 * from the parsed value in {@code FieldValueConverter}, so the two paths agree
 * exactly and the more accurate form wins. What this class stores for those two
 * types therefore only has to parse back correctly, which is why it keeps the
 * exact microsecond rather than imitating REST's lossy rendering.
 *
 * @since 2.4.0
 */
final class ArrowRowConverter {

	/**
	 * Types this converter can encode. Anything outside this set sends the query
	 * down the REST path instead — see {@link #isSupported(Schema)}.
	 *
	 * <p>
	 * Deliberately scalars only. ARRAY and STRUCT arrive as nested Arrow vectors
	 * whose JSON encoding would have to match the REST path's exactly, and INTERVAL
	 * is not supported by the Storage Read API at all. None of them are what the
	 * feature exists for — large scans of flat data — so they are excluded rather
	 * than half-supported.
	 */
	private static final Set<StandardSQLTypeName> SUPPORTED = EnumSet.of(StandardSQLTypeName.INT64,
			StandardSQLTypeName.FLOAT64, StandardSQLTypeName.NUMERIC, StandardSQLTypeName.BIGNUMERIC,
			StandardSQLTypeName.BOOL, StandardSQLTypeName.STRING, StandardSQLTypeName.BYTES, StandardSQLTypeName.DATE,
			StandardSQLTypeName.TIME, StandardSQLTypeName.DATETIME, StandardSQLTypeName.TIMESTAMP,
			StandardSQLTypeName.GEOGRAPHY, StandardSQLTypeName.JSON);

	private final FieldList fields;

	ArrowRowConverter(Schema schema) {
		this.fields = schema.getFields();
	}

	/**
	 * Whether every column in the schema can be encoded by this converter.
	 *
	 * @param schema
	 *            the result schema, may be null
	 * @return true if the Storage Read API path can serve this result
	 */
	static boolean isSupported(Schema schema) {
		if (schema == null || schema.getFields() == null || schema.getFields().isEmpty()) {
			return false;
		}
		for (Field field : schema.getFields()) {
			if (field.getMode() == Field.Mode.REPEATED) {
				return false;
			}
			StandardSQLTypeName type = field.getType() == null ? null : field.getType().getStandardType();
			if (type == null || !SUPPORTED.contains(type)) {
				return false;
			}
			// A RECORD/STRUCT reports a supported-looking type only if it has no
			// subfields, which should not happen; treat any nesting as unsupported.
			if (field.getSubFields() != null && !field.getSubFields().isEmpty()) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Converts one row of the loaded batch.
	 *
	 * @param root
	 *            the batch, already loaded
	 * @param rowIndex
	 *            the row within the batch
	 * @return the row in the driver's standard representation
	 * @throws SQLException
	 *             if a value cannot be encoded
	 */
	FieldValueList convert(VectorSchemaRoot root, int rowIndex) throws SQLException {
		List<FieldValue> values = new ArrayList<>(fields.size());
		for (int column = 0; column < fields.size(); column++) {
			Field field = fields.get(column);
			FieldVector vector = root.getVector(field.getName());
			if (vector == null) {
				throw new BQSQLException("Storage Read API returned no column named '" + field.getName() + "'");
			}
			values.add(toFieldValue(vector, rowIndex, field));
		}
		return FieldValueList.of(values, fields);
	}

	private static FieldValue toFieldValue(FieldVector vector, int rowIndex, Field field) throws SQLException {
		if (vector.isNull(rowIndex)) {
			return FieldValue.of(FieldValue.Attribute.PRIMITIVE, null);
		}
		Object raw = vector.getObject(rowIndex);
		StandardSQLTypeName type = field.getType().getStandardType();
		return FieldValue.of(FieldValue.Attribute.PRIMITIVE, encode(raw, type, field));
	}

	/** Encodes a value the way {@code tabledata.list} would have returned it. */
	private static String encode(Object raw, StandardSQLTypeName type, Field field) throws SQLException {
		return switch (type) {
			case INT64 -> raw.toString();
			case FLOAT64 -> raw.toString();
			case NUMERIC, BIGNUMERIC -> formatNumeric(asBigDecimal(raw, field));
			case BOOL -> Boolean.TRUE.equals(asBoolean(raw, field)) ? "true" : "false";
			case STRING, GEOGRAPHY, JSON -> raw.toString();
			case BYTES -> Base64.getEncoder().encodeToString(asBytes(raw, field));
			case DATE -> asLocalDate(raw, field).toString();
			case TIME -> formatTime(asLocalTime(raw, field));
			case DATETIME -> formatDateTime(asLocalDateTime(raw, field));
			case TIMESTAMP -> formatTimestamp(toEpochMicros(raw, field));
			default -> throw unsupported(field, type);
		};
	}

	/**
	 * Arrow reports a NUMERIC at its full declared scale ({@code -2.000000000});
	 * BigQuery's REST encoding strips trailing zeros ({@code -2}). Since the two
	 * forms parse to equal {@link BigDecimal}s but differ under {@code getString},
	 * the REST form is the one to reproduce.
	 */
	private static String formatNumeric(BigDecimal value) {
		return value.stripTrailingZeros().toPlainString();
	}

	/**
	 * Delegates to the one renderer both paths share, so the text a TIMESTAMP is
	 * stored as cannot drift between them.
	 */
	private static String formatTimestamp(long epochMicros) {
		return vc.tbc.bq.jdbc.util.FieldValueConverter.epochSeconds(epochMicros);
	}

	/**
	 * BigQuery renders TIME as {@code HH:MM:SS}, appending a fractional part only
	 * when there is one. {@link LocalTime#toString()} would drop the seconds
	 * entirely at exactly midnight-style values ({@code "10:15"}), which is not a
	 * form the REST path ever produces.
	 */
	private static String formatTime(LocalTime time) {
		int micros = time.getNano() / 1000;
		String base = String.format("%02d:%02d:%02d", time.getHour(), time.getMinute(), time.getSecond());
		return micros == 0 ? base : base + String.format(".%06d", micros);
	}

	/** BigQuery renders DATETIME as {@code yyyy-MM-ddTHH:MM:SS[.ffffff]}. */
	private static String formatDateTime(LocalDateTime dateTime) {
		return dateTime.toLocalDate() + "T" + formatTime(dateTime.toLocalTime());
	}

	private static long toEpochMicros(Object raw, Field field) throws SQLException {
		if (raw instanceof Long micros) {
			return micros;
		}
		if (raw instanceof LocalDateTime dateTime) {
			// Arrow hands back a LocalDateTime for some timestamp vector variants; the
			// Storage API always emits UTC for TIMESTAMP.
			return dateTime.toInstant(java.time.ZoneOffset.UTC).toEpochMilli() * 1000L
					+ dateTime.getNano() % 1_000_000 / 1000;
		}
		throw unexpected(field, raw, "a timestamp");
	}

	private static BigDecimal asBigDecimal(Object raw, Field field) throws SQLException {
		if (raw instanceof BigDecimal decimal) {
			return decimal;
		}
		throw unexpected(field, raw, "a decimal");
	}

	private static Boolean asBoolean(Object raw, Field field) throws SQLException {
		if (raw instanceof Boolean bool) {
			return bool;
		}
		// BitVector hands back an Integer 0/1 in some Arrow versions.
		if (raw instanceof Number number) {
			return number.intValue() != 0;
		}
		throw unexpected(field, raw, "a boolean");
	}

	private static byte[] asBytes(Object raw, Field field) throws SQLException {
		if (raw instanceof byte[] bytes) {
			return bytes;
		}
		throw unexpected(field, raw, "bytes");
	}

	private static LocalDate asLocalDate(Object raw, Field field) throws SQLException {
		if (raw instanceof LocalDate date) {
			return date;
		}
		// DateDayVector hands back days since the epoch as an Integer.
		if (raw instanceof Number days) {
			return LocalDate.ofEpochDay(days.longValue());
		}
		throw unexpected(field, raw, "a date");
	}

	private static LocalTime asLocalTime(Object raw, Field field) throws SQLException {
		if (raw instanceof LocalTime time) {
			return time;
		}
		// TimeMicroVector hands back microseconds since midnight as a Long.
		if (raw instanceof Number micros) {
			return LocalTime.ofNanoOfDay(micros.longValue() * 1000L);
		}
		throw unexpected(field, raw, "a time");
	}

	private static LocalDateTime asLocalDateTime(Object raw, Field field) throws SQLException {
		if (raw instanceof LocalDateTime dateTime) {
			return dateTime;
		}
		if (raw instanceof Number micros) {
			long value = micros.longValue();
			return LocalDateTime.ofEpochSecond(Math.floorDiv(value, 1_000_000L),
					(int) Math.floorMod(value, 1_000_000L) * 1000, java.time.ZoneOffset.UTC);
		}
		throw unexpected(field, raw, "a datetime");
	}

	private static SQLException unsupported(Field field, StandardSQLTypeName type) {
		return new BQSQLException(
				"Storage Read API path cannot encode column '" + field.getName() + "' of type " + type
						+ ". This should have been caught by isSupported(); please report it as a bug.",
				BQSQLException.SQLSTATE_INVALID_PARAMETER_VALUE);
	}

	private static SQLException unexpected(Field field, Object raw, String expected) {
		return new BQSQLException(
				"Storage Read API returned " + (raw == null ? "null" : raw.getClass().getName()) + " for column '"
						+ field.getName() + "', which is not " + expected,
				BQSQLException.SQLSTATE_INVALID_PARAMETER_VALUE);
	}
}

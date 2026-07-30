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
import com.google.cloud.bigquery.FieldElementType;
import com.google.cloud.bigquery.FieldList;
import com.google.cloud.bigquery.FieldValue;
import com.google.cloud.bigquery.FieldValueList;
import com.google.cloud.bigquery.Range;
import com.google.cloud.bigquery.Schema;
import com.google.cloud.bigquery.StandardSQLTypeName;
import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.PeriodDuration;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.complex.ListVector;
import org.apache.arrow.vector.complex.StructVector;
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
 * Three encodings are easy to get wrong, and all three were caught by that
 * parity test rather than by reading documentation:
 *
 * <ul>
 * <li><b>NUMERIC</b> arrives from Arrow at its full declared scale
 * ({@code -2.000000000}) where REST strips trailing zeros ({@code -2}).
 * <li><b>TIMESTAMP</b> is fractional epoch seconds, not microseconds.
 * <li><b>INTERVAL</b> fractional seconds are padded to a whole number of
 * milliseconds: 700000µs is {@code .700} and 10µs is {@code .000010}, never
 * {@code .7} or {@code .00001}.
 * </ul>
 * 
 * <p>
 * <b>ARRAY and STRUCT are built by recursion, not from Arrow's own
 * rendering.</b> A list or struct vector will hand back a
 * {@code JsonStringArrayList} or {@code JsonStringHashMap} whose
 * {@code toString()} already resembles JSON, and reaching for it would bypass
 * every encoding above — the scalars inside would be rendered by Arrow rather
 * than by this class, which is precisely the drift the design exists to
 * prevent. Nesting is walked against the BigQuery schema instead, which also
 * fixes member order rather than trusting a map's.
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
	 * ARRAY and STRUCT are handled by recursion rather than by a type of their own:
	 * a repeated field's mode and a struct's subfields say what to do, and the
	 * element type still has to appear here.
	 *
	 * <p>
	 * {@code RANGE} was the last one left out, for want of a {@code FieldValue}
	 * shape the REST path agreed on — {@code getString} on one used to throw. Once
	 * the REST path settled on the {@code [start, end)} literal (#238), the shape
	 * to reproduce existed: a {@code FieldValue} holding a {@link Range}, which
	 * both paths then render through the same code.
	 */
	private static final Set<StandardSQLTypeName> SUPPORTED = EnumSet.of(StandardSQLTypeName.INT64,
			StandardSQLTypeName.FLOAT64, StandardSQLTypeName.NUMERIC, StandardSQLTypeName.BIGNUMERIC,
			StandardSQLTypeName.BOOL, StandardSQLTypeName.STRING, StandardSQLTypeName.BYTES, StandardSQLTypeName.DATE,
			StandardSQLTypeName.TIME, StandardSQLTypeName.DATETIME, StandardSQLTypeName.TIMESTAMP,
			StandardSQLTypeName.GEOGRAPHY, StandardSQLTypeName.JSON, StandardSQLTypeName.INTERVAL,
			StandardSQLTypeName.STRUCT, StandardSQLTypeName.RANGE);

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
			if (!isSupported(field)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Whether one field, and everything nested inside it, can be encoded.
	 *
	 * <p>
	 * Recursive because a struct's support is its members' support: an
	 * {@code ARRAY<STRUCT<a INT64, b RANGE<DATE>>>} is unencodable for a reason
	 * three levels down, and the path is all-or-nothing per result.
	 */
	private static boolean isSupported(Field field) {
		StandardSQLTypeName type = field.getType() == null ? null : field.getType().getStandardType();
		if (type == null || !SUPPORTED.contains(type)) {
			return false;
		}
		if (type == StandardSQLTypeName.RANGE) {
			// A range's support is its element type's. BigQuery allows only DATE,
			// DATETIME and TIMESTAMP, all three of which encode, but reading the
			// declared type rather than assuming keeps this honest if that widens.
			FieldElementType element = field.getRangeElementType();
			if (element == null || element.getType() == null) {
				return false;
			}
			try {
				return SUPPORTED.contains(StandardSQLTypeName.valueOf(element.getType()));
			} catch (IllegalArgumentException e) {
				return false;
			}
		}
		FieldList subFields = field.getSubFields();
		if (type == StandardSQLTypeName.STRUCT) {
			// A struct with no members is not something BigQuery produces, and an
			// empty FieldValueList would be indistinguishable from a null.
			if (subFields == null || subFields.isEmpty()) {
				return false;
			}
			for (Field subField : subFields) {
				if (!isSupported(subField)) {
					return false;
				}
			}
			return true;
		}
		// A non-struct carrying subfields is a shape this converter does not know;
		// refusing it is cheaper than discovering it row by row.
		return subFields == null || subFields.isEmpty();
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
		for (Field field : fields) {
			FieldVector vector = root.getVector(field.getName());
			if (vector == null) {
				throw new BQSQLException("Storage Read API returned no column named '" + field.getName() + "'");
			}
			values.add(toFieldValue(vector, rowIndex, field));
		}
		return FieldValueList.of(values, fields);
	}

	/**
	 * Encodes one value, recursing into arrays and structs.
	 *
	 * <p>
	 * <b>Nested values are built from the BigQuery schema, not from
	 * {@code vector.getObject()}.</b> Arrow will happily hand back a
	 * {@code JsonStringArrayList} or {@code JsonStringHashMap} whose
	 * {@code toString()} already looks like JSON, and using it would be a trap
	 * twice over: the map gives no guarantee about member order, where the REST
	 * path's order is the schema's; and its rendering is Arrow's, not BigQuery's,
	 * so every scalar inside would bypass the encodings above that exist precisely
	 * because the two differ. Walking the child vectors keeps every leaf going
	 * through {@link #encode}.
	 */
	private static FieldValue toFieldValue(FieldVector vector, int rowIndex, Field field) throws SQLException {
		if (field.getMode() == Field.Mode.REPEATED) {
			// An array is never null in BigQuery, only empty, and that is what the
			// REST path reports — so this returns a REPEATED value either way.
			return FieldValue.of(FieldValue.Attribute.REPEATED, arrayElements(vector, rowIndex, field));
		}
		if (vector.isNull(rowIndex)) {
			return FieldValue.of(FieldValue.Attribute.PRIMITIVE, null);
		}
		if (field.getType().getStandardType() == StandardSQLTypeName.STRUCT) {
			return FieldValue.of(FieldValue.Attribute.RECORD, structMembers(vector, rowIndex, field));
		}
		if (field.getType().getStandardType() == StandardSQLTypeName.RANGE) {
			return FieldValue.of(FieldValue.Attribute.PRIMITIVE, range(vector, rowIndex, field));
		}
		Object raw = vector.getObject(rowIndex);
		StandardSQLTypeName type = field.getType().getStandardType();
		return FieldValue.of(FieldValue.Attribute.PRIMITIVE, encode(raw, type, field));
	}

	/**
	 * The elements of a repeated field, read out of the list vector's data vector.
	 *
	 * <p>
	 * Each element is encoded as the same field with its repetition dropped, so an
	 * {@code ARRAY<STRUCT<…>>} recurses into the struct case and an
	 * {@code ARRAY<INT64>} into the scalar one.
	 */
	private static FieldValueList arrayElements(FieldVector vector, int rowIndex, Field field) throws SQLException {
		if (!(vector instanceof ListVector list)) {
			throw unexpected(field, vector, "a list vector");
		}
		Field element = elementField(field);
		FieldVector data = list.getDataVector();
		int start = list.getElementStartIndex(rowIndex);
		int end = list.getElementEndIndex(rowIndex);

		List<FieldValue> values = new ArrayList<>(end - start);
		for (int i = start; i < end; i++) {
			values.add(toFieldValue(data, i, element));
		}
		// Elements are positional and unnamed, which is what FieldValueList.of with
		// no schema produces — the same shape the REST path builds for an array.
		return FieldValueList.of(values);
	}

	/**
	 * The members of a struct, in the schema's order, read from the struct vector's
	 * children by name.
	 */
	private static FieldValueList structMembers(FieldVector vector, int rowIndex, Field field) throws SQLException {
		if (!(vector instanceof StructVector struct)) {
			throw unexpected(field, vector, "a struct vector");
		}
		FieldList subFields = field.getSubFields();
		List<FieldValue> values = new ArrayList<>(subFields.size());
		for (Field subField : subFields) {
			FieldVector child = struct.getChild(subField.getName());
			if (child == null) {
				throw new BQSQLException("Storage Read API returned no member named '" + subField.getName()
						+ "' inside struct column '" + field.getName() + "'");
			}
			values.add(toFieldValue(child, rowIndex, subField));
		}
		// With the schema attached, getRecordValue() can be indexed by name, which
		// is what FieldValueConverter does when rendering a struct.
		return FieldValueList.of(values, subFields);
	}

	/**
	 * Reads a RANGE, which Arrow delivers as a struct of {@code start} and
	 * {@code end}.
	 *
	 * <p>
	 * The result is a {@link Range} rather than a string, which is the whole point:
	 * the REST path's {@code FieldValue} holds one too, so
	 * {@code FieldValueConverter} renders both paths through the same code and
	 * {@code StorageApiParityTest} needs no exemption for this type. Encoding a
	 * literal here instead would put a second renderer in the driver and invite the
	 * two to drift.
	 *
	 * <p>
	 * Endpoints go through {@link #encode} as the range's element type, so a
	 * TIMESTAMP bound is canonicalised the same way a TIMESTAMP column is. An
	 * absent bound is left unset, which is how the client represents
	 * {@code UNBOUNDED}.
	 */
	private static Range range(FieldVector vector, int rowIndex, Field field) throws SQLException {
		if (!(vector instanceof StructVector struct)) {
			throw unexpected(field, vector, "a struct vector");
		}
		FieldElementType element = field.getRangeElementType();
		StandardSQLTypeName elementType = StandardSQLTypeName.valueOf(element.getType());

		Range.Builder builder = Range.newBuilder().setType(element);
		builder.setStart(rangeBound(struct, rowIndex, field, "start", elementType));
		builder.setEnd(rangeBound(struct, rowIndex, field, "end", elementType));
		return builder.build();
	}

	/**
	 * One endpoint of a range, or null when the bound is absent.
	 *
	 * @return the encoded bound as the client's builder wants it, or null for
	 *         {@code UNBOUNDED}
	 */
	private static String rangeBound(StructVector struct, int rowIndex, Field field, String name,
			StandardSQLTypeName elementType) throws SQLException {
		FieldVector child = struct.getChild(name);
		if (child == null) {
			throw new BQSQLException(
					"Storage Read API returned no '" + name + "' bound inside RANGE column '" + field.getName() + "'");
		}
		if (child.isNull(rowIndex)) {
			return null;
		}
		Object encoded = encode(child.getObject(rowIndex), elementType, field);
		return encoded == null ? null : encoded.toString();
	}

	/**
	 * A repeated field seen as its element type.
	 *
	 * <p>
	 * BigQuery models {@code ARRAY<T>} as a field of type {@code T} in
	 * {@code REPEATED} mode rather than as a distinct array type, so the element
	 * field is this field with its mode cleared. Rebuilt rather than mutated
	 * because {@link Field} is immutable.
	 */
	private static Field elementField(Field field) {
		Field.Builder builder = field.toBuilder().setMode(Field.Mode.NULLABLE);
		return builder.build();
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
			case INTERVAL -> formatInterval(asPeriodDuration(raw, field));
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

	/**
	 * Renders an INTERVAL the way BigQuery does: {@code Y-M D H:M:S[.ffffff]}.
	 *
	 * <p>
	 * Three independently signed parts, not one signed quantity —
	 * {@code -1-2 3 -4:5:6} is a legal value, and normalising it into a single sign
	 * would change what it means. Arrow's {@link PeriodDuration} keeps the same
	 * split, so each part is rendered from its own source: months from the period,
	 * days from the period, and the rest from the duration.
	 *
	 * <p>
	 * No zero padding, which is BigQuery's rendering and not an oversight —
	 * {@code INTERVAL 1 YEAR} reads back as {@code 1-0 0 0:0:0}.
	 */
	private static String formatInterval(PeriodDuration value) {
		java.time.Period period = value.getPeriod();
		java.time.Duration duration = value.getDuration();

		long totalMonths = period.toTotalMonths();
		long yearMonthSign = totalMonths < 0 ? -1 : 1;
		long absMonths = Math.abs(totalMonths);

		long nanos = duration.toNanos();
		boolean timeNegative = nanos < 0;
		long absNanos = Math.abs(nanos);
		long hours = absNanos / 3_600_000_000_000L;
		long minutes = absNanos / 60_000_000_000L % 60;
		long seconds = absNanos / 1_000_000_000L % 60;
		long micros = absNanos / 1_000L % 1_000_000L;

		StringBuilder text = new StringBuilder();
		if (yearMonthSign < 0) {
			text.append('-');
		}
		text.append(absMonths / 12).append('-').append(absMonths % 12);
		text.append(' ').append(period.getDays());
		text.append(' ');
		if (timeNegative) {
			text.append('-');
		}
		text.append(hours).append(':').append(minutes).append(':').append(seconds);
		if (micros != 0) {
			text.append(trimFraction(micros));
		}
		return text.toString();
	}

	/**
	 * The fractional-seconds part of an INTERVAL, in the form BigQuery's REST
	 * encoding uses: trailing zeros dropped, then padded back out to a whole number
	 * of milliseconds.
	 *
	 * <p>
	 * So 700000µs is {@code .700} and 10µs is {@code .000010} — three digits or
	 * six, never one, four or five. Both examples are measured against REST, not
	 * inferred: the parity test rejected {@code .00001} for the second, which is
	 * what {@code stripTrailingZeros} alone would have produced.
	 */
	private static String trimFraction(long micros) {
		String digits = String.format("%06d", micros);
		int significant = digits.length();
		while (significant > 1 && digits.charAt(significant - 1) == '0') {
			significant--;
		}
		int padded = (significant + 2) / 3 * 3;
		return "." + digits.substring(0, padded);
	}

	private static PeriodDuration asPeriodDuration(Object raw, Field field) throws SQLException {
		if (raw instanceof PeriodDuration periodDuration) {
			return periodDuration;
		}
		throw unexpected(field, raw, "an interval");
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

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
import com.google.cloud.bigquery.FieldValueList;
import com.google.cloud.bigquery.Schema;
import com.google.cloud.bigquery.StandardSQLTypeName;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the exact text {@link ArrowRowConverter} produces for each type.
 *
 * <p>
 * The converter's whole contract is that its output is indistinguishable from
 * what the REST path delivers, so that every JDBC getter can be inherited
 * rather than reimplemented. {@code StorageApiParityIT} proves that against
 * real BigQuery, but it needs credentials and a network round trip, which makes
 * it a slow way to find out that a one-character format change broke every
 * timestamp.
 *
 * <p>
 * These tests build Arrow vectors in memory and assert the encoded string
 * directly, so the two encodings that were actually wrong during development —
 * NUMERIC keeping its trailing zeros, and TIMESTAMP being carried as
 * microseconds instead of fractional seconds — now fail in milliseconds without
 * touching the network.
 *
 * <p>
 * Requires {@code --add-opens=java.base/java.nio=ALL-UNNAMED}, which surefire
 * passes; without it Arrow cannot allocate and these tests are skipped rather
 * than failing misleadingly.
 */
class ArrowRowConverterEncodingTest {

	private BufferAllocator allocator;

	@BeforeEach
	void setUp() {
		assertTrue(ArrowSupport.isUsable(),
				"Arrow cannot allocate in this JVM; surefire should pass --add-opens=java.base/java.nio=ALL-UNNAMED");
		allocator = new RootAllocator(Long.MAX_VALUE);
	}

	@AfterEach
	void tearDown() {
		if (allocator != null) {
			allocator.close();
		}
	}

	/** Converts a single-row, single-column batch and returns the encoded text. */
	private String encode(Field field, FieldVector vector) throws SQLException {
		vector.setValueCount(1);
		try (VectorSchemaRoot root = new VectorSchemaRoot(List.of(vector))) {
			root.setRowCount(1);
			FieldValueList row = new ArrowRowConverter(Schema.of(field)).convert(root, 0);
			return row.get(0).isNull() ? null : row.get(0).getStringValue();
		}
	}

	private static Field field(String name, StandardSQLTypeName type) {
		return Field.of(name, type);
	}

	@Test
	@DisplayName("INT64 is the plain decimal")
	void int64() throws SQLException {
		BigIntVector v = new BigIntVector("c", allocator);
		v.allocateNew(1);
		v.set(0, -9223372036854775808L);
		assertEquals("-9223372036854775808", encode(field("c", StandardSQLTypeName.INT64), v));
	}

	@Test
	@DisplayName("NUMERIC strips trailing zeros, as REST does")
	void numericStripsTrailingZeros() throws SQLException {
		// The bug this pins: Arrow reports a NUMERIC at its full declared scale,
		// so -2 arrived as "-2.000000000" and differed from the REST path.
		DecimalVector v = new DecimalVector("c", allocator, 38, 9);
		v.allocateNew(1);
		v.set(0, new BigDecimal("-2.000000000"));
		assertEquals("-2", encode(field("c", StandardSQLTypeName.NUMERIC), v));
	}

	@Test
	@DisplayName("NUMERIC keeps significant decimals")
	void numericKeepsSignificantDigits() throws SQLException {
		DecimalVector v = new DecimalVector("c", allocator, 38, 9);
		v.allocateNew(1);
		v.set(0, new BigDecimal("-2.857142857"));
		assertEquals("-2.857142857", encode(field("c", StandardSQLTypeName.NUMERIC), v));
	}

	@Test
	@DisplayName("TIMESTAMP is fractional epoch seconds, exact to the microsecond")
	void timestampIsFractionalEpochSeconds() throws SQLException {
		// The bug this pins: microseconds were carried as an int64, so getString
		// returned "1582934399999981" where REST returns fractional seconds.
		TimeStampMicroTZVector v = new TimeStampMicroTZVector("c", allocator, "UTC");
		v.allocateNew(1);
		v.set(0, 1582934399999981L);
		assertEquals("1582934399.999981", encode(field("c", StandardSQLTypeName.TIMESTAMP), v));
	}

	@Test
	@DisplayName("a whole-second TIMESTAMP keeps one decimal place")
	void timestampOnWholeSecondKeepsOneDecimal() throws SQLException {
		TimeStampMicroTZVector v = new TimeStampMicroTZVector("c", allocator, "UTC");
		v.allocateNew(1);
		v.set(0, 1582934400000000L);
		assertEquals("1582934400.0", encode(field("c", StandardSQLTypeName.TIMESTAMP), v));
	}

	@Test
	@DisplayName("a pre-epoch TIMESTAMP stays negative and exact")
	void timestampBeforeEpoch() throws SQLException {
		TimeStampMicroTZVector v = new TimeStampMicroTZVector("c", allocator, "UTC");
		v.allocateNew(1);
		v.set(0, -1L);
		assertEquals("-0.000001", encode(field("c", StandardSQLTypeName.TIMESTAMP), v));
	}

	@Test
	@DisplayName("TIME drops the fraction when there is none")
	void timeWithoutFraction() throws SQLException {
		TimeMicroVector v = new TimeMicroVector("c", allocator);
		v.allocateNew(1);
		v.set(0, (13 * 3600 + 45 * 60 + 30) * 1_000_000L);
		assertEquals("13:45:30", encode(field("c", StandardSQLTypeName.TIME), v));
	}

	@Test
	@DisplayName("TIME keeps six digits when there is a fraction")
	void timeWithFraction() throws SQLException {
		TimeMicroVector v = new TimeMicroVector("c", allocator);
		v.allocateNew(1);
		v.set(0, (13 * 3600 + 45 * 60 + 30) * 1_000_000L + 1);
		// Padded to six digits: ".000001", never ".1".
		assertEquals("13:45:30.000001", encode(field("c", StandardSQLTypeName.TIME), v));
	}

	@Test
	@DisplayName("midnight is rendered in full, not as an abbreviated LocalTime")
	void timeAtMidnight() throws SQLException {
		// LocalTime.toString() would give "00:00" here, which is not a form the REST
		// path ever produces and would break Time.valueOf().
		TimeMicroVector v = new TimeMicroVector("c", allocator);
		v.allocateNew(1);
		v.set(0, 0L);
		assertEquals("00:00:00", encode(field("c", StandardSQLTypeName.TIME), v));
	}

	@Test
	@DisplayName("DATETIME uses the T separator")
	void datetime() throws SQLException {
		TimeStampMicroVector v = new TimeStampMicroVector("c", allocator);
		v.allocateNew(1);
		LocalDateTime when = LocalDateTime.of(2020, 2, 29, 23, 59, 59, 999_999_000);
		long micros = when.toLocalDate().toEpochDay() * 86_400_000_000L + when.toLocalTime().toNanoOfDay() / 1000L;
		v.set(0, micros);
		assertEquals("2020-02-29T23:59:59.999999", encode(field("c", StandardSQLTypeName.DATETIME), v));
	}

	@Test
	@DisplayName("DATE is ISO, including leap day")
	void date() throws SQLException {
		DateDayVector v = new DateDayVector("c", allocator);
		v.allocateNew(1);
		v.set(0, (int) java.time.LocalDate.of(2020, 2, 29).toEpochDay());
		assertEquals("2020-02-29", encode(field("c", StandardSQLTypeName.DATE), v));
	}

	@Test
	@DisplayName("BYTES are base64, matching what FieldValue.getBytesValue expects")
	void bytes() throws SQLException {
		VarBinaryVector v = new VarBinaryVector("c", allocator);
		v.allocateNew(1);
		v.set(0, "bytes_-20".getBytes(StandardCharsets.UTF_8));
		assertEquals("Ynl0ZXNfLTIw", encode(field("c", StandardSQLTypeName.BYTES), v));
	}

	@Test
	@DisplayName("BOOL is lowercase true/false")
	void bool() throws SQLException {
		BitVector t = new BitVector("c", allocator);
		t.allocateNew(1);
		t.set(0, 1);
		assertEquals("true", encode(field("c", StandardSQLTypeName.BOOL), t));

		BitVector f = new BitVector("c", allocator);
		f.allocateNew(1);
		f.set(0, 0);
		assertEquals("false", encode(field("c", StandardSQLTypeName.BOOL), f));
	}

	@Test
	@DisplayName("STRING passes through, empty string included")
	void strings() throws SQLException {
		VarCharVector v = new VarCharVector("c", allocator);
		v.allocateNew(1);
		v.set(0, "".getBytes(StandardCharsets.UTF_8));
		assertEquals("", encode(field("c", StandardSQLTypeName.STRING), v),
				"an empty string must stay an empty string, not become null");
	}

	@Test
	@DisplayName("a null is null, not the string \"null\"")
	void nullValue() throws SQLException {
		BigIntVector v = new BigIntVector("c", allocator);
		v.allocateNew(1);
		v.setNull(0);
		assertNull(encode(field("c", StandardSQLTypeName.INT64), v));
	}

	@Test
	@DisplayName("FLOAT64 round-trips through the encoded text")
	void float64() throws SQLException {
		Float8Vector v = new Float8Vector("c", allocator);
		v.allocateNew(1);
		v.set(0, -2.0 / 3);
		// The exact text matters less here than the round trip: getString
		// canonicalises FLOAT64 from the parsed double, so what this stores only has
		// to parse back to the identical value.
		assertEquals(-2.0 / 3, Double.parseDouble(encode(field("c", StandardSQLTypeName.FLOAT64), v)), 0.0);
	}
}

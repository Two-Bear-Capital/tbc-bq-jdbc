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

import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.FieldValue;
import com.google.cloud.bigquery.FieldValueList;
import com.google.cloud.bigquery.LegacySQLTypeName;
import com.google.cloud.bigquery.Schema;
import com.google.cloud.bigquery.TableResult;
import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers the failure modes of the value getters.
 *
 * <p>
 * JDBC getters must report bad data as {@link SQLException}. Several did not:
 * {@code convert()} caught {@code NumberFormatException} but not its supertype
 * {@code IllegalArgumentException}, which is what is thrown for malformed
 * base64, and {@code getDate}/{@code getTime} bypassed {@code convert()}
 * altogether. Separately, {@code getTimestamp} truncated BigQuery's microsecond
 * resolution to milliseconds.
 *
 * @since 3.0.0
 */
class ResultSetErrorHandlingTest {

	/** Builds a single-row, single-column result set over the given raw value. */
	private static ResultSet resultSetOf(Field field, String rawValue) throws SQLException {
		FieldValueList row = FieldValueList.of(List.of(FieldValue.of(FieldValue.Attribute.PRIMITIVE, rawValue)), field);
		TableResult tableResult = mock(TableResult.class);
		when(tableResult.getSchema()).thenReturn(Schema.of(field));
		when(tableResult.iterateAll()).thenReturn(List.of(row));

		ResultSet rs = new BQResultSet(null, tableResult);
		rs.next();
		return rs;
	}

	@Test
	void malformedBase64InGetBytesSurfacesAsSqlException() throws SQLException {
		// Given: a STRING column whose text is not valid base64
		ResultSet rs = resultSetOf(Field.of("s", LegacySQLTypeName.STRING), "not base64 !!!");

		// Then: a SQLException, not an unchecked IllegalArgumentException escaping
		// through a JDBC getter
		assertThrows(SQLException.class, () -> rs.getBytes(1));
	}

	@Test
	void malformedDateSurfacesAsSqlException() throws SQLException {
		// Given: a DATE column carrying unparseable text
		ResultSet rs = resultSetOf(Field.of("d", LegacySQLTypeName.DATE), "not-a-date");

		// Then: getDate reports it as SQLException rather than IllegalArgumentException
		assertThrows(SQLException.class, () -> rs.getDate(1));
	}

	@Test
	void malformedTimeSurfacesAsSqlException() throws SQLException {
		// Given: a TIME column carrying unparseable text. Note java.sql.Time.valueOf
		// is lenient about out-of-range components ("99:99:99" rolls over), so this
		// has to be text with no time structure at all.
		ResultSet rs = resultSetOf(Field.of("t", LegacySQLTypeName.TIME), "not-a-time");

		// Then: getTime reports it as SQLException
		assertThrows(SQLException.class, () -> rs.getTime(1));
	}

	@Test
	void getTimestampKeepsMicrosecondPrecision() throws SQLException {
		// Given: a TIMESTAMP 981 microseconds past a whole millisecond. BigQuery
		// delivers timestamps as epoch seconds with a fractional part.
		ResultSet rs = resultSetOf(Field.of("ts", LegacySQLTypeName.TIMESTAMP), "1582934399.999981");

		// When: read as a java.sql.Timestamp
		Timestamp ts = rs.getTimestamp(1);

		// Then: the microseconds survive. Truncating to millis would give 999000000.
		assertEquals(999_981_000, ts.getNanos());
	}

	@Test
	void getTimestampStillReportsTheCorrectSecond() throws SQLException {
		// Given: the same value
		ResultSet rs = resultSetOf(Field.of("ts", LegacySQLTypeName.TIMESTAMP), "1582934399.999981");

		// Then: carrying the nanos separately must not shift the whole-second part
		assertEquals(1_582_934_399_999L, rs.getTimestamp(1).getTime());
	}
}

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
package vc.tbc.bq.jdbc.util;

import org.junit.jupiter.api.Test;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TimezoneUtils utility class.
 *
 * <p>
 * Verifies timezone conversion logic for Calendar-based JDBC methods.
 *
 * @since 1.0.37
 */
class TimezoneUtilsTest {

	// Group 1: adjustToCalendarTimezone() Core Logic (10 tests)

	@Test
	void testAdjustToCalendarTimezoneWithNullCalendar() {
		// Given: Timestamp in milliseconds
		long millis = System.currentTimeMillis();

		// When: Adjusting with null calendar
		long result = TimezoneUtils.adjustToCalendarTimezone(millis, null);

		// Then: Should return original millis
		assertEquals(millis, result, "Null calendar should return original milliseconds");
	}

	@Test
	void testAdjustToCalendarTimezoneWithSameTimezone() {
		// Given: Milliseconds and calendar with JVM default timezone
		long millis = 1609459200000L; // 2021-01-01 00:00:00 UTC
		Calendar cal = Calendar.getInstance(TimeZone.getDefault());

		// When: Adjusting
		long result = TimezoneUtils.adjustToCalendarTimezone(millis, cal);

		// Then: Should return same millis (same timezone = no adjustment)
		assertEquals(millis, result, "Same timezone should return same milliseconds");
	}

	@Test
	void testAdjustToCalendarTimezoneUTCToEST() {
		// Given: Milliseconds and calendar with EST timezone
		// Assume JVM default is UTC for this test
		long millis = 1609459200000L; // 2021-01-01 00:00:00 UTC
		Calendar calEST = Calendar.getInstance(TimeZone.getTimeZone("America/New_York"));
		TimeZone defaultTZ = TimeZone.getDefault();
		TimeZone utc = TimeZone.getTimeZone("UTC");

		// Temporarily set default to UTC
		TimeZone.setDefault(utc);
		try {
			// When: Adjusting from UTC to EST
			long result = TimezoneUtils.adjustToCalendarTimezone(millis, calEST);

			// Then: Result should be adjusted (EST is UTC-5 in winter)
			// UTC 00:00 interpreted as EST should give different epoch millis
			assertNotEquals(millis, result, "UTC to EST should adjust milliseconds");
		} finally {
			TimeZone.setDefault(defaultTZ);
		}
	}

	@Test
	void testAdjustToCalendarTimezoneESTToUTC() {
		// Given: Milliseconds and calendar with UTC timezone
		// Assume JVM default is EST
		long millis = 1609459200000L;
		Calendar calUTC = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
		TimeZone defaultTZ = TimeZone.getDefault();
		TimeZone est = TimeZone.getTimeZone("America/New_York");

		// Temporarily set default to EST
		TimeZone.setDefault(est);
		try {
			// When: Adjusting from EST to UTC
			long result = TimezoneUtils.adjustToCalendarTimezone(millis, calUTC);

			// Then: Result should be adjusted
			assertNotEquals(millis, result, "EST to UTC should adjust milliseconds");
		} finally {
			TimeZone.setDefault(defaultTZ);
		}
	}

	@Test
	void testAdjustToCalendarTimezonePSTToEST() {
		// Given: Milliseconds and calendar with EST timezone
		long millis = 1609459200000L;
		Calendar calEST = Calendar.getInstance(TimeZone.getTimeZone("America/New_York"));
		TimeZone defaultTZ = TimeZone.getDefault();
		TimeZone pst = TimeZone.getTimeZone("America/Los_Angeles");

		// Temporarily set default to PST
		TimeZone.setDefault(pst);
		try {
			// When: Adjusting from PST to EST
			long result = TimezoneUtils.adjustToCalendarTimezone(millis, calEST);

			// Then: Result should be adjusted (3-hour difference)
			long diff = result - millis;
			// EST is 3 hours ahead of PST, so adding 3 hours worth of millis
			assertEquals(3 * 60 * 60 * 1000, diff, "PST to EST should be 3 hours difference");
		} finally {
			TimeZone.setDefault(defaultTZ);
		}
	}

	@Test
	void testAdjustToCalendarTimezoneWithHalfHourOffset() {
		// Given: Calendar with India timezone (GMT+05:30)
		long millis = 1609459200000L;
		Calendar calIndia = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"));
		TimeZone defaultTZ = TimeZone.getDefault();
		TimeZone utc = TimeZone.getTimeZone("UTC");

		TimeZone.setDefault(utc);
		try {
			// When: Adjusting to India timezone
			long result = TimezoneUtils.adjustToCalendarTimezone(millis, calIndia);

			// Then: Should handle half-hour offset (5.5 hours = 330 minutes)
			long diff = result - millis;
			assertEquals(5 * 60 * 60 * 1000 + 30 * 60 * 1000, diff, "India timezone should be +5:30 from UTC");
		} finally {
			TimeZone.setDefault(defaultTZ);
		}
	}

	@Test
	void testAdjustToCalendarTimezoneWithQuarterHourOffset() {
		// Given: Calendar with Nepal timezone (GMT+05:45)
		long millis = 1609459200000L;
		Calendar calNepal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kathmandu"));
		TimeZone defaultTZ = TimeZone.getDefault();
		TimeZone utc = TimeZone.getTimeZone("UTC");

		TimeZone.setDefault(utc);
		try {
			// When: Adjusting to Nepal timezone
			long result = TimezoneUtils.adjustToCalendarTimezone(millis, calNepal);

			// Then: Should handle quarter-hour offset (5:45)
			long diff = result - millis;
			assertEquals(5 * 60 * 60 * 1000 + 45 * 60 * 1000, diff, "Nepal timezone should be +5:45 from UTC");
		} finally {
			TimeZone.setDefault(defaultTZ);
		}
	}

	@Test
	void testAdjustToCalendarTimezoneWithNegativeOffset() {
		// Given: Calendar with UTC-12 timezone
		long millis = 1609459200000L;
		Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("Etc/GMT+12")); // Note: Etc/GMT+12 is actually
																					// UTC-12
		TimeZone defaultTZ = TimeZone.getDefault();
		TimeZone utc = TimeZone.getTimeZone("UTC");

		TimeZone.setDefault(utc);
		try {
			// When: Adjusting to UTC-12
			long result = TimezoneUtils.adjustToCalendarTimezone(millis, cal);

			// Then: Should handle negative offset
			long diff = result - millis;
			assertEquals(-12 * 60 * 60 * 1000, diff, "UTC-12 should be -12 hours from UTC");
		} finally {
			TimeZone.setDefault(defaultTZ);
		}
	}

	@Test
	void testAdjustToCalendarTimezoneWithPositiveOffset() {
		// Given: Calendar with UTC+14 timezone (Kiribati)
		long millis = 1609459200000L;
		Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("Pacific/Kiritimati"));
		TimeZone defaultTZ = TimeZone.getDefault();
		TimeZone utc = TimeZone.getTimeZone("UTC");

		TimeZone.setDefault(utc);
		try {
			// When: Adjusting to UTC+14
			long result = TimezoneUtils.adjustToCalendarTimezone(millis, cal);

			// Then: Should handle large positive offset
			long diff = result - millis;
			assertEquals(14 * 60 * 60 * 1000, diff, "UTC+14 should be +14 hours from UTC");
		} finally {
			TimeZone.setDefault(defaultTZ);
		}
	}

	@Test
	void testAdjustToCalendarTimezoneEpochTime() {
		// Given: Epoch time (0 milliseconds = Jan 1, 1970 00:00:00 UTC)
		long millis = 0L;
		Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("America/New_York"));
		TimeZone defaultTZ = TimeZone.getDefault();
		TimeZone utc = TimeZone.getTimeZone("UTC");

		TimeZone.setDefault(utc);
		try {
			// When: Adjusting epoch time
			long result = TimezoneUtils.adjustToCalendarTimezone(millis, cal);

			// Then: Should handle epoch time correctly
			assertNotEquals(0L, result, "Epoch time should be adjusted for non-UTC timezone");
		} finally {
			TimeZone.setDefault(defaultTZ);
		}
	}

	// Group 2: adjustDateToCalendar() Tests (8 tests)

	@Test
	void testAdjustDateToCalendarWithNullDate() {
		// Given: Null date
		Calendar cal = Calendar.getInstance();

		// When: Adjusting null date
		Date result = TimezoneUtils.adjustDateToCalendar(null, cal);

		// Then: Should return null
		assertNull(result, "Null date should return null");
	}

	@Test
	void testAdjustDateToCalendarWithNullCalendar() {
		// Given: Date and null calendar
		Date date = new Date(System.currentTimeMillis());

		// When: Adjusting with null calendar
		Date result = TimezoneUtils.adjustDateToCalendar(date, null);

		// Then: Should return original date
		assertSame(date, result, "Null calendar should return original date");
	}

	@Test
	void testAdjustDateToCalendarBothNull() {
		// When: Both null
		Date result = TimezoneUtils.adjustDateToCalendar(null, null);

		// Then: Should return null
		assertNull(result, "Both null should return null");
	}

	@Test
	void testAdjustDateToCalendarUTCToEST() {
		// Given: Date and EST calendar
		Date date = new Date(1609459200000L); // 2021-01-01
		Calendar calEST = Calendar.getInstance(TimeZone.getTimeZone("America/New_York"));
		TimeZone defaultTZ = TimeZone.getDefault();
		TimeZone utc = TimeZone.getTimeZone("UTC");

		TimeZone.setDefault(utc);
		try {
			// When: Adjusting
			Date result = TimezoneUtils.adjustDateToCalendar(date, calEST);

			// Then: Result should be different Date object with adjusted time
			assertNotNull(result, "Result should not be null");
			assertNotSame(date, result, "Should return new Date object");
			assertNotEquals(date.getTime(), result.getTime(), "Time should be adjusted");
		} finally {
			TimeZone.setDefault(defaultTZ);
		}
	}

	@Test
	void testAdjustDateToCalendarPreservesSQLDateType() {
		// Given: SQL Date
		Date sqlDate = new Date(1609459200000L);
		Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("America/Los_Angeles"));

		// When: Adjusting
		Date result = TimezoneUtils.adjustDateToCalendar(sqlDate, cal);

		// Then: Should preserve SQL Date type
		assertNotNull(result, "Result should not be null");
		assertInstanceOf(Date.class, result, "Should return java.sql.Date instance");
	}

	@Test
	void testAdjustDateToCalendarWithEpochDate() {
		// Given: Epoch date (Jan 1, 1970)
		Date epochDate = new Date(0L);
		Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Tokyo"));
		TimeZone defaultTZ = TimeZone.getDefault();
		TimeZone utc = TimeZone.getTimeZone("UTC");

		TimeZone.setDefault(utc);
		try {
			// When: Adjusting
			Date result = TimezoneUtils.adjustDateToCalendar(epochDate, cal);

			// Then: Should handle epoch date
			assertNotNull(result, "Epoch date should be adjusted");
			assertNotEquals(0L, result.getTime(), "Epoch should be adjusted for Tokyo timezone");
		} finally {
			TimeZone.setDefault(defaultTZ);
		}
	}

	@Test
	void testAdjustDateToCalendarWithModernDate() {
		// Given: Modern date (2025-01-15)
		Date modernDate = new Date(1736899200000L);
		Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("Europe/London"));

		// When: Adjusting
		Date result = TimezoneUtils.adjustDateToCalendar(modernDate, cal);

		// Then: Should handle modern dates
		assertNotNull(result, "Modern date should be adjusted");
	}

	@Test
	void testAdjustDateToCalendarWithFutureDate() {
		// Given: Future date (2100-06-15)
		Date futureDate = new Date(4118179200000L);
		Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("Australia/Sydney"));

		// When: Adjusting
		Date result = TimezoneUtils.adjustDateToCalendar(futureDate, cal);

		// Then: Should handle future dates
		assertNotNull(result, "Future date should be adjusted");
	}

	// Group 3: adjustTimeToCalendar() Tests (8 tests)

	@Test
	void testAdjustTimeToCalendarWithNullTime() {
		// Given: Null time
		Calendar cal = Calendar.getInstance();

		// When: Adjusting null time
		Time result = TimezoneUtils.adjustTimeToCalendar(null, cal);

		// Then: Should return null
		assertNull(result, "Null time should return null");
	}

	@Test
	void testAdjustTimeToCalendarWithNullCalendar() {
		// Given: Time and null calendar
		Time time = new Time(System.currentTimeMillis());

		// When: Adjusting with null calendar
		Time result = TimezoneUtils.adjustTimeToCalendar(time, null);

		// Then: Should return original time
		assertSame(time, result, "Null calendar should return original time");
	}

	@Test
	void testAdjustTimeToCalendarAcrossTimezones() {
		// Given: Time and different timezone calendar
		Time time = new Time(43200000L); // 12:00:00 UTC
		Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("America/Denver"));
		TimeZone defaultTZ = TimeZone.getDefault();
		TimeZone utc = TimeZone.getTimeZone("UTC");

		TimeZone.setDefault(utc);
		try {
			// When: Adjusting
			Time result = TimezoneUtils.adjustTimeToCalendar(time, cal);

			// Then: Should adjust for timezone
			assertNotNull(result, "Result should not be null");
			assertNotSame(time, result, "Should return new Time object");
		} finally {
			TimeZone.setDefault(defaultTZ);
		}
	}

	@Test
	void testAdjustTimeToCalendarPreservesSQLTimeType() {
		// Given: SQL Time
		Time sqlTime = new Time(43200000L);
		Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("Pacific/Auckland"));

		// When: Adjusting
		Time result = TimezoneUtils.adjustTimeToCalendar(sqlTime, cal);

		// Then: Should preserve SQL Time type
		assertNotNull(result, "Result should not be null");
		assertInstanceOf(Time.class, result, "Should return java.sql.Time instance");
	}

	@Test
	void testAdjustTimeToCalendarMidnight() {
		// Given: Midnight time (00:00:00)
		Time midnight = new Time(0L);
		Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("Europe/Paris"));
		TimeZone defaultTZ = TimeZone.getDefault();
		TimeZone utc = TimeZone.getTimeZone("UTC");

		TimeZone.setDefault(utc);
		try {
			// When: Adjusting
			Time result = TimezoneUtils.adjustTimeToCalendar(midnight, cal);

			// Then: Should handle midnight
			assertNotNull(result, "Midnight should be adjusted");
		} finally {
			TimeZone.setDefault(defaultTZ);
		}
	}

	@Test
	void testAdjustTimeToCalendarNoon() {
		// Given: Noon time (12:00:00)
		Time noon = new Time(43200000L);
		Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("America/Chicago"));

		// When: Adjusting
		Time result = TimezoneUtils.adjustTimeToCalendar(noon, cal);

		// Then: Should handle noon
		assertNotNull(result, "Noon should be adjusted");
	}

	@Test
	void testAdjustTimeToCalendarEndOfDay() {
		// Given: End of day time (23:59:59)
		Time endOfDay = new Time(86399000L); // 23:59:59
		Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"));

		// When: Adjusting
		Time result = TimezoneUtils.adjustTimeToCalendar(endOfDay, cal);

		// Then: Should handle end of day
		assertNotNull(result, "End of day should be adjusted");
	}

	@Test
	void testAdjustTimeToCalendarWithMilliseconds() {
		// Given: Time with millisecond precision
		Time timeWithMillis = new Time(43200567L); // 12:00:00.567
		Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("America/Sao_Paulo"));

		// When: Adjusting
		Time result = TimezoneUtils.adjustTimeToCalendar(timeWithMillis, cal);

		// Then: Should preserve precision
		assertNotNull(result, "Time with millis should be adjusted");
	}

	// Group 4: adjustTimestampToCalendar() Tests (10 tests)

	@Test
	void testAdjustTimestampToCalendarWithNullTimestamp() {
		// Given: Null timestamp
		Calendar cal = Calendar.getInstance();

		// When: Adjusting null timestamp
		Timestamp result = TimezoneUtils.adjustTimestampToCalendar(null, cal);

		// Then: Should return null
		assertNull(result, "Null timestamp should return null");
	}

	@Test
	void testAdjustTimestampToCalendarWithNullCalendar() {
		// Given: Timestamp and null calendar
		Timestamp ts = new Timestamp(System.currentTimeMillis());
		ts.setNanos(123456789);

		// When: Adjusting with null calendar
		Timestamp result = TimezoneUtils.adjustTimestampToCalendar(ts, null);

		// Then: Should return original timestamp
		assertSame(ts, result, "Null calendar should return original timestamp");
	}

	@Test
	void testAdjustTimestampToCalendarPreservesNanos() {
		// CRITICAL: This test ensures nanosecond precision is preserved
		// Given: Timestamp with specific nanosecond precision
		Timestamp original = new Timestamp(System.currentTimeMillis());
		original.setNanos(123456789);

		Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("America/Los_Angeles"));
		TimeZone defaultTZ = TimeZone.getDefault();
		TimeZone utc = TimeZone.getTimeZone("UTC");

		TimeZone.setDefault(utc);
		try {
			// When: Adjusting
			Timestamp result = TimezoneUtils.adjustTimestampToCalendar(original, cal);

			// Then: Nanoseconds must be preserved exactly
			assertNotNull(result, "Result should not be null");
			assertEquals(123456789, result.getNanos(), "Nanosecond precision must be preserved");
		} finally {
			TimeZone.setDefault(defaultTZ);
		}
	}

	@Test
	void testAdjustTimestampToCalendarAcrossTimezones() {
		// Given: Timestamp and different timezone
		Timestamp ts = new Timestamp(1609459200000L);
		ts.setNanos(500000000); // 0.5 seconds
		Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("Europe/Berlin"));
		TimeZone defaultTZ = TimeZone.getDefault();
		TimeZone utc = TimeZone.getTimeZone("UTC");

		TimeZone.setDefault(utc);
		try {
			// When: Adjusting
			Timestamp result = TimezoneUtils.adjustTimestampToCalendar(ts, cal);

			// Then: Should adjust time but preserve nanos
			assertNotNull(result, "Result should not be null");
			assertNotSame(ts, result, "Should return new Timestamp object");
			assertEquals(500000000, result.getNanos(), "Nanos should be preserved");
		} finally {
			TimeZone.setDefault(defaultTZ);
		}
	}

	@Test
	void testAdjustTimestampToCalendarPreservesSQLTimestampType() {
		// Given: SQL Timestamp
		Timestamp sqlTs = new Timestamp(System.currentTimeMillis());
		Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Dubai"));

		// When: Adjusting
		Timestamp result = TimezoneUtils.adjustTimestampToCalendar(sqlTs, cal);

		// Then: Should preserve type
		assertNotNull(result, "Result should not be null");
		assertInstanceOf(Timestamp.class, result, "Should return java.sql.Timestamp instance");
	}

	@Test
	void testAdjustTimestampToCalendarWithMaxNanos() {
		// Given: Timestamp with maximum nanosecond value
		Timestamp ts = new Timestamp(System.currentTimeMillis());
		ts.setNanos(999999999); // Maximum nanos

		Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("America/New_York"));

		// When: Adjusting
		Timestamp result = TimezoneUtils.adjustTimestampToCalendar(ts, cal);

		// Then: Should preserve max nanos
		assertNotNull(result, "Result should not be null");
		assertEquals(999999999, result.getNanos(), "Maximum nanoseconds should be preserved");
	}

	@Test
	void testAdjustTimestampToCalendarWithZeroNanos() {
		// Given: Timestamp with zero nanoseconds
		Timestamp ts = new Timestamp(System.currentTimeMillis());
		ts.setNanos(0);

		Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("Pacific/Fiji"));

		// When: Adjusting
		Timestamp result = TimezoneUtils.adjustTimestampToCalendar(ts, cal);

		// Then: Should preserve zero nanos
		assertNotNull(result, "Result should not be null");
		assertEquals(0, result.getNanos(), "Zero nanoseconds should be preserved");
	}

	@Test
	void testAdjustTimestampToCalendarWithMidRangeNanos() {
		// Given: Timestamp with mid-range nanoseconds
		Timestamp ts = new Timestamp(System.currentTimeMillis());
		ts.setNanos(500000000); // 0.5 seconds

		Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("America/Mexico_City"));

		// When: Adjusting
		Timestamp result = TimezoneUtils.adjustTimestampToCalendar(ts, cal);

		// Then: Should preserve mid-range nanos
		assertNotNull(result, "Result should not be null");
		assertEquals(500000000, result.getNanos(), "Mid-range nanoseconds should be preserved");
	}

	@Test
	void testAdjustTimestampToCalendarEpochTimestamp() {
		// Given: Epoch timestamp (Jan 1, 1970 00:00:00)
		Timestamp epochTs = new Timestamp(0L);
		epochTs.setNanos(0);

		Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("Atlantic/Reykjavik"));
		TimeZone defaultTZ = TimeZone.getDefault();
		TimeZone utc = TimeZone.getTimeZone("UTC");

		TimeZone.setDefault(utc);
		try {
			// When: Adjusting
			Timestamp result = TimezoneUtils.adjustTimestampToCalendar(epochTs, cal);

			// Then: Should handle epoch
			assertNotNull(result, "Epoch timestamp should be adjusted");
			assertEquals(0, result.getNanos(), "Epoch nanos should be preserved");
		} finally {
			TimeZone.setDefault(defaultTZ);
		}
	}

	@Test
	void testAdjustTimestampToCalendarModernTimestamp() {
		// Given: Current timestamp
		Timestamp modernTs = new Timestamp(System.currentTimeMillis());
		modernTs.setNanos(987654321);

		Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("Australia/Melbourne"));

		// When: Adjusting
		Timestamp result = TimezoneUtils.adjustTimestampToCalendar(modernTs, cal);

		// Then: Should handle modern timestamp
		assertNotNull(result, "Modern timestamp should be adjusted");
		assertEquals(987654321, result.getNanos(), "Modern timestamp nanos should be preserved");
	}

	// Group 5: DST and Complex Scenarios (8 tests)

	@Test
	void testAdjustWithSpringDSTTransition() {
		// Given: Timestamp during spring DST transition (March 10, 2024 2:00 AM)
		// In US, clocks spring forward at 2:00 AM to 3:00 AM
		Timestamp ts = new Timestamp(1710057600000L); // March 10, 2024 2:00 AM EST
		ts.setNanos(123456789);

		Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("America/New_York"));
		TimeZone defaultTZ = TimeZone.getDefault();
		TimeZone utc = TimeZone.getTimeZone("UTC");

		TimeZone.setDefault(utc);
		try {
			// When: Adjusting
			Timestamp result = TimezoneUtils.adjustTimestampToCalendar(ts, cal);

			// Then: Should handle DST transition and preserve nanos
			assertNotNull(result, "DST spring forward should be handled");
			assertEquals(123456789, result.getNanos(), "Nanos should be preserved during DST");
		} finally {
			TimeZone.setDefault(defaultTZ);
		}
	}

	@Test
	void testAdjustWithFallDSTTransition() {
		// Given: Timestamp during fall DST transition (November 3, 2024 2:00 AM)
		// In US, clocks fall back at 2:00 AM to 1:00 AM
		Timestamp ts = new Timestamp(1730617200000L); // November 3, 2024 2:00 AM EDT
		ts.setNanos(987654321);

		Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("America/New_York"));
		TimeZone defaultTZ = TimeZone.getDefault();
		TimeZone utc = TimeZone.getTimeZone("UTC");

		TimeZone.setDefault(utc);
		try {
			// When: Adjusting
			Timestamp result = TimezoneUtils.adjustTimestampToCalendar(ts, cal);

			// Then: Should handle DST transition and preserve nanos
			assertNotNull(result, "DST fall back should be handled");
			assertEquals(987654321, result.getNanos(), "Nanos should be preserved during DST");
		} finally {
			TimeZone.setDefault(defaultTZ);
		}
	}

	@Test
	void testAdjustBeforeAndAfterDST() {
		// Given: Two timestamps with same local time but different DST
		// Summer time (DST active)
		Timestamp summer = new Timestamp(1593648000000L); // July 1, 2020 12:00 PM
		Calendar calSummer = Calendar.getInstance(TimeZone.getTimeZone("America/Chicago"));

		// Winter time (DST inactive)
		Timestamp winter = new Timestamp(1609516800000L); // January 1, 2021 12:00 PM
		Calendar calWinter = Calendar.getInstance(TimeZone.getTimeZone("America/Chicago"));

		TimeZone defaultTZ = TimeZone.getDefault();
		TimeZone utc = TimeZone.getTimeZone("UTC");

		TimeZone.setDefault(utc);
		try {
			// When: Adjusting both
			Timestamp resultSummer = TimezoneUtils.adjustTimestampToCalendar(summer, calSummer);
			Timestamp resultWinter = TimezoneUtils.adjustTimestampToCalendar(winter, calWinter);

			// Then: Should handle both correctly
			assertNotNull(resultSummer, "Summer timestamp should be adjusted");
			assertNotNull(resultWinter, "Winter timestamp should be adjusted");
			// Offsets will be different due to DST
		} finally {
			TimeZone.setDefault(defaultTZ);
		}
	}

	@Test
	void testAdjustMultipleTimezonesSequentially() {
		// Given: Timestamp adjusted through multiple timezones
		Timestamp ts = new Timestamp(1609459200000L);
		ts.setNanos(111222333);

		Calendar cal1 = Calendar.getInstance(TimeZone.getTimeZone("America/New_York"));
		Calendar cal2 = Calendar.getInstance(TimeZone.getTimeZone("Europe/London"));
		Calendar cal3 = Calendar.getInstance(TimeZone.getTimeZone("Asia/Tokyo"));

		TimeZone defaultTZ = TimeZone.getDefault();
		TimeZone utc = TimeZone.getTimeZone("UTC");

		TimeZone.setDefault(utc);
		try {
			// When: Chaining adjustments
			Timestamp result1 = TimezoneUtils.adjustTimestampToCalendar(ts, cal1);
			Timestamp result2 = TimezoneUtils.adjustTimestampToCalendar(result1, cal2);
			Timestamp result3 = TimezoneUtils.adjustTimestampToCalendar(result2, cal3);

			// Then: Each adjustment should preserve nanos
			assertEquals(111222333, result1.getNanos(), "First adjustment should preserve nanos");
			assertEquals(111222333, result2.getNanos(), "Second adjustment should preserve nanos");
			assertEquals(111222333, result3.getNanos(), "Third adjustment should preserve nanos");
		} finally {
			TimeZone.setDefault(defaultTZ);
		}
	}

	@Test
	void testAdjustWithHistoricalDate() {
		// Given: Historical date (pre-1970 negative epoch)
		Timestamp historical = new Timestamp(-631152000000L); // January 1, 1950
		historical.setNanos(0);

		Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("America/New_York"));
		TimeZone defaultTZ = TimeZone.getDefault();
		TimeZone utc = TimeZone.getTimeZone("UTC");

		TimeZone.setDefault(utc);
		try {
			// When: Adjusting
			Timestamp result = TimezoneUtils.adjustTimestampToCalendar(historical, cal);

			// Then: Should handle negative epoch times
			assertNotNull(result, "Historical date should be adjusted");
			assertEquals(0, result.getNanos(), "Nanos should be preserved");
		} finally {
			TimeZone.setDefault(defaultTZ);
		}
	}

	@Test
	void testAdjustWithFarFutureDate() {
		// Given: Far future date (year 2200)
		Timestamp farFuture = new Timestamp(7258118400000L); // January 1, 2200
		farFuture.setNanos(999888777);

		Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("Pacific/Honolulu"));
		TimeZone defaultTZ = TimeZone.getDefault();
		TimeZone utc = TimeZone.getTimeZone("UTC");

		TimeZone.setDefault(utc);
		try {
			// When: Adjusting
			Timestamp result = TimezoneUtils.adjustTimestampToCalendar(farFuture, cal);

			// Then: Should handle far future dates
			assertNotNull(result, "Far future date should be adjusted");
			assertEquals(999888777, result.getNanos(), "Nanos should be preserved for future dates");
		} finally {
			TimeZone.setDefault(defaultTZ);
		}
	}

	@Test
	void testAdjustRoundTripConsistency() {
		// Given: Original timestamp
		Timestamp original = new Timestamp(1609459200000L);
		original.setNanos(555666777);

		Calendar calEST = Calendar.getInstance(TimeZone.getTimeZone("America/New_York"));
		Calendar calUTC = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

		TimeZone defaultTZ = TimeZone.getDefault();
		TimeZone utc = TimeZone.getTimeZone("UTC");

		TimeZone.setDefault(utc);
		try {
			// When: Converting UTC -> EST -> UTC
			Timestamp toEST = TimezoneUtils.adjustTimestampToCalendar(original, calEST);

			// Now adjust back assuming EST is default
			TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
			Timestamp backToUTC = TimezoneUtils.adjustTimestampToCalendar(toEST, calUTC);

			// Then: Should get back to original value
			assertEquals(original.getTime(), backToUTC.getTime(), "Round trip should preserve time");
			assertEquals(555666777, backToUTC.getNanos(), "Round trip should preserve nanos");
		} finally {
			TimeZone.setDefault(defaultTZ);
		}
	}

	@Test
	void testAdjustWithCustomCalendar() {
		// Given: Timestamp with custom (non-default) calendar instance
		Timestamp ts = new Timestamp(1609459200000L);
		ts.setNanos(123000000);

		// Create calendar with specific settings
		Calendar customCal = Calendar.getInstance(TimeZone.getTimeZone("Europe/Paris"));
		customCal.setFirstDayOfWeek(Calendar.MONDAY);
		customCal.setMinimalDaysInFirstWeek(4);

		// When: Adjusting with custom calendar
		Timestamp result = TimezoneUtils.adjustTimestampToCalendar(ts, customCal);

		// Then: Should use calendar's timezone regardless of other settings
		assertNotNull(result, "Custom calendar should be handled");
		assertEquals(123000000, result.getNanos(), "Nanos should be preserved with custom calendar");
	}
}

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

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.TimeZone;

/**
 * Utility methods for handling timezone conversions with Calendar parameters.
 *
 * <p>
 * JDBC Calendar parameters allow clients to specify the timezone context for
 * interpreting temporal values. This is critical for applications that need
 * precise control over timezone handling.
 *
 * @since 1.0.24
 */
public final class TimezoneUtils {

	private TimezoneUtils() {
		// Utility class
	}

	/**
	 * Adjusts a temporal value from the default timezone to the Calendar's
	 * timezone.
	 *
	 * <p>
	 * This is used when setting parameters: interpret the value as if it were in
	 * the Calendar's timezone instead of the JVM default timezone.
	 *
	 * @param millis
	 *            the milliseconds since epoch in default timezone
	 * @param cal
	 *            the Calendar specifying target timezone (null returns original
	 *            millis)
	 * @return adjusted milliseconds in Calendar's timezone context
	 */
	public static long adjustToCalendarTimezone(long millis, Calendar cal) {
		if (cal == null) {
			return millis;
		}

		TimeZone tz = cal.getTimeZone();
		int calendarOffset = tz.getOffset(millis);
		int defaultOffset = TimeZone.getDefault().getOffset(millis);

		// Adjust: remove default offset interpretation, apply Calendar's timezone
		// interpretation
		return millis - defaultOffset + calendarOffset;
	}

	/**
	 * Adjusts a Date value using the Calendar's timezone.
	 *
	 * <p>
	 * Returns a new Date object with milliseconds adjusted for the Calendar's
	 * timezone context.
	 *
	 * @param date
	 *            the original date (null returns null)
	 * @param cal
	 *            the Calendar specifying target timezone (null returns original
	 *            date)
	 * @return adjusted Date, or null if input was null
	 */
	public static Date adjustDateToCalendar(Date date, Calendar cal) {
		if (date == null || cal == null) {
			return date;
		}

		long adjustedMillis = adjustToCalendarTimezone(date.getTime(), cal);
		return new Date(adjustedMillis);
	}

	/**
	 * Adjusts a Time value using the Calendar's timezone.
	 *
	 * <p>
	 * Returns a new Time object with milliseconds adjusted for the Calendar's
	 * timezone context.
	 *
	 * @param time
	 *            the original time (null returns null)
	 * @param cal
	 *            the Calendar specifying target timezone (null returns original
	 *            time)
	 * @return adjusted Time, or null if input was null
	 */
	public static Time adjustTimeToCalendar(Time time, Calendar cal) {
		if (time == null || cal == null) {
			return time;
		}

		long adjustedMillis = adjustToCalendarTimezone(time.getTime(), cal);
		return new Time(adjustedMillis);
	}

	/**
	 * Adjusts a Timestamp value using the Calendar's timezone.
	 *
	 * <p>
	 * Returns a new Timestamp object with milliseconds adjusted for the Calendar's
	 * timezone context. Preserves nanosecond precision.
	 *
	 * @param timestamp
	 *            the original timestamp (null returns null)
	 * @param cal
	 *            the Calendar specifying target timezone (null returns original
	 *            timestamp)
	 * @return adjusted Timestamp with preserved nanosecond precision, or null if
	 *         input was null
	 */
	public static Timestamp adjustTimestampToCalendar(Timestamp timestamp, Calendar cal) {
		if (timestamp == null || cal == null) {
			return timestamp;
		}

		long adjustedMillis = adjustToCalendarTimezone(timestamp.getTime(), cal);
		Timestamp adjusted = new Timestamp(adjustedMillis);
		adjusted.setNanos(timestamp.getNanos()); // Preserve nanosecond precision
		return adjusted;
	}
}

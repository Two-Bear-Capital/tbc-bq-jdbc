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
 * interpreting temporal values. Two <em>inverse</em> conversions are involved,
 * and they must not be conflated:
 *
 * <ul>
 * <li>{@code toCalendarZone} — used by the <strong>setters</strong>. The
 * incoming value's wall-clock reading is whatever the JVM default zone shows;
 * reinterpret those same wall-clock fields as belonging to the Calendar's zone
 * and return the instant that produces.
 * <li>{@code fromCalendarZone} — used by the <strong>getters</strong>. Given an
 * instant from BigQuery, return the value whose wall-clock reading in the JVM
 * default zone matches what that instant reads in the Calendar's zone.
 * </ul>
 *
 * <p>
 * They differ only in sign, which is exactly why a single shared helper was
 * easy to point in the wrong direction.
 * {@code fromCalendarZone(toCalendarZone(m,
 * cal), cal) == m} for every zone, so a set/get round-trip is identity.
 *
 * @since 1.0.24
 */
public final class TimezoneUtils {

	private TimezoneUtils() {
		// Utility class
	}

	/**
	 * Signed difference between the Calendar's zone and the JVM default at the
	 * given instant. The two conversions below are this value added or subtracted.
	 */
	private static int offsetDelta(long millis, Calendar cal) {
		return cal.getTimeZone().getOffset(millis) - TimeZone.getDefault().getOffset(millis);
	}

	// ── Setter direction: wall clock in the Calendar's zone → instant ─────────

	/**
	 * Reinterprets a value's wall-clock fields as belonging to the Calendar's
	 * timezone, per {@code PreparedStatement.setXxx(int, x, Calendar)}.
	 *
	 * <p>
	 * {@code 12:34:56} with an {@code Asia/Tokyo} Calendar is the instant
	 * {@code 03:34:56Z}, not {@code 21:34:56Z}.
	 *
	 * @param millis
	 *            milliseconds since epoch as read in the JVM default timezone
	 * @param cal
	 *            the Calendar supplying the target timezone (null returns
	 *            {@code millis})
	 * @return the instant at which the Calendar's timezone shows that wall clock
	 */
	public static long toCalendarZone(long millis, Calendar cal) {
		if (cal == null) {
			return millis;
		}
		return millis - offsetDelta(millis, cal);
	}

	/**
	 * {@link #toCalendarZone(long, Calendar)} for a Date.
	 *
	 * @param date
	 *            the value (null returns null)
	 * @param cal
	 *            the Calendar (null returns {@code date})
	 * @return the converted Date
	 */
	public static Date dateToCalendarZone(Date date, Calendar cal) {
		if (date == null || cal == null) {
			return date;
		}
		return new Date(toCalendarZone(date.getTime(), cal));
	}

	/**
	 * {@link #toCalendarZone(long, Calendar)} for a Time.
	 *
	 * @param time
	 *            the value (null returns null)
	 * @param cal
	 *            the Calendar (null returns {@code time})
	 * @return the converted Time
	 */
	public static Time timeToCalendarZone(Time time, Calendar cal) {
		if (time == null || cal == null) {
			return time;
		}
		return new Time(toCalendarZone(time.getTime(), cal));
	}

	/**
	 * {@link #toCalendarZone(long, Calendar)} for a Timestamp, preserving
	 * nanosecond precision.
	 *
	 * @param timestamp
	 *            the value (null returns null)
	 * @param cal
	 *            the Calendar (null returns {@code timestamp})
	 * @return the converted Timestamp
	 */
	public static Timestamp timestampToCalendarZone(Timestamp timestamp, Calendar cal) {
		if (timestamp == null || cal == null) {
			return timestamp;
		}
		Timestamp converted = new Timestamp(toCalendarZone(timestamp.getTime(), cal));
		converted.setNanos(timestamp.getNanos());
		return converted;
	}

	// ── Getter direction: instant → wall clock in the Calendar's zone ─────────

	/**
	 * Renders an instant as the value whose wall clock in the JVM default timezone
	 * matches what the instant reads in the Calendar's timezone, per
	 * {@code ResultSet.getXxx(int, Calendar)}.
	 *
	 * <p>
	 * Inverse of {@link #toCalendarZone(long, Calendar)}.
	 *
	 * @param millis
	 *            milliseconds since epoch
	 * @param cal
	 *            the Calendar supplying the timezone (null returns {@code millis})
	 * @return the adjusted milliseconds
	 */
	public static long fromCalendarZone(long millis, Calendar cal) {
		if (cal == null) {
			return millis;
		}
		return millis + offsetDelta(millis, cal);
	}

	/**
	 * {@link #fromCalendarZone(long, Calendar)} for a Date.
	 *
	 * @param date
	 *            the value (null returns null)
	 * @param cal
	 *            the Calendar (null returns {@code date})
	 * @return the converted Date
	 */
	public static Date dateFromCalendarZone(Date date, Calendar cal) {
		if (date == null || cal == null) {
			return date;
		}
		return new Date(fromCalendarZone(date.getTime(), cal));
	}

	/**
	 * {@link #fromCalendarZone(long, Calendar)} for a Time.
	 *
	 * @param time
	 *            the value (null returns null)
	 * @param cal
	 *            the Calendar (null returns {@code time})
	 * @return the converted Time
	 */
	public static Time timeFromCalendarZone(Time time, Calendar cal) {
		if (time == null || cal == null) {
			return time;
		}
		return new Time(fromCalendarZone(time.getTime(), cal));
	}

	/**
	 * {@link #fromCalendarZone(long, Calendar)} for a Timestamp, preserving
	 * nanosecond precision.
	 *
	 * @param timestamp
	 *            the value (null returns null)
	 * @param cal
	 *            the Calendar (null returns {@code timestamp})
	 * @return the converted Timestamp
	 */
	public static Timestamp timestampFromCalendarZone(Timestamp timestamp, Calendar cal) {
		if (timestamp == null || cal == null) {
			return timestamp;
		}
		Timestamp converted = new Timestamp(fromCalendarZone(timestamp.getTime(), cal));
		converted.setNanos(timestamp.getNanos());
		return converted;
	}
}

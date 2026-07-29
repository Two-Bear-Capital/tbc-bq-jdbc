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
package vc.tbc.bq.jdbc.metadata;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Recognises date-sharded table sets — {@code events_20260101},
 * {@code events_20260102}, … — so they can be reported as one {@code events_*}
 * entry instead of one row per day.
 *
 * <p>
 * <b>Sharding is a naming convention, not something BigQuery declares.</b>
 * Nothing in the API says these tables belong together; the only evidence is
 * that their names share a prefix and end in something that looks like a date.
 * That is why collapsing is opt-in and why the date is validated rather than
 * merely counted: a table legitimately called {@code metrics_12345678} must not
 * disappear into a group, and one called {@code backup_20261301} is not a
 * January-13th shard.
 *
 * <p>
 * The wildcard form is BigQuery's own: {@code events_*} is what you write in a
 * query to scan the set, with {@code _TABLE_SUFFIX} selecting among them. So
 * the collapsed name is not a display convention the caller has to decode — it
 * is usable SQL.
 *
 * @since 3.2.0
 */
public final class ShardedTables {

	/**
	 * A shard name: any prefix, an underscore, then eight digits.
	 *
	 * <p>
	 * The prefix is greedy so {@code events_daily_20260101} groups under
	 * {@code events_daily}, not {@code events}. A name that is nothing but a date
	 * has no prefix to group by and does not match.
	 */
	private static final Pattern SHARD = Pattern.compile("^(.+)_(\\d{4})(\\d{2})(\\d{2})$");

	/** The suffix that marks a collapsed entry, and BigQuery's wildcard syntax. */
	private static final String WILDCARD_SUFFIX = "_*";

	/**
	 * Shards needed before a set is collapsed.
	 *
	 * <p>
	 * Two, not one: a lone {@code events_20260101} is far more likely to be a table
	 * that happens to end in a date than a sharded set with one member, and
	 * reporting it as {@code events_*} would rename a table nobody asked to group.
	 */
	private static final int MIN_SHARDS = 2;

	private ShardedTables() {
	}

	/**
	 * The shard prefix of a table name, or null when the name is not a shard.
	 *
	 * @param tableName
	 *            the table name
	 * @return the prefix before the date, or null
	 */
	public static String shardPrefix(String tableName) {
		if (tableName == null) {
			return null;
		}
		Matcher matcher = SHARD.matcher(tableName);
		if (!matcher.matches()) {
			return null;
		}
		int month = Integer.parseInt(matcher.group(3));
		int day = Integer.parseInt(matcher.group(4));
		// Range-checked, not parsed as a real date: 20260230 is close enough to a
		// shard name to group, and rejecting it would split a set over one typo.
		// Eight digits that cannot be a date at all are a different matter.
		if (month < 1 || month > 12 || day < 1 || day > 31) {
			return null;
		}
		return matcher.group(1);
	}

	/**
	 * The name a collapsed set is reported under.
	 *
	 * @param prefix
	 *            the shared prefix
	 * @return the wildcard name, e.g. {@code events_*}
	 */
	public static String wildcardName(String prefix) {
		return prefix + WILDCARD_SUFFIX;
	}

	/**
	 * Whether a name is a collapsed entry rather than a real table.
	 *
	 * @param tableName
	 *            the name to test
	 * @return true if it ends in {@code _*}
	 */
	public static boolean isWildcardName(String tableName) {
		return tableName != null && tableName.endsWith(WILDCARD_SUFFIX)
				&& tableName.length() > WILDCARD_SUFFIX.length();
	}

	/**
	 * The prefix a wildcard name stands for.
	 *
	 * @param wildcardName
	 *            a name for which {@link #isWildcardName} holds
	 * @return the prefix, or null if the name is not a wildcard name
	 */
	public static String prefixOf(String wildcardName) {
		if (!isWildcardName(wildcardName)) {
			return null;
		}
		return wildcardName.substring(0, wildcardName.length() - WILDCARD_SUFFIX.length());
	}

	/**
	 * Groups table names into shard sets, keeping only sets large enough to
	 * collapse.
	 *
	 * <p>
	 * Iteration order follows first appearance, and each set's members are in the
	 * order given, so a caller that passed sorted names gets sorted shards.
	 *
	 * @param tableNames
	 *            the names to group
	 * @return prefix to shard names, for prefixes with at least
	 *         {@value #MIN_SHARDS} shards
	 */
	public static Map<String, List<String>> shardSets(Iterable<String> tableNames) {
		Map<String, List<String>> byPrefix = new LinkedHashMap<>();
		for (String name : tableNames) {
			String prefix = shardPrefix(name);
			if (prefix != null) {
				byPrefix.computeIfAbsent(prefix, key -> new ArrayList<>()).add(name);
			}
		}
		byPrefix.entrySet().removeIf(entry -> entry.getValue().size() < MIN_SHARDS);
		return byPrefix;
	}

	/**
	 * The most recent shard of a set, which is the one whose schema best represents
	 * it.
	 *
	 * <p>
	 * Lexicographic order is date order here: the suffix is fixed-width and
	 * zero-padded, so no date parsing is needed to answer this.
	 *
	 * <p>
	 * The newest rather than the oldest because shards drift — a column added last
	 * month is in recent shards and not in the first one, and a caller reading the
	 * set through {@code events_*} will see it. Reporting the oldest schema would
	 * omit columns their queries can select.
	 *
	 * @param shardNames
	 *            the shard names of one set, non-empty
	 * @return the latest shard name, or null when the collection is empty
	 */
	public static String newestShard(Iterable<String> shardNames) {
		String newest = null;
		for (String name : shardNames) {
			if (newest == null || name.compareTo(newest) > 0) {
				newest = name;
			}
		}
		return newest;
	}

	/**
	 * Describes a collapsed set for the {@code REMARKS} column.
	 *
	 * @param shardNames
	 *            the shards the entry stands for
	 * @return a one-line summary
	 */
	public static String describe(List<String> shardNames) {
		String oldest = null;
		String newest = null;
		for (String name : shardNames) {
			if (oldest == null || name.compareTo(oldest) < 0) {
				oldest = name;
			}
			if (newest == null || name.compareTo(newest) > 0) {
				newest = name;
			}
		}
		if (shardNames.size() == 1) {
			return "1 date-sharded table: " + oldest;
		}
		return shardNames.size() + " date-sharded tables, " + oldest + " to " + newest;
	}
}

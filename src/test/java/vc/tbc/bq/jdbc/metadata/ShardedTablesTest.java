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

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShardedTablesTest {

	@Test
	void testRecognisesADateShard() {
		assertEquals("events", ShardedTables.shardPrefix("events_20260101"));
	}

	@Test
	void testPrefixIsGreedySoNestedUnderscoresGroupTogether() {
		// events_daily and events_hourly are different sets. A lazy prefix would put
		// both under "events" and merge two unrelated tables into one entry.
		assertEquals("events_daily", ShardedTables.shardPrefix("events_daily_20260101"));
		assertEquals("events_hourly", ShardedTables.shardPrefix("events_hourly_20260101"));
	}

	@Test
	void testRejectsEightDigitsThatCannotBeADate() {
		// A table legitimately named metrics_12345678 must not be swept into a set
		// and disappear from the listing.
		assertNull(ShardedTables.shardPrefix("metrics_12345678"));
		assertNull(ShardedTables.shardPrefix("backup_20261301"), "month 13");
		assertNull(ShardedTables.shardPrefix("backup_20260132"), "day 32");
		assertNull(ShardedTables.shardPrefix("backup_20260100"), "day 0");
		assertNull(ShardedTables.shardPrefix("backup_20260001"), "month 0");
	}

	@Test
	void testAcceptsAnImpossibleButPlausibleDay() {
		// 30 February is a typo in a shard name, not evidence the table is something
		// else. Splitting the set over it would be worse than including it.
		assertEquals("backup", ShardedTables.shardPrefix("backup_20260230"));
	}

	@Test
	void testRejectsNamesThatAreNotShards() {
		assertNull(ShardedTables.shardPrefix("events"));
		assertNull(ShardedTables.shardPrefix("events_2026010"), "seven digits");
		assertNull(ShardedTables.shardPrefix("events_202601011"), "nine digits");
		assertNull(ShardedTables.shardPrefix("20260101"), "no prefix to group by");
		assertNull(ShardedTables.shardPrefix("_20260101"), "an empty prefix is no prefix");
		assertNull(ShardedTables.shardPrefix(null));
	}

	@Test
	void testWildcardNameRoundTrips() {
		assertEquals("events_*", ShardedTables.wildcardName("events"));
		assertTrue(ShardedTables.isWildcardName("events_*"));
		assertEquals("events", ShardedTables.prefixOf("events_*"));
	}

	@Test
	void testWildcardNameRecognitionIsNarrow() {
		assertFalse(ShardedTables.isWildcardName("events"));
		assertFalse(ShardedTables.isWildcardName("events_20260101"));
		assertFalse(ShardedTables.isWildcardName("_*"), "nothing to stand for");
		assertFalse(ShardedTables.isWildcardName(null));
		assertNull(ShardedTables.prefixOf("events"));
	}

	@Test
	void testGroupsShardsByPrefix() {
		Map<String, List<String>> sets = ShardedTables
				.shardSets(List.of("events_20260101", "events_20260102", "clicks_20260101", "clicks_20260102"));

		assertEquals(2, sets.size());
		assertEquals(List.of("events_20260101", "events_20260102"), sets.get("events"));
		assertEquals(List.of("clicks_20260101", "clicks_20260102"), sets.get("clicks"));
	}

	@Test
	void testASingleShardIsNotASet() {
		// One events_20260101 is far more likely to be a table that ends in a date
		// than a one-member sharded set, and collapsing it would rename it.
		assertTrue(ShardedTables.shardSets(List.of("events_20260101")).isEmpty());
		assertTrue(ShardedTables.shardSets(List.of("events_20260101", "clicks_20260101")).isEmpty());
	}

	@Test
	void testNonShardsAreLeftOutOfEverySet() {
		Map<String, List<String>> sets = ShardedTables
				.shardSets(List.of("users", "events_20260101", "events_20260102", "metrics_12345678"));

		assertEquals(1, sets.size());
		assertEquals(List.of("events_20260101", "events_20260102"), sets.get("events"));
	}

	@Test
	void testNewestShardIsTheLatestDate() {
		// Lexicographic order is date order for a zero-padded fixed-width suffix.
		assertEquals("events_20261231",
				ShardedTables.newestShard(List.of("events_20260101", "events_20261231", "events_20260630")));
		assertNull(ShardedTables.newestShard(List.of()));
	}

	@Test
	void testDescribeNamesTheRangeAndCount() {
		assertEquals("3 date-sharded tables, events_20260101 to events_20260103",
				ShardedTables.describe(List.of("events_20260102", "events_20260101", "events_20260103")));
	}

	@Test
	void testDescribeHandlesASingleShard() {
		// Not reachable through shardSets, which needs two, but describe is public
		// and should not produce "1 date-sharded tables, X to X".
		assertEquals("1 date-sharded table: events_20260101", ShardedTables.describe(List.of("events_20260101")));
	}
}

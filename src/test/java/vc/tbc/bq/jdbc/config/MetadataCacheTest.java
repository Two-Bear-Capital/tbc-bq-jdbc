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
package vc.tbc.bq.jdbc.config;

import org.junit.jupiter.api.Test;
import vc.tbc.bq.jdbc.metadata.MetadataResultSet;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Tests for MetadataCache.
 *
 * @since 1.0.15
 */
class MetadataCacheTest {

	private MetadataResultSet createTestResultSet() {
		String[] columns = {"ID", "NAME"};
		int[] types = {Types.INTEGER, Types.VARCHAR};
		List<Object[]> rows = new ArrayList<>();
		rows.add(new Object[]{1, "Alice"});
		rows.add(new Object[]{2, "Bob"});
		return new MetadataResultSet(columns, types, rows);
	}

	// Construction Tests

	@Test
	void testConstructionWithDefaultTtl() {
		// When: Creating cache with default TTL
		MetadataCache cache = new MetadataCache();

		// Then: Should be created successfully
		assertNotNull(cache);
		assertEquals(0, cache.size());
	}

	@Test
	void testConstructionWithCustomTtl() {
		// When: Creating cache with custom TTL
		MetadataCache cache = new MetadataCache(Duration.ofMinutes(10));

		// Then: Should be created successfully
		assertNotNull(cache);
		assertEquals(0, cache.size());
	}

	@Test
	void testConstructionWithNullTtlUsesDefault() {
		// When: Creating cache with null TTL
		MetadataCache cache = new MetadataCache(null);

		// Then: Should use default TTL
		assertNotNull(cache);
		assertEquals(0, cache.size());
	}

	// Cache Operations Tests

	@Test
	void testPutAndGet() throws SQLException {
		// Given: A cache and result set
		MetadataCache cache = new MetadataCache();
		MetadataResultSet rs = createTestResultSet();

		// When: Putting and getting from cache
		cache.put("test-key", rs);
		Optional<ResultSet> cached = cache.get("test-key");

		// Then: Should return the cached result
		assertTrue(cached.isPresent());
		assertEquals(1, cache.size());
	}

	@Test
	void testGetWithMissingKey() {
		// Given: An empty cache
		MetadataCache cache = new MetadataCache();

		// When: Getting non-existent key
		Optional<ResultSet> cached = cache.get("missing-key");

		// Then: Should return empty
		assertFalse(cached.isPresent());
	}

	@Test
	void testPutOverwritesExistingKey() throws SQLException {
		// Given: A cache with existing entry
		MetadataCache cache = new MetadataCache();
		cache.put("key", createTestResultSet());

		// When: Putting new value with same key
		cache.put("key", createTestResultSet());

		// Then: Should overwrite (size remains 1)
		assertEquals(1, cache.size());
	}

	@Test
	void testGetCreatesNewResultSet() throws SQLException {
		// Given: A cached result set
		MetadataCache cache = new MetadataCache();
		MetadataResultSet original = createTestResultSet();
		cache.put("key", original);

		// When: Getting cached result twice
		Optional<ResultSet> cached1 = cache.get("key");
		Optional<ResultSet> cached2 = cache.get("key");

		// Then: Should create new ResultSet instances
		assertTrue(cached1.isPresent());
		assertTrue(cached2.isPresent());
		assertNotSame(cached1.get(), cached2.get());
	}

	@Test
	void testCachedResultSetPreservesData() throws SQLException {
		// Given: A cache with data
		MetadataCache cache = new MetadataCache();
		MetadataResultSet original = createTestResultSet();
		cache.put("key", original);

		// When: Getting and reading cached result
		Optional<ResultSet> cached = cache.get("key");
		assertTrue(cached.isPresent());

		ResultSet rs = cached.get();
		rs.next();
		int id = rs.getInt(1);
		String name = rs.getString(2);

		// Then: Should preserve original data
		assertEquals(1, id);
		assertEquals("Alice", name);
	}

	// TTL Expiration Tests

	@Test
	void testTtlExpiration() throws SQLException, InterruptedException {
		// Given: Cache with very short TTL
		MetadataCache cache = new MetadataCache(Duration.ofMillis(100));
		cache.put("key", createTestResultSet());

		// When: Waiting for TTL to expire
		Thread.sleep(150);
		Optional<ResultSet> cached = cache.get("key");

		// Then: Should return empty (expired and removed)
		assertFalse(cached.isPresent());
		assertEquals(0, cache.size());
	}

	@Test
	void testExpiredEntryAutoRemoved() throws SQLException, InterruptedException {
		// Given: Cache with expired entry
		MetadataCache cache = new MetadataCache(Duration.ofMillis(50));
		cache.put("expired", createTestResultSet());
		Thread.sleep(100);

		// When: Accessing expired entry
		cache.get("expired");

		// Then: Should be removed from cache
		assertEquals(0, cache.size());
	}

	@Test
	void testNonExpiredEntryReturned() throws SQLException, InterruptedException {
		// Given: Cache with long TTL
		MetadataCache cache = new MetadataCache(Duration.ofMinutes(10));
		cache.put("key", createTestResultSet());

		// When: Accessing immediately
		Optional<ResultSet> cached = cache.get("key");

		// Then: Should return the entry
		assertTrue(cached.isPresent());
	}

	// Clear and Invalidate Tests

	@Test
	void testClear() throws SQLException {
		// Given: Cache with multiple entries
		MetadataCache cache = new MetadataCache();
		cache.put("key1", createTestResultSet());
		cache.put("key2", createTestResultSet());
		cache.put("key3", createTestResultSet());

		// When: Clearing cache
		cache.clear();

		// Then: All entries should be removed
		assertEquals(0, cache.size());
		assertFalse(cache.get("key1").isPresent());
		assertFalse(cache.get("key2").isPresent());
		assertFalse(cache.get("key3").isPresent());
	}

	@Test
	void testClearEmptyCache() {
		// Given: Empty cache
		MetadataCache cache = new MetadataCache();

		// When: Clearing
		cache.clear();

		// Then: Should not throw
		assertEquals(0, cache.size());
	}

	@Test
	void testInvalidateWithPrefix() throws SQLException {
		// Given: Cache with entries having different prefixes
		MetadataCache cache = new MetadataCache();
		cache.put("tables:dataset1:table1", createTestResultSet());
		cache.put("tables:dataset1:table2", createTestResultSet());
		cache.put("tables:dataset2:table1", createTestResultSet());
		cache.put("columns:dataset1:table1", createTestResultSet());

		// When: Invalidating with prefix
		cache.invalidate("tables:dataset1:");

		// Then: Only matching entries should be removed
		assertEquals(2, cache.size());
		assertFalse(cache.get("tables:dataset1:table1").isPresent());
		assertFalse(cache.get("tables:dataset1:table2").isPresent());
		assertTrue(cache.get("tables:dataset2:table1").isPresent());
		assertTrue(cache.get("columns:dataset1:table1").isPresent());
	}

	@Test
	void testInvalidateWithNoMatches() throws SQLException {
		// Given: Cache with entries
		MetadataCache cache = new MetadataCache();
		cache.put("key1", createTestResultSet());
		cache.put("key2", createTestResultSet());

		// When: Invalidating with non-matching prefix
		cache.invalidate("nonexistent:");

		// Then: No entries should be removed
		assertEquals(2, cache.size());
	}

	@Test
	void testInvalidateAll() throws SQLException {
		// Given: Cache with entries
		MetadataCache cache = new MetadataCache();
		cache.put("prefix:key1", createTestResultSet());
		cache.put("prefix:key2", createTestResultSet());

		// When: Invalidating with common prefix
		cache.invalidate("prefix:");

		// Then: All entries should be removed
		assertEquals(0, cache.size());
	}

	// Statistics Tests

	@Test
	void testSize() throws SQLException {
		// Given: Cache with entries
		MetadataCache cache = new MetadataCache();

		// Then: Size should increase with each put
		assertEquals(0, cache.size());

		cache.put("key1", createTestResultSet());
		assertEquals(1, cache.size());

		cache.put("key2", createTestResultSet());
		assertEquals(2, cache.size());
	}

	@Test
	void testGetStats() throws SQLException {
		// Given: Cache with entries
		MetadataCache cache = new MetadataCache();
		cache.put("key1", createTestResultSet());

		// When: Getting stats
		String stats = cache.getStats();

		// Then: Should contain size and TTL info
		assertNotNull(stats);
		assertTrue(stats.contains("Cache size"));
		assertTrue(stats.contains("Expired"));
		assertTrue(stats.contains("TTL"));
	}

	@Test
	void testGetStatsWithExpiredEntries() throws SQLException, InterruptedException {
		// Given: Cache with expired entry
		MetadataCache cache = new MetadataCache(Duration.ofMillis(50));
		cache.put("expired", createTestResultSet());
		Thread.sleep(100);

		// When: Getting stats (without removing expired)
		String stats = cache.getStats();

		// Then: Should show expired count
		assertTrue(stats.contains("Expired: 1"));
	}

	// Concurrency Tests

	@Test
	void testConcurrentPutAndGet() throws Exception {
		// Given: Cache and executor
		MetadataCache cache = new MetadataCache();
		ExecutorService executor = Executors.newFixedThreadPool(10);
		CountDownLatch latch = new CountDownLatch(100);
		AtomicInteger successCount = new AtomicInteger(0);

		// When: Multiple threads put and get concurrently
		for (int i = 0; i < 100; i++) {
			int key = i;
			executor.submit(() -> {
				try {
					cache.put("key-" + key, createTestResultSet());
					Optional<ResultSet> cached = cache.get("key-" + key);
					if (cached.isPresent()) {
						successCount.incrementAndGet();
					}
				} catch (SQLException e) {
					// Ignore for test
				} finally {
					latch.countDown();
				}
			});
		}

		// Then: All operations should complete successfully
		assertTrue(latch.await(5, TimeUnit.SECONDS));
		executor.shutdown();
		assertTrue(cache.size() > 0);
		assertTrue(successCount.get() > 0);
	}

	@Test
	void testConcurrentInvalidate() throws Exception {
		// Given: Cache with entries
		MetadataCache cache = new MetadataCache();
		for (int i = 0; i < 50; i++) {
			cache.put("prefix-" + i, createTestResultSet());
		}

		ExecutorService executor = Executors.newFixedThreadPool(5);
		CountDownLatch latch = new CountDownLatch(10);

		// When: Multiple threads invalidate concurrently
		for (int i = 0; i < 10; i++) {
			executor.submit(() -> {
				try {
					cache.invalidate("prefix-");
				} finally {
					latch.countDown();
				}
			});
		}

		// Then: Should complete without errors
		assertTrue(latch.await(5, TimeUnit.SECONDS));
		executor.shutdown();
		assertEquals(0, cache.size());
	}

	// Non-MetadataResultSet Handling Test

	@Test
	void testPutWithNonMetadataResultSetDoesNotCache() throws SQLException {
		// Given: Cache and non-MetadataResultSet
		MetadataCache cache = new MetadataCache();
		ResultSet mockRs = mock(ResultSet.class);

		// When: Trying to cache non-MetadataResultSet
		cache.put("key", mockRs);

		// Then: Should not be cached (warning logged)
		assertEquals(0, cache.size());
		assertFalse(cache.get("key").isPresent());
	}
}

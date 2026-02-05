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
package com.twobearcapital.bigquery.jdbc.config;

import com.twobearcapital.bigquery.jdbc.metadata.MetadataResultSet;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cache for DatabaseMetaData results to improve introspection performance.
 *
 * <p>
 * This cache is particularly important for projects with many datasets (90+),
 * addressing JetBrains issue DBE-22088 where introspection can hang or become
 * very slow.
 *
 * <p>
 * The cache stores metadata query results with a configurable time-to-live
 * (TTL). All operations are thread-safe.
 *
 * @since 1.0.0
 */
public final class MetadataCache {

	private static final Logger logger = LoggerFactory.getLogger(MetadataCache.class);
	private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

	private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
	private final Duration ttl;

	/**
	 * Creates a new metadata cache with the specified TTL.
	 *
	 * @param ttl
	 *            the time-to-live for cache entries
	 */
	public MetadataCache(Duration ttl) {
		this.ttl = ttl != null ? ttl : DEFAULT_TTL;
		logger.debug("Metadata cache initialized with TTL: {}", this.ttl);
	}

	/** Creates a new metadata cache with the default TTL of 5 minutes. */
	public MetadataCache() {
		this(DEFAULT_TTL);
	}

	/**
	 * Gets a cached result set if available and not expired.
	 *
	 * @param key
	 *            the cache key
	 * @return Optional containing the cached ResultSet if available and not
	 *         expired, empty otherwise
	 */
	public Optional<ResultSet> get(String key) {
		CacheEntry entry = cache.get(key);
		if (entry == null) {
			logger.trace("Cache miss for key: {}", key);
			return Optional.empty();
		}

		if (entry.isExpired()) {
			cache.remove(key);
			logger.trace("Cache entry expired for key: {}", key);
			return Optional.empty();
		}

		logger.trace("Cache hit for key: {}", key);
		return Optional.of(entry.createResultSet());
	}

	/**
	 * Stores a result set in the cache.
	 *
	 * <p>
	 * Note: This method reads the entire ResultSet to cache its contents. The
	 * ResultSet cursor will be positioned before the first row when this method
	 * returns.
	 *
	 * @param key
	 *            the cache key
	 * @param resultSet
	 *            the ResultSet to cache
	 * @throws SQLException
	 *             if an error occurs reading the ResultSet
	 */
	public void put(String key, ResultSet resultSet) throws SQLException {
		if (!(resultSet instanceof MetadataResultSet metadataResultSet)) {
			logger.warn("Cannot cache non-MetadataResultSet: {}", resultSet.getClass().getName());
			return;
		}

		// Extract the data from MetadataResultSet
		String[] columnNames = metadataResultSet.getColumnNames();
		int[] columnTypes = metadataResultSet.getColumnTypes();
		List<Object[]> rows = metadataResultSet.getRows();

		Instant expiresAt = Instant.now().plus(ttl);
		cache.put(key, new CacheEntry(columnNames, columnTypes, rows, expiresAt));
		logger.trace("Cached {} rows for key: {} (expires: {})", rows.size(), key, expiresAt);
	}

	/**
	 * Clears all entries from the cache.
	 *
	 * <p>
	 * This method removes all cached metadata results immediately.
	 */
	public void clear() {
		int size = cache.size();
		cache.clear();
		logger.debug("Cache cleared ({} entries removed)", size);
	}

	/**
	 * Invalidates all cache entries with keys matching the specified prefix.
	 *
	 * <p>
	 * This is useful for invalidating related entries, for example all
	 * table-related caches for a specific dataset.
	 *
	 * @param keyPrefix
	 *            the prefix to match
	 */
	public void invalidate(String keyPrefix) {
		int removed = (int) cache.keySet().stream().filter(key -> key.startsWith(keyPrefix)).count();

		cache.keySet().removeIf(key -> key.startsWith(keyPrefix));
		logger.debug("Invalidated {} cache entries with prefix: {}", removed, keyPrefix);
	}

	/**
	 * Returns the number of entries currently in the cache.
	 *
	 * @return the cache size
	 */
	public int size() {
		return cache.size();
	}

	/**
	 * Returns statistics about the cache.
	 *
	 * @return cache statistics as a string
	 */
	public String getStats() {
		long expired = cache.values().stream().filter(CacheEntry::isExpired).count();
		return String.format("Cache size: %d, Expired: %d, TTL: %s", cache.size(), expired, ttl);
	}

	/**
	 * A cache entry containing metadata result data and expiration time.
	 *
	 * <p>
	 * This record stores the actual data rather than a ResultSet object, since
	 * ResultSets are stateful and can only be read once.
	 */
	private record CacheEntry(String[] columnNames, int[] columnTypes, List<Object[]> rows, Instant expiresAt) {

		boolean isExpired() {
			return Instant.now().isAfter(expiresAt);
		}

		ResultSet createResultSet() {
			return new MetadataResultSet(columnNames, columnTypes, rows);
		}
	}
}

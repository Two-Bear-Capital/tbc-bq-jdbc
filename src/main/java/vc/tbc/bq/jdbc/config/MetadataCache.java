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

import com.google.cloud.bigquery.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vc.tbc.bq.jdbc.TypeMapper;
import vc.tbc.bq.jdbc.metadata.MetadataResultSet;
import vc.tbc.bq.jdbc.metrics.DriverMetrics;
import vc.tbc.bq.jdbc.util.FieldValueConverter;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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
 * (TTL). Expired entries are automatically removed during get operations.
 *
 * <p>
 * <b>Thread Safety:</b> This class is thread-safe. The underlying storage uses
 * {@link ConcurrentHashMap} which provides concurrent access without external
 * synchronization. All cache operations (get, put, clear) can be safely called
 * from multiple threads.
 *
 * @since 1.0.0
 */
public final class MetadataCache {

	private static final Logger logger = LoggerFactory.getLogger(MetadataCache.class);
	private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

	/**
	 * Default ceiling on total cached rows across all entries.
	 *
	 * <p>
	 * The bound is rows rather than entries because entries are wildly uneven and
	 * only rows track memory. A {@code getColumns()} over a wide project is tens of
	 * thousands of rows while a {@code getSchemas()} is tens, so a limit of "N
	 * entries" would permit anything between a few kilobytes and hundreds of
	 * megabytes. Rows are also the one measure the cache can total cheaply, since
	 * it already holds every row as an {@code Object[]}.
	 *
	 * <p>
	 * 50,000 is chosen to stay useful rather than to be small. A project with a few
	 * hundred tables produces on the order of ten thousand rows from a single
	 * {@code getColumns()}, so the default still holds that result several times
	 * over plus every smaller query — which is what stops the 90-second metadata
	 * stalls the cache exists for. Worst case is a few tens of megabytes, against
	 * an <em>unbounded</em> cache before this.
	 */
	public static final int DEFAULT_MAX_ROWS = 50_000;

	private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
	private final Duration ttl;

	/** Ceiling on total cached rows; zero or less disables the bound. */
	private final int maxRows;

	// Schemas discovered via getSchemas(); used to drive speculative pre-warming.
	private final Set<String> knownSchemas = ConcurrentHashMap.newKeySet();

	// Keys of speculative queries currently in-flight; prevents duplicate BQ jobs.
	private final Set<String> inFlightSpeculative = ConcurrentHashMap.newKeySet();

	/**
	 * Creates a new metadata cache with the specified TTL.
	 *
	 * @param ttl
	 *            the time-to-live for cache entries
	 */
	public MetadataCache(Duration ttl) {
		this(ttl, DEFAULT_MAX_ROWS);
	}

	/**
	 * Creates a new metadata cache with the specified TTL and row ceiling.
	 *
	 * @param ttl
	 *            the time-to-live for cache entries
	 * @param maxRows
	 *            ceiling on total rows held across all entries; zero or less
	 *            disables the bound
	 */
	public MetadataCache(Duration ttl, int maxRows) {
		this.ttl = ttl != null ? ttl : DEFAULT_TTL;
		this.maxRows = maxRows;
		logger.debug("Metadata cache initialized with TTL: {}, max rows: {}", this.ttl,
				maxRows > 0 ? maxRows : "unbounded");
	}

	/**
	 * Creates a new metadata cache with the default TTL of 5 minutes.
	 */
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
			DriverMetrics.recordMetadataCacheMiss();
			logger.trace("Cache miss for key: {}", key);
			return Optional.empty();
		}

		if (entry.isExpired()) {
			cache.remove(key);
			// Counted as a miss, not as its own category: from the caller's point of
			// view an expired entry costs exactly what an absent one does - a round
			// trip to BigQuery - and the hit rate is only useful if it means "lookups
			// that avoided the network".
			DriverMetrics.recordMetadataCacheMiss();
			logger.trace("Cache entry expired for key: {}", key);
			return Optional.empty();
		}

		DriverMetrics.recordMetadataCacheHit();
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
	@SuppressWarnings("PMD.CloseResource") // metadataResultSet is obtained via pattern-match on the caller-owned
											// resultSet; we do not own it
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
		evictExpired();
		// The other put() overload is not the only way in, so the bound is enforced
		// on both. A ceiling applied on one of two insertion paths is not a ceiling.
		enforceRowBound(key);
		if (logger.isTraceEnabled()) {
			logger.trace("Cached {} rows for key: {} (expires: {})", rows.size(), key, expiresAt);
		}
	}

	/**
	 * Materialises and stores a BigQuery {@link TableResult} in the cache.
	 *
	 * <p>
	 * Column names are taken directly from the result schema. Column types are
	 * mapped via {@link TypeMapper#toJdbcType(Field)}. Row values are converted via
	 * {@link FieldValueConverter#toObject(FieldValue, Field)} — the same converter
	 * used by {@link vc.tbc.bq.jdbc.BQResultSet#getObject(int)}, ensuring
	 * consistent type representation.
	 *
	 * <p>
	 * Returns the materialised copy so callers do not have to read it back with
	 * {@link #get(String)}. That round trip was unsound: materialising drains the
	 * {@code TableResult}, so if the entry were evicted or expired between the
	 * write and the read, a caller falling back to the original {@code TableResult}
	 * would wrap an already-consumed result and silently produce no rows. An empty
	 * return means nothing was cached <em>and</em> the result was left untouched,
	 * so falling back to it is safe.
	 *
	 * @param key
	 *            the cache key
	 * @param result
	 *            the BigQuery result to materialise and cache
	 * @return the cached copy as a ResultSet, or empty if the result could not be
	 *         cached (in which case {@code result} has not been consumed)
	 */
	@SuppressWarnings("PMD.NullAssignment") // null represents SQL NULL in row data; intentional
	public Optional<ResultSet> put(String key, TableResult result) {
		Schema schema = result.getSchema();

		if (schema == null) {
			logger.warn("Cannot cache TableResult without schema: {}", result);
			return Optional.empty();
		}

		FieldList fields = schema.getFields();
		int fieldCount = fields.size();

		String[] columnNames = new String[fieldCount];
		int[] columnTypes = new int[fieldCount];
		for (int i = 0; i < fieldCount; i++) {
			Field field = fields.get(i);
			columnNames[i] = field.getName();
			columnTypes[i] = TypeMapper.toJdbcType(field);
		}

		long totalRows = result.getTotalRows();
		List<Object[]> rows = new ArrayList<>(totalRows > 0 ? (int) totalRows : 16);
		for (FieldValueList row : result.iterateAll()) {
			Object[] rowData = new Object[fieldCount];
			for (int i = 0; i < fieldCount; i++) {
				FieldValue fv = row.get(i);
				rowData[i] = fv.isNull() ? null : FieldValueConverter.toObject(fv, fields.get(i));
			}
			rows.add(rowData);
		}

		Instant expiresAt = Instant.now().plus(ttl);
		CacheEntry entry = new CacheEntry(columnNames, columnTypes, Collections.unmodifiableList(rows), expiresAt);
		cache.put(key, entry);
		evictExpired();
		enforceRowBound(key);
		logger.debug("Cached {} rows for key: {} (expires: {})", rows.size(), key, expiresAt);

		return Optional.of(entry.createResultSet());
	}

	/**
	 * Clears all entries from the cache.
	 *
	 * <p>
	 * This method removes all cached metadata results immediately.
	 */
	/**
	 * Drops entries whose TTL has passed.
	 *
	 * <p>
	 * Expiry was previously only noticed when the same key was read again, so an
	 * entry nobody asked for a second time stayed in memory for the life of the
	 * process — and these caches are static, shared between connections and
	 * deliberately never cleared on close. A long-lived host such as IntelliJ,
	 * which is the reason the cache is shared in the first place, would keep every
	 * materialised metadata row it ever fetched.
	 *
	 * <p>
	 * Called after each insert. The map is small (one entry per metadata query
	 * shape) so the scan is cheap next to the BigQuery query that filled it.
	 */
	private void evictExpired() {
		cache.entrySet().removeIf(entry -> entry.getValue().isExpired());
	}

	/**
	 * Drops the oldest entries until the total row count is back under
	 * {@link #maxRows}.
	 *
	 * <p>
	 * Expiry alone did not bound this cache. Within a single TTL window every
	 * distinct query shape gets its own entry holding every materialised row of its
	 * result, nothing removes an entry that has not expired, and the cache is
	 * static and shared for the life of the process. A host that issues many
	 * different metadata patterns — which is exactly what an IDE does, and IDEs are
	 * the reason the cache is shared in the first place — grew it without limit.
	 *
	 * <p>
	 * <b>Eviction is oldest-first, not least-recently-used.</b> LRU would need the
	 * read path to record an access on every hit, and that means a write to shared
	 * state on the one path that must stay concurrent — these lookups are served
	 * entirely from memory and are read by every connection at once. Ordering by
	 * {@code expiresAt} costs the read path nothing and is exactly insertion order,
	 * because every entry in a given cache takes the same TTL. Entries expire on
	 * their own anyway; this is a ceiling, not the primary mechanism.
	 *
	 * <p>
	 * The entry just inserted is never evicted. Without that, a single result
	 * larger than the whole budget would be cached and immediately dropped on every
	 * call, turning a wide project's {@code getColumns()} into a guaranteed miss
	 * forever — the stall the cache exists to prevent. The bound is therefore a
	 * ceiling on everything <em>except</em> one oversized entry, which is the safer
	 * way to be wrong.
	 *
	 * @param justInserted
	 *            key of the entry that must survive
	 */
	private void enforceRowBound(String justInserted) {
		if (maxRows <= 0) {
			return;
		}

		long total = 0;
		for (CacheEntry entry : cache.values()) {
			total += entry.rowCount();
		}
		if (total <= maxRows) {
			return;
		}

		// Sorting the whole map on each insert is fine: inserts happen only on a
		// cache miss, which just paid for a BigQuery round trip, and the map is
		// bounded by this very method.
		List<Map.Entry<String, CacheEntry>> oldestFirst = new ArrayList<>(cache.entrySet());
		oldestFirst.sort(Comparator.comparing(e -> e.getValue().expiresAt()));

		int evicted = 0;
		for (Map.Entry<String, CacheEntry> candidate : oldestFirst) {
			if (total <= maxRows) {
				break;
			}
			if (candidate.getKey().equals(justInserted)) {
				continue;
			}
			// Two-argument remove: another thread may have replaced this entry since
			// the snapshot, and that newer entry must not be dropped on its behalf.
			if (cache.remove(candidate.getKey(), candidate.getValue())) {
				total -= candidate.getValue().rowCount();
				evicted++;
			}
		}

		if (evicted > 0) {
			logger.debug("Metadata cache over its {}-row ceiling: evicted {} oldest entry(ies), now {} rows", maxRows,
					evicted, total);
		}
	}

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
		int[] removed = {0};
		cache.keySet().removeIf(key -> {
			if (key.startsWith(keyPrefix)) {
				removed[0]++;
				return true;
			}
			return false;
		});
		logger.debug("Invalidated {} cache entries with prefix: {}", removed[0], keyPrefix);
	}

	/**
	 * Registers all schemas known for this project, populated from
	 * {@code getSchemas()}. Used to drive speculative pre-warming of
	 * INFORMATION_SCHEMA queries across all known schemas.
	 *
	 * @param schemas
	 *            the schema/dataset names to register
	 */
	public void registerKnownSchemas(Collection<String> schemas) {
		knownSchemas.addAll(schemas);
		logger.debug("Registered {} known schemas for pre-warming", schemas.size());
	}

	/**
	 * Returns an unmodifiable view of the currently known schema names.
	 *
	 * @return the set of known schema names
	 */
	public Set<String> getKnownSchemas() {
		return Collections.unmodifiableSet(knownSchemas);
	}

	/**
	 * Tries to claim a speculative cache key for in-flight tracking.
	 *
	 * @param key
	 *            the cache key to claim
	 * @return {@code true} if the key was claimed (caller should fire the query),
	 *         {@code false} if already claimed or already in-flight
	 */
	public boolean claimSpeculative(String key) {
		return inFlightSpeculative.add(key);
	}

	/**
	 * Releases an in-flight speculative key after the query completes or fails.
	 *
	 * @param key
	 *            the cache key to release
	 */
	public void releaseSpeculative(String key) {
		inFlightSpeculative.remove(key);
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
		return String.format("Cache size: %d, Rows: %d/%s, Expired: %d, TTL: %s", cache.size(), totalRows(),
				maxRows > 0 ? String.valueOf(maxRows) : "unbounded", expired, ttl);
	}

	/**
	 * Total rows held across all entries.
	 *
	 * <p>
	 * This, not {@link #size()}, is what tracks the cache's memory: entries range
	 * from a handful of rows to tens of thousands, so an entry count says very
	 * little about how much is being held.
	 *
	 * @return the summed row count of every entry
	 */
	public long totalRows() {
		long total = 0;
		for (CacheEntry entry : cache.values()) {
			total += entry.rowCount();
		}
		return total;
	}

	/**
	 * The configured row ceiling.
	 *
	 * @return the ceiling, or zero or less if the cache is unbounded
	 */
	public int getMaxRows() {
		return maxRows;
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

		/** What this entry costs against the cache's row ceiling. */
		int rowCount() {
			return rows.size();
		}

		ResultSet createResultSet() {
			return new MetadataResultSet(columnNames, columnTypes, rows);
		}
	}
}

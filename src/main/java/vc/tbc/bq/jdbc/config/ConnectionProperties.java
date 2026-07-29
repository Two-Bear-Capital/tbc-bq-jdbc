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

import com.google.cloud.bigquery.DatasetId;
import vc.tbc.bq.jdbc.auth.AuthType;

import java.util.Map;
import java.util.Objects;

/**
 * Connection properties for a BigQuery JDBC connection.
 *
 * <p>
 * This record is immutable. The labels map is defensively copied and stored as
 * an unmodifiable map to prevent external modification.
 *
 * @param projectId
 *            the Google Cloud project ID (required)
 * @param datasetId
 *            the default dataset name (optional)
 * @param datasetProjectId
 *            the project ID for the dataset if different from connection
 *            project (optional)
 * @param authType
 *            the authentication type (required)
 * @param host
 *            custom BigQuery API endpoint, e.g. a proxy or Private Service
 *            Connect (optional)
 * @param port
 *            custom port for BigQuery API (optional)
 * @param timeoutSeconds
 *            query timeout in seconds (default: 300)
 * @param maxResults
 *            maximum number of rows to fetch (default: null = unlimited)
 * @param useLegacySql
 *            whether to use legacy SQL (default: false)
 * @param location
 *            BigQuery location (e.g., US, EU) (optional)
 * @param labels
 *            job labels as key-value pairs (optional, immutable)
 * @param pageSize
 *            result page size for pagination (default: 50000)
 * @param useStorageApi
 *            Storage API mode: auto, true, false (default: false)
 * @param enableSessions
 *            whether to use BigQuery sessions (default: false)
 * @param connectionTimeout
 *            connection establishment timeout in seconds (default: 30)
 * @param retryCount
 *            retry attempts for transient errors (default: 3)
 * @param maxBillingBytes
 *            query cost limit in bytes (optional)
 * @param metadataCacheTtl
 *            metadata cache TTL in seconds (default: 300 = 5 minutes)
 * @param metadataCacheEnabled
 *            whether to enable metadata caching (default: true)
 * @param metadataLazyLoad
 *            whether to use lazy loading for metadata (default: false)
 * @param enableQueryCostEstimation
 *            whether to run dry-run before each query to estimate cost
 *            (default: false). Cost estimates are attached as SQLWarnings.
 * @param nativeComplexTypes
 *            whether to return ARRAY and STRUCT as native JDBC Array/Struct
 *            objects instead of JSON strings (default: false). When false,
 *            complex types are returned as JSON strings for IntelliJ
 *            compatibility.
 * @param metadataCacheMaxRows
 *            row ceiling for a single cached metadata result (default:
 *            {@link MetadataCache#DEFAULT_MAX_ROWS})
 * @param metadataIncludeDescriptions
 *            whether {@code getTables} reads table descriptions into
 *            {@code REMARKS} (default: true). Costs one
 *            {@code INFORMATION_SCHEMA} query per dataset scanned.
 * @since 1.0.0
 */
public record ConnectionProperties(String projectId, String datasetId, String datasetProjectId, AuthType authType,
		String host, Integer port, Integer timeoutSeconds, Long maxResults, boolean useLegacySql, String location,
		Map<String, String> labels, Integer pageSize, String useStorageApi, boolean enableSessions,
		Integer connectionTimeout, Integer retryCount, Long maxBillingBytes, Integer metadataCacheTtl,
		Boolean metadataCacheEnabled, Boolean metadataLazyLoad, Boolean enableQueryCostEstimation,
		Boolean nativeComplexTypes, Integer metadataCacheMaxRows, Boolean metadataIncludeDescriptions) {

	/** Default timeout in seconds. */
	public static final int DEFAULT_TIMEOUT_SECONDS = 300;

	/** Default page size. */
	/**
	 * Rows requested per {@code jobs.getQueryResults} page.
	 *
	 * <p>
	 * This is the page size of the REST calls underneath
	 * {@code TableResult.iterateAll()}, so on a large result it decides how many
	 * HTTP round trips the client pays for.
	 *
	 * <p>
	 * 50,000 balances the two result shapes: raising it further helps narrow rows
	 * only marginally and can hurt wide ones, where each page is capped by response
	 * bytes rather than row count.
	 *
	 * <p>
	 * Raising it does not risk unbounded page memory. BigQuery bounds a
	 * {@code getQueryResults} page by response bytes as well as by row count -- "if
	 * the result is larger than the byte or field limit, the result is trimmed to
	 * fit the limit" -- so a larger row count yields more rows per page only while
	 * the payload stays under that ceiling.
	 */
	public static final int DEFAULT_PAGE_SIZE = 50000;

	/** Default connection timeout in seconds. */
	public static final int DEFAULT_CONNECTION_TIMEOUT = 30;

	/**
	 * Default number of attempts per BigQuery API call, including the first.
	 *
	 * <p>
	 * Deliberately equal to the Google client library's own default: this value is
	 * applied to the client, so a smaller number here would quietly reduce
	 * resilience for every connection that never sets the property.
	 */
	public static final int DEFAULT_RETRY_COUNT = 6;

	/** Default metadata cache TTL in seconds (5 minutes). */
	public static final int DEFAULT_METADATA_CACHE_TTL = 300;

	public ConnectionProperties {
		Objects.requireNonNull(projectId, "projectId is required");
		if (projectId.isBlank()) {
			throw new IllegalArgumentException("projectId cannot be blank");
		}
		Objects.requireNonNull(authType, "authType is required");
		// Defensive copy: create an immutable map to prevent external modification
		labels = labels == null ? Map.of() : Map.copyOf(labels);
		if (timeoutSeconds == null) {
			timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
		}
		if (pageSize == null) {
			pageSize = DEFAULT_PAGE_SIZE;
		}
		if (useStorageApi == null) {
			// The Storage Read API path works and is much faster on large results, but
			// it stays opt-in for now: enabling it by default would change how every
			// large query is fetched, and it needs a JVM flag (see ArrowSupport) that
			// most environments do not pass. Moving this to "auto" is a deliberate
			// follow-up once the path has seen real use, not something to drift into.
			useStorageApi = "false";
		}
		if (connectionTimeout == null) {
			connectionTimeout = DEFAULT_CONNECTION_TIMEOUT;
		}
		if (retryCount == null) {
			retryCount = DEFAULT_RETRY_COUNT;
		}
		if (metadataCacheTtl == null) {
			metadataCacheTtl = DEFAULT_METADATA_CACHE_TTL;
		}
		if (metadataCacheEnabled == null) {
			metadataCacheEnabled = true;
		}
		if (metadataLazyLoad == null) {
			metadataLazyLoad = false;
		}
		if (enableQueryCostEstimation == null) {
			enableQueryCostEstimation = false;
		}
		if (nativeComplexTypes == null) {
			nativeComplexTypes = false;
		}
		if (metadataCacheMaxRows == null) {
			metadataCacheMaxRows = MetadataCache.DEFAULT_MAX_ROWS;
		}
		if (metadataIncludeDescriptions == null) {
			// On by default: without it REMARKS is the empty string for every table in
			// every project, which reads as "this table has no comment" rather than as
			// a setting nobody turned on. The cost is one INFORMATION_SCHEMA query per
			// dataset, cached for the metadata TTL, and this is the opt-out for the
			// project large enough to feel it.
			metadataIncludeDescriptions = true;
		}
	}

	/**
	 * Creates properties without {@code metadataIncludeDescriptions}, which then
	 * takes its default.
	 *
	 * <p>
	 * Present for the same reason as the overload below it: the canonical
	 * constructor of a public record is part of its ABI, so growing the component
	 * list would break existing callers at source and binary level.
	 *
	 * @param projectId
	 *            the GCP project id
	 * @param datasetId
	 *            the default dataset, or null
	 * @param datasetProjectId
	 *            the project owning the dataset, or null to use {@code projectId}
	 * @param authType
	 *            the authentication type
	 * @param host
	 *            the API host override, or null
	 * @param port
	 *            the API port override, or null
	 * @param timeoutSeconds
	 *            query timeout in seconds
	 * @param maxResults
	 *            maximum rows to return, or null
	 * @param useLegacySql
	 *            whether to use legacy SQL
	 * @param location
	 *            the dataset location, or null
	 * @param labels
	 *            job labels
	 * @param pageSize
	 *            result page size
	 * @param useStorageApi
	 *            Storage Read API setting
	 * @param enableSessions
	 *            whether to create a session eagerly
	 * @param connectionTimeout
	 *            connection timeout in seconds
	 * @param retryCount
	 *            retry count
	 * @param maxBillingBytes
	 *            per-query billed-bytes ceiling, or null
	 * @param metadataCacheTtl
	 *            metadata cache TTL in seconds
	 * @param metadataCacheEnabled
	 *            whether the metadata cache is enabled
	 * @param metadataLazyLoad
	 *            whether metadata loads lazily
	 * @param enableQueryCostEstimation
	 *            whether to estimate query cost
	 * @param nativeComplexTypes
	 *            whether ARRAY/STRUCT map to native JDBC types
	 * @param metadataCacheMaxRows
	 *            row ceiling for one cached metadata result
	 */
	public ConnectionProperties(String projectId, String datasetId, String datasetProjectId, AuthType authType,
			String host, Integer port, Integer timeoutSeconds, Long maxResults, boolean useLegacySql, String location,
			Map<String, String> labels, Integer pageSize, String useStorageApi, boolean enableSessions,
			Integer connectionTimeout, Integer retryCount, Long maxBillingBytes, Integer metadataCacheTtl,
			Boolean metadataCacheEnabled, Boolean metadataLazyLoad, Boolean enableQueryCostEstimation,
			Boolean nativeComplexTypes, Integer metadataCacheMaxRows) {
		this(projectId, datasetId, datasetProjectId, authType, host, port, timeoutSeconds, maxResults, useLegacySql,
				location, labels, pageSize, useStorageApi, enableSessions, connectionTimeout, retryCount,
				maxBillingBytes, metadataCacheTtl, metadataCacheEnabled, metadataLazyLoad, enableQueryCostEstimation,
				nativeComplexTypes, metadataCacheMaxRows, null);
	}

	/**
	 * Creates properties without {@code metadataCacheMaxRows}, which then takes its
	 * default.
	 *
	 * <p>
	 * This overload exists so that adding {@code metadataCacheMaxRows} to a public
	 * record did not become a breaking change. A record's canonical constructor is
	 * part of its ABI, so growing the component list would have broken every caller
	 * at both source and binary level — and under this project's Conventional
	 * Commits release rules that means a major version bump, which is a steep price
	 * for adding a bound to a cache. Declaring the previous signature explicitly
	 * keeps existing source compiling and existing bytecode linking.
	 *
	 * @param projectId
	 *            the GCP project id
	 * @param datasetId
	 *            the default dataset, or null
	 * @param datasetProjectId
	 *            the project owning the dataset, or null to use {@code projectId}
	 * @param authType
	 *            the authentication type
	 * @param host
	 *            the API host override, or null
	 * @param port
	 *            the API port override, or null
	 * @param timeoutSeconds
	 *            query timeout in seconds
	 * @param maxResults
	 *            maximum rows to return, or null
	 * @param useLegacySql
	 *            whether to use legacy SQL
	 * @param location
	 *            the dataset location, or null
	 * @param labels
	 *            job labels
	 * @param pageSize
	 *            result page size
	 * @param useStorageApi
	 *            Storage Read API setting
	 * @param enableSessions
	 *            whether to create a session eagerly
	 * @param connectionTimeout
	 *            connection timeout in seconds
	 * @param retryCount
	 *            retry count
	 * @param maxBillingBytes
	 *            per-query billed-bytes ceiling, or null
	 * @param metadataCacheTtl
	 *            metadata cache TTL in seconds
	 * @param metadataCacheEnabled
	 *            whether the metadata cache is enabled
	 * @param metadataLazyLoad
	 *            whether metadata loads lazily
	 * @param enableQueryCostEstimation
	 *            whether to estimate query cost
	 * @param nativeComplexTypes
	 *            whether ARRAY/STRUCT map to native JDBC types
	 */
	public ConnectionProperties(String projectId, String datasetId, String datasetProjectId, AuthType authType,
			String host, Integer port, Integer timeoutSeconds, Long maxResults, boolean useLegacySql, String location,
			Map<String, String> labels, Integer pageSize, String useStorageApi, boolean enableSessions,
			Integer connectionTimeout, Integer retryCount, Long maxBillingBytes, Integer metadataCacheTtl,
			Boolean metadataCacheEnabled, Boolean metadataLazyLoad, Boolean enableQueryCostEstimation,
			Boolean nativeComplexTypes) {
		this(projectId, datasetId, datasetProjectId, authType, host, port, timeoutSeconds, maxResults, useLegacySql,
				location, labels, pageSize, useStorageApi, enableSessions, connectionTimeout, retryCount,
				maxBillingBytes, metadataCacheTtl, metadataCacheEnabled, metadataLazyLoad, enableQueryCostEstimation,
				nativeComplexTypes, null);
	}

	/**
	 * Creates a DatasetId for the default dataset.
	 *
	 * @return the DatasetId, or null if no dataset is configured
	 */
	public DatasetId getDatasetId() {
		if (datasetId == null) {
			return null;
		}
		String project = datasetProjectId != null ? datasetProjectId : projectId;
		return DatasetId.of(project, datasetId);
	}
}

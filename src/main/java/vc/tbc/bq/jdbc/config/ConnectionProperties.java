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
import vc.tbc.bq.jdbc.transport.TransportConfig;

import java.math.BigDecimal;
import java.util.List;
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
 *            custom BigQuery API endpoint, e.g. Private Service Connect
 *            (optional). Says where BigQuery is; see {@code transport} for how
 *            the driver reaches it
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
 * @param queryPricePerTiB
 *            price of one tebibyte of billed query data, in whatever currency
 *            the caller uses (optional). Unset means cost estimates report
 *            bytes only, because the driver cannot know a customer's contract.
 * @param metadataIncludeDescriptions
 *            whether {@code getTables} reads table descriptions into
 *            {@code REMARKS} (default: true). Costs one
 *            {@code INFORMATION_SCHEMA} query per dataset scanned.
 * @param batchLoadThreshold
 *            row count at or above which {@code executeBatch()} submits a
 *            BigQuery load job instead of chunked INSERT DML (optional; unset
 *            disables the load path entirely).
 * @param collapseShardedTables
 *            whether date-sharded tables ({@code events_20260101}, …) are
 *            reported as one {@code events_*} entry (default: false). Sharding
 *            is a naming convention, not something BigQuery declares, so this
 *            is opt-in.
 * @param additionalProjects
 *            further projects to report from {@code getCatalogs()}, so a tool
 *            can discover and switch to them (default: empty). Not discovered
 *            automatically — listing every project a credential can see is a
 *            different, much slower call, and most of them are not BigQuery
 *            projects at all.
 * @param includeStructFields
 *            whether {@code getColumns()} adds a row per {@code STRUCT} field,
 *            named by its dotted path (default: false). Opt-in: it changes the
 *            row count of every {@code getColumns()} call, and a tool that
 *            builds a column list from it would treat a field as a column.
 * @param includeInformationSchema
 *            whether {@code INFORMATION_SCHEMA} is browsable — a synthetic
 *            schema per project, and its dataset-scoped views as tables of each
 *            dataset (default: true). Costs no BigQuery query; the view list is
 *            static.
 * @param metadataJobCreationOptional
 *            whether metadata reads ask BigQuery to skip job creation (default:
 *            true). Applies only to the {@code INFORMATION_SCHEMA} queries this
 *            driver issues for {@link java.sql.DatabaseMetaData}, never to a
 *            caller's own statements.
 * @param transport
 *            how BigQuery calls and OAuth token requests reach Google: the
 *            proxy to route through and the truststore to verify against
 *            (default: {@link TransportConfig#direct()}, adjusted by whatever
 *            the JVM's own {@code https.proxy*} and
 *            {@code javax.net.ssl.trustStore*} properties say). Distinct from
 *            {@code host}, which changes <em>where</em> BigQuery is rather than
 *            how the driver gets there. Never null.
 * @param enableTracing
 *            whether the driver emits OpenTelemetry spans (default: true).
 *            Emitting requires the host to supply the OpenTelemetry API and
 *            register an SDK — without both, a span costs a no-op call and
 *            nothing leaves the process — so the useful setting is
 *            {@code false}, to keep a driver quiet inside a host that traces
 *            everything else.
 * @since 1.0.0
 */
public record ConnectionProperties(String projectId, String datasetId, String datasetProjectId, AuthType authType,
		String host, Integer port, Integer timeoutSeconds, Long maxResults, boolean useLegacySql, String location,
		Map<String, String> labels, Integer pageSize, String useStorageApi, boolean enableSessions,
		Integer connectionTimeout, Integer retryCount, Long maxBillingBytes, Integer metadataCacheTtl,
		Boolean metadataCacheEnabled, Boolean metadataLazyLoad, Boolean enableQueryCostEstimation,
		Boolean nativeComplexTypes, Integer metadataCacheMaxRows, BigDecimal queryPricePerTiB,
		Boolean metadataIncludeDescriptions, Boolean collapseShardedTables, Integer batchLoadThreshold,
		Boolean includeInformationSchema, List<String> additionalProjects, Boolean includeStructFields,
		Boolean metadataJobCreationOptional, TransportConfig transport, Boolean enableTracing) {

	/** Default timeout in seconds. */
	public static final int DEFAULT_TIMEOUT_SECONDS = 300;

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
		if (collapseShardedTables == null) {
			// Opt-in, unlike metadataIncludeDescriptions: collapsing removes rows
			// rather than filling one in, and the evidence for a set is a naming
			// convention BigQuery never declared. A table legitimately named
			// <something>_20260101 must not vanish from a listing by default.
			collapseShardedTables = false;
		}
		// Defensive copy, and the connection's own project is never a member: it is
		// always reported, so listing it here too would duplicate a catalog row.
		additionalProjects = additionalProjects == null
				? List.of()
				: additionalProjects.stream().filter(java.util.Objects::nonNull).map(String::trim)
						.filter(project -> !project.isEmpty() && !project.equals(projectId)).distinct().toList();
		if (includeStructFields == null) {
			// Off by default, unlike includeInformationSchema: that one adds entries
			// a caller can act on unchanged, where this one adds rows that are not
			// columns in the sense the rest of JDBC means. It also costs a second
			// INFORMATION_SCHEMA query per dataset, which the default must not.
			includeStructFields = false;
		}
		if (includeInformationSchema == null) {
			// On by default, like metadataIncludeDescriptions and unlike
			// collapseShardedTables: it adds entries that are genuinely there and
			// queryable, rather than removing or renaming rows. It also costs nothing
			// — the view list is static, so no BigQuery query is issued to produce it.
			// The opt-out exists because BigQuery's own INFORMATION_SCHEMA.SCHEMATA
			// does not report this schema, so a tool that diffs schema lists against
			// the service will see one the service does not admit to.
			includeInformationSchema = true;
		}
		if (metadataJobCreationOptional == null) {
			// On by default, and the only property here whose fallback BigQuery owns
			// rather than the driver. JOB_CREATION_OPTIONAL is a request, not an
			// instruction: the service answers small results without a job and creates
			// one anyway above a threshold of its own, so a dataset too large to
			// qualify simply behaves as it does today. Measured at 19-37% off the p50
			// of every metadata read that qualifies (#265). Results are byte-identical
			// either way, and a jobless query still appears in INFORMATION_SCHEMA.JOBS,
			// so the opt-out is for a caller who needs a real job id per metadata read
			// rather than for anyone worried about the rows.
			metadataJobCreationOptional = true;
		}
		// Never null, so properties.transport().proxy() needs no null check at any
		// call site. "Nothing configured" is a value here, not an absence.
		if (transport == null) {
			transport = TransportConfig.direct();
		}
		if (enableTracing == null) {
			// On by default, unlike every other opt-in here, because the gate that
			// matters is not this flag: with no OpenTelemetry SDK registered the span
			// is OpenTelemetry's own no-op and nothing is emitted. A host that has
			// configured tracing has already opted in, and would otherwise have to
			// repeat itself in every connection string to see the driver's spans.
			enableTracing = true;
		}
		// No default: a load job is a different mechanism, not a faster one of the
		// same kind. It is not DML-quota bound, but neither is it transactional nor
		// atomic with surrounding DML, so switching to it at some row count would
		// change the failure modes of a batch the caller wrote as an INSERT.
		if (batchLoadThreshold != null && batchLoadThreshold < 1) {
			throw new IllegalArgumentException("batchLoadThreshold must be at least 1: " + batchLoadThreshold);
		}
		// No default: a rate the driver invented would be wrong for every customer
		// not on on-demand pricing, and would silently go stale. Unset means
		// estimates report bytes and no money. Rejected rather than clamped,
		// because a negative price is a typo, not an intent.
		if (queryPricePerTiB != null && queryPricePerTiB.signum() < 0) {
			throw new IllegalArgumentException("queryPricePerTiB cannot be negative: " + queryPricePerTiB);
		}
	}

	/**
	 * Creates properties without {@code enableTracing}, which then defaults to on.
	 *
	 * <p>
	 * Same reason as the overloads below it: a record's canonical constructor is
	 * part of its ABI, so growing the component list would break existing callers
	 * at source and binary level. This is the canonical shape as of 4.3.0.
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
	 * @param queryPricePerTiB
	 *            price per tebibyte billed, or null
	 * @param metadataIncludeDescriptions
	 *            whether table descriptions are read into REMARKS
	 * @param collapseShardedTables
	 *            whether date-sharded tables collapse to one wildcard entry
	 * @param batchLoadThreshold
	 *            row count at which executeBatch uses a load job, or null
	 * @param includeInformationSchema
	 *            whether INFORMATION_SCHEMA is browsable
	 * @param additionalProjects
	 *            further projects reported from getCatalogs()
	 * @param includeStructFields
	 *            whether getColumns() adds a row per STRUCT field
	 * @param metadataJobCreationOptional
	 *            whether metadata reads ask BigQuery to skip job creation
	 * @param transport
	 *            the proxy and truststore settings, or null for the defaults
	 */
	public ConnectionProperties(String projectId, String datasetId, String datasetProjectId, AuthType authType,
			String host, Integer port, Integer timeoutSeconds, Long maxResults, boolean useLegacySql, String location,
			Map<String, String> labels, Integer pageSize, String useStorageApi, boolean enableSessions,
			Integer connectionTimeout, Integer retryCount, Long maxBillingBytes, Integer metadataCacheTtl,
			Boolean metadataCacheEnabled, Boolean metadataLazyLoad, Boolean enableQueryCostEstimation,
			Boolean nativeComplexTypes, Integer metadataCacheMaxRows, BigDecimal queryPricePerTiB,
			Boolean metadataIncludeDescriptions, Boolean collapseShardedTables, Integer batchLoadThreshold,
			Boolean includeInformationSchema, List<String> additionalProjects, Boolean includeStructFields,
			Boolean metadataJobCreationOptional, TransportConfig transport) {
		this(projectId, datasetId, datasetProjectId, authType, host, port, timeoutSeconds, maxResults, useLegacySql,
				location, labels, pageSize, useStorageApi, enableSessions, connectionTimeout, retryCount,
				maxBillingBytes, metadataCacheTtl, metadataCacheEnabled, metadataLazyLoad, enableQueryCostEstimation,
				nativeComplexTypes, metadataCacheMaxRows, queryPricePerTiB, metadataIncludeDescriptions,
				collapseShardedTables, batchLoadThreshold, includeInformationSchema, additionalProjects,
				includeStructFields, metadataJobCreationOptional, transport, null);
	}

	/**
	 * Creates properties without {@code transport}, which then means a direct
	 * connection verified against the JDK truststore, unless the JVM's own
	 * {@code https.proxy*} or {@code javax.net.ssl.trustStore*} properties say
	 * otherwise.
	 *
	 * <p>
	 * Same reason as the overloads below it: a record's canonical constructor is
	 * part of its ABI, so growing the component list would break existing callers
	 * at source and binary level.
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
	 * @param queryPricePerTiB
	 *            price per tebibyte billed, or null
	 * @param metadataIncludeDescriptions
	 *            whether table descriptions are read into REMARKS
	 * @param collapseShardedTables
	 *            whether date-sharded tables collapse to one wildcard entry
	 * @param batchLoadThreshold
	 *            row count at which executeBatch uses a load job, or null
	 * @param includeInformationSchema
	 *            whether INFORMATION_SCHEMA is browsable
	 * @param additionalProjects
	 *            further projects reported from getCatalogs()
	 * @param includeStructFields
	 *            whether getColumns() adds a row per STRUCT field
	 * @param metadataJobCreationOptional
	 *            whether metadata reads ask BigQuery to skip job creation
	 */
	public ConnectionProperties(String projectId, String datasetId, String datasetProjectId, AuthType authType,
			String host, Integer port, Integer timeoutSeconds, Long maxResults, boolean useLegacySql, String location,
			Map<String, String> labels, Integer pageSize, String useStorageApi, boolean enableSessions,
			Integer connectionTimeout, Integer retryCount, Long maxBillingBytes, Integer metadataCacheTtl,
			Boolean metadataCacheEnabled, Boolean metadataLazyLoad, Boolean enableQueryCostEstimation,
			Boolean nativeComplexTypes, Integer metadataCacheMaxRows, BigDecimal queryPricePerTiB,
			Boolean metadataIncludeDescriptions, Boolean collapseShardedTables, Integer batchLoadThreshold,
			Boolean includeInformationSchema, List<String> additionalProjects, Boolean includeStructFields,
			Boolean metadataJobCreationOptional) {
		this(projectId, datasetId, datasetProjectId, authType, host, port, timeoutSeconds, maxResults, useLegacySql,
				location, labels, pageSize, useStorageApi, enableSessions, connectionTimeout, retryCount,
				maxBillingBytes, metadataCacheTtl, metadataCacheEnabled, metadataLazyLoad, enableQueryCostEstimation,
				nativeComplexTypes, metadataCacheMaxRows, queryPricePerTiB, metadataIncludeDescriptions,
				collapseShardedTables, batchLoadThreshold, includeInformationSchema, additionalProjects,
				includeStructFields, metadataJobCreationOptional, null, null);
	}

	/**
	 * Creates properties without {@code metadataJobCreationOptional}, which then
	 * takes its default.
	 *
	 * <p>
	 * Same reason as the overloads below it: a record's canonical constructor is
	 * part of its ABI, so growing the component list would break existing callers
	 * at source and binary level.
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
	 * @param queryPricePerTiB
	 *            price per tebibyte billed, or null
	 * @param metadataIncludeDescriptions
	 *            whether table descriptions are read into REMARKS
	 * @param collapseShardedTables
	 *            whether date-sharded tables collapse to one wildcard entry
	 * @param batchLoadThreshold
	 *            row count at which executeBatch uses a load job, or null
	 * @param includeInformationSchema
	 *            whether INFORMATION_SCHEMA is browsable
	 * @param additionalProjects
	 *            further projects reported from getCatalogs()
	 * @param includeStructFields
	 *            whether getColumns() adds a row per STRUCT field
	 */
	public ConnectionProperties(String projectId, String datasetId, String datasetProjectId, AuthType authType,
			String host, Integer port, Integer timeoutSeconds, Long maxResults, boolean useLegacySql, String location,
			Map<String, String> labels, Integer pageSize, String useStorageApi, boolean enableSessions,
			Integer connectionTimeout, Integer retryCount, Long maxBillingBytes, Integer metadataCacheTtl,
			Boolean metadataCacheEnabled, Boolean metadataLazyLoad, Boolean enableQueryCostEstimation,
			Boolean nativeComplexTypes, Integer metadataCacheMaxRows, BigDecimal queryPricePerTiB,
			Boolean metadataIncludeDescriptions, Boolean collapseShardedTables, Integer batchLoadThreshold,
			Boolean includeInformationSchema, List<String> additionalProjects, Boolean includeStructFields) {
		this(projectId, datasetId, datasetProjectId, authType, host, port, timeoutSeconds, maxResults, useLegacySql,
				location, labels, pageSize, useStorageApi, enableSessions, connectionTimeout, retryCount,
				maxBillingBytes, metadataCacheTtl, metadataCacheEnabled, metadataLazyLoad, enableQueryCostEstimation,
				nativeComplexTypes, metadataCacheMaxRows, queryPricePerTiB, metadataIncludeDescriptions,
				collapseShardedTables, batchLoadThreshold, includeInformationSchema, additionalProjects,
				includeStructFields, null);
	}

	/**
	 * Creates properties without {@code includeInformationSchema}, which then takes
	 * its default.
	 *
	 * <p>
	 * Same reason as the overloads below it: a record's canonical constructor is
	 * part of its ABI, so declaring the previous signature explicitly keeps
	 * existing source compiling and existing bytecode linking. 4.0.0 breaks the
	 * {@link AuthType} hierarchy deliberately; it need not also break this.
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
	 * @param queryPricePerTiB
	 *            price per tebibyte billed, or null
	 * @param metadataIncludeDescriptions
	 *            whether table descriptions are read into REMARKS
	 * @param collapseShardedTables
	 *            whether date-sharded tables collapse to one wildcard entry
	 * @param batchLoadThreshold
	 *            row count at which executeBatch uses a load job, or null
	 */
	public ConnectionProperties(String projectId, String datasetId, String datasetProjectId, AuthType authType,
			String host, Integer port, Integer timeoutSeconds, Long maxResults, boolean useLegacySql, String location,
			Map<String, String> labels, Integer pageSize, String useStorageApi, boolean enableSessions,
			Integer connectionTimeout, Integer retryCount, Long maxBillingBytes, Integer metadataCacheTtl,
			Boolean metadataCacheEnabled, Boolean metadataLazyLoad, Boolean enableQueryCostEstimation,
			Boolean nativeComplexTypes, Integer metadataCacheMaxRows, BigDecimal queryPricePerTiB,
			Boolean metadataIncludeDescriptions, Boolean collapseShardedTables, Integer batchLoadThreshold) {
		this(projectId, datasetId, datasetProjectId, authType, host, port, timeoutSeconds, maxResults, useLegacySql,
				location, labels, pageSize, useStorageApi, enableSessions, connectionTimeout, retryCount,
				maxBillingBytes, metadataCacheTtl, metadataCacheEnabled, metadataLazyLoad, enableQueryCostEstimation,
				nativeComplexTypes, metadataCacheMaxRows, queryPricePerTiB, metadataIncludeDescriptions,
				collapseShardedTables, batchLoadThreshold, null, null, null);
	}

	/**
	 * Creates properties without {@code additionalProjects}, which then defaults to
	 * empty.
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
	 * @param queryPricePerTiB
	 *            price per tebibyte billed, or null
	 * @param metadataIncludeDescriptions
	 *            whether table descriptions are read into REMARKS
	 * @param collapseShardedTables
	 *            whether date-sharded tables collapse to one wildcard entry
	 * @param batchLoadThreshold
	 *            row count at which executeBatch uses a load job, or null
	 * @param includeInformationSchema
	 *            whether INFORMATION_SCHEMA is browsable
	 */
	public ConnectionProperties(String projectId, String datasetId, String datasetProjectId, AuthType authType,
			String host, Integer port, Integer timeoutSeconds, Long maxResults, boolean useLegacySql, String location,
			Map<String, String> labels, Integer pageSize, String useStorageApi, boolean enableSessions,
			Integer connectionTimeout, Integer retryCount, Long maxBillingBytes, Integer metadataCacheTtl,
			Boolean metadataCacheEnabled, Boolean metadataLazyLoad, Boolean enableQueryCostEstimation,
			Boolean nativeComplexTypes, Integer metadataCacheMaxRows, BigDecimal queryPricePerTiB,
			Boolean metadataIncludeDescriptions, Boolean collapseShardedTables, Integer batchLoadThreshold,
			Boolean includeInformationSchema) {
		this(projectId, datasetId, datasetProjectId, authType, host, port, timeoutSeconds, maxResults, useLegacySql,
				location, labels, pageSize, useStorageApi, enableSessions, connectionTimeout, retryCount,
				maxBillingBytes, metadataCacheTtl, metadataCacheEnabled, metadataLazyLoad, enableQueryCostEstimation,
				nativeComplexTypes, metadataCacheMaxRows, queryPricePerTiB, metadataIncludeDescriptions,
				collapseShardedTables, batchLoadThreshold, includeInformationSchema, null, null);
	}

	/**
	 * Creates properties without any of the components added in 3.2.0, which then
	 * take their defaults.
	 *
	 * <p>
	 * This is the canonical shape as of 3.1.0, and the reason both this and the
	 * overload below it exist: the canonical constructor of a public record is part
	 * of its ABI, so growing the component list would break existing callers at
	 * source and binary level. The components added in 3.2.0 have not shipped
	 * individually, so one overload covers them all rather than there being a
	 * separate step per component.
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
				nativeComplexTypes, metadataCacheMaxRows, null, null, null, null, null, null, null);
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

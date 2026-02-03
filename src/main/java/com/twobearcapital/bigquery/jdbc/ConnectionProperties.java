/*
 * Copyright 2026 Two Bear Capital
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.twobearcapital.bigquery.jdbc;

import com.google.cloud.bigquery.DatasetId;
import java.util.Map;
import java.util.Objects;

/**
 * Connection properties for a BigQuery JDBC connection.
 *
 * @param projectId the Google Cloud project ID (required)
 * @param datasetId the default dataset name (optional)
 * @param datasetProjectId the project ID for the dataset if different from connection project
 *     (optional)
 * @param authType the authentication type (required)
 * @param timeoutSeconds query timeout in seconds (default: 300)
 * @param maxResults maximum number of rows to fetch (default: null = unlimited)
 * @param useLegacySql whether to use legacy SQL (default: false)
 * @param location BigQuery location (e.g., US, EU) (optional)
 * @param labels job labels as key-value pairs (optional)
 * @param jobCreationMode job creation mode (default: REQUIRED)
 * @param pageSize result page size for pagination (default: 10000)
 * @param useStorageApi Storage API mode: auto, true, false (default: auto)
 * @param enableSessions whether to use BigQuery sessions (default: false)
 * @param connectionTimeout connection establishment timeout in seconds (default: 30)
 * @param retryCount retry attempts for transient errors (default: 3)
 * @param maxBillingBytes query cost limit in bytes (optional)
 * @param metadataCacheTtl metadata cache TTL in seconds (default: 300 = 5 minutes)
 * @param metadataCacheEnabled whether to enable metadata caching (default: true)
 * @param metadataLazyLoad whether to use lazy loading for metadata (default: false)
 * @since 1.0.0
 */
public record ConnectionProperties(
    String projectId,
    String datasetId,
    String datasetProjectId,
    AuthType authType,
    Integer timeoutSeconds,
    Long maxResults,
    boolean useLegacySql,
    String location,
    Map<String, String> labels,
    JobCreationMode jobCreationMode,
    Integer pageSize,
    String useStorageApi,
    boolean enableSessions,
    Integer connectionTimeout,
    Integer retryCount,
    Long maxBillingBytes,
    Integer metadataCacheTtl,
    Boolean metadataCacheEnabled,
    Boolean metadataLazyLoad) {

  /** Default timeout in seconds. */
  public static final int DEFAULT_TIMEOUT_SECONDS = 300;

  /** Default page size. */
  public static final int DEFAULT_PAGE_SIZE = 10000;

  /** Default connection timeout in seconds. */
  public static final int DEFAULT_CONNECTION_TIMEOUT = 30;

  /** Default retry count. */
  public static final int DEFAULT_RETRY_COUNT = 3;

  /** Default metadata cache TTL in seconds (5 minutes). */
  public static final int DEFAULT_METADATA_CACHE_TTL = 300;

  public ConnectionProperties {
    Objects.requireNonNull(projectId, "projectId is required");
    if (projectId.isBlank()) {
      throw new IllegalArgumentException("projectId cannot be blank");
    }
    Objects.requireNonNull(authType, "authType is required");
    labels = labels == null ? Map.of() : Map.copyOf(labels);
    if (jobCreationMode == null) {
      jobCreationMode = JobCreationMode.REQUIRED;
    }
    if (timeoutSeconds == null) {
      timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
    }
    if (pageSize == null) {
      pageSize = DEFAULT_PAGE_SIZE;
    }
    if (useStorageApi == null) {
      useStorageApi = "auto";
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

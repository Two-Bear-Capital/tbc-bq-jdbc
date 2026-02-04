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
package com.twobearcapital.bigquery.jdbc.storage;

import com.google.api.gax.rpc.ServerStream;
import com.google.cloud.bigquery.*;
import com.google.cloud.bigquery.storage.v1.*;
import com.twobearcapital.bigquery.jdbc.BQResultSet;
import com.twobearcapital.bigquery.jdbc.BQStatement;
import java.io.IOException;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ResultSet implementation using BigQuery Storage Read API for improved
 * performance.
 *
 * <p>
 * The Storage Read API provides:
 *
 * <ul>
 * <li>Faster data access for large result sets
 * <li>Parallel stream reading
 * <li>Efficient Arrow or Avro serialization
 * <li>Reduced costs for large queries
 * </ul>
 *
 * <p>
 * This is automatically used when:
 *
 * <ul>
 * <li>useStorageApi=true or auto (and result exceeds threshold)
 * <li>Result set is from a query with results stored in a table
 * <li>BigQuery Storage API is available
 * </ul>
 *
 * @since 1.0.0
 */
public class StorageReadResultSet extends BQResultSet {

	private static final Logger logger = LoggerFactory.getLogger(StorageReadResultSet.class);

	/** Default threshold for using Storage API (10 MB). */
	public static final long DEFAULT_SIZE_THRESHOLD = 10 * 1024 * 1024; // 10 MB

	private final BigQueryReadClient readClient;
	private final TableId tableId;
	private ReadSession readSession;
	private ServerStream<ReadRowsResponse> currentStream;

	/**
	 * Creates a StorageReadResultSet for a table.
	 *
	 * @param statement
	 *            the parent statement
	 * @param tableId
	 *            reference to the table to read
	 * @throws SQLException
	 *             if initialization fails
	 */
	public StorageReadResultSet(BQStatement statement, TableId tableId) throws SQLException {
		super(statement, null); // Will override iteration logic

		this.tableId = tableId;

		BigQueryReadClient tempClient = null;
		try {
			// Create Storage Read API client
			tempClient = BigQueryReadClient.create();

			// Create read session
			this.readSession = createReadSession();

			logger.info("Created Storage API read session: {} with {} streams", readSession.getName(),
					readSession.getStreamsCount());

			// For now, use first stream (can be enhanced for parallel reading)
			if (readSession.getStreamsCount() > 0) {
				String streamName = readSession.getStreams(0).getName();
				ReadRowsRequest request = ReadRowsRequest.newBuilder().setReadStream(streamName).build();
				this.currentStream = tempClient.readRowsCallable().call(request);
			}

			// Only assign to field after successful initialization
			this.readClient = tempClient;

		} catch (IOException e) {
			// Clean up on failure to prevent resource leak
			if (tempClient != null) {
				try {
					tempClient.close();
				} catch (Exception closeEx) {
					// Log but don't mask original exception
					e.addSuppressed(closeEx);
				}
			}
			throw new SQLException("Failed to initialize Storage API read session", e);
		}
	}

	/**
	 * Creates a Storage API read session for the table.
	 *
	 * @return the read session
	 */
	private ReadSession createReadSession() {
		String projectId = tableId.getProject();
		String datasetId = tableId.getDataset();
		String table = tableId.getTable();

		String parent = String.format("projects/%s", projectId);
		String tablePath = String.format("projects/%s/datasets/%s/tables/%s", projectId, datasetId, table);

		ReadSession.Builder sessionBuilder = ReadSession.newBuilder().setTable(tablePath)
				.setDataFormat(DataFormat.ARROW) // Use Arrow format for better performance
				.setReadOptions(ReadSession.TableReadOptions.newBuilder()
						// Can add column filtering here
						.build());

		CreateReadSessionRequest request = CreateReadSessionRequest.newBuilder().setParent(parent)
				.setReadSession(sessionBuilder).setMaxStreamCount(1) // Start with 1 stream, can be enhanced for
																		// parallel
				.build();

		return readClient.createReadSession(request);
	}

	/**
	 * Determines if Storage API should be used for a result.
	 *
	 * @param tableResult
	 *            the query result
	 * @param useStorageApiSetting
	 *            the useStorageApi connection property
	 * @return true if Storage API should be used
	 */
	public static boolean shouldUseStorageApi(TableResult tableResult, String useStorageApiSetting) {
		if ("false".equalsIgnoreCase(useStorageApiSetting)) {
			return false;
		}

		if ("true".equalsIgnoreCase(useStorageApiSetting)) {
			return true;
		}

		// Auto mode: use for large results
		if ("auto".equalsIgnoreCase(useStorageApiSetting)) {
			long totalRows = tableResult.getTotalRows();
			// Estimate: assume 1KB per row on average
			long estimatedSize = totalRows * 1024;
			boolean shouldUse = estimatedSize > DEFAULT_SIZE_THRESHOLD;

			if (shouldUse) {
				logger.info("Auto-enabling Storage API for large result: {} rows (~{} MB)", totalRows,
						estimatedSize / (1024 * 1024));
			}

			return shouldUse;
		}

		return false;
	}

	@Override
	public void close() throws SQLException {
		try {
			if (currentStream != null) {
				currentStream.cancel();
			}
			if (readClient != null) {
				readClient.close();
			}
		} catch (Exception e) {
			throw new SQLException("Failed to close Storage API resources", e);
		} finally {
			super.close();
		}
	}

	// Note: Additional methods for Arrow deserialization would go here
	// For now, this is a framework that can be enhanced with full Arrow support
}

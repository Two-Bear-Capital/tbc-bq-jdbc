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
package vc.tbc.bq.jdbc.storage;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.rpc.ServerStream;
import com.google.auth.Credentials;
import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.FieldValueList;
import com.google.cloud.bigquery.Schema;
import com.google.cloud.bigquery.TableId;
import com.google.cloud.bigquery.TableResult;
import com.google.cloud.bigquery.storage.v1.*;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.VectorLoader;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.ipc.ReadChannel;
import org.apache.arrow.vector.ipc.message.ArrowRecordBatch;
import org.apache.arrow.vector.ipc.message.MessageSerializer;
import org.apache.arrow.vector.util.ByteArrayReadableSeekableByteChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vc.tbc.bq.jdbc.BQConnection;
import vc.tbc.bq.jdbc.BQResultSet;
import vc.tbc.bq.jdbc.BQStatement;
import vc.tbc.bq.jdbc.auth.CredentialsCache;
import vc.tbc.bq.jdbc.exception.BQSQLException;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Iterator;

/**
 * ResultSet backed by the BigQuery Storage Read API.
 *
 * <p>
 * The driver's standard path pages query results over
 * {@code jobs.getQueryResults} as JSON, paying an HTTP round trip per page and
 * parsing every value out of text. This class instead opens a read session on
 * the completed job's destination table and streams Arrow record batches over
 * gRPC. Measured against the REST path on large results, that was 13-20x faster
 * end to end (#152).
 *
 * <p>
 * <b>Rows go through {@link FieldValueList}, not straight into the getters.</b>
 * This class overrides exactly one thing — where rows come from — by
 * implementing {@link BQResultSet#fetchNextRow()}. Every getter, every type
 * coercion and every error path is inherited unchanged from
 * {@link BQResultSet}, so the two paths cannot disagree about what a value
 * means. See {@link ArrowRowConverter} for why that trade is worth its cost.
 *
 * <p>
 * <b>Single stream, deliberately.</b> {@code CreateReadSession} will partition
 * a table across many streams, but a {@code java.sql.ResultSet} is one
 * forward-only cursor: consuming several streams concurrently would interleave
 * rows and silently reorder an {@code ORDER BY} result. The measured throughput
 * on one stream is already several hundred thousand rows/s, so the ordering
 * risk buys nothing. Google's own client library makes the same choice.
 *
 * <p>
 * <b>This path is never mandatory.</b> Construction can fail for reasons
 * outside the caller's control — Arrow needing a JVM flag, the Storage API not
 * being enabled, credentials lacking {@code bigquery.readsessions.create}.
 * Callers are expected to catch {@link SQLException} and fall back to
 * {@link BQResultSet}; {@code AbstractBQStatement.createResultSet} does exactly
 * that.
 *
 * @since 1.0.0
 */
public class StorageReadResultSet extends BQResultSet {

	private static final Logger logger = LoggerFactory.getLogger(StorageReadResultSet.class);

	/** Default threshold for using Storage API (10 MB). */
	public static final long DEFAULT_SIZE_THRESHOLD = 10 * 1024 * 1024; // 10 MB

	/**
	 * Rough per-row size used by auto mode, which has no byte count to work from.
	 */
	private static final long ESTIMATED_BYTES_PER_ROW = 1024;

	private final BigQueryReadClient readClient;
	private final BufferAllocator allocator;
	private final ArrowRowConverter converter;
	private final ServerStream<ReadRowsResponse> stream;
	private final Iterator<ReadRowsResponse> responses;
	private final VectorSchemaRoot root;
	private final VectorLoader loader;

	private int rowInBatch;
	private int rowsInBatch;

	/**
	 * Opens a read session over a completed query's destination table.
	 *
	 * @param statement
	 *            the statement that produced this result set
	 * @param tableResult
	 *            the query result, used for schema, metadata and {@code maxRows}
	 * @param destination
	 *            the job's destination table, typically an anonymous result table
	 * @throws SQLException
	 *             if the read session cannot be opened, in which case the caller
	 *             should fall back to the standard ResultSet
	 */
	@SuppressWarnings("PMD.CloseResource") // resources are fields closed in close(); cleaned up here only on failure
	public StorageReadResultSet(BQStatement statement, TableResult tableResult, TableId destination)
			throws SQLException {
		super(statement, tableResult);

		if (destination == null) {
			throw new BQSQLException("Storage Read API needs a destination table, but the job did not report one");
		}
		Schema schema = tableResult.getSchema();
		if (!ArrowRowConverter.isSupported(schema)) {
			throw new BQSQLException("Result schema contains types the Storage Read API path does not encode");
		}

		BigQueryReadClient clientBeingBuilt = null;
		BufferAllocator allocatorBeingBuilt = null;
		VectorSchemaRoot rootBeingBuilt = null;
		try {
			this.converter = new ArrowRowConverter(schema);
			clientBeingBuilt = openClient(statement);
			ReadSession session = createReadSession(clientBeingBuilt, statement, destination, schema);

			if (session.getStreamsCount() == 0) {
				// An empty result yields no streams. Nothing to read, but the ResultSet must
				// still behave: leave the batch empty and fetchNextRow() reports end of rows.
				logger.debug("Storage read session {} returned no streams (empty result)", session.getName());
			}

			allocatorBeingBuilt = new RootAllocator(Long.MAX_VALUE);
			org.apache.arrow.vector.types.pojo.Schema arrowSchema = MessageSerializer
					.deserializeSchema(new ReadChannel(new ByteArrayReadableSeekableByteChannel(
							session.getArrowSchema().getSerializedSchema().toByteArray())));
			rootBeingBuilt = VectorSchemaRoot.create(arrowSchema, allocatorBeingBuilt);

			this.allocator = allocatorBeingBuilt;
			this.root = rootBeingBuilt;
			this.loader = new VectorLoader(rootBeingBuilt);
			this.readClient = clientBeingBuilt;
			this.stream = session.getStreamsCount() == 0
					? null
					: clientBeingBuilt.readRowsCallable()
							.call(ReadRowsRequest.newBuilder().setReadStream(session.getStreams(0).getName()).build());
			this.responses = stream == null ? java.util.Collections.emptyIterator() : stream.iterator();

			logger.debug("Storage read session {} open on {}.{}", session.getName(), destination.getDataset(),
					destination.getTable());
		} catch (SQLException e) {
			closeQuietly(rootBeingBuilt, allocatorBeingBuilt, clientBeingBuilt, e);
			throw e;
		} catch (IOException | RuntimeException e) {
			closeQuietly(rootBeingBuilt, allocatorBeingBuilt, clientBeingBuilt, e);
			throw new BQSQLException("Failed to open a BigQuery Storage read session", e);
		}
	}

	private static BigQueryReadClient openClient(BQStatement statement) throws SQLException {
		try {
			BQConnection connection = (BQConnection) statement.getConnection();
			Credentials credentials = CredentialsCache.forAuthType(connection.getProperties().authType());
			return BigQueryReadClient.create(BigQueryReadSettings.newBuilder()
					.setCredentialsProvider(FixedCredentialsProvider.create(credentials)).build());
		} catch (SQLException e) {
			throw e;
		} catch (Exception e) {
			throw new BQSQLException("Failed to create a BigQuery Storage read client", e);
		}
	}

	private static ReadSession createReadSession(BigQueryReadClient client, BQStatement statement, TableId destination,
			Schema schema) throws SQLException {
		BQConnection connection = (BQConnection) statement.getConnection();
		String billingProject = connection.getProperties().projectId();
		String tablePath = String.format("projects/%s/datasets/%s/tables/%s", destination.getProject(),
				destination.getDataset(), destination.getTable());

		ReadSession.TableReadOptions.Builder options = ReadSession.TableReadOptions.newBuilder();
		// Ask for the columns in schema order. Without this the session returns the
		// table's own column order, which for a destination table matches the query,
		// but being explicit keeps column identity tied to the schema we convert with.
		for (Field field : schema.getFields()) {
			options.addSelectedFields(field.getName());
		}

		return client.createReadSession(CreateReadSessionRequest
				.newBuilder().setParent("projects/" + billingProject).setReadSession(ReadSession.newBuilder()
						.setTable(tablePath).setDataFormat(DataFormat.ARROW).setReadOptions(options.build()))
				.setMaxStreamCount(1).build());
	}

	@Override
	protected FieldValueList fetchNextRow() throws SQLException {
		while (true) {
			if (rowInBatch < rowsInBatch) {
				return converter.convert(root, rowInBatch++);
			}
			if (!loadNextBatch()) {
				return null;
			}
		}
	}

	/**
	 * Pulls responses until one carries a record batch.
	 *
	 * @return false once the stream is exhausted
	 */
	private boolean loadNextBatch() throws SQLException {
		while (responses.hasNext()) {
			ReadRowsResponse response = responses.next();
			if (!response.hasArrowRecordBatch()) {
				continue;
			}
			try (ArrowRecordBatch batch = MessageSerializer
					.deserializeRecordBatch(
							new ReadChannel(new ByteArrayReadableSeekableByteChannel(
									response.getArrowRecordBatch().getSerializedRecordBatch().toByteArray())),
							allocator)) {
				// load() retains the buffers it needs, so closing the batch here does not
				// invalidate the vectors we are about to read.
				loader.load(batch);
			} catch (IOException | RuntimeException e) {
				throw new BQSQLException("Failed to decode an Arrow record batch from the Storage Read API", e);
			}
			rowsInBatch = root.getRowCount();
			rowInBatch = 0;
			if (rowsInBatch > 0) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Determines if Storage API should be used for a result.
	 *
	 * <p>
	 * {@code false} never, {@code true} always, {@code auto} only for results large
	 * enough that the read session's fixed setup cost is worth paying. Small
	 * results are genuinely slower over the Storage API, so {@code true} is a blunt
	 * instrument and {@code auto} is the setting to prefer.
	 *
	 * @param tableResult
	 *            the query result
	 * @param useStorageApiSetting
	 *            the useStorageApi connection property
	 * @return true if Storage API should be used
	 */
	public static boolean shouldUseStorageApi(TableResult tableResult, String useStorageApiSetting) {
		if ("true".equalsIgnoreCase(useStorageApiSetting)) {
			return true;
		}
		if (!"auto".equalsIgnoreCase(useStorageApiSetting)) {
			return false;
		}
		if (tableResult == null) {
			return false;
		}
		long estimatedSize = tableResult.getTotalRows() * ESTIMATED_BYTES_PER_ROW;
		return estimatedSize > DEFAULT_SIZE_THRESHOLD;
	}

	@Override
	@SuppressWarnings("PMD.UseTryWithResources") // multi-resource close with suppressed-exception chaining requires
													// manual try/catch
	protected void doClose() throws SQLException {
		SQLException thrown = null;
		try {
			if (stream != null) {
				stream.cancel();
			}
		} catch (RuntimeException e) {
			thrown = new BQSQLException("Failed to cancel the Storage Read API stream", e);
		}
		thrown = closeAndCollect(root, "Arrow vectors", thrown);
		thrown = closeAndCollect(allocator, "Arrow allocator", thrown);
		thrown = closeAndCollect(readClient, "Storage Read API client", thrown);
		super.doClose();
		if (thrown != null) {
			throw thrown;
		}
	}

	private static SQLException closeAndCollect(AutoCloseable resource, String what, SQLException pending) {
		if (resource == null) {
			return pending;
		}
		try {
			resource.close();
			return pending;
		} catch (Exception e) {
			SQLException failure = new BQSQLException("Failed to close the " + what, e);
			if (pending == null) {
				return failure;
			}
			pending.addSuppressed(failure);
			return pending;
		}
	}

	/** Best-effort cleanup when construction fails partway through. */
	private static void closeQuietly(VectorSchemaRoot root, BufferAllocator allocator, BigQueryReadClient client,
			Exception primary) {
		for (AutoCloseable resource : new AutoCloseable[]{root, allocator, client}) {
			if (resource == null) {
				continue;
			}
			try {
				resource.close();
			} catch (Exception e) {
				primary.addSuppressed(e);
			}
		}
	}
}

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
package vc.tbc.bq.jdbc.benchmark;

import com.google.api.gax.rpc.ServerStream;
import com.google.cloud.bigquery.*;
import com.google.cloud.bigquery.storage.v1.*;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.VectorLoader;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.ipc.ReadChannel;
import org.apache.arrow.vector.ipc.message.ArrowRecordBatch;
import org.apache.arrow.vector.ipc.message.MessageSerializer;
import org.apache.arrow.vector.util.ByteArrayReadableSeekableByteChannel;
import vc.tbc.bq.jdbc.config.ConnectionProperties;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * SPIKE (#152): measures the BigQuery Storage Read API against the driver's
 * current REST/JSON result path, to decide whether to implement or delete
 * {@code StorageReadResultSet}.
 *
 * <p>
 * This is throwaway measurement code, not a driver feature. It reads the same
 * query result two ways and reports rows/s for each:
 *
 * <ul>
 * <li><b>REST</b> — the driver end to end, i.e.
 * {@code TableResult.iterateAll()} underneath, which is paginated JSON over
 * HTTPS
 * <li><b>Storage</b> — the completed job's anonymous destination table read via
 * a single-stream Arrow read session, decoded here the way
 * {@code ConnectionImpl.ArrowRowReader} does it
 * </ul>
 *
 * <p>
 * Both paths execute the query first, so both pay the same job cost; the query
 * is warmed once up front so every timed run hits BigQuery's result cache and
 * the difference measured is fetch, not planning. Runs alternate REST/Storage
 * and the median is reported, so a slow first connection does not decide the
 * answer.
 *
 * <p>
 * The fixture is generated with {@code GENERATE_ARRAY}, so it scans no bytes
 * and costs nothing but the read itself. Reads of anonymous result tables are
 * free and do not count against the Storage Read API free tier.
 *
 * <p>
 * Run with:
 *
 * <pre>
 * export BQ_TEST_PROJECT=my-project
 * ./mvnw test-compile exec:exec -Pstorage-spike
 * </pre>
 */
public final class StorageReadApiSpike {

	private static final String PROJECT = System.getenv().getOrDefault("BQ_TEST_PROJECT", "");
	private static final String DATASET = System.getenv().getOrDefault("BQ_TEST_DATASET",
			"tbc_bq_jdbc_integration_tests");

	/** Rows in the generated fixture; override with --rows. */
	private static int rows = 1_000_000;

	/** Timed repetitions per path; override with --runs. */
	private static int runs = 3;

	/**
	 * Query-phase seconds from the most recent Storage run, for fetch-only maths.
	 */
	private static double lastQuerySeconds;

	/** Run the alternative-explanation controls; enable with --controls. */
	private static boolean controls;

	/** Use the string-heavy fixture shape; enable with --wide. */
	private static boolean wide;

	private StorageReadApiSpike() {
	}

	public static void main(String[] args) throws Exception {
		parseArgs(args);
		if (PROJECT.isBlank()) {
			throw new IllegalStateException("BQ_TEST_PROJECT must be set");
		}

		final String sql = fixtureSql(rows);
		System.out.printf("Fixture: %,d rows, %d timed runs per path, project %s%n%n", rows, runs, PROJECT);

		final BigQuery bigquery = BigQueryOptions.newBuilder().setProjectId(PROJECT).build().getService();

		// Warm the result cache so timed runs measure fetch, not planning.
		System.out.println("Warming result cache (this run is not timed)...");
		final long warmStart = System.nanoTime();
		final TableId destination = runQuery(bigquery, sql);
		System.out.printf("  query completed in %.1f s, destination %s.%s%n", elapsedSeconds(warmStart),
				destination.getDataset(), destination.getTable());

		final Table destTable = bigquery.getTable(destination);
		final long bytes = destTable.getNumBytes() == null ? 0L : destTable.getNumBytes();
		System.out.printf("  result table is %.1f MB (%,d rows)%n%n", bytes / 1024.0 / 1024.0, destTable.getNumRows());

		final List<Double> restSeconds = new ArrayList<>();
		final List<Double> storageSeconds = new ArrayList<>();
		final List<Double> querySeconds = new ArrayList<>();

		for (int i = 1; i <= runs; i++) {
			final double rest = timeRestPath(sql);
			restSeconds.add(rest);
			System.out.printf("run %d  REST     %6.2f s  %,10.0f rows/s%n", i, rest, rows / rest);

			final double storage = timeStoragePath(bigquery, sql);
			storageSeconds.add(storage);
			querySeconds.add(lastQuerySeconds);
			System.out.printf("run %d  Storage  %6.2f s  %,10.0f rows/s  (query phase %.2f s)%n", i, storage,
					rows / storage, lastQuerySeconds);
		}

		final double restMedian = median(restSeconds);
		final double storageMedian = median(storageSeconds);

		System.out.printf("%n=== Result (median of %d) ===%n", runs);
		System.out.printf("REST     %6.2f s  %,10.0f rows/s  %6.1f MB/s%n", restMedian, rows / restMedian,
				bytes / 1024.0 / 1024.0 / restMedian);
		System.out.printf("Storage  %6.2f s  %,10.0f rows/s  %6.1f MB/s%n", storageMedian, rows / storageMedian,
				bytes / 1024.0 / 1024.0 / storageMedian);
		System.out.printf("Speedup  %.2fx (end to end, query included)%n", restMedian / storageMedian);

		// Subtract the shared query cost to isolate what the wire format actually buys.
		final double queryMedian = median(querySeconds);
		final double restFetch = restMedian - queryMedian;
		final double storageFetch = storageMedian - queryMedian;
		if (restFetch > 0 && storageFetch > 0) {
			System.out.printf("%n=== Fetch only (query phase of %.2f s subtracted from both) ===%n", queryMedian);
			System.out.printf("REST     %6.2f s  %,10.0f rows/s%n", restFetch, rows / restFetch);
			System.out.printf("Storage  %6.2f s  %,10.0f rows/s%n", storageFetch, rows / storageFetch);
			System.out.printf("Speedup  %.2fx (fetch only)%n", restFetch / storageFetch);
		}

		if (controls) {
			System.out.printf("%n=== Controls: is the gap really Arrow, or just round trips / JDBC? ===%n");

			final double big = timeRestPath(sql, 100_000);
			System.out.printf("JDBC, pageSize=100000    %6.2f s  %,10.0f rows/s%n", big, rows / big);

			final double raw = timeRawTableResult(bigquery, sql, ConnectionProperties.DEFAULT_PAGE_SIZE);
			System.out.printf("Raw TableResult, no JDBC %6.2f s  %,10.0f rows/s%n", raw, rows / raw);

			System.out.printf("%nFor reference, JDBC at the default pageSize was %.2f s (%,.0f rows/s).%n", restMedian,
					rows / restMedian);
		}
	}

	/**
	 * Generates the fixture without scanning any bytes. Two cross-joined arrays
	 * rather than one large one, so no single GENERATE_ARRAY has to be huge.
	 */
	private static String fixtureSql(int rowCount) {
		final int side = (int) Math.round(Math.sqrt(rowCount));
		// The cross join yields exactly side^2 rows, which is rarely the requested
		// count. Snap the target to what will actually be generated so the row-count
		// assertion checks decoding rather than arithmetic.
		rows = side * side;
		// JSON's overhead is worst on numeric columns, where it pays text encoding plus
		// a repeated field name for every value. The wide shape adds string payload,
		// which costs both formats similarly, so it is the harder test for Arrow.
		final String payload = wide ? """
				  RPAD(CONCAT('payload_', CAST(MOD(id, 997) AS STRING)), 48, 'x') AS pad1,
				  RPAD(CONCAT('payload_', CAST(MOD(id, 991) AS STRING)), 48, 'y') AS pad2,
				  RPAD(CONCAT('payload_', CAST(MOD(id, 983) AS STRING)), 48, 'z') AS pad3,
				  RPAD(CONCAT('payload_', CAST(MOD(id, 977) AS STRING)), 48, 'w') AS pad4,
				""" : "";
		return String.format("""
				SELECT
				  id,
				  CONCAT('user_', CAST(MOD(id, 100000) AS STRING)) AS name,
				  id * 1.5 AS score,
				  MOD(id, 7) = 0 AS flag,
				%s  TIMESTAMP_ADD(TIMESTAMP '2020-01-01 00:00:00', INTERVAL MOD(id, 86400) SECOND) AS ts
				FROM (
				  SELECT (a - 1) * %d + b AS id
				  FROM UNNEST(GENERATE_ARRAY(1, %d)) AS a,
				       UNNEST(GENERATE_ARRAY(1, %d)) AS b
				)
				""", payload, side, side, side);
	}

	/** Runs the query and returns the anonymous destination table. */
	private static TableId runQuery(BigQuery bigquery, String sql) throws InterruptedException {
		final QueryJobConfiguration config = QueryJobConfiguration.newBuilder(sql).setUseQueryCache(true).build();
		Job job = bigquery.create(JobInfo.of(config));
		job = job.waitFor();
		if (job.getStatus().getError() != null) {
			throw new IllegalStateException("query failed: " + job.getStatus().getError());
		}
		final QueryJobConfiguration completed = job.getConfiguration();
		final TableId destination = completed.getDestinationTable();
		if (destination == null) {
			throw new IllegalStateException("no destination table on completed job — cannot read via Storage API");
		}
		return destination;
	}

	/**
	 * Control: raw {@code TableResult.iterateAll()} with no JDBC layer over it.
	 * Separates the cost of the wire format from the cost of the driver's own
	 * ResultSet and TypeMapper, so the headline speedup cannot be quietly credited
	 * to Arrow when it belongs to skipping BQResultSet.
	 */
	private static double timeRawTableResult(BigQuery bigquery, String sql, int pageSize) throws Exception {
		final long start = System.nanoTime();
		final QueryJobConfiguration config = QueryJobConfiguration.newBuilder(sql).setUseQueryCache(true).build();
		Job job = bigquery.create(JobInfo.of(config));
		job = job.waitFor();

		long ids = 0;
		long nameChars = 0;
		double scores = 0;
		long count = 0;
		final TableResult result = job.getQueryResults(BigQuery.QueryResultsOption.pageSize(pageSize));
		for (FieldValueList row : result.iterateAll()) {
			ids += row.get("id").getLongValue();
			nameChars += row.get("name").getStringValue().length();
			scores += row.get("score").getDoubleValue();
			count++;
		}
		final double seconds = elapsedSeconds(start);
		checkConsumed(count, ids, nameChars, scores);
		return seconds;
	}

	/** The driver as it ships today: JDBC over paginated JSON. */
	private static double timeRestPath(String sql) throws Exception {
		return timeRestPath(sql, 0);
	}

	/** JDBC over paginated JSON, optionally with a non-default page size. */
	private static double timeRestPath(String sql, int pageSize) throws Exception {
		final String url = pageSize > 0
				? String.format("jdbc:bigquery:%s/%s?authType=ADC&pageSize=%d", PROJECT, DATASET, pageSize)
				: String.format("jdbc:bigquery:%s/%s?authType=ADC", PROJECT, DATASET);
		final long start = System.nanoTime();
		long ids = 0;
		long nameChars = 0;
		double scores = 0;
		long count = 0;
		try (Connection connection = DriverManager.getConnection(url);
				Statement statement = connection.createStatement();
				ResultSet rs = statement.executeQuery(sql)) {
			while (rs.next()) {
				ids += rs.getLong("id");
				nameChars += rs.getString("name").length();
				scores += rs.getDouble("score");
				count++;
			}
		}
		final double seconds = elapsedSeconds(start);
		checkConsumed(count, ids, nameChars, scores);
		return seconds;
	}

	/** Single-stream Arrow read session over the job's destination table. */
	private static double timeStoragePath(BigQuery bigquery, String sql) throws Exception {
		final long start = System.nanoTime();
		final TableId destination = runQuery(bigquery, sql);
		// Both paths pay this; recording it lets the report show fetch-only figures
		// rather than letting a fixed ~1 s job cost flatter the slower path.
		lastQuerySeconds = elapsedSeconds(start);

		long ids = 0;
		long nameChars = 0;
		double scores = 0;
		long count = 0;

		try (BigQueryReadClient client = BigQueryReadClient.create();
				BufferAllocator allocator = new RootAllocator(Long.MAX_VALUE)) {

			final String tablePath = String.format("projects/%s/datasets/%s/tables/%s", destination.getProject(),
					destination.getDataset(), destination.getTable());
			final ReadSession session = client.createReadSession(CreateReadSessionRequest.newBuilder()
					.setParent("projects/" + PROJECT)
					.setReadSession(ReadSession.newBuilder().setTable(tablePath).setDataFormat(DataFormat.ARROW)
							.setReadOptions(ReadSession.TableReadOptions.newBuilder().addSelectedFields("id")
									.addSelectedFields("name").addSelectedFields("score").addSelectedFields("flag")
									.addSelectedFields("ts").build()))
					.setMaxStreamCount(1).build());

			if (session.getStreamsCount() == 0) {
				throw new IllegalStateException("read session returned no streams");
			}

			final org.apache.arrow.vector.types.pojo.Schema arrowSchema = MessageSerializer
					.deserializeSchema(new ReadChannel(new ByteArrayReadableSeekableByteChannel(
							session.getArrowSchema().getSerializedSchema().toByteArray())));

			try (VectorSchemaRoot root = VectorSchemaRoot.create(arrowSchema, allocator)) {
				final VectorLoader loader = new VectorLoader(root);
				final ServerStream<ReadRowsResponse> stream = client.readRowsCallable()
						.call(ReadRowsRequest.newBuilder().setReadStream(session.getStreams(0).getName()).build());

				for (ReadRowsResponse response : stream) {
					if (!response.hasArrowRecordBatch()) {
						continue;
					}
					try (ArrowRecordBatch batch = MessageSerializer.deserializeRecordBatch(
							new ReadChannel(new ByteArrayReadableSeekableByteChannel(
									response.getArrowRecordBatch().getSerializedRecordBatch().toByteArray())),
							allocator)) {
						loader.load(batch);
						final int batchRows = root.getRowCount();
						for (int row = 0; row < batchRows; row++) {
							ids += (Long) root.getVector("id").getObject(row);
							nameChars += root.getVector("name").getObject(row).toString().length();
							scores += (Double) root.getVector("score").getObject(row);
							count++;
						}
					}
				}
			}
		}

		final double seconds = elapsedSeconds(start);
		checkConsumed(count, ids, nameChars, scores);
		return seconds;
	}

	/**
	 * Guards against a path "winning" by not actually decoding the rows, and keeps
	 * the accumulators from being optimised away.
	 */
	private static void checkConsumed(long count, long ids, long nameChars, double scores) {
		if (count != rows) {
			throw new IllegalStateException("expected " + rows + " rows, read " + count);
		}
		if (ids == 0 || nameChars == 0 || scores == 0) {
			throw new IllegalStateException("values were not decoded");
		}
	}

	private static double elapsedSeconds(long startNanos) {
		return (System.nanoTime() - startNanos) / 1_000_000_000.0;
	}

	private static double median(List<Double> values) {
		final List<Double> sorted = new ArrayList<>(values);
		sorted.sort(Double::compareTo);
		final int mid = sorted.size() / 2;
		return sorted.size() % 2 == 1 ? sorted.get(mid) : (sorted.get(mid - 1) + sorted.get(mid)) / 2;
	}

	private static void parseArgs(String[] args) {
		for (int i = 0; i < args.length; i++) {
			switch (args[i]) {
				case "--rows" -> rows = Integer.parseInt(args[++i]);
				case "--runs" -> runs = Integer.parseInt(args[++i]);
				case "--controls" -> controls = true;
				case "--wide" -> wide = true;
				default -> {
					// ignore unrecognised tokens
				}
			}
		}
	}
}

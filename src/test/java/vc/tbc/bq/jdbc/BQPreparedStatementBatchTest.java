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
package vc.tbc.bq.jdbc;

import com.google.cloud.bigquery.BigQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vc.tbc.bq.jdbc.config.ConnectionProperties;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link BQPreparedStatement} batch state management (addBatch,
 * clearBatch, empty executeBatch). Batch execution against BigQuery is covered
 * by the BatchExecutionTest integration test.
 *
 * @since 1.0.94
 */
class BQPreparedStatementBatchTest {

	private BQPreparedStatement statement;

	@BeforeEach
	void setUp() {
		BQConnection mockConnection = mock(BQConnection.class);
		BigQuery mockBigQuery = mock(BigQuery.class);
		ConnectionProperties mockProperties = mock(ConnectionProperties.class);

		when(mockConnection.getBigQuery()).thenReturn(mockBigQuery);
		when(mockConnection.getProperties()).thenReturn(mockProperties);

		statement = new BQPreparedStatement(mockConnection, "INSERT INTO t (a, b) VALUES (?, ?)");
	}

	@Test
	void testAddBatchSnapshotsAndClearsParameters() throws SQLException {
		// Given: A parameter set
		statement.setInt(1, 1);
		statement.setString(2, "one");

		// When: Adding to batch
		statement.addBatch();

		// Then: Working parameters are cleared for the next row
		assertEquals(0, statement.getParameterMetaData().getParameterCount());
	}

	@Test
	void testExecuteEmptyBatchReturnsEmptyArray() throws SQLException {
		// When: Executing with no batched parameter sets
		int[] counts = statement.executeBatch();

		// Then: Empty update count array, no BigQuery job
		assertEquals(0, counts.length);
	}

	@Test
	void testClearBatchDiscardsParameterSets() throws SQLException {
		// Given: Batched parameter sets
		statement.setInt(1, 1);
		statement.setString(2, "one");
		statement.addBatch();
		statement.setInt(1, 2);
		statement.setString(2, "two");
		statement.addBatch();

		// When: Clearing the batch
		statement.clearBatch();

		// Then: Nothing left to execute
		assertEquals(0, statement.executeBatch().length);
	}

	@Test
	void testAddBatchWithSqlThrowsOnPreparedStatement() {
		// Then: addBatch(String) is forbidden on PreparedStatement per JDBC spec
		assertThrows(SQLException.class, () -> statement.addBatch("INSERT INTO t VALUES (1, 'x')"));
	}

	@Test
	void testBatchMethodsThrowWhenClosed() throws SQLException {
		statement.close();

		assertThrows(SQLException.class, () -> statement.addBatch());
		assertThrows(SQLException.class, () -> statement.clearBatch());
		assertThrows(SQLException.class, () -> statement.executeBatch());
	}
}

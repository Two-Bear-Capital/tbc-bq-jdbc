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
import com.google.cloud.bigquery.Job;
import com.google.cloud.bigquery.JobInfo;
import com.google.cloud.bigquery.JobStatus;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.TableResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import vc.tbc.bq.jdbc.config.ConnectionProperties;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies that {@code maxBillingBytes} reaches the submitted BigQuery job.
 *
 * <p>
 * The property was parsed, defaulted and advertised through
 * {@code Driver.getPropertyInfo()} for a long time without ever being applied,
 * so it silently capped nothing while the documentation described it as a spend
 * guardrail. Nothing asserted the wiring, which is why it went unnoticed —
 * these tests capture the {@link JobInfo} actually handed to BigQuery.
 *
 * @since 2.4.3
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MaxBillingBytesTest {

	@Mock
	private BQConnection connection;

	@Mock
	private BigQuery bigquery;

	@Mock
	private Job job;

	@Mock
	private JobStatus jobStatus;

	@BeforeEach
	void setUp() throws Exception {
		when(connection.getBigQuery()).thenReturn(bigquery);
		when(connection.getSessionManager()).thenReturn(null);
		when(bigquery.create(any(JobInfo.class))).thenReturn(job);
		when(job.waitFor()).thenReturn(job);
		when(job.getStatus()).thenReturn(jobStatus);
		when(jobStatus.getError()).thenReturn(null);
		when(job.getQueryResults()).thenReturn(mock(TableResult.class));
	}

	/** Properties with only the fields these tests exercise stubbed. */
	private ConnectionProperties propertiesWith(Long maxBillingBytes) {
		ConnectionProperties properties = mock(ConnectionProperties.class);
		when(properties.maxBillingBytes()).thenReturn(maxBillingBytes);
		when(properties.labels()).thenReturn(Map.of());
		when(properties.getDatasetId()).thenReturn(null);
		when(properties.useLegacySql()).thenReturn(false);
		when(connection.getProperties()).thenReturn(properties);
		return properties;
	}

	/** Runs a query and returns the configuration BigQuery was actually given. */
	private QueryJobConfiguration captureSubmittedConfig() throws Exception {
		try (BQStatement statement = new BQStatement(connection)) {
			statement.executeQuery("SELECT 1");
		}
		ArgumentCaptor<JobInfo> captor = ArgumentCaptor.forClass(JobInfo.class);
		verify(bigquery).create(captor.capture());
		return captor.getValue().getConfiguration();
	}

	@Test
	void appliesMaximumBytesBilledWhenSet() throws Exception {
		// Given: a connection with a 10 GB billing ceiling
		propertiesWith(10_737_418_240L);

		// When: a query runs
		QueryJobConfiguration config = captureSubmittedConfig();

		// Then: the ceiling reaches the job, so BigQuery can enforce it
		assertEquals(10_737_418_240L, config.getMaximumBytesBilled());
	}

	@Test
	void leavesMaximumBytesBilledUnsetWhenPropertyAbsent() throws Exception {
		// Given: no billing ceiling configured (the default)
		propertiesWith(null);

		// When: a query runs
		QueryJobConfiguration config = captureSubmittedConfig();

		// Then: nothing is imposed, so project-level defaults apply
		assertNull(config.getMaximumBytesBilled());
	}

	@Test
	void appliesMaximumBytesBilledToDmlAsWell() throws Exception {
		// Given: a connection with a billing ceiling
		propertiesWith(5_000_000L);
		when(job.getStatistics()).thenReturn(null);

		// When: a DML statement runs rather than a query
		try (BQStatement statement = new BQStatement(connection)) {
			statement.executeUpdate("DELETE FROM t WHERE id = 1");
		}

		// Then: the ceiling applies there too — DML can scan as much as a SELECT
		ArgumentCaptor<JobInfo> captor = ArgumentCaptor.forClass(JobInfo.class);
		verify(bigquery).create(captor.capture());
		QueryJobConfiguration config = captor.getValue().getConfiguration();
		assertEquals(5_000_000L, config.getMaximumBytesBilled());
	}
}

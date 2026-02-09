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

import com.google.cloud.bigquery.DatasetId;
import org.junit.jupiter.api.Test;
import vc.tbc.bq.jdbc.auth.ApplicationDefaultAuth;
import vc.tbc.bq.jdbc.auth.AuthType;
import vc.tbc.bq.jdbc.auth.ServiceAccountAuth;
import vc.tbc.bq.jdbc.config.ConnectionProperties;
import vc.tbc.bq.jdbc.config.JobCreationMode;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ConnectionProperties record.
 *
 * @since 1.0.0
 */
class ConnectionPropertiesTest {

	@Test
	void testMinimalProperties() {
		// Given: Minimal required properties
		ConnectionProperties props = new ConnectionProperties("my-project", // projectId
				null, // datasetId
				null, // datasetProjectId
				new ApplicationDefaultAuth(), // authType
				null, // host
				null, // port
				null, // timeoutSeconds
				null, // maxResults
				false, // useLegacySql
				null, // location
				null, // labels
				null, // jobCreationMode
				null, // pageSize
				null, // useStorageApi
				false, // enableSessions
				null, // connectionTimeout
				null, // retryCount
				null, // maxBillingBytes
				null, // metadataCacheTtl
				null, // metadataCacheEnabled
				null, // metadataLazyLoad
				null, // useDestinationTables
				null // enableQueryCostEstimation
		);

		// Then: Required fields should be set, defaults applied
		assertEquals("my-project", props.projectId());
		assertNull(props.datasetId());
		assertInstanceOf(ApplicationDefaultAuth.class, props.authType());
		assertEquals(ConnectionProperties.DEFAULT_TIMEOUT_SECONDS, props.timeoutSeconds());
		assertEquals(ConnectionProperties.DEFAULT_PAGE_SIZE, props.pageSize());
		assertEquals(ConnectionProperties.DEFAULT_CONNECTION_TIMEOUT, props.connectionTimeout());
		assertEquals(ConnectionProperties.DEFAULT_RETRY_COUNT, props.retryCount());
		assertFalse(props.useLegacySql());
		assertFalse(props.enableSessions());
		assertEquals("auto", props.useStorageApi());
		assertEquals(JobCreationMode.REQUIRED, props.jobCreationMode());
		assertTrue(props.labels().isEmpty());
	}

	@Test
	void testFullProperties() {
		// Given: All properties set
		Map<String, String> labels = Map.of("env", "prod", "team", "data");
		ConnectionProperties props = new ConnectionProperties("my-project", "my_dataset", "dataset-project",
				new ServiceAccountAuth("/path/to/key.json"), null, null, 120, 1000L, true, "EU", labels,
				JobCreationMode.OPTIONAL, 5000, "true", true, 60, 5, 1000000L, null, null, null, null, null);

		// Then: All fields should match
		assertEquals("my-project", props.projectId());
		assertEquals("my_dataset", props.datasetId());
		assertEquals("dataset-project", props.datasetProjectId());
		assertInstanceOf(ServiceAccountAuth.class, props.authType());
		assertEquals(120, props.timeoutSeconds());
		assertEquals(1000L, props.maxResults());
		assertTrue(props.useLegacySql());
		assertEquals("EU", props.location());
		assertEquals(2, props.labels().size());
		assertEquals("prod", props.labels().get("env"));
		assertEquals("data", props.labels().get("team"));
		assertEquals(JobCreationMode.OPTIONAL, props.jobCreationMode());
		assertEquals(5000, props.pageSize());
		assertEquals("true", props.useStorageApi());
		assertTrue(props.enableSessions());
		assertEquals(60, props.connectionTimeout());
		assertEquals(5, props.retryCount());
		assertEquals(1000000L, props.maxBillingBytes());
	}

	@Test
	void testNullProjectIdThrowsException() {
		// Then: Null projectId should throw NPE
		assertThrows(NullPointerException.class, () -> new ConnectionProperties(null, // projectId
				null, null, new ApplicationDefaultAuth(), null, null, null, null, false, null, null, null, null, null,
				false, null, null, null, null, null, null, null, null));
	}

	@Test
	void testBlankProjectIdThrowsException() {
		// Then: Blank projectId should throw IllegalArgumentException
		assertThrows(IllegalArgumentException.class, () -> new ConnectionProperties("", // blank projectId
				null, null, new ApplicationDefaultAuth(), null, null, null, null, false, null, null, null, null, null,
				false, null, null, null, null, null, null, null, null));
	}

	@Test
	void testNullAuthTypeThrowsException() {
		// Then: Null authType should throw NPE
		assertThrows(NullPointerException.class, () -> new ConnectionProperties("my-project", null, null, null, // authType
				null, null, null, null, false, null, null, null, null, null, false, null, null, null, null, null, null,
				null, null));
	}

	@Test
	void testLabelsImmutability() {
		// Given: A mutable map of labels
		Map<String, String> mutableLabels = new java.util.HashMap<>();
		mutableLabels.put("key1", "value1");

		ConnectionProperties props = new ConnectionProperties("my-project", null, null, new ApplicationDefaultAuth(),
				null, null, null, null, false, null, mutableLabels, null, null, null, false, null, null, null, null,
				null, null, null, null);

		// When: We try to modify the original map
		mutableLabels.put("key2", "value2");

		// Then: The labels in ConnectionProperties should be immutable
		assertEquals(1, props.labels().size());
		assertTrue(props.labels().containsKey("key1"));
		assertFalse(props.labels().containsKey("key2"));

		// And: Attempting to modify the returned map should throw exception
		assertThrows(UnsupportedOperationException.class, () -> props.labels().put("key3", "value3"));
	}

	@Test
	void testNullLabelsConvertsToEmptyMap() {
		// Given: Null labels
		ConnectionProperties props = new ConnectionProperties("my-project", null, null, new ApplicationDefaultAuth(),
				null, null, null, null, false, null, null, // null labels
				null, null, null, false, null, null, null, null, null, null, null, null);

		// Then: Labels should be an empty immutable map
		assertNotNull(props.labels());
		assertTrue(props.labels().isEmpty());
	}

	@Test
	void testGetDatasetIdWithDataset() {
		// Given: Properties with dataset
		ConnectionProperties props = new ConnectionProperties("my-project", "my_dataset", null,
				new ApplicationDefaultAuth(), null, null, null, null, false, null, null, null, null, null, false, null,
				null, null, null, null, null, null, null);

		// When: Getting the DatasetId
		DatasetId datasetId = props.getDatasetId();

		// Then: DatasetId should be created with project and dataset
		assertNotNull(datasetId);
		assertEquals("my-project", datasetId.getProject());
		assertEquals("my_dataset", datasetId.getDataset());
	}

	@Test
	void testGetDatasetIdWithDifferentProject() {
		// Given: Properties with dataset in different project
		ConnectionProperties props = new ConnectionProperties("my-project", "my_dataset", "other-project", // datasetProjectId
				new ApplicationDefaultAuth(), null, null, null, null, false, null, null, null, null, null, false, null,
				null, null, null, null, null, null, null);

		// When: Getting the DatasetId
		DatasetId datasetId = props.getDatasetId();

		// Then: DatasetId should use the datasetProjectId
		assertNotNull(datasetId);
		assertEquals("other-project", datasetId.getProject());
		assertEquals("my_dataset", datasetId.getDataset());
	}

	@Test
	void testGetDatasetIdWithoutDataset() {
		// Given: Properties without dataset
		ConnectionProperties props = new ConnectionProperties("my-project", null, // no dataset
				null, new ApplicationDefaultAuth(), null, null, null, null, false, null, null, null, null, null, false,
				null, null, null, null, null, null, null, null);

		// When: Getting the DatasetId
		DatasetId datasetId = props.getDatasetId();

		// Then: DatasetId should be null
		assertNull(datasetId);
	}

	@Test
	void testDefaultJobCreationMode() {
		// Given: Properties without jobCreationMode
		ConnectionProperties props = new ConnectionProperties("my-project", null, null, new ApplicationDefaultAuth(),
				null, null, null, null, false, null, null, null, // null jobCreationMode
				null, null, false, null, null, null, null, null, null, null, null);

		// Then: Should default to REQUIRED
		assertEquals(JobCreationMode.REQUIRED, props.jobCreationMode());
	}

	@Test
	void testDefaultUseStorageApi() {
		// Given: Properties without useStorageApi
		ConnectionProperties props = new ConnectionProperties("my-project", null, null, new ApplicationDefaultAuth(),
				null, null, null, null, false, null, null, null, null, null, // null useStorageApi
				false, null, null, null, null, null, null, null, null);

		// Then: Should default to "auto"
		assertEquals("auto", props.useStorageApi());
	}

	@Test
	void testRecordEquality() {
		// Given: Two identical ConnectionProperties
		AuthType auth = new ApplicationDefaultAuth();
		ConnectionProperties props1 = createMinimalProps("my-project", auth);
		ConnectionProperties props2 = createMinimalProps("my-project", auth);

		// Then: They should be equal
		assertEquals(props1, props2);
		assertEquals(props1.hashCode(), props2.hashCode());
	}

	private ConnectionProperties createMinimalProps(String projectId, AuthType auth) {
		return new ConnectionProperties(projectId, null, null, auth, null, null, null, null, false, null, null, null,
				null, null, false, null, null, null, null, null, null, null, null);
	}

	@Test
	void testRecordToString() {
		// Given: ConnectionProperties
		ConnectionProperties props = new ConnectionProperties("my-project", "my_dataset", null,
				new ApplicationDefaultAuth(), null, null, null, null, false, null, null, null, null, null, false, null,
				null, null, null, null, null, null, null);

		// When: Converting to string
		String str = props.toString();

		// Then: Should contain key information
		assertTrue(str.contains("my-project"));
		assertTrue(str.contains("my_dataset"));
	}
}

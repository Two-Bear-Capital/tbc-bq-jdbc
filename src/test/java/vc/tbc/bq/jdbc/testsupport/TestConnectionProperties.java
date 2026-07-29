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
package vc.tbc.bq.jdbc.testsupport;

import vc.tbc.bq.jdbc.auth.ApplicationDefaultAuth;
import vc.tbc.bq.jdbc.auth.AuthType;
import vc.tbc.bq.jdbc.config.ConnectionProperties;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Builds {@link ConnectionProperties} for tests that need one but do not care
 * about its shape.
 *
 * <p>
 * {@code ConnectionProperties} is a record with 26 components, most of them
 * nullable and several of them adjacent primitives. Calling the canonical
 * constructor positionally from a test means a wall of {@code null}s, and
 * changing the record breaks every such call at once — with compiler errors
 * that point at whichever argument first mistypes rather than at the component
 * that moved. Removing a single component in one release broke thirteen call
 * sites and reported them as {@code <nulltype> cannot be converted to boolean}.
 *
 * <p>
 * This class is the one place in the test tree that knows the positional order.
 * Adding or removing a component means editing {@link #build()} and one field,
 * not migrating every test.
 *
 * <p>
 * <b>Not for tests of the record itself.</b> Anything asserting what the
 * canonical constructor does — defaults being applied, validation throwing,
 * labels being defensively copied — should keep calling the constructor
 * directly. Those tests are supposed to notice when the record changes, and
 * routing them through a builder would test the builder instead.
 *
 * @since 3.0.4
 */
public final class TestConnectionProperties {

	private String projectId = "test-project";
	private String datasetId;
	private String datasetProjectId;
	private AuthType authType = new ApplicationDefaultAuth();
	private String host;
	private Integer port;
	private Integer timeoutSeconds;
	private Long maxResults;
	private boolean useLegacySql;
	private String location;
	private Map<String, String> labels;
	private Integer pageSize;
	private String useStorageApi;
	private boolean enableSessions;
	private Integer connectionTimeout;
	private Integer retryCount;
	private Long maxBillingBytes;
	private Integer metadataCacheTtl;
	private Boolean metadataCacheEnabled;
	private Boolean metadataLazyLoad;
	private Boolean enableQueryCostEstimation;
	private Boolean nativeComplexTypes;
	private Integer metadataCacheMaxRows;
	private BigDecimal queryPricePerTiB;
	private Boolean metadataIncludeDescriptions;
	private Boolean collapseShardedTables;

	private TestConnectionProperties() {
	}

	/**
	 * Starts a builder whose only populated fields are the two the record requires:
	 * a project id and an auth type. Everything else is left unset so the record's
	 * own defaults apply, which is what a test wants unless it says otherwise.
	 *
	 * @return a new builder
	 */
	public static TestConnectionProperties props() {
		return new TestConnectionProperties();
	}

	public TestConnectionProperties projectId(String value) {
		this.projectId = value;
		return this;
	}

	public TestConnectionProperties datasetId(String value) {
		this.datasetId = value;
		return this;
	}

	public TestConnectionProperties datasetProjectId(String value) {
		this.datasetProjectId = value;
		return this;
	}

	public TestConnectionProperties authType(AuthType value) {
		this.authType = value;
		return this;
	}

	public TestConnectionProperties host(String value) {
		this.host = value;
		return this;
	}

	public TestConnectionProperties port(Integer value) {
		this.port = value;
		return this;
	}

	public TestConnectionProperties timeoutSeconds(Integer value) {
		this.timeoutSeconds = value;
		return this;
	}

	public TestConnectionProperties maxResults(Long value) {
		this.maxResults = value;
		return this;
	}

	public TestConnectionProperties useLegacySql(boolean value) {
		this.useLegacySql = value;
		return this;
	}

	public TestConnectionProperties location(String value) {
		this.location = value;
		return this;
	}

	public TestConnectionProperties labels(Map<String, String> value) {
		this.labels = value;
		return this;
	}

	public TestConnectionProperties pageSize(Integer value) {
		this.pageSize = value;
		return this;
	}

	public TestConnectionProperties useStorageApi(String value) {
		this.useStorageApi = value;
		return this;
	}

	public TestConnectionProperties enableSessions(boolean value) {
		this.enableSessions = value;
		return this;
	}

	public TestConnectionProperties connectionTimeout(Integer value) {
		this.connectionTimeout = value;
		return this;
	}

	public TestConnectionProperties retryCount(Integer value) {
		this.retryCount = value;
		return this;
	}

	public TestConnectionProperties maxBillingBytes(Long value) {
		this.maxBillingBytes = value;
		return this;
	}

	public TestConnectionProperties metadataCacheTtl(Integer value) {
		this.metadataCacheTtl = value;
		return this;
	}

	public TestConnectionProperties metadataCacheEnabled(Boolean value) {
		this.metadataCacheEnabled = value;
		return this;
	}

	public TestConnectionProperties metadataLazyLoad(Boolean value) {
		this.metadataLazyLoad = value;
		return this;
	}

	public TestConnectionProperties enableQueryCostEstimation(Boolean value) {
		this.enableQueryCostEstimation = value;
		return this;
	}

	public TestConnectionProperties nativeComplexTypes(Boolean value) {
		this.nativeComplexTypes = value;
		return this;
	}

	public TestConnectionProperties metadataCacheMaxRows(Integer value) {
		this.metadataCacheMaxRows = value;
		return this;
	}

	public TestConnectionProperties queryPricePerTiB(BigDecimal value) {
		this.queryPricePerTiB = value;
		return this;
	}

	public TestConnectionProperties metadataIncludeDescriptions(Boolean value) {
		this.metadataIncludeDescriptions = value;
		return this;
	}

	public TestConnectionProperties collapseShardedTables(Boolean value) {
		this.collapseShardedTables = value;
		return this;
	}

	/**
	 * The single site in the test tree that depends on the record's positional
	 * order. A change to {@link ConnectionProperties} should break here and nowhere
	 * else.
	 *
	 * @return the built properties, with the record's own defaults applied to
	 *         anything left unset
	 */
	public ConnectionProperties build() {
		return new ConnectionProperties(projectId, datasetId, datasetProjectId, authType, host, port, timeoutSeconds,
				maxResults, useLegacySql, location, labels, pageSize, useStorageApi, enableSessions, connectionTimeout,
				retryCount, maxBillingBytes, metadataCacheTtl, metadataCacheEnabled, metadataLazyLoad,
				enableQueryCostEstimation, nativeComplexTypes, metadataCacheMaxRows, queryPricePerTiB,
				metadataIncludeDescriptions, collapseShardedTables);
	}
}

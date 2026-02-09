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
package vc.tbc.bq.jdbc.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QueryCostEstimateTest {

	@Test
	void testCalculateCostZeroBytes() {
		BigDecimal cost = QueryCostEstimate.calculateCost(0L);
		assertEquals(BigDecimal.ZERO, cost);
	}

	@Test
	void testCalculateCostNullBytes() {
		BigDecimal cost = QueryCostEstimate.calculateCost(null);
		assertEquals(BigDecimal.ZERO, cost);
	}

	@Test
	void testCalculateCostMinimumCharge() {
		// Less than 10 MB should be charged as 10 MB
		BigDecimal cost = QueryCostEstimate.calculateCost(1_000_000L); // 1 MB
		// 10 MB = 0.00001 TB * $6.25 = $0.0000625 rounded to $0.0001
		assertEquals(new BigDecimal("0.0001"), cost);
	}

	@Test
	void testCalculateCostOneGB() {
		// 1 GB = 1,000,000,000 bytes
		BigDecimal cost = QueryCostEstimate.calculateCost(1_000_000_000L);
		// 1 GB = 0.001 TB * $6.25 = $0.00625
		assertEquals(new BigDecimal("0.0063"), cost);
	}

	@Test
	void testCalculateCostOneTB() {
		// 1 TB = 1,000,000,000,000 bytes
		BigDecimal cost = QueryCostEstimate.calculateCost(1_000_000_000_000L);
		// 1 TB * $6.25 = $6.25
		assertEquals(new BigDecimal("6.2500"), cost);
	}

	@Test
	void testFormatBytesZero() {
		assertEquals("0 B", QueryCostEstimate.formatBytes(0L));
		assertEquals("0 B", QueryCostEstimate.formatBytes(null));
	}

	@Test
	void testFormatBytesSmall() {
		assertEquals("512 B", QueryCostEstimate.formatBytes(512L));
	}

	@Test
	void testFormatBytesKilobytes() {
		assertEquals("1.50 KB", QueryCostEstimate.formatBytes(1536L));
	}

	@Test
	void testFormatBytesMegabytes() {
		assertEquals("1.50 MB", QueryCostEstimate.formatBytes(1_572_864L));
	}

	@Test
	void testFormatBytesGigabytes() {
		assertEquals("1.50 GB", QueryCostEstimate.formatBytes(1_610_612_736L));
	}

	@Test
	void testFormatBytesTerabytes() {
		assertEquals("1.50 TB", QueryCostEstimate.formatBytes(1_649_267_441_664L));
	}

	@Test
	void testFormatSummary() {
		QueryCostEstimate estimate = new QueryCostEstimate(1_610_612_736L, // 1.5 GB
				1_610_612_736L, 1_610_612_736L, new BigDecimal("0.0101"));

		String summary = estimate.formatSummary();
		assertEquals("Query will process 1.50 GB, estimated cost: $0.0101", summary);
	}

	@Test
	void testGetMegabytes() {
		QueryCostEstimate estimate = new QueryCostEstimate(1_500_000_000L, // 1500 MB
				1_500_000_000L, 1_500_000_000L, new BigDecimal("0.0094"));

		assertEquals(1500, estimate.getMegabytes());
	}

	@Test
	void testGetMegabytesNull() {
		QueryCostEstimate estimate = new QueryCostEstimate(null, null, null, BigDecimal.ZERO);

		assertEquals(0, estimate.getMegabytes());
	}
}

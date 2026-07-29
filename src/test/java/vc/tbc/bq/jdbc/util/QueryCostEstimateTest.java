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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryCostEstimateTest {

	/** BigQuery's on-demand rate at the time of writing; here it is just a rate. */
	private static final BigDecimal ON_DEMAND = new BigDecimal("6.25");

	private static final long ONE_TIB = 1024L * 1024 * 1024 * 1024;
	private static final long ONE_GIB = 1024L * 1024 * 1024;

	@Test
	void testCalculateCostZeroBytes() {
		assertEquals(new BigDecimal("0.0000"), QueryCostEstimate.calculateCost(0L, ON_DEMAND));
	}

	@Test
	void testCalculateCostNullBytes() {
		assertEquals(new BigDecimal("0.0000"), QueryCostEstimate.calculateCost(null, ON_DEMAND));
	}

	@Test
	void testCalculateCostIsNullWithoutARate() {
		// The driver cannot know a customer's contract, so an unpriced estimate says
		// nothing about money rather than guessing the on-demand rate.
		assertNull(QueryCostEstimate.calculateCost(ONE_TIB, null));
		assertNull(QueryCostEstimate.calculateCost(null, null));
	}

	@Test
	void testCalculateCostMinimumCharge() {
		// Under BigQuery's 10 MiB minimum, so priced as 10 MiB:
		// 10485760 / 2^40 * 6.25 = 0.0000596, rounded to 4dp
		assertEquals(new BigDecimal("0.0001"), QueryCostEstimate.calculateCost(1_000_000L, ON_DEMAND));
	}

	@Test
	void testCalculateCostOneGibibyte() {
		// 1 GiB = 1/1024 TiB; 6.25 / 1024 = 0.0061035...
		assertEquals(new BigDecimal("0.0061"), QueryCostEstimate.calculateCost(ONE_GIB, ON_DEMAND));
	}

	@Test
	void testCalculateCostOneTebibyte() {
		assertEquals(new BigDecimal("6.2500"), QueryCostEstimate.calculateCost(ONE_TIB, ON_DEMAND));
	}

	@Test
	void testCalculateCostPricesPerTebibyteNotPerTerabyte() {
		// BigQuery prices per TiB. Dividing by 10^12 instead of 2^40 -- which the
		// driver used to do -- prices a decimal terabyte as a full TiB and overstates
		// every estimate by about 10%.
		BigDecimal decimalTerabyte = QueryCostEstimate.calculateCost(1_000_000_000_000L, ON_DEMAND);
		assertEquals(new BigDecimal("5.6843"), decimalTerabyte);
	}

	@Test
	void testCalculateCostScalesWithTheConfiguredRate() {
		// The rate is the caller's, in the caller's currency. Half the rate, half the
		// cost -- nothing about USD or 6.25 is baked in.
		assertEquals(new BigDecimal("3.1250"), QueryCostEstimate.calculateCost(ONE_TIB, new BigDecimal("3.125")));
		assertEquals(new BigDecimal("0.0000"), QueryCostEstimate.calculateCost(ONE_TIB, BigDecimal.ZERO));
	}

	@Test
	void testFactoryPricesProcessedBytesNotBilledBytes() {
		// A dry run bills nothing, so BigQuery reports totalBytesBilled = 0 on every
		// estimate. Pricing that figure -- which the driver used to do -- makes every
		// query cost exactly zero no matter how much it reads.
		QueryCostEstimate estimate = QueryCostEstimate.of(ONE_TIB, null, 0L, ON_DEMAND);

		assertEquals(new BigDecimal("6.2500"), estimate.estimatedCost());
		assertEquals(ON_DEMAND, estimate.pricePerTiB());
		assertTrue(estimate.isPriced());
	}

	@Test
	void testFactoryFallsBackToEstimatedBytesProcessed() {
		// Some job shapes report estimatedBytesProcessed instead of the total.
		QueryCostEstimate estimate = QueryCostEstimate.of(null, ONE_TIB, 0L, ON_DEMAND);

		assertEquals(new BigDecimal("6.2500"), estimate.estimatedCost());
		assertEquals(ONE_TIB, estimate.billableBytes());
	}

	@Test
	void testFactoryLeavesEstimateUnpricedWithoutARate() {
		QueryCostEstimate estimate = QueryCostEstimate.of(ONE_GIB, null, 0L, null);

		assertNull(estimate.estimatedCost());
		assertNull(estimate.pricePerTiB());
		assertFalse(estimate.isPriced());
		// The bytes are still there: those are what BigQuery actually reported.
		assertEquals(ONE_GIB, estimate.totalBytesProcessed());
		assertEquals(ONE_GIB, estimate.billableBytes());
	}

	@Test
	void testBillableBytesRoundsUpToTheNearestMebibyte() {
		// BigQuery rounds billed volume up to the nearest MiB. One byte over a
		// boundary is charged as the whole next MiB.
		long elevenMib = 11L * 1024 * 1024;
		assertEquals(elevenMib + 1024 * 1024, QueryCostEstimate.of(elevenMib + 1, null, 0L, null).billableBytes());
		assertEquals(elevenMib, QueryCostEstimate.of(elevenMib, null, 0L, null).billableBytes());
	}

	@Test
	void testBillableBytesAppliesTheTenMebibyteMinimum() {
		assertEquals(10L * 1024 * 1024, QueryCostEstimate.of(1L, null, 0L, null).billableBytes());
	}

	@Test
	void testBillableBytesIsZeroForAQueryThatReadsNothing() {
		// SELECT 1 touches no table, so there is no table for the 10 MiB per-table
		// minimum to apply to and nothing is billed.
		assertEquals(0L, QueryCostEstimate.of(0L, null, 0L, null).billableBytes());
		assertEquals(0L, QueryCostEstimate.of(null, null, 0L, null).billableBytes());
		assertEquals(new BigDecimal("0.0000"), QueryCostEstimate.calculateCost(0L, ON_DEMAND));
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
	void testFormatBytesUsesBinaryUnitLabels() {
		// The values were always powers of 1024; only the labels used to claim
		// otherwise, which put them at odds with BigQuery's per-TiB pricing.
		assertEquals("1.50 KiB", QueryCostEstimate.formatBytes(1536L));
		assertEquals("1.50 MiB", QueryCostEstimate.formatBytes(1_572_864L));
		assertEquals("1.50 GiB", QueryCostEstimate.formatBytes(1_610_612_736L));
		assertEquals("1.50 TiB", QueryCostEstimate.formatBytes(1_649_267_441_664L));
	}

	@Test
	void testFormatSummary() {
		QueryCostEstimate estimate = QueryCostEstimate.of(1_610_612_736L, null, 0L, ON_DEMAND);

		assertEquals("Query will process 1.50 GiB, estimated cost: 0.0092", estimate.formatSummary());
	}

	@Test
	void testFormatSummaryOmitsCostWhenUnpriced() {
		QueryCostEstimate estimate = QueryCostEstimate.of(1_610_612_736L, null, 0L, null);

		assertEquals("Query will process 1.50 GiB", estimate.formatSummary());
	}

	@Test
	void testGetMegabytes() {
		QueryCostEstimate estimate = QueryCostEstimate.of(1_500_000_000L, null, 0L, ON_DEMAND);

		// Mebibytes: 1_500_000_000 / 2^20
		assertEquals(1430, estimate.getMegabytes());
	}

	@Test
	void testGetMegabytesNull() {
		QueryCostEstimate estimate = QueryCostEstimate.of(null, null, 0L, ON_DEMAND);

		assertEquals(0, estimate.getMegabytes());
	}

	@Test
	void testFormatWarningQuotesTheBytesItPrices() {
		// The number quoted and the number priced must be the same one. Small queries
		// are where they diverge: 1 MiB read is 10 MiB billed.
		QueryCostEstimate estimate = QueryCostEstimate.of(1_048_576L, null, 0L, ON_DEMAND);

		assertEquals("Query will process 1.00 MiB (10.00 MiB billable), estimated cost: 0.0001 at 6.25/TiB",
				estimate.formatWarning());
	}

	@Test
	void testFormatWarningOmitsCostWhenUnpriced() {
		QueryCostEstimate estimate = QueryCostEstimate.of(1_610_612_736L, null, 0L, null);

		assertEquals("Query will process 1.50 GiB (1.50 GiB billable)", estimate.formatWarning());
	}

	@Test
	void testFormatWarningWithNullBytesProcessed() {
		// BigQuery omits totalBytesProcessed for some jobs. Unboxing it used to throw
		// an NPE that the dry-run catch block swallowed, silently disabling cost
		// estimation and logging "Dry-run estimation failed: null".
		QueryCostEstimate estimate = QueryCostEstimate.of(null, null, 0L, null);

		assertEquals("Query will process 0 B (0 B billable)", estimate.formatWarning());
	}
}

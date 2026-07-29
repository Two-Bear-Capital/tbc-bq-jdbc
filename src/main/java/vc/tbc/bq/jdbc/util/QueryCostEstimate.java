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

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * What a BigQuery dry-run says a statement will cost, as a value rather than as
 * a sentence.
 *
 * <p>
 * Obtain one from {@link vc.tbc.bq.jdbc.base.AbstractBQStatement#estimateCost}
 * for a statement you have not run, or from
 * {@link vc.tbc.bq.jdbc.base.AbstractBQStatement#getCostEstimates()} after
 * executing with {@code enableQueryCostEstimation=true}.
 *
 * <p>
 * <b>Bytes are always present; money is not.</b> BigQuery reports the byte
 * counts, so those are what the driver knows. A price is a property of the
 * customer's contract — on-demand, editions, a negotiated rate, a currency that
 * is not USD — and the driver has no way to discover it.
 * {@link #estimatedCost()} is therefore {@code null} unless
 * {@code queryPricePerTiB} is configured on the connection, and the unit of
 * that rate is whatever currency the caller supplied it in.
 *
 * <p>
 * <b>A dry run reports processed bytes, never billed bytes.</b> BigQuery bills
 * nothing for a dry run, so {@code totalBytesBilled} comes back as {@code 0} on
 * every estimate: it describes the dry-run job itself, not the query being
 * modelled. {@link #totalBytesProcessed()} carries the answer, and
 * {@link #billableBytes()} turns it into what a real run would be charged for
 * by applying BigQuery's rounding rules. Cost is computed from that, and it is
 * the figure the formatted messages quote.
 *
 * @param totalBytesProcessed
 *            bytes the query will read, or {@code null} when BigQuery did not
 *            report it
 * @param estimatedBytesProcessed
 *            BigQuery's own estimate of bytes processed, which for some job
 *            shapes is reported when {@code totalBytesProcessed} is not
 * @param totalBytesBilled
 *            bytes billed for the dry-run job itself, which is {@code 0}.
 *            Carried because it is what BigQuery reported, not because it
 *            prices anything
 * @param estimatedCost
 *            cost of {@link #billableBytes()} at {@code pricePerTiB}, or
 *            {@code null} when no rate is configured
 * @param pricePerTiB
 *            the rate the cost was computed at, or {@code null} when none is
 *            configured. Carried so a caller can tell an estimate priced at a
 *            stale rate from one priced at the current one
 * @since 1.0.48
 */
public record QueryCostEstimate(Long totalBytesProcessed, Long estimatedBytesProcessed, Long totalBytesBilled,
		BigDecimal estimatedCost, BigDecimal pricePerTiB) {

	/**
	 * Bytes in a tebibyte.
	 *
	 * <p>
	 * BigQuery prices on-demand analysis "per TiB", and a TiB is 2^40 bytes. The
	 * driver used to divide by 10^12 and so overstated every estimate by about 10%.
	 */
	private static final BigDecimal BYTES_PER_TIB = new BigDecimal("1099511627776");

	/** Bytes in a mebibyte, the unit BigQuery rounds billed volume up to. */
	private static final long BYTES_PER_MIB = 1024L * 1024;

	/** BigQuery's minimum billable volume: 10 MiB per query and per table. */
	private static final long MIN_BILLABLE_BYTES = 10 * BYTES_PER_MIB;

	/** Decimal places the cost is rounded to. */
	private static final int COST_SCALE = 4;

	/**
	 * Builds an estimate from raw dry-run statistics, pricing it when a rate is
	 * configured.
	 *
	 * @param totalBytesProcessed
	 *            bytes the query will read, or null
	 * @param estimatedBytesProcessed
	 *            BigQuery's estimate of bytes processed, or null
	 * @param totalBytesBilled
	 *            bytes billed for the dry-run job, which BigQuery reports as 0
	 * @param pricePerTiB
	 *            the configured rate per tebibyte, or null for none
	 * @return the estimate, priced only if a rate was given
	 */
	public static QueryCostEstimate of(Long totalBytesProcessed, Long estimatedBytesProcessed, Long totalBytesBilled,
			BigDecimal pricePerTiB) {
		Long processed = totalBytesProcessed != null ? totalBytesProcessed : estimatedBytesProcessed;
		return new QueryCostEstimate(totalBytesProcessed, estimatedBytesProcessed, totalBytesBilled,
				calculateCost(processed, pricePerTiB), pricePerTiB);
	}

	/**
	 * What a real run of this query would be charged for: bytes processed, rounded
	 * up to the nearest MiB, with BigQuery's 10 MiB minimum applied.
	 *
	 * <p>
	 * Derived rather than read from {@code totalBytesBilled}, which a dry run
	 * always reports as 0 because a dry run is not billed.
	 *
	 * @return billable bytes, or 0 for a query that reads nothing
	 */
	public long billableBytes() {
		Long processed = totalBytesProcessed != null ? totalBytesProcessed : estimatedBytesProcessed;
		return billableBytes(processed);
	}

	/**
	 * Applies BigQuery's billing rounding to a processed-byte count.
	 *
	 * @param bytesProcessed
	 *            bytes the query reads, or null
	 * @return the volume that would be billed
	 */
	private static long billableBytes(Long bytesProcessed) {
		if (bytesProcessed == null || bytesProcessed <= 0) {
			// A query that reads no table -- SELECT 1, or a fully pruned scan -- is
			// free. The 10 MiB minimum is per table referenced, so with no bytes read
			// there is nothing to apply it to.
			return 0;
		}
		long roundedUpToMib = (bytesProcessed + BYTES_PER_MIB - 1) / BYTES_PER_MIB * BYTES_PER_MIB;
		return Math.max(roundedUpToMib, MIN_BILLABLE_BYTES);
	}

	/**
	 * Prices a processed-byte count at the given rate, applying BigQuery's billing
	 * rounding first.
	 *
	 * @param bytesProcessed
	 *            bytes the query will read, as reported by the dry run
	 * @param pricePerTiB
	 *            rate per tebibyte, in the caller's currency
	 * @return the cost, or {@code null} when no rate was supplied
	 */
	public static BigDecimal calculateCost(Long bytesProcessed, BigDecimal pricePerTiB) {
		if (pricePerTiB == null) {
			return null;
		}
		long billable = billableBytes(bytesProcessed);
		if (billable == 0) {
			return BigDecimal.ZERO.setScale(COST_SCALE, RoundingMode.HALF_UP);
		}

		BigDecimal tebibytes = new BigDecimal(billable).divide(BYTES_PER_TIB, 12, RoundingMode.HALF_UP);
		return tebibytes.multiply(pricePerTiB).setScale(COST_SCALE, RoundingMode.HALF_UP);
	}

	/**
	 * Whether this estimate carries a cost, which it does only when the connection
	 * configured a rate.
	 *
	 * @return true if {@link #estimatedCost()} is non-null
	 */
	public boolean isPriced() {
		return estimatedCost != null;
	}

	/**
	 * Formats bytes in human-readable form.
	 *
	 * <p>
	 * Binary units throughout, and labelled as such: BigQuery's own byte figures
	 * and its per-TiB pricing are binary, so rendering them against powers of 1000
	 * would disagree with the console.
	 *
	 * @param bytes
	 *            number of bytes
	 * @return formatted string (e.g., "1.50 GiB")
	 */
	public static String formatBytes(Long bytes) {
		if (bytes == null || bytes == 0) {
			return "0 B";
		}
		if (bytes < 1024) {
			return bytes + " B";
		}
		if (bytes < 1024L * 1024) {
			return String.format("%.2f KiB", bytes / 1024.0);
		}
		if (bytes < 1024L * 1024 * 1024) {
			return String.format("%.2f MiB", bytes / (1024.0 * 1024));
		}
		if (bytes < 1024L * 1024 * 1024 * 1024) {
			return String.format("%.2f GiB", bytes / (1024.0 * 1024 * 1024));
		}
		return String.format("%.2f TiB", bytes / (1024.0 * 1024 * 1024 * 1024));
	}

	/**
	 * Formats a summary message suitable for display.
	 *
	 * @return formatted summary (e.g., "Query will process 1.50 GiB, estimated
	 *         cost: 0.0101")
	 */
	public String formatSummary() {
		String processed = "Query will process " + formatBytes(totalBytesProcessed);
		return isPriced() ? processed + ", estimated cost: " + estimatedCost : processed;
	}

	/**
	 * Formats the message carried by the query-cost {@link java.sql.SQLWarning}.
	 *
	 * <p>
	 * Quotes the billable figure next to the processed one, so the number shown and
	 * the number priced are the same. They differ on small queries, where
	 * BigQuery's rounding and its 10 MiB minimum dominate.
	 *
	 * <p>
	 * No currency symbol: the rate comes from {@code queryPricePerTiB} and the
	 * driver has no idea what currency the caller expressed it in.
	 *
	 * @return formatted warning (e.g., "Query will process 1.50 GiB (1.50 GiB
	 *         billable), estimated cost: 0.0092 at 6.25/TiB")
	 */
	public String formatWarning() {
		String bytes = String.format("Query will process %s (%s billable)", formatBytes(totalBytesProcessed),
				formatBytes(billableBytes()));
		if (!isPriced()) {
			return bytes;
		}
		return String.format("%s, estimated cost: %s at %s/TiB", bytes, estimatedCost, pricePerTiB);
	}

	/**
	 * Bytes processed expressed in whole mebibytes, used as the vendor code of the
	 * query-cost {@link java.sql.SQLWarning}.
	 *
	 * <p>
	 * Mebibytes rather than megabytes, so the number agrees with the units
	 * everything else here reports in.
	 *
	 * @return mebibytes processed, or 0 when BigQuery did not report the figure
	 */
	public int getMegabytes() {
		if (totalBytesProcessed == null) {
			return 0;
		}
		return (int) (totalBytesProcessed / (1024 * 1024));
	}
}

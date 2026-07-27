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
 * Query cost estimate from BigQuery dry-run.
 *
 * <p>
 * Contains information about how much data a query will process and the
 * estimated cost in USD based on BigQuery's on-demand pricing.
 *
 * <p>
 * BigQuery pricing (as of 2026): $6.25 per TB processed (on-demand)
 *
 * @param totalBytesProcessed
 *            total bytes that will be processed by the query
 * @param estimatedBytesProcessed
 *            estimated bytes processed (may differ from total based on
 *            accuracy)
 * @param totalBytesBilled
 *            total bytes that will be billed (includes pricing tier
 *            adjustments)
 * @param estimatedCostUSD
 *            estimated cost in USD
 * @since 1.0.48
 */
public record QueryCostEstimate(Long totalBytesProcessed, Long estimatedBytesProcessed, Long totalBytesBilled,
		BigDecimal estimatedCostUSD) {

	/** BigQuery on-demand pricing per TB (as of 2026). */
	private static final BigDecimal PRICE_PER_TB = new BigDecimal("6.25");

	/** Minimum billable bytes (10 MB). */
	private static final long MIN_BILLABLE_BYTES = 10_000_000L;

	/**
	 * Calculates estimated cost based on bytes billed.
	 *
	 * @param bytesBilled
	 *            bytes that will be billed
	 * @return estimated cost in USD
	 */
	public static BigDecimal calculateCost(Long bytesBilled) {
		if (bytesBilled == null || bytesBilled == 0) {
			return BigDecimal.ZERO;
		}

		// BigQuery minimum charge: 10 MB
		long billableBytes = Math.max(bytesBilled, MIN_BILLABLE_BYTES);

		// Convert to terabytes
		BigDecimal terabytes = new BigDecimal(billableBytes).divide(new BigDecimal("1000000000000"), 12,
				RoundingMode.HALF_UP);

		// Calculate cost: TB * $6.25
		return terabytes.multiply(PRICE_PER_TB).setScale(4, RoundingMode.HALF_UP);
	}

	/**
	 * Formats bytes in human-readable form.
	 *
	 * @param bytes
	 *            number of bytes
	 * @return formatted string (e.g., "1.5 GB")
	 */
	public static String formatBytes(Long bytes) {
		if (bytes == null || bytes == 0) {
			return "0 B";
		}
		if (bytes < 1024) {
			return bytes + " B";
		}
		if (bytes < 1024L * 1024) {
			return String.format("%.2f KB", bytes / 1024.0);
		}
		if (bytes < 1024L * 1024 * 1024) {
			return String.format("%.2f MB", bytes / (1024.0 * 1024));
		}
		if (bytes < 1024L * 1024 * 1024 * 1024) {
			return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
		}
		return String.format("%.2f TB", bytes / (1024.0 * 1024 * 1024 * 1024));
	}

	/**
	 * Formats a summary message suitable for display.
	 *
	 * @return formatted summary (e.g., "Query will process 1.5 GB, estimated cost:
	 *         $0.0094")
	 */
	public String formatSummary() {
		return String.format("Query will process %s, estimated cost: $%s", formatBytes(totalBytesProcessed),
				estimatedCostUSD);
	}

	/**
	 * Formats the message carried by the query-cost {@link java.sql.SQLWarning},
	 * which adds the raw megabyte figure to {@link #formatSummary()}.
	 *
	 * <p>
	 * BigQuery omits {@code totalBytesProcessed} for some jobs, so the field is a
	 * nullable {@link Long}. Formatting it here rather than at the call site keeps
	 * the null handling in one place, next to {@link #getMegabytes()} which already
	 * guards the same value.
	 *
	 * @return formatted warning (e.g., "Query will process 1.5 GB (1610.61 MB),
	 *         estimated cost: $0.0094")
	 */
	public String formatWarning() {
		return String.format("Query will process %s (%.2f MB), estimated cost: $%s", formatBytes(totalBytesProcessed),
				megabytesProcessed(), estimatedCostUSD);
	}

	/**
	 * Bytes processed expressed in megabytes, or {@code 0} when BigQuery did not
	 * report the figure.
	 *
	 * @return megabytes processed
	 */
	private double megabytesProcessed() {
		return totalBytesProcessed == null ? 0.0 : totalBytesProcessed / 1_000_000.0;
	}

	/**
	 * Formats megabytes for use in SQLWarning vendor code.
	 *
	 * @return megabytes as integer
	 */
	public int getMegabytes() {
		if (totalBytesProcessed == null) {
			return 0;
		}
		return (int) (totalBytesProcessed / 1_000_000);
	}
}
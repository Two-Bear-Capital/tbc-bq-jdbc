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

import com.google.cloud.bigquery.QueryJobConfiguration;
import vc.tbc.bq.jdbc.base.AbstractBQStatement;
import vc.tbc.bq.jdbc.exception.BQSQLException;
import vc.tbc.bq.jdbc.exception.BQSQLFeatureNotSupportedException;
import vc.tbc.bq.jdbc.util.ErrorMessages;
import vc.tbc.bq.jdbc.util.UnsupportedOperations;

import java.sql.BatchUpdateException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * JDBC Statement implementation for BigQuery.
 *
 * @since 1.0.0
 */
public class BQStatement extends AbstractBQStatement {

	/** Fetch size for result pagination. 0 means use connection default. */
	private int fetchSize = 0;

	/** Batched SQL commands accumulated via {@link #addBatch(String)}. */
	private final List<String> sqlBatch = new ArrayList<>();

	public BQStatement(BQConnection connection) {
		super(connection);
	}

	@Override
	protected String getClosedErrorMessage() {
		return ErrorMessages.STATEMENT_CLOSED;
	}

	@Override
	protected QueryJobConfiguration.Builder buildQueryConfig(String sql) {
		return QueryJobConfiguration.newBuilder(sql).setUseLegacySql(properties.useLegacySql());
	}

	@Override
	protected String getLogPrefix() {
		return "Query";
	}

	/**
	 * Executes the given SQL statement and returns the results as a ResultSet.
	 *
	 * <p>
	 * The SQL is submitted to BigQuery as a query job. This method blocks until the
	 * query completes or the query timeout (configured via
	 * {@link #setQueryTimeout(int)}) is reached.
	 *
	 * <p>
	 * <b>SQL Dialect:</b> BigQuery supports standard SQL by default. Legacy SQL can
	 * be enabled via connection property {@code useLegacySql=true}.
	 *
	 * <p>
	 * <b>Blocking Behavior:</b> This method waits for the entire query to complete
	 * before returning. For large result sets, consider using the Storage Read API
	 * by configuring {@code useStorageApi=true} connection property.
	 *
	 * @param sql
	 *            the SQL query to execute (must be a SELECT or other query
	 *            statement)
	 * @return a ResultSet containing the query results
	 * @throws SQLException
	 *             if the statement is closed, the SQL is invalid, or query
	 *             execution fails
	 */
	@Override
	public ResultSet executeQuery(String sql) throws SQLException {
		return executeQueryInternal(sql);
	}

	/**
	 * Executes the given SQL DML statement (INSERT, UPDATE, DELETE, MERGE) and
	 * returns the number of affected rows.
	 *
	 * <p>
	 * The SQL is submitted to BigQuery as a query job. This method blocks until the
	 * DML statement completes or the query timeout is reached. The affected-row
	 * count is taken from BigQuery's DML job statistics
	 * ({@code numDmlAffectedRows}).
	 *
	 * <p>
	 * <b>Return Value:</b> The actual number of rows affected for DML statements.
	 * Returns 0 for statements that carry no DML statistics (DDL, SELECT), per the
	 * JDBC contract for statements that return nothing.
	 *
	 * <p>
	 * <b>Usage Example:</b>
	 *
	 * <pre>{@code
	 * int inserted = stmt.executeUpdate("INSERT INTO dataset.table (id, name) VALUES (1, 'Alice')");
	 * int updated = stmt.executeUpdate("UPDATE dataset.table SET name = 'Bob' WHERE id = 1");
	 * int deleted = stmt.executeUpdate("DELETE FROM dataset.table WHERE id = 1");
	 * }</pre>
	 *
	 * @param sql
	 *            the SQL DML statement to execute
	 * @return the number of affected rows (0 when BigQuery reports no DML
	 *         statistics)
	 * @throws SQLException
	 *             if the statement is closed, the SQL is invalid, or execution
	 *             fails
	 */
	@Override
	public int executeUpdate(String sql) throws SQLException {
		long count = executeLargeUpdate(sql);
		return count > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) count;
	}

	/**
	 * Large-count variant of {@link #executeUpdate(String)}.
	 *
	 * @param sql
	 *            the SQL DML statement to execute
	 * @return the number of affected rows (0 when BigQuery reports no DML
	 *         statistics)
	 * @throws SQLException
	 *             if the statement is closed, the SQL is invalid, or execution
	 *             fails
	 */
	@Override
	public long executeLargeUpdate(String sql) throws SQLException {
		long affectedRows = executeDmlInternal(sql, null);
		// JDBC: 0 for statements that return nothing (DDL, or no DML statistics)
		long count = Math.max(0L, affectedRows);
		currentUpdateCount = count;
		return count;
	}

	@Override
	public SQLWarning getWarnings() throws SQLException {
		checkClosed();
		return queryWarnings;
	}

	@Override
	@SuppressWarnings("PMD.NullAssignment") // null-out warnings on clear; intentional state reset
	public void clearWarnings() throws SQLException {
		checkClosed();
		queryWarnings = null;
	}

	@Override
	public void setCursorName(String name) throws SQLException {
		throw UnsupportedOperations.namedCursors();
	}

	/**
	 * Executes the given SQL statement, which may return multiple types of results.
	 *
	 * <p>
	 * <b>Return Value:</b> Returns {@code false} when BigQuery reports the
	 * statement was DML (INSERT, UPDATE, DELETE, MERGE) — retrieve the affected-row
	 * count via {@link #getUpdateCount()}. Returns {@code true} otherwise (SELECT,
	 * DDL, or when DML statistics are unavailable) — retrieve results via
	 * {@link #getResultSet()}.
	 *
	 * <p>
	 * The SQL is submitted to BigQuery as a query job. This method blocks until the
	 * query completes or the query timeout is reached.
	 *
	 * @param sql
	 *            the SQL statement to execute
	 * @return {@code true} if the result is a ResultSet, {@code false} if it is an
	 *         update count
	 * @throws SQLException
	 *             if the statement is closed, the SQL is invalid, or execution
	 *             fails
	 */
	@Override
	@SuppressWarnings("resource") // ResultSet managed by statement, closed in statement.close()
	public boolean execute(String sql) throws SQLException {
		executeQuery(sql);
		return !finishExecuteAsUpdateIfDml();
	}

	/**
	 * Shared tail for {@code execute()} implementations: when the just-executed job
	 * carried DML statistics, the JDBC result is an update count rather than a
	 * ResultSet — close the (empty) ResultSet so {@link #getResultSet()} returns
	 * null and {@link #getUpdateCount()} reports the count.
	 *
	 * @return true when the result was converted to an update count
	 * @throws SQLException
	 *             if closing the result set fails
	 */
	@SuppressWarnings("PMD.NullAssignment") // getResultSet() must return null for DML results
	protected boolean finishExecuteAsUpdateIfDml() throws SQLException {
		if (currentUpdateCount < 0) {
			return false;
		}
		if (currentResultSet != null) {
			currentResultSet.close();
			currentResultSet = null;
		}
		return true;
	}

	@Override
	public void setFetchDirection(int direction) throws SQLException {
		checkClosed();
		if (direction != ResultSet.FETCH_FORWARD) {
			throw new BQSQLFeatureNotSupportedException("Only FETCH_FORWARD is supported");
		}
	}

	@Override
	public int getFetchDirection() throws SQLException {
		checkClosed();
		return ResultSet.FETCH_FORWARD;
	}

	/**
	 * Sets the page size this statement reads results at, overriding the
	 * connection's {@code pageSize} for this statement only.
	 *
	 * <p>
	 * Per-statement is the useful granularity: one connection may run both a narrow
	 * lookup and a million-row scan, which want different page sizes. Zero restores
	 * the connection default, per the JDBC contract that 0 means the driver
	 * decides.
	 *
	 * @param rows
	 *            the page size, or 0 for the connection's {@code pageSize}
	 * @throws SQLException
	 *             if the statement is closed or {@code rows} is negative
	 */
	@Override
	public void setFetchSize(int rows) throws SQLException {
		checkClosed();
		if (rows < 0) {
			throw new SQLException("Fetch size must be non-negative");
		}
		this.fetchSize = rows;
	}

	@Override
	public int getFetchSize() throws SQLException {
		checkClosed();
		return getEffectiveFetchSize();
	}

	/**
	 * The page size results are actually read at: this statement's if one was set,
	 * otherwise the connection's {@code pageSize}.
	 *
	 * <p>
	 * {@link #getFetchSize()} delegates here rather than repeating the expression —
	 * a statement has one effective page size, and two copies of the rule is one
	 * copy to forget when it changes.
	 */
	@Override
	protected int getEffectiveFetchSize() {
		return fetchSize > 0 ? fetchSize : properties.pageSize();
	}

	@Override
	public int getResultSetConcurrency() throws SQLException {
		checkClosed();
		return ResultSet.CONCUR_READ_ONLY;
	}

	@Override
	public int getResultSetType() throws SQLException {
		checkClosed();
		return ResultSet.TYPE_FORWARD_ONLY;
	}

	/**
	 * Adds the given SQL command to the batch of commands for this statement.
	 *
	 * <p>
	 * Heterogeneous SQL batches are executed sequentially, one BigQuery query job
	 * per command (BigQuery has no native multi-statement batch API outside of
	 * sessions). For high-throughput inserts prefer
	 * {@link java.sql.PreparedStatement#addBatch()}, which collapses batched
	 * parameter sets into a single multi-row INSERT job.
	 *
	 * @param sql
	 *            the SQL command to add to the batch
	 * @throws SQLException
	 *             if the statement is closed or sql is null
	 */
	@Override
	public void addBatch(String sql) throws SQLException {
		checkClosed();
		if (sql == null) {
			throw new BQSQLException("SQL statement must not be null", BQSQLException.SQLSTATE_INVALID_PARAMETER_VALUE);
		}
		sqlBatch.add(sql);
	}

	@Override
	public void clearBatch() throws SQLException {
		checkClosed();
		sqlBatch.clear();
	}

	/**
	 * Executes the batched SQL commands sequentially, one BigQuery query job per
	 * command, and returns their update counts.
	 *
	 * <p>
	 * Update counts are taken from BigQuery's DML statistics when available;
	 * commands whose affected-row count is unknown report {@link #SUCCESS_NO_INFO}.
	 * The batch is cleared once execution completes or fails. If a command fails, a
	 * {@link BatchUpdateException} is thrown containing the update counts of the
	 * commands that completed before the failure.
	 *
	 * @return per-command update counts
	 * @throws SQLException
	 *             if the statement is closed
	 * @throws BatchUpdateException
	 *             if any command in the batch fails
	 */
	@Override
	public int[] executeBatch() throws SQLException {
		checkClosed();
		if (sqlBatch.isEmpty()) {
			return new int[0];
		}
		List<String> commands = new ArrayList<>(sqlBatch);
		sqlBatch.clear();

		int[] updateCounts = new int[commands.size()];
		for (int i = 0; i < commands.size(); i++) {
			try {
				// No per-entry estimate: this path is already one job per command (#140).
				updateCounts[i] = toUpdateCount(executeDmlInternal(commands.get(i), null, false));
			} catch (SQLException e) {
				throw new BatchUpdateException("Batch entry " + i + " failed: " + e.getMessage(), e.getSQLState(),
						e.getErrorCode(), Arrays.copyOf(updateCounts, i), e);
			}
		}
		return updateCounts;
	}

	@Override
	public long[] executeLargeBatch() throws SQLException {
		int[] updateCounts = executeBatch();
		long[] largeCounts = new long[updateCounts.length];
		for (int i = 0; i < updateCounts.length; i++) {
			largeCounts[i] = updateCounts[i];
		}
		return largeCounts;
	}

	/**
	 * Converts a BigQuery affected-row count to a JDBC batch update count. Unknown
	 * counts (negative) and counts exceeding int range map to
	 * {@link #SUCCESS_NO_INFO}.
	 *
	 * @param affectedRows
	 *            the affected-row count from BigQuery, or -1 if unknown
	 * @return the JDBC update count
	 */
	protected static int toUpdateCount(long affectedRows) {
		if (affectedRows < 0 || affectedRows > Integer.MAX_VALUE) {
			return SUCCESS_NO_INFO;
		}
		return (int) affectedRows;
	}

	@Override
	public ResultSet getGeneratedKeys() throws SQLException {
		throw UnsupportedOperations.generatedKeys();
	}

	@Override
	public int executeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
		return executeUpdate(sql);
	}

	@Override
	public int executeUpdate(String sql, int[] columnIndexes) throws SQLException {
		return executeUpdate(sql);
	}

	@Override
	public int executeUpdate(String sql, String[] columnNames) throws SQLException {
		return executeUpdate(sql);
	}

	@Override
	public long executeLargeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
		return executeLargeUpdate(sql);
	}

	@Override
	public long executeLargeUpdate(String sql, int[] columnIndexes) throws SQLException {
		return executeLargeUpdate(sql);
	}

	@Override
	public long executeLargeUpdate(String sql, String[] columnNames) throws SQLException {
		return executeLargeUpdate(sql);
	}

	@Override
	public boolean execute(String sql, int autoGeneratedKeys) throws SQLException {
		return execute(sql);
	}

	@Override
	public boolean execute(String sql, int[] columnIndexes) throws SQLException {
		return execute(sql);
	}

	@Override
	public boolean execute(String sql, String[] columnNames) throws SQLException {
		return execute(sql);
	}

	@Override
	public int getResultSetHoldability() throws SQLException {
		checkClosed();
		return ResultSet.CLOSE_CURSORS_AT_COMMIT;
	}

	@Override
	public boolean isClosed() throws SQLException {
		return closed;
	}

	@Override
	public void setPoolable(boolean poolable) throws SQLException {
		checkClosed();
	}

	@Override
	public boolean isPoolable() throws SQLException {
		checkClosed();
		return false;
	}

	@Override
	public void closeOnCompletion() throws SQLException {
		checkClosed();
	}

	@Override
	public boolean isCloseOnCompletion() throws SQLException {
		checkClosed();
		return false;
	}

	// JDBC 4.3 methods
	@Override
	public String enquoteLiteral(String val) throws SQLException {
		checkClosed();
		return "'" + val.replace("\\", "\\\\").replace("'", "\\'") + "'";
	}

	@Override
	public String enquoteIdentifier(String identifier, boolean alwaysQuote) throws SQLException {
		checkClosed();
		if (!alwaysQuote && isValidUnquotedIdentifier(identifier)) {
			return identifier;
		}
		return "`" + identifier.replace("`", "\\`") + "`";
	}

	private boolean isValidUnquotedIdentifier(String identifier) {
		if (identifier == null || identifier.isEmpty()) {
			return false;
		}
		if (!Character.isLetter(identifier.charAt(0)) && identifier.charAt(0) != '_') {
			return false;
		}
		for (char c : identifier.toCharArray()) {
			if (!Character.isLetterOrDigit(c) && c != '_') {
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean isSimpleIdentifier(String identifier) throws SQLException {
		checkClosed();
		return isValidUnquotedIdentifier(identifier);
	}

	@Override
	public String enquoteNCharLiteral(String val) throws SQLException {
		return enquoteLiteral(val);
	}

}

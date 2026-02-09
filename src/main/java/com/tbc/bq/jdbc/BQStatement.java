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
package com.tbc.bq.jdbc;

import com.google.cloud.bigquery.QueryJobConfiguration;
import com.tbc.bq.jdbc.base.AbstractBQStatement;
import com.tbc.bq.jdbc.exception.BQSQLFeatureNotSupportedException;
import com.tbc.bq.jdbc.util.ErrorMessages;
import com.tbc.bq.jdbc.util.UnsupportedOperations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLWarning;

/**
 * JDBC Statement implementation for BigQuery.
 *
 * @since 1.0.0
 */
public class BQStatement extends AbstractBQStatement {

	private static final Logger logger = LoggerFactory.getLogger(BQStatement.class);

	/** Fetch size for result pagination. 0 means use connection default. */
	private int fetchSize = 0;

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
	 * Executes the given SQL DML statement (INSERT, UPDATE, DELETE, MERGE).
	 *
	 * <p>
	 * The SQL is submitted to BigQuery as a query job. This method blocks until the
	 * DML statement completes or the query timeout is reached.
	 *
	 * <p>
	 * <b>Return Value:</b> This method always returns 0 because BigQuery does not
	 * provide row counts in the standard JDBC way. To determine the number of
	 * affected rows, query the DML statistics from the job metadata or use
	 * BigQuery's @@row_count session variable (requires session support).
	 *
	 * <p>
	 * <b>Usage Example:</b>
	 * 
	 * <pre>{@code
	 * stmt.executeUpdate("INSERT INTO dataset.table (id, name) VALUES (1, 'Alice')");
	 * stmt.executeUpdate("UPDATE dataset.table SET name = 'Bob' WHERE id = 1");
	 * stmt.executeUpdate("DELETE FROM dataset.table WHERE id = 1");
	 * }</pre>
	 *
	 * @param sql
	 *            the SQL DML statement to execute
	 * @return always returns 0 (BigQuery limitation)
	 * @throws SQLException
	 *             if the statement is closed, the SQL is invalid, or execution
	 *             fails
	 */
	@Override
	@SuppressWarnings("resource") // ResultSet managed by statement, closed in statement.close()
	public int executeUpdate(String sql) throws SQLException {
		checkClosed();
		// Execute as DML
		executeQuery(sql);
		// BigQuery doesn't return update counts in the same way
		return 0;
	}

	@Override
	public SQLWarning getWarnings() throws SQLException {
		checkClosed();
		return null;
	}

	@Override
	public void clearWarnings() throws SQLException {
		checkClosed();
	}

	@Override
	public void setCursorName(String name) throws SQLException {
		throw UnsupportedOperations.namedCursors();
	}

	/**
	 * Executes the given SQL statement, which may return multiple types of results.
	 *
	 * <p>
	 * <b>Return Value:</b> This method always returns {@code true} because BigQuery
	 * queries always produce a ResultSet, even for DML statements (which return an
	 * empty result set). Use {@link #getResultSet()} to retrieve the ResultSet
	 * after calling this method.
	 *
	 * <p>
	 * The SQL is submitted to BigQuery as a query job. This method blocks until the
	 * query completes or the query timeout is reached.
	 *
	 * @param sql
	 *            the SQL statement to execute
	 * @return always {@code true} indicating a ResultSet is available
	 * @throws SQLException
	 *             if the statement is closed, the SQL is invalid, or execution
	 *             fails
	 */
	@Override
	@SuppressWarnings("resource") // ResultSet managed by statement, closed in statement.close()
	public boolean execute(String sql) throws SQLException {
		executeQuery(sql);
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
		return fetchSize > 0 ? fetchSize : properties.pageSize();
	}

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

	@Override
	public void addBatch(String sql) throws SQLException {
		throw UnsupportedOperations.batchUpdates();
	}

	@Override
	public void clearBatch() throws SQLException {
		throw UnsupportedOperations.batchUpdates();
	}

	@Override
	public int[] executeBatch() throws SQLException {
		throw UnsupportedOperations.batchUpdates();
	}

	@Override
	public boolean getMoreResults(int current) throws SQLException {
		checkClosed();
		return false;
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

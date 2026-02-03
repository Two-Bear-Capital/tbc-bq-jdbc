/*
 * Copyright 2026 Two Bear Capital
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.twobearcapital.bigquery.jdbc;

import com.google.cloud.bigquery.*;
import java.sql.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JDBC Statement implementation for BigQuery.
 *
 * @since 1.0.0
 */
public class BQStatement implements Statement {

  private static final Logger logger = LoggerFactory.getLogger(BQStatement.class);

  protected final BQConnection connection;
  protected final BigQuery bigquery;
  protected final ConnectionProperties properties;
  protected volatile Job currentJob;
  protected int queryTimeout = 0;
  protected int maxRows = 0;
  protected boolean closed = false;
  protected ResultSet currentResultSet;

  public BQStatement(BQConnection connection) {
    this.connection = connection;
    this.bigquery = connection.getBigQuery();
    this.properties = connection.getProperties();
    connection.registerStatement(this);
  }

  protected void checkClosed() throws SQLException {
    if (closed) {
      throw new BQSQLException("Statement is closed", BQSQLException.SQLSTATE_CONNECTION_CLOSED);
    }
  }

  @Override
  public ResultSet executeQuery(String sql) throws SQLException {
    checkClosed();
    logger.debug("Executing query: {}", sql);

    QueryJobConfiguration.Builder configBuilder =
        QueryJobConfiguration.newBuilder(sql).setUseLegacySql(properties.useLegacySql());

    // Set default dataset if configured
    if (properties.getDatasetId() != null) {
      configBuilder.setDefaultDataset(properties.getDatasetId());
    }

    // Set labels
    if (!properties.labels().isEmpty()) {
      configBuilder.setLabels(properties.labels());
    }

    QueryJobConfiguration queryConfig = configBuilder.build();

    try {
      Job job = bigquery.create(JobInfo.of(queryConfig));
      this.currentJob = job;

      logger.info("Query job created: {}", job.getJobId());

      // Wait for job completion with timeout
      long timeoutSeconds = queryTimeout > 0 ? queryTimeout : properties.timeoutSeconds();
      long waitTime = 0;
      while (!job.isDone() && waitTime < timeoutSeconds) {
        Thread.sleep(500);
        job = job.reload();
        waitTime += 1;
      }
      if (!job.isDone()) {
        throw new SQLException("Query timeout exceeded");
      }

      if (job == null) {
        throw new SQLException("Job no longer exists");
      }

      JobStatus status = job.getStatus();
      if (status.getError() != null) {
        BigQueryError error = status.getError();
        throw new BQSQLException(
            "Query failed (job: " + job.getJobId() + "): " + error.getMessage(),
            BQSQLException.SQLSTATE_SYNTAX_ERROR);
      }

      TableResult result = job.getQueryResults();
      currentResultSet = new BQResultSet(this, result);
      return currentResultSet;

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new BQSQLException("Query interrupted", e);
    } catch (BigQueryException e) {
      throw new BQSQLException("Query execution failed: " + e.getMessage(), e);
    }
  }

  @Override
  public int executeUpdate(String sql) throws SQLException {
    checkClosed();
    // Execute as DML
    executeQuery(sql);
    // BigQuery doesn't return update counts in the same way
    return 0;
  }

  @Override
  public void close() throws SQLException {
    if (closed) {
      return;
    }
    closed = true;
    connection.unregisterStatement(this);
    if (currentResultSet != null) {
      currentResultSet.close();
    }
  }

  @Override
  public int getMaxFieldSize() throws SQLException {
    checkClosed();
    return 0;
  }

  @Override
  public void setMaxFieldSize(int max) throws SQLException {
    checkClosed();
  }

  @Override
  public int getMaxRows() throws SQLException {
    checkClosed();
    return maxRows;
  }

  @Override
  public void setMaxRows(int max) throws SQLException {
    checkClosed();
    this.maxRows = max;
  }

  @Override
  public void setEscapeProcessing(boolean enable) throws SQLException {
    checkClosed();
  }

  @Override
  public int getQueryTimeout() throws SQLException {
    checkClosed();
    return queryTimeout;
  }

  @Override
  public void setQueryTimeout(int seconds) throws SQLException {
    checkClosed();
    this.queryTimeout = seconds;
  }

  @Override
  public void cancel() throws SQLException {
    if (currentJob != null) {
      try {
        bigquery.cancel(currentJob.getJobId());
        logger.info("Query job cancelled: {}", currentJob.getJobId());
      } catch (BigQueryException e) {
        throw new BQSQLException("Failed to cancel query", e);
      }
    }
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
    throw new BQSQLFeatureNotSupportedException("Named cursors not supported");
  }

  @Override
  public boolean execute(String sql) throws SQLException {
    executeQuery(sql);
    return true;
  }

  @Override
  public ResultSet getResultSet() throws SQLException {
    checkClosed();
    return currentResultSet;
  }

  @Override
  public int getUpdateCount() throws SQLException {
    checkClosed();
    return -1;
  }

  @Override
  public boolean getMoreResults() throws SQLException {
    checkClosed();
    return false;
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
  }

  @Override
  public int getFetchSize() throws SQLException {
    checkClosed();
    return properties.pageSize();
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
    throw new BQSQLFeatureNotSupportedException("Batch updates not supported");
  }

  @Override
  public void clearBatch() throws SQLException {
    throw new BQSQLFeatureNotSupportedException("Batch updates not supported");
  }

  @Override
  public int[] executeBatch() throws SQLException {
    throw new BQSQLFeatureNotSupportedException("Batch updates not supported");
  }

  @Override
  public java.sql.Connection getConnection() throws SQLException {
    checkClosed();
    return connection;
  }

  @Override
  public boolean getMoreResults(int current) throws SQLException {
    checkClosed();
    return false;
  }

  @Override
  public ResultSet getGeneratedKeys() throws SQLException {
    throw new BQSQLFeatureNotSupportedException("Generated keys not supported");
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
  public boolean isClosed() {
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

  @Override
  public <T> T unwrap(Class<T> iface) throws SQLException {
    if (iface.isInstance(this)) {
      return iface.cast(this);
    }
    throw new SQLException("Cannot unwrap to " + iface.getName());
  }

  @Override
  public boolean isWrapperFor(Class<?> iface) {
    return iface.isInstance(this);
  }
}

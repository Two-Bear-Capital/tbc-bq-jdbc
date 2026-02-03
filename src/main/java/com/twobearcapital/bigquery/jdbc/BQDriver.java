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

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.logging.Logger;
import org.slf4j.LoggerFactory;

/**
 * JDBC driver for Google BigQuery.
 *
 * <p>URL format: {@code jdbc:bigquery:[project]/[dataset]?property1=value1&property2=value2}
 *
 * <p>Example:
 *
 * <pre>{@code
 * String url = "jdbc:bigquery:my-project/my_dataset?authType=ADC";
 * Connection conn = DriverManager.getConnection(url);
 * }</pre>
 *
 * @since 1.0.0
 */
public final class BQDriver implements Driver {

  private static final org.slf4j.Logger logger = LoggerFactory.getLogger(BQDriver.class);
  private static final String URL_PREFIX = "jdbc:bigquery:";
  private static final int MAJOR_VERSION = 1;
  private static final int MINOR_VERSION = 0;

  static {
    try {
      DriverManager.registerDriver(new BQDriver());
      logger.info("BigQuery JDBC Driver registered (version {}.{})", MAJOR_VERSION, MINOR_VERSION);
    } catch (SQLException e) {
      logger.error("Failed to register BigQuery JDBC Driver", e);
      throw new RuntimeException("Failed to register BigQuery JDBC Driver", e);
    }
  }

  /** Default constructor. */
  public BQDriver() {
    // Required for ServiceLoader
  }

  @Override
  public Connection connect(String url, Properties info) throws SQLException {
    if (!acceptsURL(url)) {
      return null;
    }

    logger.debug("Connecting to BigQuery with URL: {}", url);

    try {
      ConnectionProperties properties = ConnectionUrlParser.parse(url, info);
      return new BQConnection(properties);
    } catch (SQLException e) {
      logger.error("Failed to create BigQuery connection", e);
      throw e;
    }
  }

  @Override
  public boolean acceptsURL(String url) {
    return url != null && url.startsWith(URL_PREFIX);
  }

  @Override
  public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
    return new DriverPropertyInfo[0];
  }

  @Override
  public int getMajorVersion() {
    return MAJOR_VERSION;
  }

  @Override
  public int getMinorVersion() {
    return MINOR_VERSION;
  }

  @Override
  public boolean jdbcCompliant() {
    // BigQuery has limitations that prevent full JDBC compliance:
    // - No traditional transaction support outside of sessions
    // - Limited DML operations
    // - No UPDATE/DELETE with traditional syntax (requires DML)
    // - No stored procedures
    // - No savepoints
    return false;
  }

  @Override
  public Logger getParentLogger() throws SQLFeatureNotSupportedException {
    throw new SQLFeatureNotSupportedException("getParentLogger not supported");
  }
}

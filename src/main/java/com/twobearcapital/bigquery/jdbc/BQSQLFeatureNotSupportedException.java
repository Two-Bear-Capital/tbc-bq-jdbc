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

import java.sql.SQLFeatureNotSupportedException;

/**
 * Exception thrown when a JDBC feature is not supported by BigQuery.
 *
 * @since 1.0.0
 */
public class BQSQLFeatureNotSupportedException extends SQLFeatureNotSupportedException {

  public BQSQLFeatureNotSupportedException(String reason) {
    super(reason, BQSQLException.SQLSTATE_FEATURE_NOT_SUPPORTED);
  }

  public BQSQLFeatureNotSupportedException(String reason, Throwable cause) {
    super(reason, BQSQLException.SQLSTATE_FEATURE_NOT_SUPPORTED, cause);
  }
}

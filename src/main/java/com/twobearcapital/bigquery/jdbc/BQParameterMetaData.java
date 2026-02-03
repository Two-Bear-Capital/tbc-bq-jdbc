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

import java.sql.ParameterMetaData;
import java.sql.SQLException;
import java.sql.Types;

/**
 * JDBC ParameterMetaData implementation for BigQuery prepared statements.
 *
 * <p>This implementation provides basic parameter metadata. Full type information is limited
 * because BigQuery parameter types are inferred at query execution time.
 *
 * @since 1.0.0
 */
public final class BQParameterMetaData implements ParameterMetaData {

  private final int parameterCount;

  /**
   * Creates parameter metadata.
   *
   * @param parameterCount the number of parameters
   */
  public BQParameterMetaData(int parameterCount) {
    this.parameterCount = parameterCount;
  }

  @Override
  public int getParameterCount() throws SQLException {
    return parameterCount;
  }

  @Override
  public int isNullable(int param) throws SQLException {
    checkParameterIndex(param);
    return ParameterMetaData.parameterNullableUnknown;
  }

  @Override
  public boolean isSigned(int param) throws SQLException {
    checkParameterIndex(param);
    return false; // Unknown at compile time
  }

  @Override
  public int getPrecision(int param) throws SQLException {
    checkParameterIndex(param);
    return 0; // Unknown at compile time
  }

  @Override
  public int getScale(int param) throws SQLException {
    checkParameterIndex(param);
    return 0; // Unknown at compile time
  }

  @Override
  public int getParameterType(int param) throws SQLException {
    checkParameterIndex(param);
    return Types.OTHER; // Type is inferred by BigQuery at execution time
  }

  @Override
  public String getParameterTypeName(int param) throws SQLException {
    checkParameterIndex(param);
    return "OTHER"; // Type is inferred by BigQuery at execution time
  }

  @Override
  public String getParameterClassName(int param) throws SQLException {
    checkParameterIndex(param);
    return Object.class.getName();
  }

  @Override
  public int getParameterMode(int param) throws SQLException {
    checkParameterIndex(param);
    return ParameterMetaData.parameterModeIn;
  }

  private void checkParameterIndex(int param) throws SQLException {
    if (param < 1 || param > parameterCount) {
      throw new SQLException(
          "Invalid parameter index: " + param + ". Valid range: 1 to " + parameterCount);
    }
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

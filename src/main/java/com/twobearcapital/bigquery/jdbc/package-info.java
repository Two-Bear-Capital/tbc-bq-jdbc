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

/**
 * Modern JDBC driver for Google BigQuery.
 *
 * <p>This package provides a JDBC 4.3 compliant driver for connecting to and querying Google
 * BigQuery.
 *
 * <h2>Quick Start</h2>
 *
 * <pre>{@code
 * String url = "jdbc:bigquery:my-project/my_dataset?authType=ADC";
 *
 * try (Connection conn = DriverManager.getConnection(url);
 *      Statement stmt = conn.createStatement();
 *      ResultSet rs = stmt.executeQuery("SELECT name, count FROM my_table")) {
 *
 *     while (rs.next()) {
 *         System.out.println(rs.getString("name") + ": " + rs.getLong("count"));
 *     }
 * }
 * }</pre>
 *
 * <h2>URL Format</h2>
 *
 * <p>{@code jdbc:bigquery:[project]/[dataset]?property1=value1&property2=value2}
 *
 * <h2>Supported Authentication Types</h2>
 *
 * <ul>
 *   <li>SERVICE_ACCOUNT - Service account JSON key file
 *   <li>ADC - Application Default Credentials
 *   <li>USER_OAUTH - User OAuth credentials
 *   <li>WORKFORCE - Workforce Identity Federation
 *   <li>WORKLOAD - Workload Identity Federation
 * </ul>
 *
 * @since 1.0.0
 */
package com.twobearcapital.bigquery.jdbc;

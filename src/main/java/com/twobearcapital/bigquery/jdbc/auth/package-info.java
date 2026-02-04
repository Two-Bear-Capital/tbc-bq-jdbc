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
 * Authentication types and credentials handling for BigQuery JDBC connections.
 *
 * <p>
 * This package provides implementations for different authentication methods
 * supported by the BigQuery JDBC driver:
 * <ul>
 * <li>{@link com.twobearcapital.bigquery.jdbc.auth.ApplicationDefaultAuth} -
 * Application Default Credentials
 * <li>{@link com.twobearcapital.bigquery.jdbc.auth.ServiceAccountAuth} -
 * Service Account authentication
 * <li>{@link com.twobearcapital.bigquery.jdbc.auth.UserOAuthAuth} - User OAuth
 * authentication
 * <li>{@link com.twobearcapital.bigquery.jdbc.auth.WorkforceIdentityAuth} -
 * Workforce Identity Federation
 * <li>{@link com.twobearcapital.bigquery.jdbc.auth.WorkloadIdentityAuth} -
 * Workload Identity Federation
 * </ul>
 *
 * @since 1.0.0
 */
package com.twobearcapital.bigquery.jdbc.auth;

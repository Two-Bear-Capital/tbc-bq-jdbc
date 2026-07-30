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
/**
 * How the driver's HTTP requests reach Google.
 *
 * <p>
 * {@link vc.tbc.bq.jdbc.transport.ProxyConfig} is the settings, resolved once
 * per connection from the {@code proxy*} properties or the JVM's own
 * {@code https.proxy*} ones. {@link vc.tbc.bq.jdbc.transport.DriverTransports}
 * turns it into the single transport factory that both the BigQuery client and
 * every credential in {@link vc.tbc.bq.jdbc.auth} are built on — the two must
 * not diverge, because a credential refreshed over the wrong route fails before
 * a query is ever sent.
 *
 * <p>
 * Only the REST path is covered. The Storage Read API is gRPC and follows the
 * JVM's {@code https.proxyHost} on its own, which is why
 * {@link vc.tbc.bq.jdbc.transport.ProxyConfig} reads those properties too
 * rather than inventing a second way to say the same thing.
 *
 * @since 4.3.0
 */
package vc.tbc.bq.jdbc.transport;

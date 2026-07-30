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

import javax.naming.Context;
import javax.naming.Name;
import javax.naming.RefAddr;
import javax.naming.Reference;
import javax.naming.spi.ObjectFactory;

import java.util.Hashtable;

/**
 * Rebuilds a {@link BQDataSource} from the JNDI reference it was bound as.
 *
 * <p>
 * An application server stores a bound data source as a {@link Reference} — a
 * list of string addresses plus the name of the factory that can turn them back
 * into an object. {@link BQDataSource#getReference()} names this class, so a
 * lookup returns a configured data source rather than the reference.
 *
 * <p>
 * Servers that configure a resource declaratively rather than by binding a live
 * bean (Tomcat's {@code <Resource factory="...">}, for instance) can name this
 * class directly; every attribute is passed through as a connection property,
 * with {@code url} handled separately.
 *
 * <p>
 * An address whose type this driver does not recognise is passed to the
 * property bag rather than rejected: containers add their own bookkeeping
 * addresses, and the parser already ignores names it does not read.
 *
 * @since 4.2.0
 */
public class BQDataSourceFactory implements ObjectFactory {

	/**
	 * Addresses a container adds for its own bookkeeping, which are not connection
	 * properties.
	 */
	private static final java.util.Set<String> IGNORED = java.util.Set.of("factory", "scope", "auth", "singleton",
			"description");

	/** Default constructor, required by JNDI. */
	public BQDataSourceFactory() {
		// Instantiated reflectively by the naming provider
	}

	// Hashtable is the parameter type javax.naming.spi.ObjectFactory declares.
	// A Map here would not override the interface method, so JNDI would never
	// call it.
	@Override
	@SuppressWarnings("PMD.ReplaceHashtableWithMap")
	public Object getObjectInstance(Object obj, Name name, Context nameCtx, Hashtable<?, ?> environment) {
		if (!(obj instanceof Reference reference)) {
			// Contract: returning null lets the naming provider try another factory
			return null;
		}
		String className = reference.getClassName();
		if (className != null && !className.equals(BQDataSource.class.getName())
				&& !className.equals(javax.sql.DataSource.class.getName())) {
			return null;
		}

		BQDataSource dataSource = new BQDataSource();
		for (RefAddr addr : java.util.Collections.list(reference.getAll())) {
			if (IGNORED.contains(addr.getType()) || !(addr.getContent() instanceof String value)) {
				continue;
			}
			if ("url".equals(addr.getType())) {
				dataSource.setUrl(value);
			} else {
				dataSource.setProperty(addr.getType(), value);
			}
		}
		return dataSource;
	}
}

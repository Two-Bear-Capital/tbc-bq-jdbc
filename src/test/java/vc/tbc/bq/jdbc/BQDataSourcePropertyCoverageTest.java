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

import org.junit.jupiter.api.Test;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards that {@link BQDataSource} exposes a JavaBean property for every
 * connection property the driver advertises.
 *
 * <p>
 * This is the answer to "keep the setters in step with the property list by
 * hand, or generate them". They are written by hand — a generated bean would be
 * a second source of truth for the property list, and the setters are one line
 * each — and this test is what makes by-hand safe: adding a property to
 * {@code Driver.getPropertyInfo()} without a setter fails here rather than
 * being noticed by a Spring user who cannot configure it.
 *
 * <p>
 * It reads the bean through {@link Introspector}, which is what a container
 * populating the bean uses, so a setter this test finds is a setter Spring and
 * an application server can also find.
 *
 * @since 4.2.0
 */
class BQDataSourcePropertyCoverageTest {

	/**
	 * Bean properties that are not connection properties: JDBC's own, and the data
	 * source's URL and untyped escape hatch.
	 */
	private static final Set<String> NOT_CONNECTION_PROPERTIES = Set.of("url", "loginTimeout", "logWriter",
			"parentLogger", "connection", "properties", "property", "reference", "class");

	/** Bean property names {@link BQDataSource} exposes, by name. */
	private static Map<String, PropertyDescriptor> beanProperties() throws IntrospectionException {
		BeanInfo info = Introspector.getBeanInfo(BQDataSource.class, Object.class);
		Map<String, PropertyDescriptor> byName = new TreeMap<>();
		for (PropertyDescriptor pd : info.getPropertyDescriptors()) {
			byName.put(pd.getName(), pd);
		}
		return byName;
	}

	/** Names the driver advertises, plus projectId, which the URL supplies. */
	private static Set<String> advertisedProperties() throws SQLException {
		Set<String> names = Arrays.stream(new BQDriver().getPropertyInfo(null, null)).map(p -> p.name)
				.collect(Collectors.toCollection(TreeSet::new));
		// Advertised as the URL path rather than as a property, but a DataSource has
		// no URL path, so it is the one setting the bean must add
		names.add("projectId");
		return names;
	}

	@Test
	void everyAdvertisedPropertyHasASetter() throws SQLException, IntrospectionException {
		Map<String, PropertyDescriptor> bean = beanProperties();

		Set<String> missing = new TreeSet<>();
		for (String name : advertisedProperties()) {
			PropertyDescriptor pd = bean.get(name);
			if (pd == null || pd.getWriteMethod() == null) {
				missing.add(name);
			}
		}

		assertTrue(missing.isEmpty(), "These properties are advertised by Driver.getPropertyInfo() but have no "
				+ "BQDataSource setter, so a Spring or JNDI user cannot configure them: " + missing);
	}

	@Test
	void everyAdvertisedPropertyHasAGetter() throws SQLException, IntrospectionException {
		Map<String, PropertyDescriptor> bean = beanProperties();

		Set<String> missing = new TreeSet<>();
		for (String name : advertisedProperties()) {
			PropertyDescriptor pd = bean.get(name);
			if (pd == null || pd.getReadMethod() == null) {
				missing.add(name);
			}
		}

		assertTrue(missing.isEmpty(),
				"These advertised properties have no BQDataSource getter, so a container cannot read back "
						+ "what it set: " + missing);
	}

	@Test
	void noBeanPropertyIsAbsentFromTheAdvertisedList() throws SQLException, IntrospectionException {
		Set<String> advertised = advertisedProperties();

		Set<String> unknown = new TreeSet<>();
		for (String name : beanProperties().keySet()) {
			if (!advertised.contains(name) && !NOT_CONNECTION_PROPERTIES.contains(name)) {
				unknown.add(name);
			}
		}

		// The other direction: a setter writing a name the parser never reads is
		// configuration that silently does nothing
		assertTrue(unknown.isEmpty(), "These BQDataSource properties are not advertised by "
				+ "Driver.getPropertyInfo(), so they may write a key the parser never reads: " + unknown);
	}

	@Test
	void aSetterWritesThePropertyNamedAfterIt() throws Exception {
		// The setters are one line each and the mapping is by name; this proves the
		// name a setter writes is its own, so the two tests above are meaningful
		Map<String, PropertyDescriptor> bean = beanProperties();
		for (String name : advertisedProperties()) {
			PropertyDescriptor pd = bean.get(name);
			Object value = sampleValueFor(pd.getPropertyType());
			BQDataSource ds = new BQDataSource();
			pd.getWriteMethod().invoke(ds, value);
			assertEquals(String.valueOf(value), ds.getProperty(name),
					"setter " + pd.getWriteMethod().getName() + " did not write the property named " + name);
		}
	}

	@Test
	void theIntrospectionFindsTheWholeBean() throws SQLException, IntrospectionException {
		// Guards the tests above from passing vacuously if introspection breaks
		assertTrue(beanProperties().size() > 30, "expected BQDataSource to expose many bean properties");
		assertTrue(advertisedProperties().size() > 30, "expected the driver to advertise many properties");
	}

	/** A value the setter for this type will accept. */
	private static Object sampleValueFor(Class<?> type) {
		if (type == String.class) {
			// Not a value with meaning; only its round trip through the bag is asserted
			return "sample";
		}
		if (type == Integer.class) {
			return 7;
		}
		if (type == Long.class) {
			return 7L;
		}
		if (type == Boolean.class) {
			return Boolean.TRUE;
		}
		if (type == java.math.BigDecimal.class) {
			return new java.math.BigDecimal("1.5");
		}
		throw new AssertionError("No sample value for bean property type " + type.getName()
				+ "; add one so the property is covered rather than skipped");
	}
}

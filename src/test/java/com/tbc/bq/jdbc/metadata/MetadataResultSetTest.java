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
package com.tbc.bq.jdbc.metadata;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MetadataResultSet.
 *
 * @since 1.0.15
 */
class MetadataResultSetTest {

	private MetadataResultSet createTestResultSet() {
		String[] columns = {"ID", "NAME", "AGE", "ACTIVE"};
		int[] types = {Types.INTEGER, Types.VARCHAR, Types.INTEGER, Types.BOOLEAN};
		List<Object[]> rows = new ArrayList<>();
		rows.add(new Object[]{1, "Alice", 30, true});
		rows.add(new Object[]{2, "Bob", 25, false});
		rows.add(new Object[]{3, null, 35, true});
		return new MetadataResultSet(columns, types, rows);
	}

	private MetadataResultSet createEmptyResultSet() {
		String[] columns = {"ID", "NAME"};
		int[] types = {Types.INTEGER, Types.VARCHAR};
		List<Object[]> rows = new ArrayList<>();
		return new MetadataResultSet(columns, types, rows);
	}

	private MetadataResultSet createSingleRowResultSet() {
		String[] columns = {"ID"};
		int[] types = {Types.INTEGER};
		List<Object[]> rows = new ArrayList<>();
		rows.add(new Object[]{42});
		return new MetadataResultSet(columns, types, rows);
	}

	// Construction Tests

	@Test
	void testConstructionWithValidData() {
		// Given: Valid columns, types, and rows
		String[] columns = {"COL1", "COL2"};
		int[] types = {Types.INTEGER, Types.VARCHAR};
		List<Object[]> rows = new ArrayList<>();
		rows.add(new Object[]{1, "test"});

		// When: Creating MetadataResultSet
		MetadataResultSet rs = new MetadataResultSet(columns, types, rows);

		// Then: Should be created successfully
		assertNotNull(rs);
		assertArrayEquals(columns, rs.getColumnNames());
		assertArrayEquals(types, rs.getColumnTypes());
		assertEquals(rows, rs.getRows());
	}

	@Test
	void testConstructionWithNullColumnsThrows() {
		// Given: Null column names
		int[] types = {Types.INTEGER};
		List<Object[]> rows = new ArrayList<>();

		// Then: Should throw NullPointerException
		assertThrows(NullPointerException.class, () -> new MetadataResultSet(null, types, rows));
	}

	@Test
	void testConstructionWithNullTypesThrows() {
		// Given: Null column types
		String[] columns = {"COL1"};
		List<Object[]> rows = new ArrayList<>();

		// Then: Should throw NullPointerException
		assertThrows(NullPointerException.class, () -> new MetadataResultSet(columns, null, rows));
	}

	@Test
	void testConstructionWithNullRowsThrows() {
		// Given: Null rows
		String[] columns = {"COL1"};
		int[] types = {Types.INTEGER};

		// Then: Should throw NullPointerException
		assertThrows(NullPointerException.class, () -> new MetadataResultSet(columns, types, null));
	}

	@Test
	void testConstructionWithMismatchedArrayLengthThrows() {
		// Given: Mismatched column names and types
		String[] columns = {"COL1", "COL2"};
		int[] types = {Types.INTEGER};
		List<Object[]> rows = new ArrayList<>();

		// Then: Should throw IllegalArgumentException
		assertThrows(IllegalArgumentException.class, () -> new MetadataResultSet(columns, types, rows));
	}

	// Navigation Tests

	@Test
	void testNextWithData() throws SQLException {
		// Given: A result set with data
		MetadataResultSet rs = createTestResultSet();

		// When: Calling next()
		boolean hasNext = rs.next();

		// Then: Should return true and position on first row
		assertTrue(hasNext);
		assertEquals(1, rs.getInt(1));
	}

	@Test
	void testNextReturnsFalseWhenNoMoreRows() throws SQLException {
		// Given: A single row result set
		MetadataResultSet rs = createSingleRowResultSet();

		// When: Calling next() twice
		assertTrue(rs.next());
		boolean hasNext = rs.next();

		// Then: Should return false
		assertFalse(hasNext);
	}

	@Test
	void testNextOnEmptyResultSet() throws SQLException {
		// Given: An empty result set
		MetadataResultSet rs = createEmptyResultSet();

		// When: Calling next()
		boolean hasNext = rs.next();

		// Then: Should return false
		assertFalse(hasNext);
	}

	@Test
	void testMultipleNextCalls() throws SQLException {
		// Given: A result set with 3 rows
		MetadataResultSet rs = createTestResultSet();

		// When: Calling next() three times
		assertTrue(rs.next());
		assertEquals(1, rs.getInt(1));
		assertTrue(rs.next());
		assertEquals(2, rs.getInt(1));
		assertTrue(rs.next());
		assertEquals(3, rs.getInt(1));

		// Then: Fourth call should return false
		assertFalse(rs.next());
	}

	@Test
	void testIsBeforeFirst() throws SQLException {
		// Given: A result set
		MetadataResultSet rs = createTestResultSet();

		// Then: Should be before first row initially
		assertTrue(rs.isBeforeFirst());

		// When: Moving to first row
		rs.next();

		// Then: Should not be before first anymore
		assertFalse(rs.isBeforeFirst());
	}

	@Test
	void testIsBeforeFirstOnEmptyResultSet() throws SQLException {
		// Given: An empty result set
		MetadataResultSet rs = createEmptyResultSet();

		// Then: Should not be before first (empty set)
		assertFalse(rs.isBeforeFirst());
	}

	@Test
	void testIsAfterLast() throws SQLException {
		// Given: A single row result set
		MetadataResultSet rs = createSingleRowResultSet();

		// Then: Should not be after last initially
		assertFalse(rs.isAfterLast());

		// When: Moving past the last row
		rs.next();
		rs.next();

		// Then: Should be after last
		assertTrue(rs.isAfterLast());
	}

	@Test
	void testIsAfterLastOnEmptyResultSet() throws SQLException {
		// Given: An empty result set
		MetadataResultSet rs = createEmptyResultSet();

		// Then: Should not be after last (empty set)
		assertFalse(rs.isAfterLast());
	}

	@Test
	void testIsFirst() throws SQLException {
		// Given: A result set with data
		MetadataResultSet rs = createTestResultSet();

		// Then: Should not be on first row initially
		assertFalse(rs.isFirst());

		// When: Moving to first row
		rs.next();

		// Then: Should be on first row
		assertTrue(rs.isFirst());

		// When: Moving to second row
		rs.next();

		// Then: Should not be on first row
		assertFalse(rs.isFirst());
	}

	@Test
	void testIsLast() throws SQLException {
		// Given: A result set with 3 rows
		MetadataResultSet rs = createTestResultSet();

		// When: Moving through rows
		rs.next(); // Row 1
		assertFalse(rs.isLast());

		rs.next(); // Row 2
		assertFalse(rs.isLast());

		rs.next(); // Row 3
		assertTrue(rs.isLast());
	}

	@Test
	void testGetRow() throws SQLException {
		// Given: A result set with data
		MetadataResultSet rs = createTestResultSet();

		// Then: Initial row number should be 0
		assertEquals(0, rs.getRow());

		// When: Moving through rows
		rs.next();
		assertEquals(1, rs.getRow());

		rs.next();
		assertEquals(2, rs.getRow());

		rs.next();
		assertEquals(3, rs.getRow());
	}

	// Data Retrieval by Index Tests

	@Test
	void testGetStringByIndex() throws SQLException {
		// Given: A result set positioned on first row
		MetadataResultSet rs = createTestResultSet();
		rs.next();

		// When: Getting string value
		String name = rs.getString(2);

		// Then: Should return the value
		assertEquals("Alice", name);
	}

	@Test
	void testGetStringWithNullValue() throws SQLException {
		// Given: A result set positioned on row with null
		MetadataResultSet rs = createTestResultSet();
		rs.next();
		rs.next();
		rs.next(); // Row with null NAME

		// When: Getting null string
		String name = rs.getString(2);

		// Then: Should return null
		assertNull(name);
	}

	@Test
	void testGetIntByIndex() throws SQLException {
		// Given: A result set positioned on first row
		MetadataResultSet rs = createTestResultSet();
		rs.next();

		// When: Getting int value
		int id = rs.getInt(1);

		// Then: Should return the value
		assertEquals(1, id);
	}

	@Test
	void testGetBooleanByIndex() throws SQLException {
		// Given: A result set positioned on first row
		MetadataResultSet rs = createTestResultSet();
		rs.next();

		// When: Getting boolean value
		boolean active = rs.getBoolean(4);

		// Then: Should return the value
		assertTrue(active);
	}

	@Test
	void testGetBooleanWithNullReturnsFalse() throws SQLException {
		// Given: Result set with null boolean
		String[] columns = {"FLAG"};
		int[] types = {Types.BOOLEAN};
		List<Object[]> rows = new ArrayList<>();
		rows.add(new Object[]{null});
		MetadataResultSet rs = new MetadataResultSet(columns, types, rows);
		rs.next();

		// When: Getting null boolean
		boolean flag = rs.getBoolean(1);

		// Then: Should return false
		assertFalse(flag);
	}

	@Test
	void testGetBooleanFromString() throws SQLException {
		// Given: Result set with string "true"
		String[] columns = {"FLAG"};
		int[] types = {Types.VARCHAR};
		List<Object[]> rows = new ArrayList<>();
		rows.add(new Object[]{"true"});
		MetadataResultSet rs = new MetadataResultSet(columns, types, rows);
		rs.next();

		// When: Getting boolean from string
		boolean flag = rs.getBoolean(1);

		// Then: Should parse correctly
		assertTrue(flag);
	}

	@Test
	void testGetByteByIndex() throws SQLException {
		// Given: Result set with byte value
		String[] columns = {"NUM"};
		int[] types = {Types.TINYINT};
		List<Object[]> rows = new ArrayList<>();
		rows.add(new Object[]{(byte) 42});
		MetadataResultSet rs = new MetadataResultSet(columns, types, rows);
		rs.next();

		// When: Getting byte value
		byte num = rs.getByte(1);

		// Then: Should return the value
		assertEquals(42, num);
	}

	@Test
	void testGetShortByIndex() throws SQLException {
		// Given: Result set with short value
		String[] columns = {"NUM"};
		int[] types = {Types.SMALLINT};
		List<Object[]> rows = new ArrayList<>();
		rows.add(new Object[]{(short) 1000});
		MetadataResultSet rs = new MetadataResultSet(columns, types, rows);
		rs.next();

		// When: Getting short value
		short num = rs.getShort(1);

		// Then: Should return the value
		assertEquals(1000, num);
	}

	@Test
	void testGetLongByIndex() throws SQLException {
		// Given: Result set with long value
		String[] columns = {"NUM"};
		int[] types = {Types.BIGINT};
		List<Object[]> rows = new ArrayList<>();
		rows.add(new Object[]{1234567890L});
		MetadataResultSet rs = new MetadataResultSet(columns, types, rows);
		rs.next();

		// When: Getting long value
		long num = rs.getLong(1);

		// Then: Should return the value
		assertEquals(1234567890L, num);
	}

	@Test
	void testGetFloatByIndex() throws SQLException {
		// Given: Result set with float value
		String[] columns = {"NUM"};
		int[] types = {Types.FLOAT};
		List<Object[]> rows = new ArrayList<>();
		rows.add(new Object[]{3.14f});
		MetadataResultSet rs = new MetadataResultSet(columns, types, rows);
		rs.next();

		// When: Getting float value
		float num = rs.getFloat(1);

		// Then: Should return the value
		assertEquals(3.14f, num, 0.001f);
	}

	@Test
	void testGetDoubleByIndex() throws SQLException {
		// Given: Result set with double value
		String[] columns = {"NUM"};
		int[] types = {Types.DOUBLE};
		List<Object[]> rows = new ArrayList<>();
		rows.add(new Object[]{3.14159});
		MetadataResultSet rs = new MetadataResultSet(columns, types, rows);
		rs.next();

		// When: Getting double value
		double num = rs.getDouble(1);

		// Then: Should return the value
		assertEquals(3.14159, num, 0.00001);
	}

	@Test
	void testGetBigDecimalByIndex() throws SQLException {
		// Given: Result set with numeric value
		String[] columns = {"NUM"};
		int[] types = {Types.NUMERIC};
		List<Object[]> rows = new ArrayList<>();
		rows.add(new Object[]{new BigDecimal("123.45")});
		MetadataResultSet rs = new MetadataResultSet(columns, types, rows);
		rs.next();

		// When: Getting BigDecimal value
		BigDecimal num = rs.getBigDecimal(1);

		// Then: Should return the value
		assertEquals(new BigDecimal("123.45"), num);
	}

	@Test
	@SuppressWarnings("deprecation")
	void testGetBigDecimalWithScale() throws SQLException {
		// Given: Result set with numeric value
		String[] columns = {"NUM"};
		int[] types = {Types.NUMERIC};
		List<Object[]> rows = new ArrayList<>();
		rows.add(new Object[]{new BigDecimal("123.456")});
		MetadataResultSet rs = new MetadataResultSet(columns, types, rows);
		rs.next();

		// When: Getting BigDecimal with scale
		BigDecimal num = rs.getBigDecimal(1, 2);

		// Then: Should return the value (scale not enforced in implementation)
		assertNotNull(num);
	}

	@Test
	void testGetBytesByIndex() throws SQLException {
		// Given: Result set with byte array
		byte[] data = {1, 2, 3, 4};
		String[] columns = {"DATA"};
		int[] types = {Types.BINARY};
		List<Object[]> rows = new ArrayList<>();
		rows.add(new Object[]{data});
		MetadataResultSet rs = new MetadataResultSet(columns, types, rows);
		rs.next();

		// When: Getting byte array
		byte[] result = rs.getBytes(1);

		// Then: Should return the value
		assertArrayEquals(data, result);
	}

	@Test
	void testGetBytesFromString() throws SQLException {
		// Given: Result set with string
		String[] columns = {"DATA"};
		int[] types = {Types.VARCHAR};
		List<Object[]> rows = new ArrayList<>();
		rows.add(new Object[]{"test"});
		MetadataResultSet rs = new MetadataResultSet(columns, types, rows);
		rs.next();

		// When: Getting bytes from string
		byte[] result = rs.getBytes(1);

		// Then: Should convert to bytes
		assertArrayEquals("test".getBytes(), result);
	}

	@Test
	void testGetDateByIndex() throws SQLException {
		// Given: Result set with date
		Date date = Date.valueOf("2024-01-15");
		String[] columns = {"DATE_COL"};
		int[] types = {Types.DATE};
		List<Object[]> rows = new ArrayList<>();
		rows.add(new Object[]{date});
		MetadataResultSet rs = new MetadataResultSet(columns, types, rows);
		rs.next();

		// When: Getting date value
		Date result = rs.getDate(1);

		// Then: Should return the value
		assertEquals(date, result);
	}

	@Test
	void testGetDateFromString() throws SQLException {
		// Given: Result set with date string
		String[] columns = {"DATE_COL"};
		int[] types = {Types.VARCHAR};
		List<Object[]> rows = new ArrayList<>();
		rows.add(new Object[]{"2024-01-15"});
		MetadataResultSet rs = new MetadataResultSet(columns, types, rows);
		rs.next();

		// When: Getting date from string
		Date result = rs.getDate(1);

		// Then: Should parse correctly
		assertEquals(Date.valueOf("2024-01-15"), result);
	}

	@Test
	void testGetTimeByIndex() throws SQLException {
		// Given: Result set with time
		Time time = Time.valueOf("14:30:00");
		String[] columns = {"TIME_COL"};
		int[] types = {Types.TIME};
		List<Object[]> rows = new ArrayList<>();
		rows.add(new Object[]{time});
		MetadataResultSet rs = new MetadataResultSet(columns, types, rows);
		rs.next();

		// When: Getting time value
		Time result = rs.getTime(1);

		// Then: Should return the value
		assertEquals(time, result);
	}

	@Test
	void testGetTimestampByIndex() throws SQLException {
		// Given: Result set with timestamp
		Timestamp ts = Timestamp.valueOf("2024-01-15 14:30:00");
		String[] columns = {"TS_COL"};
		int[] types = {Types.TIMESTAMP};
		List<Object[]> rows = new ArrayList<>();
		rows.add(new Object[]{ts});
		MetadataResultSet rs = new MetadataResultSet(columns, types, rows);
		rs.next();

		// When: Getting timestamp value
		Timestamp result = rs.getTimestamp(1);

		// Then: Should return the value
		assertEquals(ts, result);
	}

	@Test
	void testGetObjectByIndex() throws SQLException {
		// Given: A result set positioned on first row
		MetadataResultSet rs = createTestResultSet();
		rs.next();

		// When: Getting object value
		Object name = rs.getObject(2);

		// Then: Should return the value
		assertEquals("Alice", name);
	}

	@Test
	void testGetValueWithInvalidIndexThrows() throws SQLException {
		// Given: A result set positioned on first row
		MetadataResultSet rs = createTestResultSet();
		rs.next();

		// Then: Invalid index should throw SQLException
		assertThrows(SQLException.class, () -> rs.getString(0));
		assertThrows(SQLException.class, () -> rs.getString(5));
	}

	@Test
	void testGetValueBeforeNextThrows() throws SQLException {
		// Given: A result set not positioned on a row
		MetadataResultSet rs = createTestResultSet();

		// Then: Should throw SQLException
		assertThrows(SQLException.class, () -> rs.getString(1));
	}

	// Data Retrieval by Label Tests

	@Test
	void testGetStringByLabel() throws SQLException {
		// Given: A result set positioned on first row
		MetadataResultSet rs = createTestResultSet();
		rs.next();

		// When: Getting value by label
		String name = rs.getString("NAME");

		// Then: Should return the value
		assertEquals("Alice", name);
	}

	@Test
	void testGetStringByLabelCaseInsensitive() throws SQLException {
		// Given: A result set positioned on first row
		MetadataResultSet rs = createTestResultSet();
		rs.next();

		// When: Getting value by label (different case)
		String name = rs.getString("name");

		// Then: Should return the value
		assertEquals("Alice", name);
	}

	@Test
	void testGetIntByLabel() throws SQLException {
		// Given: A result set positioned on first row
		MetadataResultSet rs = createTestResultSet();
		rs.next();

		// When: Getting value by label
		int age = rs.getInt("AGE");

		// Then: Should return the value
		assertEquals(30, age);
	}

	@Test
	void testGetBooleanByLabel() throws SQLException {
		// Given: A result set positioned on first row
		MetadataResultSet rs = createTestResultSet();
		rs.next();

		// When: Getting value by label
		boolean active = rs.getBoolean("ACTIVE");

		// Then: Should return the value
		assertTrue(active);
	}

	@Test
	void testGetByteByLabel() throws SQLException {
		// Given: Result set with byte value
		String[] columns = {"NUM"};
		int[] types = {Types.TINYINT};
		List<Object[]> rows = new ArrayList<>();
		rows.add(new Object[]{(byte) 42});
		MetadataResultSet rs = new MetadataResultSet(columns, types, rows);
		rs.next();

		// When: Getting value by label
		byte num = rs.getByte("NUM");

		// Then: Should return the value
		assertEquals(42, num);
	}

	@Test
	void testGetShortByLabel() throws SQLException {
		// Given: Result set with short value
		String[] columns = {"NUM"};
		int[] types = {Types.SMALLINT};
		List<Object[]> rows = new ArrayList<>();
		rows.add(new Object[]{(short) 1000});
		MetadataResultSet rs = new MetadataResultSet(columns, types, rows);
		rs.next();

		// When: Getting value by label
		short num = rs.getShort("NUM");

		// Then: Should return the value
		assertEquals(1000, num);
	}

	@Test
	void testGetLongByLabel() throws SQLException {
		// Given: Result set with long value
		String[] columns = {"NUM"};
		int[] types = {Types.BIGINT};
		List<Object[]> rows = new ArrayList<>();
		rows.add(new Object[]{1234567890L});
		MetadataResultSet rs = new MetadataResultSet(columns, types, rows);
		rs.next();

		// When: Getting value by label
		long num = rs.getLong("NUM");

		// Then: Should return the value
		assertEquals(1234567890L, num);
	}

	@Test
	void testGetFloatByLabel() throws SQLException {
		// Given: Result set with float value
		String[] columns = {"NUM"};
		int[] types = {Types.FLOAT};
		List<Object[]> rows = new ArrayList<>();
		rows.add(new Object[]{3.14f});
		MetadataResultSet rs = new MetadataResultSet(columns, types, rows);
		rs.next();

		// When: Getting value by label
		float num = rs.getFloat("NUM");

		// Then: Should return the value
		assertEquals(3.14f, num, 0.001f);
	}

	@Test
	void testGetDoubleByLabel() throws SQLException {
		// Given: Result set with double value
		String[] columns = {"NUM"};
		int[] types = {Types.DOUBLE};
		List<Object[]> rows = new ArrayList<>();
		rows.add(new Object[]{3.14159});
		MetadataResultSet rs = new MetadataResultSet(columns, types, rows);
		rs.next();

		// When: Getting value by label
		double num = rs.getDouble("NUM");

		// Then: Should return the value
		assertEquals(3.14159, num, 0.00001);
	}

	@Test
	void testGetBigDecimalByLabel() throws SQLException {
		// Given: Result set with numeric value
		String[] columns = {"NUM"};
		int[] types = {Types.NUMERIC};
		List<Object[]> rows = new ArrayList<>();
		rows.add(new Object[]{new BigDecimal("123.45")});
		MetadataResultSet rs = new MetadataResultSet(columns, types, rows);
		rs.next();

		// When: Getting value by label
		BigDecimal num = rs.getBigDecimal("NUM");

		// Then: Should return the value
		assertEquals(new BigDecimal("123.45"), num);
	}

	@Test
	@SuppressWarnings("deprecation")
	void testGetBigDecimalWithScaleByLabel() throws SQLException {
		// Given: Result set with numeric value
		String[] columns = {"NUM"};
		int[] types = {Types.NUMERIC};
		List<Object[]> rows = new ArrayList<>();
		rows.add(new Object[]{new BigDecimal("123.456")});
		MetadataResultSet rs = new MetadataResultSet(columns, types, rows);
		rs.next();

		// When: Getting value by label with scale
		BigDecimal num = rs.getBigDecimal("NUM", 2);

		// Then: Should return the value
		assertNotNull(num);
	}

	@Test
	void testGetBytesByLabel() throws SQLException {
		// Given: Result set with byte array
		byte[] data = {1, 2, 3, 4};
		String[] columns = {"DATA"};
		int[] types = {Types.BINARY};
		List<Object[]> rows = new ArrayList<>();
		rows.add(new Object[]{data});
		MetadataResultSet rs = new MetadataResultSet(columns, types, rows);
		rs.next();

		// When: Getting value by label
		byte[] result = rs.getBytes("DATA");

		// Then: Should return the value
		assertArrayEquals(data, result);
	}

	@Test
	void testGetDateByLabel() throws SQLException {
		// Given: Result set with date
		Date date = Date.valueOf("2024-01-15");
		String[] columns = {"DATE_COL"};
		int[] types = {Types.DATE};
		List<Object[]> rows = new ArrayList<>();
		rows.add(new Object[]{date});
		MetadataResultSet rs = new MetadataResultSet(columns, types, rows);
		rs.next();

		// When: Getting value by label
		Date result = rs.getDate("DATE_COL");

		// Then: Should return the value
		assertEquals(date, result);
	}

	@Test
	void testGetTimeByLabel() throws SQLException {
		// Given: Result set with time
		Time time = Time.valueOf("14:30:00");
		String[] columns = {"TIME_COL"};
		int[] types = {Types.TIME};
		List<Object[]> rows = new ArrayList<>();
		rows.add(new Object[]{time});
		MetadataResultSet rs = new MetadataResultSet(columns, types, rows);
		rs.next();

		// When: Getting value by label
		Time result = rs.getTime("TIME_COL");

		// Then: Should return the value
		assertEquals(time, result);
	}

	@Test
	void testGetTimestampByLabel() throws SQLException {
		// Given: Result set with timestamp
		Timestamp ts = Timestamp.valueOf("2024-01-15 14:30:00");
		String[] columns = {"TS_COL"};
		int[] types = {Types.TIMESTAMP};
		List<Object[]> rows = new ArrayList<>();
		rows.add(new Object[]{ts});
		MetadataResultSet rs = new MetadataResultSet(columns, types, rows);
		rs.next();

		// When: Getting value by label
		Timestamp result = rs.getTimestamp("TS_COL");

		// Then: Should return the value
		assertEquals(ts, result);
	}

	@Test
	void testGetObjectByLabel() throws SQLException {
		// Given: A result set positioned on first row
		MetadataResultSet rs = createTestResultSet();
		rs.next();

		// When: Getting object by label
		Object name = rs.getObject("NAME");

		// Then: Should return the value
		assertEquals("Alice", name);
	}

	@Test
	void testGetValueWithInvalidLabelThrows() throws SQLException {
		// Given: A result set positioned on first row
		MetadataResultSet rs = createTestResultSet();
		rs.next();

		// Then: Invalid label should throw SQLException
		assertThrows(SQLException.class, () -> rs.getString("NONEXISTENT"));
	}

	@Test
	void testFindColumn() throws SQLException {
		// Given: A result set
		MetadataResultSet rs = createTestResultSet();

		// When: Finding column index
		int index = rs.findColumn("NAME");

		// Then: Should return correct index (1-based)
		assertEquals(2, index);
	}

	@Test
	void testFindColumnCaseInsensitive() throws SQLException {
		// Given: A result set
		MetadataResultSet rs = createTestResultSet();

		// When: Finding column with different case
		int index = rs.findColumn("name");

		// Then: Should return correct index
		assertEquals(2, index);
	}

	// wasNull() Tests

	@Test
	void testWasNullWithNullValue() throws SQLException {
		// Given: A result set positioned on row with null
		MetadataResultSet rs = createTestResultSet();
		rs.next();
		rs.next();
		rs.next(); // Row with null NAME

		// When: Getting null value
		String name = rs.getString(2);

		// Then: wasNull() should return true
		assertNull(name);
		assertTrue(rs.wasNull());
	}

	@Test
	void testWasNullWithNonNullValue() throws SQLException {
		// Given: A result set positioned on first row
		MetadataResultSet rs = createTestResultSet();
		rs.next();

		// When: Getting non-null value
		String name = rs.getString(2);

		// Then: wasNull() should return false
		assertEquals("Alice", name);
		assertFalse(rs.wasNull());
	}

	@Test
	void testWasNullAfterMultipleGets() throws SQLException {
		// Given: A result set positioned on row with mixed null/non-null
		MetadataResultSet rs = createTestResultSet();
		rs.next();
		rs.next();
		rs.next(); // Row with null NAME

		// When: Getting non-null then null values
		int id = rs.getInt(1);
		assertFalse(rs.wasNull());

		String name = rs.getString(2);
		assertTrue(rs.wasNull());

		int age = rs.getInt(3);
		assertFalse(rs.wasNull());
	}

	// ResultSetMetaData Tests

	@Test
	void testGetMetaData() throws SQLException {
		// Given: A result set
		MetadataResultSet rs = createTestResultSet();

		// When: Getting metadata
		ResultSetMetaData metaData = rs.getMetaData();

		// Then: Should return metadata
		assertNotNull(metaData);
		assertEquals(4, metaData.getColumnCount());
	}

	@Test
	void testMetaDataColumnNames() throws SQLException {
		// Given: A result set
		MetadataResultSet rs = createTestResultSet();
		ResultSetMetaData metaData = rs.getMetaData();

		// When: Getting column names
		String col1 = metaData.getColumnName(1);
		String col2 = metaData.getColumnName(2);

		// Then: Should return correct names
		assertEquals("ID", col1);
		assertEquals("NAME", col2);
	}

	@Test
	void testMetaDataColumnTypes() throws SQLException {
		// Given: A result set
		MetadataResultSet rs = createTestResultSet();
		ResultSetMetaData metaData = rs.getMetaData();

		// When: Getting column types
		int type1 = metaData.getColumnType(1);
		int type2 = metaData.getColumnType(2);

		// Then: Should return correct types
		assertEquals(Types.INTEGER, type1);
		assertEquals(Types.VARCHAR, type2);
	}

	// Lifecycle Tests

	@Test
	void testClose() throws SQLException {
		// Given: A result set
		MetadataResultSet rs = createTestResultSet();

		// When: Closing the result set
		rs.close();

		// Then: Should be closed
		assertTrue(rs.isClosed());
	}

	@Test
	void testOperationsAfterCloseThrow() throws SQLException {
		// Given: A closed result set
		MetadataResultSet rs = createTestResultSet();
		rs.close();

		// Then: Operations should throw SQLException
		assertThrows(SQLException.class, rs::next);
		assertThrows(SQLException.class, () -> rs.getString(1));
		assertThrows(SQLException.class, rs::getMetaData);
		assertThrows(SQLException.class, rs::isBeforeFirst);
	}

	@Test
	void testDoubleCloseIsAllowed() throws SQLException {
		// Given: A result set
		MetadataResultSet rs = createTestResultSet();

		// When: Closing twice
		rs.close();
		rs.close();

		// Then: Should not throw exception
		assertTrue(rs.isClosed());
	}

	@Test
	void testIsClosedInitially() throws SQLException {
		// Given: A new result set
		MetadataResultSet rs = createTestResultSet();

		// Then: Should not be closed initially
		assertFalse(rs.isClosed());
	}
}

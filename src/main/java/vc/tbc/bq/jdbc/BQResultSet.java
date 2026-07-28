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

import com.google.cloud.bigquery.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vc.tbc.bq.jdbc.base.BaseReadOnlyResultSet;
import vc.tbc.bq.jdbc.exception.BQSQLException;
import vc.tbc.bq.jdbc.exception.BQSQLFeatureNotSupportedException;
import vc.tbc.bq.jdbc.metadata.BQResultSetMetaData;
import vc.tbc.bq.jdbc.util.ErrorMessages;
import vc.tbc.bq.jdbc.util.FieldValueConverter;
import vc.tbc.bq.jdbc.util.TimezoneUtils;

import java.math.BigDecimal;
import java.sql.*;
import java.sql.Date;
import java.util.*;
import java.util.function.Supplier;

/**
 * JDBC ResultSet implementation for BigQuery.
 *
 * <p>
 * This implementation wraps a Google Cloud BigQuery {@link TableResult} and
 * provides JDBC ResultSet access to the query results.
 *
 * <p>
 * This class extends {@link BaseReadOnlyResultSet} which provides default
 * implementations for all unsupported operations. Only methods that are
 * actually supported by BigQuery are implemented here.
 *
 * @since 1.0.0
 */
public class BQResultSet extends BaseReadOnlyResultSet {

	private static final Logger logger = LoggerFactory.getLogger(BQResultSet.class);

	private final BQStatement statement;
	private final TableResult tableResult;
	private final Iterator<FieldValueList> rowIterator;
	private final FieldList schemaFields; // Cached at construction to avoid repeated schema traversal
	private final int maxRows; // Cached at construction; setMaxRows must be called before execution per JDBC
								// spec
	private final boolean nativeComplexTypes; // When true, return BQArray/BQStruct instead of JSON strings
	private FieldValueList currentRow;
	private boolean lastWasNull = false;
	private int rowCount = 0; // Track rows returned for maxRows enforcement
	private Map<String, Integer> columnIndexByName; // Lazy-initialized for O(1) findColumn()

	/**
	 * Creates a new BigQuery ResultSet.
	 *
	 * @param statement
	 *            the statement that produced this result set
	 * @param tableResult
	 *            the BigQuery table result
	 */
	@SuppressWarnings("PMD.NullAssignment") // schemaFields is null when schema is unavailable; intentional
	public BQResultSet(BQStatement statement, TableResult tableResult) {
		this.statement = statement;
		this.tableResult = tableResult;
		this.rowIterator = tableResult.iterateAll().iterator();
		Schema schema = tableResult.getSchema();
		this.schemaFields = schema != null ? schema.getFields() : null;
		this.maxRows = resolveMaxRows(statement);
		this.nativeComplexTypes = resolveNativeComplexTypes(statement);
	}

	/**
	 * Creates a new BigQuery ResultSet for metadata operations.
	 *
	 * <p>
	 * This constructor is used internally for creating result sets that represent
	 * metadata query results (e.g., from DatabaseMetaData methods).
	 *
	 * @param tableResult
	 *            the BigQuery table result
	 */
	BQResultSet(TableResult tableResult) {
		this(null, tableResult);
	}

	private static boolean resolveNativeComplexTypes(BQStatement statement) {
		if (statement == null) {
			return false;
		}
		try {
			BQConnection conn = (BQConnection) statement.getConnection();
			return conn != null && conn.getProperties().nativeComplexTypes();
		} catch (Exception ignored) {
			return false;
		}
	}

	/**
	 * Protected constructor for subclasses that override iteration logic.
	 *
	 * <p>
	 * This constructor allows subclasses like StorageReadResultSet to provide their
	 * own iteration mechanism without requiring a TableResult.
	 *
	 * @param statement
	 *            the statement that produced this result set
	 * @param tableResult
	 *            the BigQuery table result (may be null for subclasses)
	 * @param allowNullTableResult
	 *            marker parameter to distinguish this constructor
	 */
	@SuppressWarnings("PMD.NullAssignment") // rowIterator and schemaFields may be null; intentional for subclass usage
	protected BQResultSet(BQStatement statement, TableResult tableResult, boolean allowNullTableResult) {
		if (!allowNullTableResult && tableResult == null) {
			throw new IllegalArgumentException("tableResult cannot be null");
		}
		this.statement = statement;
		this.tableResult = tableResult;
		this.rowIterator = tableResult != null ? tableResult.iterateAll().iterator() : null;
		Schema schema = tableResult != null ? tableResult.getSchema() : null;
		this.schemaFields = schema != null ? schema.getFields() : null;
		this.maxRows = resolveMaxRows(statement);
		this.nativeComplexTypes = resolveNativeComplexTypes(statement);
	}

	private static int resolveMaxRows(BQStatement statement) {
		if (statement == null) {
			return 0;
		}
		try {
			return statement.getMaxRows();
		} catch (SQLException ignored) {
			return 0;
		}
	}

	@Override
	protected String getClosedErrorMessage() {
		return ErrorMessages.RESULTSET_CLOSED;
	}

	private void checkPosition() throws SQLException {
		checkClosed();
		if (currentRow == null) {
			throw new BQSQLException("ResultSet not positioned on a row");
		}
	}

	private FieldValue getFieldValue(int columnIndex) throws SQLException {
		checkPosition();
		if (columnIndex < 1 || columnIndex > currentRow.size()) {
			throw new BQSQLException("Column index out of bounds: " + columnIndex);
		}
		FieldValue value = currentRow.get(columnIndex - 1);
		lastWasNull = value.isNull();
		return value;
	}

	private Field getSchemaField(int columnIndex) {
		if (schemaFields != null && columnIndex > 0 && columnIndex <= schemaFields.size()) {
			return schemaFields.get(columnIndex - 1);
		}
		return null;
	}

	/**
	 * Converts a non-null FieldValue to a long, dispatching on the BigQuery column
	 * type.
	 *
	 * <p>
	 * FLOAT64 columns store values as decimal strings (e.g. {@code "25.0"}), which
	 * would cause {@link NumberFormatException} if passed to
	 * {@code FieldValue.getLongValue()}. NUMERIC/BIGNUMERIC similarly need
	 * BigDecimal handling. This method routes to the appropriate conversion so that
	 * {@code getInt()}/{@code getLong()}/etc. work correctly across all numeric
	 * types per the JDBC spec (truncation toward zero is expected).
	 *
	 * @param value
	 *            a non-null, non-null-attribute FieldValue
	 * @param field
	 *            the schema Field (may be null, in which case getLongValue is used)
	 * @return the integral long representation, truncated if necessary
	 */
	private static long toIntegralLong(FieldValue value, Field field) {
		if (field != null) {
			switch (field.getType().getStandardType()) {
				case FLOAT64 -> {
					return (long) value.getDoubleValue();
				}
				case NUMERIC, BIGNUMERIC -> {
					return value.getNumericValue().longValue();
				}
				default -> {
				}
			}
		}
		return value.getLongValue();
	}

	/**
	 * Produces the next row, or {@code null} once the rows are exhausted.
	 *
	 * <p>
	 * This is the seam subclasses override to supply rows from somewhere other than
	 * the REST pager — {@link vc.tbc.bq.jdbc.storage.StorageReadResultSet} decodes
	 * them from Arrow record batches. Everything that makes a row behave like a
	 * JDBC row (the {@code maxRows} limit, {@code wasNull} tracking, and every
	 * getter's type coercion) stays in this class, so an alternative row source
	 * cannot drift from the standard path's semantics.
	 *
	 * @return the next row, or null at end of rows
	 * @throws SQLException
	 *             if fetching fails
	 */
	protected FieldValueList fetchNextRow() throws SQLException {
		// Subclasses constructed with a null TableResult must supply their own
		// iteration by overriding this. Fail loudly rather than dereferencing null.
		if (rowIterator == null) {
			throw new BQSQLException(
					"This ResultSet has no row iterator: a subclass was constructed without a TableResult "
							+ "but did not override fetchNextRow()");
		}
		return rowIterator.hasNext() ? rowIterator.next() : null;
	}

	@Override
	@SuppressWarnings("PMD.NullAssignment") // null-out currentRow to indicate end-of-rows; intentional JDBC iterator
											// pattern
	public boolean next() throws SQLException {
		checkClosed();

		// Check if maxRows limit has been reached (JDBC Statement.setMaxRows)
		if (maxRows > 0 && rowCount >= maxRows) {
			currentRow = null;
			return false;
		}

		FieldValueList row = fetchNextRow();
		if (row != null) {
			currentRow = row;
			rowCount++;
			return true;
		}
		currentRow = null;
		return false;
	}

	@Override
	@SuppressWarnings("PMD.NullAssignment") // null-out currentRow on close to release reference; intentional cleanup
	protected void doClose() throws SQLException {
		currentRow = null;
		logger.debug("ResultSet closed");
	}

	@Override
	public boolean wasNull() throws SQLException {
		checkClosed();
		return lastWasNull;
	}

	@Override
	public String getString(int columnIndex) throws SQLException {
		FieldValue value = getFieldValue(columnIndex);
		return FieldValueConverter.toString(value, getSchemaField(columnIndex));
	}

	/**
	 * Runs a {@link FieldValue} accessor and reports failure as
	 * {@link SQLException}.
	 *
	 * <p>
	 * The BigQuery client signals a value it cannot interpret with unchecked
	 * exceptions — {@code IllegalStateException} when the attribute is wrong for
	 * the accessor, {@code NumberFormatException}/{@code ArithmeticException} when
	 * the text will not parse. JDBC requires getters to report failure as
	 * {@code SQLException}, so leaking those meant a caller's
	 * {@code catch (SQLException)} never fired and a conversion error surfaced as
	 * an unhandled crash (#129).
	 *
	 * @param <T>
	 *            the converted type
	 * @param columnIndex
	 *            the 1-based column, for the message
	 * @param targetType
	 *            the JDBC type being converted to, for the message
	 * @param conversion
	 *            the accessor to run
	 * @return the converted value
	 * @throws SQLException
	 *             if the conversion fails
	 */
	private <T> T convert(int columnIndex, String targetType, Supplier<T> conversion) throws SQLException {
		try {
			return conversion.get();
		} catch (IllegalStateException | NumberFormatException | ArithmeticException e) {
			throw new BQSQLException(String.format(ErrorMessages.CANNOT_CONVERT_COLUMN, columnLabelFor(columnIndex),
					targetType, e.getMessage()), BQSQLException.SQLSTATE_INVALID_PARAMETER_VALUE, e);
		}
	}

	/** Column name for an error message, falling back to the index. */
	private String columnLabelFor(int columnIndex) {
		Field field = getSchemaField(columnIndex);
		return field != null ? field.getName() : String.valueOf(columnIndex);
	}

	/**
	 * Coerces a value to boolean per the JDBC conversion table, which lists
	 * {@code getBoolean} as supported for every numeric type as well as BOOL and
	 * the character types. Non-zero is true, zero is false.
	 */
	private boolean toBoolean(FieldValue value, int columnIndex) throws SQLException {
		Field field = getSchemaField(columnIndex);
		StandardSQLTypeName type = field != null ? field.getType().getStandardType() : null;
		if (type == null) {
			return convert(columnIndex, "boolean", value::getBooleanValue);
		}
		return switch (type) {
			case BOOL -> convert(columnIndex, "boolean", value::getBooleanValue);
			case INT64 -> convert(columnIndex, "boolean", () -> value.getLongValue() != 0L);
			case FLOAT64 -> convert(columnIndex, "boolean", () -> value.getDoubleValue() != 0.0d);
			case NUMERIC, BIGNUMERIC -> convert(columnIndex, "boolean", () -> value.getNumericValue().signum() != 0);
			case STRING -> parseBooleanText(value.getStringValue(), columnIndex);
			default -> convert(columnIndex, "boolean", value::getBooleanValue);
		};
	}

	/**
	 * {@code "true"}/{@code "1"} and {@code "false"}/{@code "0"}, case-insensitive.
	 */
	private boolean parseBooleanText(String text, int columnIndex) throws SQLException {
		String trimmed = text.trim();
		if ("true".equalsIgnoreCase(trimmed) || "1".equals(trimmed)) {
			return true;
		}
		if ("false".equalsIgnoreCase(trimmed) || "0".equals(trimmed)) {
			return false;
		}
		throw new BQSQLException(
				String.format(ErrorMessages.CANNOT_CONVERT_COLUMN, columnLabelFor(columnIndex), "boolean", text),
				BQSQLException.SQLSTATE_INVALID_PARAMETER_VALUE);
	}

	@Override
	public boolean getBoolean(int columnIndex) throws SQLException {
		FieldValue value = getFieldValue(columnIndex);
		return !value.isNull() && toBoolean(value, columnIndex);
	}

	@Override
	public byte getByte(int columnIndex) throws SQLException {
		FieldValue value = getFieldValue(columnIndex);
		if (value.isNull()) {
			return 0;
		}
		long longValue = convert(columnIndex, "integer", () -> toIntegralLong(value, getSchemaField(columnIndex)));
		if (longValue < Byte.MIN_VALUE || longValue > Byte.MAX_VALUE) {
			throw new BQSQLException(String.format(ErrorMessages.VALUE_OUT_OF_RANGE, "byte", longValue),
					BQSQLException.SQLSTATE_NUMERIC_VALUE_OUT_OF_RANGE);
		}
		return (byte) longValue;
	}

	@Override
	public short getShort(int columnIndex) throws SQLException {
		FieldValue value = getFieldValue(columnIndex);
		if (value.isNull()) {
			return 0;
		}
		long longValue = convert(columnIndex, "integer", () -> toIntegralLong(value, getSchemaField(columnIndex)));
		if (longValue < Short.MIN_VALUE || longValue > Short.MAX_VALUE) {
			throw new BQSQLException(String.format(ErrorMessages.VALUE_OUT_OF_RANGE, "short", longValue),
					BQSQLException.SQLSTATE_NUMERIC_VALUE_OUT_OF_RANGE);
		}
		return (short) longValue;
	}

	@Override
	public int getInt(int columnIndex) throws SQLException {
		FieldValue value = getFieldValue(columnIndex);
		if (value.isNull()) {
			return 0;
		}
		long longValue = convert(columnIndex, "integer", () -> toIntegralLong(value, getSchemaField(columnIndex)));
		if (longValue < Integer.MIN_VALUE || longValue > Integer.MAX_VALUE) {
			throw new BQSQLException(String.format(ErrorMessages.VALUE_OUT_OF_RANGE, "int", longValue),
					BQSQLException.SQLSTATE_NUMERIC_VALUE_OUT_OF_RANGE);
		}
		return (int) longValue;
	}

	@Override
	public long getLong(int columnIndex) throws SQLException {
		FieldValue value = getFieldValue(columnIndex);
		return value.isNull()
				? 0
				: convert(columnIndex, "long", () -> toIntegralLong(value, getSchemaField(columnIndex)));
	}

	@Override
	public float getFloat(int columnIndex) throws SQLException {
		FieldValue value = getFieldValue(columnIndex);
		return value.isNull() ? 0 : convert(columnIndex, "float", () -> (float) value.getDoubleValue());
	}

	@Override
	public double getDouble(int columnIndex) throws SQLException {
		FieldValue value = getFieldValue(columnIndex);
		return value.isNull() ? 0 : convert(columnIndex, "double", value::getDoubleValue);
	}

	@Deprecated
	@Override
	public BigDecimal getBigDecimal(int columnIndex, int scale) throws SQLException {
		FieldValue value = getFieldValue(columnIndex);
		if (value.isNull()) {
			return null;
		}
		return convert(columnIndex, "BigDecimal", value::getNumericValue);
	}

	@Override
	public byte[] getBytes(int columnIndex) throws SQLException {
		FieldValue value = getFieldValue(columnIndex);
		return value.isNull() ? null : convert(columnIndex, "byte[]", value::getBytesValue);
	}

	@Override
	public Date getDate(int columnIndex) throws SQLException {
		FieldValue value = getFieldValue(columnIndex);
		if (value.isNull()) {
			return null;
		}
		String dateStr = value.getStringValue();
		return Date.valueOf(dateStr);
	}

	@Override
	public Time getTime(int columnIndex) throws SQLException {
		FieldValue value = getFieldValue(columnIndex);
		if (value.isNull()) {
			return null;
		}
		String timeStr = value.getStringValue();
		return Time.valueOf(timeStr);
	}

	@Override
	public Timestamp getTimestamp(int columnIndex) throws SQLException {
		FieldValue value = getFieldValue(columnIndex);
		if (value.isNull()) {
			return null;
		}
		long micros = value.getTimestampValue();
		return new Timestamp(micros / 1000);
	}

	@Override
	public String getString(String columnLabel) throws SQLException {
		return getString(findColumn(columnLabel));
	}

	@Override
	public boolean getBoolean(String columnLabel) throws SQLException {
		return getBoolean(findColumn(columnLabel));
	}

	@Override
	public byte getByte(String columnLabel) throws SQLException {
		return getByte(findColumn(columnLabel));
	}

	@Override
	public short getShort(String columnLabel) throws SQLException {
		return getShort(findColumn(columnLabel));
	}

	@Override
	public int getInt(String columnLabel) throws SQLException {
		return getInt(findColumn(columnLabel));
	}

	@Override
	public long getLong(String columnLabel) throws SQLException {
		return getLong(findColumn(columnLabel));
	}

	@Override
	public float getFloat(String columnLabel) throws SQLException {
		return getFloat(findColumn(columnLabel));
	}

	@Override
	public double getDouble(String columnLabel) throws SQLException {
		return getDouble(findColumn(columnLabel));
	}

	@Deprecated
	@SuppressWarnings("deprecation")
	@Override
	public BigDecimal getBigDecimal(String columnLabel, int scale) throws SQLException {
		return getBigDecimal(findColumn(columnLabel), scale);
	}

	@Override
	public byte[] getBytes(String columnLabel) throws SQLException {
		return getBytes(findColumn(columnLabel));
	}

	@Override
	public Date getDate(String columnLabel) throws SQLException {
		return getDate(findColumn(columnLabel));
	}

	@Override
	public Time getTime(String columnLabel) throws SQLException {
		return getTime(findColumn(columnLabel));
	}

	@Override
	public Timestamp getTimestamp(String columnLabel) throws SQLException {
		return getTimestamp(findColumn(columnLabel));
	}
	@Override
	public ResultSetMetaData getMetaData() throws SQLException {
		checkClosed();
		return new BQResultSetMetaData(tableResult.getSchema());
	}

	@Override
	public Object getObject(int columnIndex) throws SQLException {
		FieldValue value = getFieldValue(columnIndex);
		if (value.isNull()) {
			return null;
		}
		Field field = getSchemaField(columnIndex);
		if (nativeComplexTypes) {
			if (value.getAttribute() == FieldValue.Attribute.REPEATED) {
				return FieldValueConverter.toBQArray(value, field);
			}
			if (value.getAttribute() == FieldValue.Attribute.RECORD) {
				return FieldValueConverter.toBQStruct(value, field);
			}
		}
		return FieldValueConverter.toObject(value, field);
	}

	@Override
	public Object getObject(String columnLabel) throws SQLException {
		return getObject(findColumn(columnLabel));
	}

	@Override
	public Array getArray(int columnIndex) throws SQLException {
		if (!nativeComplexTypes) {
			throw new BQSQLFeatureNotSupportedException(
					"getArray() requires nativeComplexTypes=true connection property");
		}
		FieldValue value = getFieldValue(columnIndex);
		if (value.isNull()) {
			return null;
		}
		return FieldValueConverter.toBQArray(value, getSchemaField(columnIndex));
	}

	@Override
	public Array getArray(String columnLabel) throws SQLException {
		return getArray(findColumn(columnLabel));
	}

	@Override
	public int findColumn(String columnLabel) throws SQLException {
		checkClosed();
		if (schemaFields == null) {
			throw new BQSQLException("Schema is not available");
		}
		if (columnIndexByName == null) {
			columnIndexByName = new HashMap<>(schemaFields.size() * 2);
			for (int i = 0; i < schemaFields.size(); i++) {
				columnIndexByName.put(schemaFields.get(i).getName().toLowerCase(Locale.ROOT), i + 1);
			}
		}
		Integer index = columnIndexByName.get(columnLabel.toLowerCase(Locale.ROOT));
		if (index == null) {
			throw new BQSQLException("Column not found: " + columnLabel);
		}
		return index;
	}

	@Override
	public BigDecimal getBigDecimal(int columnIndex) throws SQLException {
		FieldValue value = getFieldValue(columnIndex);
		return value.isNull() ? null : convert(columnIndex, "BigDecimal", value::getNumericValue);
	}

	@Override
	public BigDecimal getBigDecimal(String columnLabel) throws SQLException {
		return getBigDecimal(findColumn(columnLabel));
	}

	@Override
	public boolean isBeforeFirst() throws SQLException {
		checkClosed();
		return currentRow == null && rowIterator.hasNext();
	}

	@Override
	public boolean isAfterLast() throws SQLException {
		checkClosed();
		return currentRow == null && !rowIterator.hasNext();
	}

	@Override
	public Statement getStatement() throws SQLException {
		checkClosed();
		return statement;
	}

	@Override
	public Object getObject(int columnIndex, Map<String, Class<?>> map) throws SQLException {
		return getObject(columnIndex);
	}

	@Override
	public Object getObject(String columnLabel, Map<String, Class<?>> map) throws SQLException {
		return getObject(columnLabel);
	}

	@Override
	public Date getDate(int columnIndex, Calendar cal) throws SQLException {
		Date date = getDate(columnIndex);
		return TimezoneUtils.dateFromCalendarZone(date, cal);
	}

	@Override
	public Date getDate(String columnLabel, Calendar cal) throws SQLException {
		return getDate(findColumn(columnLabel), cal);
	}

	@Override
	public Time getTime(int columnIndex, Calendar cal) throws SQLException {
		Time time = getTime(columnIndex);
		return TimezoneUtils.timeFromCalendarZone(time, cal);
	}

	@Override
	public Time getTime(String columnLabel, Calendar cal) throws SQLException {
		return getTime(findColumn(columnLabel), cal);
	}

	@Override
	public Timestamp getTimestamp(int columnIndex, Calendar cal) throws SQLException {
		Timestamp timestamp = getTimestamp(columnIndex);
		return TimezoneUtils.timestampFromCalendarZone(timestamp, cal);
	}

	@Override
	public Timestamp getTimestamp(String columnLabel, Calendar cal) throws SQLException {
		return getTimestamp(findColumn(columnLabel), cal);
	}

	@Override
	public String getNString(int columnIndex) throws SQLException {
		return getString(columnIndex);
	}

	@Override
	public String getNString(String columnLabel) throws SQLException {
		return getString(columnLabel);
	}

}

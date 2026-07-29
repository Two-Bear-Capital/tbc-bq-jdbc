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

import com.google.cloud.bigquery.FormatOptions;
import com.google.cloud.bigquery.Job;
import com.google.cloud.bigquery.JobInfo;
import com.google.cloud.bigquery.JobStatistics;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.QueryParameterValue;
import com.google.cloud.bigquery.StandardSQLTypeName;
import com.google.cloud.bigquery.TableDataWriteChannel;
import com.google.cloud.bigquery.TableId;
import com.google.cloud.bigquery.WriteChannelConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vc.tbc.bq.jdbc.base.AbstractBQPreparedStatement;
import vc.tbc.bq.jdbc.exception.BQSQLException;
import vc.tbc.bq.jdbc.metadata.BQParameterMetaData;
import vc.tbc.bq.jdbc.util.BatchInsertRewriter;
import vc.tbc.bq.jdbc.util.BatchLoadEncoder;
import vc.tbc.bq.jdbc.util.ErrorMessages;
import vc.tbc.bq.jdbc.util.ParameterConverter;
import vc.tbc.bq.jdbc.util.QueryCostEstimate;
import vc.tbc.bq.jdbc.util.StructTypeNames;
import vc.tbc.bq.jdbc.util.TimezoneUtils;

import java.math.BigDecimal;
import java.net.URL;
import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * JDBC PreparedStatement implementation for BigQuery.
 *
 * @since 1.0.0
 */
public final class BQPreparedStatement extends AbstractBQPreparedStatement {

	private static final Logger logger = LoggerFactory.getLogger(BQPreparedStatement.class);

	private final String sqlTemplate;
	private final List<QueryParameterValue> parameters = new ArrayList<>();

	/** Parameter sets accumulated via {@link #addBatch()} for batch execution. */
	private final List<List<QueryParameterValue>> batchParameterSets = new ArrayList<>();

	public BQPreparedStatement(BQConnection connection, String sql) {
		super(connection);
		this.sqlTemplate = sql;
	}

	/**
	 * Formatter for TIME parameters. {@code QueryParameterValue.time} requires
	 * exactly six fractional digits, which {@code Time.toString()} does not emit.
	 */
	private static final java.time.format.DateTimeFormatter TIME_FORMATTER = java.time.format.DateTimeFormatter
			.ofPattern("HH:mm:ss.SSSSSS");

	/**
	 * Binds a Timestamp as epoch microseconds.
	 *
	 * <p>
	 * {@code QueryParameterValue.of(instant.toString(), TIMESTAMP)} is rejected
	 * client-side: the validator wants a space-separated format, not ISO-8601's
	 * {@code T} separator. The typed factory takes microseconds and does its own
	 * formatting, which removes the question.
	 *
	 * @param value
	 *            the timestamp to bind
	 * @return a TIMESTAMP query parameter
	 */
	private static QueryParameterValue timestampParameter(Timestamp value) {
		java.time.Instant instant = value.toInstant();
		long micros = instant.getEpochSecond() * 1_000_000L + instant.getNano() / 1_000L;
		return QueryParameterValue.timestamp(micros);
	}

	private void validateParameterIndex(int parameterIndex) throws SQLException {
		if (parameterIndex < 1) {
			throw new BQSQLException(String.format(ErrorMessages.INVALID_PARAMETER_INDEX, parameterIndex),
					BQSQLException.SQLSTATE_INVALID_PARAMETER_VALUE);
		}
	}

	private void ensureCapacity(int parameterIndex) {
		while (parameters.size() < parameterIndex) {
			parameters.add(null);
		}
	}

	/**
	 * Builds a parameter value, which may reject its input.
	 *
	 * <p>
	 * A supplier rather than a value so that construction happens <em>inside</em>
	 * {@link #setParameter}: {@code QueryParameterValue}'s factories validate
	 * client-side and throw {@link IllegalArgumentException}, and that happens
	 * while building the value, before any method of this class would otherwise see
	 * it. Taking the finished value could not wrap anything.
	 */
	@FunctionalInterface
	private interface ParameterFactory {
		QueryParameterValue create() throws SQLException;
	}

	/**
	 * Builds and stores one parameter, turning a rejected value into a
	 * {@link SQLException}.
	 *
	 * <p>
	 * <b>Every parameter this statement binds goes through here.</b>
	 * {@code QueryParameterValue}'s factories validate client-side and signal a bad
	 * value with {@link IllegalArgumentException} — an unchecked exception escaping
	 * a JDBC method that declares {@code throws SQLException}, which a caller's
	 * {@code catch (SQLException)} does not catch and whose message names neither
	 * the parameter nor the driver.
	 *
	 * <p>
	 * Wrapping here rather than at each construction site is what makes the
	 * guarantee structural: a new setter cannot forget, because there is no other
	 * way to store a parameter. That also covers the values built inside struct and
	 * array binding, which are constructed within the factory rather than before
	 * it.
	 *
	 * @param parameterIndex
	 *            the 1-based parameter index
	 * @param factory
	 *            builds the value
	 * @throws SQLException
	 *             if the statement is closed, the index is invalid, or the value is
	 *             rejected
	 */
	private void setParameter(int parameterIndex, ParameterFactory factory) throws SQLException {
		checkClosed();
		validateParameterIndex(parameterIndex);
		QueryParameterValue value;
		try {
			value = factory.create();
		} catch (IllegalArgumentException e) {
			throw new BQSQLException(
					"Cannot bind parameter " + parameterIndex + ": " + e.getMessage()
							+ ". BigQuery rejected the value before the statement was sent",
					BQSQLException.SQLSTATE_INVALID_PARAMETER_VALUE, e);
		}
		ensureCapacity(parameterIndex);
		parameters.set(parameterIndex - 1, value);
	}

	@Override
	protected QueryJobConfiguration.Builder buildQueryConfig(String sql) {
		return QueryJobConfiguration.newBuilder(sql).setUseLegacySql(properties.useLegacySql())
				.setPositionalParameters(parameters);
	}

	@Override
	protected String getLogPrefix() {
		return "Prepared query";
	}

	/**
	 * Estimates what this statement would cost with its parameters as currently
	 * set, without running it.
	 *
	 * <p>
	 * The no-SQL form of
	 * {@link vc.tbc.bq.jdbc.base.AbstractBQStatement#estimateCost(String)}, since a
	 * prepared statement already knows its SQL. The dry-run binds the parameters
	 * set so far, so bind them first — BigQuery prices a query by the partitions
	 * and columns it touches, and an unbound placeholder can change both.
	 *
	 * @return the estimate, with a cost only when {@code queryPricePerTiB} is
	 *         configured
	 * @throws SQLException
	 *             if the statement is closed or BigQuery rejects the dry-run
	 * @since 3.2.0
	 */
	public QueryCostEstimate estimateCost() throws SQLException {
		return estimateCost(sqlTemplate);
	}

	@Override
	public ResultSet executeQuery() throws SQLException {
		return executeQueryInternal(sqlTemplate);
	}

	/**
	 * Executes the prepared DML statement and returns the number of affected rows,
	 * taken from BigQuery's DML job statistics ({@code numDmlAffectedRows}).
	 * Returns 0 for statements that carry no DML statistics (DDL, SELECT), per the
	 * JDBC contract for statements that return nothing.
	 */
	@Override
	public int executeUpdate() throws SQLException {
		long count = executeLargeUpdate();
		return count > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) count;
	}

	/**
	 * Sets the designated parameter to SQL NULL with explicit type information.
	 *
	 * <p>
	 * BigQuery requires explicit type information for NULL values because it cannot
	 * infer the type from NULL alone. The SQL type is mapped to the corresponding
	 * BigQuery StandardSQLTypeName.
	 *
	 * @param parameterIndex
	 *            the first parameter is 1, the second is 2, ...
	 * @param sqlType
	 *            the SQL type code from {@link java.sql.Types}
	 * @throws SQLException
	 *             if parameterIndex is invalid or the statement is closed
	 */
	@Override
	public void setNull(int parameterIndex, int sqlType) throws SQLException {
		// Use explicit type information for NULL values
		// This is critical because BigQuery cannot infer type from a NULL value
		StandardSQLTypeName bqType = TypeMapper.toStandardSQLTypeName(sqlType);
		setParameter(parameterIndex, () -> QueryParameterValue.of(null, bqType));
	}

	/**
	 * Sets the designated parameter to the given Java boolean value.
	 *
	 * <p>
	 * Maps to BigQuery BOOL type. The value is converted to a QueryParameterValue
	 * with explicit type information for optimal BigQuery compatibility.
	 *
	 * @param parameterIndex
	 *            the first parameter is 1, the second is 2, ...
	 * @param x
	 *            the parameter value
	 * @throws SQLException
	 *             if parameterIndex is invalid or the statement is closed
	 */
	@Override
	public void setBoolean(int parameterIndex, boolean x) throws SQLException {
		setParameter(parameterIndex, () -> QueryParameterValue.of(x, StandardSQLTypeName.BOOL));
	}

	/**
	 * Sets the designated parameter to the given Java byte value.
	 *
	 * <p>
	 * Maps to BigQuery INT64 type. The byte value is widened to long for BigQuery
	 * storage.
	 *
	 * @param parameterIndex
	 *            the first parameter is 1, the second is 2, ...
	 * @param x
	 *            the parameter value
	 * @throws SQLException
	 *             if parameterIndex is invalid or the statement is closed
	 */
	@Override
	public void setByte(int parameterIndex, byte x) throws SQLException {
		setParameter(parameterIndex, () -> QueryParameterValue.of((long) x, StandardSQLTypeName.INT64));
	}

	/**
	 * Sets the designated parameter to the given Java short value.
	 *
	 * <p>
	 * Maps to BigQuery INT64 type. The short value is widened to long for BigQuery
	 * storage.
	 *
	 * @param parameterIndex
	 *            the first parameter is 1, the second is 2, ...
	 * @param x
	 *            the parameter value
	 * @throws SQLException
	 *             if parameterIndex is invalid or the statement is closed
	 */
	@Override
	public void setShort(int parameterIndex, short x) throws SQLException {
		setParameter(parameterIndex, () -> QueryParameterValue.of((long) x, StandardSQLTypeName.INT64));
	}

	/**
	 * Sets the designated parameter to the given Java int value.
	 *
	 * <p>
	 * Maps to BigQuery INT64 type. The int value is widened to long for BigQuery
	 * storage.
	 *
	 * @param parameterIndex
	 *            the first parameter is 1, the second is 2, ...
	 * @param x
	 *            the parameter value
	 * @throws SQLException
	 *             if parameterIndex is invalid or the statement is closed
	 */
	@Override
	public void setInt(int parameterIndex, int x) throws SQLException {
		setParameter(parameterIndex, () -> QueryParameterValue.of((long) x, StandardSQLTypeName.INT64));
	}

	/**
	 * Sets the designated parameter to the given Java long value.
	 *
	 * <p>
	 * Maps to BigQuery INT64 type with direct conversion.
	 *
	 * @param parameterIndex
	 *            the first parameter is 1, the second is 2, ...
	 * @param x
	 *            the parameter value
	 * @throws SQLException
	 *             if parameterIndex is invalid or the statement is closed
	 */
	@Override
	public void setLong(int parameterIndex, long x) throws SQLException {
		setParameter(parameterIndex, () -> QueryParameterValue.of(x, StandardSQLTypeName.INT64));
	}

	/**
	 * Sets the designated parameter to the given Java float value.
	 *
	 * <p>
	 * Maps to BigQuery FLOAT64 type. The float value is widened to double for
	 * BigQuery storage.
	 *
	 * @param parameterIndex
	 *            the first parameter is 1, the second is 2, ...
	 * @param x
	 *            the parameter value
	 * @throws SQLException
	 *             if parameterIndex is invalid or the statement is closed
	 */
	@Override
	public void setFloat(int parameterIndex, float x) throws SQLException {
		// Bind an explicit BigQuery type rather than letting the service infer one
		setParameter(parameterIndex, () -> QueryParameterValue.of((double) x, StandardSQLTypeName.FLOAT64));
	}

	/**
	 * Sets the designated parameter to the given Java double value.
	 *
	 * <p>
	 * Maps to BigQuery FLOAT64 type with direct conversion.
	 *
	 * @param parameterIndex
	 *            the first parameter is 1, the second is 2, ...
	 * @param x
	 *            the parameter value
	 * @throws SQLException
	 *             if parameterIndex is invalid or the statement is closed
	 */
	@Override
	public void setDouble(int parameterIndex, double x) throws SQLException {
		setParameter(parameterIndex, () -> QueryParameterValue.of(x, StandardSQLTypeName.FLOAT64));
	}

	/**
	 * Sets the designated parameter to the given {@link java.math.BigDecimal}
	 * value.
	 *
	 * <p>
	 * Maps to BigQuery NUMERIC type. If the value is null, calls
	 * {@link #setNull(int, int)} with {@link Types#NUMERIC}.
	 *
	 * @param parameterIndex
	 *            the first parameter is 1, the second is 2, ...
	 * @param x
	 *            the parameter value (may be null)
	 * @throws SQLException
	 *             if parameterIndex is invalid or the statement is closed
	 */
	@Override
	public void setBigDecimal(int parameterIndex, BigDecimal x) throws SQLException {
		if (x == null) {
			setNull(parameterIndex, Types.NUMERIC);
		} else {
			setParameter(parameterIndex, () -> QueryParameterValue.of(x, StandardSQLTypeName.NUMERIC));
		}
	}

	/**
	 * Sets the designated parameter to the given Java String value.
	 *
	 * <p>
	 * Maps to BigQuery STRING type. If the value is null, calls
	 * {@link #setNull(int, int)} with {@link Types#VARCHAR}.
	 *
	 * @param parameterIndex
	 *            the first parameter is 1, the second is 2, ...
	 * @param x
	 *            the parameter value (may be null)
	 * @throws SQLException
	 *             if parameterIndex is invalid or the statement is closed
	 */
	@Override
	public void setString(int parameterIndex, String x) throws SQLException {
		// Bind an explicit BigQuery type rather than letting the service infer one
		if (x == null) {
			setNull(parameterIndex, Types.VARCHAR);
		} else {
			setParameter(parameterIndex, () -> QueryParameterValue.of(x, StandardSQLTypeName.STRING));
		}
	}

	/**
	 * Sets the designated parameter to the given Java byte array value.
	 *
	 * <p>
	 * Maps to BigQuery BYTES type. If the value is null, calls
	 * {@link #setNull(int, int)} with {@link Types#VARBINARY}.
	 *
	 * @param parameterIndex
	 *            the first parameter is 1, the second is 2, ...
	 * @param x
	 *            the parameter value (may be null)
	 * @throws SQLException
	 *             if parameterIndex is invalid or the statement is closed
	 */
	@Override
	public void setBytes(int parameterIndex, byte[] x) throws SQLException {
		// Bind an explicit BigQuery type rather than letting the service infer one
		if (x == null) {
			setNull(parameterIndex, Types.VARBINARY);
		} else {
			setParameter(parameterIndex, () -> QueryParameterValue.of(x, StandardSQLTypeName.BYTES));
		}
	}

	/**
	 * Sets the designated parameter to the given {@link java.sql.Date} value.
	 *
	 * <p>
	 * Maps to BigQuery DATE type. The date is converted to ISO-8601 format
	 * (yyyy-MM-dd). If the value is null, calls {@link #setNull(int, int)} with
	 * {@link Types#DATE}.
	 *
	 * @param parameterIndex
	 *            the first parameter is 1, the second is 2, ...
	 * @param x
	 *            the parameter value (may be null)
	 * @throws SQLException
	 *             if parameterIndex is invalid or the statement is closed
	 */
	@Override
	public void setDate(int parameterIndex, Date x) throws SQLException {
		if (x == null) {
			setNull(parameterIndex, Types.DATE);
		} else {
			setParameter(parameterIndex, () -> QueryParameterValue.of(x.toString(), StandardSQLTypeName.DATE));
		}
	}

	/**
	 * Sets the designated parameter to the given {@link java.sql.Time} value.
	 *
	 * <p>
	 * Maps to BigQuery TIME type. The time is converted to ISO-8601 format
	 * (HH:mm:ss[.SSS]). If the value is null, calls {@link #setNull(int, int)} with
	 * {@link Types#TIME}.
	 *
	 * @param parameterIndex
	 *            the first parameter is 1, the second is 2, ...
	 * @param x
	 *            the parameter value (may be null)
	 * @throws SQLException
	 *             if parameterIndex is invalid or the statement is closed
	 */
	@Override
	public void setTime(int parameterIndex, Time x) throws SQLException {
		if (x == null) {
			setNull(parameterIndex, Types.TIME);
		} else {
			setParameter(parameterIndex, () -> QueryParameterValue.time(TIME_FORMATTER.format(x.toLocalTime())));
		}
	}

	/**
	 * Sets the designated parameter to the given {@link java.sql.Timestamp} value.
	 *
	 * <p>
	 * Maps to BigQuery TIMESTAMP type, bound as epoch microseconds via
	 * {@link #timestampParameter}. If the value is null, calls
	 * {@link #setNull(int, int)} with {@link Types#TIMESTAMP}.
	 *
	 * <p>
	 * Not an ISO-8601 string: {@code QueryParameterValue} rejects the {@code T}
	 * separator client-side.
	 *
	 * @param parameterIndex
	 *            the first parameter is 1, the second is 2, ...
	 * @param x
	 *            the parameter value (may be null)
	 * @throws SQLException
	 *             if parameterIndex is invalid or the statement is closed
	 */
	@Override
	public void setTimestamp(int parameterIndex, Timestamp x) throws SQLException {
		if (x == null) {
			setNull(parameterIndex, Types.TIMESTAMP);
		} else {
			setParameter(parameterIndex, () -> timestampParameter(x));
		}
	}

	/**
	 * Clears all previously set parameter values.
	 *
	 * <p>
	 * After calling this method, all parameters must be set again before executing
	 * the prepared statement.
	 *
	 * @throws SQLException
	 *             if the statement is closed
	 */
	@Override
	public void clearParameters() throws SQLException {
		checkClosed();
		parameters.clear();
	}

	/**
	 * Sets the designated parameter to the given Java object value with target SQL
	 * type.
	 *
	 * <p>
	 * When a targetSqlType is provided, the driver attempts to convert the object
	 * to the target type before setting the parameter. This allows type conversions
	 * such as String to Integer. Type conversion is handled by
	 * {@link ParameterConverter}.
	 *
	 * @param parameterIndex
	 *            the first parameter is 1, the second is 2, ...
	 * @param x
	 *            the parameter value (may be null)
	 * @param targetSqlType
	 *            the SQL type from {@link java.sql.Types}
	 * @throws SQLException
	 *             if parameterIndex is invalid, the statement is closed, or type
	 *             conversion fails
	 */
	@Override
	public void setObject(int parameterIndex, Object x, int targetSqlType) throws SQLException {
		checkClosed();
		if (x == null) {
			setNull(parameterIndex, targetSqlType);
			return;
		}

		// Convert the object to the target SQL type using ParameterConverter utility
		switch (targetSqlType) {
			case Types.BIT, Types.BOOLEAN ->
				setBoolean(parameterIndex, ParameterConverter.toBoolean(x, parameterIndex));
			case Types.TINYINT -> setByte(parameterIndex, ParameterConverter.toByte(x, parameterIndex));
			case Types.SMALLINT -> setShort(parameterIndex, ParameterConverter.toShort(x, parameterIndex));
			case Types.INTEGER -> setInt(parameterIndex, ParameterConverter.toInt(x, parameterIndex));
			case Types.BIGINT -> setLong(parameterIndex, ParameterConverter.toLong(x, parameterIndex));
			case Types.REAL -> setFloat(parameterIndex, ParameterConverter.toFloat(x, parameterIndex));
			case Types.FLOAT, Types.DOUBLE -> setDouble(parameterIndex, ParameterConverter.toDouble(x, parameterIndex));
			case Types.DECIMAL, Types.NUMERIC ->
				setBigDecimal(parameterIndex, ParameterConverter.toBigDecimal(x, parameterIndex));
			case Types.CHAR, Types.VARCHAR, Types.LONGVARCHAR, Types.NCHAR, Types.NVARCHAR, Types.LONGNVARCHAR ->
				setString(parameterIndex, ParameterConverter.toString(x));
			case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY ->
				setBytes(parameterIndex, ParameterConverter.toBytes(x, parameterIndex));
			case Types.DATE -> setDate(parameterIndex, ParameterConverter.toDate(x, parameterIndex));
			case Types.TIME, Types.TIME_WITH_TIMEZONE ->
				setTime(parameterIndex, ParameterConverter.toTime(x, parameterIndex));
			case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE ->
				setTimestamp(parameterIndex, ParameterConverter.toTimestamp(x, parameterIndex));
			default -> setObject(parameterIndex, x);
		}
	}

	/**
	 * Sets the designated parameter to the given Java object value.
	 *
	 * <p>
	 * The driver infers the BigQuery type from the Java object type:
	 * <ul>
	 * <li>{@link String} → STRING
	 * <li>{@link Integer}, {@link Long} → INT64
	 * <li>{@link Float}, {@link Double} → FLOAT64
	 * <li>{@link Boolean} → BOOL
	 * <li>{@link BigDecimal} → NUMERIC
	 * <li>{@link Timestamp} → TIMESTAMP (converted to UTC Instant)
	 * <li>{@link Date} → DATE
	 * <li>{@link Time} → TIME
	 * <li>{@code byte[]} → BYTES
	 * </ul>
	 *
	 * <p>
	 * If the value is null, calls {@link #setNull(int, int)} with
	 * {@link Types#NULL}.
	 *
	 * @param parameterIndex
	 *            the first parameter is 1, the second is 2, ...
	 * @param x
	 *            the parameter value (may be null)
	 * @throws SQLException
	 *             if parameterIndex is invalid, the statement is closed, or the
	 *             object type is unsupported
	 */
	@Override
	public void setObject(int parameterIndex, Object x) throws SQLException {
		checkClosed();
		if (x == null) {
			setNull(parameterIndex, Types.NULL);
			return;
		}

		// Bind an explicit BigQuery type per Java type rather than letting the
		// service infer one
		switch (x) {
			case String s -> setParameter(parameterIndex, () -> QueryParameterValue.of(s, StandardSQLTypeName.STRING));
			case Integer i ->
				setParameter(parameterIndex, () -> QueryParameterValue.of(Long.valueOf(i), StandardSQLTypeName.INT64));
			case Long l -> setParameter(parameterIndex, () -> QueryParameterValue.of(l, StandardSQLTypeName.INT64));
			case Float f -> setParameter(parameterIndex,
					() -> QueryParameterValue.of(Double.valueOf(f), StandardSQLTypeName.FLOAT64));
			case Double d -> setParameter(parameterIndex, () -> QueryParameterValue.of(d, StandardSQLTypeName.FLOAT64));
			case Boolean b -> setParameter(parameterIndex, () -> QueryParameterValue.of(b, StandardSQLTypeName.BOOL));
			case BigDecimal bd ->
				setParameter(parameterIndex, () -> QueryParameterValue.of(bd, StandardSQLTypeName.NUMERIC));
			// Delegated rather than re-encoded here. Building the parameter a second
			// time is what let this drift: TIMESTAMP was bound as an ISO-8601 string
			// and TIME as HH:mm:ss, both of which QueryParameterValue rejects
			// client-side, so setObject could not bind either while setTimestamp and
			// setTime — which use the encodings below — always could.
			case Timestamp ts -> setTimestamp(parameterIndex, ts);
			case Date dt -> setDate(parameterIndex, dt);
			case Time t -> setTime(parameterIndex, t);
			case byte[] bytes ->
				setParameter(parameterIndex, () -> QueryParameterValue.of(bytes, StandardSQLTypeName.BYTES));
			case java.sql.Array a -> setArray(parameterIndex, a);
			case java.util.List<?> list -> setListParameter(parameterIndex, list);
			case java.sql.Struct s -> setParameter(parameterIndex, () -> toStructParameter(s, parameterIndex));
			case java.util.Map<?, ?> m -> setParameter(parameterIndex, () -> toStructParameter(m, parameterIndex));
			default -> throw new SQLException("Unsupported parameter type: " + x.getClass().getName());
		}
	}

	/**
	 * Binds a {@link java.sql.Struct} as a BigQuery struct parameter, taking the
	 * field names from its {@code STRUCT<...>} type name.
	 *
	 * <p>
	 * The type name is the only place the names can come from: {@code Struct}
	 * carries its attributes positionally. A struct this driver returned from
	 * {@link java.sql.ResultSet#getObject} always has them, which is what lets a
	 * struct that was read be bound straight back.
	 *
	 * @param struct
	 *            the struct to bind
	 * @param parameterIndex
	 *            the parameter index, for error messages
	 * @return the BigQuery parameter
	 * @throws SQLException
	 *             if the type name declares no usable field names, or the attribute
	 *             count disagrees with it
	 */
	private QueryParameterValue toStructParameter(java.sql.Struct struct, int parameterIndex) throws SQLException {
		Object[] attributes = struct.getAttributes();
		List<StructTypeNames.StructField> fields = StructTypeNames.parse(struct.getSQLTypeName());
		if (fields.isEmpty()) {
			throw new BQSQLException("Cannot bind the struct at parameter " + parameterIndex + ": its type name ("
					+ struct.getSQLTypeName() + ") does not name its fields, and BigQuery struct parameters "
					+ "are named. Use Connection.createStruct(\"STRUCT<a INT64, b STRING>\", ...), or pass a "
					+ "Map<String, Object> to setObject", BQSQLException.SQLSTATE_INVALID_PARAMETER_VALUE);
		}
		if (attributes.length != fields.size()) {
			throw new BQSQLException(
					"The struct at parameter " + parameterIndex + " has " + attributes.length + " attribute(s) but its "
							+ "type name declares " + fields.size() + ": " + struct.getSQLTypeName(),
					BQSQLException.SQLSTATE_INVALID_PARAMETER_VALUE);
		}

		Map<String, QueryParameterValue> values = new LinkedHashMap<>();
		for (int i = 0; i < fields.size(); i++) {
			StructTypeNames.StructField field = fields.get(i);
			values.put(field.name(), toFieldParameter(attributes[i], field.type(), parameterIndex));
		}
		return QueryParameterValue.struct(values);
	}

	/**
	 * Binds a {@code Map} as a BigQuery struct parameter, which is the shape
	 * BigQuery itself uses — {@code QueryParameterValue.struct} takes named fields.
	 *
	 * <p>
	 * Iteration order is preserved, so a {@code LinkedHashMap} binds its fields in
	 * the order they were added. Names are what BigQuery matches on, so the order
	 * only affects the struct's declared shape.
	 *
	 * <p>
	 * Field types are inferred from the values, so a {@code null} value has nothing
	 * to infer from and binds as a {@code STRING} null. Where that is not the right
	 * type, name the fields with {@code createStruct} instead — a declared type
	 * answers what a null value cannot.
	 *
	 * @param map
	 *            the field names and values
	 * @param parameterIndex
	 *            the parameter index, for error messages
	 * @return the BigQuery parameter
	 * @throws SQLException
	 *             if a key is not a string, or a value has no BigQuery mapping
	 */
	private QueryParameterValue toStructParameter(Map<?, ?> map, int parameterIndex) throws SQLException {
		Map<String, QueryParameterValue> values = new LinkedHashMap<>();
		for (Map.Entry<?, ?> entry : map.entrySet()) {
			if (!(entry.getKey() instanceof String name)) {
				throw new BQSQLException(
						"The map at parameter " + parameterIndex + " has a non-String key ("
								+ (entry.getKey() == null ? "null" : entry.getKey().getClass().getName())
								+ "); BigQuery struct field names are strings",
						BQSQLException.SQLSTATE_INVALID_PARAMETER_VALUE);
			}
			values.put(name, toFieldParameter(entry.getValue(), null, parameterIndex));
		}
		return QueryParameterValue.struct(values);
	}

	/**
	 * Converts one struct field value to a parameter.
	 *
	 * <p>
	 * Mirrors the scalar cases of {@link #setObject(int, Object)} so a value binds
	 * the same way inside a struct as it does on its own, and recurses for the
	 * nested shapes: a map or struct becomes a nested struct, a list or array
	 * becomes an array.
	 *
	 * @param value
	 *            the field value, possibly null
	 * @param declaredType
	 *            the type from the struct's type name, or null when the field was
	 *            not declared. Used only to type a null
	 * @param parameterIndex
	 *            the parameter index, for error messages
	 * @return the BigQuery parameter
	 * @throws SQLException
	 *             if the value has no BigQuery mapping
	 */
	private QueryParameterValue toFieldParameter(Object value, StandardSQLTypeName declaredType, int parameterIndex)
			throws SQLException {
		if (value == null) {
			// BigQuery rejects an untyped null, so one is needed either way. The
			// declared type is the honest answer where there is one; STRING is the
			// fallback, which is why createStruct beats a map for nullable fields.
			return QueryParameterValue.of(null, declaredType != null ? declaredType : StandardSQLTypeName.STRING);
		}

		return switch (value) {
			case String s -> QueryParameterValue.of(s, StandardSQLTypeName.STRING);
			case Integer i -> QueryParameterValue.of(Long.valueOf(i), StandardSQLTypeName.INT64);
			case Long l -> QueryParameterValue.of(l, StandardSQLTypeName.INT64);
			case Short sh -> QueryParameterValue.of(Long.valueOf(sh), StandardSQLTypeName.INT64);
			case Byte b -> QueryParameterValue.of(Long.valueOf(b), StandardSQLTypeName.INT64);
			case Float f -> QueryParameterValue.of(Double.valueOf(f), StandardSQLTypeName.FLOAT64);
			case Double d -> QueryParameterValue.of(d, StandardSQLTypeName.FLOAT64);
			case Boolean b -> QueryParameterValue.of(b, StandardSQLTypeName.BOOL);
			case BigDecimal bd -> QueryParameterValue.of(bd, StandardSQLTypeName.NUMERIC);
			// Through the same typed factories setTimestamp and setTime use, not the
			// string forms setObject reaches for. QueryParameterValue rejects an
			// ISO-8601 timestamp string client-side, and wants exactly six fractional
			// digits on a TIME — see timestampParameter and TIME_FORMATTER.
			case Timestamp ts -> timestampParameter(ts);
			case Time t -> QueryParameterValue.time(TIME_FORMATTER.format(t.toLocalTime()));
			case Date dt -> QueryParameterValue.of(dt.toString(), StandardSQLTypeName.DATE);
			case byte[] bytes -> QueryParameterValue.of(bytes, StandardSQLTypeName.BYTES);
			case java.sql.Struct nested -> toStructParameter(nested, parameterIndex);
			case Map<?, ?> nested -> toStructParameter(nested, parameterIndex);
			case List<?> list -> toArrayFieldParameter(list, parameterIndex);
			case Object[] array -> toArrayFieldParameter(Arrays.asList(array), parameterIndex);
			case java.sql.Array array -> toArrayFieldParameter(arrayElements(array), parameterIndex);
			default -> throw new BQSQLException(
					"Unsupported struct field type at parameter " + parameterIndex + ": " + value.getClass().getName(),
					BQSQLException.SQLSTATE_INVALID_PARAMETER_VALUE);
		};
	}

	/** The elements of a {@link java.sql.Array}, as a list. */
	private static List<?> arrayElements(java.sql.Array array) throws SQLException {
		Object elements = array.getArray();
		return elements instanceof Object[] objects ? Arrays.asList(objects) : List.of();
	}

	/**
	 * Binds a struct field that is itself an array.
	 *
	 * <p>
	 * BigQuery arrays are homogeneous, so the element type is taken from the first
	 * non-null element and every element is bound with it. An array of all nulls
	 * has nothing to infer from and is rejected rather than guessed at, because a
	 * wrong element type surfaces as a query error far from its cause.
	 */
	private QueryParameterValue toArrayFieldParameter(List<?> elements, int parameterIndex) throws SQLException {
		if (elements.isEmpty()) {
			return QueryParameterValue.array(new QueryParameterValue[0], StandardSQLTypeName.STRING);
		}
		Object first = elements.stream().filter(java.util.Objects::nonNull).findFirst().orElse(null);
		if (first == null) {
			throw new BQSQLException("The array struct field at parameter " + parameterIndex
					+ " contains only nulls, so its element " + "type cannot be inferred",
					BQSQLException.SQLSTATE_INVALID_PARAMETER_VALUE);
		}
		StandardSQLTypeName elementType = inferSqlType(first);
		QueryParameterValue[] values = new QueryParameterValue[elements.size()];
		for (int i = 0; i < elements.size(); i++) {
			values[i] = toFieldParameter(elements.get(i), elementType, parameterIndex);
		}
		return QueryParameterValue.array(values, elementType);
	}

	@Override
	public void setArray(int parameterIndex, java.sql.Array x) throws SQLException {
		checkClosed();
		if (x == null) {
			setNull(parameterIndex, java.sql.Types.ARRAY);
			return;
		}
		Object[] arr = (Object[]) x.getArray();
		StandardSQLTypeName elemType = TypeMapper.toStandardSQLTypeName(x.getBaseType());
		// Built inside the factory, not before it: an element BigQuery rejects must
		// surface as a SQLException like any other bad parameter.
		setParameter(parameterIndex, () -> arrayOf(java.util.Arrays.asList(arr), elemType));
	}

	/** Builds an array parameter, binding every element with the same type. */
	private static QueryParameterValue arrayOf(java.util.List<?> elements, StandardSQLTypeName elementType) {
		QueryParameterValue[] values = new QueryParameterValue[elements.size()];
		for (int i = 0; i < elements.size(); i++) {
			Object element = elements.get(i);
			values[i] = element == null
					? QueryParameterValue.of(null, elementType)
					: QueryParameterValue.of(element.toString(), elementType);
		}
		return QueryParameterValue.array(values, elementType);
	}

	private void setListParameter(int parameterIndex, java.util.List<?> list) throws SQLException {
		if (list.isEmpty()) {
			setParameter(parameterIndex,
					() -> QueryParameterValue.array(new QueryParameterValue[0], StandardSQLTypeName.STRING));
			return;
		}
		// Infer element type from first non-null element
		Object first = list.stream().filter(e -> e != null).findFirst().orElse(null);
		StandardSQLTypeName elemType = inferSqlType(first);
		setParameter(parameterIndex, () -> arrayOf(list, elemType));
	}

	private static StandardSQLTypeName inferSqlType(Object obj) {
		if (obj instanceof String)
			return StandardSQLTypeName.STRING;
		if (obj instanceof Long || obj instanceof Integer)
			return StandardSQLTypeName.INT64;
		if (obj instanceof Double || obj instanceof Float)
			return StandardSQLTypeName.FLOAT64;
		if (obj instanceof Boolean)
			return StandardSQLTypeName.BOOL;
		if (obj instanceof java.math.BigDecimal)
			return StandardSQLTypeName.NUMERIC;
		if (obj instanceof java.sql.Timestamp)
			return StandardSQLTypeName.TIMESTAMP;
		if (obj instanceof java.sql.Date)
			return StandardSQLTypeName.DATE;
		if (obj instanceof java.sql.Time)
			return StandardSQLTypeName.TIME;
		return StandardSQLTypeName.STRING;
	}

	/**
	 * Executes the prepared statement, which may return multiple types of results.
	 *
	 * <p>
	 * Returns {@code false} when BigQuery reports the statement was DML — retrieve
	 * the affected-row count via {@link #getUpdateCount()}. Returns {@code true}
	 * otherwise — retrieve results via {@link #getResultSet()}.
	 */
	@Override
	@SuppressWarnings("resource") // ResultSet managed by statement, closed in statement.close()
	public boolean execute() throws SQLException {
		executeQuery();
		return !finishExecuteAsUpdateIfDml();
	}

	@Override
	public ResultSetMetaData getMetaData() throws SQLException {
		if (currentResultSet != null) {
			return currentResultSet.getMetaData();
		}
		return null;
	}

	/**
	 * Sets the designated parameter to the given {@link java.sql.Date} value using
	 * the specified Calendar.
	 *
	 * <p>
	 * The Calendar is used to interpret the date in a specific timezone. The date
	 * is adjusted from the Calendar's timezone to UTC before storing in BigQuery.
	 * If the Calendar is null, this method behaves the same as
	 * {@link #setDate(int, Date)}.
	 *
	 * @param parameterIndex
	 *            the first parameter is 1, the second is 2, ...
	 * @param x
	 *            the parameter value (may be null)
	 * @param cal
	 *            the Calendar to use for timezone interpretation (may be null)
	 * @throws SQLException
	 *             if parameterIndex is invalid or the statement is closed
	 */
	@Override
	public void setDate(int parameterIndex, Date x, Calendar cal) throws SQLException {
		if (x == null) {
			setNull(parameterIndex, Types.DATE);
			return;
		}

		if (cal == null) {
			setDate(parameterIndex, x);
			return;
		}

		// Reinterpret the wall clock as belonging to the Calendar's zone (#121)
		Date adjustedDate = TimezoneUtils.dateToCalendarZone(x, cal);
		setDate(parameterIndex, adjustedDate);
	}

	/**
	 * Sets the designated parameter to the given {@link java.sql.Time} value using
	 * the specified Calendar.
	 *
	 * <p>
	 * The Calendar is used to interpret the time in a specific timezone. The time
	 * is adjusted from the Calendar's timezone to UTC before storing in BigQuery.
	 * If the Calendar is null, this method behaves the same as
	 * {@link #setTime(int, Time)}.
	 *
	 * @param parameterIndex
	 *            the first parameter is 1, the second is 2, ...
	 * @param x
	 *            the parameter value (may be null)
	 * @param cal
	 *            the Calendar to use for timezone interpretation (may be null)
	 * @throws SQLException
	 *             if parameterIndex is invalid or the statement is closed
	 */
	@Override
	public void setTime(int parameterIndex, Time x, Calendar cal) throws SQLException {
		if (x == null) {
			setNull(parameterIndex, Types.TIME);
			return;
		}

		if (cal == null) {
			setTime(parameterIndex, x);
			return;
		}

		// Reinterpret the wall clock as belonging to the Calendar's zone (#121)
		Time adjustedTime = TimezoneUtils.timeToCalendarZone(x, cal);
		setTime(parameterIndex, adjustedTime);
	}

	/**
	 * Sets the designated parameter to the given {@link java.sql.Timestamp} value
	 * using the specified Calendar.
	 *
	 * <p>
	 * The Calendar is used to interpret the timestamp in a specific timezone. The
	 * timestamp is adjusted from the Calendar's timezone to UTC before storing in
	 * BigQuery. Nanosecond precision is preserved during the conversion. If the
	 * Calendar is null, this method behaves the same as
	 * {@link #setTimestamp(int, Timestamp)}.
	 *
	 * @param parameterIndex
	 *            the first parameter is 1, the second is 2, ...
	 * @param x
	 *            the parameter value (may be null)
	 * @param cal
	 *            the Calendar to use for timezone interpretation (may be null)
	 * @throws SQLException
	 *             if parameterIndex is invalid or the statement is closed
	 */
	@Override
	public void setTimestamp(int parameterIndex, Timestamp x, Calendar cal) throws SQLException {
		if (x == null) {
			setNull(parameterIndex, Types.TIMESTAMP);
			return;
		}

		if (cal == null) {
			setTimestamp(parameterIndex, x);
			return;
		}

		// Reinterpret the wall clock as belonging to the Calendar's zone (#121)
		Timestamp adjusted = TimezoneUtils.timestampToCalendarZone(x, cal);
		setParameter(parameterIndex, () -> timestampParameter(adjusted));
	}

	/**
	 * Sets the designated parameter to SQL NULL with type name.
	 *
	 * <p>
	 * The typeName parameter is ignored; this method delegates to
	 * {@link #setNull(int, int)}.
	 *
	 * @param parameterIndex
	 *            the first parameter is 1, the second is 2, ...
	 * @param sqlType
	 *            the SQL type code from {@link java.sql.Types}
	 * @param typeName
	 *            the fully-qualified name of an SQL user-defined type (ignored)
	 * @throws SQLException
	 *             if parameterIndex is invalid or the statement is closed
	 */
	@Override
	public void setNull(int parameterIndex, int sqlType, String typeName) throws SQLException {
		setNull(parameterIndex, sqlType);
	}

	/**
	 * Sets the designated parameter to the given {@link java.net.URL} value.
	 *
	 * <p>
	 * The URL is converted to a string representation and stored as a BigQuery
	 * STRING type.
	 *
	 * @param parameterIndex
	 *            the first parameter is 1, the second is 2, ...
	 * @param x
	 *            the parameter value
	 * @throws SQLException
	 *             if parameterIndex is invalid or the statement is closed
	 */
	@Override
	public void setURL(int parameterIndex, URL x) throws SQLException {
		setString(parameterIndex, x.toString());
	}

	@Override
	public ParameterMetaData getParameterMetaData() throws SQLException {
		checkClosed();
		return new BQParameterMetaData(parameters.size());
	}

	/**
	 * Sets the designated parameter to the given String value (NCHAR support).
	 *
	 * <p>
	 * This method behaves identically to {@link #setString(int, String)} since
	 * BigQuery STRING type natively supports Unicode characters.
	 *
	 * @param parameterIndex
	 *            the first parameter is 1, the second is 2, ...
	 * @param value
	 *            the parameter value (may be null)
	 * @throws SQLException
	 *             if parameterIndex is invalid or the statement is closed
	 */
	@Override
	public void setNString(int parameterIndex, String value) throws SQLException {
		setString(parameterIndex, value);
	}

	/**
	 * Sets the designated parameter to the given Java object with target SQL type
	 * and scale/length.
	 *
	 * <p>
	 * The targetSqlType and scaleOrLength parameters are ignored; this method
	 * delegates to {@link #setObject(int, Object)}.
	 *
	 * @param parameterIndex
	 *            the first parameter is 1, the second is 2, ...
	 * @param x
	 *            the parameter value (may be null)
	 * @param targetSqlType
	 *            the SQL type (ignored)
	 * @param scaleOrLength
	 *            the scale or length (ignored)
	 * @throws SQLException
	 *             if parameterIndex is invalid, the statement is closed, or the
	 *             object type is unsupported
	 */
	@Override
	public void setObject(int parameterIndex, Object x, int targetSqlType, int scaleOrLength) throws SQLException {
		setObject(parameterIndex, x);
	}

	/**
	 * Sets the designated parameter to the given Java object with target SQLType
	 * and scale/length.
	 *
	 * <p>
	 * The targetSqlType and scaleOrLength parameters are ignored; this method
	 * delegates to {@link #setObject(int, Object)}.
	 *
	 * @param parameterIndex
	 *            the first parameter is 1, the second is 2, ...
	 * @param x
	 *            the parameter value (may be null)
	 * @param targetSqlType
	 *            the SQLType (ignored)
	 * @param scaleOrLength
	 *            the scale or length (ignored)
	 * @throws SQLException
	 *             if parameterIndex is invalid, the statement is closed, or the
	 *             object type is unsupported
	 */
	@Override
	public void setObject(int parameterIndex, Object x, SQLType targetSqlType, int scaleOrLength) throws SQLException {
		setObject(parameterIndex, x);
	}

	/**
	 * Sets the designated parameter to the given Java object with target SQLType.
	 *
	 * <p>
	 * The targetSqlType parameter is ignored; this method delegates to
	 * {@link #setObject(int, Object)}.
	 *
	 * @param parameterIndex
	 *            the first parameter is 1, the second is 2, ...
	 * @param x
	 *            the parameter value (may be null)
	 * @param targetSqlType
	 *            the SQLType (ignored)
	 * @throws SQLException
	 *             if parameterIndex is invalid, the statement is closed, or the
	 *             object type is unsupported
	 */
	@Override
	public void setObject(int parameterIndex, Object x, SQLType targetSqlType) throws SQLException {
		setObject(parameterIndex, x);
	}

	/**
	 * Large-count variant of {@link #executeUpdate()}.
	 */
	@Override
	public long executeLargeUpdate() throws SQLException {
		long affectedRows = executeDmlInternal(sqlTemplate, parameters);
		// JDBC: 0 for statements that return nothing (DDL, or no DML statistics)
		long count = Math.max(0L, affectedRows);
		currentUpdateCount = count;
		return count;
	}

	// Batch operations

	/**
	 * Adds the current parameter set to this statement's batch.
	 *
	 * <p>
	 * The parameter set is snapshotted and the working parameters are cleared, so
	 * all parameters must be set again before the next {@code addBatch()} call.
	 *
	 * @throws SQLException
	 *             if the statement is closed
	 */
	@Override
	public void addBatch() throws SQLException {
		checkClosed();
		batchParameterSets.add(new ArrayList<>(parameters));
		parameters.clear();
	}

	/**
	 * Discards all parameter sets accumulated via {@link #addBatch()}.
	 *
	 * @throws SQLException
	 *             if the statement is closed
	 */
	@Override
	public void clearBatch() throws SQLException {
		checkClosed();
		batchParameterSets.clear();
	}

	/**
	 * Executes the batched parameter sets against BigQuery.
	 *
	 * <p>
	 * When the SQL template is a simple single-row parameterized INSERT
	 * ({@code INSERT INTO t (...) VALUES (?, ...)}), the batch is collapsed into
	 * multi-row {@code INSERT ... VALUES (...), (...), ...} statements — the moral
	 * equivalent of PostgreSQL's {@code reWriteBatchedInserts} and the only DML
	 * shape that performs acceptably on BigQuery. Large batches are chunked to stay
	 * under BigQuery's per-query limits (10,000 query parameters, ~1 MB query
	 * text), one query job per chunk.
	 *
	 * <p>
	 * Statements that cannot be collapsed (non-INSERT DML,
	 * {@code INSERT ... SELECT}, tuples mixing literals with placeholders) are
	 * executed sequentially, one job per parameter set.
	 *
	 * <p>
	 * The batch is cleared once execution completes or fails. If execution fails, a
	 * {@link BatchUpdateException} is thrown containing the update counts of the
	 * parameter sets that completed before the failure.
	 *
	 * @return per-row update counts: the affected-row count when BigQuery reports
	 *         one, otherwise {@link #SUCCESS_NO_INFO}
	 * @throws SQLException
	 *             if the statement is closed
	 * @throws BatchUpdateException
	 *             if any part of the batch fails
	 */
	@Override
	public int[] executeBatch() throws SQLException {
		checkClosed();
		if (batchParameterSets.isEmpty()) {
			return new int[0];
		}
		List<List<QueryParameterValue>> parameterSets = new ArrayList<>(batchParameterSets);
		batchParameterSets.clear();

		Optional<BatchInsertRewriter.RewritableInsert> rewritable = BatchInsertRewriter.parse(sqlTemplate);
		if (rewritable.isPresent() && allSetsMatchTemplate(rewritable.get().parametersPerRow(), parameterSets)) {
			Optional<BatchLoadEncoder.LoadTarget> loadTarget = loadTargetFor(rewritable.get(), parameterSets);
			if (loadTarget.isPresent()) {
				return executeLoadBatch(loadTarget.get(), parameterSets);
			}
			return executeCollapsedBatch(rewritable.get(), parameterSets);
		}
		return executeSequentialBatch(parameterSets);
	}

	/**
	 * Verifies every batched parameter set has exactly the number of parameters the
	 * INSERT template expects. Collapsing misaligned sets would silently shift
	 * values across rows; such batches fall back to sequential execution, where
	 * BigQuery reports an accurate per-statement error.
	 */
	private static boolean allSetsMatchTemplate(int parametersPerRow, List<List<QueryParameterValue>> parameterSets) {
		return parameterSets.stream().allMatch(set -> set.size() == parametersPerRow);
	}

	/**
	 * Decides whether this batch goes through a load job, and where to.
	 *
	 * <p>
	 * Every condition here is a reason the DML path stays correct where the load
	 * path would not, so each returns empty rather than throwing:
	 *
	 * <ul>
	 * <li><b>The property is unset, or the batch is smaller than it.</b> A load job
	 * is a different mechanism, not a faster one of the same kind, so it is never
	 * chosen without being asked for.
	 * <li><b>The connection is in a transaction or a session.</b> Load jobs cannot
	 * participate in a BigQuery transaction — rows would land outside it and
	 * survive a rollback. This is the distinction the issue asked to be explicit
	 * rather than accidental, and it is checked on {@code autoCommit} rather than
	 * on whether a transaction has actually begun, because {@code BEGIN} is
	 * deferred to the first statement and this batch could be it.
	 * <li><b>The INSERT has no explicit column list, or a shape this driver cannot
	 * resolve.</b>
	 * <li><b>Some parameter has a type the encoder is not sure of.</b>
	 * </ul>
	 *
	 * @param insert
	 *            the parsed INSERT
	 * @param parameterSets
	 *            the accumulated batch
	 * @return where to load, or empty to use the DML path
	 * @throws SQLException
	 *             if the connection's state cannot be read
	 */
	private Optional<BatchLoadEncoder.LoadTarget> loadTargetFor(BatchInsertRewriter.RewritableInsert insert,
			List<List<QueryParameterValue>> parameterSets) throws SQLException {
		Integer threshold = properties.batchLoadThreshold();
		if (threshold == null || parameterSets.size() < threshold) {
			return Optional.empty();
		}
		if (!connection.getAutoCommit() || connection.getSessionManager().hasSession()) {
			logger.debug("Batch of {} rows stays on the DML path: a load job cannot join a transaction or session",
					parameterSets.size());
			return Optional.empty();
		}
		if (!BatchLoadEncoder.canEncode(parameterSets)) {
			logger.debug("Batch of {} rows stays on the DML path: a parameter type is not loadable",
					parameterSets.size());
			return Optional.empty();
		}
		Optional<BatchLoadEncoder.LoadTarget> target = BatchLoadEncoder.parseTarget(insert.insertPrefix(),
				insert.parametersPerRow());
		if (target.isEmpty()) {
			logger.debug("Batch of {} rows stays on the DML path: the INSERT names no explicit column list",
					parameterSets.size());
		}
		return target;
	}

	/**
	 * Executes the batch as a single BigQuery load job.
	 *
	 * <p>
	 * The rows are streamed as newline-delimited JSON straight into the job's write
	 * channel, so nothing is staged in GCS and the whole batch never exists as one
	 * string in memory.
	 *
	 * <p>
	 * <b>Update counts.</b> A load job reports rows written in aggregate, with no
	 * per-row breakdown to map back onto parameter sets. When the count matches the
	 * batch exactly, every entry is 1; otherwise every entry is
	 * {@link #SUCCESS_NO_INFO}, which JDBC defines for precisely this case. The
	 * counts are never fabricated from the batch size.
	 *
	 * @param target
	 *            the table and columns to write
	 * @param parameterSets
	 *            the rows
	 * @return per-row update counts
	 * @throws SQLException
	 *             if the load fails
	 */
	private int[] executeLoadBatch(BatchLoadEncoder.LoadTarget target, List<List<QueryParameterValue>> parameterSets)
			throws SQLException {
		TableId tableId = resolveTableId(target.tablePath());
		WriteChannelConfiguration writeConfig = WriteChannelConfiguration.newBuilder(tableId)
				.setFormatOptions(FormatOptions.json())
				// The table exists and owns its schema: this INSERT names columns, and
				// autodetect would infer types from the JSON text instead, quietly
				// widening or narrowing them.
				.setAutodetect(false).setWriteDisposition(JobInfo.WriteDisposition.WRITE_APPEND)
				.setCreateDisposition(JobInfo.CreateDisposition.CREATE_NEVER).build();

		logger.debug("Loading {} rows into {} via a load job", parameterSets.size(), target.tablePath());

		Job job;
		TableDataWriteChannel channel = connection.getBigQuery().writer(writeConfig);
		try {
			for (List<QueryParameterValue> values : parameterSets) {
				String line = BatchLoadEncoder.toJsonRow(target, values) + "\n";
				channel.write(java.nio.ByteBuffer.wrap(line.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
			}
			// Closed here rather than in a finally, and deliberately not with
			// try-with-resources: the load job does not exist until the channel is
			// closed, so getJob() returns null before this line and the ordering is
			// the point rather than an incidental detail.
			channel.close();
			job = channel.getJob();
		} catch (java.io.IOException e) {
			closeQuietly(channel);
			throw new BQSQLException("Failed to stream a batch load of " + parameterSets.size() + " rows into "
					+ target.tablePath() + ": " + e.getMessage(), BQSQLException.SQLSTATE_GENERAL_ERROR, e);
		}

		long written = awaitLoad(job, target, parameterSets.size());
		int[] updateCounts = new int[parameterSets.size()];
		java.util.Arrays.fill(updateCounts, written == parameterSets.size() ? 1 : SUCCESS_NO_INFO);
		return updateCounts;
	}

	/**
	 * Closes a write channel while an earlier failure is already propagating.
	 *
	 * <p>
	 * A close failure here is swallowed on purpose: the exception on its way out
	 * says why the load did not happen, and replacing it with a second one about
	 * the cleanup would hide that.
	 */
	private static void closeQuietly(TableDataWriteChannel channel) {
		try {
			channel.close();
		} catch (java.io.IOException | RuntimeException e) {
			logger.debug("Ignoring failure closing a batch load channel after an earlier error: {}", e.getMessage());
		}
	}

	/**
	 * Waits for a load job and reports how many rows it wrote.
	 *
	 * @throws SQLException
	 *             if the job fails, which for a load job means nothing was written
	 */
	private long awaitLoad(Job job, BatchLoadEncoder.LoadTarget target, int rowCount) throws SQLException {
		try {
			Job completed = job.waitFor();
			if (completed == null) {
				throw new BQSQLException("Batch load job for " + target.tablePath() + " no longer exists",
						BQSQLException.SQLSTATE_GENERAL_ERROR);
			}
			if (completed.getStatus() != null && completed.getStatus().getError() != null) {
				throw new BatchUpdateException(
						"Batch load of " + rowCount + " rows into " + target.tablePath() + " failed: "
								+ completed.getStatus().getError().getMessage(),
						BQSQLException.SQLSTATE_GENERAL_ERROR, 0, new int[0]);
			}
			JobStatistics.LoadStatistics stats = completed.getStatistics();
			return stats == null || stats.getOutputRows() == null ? -1 : stats.getOutputRows();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new BQSQLException("Interrupted waiting for a batch load job",
					BQSQLException.SQLSTATE_OPERATION_CANCELED, e);
		}
	}

	/**
	 * Resolves an INSERT's table path against the connection's defaults.
	 *
	 * <p>
	 * A load job needs a fully-qualified {@link TableId}, where the SQL may have
	 * named the table with one, two or three parts.
	 */
	private TableId resolveTableId(String tablePath) throws SQLException {
		String[] parts = tablePath.split("\\.");
		return switch (parts.length) {
			case 3 -> TableId.of(parts[0], parts[1], parts[2]);
			case 2 -> TableId.of(properties.projectId(), parts[0], parts[1]);
			case 1 -> {
				if (properties.datasetId() == null) {
					throw new BQSQLException("Cannot load into '" + tablePath
							+ "': the statement names no dataset and the " + "connection has no default one",
							BQSQLException.SQLSTATE_INVALID_PARAMETER_VALUE);
				}
				yield TableId.of(properties.projectId(), properties.datasetId(), parts[0]);
			}
			default -> throw new BQSQLException("Cannot load into '" + tablePath + "': unrecognised table path",
					BQSQLException.SQLSTATE_INVALID_PARAMETER_VALUE);
		};
	}

	/**
	 * Executes the batch as chunked multi-row INSERT query jobs.
	 */
	private int[] executeCollapsedBatch(BatchInsertRewriter.RewritableInsert insert,
			List<List<QueryParameterValue>> parameterSets) throws SQLException {
		int totalRows = parameterSets.size();
		int maxRowsPerChunk = insert.maxRowsPerChunk();
		int[] updateCounts = new int[totalRows];
		int completedRows = 0;

		// Each chunk is a separate execution and so clears the previous chunk's
		// warnings and cost estimates. Collecting them here is what makes
		// getWarnings() a chain and getCostEstimates() one entry per chunk; without
		// it a caller sees only the final chunk, which for a large batch is the
		// least interesting one.
		Diagnostics batchDiagnostics = Diagnostics.NONE;

		while (completedRows < totalRows) {
			int chunkRows = Math.min(maxRowsPerChunk, totalRows - completedRows);
			String chunkSql = insert.buildSql(chunkRows);
			List<QueryParameterValue> chunkParameters = new ArrayList<>(chunkRows * insert.parametersPerRow());
			for (int row = completedRows; row < completedRows + chunkRows; row++) {
				chunkParameters.addAll(parameterSets.get(row));
			}

			long affectedRows;
			try {
				affectedRows = executeDmlInternal(chunkSql, chunkParameters);
			} catch (SQLException e) {
				throw new BatchUpdateException(
						"Batch INSERT failed on rows " + completedRows + "-" + (completedRows + chunkRows - 1) + ": "
								+ e.getMessage(),
						e.getSQLState(), e.getErrorCode(), Arrays.copyOf(updateCounts, completedRows), e);
			}
			batchDiagnostics = batchDiagnostics.andThen(currentDiagnostics());

			// Only claim per-row success when BigQuery confirms the expected count
			int perRowCount = affectedRows == chunkRows ? 1 : SUCCESS_NO_INFO;
			Arrays.fill(updateCounts, completedRows, completedRows + chunkRows, perRowCount);
			completedRows += chunkRows;
		}
		publishDiagnostics(batchDiagnostics);
		return updateCounts;
	}

	/**
	 * Executes the batch sequentially, one query job per parameter set. Fallback
	 * for statements that cannot be collapsed into a multi-row INSERT.
	 */
	private int[] executeSequentialBatch(List<List<QueryParameterValue>> parameterSets) throws SQLException {
		int[] updateCounts = new int[parameterSets.size()];
		for (int i = 0; i < parameterSets.size(); i++) {
			try {
				// No per-entry estimate: this path is already one job per set (#140).
				updateCounts[i] = toUpdateCount(executeDmlInternal(sqlTemplate, parameterSets.get(i), false));
			} catch (SQLException e) {
				throw new BatchUpdateException("Batch entry " + i + " failed: " + e.getMessage(), e.getSQLState(),
						e.getErrorCode(), Arrays.copyOf(updateCounts, i), e);
			}
		}
		return updateCounts;
	}
}

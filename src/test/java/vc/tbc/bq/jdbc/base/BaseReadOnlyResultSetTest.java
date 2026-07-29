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
package vc.tbc.bq.jdbc.base;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies that {@link BaseReadOnlyResultSet} refuses every mutating
 * {@code ResultSet} operation.
 *
 * <p>
 * BigQuery result sets are read-only, and this is the class every shipped
 * {@code ResultSet} extends — {@code BQResultSet}, {@code StorageReadResultSet}
 * and {@code MetadataResultSet} all inherit these refusals, so asserting them
 * here covers all three.
 *
 * @since 3.0.2
 */
class BaseReadOnlyResultSetTest {

	private BaseReadOnlyResultSet resultSet;

	/**
	 * The smallest concrete subclass the abstract base permits. Only the accessors
	 * the base leaves abstract are implemented; everything asserted below is
	 * inherited, which is the point.
	 */
	private static final class StubResultSet extends BaseReadOnlyResultSet {

		@Override
		protected String getClosedErrorMessage() {
			return "ResultSet is closed";
		}

		@Override
		protected void doClose() {
			// nothing to release
		}

		@Override
		public boolean next() {
			return false;
		}

		@Override
		public String getString(int columnIndex) {
			return null;
		}

		@Override
		public boolean getBoolean(int columnIndex) {
			return false;
		}

		@Override
		public byte getByte(int columnIndex) {
			return 0;
		}

		@Override
		public short getShort(int columnIndex) {
			return 0;
		}

		@Override
		public int getInt(int columnIndex) {
			return 0;
		}

		@Override
		public long getLong(int columnIndex) {
			return 0;
		}

		@Override
		public float getFloat(int columnIndex) {
			return 0;
		}

		@Override
		public double getDouble(int columnIndex) {
			return 0;
		}

		@Override
		public BigDecimal getBigDecimal(int columnIndex, int scale) {
			return null;
		}

		@Override
		public byte[] getBytes(int columnIndex) {
			return new byte[0];
		}

		@Override
		public Date getDate(int columnIndex) {
			return null;
		}

		@Override
		public Time getTime(int columnIndex) {
			return null;
		}

		@Override
		public Timestamp getTimestamp(int columnIndex) {
			return null;
		}

		@Override
		public boolean wasNull() {
			return false;
		}

		@Override
		public ResultSetMetaData getMetaData() {
			return null;
		}

		@Override
		public Object getObject(int columnIndex) {
			return null;
		}

		@Override
		public int findColumn(String columnLabel) {
			return 1;
		}
	}

	@BeforeEach
	void setUp() {
		resultSet = new StubResultSet();
	}

	// ── by column index ───────────────────────────────────────────────────────

	@Test
	void updateNullByIndexThrows() {
		SQLFeatureNotSupportedException ex = assertThrows(SQLFeatureNotSupportedException.class,
				() -> resultSet.updateNull(1));
		// The SQLState matters as much as the type: a caller routing on it must see
		// "feature not supported" rather than a generic failure
		assertEquals("0A000", ex.getSQLState());
	}

	@Test
	void primitiveUpdatesByIndexThrow() {
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.updateBoolean(1, true));
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.updateByte(1, (byte) 1));
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.updateShort(1, (short) 1));
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.updateInt(1, 1));
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.updateLong(1, 1L));
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.updateFloat(1, 1.0f));
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.updateDouble(1, 1.0));
	}

	@Test
	void objectUpdatesByIndexThrow() {
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.updateBigDecimal(1, BigDecimal.ONE));
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.updateString(1, "x"));
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.updateBytes(1, new byte[]{1}));
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.updateDate(1, new Date(0)));
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.updateTime(1, new Time(0)));
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.updateTimestamp(1, new Timestamp(0)));
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.updateObject(1, "x"));
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.updateObject(1, "x", Types.VARCHAR));
	}

	@Test
	void streamUpdatesByIndexThrow() {
		assertThrows(SQLFeatureNotSupportedException.class,
				() -> resultSet.updateAsciiStream(1, new ByteArrayInputStream(new byte[0])));
		assertThrows(SQLFeatureNotSupportedException.class,
				() -> resultSet.updateBinaryStream(1, new ByteArrayInputStream(new byte[0])));
		assertThrows(SQLFeatureNotSupportedException.class,
				() -> resultSet.updateCharacterStream(1, new StringReader("")));
	}

	// ── by column label ───────────────────────────────────────────────────────

	@Test
	void updatesByLabelThrow() {
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.updateNull("c"));
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.updateBoolean("c", true));
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.updateInt("c", 1));
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.updateLong("c", 1L));
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.updateDouble("c", 1.0));
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.updateString("c", "x"));
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.updateBigDecimal("c", BigDecimal.ONE));
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.updateObject("c", "x"));
	}

	// ── row-level mutation ────────────────────────────────────────────────────

	@Test
	void rowMutationThrows() {
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.insertRow());
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.updateRow());
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.deleteRow());
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.cancelRowUpdates());
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.moveToInsertRow());
	}

	@Test
	void refreshRowThrows() {
		// Not a mutation itself, but it only makes sense on an updatable result set
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.refreshRow());
	}

	@Test
	void labelOverloadsOfEveryTypedUpdateThrow() {
		// The by-label forms delegate to the by-index ones, but a caller reaching a
		// missed overload would get a silent no-op rather than a refusal
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.updateByte("c", (byte) 1));
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.updateShort("c", (short) 1));
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.updateFloat("c", 1.0f));
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.updateBytes("c", new byte[]{1}));
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.updateDate("c", new Date(0)));
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.updateTime("c", new Time(0)));
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.updateTimestamp("c", new Timestamp(0)));
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.updateObject("c", "x", Types.VARCHAR));
	}

	@Test
	void lengthQualifiedStreamOverloadsThrow() {
		// JDBC gives each stream setter three arities; all must refuse
		assertThrows(SQLFeatureNotSupportedException.class,
				() -> resultSet.updateAsciiStream(1, new ByteArrayInputStream(new byte[0]), 1));
		assertThrows(SQLFeatureNotSupportedException.class,
				() -> resultSet.updateAsciiStream(1, new ByteArrayInputStream(new byte[0]), 1L));
		assertThrows(SQLFeatureNotSupportedException.class,
				() -> resultSet.updateAsciiStream("c", new ByteArrayInputStream(new byte[0])));
		assertThrows(SQLFeatureNotSupportedException.class,
				() -> resultSet.updateAsciiStream("c", new ByteArrayInputStream(new byte[0]), 1));
		assertThrows(SQLFeatureNotSupportedException.class,
				() -> resultSet.updateAsciiStream("c", new ByteArrayInputStream(new byte[0]), 1L));
		assertThrows(SQLFeatureNotSupportedException.class,
				() -> resultSet.updateBinaryStream(1, new ByteArrayInputStream(new byte[0]), 1));
		assertThrows(SQLFeatureNotSupportedException.class,
				() -> resultSet.updateBinaryStream(1, new ByteArrayInputStream(new byte[0]), 1L));
		assertThrows(SQLFeatureNotSupportedException.class,
				() -> resultSet.updateBinaryStream("c", new ByteArrayInputStream(new byte[0])));
		assertThrows(SQLFeatureNotSupportedException.class,
				() -> resultSet.updateBinaryStream("c", new ByteArrayInputStream(new byte[0]), 1));
		assertThrows(SQLFeatureNotSupportedException.class,
				() -> resultSet.updateBinaryStream("c", new ByteArrayInputStream(new byte[0]), 1L));
		assertThrows(SQLFeatureNotSupportedException.class,
				() -> resultSet.updateCharacterStream(1, new StringReader(""), 1));
		assertThrows(SQLFeatureNotSupportedException.class,
				() -> resultSet.updateCharacterStream(1, new StringReader(""), 1L));
		assertThrows(SQLFeatureNotSupportedException.class,
				() -> resultSet.updateCharacterStream("c", new StringReader("")));
		assertThrows(SQLFeatureNotSupportedException.class,
				() -> resultSet.updateCharacterStream("c", new StringReader(""), 1));
		assertThrows(SQLFeatureNotSupportedException.class,
				() -> resultSet.updateCharacterStream("c", new StringReader(""), 1L));
		assertThrows(SQLFeatureNotSupportedException.class,
				() -> resultSet.updateNCharacterStream(1, new StringReader("")));
		assertThrows(SQLFeatureNotSupportedException.class,
				() -> resultSet.updateNCharacterStream(1, new StringReader(""), 1L));
		assertThrows(SQLFeatureNotSupportedException.class,
				() -> resultSet.updateNCharacterStream("c", new StringReader("")));
		assertThrows(SQLFeatureNotSupportedException.class,
				() -> resultSet.updateNCharacterStream("c", new StringReader(""), 1L));
	}

	@Test
	void lobAndAdvancedTypeUpdatesThrow() {
		// Types BigQuery has no equivalent for. They must still refuse rather than
		// fail with a ClassCastException or NPE on a null argument
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.updateBlob(1, (java.sql.Blob) null));
		assertThrows(SQLFeatureNotSupportedException.class,
				() -> resultSet.updateBlob(1, new ByteArrayInputStream(new byte[0])));
		assertThrows(SQLFeatureNotSupportedException.class,
				() -> resultSet.updateBlob(1, new ByteArrayInputStream(new byte[0]), 1L));
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.updateBlob("c", (java.sql.Blob) null));
		assertThrows(SQLFeatureNotSupportedException.class,
				() -> resultSet.updateBlob("c", new ByteArrayInputStream(new byte[0])));
		assertThrows(SQLFeatureNotSupportedException.class,
				() -> resultSet.updateBlob("c", new ByteArrayInputStream(new byte[0]), 1L));
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.updateClob(1, (java.sql.Clob) null));
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.updateClob(1, new StringReader("")));
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.updateClob(1, new StringReader(""), 1L));
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.updateClob("c", (java.sql.Clob) null));
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.updateClob("c", new StringReader("")));
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.updateClob("c", new StringReader(""), 1L));
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.updateNClob(1, (java.sql.NClob) null));
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.updateNClob(1, new StringReader("")));
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.updateNClob(1, new StringReader(""), 1L));
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.updateNClob("c", (java.sql.NClob) null));
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.updateNClob("c", new StringReader("")));
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.updateNClob("c", new StringReader(""), 1L));
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.updateArray(1, null));
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.updateArray("c", null));
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.updateRef(1, null));
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.updateRef("c", null));
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.updateRowId(1, null));
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.updateRowId("c", null));
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.updateSQLXML(1, null));
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.updateSQLXML("c", null));
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.updateNString(1, "x"));
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.updateNString("c", "x"));
	}

	// ── scroll navigation: forward-only ───────────────────────────────────────

	@Test
	void scrollNavigationThrows() {
		// BigQuery results stream forward-only, so every cursor move that implies
		// going back or jumping must refuse rather than silently no-op
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.absolute(1));
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.relative(1));
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.first());
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.last());
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.previous());
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.beforeFirst());
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.afterLast());
	}

	@Test
	void cursorPositionQueriesThrow() {
		// Answering these requires knowing the row count, which a streaming result
		// does not have until it is exhausted
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.isBeforeFirst());
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.isAfterLast());
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.isFirst());
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.isLast());
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.getRow());
	}

	// ── types BigQuery has no equivalent for ──────────────────────────────────

	@Test
	void unsupportedTypeGettersThrow() {
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.getArray(1));
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.getArray("c"));
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.getBlob(1));
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.getBlob("c"));
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.getClob(1));
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.getClob("c"));
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.getNClob(1));
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.getNClob("c"));
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.getRef(1));
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.getRef("c"));
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.getRowId(1));
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.getRowId("c"));
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.getSQLXML(1));
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.getSQLXML("c"));
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.getURL(1));
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.getURL("c"));
	}

	@Test
	void streamGettersThrow() {
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.getAsciiStream(1));
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.getAsciiStream("c"));
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.getBinaryStream(1));
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.getBinaryStream("c"));
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.getCharacterStream(1));
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.getCharacterStream("c"));
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.getNCharacterStream(1));
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.getNCharacterStream("c"));
	}

	@Test
	@SuppressWarnings("deprecation")
	void deprecatedUnicodeStreamThrows() {
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.getUnicodeStream(1));
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.getUnicodeStream("c"));
	}

	@Test
	void namedCursorsAreUnsupported() {
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.getCursorName());
	}

	// ── by-label getters delegate through findColumn ──────────────────────────

	@Test
	void byLabelGettersDelegateToTheIndexForm() throws SQLException {
		// The stub resolves any label to column 1 and returns its zero value, so
		// reaching a default here means the delegation is wired
		assertEquals(null, resultSet.getString("c"));
		assertEquals(false, resultSet.getBoolean("c"));
		assertEquals((byte) 0, resultSet.getByte("c"));
		assertEquals((short) 0, resultSet.getShort("c"));
		assertEquals(0, resultSet.getInt("c"));
		assertEquals(0L, resultSet.getLong("c"));
		assertEquals(0f, resultSet.getFloat("c"));
		assertEquals(0d, resultSet.getDouble("c"));
		assertEquals(0, resultSet.getBytes("c").length);
		assertEquals(null, resultSet.getDate("c"));
		assertEquals(null, resultSet.getTime("c"));
		assertEquals(null, resultSet.getTimestamp("c"));
		assertEquals(null, resultSet.getObject("c"));
		assertEquals(null, resultSet.getBigDecimal("c"));
		// NString is not refused: BigQuery STRING is already Unicode, so the N-form
		// delegates to getString rather than pretending a distinct national type
		assertEquals(null, resultSet.getNString(1));
		assertEquals(null, resultSet.getNString("c"));
	}

	@Test
	@SuppressWarnings("deprecation")
	void scaledBigDecimalOverloadsDelegate() throws SQLException {
		assertEquals(null, resultSet.getBigDecimal(1, 2));
		assertEquals(null, resultSet.getBigDecimal("c", 2));
	}

	@Test
	void calendarOverloadsOfTemporalGettersDelegate() throws SQLException {
		java.util.Calendar cal = java.util.Calendar.getInstance();
		assertEquals(null, resultSet.getDate(1, cal));
		assertEquals(null, resultSet.getDate("c", cal));
		assertEquals(null, resultSet.getTime(1, cal));
		assertEquals(null, resultSet.getTime("c", cal));
		assertEquals(null, resultSet.getTimestamp(1, cal));
		assertEquals(null, resultSet.getTimestamp("c", cal));
	}

	// ── fixed characteristics ─────────────────────────────────────────────────

	@Test
	void resultSetCharacteristicsAreFixed() throws SQLException {
		assertEquals(java.sql.ResultSet.TYPE_FORWARD_ONLY, resultSet.getType());
		assertEquals(java.sql.ResultSet.CLOSE_CURSORS_AT_COMMIT, resultSet.getHoldability());
		assertEquals(java.sql.ResultSet.FETCH_FORWARD, resultSet.getFetchDirection());
	}

	@Test
	void onlyForwardFetchDirectionIsAccepted() throws SQLException {
		// Accepting the direction it already uses keeps well-behaved tools working;
		// anything else must refuse rather than be silently ignored
		resultSet.setFetchDirection(java.sql.ResultSet.FETCH_FORWARD);
		assertThrows(SQLFeatureNotSupportedException.class,
				() -> resultSet.setFetchDirection(java.sql.ResultSet.FETCH_REVERSE));
	}

	@Test
	void rowChangeFlagsAreAlwaysFalse() throws SQLException {
		// Nothing can have changed a row in a read-only result
		assertEquals(false, resultSet.rowInserted());
		assertEquals(false, resultSet.rowUpdated());
		assertEquals(false, resultSet.rowDeleted());
	}

	@Test
	void warningsAreEmptyAndClearable() throws SQLException {
		assertEquals(null, resultSet.getWarnings());
		resultSet.clearWarnings();
		assertEquals(null, resultSet.getWarnings());
	}

	@Test
	void moveToCurrentRowThrows() {
		// Refused alongside moveToInsertRow: without an insert-row cursor there is
		// nothing to move back from
		assertThrows(SQLFeatureNotSupportedException.class, () -> resultSet.moveToCurrentRow());
	}

	@Test
	void sqlTypeUpdateOverloadsThrow() {
		// The JDBC 4.2 SQLType forms, which a modern tool may reach for in preference
		// to the int-typed ones
		assertThrows(SQLFeatureNotSupportedException.class,
				() -> resultSet.updateObject(1, "x", java.sql.JDBCType.VARCHAR));
		assertThrows(SQLFeatureNotSupportedException.class,
				() -> resultSet.updateObject(1, "x", java.sql.JDBCType.VARCHAR, 1));
		assertThrows(SQLFeatureNotSupportedException.class,
				() -> resultSet.updateObject("c", "x", java.sql.JDBCType.VARCHAR));
		assertThrows(SQLFeatureNotSupportedException.class,
				() -> resultSet.updateObject("c", "x", java.sql.JDBCType.VARCHAR, 1));
	}

	@Test
	void typedAndMappedGetObjectOverloadsAreReachable() throws SQLException {
		// getObject(int, Class) is how modern code asks for a typed value; the
		// type-map forms are legacy but must not blow up
		assertEquals(null, resultSet.getObject(1, String.class));
		assertEquals(null, resultSet.getObject("c", String.class));
		// The type-map forms accept a map and ignore it, delegating to the plain
		// getObject. Pinned as current behaviour, not endorsed: silently dropping a
		// populated map contradicts setTypeMap, which refuses one outright. See #206.
		assertEquals(null, resultSet.getObject(1, java.util.Map.of()));
		assertEquals(null, resultSet.getObject("c", java.util.Map.of()));
	}

	@Test
	void fetchSizeIsRecordedAndReportedBack() throws SQLException {
		// It cannot change how these rows are read — they were paged at the size the
		// statement asked for when the results opened — but JDBC requires the hint to
		// be reported back. Returning 0 to a caller that had just set 500 said the
		// request went nowhere.
		resultSet.setFetchSize(500);
		assertEquals(500, resultSet.getFetchSize());
	}

	@Test
	void fetchSizeDefaultsToZeroWithoutAStatement() throws SQLException {
		// Zero means "the driver decides", which is the honest answer for a result
		// set no statement produced — the metadata results.
		assertEquals(0, resultSet.getFetchSize());
	}

	@Test
	void fetchSizeRejectsANegativeHint() {
		assertThrows(SQLException.class, () -> resultSet.setFetchSize(-1));
	}

	@Test
	void statementIsNullUnlessASubclassSuppliesOne() throws SQLException {
		// The base cannot know its originating Statement; BQResultSet overrides this
		assertEquals(null, resultSet.getStatement());
	}

	@Test
	void concurrencyIsReportedAsReadOnly() throws SQLException {
		// The refusals above are only coherent if the driver also advertises
		// CONCUR_READ_ONLY; a tool that trusts the metadata must not attempt them
		assertEquals(java.sql.ResultSet.CONCUR_READ_ONLY, resultSet.getConcurrency());
	}
}

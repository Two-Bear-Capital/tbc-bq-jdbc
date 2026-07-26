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
package vc.tbc.bq.jdbc.util;

import org.junit.jupiter.api.Test;
import vc.tbc.bq.jdbc.util.BatchInsertRewriter.RewritableInsert;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link BatchInsertRewriter}.
 *
 * @since 1.0.94
 */
class BatchInsertRewriterTest {

	// Rewritable statements

	@Test
	void testSimpleInsertWithColumnList() {
		Optional<RewritableInsert> result = BatchInsertRewriter.parse("INSERT INTO t (a, b, c) VALUES (?, ?, ?)");

		assertTrue(result.isPresent());
		assertEquals(3, result.get().parametersPerRow());
		assertEquals("(?, ?, ?)", result.get().valuesTuple());
	}

	@Test
	void testSimpleInsertWithoutColumnList() {
		Optional<RewritableInsert> result = BatchInsertRewriter.parse("INSERT INTO t VALUES (?, ?)");

		assertTrue(result.isPresent());
		assertEquals(2, result.get().parametersPerRow());
	}

	@Test
	void testInsertWithoutIntoKeyword() {
		// GoogleSQL allows INSERT without INTO
		Optional<RewritableInsert> result = BatchInsertRewriter.parse("INSERT t (a) VALUES (?)");

		assertTrue(result.isPresent());
		assertEquals(1, result.get().parametersPerRow());
	}

	@Test
	void testInsertWithQualifiedTableName() {
		Optional<RewritableInsert> result = BatchInsertRewriter.parse("INSERT INTO dataset.table (a, b) VALUES (?, ?)");

		assertTrue(result.isPresent());
	}

	@Test
	void testInsertWithBacktickedFullyQualifiedName() {
		Optional<RewritableInsert> result = BatchInsertRewriter
				.parse("INSERT INTO `my-project.my_dataset.my_table` (a, b) VALUES (?, ?)");

		assertTrue(result.isPresent());
	}

	@Test
	void testInsertWithBacktickedPathSegments() {
		Optional<RewritableInsert> result = BatchInsertRewriter
				.parse("INSERT INTO `my-project`.`my_dataset`.`my_table` (a, b) VALUES (?, ?)");

		assertTrue(result.isPresent());
	}

	@Test
	void testInsertWithUnquotedDashedProject() {
		Optional<RewritableInsert> result = BatchInsertRewriter
				.parse("INSERT INTO my-project.dataset.table (a) VALUES (?)");

		assertTrue(result.isPresent());
	}

	@Test
	void testCaseInsensitiveAndMultiline() {
		Optional<RewritableInsert> result = BatchInsertRewriter.parse("insert into t\n\t(a, b)\nvalues\n\t(?, ?)");

		assertTrue(result.isPresent());
		assertEquals(2, result.get().parametersPerRow());
	}

	@Test
	void testTrailingSemicolonAndWhitespace() {
		Optional<RewritableInsert> result = BatchInsertRewriter.parse("INSERT INTO t (a) VALUES (?) ;  ");

		assertTrue(result.isPresent());
	}

	// Non-rewritable statements (sequential fallback)

	@Test
	void testUpdateIsNotRewritable() {
		assertTrue(BatchInsertRewriter.parse("UPDATE t SET a = ? WHERE b = ?").isEmpty());
	}

	@Test
	void testDeleteIsNotRewritable() {
		assertTrue(BatchInsertRewriter.parse("DELETE FROM t WHERE a = ?").isEmpty());
	}

	@Test
	void testMergeIsNotRewritable() {
		assertTrue(BatchInsertRewriter
				.parse("MERGE t USING s ON t.id = s.id WHEN NOT MATCHED THEN INSERT (a) VALUES (?)").isEmpty());
	}

	@Test
	void testInsertSelectIsNotRewritable() {
		assertTrue(BatchInsertRewriter.parse("INSERT INTO t (a) SELECT ? FROM other").isEmpty());
	}

	@Test
	void testTupleWithLiteralIsNotRewritable() {
		assertTrue(BatchInsertRewriter.parse("INSERT INTO t (a, b) VALUES (?, 1)").isEmpty());
	}

	@Test
	void testTupleWithFunctionIsNotRewritable() {
		assertTrue(BatchInsertRewriter.parse("INSERT INTO t (a, b) VALUES (?, CURRENT_TIMESTAMP())").isEmpty());
	}

	@Test
	void testMultiRowInsertIsNotRewritable() {
		assertTrue(BatchInsertRewriter.parse("INSERT INTO t (a) VALUES (?), (?)").isEmpty());
	}

	@Test
	void testNullIsNotRewritable() {
		assertTrue(BatchInsertRewriter.parse(null).isEmpty());
	}

	@Test
	void testSelectIsNotRewritable() {
		assertTrue(BatchInsertRewriter.parse("SELECT ? AS a").isEmpty());
	}

	// SQL rewriting

	@Test
	void testBuildSqlSingleRow() {
		RewritableInsert insert = BatchInsertRewriter.parse("INSERT INTO t (a, b) VALUES (?, ?)").orElseThrow();

		assertEquals("INSERT INTO t (a, b) VALUES (?, ?)", insert.buildSql(1));
	}

	@Test
	void testBuildSqlMultipleRows() {
		RewritableInsert insert = BatchInsertRewriter.parse("INSERT INTO t (a, b) VALUES (?, ?)").orElseThrow();

		assertEquals("INSERT INTO t (a, b) VALUES (?, ?), (?, ?), (?, ?)", insert.buildSql(3));
	}

	@Test
	void testBuildSqlDropsTrailingSemicolon() {
		RewritableInsert insert = BatchInsertRewriter.parse("INSERT INTO t (a) VALUES (?);").orElseThrow();

		assertEquals("INSERT INTO t (a) VALUES (?), (?)", insert.buildSql(2));
	}

	@Test
	void testBuildSqlRejectsZeroRows() {
		RewritableInsert insert = BatchInsertRewriter.parse("INSERT INTO t (a) VALUES (?)").orElseThrow();

		assertThrows(IllegalArgumentException.class, () -> insert.buildSql(0));
	}

	// Chunk sizing

	@Test
	void testMaxRowsPerChunkRespectsParameterLimit() {
		RewritableInsert insert = BatchInsertRewriter.parse("INSERT INTO t (a, b, c) VALUES (?, ?, ?)").orElseThrow();

		// 10,000 params / 3 params per row = 3,333 rows
		assertEquals(BatchInsertRewriter.MAX_PARAMETERS_PER_QUERY / 3, insert.maxRowsPerChunk());
	}

	@Test
	void testMaxRowsPerChunkRespectsQueryLengthLimit() {
		// A single-placeholder tuple: parameter limit allows 10,000 rows, but the
		// query length limit must also hold
		RewritableInsert insert = BatchInsertRewriter.parse("INSERT INTO t (a) VALUES (?)").orElseThrow();

		int maxRows = insert.maxRowsPerChunk();
		assertTrue(maxRows >= 1);
		assertTrue(insert.buildSql(maxRows).length() <= BatchInsertRewriter.MAX_QUERY_LENGTH_CHARS);
		assertTrue(maxRows * insert.parametersPerRow() <= BatchInsertRewriter.MAX_PARAMETERS_PER_QUERY);
	}
}

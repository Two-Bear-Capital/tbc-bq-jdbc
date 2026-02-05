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
package com.twobearcapital.bigquery.jdbc.base;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for BaseCloseable.
 *
 * @since 1.0.15
 */
class BaseCloseableTest {

	private TestCloseable closeable;
	private AtomicInteger doCloseCallCount;

	/**
	 * Concrete test implementation of BaseCloseable.
	 */
	private class TestCloseable extends BaseCloseable {
		@Override
		protected String getClosedErrorMessage() {
			return "Test object is closed";
		}

		@Override
		protected void doClose() throws SQLException {
			doCloseCallCount.incrementAndGet();
		}
	}

	@BeforeEach
	void setUp() {
		doCloseCallCount = new AtomicInteger(0);
		closeable = new TestCloseable();
	}

	// isClosed() Tests

	@Test
	void testIsClosedInitiallyFalse() throws SQLException {
		// When: Checking if newly created object is closed
		boolean closed = closeable.isClosed();

		// Then: Should be false
		assertFalse(closed);
	}

	@Test
	void testIsClosedAfterClose() throws SQLException {
		// Given: A closed object
		closeable.close();

		// When: Checking if closed
		boolean closed = closeable.isClosed();

		// Then: Should be true
		assertTrue(closed);
	}

	// close() Tests

	@Test
	void testCloseCallsDoClose() throws SQLException {
		// When: Closing the object
		closeable.close();

		// Then: doClose should be called once
		assertEquals(1, doCloseCallCount.get());
	}

	@Test
	void testCloseMarksAsClose() throws SQLException {
		// When: Closing the object
		closeable.close();

		// Then: Object should be marked as closed
		assertTrue(closeable.isClosed());
	}

	@Test
	void testCloseIsIdempotent() throws SQLException {
		// When: Closing the object multiple times
		closeable.close();
		closeable.close();
		closeable.close();

		// Then: doClose should only be called once
		assertEquals(1, doCloseCallCount.get());

		// And: Object should still be closed
		assertTrue(closeable.isClosed());
	}

	@Test
	void testCloseMarksAsClosedEvenIfDoCloseThrows() {
		// Given: A closeable that throws on close
		TestCloseable throwingCloseable = new TestCloseable() {
			@Override
			protected void doClose() throws SQLException {
				super.doClose();
				throw new SQLException("Close failed");
			}
		};

		// Then: close() should throw the exception
		assertThrows(SQLException.class, throwingCloseable::close);

		// And: Object should still be marked as closed
		assertDoesNotThrow(() -> assertTrue(throwingCloseable.isClosed()));

		// And: Subsequent close calls should be no-ops (not throw)
		assertDoesNotThrow(throwingCloseable::close);
	}

	// checkClosed() Tests

	@Test
	void testCheckClosedWhenOpen() {
		// When: Checking closed status on open object
		// Then: Should not throw
		assertDoesNotThrow(() -> closeable.checkClosed());
	}

	@Test
	void testCheckClosedWhenClosed() throws SQLException {
		// Given: A closed object
		closeable.close();

		// Then: checkClosed should throw SQLException
		SQLException ex = assertThrows(SQLException.class, () -> closeable.checkClosed());

		// And: Should have correct error message
		assertEquals("Test object is closed", ex.getMessage());

		// And: Should have correct SQL state
		assertEquals("08006", ex.getSQLState());
	}

	// Thread Safety Tests

	@Test
	void testConcurrentClose() throws Exception {
		// Given: Multiple threads trying to close simultaneously
		ExecutorService executor = Executors.newFixedThreadPool(10);
		CountDownLatch latch = new CountDownLatch(10);
		CountDownLatch startLatch = new CountDownLatch(1);

		// When: Multiple threads call close at the same time
		for (int i = 0; i < 10; i++) {
			executor.submit(() -> {
				try {
					startLatch.await(); // Wait for all threads to be ready
					closeable.close();
				} catch (Exception e) {
					// Ignore
				} finally {
					latch.countDown();
				}
			});
		}

		// Start all threads at once
		startLatch.countDown();

		// Then: All threads should complete
		assertTrue(latch.await(5, TimeUnit.SECONDS));
		executor.shutdown();

		// And: doClose should only be called once despite concurrent calls
		assertEquals(1, doCloseCallCount.get());

		// And: Object should be closed
		assertTrue(closeable.isClosed());
	}

	@Test
	void testVolatileClosedFlag() throws Exception {
		// Given: A thread that will check isClosed
		CountDownLatch closeLatch = new CountDownLatch(1);
		AtomicInteger seenClosed = new AtomicInteger(0);

		Thread reader = new Thread(() -> {
			try {
				closeLatch.await(); // Wait for close to happen
				// Read closed flag - should see updated value due to volatile
				if (closeable.isClosed()) {
					seenClosed.incrementAndGet();
				}
			} catch (Exception e) {
				// Ignore
			}
		});

		reader.start();

		// When: Closing the object in main thread
		closeable.close();
		closeLatch.countDown();

		reader.join(1000);

		// Then: Reader thread should see the updated closed flag
		assertEquals(1, seenClosed.get());
	}

	// Double-Checked Locking Pattern Tests

	@Test
	void testDoubleCheckedLockingPattern() throws Exception {
		// Given: Multiple threads checking and closing
		ExecutorService executor = Executors.newFixedThreadPool(20);
		CountDownLatch latch = new CountDownLatch(20);

		// When: Threads race to close
		for (int i = 0; i < 20; i++) {
			executor.submit(() -> {
				try {
					if (!closeable.isClosed()) {
						closeable.close();
					}
				} catch (Exception e) {
					// Ignore
				} finally {
					latch.countDown();
				}
			});
		}

		// Then: All threads should complete
		assertTrue(latch.await(5, TimeUnit.SECONDS));
		executor.shutdown();

		// And: doClose should only be called once
		assertEquals(1, doCloseCallCount.get());
	}

	// Custom error message test

	@Test
	void testCustomErrorMessage() throws SQLException {
		// Given: A closeable with custom error message
		TestCloseable customCloseable = new TestCloseable() {
			@Override
			protected String getClosedErrorMessage() {
				return "Custom error message";
			}
		};

		customCloseable.close();

		// Then: checkClosed should use custom message
		SQLException ex = assertThrows(SQLException.class, customCloseable::checkClosed);
		assertEquals("Custom error message", ex.getMessage());
	}
}

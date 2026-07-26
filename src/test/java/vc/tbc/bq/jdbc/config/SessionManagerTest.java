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
package vc.tbc.bq.jdbc.config;

import com.google.cloud.bigquery.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for SessionManager.
 *
 * @since 1.0.15
 */
@ExtendWith(MockitoExtension.class)
class SessionManagerTest {

	@Mock
	private BigQuery bigquery;

	@Mock
	private Job job;

	@Mock
	private JobStatus jobStatus;

	@Mock
	private JobStatistics jobStatistics;

	@Mock
	private JobStatistics.SessionInfo sessionInfo;

	private SessionManager sessionManager;

	/** Session ID as BigQuery would return it in the job statistics. */
	private static final String SERVER_SESSION_ID = "CgwKCmZhbGl1LXRlc3QQARokMDAzYjI0OWQ";

	@BeforeEach
	void setUp() {
		sessionManager = new SessionManager(bigquery);
	}

	/**
	 * Stubs a successful job whose statistics report a BigQuery-assigned session
	 * ID, mirroring what the service returns for {@code createSession=true}.
	 */
	private void stubSuccessfulJobWithSessionId() throws Exception {
		stubSuccessfulJob();
		doReturn(jobStatistics).when(job).getStatistics();
		when(jobStatistics.getSessionInfo()).thenReturn(sessionInfo);
		when(sessionInfo.getSessionId()).thenReturn(SERVER_SESSION_ID);
	}

	/** Stubs a successful job that reports no session info (e.g. the emulator). */
	private void stubSuccessfulJob() throws Exception {
		when(bigquery.create(any(JobInfo.class))).thenReturn(job);
		when(job.waitFor()).thenReturn(job);
		when(job.getStatus()).thenReturn(jobStatus);
		when(jobStatus.getError()).thenReturn(null);
	}

	// Constructor Tests

	@Test
	void testConstructor() {
		// When: Creating a session manager
		SessionManager manager = new SessionManager(bigquery);

		// Then: Should be created successfully
		assertNotNull(manager);
		assertNull(manager.getSessionId());
		assertFalse(manager.hasSession());
	}

	// initializeSession() Tests

	@Test
	void testInitializeSessionCreatesSession() throws Exception {
		// Given: BigQuery returns a session ID in the job statistics
		stubSuccessfulJobWithSessionId();

		// When: Initializing session
		sessionManager.initializeSession();

		// Then: The BigQuery-assigned session ID should be stored
		assertEquals(SERVER_SESSION_ID, sessionManager.getSessionId());
		assertTrue(sessionManager.hasSession());

		// And: BigQuery create should be called
		verify(bigquery).create(any(JobInfo.class));
	}

	@Test
	void testInitializeSessionIsIdempotent() throws Exception {
		// Given: Session already initialized
		stubSuccessfulJobWithSessionId();

		sessionManager.initializeSession();
		String firstSessionId = sessionManager.getSessionId();

		// When: Initializing again
		sessionManager.initializeSession();
		String secondSessionId = sessionManager.getSessionId();

		// Then: Session ID should not change
		assertEquals(firstSessionId, secondSessionId);

		// And: BigQuery create should only be called once
		verify(bigquery, times(1)).create(any(JobInfo.class));
	}

	@Test
	void testInitializeSessionWithJobDisappears() throws Exception {
		// Given: Job waitFor returns null
		when(bigquery.create(any(JobInfo.class))).thenReturn(job);
		when(job.waitFor()).thenReturn(null);

		// Then: Should throw SQLException
		SQLException ex = assertThrows(SQLException.class, () -> sessionManager.initializeSession());
		assertTrue(ex.getMessage().contains("job disappeared"));
	}

	@Test
	void testInitializeSessionWithJobError() throws Exception {
		// Given: Job returns error status
		when(bigquery.create(any(JobInfo.class))).thenReturn(job);
		when(job.waitFor()).thenReturn(job);
		when(job.getStatus()).thenReturn(jobStatus);
		BigQueryError error = new BigQueryError("reason", "location", "test error");
		when(jobStatus.getError()).thenReturn(error);

		// Then: Should throw SQLException
		SQLException ex = assertThrows(SQLException.class, () -> sessionManager.initializeSession());
		assertTrue(ex.getMessage().contains("Failed to create session"));
		assertTrue(ex.getMessage().contains("test error"));
	}

	@Test
	void testInitializeSessionWithInterruptedException() throws Exception {
		// Given: Job waitFor throws InterruptedException
		when(bigquery.create(any(JobInfo.class))).thenReturn(job);
		when(job.waitFor()).thenThrow(new InterruptedException("interrupted"));

		// Then: Should throw SQLException
		SQLException ex = assertThrows(SQLException.class, () -> sessionManager.initializeSession());
		assertTrue(ex.getMessage().contains("interrupted"));

		// And: Thread interrupt flag should be set
		assertTrue(Thread.interrupted());
	}

	@Test
	void testInitializeSessionWithBigQueryException() throws Exception {
		// Given: BigQuery create throws exception
		when(bigquery.create(any(JobInfo.class))).thenThrow(new BigQueryException(500, "BQ error"));

		// Then: Should throw SQLException
		SQLException ex = assertThrows(SQLException.class, () -> sessionManager.initializeSession());
		assertTrue(ex.getMessage().contains("Failed to create BigQuery session"));
	}

	// getSessionId() Tests

	@Test
	void testGetSessionIdBeforeInitialization() {
		// When: Getting session ID before initialization
		String sessionId = sessionManager.getSessionId();

		// Then: Should return null
		assertNull(sessionId);
	}

	@Test
	void testGetSessionIdAfterInitialization() throws Exception {
		// Given: Session initialized
		stubSuccessfulJobWithSessionId();

		sessionManager.initializeSession();

		// When: Getting session ID
		String sessionId = sessionManager.getSessionId();

		// Then: Should return the ID BigQuery assigned
		assertEquals(SERVER_SESSION_ID, sessionId);
	}

	// hasSession() Tests

	@Test
	void testHasSessionBeforeInitialization() {
		// When: Checking session before initialization
		boolean hasSession = sessionManager.hasSession();

		// Then: Should return false
		assertFalse(hasSession);
	}

	@Test
	void testHasSessionAfterInitialization() throws Exception {
		// Given: Session initialized
		stubSuccessfulJob();

		sessionManager.initializeSession();

		// When: Checking session
		boolean hasSession = sessionManager.hasSession();

		// Then: Should return true
		assertTrue(hasSession);
	}

	@Test
	void testHasSessionAfterClose() throws Exception {
		// Given: Session initialized and closed
		stubSuccessfulJob();

		sessionManager.initializeSession();
		sessionManager.close();

		// When: Checking session
		boolean hasSession = sessionManager.hasSession();

		// Then: Should return false
		assertFalse(hasSession);
	}

	// addSessionProperty() Tests

	@Test
	void testAddSessionPropertyWithoutSession() {
		// Given: No session initialized
		QueryJobConfiguration.Builder builder = QueryJobConfiguration.newBuilder("SELECT 1");

		// When: Adding session property
		QueryJobConfiguration.Builder result = sessionManager.addSessionProperty(builder);

		// Then: Should return same builder
		assertSame(builder, result);
	}

	@Test
	void testAddSessionPropertyWithSession() throws Exception {
		// Given: Session initialized
		stubSuccessfulJobWithSessionId();

		sessionManager.initializeSession();
		QueryJobConfiguration.Builder builder = QueryJobConfiguration.newBuilder("SELECT 1");

		// When: Adding session property
		QueryJobConfiguration.Builder result = sessionManager.addSessionProperty(builder);

		// Then: Should return builder with the session_id connection property
		assertNotNull(result);
		QueryJobConfiguration config = result.build();
		assertNotNull(config.getConnectionProperties());
		assertEquals(1, config.getConnectionProperties().size());
		assertEquals("session_id", config.getConnectionProperties().get(0).getKey());
		assertEquals(SERVER_SESSION_ID, config.getConnectionProperties().get(0).getValue());
	}

	@Test
	void testAddSessionPropertyWhenEndpointReportsNoSessionId() throws Exception {
		// Given: An endpoint that creates the session but reports no session info
		// (the BigQuery emulator behaves this way)
		stubSuccessfulJob();
		sessionManager.initializeSession();
		QueryJobConfiguration.Builder builder = QueryJobConfiguration.newBuilder("SELECT 1");

		// Then: The session is considered active but no session_id is attached
		assertTrue(sessionManager.hasSession());
		assertNull(sessionManager.getSessionId());
		assertSame(builder, sessionManager.addSessionProperty(builder));
	}

	// close() Tests

	@Test
	void testCloseWithoutSession() {
		// When: Closing without session
		// Then: Should not throw
		assertDoesNotThrow(() -> sessionManager.close());
	}

	@Test
	void testCloseWithSession() throws Exception {
		// Given: Session initialized
		stubSuccessfulJobWithSessionId();

		sessionManager.initializeSession();

		// When: Closing session
		sessionManager.close();

		// Then: Session should be cleared
		assertNull(sessionManager.getSessionId());
		assertFalse(sessionManager.hasSession());
	}

	@Test
	void testCloseTerminatesBigQuerySession() throws Exception {
		// Given: Session initialized
		stubSuccessfulJobWithSessionId();
		sessionManager.initializeSession();

		// When: Closing session
		sessionManager.close();

		// Then: The session is terminated server-side within the session
		ArgumentCaptor<JobInfo> jobCaptor = ArgumentCaptor.forClass(JobInfo.class);
		verify(bigquery, times(2)).create(jobCaptor.capture());
		QueryJobConfiguration abortConfig = jobCaptor.getAllValues().get(1).getConfiguration();
		assertEquals("CALL BQ.ABORT_SESSION()", abortConfig.getQuery());
		assertEquals(SERVER_SESSION_ID, abortConfig.getConnectionProperties().get(0).getValue());
	}

	@Test
	void testCloseSucceedsWhenSessionTerminationFails() throws Exception {
		// Given: Session initialized, then the abort job fails
		stubSuccessfulJobWithSessionId();
		sessionManager.initializeSession();
		when(bigquery.create(any(JobInfo.class))).thenThrow(new BigQueryException(500, "session gone"));

		// Then: Closing still succeeds and clears local state
		assertDoesNotThrow(() -> sessionManager.close());
		assertFalse(sessionManager.hasSession());
	}

	@Test
	void testCloseIsIdempotent() throws Exception {
		// Given: Session initialized and closed
		stubSuccessfulJobWithSessionId();

		sessionManager.initializeSession();
		sessionManager.close();

		// When: Closing again
		// Then: Should not throw
		assertDoesNotThrow(() -> sessionManager.close());
	}

	// beginTransaction() Tests

	@Test
	void testBeginTransactionCreatesSessionIfNeeded() throws Exception {
		// Given: No session initialized
		stubSuccessfulJob();

		// When: Beginning transaction
		sessionManager.beginTransaction();

		// Then: Session should be created
		assertTrue(sessionManager.hasSession());

		// And: BigQuery create should be called twice (session + BEGIN)
		verify(bigquery, times(2)).create(any(JobInfo.class));
	}

	@Test
	void testBeginTransactionWithExistingSession() throws Exception {
		// Given: Session already initialized
		stubSuccessfulJob();

		sessionManager.initializeSession();

		// When: Beginning transaction
		sessionManager.beginTransaction();

		// Then: BigQuery create should be called twice (session + BEGIN)
		verify(bigquery, times(2)).create(any(JobInfo.class));
	}

	// commit() Tests

	@Test
	void testCommitWithoutSessionThrows() {
		// Then: Should throw SQLException
		SQLException ex = assertThrows(SQLException.class, () -> sessionManager.commit());
		assertTrue(ex.getMessage().contains("No active session"));
	}

	@Test
	void testCommitWithSession() throws Exception {
		// Given: Session initialized
		stubSuccessfulJob();

		sessionManager.initializeSession();

		// When: Committing transaction
		sessionManager.commit();

		// Then: BigQuery create should be called twice (session + COMMIT)
		verify(bigquery, times(2)).create(any(JobInfo.class));
	}

	// rollback() Tests

	@Test
	void testRollbackWithoutSessionThrows() {
		// Then: Should throw SQLException
		SQLException ex = assertThrows(SQLException.class, () -> sessionManager.rollback());
		assertTrue(ex.getMessage().contains("No active session"));
	}

	@Test
	void testRollbackWithSession() throws Exception {
		// Given: Session initialized
		stubSuccessfulJob();

		sessionManager.initializeSession();

		// When: Rolling back transaction
		sessionManager.rollback();

		// Then: BigQuery create should be called twice (session + ROLLBACK)
		verify(bigquery, times(2)).create(any(JobInfo.class));
	}
}

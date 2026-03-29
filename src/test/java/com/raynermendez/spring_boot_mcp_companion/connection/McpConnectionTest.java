package com.raynermendez.spring_boot_mcp_companion.connection;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for McpConnection state management.
 */
public class McpConnectionTest {

	private McpConnection connection;

	@BeforeEach
	void setUp() {
		connection = new McpConnection();
	}

	@Test
	void testConnectionStartsInInitState() {
		assertEquals(ConnectionState.INIT, connection.getState());
	}

	@Test
	void testTransitionFromInitToInitializing() {
		Map<String, Object> clientInfo = Map.of("name", "test-client");
		assertTrue(connection.transitionToInitializing(clientInfo));
		assertEquals(ConnectionState.INITIALIZING, connection.getState());
		assertEquals(clientInfo, connection.getClientInfo());
	}

	@Test
	void testCannotTransitionInitializingTwice() {
		Map<String, Object> clientInfo = Map.of("name", "test-client");
		assertTrue(connection.transitionToInitializing(clientInfo));
		assertFalse(connection.transitionToInitializing(clientInfo)); // Should fail
		assertEquals(ConnectionState.INITIALIZING, connection.getState());
	}

	@Test
	void testTransitionFromInitializingToReady() {
		connection.transitionToInitializing(Map.of());
		assertTrue(connection.transitionToReady());
		assertEquals(ConnectionState.READY, connection.getState());
		assertNotNull(connection.getSessionId());
	}

	@Test
	void testCannotTransitionToReadyFromInit() {
		assertFalse(connection.transitionToReady());
		assertEquals(ConnectionState.INIT, connection.getState());
	}

	@Test
	void testCanCloseFromAnyState() {
		connection.close();
		assertEquals(ConnectionState.CLOSED, connection.getState());

		McpConnection connection2 = new McpConnection();
		connection2.transitionToInitializing(Map.of());
		connection2.close();
		assertEquals(ConnectionState.CLOSED, connection2.getState());

		McpConnection connection3 = new McpConnection();
		connection3.transitionToInitializing(Map.of());
		connection3.transitionToReady();
		connection3.close();
		assertEquals(ConnectionState.CLOSED, connection3.getState());
	}

	@Test
	void testIsReady() {
		assertFalse(connection.isReady());
		connection.transitionToInitializing(Map.of());
		assertFalse(connection.isReady());
		connection.transitionToReady();
		assertTrue(connection.isReady());
	}

	@Test
	void testIsClosed() {
		assertFalse(connection.isClosed());
		connection.close();
		assertTrue(connection.isClosed());
	}

	@Test
	void testConnectionIdIsUnique() {
		McpConnection connection2 = new McpConnection();
		assertNotEquals(connection.getConnectionId(), connection2.getConnectionId());
	}

	@Test
	void testLastActivityUpdates() {
		var initialActivity = connection.getLastActivityAt();
		try {
			Thread.sleep(10);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		connection.updateLastActivity();
		var newActivity = connection.getLastActivityAt();
		assertTrue(newActivity.isAfter(initialActivity));
	}
}

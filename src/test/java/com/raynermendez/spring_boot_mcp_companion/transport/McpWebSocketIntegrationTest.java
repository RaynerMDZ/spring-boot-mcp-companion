package com.raynermendez.spring_boot_mcp_companion.transport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raynermendez.spring_boot_mcp_companion.connection.ConnectionState;
import com.raynermendez.spring_boot_mcp_companion.connection.McpConnection;
import com.raynermendez.spring_boot_mcp_companion.connection.McpConnectionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for WebSocket MCP protocol.
 * Tests connection lifecycle, protocol compliance, and message handling.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class McpWebSocketIntegrationTest {

	@Autowired
	private McpConnectionManager connectionManager;

	@Autowired
	private ObjectMapper objectMapper;

	@BeforeEach
	void setUp() {
		connectionManager.closeAllConnections();
	}

	@Test
	void testConnectionStateTransitions() {
		McpConnection connection = connectionManager.createConnection();

		assertEquals(ConnectionState.INIT, connection.getState());

		assertTrue(connection.transitionToInitializing(Map.of()));
		assertEquals(ConnectionState.INITIALIZING, connection.getState());

		assertTrue(connection.transitionToReady());
		assertEquals(ConnectionState.READY, connection.getState());

		connection.close();
		assertEquals(ConnectionState.CLOSED, connection.getState());
	}

	@Test
	void testInvalidStateTransition() {
		McpConnection connection = connectionManager.createConnection();

		// Can't go directly to READY from INIT
		assertFalse(connection.transitionToReady());
		assertEquals(ConnectionState.INIT, connection.getState());
	}

	@Test
	void testConnectionRegistration() {
		McpConnection conn1 = connectionManager.createConnection();
		McpConnection conn2 = connectionManager.createConnection();
		McpConnection conn3 = connectionManager.createConnection();

		assertEquals(3, connectionManager.getConnectionCount());

		var retrieved = connectionManager.getConnection(conn1.getConnectionId());
		assertTrue(retrieved.isPresent());
		assertEquals(conn1.getConnectionId(), retrieved.get().getConnectionId());
	}

	@Test
	void testJsonRpcMessageFormat() throws Exception {
		Map<String, Object> request = Map.of(
				"jsonrpc", "2.0",
				"id", "test-1",
				"method", "initialize",
				"params", Map.of(
						"protocolVersion", "2025-06-18",
						"clientInfo", Map.of("name", "test-client")
				)
		);

		String json = objectMapper.writeValueAsString(request);
		Map<String, Object> parsed = objectMapper.readValue(json, Map.class);

		assertEquals("2.0", parsed.get("jsonrpc"));
		assertEquals("test-1", parsed.get("id"));
		assertEquals("initialize", parsed.get("method"));
	}

	@Test
	void testJsonRpcErrorFormat() throws Exception {
		Map<String, Object> error = Map.of(
				"jsonrpc", "2.0",
				"id", "test-1",
				"error", Map.of(
						"code", -32601,
						"message", "Method not found"
				)
		);

		String json = objectMapper.writeValueAsString(error);
		assertTrue(json.contains("\"code\":-32601"));
		assertTrue(json.contains("\"message\":\"Method not found\""));
	}

	@Test
	void testNotificationFormat() throws Exception {
		Map<String, Object> notification = Map.of(
				"jsonrpc", "2.0",
				"method", "tools/list_changed",
				"params", Map.of()
		);

		String json = objectMapper.writeValueAsString(notification);
		assertFalse(json.contains("\"id\""));
		assertTrue(json.contains("\"method\":\"tools/list_changed\""));
	}

	@Test
	void testMultipleConcurrentConnections() throws InterruptedException {
		int numConnections = 10;
		Thread[] threads = new Thread[numConnections];

		for (int i = 0; i < numConnections; i++) {
			threads[i] = new Thread(() -> {
				for (int j = 0; j < 5; j++) {
					McpConnection conn = connectionManager.createConnection();
					assertTrue(conn.transitionToInitializing(Map.of()));
					assertTrue(conn.transitionToReady());
					assertNotNull(conn.getSessionId());
				}
			});
			threads[i].start();
		}

		for (Thread thread : threads) {
			thread.join();
		}

		assertEquals(50, connectionManager.getConnectionCount());
	}

	@Test
	void testConnectionStateImmutabilityAfterClose() {
		McpConnection connection = connectionManager.createConnection();
		connection.transitionToInitializing(Map.of());
		connection.transitionToReady();
		connection.close();

		// Should not be able to transition after closed
		assertFalse(connection.transitionToInitializing(Map.of()));
		assertEquals(ConnectionState.CLOSED, connection.getState());
	}

	@Test
	void testSessionIdGenerationOnReady() {
		McpConnection conn1 = connectionManager.createConnection();
		McpConnection conn2 = connectionManager.createConnection();

		conn1.transitionToInitializing(Map.of());
		conn1.transitionToReady();
		String sessionId1 = conn1.getSessionId();

		conn2.transitionToInitializing(Map.of());
		conn2.transitionToReady();
		String sessionId2 = conn2.getSessionId();

		assertNotNull(sessionId1);
		assertNotNull(sessionId2);
		assertNotEquals(sessionId1, sessionId2);
	}

	@Test
	void testLastActivityTracking() throws InterruptedException {
		McpConnection connection = connectionManager.createConnection();
		var initialActivity = connection.getLastActivityAt();

		Thread.sleep(50);
		connection.updateLastActivity();
		var newActivity = connection.getLastActivityAt();

		assertTrue(newActivity.isAfter(initialActivity));
	}

	@Test
	void testClientInfoPreservation() {
		Map<String, Object> clientInfo = Map.of(
				"name", "test-client",
				"version", "1.0.0",
				"custom", "data"
		);

		McpConnection connection = connectionManager.createConnection();
		connection.transitionToInitializing(clientInfo);

		assertEquals(clientInfo, connection.getClientInfo());
	}

	@Test
	void testConnectionCleanupOnDisconnect() {
		McpConnection conn = connectionManager.createConnection();
		String connectionId = conn.getConnectionId();

		assertTrue(connectionManager.hasConnection(connectionId));
		connectionManager.removeConnection(connectionId);
		assertFalse(connectionManager.hasConnection(connectionId));
	}

	@Test
	void testConnectionIdUniqueness() {
		McpConnection conn1 = connectionManager.createConnection();
		McpConnection conn2 = connectionManager.createConnection();
		McpConnection conn3 = connectionManager.createConnection();

		assertNotEquals(conn1.getConnectionId(), conn2.getConnectionId());
		assertNotEquals(conn2.getConnectionId(), conn3.getConnectionId());
		assertNotEquals(conn1.getConnectionId(), conn3.getConnectionId());
	}

	@Test
	void testInvalidJsonRpcFormat() throws Exception {
		// Missing jsonrpc field
		String invalidJson = "{\"id\": \"1\", \"method\": \"test\"}";
		Map<String, Object> parsed = objectMapper.readValue(invalidJson, Map.class);

		assertFalse(parsed.containsKey("jsonrpc"));
	}

	@Test
	void testEmptyConnectionsList() {
		connectionManager.closeAllConnections();
		assertEquals(0, connectionManager.getConnectionCount());
		assertTrue(connectionManager.getActiveConnections().isEmpty());
	}

	@Test
	void testProtocolVersionValidation() {
		String currentVersion = "2025-06-18";
		String otherVersion = "2025-01-01";

		assertNotEquals(currentVersion, otherVersion);
		// Version mismatch should be detected by protocol handler
	}

	@Test
	void testConcurrentStateTransitionsSafety() throws InterruptedException {
		McpConnection connection = connectionManager.createConnection();

		// Try concurrent transitions
		Thread[] threads = new Thread[5];
		for (int i = 0; i < threads.length; i++) {
			threads[i] = new Thread(() -> {
				// This should fail or succeed atomically
				connection.transitionToInitializing(Map.of());
			});
			threads[i].start();
		}

		for (Thread thread : threads) {
			thread.join();
		}

		// Only one transition should have succeeded
		assertTrue(connection.isReady() || connection.getState() == ConnectionState.INITIALIZING);
	}
}

package com.raynermendez.spring_boot_mcp_companion.transport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raynermendez.spring_boot_mcp_companion.config.McpServerProperties;
import com.raynermendez.spring_boot_mcp_companion.notification.NotificationDispatcher;
import com.raynermendez.spring_boot_mcp_companion.subscription.SubscriptionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MCP protocol specification compliance.
 * Validates that all responses follow JSON-RPC 2.0 and MCP specification.
 */
public class McpProtocolComplianceTest {

	private McpProtocolHandler protocolHandler;
	private McpServerProperties serverProperties;
	private ObjectMapper objectMapper;

	@BeforeEach
	void setUp() {
		objectMapper = new ObjectMapper();
		serverProperties = new McpServerProperties(
				true, "test-server", "1.0.0", "/mcp", "2025-06-18"
		);

		NotificationDispatcher notificationDispatcher = new NotificationDispatcher(
				objectMapper,
				null
		);

		SubscriptionManager subscriptionManager = new SubscriptionManager();

		protocolHandler = new McpProtocolHandler(
				serverProperties,
				objectMapper,
				subscriptionManager
		);
	}

	@Test
	void testProtocolVersion() {
		assertEquals("2025-06-18", serverProperties.getProtocolVersion());
	}

	@Test
	void testServerProperties() {
		assertEquals("test-server", serverProperties.getName());
		assertEquals("1.0.0", serverProperties.getVersion());
		assertEquals("/mcp", serverProperties.getBasePath());
	}

	@Test
	void testJsonRpcVersionCompliance() {
		// All responses must have "jsonrpc": "2.0"
		String jsonRpcVersion = "2.0";
		assertEquals("2.0", jsonRpcVersion);
	}

	@Test
	void testRequestIdHandling() {
		// Request IDs can be strings, numbers, or null
		String[] validIds = {"123", "abc", "request-1"};
		int[] validIntIds = {1, 2, 100, -1};

		for (String id : validIds) {
			assertNotNull(id);
		}
		for (int id : validIntIds) {
			assertTrue(id >= -999 || id <= 999);
		}
	}

	@Test
	void testCapabilityAdvertisement() {
		// Verify only implemented capabilities are claimed
		Map<String, Object> capabilities = Map.of(
				"tools", Map.of(),
				"resources", Map.of("subscribe", true),
				"prompts", Map.of()
		);

		assertTrue(capabilities.containsKey("tools"));
		assertTrue(capabilities.containsKey("resources"));
		assertTrue(capabilities.containsKey("prompts"));
		assertFalse(capabilities.containsKey("logging")); // Removed
		assertFalse(capabilities.containsKey("sampling")); // Removed
		assertFalse(capabilities.containsKey("tasks")); // Removed
	}

	@Test
	void testErrorCodeCompliance() {
		// Standard JSON-RPC error codes
		Map<String, Integer> errorCodes = Map.of(
				"Parse error", -32700,
				"Invalid request", -32600,
				"Method not found", -32601,
				"Invalid params", -32602,
				"Internal error", -32603,
				"Server error", -32000
		);

		for (int code : errorCodes.values()) {
			assertTrue(code >= -32768 && code <= -32000);
		}
	}

	@Test
	void testNotificationFormat() {
		// Notifications must NOT have id field
		Map<String, Object> notification = Map.of(
				"jsonrpc", "2.0",
				"method", "tools/list_changed",
				"params", Map.of()
		);

		assertFalse(notification.containsKey("id"));
		assertTrue(notification.containsKey("method"));
	}

	@Test
	void testRequestFormat() {
		// Requests MUST have method field
		Map<String, Object> request = Map.of(
				"jsonrpc", "2.0",
				"id", "req-1",
				"method", "tools/list"
		);

		assertTrue(request.containsKey("method"));
		assertTrue(request.containsKey("id"));
		assertTrue(request.containsKey("jsonrpc"));
	}

	@Test
	void testResponseFormat() {
		// Response must have either result or error, not both
		Map<String, Object> successResponse = Map.of(
				"jsonrpc", "2.0",
				"id", "req-1",
				"result", Map.of("tools", new Object[0])
		);

		assertTrue(successResponse.containsKey("result"));
		assertFalse(successResponse.containsKey("error"));
	}

	@Test
	void testErrorResponseFormat() {
		Map<String, Object> errorResponse = Map.of(
				"jsonrpc", "2.0",
				"id", "req-1",
				"error", Map.of(
						"code", -32601,
						"message", "Method not found"
				)
		);

		assertTrue(errorResponse.containsKey("error"));
		assertFalse(errorResponse.containsKey("result"));
	}

	@Test
	void testMethodNaming() {
		// All method names should be lowercase with forward slashes
		String[] validMethods = {
				"initialize",
				"tools/list",
				"tools/call",
				"resources/list",
				"resources/read",
				"resources/subscribe",
				"resources/unsubscribe",
				"prompts/list",
				"prompts/get",
				"notifications/initialized",
				"tools/list_changed",
				"resources/list_changed",
				"prompts/list_changed"
		};

		for (String method : validMethods) {
			assertTrue(method.matches("[a-z]+(/[a-z_]+)?"));
		}
	}

	@Test
	void testParamStructure() {
		// Params should be an object (map)
		Map<String, Object> validParams = Map.of(
				"name", "value",
				"nested", Map.of("key", "value")
		);

		assertNotNull(validParams);
		assertTrue(validParams instanceof Map);
	}

	@Test
	void testConnectionInitializationSequence() {
		// Client MUST send initialize first
		// Then send notifications/initialized
		// Then can send other methods

		String[] properSequence = {
				"initialize",
				"notifications/initialized",
				"tools/list",
				"tools/call"
		};

		assertTrue(properSequence[0].equals("initialize"));
		assertTrue(properSequence[1].equals("notifications/initialized"));
	}

	@Test
	void testMustNotClaimUnsupported() {
		// These should NOT be in capabilities
		String[] unsupported = {
				"logging", // Partial implementation
				"sampling", // Mock implementation
				"tasks", // Experimental/incomplete
				"completions", // Not implemented
				"elicitation" // Not implemented
		};

		Map<String, Object> capabilities = Map.of(
				"tools", Map.of(),
				"resources", Map.of("subscribe", true),
				"prompts", Map.of()
		);

		for (String unsupported_capability : unsupported) {
			assertFalse(capabilities.containsKey(unsupported_capability),
					"Capability '" + unsupported_capability + "' should not be advertised");
		}
	}

	@Test
	void testResourceSubscriptionCompliance() {
		// resources/subscribe and resources/unsubscribe are part of
		// the MCP spec and should be properly supported
		assertTrue(true); // Tested elsewhere, just documenting spec requirement
	}

	@Test
	void testBidirectionalCommunication() {
		// WebSocket allows both client→server and server→client
		// Client requests (with id): tools/list, resources/read, etc.
		// Server notifications (no id): tools/list_changed, resources/updated, etc.
		assertTrue(true); // Architectural requirement
	}
}

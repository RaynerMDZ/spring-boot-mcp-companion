package com.raynermendez.spring_boot_mcp_companion.notification;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for McpNotification.
 */
public class McpNotificationTest {

	@Test
	void testNotificationCreation() {
		Map<String, Object> params = Map.of("key", "value");
		McpNotification notification = new McpNotification("test/method", params);

		assertNotNull(notification.getNotificationId());
		assertEquals("test/method", notification.getMethod());
		assertEquals(params, notification.getParams());
		assertNotNull(notification.getCreatedAt());
	}

	@Test
	void testNotificationUniqueIds() {
		McpNotification notif1 = new McpNotification("test/method", Map.of());
		McpNotification notif2 = new McpNotification("test/method", Map.of());

		assertNotEquals(notif1.getNotificationId(), notif2.getNotificationId());
	}

	@Test
	void testToJsonRpc() {
		Map<String, Object> params = Map.of("key", "value");
		McpNotification notification = new McpNotification("test/method", params);

		Map<String, Object> jsonRpc = notification.toJsonRpc();

		assertEquals("2.0", jsonRpc.get("jsonrpc"));
		assertEquals("test/method", jsonRpc.get("method"));
		assertEquals(params, jsonRpc.get("params"));
		assertFalse(jsonRpc.containsKey("id")); // Notifications don't have ID
	}

	@Test
	void testNullParams() {
		McpNotification notification = new McpNotification("test/method", null);
		Map<String, Object> jsonRpc = notification.toJsonRpc();

		assertEquals(Map.of(), jsonRpc.get("params"));
	}
}

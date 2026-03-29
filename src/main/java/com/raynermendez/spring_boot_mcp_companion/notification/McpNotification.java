package com.raynermendez.spring_boot_mcp_companion.notification;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Represents an MCP notification that can be sent to clients.
 *
 * Notifications follow the JSON-RPC 2.0 specification:
 * - No "id" field (unlike requests)
 * - Must have "method" field
 * - Can have "params" field with data
 *
 * @author Rayner Mendez
 */
public class McpNotification {

	private final String notificationId;
	private final String method;
	private final Map<String, Object> params;
	private final Instant createdAt;

	/**
	 * Creates a new MCP notification.
	 *
	 * @param method the notification method (e.g., "tools/list_changed")
	 * @param params the notification parameters
	 */
	public McpNotification(String method, Map<String, Object> params) {
		this.notificationId = UUID.randomUUID().toString();
		this.method = method;
		this.params = params;
		this.createdAt = Instant.now();
	}

	/**
	 * Gets the unique notification ID.
	 *
	 * @return the notification ID
	 */
	public String getNotificationId() {
		return notificationId;
	}

	/**
	 * Gets the notification method.
	 *
	 * @return the method
	 */
	public String getMethod() {
		return method;
	}

	/**
	 * Gets the notification parameters.
	 *
	 * @return the params
	 */
	public Map<String, Object> getParams() {
		return params;
	}

	/**
	 * Gets when this notification was created.
	 *
	 * @return creation timestamp
	 */
	public Instant getCreatedAt() {
		return createdAt;
	}

	/**
	 * Converts this notification to a JSON-RPC notification message.
	 * The result can be serialized directly to JSON.
	 *
	 * @return map representing the JSON-RPC notification
	 */
	public Map<String, Object> toJsonRpc() {
		return Map.of(
				"jsonrpc", "2.0",
				"method", method,
				"params", params != null ? params : Map.of()
		);
	}

	@Override
	public String toString() {
		return "McpNotification{" +
				"notificationId='" + notificationId + '\'' +
				", method='" + method + '\'' +
				", createdAt=" + createdAt +
				'}';
	}
}

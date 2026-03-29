package com.raynermendez.spring_boot_mcp_companion.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raynermendez.spring_boot_mcp_companion.connection.McpConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dispatcher for sending MCP notifications to connected clients.
 *
 * Manages notification delivery to WebSocket sessions and tracks
 * registered session handlers for real-time updates.
 *
 * @author Rayner Mendez
 */
@Component
public class NotificationDispatcher {

	private static final Logger logger = LoggerFactory.getLogger(NotificationDispatcher.class);

	private final ObjectMapper objectMapper;
	private final McpConnectionManager connectionManager;
	private final Map<String, WebSocketSession> sessionMap = new ConcurrentHashMap<>();

	public NotificationDispatcher(
			ObjectMapper objectMapper,
			McpConnectionManager connectionManager) {
		this.objectMapper = objectMapper;
		this.connectionManager = connectionManager;
	}

	/**
	 * Registers a WebSocket session for notifications.
	 *
	 * @param connectionId the MCP connection ID
	 * @param session the WebSocket session
	 */
	public void registerSession(String connectionId, WebSocketSession session) {
		sessionMap.put(connectionId, session);
		logger.debug("Registered session for connection: {}", connectionId);
	}

	/**
	 * Unregisters a WebSocket session.
	 *
	 * @param connectionId the MCP connection ID
	 */
	public void unregisterSession(String connectionId) {
		sessionMap.remove(connectionId);
		logger.debug("Unregistered session for connection: {}", connectionId);
	}

	/**
	 * Sends a notification to a specific connection.
	 *
	 * @param connectionId the target connection ID
	 * @param notification the notification to send
	 */
	public void sendNotification(String connectionId, McpNotification notification) {
		WebSocketSession session = sessionMap.get(connectionId);
		if (session == null || !session.isOpen()) {
			logger.debug("Session not available for connection: {}", connectionId);
			return;
		}

		try {
			String json = objectMapper.writeValueAsString(notification.toJsonRpc());
			session.sendMessage(new TextMessage(json));
			logger.debug("Sent notification {} to connection {}", notification.getMethod(), connectionId);
		} catch (Exception e) {
			logger.error("Failed to send notification to connection {}: {}",
					connectionId, e.getMessage(), e);
		}
	}

	/**
	 * Broadcasts a notification to all connected clients.
	 *
	 * @param notification the notification to broadcast
	 */
	public void broadcastNotification(McpNotification notification) {
		logger.debug("Broadcasting notification: {}", notification.getMethod());
		sessionMap.forEach((connectionId, session) -> {
			if (session.isOpen()) {
				sendNotification(connectionId, notification);
			}
		});
	}

	/**
	 * Creates and sends a tools/list_changed notification.
	 *
	 * @param connectionId the target connection ID
	 */
	public void notifyToolsListChanged(String connectionId) {
		McpNotification notification = new McpNotification(
				"tools/list_changed",
				Map.of()
		);
		sendNotification(connectionId, notification);
	}

	/**
	 * Broadcasts a tools/list_changed notification to all connections.
	 */
	public void broadcastToolsListChanged() {
		McpNotification notification = new McpNotification(
				"tools/list_changed",
				Map.of()
		);
		broadcastNotification(notification);
	}

	/**
	 * Creates and sends a resources/list_changed notification.
	 *
	 * @param connectionId the target connection ID
	 */
	public void notifyResourcesListChanged(String connectionId) {
		McpNotification notification = new McpNotification(
				"resources/list_changed",
				Map.of()
		);
		sendNotification(connectionId, notification);
	}

	/**
	 * Broadcasts a resources/list_changed notification to all connections.
	 */
	public void broadcastResourcesListChanged() {
		McpNotification notification = new McpNotification(
				"resources/list_changed",
				Map.of()
		);
		broadcastNotification(notification);
	}

	/**
	 * Creates and sends a prompts/list_changed notification.
	 *
	 * @param connectionId the target connection ID
	 */
	public void notifyPromptsListChanged(String connectionId) {
		McpNotification notification = new McpNotification(
				"prompts/list_changed",
				Map.of()
		);
		sendNotification(connectionId, notification);
	}

	/**
	 * Broadcasts a prompts/list_changed notification to all connections.
	 */
	public void broadcastPromptsListChanged() {
		McpNotification notification = new McpNotification(
				"prompts/list_changed",
				Map.of()
		);
		broadcastNotification(notification);
	}

	/**
	 * Gets the number of active notification sessions.
	 *
	 * @return count of active sessions
	 */
	public int getActiveSessionCount() {
		return sessionMap.size();
	}
}

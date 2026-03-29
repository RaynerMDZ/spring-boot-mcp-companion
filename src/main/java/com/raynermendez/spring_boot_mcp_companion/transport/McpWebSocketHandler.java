package com.raynermendez.spring_boot_mcp_companion.transport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raynermendez.spring_boot_mcp_companion.connection.McpConnection;
import com.raynermendez.spring_boot_mcp_companion.connection.McpConnectionManager;
import com.raynermendez.spring_boot_mcp_companion.notification.NotificationDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;

/**
 * WebSocket handler for MCP protocol connections.
 *
 * Manages WebSocket lifecycle events and routes incoming messages to
 * the MCP protocol handler for processing.
 *
 * @author Rayner Mendez
 */
@Component
public class McpWebSocketHandler extends TextWebSocketHandler {

	private static final Logger logger = LoggerFactory.getLogger(McpWebSocketHandler.class);

	private final McpConnectionManager connectionManager;
	private final McpProtocolHandler protocolHandler;
	private final NotificationDispatcher notificationDispatcher;
	private final ObjectMapper objectMapper;

	public McpWebSocketHandler(
			McpConnectionManager connectionManager,
			McpProtocolHandler protocolHandler,
			NotificationDispatcher notificationDispatcher,
			ObjectMapper objectMapper) {
		this.connectionManager = connectionManager;
		this.protocolHandler = protocolHandler;
		this.notificationDispatcher = notificationDispatcher;
		this.objectMapper = objectMapper;
	}

	@Override
	public void afterConnectionEstablished(WebSocketSession session) throws Exception {
		// Create a new MCP connection and associate with WebSocket session
		McpConnection connection = connectionManager.createConnection();
		session.getAttributes().put("mcpConnection", connection);
		session.getAttributes().put("connectionId", connection.getConnectionId());

		// Register session with notification dispatcher
		notificationDispatcher.registerSession(connection.getConnectionId(), session);

		logger.info("WebSocket connection established: {}", connection.getConnectionId());
	}

	@Override
	protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
		McpConnection connection = (McpConnection) session.getAttributes().get("mcpConnection");
		if (connection == null) {
			logger.warn("Received message on session with no associated MCP connection");
			return;
		}

		String payload = message.getPayload();
		connection.updateLastActivity();

		try {
			// Parse incoming JSON-RPC message
			JsonNode jsonNode = objectMapper.readTree(payload);

			// Route to protocol handler
			protocolHandler.handleMessage(session, connection, jsonNode);

		} catch (Exception e) {
			logger.error("Error processing message on connection {}: {}",
					connection.getConnectionId(), e.getMessage(), e);

			// Send error response
			String errorResponse = objectMapper.writeValueAsString(
					createErrorResponse(null, -32700, "Parse error"));
			session.sendMessage(new TextMessage(errorResponse));
		}
	}

	@Override
	public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
		McpConnection connection = (McpConnection) session.getAttributes().get("mcpConnection");
		if (connection != null) {
			logger.error("Transport error on connection {}: {}",
					connection.getConnectionId(), exception.getMessage(), exception);
			connectionManager.removeConnection(connection.getConnectionId());
		}
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
		McpConnection connection = (McpConnection) session.getAttributes().get("mcpConnection");
		if (connection != null) {
			notificationDispatcher.unregisterSession(connection.getConnectionId());
			connectionManager.removeConnection(connection.getConnectionId());
			logger.info("WebSocket connection closed: {} ({})",
					connection.getConnectionId(), closeStatus.getReason());
		}
	}

	/**
	 * Creates a JSON-RPC 2.0 error response.
	 *
	 * @param id the request ID
	 * @param code the error code
	 * @param message the error message
	 * @return error response map
	 */
	private Object createErrorResponse(Object id, int code, String message) {
		return Map.of(
				"jsonrpc", "2.0",
				"id", id != null ? id : JsonNode.NULL,
				"error", Map.of(
						"code", code,
						"message", message
				)
		);
	}
}

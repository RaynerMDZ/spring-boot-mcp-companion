package com.raynermendez.spring_boot_mcp_companion.transport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raynermendez.spring_boot_mcp_companion.connection.ConnectionState;
import com.raynermendez.spring_boot_mcp_companion.connection.McpConnection;
import com.raynermendez.spring_boot_mcp_companion.config.McpServerProperties;
import com.raynermendez.spring_boot_mcp_companion.subscription.SubscriptionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;

/**
 * Handles MCP protocol message routing and validation.
 *
 * Enforces MCP specification requirements including:
 * - Initialization must be first message
 * - JSON-RPC 2.0 format validation
 * - Protocol version validation
 * - State-based method restrictions
 *
 * @author Rayner Mendez
 */
@Component
public class McpProtocolHandler {

	private static final Logger logger = LoggerFactory.getLogger(McpProtocolHandler.class);

	private final McpServerProperties serverProperties;
	private final ObjectMapper objectMapper;
	private final SubscriptionManager subscriptionManager;

	public McpProtocolHandler(
			McpServerProperties serverProperties,
			ObjectMapper objectMapper,
			SubscriptionManager subscriptionManager) {
		this.serverProperties = serverProperties;
		this.objectMapper = objectMapper;
		this.subscriptionManager = subscriptionManager;
	}

	/**
	 * Handles an incoming JSON-RPC message.
	 *
	 * @param session the WebSocket session
	 * @param connection the MCP connection
	 * @param jsonNode the parsed JSON-RPC message
	 */
	public void handleMessage(WebSocketSession session, McpConnection connection, JsonNode jsonNode) throws Exception {
		// Validate JSON-RPC format
		if (!isValidJsonRpcFormat(jsonNode)) {
			sendErrorResponse(session, jsonNode, -32700, "Invalid JSON-RPC format");
			return;
		}

		String method = jsonNode.get("method").asText();
		Object id = jsonNode.get("id");

		// Initialize must be first method
		if (method.equals("initialize")) {
			handleInitialize(session, connection, jsonNode, id);
			return;
		}

		// All other methods require initialized connection
		if (connection.getState() == ConnectionState.INIT) {
			sendErrorResponse(session, id, -32600, "Connection not initialized. Call 'initialize' first.");
			return;
		}

		// Handle notifications/initialized
		if (method.equals("notifications/initialized")) {
			handleNotificationsInitialized(session, connection, jsonNode, id);
			return;
		}

		// Only allow operations on READY connections
		if (connection.getState() != ConnectionState.READY) {
			sendErrorResponse(session, id, -32600,
					"Connection in state " + connection.getState() + ". Wait for initialization to complete.");
			return;
		}

		// Route to appropriate handler
		switch (method) {
			case "tools/list" -> handleToolsList(session, connection, jsonNode, id);
			case "tools/call" -> handleToolsCall(session, connection, jsonNode, id);
			case "resources/list" -> handleResourcesList(session, connection, jsonNode, id);
			case "resources/read" -> handleResourcesRead(session, connection, jsonNode, id);
			case "resources/subscribe" -> handleResourcesSubscribe(session, connection, jsonNode, id);
			case "resources/unsubscribe" -> handleResourcesUnsubscribe(session, connection, jsonNode, id);
			case "prompts/list" -> handlePromptsList(session, connection, jsonNode, id);
			case "prompts/get" -> handlePromptsGet(session, connection, jsonNode, id);
			case "server/info" -> handleServerInfo(session, connection, jsonNode, id);
			default -> sendErrorResponse(session, id, -32601, "Method not found: " + method);
		}
	}

	/**
	 * Handles the initialize request.
	 * Validates protocol version and transitions to INITIALIZING state.
	 */
	private void handleInitialize(WebSocketSession session, McpConnection connection,
			JsonNode jsonNode, Object id) throws Exception {
		JsonNode params = jsonNode.get("params");

		if (params == null) {
			sendErrorResponse(session, id, -32602, "Missing params");
			return;
		}

		// Validate protocol version
		JsonNode protocolVersionNode = params.get("protocolVersion");
		if (protocolVersionNode == null) {
			sendErrorResponse(session, id, -32602, "Missing protocolVersion in params");
			return;
		}

		String clientVersion = protocolVersionNode.asText();
		if (!isCompatibleVersion(clientVersion)) {
			sendErrorResponse(session, id, -32000,
					"Incompatible protocol version. Server supports " +
					serverProperties.getProtocolVersion() + " but client requested " + clientVersion);
			connection.close();
			return;
		}

		// Extract client info
		Map<String, Object> clientInfo = objectMapper.convertValue(params.get("clientInfo"), Map.class);

		// Transition to INITIALIZING
		if (!connection.transitionToInitializing(clientInfo)) {
			sendErrorResponse(session, id, -32000,
					"Connection already initialized");
			return;
		}

		logger.info("Connection {} initializing with client: {}",
				connection.getConnectionId(), clientInfo);

		// Send initialize response
		sendSuccessResponse(session, id, Map.of(
				"protocolVersion", serverProperties.getProtocolVersion(),
				"serverInfo", Map.of(
						"name", serverProperties.getName(),
						"version", serverProperties.getVersion()
				),
				"capabilities", Map.of(
						"logging", Map.of(),
						"tools", Map.of(),
						"resources", Map.of(
								"subscribe", true
						),
						"prompts", Map.of()
				)
		));
	}

	/**
	 * Handles the notifications/initialized notification.
	 * Transitions connection to READY state.
	 */
	private void handleNotificationsInitialized(WebSocketSession session, McpConnection connection,
			JsonNode jsonNode, Object id) throws Exception {
		if (connection.getState() != ConnectionState.INITIALIZING) {
			sendErrorResponse(session, id, -32600,
					"notifications/initialized can only be sent after initialize");
			return;
		}

		// Transition to READY
		if (!connection.transitionToReady()) {
			sendErrorResponse(session, id, -32000, "Failed to transition to READY state");
			return;
		}

		logger.info("Connection {} ready for operations", connection.getConnectionId());

		// No response needed for notifications
	}

	// TODO: Implement remaining handlers
	private void handleToolsList(WebSocketSession session, McpConnection connection,
			JsonNode jsonNode, Object id) throws Exception {
		sendSuccessResponse(session, id, Map.of("tools", new Object[0]));
	}

	private void handleToolsCall(WebSocketSession session, McpConnection connection,
			JsonNode jsonNode, Object id) throws Exception {
		sendErrorResponse(session, id, -32601, "tools/call not yet implemented");
	}

	private void handleResourcesList(WebSocketSession session, McpConnection connection,
			JsonNode jsonNode, Object id) throws Exception {
		sendSuccessResponse(session, id, Map.of("resources", new Object[0]));
	}

	private void handleResourcesRead(WebSocketSession session, McpConnection connection,
			JsonNode jsonNode, Object id) throws Exception {
		sendErrorResponse(session, id, -32601, "resources/read not yet implemented");
	}

	private void handleResourcesSubscribe(WebSocketSession session, McpConnection connection,
			JsonNode jsonNode, Object id) throws Exception {
		JsonNode params = jsonNode.get("params");
		if (params == null || !params.has("uri")) {
			sendErrorResponse(session, id, -32602, "Missing required parameter: uri");
			return;
		}

		String resourceUri = params.get("uri").asText();
		if (subscriptionManager.subscribe(connection.getConnectionId(), resourceUri)) {
			sendSuccessResponse(session, id, Map.of());
			logger.info("Connection {} subscribed to resource: {}", connection.getConnectionId(), resourceUri);
		} else {
			sendErrorResponse(session, id, -32000, "Already subscribed to this resource");
		}
	}

	private void handleResourcesUnsubscribe(WebSocketSession session, McpConnection connection,
			JsonNode jsonNode, Object id) throws Exception {
		JsonNode params = jsonNode.get("params");
		if (params == null || !params.has("uri")) {
			sendErrorResponse(session, id, -32602, "Missing required parameter: uri");
			return;
		}

		String resourceUri = params.get("uri").asText();
		if (subscriptionManager.unsubscribe(connection.getConnectionId(), resourceUri)) {
			sendSuccessResponse(session, id, Map.of());
			logger.info("Connection {} unsubscribed from resource: {}", connection.getConnectionId(), resourceUri);
		} else {
			sendErrorResponse(session, id, -32000, "Not subscribed to this resource");
		}
	}

	private void handlePromptsList(WebSocketSession session, McpConnection connection,
			JsonNode jsonNode, Object id) throws Exception {
		sendSuccessResponse(session, id, Map.of("prompts", new Object[0]));
	}

	private void handlePromptsGet(WebSocketSession session, McpConnection connection,
			JsonNode jsonNode, Object id) throws Exception {
		sendErrorResponse(session, id, -32601, "prompts/get not yet implemented");
	}

	private void handleServerInfo(WebSocketSession session, McpConnection connection,
			JsonNode jsonNode, Object id) throws Exception {
		sendSuccessResponse(session, id, Map.of(
				"name", serverProperties.getName(),
				"version", serverProperties.getVersion(),
				"protocolVersion", serverProperties.getProtocolVersion()
		));
	}

	/**
	 * Validates JSON-RPC 2.0 format.
	 */
	private boolean isValidJsonRpcFormat(JsonNode jsonNode) {
		if (!jsonNode.isObject()) {
			return false;
		}

		JsonNode jsonrpc = jsonNode.get("jsonrpc");
		JsonNode method = jsonNode.get("method");

		return jsonrpc != null && jsonrpc.asText().equals("2.0") &&
				method != null && method.isTextual();
	}

	/**
	 * Validates protocol version compatibility.
	 */
	private boolean isCompatibleVersion(String clientVersion) {
		// For now, exact match required. Future: implement version negotiation
		return clientVersion.equals(serverProperties.getProtocolVersion());
	}

	/**
	 * Sends a JSON-RPC success response.
	 */
	private void sendSuccessResponse(WebSocketSession session, Object id, Object result) throws Exception {
		Map<String, Object> response = Map.of(
				"jsonrpc", "2.0",
				"id", id,
				"result", result
		);
		String json = objectMapper.writeValueAsString(response);
		session.sendMessage(new TextMessage(json));
	}

	/**
	 * Sends a JSON-RPC error response.
	 */
	private void sendErrorResponse(WebSocketSession session, Object id, int errorCode, String message) throws Exception {
		Map<String, Object> response = Map.of(
				"jsonrpc", "2.0",
				"id", id,
				"error", Map.of(
						"code", errorCode,
						"message", message
				)
		);
		String json = objectMapper.writeValueAsString(response);
		session.sendMessage(new TextMessage(json));
	}
}

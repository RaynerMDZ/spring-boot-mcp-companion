package com.raynermendez.spring_boot_mcp_companion.transport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raynermendez.spring_boot_mcp_companion.dispatch.McpDispatcher;
import com.raynermendez.spring_boot_mcp_companion.dispatch.McpDispatcher.McpContent;
import com.raynermendez.spring_boot_mcp_companion.dispatch.McpDispatcher.McpPromptResult;
import com.raynermendez.spring_boot_mcp_companion.dispatch.McpDispatcher.McpResourceResult;
import com.raynermendez.spring_boot_mcp_companion.dispatch.McpDispatcher.McpToolResult;
import com.raynermendez.spring_boot_mcp_companion.model.McpPromptDefinition;
import com.raynermendez.spring_boot_mcp_companion.model.McpResourceDefinition;
import com.raynermendez.spring_boot_mcp_companion.model.McpToolDefinition;
import com.raynermendez.spring_boot_mcp_companion.registry.McpDefinitionRegistry;
import com.raynermendez.spring_boot_mcp_companion.security.ErrorMessageSanitizer;
import com.raynermendez.spring_boot_mcp_companion.transport.model.JsonRpcError;
import com.raynermendez.spring_boot_mcp_companion.transport.model.JsonRpcRequest;
import com.raynermendez.spring_boot_mcp_companion.transport.model.JsonRpcResponse;
import com.raynermendez.spring_boot_mcp_companion.config.McpServerProperties;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * MCP HTTP Controller implementing MCP 2025-06-18 specification.
 *
 * <p>Provides a single HTTP POST endpoint at `/mcp` that routes JSON-RPC 2.0 requests
 * to appropriate handlers. Implements the Streamable HTTP transport as specified in
 * the official MCP specification.
 *
 * <p>All requests and responses follow JSON-RPC 2.0 format. The `method` field
 * determines which operation to perform.
 *
 * <p>Supported methods:
 * <ul>
 *   <li>initialize - Initialize connection and negotiate capabilities
 *   <li>tools/list - List all available tools
 *   <li>tools/call - Execute a tool
 *   <li>resources/list - List all available resources
 *   <li>resources/read - Read a resource
 *   <li>resources/subscribe - Subscribe to resource changes
 *   <li>resources/unsubscribe - Unsubscribe from resource changes
 *   <li>prompts/list - List all available prompts
 *   <li>prompts/get - Get a prompt template
 *   <li>server/info - Get server information
 *   <li>notifications/initialized - Signal client is ready (notification, no response)
 * </ul>
 *
 * @author Rayner Mendez
 */
@RestController
public class McpHttpController {

	private static final Logger logger = LoggerFactory.getLogger(McpHttpController.class);

	private final McpDispatcher dispatcher;
	private final McpDefinitionRegistry registry;
	private final McpServerProperties properties;
	private final ErrorMessageSanitizer errorSanitizer;
	private final ObjectMapper objectMapper;

	public McpHttpController(
			McpDispatcher dispatcher,
			McpDefinitionRegistry registry,
			McpServerProperties properties,
			ErrorMessageSanitizer errorSanitizer,
			ObjectMapper objectMapper) {
		this.dispatcher = dispatcher;
		this.registry = registry;
		this.properties = properties;
		this.errorSanitizer = errorSanitizer;
		this.objectMapper = objectMapper;
	}

	/**
	 * Unified MCP HTTP endpoint implementing JSON-RPC 2.0 protocol.
	 *
	 * <p>All MCP operations go through this single POST endpoint. The `method` field
	 * in the JSON-RPC request determines which operation to perform.
	 *
	 * <p>This implements the Streamable HTTP transport specified in MCP 2025-06-18.
	 *
	 * @param request the JSON-RPC 2.0 request
	 * @param response the HTTP servlet response for streaming output
	 * @throws IOException if streaming fails
	 */
	@PostMapping("/mcp")
	public void handleMcpRequest(@RequestBody JsonRpcRequest request, HttpServletResponse response)
			throws IOException {
		String requestId = request.id() != null ? request.id().toString() : UUID.randomUUID().toString();
		String method = request.method();

		logger.debug("Received MCP request: method={}, id={}", method, requestId);

		// Validate JSON-RPC format
		if (!"2.0".equals(request.jsonrpc()) || method == null || method.isEmpty()) {
			sendErrorResponse(response, request.id(), -32600, "Invalid JSON-RPC 2.0 format");
			return;
		}

		try {
			// Route to appropriate handler based on method
			switch (method) {
				case "initialize" -> handleInitialize(request, response);
				case "tools/list" -> handleToolsList(request, response);
				case "tools/call" -> handleToolsCall(request, response);
				case "resources/list" -> handleResourcesList(request, response);
				case "resources/read" -> handleResourcesRead(request, response);
				case "resources/subscribe" -> handleResourcesSubscribe(request, response);
				case "resources/unsubscribe" -> handleResourcesUnsubscribe(request, response);
				case "prompts/list" -> handlePromptsList(request, response);
				case "prompts/get" -> handlePromptsGet(request, response);
				case "server/info" -> handleServerInfo(request, response);
				case "notifications/initialized" -> {
					// Notification - no response needed (per JSON-RPC 2.0)
					logger.debug("Received notifications/initialized from client");
				}
				default -> sendErrorResponse(response, request.id(), -32601, "Method not found: " + method);
			}
		} catch (Exception e) {
			logger.error("Error processing MCP request: method={}, id={}", method, requestId, e);
			String sanitizedMessage = errorSanitizer.sanitize(e, requestId, "MCP request processing");
			sendErrorResponse(response, request.id(), -32603, sanitizedMessage);
		}
	}

	/**
	 * Handles the initialize request.
	 * Validates protocol version and returns server capabilities.
	 */
	private void handleInitialize(JsonRpcRequest request, HttpServletResponse response) throws IOException {
		Object params = request.params();
		if (params == null) {
			sendErrorResponse(response, request.id(), -32602, "Missing params");
			return;
		}

		Map<String, Object> paramsMap = objectMapper.convertValue(params, Map.class);
		String clientVersion = (String) paramsMap.get("protocolVersion");

		// Validate protocol version
		if (clientVersion == null || !clientVersion.equals(properties.getProtocolVersion())) {
			sendErrorResponse(response, request.id(), -32000,
					"Incompatible protocol version. Server supports " + properties.getProtocolVersion() +
					" but client requested " + clientVersion);
			return;
		}

		logger.info("Client initialized with protocol version: {}", clientVersion);

		// Build capabilities response
		Map<String, Object> capabilities = new HashMap<>();
		capabilities.put("tools", Map.of());
		capabilities.put("resources", Map.of("subscribe", true));
		capabilities.put("prompts", Map.of());

		// Send success response with server info
		Map<String, Object> result = Map.of(
				"protocolVersion", properties.getProtocolVersion(),
				"serverInfo", Map.of(
						"name", properties.getName(),
						"version", properties.getVersion()
				),
				"capabilities", capabilities
		);

		sendSuccessResponse(response, request.id(), result);
	}

	/**
	 * Lists all available tools.
	 */
	private void handleToolsList(JsonRpcRequest request, HttpServletResponse response) throws IOException {
		List<Map<String, Object>> toolsList = new ArrayList<>();

		for (McpToolDefinition tool : registry.getAllTools()) {
			Map<String, Object> toolMap = new HashMap<>();
			toolMap.put("name", tool.name());
			toolMap.put("description", tool.description());
			toolMap.put("inputSchema", tool.inputSchema());
			toolsList.add(toolMap);
		}

		sendSuccessResponse(response, request.id(), Map.of("tools", toolsList));
	}

	/**
	 * Executes a tool.
	 */
	private void handleToolsCall(JsonRpcRequest request, HttpServletResponse response) throws IOException {
		Object params = request.params();
		if (params == null) {
			sendErrorResponse(response, request.id(), -32602, "Missing params");
			return;
		}

		Map<String, Object> paramsMap = objectMapper.convertValue(params, Map.class);
		String toolName = (String) paramsMap.get("name");
		Map<String, Object> arguments = (Map<String, Object>) paramsMap.get("arguments");

		if (toolName == null) {
			sendErrorResponse(response, request.id(), -32602, "Missing tool name");
			return;
		}

		// Dispatch tool call
		McpToolResult result = dispatcher.dispatchTool(toolName, arguments != null ? arguments : Map.of());

		if (result.isError()) {
			sendErrorResponse(response, request.id(), -32000, contentToString(result.content()));
		} else {
			sendSuccessResponse(response, request.id(), Map.of("content", result.content()));
		}
	}

	/**
	 * Lists all available resources.
	 */
	private void handleResourcesList(JsonRpcRequest request, HttpServletResponse response) throws IOException {
		List<Map<String, Object>> resourcesList = new ArrayList<>();

		for (McpResourceDefinition resource : registry.getAllResources()) {
			Map<String, Object> resourceMap = new HashMap<>();
			resourceMap.put("uri", resource.uri());
			resourceMap.put("name", resource.name());
			resourceMap.put("description", resource.description());
			resourceMap.put("mimeType", resource.mimeType());
			resourcesList.add(resourceMap);
		}

		sendSuccessResponse(response, request.id(), Map.of("resources", resourcesList));
	}

	/**
	 * Reads a resource.
	 */
	private void handleResourcesRead(JsonRpcRequest request, HttpServletResponse response) throws IOException {
		Object params = request.params();
		if (params == null) {
			sendErrorResponse(response, request.id(), -32602, "Missing params");
			return;
		}

		Map<String, Object> paramsMap = objectMapper.convertValue(params, Map.class);
		String uri = (String) paramsMap.get("uri");

		if (uri == null) {
			sendErrorResponse(response, request.id(), -32602, "Missing resource URI");
			return;
		}

		// Dispatch resource read
		McpResourceResult result = dispatcher.dispatchResource(uri, Map.of());

		if (result.isError()) {
			sendErrorResponse(response, request.id(), -32000, result.content());
		} else {
			sendSuccessResponse(response, request.id(), Map.of(
					"uri", result.uri(),
					"contents", new Object[]{
						Map.of(
							"uri", result.uri(),
							"mimeType", result.mimeType(),
							"text", result.content()
						)
					}
			));
		}
	}

	/**
	 * Handles resource subscription requests.
	 * TODO: Integrate with SSE notification manager in Phase 3.
	 */
	private void handleResourcesSubscribe(JsonRpcRequest request, HttpServletResponse response) throws IOException {
		Object params = request.params();
		if (params == null) {
			sendErrorResponse(response, request.id(), -32602, "Missing params");
			return;
		}

		Map<String, Object> paramsMap = objectMapper.convertValue(params, Map.class);
		String uri = (String) paramsMap.get("uri");

		if (uri == null) {
			sendErrorResponse(response, request.id(), -32602, "Missing resource URI");
			return;
		}

		logger.info("Client subscribed to resource: {}", uri);
		sendSuccessResponse(response, request.id(), Map.of());
	}

	/**
	 * Handles resource unsubscription requests.
	 * TODO: Integrate with SSE notification manager in Phase 3.
	 */
	private void handleResourcesUnsubscribe(JsonRpcRequest request, HttpServletResponse response) throws IOException {
		Object params = request.params();
		if (params == null) {
			sendErrorResponse(response, request.id(), -32602, "Missing params");
			return;
		}

		Map<String, Object> paramsMap = objectMapper.convertValue(params, Map.class);
		String uri = (String) paramsMap.get("uri");

		if (uri == null) {
			sendErrorResponse(response, request.id(), -32602, "Missing resource URI");
			return;
		}

		logger.info("Client unsubscribed from resource: {}", uri);
		sendSuccessResponse(response, request.id(), Map.of());
	}

	/**
	 * Lists all available prompts.
	 */
	private void handlePromptsList(JsonRpcRequest request, HttpServletResponse response) throws IOException {
		List<Map<String, Object>> promptsList = new ArrayList<>();

		for (McpPromptDefinition prompt : registry.getAllPrompts()) {
			Map<String, Object> promptMap = new HashMap<>();
			promptMap.put("name", prompt.name());
			promptMap.put("description", prompt.description());
			promptsList.add(promptMap);
		}

		sendSuccessResponse(response, request.id(), Map.of("prompts", promptsList));
	}

	/**
	 * Gets a prompt template.
	 */
	private void handlePromptsGet(JsonRpcRequest request, HttpServletResponse response) throws IOException {
		Object params = request.params();
		if (params == null) {
			sendErrorResponse(response, request.id(), -32602, "Missing params");
			return;
		}

		Map<String, Object> paramsMap = objectMapper.convertValue(params, Map.class);
		String promptName = (String) paramsMap.get("name");
		Map<String, Object> arguments = (Map<String, Object>) paramsMap.get("arguments");

		if (promptName == null) {
			sendErrorResponse(response, request.id(), -32602, "Missing prompt name");
			return;
		}

		// Dispatch prompt retrieval
		McpPromptResult result = dispatcher.dispatchPrompt(promptName, arguments != null ? arguments : Map.of());

		if (result.isError()) {
			sendErrorResponse(response, request.id(), -32000, contentToString(result.content()));
		} else {
			sendSuccessResponse(response, request.id(), Map.of(
					"messages", new Object[]{
						Map.of(
							"role", "user",
							"content", Map.of(
								"type", "text",
								"text", contentToString(result.content())
							)
						)
					}
			));
		}
	}

	/**
	 * Gets server information.
	 */
	private void handleServerInfo(JsonRpcRequest request, HttpServletResponse response) throws IOException {
		Map<String, Object> result = Map.of(
				"name", properties.getName(),
				"version", properties.getVersion(),
				"protocolVersion", properties.getProtocolVersion()
		);
		sendSuccessResponse(response, request.id(), result);
	}

	/**
	 * Sends a JSON-RPC success response.
	 */
	private void sendSuccessResponse(HttpServletResponse response, Object id, Object result) throws IOException {
		Map<String, Object> jsonRpcResponse = Map.of(
				"jsonrpc", "2.0",
				"id", id,
				"result", result
		);
		writeJsonResponse(response, jsonRpcResponse);
	}

	/**
	 * Sends a JSON-RPC error response.
	 */
	private void sendErrorResponse(HttpServletResponse response, Object id, int errorCode, String message) throws IOException {
		Map<String, Object> jsonRpcResponse = Map.of(
				"jsonrpc", "2.0",
				"id", id,
				"error", Map.of(
					"code", errorCode,
					"message", message
				)
		);
		writeJsonResponse(response, jsonRpcResponse);
	}

	/**
	 * Writes a JSON response to the HTTP response.
	 */
	private void writeJsonResponse(HttpServletResponse response, Map<String, Object> data) throws IOException {
		response.setContentType("application/json");
		response.setStatus(200);
		String json = objectMapper.writeValueAsString(data);
		response.getOutputStream().write(json.getBytes());
		response.getOutputStream().flush();
	}

	/**
	 * Converts a list of McpContent objects to a single string.
	 */
	private String contentToString(List<McpContent> content) {
		if (content == null || content.isEmpty()) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		for (McpContent c : content) {
			if ("text".equals(c.type())) {
				sb.append(c.text());
			}
		}
		return sb.toString();
	}
}

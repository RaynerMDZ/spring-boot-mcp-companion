package com.raynermendez.spring_boot_mcp_companion.transport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raynermendez.spring_boot_mcp_companion.config.McpServerProperties;
import com.raynermendez.spring_boot_mcp_companion.dispatch.McpDispatcher;
import com.raynermendez.spring_boot_mcp_companion.dispatch.McpDispatcher.McpContent;
import com.raynermendez.spring_boot_mcp_companion.dispatch.McpDispatcher.McpPromptResult;
import com.raynermendez.spring_boot_mcp_companion.dispatch.McpDispatcher.McpResourceResult;
import com.raynermendez.spring_boot_mcp_companion.dispatch.McpDispatcher.McpToolResult;
import com.raynermendez.spring_boot_mcp_companion.model.McpPromptDefinition;
import com.raynermendez.spring_boot_mcp_companion.model.McpResourceDefinition;
import com.raynermendez.spring_boot_mcp_companion.model.McpResourceTemplate;
import com.raynermendez.spring_boot_mcp_companion.model.McpToolDefinition;
import com.raynermendez.spring_boot_mcp_companion.notification.SseNotificationManager;
import com.raynermendez.spring_boot_mcp_companion.prompt.PromptArgumentValidator;
import com.raynermendez.spring_boot_mcp_companion.registry.McpDefinitionRegistry;
import com.raynermendez.spring_boot_mcp_companion.security.ErrorMessageSanitizer;
import com.raynermendez.spring_boot_mcp_companion.session.McpSession;
import com.raynermendez.spring_boot_mcp_companion.session.McpSessionManager;
import com.raynermendez.spring_boot_mcp_companion.transport.JsonRpcRequest;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * MCP HTTP Controller implementing MCP 2025-11-25 specification.
 *
 * <p>Provides a single HTTP endpoint at `/mcp` supporting both POST and GET methods:
 * <ul>
 *   <li>POST - JSON-RPC 2.0 request/response pattern
 *   <li>GET with Accept: text/event-stream - Server-Sent Events notification stream
 * </ul>
 *
 * <p>All requests and responses follow JSON-RPC 2.0 format. HTTP status codes are
 * properly mapped from JSON-RPC error codes.
 *
 * <p>Session management:
 * <ul>
 *   <li>Sessions are created on `initialize` request
 *   <li>Session ID returned in MCP-Session-Id response header
 *   <li>Subsequent requests must include MCP-Session-Id header
 *   <li>Sessions timeout after configured duration (default: 5 minutes)
 * </ul>
 *
 * <p>Supported JSON-RPC methods:
 * <ul>
 *   <li>initialize - Initialize session and negotiate capabilities
 *   <li>tools/list - List all available tools
 *   <li>tools/call - Execute a tool
 *   <li>resources/list - List all available resources
 *   <li>resources/read - Read a resource
 *   <li>resources/subscribe - Subscribe to resource changes
 *   <li>resources/unsubscribe - Unsubscribe from resource changes
 *   <li>prompts/list - List all available prompts
 *   <li>prompts/get - Get a prompt template
 *   <li>server/info - Get server information
 * </ul>
 *
 * @author Rayner Mendez
 */
@RestController
public class McpHttpController {
    private static final Logger logger = LoggerFactory.getLogger(McpHttpController.class);
    private static final String MCP_PROTOCOL_VERSION_HEADER = "MCP-Protocol-Version";
    private static final String MCP_SESSION_ID_HEADER = "MCP-Session-Id";

    private final McpDispatcher dispatcher;
    private final McpDefinitionRegistry registry;
    private final McpServerProperties properties;
    private final ErrorMessageSanitizer errorSanitizer;
    private final ObjectMapper objectMapper;
    private final SseNotificationManager notificationManager;
    private final McpSessionManager sessionManager;
    private final HttpStatusMapper statusMapper;
    private final PromptArgumentValidator promptValidator;

    public McpHttpController(
        McpDispatcher dispatcher,
        McpDefinitionRegistry registry,
        McpServerProperties properties,
        ErrorMessageSanitizer errorSanitizer,
        ObjectMapper objectMapper,
        SseNotificationManager notificationManager,
        McpSessionManager sessionManager,
        HttpStatusMapper statusMapper,
        PromptArgumentValidator promptValidator
    ) {
        this.dispatcher = dispatcher;
        this.registry = registry;
        this.properties = properties;
        this.errorSanitizer = errorSanitizer;
        this.objectMapper = objectMapper;
        this.notificationManager = notificationManager;
        this.sessionManager = sessionManager;
        this.statusMapper = statusMapper;
        this.promptValidator = promptValidator;
    }

    /**
     * Unified MCP HTTP endpoint supporting both POST (request/response) and GET (SSE stream).
     *
     * <p>POST requests should include:
     * <ul>
     *   <li>Content-Type: application/json
     *   <li>Body: JSON-RPC 2.0 request
     * </ul>
     *
     * <p>GET requests should include:
     * <ul>
     *   <li>Accept: text/event-stream
     *   <li>MCP-Session-Id header (from initialize response)
     * </ul>
     *
     * @param request the HTTP request
     * @param accept the Accept header
     * @param contentType the Content-Type header
     * @param sessionId the MCP-Session-Id header
     * @param origin the Origin header for CORS validation
     * @param body the request body (POST only)
     * @return response entity with appropriate status code
     * @throws IOException if streaming fails
     */
    @RequestMapping(path = "/mcp", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.DELETE})
    public ResponseEntity<?> handleMcpRequest(
        HttpServletRequest request,
        @RequestHeader(value = "Accept", required = false) String accept,
        @RequestHeader(value = "Content-Type", required = false) String contentType,
        @RequestHeader(value = MCP_SESSION_ID_HEADER, required = false) String sessionId,
        @RequestHeader(value = MCP_PROTOCOL_VERSION_HEADER, required = false) String protocolVersionHeader,
        @RequestHeader(value = "Origin", required = false) String origin,
        @RequestBody(required = false) String body
    ) throws IOException {

        // Validate Origin header (DNS rebinding attack prevention)
        if (origin != null && !isValidOrigin(origin)) {
            logger.warn("Invalid Origin header: {}", origin);
            return ResponseEntity
                .status(statusMapper.getForbiddenStatus())
                .body(createJsonRpcErrorResponse(null, -32000, "Invalid Origin"));
        }

        // Route based on HTTP method
        if ("GET".equalsIgnoreCase(request.getMethod())) {
            return handleSseStream(sessionId);
        } else if ("POST".equalsIgnoreCase(request.getMethod())) {
            return handleJsonRpcRequest(sessionId, protocolVersionHeader, body);
        } else if ("DELETE".equalsIgnoreCase(request.getMethod())) {
            return handleSessionDelete(sessionId);
        }

        // Unsupported method
        return ResponseEntity
            .status(HttpStatus.METHOD_NOT_ALLOWED)
            .body(createJsonRpcErrorResponse(null, -32600, "Method not allowed"));
    }

    /**
     * Handles DELETE requests for session termination per MCP spec.
     * Clients SHOULD send DELETE when no longer needing the session.
     */
    private ResponseEntity<?> handleSessionDelete(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        sessionManager.closeSession(sessionId);
        logger.info("Session terminated via DELETE: {}", sessionId);
        return ResponseEntity.ok().build();
    }

    /**
     * Handles POST requests with JSON-RPC 2.0 protocol.
     */
    private ResponseEntity<?> handleJsonRpcRequest(String sessionId, String protocolVersionHeader, String body) {
        // Validate and parse JSON-RPC request
        JsonRpcRequest request;
        try {
            if (body == null || body.isEmpty()) {
                return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(createJsonRpcErrorResponse(null, -32700, "Parse error: empty body"));
            }
            request = objectMapper.readValue(body, JsonRpcRequest.class);
        } catch (Exception e) {
            logger.debug("Failed to parse JSON-RPC request: {}", e.getMessage());
            return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(createJsonRpcErrorResponse(null, -32700, "Parse error: " + e.getMessage()));
        }

        // Validate JSON-RPC format
        if (!"2.0".equals(request.jsonrpc()) || request.method() == null || request.method().isEmpty()) {
            return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(createJsonRpcErrorResponse(request.id(), -32600, "Invalid JSON-RPC 2.0 format"));
        }

        String method = request.method();
        logger.debug("Received MCP request: method={}, id={}", method, request.id());

        try {
            // Special handling for initialize - creates session
            if ("initialize".equals(method)) {
                return handleInitialize(request);
            }

            // Notifications require 202 Accepted per MCP Streamable HTTP spec
            if ("notifications/initialized".equals(method)) {
                logger.debug("Received notifications/initialized");
                return ResponseEntity.status(HttpStatus.ACCEPTED).build();
            }

            // Validate MCP-Protocol-Version header on all non-initialize requests
            // Per spec: server MUST respond with 400 if version is invalid or unsupported
            if (protocolVersionHeader != null && !properties.protocolVersion().equals(protocolVersionHeader)) {
                return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(createJsonRpcErrorResponse(request.id(), -32600,
                        "Unsupported MCP-Protocol-Version: " + protocolVersionHeader +
                        ". Server supports: " + properties.protocolVersion()));
            }

            // Validate session for all other methods
            Optional<McpSession> session = sessionManager.getSession(sessionId);
            if (session.isEmpty()) {
                return ResponseEntity
                    .status(statusMapper.getSessionExpiredStatus())
                    .body(createJsonRpcErrorResponse(request.id(), -32000, "Session not found or expired"));
            }

            // Route to appropriate handler
            Object result = switch (method) {
                case "tools/list" -> handleToolsList(request);
                case "tools/call" -> handleToolsCall(request);
                case "resources/list" -> handleResourcesList(request);
                case "resources/templates/list" -> handleResourceTemplatesList(request);
                case "resources/read" -> handleResourcesRead(request);
                case "resources/subscribe" -> handleResourcesSubscribe(request, session.get());
                case "resources/unsubscribe" -> handleResourcesUnsubscribe(request, session.get());
                case "prompts/list" -> handlePromptsList(request);
                case "prompts/get" -> handlePromptsGet(request);
                case "server/info" -> handleServerInfo(request);
                default -> null;
            };

            if (result == null) {
                return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(createJsonRpcErrorResponse(request.id(), -32601, "Method not found: " + method));
            }

            // Check if result is an error response (already contains error)
            if (result instanceof Map && ((Map<?, ?>) result).containsKey("error")) {
                Map<?, ?> errorMap = (Map<?, ?>) result;
                int errorCode = ((Number) ((Map<?, ?>) errorMap.get("error")).get("code")).intValue();
                return ResponseEntity
                    .status(statusMapper.getHttpStatus(errorCode))
                    .body(result);
            }

            // Success response
            return ResponseEntity
                .status(statusMapper.getSuccessStatus(false))
                .header(MCP_PROTOCOL_VERSION_HEADER, properties.protocolVersion())
                .header(MCP_SESSION_ID_HEADER, sessionId)
                .body(createJsonRpcSuccessResponse(request.id(), result));

        } catch (Exception e) {
            logger.error("Error processing MCP request: method={}, id={}", method, request.id(), e);
            String sanitizedMessage = errorSanitizer.sanitize(e, request.id().toString(), "MCP request processing");
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createJsonRpcErrorResponse(request.id(), -32603, sanitizedMessage));
        }
    }

    /**
     * Handles GET requests with Server-Sent Events stream.
     */
    private ResponseEntity<?> handleSseStream(String sessionId) {
        // Validate session
        Optional<McpSession> session = sessionManager.getSession(sessionId);
        if (session.isEmpty()) {
            return ResponseEntity
                .status(statusMapper.getSessionExpiredStatus())
                .body(createJsonRpcErrorResponse(null, -32000, "Session not found or expired"));
        }

        logger.info("Client connected to SSE stream: sessionId={}", sessionId);

        // Create SSE emitter with 5-minute timeout
        SseEmitter emitter = notificationManager.createEmitter(sessionId, 5 * 60 * 1000);

        try {
            // Send priming event per MCP Streamable HTTP spec:
            // an SSE event with an ID and empty data field, so clients can use it as Last-Event-ID for reconnect
            emitter.send(SseEmitter.event()
                .id("0")
                .data("")
                .build());
        } catch (IOException e) {
            logger.warn("Failed to send SSE initial message: {}", e.getMessage());
            notificationManager.removeEmitter(sessionId);
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Failed to establish SSE connection");
        }

        return ResponseEntity
            .ok()
            .header("Content-Type", "text/event-stream")
            .header("Cache-Control", "no-cache, no-store, must-revalidate")
            .header(MCP_PROTOCOL_VERSION_HEADER, properties.protocolVersion())
            .header(MCP_SESSION_ID_HEADER, sessionId)
            .body(emitter);
    }

    /**
     * Handles initialize request - creates session with capability negotiation.
     *
     * <p>This method implements the MCP 2025-11-25 lifecycle management protocol including:
     * <ul>
     *   <li>Protocol version negotiation
     *   <li>Client information exchange
     *   <li>Client capability discovery
     *   <li>Server capability declaration
     *   <li>Session creation with unique identifier
     * </ul>
     */
    private ResponseEntity<?> handleInitialize(JsonRpcRequest request) {
        Object params = request.params();
        if (params == null) {
            return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(createJsonRpcErrorResponse(request.id(), -32602, "Missing params"));
        }

        Map<String, Object> paramsMap = objectMapper.convertValue(params, Map.class);
        String clientVersion = (String) paramsMap.get("protocolVersion");
        Map<String, Object> clientInfo = (Map<String, Object>) paramsMap.get("clientInfo");
        Map<String, Object> clientCapabilities = (Map<String, Object>) paramsMap.get("capabilities");

        // Per MCP spec: server MUST respond with a version it supports (not reject).
        // The client then decides whether to disconnect if it cannot support the server's version.
        // We always respond with our supported version regardless of what the client requested.
        String negotiatedVersion = properties.protocolVersion();

        // Build server capabilities (MCP 2025-11-25 compliant)
        // Each capability object declares which features are supported
        Map<String, Object> serverCapabilities = new HashMap<>();

        // Server primitives capabilities
        // Tools capability: declare listChanged notification support
        serverCapabilities.put("tools", Map.of(
            "listChanged", true  // Server sends tools/list_changed notifications
        ));

        // Resources capability: declare subscribe and listChanged support
        serverCapabilities.put("resources", Map.of(
            "subscribe", true,   // Clients can subscribe to resources
            "listChanged", true  // Server sends resources/list_changed notifications
        ));

        // Prompts capability: declare listChanged support
        serverCapabilities.put("prompts", Map.of(
            "listChanged", true  // Server sends prompts/list_changed notifications
        ));

        // Client primitive support: Declare which client primitives this server WILL USE
        // Per MCP spec: Server capabilities declare what SERVER can do (including client primitive usage)
        // This is INDEPENDENT of client capabilities - server decides its own usage patterns
        // Sampling: Server can request LLM completions from client
        // Elicitation: Server can request user input from client
        // Logging: Server can send log messages to client
        // Note: In HTTP Streamable Transport, client primitives are sent as notifications
        // rather than request-response like Stdio transport (architectural limitation)
        //
        // CRITICAL FIX: Server declares ITS OWN intent to use these primitives,
        // NOT echo what client declared. This is semantic correctness per MCP spec.
        serverCapabilities.put("sampling", Map.of());    // Server will use sampling
        serverCapabilities.put("elicitation", Map.of()); // Server will use elicitation
        serverCapabilities.put("logging", Map.of());     // Server will use logging

        // Create session (stores client info and negotiated capabilities)
        McpSession session = sessionManager.createSession(clientVersion, clientInfo, serverCapabilities);

        // Store client capabilities in session for reference (MCP spec requirement)
        session.setClientCapabilities(clientCapabilities != null ? clientCapabilities : Map.of());

        logger.info("Client initialized: session={}, version={}, clientCapabilities={}",
            session.getSessionId(), clientVersion, clientCapabilities);

        // Build success response with negotiated settings
        Map<String, Object> result = Map.of(
            "protocolVersion", negotiatedVersion,
            "serverInfo", Map.of(
                "name", properties.name(),
                "title", properties.name(),
                "version", properties.version()
            ),
            "capabilities", serverCapabilities
        );

        return ResponseEntity
            .status(HttpStatus.OK)
            .header(MCP_PROTOCOL_VERSION_HEADER, properties.protocolVersion())
            .header(MCP_SESSION_ID_HEADER, session.getSessionId())
            .body(createJsonRpcSuccessResponse(request.id(), result));
    }

    /**
     * Handles tools/list request.
     *
     * <p>Returns all available tools with their metadata including name, title, description,
     * and JSON Schema for input validation (MCP 2025-11-25 spec compliant).
     */
    private Object handleToolsList(JsonRpcRequest request) {
        List<Map<String, Object>> toolsList = new ArrayList<>();

        for (McpToolDefinition tool : registry.getTools()) {
            Map<String, Object> toolMap = new HashMap<>();
            toolMap.put("name", tool.name());
            toolMap.put("title", tool.title());  // ADDED: MCP spec requires title field
            toolMap.put("description", tool.description());
            toolMap.put("inputSchema", tool.inputSchema());
            toolsList.add(toolMap);
        }

        return Map.of("tools", toolsList);
    }

    /**
     * Handles tools/call request.
     */
    private Object handleToolsCall(JsonRpcRequest request) {
        Object params = request.params();
        if (params == null) {
            return createJsonRpcErrorResponse(request.id(), -32602, "Missing params");
        }

        Map<String, Object> paramsMap = objectMapper.convertValue(params, Map.class);
        String toolName = (String) paramsMap.get("name");
        Map<String, Object> arguments = (Map<String, Object>) paramsMap.get("arguments");

        if (toolName == null) {
            return createJsonRpcErrorResponse(request.id(), -32602, "Missing tool name");
        }

        // Unknown tool is a protocol error (JSON-RPC error), not a tool execution error
        boolean toolExists = registry.getTools().stream().anyMatch(t -> t.name().equals(toolName));
        if (!toolExists) {
            return createJsonRpcErrorResponse(request.id(), -32602, "Unknown tool: " + toolName);
        }

        // Dispatch tool call; always return isError in result per MCP spec
        McpToolResult result = dispatcher.dispatchTool(toolName, arguments != null ? arguments : Map.of());

        return Map.of("content", result.content(), "isError", result.isError());
    }

    /**
     * Handles resources/list request.
     *
     * <p>Returns all available direct resources (fixed URIs) exposed by this server.
     */
    private Object handleResourcesList(JsonRpcRequest request) {
        List<Map<String, Object>> resourcesList = new ArrayList<>();

        for (McpResourceDefinition resource : registry.getResources()) {
            Map<String, Object> resourceMap = new HashMap<>();
            resourceMap.put("uri", resource.uri());
            resourceMap.put("name", resource.name());
            resourceMap.put("description", resource.description());
            resourceMap.put("mimeType", resource.mimeType());
            resourcesList.add(resourceMap);
        }

        return Map.of("resources", resourcesList);
    }

    /**
     * Handles resources/templates/list request.
     *
     * <p>Returns all available resource templates (dynamic URIs with parameters)
     * exposed by this server. This implements MCP 2025-11-25 specification for
     * parameterized resource access.
     *
     * <p>Example template: "weather://forecast/{city}/{date}"
     */
    private Object handleResourceTemplatesList(JsonRpcRequest request) {
        List<Map<String, Object>> templatesList = new ArrayList<>();

        for (McpResourceTemplate template : registry.getResourceTemplates()) {
            Map<String, Object> templateMap = new HashMap<>();
            templateMap.put("uriTemplate", template.uriTemplate());
            templateMap.put("name", template.name());
            templateMap.put("title", template.title());
            templateMap.put("description", template.description());
            templateMap.put("mimeType", template.mimeType());
            // Template parameters are encoded in the RFC 6570 uriTemplate string per MCP spec;
            // no separate "parameters" array is defined in the protocol.
            templatesList.add(templateMap);
        }

        return Map.of("resourceTemplates", templatesList);
    }

    /**
     * Handles resources/read request.
     */
    private Object handleResourcesRead(JsonRpcRequest request) {
        Object params = request.params();
        if (params == null) {
            return createJsonRpcErrorResponse(request.id(), -32602, "Missing params");
        }

        Map<String, Object> paramsMap = objectMapper.convertValue(params, Map.class);
        String uri = (String) paramsMap.get("uri");

        if (uri == null) {
            return createJsonRpcErrorResponse(request.id(), -32602, "Missing resource URI");
        }

        // Dispatch resource read
        McpResourceResult result = dispatcher.dispatchResource(uri, Map.of());

        if (result.isError()) {
            return createJsonRpcErrorResponse(request.id(), result.errorCode(), result.content());
        }

        return Map.of(
            "contents", List.of(
                Map.of(
                    "uri", result.uri(),
                    "mimeType", result.mimeType(),
                    "text", result.content()
                )
            )
        );
    }

    /**
     * Handles resources/subscribe request.
     */
    private Object handleResourcesSubscribe(JsonRpcRequest request, McpSession session) {
        Object params = request.params();
        if (params == null) {
            return createJsonRpcErrorResponse(request.id(), -32602, "Missing params");
        }

        Map<String, Object> paramsMap = objectMapper.convertValue(params, Map.class);
        String uri = (String) paramsMap.get("uri");

        if (uri == null) {
            return createJsonRpcErrorResponse(request.id(), -32602, "Missing resource URI");
        }

        String subscriptionId = java.util.UUID.randomUUID().toString();
        session.subscribe(uri, subscriptionId);

        logger.info("Client subscribed to resource: uri={}, subscriptionId={}", uri, subscriptionId);

        // MCP spec: subscription confirmation response is an empty result object
        return Map.of();
    }

    /**
     * Handles resources/unsubscribe request.
     */
    private Object handleResourcesUnsubscribe(JsonRpcRequest request, McpSession session) {
        Object params = request.params();
        if (params == null) {
            return createJsonRpcErrorResponse(request.id(), -32602, "Missing params");
        }

        Map<String, Object> paramsMap = objectMapper.convertValue(params, Map.class);
        String uri = (String) paramsMap.get("uri");

        if (uri == null) {
            return createJsonRpcErrorResponse(request.id(), -32602, "Missing resource URI");
        }

        session.unsubscribe(uri);
        logger.info("Client unsubscribed from resource: uri={}", uri);
        return Map.of();
    }

    /**
     * Handles prompts/list request.
     *
     * <p>Returns all available prompts with their metadata including name, title, description,
     * and argument schema (MCP 2025-11-25 spec compliant).
     */
    private Object handlePromptsList(JsonRpcRequest request) {
        List<Map<String, Object>> promptsList = new ArrayList<>();

        for (McpPromptDefinition prompt : registry.getPrompts()) {
            Map<String, Object> promptMap = new HashMap<>();
            promptMap.put("name", prompt.name());
            promptMap.put("title", prompt.title());
            promptMap.put("description", prompt.description());

            // MCP spec: prompts/list includes arguments as an array of {name, description, required}
            if (prompt.arguments() != null && !prompt.arguments().isEmpty()) {
                List<Map<String, Object>> argumentsList = new ArrayList<>();
                for (com.raynermendez.spring_boot_mcp_companion.model.McpPromptDefinition.McpPromptArgument arg : prompt.arguments()) {
                    Map<String, Object> argMap = new HashMap<>();
                    argMap.put("name", arg.name());
                    argMap.put("description", arg.description());
                    argMap.put("required", arg.required());
                    argumentsList.add(argMap);
                }
                promptMap.put("arguments", argumentsList);
            }

            promptsList.add(promptMap);
        }

        return Map.of("prompts", promptsList);
    }

    /**
     * Handles prompts/get request.
     *
     * <p>This method implements MCP 2025-11-25 specification for prompt templates:
     * <ul>
     *   <li>Validates arguments against prompt definition
     *   <li>Performs variable substitution in template text
     *   <li>Returns properly formatted message response
     *   <li>Includes argument schema for documentation
     * </ul>
     */
    private Object handlePromptsGet(JsonRpcRequest request) {
        Object params = request.params();
        if (params == null) {
            return createJsonRpcErrorResponse(request.id(), -32602, "Missing params");
        }

        Map<String, Object> paramsMap = objectMapper.convertValue(params, Map.class);
        String promptName = (String) paramsMap.get("name");
        Map<String, Object> arguments = (Map<String, Object>) paramsMap.get("arguments");

        if (promptName == null) {
            return createJsonRpcErrorResponse(request.id(), -32602, "Missing prompt name");
        }

        // Find prompt definition for validation
        McpPromptDefinition promptDef = registry.getPrompts().stream()
            .filter(p -> p.name().equals(promptName))
            .findFirst()
            .orElse(null);

        // Validate arguments against prompt definition (MCP spec requirement)
        Map<String, Object> validatedArgs = arguments != null ? arguments : Map.of();
        if (promptDef != null) {
            PromptArgumentValidator.ValidationResult validation =
                promptValidator.validateArguments(promptDef, validatedArgs);
            if (validation.hasErrors()) {
                logger.warn("Invalid prompt arguments for {}: {}", promptName, validation.getErrors());
                return createJsonRpcErrorResponse(request.id(), -32602,
                    "Invalid prompt arguments: " + validation.getErrorMessage());
            }
        }

        // Dispatch prompt retrieval with validated arguments
        McpPromptResult result = dispatcher.dispatchPrompt(promptName, validatedArgs);

        if (result.isError()) {
            return createJsonRpcErrorResponse(request.id(), -32000, contentToString(result.content()));
        }

        // Perform variable substitution in prompt text (MCP spec requirement)
        String promptText = contentToString(result.content());
        if (promptDef != null && !validatedArgs.isEmpty()) {
            promptText = promptValidator.substituteVariables(promptText, validatedArgs);
            logger.debug("Substituted variables in prompt {}: {} args applied", promptName, validatedArgs.size());
        }

        // MCP spec prompts/get response: {description, messages}
        Map<String, Object> promptContentMap = new HashMap<>();
        promptContentMap.put("type", "text");
        promptContentMap.put("text", promptText);

        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("description", promptDef != null ? promptDef.description() : "");
        resultMap.put("messages", List.of(
            Map.of(
                "role", "user",
                "content", promptContentMap
            )
        ));
        return resultMap;
    }

    /**
     * Handles server/info request.
     */
    private Object handleServerInfo(JsonRpcRequest request) {
        return Map.of(
            "name", properties.name(),
            "version", properties.version(),
            "protocolVersion", properties.protocolVersion()
        );
    }

    /**
     * Creates a JSON-RPC success response.
     * Uses HashMap to allow null id (required for spec-compliant responses before id is known).
     */
    private Map<String, Object> createJsonRpcSuccessResponse(Object id, Object result) {
        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("result", result);
        return response;
    }

    /**
     * Creates a JSON-RPC error response.
     * Uses HashMap to allow null id (required for parse errors before request id is known).
     */
    private Map<String, Object> createJsonRpcErrorResponse(Object id, int errorCode, String message) {
        Map<String, Object> error = new java.util.LinkedHashMap<>();
        error.put("code", errorCode);
        error.put("message", message != null ? message : "Internal error");
        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("error", error);
        return response;
    }

    /**
     * Creates a JSON-RPC notification (no id field).
     */
    private Map<String, Object> createJsonRpcNotification(String method, Object params) {
        return Map.of(
            "jsonrpc", "2.0",
            "method", method,
            "params", params
        );
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

    /**
     * Validates the Origin header.
     * Currently allows all origins - extend this for CORS configuration.
     */
    private boolean isValidOrigin(String origin) {
        // TODO: Implement proper CORS configuration
        // For now, allow all origins
        return true;
    }
}

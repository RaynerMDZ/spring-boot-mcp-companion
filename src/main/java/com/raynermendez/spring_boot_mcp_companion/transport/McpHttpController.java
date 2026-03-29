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

    public McpHttpController(
        McpDispatcher dispatcher,
        McpDefinitionRegistry registry,
        McpServerProperties properties,
        ErrorMessageSanitizer errorSanitizer,
        ObjectMapper objectMapper,
        SseNotificationManager notificationManager,
        McpSessionManager sessionManager,
        HttpStatusMapper statusMapper
    ) {
        this.dispatcher = dispatcher;
        this.registry = registry;
        this.properties = properties;
        this.errorSanitizer = errorSanitizer;
        this.objectMapper = objectMapper;
        this.notificationManager = notificationManager;
        this.sessionManager = sessionManager;
        this.statusMapper = statusMapper;
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
    @RequestMapping(path = "/mcp", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<?> handleMcpRequest(
        HttpServletRequest request,
        @RequestHeader(value = "Accept", required = false) String accept,
        @RequestHeader(value = "Content-Type", required = false) String contentType,
        @RequestHeader(value = MCP_SESSION_ID_HEADER, required = false) String sessionId,
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
            return handleJsonRpcRequest(sessionId, body);
        }

        // Unsupported method
        return ResponseEntity
            .status(HttpStatus.METHOD_NOT_ALLOWED)
            .body(createJsonRpcErrorResponse(null, -32600, "Method not allowed"));
    }

    /**
     * Handles POST requests with JSON-RPC 2.0 protocol.
     */
    private ResponseEntity<?> handleJsonRpcRequest(String sessionId, String body) {
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

            // All other methods require valid session
            if ("notifications/initialized".equals(method)) {
                // This is a notification - no response needed
                logger.debug("Received notifications/initialized");
                return ResponseEntity.ok().build();
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
            // Send initial connection confirmation
            emitter.send(SseEmitter.event()
                .id("connected")
                .data(objectMapper.writeValueAsString(createJsonRpcNotification("server/initialized", Map.of())))
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

        // Validate protocol version
        if (clientVersion == null || !clientVersion.equals(properties.protocolVersion())) {
            return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(createJsonRpcErrorResponse(
                    request.id(),
                    -32000,
                    "Incompatible protocol version. Server supports " +
                    properties.protocolVersion() +
                    " but client requested " +
                    clientVersion
                ));
        }

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

        // Client primitive support: Declare which client primitives this server uses
        // Note: Requires matching client capability declaration in initialize request
        // Sampling: Server can request LLM completions from client
        // Elicitation: Server can request user input from client
        // Logging: Server can send log messages to client
        // Note: In HTTP Streamable Transport, client primitives are sent as notifications
        // rather than request-response like Stdio transport (architectural limitation)
        if (clientCapabilities != null) {
            if (clientCapabilities.containsKey("sampling")) {
                serverCapabilities.put("sampling", clientCapabilities.get("sampling"));
            }
            if (clientCapabilities.containsKey("elicitation")) {
                serverCapabilities.put("elicitation", clientCapabilities.get("elicitation"));
            }
            if (clientCapabilities.containsKey("logging")) {
                serverCapabilities.put("logging", clientCapabilities.get("logging"));
            }
        }

        // Create session (stores client info and negotiated capabilities)
        McpSession session = sessionManager.createSession(clientVersion, clientInfo, serverCapabilities);

        // Store client capabilities in session for reference (MCP spec requirement)
        session.setClientCapabilities(clientCapabilities != null ? clientCapabilities : Map.of());

        logger.info("Client initialized: session={}, version={}, clientCapabilities={}",
            session.getSessionId(), clientVersion, clientCapabilities);

        // Build success response with negotiated settings
        Map<String, Object> result = Map.of(
            "protocolVersion", properties.protocolVersion(),
            "serverInfo", Map.of(
                "name", properties.name(),
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

        // Dispatch tool call
        McpToolResult result = dispatcher.dispatchTool(toolName, arguments != null ? arguments : Map.of());

        if (result.isError()) {
            return createJsonRpcErrorResponse(request.id(), -32000, contentToString(result.content()));
        }

        return Map.of("content", result.content());
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

            // Include parameters with their schemas
            if (template.parameters() != null && !template.parameters().isEmpty()) {
                List<Map<String, Object>> paramsList = new ArrayList<>();
                for (McpResourceTemplate.TemplateParameter param : template.parameters()) {
                    Map<String, Object> paramMap = new HashMap<>();
                    paramMap.put("name", param.name());
                    paramMap.put("description", param.description());
                    paramMap.put("type", param.type());
                    if (param.schema() != null) {
                        paramMap.put("schema", param.schema());
                    }
                    paramMap.put("required", param.required());
                    paramsList.add(paramMap);
                }
                templateMap.put("parameters", paramsList);
            }

            templatesList.add(templateMap);
        }

        return Map.of("resources", templatesList);
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
            return createJsonRpcErrorResponse(request.id(), -32000, result.content());
        }

        return Map.of(
            "uri", result.uri(),
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
     * <p>Returns all available prompts with their metadata including name, title, and description
     * (MCP 2025-11-25 spec compliant).
     */
    private Object handlePromptsList(JsonRpcRequest request) {
        List<Map<String, Object>> promptsList = new ArrayList<>();

        for (McpPromptDefinition prompt : registry.getPrompts()) {
            Map<String, Object> promptMap = new HashMap<>();
            promptMap.put("name", prompt.name());
            promptMap.put("title", prompt.title());  // ADDED: MCP spec requires title field
            promptMap.put("description", prompt.description());
            promptsList.add(promptMap);
        }

        return Map.of("prompts", promptsList);
    }

    /**
     * Handles prompts/get request.
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

        // Dispatch prompt retrieval
        McpPromptResult result = dispatcher.dispatchPrompt(promptName, arguments != null ? arguments : Map.of());

        if (result.isError()) {
            return createJsonRpcErrorResponse(request.id(), -32000, contentToString(result.content()));
        }

        return Map.of(
            "messages", List.of(
                Map.of(
                    "role", "user",
                    "content", Map.of(
                        "type", "text",
                        "text", contentToString(result.content())
                    )
                )
            )
        );
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
     */
    private Map<String, Object> createJsonRpcSuccessResponse(Object id, Object result) {
        return Map.of(
            "jsonrpc", "2.0",
            "id", id,
            "result", result
        );
    }

    /**
     * Creates a JSON-RPC error response.
     */
    private Map<String, Object> createJsonRpcErrorResponse(Object id, int errorCode, String message) {
        return Map.of(
            "jsonrpc", "2.0",
            "id", id,
            "error", Map.of(
                "code", errorCode,
                "message", message
            )
        );
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

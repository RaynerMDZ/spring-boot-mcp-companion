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
import com.raynermendez.spring_boot_mcp_companion.model.McpToolDefinition;
import com.raynermendez.spring_boot_mcp_companion.registry.McpDefinitionRegistry;
import com.raynermendez.spring_boot_mcp_companion.security.ErrorMessageSanitizer;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for MCP service via JSON-RPC 2.0.
 *
 * <p>Implements the Model Context Protocol specification (https://modelcontextprotocol.io).
 *
 * <p>This controller exposes the following endpoints:
 * - POST {basePath}/initialize: Session initialization and capability negotiation
 * - GET {basePath}/server-info: Returns server name and version
 * - POST {basePath}/tools/list: Returns list of available tools
 * - POST {basePath}/tools/call: Invokes a tool with arguments
 * - POST {basePath}/resources/list: Returns list of available resources
 * - POST {basePath}/resources/read: Reads a resource
 * - POST {basePath}/prompts/list: Returns list of available prompts
 * - POST {basePath}/prompts/get: Invokes a prompt template
 *
 * <p>This bean is conditionally registered by McpAutoConfiguration when
 * mcp.server.enabled is true (defaults to true if not specified).
 */
@RestController
public class McpTransportController {

  private static final Logger logger = LoggerFactory.getLogger(McpTransportController.class);

  private final McpDispatcher dispatcher;
  private final McpDefinitionRegistry registry;
  private final McpServerProperties properties;
  private final ErrorMessageSanitizer errorSanitizer;
  private final ObjectMapper objectMapper;
  private final String basePath;

  @Autowired
  public McpTransportController(
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
    this.basePath = properties.basePath();
    logger.info("MCP Transport Controller initialized at base path: {}", basePath);
  }

  /**
   * Handles POST /initialize requests for session setup.
   *
   * <p>This is the first method called by MCP clients to establish a session and negotiate
   * capabilities and protocol version. Required by MCP specification 2025-11-25. Response is
   * streamed directly to the HTTP output without buffering in memory.
   *
   * @param request the JSON-RPC request
   * @param response the HTTP response to stream to
   * @throws IOException if streaming fails
   */
  @PostMapping("${mcp.server.basePath:/mcp}/initialize")
  public void initialize(@RequestBody JsonRpcRequest request, HttpServletResponse response)
      throws IOException {
    String requestId = request.id() != null ? request.id().toString() : UUID.randomUUID().toString();
    logger.debug("Received initialize request with id: {}", requestId);

    try {
      // Verify protocol version from client
      @SuppressWarnings("unchecked")
      Map<String, Object> params = (Map<String, Object>) request.params();
      String clientProtocolVersion = params != null ? (String) params.get("protocolVersion") : null;

      // Define supported protocol versions (latest first for negotiation)
      String serverProtocolVersion = "2025-11-25";
      boolean versionSupported = "2025-11-25".equals(clientProtocolVersion)
          || "2024-11-05".equals(clientProtocolVersion);  // Support previous version

      if (!versionSupported && clientProtocolVersion != null) {
        // Version mismatch - return error with supported versions
        JsonRpcError error = new JsonRpcError(
            JsonRpcError.INVALID_PARAMS,
            "Unsupported protocol version",
            Map.of("requested", clientProtocolVersion, "supported", java.util.List.of("2025-11-25", "2024-11-05"))
        );
        JsonRpcResponse errorResponse = JsonRpcResponse.error(request.id(), error);
        @SuppressWarnings("unchecked")
        Map<String, Object> responseMap = objectMapper.convertValue(errorResponse, Map.class);
        streamResponse(response, responseMap);
        return;
      }

      Map<String, Object> result = new HashMap<>();

      // Protocol version - MCP specification 2025-11-25
      result.put("protocolVersion", serverProtocolVersion);

      // Server capabilities - detailed capability declaration
      Map<String, Object> capabilities = new HashMap<>();

      // Tool capabilities with listChanged support
      Map<String, Object> toolsCapability = new HashMap<>();
      toolsCapability.put("listChanged", true);
      capabilities.put("tools", toolsCapability);

      // Resource capabilities with listChanged and subscribe support
      Map<String, Object> resourcesCapability = new HashMap<>();
      resourcesCapability.put("listChanged", true);
      resourcesCapability.put("subscribe", true);
      capabilities.put("resources", resourcesCapability);

      // Prompt capabilities with listChanged support
      Map<String, Object> promptsCapability = new HashMap<>();
      promptsCapability.put("listChanged", true);
      capabilities.put("prompts", promptsCapability);

      // Logging capability - server can send log messages
      capabilities.put("logging", Map.of());

      // Completions capability - server supports argument autocompletion
      capabilities.put("completions", Map.of());

      // Task capabilities (experimental)
      Map<String, Object> tasksCapability = new HashMap<>();
      tasksCapability.put("list", Map.of());
      tasksCapability.put("cancel", Map.of());
      Map<String, Object> taskRequests = new HashMap<>();
      taskRequests.put("tools", Map.of("call", Map.of()));
      tasksCapability.put("requests", taskRequests);
      capabilities.put("tasks", tasksCapability);

      result.put("capabilities", capabilities);

      // Enhanced server info
      Map<String, Object> serverInfo = new HashMap<>();
      serverInfo.put("name", "Spring Boot MCP Companion");
      serverInfo.put("title", "Spring Boot MCP Server");
      serverInfo.put("version", "1.0.0");
      serverInfo.put("description", "A Spring Boot implementation of the Model Context Protocol 2025-11-25");
      serverInfo.put("websiteUrl", "https://github.com/raynermendez/spring-boot-mcp-companion");

      // Optional server icons/branding
      List<Map<String, Object>> icons = new ArrayList<>();
      Map<String, Object> icon = new HashMap<>();
      icon.put("mimeType", "image/svg+xml");
      icon.put("sizes", List.of("any"));
      icons.add(icon);
      serverInfo.put("icons", icons);

      serverInfo.put("instructions", "This MCP server provides tools, resources, and prompts for AI applications. "
          + "It supports streaming HTTP transport for efficient large response handling. "
          + "Available primitives: tools/list, tools/call, resources/list, resources/read, prompts/list, prompts/get");

      result.put("serverInfo", serverInfo);

      logger.info("Session initialized with protocol version {} for client {}", serverProtocolVersion, clientProtocolVersion);
      JsonRpcResponse jsonRpcResponse = JsonRpcResponse.success(request.id(), result);

      // Stream the response
      @SuppressWarnings("unchecked")
      Map<String, Object> responseMap = objectMapper.convertValue(jsonRpcResponse, Map.class);
      streamResponse(response, responseMap);

      // Set protocol version header for HTTP transport
      response.setHeader("MCP-Protocol-Version", serverProtocolVersion);
    } catch (Exception e) {
      String sanitizedMessage = errorSanitizer.sanitize(e, requestId, "initialize session");
      JsonRpcError error = new JsonRpcError(
          JsonRpcError.INTERNAL_ERROR,
          sanitizedMessage,
          null);
      JsonRpcResponse errorResponse = JsonRpcResponse.error(request.id(), error);
      @SuppressWarnings("unchecked")
      Map<String, Object> responseMap = objectMapper.convertValue(errorResponse, Map.class);
      streamResponse(response, responseMap);
    }
  }

  /**
   * Helper method to stream a JSON-RPC response to the HTTP output.
   *
   * @param response the HTTP response
   * @param responseMap the response map to stream
   * @throws IOException if streaming fails
   */
  private void streamResponse(HttpServletResponse response, Map<String, Object> responseMap)
      throws IOException {
    StreamableResponse streamable = StreamableResponse.jsonRpc(responseMap);
    response.setContentType(streamable.getContentType());
    if (streamable.getContentLength() > 0) {
      response.setContentLength((int) streamable.getContentLength());
    }
    if (!response.isCommitted()) {
      response.setStatus(200);
    }
    streamable.writeTo(response.getOutputStream());
  }

  /**
   * Handles GET /tools/list requests to list available tools.
   *
   * <p>Response is streamed directly to the HTTP output without buffering in memory.
   *
   * @param request the JSON-RPC request
   * @param response the HTTP response to stream to
   * @throws IOException if streaming fails
   */
  @PostMapping("${mcp.server.basePath:/mcp}/tools/list")
  public void listTools(@RequestBody JsonRpcRequest request, HttpServletResponse response)
      throws IOException {
    String requestId = request.id() != null ? request.id().toString() : UUID.randomUUID().toString();
    logger.debug("Received tools/list request with id: {}", requestId);

    try {
      List<McpToolDefinition> tools = registry.getTools();
      List<Map<String, Object>> toolList = tools.stream()
          .map(this::toolToDescriptor)
          .toList();

      JsonRpcResponse jsonRpcResponse =
          JsonRpcResponse.success(request.id(), Map.of("tools", toolList));
      @SuppressWarnings("unchecked")
      Map<String, Object> responseMap = objectMapper.convertValue(jsonRpcResponse, Map.class);
      StreamableResponse streamable = StreamableResponse.jsonRpc(responseMap);
      response.setContentType(streamable.getContentType());
      if (streamable.getContentLength() > 0) {
        response.setContentLength((int) streamable.getContentLength());
      }
      response.setStatus(200);
      streamable.writeTo(response.getOutputStream());
    } catch (Exception e) {
      // Security: Return generic error message to client
      String sanitizedMessage = errorSanitizer.sanitize(e, requestId, "list tools");
      JsonRpcError error = new JsonRpcError(
          JsonRpcError.INTERNAL_ERROR,
          sanitizedMessage,
          null);
      JsonRpcResponse errorResponse = JsonRpcResponse.error(request.id(), error);
      @SuppressWarnings("unchecked")
      Map<String, Object> responseMap = objectMapper.convertValue(errorResponse, Map.class);
      StreamableResponse streamable = StreamableResponse.jsonRpc(responseMap);
      response.setContentType(streamable.getContentType());
      if (streamable.getContentLength() > 0) {
        response.setContentLength((int) streamable.getContentLength());
      }
      response.setStatus(200);
      streamable.writeTo(response.getOutputStream());
    }
  }

  /**
   * Handles POST /tools/call requests to invoke a tool.
   *
   * <p>Response is streamed directly to the HTTP output without buffering in memory.
   *
   * @param request the JSON-RPC request with method="tools/call"
   * @param response the HTTP response to stream to
   * @throws IOException if streaming fails
   */
  @PostMapping("${mcp.server.basePath:/mcp}/tools/call")
  public void callTool(@RequestBody JsonRpcRequest request, HttpServletResponse response)
      throws IOException {
    String requestId = request.id() != null ? request.id().toString() : UUID.randomUUID().toString();
    logger.debug("Received tools/call request: method={}, id={}", request.method(), requestId);

    try {
      // Validate JSON-RPC structure
      if (request.params() == null || !request.params().containsKey("name")) {
        JsonRpcError error = new JsonRpcError(
            JsonRpcError.INVALID_PARAMS,
            "Missing required parameter: name",
            null);
        JsonRpcResponse errorResponse = JsonRpcResponse.error(request.id(), error);
        @SuppressWarnings("unchecked")
        Map<String, Object> responseMap = objectMapper.convertValue(errorResponse, Map.class);
        StreamableResponse streamable = StreamableResponse.jsonRpc(responseMap);
        response.setContentType(streamable.getContentType());
        if (streamable.getContentLength() > 0) {
          response.setContentLength((int) streamable.getContentLength());
        }
        response.setStatus(200);
        streamable.writeTo(response.getOutputStream());
        return;
      }

      String toolName = (String) request.params().get("name");
      @SuppressWarnings("unchecked")
      Map<String, Object> arguments =
          (Map<String, Object>) request.params().getOrDefault("arguments", Map.of());

      // Dispatch the tool call
      McpToolResult result = dispatcher.dispatchTool(toolName, arguments);

      // Return appropriate response based on result
      JsonRpcResponse jsonRpcResponse;
      if (result.isError()) {
        JsonRpcError error = new JsonRpcError(
            JsonRpcError.METHOD_NOT_FOUND,
            extractErrorMessage(result),
            null);
        jsonRpcResponse = JsonRpcResponse.error(request.id(), error);
      } else {
        Map<String, Object> resultMap = Map.of(
            "content", result.content().stream()
                .map(c -> Map.of("type", c.type(), "text", c.text()))
                .toList()
        );
        jsonRpcResponse = JsonRpcResponse.success(request.id(), resultMap);
      }

      @SuppressWarnings("unchecked")
      Map<String, Object> responseMap = objectMapper.convertValue(jsonRpcResponse, Map.class);
      StreamableResponse streamable = StreamableResponse.jsonRpc(responseMap);
      response.setContentType(streamable.getContentType());
      if (streamable.getContentLength() > 0) {
        response.setContentLength((int) streamable.getContentLength());
      }
      response.setStatus(200);
      streamable.writeTo(response.getOutputStream());
    } catch (Exception e) {
      // Security: Return generic error message to client
      String sanitizedMessage = errorSanitizer.sanitize(e, requestId, "call tool");
      JsonRpcError error = new JsonRpcError(
          JsonRpcError.INTERNAL_ERROR,
          sanitizedMessage,
          null);
      JsonRpcResponse errorResponse = JsonRpcResponse.error(request.id(), error);
      @SuppressWarnings("unchecked")
      Map<String, Object> responseMap = objectMapper.convertValue(errorResponse, Map.class);
      StreamableResponse streamable = StreamableResponse.jsonRpc(responseMap);
      response.setContentType(streamable.getContentType());
      if (streamable.getContentLength() > 0) {
        response.setContentLength((int) streamable.getContentLength());
      }
      response.setStatus(200);
      streamable.writeTo(response.getOutputStream());
    }
  }

  /**
   * Converts a McpToolDefinition to a descriptor suitable for clients.
   *
   * @param tool the tool definition
   * @return a map representing the tool descriptor
   */
  private Map<String, Object> toolToDescriptor(McpToolDefinition tool) {
    Map<String, Object> descriptor = new HashMap<>();
    descriptor.put("name", tool.name());
    descriptor.put("description", tool.description());
    descriptor.put("inputSchema", tool.inputSchema());
    return descriptor;
  }

  /**
   * Handles GET /server-info requests to retrieve server metadata.
   *
   * @return server info response with name and version
   */
  @GetMapping("${mcp.server.basePath:/mcp}/server-info")
  public Map<String, Object> getServerInfo() {
    logger.debug("Received server-info request");
    return Map.of(
        "name", properties.name(),
        "version", properties.version());
  }

  /**
   * Handles POST /resources/list requests to list available resources.
   *
   * <p>Response is streamed directly to the HTTP output without buffering in memory.
   *
   * @param request the JSON-RPC request
   * @param response the HTTP response to stream to
   * @throws IOException if streaming fails
   */
  @PostMapping("${mcp.server.basePath:/mcp}/resources/list")
  public void listResources(@RequestBody JsonRpcRequest request, HttpServletResponse response)
      throws IOException {
    String requestId = request.id() != null ? request.id().toString() : UUID.randomUUID().toString();
    logger.debug("Received resources/list request with id: {}", requestId);

    try {
      List<McpResourceDefinition> resources = registry.getResources();
      List<Map<String, Object>> resourceList = resources.stream()
          .map(this::resourceToDescriptor)
          .toList();

      JsonRpcResponse jsonRpcResponse =
          JsonRpcResponse.success(request.id(), Map.of("resources", resourceList));
      @SuppressWarnings("unchecked")
      Map<String, Object> responseMap = objectMapper.convertValue(jsonRpcResponse, Map.class);
      StreamableResponse streamable = StreamableResponse.jsonRpc(responseMap);
      response.setContentType(streamable.getContentType());
      if (streamable.getContentLength() > 0) {
        response.setContentLength((int) streamable.getContentLength());
      }
      response.setStatus(200);
      streamable.writeTo(response.getOutputStream());
    } catch (Exception e) {
      // Security: Return generic error message to client
      String sanitizedMessage = errorSanitizer.sanitize(e, requestId, "list resources");
      JsonRpcError error = new JsonRpcError(
          JsonRpcError.INTERNAL_ERROR,
          sanitizedMessage,
          null);
      JsonRpcResponse errorResponse = JsonRpcResponse.error(request.id(), error);
      @SuppressWarnings("unchecked")
      Map<String, Object> responseMap = objectMapper.convertValue(errorResponse, Map.class);
      StreamableResponse streamable = StreamableResponse.jsonRpc(responseMap);
      response.setContentType(streamable.getContentType());
      if (streamable.getContentLength() > 0) {
        response.setContentLength((int) streamable.getContentLength());
      }
      response.setStatus(200);
      streamable.writeTo(response.getOutputStream());
    }
  }

  /**
   * Handles POST /resources/read requests to read a resource.
   *
   * <p>Response is streamed directly to the HTTP output without buffering in memory.
   *
   * @param request the JSON-RPC request with method="resources/read"
   * @param response the HTTP response to stream to
   * @throws IOException if streaming fails
   */
  @PostMapping("${mcp.server.basePath:/mcp}/resources/read")
  public void readResource(@RequestBody JsonRpcRequest request, HttpServletResponse response)
      throws IOException {
    String requestId = request.id() != null ? request.id().toString() : UUID.randomUUID().toString();
    logger.debug("Received resources/read request: method={}, id={}", request.method(), requestId);

    try {
      if (request.params() == null || !request.params().containsKey("uri")) {
        JsonRpcError error = new JsonRpcError(
            JsonRpcError.INVALID_PARAMS,
            "Missing required parameter: uri",
            null);
        JsonRpcResponse errorResponse = JsonRpcResponse.error(request.id(), error);
        @SuppressWarnings("unchecked")
        Map<String, Object> responseMap = objectMapper.convertValue(errorResponse, Map.class);
        StreamableResponse streamable = StreamableResponse.jsonRpc(responseMap);
        response.setContentType(streamable.getContentType());
        if (streamable.getContentLength() > 0) {
          response.setContentLength((int) streamable.getContentLength());
        }
        response.setStatus(200);
        streamable.writeTo(response.getOutputStream());
        return;
      }

      String resourceUri = (String) request.params().get("uri");
      @SuppressWarnings("unchecked")
      Map<String, Object> params =
          (Map<String, Object>) request.params().getOrDefault("params", Map.of());

      McpResourceResult result = dispatcher.dispatchResource(resourceUri, params);

      JsonRpcResponse jsonRpcResponse;
      if (result.isError()) {
        JsonRpcError error = new JsonRpcError(
            JsonRpcError.METHOD_NOT_FOUND,
            result.content(),
            null);
        jsonRpcResponse = JsonRpcResponse.error(request.id(), error);
      } else {
        Map<String, Object> resultMap = Map.of(
            "uri", result.uri(),
            "content", result.content(),
            "mimeType", result.mimeType()
        );
        jsonRpcResponse = JsonRpcResponse.success(request.id(), resultMap);
      }

      @SuppressWarnings("unchecked")
      Map<String, Object> responseMap = objectMapper.convertValue(jsonRpcResponse, Map.class);
      StreamableResponse streamable = StreamableResponse.jsonRpc(responseMap);
      response.setContentType(streamable.getContentType());
      if (streamable.getContentLength() > 0) {
        response.setContentLength((int) streamable.getContentLength());
      }
      response.setStatus(200);
      streamable.writeTo(response.getOutputStream());
    } catch (Exception e) {
      // Security: Return generic error message to client
      String sanitizedMessage = errorSanitizer.sanitize(e, requestId, "read resource");
      JsonRpcError error = new JsonRpcError(
          JsonRpcError.INTERNAL_ERROR,
          sanitizedMessage,
          null);
      JsonRpcResponse errorResponse = JsonRpcResponse.error(request.id(), error);
      @SuppressWarnings("unchecked")
      Map<String, Object> responseMap = objectMapper.convertValue(errorResponse, Map.class);
      StreamableResponse streamable = StreamableResponse.jsonRpc(responseMap);
      response.setContentType(streamable.getContentType());
      if (streamable.getContentLength() > 0) {
        response.setContentLength((int) streamable.getContentLength());
      }
      response.setStatus(200);
      streamable.writeTo(response.getOutputStream());
    }
  }

  /**
   * Handles POST /prompts/list requests to list available prompts.
   *
   * <p>Response is streamed directly to the HTTP output without buffering in memory.
   *
   * @param request the JSON-RPC request
   * @param response the HTTP response to stream to
   * @throws IOException if streaming fails
   */
  @PostMapping("${mcp.server.basePath:/mcp}/prompts/list")
  public void listPrompts(@RequestBody JsonRpcRequest request, HttpServletResponse response)
      throws IOException {
    String requestId = request.id() != null ? request.id().toString() : UUID.randomUUID().toString();
    logger.debug("Received prompts/list request with id: {}", requestId);

    try {
      List<McpPromptDefinition> prompts = registry.getPrompts();
      List<Map<String, Object>> promptList = prompts.stream()
          .map(this::promptToDescriptor)
          .toList();

      JsonRpcResponse jsonRpcResponse =
          JsonRpcResponse.success(request.id(), Map.of("prompts", promptList));
      @SuppressWarnings("unchecked")
      Map<String, Object> responseMap = objectMapper.convertValue(jsonRpcResponse, Map.class);
      StreamableResponse streamable = StreamableResponse.jsonRpc(responseMap);
      response.setContentType(streamable.getContentType());
      if (streamable.getContentLength() > 0) {
        response.setContentLength((int) streamable.getContentLength());
      }
      response.setStatus(200);
      streamable.writeTo(response.getOutputStream());
    } catch (Exception e) {
      // Security: Return generic error message to client
      String sanitizedMessage = errorSanitizer.sanitize(e, requestId, "list prompts");
      JsonRpcError error = new JsonRpcError(
          JsonRpcError.INTERNAL_ERROR,
          sanitizedMessage,
          null);
      JsonRpcResponse errorResponse = JsonRpcResponse.error(request.id(), error);
      @SuppressWarnings("unchecked")
      Map<String, Object> responseMap = objectMapper.convertValue(errorResponse, Map.class);
      StreamableResponse streamable = StreamableResponse.jsonRpc(responseMap);
      response.setContentType(streamable.getContentType());
      if (streamable.getContentLength() > 0) {
        response.setContentLength((int) streamable.getContentLength());
      }
      response.setStatus(200);
      streamable.writeTo(response.getOutputStream());
    }
  }

  /**
   * Handles POST /prompts/get requests to invoke a prompt template.
   *
   * <p>Response is streamed directly to the HTTP output without buffering in memory.
   *
   * @param request the JSON-RPC request with method="prompts/get"
   * @param response the HTTP response to stream to
   * @throws IOException if streaming fails
   */
  @PostMapping("${mcp.server.basePath:/mcp}/prompts/get")
  public void getPrompt(@RequestBody JsonRpcRequest request, HttpServletResponse response)
      throws IOException {
    String requestId = request.id() != null ? request.id().toString() : UUID.randomUUID().toString();
    logger.debug("Received prompts/get request: method={}, id={}", request.method(), requestId);

    try {
      if (request.params() == null || !request.params().containsKey("name")) {
        JsonRpcError error = new JsonRpcError(
            JsonRpcError.INVALID_PARAMS,
            "Missing required parameter: name",
            null);
        JsonRpcResponse errorResponse = JsonRpcResponse.error(request.id(), error);
        @SuppressWarnings("unchecked")
        Map<String, Object> responseMap = objectMapper.convertValue(errorResponse, Map.class);
        StreamableResponse streamable = StreamableResponse.jsonRpc(responseMap);
        response.setContentType(streamable.getContentType());
        if (streamable.getContentLength() > 0) {
          response.setContentLength((int) streamable.getContentLength());
        }
        response.setStatus(200);
        streamable.writeTo(response.getOutputStream());
        return;
      }

      String promptName = (String) request.params().get("name");
      @SuppressWarnings("unchecked")
      Map<String, Object> arguments =
          (Map<String, Object>) request.params().getOrDefault("arguments", Map.of());

      McpPromptResult result = dispatcher.dispatchPrompt(promptName, arguments);

      JsonRpcResponse jsonRpcResponse;
      if (result.isError()) {
        JsonRpcError error = new JsonRpcError(
            JsonRpcError.METHOD_NOT_FOUND,
            extractErrorMessage(result),
            null);
        jsonRpcResponse = JsonRpcResponse.error(request.id(), error);
      } else {
        Map<String, Object> resultMap = Map.of(
            "content", result.content().stream()
                .map(c -> Map.of("type", c.type(), "text", c.text()))
                .toList()
        );
        jsonRpcResponse = JsonRpcResponse.success(request.id(), resultMap);
      }

      @SuppressWarnings("unchecked")
      Map<String, Object> responseMap = objectMapper.convertValue(jsonRpcResponse, Map.class);
      StreamableResponse streamable = StreamableResponse.jsonRpc(responseMap);
      response.setContentType(streamable.getContentType());
      if (streamable.getContentLength() > 0) {
        response.setContentLength((int) streamable.getContentLength());
      }
      response.setStatus(200);
      streamable.writeTo(response.getOutputStream());
    } catch (Exception e) {
      // Security: Return generic error message to client
      String sanitizedMessage = errorSanitizer.sanitize(e, requestId, "invoke prompt");
      JsonRpcError error = new JsonRpcError(
          JsonRpcError.INTERNAL_ERROR,
          sanitizedMessage,
          null);
      JsonRpcResponse errorResponse = JsonRpcResponse.error(request.id(), error);
      @SuppressWarnings("unchecked")
      Map<String, Object> responseMap = objectMapper.convertValue(errorResponse, Map.class);
      StreamableResponse streamable = StreamableResponse.jsonRpc(responseMap);
      response.setContentType(streamable.getContentType());
      if (streamable.getContentLength() > 0) {
        response.setContentLength((int) streamable.getContentLength());
      }
      response.setStatus(200);
      streamable.writeTo(response.getOutputStream());
    }
  }

  /**
   * Converts a McpResourceDefinition to a descriptor suitable for clients.
   *
   * @param resource the resource definition
   * @return a map representing the resource descriptor
   */
  private Map<String, Object> resourceToDescriptor(McpResourceDefinition resource) {
    Map<String, Object> descriptor = new HashMap<>();
    descriptor.put("uri", resource.uri());
    descriptor.put("name", resource.name());
    descriptor.put("description", resource.description());
    descriptor.put("mimeType", resource.mimeType());
    return descriptor;
  }

  /**
   * Converts a McpPromptDefinition to a descriptor suitable for clients.
   *
   * @param prompt the prompt definition
   * @return a map representing the prompt descriptor
   */
  private Map<String, Object> promptToDescriptor(McpPromptDefinition prompt) {
    Map<String, Object> descriptor = new HashMap<>();
    descriptor.put("name", prompt.name());
    descriptor.put("description", prompt.description());
    descriptor.put("arguments", prompt.arguments().stream()
        .map(arg -> Map.of(
            "name", arg.name(),
            "description", arg.description(),
            "required", arg.required()))
        .toList());
    return descriptor;
  }

  /**
   * Extracts error message from tool result content.
   *
   * @param result the tool result
   * @return the error message
   */
  private String extractErrorMessage(McpToolResult result) {
    if (!result.content().isEmpty()) {
      return result.content().get(0).text();
    }
    return "Unknown error";
  }

  /**
   * Extracts error message from prompt result content.
   *
   * @param result the prompt result
   * @return the error message
   */
  private String extractErrorMessage(McpPromptResult result) {
    if (!result.content().isEmpty()) {
      return result.content().get(0).text();
    }
    return "Unknown error";
  }

  // ========== CLIENT PRIMITIVE ENDPOINTS ==========
  // These endpoints allow MCP servers to call back to clients

  /**
   * Handles POST /sampling/createMessage requests for LLM completions.
   *
   * <p>Allows MCP servers to request language model completions from the client's AI
   * application. This is useful when server authors want access to a language model but want to
   * stay model-independent.
   *
   * @param request the JSON-RPC request with method="sampling/createMessage"
   * @param response the HTTP response to stream to
   * @throws IOException if streaming fails
   */
  @PostMapping("${mcp.server.basePath:/mcp}/sampling/createMessage")
  public void sampling(@RequestBody JsonRpcRequest request, HttpServletResponse response)
      throws IOException {
    String requestId = request.id() != null ? request.id().toString() : UUID.randomUUID().toString();
    logger.debug("Received sampling/createMessage request with id: {}", requestId);

    try {
      // This would typically be handled by the client application
      // For now, return a mock response
      Map<String, Object> result = Map.of(
          "content", Map.of(
              "type", "text",
              "text", "Mock LLM response from client"
          )
      );

      JsonRpcResponse jsonRpcResponse = JsonRpcResponse.success(request.id(), result);
      @SuppressWarnings("unchecked")
      Map<String, Object> responseMap = objectMapper.convertValue(jsonRpcResponse, Map.class);
      streamResponse(response, responseMap);
    } catch (Exception e) {
      String sanitizedMessage = errorSanitizer.sanitize(e, requestId, "sampling request");
      JsonRpcError error = new JsonRpcError(
          JsonRpcError.INTERNAL_ERROR,
          sanitizedMessage,
          null);
      JsonRpcResponse errorResponse = JsonRpcResponse.error(request.id(), error);
      @SuppressWarnings("unchecked")
      Map<String, Object> responseMap = objectMapper.convertValue(errorResponse, Map.class);
      streamResponse(response, responseMap);
    }
  }

  /**
   * Handles POST /elicitation/create requests for user input.
   *
   * <p>Allows MCP servers to request additional information from users or ask for confirmation
   * of an action. This is useful when server authors want to get more information from the user.
   *
   * @param request the JSON-RPC request with method="elicitation/create"
   * @param response the HTTP response to stream to
   * @throws IOException if streaming fails
   */
  @PostMapping("${mcp.server.basePath:/mcp}/elicitation/create")
  public void elicitation(@RequestBody JsonRpcRequest request, HttpServletResponse response)
      throws IOException {
    String requestId = request.id() != null ? request.id().toString() : UUID.randomUUID().toString();
    logger.debug("Received elicitation/create request with id: {}", requestId);

    try {
      // This would typically be handled by the client application
      // For now, return a mock response
      Map<String, Object> result = Map.of(
          "elicitationId", UUID.randomUUID().toString(),
          "type", "text",
          "status", "pending"
      );

      JsonRpcResponse jsonRpcResponse = JsonRpcResponse.success(request.id(), result);
      @SuppressWarnings("unchecked")
      Map<String, Object> responseMap = objectMapper.convertValue(jsonRpcResponse, Map.class);
      streamResponse(response, responseMap);
    } catch (Exception e) {
      String sanitizedMessage = errorSanitizer.sanitize(e, requestId, "elicitation request");
      JsonRpcError error = new JsonRpcError(
          JsonRpcError.INTERNAL_ERROR,
          sanitizedMessage,
          null);
      JsonRpcResponse errorResponse = JsonRpcResponse.error(request.id(), error);
      @SuppressWarnings("unchecked")
      Map<String, Object> responseMap = objectMapper.convertValue(errorResponse, Map.class);
      streamResponse(response, responseMap);
    }
  }

  /**
   * Handles POST /logging/create requests for server logging.
   *
   * <p>Allows MCP servers to send log messages to clients for debugging and monitoring purposes.
   * This enables servers to provide visibility into their operations.
   *
   * @param request the JSON-RPC request with method="logging/create"
   * @param response the HTTP response to stream to
   * @throws IOException if streaming fails
   */
  @PostMapping("${mcp.server.basePath:/mcp}/logging/create")
  public void logging(@RequestBody JsonRpcRequest request, HttpServletResponse response)
      throws IOException {
    String requestId = request.id() != null ? request.id().toString() : UUID.randomUUID().toString();

    try {
      @SuppressWarnings("unchecked")
      Map<String, Object> params = (Map<String, Object>) request.params();
      String level = params != null ? (String) params.get("level") : "info";
      String message = params != null ? (String) params.get("message") : "";

      // Log the server message
      switch (level) {
        case "debug" -> logger.debug("[Server Log] {}", message);
        case "info" -> logger.info("[Server Log] {}", message);
        case "warning" -> logger.warn("[Server Log] {}", message);
        case "error" -> logger.error("[Server Log] {}", message);
        default -> logger.info("[Server Log] {}", message);
      }

      // Return success
      Map<String, Object> result = Map.of("accepted", true);
      JsonRpcResponse jsonRpcResponse = JsonRpcResponse.success(request.id(), result);
      @SuppressWarnings("unchecked")
      Map<String, Object> responseMap = objectMapper.convertValue(jsonRpcResponse, Map.class);
      streamResponse(response, responseMap);
    } catch (Exception e) {
      String sanitizedMessage = errorSanitizer.sanitize(e, requestId, "logging request");
      JsonRpcError error = new JsonRpcError(
          JsonRpcError.INTERNAL_ERROR,
          sanitizedMessage,
          null);
      JsonRpcResponse errorResponse = JsonRpcResponse.error(request.id(), error);
      @SuppressWarnings("unchecked")
      Map<String, Object> responseMap = objectMapper.convertValue(errorResponse, Map.class);
      streamResponse(response, responseMap);
    }
  }

  /**
   * Handles POST /roots/list requests for filesystem roots.
   *
   * <p>Allows MCP servers to discover filesystem roots available to the client. This enables
   * servers to understand what filesystem paths are available to access resources.
   *
   * @param request the JSON-RPC request with method="roots/list"
   * @param response the HTTP response to stream to
   * @throws IOException if streaming fails
   */
  @PostMapping("${mcp.server.basePath:/mcp}/roots/list")
  public void rootsList(@RequestBody JsonRpcRequest request, HttpServletResponse response)
      throws IOException {
    String requestId = request.id() != null ? request.id().toString() : UUID.randomUUID().toString();
    logger.debug("Received roots/list request with id: {}", requestId);

    try {
      // Return filesystem roots available to the client
      List<Map<String, Object>> roots = new ArrayList<>();

      // Add common roots (these would typically come from client configuration)
      Map<String, Object> homeRoot = new HashMap<>();
      homeRoot.put("uri", "file://" + System.getProperty("user.home"));
      homeRoot.put("name", "Home Directory");
      roots.add(homeRoot);

      // Add working directory
      Map<String, Object> workRoot = new HashMap<>();
      workRoot.put("uri", "file://" + System.getProperty("user.dir"));
      workRoot.put("name", "Current Directory");
      roots.add(workRoot);

      Map<String, Object> result = Map.of("roots", roots);
      JsonRpcResponse jsonRpcResponse = JsonRpcResponse.success(request.id(), result);
      @SuppressWarnings("unchecked")
      Map<String, Object> responseMap = objectMapper.convertValue(jsonRpcResponse, Map.class);
      streamResponse(response, responseMap);
    } catch (Exception e) {
      String sanitizedMessage = errorSanitizer.sanitize(e, requestId, "roots list");
      JsonRpcError error = new JsonRpcError(
          JsonRpcError.INTERNAL_ERROR,
          sanitizedMessage,
          null);
      JsonRpcResponse errorResponse = JsonRpcResponse.error(request.id(), error);
      @SuppressWarnings("unchecked")
      Map<String, Object> responseMap = objectMapper.convertValue(errorResponse, Map.class);
      streamResponse(response, responseMap);
    }
  }

  // ========== NOTIFICATION ENDPOINTS ==========
  // These endpoints allow servers to send notifications to clients

  /**
   * Handles POST /notifications/tools/list_changed for tool list updates.
   *
   * <p>Sent by the server to notify clients that the list of available tools has changed. This
   * allows clients to refresh their understanding of available server capabilities.
   *
   * @param request the JSON-RPC notification
   * @param response the HTTP response
   * @throws IOException if streaming fails
   */
  @PostMapping("${mcp.server.basePath:/mcp}/notifications/tools/list_changed")
  public void toolsListChanged(@RequestBody JsonRpcRequest request, HttpServletResponse response)
      throws IOException {
    logger.info("Received tools/list_changed notification");
    // Clients typically respond by calling tools/list to get updated tools
    streamEmptySuccessResponse(response, request.id());
  }

  /**
   * Handles POST /notifications/resources/list_changed for resource list updates.
   *
   * @param request the JSON-RPC notification
   * @param response the HTTP response
   * @throws IOException if streaming fails
   */
  @PostMapping("${mcp.server.basePath:/mcp}/notifications/resources/list_changed")
  public void resourcesListChanged(@RequestBody JsonRpcRequest request, HttpServletResponse response)
      throws IOException {
    logger.info("Received resources/list_changed notification");
    streamEmptySuccessResponse(response, request.id());
  }

  /**
   * Handles POST /notifications/prompts/list_changed for prompt list updates.
   *
   * @param request the JSON-RPC notification
   * @param response the HTTP response
   * @throws IOException if streaming fails
   */
  @PostMapping("${mcp.server.basePath:/mcp}/notifications/prompts/list_changed")
  public void promptsListChanged(@RequestBody JsonRpcRequest request, HttpServletResponse response)
      throws IOException {
    logger.info("Received prompts/list_changed notification");
    streamEmptySuccessResponse(response, request.id());
  }

  /**
   * Helper method to stream an empty success response.
   *
   * @param response the HTTP response
   * @param id the request ID
   * @throws IOException if streaming fails
   */
  private void streamEmptySuccessResponse(HttpServletResponse response, Object id) throws IOException {
    JsonRpcResponse jsonRpcResponse = JsonRpcResponse.success(id, Map.of());
    @SuppressWarnings("unchecked")
    Map<String, Object> responseMap = objectMapper.convertValue(jsonRpcResponse, Map.class);
    streamResponse(response, responseMap);
  }
}

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
   * capabilities. Required by MCP specification. Response is streamed directly to the HTTP output
   * without buffering in memory.
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
      Map<String, Object> result = new HashMap<>();

      // Protocol version - MCP specification
      result.put("protocolVersion", "2024-11-05");

      // Server capabilities
      Map<String, Object> capabilities = new HashMap<>();
      capabilities.put("tools", Map.of());
      capabilities.put("resources", Map.of());
      capabilities.put("prompts", Map.of());
      result.put("capabilities", capabilities);

      // Server info
      Map<String, String> serverInfo = new HashMap<>();
      serverInfo.put("name", "Spring Boot MCP Companion");
      serverInfo.put("version", "1.0.0");
      result.put("serverInfo", serverInfo);

      logger.info("Session initialized with protocol version 2024-11-05");
      JsonRpcResponse jsonRpcResponse = JsonRpcResponse.success(request.id(), result);

      // Stream the response
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
      String sanitizedMessage = errorSanitizer.sanitize(e, requestId, "initialize session");
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
}

package com.raynermendez.spring_boot_mcp_companion.transport;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * <p>This controller exposes the following endpoints:
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
  private final String basePath;

  @Autowired
  public McpTransportController(
      McpDispatcher dispatcher,
      McpDefinitionRegistry registry,
      McpServerProperties properties) {
    this.dispatcher = dispatcher;
    this.registry = registry;
    this.properties = properties;
    this.basePath = properties.basePath();
    logger.info("MCP Transport Controller initialized at base path: {}", basePath);
  }

  /**
   * Handles GET /tools/list requests to list available tools.
   *
   * @param request the JSON-RPC request
   * @return JSON-RPC response with array of tool descriptors
   */
  @PostMapping("${mcp.server.basePath:/mcp}/tools/list")
  public JsonRpcResponse listTools(@RequestBody JsonRpcRequest request) {
    logger.debug("Received tools/list request with id: {}", request.id());

    try {
      List<McpToolDefinition> tools = registry.getTools();
      List<Map<String, Object>> toolList = tools.stream()
          .map(this::toolToDescriptor)
          .toList();

      return JsonRpcResponse.success(request.id(), Map.of("tools", toolList));
    } catch (Exception e) {
      logger.error("Error listing tools", e);
      JsonRpcError error = new JsonRpcError(
          JsonRpcError.INTERNAL_ERROR,
          "Failed to list tools: " + e.getMessage(),
          null);
      return JsonRpcResponse.error(request.id(), error);
    }
  }

  /**
   * Handles POST /tools/call requests to invoke a tool.
   *
   * @param request the JSON-RPC request with method="tools/call"
   * @return JSON-RPC response with tool result
   */
  @PostMapping("${mcp.server.basePath:/mcp}/tools/call")
  public JsonRpcResponse callTool(@RequestBody JsonRpcRequest request) {
    logger.debug("Received tools/call request: method={}, id={}", request.method(), request.id());

    try {
      // Validate JSON-RPC structure
      if (request.params() == null || !request.params().containsKey("name")) {
        JsonRpcError error = new JsonRpcError(
            JsonRpcError.INVALID_PARAMS,
            "Missing required parameter: name",
            null);
        return JsonRpcResponse.error(request.id(), error);
      }

      String toolName = (String) request.params().get("name");
      @SuppressWarnings("unchecked")
      Map<String, Object> arguments =
          (Map<String, Object>) request.params().getOrDefault("arguments", Map.of());

      // Dispatch the tool call
      McpToolResult result = dispatcher.dispatchTool(toolName, arguments);

      // Return appropriate response based on result
      if (result.isError()) {
        JsonRpcError error = new JsonRpcError(
            JsonRpcError.METHOD_NOT_FOUND,
            extractErrorMessage(result),
            null);
        return JsonRpcResponse.error(request.id(), error);
      } else {
        Map<String, Object> resultMap = Map.of(
            "content", result.content().stream()
                .map(c -> Map.of("type", c.type(), "text", c.text()))
                .toList()
        );
        return JsonRpcResponse.success(request.id(), resultMap);
      }
    } catch (Exception e) {
      logger.error("Error calling tool", e);
      JsonRpcError error = new JsonRpcError(
          JsonRpcError.INTERNAL_ERROR,
          "Tool invocation failed: " + e.getMessage(),
          null);
      return JsonRpcResponse.error(request.id(), error);
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
   * @param request the JSON-RPC request
   * @return JSON-RPC response with array of resource descriptors
   */
  @PostMapping("${mcp.server.basePath:/mcp}/resources/list")
  public JsonRpcResponse listResources(@RequestBody JsonRpcRequest request) {
    logger.debug("Received resources/list request with id: {}", request.id());

    try {
      List<McpResourceDefinition> resources = registry.getResources();
      List<Map<String, Object>> resourceList = resources.stream()
          .map(this::resourceToDescriptor)
          .toList();

      return JsonRpcResponse.success(request.id(), Map.of("resources", resourceList));
    } catch (Exception e) {
      logger.error("Error listing resources", e);
      JsonRpcError error = new JsonRpcError(
          JsonRpcError.INTERNAL_ERROR,
          "Failed to list resources: " + e.getMessage(),
          null);
      return JsonRpcResponse.error(request.id(), error);
    }
  }

  /**
   * Handles POST /resources/read requests to read a resource.
   *
   * @param request the JSON-RPC request with method="resources/read"
   * @return JSON-RPC response with resource content
   */
  @PostMapping("${mcp.server.basePath:/mcp}/resources/read")
  public JsonRpcResponse readResource(@RequestBody JsonRpcRequest request) {
    logger.debug("Received resources/read request: method={}, id={}", request.method(), request.id());

    try {
      if (request.params() == null || !request.params().containsKey("uri")) {
        JsonRpcError error = new JsonRpcError(
            JsonRpcError.INVALID_PARAMS,
            "Missing required parameter: uri",
            null);
        return JsonRpcResponse.error(request.id(), error);
      }

      String resourceUri = (String) request.params().get("uri");
      @SuppressWarnings("unchecked")
      Map<String, Object> params =
          (Map<String, Object>) request.params().getOrDefault("params", Map.of());

      McpResourceResult result = dispatcher.dispatchResource(resourceUri, params);

      if (result.isError()) {
        JsonRpcError error = new JsonRpcError(
            JsonRpcError.METHOD_NOT_FOUND,
            result.content(),
            null);
        return JsonRpcResponse.error(request.id(), error);
      } else {
        Map<String, Object> resultMap = Map.of(
            "uri", result.uri(),
            "content", result.content(),
            "mimeType", result.mimeType()
        );
        return JsonRpcResponse.success(request.id(), resultMap);
      }
    } catch (Exception e) {
      logger.error("Error reading resource", e);
      JsonRpcError error = new JsonRpcError(
          JsonRpcError.INTERNAL_ERROR,
          "Resource read failed: " + e.getMessage(),
          null);
      return JsonRpcResponse.error(request.id(), error);
    }
  }

  /**
   * Handles POST /prompts/list requests to list available prompts.
   *
   * @param request the JSON-RPC request
   * @return JSON-RPC response with array of prompt descriptors
   */
  @PostMapping("${mcp.server.basePath:/mcp}/prompts/list")
  public JsonRpcResponse listPrompts(@RequestBody JsonRpcRequest request) {
    logger.debug("Received prompts/list request with id: {}", request.id());

    try {
      List<McpPromptDefinition> prompts = registry.getPrompts();
      List<Map<String, Object>> promptList = prompts.stream()
          .map(this::promptToDescriptor)
          .toList();

      return JsonRpcResponse.success(request.id(), Map.of("prompts", promptList));
    } catch (Exception e) {
      logger.error("Error listing prompts", e);
      JsonRpcError error = new JsonRpcError(
          JsonRpcError.INTERNAL_ERROR,
          "Failed to list prompts: " + e.getMessage(),
          null);
      return JsonRpcResponse.error(request.id(), error);
    }
  }

  /**
   * Handles POST /prompts/get requests to invoke a prompt template.
   *
   * @param request the JSON-RPC request with method="prompts/get"
   * @return JSON-RPC response with prompt result
   */
  @PostMapping("${mcp.server.basePath:/mcp}/prompts/get")
  public JsonRpcResponse getPrompt(@RequestBody JsonRpcRequest request) {
    logger.debug("Received prompts/get request: method={}, id={}", request.method(), request.id());

    try {
      if (request.params() == null || !request.params().containsKey("name")) {
        JsonRpcError error = new JsonRpcError(
            JsonRpcError.INVALID_PARAMS,
            "Missing required parameter: name",
            null);
        return JsonRpcResponse.error(request.id(), error);
      }

      String promptName = (String) request.params().get("name");
      @SuppressWarnings("unchecked")
      Map<String, Object> arguments =
          (Map<String, Object>) request.params().getOrDefault("arguments", Map.of());

      McpPromptResult result = dispatcher.dispatchPrompt(promptName, arguments);

      if (result.isError()) {
        JsonRpcError error = new JsonRpcError(
            JsonRpcError.METHOD_NOT_FOUND,
            extractErrorMessage(result),
            null);
        return JsonRpcResponse.error(request.id(), error);
      } else {
        Map<String, Object> resultMap = Map.of(
            "content", result.content().stream()
                .map(c -> Map.of("type", c.type(), "text", c.text()))
                .toList()
        );
        return JsonRpcResponse.success(request.id(), resultMap);
      }
    } catch (Exception e) {
      logger.error("Error invoking prompt", e);
      JsonRpcError error = new JsonRpcError(
          JsonRpcError.INTERNAL_ERROR,
          "Prompt invocation failed: " + e.getMessage(),
          null);
      return JsonRpcResponse.error(request.id(), error);
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

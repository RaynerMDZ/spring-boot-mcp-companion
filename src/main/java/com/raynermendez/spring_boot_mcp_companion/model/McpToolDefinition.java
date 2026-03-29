package com.raynermendez.spring_boot_mcp_companion.model;

import java.util.List;
import java.util.Map;

/**
 * Immutable record representing a complete MCP Tool definition.
 *
 * <p>This record encapsulates all metadata about a tool, including its name, title, description,
 * tags, parameters, and a handler reference that can be invoked when the tool is called.
 *
 * <p>Complies with MCP 2025-11-25 specification for tool definitions.
 */
public record McpToolDefinition(
    String name,
    String title,
    String description,
    String[] tags,
    List<McpParameterDefinition> parameters,
    Map<String, Object> inputSchema,
    MethodHandlerRef handler) {

  /**
   * Creates a new tool definition.
   *
   * @param name the tool name as exposed via MCP protocol (unique identifier)
   * @param title human-readable display name for the tool in UI
   * @param description detailed explanation of what the tool does
   * @param tags optional tags for categorization
   * @param parameters list of parameter definitions
   * @param inputSchema JSON Schema object for all tool inputs (for validation)
   * @param handler method handler reference for invocation
   */
  public McpToolDefinition {}
}

package com.raynermendez.spring_boot_mcp_companion.model;

import java.util.Map;

/**
 * Immutable record representing a single parameter of an MCP Tool or Prompt.
 *
 * <p>This record captures parameter metadata extracted from @McpInput annotations, including
 * JSON Schema information for validation and client-side rendering.
 */
public record McpParameterDefinition(
    String name,
    String description,
    boolean required,
    Map<String, Object> jsonSchema,
    boolean sensitive) {

  /**
   * Creates a new parameter definition.
   *
   * @param name the parameter name
   * @param description human-readable description
   * @param required whether the parameter is required
   * @param jsonSchema JSON Schema object defining the parameter's type and constraints
   * @param sensitive whether this parameter contains sensitive data
   */
  public McpParameterDefinition {}
}

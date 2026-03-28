package com.raynermendez.spring_boot_mcp_companion.model;

import java.util.List;

/**
 * Immutable record representing a complete MCP Prompt definition.
 *
 * <p>This record encapsulates metadata for a prompt template exposed via the MCP protocol,
 * including its name, description, arguments, and handler reference.
 */
public record McpPromptDefinition(
    String name,
    String description,
    List<McpPromptArgument> arguments,
    MethodHandlerRef handler) {

  /**
   * Creates a new prompt definition.
   *
   * @param name the prompt template name
   * @param description human-readable description of the prompt
   * @param arguments list of prompt arguments
   * @param handler method handler reference for execution
   */
  public McpPromptDefinition {}

  /**
   * Immutable record representing a single argument in a prompt template.
   */
  public record McpPromptArgument(
      String name,
      String description,
      boolean required) {

    /**
     * Creates a new prompt argument.
     *
     * @param name the argument name
     * @param description human-readable description of the argument
     * @param required whether this argument is required
     */
    public McpPromptArgument {}
  }
}

package com.raynermendez.spring_boot_mcp_companion.model;

/**
 * Immutable record representing a complete MCP Resource definition.
 *
 * <p>This record encapsulates metadata for a resource exposed via the MCP protocol, including
 * its URI template, name, description, MIME type, and handler reference.
 */
public record McpResourceDefinition(
    String uri,
    String name,
    String description,
    String mimeType,
    MethodHandlerRef handler) {

  /**
   * Creates a new resource definition.
   *
   * @param uri URI template for this resource (e.g., "file:///{path}")
   * @param name the resource name
   * @param description human-readable description of the resource
   * @param mimeType MIME type of the resource content
   * @param handler method handler reference for retrieval
   */
  public McpResourceDefinition {}
}

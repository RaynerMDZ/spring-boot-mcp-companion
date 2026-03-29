package com.raynermendez.spring_boot_mcp_companion.model;

import java.util.List;
import java.util.Map;

/**
 * Immutable record representing an MCP Resource Template definition.
 *
 * <p>Resource templates enable dynamic resource access with parameterized URIs.
 * For example, a weather template might be "weather://forecast/{city}/{date}" allowing
 * clients to request weather for any city and date combination.
 *
 * <p>This supports the MCP 2025-11-25 specification for dynamic resource discovery
 * and flexible queries.
 *
 * @author Rayner Mendez
 */
public record McpResourceTemplate(
    String uriTemplate,
    String name,
    String title,
    String description,
    String mimeType,
    List<TemplateParameter> parameters,
    MethodHandlerRef handler) {

  /**
   * Creates a new resource template definition.
   *
   * @param uriTemplate URI template with parameters (e.g., "weather://forecast/{city}/{date}")
   * @param name machine-readable template name
   * @param title human-readable display title
   * @param description description of what this template provides
   * @param mimeType MIME type of the resource content
   * @param parameters list of template parameters and their schemas
   * @param handler method handler reference for resource retrieval
   */
  public McpResourceTemplate {}

  /**
   * Represents a parameter in a resource template.
   */
  public record TemplateParameter(
      String name,
      String description,
      String type,
      Map<String, Object> schema,
      boolean required) {

    /**
     * Creates a new template parameter.
     *
     * @param name parameter name (must match {name} in uriTemplate)
     * @param description parameter description
     * @param type parameter type (string, number, integer, etc.)
     * @param schema JSON Schema for parameter validation
     * @param required whether this parameter is required
     */
    public TemplateParameter {}
  }
}

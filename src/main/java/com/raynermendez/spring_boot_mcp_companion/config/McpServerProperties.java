package com.raynermendez.spring_boot_mcp_companion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/**
 * Configuration properties for the MCP server.
 *
 * <p>This class is bound from Spring configuration using the "mcp.server" prefix. All properties
 * support constructor binding for immutability.
 *
 * <p>Example configuration:
 * <pre>{@code
 * mcp.server.enabled=true
 * mcp.server.name=my-mcp-server
 * mcp.server.version=1.0.0
 * mcp.server.port=8090
 * mcp.server.basePath=/api/mcp
 * }</pre>
 */
@ConfigurationProperties(prefix = "mcp.server")
public record McpServerProperties(
    /**
     * Enable/disable MCP server endpoints.
     * Defaults to true if not specified.
     */
    boolean enabled,
    /**
     * Server name advertised in MCP server-info.
     * Defaults to "spring-boot-mcp-companion".
     */
    String name,
    /**
     * Server version advertised in MCP server-info.
     * Defaults to "1.0.0".
     */
    String version,
    /**
     * Port for MCP server endpoints.
     * Defaults to 8090.
     */
    int port,
    /**
     * Base path for all MCP endpoints (e.g., /api/mcp).
     * Defaults to "/mcp".
     */
    String basePath) {

  /**
   * Creates MCP server properties with defaults applied.
   *
   * @param enabled whether the MCP server is enabled
   * @param name the server name
   * @param version the server version
   * @param port the port number
   * @param basePath the base path for endpoints
   */
  @ConstructorBinding
  public McpServerProperties(
      boolean enabled,
      String name,
      String version,
      int port,
      String basePath) {
    this.enabled = enabled;
    this.name = name != null ? name : "spring-boot-mcp-companion";
    this.version = version != null ? version : "1.0.0";
    this.port = port > 0 ? port : 8090;
    this.basePath = basePath != null ? basePath : "/mcp";
  }
}

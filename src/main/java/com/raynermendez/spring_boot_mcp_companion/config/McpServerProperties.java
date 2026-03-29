package com.raynermendez.spring_boot_mcp_companion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/**
 * Configuration properties for the MCP server.
 *
 * <p>This class is bound from Spring configuration using the "mcp.server" prefix. All properties
 * support constructor binding for immutability.
 *
 * <p>MCP endpoints are served on the same port as the main Spring Boot application
 * (configured via server.port). Only the base path for MCP endpoints is configurable.
 *
 * <p>Example configuration:
 * <pre>{@code
 * server:
 *   port: 8080
 *
 * mcp:
 *   server:
 *     enabled: true
 *     name: my-mcp-server
 *     version: 1.0.0
 *     base-path: /mcp
 *     protocol-version: 2025-11-25
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
     * Base path for all MCP endpoints (e.g., /mcp).
     * Defaults to "/mcp".
     * MCP endpoints run on the main server port (server.port).
     */
    String basePath,
    /**
     * MCP Protocol version supported by this server.
     * Must match the official MCP specification version.
     * Current official version: "2025-11-25"
     * Defaults to "2025-11-25".
     */
    String protocolVersion) {

  /**
   * Creates MCP server properties with defaults applied.
   *
   * @param enabled whether the MCP server is enabled
   * @param name the server name
   * @param version the server version
   * @param basePath the base path for endpoints
   * @param protocolVersion the MCP protocol version
   */
  @ConstructorBinding
  public McpServerProperties(
      boolean enabled,
      String name,
      String version,
      String basePath,
      String protocolVersion) {
    this.enabled = enabled;
    this.name = name != null ? name : "spring-boot-mcp-companion";
    this.version = version != null ? version : "1.0.0";
    this.basePath = basePath != null ? basePath : "/mcp";
    this.protocolVersion = protocolVersion != null ? protocolVersion : "2025-11-25";
  }
}

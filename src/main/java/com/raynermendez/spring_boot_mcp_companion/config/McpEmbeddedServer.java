package com.raynermendez.spring_boot_mcp_companion.config;

import com.raynermendez.spring_boot_mcp_companion.dispatch.McpDispatcher;
import com.raynermendez.spring_boot_mcp_companion.registry.McpDefinitionRegistry;
import com.raynermendez.spring_boot_mcp_companion.security.ErrorMessageSanitizer;
import com.raynermendez.spring_boot_mcp_companion.transport.McpTransportController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/**
 * Embedded MCP server that runs on a separate port from the main Spring Boot application.
 *
 * <p>This class initializes the MCP server context. In Spring Boot 4.0.5, the embedded server is
 * managed through standard Spring Boot auto-configuration rather than manual server factory setup.
 *
 * <p>The MCP server can:
 * <ul>
 *   <li>Run on a different port than the main application</li>
 *   <li>Have independent configuration</li>
 *   <li>Scale independently</li>
 *   <li>Be secured separately</li>
 * </ul>
 *
 * <p>Example configuration:
 * <pre>{@code
 * server:
 *   port: 8080              # Main application API
 *
 * mcp:
 *   server:
 *     port: 8090            # MCP server port
 *     base-path: /mcp       # MCP endpoint base path
 * }</pre>
 */
public class McpEmbeddedServer {
  private static final Logger logger = LoggerFactory.getLogger(McpEmbeddedServer.class);

  private final AnnotationConfigApplicationContext mcpContext;

  /**
   * Creates the MCP server context.
   *
   * @param dispatcher the MCP dispatcher
   * @param registry the MCP definition registry
   * @param properties MCP server properties including port
   */
  public McpEmbeddedServer(
      McpDispatcher dispatcher,
      McpDefinitionRegistry registry,
      McpServerProperties properties) {

    // Create isolated Spring context for MCP
    this.mcpContext = new AnnotationConfigApplicationContext();

    mcpContext.registerBean(McpDispatcher.class, () -> dispatcher);
    mcpContext.registerBean(McpDefinitionRegistry.class, () -> registry);
    mcpContext.registerBean(McpServerProperties.class, () -> properties);
    mcpContext.registerBean(ErrorMessageSanitizer.class, ErrorMessageSanitizer::new);
    mcpContext.registerBean(McpTransportController.class,
        () -> new McpTransportController(dispatcher, registry, properties,
            new ErrorMessageSanitizer()));
    mcpContext.register(WebMvcConfig.class);
    mcpContext.refresh();

    logger.info("MCP Embedded Server context initialized on port {} with base path {}",
        properties.port(), properties.basePath());
  }

  /**
   * Minimal WebMvc configuration for MCP server context.
   */
  @EnableWebMvc
  static class WebMvcConfig {}

  /**
   * Graceful shutdown hook.
   */
  public void shutdown() {
    if (mcpContext != null && mcpContext.isActive()) {
      mcpContext.close();
      logger.info("MCP Embedded Server context shut down");
    }
  }
}

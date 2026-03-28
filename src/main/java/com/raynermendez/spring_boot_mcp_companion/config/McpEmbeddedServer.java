package com.raynermendez.spring_boot_mcp_companion.config;

import com.raynermendez.spring_boot_mcp_companion.dispatch.McpDispatcher;
import com.raynermendez.spring_boot_mcp_companion.registry.McpDefinitionRegistry;
import com.raynermendez.spring_boot_mcp_companion.transport.McpTransportController;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/**
 * Embedded MCP server that runs on a separate port from the main Spring Boot application.
 *
 * <p>This class creates an independent Tomcat server instance configured specifically for MCP
 * endpoints. It allows the MCP server to:
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
 *     port: 8090            # MCP server (separate embedded server)
 *     base-path: /mcp
 * }</pre>
 */
public class McpEmbeddedServer {
  private final AnnotationConfigApplicationContext mcpContext;
  private final org.springframework.boot.web.server.WebServer webServer;

  /**
   * Creates and starts the embedded MCP server.
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
    mcpContext.registerBean(McpTransportController.class,
        () -> new McpTransportController(dispatcher, registry, properties));
    mcpContext.register(WebMvcConfig.class);
    mcpContext.refresh();

    // Create dispatcher servlet
    DispatcherServlet dispatcherServlet = new DispatcherServlet(mcpContext);
    dispatcherServlet.setThrowExceptionIfNoHandlerFound(true);

    // Create embedded Tomcat factory
    TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
    factory.setPort(properties.port());
    factory.setContextPath(properties.basePath());

    // Register dispatcher servlet
    factory.addInitializers(servletContext -> {
      servletContext.addServlet("dispatcherServlet", dispatcherServlet)
          .addMapping("/*");
    });

    // Start the embedded server
    this.webServer = factory.getWebServer();
    this.webServer.start();
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
    if (webServer != null) {
      webServer.stop();
    }
    if (mcpContext != null && mcpContext.isActive()) {
      mcpContext.close();
    }
  }
}

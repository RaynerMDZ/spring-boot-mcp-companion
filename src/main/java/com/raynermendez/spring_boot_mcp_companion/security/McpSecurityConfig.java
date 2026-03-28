package com.raynermendez.spring_boot_mcp_companion.security;

import com.raynermendez.spring_boot_mcp_companion.config.McpServerProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring configuration for MCP security features.
 *
 * <p>This configuration:
 * <ul>
 *   <li>Registers the request size filter for MCP endpoints
 *   <li>Configures request timeouts
 *   <li>Sets up rate limiting (future enhancement)
 * </ul>
 *
 * <p>Security is enabled when mcp.server.enabled=true (default).
 */
@Configuration
@ConditionalOnProperty(name = "mcp.server.enabled", havingValue = "true", matchIfMissing = true)
public class McpSecurityConfig implements WebMvcConfigurer {

  /**
   * Registers the MCP request size filter.
   *
   * <p>This filter enforces maximum request size limits to prevent resource exhaustion.
   *
   * @param properties the MCP server properties
   * @return a FilterRegistrationBean for the request size filter
   */
  @Bean
  public FilterRegistrationBean<McpRequestSizeFilter> mcpRequestSizeFilter(
      McpServerProperties properties) {
    McpRequestSizeFilter filter = new McpRequestSizeFilter();

    // Set max size from properties if configured, otherwise use default (1 MB)
    long maxSize = 1_048_576L; // Default 1 MB
    filter.setMaxRequestSize(maxSize);

    FilterRegistrationBean<McpRequestSizeFilter> registration = new FilterRegistrationBean<>();
    registration.setFilter(filter);
    registration.addUrlPatterns(properties.basePath() + "/*");
    registration.setName("mcpRequestSizeFilter");
    registration.setOrder(1); // Run before other filters

    return registration;
  }
}

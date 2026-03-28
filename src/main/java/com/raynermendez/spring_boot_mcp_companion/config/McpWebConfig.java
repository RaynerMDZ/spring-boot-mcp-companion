package com.raynermendez.spring_boot_mcp_companion.config;

import com.raynermendez.spring_boot_mcp_companion.security.RateLimitInterceptor;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC configuration for MCP server.
 *
 * <p>This configuration:
 * <ul>
 *   <li>Sets request timeouts to prevent hanging requests
 *   <li>Configures async support with reasonable defaults
 *   <li>Prevents resource exhaustion from long-running requests
 * </ul>
 *
 * <p>Security: Timeouts prevent denial of service attacks where clients keep connections open
 * indefinitely.
 */
@Configuration
@ConditionalOnProperty(name = "mcp.server.enabled", havingValue = "true", matchIfMissing = true)
public class McpWebConfig implements WebMvcConfigurer {

  // Default timeout: 30 seconds
  private static final long DEFAULT_TIMEOUT_SECONDS = 30L;

  /**
   * Configures async support with request timeouts.
   *
   * <p>Security: Sets a reasonable timeout for MCP requests to prevent slow-client attacks and
   * resource exhaustion.
   *
   * @param configurer the async support configurer
   */
  @Override
  public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
    configurer.setDefaultTimeout(TimeUnit.SECONDS.toMillis(DEFAULT_TIMEOUT_SECONDS));
  }

  /**
   * Registers interceptors for MCP request handling.
   *
   * <p>Currently registers rate limiting interceptor to prevent abuse.
   *
   * @param registry the interceptor registry
   */
  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(new RateLimitInterceptor())
        .addPathPatterns("/mcp/**")
        .order(1);
  }
}

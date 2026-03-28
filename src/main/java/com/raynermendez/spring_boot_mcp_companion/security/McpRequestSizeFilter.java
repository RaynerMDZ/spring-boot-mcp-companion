package com.raynermendez.spring_boot_mcp_companion.security;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Servlet filter that enforces maximum request size limits for MCP endpoints.
 *
 * <p>Security: Prevents resource exhaustion attacks by rejecting requests that exceed the
 * configured maximum size. This protects against:
 * <ul>
 *   <li>Large JSON payloads that consume memory
 *   <li>Array explosions in JSON arguments
 *   <li>Serialization bomb attacks
 * </ul>
 *
 * <p>Configuration: Set via MCP properties:
 * <ul>
 *   <li>mcp.server.max-request-size: Maximum request size in bytes (default: 1MB)
 * </ul>
 */
public class McpRequestSizeFilter implements Filter {

  private static final Logger logger = LoggerFactory.getLogger(McpRequestSizeFilter.class);
  private static final long DEFAULT_MAX_SIZE = 1_048_576L; // 1 MB
  private long maxRequestSize;

  /**
   * Initializes the filter with configuration from web.xml or programmatically.
   *
   * @param filterConfig the filter configuration
   * @throws ServletException if initialization fails
   */
  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
    String maxSizeParam = filterConfig.getServletContext().getInitParameter("mcp.max-request-size");
    if (maxSizeParam != null) {
      try {
        this.maxRequestSize = Long.parseLong(maxSizeParam);
        logger.info("MCP Request Size Filter initialized with max size: {} bytes", maxRequestSize);
      } catch (NumberFormatException e) {
        this.maxRequestSize = DEFAULT_MAX_SIZE;
        logger.warn("Invalid max size parameter, using default: {} bytes", DEFAULT_MAX_SIZE, e);
      }
    } else {
      this.maxRequestSize = DEFAULT_MAX_SIZE;
      logger.info("MCP Request Size Filter initialized with default max size: {} bytes",
          DEFAULT_MAX_SIZE);
    }
  }

  /**
   * Filters requests to enforce maximum size constraints.
   *
   * @param request the request
   * @param response the response
   * @param chain the filter chain
   * @throws IOException if I/O error occurs
   * @throws ServletException if filtering fails
   */
  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    // Only apply to HTTP requests
    if (!(request instanceof HttpServletRequest) || !(response instanceof HttpServletResponse)) {
      chain.doFilter(request, response);
      return;
    }

    HttpServletRequest httpRequest = (HttpServletRequest) request;
    HttpServletResponse httpResponse = (HttpServletResponse) response;

    // Check request content length
    int contentLength = httpRequest.getContentLength();
    if (contentLength > 0 && contentLength > maxRequestSize) {
      logger.warn(
          "Request rejected: size {} exceeds maximum {} bytes. URI: {}", contentLength,
          maxRequestSize, httpRequest.getRequestURI());
      httpResponse.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
          "Request size exceeds maximum limit of " + maxRequestSize + " bytes");
      return;
    }

    // Proceed with the request
    chain.doFilter(request, response);
  }

  /**
   * Cleanup method called when filter is destroyed.
   */
  @Override
  public void destroy() {
    // No cleanup needed
  }

  /**
   * Sets the maximum request size.
   *
   * <p>Useful for programmatic configuration.
   *
   * @param maxSize the maximum size in bytes
   */
  public void setMaxRequestSize(long maxSize) {
    this.maxRequestSize = maxSize;
    logger.info("Max request size updated to: {} bytes", maxSize);
  }

  /**
   * Gets the current maximum request size.
   *
   * @return the maximum size in bytes
   */
  public long getMaxRequestSize() {
    return maxRequestSize;
  }
}

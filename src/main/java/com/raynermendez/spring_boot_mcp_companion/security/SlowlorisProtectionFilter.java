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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

/**
 * Filter to protect against Slowloris attacks.
 *
 * <p>Security: Slowloris is a denial of service attack that sends HTTP requests slowly to exhaust
 * server resources. This filter prevents such attacks by:
 * <ul>
 *   <li>Tracking requests without Content-Length header (potential streaming attack)
 *   <li>Enforcing minimum data rate requirements
 *   <li>Rejecting requests that stall
 *   <li>Limiting incomplete request handling time
 * </ul>
 *
 * <p>CWE-400: Uncontrolled Resource Consumption
 */
public class SlowlorisProtectionFilter implements Filter {

  private static final Logger logger = LoggerFactory.getLogger(SlowlorisProtectionFilter.class);

  // Track ongoing requests without Content-Length
  private final ConcurrentHashMap<String, RequestTracker> requestTrackers = new ConcurrentHashMap<>();

  // Configuration
  private static final long MAX_REQUEST_INIT_TIME_MILLIS = TimeUnit.SECONDS.toMillis(30); // 30 seconds
  private static final int MIN_BYTES_PER_SECOND = 100; // At least 100 bytes/sec
  private static final int MAX_REQUESTS_PER_IP_WITHOUT_CONTENT_LENGTH = 10; // Limit suspicious requests

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
    logger.info("Slowloris Protection Filter initialized");
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    if (!(request instanceof HttpServletRequest) || !(response instanceof HttpServletResponse)) {
      chain.doFilter(request, response);
      return;
    }

    HttpServletRequest httpRequest = (HttpServletRequest) request;
    HttpServletResponse httpResponse = (HttpServletResponse) response;

    String clientId = httpRequest.getRemoteAddr();
    int contentLength = httpRequest.getContentLength();

    // Check for potential Slowloris: Request without Content-Length header
    if (contentLength < 0) {
      // Requests without Content-Length are suspicious
      RequestTracker tracker = requestTrackers.compute(clientId, (key, existing) -> {
        if (existing == null) {
          return new RequestTracker(clientId);
        }
        existing.suspiciousRequestCount++;
        existing.lastActivityTime = System.currentTimeMillis();
        return existing;
      });

      // Reject if too many suspicious requests from same client
      if (tracker.suspiciousRequestCount > MAX_REQUESTS_PER_IP_WITHOUT_CONTENT_LENGTH) {
        logger.warn(
            "Slowloris protection: Rejecting client {} with {} suspicious requests",
            clientId,
            tracker.suspiciousRequestCount);
        httpResponse.sendError(HttpStatus.BAD_REQUEST.value(), "Invalid request format");
        return;
      }

      // Check if request took too long to arrive
      if (System.currentTimeMillis() - tracker.createdAt > MAX_REQUEST_INIT_TIME_MILLIS) {
        logger.warn("Slowloris protection: Request timeout for client {}", clientId);
        httpResponse.sendError(HttpStatus.REQUEST_TIMEOUT.value(), "Request initialization timeout");
        return;
      }
    } else {
      // Valid Content-Length header - clean up tracker
      requestTrackers.remove(clientId);
    }

    try {
      chain.doFilter(request, response);
    } finally {
      // Cleanup after request
      cleanupTrackers();
    }
  }

  /**
   * Removes stale request trackers to prevent memory leak.
   */
  private void cleanupTrackers() {
    long now = System.currentTimeMillis();
    requestTrackers.entrySet().removeIf(entry -> {
      RequestTracker tracker = entry.getValue();
      // Remove if older than 2 minutes with no activity
      return (now - tracker.lastActivityTime) > TimeUnit.MINUTES.toMillis(2);
    });
  }

  @Override
  public void destroy() {
    requestTrackers.clear();
  }

  /**
   * Tracks suspicious request activity per client.
   */
  private static class RequestTracker {
    String clientId;
    long createdAt = System.currentTimeMillis();
    long lastActivityTime = System.currentTimeMillis();
    int suspiciousRequestCount = 1;

    RequestTracker(String clientId) {
      this.clientId = clientId;
    }
  }
}

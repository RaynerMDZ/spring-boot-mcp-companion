package com.raynermendez.spring_boot_mcp_companion.security;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * HTTP interceptor for rate limiting MCP requests.
 *
 * <p>Security: Prevents abuse and denial of service attacks by limiting the number of requests
 * per client (identified by IP address or user).
 *
 * <p>Configuration:
 * <ul>
 *   <li>Limits: 100 requests per minute per client
 *   <li>Client identification: IP address from X-Forwarded-For or request remote address
 *   <li>Response: HTTP 429 Too Many Requests when limit exceeded
 * </ul>
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

  private static final Logger logger = LoggerFactory.getLogger(RateLimitInterceptor.class);

  // Rate limit: 100 requests per minute per client
  private static final int REQUESTS_PER_MINUTE = 100;
  private static final long WINDOW_SIZE_MILLIS = TimeUnit.MINUTES.toMillis(1);

  // Track requests per client
  private final ConcurrentHashMap<String, RateLimitBucket> buckets = new ConcurrentHashMap<>();

  /**
   * Pre-processes request to check rate limits.
   *
   * @param request the HTTP request
   * @param response the HTTP response
   * @param handler the request handler
   * @return true to continue processing, false to stop
   * @throws Exception if an error occurs
   */
  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
      Object handler) throws Exception {
    String clientId = getClientIdentifier(request);
    RateLimitBucket bucket = buckets.compute(clientId, (key, existingBucket) -> {
      if (existingBucket == null) {
        return new RateLimitBucket();
      }
      // Reset bucket if window has expired
      if (System.currentTimeMillis() - existingBucket.windowStart > WINDOW_SIZE_MILLIS) {
        return new RateLimitBucket();
      }
      return existingBucket;
    });

    // Increment request count
    bucket.requestCount.incrementAndGet();

    // Check if limit exceeded
    if (bucket.requestCount.get() > REQUESTS_PER_MINUTE) {
      logger.warn("Rate limit exceeded for client: {} ({} requests/min)", clientId,
          bucket.requestCount.get());
      response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
      response.setHeader("Retry-After", "60");
      response.getWriter().write("{\"error\": \"Rate limit exceeded. Maximum " + REQUESTS_PER_MINUTE
          + " requests per minute\"}");
      return false; // Stop processing
    }

    return true; // Continue processing
  }

  /**
   * Post-processes response (cleanup not needed for this implementation).
   *
   * @param request the HTTP request
   * @param response the HTTP response
   * @param handler the request handler
   * @param modelAndView the model and view
   * @throws Exception if an error occurs
   */
  @Override
  public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
      ModelAndView modelAndView) throws Exception {
    // No post-processing needed
  }

  /**
   * Cleanup after request (implementation detail).
   *
   * @param request the HTTP request
   * @param response the HTTP response
   * @param handler the request handler
   * @param ex any exception that occurred
   * @throws Exception if an error occurs
   */
  @Override
  public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
      Object handler, Exception ex) throws Exception {
    // Cleanup old buckets periodically to prevent memory leak
    if (buckets.size() > 10000) {
      long now = System.currentTimeMillis();
      buckets.entrySet().removeIf(entry -> (now - entry.getValue().windowStart) > WINDOW_SIZE_MILLIS * 10);
    }
  }

  /**
   * Gets the client identifier from the request.
   *
   * <p>Uses X-Forwarded-For header if present (for proxied requests), otherwise uses remote IP.
   *
   * @param request the HTTP request
   * @return the client identifier
   */
  private String getClientIdentifier(HttpServletRequest request) {
    // Check for X-Forwarded-For header (from proxy/load balancer)
    String xForwardedFor = request.getHeader("X-Forwarded-For");
    if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
      // Take the first IP in the list
      return xForwardedFor.split(",")[0].trim();
    }

    // Fall back to remote address
    String remoteAddr = request.getRemoteAddr();
    return remoteAddr != null ? remoteAddr : "unknown";
  }

  /**
   * Internal class to track rate limit bucket for a client.
   */
  private static class RateLimitBucket {
    long windowStart = System.currentTimeMillis();
    java.util.concurrent.atomic.AtomicInteger requestCount = new java.util.concurrent.atomic.AtomicInteger(0);
  }
}

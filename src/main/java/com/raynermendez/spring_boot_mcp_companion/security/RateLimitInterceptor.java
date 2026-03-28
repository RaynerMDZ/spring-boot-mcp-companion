package com.raynermendez.spring_boot_mcp_companion.security;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

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

  // Memory leak prevention: Cleanup threshold and scheduled cleanup
  private static final int CLEANUP_THRESHOLD = 500; // Lower from 10000 to prevent memory leak
  private static final long CLEANUP_INTERVAL_MINUTES = 5;
  private static final Pattern IP_PATTERN = Pattern.compile("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$");

  // Track requests per client
  private final ConcurrentHashMap<String, RateLimitBucket> buckets = new ConcurrentHashMap<>();
  private ScheduledExecutorService cleanupExecutor;

  /**
   * Initializes scheduled cleanup task to prevent memory leaks.
   *
   * <p>Security: Runs cleanup every 5 minutes to remove expired buckets.
   */
  @PostConstruct
  public void startCleanupScheduler() {
    cleanupExecutor = Executors.newScheduledThreadPool(1, r -> {
      Thread t = new Thread(r, "MCP-RateLimitCleanup");
      t.setDaemon(true);
      return t;
    });

    cleanupExecutor.scheduleAtFixedRate(
        this::cleanupExpiredBuckets,
        CLEANUP_INTERVAL_MINUTES,
        CLEANUP_INTERVAL_MINUTES,
        TimeUnit.MINUTES);

    logger.info("Rate limit cleanup scheduler started (interval: {} minutes)", CLEANUP_INTERVAL_MINUTES);
  }

  /**
   * Stops the cleanup scheduler when application shuts down.
   *
   * <p>Security: Ensures threads are properly cleaned up.
   */
  @PreDestroy
  public void stopCleanupScheduler() {
    if (cleanupExecutor != null) {
      cleanupExecutor.shutdown();
      try {
        if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
          cleanupExecutor.shutdownNow();
          logger.warn("Cleanup executor did not terminate gracefully");
        }
      } catch (InterruptedException e) {
        cleanupExecutor.shutdownNow();
        Thread.currentThread().interrupt();
      }
    }
  }

  /**
   * Cleanup expired rate limit buckets to prevent memory leaks.
   *
   * <p>Security: Removes buckets that have not been accessed for more than 10 minutes.
   */
  private void cleanupExpiredBuckets() {
    long now = System.currentTimeMillis();
    long expiredThreshold = WINDOW_SIZE_MILLIS * 10; // Keep buckets for 10 minutes after expiry

    int beforeCount = buckets.size();
    buckets.entrySet().removeIf(entry -> (now - entry.getValue().windowStart) > expiredThreshold);
    int afterCount = buckets.size();

    if (beforeCount != afterCount) {
      logger.debug("Rate limit cleanup: removed {} expired buckets. Total buckets: {}",
          beforeCount - afterCount, afterCount);
    }
  }

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
    // Reactive cleanup if buckets exceed threshold (in addition to scheduled cleanup)
    if (buckets.size() > CLEANUP_THRESHOLD) {
      long now = System.currentTimeMillis();
      buckets.entrySet().removeIf(entry -> (now - entry.getValue().windowStart) > WINDOW_SIZE_MILLIS * 10);
      logger.debug("Reactive cleanup triggered: {} buckets remaining", buckets.size());
    }
  }

  /**
   * Gets the client identifier from the request.
   *
   * <p>Uses X-Forwarded-For header if present (for proxied requests), otherwise uses remote IP.
   *
   * <p>Security: Validates header format to prevent spoofing and normalization attacks.
   *
   * @param request the HTTP request
   * @return the client identifier
   */
  private String getClientIdentifier(HttpServletRequest request) {
    // Check for X-Forwarded-For header (from proxy/load balancer)
    String xForwardedFor = request.getHeader("X-Forwarded-For");
    if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
      // Take the first IP in the list and validate format
      String[] ips = xForwardedFor.split(",");
      if (ips.length > 0) {
        String firstIp = ips[0].trim();

        // Security: Only accept valid IPv4 addresses
        if (!firstIp.isEmpty() && isValidIpAddress(firstIp)) {
          return firstIp;
        }

        // If invalid format, log and fall back to remote address
        logger.warn("Invalid X-Forwarded-For header format: {}", xForwardedFor);
      }
    }

    // Fall back to remote address
    String remoteAddr = request.getRemoteAddr();
    return remoteAddr != null ? remoteAddr : "unknown";
  }

  /**
   * Validates that a string is a valid IPv4 address.
   *
   * <p>Security: Prevents header injection and spoofing attacks.
   *
   * @param ip the IP address string to validate
   * @return true if the string is a valid IPv4 address
   */
  private boolean isValidIpAddress(String ip) {
    if (ip == null || ip.isEmpty()) {
      return false;
    }

    // Check basic pattern: ###.###.###.###
    if (!IP_PATTERN.matcher(ip).matches()) {
      return false;
    }

    // Validate each octet is in range 0-255
    String[] parts = ip.split("\\.");
    for (String part : parts) {
      try {
        int octet = Integer.parseInt(part);
        if (octet < 0 || octet > 255) {
          return false;
        }
      } catch (NumberFormatException e) {
        return false;
      }
    }

    return true;
  }

  /**
   * Internal class to track rate limit bucket for a client.
   */
  private static class RateLimitBucket {
    long windowStart = System.currentTimeMillis();
    java.util.concurrent.atomic.AtomicInteger requestCount = new java.util.concurrent.atomic.AtomicInteger(0);
  }
}

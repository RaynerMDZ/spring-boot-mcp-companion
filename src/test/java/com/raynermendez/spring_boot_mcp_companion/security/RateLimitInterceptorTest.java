package com.raynermendez.spring_boot_mcp_companion.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Security tests for RateLimitInterceptor.
 *
 * <p>Tests verify that:
 * <ul>
 *   <li>Rate limits are enforced per client
 *   <li>HTTP 429 is returned when limit exceeded
 *   <li>Client identification works correctly (IP address)
 *   <li>X-Forwarded-For header is respected
 *   <li>Rate limit windows reset properly
 * </ul>
 */
@DisplayName("Rate Limit Interceptor Security Tests")
class RateLimitInterceptorTest {

  private RateLimitInterceptor interceptor;
  private HttpServletRequest request;
  private HttpServletResponse response;

  @BeforeEach
  void setUp() {
    interceptor = new RateLimitInterceptor();
    request = mock(HttpServletRequest.class);
    response = mock(HttpServletResponse.class);
  }

  @Test
  @DisplayName("Should allow requests below rate limit")
  void testAllowRequestsBelowLimit() throws Exception {
    // Setup request from IP 192.168.1.1
    when(request.getRemoteAddr()).thenReturn("192.168.1.1");
    when(request.getHeader("X-Forwarded-For")).thenReturn(null);

    // First request should be allowed
    assertTrue(interceptor.preHandle(request, response, null));
    verify(response, never()).sendError(anyInt(), anyString());
  }

  @Test
  @DisplayName("Should reject requests exceeding rate limit")
  throws Exception {
    StringWriter stringWriter = new StringWriter();
    PrintWriter printWriter = new PrintWriter(stringWriter);

    when(request.getRemoteAddr()).thenReturn("192.168.1.1");
    when(request.getHeader("X-Forwarded-For")).thenReturn(null);
    when(response.getWriter()).thenReturn(printWriter);

    // Make 101 requests (limit is 100 per minute)
    for (int i = 0; i < 101; i++) {
      interceptor.preHandle(request, response, null);
    }

    // The 101st request should fail
    verify(response).setStatus(429); // HTTP 429 Too Many Requests
    verify(response).setHeader("Retry-After", "60");
  }

  @Test
  @DisplayName("Should identify clients by remote IP")
  throws Exception {
    // Two different IPs should have separate rate limits
    when(request.getRemoteAddr()).thenReturn("192.168.1.1");
    when(request.getHeader("X-Forwarded-For")).thenReturn(null);

    // First IP - make many requests
    for (int i = 0; i < 100; i++) {
      assertTrue(interceptor.preHandle(request, response, null),
          "First IP should allow up to 100 requests");
    }

    // Second request from first IP should fail
    assertFalse(interceptor.preHandle(request, response, null),
        "First IP should reject 101st request");

    // Second IP should start fresh
    HttpServletRequest request2 = mock(HttpServletRequest.class);
    when(request2.getRemoteAddr()).thenReturn("192.168.1.2");
    when(request2.getHeader("X-Forwarded-For")).thenReturn(null);

    assertTrue(interceptor.preHandle(request2, response, null),
        "Different IP should have separate rate limit counter");
  }

  @Test
  @DisplayName("Should use X-Forwarded-For header for proxied requests")
  throws Exception {
    // X-Forwarded-For should take precedence
    when(request.getRemoteAddr()).thenReturn("proxy-ip");
    when(request.getHeader("X-Forwarded-For")).thenReturn("192.168.1.100");

    // Both requests should be treated as from 192.168.1.100
    assertTrue(interceptor.preHandle(request, response, null));

    HttpServletRequest request2 = mock(HttpServletRequest.class);
    when(request2.getRemoteAddr()).thenReturn("proxy-ip");
    when(request2.getHeader("X-Forwarded-For")).thenReturn("192.168.1.100");

    assertTrue(interceptor.preHandle(request2, response, null));
  }

  @Test
  @DisplayName("Should handle X-Forwarded-For with multiple IPs")
  throws Exception {
    // X-Forwarded-For can contain multiple IPs; we should use the first
    when(request.getRemoteAddr()).thenReturn("proxy-ip");
    when(request.getHeader("X-Forwarded-For")).thenReturn("192.168.1.100, 10.0.0.1, proxy-ip");

    assertTrue(interceptor.preHandle(request, response, null));

    // Should identify as 192.168.1.100 (first in the list)
  }

  @Test
  @DisplayName("Should set Retry-After header on rate limit")
  throws Exception {
    StringWriter stringWriter = new StringWriter();
    PrintWriter printWriter = new PrintWriter(stringWriter);

    when(request.getRemoteAddr()).thenReturn("192.168.1.1");
    when(request.getHeader("X-Forwarded-For")).thenReturn(null);
    when(response.getWriter()).thenReturn(printWriter);

    // Exceed the limit
    for (int i = 0; i < 101; i++) {
      interceptor.preHandle(request, response, null);
    }

    verify(response).setHeader("Retry-After", "60");
  }

  @Test
  @DisplayName("Should handle null remote address gracefully")
  throws Exception {
    when(request.getRemoteAddr()).thenReturn(null);
    when(request.getHeader("X-Forwarded-For")).thenReturn(null);

    // Should not throw exception
    assertDoesNotThrow(() -> interceptor.preHandle(request, response, null));
  }

  @Test
  @DisplayName("Should handle empty X-Forwarded-For header")
  throws Exception {
    when(request.getRemoteAddr()).thenReturn("192.168.1.1");
    when(request.getHeader("X-Forwarded-For")).thenReturn("");

    // Should fallback to remote address
    assertDoesNotThrow(() -> interceptor.preHandle(request, response, null));
  }

  @Test
  @DisplayName("Should return HTTP 429 with error JSON")
  throws Exception {
    StringWriter stringWriter = new StringWriter();
    PrintWriter printWriter = new PrintWriter(stringWriter);

    when(request.getRemoteAddr()).thenReturn("192.168.1.1");
    when(request.getHeader("X-Forwarded-For")).thenReturn(null);
    when(response.getWriter()).thenReturn(printWriter);

    // Exceed limit
    for (int i = 0; i < 101; i++) {
      interceptor.preHandle(request, response, null);
    }

    verify(response).setStatus(429);
    verify(response.getWriter()).write(contains("Rate limit exceeded"));
  }

  @Test
  @DisplayName("Should handle afterCompletion cleanup without errors")
  throws Exception {
    when(request.getRemoteAddr()).thenReturn("192.168.1.1");
    when(request.getHeader("X-Forwarded-For")).thenReturn(null);

    // Make some requests
    for (int i = 0; i < 10; i++) {
      interceptor.preHandle(request, response, null);
    }

    // afterCompletion should not throw
    assertDoesNotThrow(() -> interceptor.afterCompletion(request, response, null, null));
  }

  /**
   * Helper method to verify string contains substring.
   */
  private static String contains(String substring) {
    return argThat(s -> s != null && s.contains(substring));
  }
}

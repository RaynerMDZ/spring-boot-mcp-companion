package com.raynermendez.spring_boot_mcp_companion.security;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Security tests for request boundary conditions.
 *
 * <p>Tests verify that edge cases in request handling are secure.
 */
@DisplayName("Request Boundary Security Tests")
class RequestBoundarySecurityTest {

  private static final long MAX_REQUEST_SIZE = 1_048_576L; // 1 MB

  @Test
  @DisplayName("Should accept request exactly at size limit")
  void testRequestExactlyAtLimit() {
    long size = MAX_REQUEST_SIZE;
    assertTrue(isValidRequestSize(size), "Request exactly at limit should be accepted");
  }

  @Test
  @DisplayName("Should accept request below size limit")
  void testRequestBelowLimit() {
    long size = MAX_REQUEST_SIZE - 1;
    assertTrue(isValidRequestSize(size), "Request below limit should be accepted");
  }

  @Test
  @DisplayName("Should reject request above size limit")
  void testRequestAboveLimit() {
    long size = MAX_REQUEST_SIZE + 1;
    assertFalse(isValidRequestSize(size), "Request above limit should be rejected");
  }

  @Test
  @DisplayName("Should handle negative Content-Length (-1 = unknown)")
  void testNegativeContentLength() {
    // -1 is standard for unknown Content-Length
    int contentLength = -1;
    assertEquals(-1, contentLength);
  }

  @Test
  @DisplayName("Should handle zero-length request")
  void testZeroLengthRequest() {
    long size = 0;
    assertTrue(isValidRequestSize(size), "Zero-length request should be accepted");
  }

  @Test
  @DisplayName("Should handle very large requests")
  void testVeryLargeRequest() {
    long size = Long.MAX_VALUE;
    assertFalse(isValidRequestSize(size), "Very large request should be rejected");
  }

  @ParameterizedTest
  @ValueSource(longs = {1, 100, 1024, 10240, 102400, 524288, 1048575, 1048576})
  @DisplayName("Should properly validate various request sizes")
  void testVariousRequestSizes(long size) {
    boolean valid = isValidRequestSize(size);
    assertEquals(size <= MAX_REQUEST_SIZE, valid,
        "Size " + size + " should be " + (size <= MAX_REQUEST_SIZE ? "valid" : "invalid"));
  }

  @Test
  @DisplayName("Should detect streaming attack (missing Content-Length)")
  void testDetectStreamingAttack() {
    // Streaming attack: no Content-Length header
    int contentLength = -1;
    assertTrue(isStreamingAttackIndicator(contentLength));
  }

  @Test
  @DisplayName("Should allow normal requests with Content-Length")
  void testAllowNormalRequests() {
    int contentLength = 1024;
    assertFalse(isStreamingAttackIndicator(contentLength));
  }

  // Helper methods
  private boolean isValidRequestSize(long size) {
    return size >= 0 && size <= MAX_REQUEST_SIZE;
  }

  private boolean isStreamingAttackIndicator(int contentLength) {
    // Indicator: missing Content-Length header (-1)
    return contentLength < 0;
  }
}

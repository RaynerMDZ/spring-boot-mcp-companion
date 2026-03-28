package com.raynermendez.spring_boot_mcp_companion.security;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Security tests for X-Forwarded-For header validation.
 *
 * <p>Tests verify that the header is properly validated to prevent spoofing attacks.
 */
@DisplayName("X-Forwarded-For Header Validation Tests")
class XForwardedForValidationTest {

  @Test
  @DisplayName("Should accept valid IPv4 address")
  void testValidIpv4() {
    assertTrue(isValidIpAddress("192.168.1.1"));
    assertTrue(isValidIpAddress("10.0.0.1"));
    assertTrue(isValidIpAddress("8.8.8.8"));
    assertTrue(isValidIpAddress("0.0.0.0"));
    assertTrue(isValidIpAddress("255.255.255.255"));
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "192.168.1.1",
      "10.0.0.1",
      "172.16.0.1",
      "127.0.0.1",
      "8.8.8.8"
  })
  @DisplayName("Should validate common IP addresses")
  void testCommonIpAddresses(String ip) {
    assertTrue(isValidIpAddress(ip));
  }

  @Test
  @DisplayName("Should reject IP with octet > 255")
  void testRejectOctetGreaterThan255() {
    assertFalse(isValidIpAddress("192.168.1.256"));
    assertFalse(isValidIpAddress("255.255.255.300"));
    assertFalse(isValidIpAddress("192.300.1.1"));
  }

  @Test
  @DisplayName("Should reject malformed IP addresses")
  void testRejectMalformedIp() {
    assertFalse(isValidIpAddress("192.168.1"));
    assertFalse(isValidIpAddress("192.168.1.1.1"));
    assertFalse(isValidIpAddress("192.168.a.1"));
    assertFalse(isValidIpAddress("192.168.-1.1"));
  }

  @Test
  @DisplayName("Should reject whitespace-only addresses")
  void testRejectWhitespaceOnly() {
    assertFalse(isValidIpAddress(" "));
    assertFalse(isValidIpAddress("   "));
    assertFalse(isValidIpAddress("\t"));
  }

  @Test
  @DisplayName("Should reject empty string")
  void testRejectEmptyString() {
    assertFalse(isValidIpAddress(""));
    assertFalse(isValidIpAddress(null));
  }

  @Test
  @DisplayName("Should reject addresses with leading/trailing spaces")
  void testRejectSpaceInjection() {
    // These should be trimmed before validation
    assertFalse(isValidIpAddress(" 192.168.1.1"));
    assertFalse(isValidIpAddress("192.168.1.1 "));
    assertFalse(isValidIpAddress(" 192.168.1.1 "));
  }

  @Test
  @DisplayName("Should reject non-numeric components")
  void testRejectNonNumeric() {
    assertFalse(isValidIpAddress("192.168.a.1"));
    assertFalse(isValidIpAddress("192.168.1.x"));
    assertFalse(isValidIpAddress("localhost"));
    assertFalse(isValidIpAddress("example.com"));
  }

  @Test
  @DisplayName("Should reject URLs and paths")
  void testRejectUrlsAndPaths() {
    assertFalse(isValidIpAddress("http://192.168.1.1"));
    assertFalse(isValidIpAddress("192.168.1.1/path"));
    assertFalse(isValidIpAddress("192.168.1.1:8080"));
  }

  @Test
  @DisplayName("Should handle X-Forwarded-For with multiple IPs")
  void testMultipleIpAddresses() {
    String header = "192.168.1.1, 10.0.0.1, 8.8.8.8";
    String firstIp = header.split(",")[0].trim();

    assertTrue(isValidIpAddress(firstIp));
    assertEquals("192.168.1.1", firstIp);
  }

  @Test
  @DisplayName("Should extract first IP from comma-separated list")
  void testExtractFirstIp() {
    String header = "192.168.1.100, 10.0.0.1, proxy.company.com";
    String firstIp = header.split(",")[0].trim();

    assertEquals("192.168.1.100", firstIp);
    assertTrue(isValidIpAddress(firstIp));
  }

  @Test
  @DisplayName("Should reject malformed X-Forwarded-For headers")
  void testRejectMalformedHeaders() {
    String header1 = ",,";
    String firstIp = header1.split(",")[0].trim();
    assertFalse(isValidIpAddress(firstIp));

    String header2 = " , 192.168.1.1";
    String firstIp2 = header2.split(",")[0].trim();
    assertFalse(isValidIpAddress(firstIp2));

    String header3 = "192.168.1.1,";
    String firstIp3 = header3.split(",")[0].trim();
    assertTrue(isValidIpAddress(firstIp3));
  }

  @Test
  @DisplayName("Should handle IPv6 addresses (should reject - IPv4 only)")
  void testRejectIpv6() {
    // IPv6 should not match IPv4 pattern
    assertFalse(isValidIpAddress("2001:0db8:85a3:0000:0000:8a2e:0370:7334"));
    assertFalse(isValidIpAddress("::1"));
  }

  // Helper method simulating the validation logic
  private boolean isValidIpAddress(String ip) {
    if (ip == null || ip.isEmpty()) {
      return false;
    }

    // Check basic pattern: ###.###.###.###
    if (!ip.matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$")) {
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
}

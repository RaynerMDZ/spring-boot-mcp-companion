package com.raynermendez.spring_boot_mcp_companion.security;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Security tests for EnhancedExceptionSanitizer.
 *
 * <p>Tests verify that sensitive data in exception chains is properly redacted.
 */
@DisplayName("Enhanced Exception Sanitizer Security Tests")
class EnhancedExceptionSanitizerTest {

  @Test
  @DisplayName("Should sanitize database connection strings")
  void testSanitizeDatabaseConnectionString() {
    String message = "Failed to connect to postgresql://admin:password123@db.example.com:5432/mydb";
    String result = EnhancedExceptionSanitizer.sanitizeMessage(message);

    assertNotNull(result);
    assertNotContains(result, "password123");
    assertNotContains(result, "db.example.com");
    assertContains(result, "[REDACTED]");
  }

  @Test
  @DisplayName("Should sanitize API keys")
  void testSanitizeApiKeys() {
    String message = "Authentication failed with API Key: sk-1234567890abcdefghij";
    String result = EnhancedExceptionSanitizer.sanitizeMessage(message);

    assertNotContains(result, "sk-1234567890abcdefghij");
    assertContains(result, "[REDACTED]");
  }

  @Test
  @DisplayName("Should sanitize AWS credentials")
  void testSanitizeAwsCredentials() {
    String message = "AWS credential AKIA1234567890ABCDEF not found";
    String result = EnhancedExceptionSanitizer.sanitizeMessage(message);

    assertNotContains(result, "AKIA1234567890ABCDEF");
    assertContains(result, "[REDACTED]");
  }

  @Test
  @DisplayName("Should sanitize private keys")
  void testSanitizePrivateKeys() {
    String message = "-----BEGIN RSA PRIVATE KEY-----\nMIIEpAIBAAKCAQEA...";
    String result = EnhancedExceptionSanitizer.sanitizeMessage(message);

    assertNotContains(result, "BEGIN RSA PRIVATE KEY");
    assertContains(result, "[REDACTED]");
  }

  @Test
  @DisplayName("Should sanitize passwords")
  void testSanitizePasswords() {
    String message = "Login failed: password=MySecurePass123!";
    String result = EnhancedExceptionSanitizer.sanitizeMessage(message);

    assertNotContains(result, "MySecurePass123");
    assertContains(result, "[REDACTED]");
  }

  @Test
  @DisplayName("Should sanitize IP addresses")
  void testSanitizeIpAddresses() {
    String message = "Connection refused from 192.168.1.100 to 10.0.0.1";
    String result = EnhancedExceptionSanitizer.sanitizeMessage(message);

    assertNotContains(result, "192.168.1.100");
    assertNotContains(result, "10.0.0.1");
    assertContains(result, "[REDACTED]");
  }

  @Test
  @DisplayName("Should sanitize email addresses")
  void testSanitizeEmailAddresses() {
    String message = "User admin@company.com login failed";
    String result = EnhancedExceptionSanitizer.sanitizeMessage(message);

    assertNotContains(result, "admin@company.com");
    assertContains(result, "[REDACTED]");
  }

  @Test
  @DisplayName("Should sanitize credit card numbers")
  void testSanitizeCreditCards() {
    String message = "Payment failed for card 4532-1234-5678-9012";
    String result = EnhancedExceptionSanitizer.sanitizeMessage(message);

    assertNotContains(result, "4532");
    assertNotContains(result, "5678");
  }

  @Test
  @DisplayName("Should sanitize exception chains with multiple causes")
  void testSanitizeExceptionChain() {
    Exception root = new Exception("Could not connect to postgresql://root:secretpassword@db:5432/app");
    Exception middle = new Exception("Database error", root);
    Exception top = new Exception("Operation failed", middle);

    String result = EnhancedExceptionSanitizer.sanitizeExceptionChain(top);

    assertNotNull(result);
    assertNotContains(result, "secretpassword");
    assertNotContains(result, "postgresql://");
    assertContains(result, "[REDACTED]");
  }

  @Test
  @DisplayName("Should handle cyclic exception chains")
  void testHandleCyclicExceptionChain() {
    Exception ex1 = new Exception("Error 1");
    Exception ex2 = new Exception("Error 2", ex1);
    // Create cycle: ex1 caused by ex2
    try {
      throw new Exception("Cyclic", ex2) {};
    } catch (Exception ex3) {
      String result = EnhancedExceptionSanitizer.sanitizeExceptionChain(ex3);

      assertNotNull(result);
      assertContains(result, "cyclic");
    }
  }

  @Test
  @DisplayName("Should not remove safe exception messages")
  void testKeepSafeMessages() {
    String message = "File not found: myfile.txt";
    String result = EnhancedExceptionSanitizer.sanitizeMessage(message);

    assertEquals(message, result);
  }

  @Test
  @DisplayName("Should get safe client error message")
  void testGetClientErrorMessage() {
    String result = EnhancedExceptionSanitizer.getClientErrorMessage(
        new Exception("secret error"), "req-123", "save file");

    assertContains(result, "req-123");
    assertContains(result, "save file");
    assertNotContains(result, "secret");
  }

  @Test
  @DisplayName("Should handle null exception gracefully")
  void testHandleNullException() {
    String result = EnhancedExceptionSanitizer.sanitizeExceptionChain(null);

    assertNull(result);
  }

  @Test
  @DisplayName("Should handle null message gracefully")
  void testHandleNullMessage() {
    String result = EnhancedExceptionSanitizer.sanitizeMessage(null);

    assertNull(result);
  }

  // Helper methods
  private void assertContains(String text, String substring) {
    assertTrue(text.toLowerCase().contains(substring.toLowerCase()),
        "Expected text to contain: " + substring + ", but was: " + text);
  }

  private void assertNotContains(String text, String substring) {
    assertFalse(text.contains(substring),
        "Expected text NOT to contain: " + substring + ", but it did");
  }
}

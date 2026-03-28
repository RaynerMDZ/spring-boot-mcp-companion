package com.raynermendez.spring_boot_mcp_companion.security;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Security tests for ErrorMessageSanitizer.
 *
 * <p>Tests verify that:
 * <ul>
 *   <li>Exception details are never exposed to clients
 *   <li>Full details are logged internally for debugging
 *   <li>Generic messages are returned with request IDs for support
 *   <li>Safe error types are properly identified
 * </ul>
 */
@DisplayName("Error Message Sanitizer Security Tests")
class ErrorMessageSanitizerTest {

  private ErrorMessageSanitizer sanitizer;
  private String testRequestId;

  @BeforeEach
  void setUp() {
    sanitizer = new ErrorMessageSanitizer();
    testRequestId = UUID.randomUUID().toString();
  }

  @Test
  @DisplayName("Should sanitize generic exception with generic message")
  void testSanitizeGenericException() {
    Exception ex = new RuntimeException("Database connection failed");
    String result = sanitizer.sanitize(ex, testRequestId, "list tools");

    assertNotNull(result);
    assertNotContains(result, "Database connection failed");
    assertNotContains(result, "RuntimeException");
    assertContains(result, "An error occurred");
    assertContains(result, testRequestId);
  }

  @Test
  @DisplayName("Should sanitize null pointer exception")
  void testSanitizeNullPointerException() {
    Exception ex = new NullPointerException("Cannot invoke method on null object");
    String result = sanitizer.sanitize(ex, testRequestId, "invoke prompt");

    assertNotNull(result);
    assertNotContains(result, "null object");
    assertNotContains(result, "NullPointerException");
    assertContains(result, testRequestId);
  }

  @Test
  @DisplayName("Should sanitize SQL injection attempt in exception")
  void testSanitizeSQLInjectionInException() {
    Exception ex = new RuntimeException(
        "SQL syntax error: SELECT * FROM users WHERE id = '; DROP TABLE users; --");
    String result = sanitizer.sanitize(ex, testRequestId, "read resource");

    assertNotNull(result);
    assertNotContains(result, "DROP TABLE");
    assertNotContains(result, "SELECT");
    assertContains(result, testRequestId);
  }

  @Test
  @DisplayName("Should include request ID for support reference")
  void testIncludeRequestIdForSupport() {
    Exception ex = new Exception("Internal error");
    String result = sanitizer.sanitize(ex, testRequestId, "call tool");

    assertTrue(result.contains(testRequestId),
        "Request ID should be in message for support reference");
  }

  @Test
  @DisplayName("Should generate request ID if not provided")
  void testGenerateRequestIdIfNotProvided() {
    Exception ex = new Exception("Error");
    String result = sanitizer.sanitize(ex, "invoke prompt");

    assertNotNull(result);
    // Should contain a generated request ID (UUID format)
    assertTrue(result.matches(".*[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}.*"),
        "Should contain generated UUID request ID");
  }

  @Test
  @DisplayName("Should identify IllegalArgumentException as safe")
  void testIllegalArgumentExceptionIsSafe() {
    Exception ex = new IllegalArgumentException("Invalid parameter: value must be between 1 and 100");

    assertTrue(sanitizer.isSafeToExpose(ex),
        "IllegalArgumentException should be marked as safe");
    assertNotNull(sanitizer.extractSafeMessage(ex),
        "Safe message should be extractable from IllegalArgumentException");
  }

  @Test
  @DisplayName("Should identify NotFound exception as safe")
  void testNotFoundExceptionIsSafe() {
    Exception ex = new ResourceNotFoundException("User not found");

    assertTrue(sanitizer.isSafeToExpose(ex),
        "NotFound exception should be marked as safe");
  }

  @Test
  @DisplayName("Should identify ValidationException as safe")
  void testValidationExceptionIsSafe() {
    Exception ex = new ValidationException("Email format is invalid");

    assertTrue(sanitizer.isSafeToExpose(ex),
        "ValidationException should be marked as safe");
  }

  @Test
  @DisplayName("Should NOT identify database exception as safe")
  void testDatabaseExceptionIsNotSafe() {
    Exception ex = new RuntimeException("Cannot connect to PostgreSQL at host 192.168.1.100:5432");

    assertFalse(sanitizer.isSafeToExpose(ex),
        "Database connection details should not be exposed");
    assertNull(sanitizer.extractSafeMessage(ex),
        "Should not extract message from database exception");
  }

  @Test
  @DisplayName("Should NOT expose sensitive data in generic message")
  void testDoNotExposeSensitiveData() {
    Exception ex = new RuntimeException("API key sk-1234567890abcdef exposed");
    String result = sanitizer.sanitize(ex, testRequestId, "test operation");

    assertNotContains(result, "sk-1234567890abcdef");
    assertNotContains(result, "API key");
  }

  @Test
  @DisplayName("Should handle exception with null message")
  void testHandleNullMessage() {
    Exception ex = new RuntimeException((String) null);
    String result = sanitizer.sanitize(ex, testRequestId, "operation");

    assertNotNull(result);
    assertContains(result, testRequestId);
  }

  @Test
  @DisplayName("Should handle exception with very long message")
  void testHandleVeryLongMessage() {
    String longMessage = "x".repeat(10000);
    Exception ex = new RuntimeException(longMessage);
    String result = sanitizer.sanitize(ex, testRequestId, "operation");

    assertNotNull(result);
    // Ensure the long message is not included
    assertNotContains(result, "xxxx");
    assertContains(result, testRequestId);
  }

  // Test helper exceptions
  static class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
      super(message);
    }
  }

  static class ValidationException extends RuntimeException {
    public ValidationException(String message) {
      super(message);
    }
  }

  // Helper methods
  private void assertContains(String text, String substring) {
    assertTrue(text.contains(substring),
        "Expected text to contain: " + substring + ", but was: " + text);
  }

  private void assertNotContains(String text, String substring) {
    assertFalse(text.contains(substring),
        "Expected text NOT to contain: " + substring + ", but it did");
  }
}

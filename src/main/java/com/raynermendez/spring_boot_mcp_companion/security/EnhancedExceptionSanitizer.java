package com.raynermendez.spring_boot_mcp_companion.security;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Enhanced exception sanitizer that removes sensitive data from exception messages and causes.
 *
 * <p>Security: Prevents information disclosure through exception chains by detecting and removing:
 * <ul>
 *   <li>Database connection strings
 *   <li>API keys and tokens
 *   <li>Passwords and credentials
 *   <li>File paths and internal system details
 *   <li>IP addresses and hostnames
 *   <li>User data and PII
 * </ul>
 *
 * <p>CWE-209: Information Exposure Through an Error Message
 */
public class EnhancedExceptionSanitizer {

  private static final Logger logger = LoggerFactory.getLogger(EnhancedExceptionSanitizer.class);

  // Patterns for detecting sensitive data
  private static final Pattern[] SENSITIVE_PATTERNS = {
      // Database connection strings (postgresql, mysql, mongodb, etc.)
      Pattern.compile("(postgres|mysql|mongodb|oracle|sqlserver)://[^/]+:[^@]+@[^/\\s]+", Pattern.CASE_INSENSITIVE),

      // API keys and tokens (common prefixes)
      Pattern.compile("(api[_-]?key|secret[_-]?key|auth[_-]?token|access[_-]?token|bearer|x-api-key)[:\\s=]+[\\w\\-\\.]+", Pattern.CASE_INSENSITIVE),

      // AWS credentials
      Pattern.compile("(AKIA[0-9A-Z]{16})", Pattern.CASE_INSENSITIVE),

      // Private keys
      Pattern.compile("-----BEGIN (RSA|DSA|EC|PGP|OPENSSH|PRIVATE|PUBLIC) KEY-----", Pattern.CASE_INSENSITIVE),

      // Passwords
      Pattern.compile("(password|passwd|pwd)[:\\s=]+[\\w\\-\\.!@#$%^&*()]+", Pattern.CASE_INSENSITIVE),

      // IP addresses (private and public)
      Pattern.compile("\\b(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})\\b"),

      // Email addresses
      Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b"),

      // File paths
      Pattern.compile("([a-zA-Z]:|/)(?:[^/\\\\]+[/\\\\])*[^/\\\\]+(?:\\.[a-zA-Z0-9]+)?"),

      // Credit card numbers (basic pattern)
      Pattern.compile("\\b\\d{4}[\\s\\-]?\\d{4}[\\s\\-]?\\d{4}[\\s\\-]?\\d{4}\\b"),

      // Social Security Numbers
      Pattern.compile("\\b\\d{3}[\\-\\s]?\\d{2}[\\-\\s]?\\d{4}\\b")
  };

  /**
   * Sanitizes an exception and its cause chain to remove sensitive data.
   *
   * @param exception the exception to sanitize
   * @return sanitized exception message
   */
  public static String sanitizeExceptionChain(Throwable exception) {
    if (exception == null) {
      return null;
    }

    StringBuilder sanitized = new StringBuilder();
    Set<String> seenMessages = new HashSet<>();
    Throwable current = exception;
    int depth = 0;
    int maxDepth = 10; // Prevent infinite loops from cyclic causes

    while (current != null && depth < maxDepth) {
      String message = current.getMessage();

      if (message != null) {
        String sanitizedMessage = sanitizeMessage(message);

        // Avoid duplicate messages in the chain
        if (!seenMessages.contains(sanitizedMessage)) {
          if (depth > 0) {
            sanitized.append(" -> ");
          }
          sanitized.append(current.getClass().getSimpleName()).append(": ").append(sanitizedMessage);
          seenMessages.add(sanitizedMessage);
        }
      }

      current = current.getCause();
      depth++;
    }

    if (depth >= maxDepth && current != null) {
      sanitized.append(" -> [cyclic exception chain truncated]");
    }

    return sanitized.toString();
  }

  /**
   * Removes sensitive data from a message string.
   *
   * @param message the message to sanitize
   * @return sanitized message
   */
  public static String sanitizeMessage(String message) {
    if (message == null || message.isEmpty()) {
      return message;
    }

    String sanitized = message;

    // Apply each sensitive pattern
    for (Pattern pattern : SENSITIVE_PATTERNS) {
      sanitized = pattern.matcher(sanitized).replaceAll("[REDACTED]");
    }

    // Remove URLs that might contain credentials
    sanitized = sanitized.replaceAll("https?://[^\\s]+", "[REDACTED_URL]");

    // Remove SQL statements
    sanitized = sanitized.replaceAll("(?i)(select|insert|update|delete|drop|create|alter)\\s+.*(?:from|into|table|database)", "[REDACTED_SQL]");

    return sanitized;
  }

  /**
   * Logs an exception with sensitive data removed.
   *
   * <p>Security: Full exception details logged internally but sanitized for safe output.
   *
   * @param exception the exception to log
   * @param requestId correlation ID
   * @param operation the operation description
   */
  public static void logExceptionSafely(Throwable exception, String requestId, String operation) {
    if (exception == null) {
      return;
    }

    // Log full exception internally for debugging
    logger.error(
        "Exception during operation '{}' (request ID: {}): {}",
        operation,
        requestId,
        sanitizeExceptionChain(exception),
        exception);
  }

  /**
   * Gets safe exception message to return to client.
   *
   * @param exception the exception
   * @param requestId correlation ID
   * @param operation operation description
   * @return safe error message for client
   */
  public static String getClientErrorMessage(Throwable exception, String requestId, String operation) {
    return String.format(
        "An error occurred while %s. Please contact support with request ID: %s",
        operation,
        requestId);
  }
}

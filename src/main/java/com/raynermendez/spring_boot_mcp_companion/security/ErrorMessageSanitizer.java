package com.raynermendez.spring_boot_mcp_companion.security;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Sanitizes error messages to prevent information disclosure.
 *
 * <p>This component ensures that:
 * <ul>
 *   <li>Exception stack traces are never exposed to clients
 *   <li>Internal implementation details are hidden
 *   <li>Full error details are logged internally for debugging
 *   <li>Clients receive generic error messages with request IDs for support
 * </ul>
 *
 * <p>Security best practice: Always log full exceptions internally, return generic messages
 * to clients.
 */
@Component
public class ErrorMessageSanitizer {

  private static final Logger logger = LoggerFactory.getLogger(ErrorMessageSanitizer.class);

  /**
   * Sanitizes an exception for client exposure.
   *
   * <p>Logs the full exception internally and returns a generic error message for the client.
   *
   * @param exception the exception to sanitize
   * @param requestId the request ID for correlation
   * @param operation the operation that failed (e.g., "list tools")
   * @return a generic error message safe for client exposure
   */
  public String sanitize(Throwable exception, String requestId, String operation) {
    // Log full details internally for debugging
    logger.error(
        "Error occurred during operation '{}' (request ID: {})",
        operation,
        requestId,
        exception);

    // Return generic message to client
    return String.format(
        "An error occurred while %s. Please contact support with request ID: %s",
        operation,
        requestId);
  }

  /**
   * Sanitizes an exception without request ID.
   *
   * @param exception the exception to sanitize
   * @param operation the operation that failed
   * @return a generic error message safe for client exposure
   */
  public String sanitize(Throwable exception, String operation) {
    String requestId = UUID.randomUUID().toString();
    return sanitize(exception, requestId, operation);
  }

  /**
   * Extracts safe information from an exception message.
   *
   * <p>Only safe error types (validation errors, not found errors) are exposed. Database
   * errors, IO errors, and internal errors are never exposed.
   *
   * @param exception the exception to extract from
   * @return a safe error message or null if no safe message can be extracted
   */
  public String extractSafeMessage(Throwable exception) {
    String message = exception.getMessage();
    if (message == null) {
      return null;
    }

    // Only expose certain safe error types
    if (exception instanceof IllegalArgumentException) {
      // Validation errors are safe to expose
      return message;
    }

    if (exception.getClass().getSimpleName().contains("NotFound")) {
      // Not found errors are safe
      return message;
    }

    // All other exceptions: don't expose details
    return null;
  }

  /**
   * Checks if an exception is safe to expose to clients.
   *
   * <p>Only specific exception types are considered safe (validation, not found). Database,
   * network, and other infrastructure exceptions are never safe.
   *
   * @param exception the exception to check
   * @return true if the exception is safe to expose
   */
  public boolean isSafeToExpose(Throwable exception) {
    return exception instanceof IllegalArgumentException
        || exception.getClass().getSimpleName().contains("NotFound")
        || exception.getClass().getSimpleName().contains("Validation");
  }
}

package com.raynermendez.spring_boot_mcp_companion.transport;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Maps JSON-RPC error codes to appropriate HTTP status codes.
 *
 * <p>Follows the JSON-RPC 2.0 specification error code mapping:
 * <ul>
 *   <li>-32700: Parse error → 400 Bad Request
 *   <li>-32600: Invalid Request → 400 Bad Request
 *   <li>-32601: Method not found → 404 Not Found
 *   <li>-32602: Invalid params → 400 Bad Request
 *   <li>-32603: Internal error → 500 Internal Server Error
 *   <li>-32000 to -32099: Server error → 500 Internal Server Error
 *   <li>null/no error: Success → 200 OK or 202 Accepted
 * </ul>
 *
 * @author Rayner Mendez
 */
@Component
public class HttpStatusMapper {
    /**
     * Maps a JSON-RPC error code to an HTTP status.
     *
     * @param errorCode the JSON-RPC error code, or null for success
     * @return the corresponding HTTP status
     */
    public HttpStatus getHttpStatus(Integer errorCode) {
        if (errorCode == null) {
            // No error - success
            return HttpStatus.OK;
        }

        return switch (errorCode) {
            // Parse error
            case -32700 -> HttpStatus.BAD_REQUEST;

            // Invalid Request
            case -32600 -> HttpStatus.BAD_REQUEST;

            // Method not found
            case -32601 -> HttpStatus.NOT_FOUND;

            // Invalid params
            case -32602 -> HttpStatus.BAD_REQUEST;

            // Internal error
            case -32603 -> HttpStatus.INTERNAL_SERVER_ERROR;

            // Server errors (-32000 to -32099)
            default -> {
                if (errorCode >= -32099 && errorCode <= -32000) {
                    yield HttpStatus.INTERNAL_SERVER_ERROR;
                }
                // Unknown error code
                yield HttpStatus.INTERNAL_SERVER_ERROR;
            }
        };
    }

    /**
     * Gets HTTP status for a success or subscription response.
     *
     * @param isSubscription true if this is a subscription response
     * @return 202 Accepted for subscriptions, 200 OK otherwise
     */
    public HttpStatus getSuccessStatus(boolean isSubscription) {
        return isSubscription ? HttpStatus.ACCEPTED : HttpStatus.OK;
    }

    /**
     * Gets HTTP status for session expired error.
     *
     * @return 404 Not Found
     */
    public HttpStatus getSessionExpiredStatus() {
        return HttpStatus.NOT_FOUND;
    }

    /**
     * Gets HTTP status for security violations.
     *
     * @return 403 Forbidden
     */
    public HttpStatus getForbiddenStatus() {
        return HttpStatus.FORBIDDEN;
    }
}

package com.raynermendez.spring_boot_mcp_companion.transport;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * Tests for HttpStatusMapper JSON-RPC error code to HTTP status mapping.
 */
class HttpStatusCodeTest {

    private HttpStatusMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new HttpStatusMapper();
    }

    @Test
    void testSuccessStatus() {
        // Act
        HttpStatus status = mapper.getHttpStatus(null);

        // Assert
        assertEquals(HttpStatus.OK, status);
    }

    @Test
    void testParseError() {
        // Act
        HttpStatus status = mapper.getHttpStatus(-32700);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, status);
    }

    @Test
    void testInvalidRequest() {
        // Act
        HttpStatus status = mapper.getHttpStatus(-32600);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, status);
    }

    @Test
    void testMethodNotFound() {
        // Act
        HttpStatus status = mapper.getHttpStatus(-32601);

        // Assert - Method not found is a client error (400), not a resource error (404)
        assertEquals(HttpStatus.BAD_REQUEST, status);
    }

    @Test
    void testInvalidParams() {
        // Act
        HttpStatus status = mapper.getHttpStatus(-32602);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, status);
    }

    @Test
    void testInternalError() {
        // Act
        HttpStatus status = mapper.getHttpStatus(-32603);

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, status);
    }

    @Test
    void testServerError() {
        // Act - Any error code between -32000 and -32099
        HttpStatus status1 = mapper.getHttpStatus(-32000);
        HttpStatus status2 = mapper.getHttpStatus(-32050);
        HttpStatus status3 = mapper.getHttpStatus(-32099);

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, status1);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, status2);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, status3);
    }

    @Test
    void testUnknownErrorCode() {
        // Act - Any error code outside the spec ranges
        HttpStatus status = mapper.getHttpStatus(-99999);

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, status);
    }

    @Test
    void testSuccessStatusDefault() {
        // Act
        HttpStatus status = mapper.getSuccessStatus(false);

        // Assert
        assertEquals(HttpStatus.OK, status);
    }

    @Test
    void testSubscriptionStatus() {
        // Act - Subscription operations return 202 Accepted
        HttpStatus status = mapper.getSuccessStatus(true);

        // Assert
        assertEquals(HttpStatus.ACCEPTED, status);
    }

    @Test
    void testSessionExpiredStatus() {
        // Act
        HttpStatus status = mapper.getSessionExpiredStatus();

        // Assert
        assertEquals(HttpStatus.NOT_FOUND, status);
    }

    @Test
    void testForbiddenStatus() {
        // Act
        HttpStatus status = mapper.getForbiddenStatus();

        // Assert
        assertEquals(HttpStatus.FORBIDDEN, status);
    }

    @Test
    void testAllJsonRpcErrorCodes() {
        // Test all JSON-RPC 2.0 standard error codes
        assertEquals(HttpStatus.BAD_REQUEST, mapper.getHttpStatus(-32700)); // Parse error
        assertEquals(HttpStatus.BAD_REQUEST, mapper.getHttpStatus(-32600)); // Invalid Request
        assertEquals(HttpStatus.BAD_REQUEST, mapper.getHttpStatus(-32601)); // Method not found (client error, not 404)
        assertEquals(HttpStatus.BAD_REQUEST, mapper.getHttpStatus(-32602)); // Invalid params
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, mapper.getHttpStatus(-32603)); // Internal error
    }
}

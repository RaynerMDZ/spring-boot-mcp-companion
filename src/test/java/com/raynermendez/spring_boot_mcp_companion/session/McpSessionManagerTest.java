package com.raynermendez.spring_boot_mcp_companion.session;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for McpSessionManager session lifecycle and timeout management.
 */
class McpSessionManagerTest {

    private McpSessionManager sessionManager;

    @BeforeEach
    void setUp() {
        sessionManager = new McpSessionManager(1); // 1 minute timeout for testing
    }

    @Test
    void testCreateSession() {
        // Act
        McpSession session = sessionManager.createSession(
            "2025-11-25",
            Map.of("name", "test-client", "version", "1.0"),
            Map.of("tools", Map.of())
        );

        // Assert
        assertNotNull(session);
        assertNotNull(session.getSessionId());
        assertEquals("2025-11-25", session.getProtocolVersion());
        assertEquals("test-client", session.getClientInfo().get("name"));
    }

    @Test
    void testGetActiveSession() {
        // Arrange
        McpSession created = sessionManager.createSession(
            "2025-11-25",
            Map.of("name", "test-client"),
            Map.of()
        );

        // Act
        var retrieved = sessionManager.getSession(created.getSessionId());

        // Assert
        assertTrue(retrieved.isPresent());
        assertEquals(created.getSessionId(), retrieved.get().getSessionId());
    }

    @Test
    void testSessionNotFound() {
        // Act
        var session = sessionManager.getSession("nonexistent-session");

        // Assert
        assertTrue(session.isEmpty());
    }

    @Test
    void testNullSessionId() {
        // Act
        var session = sessionManager.getSession(null);

        // Assert
        assertTrue(session.isEmpty());
    }

    @Test
    void testEmptySessionId() {
        // Act
        var session = sessionManager.getSession("");

        // Assert
        assertTrue(session.isEmpty());
    }

    @Test
    void testIsSessionActive() {
        // Arrange
        McpSession session = sessionManager.createSession(
            "2025-11-25",
            Map.of("name", "test-client"),
            Map.of()
        );

        // Act & Assert
        assertTrue(sessionManager.isSessionActive(session.getSessionId()));
        assertFalse(sessionManager.isSessionActive("nonexistent"));
    }

    @Test
    void testCloseSession() {
        // Arrange
        McpSession session = sessionManager.createSession(
            "2025-11-25",
            Map.of("name", "test-client"),
            Map.of()
        );

        // Act
        sessionManager.closeSession(session.getSessionId());

        // Assert
        assertFalse(sessionManager.isSessionActive(session.getSessionId()));
    }

    @Test
    void testActiveSessionCount() {
        // Act
        int initialCount = sessionManager.getActiveSessionCount();
        McpSession session1 = sessionManager.createSession("2025-11-25", Map.of(), Map.of());
        int afterCreate1 = sessionManager.getActiveSessionCount();
        McpSession session2 = sessionManager.createSession("2025-11-25", Map.of(), Map.of());
        int afterCreate2 = sessionManager.getActiveSessionCount();

        // Assert
        assertEquals(initialCount + 1, afterCreate1);
        assertEquals(initialCount + 2, afterCreate2);
    }

    @Test
    void testLastAccessTimeUpdate() {
        // Arrange
        McpSession session = sessionManager.createSession(
            "2025-11-25",
            Map.of("name", "test-client"),
            Map.of()
        );
        Instant createdTime = session.getLastAccessAt();

        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Act
        var retrieved = sessionManager.getSession(session.getSessionId());

        // Assert
        assertTrue(retrieved.isPresent());
        assertTrue(retrieved.get().getLastAccessAt().isAfter(createdTime));
    }

    @Test
    void testSessionSubscriptions() {
        // Arrange
        McpSession session = sessionManager.createSession(
            "2025-11-25",
            Map.of("name", "test-client"),
            Map.of()
        );

        // Act
        session.subscribe("resource://test", "sub-123");
        boolean isSubscribed = session.isSubscribed("resource://test");
        session.unsubscribe("resource://test");
        boolean isUnsubscribed = session.isSubscribed("resource://test");

        // Assert
        assertTrue(isSubscribed);
        assertFalse(isUnsubscribed);
    }

    @Test
    void testCloseAllSessions() {
        // Arrange
        McpSession session1 = sessionManager.createSession("2025-11-25", Map.of(), Map.of());
        McpSession session2 = sessionManager.createSession("2025-11-25", Map.of(), Map.of());

        int countBefore = sessionManager.getActiveSessionCount();
        assertTrue(countBefore >= 2);

        // Act
        sessionManager.closeAllSessions();

        // Assert
        assertEquals(0, sessionManager.getActiveSessionCount());
    }

    @Test
    void testSessionImmutability() {
        // Arrange
        McpSession session = sessionManager.createSession(
            "2025-11-25",
            Map.of("name", "test-client", "version", "1.0"),
            Map.of("tools", Map.of())
        );

        // Act
        Map<String, Object> clientInfo = session.getClientInfo();
        clientInfo.put("name", "modified-client");

        // Assert - Original session should not be modified
        assertEquals("test-client", session.getClientInfo().get("name"));
    }
}

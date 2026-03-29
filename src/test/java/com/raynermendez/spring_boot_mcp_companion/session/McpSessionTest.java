package com.raynermendez.spring_boot_mcp_companion.session;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for McpSession state management.
 */
class McpSessionTest {

    private McpSession session;

    @BeforeEach
    void setUp() {
        session = new McpSession(
            "session-123",
            "2025-11-25",
            Map.of("name", "test-client", "version", "1.0"),
            Map.of("tools", Map.of())
        );
    }

    @Test
    void testSessionCreation() {
        // Assert
        assertEquals("session-123", session.getSessionId());
        assertEquals("2025-11-25", session.getProtocolVersion());
        assertNotNull(session.getCreatedAt());
        assertNotNull(session.getLastAccessAt());
    }

    @Test
    void testGetClientInfo() {
        // Act
        Map<String, Object> clientInfo = session.getClientInfo();

        // Assert
        assertEquals("test-client", clientInfo.get("name"));
        assertEquals("1.0", clientInfo.get("version"));
    }

    @Test
    void testGetServerCapabilities() {
        // Act
        Map<String, Object> capabilities = session.getServerCapabilities();

        // Assert
        assertNotNull(capabilities);
        assertTrue(capabilities.containsKey("tools"));
    }

    @Test
    void testUpdateLastAccess() {
        // Arrange
        var initialTime = session.getLastAccessAt();

        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Act
        session.updateLastAccess();

        // Assert
        assertTrue(session.getLastAccessAt().isAfter(initialTime));
    }

    @Test
    void testResourceSubscription() {
        // Act
        session.subscribe("resource://test", "sub-123");
        boolean isSubscribed = session.isSubscribed("resource://test");

        // Assert
        assertTrue(isSubscribed);
    }

    @Test
    void testResourceUnsubscription() {
        // Arrange
        session.subscribe("resource://test", "sub-123");

        // Act
        session.unsubscribe("resource://test");

        // Assert
        assertFalse(session.isSubscribed("resource://test"));
    }

    @Test
    void testGetSubscriptions() {
        // Arrange
        session.subscribe("resource://test1", "sub-1");
        session.subscribe("resource://test2", "sub-2");

        // Act
        Map<String, String> subscriptions = session.getSubscriptions();

        // Assert
        assertEquals(2, subscriptions.size());
        assertTrue(subscriptions.containsKey("resource://test1"));
        assertTrue(subscriptions.containsKey("resource://test2"));
    }

    @Test
    void testClearSubscriptions() {
        // Arrange
        session.subscribe("resource://test1", "sub-1");
        session.subscribe("resource://test2", "sub-2");

        // Act
        session.clearSubscriptions();

        // Assert
        assertFalse(session.isSubscribed("resource://test1"));
        assertFalse(session.isSubscribed("resource://test2"));
    }

    @Test
    void testSessionToString() {
        // Act
        String str = session.toString();

        // Assert
        assertTrue(str.contains("session-123"));
        assertTrue(str.contains("2025-11-25"));
    }

    @Test
    void testClientInfoImmutability() {
        // Arrange
        Map<String, Object> clientInfo = session.getClientInfo();

        // Act
        clientInfo.put("name", "modified");

        // Assert - Original should not be modified
        assertEquals("test-client", session.getClientInfo().get("name"));
    }

    @Test
    void testCapabilitiesImmutability() {
        // Arrange
        Map<String, Object> capabilities = session.getServerCapabilities();

        // Act
        capabilities.put("newKey", "newValue");

        // Assert - Original should not be modified
        assertFalse(session.getServerCapabilities().containsKey("newKey"));
    }
}

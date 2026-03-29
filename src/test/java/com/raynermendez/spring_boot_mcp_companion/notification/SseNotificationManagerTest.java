package com.raynermendez.spring_boot_mcp_companion.notification;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Tests for SseNotificationManager SSE connection and notification delivery.
 */
class SseNotificationManagerTest {

    private SseNotificationManager manager;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        manager = new SseNotificationManager(objectMapper);
    }

    @Test
    void testCreateEmitter() {
        // Act
        SseEmitter emitter = manager.createEmitter("client-1", 5 * 60 * 1000);

        // Assert
        assertNotNull(emitter);
        assertTrue(manager.isClientConnected("client-1"));
    }

    @Test
    void testClientConnected() {
        // Arrange
        SseEmitter emitter = manager.createEmitter("client-1", 5 * 60 * 1000);

        // Act
        boolean isConnected = manager.isClientConnected("client-1");
        boolean notConnected = manager.isClientConnected("client-2");

        // Assert
        assertTrue(isConnected);
        assertFalse(notConnected);
    }

    @Test
    void testRemoveEmitter() {
        // Arrange
        SseEmitter emitter = manager.createEmitter("client-1", 5 * 60 * 1000);
        assertTrue(manager.isClientConnected("client-1"));

        // Act
        manager.removeEmitter("client-1");

        // Assert
        assertFalse(manager.isClientConnected("client-1"));
    }

    @Test
    void testActiveConnectionCount() {
        // Act
        int initialCount = manager.getActiveConnectionCount();
        manager.createEmitter("client-1", 5 * 60 * 1000);
        int count1 = manager.getActiveConnectionCount();
        manager.createEmitter("client-2", 5 * 60 * 1000);
        int count2 = manager.getActiveConnectionCount();

        // Assert
        assertEquals(initialCount, 0);
        assertEquals(count1, 1);
        assertEquals(count2, 2);
    }

    @Test
    void testBroadcastToolsListChanged() {
        // Arrange
        manager.createEmitter("client-1", 5 * 60 * 1000);
        manager.createEmitter("client-2", 5 * 60 * 1000);

        // Act - This should not throw
        manager.broadcastToolsListChanged();

        // Assert - Just verify no exceptions
        assertEquals(2, manager.getActiveConnectionCount());
    }

    @Test
    void testNotifyToolsListChanged() {
        // Arrange
        manager.createEmitter("client-1", 5 * 60 * 1000);

        // Act - This should not throw
        manager.notifyToolsListChanged("client-1");

        // Assert
        assertTrue(manager.isClientConnected("client-1"));
    }

    @Test
    void testNotifyResourceUpdated() {
        // Arrange
        manager.createEmitter("client-1", 5 * 60 * 1000);

        // Act - This should not throw
        manager.notifyResourceUpdated("client-1", "resource://test");

        // Assert
        assertTrue(manager.isClientConnected("client-1"));
    }

    @Test
    void testBroadcastResourceUpdated() {
        // Arrange
        manager.createEmitter("client-1", 5 * 60 * 1000);
        manager.createEmitter("client-2", 5 * 60 * 1000);

        // Act - This should not throw
        manager.broadcastResourceUpdated("resource://test");

        // Assert
        assertEquals(2, manager.getActiveConnectionCount());
    }

    @Test
    void testBroadcastResourcesListChanged() {
        // Arrange
        manager.createEmitter("client-1", 5 * 60 * 1000);

        // Act
        manager.broadcastResourcesListChanged();

        // Assert
        assertTrue(manager.isClientConnected("client-1"));
    }

    @Test
    void testBroadcastPromptsListChanged() {
        // Arrange
        manager.createEmitter("client-1", 5 * 60 * 1000);

        // Act
        manager.broadcastPromptsListChanged();

        // Assert
        assertTrue(manager.isClientConnected("client-1"));
    }

    @Test
    void testNotifyNonexistentClient() {
        // Act - Should not throw for nonexistent client
        manager.notifyToolsListChanged("nonexistent-client");

        // Assert - No exception should be thrown
        assertFalse(manager.isClientConnected("nonexistent-client"));
    }

    @Test
    void testCloseAllConnections() {
        // Arrange
        manager.createEmitter("client-1", 5 * 60 * 1000);
        manager.createEmitter("client-2", 5 * 60 * 1000);
        assertEquals(2, manager.getActiveConnectionCount());

        // Act
        manager.closeAllConnections();

        // Assert
        assertEquals(0, manager.getActiveConnectionCount());
    }

    @Test
    void testSendNotification() {
        // Arrange
        manager.createEmitter("client-1", 5 * 60 * 1000);

        // Act
        manager.sendNotification("client-1", "tools/list_changed", Map.of());

        // Assert
        assertTrue(manager.isClientConnected("client-1"));
    }

    @Test
    void testSendNotificationWithParams() {
        // Arrange
        manager.createEmitter("client-1", 5 * 60 * 1000);

        // Act
        manager.sendNotification("client-1", "resources/updated", Map.of("uri", "resource://test"));

        // Assert
        assertTrue(manager.isClientConnected("client-1"));
    }

    @Test
    void testBroadcastNotification() {
        // Arrange
        manager.createEmitter("client-1", 5 * 60 * 1000);
        manager.createEmitter("client-2", 5 * 60 * 1000);

        // Act
        manager.broadcastNotification("test/notification", Map.of("key", "value"));

        // Assert
        assertEquals(2, manager.getActiveConnectionCount());
    }
}

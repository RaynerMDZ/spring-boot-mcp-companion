package com.raynermendez.spring_boot_mcp_companion.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages Server-Sent Events (SSE) connections for MCP notifications.
 *
 * <p>Enables real-time push notifications from server to clients using HTTP Server-Sent Events.
 * Each client can connect to the SSE endpoint and receive notifications when tools,
 * resources, or prompts change.
 *
 * <p>This implements the notification push pattern specified in MCP 2025-11-25.
 *
 * @author Rayner Mendez
 */
@Component
public class SseNotificationManager {

    private static final Logger logger = LoggerFactory.getLogger(SseNotificationManager.class);

    private final ObjectMapper objectMapper;
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseNotificationManager(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Creates a new SSE emitter for client connections.
     *
     * @param clientId unique identifier for the client
     * @param timeout timeout in milliseconds (default: 5 minutes)
     * @return new SSE emitter
     */
    public SseEmitter createEmitter(String clientId, long timeout) {
        SseEmitter emitter = new SseEmitter(timeout);

        // Set up callbacks for cleanup
        emitter.onCompletion(() -> {
            logger.debug("SSE connection completed for client: {}", clientId);
            emitters.remove(clientId);
        });

        emitter.onTimeout(() -> {
            logger.debug("SSE connection timed out for client: {}", clientId);
            emitters.remove(clientId);
        });

        emitter.onError(throwable -> {
            logger.warn("SSE connection error for client {}: {}", clientId, throwable.getMessage());
            emitters.remove(clientId);
        });

        emitters.put(clientId, emitter);
        logger.info("Created SSE emitter for client: {}", clientId);
        return emitter;
    }

    /**
     * Removes a client's SSE emitter.
     *
     * @param clientId the client identifier
     */
    public void removeEmitter(String clientId) {
        emitters.remove(clientId);
        logger.debug("Removed SSE emitter for client: {}", clientId);
    }

    /**
     * Sends a notification to a specific client via SSE.
     *
     * @param clientId the target client ID
     * @param method the notification method (e.g., "tools/list_changed")
     * @param params notification parameters
     */
    public void sendNotification(String clientId, String method, Map<String, Object> params) {
        SseEmitter emitter = emitters.get(clientId);
        if (emitter == null) {
            logger.debug("No active SSE connection for client: {}", clientId);
            return;
        }

        try {
            // Create JSON-RPC notification message
            Map<String, Object> notification = Map.of(
                "jsonrpc", "2.0",
                "method", method,
                "params", params != null ? params : Map.of()
            );

            String json = objectMapper.writeValueAsString(notification);
            emitter.send(SseEmitter.event()
                .id(String.valueOf(System.nanoTime()))
                .data(json)
                .build());

            logger.debug("Sent SSE notification to client {}: method={}", clientId, method);
        } catch (IOException e) {
            logger.warn("Failed to send SSE notification to client {}: {}", clientId, e.getMessage());
            emitters.remove(clientId);
        }
    }

    /**
     * Broadcasts a notification to all connected clients.
     *
     * @param method the notification method
     * @param params notification parameters
     */
    public void broadcastNotification(String method, Map<String, Object> params) {
        logger.debug("Broadcasting notification to {} clients: method={}", emitters.size(), method);

        for (String clientId : emitters.keySet()) {
            sendNotification(clientId, method, params);
        }
    }

    /**
     * Sends tools/list_changed notification to a client.
     *
     * @param clientId the target client
     */
    public void notifyToolsListChanged(String clientId) {
        sendNotification(clientId, "tools/list_changed", Map.of());
    }

    /**
     * Broadcasts tools/list_changed to all clients.
     */
    public void broadcastToolsListChanged() {
        broadcastNotification("tools/list_changed", Map.of());
    }

    /**
     * Sends resources/list_changed notification to a client.
     *
     * @param clientId the target client
     */
    public void notifyResourcesListChanged(String clientId) {
        sendNotification(clientId, "resources/list_changed", Map.of());
    }

    /**
     * Broadcasts resources/list_changed to all clients.
     */
    public void broadcastResourcesListChanged() {
        broadcastNotification("resources/list_changed", Map.of());
    }

    /**
     * Sends prompts/list_changed notification to a client.
     *
     * @param clientId the target client
     */
    public void notifyPromptsListChanged(String clientId) {
        sendNotification(clientId, "prompts/list_changed", Map.of());
    }

    /**
     * Broadcasts prompts/list_changed to all clients.
     */
    public void broadcastPromptsListChanged() {
        broadcastNotification("prompts/list_changed", Map.of());
    }

    /**
     * Sends resources/updated notification for a specific resource.
     *
     * @param clientId the target client
     * @param resourceUri the URI of the updated resource
     */
    public void notifyResourceUpdated(String clientId, String resourceUri) {
        sendNotification(clientId, "resources/updated", Map.of("uri", resourceUri));
    }

    /**
     * Broadcasts resources/updated for a specific resource to all clients.
     *
     * @param resourceUri the URI of the updated resource
     */
    public void broadcastResourceUpdated(String resourceUri) {
        broadcastNotification("resources/updated", Map.of("uri", resourceUri));
    }

    /**
     * Gets the number of active SSE connections.
     *
     * @return count of connected clients
     */
    public int getActiveConnectionCount() {
        return emitters.size();
    }

    /**
     * Checks if a client has an active SSE connection.
     *
     * @param clientId the client ID
     * @return true if connected, false otherwise
     */
    public boolean isClientConnected(String clientId) {
        return emitters.containsKey(clientId);
    }

    /**
     * Closes all SSE connections (for shutdown).
     */
    public void closeAllConnections() {
        logger.info("Closing all {} SSE connections", emitters.size());
        for (SseEmitter emitter : emitters.values()) {
            try {
                emitter.complete();
            } catch (Exception e) {
                logger.warn("Error closing SSE emitter: {}", e.getMessage());
            }
        }
        emitters.clear();
    }
}

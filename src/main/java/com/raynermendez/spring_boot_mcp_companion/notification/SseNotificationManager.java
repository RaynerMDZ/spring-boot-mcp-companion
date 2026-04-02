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
        sendNotification(clientId, "notifications/tools/list_changed", Map.of());
    }

    /**
     * Broadcasts notifications/tools/list_changed to all clients.
     */
    public void broadcastToolsListChanged() {
        broadcastNotification("notifications/tools/list_changed", Map.of());
    }

    /**
     * Sends notifications/resources/list_changed notification to a client.
     *
     * @param clientId the target client
     */
    public void notifyResourcesListChanged(String clientId) {
        sendNotification(clientId, "notifications/resources/list_changed", Map.of());
    }

    /**
     * Broadcasts notifications/resources/list_changed to all clients.
     */
    public void broadcastResourcesListChanged() {
        broadcastNotification("notifications/resources/list_changed", Map.of());
    }

    /**
     * Sends notifications/prompts/list_changed notification to a client.
     *
     * @param clientId the target client
     */
    public void notifyPromptsListChanged(String clientId) {
        sendNotification(clientId, "notifications/prompts/list_changed", Map.of());
    }

    /**
     * Broadcasts notifications/prompts/list_changed to all clients.
     */
    public void broadcastPromptsListChanged() {
        broadcastNotification("notifications/prompts/list_changed", Map.of());
    }

    /**
     * Sends notifications/resources/updated notification for a specific resource.
     *
     * @param clientId the target client
     * @param resourceUri the URI of the updated resource
     */
    public void notifyResourceUpdated(String clientId, String resourceUri) {
        sendNotification(clientId, "notifications/resources/updated", Map.of("uri", resourceUri));
    }

    /**
     * Broadcasts notifications/resources/updated for a specific resource to all clients.
     *
     * @param resourceUri the URI of the updated resource
     */
    public void broadcastResourceUpdated(String resourceUri) {
        broadcastNotification("notifications/resources/updated", Map.of("uri", resourceUri));
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
     * Sends a sampling/complete request to a specific client.
     *
     * <p>Requests the client (AI application) to perform language model sampling.
     *
     * @param clientId the target client
     * @param model the model to use
     * @param systemPrompt system prompt
     * @param messages message history
     */
    public void sendSamplingCompleteRequest(
        String clientId,
        String model,
        String systemPrompt,
        java.util.List<Map<String, Object>> messages
    ) {
        Map<String, Object> params = Map.of(
            "model", model,
            "systemPrompt", systemPrompt,
            "messages", messages != null ? messages : java.util.List.of()
        );
        // Note: sampling/createMessage is a JSON-RPC request (needs id + response handling),
        // not a notification. This sends it as a notification — a proper implementation
        // would require bidirectional request-response over SSE.
        sendNotification(clientId, "sampling/createMessage", params);
    }

    /**
     * Sends an elicitation/request to a specific client.
     *
     * <p>Requests the client (user) to provide additional input.
     *
     * @param clientId the target client
     * @param type type of elicitation
     * @param title user-facing title
     * @param description description of request
     */
    public void sendElicitationRequest(
        String clientId,
        String type,
        String title,
        String description
    ) {
        Map<String, Object> params = Map.of(
            "type", type,
            "title", title,
            "description", description
        );
        // Note: elicitation/create is a JSON-RPC request (needs id + response handling),
        // not a notification. This sends it as a notification — a proper implementation
        // would require bidirectional request-response over SSE.
        sendNotification(clientId, "elicitation/create", params);
    }

    /**
     * Sends a logging/message to a specific client.
     *
     * <p>Sends a log message to the client for monitoring and debugging.
     *
     * @param clientId the target client
     * @param level log level (debug, info, warn, error)
     * @param message the log message
     */
    public void sendLoggingMessage(
        String clientId,
        String level,
        String message
    ) {
        Map<String, Object> params = Map.of(
            "level", level,
            "message", message
        );
        sendNotification(clientId, "notifications/message", params);
    }

    /**
     * Broadcasts a logging/message to all connected clients.
     *
     * @param level log level
     * @param message the log message
     */
    public void broadcastLoggingMessage(String level, String message) {
        broadcastNotification("notifications/message", Map.of(
            "level", level,
            "message", message
        ));
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

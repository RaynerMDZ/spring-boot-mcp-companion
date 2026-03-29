package com.raynermendez.spring_boot_mcp_companion.transport;

import com.raynermendez.spring_boot_mcp_companion.notification.SseNotificationManager;
import java.io.IOException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * HTTP Streamable Transport implementation for MCP 2025-11-25.
 *
 * <p>Implements the Streamable HTTP transport as defined in the MCP specification:
 * <ul>
 *   <li>HTTP POST for client-to-server requests
 *   <li>Server-Sent Events (SSE) for server-to-client notifications
 *   <li>Standard HTTP authentication (Bearer tokens, API keys)
 * </ul>
 *
 * <p>This transport is suitable for remote servers and cloud deployments.
 *
 * @author Rayner Mendez
 */
@Component
public class StreamableHttpTransport implements McpTransport {
    private static final Logger logger = LoggerFactory.getLogger(StreamableHttpTransport.class);

    private final SseNotificationManager notificationManager;
    private volatile boolean running = false;

    public StreamableHttpTransport(SseNotificationManager notificationManager) {
        this.notificationManager = notificationManager;
    }

    @Override
    public void start() throws IOException {
        logger.info("Starting Streamable HTTP Transport");
        running = true;
        logger.info("Streamable HTTP Transport started on /mcp endpoint");
    }

    @Override
    public void stop() throws IOException {
        logger.info("Stopping Streamable HTTP Transport");
        notificationManager.closeAllConnections();
        running = false;
        logger.info("Streamable HTTP Transport stopped");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public String getTransportType() {
        return "http-streamable";
    }

    @Override
    public String getDescription() {
        return "MCP 2025-11-25 HTTP Streamable Transport (HTTP POST + SSE)";
    }

    @Override
    public void sendMessage(String sessionId, Map<String, Object> message) throws IOException {
        // Extract method and params from message
        String method = (String) message.get("method");
        Map<String, Object> params = (Map<String, Object>) message.getOrDefault("params", Map.of());

        if (method != null) {
            notificationManager.sendNotification(sessionId, method, params);
        }
    }

    @Override
    public void broadcastMessage(Map<String, Object> message) throws IOException {
        // Extract method and params from message
        String method = (String) message.get("method");
        Map<String, Object> params = (Map<String, Object>) message.getOrDefault("params", Map.of());

        if (method != null) {
            notificationManager.broadcastNotification(method, params);
        }
    }

    /**
     * Gets the number of active HTTP connections.
     */
    public int getActiveConnectionCount() {
        return notificationManager.getActiveConnectionCount();
    }
}

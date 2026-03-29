package com.raynermendez.spring_boot_mcp_companion.transport;

import java.io.IOException;
import java.util.Map;

/**
 * Abstract interface for MCP transport mechanisms.
 *
 * <p>MCP 2025-11-25 specification defines two transport mechanisms:
 * <ul>
 *   <li>Stdio Transport: For local process communication
 *   <li>Streamable HTTP Transport: For remote server communication
 * </ul>
 *
 * <p>This interface enables multiple transport implementations while maintaining
 * consistent protocol handling above the transport layer.
 *
 * @author Rayner Mendez
 */
public interface McpTransport {

    /**
     * Starts the transport mechanism.
     *
     * <p>Implementation-specific initialization:
     * <ul>
     *   <li>HTTP: Starts HTTP server on configured port
     *   <li>Stdio: Initializes input/output streams
     * </ul>
     *
     * @throws IOException if transport cannot be started
     */
    void start() throws IOException;

    /**
     * Stops the transport mechanism.
     *
     * <p>Implementation-specific cleanup:
     * <ul>
     *   <li>HTTP: Shuts down HTTP server
     *   <li>Stdio: Closes input/output streams
     * </ul>
     *
     * @throws IOException if transport cannot be stopped cleanly
     */
    void stop() throws IOException;

    /**
     * Checks if transport is currently running.
     *
     * @return true if transport is active
     */
    boolean isRunning();

    /**
     * Gets the transport type identifier.
     *
     * @return "stdio", "http", etc.
     */
    String getTransportType();

    /**
     * Gets human-readable description of this transport.
     *
     * @return transport description
     */
    String getDescription();

    /**
     * Sends a message through the transport to a specific session/connection.
     *
     * <p>Implementation-specific behavior:
     * <ul>
     *   <li>HTTP: Sends via SSE notification
     *   <li>Stdio: Writes to stdout
     * </ul>
     *
     * @param sessionId session/connection identifier
     * @param message JSON-RPC message to send
     * @throws IOException if message cannot be sent
     */
    void sendMessage(String sessionId, Map<String, Object> message) throws IOException;

    /**
     * Broadcasts a message to all connected sessions.
     *
     * @param message JSON-RPC message to broadcast
     * @throws IOException if message cannot be broadcast
     */
    void broadcastMessage(Map<String, Object> message) throws IOException;
}

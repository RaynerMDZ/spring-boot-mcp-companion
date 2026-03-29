package com.raynermendez.spring_boot_mcp_companion.session;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Manages MCP client sessions.
 *
 * <p>Handles session creation, validation, cleanup, and timeout detection.
 * Sessions are stored in memory with a configurable timeout duration.
 *
 * <p>Thread-safe implementation using ConcurrentHashMap for session storage.
 *
 * @author Rayner Mendez
 */
@Component
public class McpSessionManager {
    private static final Logger logger = LoggerFactory.getLogger(McpSessionManager.class);

    private final Map<String, McpSession> sessions = new ConcurrentHashMap<>();
    private final Duration sessionTimeout;

    /**
     * Creates a new session manager.
     *
     * @param sessionTimeoutMinutes session timeout in minutes (default: 5)
     */
    public McpSessionManager(
        @Value("${mcp.server.session-timeout-minutes:5}") int sessionTimeoutMinutes
    ) {
        this.sessionTimeout = Duration.ofMinutes(sessionTimeoutMinutes);
        logger.info("Session manager initialized with timeout: {} minutes", sessionTimeoutMinutes);
    }

    /**
     * Creates a new session.
     *
     * @param protocolVersion the negotiated protocol version
     * @param clientInfo client information
     * @param serverCapabilities server capabilities
     * @return the newly created session
     */
    public McpSession createSession(
        String protocolVersion,
        Map<String, Object> clientInfo,
        Map<String, Object> serverCapabilities
    ) {
        String sessionId = UUID.randomUUID().toString();
        McpSession session = new McpSession(sessionId, protocolVersion, clientInfo, serverCapabilities);
        sessions.put(sessionId, session);

        logger.info(
            "Created session: id={}, protocol={}, client={}",
            sessionId,
            protocolVersion,
            clientInfo.get("name")
        );

        return session;
    }

    /**
     * Gets an active session by ID.
     *
     * <p>Validates that the session exists and has not expired.
     * Updates the last access timestamp if found.
     *
     * @param sessionId the session identifier
     * @return the session if active, or empty if not found or expired
     */
    public Optional<McpSession> getSession(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return Optional.empty();
        }

        McpSession session = sessions.get(sessionId);
        if (session == null) {
            logger.debug("Session not found: {}", sessionId);
            return Optional.empty();
        }

        // Check for expiration
        if (isExpired(session)) {
            logger.info("Session expired: {}", sessionId);
            sessions.remove(sessionId);
            return Optional.empty();
        }

        // Update last access
        session.updateLastAccess();
        return Optional.of(session);
    }

    /**
     * Validates a session exists and is not expired.
     *
     * @param sessionId the session identifier
     * @return true if session is active, false otherwise
     */
    public boolean isSessionActive(String sessionId) {
        return getSession(sessionId).isPresent();
    }

    /**
     * Closes a session.
     *
     * @param sessionId the session identifier
     */
    public void closeSession(String sessionId) {
        if (sessions.remove(sessionId) != null) {
            logger.debug("Closed session: {}", sessionId);
        }
    }

    /**
     * Checks if a session has expired.
     *
     * @param session the session to check
     * @return true if expired, false otherwise
     */
    private boolean isExpired(McpSession session) {
        Duration inactivity = Duration.between(session.getLastAccessAt(), Instant.now());
        return inactivity.compareTo(sessionTimeout) >= 0;
    }

    /**
     * Performs cleanup of expired sessions.
     * Called periodically to reclaim memory from unused sessions.
     */
    public void cleanupExpiredSessions() {
        int initialSize = sessions.size();
        sessions.entrySet().removeIf(entry -> isExpired(entry.getValue()));
        int finalSize = sessions.size();

        if (initialSize != finalSize) {
            logger.info("Cleaned up {} expired sessions", initialSize - finalSize);
        }
    }

    /**
     * Gets the number of active sessions.
     *
     * @return count of sessions
     */
    public int getActiveSessionCount() {
        return sessions.size();
    }

    /**
     * Closes all sessions (for shutdown).
     */
    public void closeAllSessions() {
        logger.info("Closing all {} sessions", sessions.size());
        sessions.clear();
    }
}

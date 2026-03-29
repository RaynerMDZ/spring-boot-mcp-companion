package com.raynermendez.spring_boot_mcp_companion.session;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Represents an MCP client session.
 *
 * <p>Each session is identified by a unique ID and tracks:
 * <ul>
 *   <li>Session creation timestamp
 *   <li>Last access timestamp for timeout detection
 *   <li>Client information (name, version)
 *   <li>Negotiated protocol version
 *   <li>Server capabilities
 *   <li>Active resource subscriptions
 * </ul>
 *
 * @author Rayner Mendez
 */
public class McpSession {
    private final String sessionId;
    private final Instant createdAt;
    private Instant lastAccessAt;
    private final Map<String, Object> clientInfo;
    private final String protocolVersion;
    private final Map<String, Object> serverCapabilities;
    private final Map<String, String> subscriptions; // resourceUri -> subscriptionId
    private Map<String, Object> clientCapabilities; // Client capabilities from initialize request

    /**
     * Creates a new MCP session.
     *
     * @param sessionId unique session identifier
     * @param protocolVersion negotiated MCP protocol version
     * @param clientInfo client information map
     * @param serverCapabilities server capabilities
     */
    public McpSession(
        String sessionId,
        String protocolVersion,
        Map<String, Object> clientInfo,
        Map<String, Object> serverCapabilities
    ) {
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId cannot be null");
        this.protocolVersion = Objects.requireNonNull(protocolVersion, "protocolVersion cannot be null");
        this.clientInfo = clientInfo != null ? new HashMap<>(clientInfo) : new HashMap<>();
        this.serverCapabilities = serverCapabilities != null ? new HashMap<>(serverCapabilities) : new HashMap<>();
        this.subscriptions = new HashMap<>();
        this.clientCapabilities = new HashMap<>();  // Initialize empty, will be set via setClientCapabilities

        Instant now = Instant.now();
        this.createdAt = now;
        this.lastAccessAt = now;
    }

    /**
     * Gets the session ID.
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * Gets the creation timestamp.
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Gets the last access timestamp.
     */
    public Instant getLastAccessAt() {
        return lastAccessAt;
    }

    /**
     * Updates the last access timestamp to now.
     */
    public void updateLastAccess() {
        this.lastAccessAt = Instant.now();
    }

    /**
     * Gets the client information.
     */
    public Map<String, Object> getClientInfo() {
        return new HashMap<>(clientInfo);
    }

    /**
     * Gets the negotiated protocol version.
     */
    public String getProtocolVersion() {
        return protocolVersion;
    }

    /**
     * Gets the server capabilities negotiated for this session.
     */
    public Map<String, Object> getServerCapabilities() {
        return new HashMap<>(serverCapabilities);
    }

    /**
     * Sets the client capabilities from the initialize request.
     *
     * <p>MCP spec requires servers to acknowledge client capabilities.
     *
     * @param capabilities the client capabilities
     */
    public void setClientCapabilities(Map<String, Object> capabilities) {
        this.clientCapabilities = capabilities != null ? new HashMap<>(capabilities) : new HashMap<>();
    }

    /**
     * Gets the client capabilities from the initialize request.
     */
    public Map<String, Object> getClientCapabilities() {
        return new HashMap<>(clientCapabilities);
    }

    /**
     * Checks if client supports a specific capability.
     *
     * @param capability the capability name (e.g., "elicitation", "sampling")
     * @return true if client declares support for this capability
     */
    public boolean supportsClientCapability(String capability) {
        return clientCapabilities.containsKey(capability);
    }

    /**
     * Subscribes to a resource.
     *
     * @param resourceUri the resource URI
     * @param subscriptionId the subscription identifier
     */
    public void subscribe(String resourceUri, String subscriptionId) {
        subscriptions.put(resourceUri, subscriptionId);
    }

    /**
     * Unsubscribes from a resource.
     *
     * @param resourceUri the resource URI
     */
    public void unsubscribe(String resourceUri) {
        subscriptions.remove(resourceUri);
    }

    /**
     * Checks if subscribed to a resource.
     *
     * @param resourceUri the resource URI
     * @return true if subscribed, false otherwise
     */
    public boolean isSubscribed(String resourceUri) {
        return subscriptions.containsKey(resourceUri);
    }

    /**
     * Gets all active subscriptions.
     */
    public Map<String, String> getSubscriptions() {
        return new HashMap<>(subscriptions);
    }

    /**
     * Clears all subscriptions.
     */
    public void clearSubscriptions() {
        subscriptions.clear();
    }

    @Override
    public String toString() {
        return "McpSession{" +
            "sessionId='" + sessionId + '\'' +
            ", protocolVersion='" + protocolVersion + '\'' +
            ", createdAt=" + createdAt +
            ", lastAccessAt=" + lastAccessAt +
            '}';
    }
}

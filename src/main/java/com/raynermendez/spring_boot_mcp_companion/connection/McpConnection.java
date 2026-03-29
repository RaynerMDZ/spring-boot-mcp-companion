package com.raynermendez.spring_boot_mcp_companion.connection;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Represents a single MCP client connection with state management.
 *
 * This class maintains the connection lifecycle and ensures thread-safe
 * state transitions according to the MCP specification.
 *
 * @author Rayner Mendez
 */
public class McpConnection {

	private final String connectionId;
	private final AtomicReference<ConnectionState> state;
	private final Instant createdAt;
	private volatile Instant lastActivityAt;
	private volatile String sessionId;
	private volatile Map<String, Object> clientInfo;

	/**
	 * Creates a new MCP connection.
	 */
	public McpConnection() {
		this.connectionId = UUID.randomUUID().toString();
		this.state = new AtomicReference<>(ConnectionState.INIT);
		this.createdAt = Instant.now();
		this.lastActivityAt = this.createdAt;
	}

	/**
	 * Gets the unique connection ID.
	 *
	 * @return the connection ID
	 */
	public String getConnectionId() {
		return connectionId;
	}

	/**
	 * Gets the current connection state.
	 *
	 * @return the current state
	 */
	public ConnectionState getState() {
		return state.get();
	}

	/**
	 * Transitions the connection to INITIALIZING state.
	 * Only valid from INIT state.
	 *
	 * @param clientInfo the client information from initialize request
	 * @return true if transition succeeded, false if invalid transition
	 */
	public synchronized boolean transitionToInitializing(Map<String, Object> clientInfo) {
		ConnectionState current = state.get();
		if (!ConnectionState.isValidTransition(current, ConnectionState.INITIALIZING)) {
			return false;
		}
		this.clientInfo = clientInfo;
		state.set(ConnectionState.INITIALIZING);
		updateLastActivity();
		return true;
	}

	/**
	 * Transitions the connection to READY state.
	 * Only valid from INITIALIZING state.
	 *
	 * @return true if transition succeeded, false if invalid transition
	 */
	public synchronized boolean transitionToReady() {
		ConnectionState current = state.get();
		if (!ConnectionState.isValidTransition(current, ConnectionState.READY)) {
			return false;
		}
		state.set(ConnectionState.READY);
		this.sessionId = UUID.randomUUID().toString();
		updateLastActivity();
		return true;
	}

	/**
	 * Closes the connection.
	 * Can be called from any state.
	 */
	public void close() {
		state.set(ConnectionState.CLOSED);
		updateLastActivity();
	}

	/**
	 * Gets when this connection was created.
	 *
	 * @return creation timestamp
	 */
	public Instant getCreatedAt() {
		return createdAt;
	}

	/**
	 * Gets when this connection last had activity.
	 *
	 * @return last activity timestamp
	 */
	public Instant getLastActivityAt() {
		return lastActivityAt;
	}

	/**
	 * Gets the session ID (only available after READY state).
	 *
	 * @return the session ID, or null if not yet in READY state
	 */
	public String getSessionId() {
		return sessionId;
	}

	/**
	 * Gets the client information from the initialize request.
	 *
	 * @return the client info map, or null if not yet initialized
	 */
	public Map<String, Object> getClientInfo() {
		return clientInfo;
	}

	/**
	 * Updates the last activity timestamp to now.
	 * Called whenever the connection processes a message.
	 */
	public void updateLastActivity() {
		this.lastActivityAt = Instant.now();
	}

	/**
	 * Checks if the connection is in a state that allows MCP operations.
	 *
	 * @return true if state is READY, false otherwise
	 */
	public boolean isReady() {
		return state.get() == ConnectionState.READY;
	}

	/**
	 * Checks if the connection is closed.
	 *
	 * @return true if state is CLOSED, false otherwise
	 */
	public boolean isClosed() {
		return state.get() == ConnectionState.CLOSED;
	}

	@Override
	public String toString() {
		return "McpConnection{" +
				"connectionId='" + connectionId + '\'' +
				", state=" + state.get() +
				", createdAt=" + createdAt +
				", lastActivityAt=" + lastActivityAt +
				'}';
	}
}

package com.raynermendez.spring_boot_mcp_companion.connection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages all active MCP client connections.
 *
 * This component maintains a registry of active connections, provides lifecycle
 * management, and ensures thread-safe access to connection state.
 *
 * @author Rayner Mendez
 */
@Component
public class McpConnectionManager {

	private static final Logger logger = LoggerFactory.getLogger(McpConnectionManager.class);

	private final ConcurrentHashMap<String, McpConnection> connections = new ConcurrentHashMap<>();

	/**
	 * Creates a new MCP connection and registers it.
	 *
	 * @return the newly created connection
	 */
	public McpConnection createConnection() {
		McpConnection connection = new McpConnection();
		connections.put(connection.getConnectionId(), connection);
		logger.debug("Created connection: {}", connection.getConnectionId());
		return connection;
	}

	/**
	 * Retrieves an existing connection by ID.
	 *
	 * @param connectionId the connection ID
	 * @return Optional containing the connection if found
	 */
	public Optional<McpConnection> getConnection(String connectionId) {
		return Optional.ofNullable(connections.get(connectionId));
	}

	/**
	 * Removes and closes a connection.
	 *
	 * @param connectionId the connection ID to remove
	 * @return true if connection was found and removed, false otherwise
	 */
	public boolean removeConnection(String connectionId) {
		McpConnection connection = connections.remove(connectionId);
		if (connection != null) {
			connection.close();
			logger.debug("Removed connection: {}", connectionId);
			return true;
		}
		return false;
	}

	/**
	 * Gets all currently active connections.
	 *
	 * @return collection of all active connections
	 */
	public Collection<McpConnection> getActiveConnections() {
		return connections.values();
	}

	/**
	 * Gets the number of active connections.
	 *
	 * @return count of active connections
	 */
	public int getConnectionCount() {
		return connections.size();
	}

	/**
	 * Closes all connections (useful for shutdown).
	 */
	public void closeAllConnections() {
		logger.info("Closing all {} connections", connections.size());
		connections.values().forEach(McpConnection::close);
		connections.clear();
	}

	/**
	 * Checks if a connection exists.
	 *
	 * @param connectionId the connection ID
	 * @return true if connection exists, false otherwise
	 */
	public boolean hasConnection(String connectionId) {
		return connections.containsKey(connectionId);
	}
}

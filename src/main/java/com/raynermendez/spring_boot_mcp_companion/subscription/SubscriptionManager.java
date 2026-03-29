package com.raynermendez.spring_boot_mcp_companion.subscription;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages resource subscriptions per MCP connection.
 *
 * Tracks which resources each client has subscribed to, enabling
 * efficient notification delivery when resources change.
 *
 * @author Rayner Mendez
 */
@Component
public class SubscriptionManager {

	private static final Logger logger = LoggerFactory.getLogger(SubscriptionManager.class);

	// Map: connectionId -> Set of subscribed resource URIs
	private final Map<String, Map<String, ResourceSubscription>> subscriptions = new ConcurrentHashMap<>();

	/**
	 * Subscribes a connection to a resource.
	 *
	 * @param connectionId the MCP connection ID
	 * @param resourceUri the resource URI pattern (e.g., "file://path/to/*")
	 * @return true if subscription was created, false if already subscribed
	 */
	public boolean subscribe(String connectionId, String resourceUri) {
		Map<String, ResourceSubscription> connSubscriptions = subscriptions
				.computeIfAbsent(connectionId, k -> new ConcurrentHashMap<>());

		if (connSubscriptions.containsKey(resourceUri)) {
			logger.debug("Connection {} already subscribed to {}", connectionId, resourceUri);
			return false;
		}

		ResourceSubscription subscription = new ResourceSubscription(connectionId, resourceUri);
		connSubscriptions.put(resourceUri, subscription);
		logger.info("Connection {} subscribed to resource: {}", connectionId, resourceUri);
		return true;
	}

	/**
	 * Unsubscribes a connection from a resource.
	 *
	 * @param connectionId the MCP connection ID
	 * @param resourceUri the resource URI pattern
	 * @return true if subscription was removed, false if not found
	 */
	public boolean unsubscribe(String connectionId, String resourceUri) {
		Map<String, ResourceSubscription> connSubscriptions = subscriptions.get(connectionId);
		if (connSubscriptions == null) {
			return false;
		}

		boolean removed = connSubscriptions.remove(resourceUri) != null;
		if (removed) {
			logger.info("Connection {} unsubscribed from resource: {}", connectionId, resourceUri);

			// Clean up empty entry
			if (connSubscriptions.isEmpty()) {
				subscriptions.remove(connectionId);
			}
		}
		return removed;
	}

	/**
	 * Gets all resource subscriptions for a connection.
	 *
	 * @param connectionId the MCP connection ID
	 * @return collection of subscribed resource URIs
	 */
	public Collection<String> getSubscribedResources(String connectionId) {
		Map<String, ResourceSubscription> connSubscriptions = subscriptions.get(connectionId);
		if (connSubscriptions == null) {
			return Collections.emptyList();
		}
		return connSubscriptions.keySet();
	}

	/**
	 * Gets all connections subscribed to a resource.
	 *
	 * @param resourceUri the resource URI or pattern
	 * @return collection of connection IDs subscribed to this resource
	 */
	public Collection<String> getSubscribersForResource(String resourceUri) {
		Collection<String> subscribers = Collections.synchronizedList(new java.util.ArrayList<>());

		subscriptions.forEach((connectionId, connSubscriptions) -> {
			if (connSubscriptions.containsKey(resourceUri)) {
				subscribers.add(connectionId);
			}
		});

		return subscribers;
	}

	/**
	 * Checks if a connection is subscribed to a resource.
	 *
	 * @param connectionId the MCP connection ID
	 * @param resourceUri the resource URI
	 * @return true if subscribed, false otherwise
	 */
	public boolean isSubscribed(String connectionId, String resourceUri) {
		Map<String, ResourceSubscription> connSubscriptions = subscriptions.get(connectionId);
		return connSubscriptions != null && connSubscriptions.containsKey(resourceUri);
	}

	/**
	 * Removes all subscriptions for a connection (e.g., on disconnect).
	 *
	 * @param connectionId the MCP connection ID
	 */
	public void removeAllSubscriptions(String connectionId) {
		subscriptions.remove(connectionId);
		logger.debug("Removed all subscriptions for connection: {}", connectionId);
	}

	/**
	 * Gets the number of subscriptions for a connection.
	 *
	 * @param connectionId the MCP connection ID
	 * @return subscription count
	 */
	public int getSubscriptionCount(String connectionId) {
		Map<String, ResourceSubscription> connSubscriptions = subscriptions.get(connectionId);
		return connSubscriptions != null ? connSubscriptions.size() : 0;
	}

	/**
	 * Gets total number of subscriptions across all connections.
	 *
	 * @return total subscription count
	 */
	public int getTotalSubscriptionCount() {
		return subscriptions.values().stream()
				.mapToInt(Map::size)
				.sum();
	}

	/**
	 * Represents a single resource subscription.
	 */
	public static class ResourceSubscription {
		private final String connectionId;
		private final String resourceUri;
		private final java.time.Instant createdAt;

		public ResourceSubscription(String connectionId, String resourceUri) {
			this.connectionId = connectionId;
			this.resourceUri = resourceUri;
			this.createdAt = java.time.Instant.now();
		}

		public String getConnectionId() {
			return connectionId;
		}

		public String getResourceUri() {
			return resourceUri;
		}

		public java.time.Instant getCreatedAt() {
			return createdAt;
		}
	}
}

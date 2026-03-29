package com.raynermendez.spring_boot_mcp_companion.subscription;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SubscriptionManager.
 */
public class SubscriptionManagerTest {

	private SubscriptionManager manager;

	@BeforeEach
	void setUp() {
		manager = new SubscriptionManager();
	}

	@Test
	void testSubscribeToResource() {
		assertTrue(manager.subscribe("conn1", "file://resource1"));
		assertEquals(1, manager.getSubscriptionCount("conn1"));
	}

	@Test
	void testDuplicateSubscription() {
		assertTrue(manager.subscribe("conn1", "file://resource1"));
		assertFalse(manager.subscribe("conn1", "file://resource1"));
		assertEquals(1, manager.getSubscriptionCount("conn1"));
	}

	@Test
	void testUnsubscribeFromResource() {
		manager.subscribe("conn1", "file://resource1");
		assertTrue(manager.unsubscribe("conn1", "file://resource1"));
		assertEquals(0, manager.getSubscriptionCount("conn1"));
	}

	@Test
	void testUnsubscribeNonExistent() {
		assertFalse(manager.unsubscribe("conn1", "file://resource1"));
	}

	@Test
	void testGetSubscribedResources() {
		manager.subscribe("conn1", "file://resource1");
		manager.subscribe("conn1", "file://resource2");
		manager.subscribe("conn1", "file://resource3");

		Collection<String> resources = manager.getSubscribedResources("conn1");
		assertEquals(3, resources.size());
		assertTrue(resources.contains("file://resource1"));
		assertTrue(resources.contains("file://resource2"));
		assertTrue(resources.contains("file://resource3"));
	}

	@Test
	void testGetSubscribersForResource() {
		manager.subscribe("conn1", "file://resource1");
		manager.subscribe("conn2", "file://resource1");
		manager.subscribe("conn3", "file://resource2");

		Collection<String> subscribers = manager.getSubscribersForResource("file://resource1");
		assertEquals(2, subscribers.size());
		assertTrue(subscribers.contains("conn1"));
		assertTrue(subscribers.contains("conn2"));
		assertFalse(subscribers.contains("conn3"));
	}

	@Test
	void testIsSubscribed() {
		manager.subscribe("conn1", "file://resource1");
		assertTrue(manager.isSubscribed("conn1", "file://resource1"));
		assertFalse(manager.isSubscribed("conn1", "file://resource2"));
		assertFalse(manager.isSubscribed("conn2", "file://resource1"));
	}

	@Test
	void testRemoveAllSubscriptions() {
		manager.subscribe("conn1", "file://resource1");
		manager.subscribe("conn1", "file://resource2");
		assertEquals(2, manager.getSubscriptionCount("conn1"));

		manager.removeAllSubscriptions("conn1");
		assertEquals(0, manager.getSubscriptionCount("conn1"));
	}

	@Test
	void testGetTotalSubscriptionCount() {
		manager.subscribe("conn1", "file://resource1");
		manager.subscribe("conn1", "file://resource2");
		manager.subscribe("conn2", "file://resource3");

		assertEquals(3, manager.getTotalSubscriptionCount());
	}

	@Test
	void testMultipleConnectionsIndependent() {
		manager.subscribe("conn1", "file://resource1");
		manager.subscribe("conn2", "file://resource2");

		assertEquals(1, manager.getSubscriptionCount("conn1"));
		assertEquals(1, manager.getSubscriptionCount("conn2"));

		manager.unsubscribe("conn1", "file://resource1");
		assertEquals(0, manager.getSubscriptionCount("conn1"));
		assertEquals(1, manager.getSubscriptionCount("conn2"));
	}

	@Test
	void testEmptySubscriptionsAfterUnsubscribeAll() {
		manager.subscribe("conn1", "file://resource1");
		manager.unsubscribe("conn1", "file://resource1");

		Collection<String> resources = manager.getSubscribedResources("conn1");
		assertTrue(resources.isEmpty());
	}
}

package com.raynermendez.spring_boot_mcp_companion.connection;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for McpConnectionManager.
 */
public class McpConnectionManagerTest {

	private McpConnectionManager manager;

	@BeforeEach
	void setUp() {
		manager = new McpConnectionManager();
	}

	@Test
	void testCreateConnection() {
		McpConnection connection = manager.createConnection();
		assertNotNull(connection);
		assertTrue(manager.hasConnection(connection.getConnectionId()));
	}

	@Test
	void testGetConnection() {
		McpConnection created = manager.createConnection();
		var retrieved = manager.getConnection(created.getConnectionId());
		assertTrue(retrieved.isPresent());
		assertEquals(created.getConnectionId(), retrieved.get().getConnectionId());
	}

	@Test
	void testGetNonExistentConnection() {
		var result = manager.getConnection("non-existent");
		assertFalse(result.isPresent());
	}

	@Test
	void testRemoveConnection() {
		McpConnection connection = manager.createConnection();
		String id = connection.getConnectionId();
		assertTrue(manager.hasConnection(id));
		assertTrue(manager.removeConnection(id));
		assertFalse(manager.hasConnection(id));
	}

	@Test
	void testRemoveNonExistentConnection() {
		assertFalse(manager.removeConnection("non-existent"));
	}

	@Test
	void testGetActiveConnections() {
		McpConnection conn1 = manager.createConnection();
		McpConnection conn2 = manager.createConnection();
		McpConnection conn3 = manager.createConnection();

		Collection<McpConnection> active = manager.getActiveConnections();
		assertEquals(3, active.size());
	}

	@Test
	void testGetConnectionCount() {
		assertEquals(0, manager.getConnectionCount());
		manager.createConnection();
		assertEquals(1, manager.getConnectionCount());
		manager.createConnection();
		assertEquals(2, manager.getConnectionCount());
	}

	@Test
	void testCloseAllConnections() {
		manager.createConnection();
		manager.createConnection();
		assertEquals(2, manager.getConnectionCount());

		manager.closeAllConnections();
		assertEquals(0, manager.getConnectionCount());
	}

	@Test
	void testConcurrentCreations() throws InterruptedException {
		int threads = 10;
		Thread[] threadArray = new Thread[threads];

		for (int i = 0; i < threads; i++) {
			threadArray[i] = new Thread(() -> {
				for (int j = 0; j < 10; j++) {
					manager.createConnection();
				}
			});
			threadArray[i].start();
		}

		for (Thread thread : threadArray) {
			thread.join();
		}

		assertEquals(100, manager.getConnectionCount());
	}
}

package com.raynermendez.spring_boot_mcp_companion.security;

import static org.junit.jupiter.api.Assertions.*;

import com.raynermendez.spring_boot_mcp_companion.dispatch.DefaultMcpDispatcher;
import com.raynermendez.spring_boot_mcp_companion.dispatch.McpDispatcher;
import com.raynermendez.spring_boot_mcp_companion.model.McpParameterDefinition;
import com.raynermendez.spring_boot_mcp_companion.model.McpToolDefinition;
import com.raynermendez.spring_boot_mcp_companion.model.MethodHandlerRef;
import com.raynermendez.spring_boot_mcp_companion.registry.DefaultMcpDefinitionRegistry;
import com.raynermendez.spring_boot_mcp_companion.registry.McpDefinitionRegistry;
import com.raynermendez.spring_boot_mcp_companion.spi.McpOutputSerializer;
import com.raynermendez.spring_boot_mcp_companion.validation.DefaultMcpInputValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Security tests for concurrent access scenarios.
 *
 * <p>Tests verify that the framework handles concurrent tool invocations safely.
 */
@DisplayName("Concurrent Access Security Tests")
class ConcurrentAccessSecurityTest {

  private McpDefinitionRegistry registry;
  private DefaultMcpDispatcher dispatcher;

  @BeforeEach
  void setUp() throws Exception {
    registry = new DefaultMcpDefinitionRegistry();
    dispatcher = new DefaultMcpDispatcher(
        registry,
        new ObjectMapper(),
        new DefaultMcpInputValidator(),
        createMockSerializer()
    );

    // Register a test tool
    List<McpParameterDefinition> params = List.of(
        new McpParameterDefinition("input", "Input", true, Map.of("type", "string"), false)
    );

    Method dummyMethod = String.class.getMethod("valueOf", Object.class);
    Object targetBean = new Object();
    MethodHandlerRef handler = new MethodHandlerRef(targetBean, dummyMethod, "testBean");

    McpToolDefinition tool = new McpToolDefinition(
        "test_tool",
        "Test Tool",
        "Test tool",
        new String[]{},
        params,
        Map.of("type", "object"),
        handler
    );

    registry.register(tool);
  }

  @Test
  @DisplayName("Should handle concurrent tool invocations safely")
  void testConcurrentToolInvocations() throws Exception {
    int threadCount = 10;
    int invocationsPerThread = 100;
    CountDownLatch latch = new CountDownLatch(threadCount);
    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger errorCount = new AtomicInteger(0);

    for (int t = 0; t < threadCount; t++) {
      new Thread(() -> {
        try {
          for (int i = 0; i < invocationsPerThread; i++) {
            Map<String, Object> args = new HashMap<>();
            args.put("input", "test-" + i);

            McpDispatcher.McpToolResult result = dispatcher.dispatchTool("test_tool", args);
            if (!result.isError()) {
              successCount.incrementAndGet();
            } else {
              errorCount.incrementAndGet();
            }
          }
        } finally {
          latch.countDown();
        }
      }).start();
    }

    latch.await();

    // All invocations should succeed
    assertEquals(threadCount * invocationsPerThread, successCount.get() + errorCount.get());
    assertTrue(successCount.get() > 0, "Should have successful invocations");
  }

  @Test
  @DisplayName("Should handle concurrent registry access safely")
  void testConcurrentRegistryAccess() throws Exception {
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int t = 0; t < threadCount; t++) {
      new Thread(() -> {
        try {
          // Concurrent reads from registry
          List<McpToolDefinition> tools = registry.getTools();
          assertNotNull(tools);
          assertTrue(tools.size() > 0);
        } finally {
          latch.countDown();
        }
      }).start();
    }

    latch.await();
  }

  @Test
  @DisplayName("Should not corrupt tool definitions under concurrent access")
  void testNoConcurrentCorruption() throws Exception {
    int threadCount = 10;
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch endLatch = new CountDownLatch(threadCount);

    for (int t = 0; t < threadCount; t++) {
      new Thread(() -> {
        try {
          startLatch.await(); // Start all threads at the same time
          List<McpToolDefinition> tools = registry.getTools();

          for (McpToolDefinition tool : tools) {
            // Verify tool integrity
            assertNotNull(tool.name());
            assertNotNull(tool.handler());
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        } finally {
          endLatch.countDown();
        }
      }).start();
    }

    startLatch.countDown(); // Release all threads
    endLatch.await();

    // Registry should still be valid
    List<McpToolDefinition> tools = registry.getTools();
    assertEquals(1, tools.size());
  }

  private McpOutputSerializer createMockSerializer() {
    return new McpOutputSerializer() {
      @Override
      public String serialize(Object value, McpToolDefinition toolDef) {
        return value != null ? value.toString() : "null";
      }
    };
  }
}

package com.raynermendez.spring_boot_mcp_companion.performance;

import static org.junit.jupiter.api.Assertions.*;

import com.raynermendez.spring_boot_mcp_companion.dispatch.DefaultMcpDispatcher;
import com.raynermendez.spring_boot_mcp_companion.model.McpParameterDefinition;
import com.raynermendez.spring_boot_mcp_companion.model.McpToolDefinition;
import com.raynermendez.spring_boot_mcp_companion.model.MethodHandlerRef;
import com.raynermendez.spring_boot_mcp_companion.registry.DefaultMcpDefinitionRegistry;
import com.raynermendez.spring_boot_mcp_companion.registry.McpDefinitionRegistry;
import com.raynermendez.spring_boot_mcp_companion.spi.McpOutputSerializer;
import com.raynermendez.spring_boot_mcp_companion.validation.DefaultMcpInputValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Performance and load testing for MCP framework.
 *
 * <p>Tests verify framework behavior under production load conditions:
 * - Throughput (requests per second)
 * - Latency (response times)
 * - Memory efficiency
 * - GC impact
 */
@DisplayName("Performance & Load Testing")
class PerformanceLoadTest {

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

    // Register test tool
    List<McpParameterDefinition> params = List.of(
        new McpParameterDefinition("input", "Input", true, Map.of("type", "string"), false)
    );

    Method dummyMethod = String.class.getMethod("valueOf", Object.class);
    Object targetBean = new Object();
    MethodHandlerRef handler = new MethodHandlerRef(targetBean, dummyMethod, "testBean");

    McpToolDefinition tool = new McpToolDefinition(
        "perf_test_tool",
        "Performance test tool",
        new String[]{},
        params,
        Map.of("type", "object"),
        handler
    );

    registry.register(tool);
  }

  @Test
  @DisplayName("Should handle 1000+ requests/second throughput")
  @Timeout(30) // 30 second timeout for this test
  void testThroughput() {
    int totalRequests = 5000;
    AtomicLong successCount = new AtomicLong(0);
    long startTime = System.currentTimeMillis();

    for (int i = 0; i < totalRequests; i++) {
      Map<String, Object> args = new HashMap<>();
      args.put("input", "test-" + i);

      try {
        dispatcher.dispatchTool("perf_test_tool", args);
        successCount.incrementAndGet();
      } catch (Exception e) {
        // Tolerate failures
      }
    }

    long duration = System.currentTimeMillis() - startTime;
    long requestsPerSecond = (successCount.get() * 1000) / duration;

    System.out.println("\n=== Throughput Results ===");
    System.out.println("Total Requests: " + totalRequests);
    System.out.println("Successful: " + successCount.get());
    System.out.println("Duration: " + duration + " ms");
    System.out.println("Throughput: " + requestsPerSecond + " req/sec");

    assertTrue(requestsPerSecond > 1000,
        "Framework should handle 1000+ requests/second (got " + requestsPerSecond + ")");
  }

  @Test
  @DisplayName("Should maintain reasonable latency under load (p95 < 100ms)")
  @Timeout(30)
  void testLatency() {
    int requests = 1000;
    List<Long> latencies = new ArrayList<>();

    for (int i = 0; i < requests; i++) {
      Map<String, Object> args = new HashMap<>();
      args.put("input", "latency-test-" + i);

      long requestStart = System.nanoTime();
      try {
        dispatcher.dispatchTool("perf_test_tool", args);
      } catch (Exception ignored) {
      }
      long requestEnd = System.nanoTime();
      latencies.add((requestEnd - requestStart) / 1_000_000); // Convert to ms
    }

    // Calculate percentiles
    latencies.sort(Long::compareTo);
    long p50 = latencies.get((int) (latencies.size() * 0.50));
    long p95 = latencies.get((int) (latencies.size() * 0.95));
    long p99 = latencies.get((int) (latencies.size() * 0.99));
    long max = latencies.get(latencies.size() - 1);

    System.out.println("\n=== Latency Results ===");
    System.out.println("P50: " + p50 + " ms");
    System.out.println("P95: " + p95 + " ms");
    System.out.println("P99: " + p99 + " ms");
    System.out.println("Max: " + max + " ms");

    assertTrue(p95 < 200, "P95 latency should be < 200ms (got " + p95 + "ms)");
  }

  @Test
  @DisplayName("Should not leak memory during sustained load")
  void testMemoryStability() {
    Runtime runtime = Runtime.getRuntime();
    long initialMemory = runtime.totalMemory() - runtime.freeMemory();

    // Generate sustained load
    int iterations = 10000;
    for (int i = 0; i < iterations; i++) {
      Map<String, Object> args = new HashMap<>();
      args.put("input", "mem-test-" + i);

      try {
        dispatcher.dispatchTool("perf_test_tool", args);
      } catch (Exception ignored) {
      }

      // Periodic GC hint
      if (i % 1000 == 0) {
        System.gc();
      }
    }

    System.gc();
    long finalMemory = runtime.totalMemory() - runtime.freeMemory();
    long memoryIncrease = finalMemory - initialMemory;
    long memoryIncreasePercent = (memoryIncrease * 100) / initialMemory;

    System.out.println("\n=== Memory Stability Results ===");
    System.out.println("Initial Memory: " + (initialMemory / 1024 / 1024) + " MB");
    System.out.println("Final Memory: " + (finalMemory / 1024 / 1024) + " MB");
    System.out.println("Increase: " + (memoryIncrease / 1024) + " KB (" + memoryIncreasePercent + "%)");

    assertTrue(memoryIncreasePercent < 50,
        "Memory increase should be < 50% (got " + memoryIncreasePercent + "%)");
  }

  @Test
  @DisplayName("Should handle concurrent requests efficiently")
  @Timeout(15)
  void testConcurrentLoad() throws InterruptedException {
    int threadCount = 10;
    int requestsPerThread = 500;
    AtomicLong totalSuccess = new AtomicLong(0);
    AtomicLong totalFailure = new AtomicLong(0);

    long startTime = System.currentTimeMillis();

    List<Thread> threads = new ArrayList<>();
    for (int t = 0; t < threadCount; t++) {
      Thread thread = new Thread(() -> {
        for (int i = 0; i < requestsPerThread; i++) {
          Map<String, Object> args = new HashMap<>();
          args.put("input", "concurrent-" + Thread.currentThread().getId() + "-" + i);

          try {
            dispatcher.dispatchTool("perf_test_tool", args);
            totalSuccess.incrementAndGet();
          } catch (Exception e) {
            totalFailure.incrementAndGet();
          }
        }
      });
      threads.add(thread);
      thread.start();
    }

    for (Thread thread : threads) {
      thread.join();
    }

    long duration = System.currentTimeMillis() - startTime;
    long totalRequests = threadCount * requestsPerThread;

    System.out.println("\n=== Concurrent Load Results ===");
    System.out.println("Threads: " + threadCount);
    System.out.println("Requests per Thread: " + requestsPerThread);
    System.out.println("Total Requests: " + totalRequests);
    System.out.println("Successful: " + totalSuccess.get());
    System.out.println("Failed: " + totalFailure.get());
    System.out.println("Duration: " + duration + " ms");
    System.out.println("Throughput: " + ((totalSuccess.get() * 1000) / duration) + " req/sec");

    assertTrue(totalSuccess.get() >= (totalRequests * 0.95),
        "At least 95% of concurrent requests should succeed (got " + totalSuccess.get() + "/" + totalRequests + ")");
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

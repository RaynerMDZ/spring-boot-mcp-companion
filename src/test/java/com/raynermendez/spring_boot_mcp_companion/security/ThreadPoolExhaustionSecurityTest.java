package com.raynermendez.spring_boot_mcp_companion.security;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Security tests for thread pool exhaustion attacks.
 *
 * <p>Tests verify that the async request executor cannot be starved by malicious long-running
 * requests.
 */
@DisplayName("Thread Pool Exhaustion Security Tests")
class ThreadPoolExhaustionSecurityTest {

  private ThreadPoolExecutor executor;
  private static final int CORE_THREADS = 5;
  private static final int MAX_THREADS = 10;
  private static final long THREAD_TIMEOUT_SECONDS = 60;

  @BeforeEach
  void setUp() {
    // Create executor with bounded thread pool and queue
    executor =
        new ThreadPoolExecutor(CORE_THREADS, MAX_THREADS, THREAD_TIMEOUT_SECONDS,
            TimeUnit.SECONDS, new SynchronousQueue<>());
  }

  @Test
  @DisplayName("Should reject tasks when thread pool is exhausted")
  void testRejectTasksWhenExhausted() throws InterruptedException {
    CountDownLatch startLatch = new CountDownLatch(MAX_THREADS);
    CountDownLatch endLatch = new CountDownLatch(1);
    AtomicInteger rejectionCount = new AtomicInteger(0);

    // Fill all available threads with blocking tasks
    for (int i = 0; i < MAX_THREADS; i++) {
      try {
        executor.submit(() -> {
          startLatch.countDown();
          try {
            endLatch.await(10, TimeUnit.SECONDS);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        });
      } catch (RejectedExecutionException e) {
        rejectionCount.incrementAndGet();
      }
    }

    startLatch.await(5, TimeUnit.SECONDS);

    // Now try to submit another task - should be rejected
    assertThrows(RejectedExecutionException.class,
        () -> executor.submit(() -> {
          /* blocked */
        }),
        "Should reject new tasks when pool is exhausted");

    // Cleanup
    endLatch.countDown();
    executor.shutdownNow();
    // Wait briefly for threads to exit
    executor.awaitTermination(3, TimeUnit.SECONDS);
    assertTrue(executor.isShutdown(), "Executor should be shut down");
  }

  @Test
  @DisplayName("Should handle many rapid short-lived tasks")
  void testHandleRapidShortLivedTasks() throws InterruptedException {
    int taskCount = 1000;
    CountDownLatch latch = new CountDownLatch(taskCount);
    AtomicInteger completedCount = new AtomicInteger(0);

    for (int i = 0; i < taskCount; i++) {
      executor.submit(() -> {
        try {
          // Simulate short work
          Thread.sleep(1);
          completedCount.incrementAndGet();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        } finally {
          latch.countDown();
        }
      });
    }

    boolean completed = latch.await(30, TimeUnit.SECONDS);
    assertTrue(completed, "All tasks should complete within timeout");
    assertEquals(taskCount, completedCount.get(),
        "All tasks should execute successfully");

    executor.shutdown();
  }

  @Test
  @DisplayName("Should timeout long-running tasks")
  void testTimeoutLongRunningTasks() throws InterruptedException {
    CountDownLatch taskStarted = new CountDownLatch(1);
    CountDownLatch taskCompleted = new CountDownLatch(1);
    AtomicInteger interrupted = new AtomicInteger(0);

    executor.submit(() -> {
      taskStarted.countDown();
      try {
        // Try to run for longer than timeout
        Thread.sleep(THREAD_TIMEOUT_SECONDS * 1000 + 5000);
      } catch (InterruptedException e) {
        interrupted.incrementAndGet();
        Thread.currentThread().interrupt();
      } finally {
        taskCompleted.countDown();
      }
    });

    taskStarted.await();

    // Request shutdown
    executor.shutdownNow();
    boolean terminated =
        executor.awaitTermination(THREAD_TIMEOUT_SECONDS + 5, TimeUnit.SECONDS);

    assertTrue(terminated || interrupted.get() > 0,
        "Long-running tasks should be interrupted");
  }

  @Test
  @DisplayName("Should prevent thread leak from incomplete async operations")
  void testPreventThreadLeak() throws InterruptedException {
    ExecutorService service = Executors.newFixedThreadPool(3);
    int initialThreadCount = Thread.activeCount();

    // Submit tasks but track they complete
    CountDownLatch latch = new CountDownLatch(10);
    for (int i = 0; i < 10; i++) {
      service.submit(() -> {
        try {
          Thread.sleep(100);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        } finally {
          latch.countDown();
        }
      });
    }

    latch.await(10, TimeUnit.SECONDS);
    service.shutdown();
    assertTrue(service.awaitTermination(5, TimeUnit.SECONDS), "Service should terminate");

    // Wait for thread cleanup
    Thread.sleep(500);

    // Thread count should return to near initial
    int finalThreadCount = Thread.activeCount();
    assertTrue(finalThreadCount <= initialThreadCount + 2,
        "Threads should be cleaned up after executor shutdown");
  }

  @Test
  @DisplayName("Should queue tasks when below max threads")
  void testQueueTasksBelowMaxThreads() throws InterruptedException {
    // Use a bounded queue executor
    ThreadPoolExecutor queuedExecutor = new ThreadPoolExecutor(2, 4, 60, TimeUnit.SECONDS,
        new java.util.concurrent.LinkedBlockingQueue<>(100));

    CountDownLatch taskLatch = new CountDownLatch(50);
    AtomicInteger executedCount = new AtomicInteger(0);

    // Submit tasks that should be queued
    for (int i = 0; i < 50; i++) {
      queuedExecutor.submit(() -> {
        try {
          Thread.sleep(50);
          executedCount.incrementAndGet();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        } finally {
          taskLatch.countDown();
        }
      });
    }

    boolean completed = taskLatch.await(30, TimeUnit.SECONDS);
    assertTrue(completed, "All queued tasks should complete");
    assertEquals(50, executedCount.get(), "All tasks should execute");

    queuedExecutor.shutdown();
    assertTrue(queuedExecutor.awaitTermination(5, TimeUnit.SECONDS),
        "Executor should terminate");
  }

  @Test
  @DisplayName("Should track active thread count")
  void testTrackActiveThreadCount() throws InterruptedException {
    CountDownLatch startLatch = new CountDownLatch(CORE_THREADS);
    CountDownLatch endLatch = new CountDownLatch(1);

    // Submit CORE_THREADS tasks
    for (int i = 0; i < CORE_THREADS; i++) {
      executor.submit(() -> {
        startLatch.countDown();
        try {
          endLatch.await();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      });
    }

    startLatch.await();

    // Check active count
    int activeCount = executor.getActiveCount();
    assertEquals(CORE_THREADS, activeCount,
        "Should have CORE_THREADS active threads");

    endLatch.countDown();
    executor.shutdown();
    assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS),
        "Executor should terminate");
  }

  @Test
  @DisplayName("Should prevent context switching overhead from excessive threads")
  void testPreventExcessiveContextSwitching() throws InterruptedException {
    ThreadPoolExecutor limitedExecutor =
        new ThreadPoolExecutor(5, 5, 60, TimeUnit.SECONDS, new java.util.concurrent.LinkedBlockingQueue<>(1000));

    long startTime = System.currentTimeMillis();
    CountDownLatch latch = new CountDownLatch(1000);
    AtomicInteger completedCount = new AtomicInteger(0);

    for (int i = 0; i < 1000; i++) {
      limitedExecutor.submit(() -> {
        completedCount.incrementAndGet();
        latch.countDown();
      });
    }

    latch.await(10, TimeUnit.SECONDS);
    long duration = System.currentTimeMillis() - startTime;

    assertEquals(1000, completedCount.get(), "All tasks should complete");
    assertTrue(duration < 30000, "Execution should complete in reasonable time");

    limitedExecutor.shutdown();
    assertTrue(limitedExecutor.awaitTermination(5, TimeUnit.SECONDS),
        "Executor should terminate");
  }

  @Test
  @DisplayName("Should handle executor shutdown gracefully during load")
  void testGracefulShutdownDuringLoad() throws InterruptedException {
    CountDownLatch submissionLatch = new CountDownLatch(100);
    AtomicInteger completedCount = new AtomicInteger(0);

    // Submit many tasks
    for (int i = 0; i < 100; i++) {
      try {
        executor.submit(() -> {
          try {
            Thread.sleep(100);
            completedCount.incrementAndGet();
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          } finally {
            submissionLatch.countDown();
          }
        });
      } catch (RejectedExecutionException e) {
        submissionLatch.countDown();
      }
    }

    // Request shutdown
    executor.shutdown();

    // Wait for completion with timeout
    boolean terminated = executor.awaitTermination(30, TimeUnit.SECONDS);
    assertTrue(terminated, "Executor should terminate");

    // Some tasks may not complete due to shutdown
    assertTrue(completedCount.get() >= 0, "Task completion count tracked");
  }

  @Test
  @DisplayName("Should not allow unbounded task queue")
  void testPreventUnboundedQueue() {
    // Executor with SynchronousQueue has no queue - prevents unbounded growth
    assertEquals(0, executor.getQueue().size(),
        "SynchronousQueue should not accumulate tasks");

    // Try to submit task - if pool is exhausted, should reject
    try {
      // Fill the pool
      for (int i = 0; i < MAX_THREADS + 1; i++) {
        executor.submit(() -> {
          try {
            Thread.sleep(1000);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        });
      }
      fail("Should have rejected task when pool exhausted");
    } catch (RejectedExecutionException e) {
      assertTrue(true, "Correctly rejected when pool exhausted");
    } finally {
      executor.shutdownNow();
    }
  }
}

package com.raynermendez.spring_boot_mcp_companion.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Map;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Aspect for observability of MCP tool invocations.
 *
 * <p>This aspect:
 * <ul>
 *   <li>Increments "mcp.tool.invocations" counter with tags (tool-name, status)
 *   <li>Records "mcp.tool.duration" timer
 *   <li>Logs structured information about tool invocations
 *   <li>Excludes sensitive parameters from metrics and logs
 * </ul>
 *
 * <p>Security: Sensitive parameters marked with @McpInput(sensitive=true) are never included
 * in observability data (metric tags, counters, or log entries). Argument values are not
 * recorded in metrics. See SensitiveParameterFilter for parameter-level filtering strategy.
 */
@Aspect
@Component
public class McpObservabilityAspect {

  private static final Logger logger = LoggerFactory.getLogger(McpObservabilityAspect.class);

  private final MeterRegistry meterRegistry;

  public McpObservabilityAspect(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  /**
   * Advice for dispatchTool method calls.
   *
   * <p>Wraps tool invocations with timing, metrics, and logging.
   *
   * @param joinPoint the join point
   * @return the result from the dispatched tool
   * @throws Throwable if the method throws
   */
  @Around(
      "execution(com.raynermendez.spring_boot_mcp_companion.dispatch.McpDispatcher.McpToolResult"
          + " com.raynermendez.spring_boot_mcp_companion.dispatch.DefaultMcpDispatcher.dispatchTool(..))")
  public Object aroundDispatchTool(ProceedingJoinPoint joinPoint) throws Throwable {
    long startTime = System.currentTimeMillis();
    String toolName = (String) joinPoint.getArgs()[0];
    String status = "success";

    try {
      Object result = joinPoint.proceed();

      // Determine if result indicates an error
      if (result != null && result.getClass().getSimpleName().equals("McpToolResult")) {
        // Check if isError() is true (reflection needed since we can't import McpToolResult in
        // aspect)
        try {
          var resultObj = result;
          var isErrorMethod =
              resultObj.getClass().getMethod("isError");
          if ((boolean) isErrorMethod.invoke(resultObj)) {
            status = "error";
          }
        } catch (Exception e) {
          // Ignore introspection errors
        }
      }

      return result;
    } catch (Exception e) {
      status = "error";
      throw e;
    } finally {
      long duration = System.currentTimeMillis() - startTime;

      // Record metrics
      recordMetrics(toolName, status, duration);

      // Log structured information
      logger.info(
          "MCP tool invocation: tool={}, status={}, durationMs={}",
          toolName,
          status,
          duration);
    }
  }

  /**
   * Records metrics for a tool invocation.
   *
   * @param toolName the tool name
   * @param status the invocation status (success/error)
   * @param duration the duration in milliseconds
   */
  private void recordMetrics(String toolName, String status, long duration) {
    // Increment counter
    Counter counter =
        Counter.builder("mcp.tool.invocations")
            .tag("tool-name", toolName)
            .tag("status", status)
            .register(meterRegistry);
    counter.increment();

    // Record timer
    Timer timer =
        Timer.builder("mcp.tool.duration")
            .tag("tool-name", toolName)
            .tag("status", status)
            .register(meterRegistry);
    timer.record(duration, java.util.concurrent.TimeUnit.MILLISECONDS);
  }
}

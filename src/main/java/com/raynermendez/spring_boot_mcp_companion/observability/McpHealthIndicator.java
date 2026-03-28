package com.raynermendez.spring_boot_mcp_companion.observability;

import com.raynermendez.spring_boot_mcp_companion.registry.McpDefinitionRegistry;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Health indicator for MCP server status.
 *
 * <p>Reports:
 * <ul>
 *   <li>UP/DOWN status based on registry state
 *   <li>toolCount: number of registered tools
 *   <li>resourceCount: number of registered resources
 *   <li>promptCount: number of registered prompts
 * </ul>
 */
@Component("mcpServer")
public class McpHealthIndicator implements HealthIndicator {

  private final McpDefinitionRegistry registry;

  public McpHealthIndicator(McpDefinitionRegistry registry) {
    this.registry = registry;
  }

  @Override
  public Health health() {
    try {
      int toolCount = registry.getTools().size();
      int resourceCount = registry.getResources().size();
      int promptCount = registry.getPrompts().size();

      return Health.up()
          .withDetail("toolCount", toolCount)
          .withDetail("resourceCount", resourceCount)
          .withDetail("promptCount", promptCount)
          .withDetail("registryState", "active")
          .build();
    } catch (Exception e) {
      return Health.down()
          .withDetail("error", e.getMessage())
          .withDetail("registryState", "failed")
          .build();
    }
  }
}

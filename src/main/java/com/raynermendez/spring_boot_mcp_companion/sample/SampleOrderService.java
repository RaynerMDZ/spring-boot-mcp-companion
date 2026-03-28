package com.raynermendez.spring_boot_mcp_companion.sample;

import com.raynermendez.spring_boot_mcp_companion.annotation.McpInput;
import com.raynermendez.spring_boot_mcp_companion.annotation.McpPrompt;
import com.raynermendez.spring_boot_mcp_companion.annotation.McpResource;
import com.raynermendez.spring_boot_mcp_companion.annotation.McpTool;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;

/**
 * Sample service demonstrating MCP tool integration.
 *
 * <p>This service exposes an MCP tool for retrieving orders, alongside providing a regular
 * service interface for business logic.
 */
@Service
public class SampleOrderService {

  /**
   * Retrieves an order by ID via MCP tool.
   *
   * @param orderId the order ID to retrieve
   * @return the Order object
   */
  @McpTool(description = "Retrieves an order by its ID")
  public Order getOrder(@McpInput(required = true) String orderId) {
    // Hardcoded sample data for demonstration
    if ("ORDER-001".equals(orderId)) {
      return new Order("ORDER-001", "Sample Widget", new BigDecimal("99.99"), "COMPLETED");
    } else if ("ORDER-002".equals(orderId)) {
      return new Order("ORDER-002", "Sample Gadget", new BigDecimal("149.99"), "PENDING");
    }
    return new Order(orderId, "Unknown Order", BigDecimal.ZERO, "NOT_FOUND");
  }

  /**
   * Retrieves order details as a resource.
   *
   * @param orderId the order ID
   * @return the Order object as resource
   */
  @McpResource(uri = "order://{orderId}", description = "Retrieves order by ID as resource")
  public Order getOrderAsResource(@McpInput(required = true) String orderId) {
    // Reuse the same data as the tool
    if ("ORDER-001".equals(orderId)) {
      return new Order("ORDER-001", "Sample Widget", new BigDecimal("99.99"), "COMPLETED");
    } else if ("ORDER-002".equals(orderId)) {
      return new Order("ORDER-002", "Sample Gadget", new BigDecimal("149.99"), "PENDING");
    }
    return new Order(orderId, "Unknown Order", BigDecimal.ZERO, "NOT_FOUND");
  }

  /**
   * Generates an order summary prompt.
   *
   * @param orderId the order ID
   * @return formatted order summary string
   */
  @McpPrompt(name = "order-summary", description = "Generates an order summary prompt")
  public String generateOrderSummary(@McpInput(required = true) String orderId) {
    Order order = getOrder(orderId);
    return String.format(
        "Order Summary: ID=%s, Item=%s, Price=%s, Status=%s",
        order.orderId(), order.name(), order.amount(), order.status());
  }

  /**
   * Regular service method (not exposed as MCP tool).
   *
   * @param orderId the order ID
   * @return true if the order exists
   */
  public boolean orderExists(String orderId) {
    return "ORDER-001".equals(orderId) || "ORDER-002".equals(orderId);
  }
}

package com.raynermendez.spring_boot_mcp_companion.sample;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sample REST controller demonstrating backward compatibility with traditional HTTP APIs.
 *
 * <p>This controller provides HTTP endpoints for the same functionality exposed via MCP tools,
 * demonstrating that MCP tools can coexist with existing HTTP APIs.
 */
@RestController
@RequestMapping("/api/orders")
public class SampleOrderController {

  private final SampleOrderService orderService;

  @Autowired
  public SampleOrderController(SampleOrderService orderService) {
    this.orderService = orderService;
  }

  /**
   * HTTP GET endpoint for retrieving orders.
   *
   * @param orderId the order ID
   * @return the Order object
   */
  @GetMapping("/{orderId}")
  public Order getOrder(@PathVariable String orderId) {
    return orderService.getOrder(orderId);
  }
}

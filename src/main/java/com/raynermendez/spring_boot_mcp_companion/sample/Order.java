package com.raynermendez.spring_boot_mcp_companion.sample;

import java.math.BigDecimal;

/**
 * Sample domain object representing an Order.
 *
 * @param orderId the unique order identifier
 * @param name the order name or description
 * @param amount the order amount
 * @param status the order status
 */
public record Order(
    String orderId,
    String name,
    BigDecimal amount,
    String status) {
}

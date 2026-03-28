package com.raynermendez.spring_boot_mcp_companion.validation;

/**
 * Represents a validation violation for MCP input.
 *
 * <p>A violation includes the field name and the error message that describes why the field is
 * invalid.
 */
public record McpViolation(
    /**
     * The name of the field that failed validation.
     */
    String field,
    /**
     * The error message describing the validation failure.
     */
    String message) {}

package com.raynermendez.spring_boot_mcp_companion.spi;

import com.raynermendez.spring_boot_mcp_companion.model.McpToolDefinition;

/**
 * SPI interface for serializing MCP tool output to strings.
 *
 * <p>Implementations can customize how tool results are serialized (JSON, XML, etc.).
 */
public interface McpOutputSerializer {

  /**
   * Serializes a tool result object to a string representation.
   *
   * @param result the result object from the tool method
   * @param toolDef the tool definition (for context)
   * @return serialized string representation
   * @throws Exception if serialization fails
   */
  String serialize(Object result, McpToolDefinition toolDef) throws Exception;
}

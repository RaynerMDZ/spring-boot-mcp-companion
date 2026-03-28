package com.raynermendez.spring_boot_mcp_companion.spi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raynermendez.spring_boot_mcp_companion.model.McpToolDefinition;

/**
 * Default implementation of MCP output serialization using Jackson JSON.
 */
public class DefaultMcpOutputSerializer implements McpOutputSerializer {

  private final ObjectMapper objectMapper;

  public DefaultMcpOutputSerializer(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public String serialize(Object result, McpToolDefinition toolDef) throws Exception {
    if (result == null) {
      return "null";
    } else if (result instanceof String) {
      return (String) result;
    } else {
      return objectMapper.writeValueAsString(result);
    }
  }
}

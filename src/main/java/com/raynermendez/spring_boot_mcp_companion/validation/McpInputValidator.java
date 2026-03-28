package com.raynermendez.spring_boot_mcp_companion.validation;

import com.raynermendez.spring_boot_mcp_companion.model.McpToolDefinition;
import java.util.List;
import java.util.Map;

/**
 * Interface for validating MCP tool input arguments.
 *
 * <p>Implementors should validate input arguments against the tool definition and return a list
 * of violations if validation fails.
 */
public interface McpInputValidator {

  /**
   * Validates input arguments against a tool definition.
   *
   * @param toolDef the tool definition containing parameter definitions
   * @param arguments the input arguments map
   * @return a list of violations (empty if validation passes)
   */
  List<McpViolation> validate(McpToolDefinition toolDef, Map<String, Object> arguments);
}

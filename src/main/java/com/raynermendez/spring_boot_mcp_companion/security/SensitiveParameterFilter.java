package com.raynermendez.spring_boot_mcp_companion.security;

import com.raynermendez.spring_boot_mcp_companion.model.McpParameterDefinition;
import com.raynermendez.spring_boot_mcp_companion.model.McpToolDefinition;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Filters sensitive parameters from argument maps to prevent information disclosure.
 *
 * <p>This utility ensures that sensitive parameters marked with @McpInput(sensitive=true) are:
 * <ul>
 *   <li>Not included in debug logs or metric tags
 *   <li>Not exposed in error messages
 *   <li>Redacted as "[REDACTED]" in any output used for logging/monitoring
 * </ul>
 *
 * <p>Sensitive parameters are still available to the actual method invocation but are masked in
 * all observability/logging contexts.
 */
public class SensitiveParameterFilter {

  private static final String REDACTION_PLACEHOLDER = "[REDACTED]";

  /**
   * Filters a map of arguments, replacing sensitive values with redaction placeholder.
   *
   * <p>Returns a new map suitable for logging/monitoring use. The original arguments map is not
   * modified.
   *
   * @param arguments the original arguments map (may contain sensitive data)
   * @param toolDef the tool definition containing parameter metadata
   * @return a filtered copy with sensitive values redacted
   */
  public static Map<String, Object> filterSensitiveArguments(
      Map<String, Object> arguments, McpToolDefinition toolDef) {
    if (arguments == null || arguments.isEmpty()) {
      return arguments;
    }

    Map<String, Object> filtered = new HashMap<>(arguments);
    List<McpParameterDefinition> parameters = toolDef.parameters();

    for (McpParameterDefinition param : parameters) {
      if (param.sensitive() && filtered.containsKey(param.name())) {
        filtered.put(param.name(), REDACTION_PLACEHOLDER);
      }
    }

    return filtered;
  }

  /**
   * Checks if a parameter is marked as sensitive.
   *
   * @param parameters the list of parameter definitions
   * @param paramName the parameter name to check
   * @return true if the parameter is sensitive, false otherwise
   */
  public static boolean isSensitiveParameter(
      List<McpParameterDefinition> parameters, String paramName) {
    return parameters.stream()
        .filter(p -> p.name().equals(paramName))
        .findFirst()
        .map(McpParameterDefinition::sensitive)
        .orElse(false);
  }

  /**
   * Extracts a parameter definition by name.
   *
   * @param parameters the list of parameter definitions
   * @param paramName the parameter name to find
   * @return the parameter definition, or null if not found
   */
  public static McpParameterDefinition findParameter(
      List<McpParameterDefinition> parameters, String paramName) {
    return parameters.stream()
        .filter(p -> p.name().equals(paramName))
        .findFirst()
        .orElse(null);
  }

  /**
   * Gets the redaction placeholder used in logs.
   *
   * @return the redaction placeholder string
   */
  public static String getRedactionPlaceholder() {
    return REDACTION_PLACEHOLDER;
  }
}

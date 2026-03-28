package com.raynermendez.spring_boot_mcp_companion.dispatch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raynermendez.spring_boot_mcp_companion.model.McpParameterDefinition;
import com.raynermendez.spring_boot_mcp_companion.model.McpPromptDefinition;
import com.raynermendez.spring_boot_mcp_companion.model.McpResourceDefinition;
import com.raynermendez.spring_boot_mcp_companion.model.McpToolDefinition;
import com.raynermendez.spring_boot_mcp_companion.registry.McpDefinitionRegistry;
import com.raynermendez.spring_boot_mcp_companion.spi.McpOutputSerializer;
import com.raynermendez.spring_boot_mcp_companion.validation.McpInputValidator;
import com.raynermendez.spring_boot_mcp_companion.validation.McpViolation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of the MCP Dispatcher.
 *
 * <p>This dispatcher handles tool invocations by looking up registered tools, validating
 * arguments, invoking the underlying methods, and returning standardized results.
 */
public class DefaultMcpDispatcher implements McpDispatcher {

  private static final Logger logger = LoggerFactory.getLogger(DefaultMcpDispatcher.class);

  private final McpDefinitionRegistry registry;
  private final ObjectMapper objectMapper;
  private final McpInputValidator validator;
  private final McpOutputSerializer serializer;

  public DefaultMcpDispatcher(
      McpDefinitionRegistry registry,
      ObjectMapper objectMapper,
      McpInputValidator validator,
      McpOutputSerializer serializer) {
    this.registry = registry;
    this.objectMapper = objectMapper;
    this.validator = validator;
    this.serializer = serializer;
  }

  @Override
  public McpToolResult dispatchTool(String name, Map<String, Object> arguments) {
    try {
      // Look up the tool by name
      McpToolDefinition toolDef =
          registry.getTools().stream()
              .filter(t -> t.name().equals(name))
              .findFirst()
              .orElse(null);

      if (toolDef == null) {
        String errorMsg = "Tool not found: " + name;
        return new McpToolResult(
            List.of(new McpDispatcher.McpContent("text", errorMsg)), true);
      }

      // Validate arguments
      List<McpViolation> violations = validator.validate(toolDef, arguments);
      if (!violations.isEmpty()) {
        String errorMsg = "Validation failed: " + formatViolations(violations);
        return new McpToolResult(
            List.of(new McpDispatcher.McpContent("text", errorMsg)), true);
      }

      // Convert arguments map to method parameters
      Object[] methodArgs = buildMethodArguments(toolDef, arguments);

      // Invoke the method
      Method method = toolDef.handler().method();
      Object targetBean = toolDef.handler().targetBean();
      Object result = method.invoke(targetBean, methodArgs);

      // Serialize result using the output serializer
      String serializedResult = serializer.serialize(result, toolDef);

      return new McpToolResult(
          List.of(new McpDispatcher.McpContent("text", serializedResult)), false);

    } catch (Exception e) {
      String errorMsg = "Error invoking tool '" + name + "': " + e.getMessage();
      logger.error(errorMsg, e);
      return new McpToolResult(
          List.of(new McpDispatcher.McpContent("text", errorMsg)), true);
    }
  }

  /**
   * Builds method arguments from the arguments map and tool parameter definitions.
   *
   * @param toolDef the tool definition
   * @param arguments the arguments map
   * @return an array of objects to pass to the method
   */
  private Object[] buildMethodArguments(McpToolDefinition toolDef, Map<String, Object> arguments) {
    List<McpParameterDefinition> parameters = toolDef.parameters();
    Object[] args = new Object[parameters.size()];

    for (int i = 0; i < parameters.size(); i++) {
      McpParameterDefinition param = parameters.get(i);
      Object value = arguments.get(param.name());

      if (value != null) {
        // Use Jackson to convert the value to the expected type
        Method method = toolDef.handler().method();
        Class<?> paramType = method.getParameterTypes()[i];
        args[i] = objectMapper.convertValue(value, paramType);
      } else {
        args[i] = null;
      }
    }

    return args;
  }

  @Override
  public McpDispatcher.McpResourceResult dispatchResource(String uri, Map<String, Object> params) {
    try {
      // Look up the resource by URI
      McpResourceDefinition resourceDef =
          registry.getResources().stream()
              .filter(r -> r.uri().equals(uri))
              .findFirst()
              .orElse(null);

      if (resourceDef == null) {
        String errorMsg = "Resource not found: " + uri;
        return new McpDispatcher.McpResourceResult(uri, errorMsg, "text/plain", true);
      }

      // Build method arguments and invoke
      Object[] methodArgs = buildMethodArgumentsForResource(resourceDef, params);
      Method method = resourceDef.handler().method();
      Object targetBean = resourceDef.handler().targetBean();
      Object result = method.invoke(targetBean, methodArgs);

      // Serialize result
      String serializedResult = serializer.serialize(result, null);

      return new McpDispatcher.McpResourceResult(
          uri, serializedResult, resourceDef.mimeType(), false);

    } catch (Exception e) {
      String errorMsg = "Error reading resource '" + uri + "': " + e.getMessage();
      logger.error(errorMsg, e);
      return new McpDispatcher.McpResourceResult(uri, errorMsg, "text/plain", true);
    }
  }

  @Override
  public McpDispatcher.McpPromptResult dispatchPrompt(String name, Map<String, Object> args) {
    try {
      // Look up the prompt by name
      McpPromptDefinition promptDef =
          registry.getPrompts().stream()
              .filter(p -> p.name().equals(name))
              .findFirst()
              .orElse(null);

      if (promptDef == null) {
        String errorMsg = "Prompt not found: " + name;
        return new McpDispatcher.McpPromptResult(
            List.of(new McpDispatcher.McpContent("text", errorMsg)), true);
      }

      // Build method arguments and invoke
      Object[] methodArgs = buildMethodArgumentsForPrompt(promptDef, args);
      Method method = promptDef.handler().method();
      Object targetBean = promptDef.handler().targetBean();
      Object result = method.invoke(targetBean, methodArgs);

      // Serialize result
      String serializedResult = serializer.serialize(result, null);

      return new McpDispatcher.McpPromptResult(
          List.of(new McpDispatcher.McpContent("text", serializedResult)), false);

    } catch (Exception e) {
      String errorMsg = "Error invoking prompt '" + name + "': " + e.getMessage();
      logger.error(errorMsg, e);
      return new McpDispatcher.McpPromptResult(
          List.of(new McpDispatcher.McpContent("text", errorMsg)), true);
    }
  }

  /**
   * Builds method arguments for resource dispatch.
   *
   * @param resourceDef the resource definition
   * @param params the parameters map
   * @return method arguments array
   */
  private Object[] buildMethodArgumentsForResource(
      McpResourceDefinition resourceDef, Map<String, Object> params) {
    Method method = resourceDef.handler().method();
    int paramCount = method.getParameterCount();
    Object[] args = new Object[paramCount];

    if (params == null) {
      params = Map.of();
    }

    for (int i = 0; i < paramCount; i++) {
      Class<?> paramType = method.getParameterTypes()[i];
      String paramName = method.getParameters()[i].getName();
      Object value = params.get(paramName);

      if (value != null) {
        args[i] = objectMapper.convertValue(value, paramType);
      } else {
        args[i] = null;
      }
    }

    return args;
  }

  /**
   * Builds method arguments for prompt dispatch.
   *
   * @param promptDef the prompt definition
   * @param args the arguments map
   * @return method arguments array
   */
  private Object[] buildMethodArgumentsForPrompt(
      McpPromptDefinition promptDef, Map<String, Object> args) {
    Method method = promptDef.handler().method();
    int paramCount = method.getParameterCount();
    Object[] methodArgs = new Object[paramCount];

    if (args == null) {
      args = Map.of();
    }

    for (int i = 0; i < paramCount; i++) {
      Class<?> paramType = method.getParameterTypes()[i];
      String paramName = method.getParameters()[i].getName();
      Object value = args.get(paramName);

      if (value != null) {
        methodArgs[i] = objectMapper.convertValue(value, paramType);
      } else {
        methodArgs[i] = null;
      }
    }

    return methodArgs;
  }

  /**
   * Formats validation violations into a human-readable message.
   *
   * @param violations the list of violations
   * @return formatted error message
   */
  private String formatViolations(List<McpViolation> violations) {
    StringBuilder sb = new StringBuilder();
    for (McpViolation violation : violations) {
      if (sb.length() > 0) {
        sb.append("; ");
      }
      sb.append(violation.field()).append(": ").append(violation.message());
    }
    return sb.toString();
  }
}

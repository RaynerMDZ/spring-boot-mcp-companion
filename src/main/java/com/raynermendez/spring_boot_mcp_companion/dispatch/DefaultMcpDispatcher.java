package com.raynermendez.spring_boot_mcp_companion.dispatch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raynermendez.spring_boot_mcp_companion.model.McpParameterDefinition;
import com.raynermendez.spring_boot_mcp_companion.model.McpPromptDefinition;
import com.raynermendez.spring_boot_mcp_companion.model.McpResourceDefinition;
import com.raynermendez.spring_boot_mcp_companion.model.McpToolDefinition;
import com.raynermendez.spring_boot_mcp_companion.registry.McpDefinitionRegistry;
import com.raynermendez.spring_boot_mcp_companion.security.SensitiveParameterFilter;
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
    McpToolDefinition toolDef = null;
    try {
      // Look up the tool by name
      toolDef =
          registry.getTools().stream()
              .filter(t -> t.name().equals(name))
              .findFirst()
              .orElse(null);

      if (toolDef == null) {
        String errorMsg = "Tool not found: " + name;
        return new McpToolResult(
            List.of(new McpDispatcher.McpContent("text", errorMsg)), true);
      }

      // Security: Verify the method is safe to invoke
      verifyMethodSecurity(toolDef.handler().method(), "tool '" + name + "'");

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
      // Security: Log with filtered arguments to avoid exposing sensitive parameters
      // Note: toolDef may be null if tool lookup failed, so filter only if available
      Map<String, Object> filteredArguments =
          toolDef != null
              ? SensitiveParameterFilter.filterSensitiveArguments(arguments, toolDef)
              : arguments;
      logger.error("Error invoking tool '{}' with arguments {}", name, filteredArguments, e);
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
        // Security: Pre-validate type compatibility before Jackson conversion
        Method method = toolDef.handler().method();
        Class<?> paramType = method.getParameterTypes()[i];

        if (!isValidType(value, paramType)) {
          throw new IllegalArgumentException(
              "Parameter '" + param.name() + "' has invalid type. Expected " + paramType.getSimpleName()
                  + " but got " + value.getClass().getSimpleName());
        }

        args[i] = objectMapper.convertValue(value, paramType);
      } else {
        args[i] = null;
      }
    }

    return args;
  }

  /**
   * Validates that a value is compatible with the target type before conversion.
   *
   * <p>Security: Performs basic type validation to prevent type coercion attacks that could
   * bypass business logic or exploit unexpected type conversions. Accepts primitives, collections,
   * and complex objects for flexible parameter handling.
   *
   * @param value the value to validate
   * @param targetType the target type
   * @return true if the value is compatible with the target type
   */
  private boolean isValidType(Object value, Class<?> targetType) {
    // null values are handled separately
    if (value == null) {
      return true;
    }

    // String type - accept any value (will be converted to string)
    if (targetType == String.class) {
      return true;
    }

    // Boolean type - accept Boolean or String values
    if (targetType == Boolean.class || targetType == boolean.class) {
      return value instanceof Boolean || value instanceof String;
    }

    // Numeric types - accept Number or String values
    if (targetType == Integer.class || targetType == int.class ||
        targetType == Long.class || targetType == long.class ||
        targetType == Double.class || targetType == double.class ||
        targetType == Float.class || targetType == float.class) {
      return value instanceof Number || value instanceof String;
    }

    // List/Array type - accept List, Map (will be converted), or String values
    if (targetType == java.util.List.class || targetType.isArray()) {
      return value instanceof java.util.List || value instanceof Map || value instanceof String;
    }

    // Map type - accept Map, List (array-like), or String values
    if (targetType == Map.class || java.util.Map.class.isAssignableFrom(targetType)) {
      return value instanceof Map || value instanceof java.util.List || value instanceof String;
    }

    // Complex objects - accept Map (JSON object) or String values
    // This allows JSON objects to be deserialized into custom objects
    if (!targetType.isPrimitive() && !targetType.isArray()) {
      return value instanceof Map || value instanceof String;
    }

    // For primitive arrays, only accept List or primitive array
    if (value.getClass().isArray()) {
      return value.getClass().getComponentType().isPrimitive() ||
             targetType == java.util.List.class ||
             targetType.isArray();
    }

    // Default: allow Jackson to attempt conversion
    return true;
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

      // Security: Verify the method is safe to invoke
      verifyMethodSecurity(resourceDef.handler().method(), "resource '" + uri + "'");

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
      // Security: Do not log resource parameters to avoid exposing sensitive data
      logger.error("Error reading resource '{}'", uri, e);
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

      // Security: Verify the method is safe to invoke
      verifyMethodSecurity(promptDef.handler().method(), "prompt '" + name + "'");

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
      // Security: Do not log prompt arguments to avoid exposing sensitive data
      logger.error("Error invoking prompt '{}'", name, e);
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

  /**
   * Verifies that a method is safe to invoke via reflection.
   *
   * <p>Security: Performs runtime checks to prevent arbitrary method invocation:
   * <ul>
   *   <li>Method must be public
   *   <li>Method must not be from Object, Class, or other dangerous classes
   *   <li>Target bean class must be Spring-managed
   * </ul>
   *
   * @param method the method to verify
   * @param context the context description for error messages (e.g., "tool 'getName'")
   * @throws IllegalAccessException if the method is not safe to invoke
   */
  private void verifyMethodSecurity(Method method, String context) throws IllegalAccessException {
    // Check if method is public
    if (!java.lang.reflect.Modifier.isPublic(method.getModifiers())) {
      throw new IllegalAccessException("Method for " + context + " is not public");
    }

    // Prevent invocation of dangerous base class methods
    Class<?> declaringClass = method.getDeclaringClass();
    String className = declaringClass.getName();

    if (className.startsWith("java.lang.") || className.startsWith("java.lang.reflect.")) {
      throw new IllegalAccessException(
          "Method for " + context + " is from restricted class: " + className);
    }

    // Reject methods from Object, Class, ClassLoader, etc.
    if (declaringClass == Object.class ||
        declaringClass == Class.class ||
        declaringClass == ClassLoader.class ||
        declaringClass == Runtime.class ||
        declaringClass == System.class) {
      throw new IllegalAccessException(
          "Method for " + context + " is from dangerous base class: " + className);
    }

    logger.debug("Method security verified for {}: {}.{}", context, declaringClass.getSimpleName(),
        method.getName());
  }
}

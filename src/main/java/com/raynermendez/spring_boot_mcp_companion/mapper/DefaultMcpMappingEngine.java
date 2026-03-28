package com.raynermendez.spring_boot_mcp_companion.mapper;

import com.raynermendez.spring_boot_mcp_companion.annotation.McpInput;
import com.raynermendez.spring_boot_mcp_companion.annotation.McpPrompt;
import com.raynermendez.spring_boot_mcp_companion.annotation.McpResource;
import com.raynermendez.spring_boot_mcp_companion.annotation.McpTool;
import com.raynermendez.spring_boot_mcp_companion.model.McpParameterDefinition;
import com.raynermendez.spring_boot_mcp_companion.model.McpPromptDefinition;
import com.raynermendez.spring_boot_mcp_companion.model.McpResourceDefinition;
import com.raynermendez.spring_boot_mcp_companion.model.McpToolDefinition;
import com.raynermendez.spring_boot_mcp_companion.model.MethodHandlerRef;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Default implementation of the MCP Mapping Engine.
 *
 * <p>This component converts @McpTool-annotated methods into complete McpToolDefinition objects,
 * deriving tool names, building parameter lists, and generating JSON Schema input definitions.
 */
public class DefaultMcpMappingEngine implements McpMappingEngine {

  private final JsonSchemaGenerator jsonSchemaGenerator;

  public DefaultMcpMappingEngine(JsonSchemaGenerator jsonSchemaGenerator) {
    this.jsonSchemaGenerator = jsonSchemaGenerator;
  }

  @Override
  public McpToolDefinition toToolDefinition(
      Object bean, Method method, McpTool annotation) {
    // Derive tool name from annotation or method name
    String toolName =
        annotation.name() != null && !annotation.name().isBlank()
            ? annotation.name()
            : methodNameToSnakeCase(method.getName());

    // Get description from annotation
    String description =
        annotation.description() != null ? annotation.description() : "";

    // Get tags from annotation
    String[] tags = annotation.tags() != null ? annotation.tags() : new String[0];

    // Build parameter definitions
    List<McpParameterDefinition> parameters = buildParameterDefinitions(method);

    // Build input schema by merging all parameters
    Map<String, Object> inputSchema = buildInputSchema(method, parameters);

    // Create method handler reference
    MethodHandlerRef handler = new MethodHandlerRef(bean, method, bean.getClass().getSimpleName());

    return new McpToolDefinition(toolName, description, tags, parameters, inputSchema, handler);
  }

  @Override
  public McpResourceDefinition toResourceDefinition(
      Object bean, Method method, McpResource annotation) {
    String resourceUri = annotation.uri() != null ? annotation.uri() : method.getName();
    String description =
        annotation.description() != null ? annotation.description() : "";
    String mimeType = annotation.mimeType() != null ? annotation.mimeType() : "text/plain";

    MethodHandlerRef handler = new MethodHandlerRef(bean, method, bean.getClass().getSimpleName());

    return new McpResourceDefinition(
        resourceUri,
        method.getName(),
        description,
        mimeType,
        handler);
  }

  @Override
  public McpPromptDefinition toPromptDefinition(
      Object bean, Method method, McpPrompt annotation) {
    String promptName =
        annotation.name() != null && !annotation.name().isBlank()
            ? annotation.name()
            : methodNameToSnakeCase(method.getName());
    String description =
        annotation.description() != null ? annotation.description() : "";

    // Build prompt arguments from parameters
    List<McpPromptDefinition.McpPromptArgument> arguments = buildPromptArguments(method);

    MethodHandlerRef handler = new MethodHandlerRef(bean, method, bean.getClass().getSimpleName());

    return new McpPromptDefinition(promptName, description, arguments, handler);
  }

  /**
   * Converts a camelCase method name to snake_case.
   *
   * @param methodName the camelCase method name
   * @return the snake_case equivalent
   */
  private String methodNameToSnakeCase(String methodName) {
    return methodName.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
  }

  /**
   * Builds parameter definitions for all parameters of the method.
   *
   * @param method the method to extract parameters from
   * @return a list of McpParameterDefinition objects
   */
  private List<McpParameterDefinition> buildParameterDefinitions(Method method) {
    List<McpParameterDefinition> parameters = new ArrayList<>();
    Parameter[] methodParameters = method.getParameters();

    for (Parameter param : methodParameters) {
      McpInput mcpInput = param.getAnnotation(McpInput.class);

      String paramName =
          (mcpInput != null && !mcpInput.name().isBlank())
              ? mcpInput.name()
              : param.getName();

      String paramDescription = (mcpInput != null) ? mcpInput.description() : "";
      boolean required = (mcpInput != null) ? mcpInput.required() : true;
      boolean sensitive = (mcpInput != null) ? mcpInput.sensitive() : false;

      Map<String, Object> jsonSchema =
          jsonSchemaGenerator.generateParameterSchema(
              param.getType(), param.getAnnotations());

      McpParameterDefinition paramDef =
          new McpParameterDefinition(paramName, paramDescription, required, jsonSchema, sensitive);
      parameters.add(paramDef);
    }

    return parameters;
  }

  /**
   * Builds the input schema by merging all parameter definitions.
   *
   * @param method the method
   * @param parameters the list of parameter definitions
   * @return a JSON Schema object representing all inputs
   */
  private Map<String, Object> buildInputSchema(
      Method method, List<McpParameterDefinition> parameters) {
    Map<String, Object> schema = new HashMap<>();
    schema.put("type", "object");

    Map<String, Object> properties = new HashMap<>();
    List<String> required = new ArrayList<>();

    for (McpParameterDefinition param : parameters) {
      properties.put(param.name(), param.jsonSchema());
      if (param.required()) {
        required.add(param.name());
      }
    }

    if (!properties.isEmpty()) {
      schema.put("properties", properties);
    }
    if (!required.isEmpty()) {
      schema.put("required", required);
    }

    return schema;
  }

  /**
   * Builds prompt arguments from method parameters.
   *
   * @param method the method to extract arguments from
   * @return list of prompt arguments
   */
  private List<McpPromptDefinition.McpPromptArgument> buildPromptArguments(Method method) {
    List<McpPromptDefinition.McpPromptArgument> arguments = new ArrayList<>();
    Parameter[] methodParameters = method.getParameters();

    for (Parameter param : methodParameters) {
      McpInput mcpInput = param.getAnnotation(McpInput.class);

      String argName =
          (mcpInput != null && !mcpInput.name().isBlank())
              ? mcpInput.name()
              : param.getName();

      String argDescription = (mcpInput != null) ? mcpInput.description() : "";
      boolean required = (mcpInput != null) ? mcpInput.required() : true;

      arguments.add(new McpPromptDefinition.McpPromptArgument(argName, argDescription, required));
    }

    return arguments;
  }
}

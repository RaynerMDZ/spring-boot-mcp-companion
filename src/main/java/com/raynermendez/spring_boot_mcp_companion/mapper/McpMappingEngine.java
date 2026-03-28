package com.raynermendez.spring_boot_mcp_companion.mapper;

import com.raynermendez.spring_boot_mcp_companion.annotation.McpPrompt;
import com.raynermendez.spring_boot_mcp_companion.annotation.McpResource;
import com.raynermendez.spring_boot_mcp_companion.annotation.McpTool;
import com.raynermendez.spring_boot_mcp_companion.model.McpPromptDefinition;
import com.raynermendez.spring_boot_mcp_companion.model.McpResourceDefinition;
import com.raynermendez.spring_boot_mcp_companion.model.McpToolDefinition;
import java.lang.reflect.Method;

/**
 * Interface for mapping Java methods annotated with @McpTool, @McpResource, or @McpPrompt to MCP
 * definitions.
 *
 * <p>The mapping engine takes a Spring bean instance, a Method, and an annotation and produces a
 * complete definition with parameter definitions and JSON Schema information.
 */
public interface McpMappingEngine {

  /**
   * Maps a Java method annotated with @McpTool to a complete MCP tool definition.
   *
   * @param bean the Spring bean instance containing the method
   * @param method the Java Method object
   * @param annotation the @McpTool annotation from the method
   * @return a complete McpToolDefinition ready for registration
   */
  McpToolDefinition toToolDefinition(Object bean, Method method, McpTool annotation);

  /**
   * Maps a Java method annotated with @McpResource to a complete MCP resource definition.
   *
   * @param bean the Spring bean instance containing the method
   * @param method the Java Method object
   * @param annotation the @McpResource annotation from the method
   * @return a complete McpResourceDefinition ready for registration
   */
  McpResourceDefinition toResourceDefinition(Object bean, Method method, McpResource annotation);

  /**
   * Maps a Java method annotated with @McpPrompt to a complete MCP prompt definition.
   *
   * @param bean the Spring bean instance containing the method
   * @param method the Java Method object
   * @param annotation the @McpPrompt annotation from the method
   * @return a complete McpPromptDefinition ready for registration
   */
  McpPromptDefinition toPromptDefinition(Object bean, Method method, McpPrompt annotation);
}

package com.raynermendez.spring_boot_mcp_companion.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as an MCP Prompt, exposing reusable prompt templates through the MCP protocol.
 *
 * <p>Methods annotated with @McpPrompt are automatically registered when the application starts,
 * and become available as prompt templates through the MCP server.
 */
@Target(java.lang.annotation.ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface McpPrompt {

  /**
   * The name of the prompt template.
   */
  String name() default "";

  /**
   * Human-readable description of what this prompt template does.
   */
  String description() default "";
}

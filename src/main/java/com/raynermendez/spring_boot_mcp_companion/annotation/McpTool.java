package com.raynermendez.spring_boot_mcp_companion.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as an MCP Tool, making it discoverable and callable through the MCP protocol.
 *
 * <p>Methods annotated with @McpTool are automatically registered when the application starts,
 * and become available as remote-callable functions through the MCP server.
 */
@Target(java.lang.annotation.ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface McpTool {

  /**
   * The name of the tool as exposed via MCP protocol.
   * If empty, defaults to the method name.
   */
  String name() default "";

  /**
   * Human-readable description of what this tool does.
   * Used in MCP server-info and client discovery.
   */
  String description() default "";

  /**
   * Optional tags for categorizing or filtering tools.
   */
  String[] tags() default {};
}

package com.raynermendez.spring_boot_mcp_companion.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a parameter of an MCP-annotated method as an input parameter.
 *
 * <p>Parameters annotated with @McpInput are extracted and exposed in the MCP tool/prompt/resource
 * definitions, allowing clients to discover and provide parameter values.
 */
@Target(java.lang.annotation.ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface McpInput {

  /**
   * The name of the input parameter.
   * If empty, defaults to the parameter name.
   */
  String name() default "";

  /**
   * Human-readable description of what this parameter expects.
   */
  String description() default "";

  /**
   * Whether this parameter is required (true) or optional (false).
   * Defaults to true.
   */
  boolean required() default true;

  /**
   * Whether this parameter contains sensitive data (e.g., passwords, API keys).
   * Defaults to false. When true, clients may mask or handle the parameter specially.
   */
  boolean sensitive() default false;
}

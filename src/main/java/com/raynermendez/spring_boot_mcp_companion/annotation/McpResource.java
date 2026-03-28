package com.raynermendez.spring_boot_mcp_companion.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as an MCP Resource provider, exposing file or data resources through the MCP
 * protocol.
 *
 * <p>Methods annotated with @McpResource are automatically registered when the application
 * starts, and become available as resource endpoints through the MCP server.
 */
@Target(java.lang.annotation.ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface McpResource {

  /**
   * The URI template for this resource (e.g., "file:///{path}").
   * Used to route resource requests to the appropriate handler.
   */
  String uri() default "";

  /**
   * The name of the resource.
   */
  String name() default "";

  /**
   * Human-readable description of what this resource provides.
   */
  String description() default "";

  /**
   * MIME type of the resource content.
   * Defaults to "application/octet-stream" for binary data.
   */
  String mimeType() default "application/octet-stream";
}

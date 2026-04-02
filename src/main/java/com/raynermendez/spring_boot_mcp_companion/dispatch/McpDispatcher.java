package com.raynermendez.spring_boot_mcp_companion.dispatch;

import java.util.List;
import java.util.Map;

/**
 * Interface for dispatching tool invocations to registered MCP tools.
 *
 * <p>The dispatcher takes a tool name and a map of arguments, looks up the corresponding tool
 * definition, invokes the underlying method, and returns the result in a standardized format.
 */
public interface McpDispatcher {

  /**
   * Dispatches a call to the named MCP tool with the given arguments.
   *
   * @param name the name of the tool to invoke
   * @param arguments a map of argument values keyed by parameter name
   * @return the result of the tool invocation
   */
  McpToolResult dispatchTool(String name, Map<String, Object> arguments);

  /**
   * Dispatches a read request for a resource at the given URI.
   *
   * @param uri the resource URI
   * @param params optional parameters for the resource read
   * @return the result of the resource read
   */
  McpResourceResult dispatchResource(String uri, Map<String, Object> params);

  /**
   * Dispatches a call to the named MCP prompt with the given arguments.
   *
   * @param name the name of the prompt to invoke
   * @param args a map of argument values keyed by argument name
   * @return the result of the prompt invocation
   */
  McpPromptResult dispatchPrompt(String name, Map<String, Object> args);

  /**
   * Represents the result of a tool invocation.
   *
   * @param content list of content items returned by the tool
   * @param isError whether the invocation resulted in an error
   */
  record McpToolResult(List<McpContent> content, boolean isError) {}

  /**
   * Represents a single piece of content in a tool result.
   *
   * @param type the content type (e.g., "text", "image")
   * @param text the content value as a string
   */
  record McpContent(String type, String text) {}

  /**
   * Represents the result of a resource read.
   *
   * @param uri the resource URI
   * @param content the resource content
   * @param mimeType the MIME type of the content
   * @param isError whether the read resulted in an error
   * @param errorCode JSON-RPC error code to use when isError is true (-32002 not found, -32603 internal)
   */
  record McpResourceResult(String uri, String content, String mimeType, boolean isError, int errorCode) {}

  /**
   * Represents the result of a prompt invocation.
   *
   * @param content list of content items returned by the prompt
   * @param isError whether the invocation resulted in an error
   */
  record McpPromptResult(List<McpContent> content, boolean isError) {}
}

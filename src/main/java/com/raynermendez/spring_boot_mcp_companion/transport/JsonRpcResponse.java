package com.raynermendez.spring_boot_mcp_companion.transport;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * JSON-RPC 2.0 response envelope.
 *
 * @param jsonrpc the JSON-RPC version (always "2.0")
 * @param id the request ID (null for notifications)
 * @param result the result of the method call
 * @param error the error, if the call failed
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JsonRpcResponse(
    String jsonrpc,
    Object id,
    Object result,
    JsonRpcError error) {

  /**
   * Creates a success response.
   *
   * @param id the request ID
   * @param result the result object
   * @return a new JsonRpcResponse with the result
   */
  public static JsonRpcResponse success(Object id, Object result) {
    return new JsonRpcResponse("2.0", id, result, null);
  }

  /**
   * Creates an error response.
   *
   * @param id the request ID
   * @param error the error object
   * @return a new JsonRpcResponse with the error
   */
  public static JsonRpcResponse error(Object id, JsonRpcError error) {
    return new JsonRpcResponse("2.0", id, null, error);
  }
}

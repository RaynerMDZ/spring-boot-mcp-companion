package com.raynermendez.spring_boot_mcp_companion.transport;

/**
 * JSON-RPC 2.0 error object.
 *
 * @param code the error code
 * @param message the error message
 * @param data optional additional error data
 */
public record JsonRpcError(
    int code,
    String message,
    Object data) {

  // Predefined error codes per JSON-RPC 2.0 spec
  public static final int METHOD_NOT_FOUND = -32601;
  public static final int INVALID_REQUEST = -32600;
  public static final int PARSE_ERROR = -32700;
  public static final int INVALID_PARAMS = -32602;
  public static final int INTERNAL_ERROR = -32603;
}

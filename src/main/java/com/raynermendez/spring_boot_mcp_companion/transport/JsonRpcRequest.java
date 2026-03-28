package com.raynermendez.spring_boot_mcp_companion.transport;

import java.util.Map;

/**
 * JSON-RPC 2.0 request envelope.
 *
 * @param jsonrpc the JSON-RPC version (always "2.0")
 * @param id the request ID for correlation with response
 * @param method the method to invoke
 * @param params the method parameters
 */
public record JsonRpcRequest(
    String jsonrpc,
    Object id,
    String method,
    Map<String, Object> params) {
}

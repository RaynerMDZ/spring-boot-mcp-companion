package com.raynermendez.spring_boot_mcp_companion.transport;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

/**
 * Streamable response wrapper for MCP responses.
 *
 * <p>Allows MCP responses to be streamed directly to the HTTP response body without buffering
 * in memory. Supports both JSON-RPC formatted responses and raw streaming.
 *
 * <p>This enables efficient handling of large responses (>100MB) that would otherwise cause
 * memory issues.
 */
public interface StreamableResponse {

  /**
   * Gets the content type for this response.
   *
   * @return MIME type (e.g., "application/json", "application/octet-stream")
   */
  String getContentType();

  /**
   * Gets the content length if known, or -1 for chunked encoding.
   *
   * @return byte count or -1 for unknown
   */
  long getContentLength();

  /**
   * Writes the response to the output stream.
   *
   * @param out the output stream to write to
   * @throws IOException if write fails
   */
  void writeTo(OutputStream out) throws IOException;

  /**
   * Factory method for JSON-RPC responses (buffered).
   *
   * @param response the JSON-RPC response map
   * @return streamable response
   */
  static StreamableResponse jsonRpc(Map<String, Object> response) {
    return new JsonRpcStreamableResponse(response);
  }

  /**
   * Factory method for raw streaming responses.
   *
   * @param inputSupplier supplies the input stream to stream from
   * @param contentType the MIME type
   * @param contentLength the content length or -1 for chunked
   * @return streamable response
   */
  static StreamableResponse stream(
      InputStreamSupplier inputSupplier, String contentType, long contentLength) {
    return new StreamingResponse(inputSupplier, contentType, contentLength);
  }

  /**
   * Supplies an input stream for streaming responses.
   */
  @FunctionalInterface
  interface InputStreamSupplier {
    java.io.InputStream get() throws IOException;
  }
}

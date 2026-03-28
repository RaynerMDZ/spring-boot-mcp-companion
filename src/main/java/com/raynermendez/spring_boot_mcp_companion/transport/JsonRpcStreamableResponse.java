package com.raynermendez.spring_boot_mcp_companion.transport;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * JSON-RPC streamable response implementation.
 *
 * <p>Streams JSON-RPC responses directly to the output stream without buffering the entire
 * response in memory. Uses streaming JSON writer for efficient serialization.
 */
class JsonRpcStreamableResponse implements StreamableResponse {

  private final Map<String, Object> response;
  private final ObjectMapper objectMapper = new ObjectMapper();

  JsonRpcStreamableResponse(Map<String, Object> response) {
    this.response = response;
  }

  @Override
  public String getContentType() {
    return "application/json";
  }

  @Override
  public long getContentLength() {
    // Unknown - will use chunked transfer encoding
    return -1;
  }

  @Override
  public void writeTo(OutputStream out) throws IOException {
    // Stream the JSON-RPC response directly to the output stream
    // This avoids buffering the entire response in memory
    byte[] jsonBytes = objectMapper.writeValueAsBytes(response);
    out.write(jsonBytes);
    out.flush();
  }
}

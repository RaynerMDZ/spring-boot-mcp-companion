package com.raynermendez.spring_boot_mcp_companion.transport;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Raw streaming response implementation.
 *
 * <p>Streams raw data from an InputStream directly to the HTTP response output stream. Supports
 * large files and real-time data streams without buffering in memory.
 */
class StreamingResponse implements StreamableResponse {

  private static final int BUFFER_SIZE = 8192; // 8KB buffer

  private final StreamableResponse.InputStreamSupplier inputSupplier;
  private final String contentType;
  private final long contentLength;

  StreamingResponse(
      StreamableResponse.InputStreamSupplier inputSupplier,
      String contentType,
      long contentLength) {
    this.inputSupplier = inputSupplier;
    this.contentType = contentType;
    this.contentLength = contentLength;
  }

  @Override
  public String getContentType() {
    return contentType;
  }

  @Override
  public long getContentLength() {
    return contentLength;
  }

  @Override
  public void writeTo(OutputStream out) throws IOException {
    try (InputStream in = inputSupplier.get()) {
      byte[] buffer = new byte[BUFFER_SIZE];
      int bytesRead;

      while ((bytesRead = in.read(buffer)) != -1) {
        out.write(buffer, 0, bytesRead);
        out.flush();
      }
    }
  }
}

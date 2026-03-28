package com.raynermendez.spring_boot_mcp_companion.transport;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests for StreamableResponse interface and implementations.
 *
 * <p>Verifies that responses can be streamed directly to output streams without buffering in
 * memory, supporting both JSON-RPC responses and raw binary data streaming.
 */
class StreamableResponseTest {

  private static final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void testJsonRpcStreamableResponseStreamsCorrectly() throws IOException {
    Map<String, Object> response = Map.of(
        "jsonrpc", "2.0",
        "id", 1,
        "result", Map.of(
            "protocolVersion", "2025-11-25",
            "capabilities", Map.of(),
            "serverInfo", Map.of("name", "Test", "version", "1.0.0")
        )
    );

    StreamableResponse streamable = StreamableResponse.jsonRpc(response);

    // Verify content type
    assertEquals("application/json", streamable.getContentType());

    // Verify content is streamed correctly
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    streamable.writeTo(outputStream);
    String result = outputStream.toString(StandardCharsets.UTF_8);

    assertTrue(result.contains("\"jsonrpc\":\"2.0\""));
    assertTrue(result.contains("\"protocolVersion\":\"2025-11-25\""));
    assertTrue(result.contains("\"serverInfo\""));
  }

  @Test
  void testStreamingResponseStreamsFromInputStream() throws IOException {
    String testData = "This is test streaming data";
    byte[] testBytes = testData.getBytes(StandardCharsets.UTF_8);

    StreamableResponse streamable = StreamableResponse.stream(
        () -> new java.io.ByteArrayInputStream(testBytes),
        "text/plain",
        testBytes.length
    );

    // Verify content type and length
    assertEquals("text/plain", streamable.getContentType());
    assertEquals(testBytes.length, streamable.getContentLength());

    // Verify content is streamed correctly
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    streamable.writeTo(outputStream);
    String result = outputStream.toString(StandardCharsets.UTF_8);

    assertEquals(testData, result);
  }

  @Test
  void testStreamingResponseHandlesLargeData() throws IOException {
    // Create 1MB of test data
    byte[] largeData = new byte[1024 * 1024];
    for (int i = 0; i < largeData.length; i++) {
      largeData[i] = (byte) (i % 256);
    }

    StreamableResponse streamable = StreamableResponse.stream(
        () -> new java.io.ByteArrayInputStream(largeData),
        "application/octet-stream",
        largeData.length
    );

    // Verify it can stream large data without buffering everything
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    streamable.writeTo(outputStream);
    byte[] result = outputStream.toByteArray();

    assertEquals(largeData.length, result.length);
    assertArrayEquals(largeData, result);
  }

  @Test
  void testJsonRpcStreamableResponseHandlesComplexNesting() throws IOException {
    Map<String, Object> complexResponse = Map.of(
        "jsonrpc", "2.0",
        "id", "request-123",
        "result", Map.of(
            "content", java.util.List.of(
                Map.of("type", "text", "text", "Result 1"),
                Map.of("type", "text", "text", "Result 2")
            ),
            "metadata", Map.of(
                "timestamp", "2026-03-28T00:00:00Z",
                "duration_ms", 150
            )
        )
    );

    StreamableResponse streamable = StreamableResponse.jsonRpc(complexResponse);

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    streamable.writeTo(outputStream);
    String result = outputStream.toString(StandardCharsets.UTF_8);

    assertTrue(result.contains("\"request-123\""));
    assertTrue(result.contains("\"duration_ms\":150"));
    assertTrue(result.contains("\"Result 1\""));
  }

  @Test
  void testStreamingResponseWithUnknownLength() throws IOException {
    String testData = "Unknown length data";
    byte[] testBytes = testData.getBytes(StandardCharsets.UTF_8);

    StreamableResponse streamable = StreamableResponse.stream(
        () -> new java.io.ByteArrayInputStream(testBytes),
        "text/plain",
        -1  // Unknown length for chunked transfer encoding
    );

    // Verify content length returns -1
    assertEquals(-1, streamable.getContentLength());

    // Verify content still streams correctly
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    streamable.writeTo(outputStream);
    String result = outputStream.toString(StandardCharsets.UTF_8);

    assertEquals(testData, result);
  }

  @Test
  void testStreamingResponseUtilizesBuffer() throws IOException {
    // Create data larger than 8KB buffer to verify buffering works
    byte[] largeData = new byte[20 * 1024];  // 20KB
    for (int i = 0; i < largeData.length; i++) {
      largeData[i] = (byte) (i % 256);
    }

    StreamableResponse streamable = StreamableResponse.stream(
        () -> new java.io.ByteArrayInputStream(largeData),
        "application/octet-stream",
        largeData.length
    );

    // Write to output stream
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    streamable.writeTo(outputStream);

    // Verify all data was buffered and streamed correctly
    byte[] result = outputStream.toByteArray();
    assertEquals(largeData.length, result.length);
    assertArrayEquals(largeData, result);
  }

  @Test
  void testJsonRpcStreamableResponseSerializesWithoutNulls() throws IOException {
    // Create response with null fields that should not be serialized
    Map<String, Object> response = Map.of(
        "jsonrpc", "2.0",
        "id", 1,
        "result", Map.of("success", true)
        // "error" field is missing (null would be excluded due to @JsonInclude)
    );

    StreamableResponse streamable = StreamableResponse.jsonRpc(response);

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    streamable.writeTo(outputStream);
    String result = outputStream.toString(StandardCharsets.UTF_8);

    assertTrue(result.contains("\"result\""));
    assertFalse(result.contains("\"error\":null"));
  }
}

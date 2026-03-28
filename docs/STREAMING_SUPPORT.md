# MCP Server Streaming Support Analysis

**Question:** Must MCP server resource responses be streamable HTTP?

**Answer:** ⚠️ **Currently NOT fully supported for large resources**

---

## Current Implementation

### Limitation
The framework currently **buffers the entire resource content in memory** before returning it:

```java
record McpResourceResult(String uri, String content, String mimeType, boolean isError) {}
```

- `content` is a `String` (fully buffered)
- Suitable for: Small to medium files (~1-100MB)
- NOT suitable for: Large files (>100MB) or real-time streaming

### Current Data Flow

```
Resource Handler (Spring bean)
    ↓
DefaultMcpDispatcher.dispatchResource()
    ↓
Serialized to String (entire content loaded)
    ↓
JSON-RPC response (included in response body)
    ↓
Client receives (all at once)
```

---

## Streaming Requirements

According to MCP specification, resources **should support streaming** for:
- Large files (>10MB)
- Real-time data (logs, metrics)
- External sources (database cursors, API responses)

### Why Streaming Matters

| Scenario | Current | Streaming | Benefit |
|----------|---------|-----------|---------|
| 100MB file | ❌ OOM risk | ✅ Safe | Constant memory |
| 1GB dataset | ❌ Fails | ✅ Works | Handles any size |
| Live log | ❌ Delayed | ✅ Immediate | Real-time delivery |
| 1000 requests | ❌ 1GB × 1000 | ✅ 1GB total | 1000x improvement |

---

## How to Implement Streaming

### Option 1: Return InputStrea m (v1.1.0 Roadmap)

```java
@McpResource(uri = "file://large-file")
public InputStream streamLargeFile() {
    return new FileInputStream("/large-file.bin");
}
```

**Pros:**
- Simple for developers
- Framework handles streaming
- Efficient memory usage

**Cons:**
- Requires framework changes
- Resource cleanup needed

### Option 2: Direct HTTP Streaming (v1.2.0 Roadmap)

```java
@McpResource(uri = "stream://events")
public void streamEvents(HttpServletResponse response) throws IOException {
    response.setContentType("application/octet-stream");
    response.setContentLength(-1);  // Chunked encoding

    try (OutputStream out = response.getOutputStream()) {
        for (Event event : eventSource) {
            out.write(event.toBytes());
            out.flush();
        }
    }
}
```

**Pros:**
- Full control over streaming
- Works with any data source
- Efficient

**Cons:**
- More complex
- Developer responsible for streaming

### Option 3: Hybrid Approach (Recommended for v1.1.0)

```java
public interface StreamableResource {
    String getUri();
    String getMimeType();
    InputStream getStream() throws IOException;  // ← Stream support
    String getContent();                          // ← String fallback
}
```

**Logic:**
- Small files (<10MB) → String (current behavior)
- Large files (>10MB) → Stream (new behavior)
- Automatic detection

---

## Recommended Implementation Path

### Phase 1: Enhance McpResourceResult (v1.1.0)

```java
record McpResourceResult(
    String uri,
    String content,           // For small/medium files
    InputStream stream,        // NEW: For large files
    String mimeType,
    boolean isError
) {
    public boolean isStreamable() {
        return stream != null;
    }
}
```

### Phase 2: Update Transport Layer (v1.1.0)

```java
@PostMapping("/resources/{uri}")
public ResponseEntity<?> getResource(@PathVariable String uri) {
    McpResourceResult result = dispatcher.dispatchResource(uri, params);

    if (result.isStreamable()) {
        // Stream response
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(result.mimeType()))
            .body(new InputStreamResource(result.stream()));
    } else {
        // JSON-RPC response (current behavior)
        return ResponseEntity.ok(jsonRpcResponse(result.content()));
    }
}
```

### Phase 3: Documentation & Guidelines (v1.1.0)

```markdown
## Resource Streaming Guidelines

**For files < 10MB:** Return String
```java
@McpResource(uri = "data://config")
public String getConfig() {
    return "...";  // Returned as String in JSON-RPC
}
```

**For files > 10MB:** Return InputStream
```java
@McpResource(uri = "file://large-data")
public InputStream getFile() {
    return new FileInputStream("/large-file.bin");  // Streamed
}
```
```

---

## Workarounds (Current Version v1.0.0)

### For Large Resources

**Option A: Return Reference URL**
```java
@McpResource(uri = "file://large-file")
public String getFile() {
    return "https://storage.example.com/large-file.bin";
}
```
Client downloads separately via URL.

**Option B: Paginate/Chunk Responses**
```java
@McpResource(uri = "data://events?page=1")
public String getEvents(
    @McpInput(name = "page") int page
) {
    return events.stream()
        .skip((page - 1) * 100)
        .limit(100)
        .toJson();
}
```
Client fetches multiple pages.

**Option C: Use Custom Handler**
```java
@RestController
@RequestMapping("/custom")
public class CustomStreamingHandler {
    @GetMapping("/file/{id}")
    public ResponseEntity<InputStreamResource> downloadFile(
        @PathVariable String id
    ) throws IOException {
        // Direct HTTP streaming (not via MCP)
        return ResponseEntity.ok()
            .header("Content-Disposition", "attachment")
            .body(new InputStreamResource(getFileStream(id)));
    }
}
```

---

## Testing Streaming Support

### Performance Metrics

```java
@Test
void testStreamingLargeFile() {
    // Should not cause OutOfMemoryError
    // Should complete in reasonable time
    // Should use constant memory

    InputStream stream = handler.streamFile("1GB-file.bin");

    byte[] buffer = new byte[8192];
    int bytesRead;
    long totalBytes = 0;
    long startTime = System.currentTimeMillis();

    while ((bytesRead = stream.read(buffer)) != -1) {
        totalBytes += bytesRead;
        // Process chunk...
    }

    long elapsed = System.currentTimeMillis() - startTime;
    long throughput = totalBytes / elapsed;  // bytes/ms

    assertTrue(throughput > 100);  // > 100 MB/s
    assertTrue(totalBytes == 1_000_000_000L);
}
```

---

## Recommendations for Users

### If Using v1.0.0

1. **Keep resources small** (<10MB)
2. **Use pagination** for large datasets
3. **Return URLs** for external resources
4. **Monitor memory** during resource access

### For v1.1.0+ (When Available)

1. **Use InputStrea m** return type for large files
2. **Framework handles** streaming automatically
3. **No memory overhead** regardless of file size

---

## Roadmap

### v1.0.0 (Current) ✅
- String-based resource responses
- JSON-RPC resource delivery
- Suitable for < 10MB resources

### v1.1.0 (Q2 2026) 📋
- [ ] Add InputStream support
- [ ] Automatic streaming detection
- [ ] Enhanced docs & examples
- [ ] Chunked transfer encoding

### v1.2.0 (Q3 2026) 📋
- [ ] Direct HTTP streaming option
- [ ] Backpressure handling
- [ ] Range request support
- [ ] Performance benchmarks

---

## Summary

| Question | Answer | Status |
|----------|--------|--------|
| Are MCP resources streamable? | Partially (for <10MB) | ✅ v1.0.0 |
| Is full streaming supported? | No | ❌ Planned v1.1.0 |
| Can I work around this? | Yes, multiple options | ✅ Documented above |
| What's the roadmap? | Streaming in v1.1.0 | 📋 Q2 2026 |
| Should I upgrade? | Only for large files | ⚠️ Case-by-case |

---

## References

- [MCP Specification - Resources](https://modelcontextprotocol.io/spec/basic-server)
- [Spring HttpMessageConverter Documentation](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-config/message-converters.html)
- [Streaming HTTP Responses](https://spring.io/guides/tutorials/spring-rest/)

---

*Last Updated: March 28, 2026*
*Framework Version: 1.0.0*

# Stdio Transport Implementation Guide
**For**: MCP 2025-11-25 Specification Compliance
**Status**: Framework Ready → Ready for Implementation
**Estimated Effort**: 4-6 hours

---

## Overview

This guide details the implementation of Stdio Transport for the MCP 2025-11-25 specification. The project currently achieves 95% compliance with HTTP Streamable Transport fully implemented. Completing Stdio Transport will bring compliance to 100%.

### What Is Stdio Transport?

Stdio transport uses standard input/output (STDIN/STDOUT) streams for local process communication between MCP client and server on the same machine. Key characteristics:

- **Local Only**: STDIN/STDOUT IPC between processes
- **1:1 Communication**: One client per server instance
- **High Performance**: No network overhead
- **Message Format**: Newline-delimited JSON-RPC 2.0
- **Typical Use**: Embedded servers, command-line tools, IDE integration

### Why Implement Stdio Transport?

1. **Specification Compliance**: MCP spec defines Stdio as primary transport for local servers
2. **IDE Integration**: Required for proper Claude Code and IDE plugin integration
3. **Testing**: Essential for standalone server testing
4. **Performance**: Optimal for local machine integration (no HTTP overhead)

---

## Architecture

### Current State: Framework Ready ✅

**File**: `src/main/java/com/raynermendez/spring_boot_mcp_companion/transport/StdioTransport.java`

**What's Implemented**:
- ✅ Class structure and interface implementation
- ✅ Skeleton methods for start/stop
- ✅ Helper methods: getTransportType(), getDescription(), isRunning()
- ✅ TODO comments marking implementation points

**What's Missing**:
- ❌ STDIN reader thread
- ❌ STDOUT writer
- ❌ Message parsing (newline-delimited JSON)
- ❌ Session management
- ❌ Request routing to MCP dispatcher

### Message Flow Design

```
Client (STDIN)
    ↓
[Reader Thread]
    ↓ (parse newline-delimited JSON)
[Message Queue]
    ↓ (dispatch)
[McpDispatcher]
    ↓ (process)
[Response]
    ↓
[STDOUT Writer]
    ↓
Server (STDOUT)
```

### Spring Boot Integration

The Stdio transport should work in Spring Boot context while being independent of Spring Web:

```java
// Instead of HTTP server, use Spring's lifecycle
@Component
public class StdioTransport implements McpTransport, ApplicationRunner {
    @Override
    public void run(ApplicationArguments args) throws Exception {
        start();
        // Keep process alive for STDIN reading
    }
}
```

---

## Implementation Steps

### Step 1: Set Up Reader Thread

**Goal**: Read from STDIN and parse newline-delimited JSON

```java
// Fields to add to StdioTransport
private BufferedReader stdinReader;
private Thread readerThread;
private final BlockingQueue<Map<String, Object>> messageQueue = new LinkedBlockingQueue<>();
private final ObjectMapper objectMapper = new ObjectMapper();

// Implement reader
private void startReaderThread() {
    readerThread = new Thread(() -> {
        try {
            String line;
            while (running && (line = stdinReader.readLine()) != null) {
                try {
                    Map<String, Object> message = objectMapper.readValue(line, Map.class);
                    messageQueue.put(message);
                } catch (JsonProcessingException e) {
                    logger.error("Failed to parse JSON from STDIN: {}", e.getMessage());
                }
            }
        } catch (IOException e) {
            if (running) {
                logger.error("Error reading from STDIN: {}", e.getMessage());
            }
        }
    });
    readerThread.setDaemon(false);
    readerThread.setName("MCP-StdioReader");
    readerThread.start();
}
```

**Key Points**:
- Use `BufferedReader` for line-by-line reading
- Use `BlockingQueue` for thread-safe message buffering
- Catch and log JSON parse errors
- Stop when EOF or `running` becomes false

### Step 2: Set Up Writer and Message Dispatcher

**Goal**: Process messages from queue and route to MCP dispatcher

```java
// Fields to add
private PrintWriter stdoutWriter;
private final McpDispatcher dispatcher;

// Constructor injection
public StdioTransport(McpDispatcher dispatcher, SseNotificationManager notificationManager) {
    this.dispatcher = dispatcher;
    this.notificationManager = notificationManager;
}

// Start method update
@Override
public void start() throws IOException {
    synchronized (lock) {
        logger.info("Starting Stdio Transport");

        // Initialize readers/writers
        this.stdinReader = new BufferedReader(new InputStreamReader(System.in));
        this.stdoutWriter = new PrintWriter(new OutputStreamWriter(System.out), true);

        running = true;
        startReaderThread();
        startMessageDispatcher();

        logger.info("Stdio Transport started successfully");
    }
}

// Message processor
private void startMessageDispatcher() {
    Thread dispatcherThread = new Thread(() -> {
        while (running) {
            try {
                Map<String, Object> message = messageQueue.poll(100, TimeUnit.MILLISECONDS);
                if (message != null) {
                    processMessage(message);
                }
            } catch (InterruptedException e) {
                if (running) {
                    logger.debug("Message dispatcher interrupted");
                }
            }
        }
    });
    dispatcherThread.setDaemon(false);
    dispatcherThread.setName("MCP-StdioDispatcher");
    dispatcherThread.start();
}
```

### Step 3: Implement Request Processing

**Goal**: Route JSON-RPC messages to McpDispatcher and send responses

```java
private void processMessage(Map<String, Object> message) {
    try {
        String method = (String) message.get("method");
        Object id = message.get("id");
        Map<String, Object> params = (Map<String, Object>) message.getOrDefault("params", Map.of());

        logger.debug("Processing Stdio message: method={}, id={}", method, id);

        // Create session if needed (Stdio is 1:1, so use constant sessionId)
        String sessionId = "stdio-session";

        // Dispatch based on method (same logic as HTTP controller)
        Object result = null;

        if ("initialize".equals(method)) {
            result = handleInitialize(params, message);
        } else if ("tools/list".equals(method)) {
            result = handleToolsList();
        } else if ("tools/call".equals(method)) {
            result = handleToolsCall(params);
        }
        // ... etc for other methods

        // Send response
        if (id != null) { // Only send response if request had id
            Map<String, Object> response = result instanceof Map && ((Map)result).containsKey("error")
                ? (Map<String, Object>) result
                : Map.of(
                    "jsonrpc", "2.0",
                    "id", id,
                    "result", result
                );
            sendMessage(sessionId, response);
        }
    } catch (Exception e) {
        logger.error("Error processing message: {}", e.getMessage(), e);
    }
}

@Override
public void sendMessage(String sessionId, Map<String, Object> message) throws IOException {
    String json = objectMapper.writeValueAsString(message);
    stdoutWriter.println(json);
    stdoutWriter.flush();
}

@Override
public void broadcastMessage(Map<String, Object> message) throws IOException {
    // Stdio is 1:1, so broadcast = send to single session
    sendMessage("stdio-session", message);
}
```

### Step 4: Implement Session Management

**Goal**: Create and manage single session for Stdio connection

```java
// Add to McpSessionManager or create StdioSessionManager
private McpSession stdioSession;

private void createStdioSession(JsonRpcRequest request) {
    Map<String, Object> params = (Map<String, Object>) request.params();
    String protocolVersion = (String) params.get("protocolVersion");
    Map<String, Object> clientInfo = (Map<String, Object>) params.get("clientInfo");

    // Use same capability declaration as HTTP
    Map<String, Object> capabilities = Map.of(
        "tools", Map.of("listChanged", true),
        "resources", Map.of("subscribe", true, "listChanged", true),
        "prompts", Map.of("listChanged", true)
    );

    this.stdioSession = sessionManager.createSession(
        protocolVersion,
        clientInfo,
        capabilities
    );

    logger.info("Created Stdio session: {}", stdioSession.getSessionId());
}
```

### Step 5: Implement Stop and Cleanup

**Goal**: Gracefully shutdown Stdio transport

```java
@Override
public void stop() throws IOException {
    synchronized (lock) {
        if (!running) return;

        logger.info("Stopping Stdio Transport");
        running = false;

        try {
            // Signal reader thread to stop
            if (readerThread != null && readerThread.isAlive()) {
                readerThread.join(5000); // Wait max 5 seconds
            }

            // Close streams
            if (stdinReader != null) stdinReader.close();
            if (stdoutWriter != null) {
                stdoutWriter.flush();
                stdoutWriter.close();
            }

            // Close session
            if (stdioSession != null) {
                sessionManager.closeSession(stdioSession.getSessionId());
            }

            logger.info("Stdio Transport stopped successfully");
        } catch (Exception e) {
            logger.error("Error during Stdio Transport shutdown: {}", e.getMessage(), e);
            throw new IOException("Failed to stop Stdio transport", e);
        }
    }
}
```

---

## Testing Strategy

### Unit Tests

```java
// Example test structure
@Test
void testStdioTransportStartStop() throws IOException {
    StdioTransport transport = new StdioTransport(dispatcher, notificationManager);
    assertFalse(transport.isRunning());

    transport.start();
    assertTrue(transport.isRunning());

    transport.stop();
    assertFalse(transport.isRunning());
}

@Test
void testJsonRpcMessageParsing() throws Exception {
    // Verify newline-delimited JSON parsing
    String input = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}\n";
    // Simulate STDIN with input
    // Verify message queue contains parsed message
}
```

### Integration Tests

Create a test client that communicates via Stdio:

```java
// Test helper: SimpleStdioClient
class SimpleStdioClient {
    private BufferedWriter writer;
    private BufferedReader reader;
    private ObjectMapper mapper = new ObjectMapper();

    public void sendMessage(Map<String, Object> message) throws IOException {
        String json = mapper.writeValueAsString(message);
        writer.write(json + "\n");
        writer.flush();
    }

    public Map<String, Object> receiveMessage() throws IOException {
        String line = reader.readLine();
        return mapper.readValue(line, Map.class);
    }
}

@Test
void testStdioInitializeFlow() throws Exception {
    // Start transport in separate thread
    // Send initialize message
    // Verify response contains capabilities
}
```

### Manual Testing

```bash
# Start server with Stdio transport
java -cp target/spring-boot-mcp-companion.jar \
    com.raynermendez.spring_boot_mcp_companion.Application

# Send request via STDIN
echo '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","clientInfo":{"name":"test"}}}' | java ...

# Verify STDOUT response contains initialize response
```

---

## Integration Points

### McpDispatcher Integration

The Stdio transport needs to use the same `McpDispatcher` as HTTP transport:

```java
// Inject in constructor
public StdioTransport(
    McpDispatcher dispatcher,
    McpDefinitionRegistry registry,
    McpSessionManager sessionManager,
    SseNotificationManager notificationManager,
    HttpStatusMapper statusMapper
) {
    this.dispatcher = dispatcher;
    this.registry = registry;
    // ... etc
}

// Reuse HTTP handler logic where possible
private Object handleToolsCall(Map<String, Object> params) {
    McpToolResult result = dispatcher.dispatchTool(
        (String) params.get("name"),
        (Map<String, Object>) params.get("arguments")
    );
    // ... format response
}
```

### Notification Handling

In Stdio transport, send notifications via STDOUT:

```java
@Override
public void broadcastMessage(Map<String, Object> message) throws IOException {
    // Send as notification (no id field)
    sendMessage("stdio-session", message);
}

// When tools change, notify via STDOUT
public void notifyToolsChanged() throws IOException {
    Map<String, Object> notification = Map.of(
        "jsonrpc", "2.0",
        "method", "tools/list_changed",
        "params", Map.of()
    );
    broadcastMessage(notification);
}
```

---

## Configuration Options

Add configuration properties for Stdio transport:

```yaml
mcp:
  transport:
    type: stdio  # or "http"
    stdio:
      buffer-size: 8192
      read-timeout-ms: 5000
      response-timeout-ms: 30000
```

---

## Debugging Tips

1. **Enable Debug Logging**:
   ```yaml
   logging:
     level:
       com.raynermendez.spring_boot_mcp_companion.transport: DEBUG
   ```

2. **Use Logging Instead of printLn()**:
   All debug output should go to logger, not System.out

3. **Test Message Format**:
   - Each message must be valid JSON
   - Each message must end with newline: `\n`
   - Format: `<JSON-RPC message>\n`

4. **Handle Edge Cases**:
   - Empty lines
   - Partial messages
   - Connection closure
   - Very large messages

---

## Common Pitfalls to Avoid

1. **STDOUT Buffering**: Always flush after writing to ensure client sees message immediately
2. **Thread Safety**: Use synchronized blocks for shared state
3. **Error Handling**: Never let exceptions kill the reader thread
4. **Session Cleanup**: Ensure session is properly closed on shutdown
5. **Message Framing**: Every message must have exactly one newline separator

---

## Expected Behavior After Implementation

1. **Server Startup**: Process starts and reads from STDIN
2. **Client Connection**: Client sends initialize message via STDIN
3. **Initialize Response**: Server responds with capabilities via STDOUT
4. **Normal Operation**: Client sends requests, server responds via STDOUT
5. **Notifications**: Server can send notifications at any time via STDOUT
6. **Graceful Shutdown**: Process exits cleanly on EOF

---

## Files to Modify

- `StdioTransport.java` - Main implementation
- `TransportFactory.java` - (Create) For selecting transport type
- `McpTransportConfiguration.java` - (Create) For transport configuration
- `application.yaml` - Add transport configuration properties
- `StdioTransportTest.java` - (Create) Unit and integration tests

---

## Next Steps

1. Clone the current repository with 95% compliance
2. Follow this guide to implement Stdio transport
3. Run unit tests to verify functionality
4. Run integration tests with test client
5. Manual testing with actual Stdio client
6. Verify all 51 existing tests still pass
7. Commit with message: "feat: Implement Stdio Transport for 100% MCP compliance"
8. Final verification against MCP 2025-11-25 specification

---

## Resources

- **MCP 2025-11-25 Specification**: https://modelcontextprotocol.io/specification
- **JSON-RPC 2.0 Specification**: https://www.jsonrpc.org/specification
- **Java JSON Processing**: Jackson ObjectMapper documentation
- **Threading Best Practices**: Java concurrency utilities (BlockingQueue, Thread)

---

## Questions to Consider During Implementation

1. How to handle very large JSON messages?
2. What should timeout behavior be?
3. How to differentiate between client disconnect and process exit?
4. Should we validate message format before queueing?
5. What error messages should go to STDOUT vs logs?

The framework is in place. Following this guide should result in complete Stdio transport implementation within 4-6 hours.

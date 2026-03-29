# HTTP Transport Implementation Guide

## Overview

The Spring Boot MCP Companion implements the MCP (Model Context Protocol) 2025-11-25 specification using HTTP Streamable Transport with Server-Sent Events for notifications.

## Single Unified Endpoint

All MCP operations go through a single endpoint:

```
Endpoint: POST/GET /mcp
```

### Request Methods

**POST Requests** - JSON-RPC 2.0 request/response
```http
POST /mcp HTTP/1.1
Content-Type: application/json
MCP-Protocol-Version: 2025-11-25
MCP-Session-Id: <session-uuid>

{
  "jsonrpc": "2.0",
  "id": "request-id",
  "method": "tools/list",
  "params": {}
}
```

**GET Requests** - Server-Sent Events (SSE) streaming
```http
GET /mcp HTTP/1.1
Accept: text/event-stream
MCP-Protocol-Version: 2025-11-25
MCP-Session-Id: <session-uuid>
```

---

## Session Lifecycle

### 1. Initialize (Create Session)

```bash
POST /mcp HTTP/1.1
Content-Type: application/json

{
  "jsonrpc": "2.0",
  "id": "1",
  "method": "initialize",
  "params": {
    "protocolVersion": "2025-11-25",
    "clientInfo": {
      "name": "my-client",
      "version": "1.0.0"
    }
  }
}
```

**Response**:
```http
HTTP/1.1 200 OK
Content-Type: application/json
MCP-Protocol-Version: 2025-11-25
MCP-Session-Id: f47ac10b-58cc-4372-a567-0e02b2c3d479

{
  "jsonrpc": "2.0",
  "id": "1",
  "result": {
    "protocolVersion": "2025-11-25",
    "serverInfo": {
      "name": "spring-boot-mcp-companion",
      "version": "1.0.0"
    },
    "capabilities": {
      "tools": {},
      "resources": {"subscribe": true},
      "prompts": {}
    }
  }
}
```

### 2. Use Session

All subsequent requests must include the session ID from the initialize response:

```bash
POST /mcp HTTP/1.1
Content-Type: application/json
MCP-Session-Id: f47ac10b-58cc-4372-a567-0e02b2c3d479

{
  "jsonrpc": "2.0",
  "id": "2",
  "method": "tools/list",
  "params": {}
}
```

### 3. Session Timeout

Sessions timeout after 5 minutes of inactivity. Accessing the session updates the last access time.

**Expired Session Response**:
```http
HTTP/1.1 404 Not Found
Content-Type: application/json

{
  "jsonrpc": "2.0",
  "id": "2",
  "error": {
    "code": -32000,
    "message": "Session not found or expired"
  }
}
```

---

## Supported Methods

All methods are called via the unified `/mcp` endpoint with the `method` field in the JSON-RPC request.

### Tool Operations

#### tools/list
List all available tools.

```json
{
  "jsonrpc": "2.0",
  "id": "1",
  "method": "tools/list",
  "params": {}
}
```

#### tools/call
Execute a tool.

```json
{
  "jsonrpc": "2.0",
  "id": "2",
  "method": "tools/call",
  "params": {
    "name": "tool-name",
    "arguments": {
      "arg1": "value1"
    }
  }
}
```

### Resource Operations

#### resources/list
List all available resources.

```json
{
  "jsonrpc": "2.0",
  "id": "1",
  "method": "resources/list",
  "params": {}
}
```

#### resources/read
Read a specific resource.

```json
{
  "jsonrpc": "2.0",
  "id": "2",
  "method": "resources/read",
  "params": {
    "uri": "resource://my-resource"
  }
}
```

#### resources/subscribe
Subscribe to resource updates.

```json
{
  "jsonrpc": "2.0",
  "id": "3",
  "method": "resources/subscribe",
  "params": {
    "uri": "resource://my-resource"
  }
}
```

#### resources/unsubscribe
Unsubscribe from resource updates.

```json
{
  "jsonrpc": "2.0",
  "id": "4",
  "method": "resources/unsubscribe",
  "params": {
    "uri": "resource://my-resource"
  }
}
```

### Prompt Operations

#### prompts/list
List all available prompts.

```json
{
  "jsonrpc": "2.0",
  "id": "1",
  "method": "prompts/list",
  "params": {}
}
```

#### prompts/get
Get a specific prompt template.

```json
{
  "jsonrpc": "2.0",
  "id": "2",
  "method": "prompts/get",
  "params": {
    "name": "prompt-name",
    "arguments": {
      "arg1": "value1"
    }
  }
}
```

### Server Operations

#### server/info
Get server information.

```json
{
  "jsonrpc": "2.0",
  "id": "1",
  "method": "server/info",
  "params": {}
}
```

---

## Server-Sent Events (SSE)

Connect to receive real-time notifications from the server.

### Connect to SSE Stream

```bash
curl -X GET 'http://localhost:8080/mcp' \
  -H 'Accept: text/event-stream' \
  -H 'MCP-Session-Id: <session-id>'
```

### Event Formats

The server sends notifications as Server-Sent Events in JSON-RPC notification format (no `id` field):

```
data: {"jsonrpc":"2.0","method":"tools/list_changed","params":{}}
data: {"jsonrpc":"2.0","method":"resources/updated","params":{"uri":"resource://..."}}
```

### Notification Types

- **tools/list_changed** - Sent when tool list changes
- **resources/list_changed** - Sent when resource list changes
- **resources/updated** - Sent when a specific resource content changes
- **prompts/list_changed** - Sent when prompt list changes

---

## HTTP Status Codes

The implementation properly maps JSON-RPC errors to HTTP status codes:

| HTTP Status | Scenario | JSON-RPC Code |
|-------------|----------|---------------|
| 200 OK | Success | (no error) |
| 202 Accepted | Subscription created | (no error) |
| 400 Bad Request | Parse/invalid request | -32700, -32600, -32602 |
| 404 Not Found | Method not found / Session expired | -32601 / -32000 |
| 403 Forbidden | Invalid Origin header | (security) |
| 500 Internal Error | Server error | -32603 |

---

## HTTP Headers

### Request Headers (Optional)

```http
Content-Type: application/json
Accept: application/json | text/event-stream
Origin: http://client.example.com
MCP-Session-Id: <session-uuid>
```

### Response Headers (Always Set)

```http
Content-Type: application/json | text/event-stream
MCP-Protocol-Version: 2025-11-25
MCP-Session-Id: <session-uuid>
```

---

## Error Handling

All errors follow JSON-RPC 2.0 error format:

### Error Response Format

```json
{
  "jsonrpc": "2.0",
  "id": "request-id",
  "error": {
    "code": -32000,
    "message": "Error description"
  }
}
```

### Common Error Codes

| Code | Message | Meaning |
|------|---------|---------|
| -32700 | Parse error | Invalid JSON |
| -32600 | Invalid Request | Missing fields |
| -32601 | Method not found | Unknown method |
| -32602 | Invalid params | Bad parameters |
| -32603 | Internal error | Server error |
| -32000 | Server error | Session expired, etc |

---

## Implementation Details

### Controller

**Class**: `McpHttpController`
**Location**: `transport/McpHttpController.java`
**Responsibility**: Handle HTTP requests, route to handlers, return responses

### Session Management

**Classes**:
- `McpSession` - Session state holder
- `McpSessionManager` - Session lifecycle management

**Features**:
- UUID-based session IDs
- 5-minute inactivity timeout
- Subscription tracking
- Immutable data copies

### Status Code Mapping

**Class**: `HttpStatusMapper`
**Location**: `transport/HttpStatusMapper.java`
**Responsibility**: Map JSON-RPC error codes to HTTP status codes

### Notifications

**Class**: `SseNotificationManager`
**Location**: `notification/SseNotificationManager.java`
**Features**:
- SSE connection management
- Broadcast and targeted notifications
- Automatic cleanup on disconnect

---

## Testing

Run the comprehensive test suite to verify compliance:

```bash
./mvnw test -Dtest=McpSessionManagerTest,HttpStatusCodeTest,SseNotificationManagerTest,McpSessionTest
```

Tests verify:
- ✅ Session creation and lifecycle
- ✅ HTTP status code mapping
- ✅ SSE connection management
- ✅ Notification delivery
- ✅ Error handling

---

## Configuration

**Protocol Version** (in `application.properties`):
```properties
mcp.server.protocol-version=2025-11-25
mcp.server.session-timeout-minutes=5
```

---

## Performance Considerations

- **Stateless Design**: Each HTTP request is independent
- **In-Memory Sessions**: Fast access, no database overhead
- **Lazy Cleanup**: Expired sessions cleaned on access
- **Direct Notification**: SSE delivers directly to connected clients

---

## Security Considerations

- **Session IDs**: UUID-based, cryptographically random
- **Origin Validation**: Prevents DNS rebinding attacks
- **Error Handling**: Sanitized error messages
- **Session Timeout**: Automatic cleanup of inactive sessions

---

## Deployment

### Requirements
- Java 17+
- Spring Boot 3.0+
- MCP 2025-11-25 compatible clients

### Running

```bash
./mvnw spring-boot:run
# Server starts on http://localhost:8080
# MCP endpoint: http://localhost:8080/mcp
```

### Docker

```dockerfile
FROM eclipse-temurin:17-jdk
COPY . /app
WORKDIR /app
RUN ./mvnw clean package -DskipTests
EXPOSE 8080
CMD ["java", "-jar", "target/spring-boot-mcp-companion-core-1.0.0.jar"]
```

---

## Troubleshooting

### Session Expired

**Problem**: Request returns 404 "Session not found or expired"
**Solution**: Call `initialize` again to create new session

### Wrong Content-Type

**Problem**: Request fails with parse error
**Solution**: Ensure `Content-Type: application/json` header is set

### No Notifications

**Problem**: SSE stream opens but no events received
**Solution**: Make sure to use `GET /mcp` with `Accept: text/event-stream`

### Method Not Found

**Problem**: Request returns 404 for valid method name
**Solution**: Check method name matches exact format (e.g., `tools/list` not `tools/getList`)

---

## References

- [MCP Specification](https://modelcontextprotocol.io/specification/latest)
- [Server Concepts](https://modelcontextprotocol.io/docs/learn/server-concepts)
- [Architecture Guide](https://modelcontextprotocol.io/docs/learn/architecture)


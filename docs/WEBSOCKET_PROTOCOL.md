# WebSocket Protocol Implementation

## Overview

This document describes the WebSocket-based MCP protocol implementation that provides full compliance with the MCP 2025-06-18 specification.

## Connection Lifecycle

```
┌─────────────────────────────────────────────────────────────────┐
│                    WebSocket Connection                         │
└─────────────────────────────────────────────────────────────────┘
                           │
                           ▼
                    ┌───────────────┐
                    │  INIT State   │ (Connection established)
                    └───────┬───────┘
                           │
                    (client must send initialize)
                           │
                           ▼
                  ┌──────────────────┐
                  │ INITIALIZING     │ (Server processing init)
                  └──────────┬───────┘
                            │
                  (server waits for notifications/initialized)
                            │
                            ▼
                        ┌────────────┐
                        │ READY      │ (All operations allowed)
                        └──────┬─────┘
                               │
                 (client can call tools, read resources, etc.)
                               │
                               ▼
                            ┌────────┐
                            │ CLOSED │ (Connection terminated)
                            └────────┘
```

## Message Format

All messages use JSON-RPC 2.0 format:

### Request (from client to server)
```json
{
  "jsonrpc": "2.0",
  "id": "unique-request-id",
  "method": "tools/list",
  "params": {}
}
```

### Response (from server to client)
```json
{
  "jsonrpc": "2.0",
  "id": "unique-request-id",
  "result": {
    "tools": [...]
  }
}
```

### Error Response
```json
{
  "jsonrpc": "2.0",
  "id": "unique-request-id",
  "error": {
    "code": -32601,
    "message": "Method not found"
  }
}
```

### Notification (server to client, no id)
```json
{
  "jsonrpc": "2.0",
  "method": "tools/list_changed",
  "params": {}
}
```

## Core Methods

### initialize
**Sent by**: Client
**State**: INIT → INITIALIZING
**Response**: Protocol version, server info, capabilities

```json
{
  "jsonrpc": "2.0",
  "id": "init-1",
  "method": "initialize",
  "params": {
    "protocolVersion": "2025-06-18",
    "clientInfo": {
      "name": "my-client",
      "version": "1.0.0"
    }
  }
}
```

### notifications/initialized
**Sent by**: Client (notification)
**State**: INITIALIZING → READY
**Purpose**: Signals client is ready to receive notifications

```json
{
  "jsonrpc": "2.0",
  "method": "notifications/initialized",
  "params": {}
}
```

### tools/list
**Sent by**: Client
**State**: READY only
**Response**: Array of available tools

```json
{
  "jsonrpc": "2.0",
  "id": "tools-1",
  "method": "tools/list",
  "params": {}
}
```

### tools/call
**Sent by**: Client
**State**: READY only
**Purpose**: Execute a tool

```json
{
  "jsonrpc": "2.0",
  "id": "tool-call-1",
  "method": "tools/call",
  "params": {
    "name": "tool_name",
    "arguments": {
      "arg1": "value1",
      "arg2": "value2"
    }
  }
}
```

### resources/list
**Sent by**: Client
**State**: READY only
**Response**: Array of available resources

```json
{
  "jsonrpc": "2.0",
  "id": "res-list-1",
  "method": "resources/list",
  "params": {}
}
```

### resources/read
**Sent by**: Client
**State**: READY only
**Purpose**: Read resource content

```json
{
  "jsonrpc": "2.0",
  "id": "res-read-1",
  "method": "resources/read",
  "params": {
    "uri": "file://path/to/resource"
  }
}
```

### resources/subscribe
**Sent by**: Client
**State**: READY only
**Purpose**: Subscribe to resource change notifications

```json
{
  "jsonrpc": "2.0",
  "id": "res-sub-1",
  "method": "resources/subscribe",
  "params": {
    "uri": "file://path/to/resource"
  }
}
```

### resources/unsubscribe
**Sent by**: Client
**State**: READY only
**Purpose**: Unsubscribe from resource change notifications

```json
{
  "jsonrpc": "2.0",
  "id": "res-unsub-1",
  "method": "resources/unsubscribe",
  "params": {
    "uri": "file://path/to/resource"
  }
}
```

### prompts/list
**Sent by**: Client
**State**: READY only
**Response**: Array of available prompts

```json
{
  "jsonrpc": "2.0",
  "id": "prompts-1",
  "method": "prompts/list",
  "params": {}
}
```

### prompts/get
**Sent by**: Client
**State**: READY only
**Purpose**: Retrieve prompt template

```json
{
  "jsonrpc": "2.0",
  "id": "prompt-get-1",
  "method": "prompts/get",
  "params": {
    "name": "prompt_name",
    "arguments": {
      "arg1": "value1"
    }
  }
}
```

### server/info
**Sent by**: Client
**State**: READY only
**Response**: Server metadata

```json
{
  "jsonrpc": "2.0",
  "id": "info-1",
  "method": "server/info",
  "params": {}
}
```

## Notifications (Server to Client)

Notifications are messages without an `id` field. Clients cannot respond to notifications.

### tools/list_changed
Sent when available tools change

```json
{
  "jsonrpc": "2.0",
  "method": "tools/list_changed",
  "params": {}
}
```

### resources/list_changed
Sent when available resources change

```json
{
  "jsonrpc": "2.0",
  "method": "resources/list_changed",
  "params": {}
}
```

### prompts/list_changed
Sent when available prompts change

```json
{
  "jsonrpc": "2.0",
  "method": "prompts/list_changed",
  "params": {}
}
```

### resources/updated
Sent when a subscribed resource is updated

```json
{
  "jsonrpc": "2.0",
  "method": "resources/updated",
  "params": {
    "uri": "file://path/to/resource"
  }
}
```

## Error Codes

| Code | Meaning |
|------|---------|
| -32700 | Parse error |
| -32600 | Invalid request |
| -32601 | Method not found |
| -32602 | Invalid parameters |
| -32603 | Internal error |
| -32000 | Server error |

## Connection Management

### Timeouts
- Connection idle timeout: 5 minutes (configurable)
- Request timeout: 30 seconds (configurable)

### Keep-Alive
- Ping/Pong frames: Every 30 seconds (automatic Spring WebSocket)

### Backpressure
- Large tool results are chunked automatically
- Client should implement backpressure handling

## Security

### Authentication
- Uses Spring Security configuration
- Credentials passed in WebSocket handshake headers
- Per-connection auth context maintained

### Authorization
- Tool execution enforces method-level authorization
- Resource access checks per operation
- Prompt access restricted by configuration

## Performance

### Concurrent Connections
- Fully concurrent, thread-safe implementation
- ConcurrentHashMap for connection management
- No global locks on message processing

### Subscriptions
- Efficient O(1) lookup for subscribed connections
- Broadcast notifications to all subscribers
- Minimal memory overhead per subscription

## Configuration

```yaml
mcp:
  server:
    enabled: true
    name: spring-boot-mcp-companion
    version: 1.0.0
    base-path: /mcp
    protocol-version: 2025-06-18

server:
  port: 8080
  servlet:
    context-path: /
```

## Debugging

### Enable Debug Logging
```yaml
logging:
  level:
    com.raynermendez.spring_boot_mcp_companion: DEBUG
    org.springframework.web.socket: DEBUG
```

### Monitor Connections
```bash
curl http://localhost:8080/actuator/metrics
```

## Examples

See the Postman collection at `postman/MCP-Agent-Simulator.postman_collection.json` for complete examples.

## Specification References

- [MCP Overview](https://modelcontextprotocol.io/docs/getting-started/intro)
- [Architecture](https://modelcontextprotocol.io/docs/learn/architecture)
- [Server Concepts](https://modelcontextprotocol.io/docs/learn/server-concepts)
- [Building Clients](https://modelcontextprotocol.io/docs/develop/build-client)

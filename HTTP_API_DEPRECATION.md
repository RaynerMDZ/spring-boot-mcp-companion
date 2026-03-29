# HTTP REST API - Deprecation Notice

## Status: DEPRECATED (Backward Compatibility Only)

The HTTP REST API provided by `McpTransportController` is **deprecated** in favor of the WebSocket-based MCP protocol implementation.

**Current Version**: HTTP API maintained for backward compatibility
**Recommended Version**: WebSocket MCP Protocol (see [WebSocket Migration Guide](#websocket-migration-guide))

## Why Deprecation?

The HTTP REST API violates core MCP specification requirements:

1. **Stateless Design**: HTTP is fundamentally stateless, but MCP requires persistent, stateful connections
2. **No Connection Lifecycle**: HTTP lacks the INIT → INITIALIZING → READY → CLOSED state flow required by MCP
3. **No Notifications**: HTTP request/response model cannot support real-time notifications from server to client
4. **No Subscriptions**: Resource subscriptions require persistent connections to be efficient

The new WebSocket-based implementation correctly follows the MCP 2025-06-18 specification.

## HTTP Endpoints (Deprecated)

| Endpoint | Method | Status | Replacement |
|----------|--------|--------|-------------|
| `/mcp/initialize` | POST | ⚠️ Deprecated | WebSocket: Initialize handshake |
| `/mcp/server-info` | GET | ⚠️ Deprecated | WebSocket: Server info in initialize response |
| `/mcp/tools/list` | POST | ⚠️ Deprecated | WebSocket: `tools/list` method |
| `/mcp/tools/call` | POST | ⚠️ Deprecated | WebSocket: `tools/call` method |
| `/mcp/resources/list` | POST | ⚠️ Deprecated | WebSocket: `resources/list` method |
| `/mcp/resources/read` | POST | ⚠️ Deprecated | WebSocket: `resources/read` method |
| `/mcp/prompts/list` | POST | ⚠️ Deprecated | WebSocket: `prompts/list` method |
| `/mcp/prompts/get` | POST | ⚠️ Deprecated | WebSocket: `prompts/get` method |
| `/mcp/sampling/createMessage` | POST | ❌ Removed | WebSocket: Sampling (when implemented) |
| `/mcp/logging/create` | POST | ❌ Removed | WebSocket: Logging notifications (when implemented) |
| `/mcp/elicitation/create` | POST | ❌ Removed | WebSocket: Elicitation (when implemented) |

**Legend**:
- ⚠️ Deprecated: Still functional for backward compatibility, will be removed in v2.0.0
- ❌ Removed: Not available in current version, use WebSocket instead

## WebSocket Migration Guide

### Step 1: Connect to WebSocket Endpoint

```javascript
const ws = new WebSocket('ws://localhost:8080/mcp/connect');

ws.onopen = () => {
  console.log('Connected to MCP server');
};

ws.onmessage = (event) => {
  const message = JSON.parse(event.data);
  console.log('Received:', message);
};

ws.onerror = (error) => {
  console.error('WebSocket error:', error);
};
```

### Step 2: Send Initialize Request

```javascript
const initRequest = {
  "jsonrpc": "2.0",
  "id": "init-1",
  "method": "initialize",
  "params": {
    "protocolVersion": "2025-06-18",
    "clientInfo": {
      "name": "my-mcp-client",
      "version": "1.0.0"
    }
  }
};

ws.send(JSON.stringify(initRequest));

// Receive initialize response:
// {
//   "jsonrpc": "2.0",
//   "id": "init-1",
//   "result": {
//     "protocolVersion": "2025-06-18",
//     "serverInfo": {...},
//     "capabilities": {...}
//   }
// }
```

### Step 3: Send notifications/initialized

```javascript
const readyNotification = {
  "jsonrpc": "2.0",
  "method": "notifications/initialized",
  "params": {}
};

ws.send(JSON.stringify(readyNotification));
```

### Step 4: Make Requests

```javascript
// Example: List tools
const listToolsRequest = {
  "jsonrpc": "2.0",
  "id": "tools-list-1",
  "method": "tools/list",
  "params": {}
};

ws.send(JSON.stringify(listToolsRequest));

// Receive response:
// {
//   "jsonrpc": "2.0",
//   "id": "tools-list-1",
//   "result": {
//     "tools": [...]
//   }
// }
```

### Step 5: Handle Notifications

```javascript
ws.onmessage = (event) => {
  const message = JSON.parse(event.data);

  // Check if it's a notification (no "id" field)
  if (!message.id && message.method) {
    console.log('Received notification:', message.method);

    if (message.method === 'tools/list_changed') {
      // Refresh tools list
    } else if (message.method === 'resources/list_changed') {
      // Refresh resources list
    }
  } else if (message.id) {
    // Handle response to our request
    console.log('Response to request', message.id);
  }
};
```

## Breaking Changes

When migrating from HTTP to WebSocket:

### Before (HTTP)
```javascript
// Initialize
POST /mcp/initialize
body: { protocolVersion: "2025-06-18", clientInfo: {...} }
response: { protocolVersion, serverInfo, capabilities }

// List tools
POST /mcp/tools/list
body: {}
response: { tools: [...] }
```

### After (WebSocket)
```javascript
// Connect
ws://localhost:8080/mcp/connect

// Initialize (over WebSocket)
send: { jsonrpc: "2.0", id: "1", method: "initialize", params: {...} }
receive: { jsonrpc: "2.0", id: "1", result: {...} }

// Send initialized
send: { jsonrpc: "2.0", method: "notifications/initialized", params: {} }

// List tools (over WebSocket)
send: { jsonrpc: "2.0", id: "2", method: "tools/list", params: {} }
receive: { jsonrpc: "2.0", id: "2", result: {...} }

// Receive notifications
receive: { jsonrpc: "2.0", method: "tools/list_changed", params: {} }
```

## Timeline

- **Current** (v1.x): Both HTTP and WebSocket available, HTTP deprecated
- **v2.0.0** (planned): HTTP REST API removed, WebSocket only
- **Migration period**: 6 months from v1.3.0 release

## Support

For questions about migration or WebSocket usage:
1. Check [WebSocket examples](../postman/README.md)
2. Review [MCP Specification](https://modelcontextprotocol.io/docs/develop/build-client)
3. Open an issue on GitHub

## Configuration

### To disable HTTP API (not recommended for backward compatibility)

```yaml
mcp:
  server:
    enabled: true
  transport:
    http:
      enabled: false  # Disables HTTP REST API
    websocket:
      enabled: true   # Enables WebSocket transport
```

## FAQ

### Q: Can I still use the HTTP API?
**A**: Yes, it's still functional for backward compatibility. However, it won't receive new features and may be removed in v2.0.0.

### Q: Will my HTTP client break?
**A**: No, HTTP endpoints remain functional. However, you won't get access to real-time notifications or proper MCP specification compliance.

### Q: How do I handle subscriptions in HTTP?
**A**: Subscriptions are not possible over HTTP due to stateless design. Use WebSocket instead.

### Q: What about authentication?
**A**: WebSocket uses the same Spring Security configuration as HTTP. Credentials are passed in WebSocket handshake headers.

## More Information

- [MCP Specification](https://modelcontextprotocol.io/)
- [WebSocket Protocol Overview](../docs/WEBSOCKET_PROTOCOL.md)
- [Architecture Guide](../docs/ARCHITECTURE.md)

# MCP Endpoint Contracts

**Feature**: 001-mcp-annotation-bootstrap
**Date**: 2026-03-27
**Protocol**: MCP (Model Context Protocol) over HTTP — JSON-RPC 2.0 message framing
**Base path**: Configurable via `mcp.server.base-path` (default: `/mcp`)

---

## Transport

All MCP endpoints use **Streamable HTTP transport** (MCP spec 2025-03-26+).
Requests are standard HTTP POST. Responses are either:
- A single JSON response body (synchronous invocations)
- An SSE stream for streaming tool results (optional, future iteration)

All requests and responses use `Content-Type: application/json`.

**Authentication**: All endpoints require authentication by default (secure defaults,
Principle IV). The framework defers to the host application's Spring Security
configuration. With no security configured, the default Spring Boot auto-configuration
permits all requests — adopters MUST configure Spring Security explicitly for production.

---

## Annotations API (Java — not HTTP)

These are the Java annotation contracts for framework consumers.

### @McpTool

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface McpTool {
    /**
     * MCP tool name. Defaults to method name in snake_case if empty.
     */
    String name() default "";

    /**
     * Human-readable description shown to MCP clients.
     * Required — must not be blank.
     */
    String description();

    /**
     * Optional tags for grouping / filtering.
     */
    String[] tags() default {};
}
```

### @McpResource

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface McpResource {
    /**
     * URI template for this resource (e.g., "orders://{orderId}").
     * Required — must be a valid URI template.
     */
    String uri();

    /** Human-readable resource name. Defaults to method name if empty. */
    String name() default "";

    /** Optional description. */
    String description() default "";

    /** MIME type of returned content. Defaults to "application/json". */
    String mimeType() default "application/json";
}
```

### @McpPrompt

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface McpPrompt {
    /**
     * MCP prompt name. Defaults to method name in snake_case if empty.
     */
    String name() default "";

    /** Optional description. */
    String description() default "";
}
```

### @McpInput

```java
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface McpInput {
    /**
     * Parameter name in the MCP input schema.
     * Defaults to Java parameter name if compiler -parameters flag is active.
     */
    String name() default "";

    /** Human-readable description for the schema. */
    String description() default "";

    /**
     * Whether this parameter is required. Defaults to true.
     * Set to false for optional parameters (Java type should be Optional<T>
     * or nullable).
     */
    boolean required() default true;

    /**
     * If true, the parameter value is redacted in structured logs.
     * Use for passwords, tokens, PII.
     */
    boolean sensitive() default false;
}
```

---

## HTTP Endpoint Contracts

### GET/POST `{basePath}/server-info`

Returns the MCP server capabilities and identity.

**Request**: No body required.

**Response** `200 OK`:
```json
{
  "protocolVersion": "2025-03-26",
  "capabilities": {
    "tools": { "listChanged": false },
    "resources": { "listChanged": false, "subscribe": false },
    "prompts": { "listChanged": false }
  },
  "serverInfo": {
    "name": "my-application",
    "version": "1.0.0"
  }
}
```

---

### POST `{basePath}/tools/list`

Lists all registered MCP tools.

**Request body** (optional cursor for pagination):
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "tools/list",
  "params": {
    "cursor": null
  }
}
```

**Response** `200 OK`:
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "tools": [
      {
        "name": "get_order",
        "description": "Retrieves an order by its ID.",
        "inputSchema": {
          "type": "object",
          "properties": {
            "orderId": {
              "type": "string",
              "description": "The order identifier"
            }
          },
          "required": ["orderId"]
        }
      }
    ]
  }
}
```

**Error response** — when MCP subsystem is disabled or unavailable:
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "error": {
    "code": -32601,
    "message": "MCP server is disabled"
  }
}
```

---

### POST `{basePath}/tools/call`

Invokes a registered tool by name.

**Request body**:
```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "method": "tools/call",
  "params": {
    "name": "get_order",
    "arguments": {
      "orderId": "ORD-001"
    }
  }
}
```

**Response** `200 OK` — success:
```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "result": {
    "content": [
      {
        "type": "text",
        "text": "{\"id\":\"ORD-001\",\"status\":\"SHIPPED\"}"
      }
    ],
    "isError": false
  }
}
```

**Response** `200 OK` — tool error (not HTTP error; MCP uses isError flag):
```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "result": {
    "content": [
      {
        "type": "text",
        "text": "Order ORD-999 not found"
      }
    ],
    "isError": true
  }
}
```

**Response** `200 OK` — validation error:
```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "error": {
    "code": -32602,
    "message": "Invalid params",
    "data": {
      "violations": [
        {
          "field": "orderId",
          "message": "must not be blank"
        }
      ]
    }
  }
}
```

**Response** — tool not found:
```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "error": {
    "code": -32601,
    "message": "Tool 'unknown_tool' not found"
  }
}
```

---

### POST `{basePath}/resources/list`

Lists all registered MCP resources.

**Response** `200 OK`:
```json
{
  "jsonrpc": "2.0",
  "id": 3,
  "result": {
    "resources": [
      {
        "uri": "orders://{orderId}",
        "name": "Order",
        "description": "An order resource",
        "mimeType": "application/json"
      }
    ]
  }
}
```

---

### POST `{basePath}/resources/read`

Reads a resource by URI.

**Request body**:
```json
{
  "jsonrpc": "2.0",
  "id": 4,
  "method": "resources/read",
  "params": {
    "uri": "orders://ORD-001"
  }
}
```

**Response** `200 OK`:
```json
{
  "jsonrpc": "2.0",
  "id": 4,
  "result": {
    "contents": [
      {
        "uri": "orders://ORD-001",
        "mimeType": "application/json",
        "text": "{\"id\":\"ORD-001\",\"status\":\"SHIPPED\"}"
      }
    ]
  }
}
```

---

### POST `{basePath}/prompts/list`

Lists all registered MCP prompts.

**Response** `200 OK`:
```json
{
  "jsonrpc": "2.0",
  "id": 5,
  "result": {
    "prompts": [
      {
        "name": "summarize_order",
        "description": "Generates a summary prompt for an order",
        "arguments": [
          {
            "name": "orderId",
            "description": "The order to summarize",
            "required": true
          }
        ]
      }
    ]
  }
}
```

---

### POST `{basePath}/prompts/get`

Resolves a prompt with given arguments.

**Request body**:
```json
{
  "jsonrpc": "2.0",
  "id": 6,
  "method": "prompts/get",
  "params": {
    "name": "summarize_order",
    "arguments": {
      "orderId": "ORD-001"
    }
  }
}
```

**Response** `200 OK`:
```json
{
  "jsonrpc": "2.0",
  "id": 6,
  "result": {
    "description": "Summary prompt for order ORD-001",
    "messages": [
      {
        "role": "user",
        "content": {
          "type": "text",
          "text": "Please summarize order ORD-001 which has status SHIPPED."
        }
      }
    ]
  }
}
```

---

## Error Codes

| Code | Name | When Used |
|------|------|-----------|
| `-32700` | Parse error | Malformed JSON in request body |
| `-32600` | Invalid Request | JSON-RPC structure is invalid |
| `-32601` | Method Not Found | Tool/resource/prompt name not registered, or MCP disabled |
| `-32602` | Invalid Params | Input validation failure; `data.violations` array included |
| `-32603` | Internal Error | Unexpected exception in tool dispatch |
| `-32000` | Server Error | General MCP server error (e.g., registry not ready) |

---

## Actuator Endpoints (read-only, informational)

These are standard Spring Boot Actuator endpoints, not MCP protocol endpoints.

### GET `/actuator/health`

Includes `McpServerHealthIndicator` reporting `UP` when registry is in `READY` state.

```json
{
  "status": "UP",
  "components": {
    "mcpServer": {
      "status": "UP",
      "details": {
        "toolCount": 3,
        "resourceCount": 1,
        "promptCount": 2,
        "registryState": "READY"
      }
    }
  }
}
```

### GET `/actuator/metrics/mcp.tool.invocations`

Standard Micrometer metric endpoint, tags: `tool-name`, `status`.

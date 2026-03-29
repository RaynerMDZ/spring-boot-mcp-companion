# MCP 2025-11-25 Specification vs Current Implementation Analysis

## Executive Summary

The current Spring Boot MCP Companion implementation has **significant gaps** against the official MCP 2025-11-25 specification. While the basic transport structure is in place, critical requirements are missing or incorrectly implemented:

- **Protocol Version**: Spec is 2025-11-25, implementation claims 2025-06-18 (outdated)
- **HTTP Headers**: Missing required `MCP-Protocol-Version` and `MCP-Session-Id` headers
- **Endpoint Structure**: Uses multiple endpoints (/mcp, /mcp/initialize, /mcp/stream) instead of single unified endpoint
- **HTTP Methods**: Only POST implemented; GET is required for SSE subscriptions
- **Accept Headers**: Not validated per spec requirements
- **Origin Header**: No DNS rebinding protection (required security feature)
- **Session Management**: No `MCP-Session-Id` implementation
- **Response Codes**: Not using correct HTTP status codes (202 for notifications, 404 for expired sessions)
- **Error Responses**: Missing `data` field in error responses for detailed information
- **Capability Negotiation**: Incomplete - missing listChanged indicators and sub-capabilities
- **Content Response Format**: Tool results missing `isError` field in result structure
- **Resource Responses**: Wrong format - spec requires `contents` array with proper structure
- **Message Format Issues**: Several response formats don't match specification

---

## 1. TRANSPORT LAYER COMPLIANCE

### Specification Requirements (2025-11-25)

#### Endpoint Structure
**REQUIRED**: Single HTTP endpoint path that supports BOTH POST and GET methods.

**SPEC**: "The server **MUST** provide a single HTTP endpoint path (hereafter referred to as the **MCP endpoint**) that supports both POST and GET methods."

#### POST Request Handling
**REQUIRED** per spec:
1. Client **MUST** include `Accept` header listing both `application/json` and `text/event-stream`
2. POST body **MUST** be single JSON-RPC request, notification, or response
3. If input is response/notification: return HTTP 202 Accepted with no body
4. If input is request: return either `Content-Type: text/event-stream` OR `Content-Type: application/json`
5. Server **SHOULD** immediately send SSE event with empty data to prime reconnection

#### GET Request Handling
**REQUIRED** per spec:
1. Client **MAY** issue HTTP GET to open SSE stream
2. Client **MUST** include `Accept: text/event-stream` header
3. Server **MUST** return `Content-Type: text/event-stream` OR HTTP 405 Method Not Allowed
4. Server **MUST NOT** send response unless resuming a stream

#### HTTP Headers - CRITICAL SECURITY
**REQUIRED** per spec:
- `Origin` header validation: "Servers **MUST** validate the Origin header to prevent DNS rebinding attacks"
- `MCP-Protocol-Version` header: "Client **MUST** include on all subsequent requests after initialize"
- `MCP-Session-Id` header: "Clients using HTTP **MUST** include in all requests if set by server"

#### HTTP Status Codes
**REQUIRED** per spec:
- 200 OK: Successful operation with response body
- 202 Accepted: Successful notification/response with no body
- 400 Bad Request: Malformed request or invalid protocol version
- 404 Not Found: Session expired (server must terminate session)
- 405 Method Not Allowed: Server doesn't support method at endpoint
- 403 Forbidden: Origin header invalid (DNS rebinding attack)

#### Session Management
**REQUIRED** per spec:
- Server **MAY** assign session ID in `MCP-Session-Id` response header on InitializeResult
- Session ID **SHOULD** be globally unique and cryptographically secure
- Session ID **MUST** contain only visible ASCII (0x21 to 0x7E)
- Server **MAY** terminate session (respond with 404 thereafter)
- Client **SHOULD** send HTTP DELETE to terminate session

### Current Implementation Status

❌ **CRITICAL FAILURES**:

1. **Wrong Endpoint Structure**
   - Implementation: `/mcp`, `/mcp/initialize`, `/mcp/stream` (multiple endpoints)
   - Spec Required: Single `/mcp` endpoint handling POST and GET
   - File: McpHttpController.java:100, 466

2. **Missing GET Support**
   - Implementation: Only POST at `/mcp`
   - Spec Required: GET at same endpoint for SSE subscriptions
   - Current: `@GetMapping("/mcp/stream")` is incorrect separate endpoint
   - Missing: Ability to open long-lived SSE stream via GET /mcp

3. **Missing HTTP Headers Validation**
   - NOT checking `Accept` header (required: `application/json` and `text/event-stream`)
   - NOT including `MCP-Protocol-Version` in responses
   - NOT implementing `MCP-Session-Id` header mechanism
   - NOT validating `Origin` header (DNS rebinding attack vulnerability)
   - File: McpHttpController.java shows no header validation

4. **Wrong HTTP Status Codes**
   - Line 429: Always returns 200 for all responses
   - Should return: 202 Accepted for notifications
   - Should return: 404 Not Found when session expired
   - Should return: 403 Forbidden for invalid Origin

5. **Missing Session Management**
   - No `MCP-Session-Id` generation or tracking
   - No session expiration handling
   - No DELETE method to close sessions
   - No session ID validation on subsequent requests

6. **Incorrect Response Structure for Notifications**
   - Spec: Notifications should return HTTP 202 Accepted with no body
   - Current: Line 128-130 doesn't return 202 status
   - File: McpHttpController.java line 100-138

---

## 2. PROTOCOL FORMAT & JSON-RPC COMPLIANCE

### Specification Requirements

#### Request Format
```json
{
  "jsonrpc": "2.0",
  "id": "string or number (MUST NOT be null)",
  "method": "string",
  "params": { /* object, optional */ }
}
```

**REQUIRED**: "The request ID **MUST NOT** have been previously used by the requestor within the same session."

#### Response Format - Success
```json
{
  "jsonrpc": "2.0",
  "id": "same as request",
  "result": { /* any object */ }
}
```

#### Response Format - Error
```json
{
  "jsonrpc": "2.0",
  "id": "same as request (except if ID unreadable)",
  "error": {
    "code": "integer",
    "message": "string",
    "data": "optional unknown"
  }
}
```

**REQUIRED**: Error must include `code` (integer) and `message` (string). Data field is optional but recommended for context.

#### Notification Format
```json
{
  "jsonrpc": "2.0",
  "method": "string",
  "params": { /* object, optional */ }
}
```

**REQUIRED**: "Notifications **MUST NOT** include an ID."

### Current Implementation Status

❌ **FAILURES**:

1. **Missing `data` Field in Error Responses**
   - Spec: "Error responses **MUST** include error field with code and message. The data **MAY** provide additional context."
   - Current: McpHttpController.java line 413-419 only includes code and message
   - Missing: Data field for detailed error context (e.g., supported versions in version mismatch)
   - Test expects: MCPSpecCompliance2025Test.java line 102 expects `error.data.supported` and `error.data.requested`

2. **ID Validation Missing**
   - Spec: "The request ID **MUST NOT** have been previously used by the requestor within the same session."
   - Current: No tracking of used IDs in session
   - Implementation concern: Thread-safety for ID tracking

3. **Notification Handling Incomplete**
   - Spec: "If input is JSON-RPC notification, server **MUST** return HTTP 202 Accepted with no body"
   - Current: Line 127-130 logs but doesn't return 202 status
   - Missing: Proper response code handling

---

## 3. LIFECYCLE & INITIALIZATION COMPLIANCE

### Specification Requirements

#### Version Negotiation
**REQUIRED** exact sequence:
1. Client sends `initialize` with `protocolVersion`
2. Server responds with same version if supported
3. Server responds with different version if not supported
4. Client disconnects if can't support server's version

**SPEC QUOTE**: "If the server supports the requested protocol version, it **MUST** respond with the same version. Otherwise, the server **MUST** respond with another protocol version it supports."

#### Initialize Request Fields
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "initialize",
  "params": {
    "protocolVersion": "2025-11-25",
    "capabilities": {
      "roots": { "listChanged": true },
      "sampling": {},
      "elicitation": { "form": {}, "url": {} },
      "tasks": { "requests": { ... } }
    },
    "clientInfo": {
      "name": "string",
      "title": "string (optional)",
      "version": "string",
      "description": "string (optional)",
      "icons": [ /* array, optional */ ],
      "websiteUrl": "string (optional)"
    }
  }
}
```

#### Initialize Response Fields
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "protocolVersion": "2025-11-25",
    "capabilities": {
      "logging": {},
      "prompts": { "listChanged": true },
      "resources": { "subscribe": true, "listChanged": true },
      "tools": { "listChanged": true },
      "completions": {},
      "tasks": { "list": {}, "cancel": {} }
    },
    "serverInfo": {
      "name": "string",
      "title": "string (optional)",
      "version": "string",
      "description": "string (optional)",
      "icons": [ /* array, optional */ ],
      "websiteUrl": "string (optional)"
    },
    "instructions": "string (optional)"
  }
}
```

#### After Initialize - Required `initialized` Notification
**SPEC QUOTE**: "After successful initialization, the client **MUST** send an `initialized` notification"

```json
{
  "jsonrpc": "2.0",
  "method": "notifications/initialized"
}
```

### Current Implementation Status

❌ **CRITICAL FAILURES**:

1. **Protocol Version Outdated**
   - Spec: 2025-11-25
   - Current: 2025-06-18 (outdated by 5 months)
   - File: McpHttpController.java line 35 comment says "2025-06-18"

2. **Missing Protocol Version Header Requirement**
   - Spec: "Client **MUST** include `MCP-Protocol-Version: <version>` header on all subsequent requests"
   - Current: Not checking or enforcing this header
   - Implication: Servers can't determine which version client supports for multi-version scenarios

3. **Wrong Endpoint for Initialize**
   - Spec: Single POST endpoint `/mcp` for ALL operations
   - Current: `/mcp` endpoint (correct)
   - BUT: Test expects `/mcp/initialize` (line 57) - implementation mismatch

4. **Missing Client Capabilities Fields**
   - Spec requires client to send: `roots`, `sampling`, `elicitation`, `tasks` capabilities
   - Current: No validation of these fields received from client
   - Missing: `title`, `description`, `websiteUrl`, `icons` in clientInfo
   - File: McpHttpController.java line 151-153 only checks protocolVersion

5. **Incomplete Server Capabilities Declaration**
   - Spec requires detailed capability structure:
     ```json
     {
       "tools": { "listChanged": true },
       "resources": { "subscribe": true, "listChanged": true },
       "prompts": { "listChanged": true },
       "logging": {},
       "completions": {},
       "tasks": { "list": {}, "cancel": {} }
     }
     ```
   - Current: Line 165-168 only has tools, resources with subscribe, prompts
   - Missing: logging, completions, tasks, listChanged for tools and prompts
   - File: McpHttpController.java line 165-168

6. **Incomplete Server Info**
   - Spec requires: name, title, version, description, icons, websiteUrl, instructions
   - Current: Line 173-176 only has name and version
   - Missing: title, description, icons, websiteUrl, instructions
   - File: McpHttpController.java line 173-176

7. **Missing Instructions Field**
   - Spec: "instructions: Optional instructions for the client"
   - Current: Not included in response
   - Severity: Low - optional field but still missing

---

## 4. CORE METHODS - TOOLS COMPLIANCE

### Specification Requirements

#### tools/list Request
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "tools/list",
  "params": {
    "cursor": "optional-cursor-value"
  }
}
```

**SUPPORTS PAGINATION**: `cursor` parameter for paginated results

#### tools/list Response
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "tools": [
      {
        "name": "unique-identifier",
        "title": "Human-readable (optional)",
        "description": "Description",
        "inputSchema": { /* JSON Schema object */ },
        "outputSchema": { /* JSON Schema object, optional */ },
        "icons": [ /* optional */ ],
        "annotations": { /* optional */ },
        "execution": { "taskSupport": "optional|required|forbidden" }
      }
    ],
    "nextCursor": "cursor-for-next-page"
  }
}
```

**REQUIRED FIELDS** per spec:
- `name`: String identifier
- `description`: String description
- `inputSchema`: JSON Schema object (MUST be valid, not null)
- `outputSchema`: Optional JSON Schema for output validation
- `title`: Optional human-readable name
- `icons`: Optional array of icon objects
- `execution`: Optional taskSupport indicator

#### tools/call Request
```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "method": "tools/call",
  "params": {
    "name": "tool-name",
    "arguments": {
      "param1": "value1"
    }
  }
}
```

#### tools/call Response - Success
```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "result": {
    "content": [
      {
        "type": "text",
        "text": "Result text"
      }
    ],
    "isError": false
  }
}
```

**REQUIRED**: `isError` field MUST be present in result. Tool execution errors return `isError: true` with content explaining error (not a protocol error).

#### tools/call Response - Tool Error
```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "result": {
    "content": [
      {
        "type": "text",
        "text": "Invalid input: parameter X is required"
      }
    ],
    "isError": true
  }
}
```

### Current Implementation Status

❌ **FAILURES**:

1. **Missing `title` Field**
   - Spec: Optional human-readable name
   - Current: Line 191-194 doesn't include title in response
   - Should include but is optional

2. **Missing `isError` Field in Response**
   - Spec: "result **MUST** include isError field"
   - Current: Line 225 returns only `content`
   - File: McpHttpController.java line 220-226
   - Impact: Clients can't distinguish tool execution errors from protocol errors

3. **Missing `outputSchema` Support**
   - Spec: Optional but recommended for output validation
   - Current: Not included in tool definition or response
   - File: McpToolDefinition.java doesn't include outputSchema field

4. **Missing Pagination Support**
   - Spec: "tools/list operation supports pagination"
   - Current: No `cursor` parameter handling
   - Current: No `nextCursor` in response
   - File: McpHttpController.java line 186-198 no pagination

5. **Missing `icons` and `annotations` Support**
   - Spec: Optional fields for UI and metadata
   - Current: Not included in response
   - Low priority but missing

6. **Missing `execution.taskSupport` Field**
   - Spec: Optional indicator for task execution support
   - Current: Not included
   - File: McpToolDefinition.java line 12-18

---

## 5. RESOURCES COMPLIANCE

### Specification Requirements

#### resources/list Response
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "resources": [
      {
        "uri": "file:///path",
        "name": "filename",
        "title": "Display name (optional)",
        "description": "Description",
        "mimeType": "text/plain",
        "icons": [ /* optional */ ],
        "size": 12345  /* optional */
      }
    ],
    "nextCursor": "cursor-value"
  }
}
```

#### resources/read Response - TEXT
```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "result": {
    "contents": [
      {
        "uri": "file:///example.txt",
        "mimeType": "text/plain",
        "text": "File contents",
        "annotations": { /* optional */ }
      }
    ]
  }
}
```

#### resources/read Response - BINARY
```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "result": {
    "contents": [
      {
        "uri": "file:///example.png",
        "mimeType": "image/png",
        "blob": "base64-encoded-data",
        "annotations": { /* optional */ }
      }
    ]
  }
}
```

**CRITICAL**: `contents` is an ARRAY. Text content has `text` field OR `blob` field (not both).

#### resources/subscribe Request
```json
{
  "jsonrpc": "2.0",
  "id": 4,
  "method": "resources/subscribe",
  "params": {
    "uri": "file:///resource"
  }
}
```

#### resources/subscribe Response
**SPEC**: Server returns successful response (HTTP 200 with result)

#### Resource Updates - Notification
```json
{
  "jsonrpc": "2.0",
  "method": "notifications/resources/updated",
  "params": {
    "uri": "file:///resource"
  }
}
```

#### resources/templates/list Response
```json
{
  "jsonrpc": "2.0",
  "id": 3,
  "result": {
    "resourceTemplates": [
      {
        "uriTemplate": "file:///{path}",
        "name": "Project Files",
        "title": "Display name (optional)",
        "description": "Description",
        "mimeType": "application/octet-stream",
        "icons": [ /* optional */ ]
      }
    ]
  }
}
```

### Current Implementation Status

❌ **CRITICAL FAILURES**:

1. **Wrong Response Structure for resources/read**
   - Spec: Response has `contents` array
   - Current: Line 271-280 has `contents` array but mismatch in structure
   ```
   Current: { uri, contents: [{ uri, mimeType, text }] }
   Spec:    { contents: [{ uri, mimeType, text }] }
   ```
   - File: McpHttpController.java line 271-280
   - Issue: Duplicate uri field, wrong nesting

2. **Missing `blob` Field Support for Binary**
   - Spec: Binary content uses `blob` field (base64)
   - Current: Only supports `text` field
   - File: McpHttpController.java only handles text

3. **Missing Pagination in resources/list**
   - Spec: Supports pagination with `cursor` and `nextCursor`
   - Current: No pagination support
   - File: McpHttpController.java line 232-244

4. **Missing Optional Resource Fields**
   - Spec: `title`, `icons`, `size`, `annotations`
   - Current: Only uri, name, description, mimeType
   - File: McpResourceDefinition.java line 9-14 missing these fields

5. **Missing resources/templates/list Support**
   - Spec: Server **SHOULD** support resource templates for parameterized resources
   - Current: No endpoint for `resources/templates/list`
   - Missing: URI template support
   - File: No resources/templates/list handler in McpHttpController

6. **Missing Annotation Support**
   - Spec: Resources support optional annotations (audience, priority, lastModified)
   - Current: No annotations in response
   - File: McpResourceDefinition.java doesn't include annotations

7. **Incomplete Subscription Implementation**
   - Spec: Subscribe returns success, then server sends notifications
   - Current: Line 288-305 just logs but doesn't track subscriptions
   - Missing: Integration with SSE manager to send updates
   - Missing: Proper subscription state management
   - TODO comment at line 286 acknowledges this

---

## 6. PROMPTS COMPLIANCE

### Specification Requirements

#### prompts/list Response
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "prompts": [
      {
        "name": "prompt-name",
        "title": "Display name (optional)",
        "description": "Description",
        "arguments": [
          {
            "name": "arg-name",
            "description": "Arg description",
            "required": true
          }
        ],
        "icons": [ /* optional */ ]
      }
    ],
    "nextCursor": "cursor-value"
  }
}
```

#### prompts/get Request
```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "method": "prompts/get",
  "params": {
    "name": "prompt-name",
    "arguments": {
      "arg1": "value1"
    }
  }
}
```

#### prompts/get Response
```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "result": {
    "description": "Prompt description",
    "messages": [
      {
        "role": "user",
        "content": {
          "type": "text",
          "text": "Message text"
        }
      }
    ]
  }
}
```

**SPEC**: Messages array can contain multiple message objects with roles "user" or "assistant"

### Current Implementation Status

❌ **FAILURES**:

1. **Missing `title` Field**
   - Spec: Optional human-readable name
   - Current: Line 337-340 doesn't include title
   - Should include if available

2. **Missing `icons` Field**
   - Spec: Optional array for UI display
   - Current: Not included in response
   - File: McpPromptDefinition.java doesn't include icons field

3. **Missing Pagination**
   - Spec: Supports pagination with `cursor` and `nextCursor`
   - Current: No pagination
   - File: McpHttpController.java line 333-343

4. **Missing Prompt Content Structure**
   - Spec: prompts/get response has `description` and `messages` fields
   - Current: Line 371-381 has different structure, returns user message with content
   - Issue: Message role and content structure might be correct but need to verify

5. **Incorrect Arguments Handling in Definition**
   - Spec: Arguments have `name`, `description`, `required`
   - Current: McpPromptDefinition.java line 30-42 has these fields
   - Status: ✓ Correct in model, needs verification in response

6. **Missing Message Type Support**
   - Spec: Messages can have type "text", "image", "audio", "resource"
   - Current: Only text content
   - Missing: Image, audio, resource embedding support

7. **Missing listChanged Capability**
   - Spec: Server declares `"prompts": { "listChanged": true }`
   - Current: Line 168 has empty object
   - File: McpHttpController.java line 168

---

## 7. NOTIFICATIONS COMPLIANCE

### Specification Requirements

#### List Changed Notifications
When tool/resource/prompt list changes:

```json
{
  "jsonrpc": "2.0",
  "method": "notifications/tools/list_changed"
}
```

OR for resources/prompts:
- `notifications/resources/list_changed`
- `notifications/prompts/list_changed`

#### Resource Update Notification
```json
{
  "jsonrpc": "2.0",
  "method": "notifications/resources/updated",
  "params": {
    "uri": "file:///resource"
  }
}
```

### Current Implementation Status

❌ **FAILURES**:

1. **No Notification System for List Changes**
   - Spec: Server **SHOULD** send notifications when lists change
   - Current: No mechanism to send `notifications/tools/list_changed`
   - Missing: Integration points to trigger notifications

2. **Incomplete SSE Notification Manager Integration**
   - Current: SseNotificationManager exists but not used for list changes
   - File: McpHttpController.java has TODO comment at line 286

3. **No Resource Update Notifications**
   - Spec: When subscribed resource changes, send `notifications/resources/updated`
   - Current: Not implemented
   - File: Subscription handlers at line 288-305 don't track or notify

---

## 8. ERROR HANDLING COMPLIANCE

### Specification Requirements

#### Standard Error Codes
```
-32700: Parse error
-32600: Invalid Request
-32601: Method not found
-32602: Invalid params
-32603: Internal error
-32002: Resource not found (custom)
-32000: Server error (custom range)
```

#### Error Response with Data
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "error": {
    "code": -32602,
    "message": "Invalid params",
    "data": {
      "details": "Missing required parameter"
    }
  }
}
```

### Current Implementation Status

❌ **FAILURES**:

1. **Missing `data` Field in Error Responses**
   - Spec: Error responses **MAY** include data field
   - Current: Line 413-419 only has code and message
   - Should include: data field with context (e.g., supported versions)
   - Impact: Test at line 102-103 expects `error.data.supported` and `error.data.requested`

2. **Wrong Error Code for Version Mismatch**
   - Spec: Use -32602 (Invalid params) or custom codes
   - Current: Line 156 uses -32000
   - File: McpHttpController.java line 156

---

## 9. MISSING HTTP-SPECIFIC REQUIREMENTS

### Security Requirements

#### Origin Header Validation
**SPEC QUOTE**: "Servers **MUST** validate the `Origin` header on all incoming connections to prevent DNS rebinding attacks"

**SPEC QUOTE**: "If the `Origin` header is present and invalid, servers **MUST** respond with HTTP 403 Forbidden"

**Current Status**: NOT IMPLEMENTED
- No Origin header validation
- No 403 response for invalid origins
- DNS rebinding attack vulnerability exists

#### Authorization
**SPEC**: Servers **SHOULD** implement proper authentication

**Current Status**: NOT VISIBLE
- No authentication mentioned
- No bearer token support
- No API key support
- No OAuth support mentioned

#### HTTP Accept Header
**SPEC**: "Client **MUST** include `Accept` header, listing both `application/json` and `text/event-stream`"

**Current Status**: NOT VALIDATED
- No Accept header checking
- Server doesn't validate client accepts response format

#### Content-Type Negotiation
**SPEC**: Server chooses response format based on Accept header:
- `Content-Type: application/json` for single response
- `Content-Type: text/event-stream` for SSE stream

**Current Status**: ALWAYS RETURNS application/json
- Line 428 always sets application/json
- Never initiates SSE streams
- No Content-Type negotiation logic

---

## 10. STREAMABLE HTTP TRANSPORT SPECIFICS

### SSE Stream Handling

**SPEC**: For SSE streams:
1. Server **SHOULD** send event with empty data immediately (prime for reconnect)
2. Server **MAY** close connection without terminating stream (allow polling)
3. Server **SHOULD** send `retry` field before closing
4. Server **MAY** send requests/notifications before response
5. Server **SHOULD** terminate stream after response sent

**Current Status**: NOT IMPLEMENTED
- Line 466-489: GET /mcp/stream endpoint exists
- Line 479-482: Sends initial connection confirmation (correct)
- Missing: Proper SSE stream lifecycle management
- Missing: Empty data event for priming
- Missing: Retry field on connection close
- Missing: Proper stream termination

### Resumability and Redelivery

**SPEC**: Support resuming broken connections:
1. Servers **MAY** attach `id` field to SSE events (globally unique per session)
2. Client sends `Last-Event-ID` header to resume
3. Server replays messages after that event ID

**Current Status**: NOT IMPLEMENTED
- No event ID tracking
- No Last-Event-ID header handling
- No message replay mechanism

---

## COMPLIANCE SUMMARY TABLE

| Requirement | Status | Severity | Location |
|-----------|--------|----------|----------|
| Single unified HTTP endpoint | Multiple endpoints | CRITICAL | Multiple endpoints used |
| GET method support | Wrong endpoint | CRITICAL | /mcp/stream separate |
| Origin header validation | Missing | CRITICAL | Security vulnerability |
| MCP-Protocol-Version header | Missing | HIGH | Not checked/sent |
| MCP-Session-Id header | Missing | HIGH | No session tracking |
| HTTP Accept header validation | Missing | MEDIUM | Not checked |
| HTTP 202 Accepted for notifications | Always 200 | MEDIUM | Line 429 |
| HTTP 404 for expired session | Missing | MEDIUM | No session mgmt |
| HTTP 403 for invalid Origin | Missing | HIGH | Security |
| Error response `data` field | Missing | MEDIUM | Line 413-419 |
| Protocol Version 2025-11-25 | 2025-06-18 | MEDIUM | Line 35 |
| tools/list pagination | Missing | MEDIUM | No cursor support |
| tools response `isError` field | Missing | CRITICAL | Line 225 |
| outputSchema for tools | Missing | LOW | Optional field |
| resources/read `contents` format | Wrong structure | HIGH | Line 271-280 |
| resources/read binary `blob` | Missing | MEDIUM | Only text |
| resources/templates/list | Missing | MEDIUM | No endpoint |
| resources subscription tracking | Incomplete | MEDIUM | TODO at line 286 |
| prompts/list pagination | Missing | MEDIUM | No cursor |
| prompts/list `title` field | Missing | LOW | Optional |
| List changed notifications | Missing | HIGH | No mechanism |
| Resource update notifications | Missing | HIGH | No mechanism |
| SSE stream proper lifecycle | Missing | HIGH | Wrong endpoint |
| SSE resumability/redelivery | Missing | MEDIUM | No event IDs |
| Capability negotiation details | Incomplete | MEDIUM | Line 165-168 |
| Server info enhancements | Minimal | MEDIUM | Line 173-176 |

---

## RECOMMENDED PRIORITY FIXES

### PHASE 1: CRITICAL (Blocks spec compliance)
1. Implement single unified `/mcp` endpoint with POST and GET
2. Add Origin header validation with 403 response
3. Add `isError` field to tool call responses
4. Implement proper HTTP status codes (202, 404, 403)
5. Fix resources/read response structure (`contents` array format)

### PHASE 2: HIGH (Breaks interoperability)
1. Implement MCP-Protocol-Version header handling
2. Implement MCP-Session-Id session management
3. Implement proper SSE stream lifecycle via GET
4. Add Accept header validation
5. Update protocol version to 2025-11-25

### PHASE 3: MEDIUM (Missing features)
1. Add pagination support (cursor/nextCursor)
2. Implement list changed notifications
3. Implement resource update notifications
4. Add resource subscription tracking
5. Complete capability negotiation

### PHASE 4: LOW (Polish)
1. Add missing optional fields (title, icons, annotations)
2. Add output schema support
3. Add resource templates support
4. Improve error responses with data field

---

## CONCLUSION

The implementation has fundamental architectural issues that prevent it from being spec-compliant. Most critically:
- Wrong endpoint structure (multiple vs. single)
- Missing security requirements (Origin validation)
- Wrong HTTP status codes
- Incomplete response structures
- Missing critical fields (isError, contents array format)

These are not minor issues - they will cause interoperability failures with standard MCP clients.

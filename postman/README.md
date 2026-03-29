# Postman Collections for Spring Boot MCP

This folder contains Postman collections for testing the Model Context Protocol (MCP) endpoints as an AI Agent would use them.

## Files

### `MCP-Agent-Simulator.postman_collection.json`
A comprehensive Postman collection that simulates AI agent interactions with the MCP server.

**Includes:**
- Server Information endpoints
- Tool discovery and invocation
- Resource management
- Prompt template retrieval
- Complete AI Agent workflow examples
- Error scenario testing

### `MCP-Development.postman_environment.json`
Environment configuration for local development.

**Variables:**
- `baseUrl`: API base URL (default: `http://localhost:8080`)
- `mcpBasePath`: MCP endpoint path (default: `/mcp`)
- `requestId`: JSON-RPC request ID
- `agentName`: AI Agent identifier
- `timeout`: Request timeout in ms

## Getting Started

### 1. Install Postman
Download from [postman.com](https://www.postman.com/downloads/)

### 2. Import Collection and Environment

**Option A: Import Files**
1. Open Postman
2. Click "Import" → Select `MCP-Agent-Simulator.postman_collection.json`
3. Click "Import" → Select `MCP-Development.postman_environment.json`

**Option B: Use Postman Links**
Copy the import URLs and paste in Postman's import dialog.

### 3. Select Environment
In Postman's top-right, select "MCP Development" environment.

### 4. Start Your MCP Server
```bash
# Terminal 1: Start Spring Boot application
mvn spring-boot:run

# Terminal 2: View logs (optional)
tail -f logs/app.log
```

Verify server is running:
```bash
curl http://localhost:8080/mcp/server-info
```

### 5. Run Requests

#### Single Request
1. Select a request from the collection
2. Click "Send"
3. Review response in the Response panel

#### Full Workflow (Recommended)
Run the "AI Agent Workflows" folder in order:
1. Initialize - Get Server Capabilities
2. Discover - List Available Tools
3. Discover - List Available Resources
4. Discover - List Available Prompts
5. Execute - Call a Tool
6. Context - Read Required Resource
7. Reasoning - Get Prompt Template

## Collection Structure

### 📋 Server Information
- `Get Server Info` - Retrieve server metadata

### 🔧 Tools
- `List Available Tools` - Discover all tools
- `Call Tool - Simple String Parameter` - Basic tool invocation
- `Call Tool - Numeric Parameters` - Array and numeric parameters
- `Call Tool - Complex Object` - Nested POJO serialization
- `Call Tool - With Validation` - Input validation examples

### 📦 Resources
- `List Available Resources` - Discover resources
- `Read Resource - By URI` - Simple resource read
- `Read Resource - With Parameters` - Parameterized resource read

### 🎯 Prompts
- `List Available Prompts` - Discover prompt templates
- `Get Prompt - Code Review Template` - Code review prompt
- `Get Prompt - Documentation Generator` - Documentation prompt

### 🤖 AI Agent Workflows
Complete end-to-end workflows demonstrating:
1. **Initialize** - Server capability discovery
2. **Discover** - Tools, resources, prompts inventory
3. **Execute** - Tool invocation
4. **Context** - Resource reading
5. **Reasoning** - Prompt template usage

### ⚠️ Error Scenarios
Test error handling:
- Invalid tool name
- Invalid parameters
- Missing required parameters

## Example Workflows

### Workflow 1: Simple Tool Call
```
1. Get Server Info
2. List Tools
3. Call Tool (with your parameters)
```

### Workflow 2: Full Agent Interaction
```
1. Initialize (Get Server Info)
2. Discover Tools
3. Discover Resources
4. Discover Prompts
5. Call Tool (decision making)
6. Read Resource (context gathering)
7. Get Prompt (reasoning)
```

### Workflow 3: Error Handling Testing
```
1. Call Tool with Invalid Name
2. Call Tool with Invalid Parameters
3. Call Tool with Missing Parameters
4. Verify error responses
```

## JSON-RPC 2.0 Format

The MCP specification uses a **single endpoint** (`POST /mcp`) with JSON-RPC 2.0 protocol. The operation is specified in the `method` field:

```json
{
  "jsonrpc": "2.0",
  "id": "unique-request-id",
  "method": "tools/list",
  "params": {}
}
```

### Available Methods

- `server/info` - Get server metadata
- `tools/list` - List available tools
- `tools/call` - Call a tool
- `resources/list` - List available resources
- `resources/read` - Read a resource
- `prompts/list` - List available prompts
- `prompts/get` - Get a prompt template

### Response Format

Success:
```json
{
  "jsonrpc": "2.0",
  "id": "unique-request-id",
  "result": { /* response data */ }
}
```

Error:
```json
{
  "jsonrpc": "2.0",
  "id": "unique-request-id",
  "error": {
    "code": -32600,
    "message": "Invalid Request"
  }
}
```

### MCP Specification Reference

According to the official MCP documentation:
- **Single Transport**: All communication goes through one endpoint (`/mcp`)
- **JSON-RPC 2.0**: Standard JSON-RPC 2.0 protocol for request/response
- **Methods**: Operations specified in the `method` field (e.g., `tools/list`, `resources/read`)
- **No separate HTTP endpoints**: Don't use `/mcp/tools/list` or `/mcp/resources/read` - use `/mcp` with the method field

See: https://modelcontextprotocol.io/docs/develop/build-client

## Customization

### Change Server URL
1. Select "MCP Development" environment
2. Click the eye icon
3. Edit `baseUrl` variable
4. Save

### Change API Base Path
If your server uses a different base path:
1. Edit `mcpBasePath` environment variable
2. Change `/mcp` to your path
3. Save

### Add Custom Headers
1. Open any request
2. Click "Headers" tab
3. Add your headers (e.g., authentication)
4. Save as a new request

### Create New Request
1. Right-click a folder
2. Select "Add Request"
3. Fill in method, URL, and body
4. Save

## Tips & Tricks

### Use Variables in Requests
```
{{baseUrl}}{{mcpBasePath}}/endpoint
{{agentName}}
{{requestId}}
```

### Dynamic Request IDs
Add Pre-request Script:
```javascript
pm.variables.set("requestId", "req-" + Date.now());
```

### Test Response Validation
Add Tests tab:
```javascript
pm.test("Status is 200", () => {
  pm.response.to.have.status(200);
});

pm.test("Response is valid JSON-RPC", () => {
  const json = pm.response.json();
  pm.expect(json.jsonrpc).to.equal("2.0");
  pm.expect(json).to.have.any.keys("result", "error");
});
```

### Run Collection Automatically
1. Click the folder
2. Click "Run" (play icon)
3. Select requests to run
4. Watch collection execute sequentially

## Troubleshooting

### Connection Refused
- Verify Spring Boot server is running
- Check `baseUrl` in environment
- Ensure port 8080 is accessible

### 404 Not Found
- Verify `mcpBasePath` is correct
- Check server logs for routing issues
- Confirm endpoint exists in your server

### 400 Bad Request
- Verify JSON-RPC format is correct
- Check required parameters are present
- Validate parameter types match schema

### 500 Internal Server Error
- Check server logs
- Verify tool/resource exists
- Check parameter validation

## Examples

### Call Tool with POJO
```json
{
  "jsonrpc": "2.0",
  "id": "call-1",
  "method": "tools/call",
  "params": {
    "name": "process_order",
    "arguments": {
      "order": {
        "id": "ORD-123",
        "items": [
          {"sku": "ITEM-1", "qty": 2}
        ]
      }
    }
  }
}
```

### Read Resource with Parameters
```json
{
  "jsonrpc": "2.0",
  "id": "read-1",
  "method": "resources/read",
  "params": {
    "uri": "database://users/{id}",
    "arguments": {
      "id": "user-456"
    }
  }
}
```

### Get Prompt Template
```json
{
  "jsonrpc": "2.0",
  "id": "prompt-1",
  "method": "prompts/get",
  "params": {
    "name": "code_review",
    "arguments": {
      "language": "java",
      "severity": "high"
    }
  }
}
```

## Next Steps

1. ✅ Import collection and environment
2. ✅ Start your Spring Boot server
3. ✅ Run "AI Agent Workflows" folder
4. ✅ Customize for your needs
5. ✅ Integrate with CI/CD (see below)

## CI/CD Integration

Run collection in CI/CD:

```bash
# Using Newman (npm install -g newman)
newman run MCP-Agent-Simulator.postman_collection.json \
  -e MCP-Development.postman_environment.json \
  --reporters cli,json \
  --reporter-json-export results.json
```

## Support

For issues or improvements:
1. Check troubleshooting section
2. Review server logs
3. Verify JSON-RPC format
4. Open an issue on GitHub

## License

Same as Spring Boot MCP Companion - Apache 2.0

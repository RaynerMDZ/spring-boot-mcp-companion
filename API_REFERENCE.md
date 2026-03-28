# API Reference - Spring Boot MCP Companion

## Annotations

### @EnableMcpCompanion

Main annotation to enable MCP support in your Spring Boot application.

**Location:** Place on your `@SpringBootApplication` class

**Syntax:**
```java
@SpringBootApplication
@EnableMcpCompanion
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}
```

**Effect:**
- Enables Spring Boot auto-configuration for MCP
- Scans classpath for `@McpTool`, `@McpResource`, and `@McpPrompt` annotations
- Registers MCP transport endpoints
- Enables observability and health checks

**Required:** Yes (must be present for any MCP features to work)

---

### @McpTool

Exposes a method as a remote-callable function (tool).

**Location:** Method in any Spring `@Service`, `@Component`, or `@Controller`

**Syntax:**
```java
@McpTool(description = "What this tool does")
public String myTool(@McpInput String param) {
    return "result";
}
```

**Attributes:**

| Attribute | Type | Default | Required | Description |
|-----------|------|---------|----------|-------------|
| `description` | String | "" | ✓ Yes | Human-readable description of what the tool does |
| `name` | String | Method name (snake_case) | No | Tool name in MCP (auto-converted to snake_case) |
| `tags` | String[] | {} | No | Categorization tags |

**Naming Convention:**
- `getOrder()` → `get_order`
- `createNewUser()` → `create_new_user`
- `calculateTotal()` → `calculate_total`

**Return Type:** Any Java type (auto-converted to JSON)

**Parameters:** Marked with `@McpInput`

**Example:**
```java
@McpTool(
    description = "Create a new order",
    tags = {"orders", "e-commerce"}
)
public Order createOrder(
    @McpInput String productId,
    @McpInput Integer quantity
) {
    return orderService.create(productId, quantity);
}
```

---

### @McpResource

Exposes a method as a URI-based resource.

**Location:** Method in any Spring component

**Syntax:**
```java
@McpResource(
    uri = "order://{orderId}",
    description = "Order details"
)
public Order getOrder(@McpInput String orderId) {
    return orderRepository.findById(orderId).orElseThrow();
}
```

**Attributes:**

| Attribute | Type | Default | Required | Description |
|-----------|------|---------|----------|-------------|
| `uri` | String | N/A | ✓ Yes | URI template (can include path parameters like `{id}`) |
| `description` | String | "" | ✓ Yes | Human-readable description |
| `mimeType` | String | "application/octet-stream" | No | MIME type of the resource |
| `name` | String | Derived from uri | No | Resource name |

**URI Template Syntax:**
- `order://{orderId}` - Single path parameter
- `user://{userId}/profile` - Nested path
- `document://{docId}/pages/{pageNum}` - Multiple parameters
- `static:///{filePath}` - Catch-all pattern

**MIME Types:**
- `application/json` - JSON data
- `application/xml` - XML data
- `text/plain` - Plain text
- `application/pdf` - PDF document
- `image/*` - Image data

**Example:**
```java
@McpResource(
    uri = "user://{userId}/profile",
    description = "User profile information",
    mimeType = "application/json"
)
public UserProfile getUserProfile(@McpInput String userId) {
    return userRepository.findById(userId).orElseThrow();
}
```

---

### @McpPrompt

Exposes a method as a reusable prompt template.

**Location:** Method in any Spring component

**Syntax:**
```java
@McpPrompt(
    name = "summarize",
    description = "Summarize content"
)
public String summarize(@McpInput String content) {
    return ai.summarize(content);
}
```

**Attributes:**

| Attribute | Type | Default | Required | Description |
|-----------|------|---------|----------|-------------|
| `name` | String | N/A | ✓ Yes | Unique prompt template name |
| `description` | String | "" | ✓ Yes | What this prompt does |
| `tags` | String[] | {} | No | Categorization tags |

**Naming Convention:**
- Use lowercase with underscores
- Example: `user_onboarding`, `code_review`, `bug_analysis`

**Return Type:** String (the prompt text/content)

**Parameters:** Marked with `@McpInput`

**Example:**
```java
@McpPrompt(
    name = "user_analysis",
    description = "Analyze user behavior and generate insights"
)
public String analyzeUser(@McpInput String userId) {
    User user = userRepository.findById(userId).orElseThrow();
    return String.format("""
        Analyze this user:
        Name: %s
        Email: %s
        Created: %s
        Last Active: %s
        """,
        user.getName(),
        user.getEmail(),
        user.getCreatedAt(),
        user.getLastActive()
    );
}
```

---

### @McpInput

Marks a method parameter as an MCP input.

**Location:** Method parameter (on methods annotated with `@McpTool`, `@McpResource`, or `@McpPrompt`)

**Syntax:**
```java
@McpTool
public Order getOrder(
    @McpInput(description = "The order ID") String orderId,
    @McpInput(required = false) String sortBy
) {
    // ...
}
```

**Attributes:**

| Attribute | Type | Default | Required | Description |
|-----------|------|---------|----------|-------------|
| `description` | String | "" | No | Parameter description for clients |
| `name` | String | Parameter name | No | Override parameter name |
| `required` | boolean | true | No | Whether this parameter is required |
| `sensitive` | boolean | false | No | Mark as sensitive (excluded from logs/metrics) |

**With Jakarta Bean Validation:**

```java
@McpTool
public User createUser(
    @McpInput @NotBlank(message = "Name required") String name,
    @McpInput @Email String email,
    @McpInput @Size(min = 8, max = 128) String password,
    @McpInput @Min(18) @Max(120) Integer age
) {
    // Validation happens automatically before execution
}
```

**Sensitive Parameters:**

```java
@McpTool
public AuthResult authenticate(
    @McpInput String username,
    @McpInput(sensitive = true) String password,
    @McpInput(sensitive = true) String apiKey
) {
    // password and apiKey won't appear in logs or metrics
}
```

**Optional Parameters:**

```java
@McpTool
public List<Order> searchOrders(
    @McpInput String customerId,  // Required
    @McpInput(required = false) String status,  // Optional
    @McpInput(required = false) LocalDate startDate  // Optional
) {
    // Handle null values for optional parameters
}
```

---

## REST Endpoints

All endpoints use JSON-RPC 2.0 format.

### GET /mcp/server-info

Get server metadata and capabilities.

**Request:**
```bash
curl http://localhost:8090/mcp/server-info
```

**Response:**
```json
{
  "name": "My Service",
  "version": "1.0.0",
  "capabilities": {
    "tools": {},
    "resources": {},
    "prompts": {}
  }
}
```

---

### POST /mcp/tools/list

List all available tools.

**Request:**
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "tools/list"
}
```

**Response:**
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "tools": [
      {
        "name": "create_order",
        "description": "Create a new order",
        "inputSchema": {
          "type": "object",
          "properties": {
            "productId": { "type": "string" },
            "quantity": { "type": "integer" }
          },
          "required": ["productId", "quantity"]
        }
      }
    ]
  }
}
```

---

### POST /mcp/tools/call

Call a tool with arguments.

**Request:**
```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "method": "tools/call",
  "params": {
    "name": "create_order",
    "arguments": {
      "productId": "PROD-123",
      "quantity": 5
    }
  }
}
```

**Response (Success):**
```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "result": {
    "content": [
      {
        "type": "text",
        "text": "{\"orderId\":\"ORD-456\",\"status\":\"CREATED\"}"
      }
    ]
  }
}
```

**Response (Validation Error):**
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
          "parameter": "productId",
          "message": "must not be blank"
        }
      ]
    }
  }
}
```

---

### POST /mcp/resources/list

List all available resources.

**Request:**
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "resources/list"
}
```

**Response:**
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "resources": [
      {
        "uri": "order://{orderId}",
        "name": "order",
        "description": "Order details by ID",
        "mimeType": "application/json"
      }
    ]
  }
}
```

---

### POST /mcp/resources/read

Read a resource by URI.

**Request:**
```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "method": "resources/read",
  "params": {
    "uri": "order://ORD-123"
  }
}
```

**Response:**
```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "result": {
    "contents": [
      {
        "uri": "order://ORD-123",
        "mimeType": "application/json",
        "text": "{\"id\":\"ORD-123\",\"status\":\"COMPLETED\",\"total\":99.99}"
      }
    ]
  }
}
```

---

### POST /mcp/prompts/list

List all available prompts.

**Request:**
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "prompts/list"
}
```

**Response:**
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "prompts": [
      {
        "name": "order_summary",
        "description": "Generate order summary",
        "arguments": [
          {
            "name": "orderId",
            "description": "The order ID",
            "required": true
          }
        ]
      }
    ]
  }
}
```

---

### POST /mcp/prompts/get

Get a prompt template with arguments.

**Request:**
```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "method": "prompts/get",
  "params": {
    "name": "order_summary",
    "arguments": {
      "orderId": "ORD-123"
    }
  }
}
```

**Response:**
```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "result": {
    "messages": [
      {
        "role": "user",
        "content": "Generate summary for Order: ORD-123\n..."
      }
    ]
  }
}
```

---

## Configuration Properties

**Property:** `server.port`
- **Type:** integer
- **Default:** 8090
- **Description:** Port for MCP server endpoints

**Property:** `mcp.server.enabled`
- **Type:** boolean
- **Default:** true
- **Description:** Enable/disable MCP endpoints globally

**Property:** `mcp.server.port`
- **Type:** integer
- **Default:** 8090
- **Description:** MCP server port (mirrors server.port)

**Property:** `mcp.server.name`
- **Type:** String
- **Default:** "spring-boot-mcp-companion"
- **Description:** Server name returned by server-info endpoint

**Property:** `mcp.server.version`
- **Type:** String
- **Default:** "1.0.0"
- **Description:** Server version

**Property:** `mcp.server.base-path`
- **Type:** String
- **Default:** "/mcp"
- **Description:** Base path for all MCP endpoints

**Example Configuration:**
```yaml
server:
  port: 8090                  # MCP server port (default: 8090)

mcp:
  server:
    enabled: true
    port: 8090
    name: "Order Service"
    version: "2.0.0"
    base-path: /api/mcp
```

---

## Error Codes

| Code | Meaning | HTTP Status |
|------|---------|-------------|
| -32700 | Parse error | 400 |
| -32600 | Invalid Request | 400 |
| -32601 | Method not found | 404 |
| -32602 | Invalid params | 400 |
| -32603 | Internal error | 500 |
| -32000 to -32099 | Server error | 500 |

---

## Type Support

Automatically supported types:

- **Primitives:** `int`, `long`, `float`, `double`, `boolean`, `char`
- **Wrappers:** `Integer`, `Long`, `Float`, `Double`, `Boolean`, `Character`
- **Strings:** `String`
- **Numbers:** `BigDecimal`, `BigInteger`
- **Dates:** `LocalDate`, `LocalDateTime`, `LocalTime`, `ZonedDateTime`
- **Collections:** `List<T>`, `Set<T>`, `Map<K,V>`
- **Enums:** Any `Enum` type
- **Custom Classes:** Any POJO with getters/setters
- **Records:** Java records with components

---

See [EXAMPLES.md](EXAMPLES.md) for working examples and [ADVANCED.md](ADVANCED.md) for advanced patterns.

# Quickstart Guide: Spring Boot MCP Annotation Framework

**Feature**: 001-mcp-annotation-bootstrap
**Date**: 2026-03-27

---

## What This Framework Does

This framework lets you expose existing Spring Boot service methods as an MCP
(Model Context Protocol) server — without changing any existing code. Add one
dependency, add annotations, and your application becomes a discoverable MCP server.

---

## Prerequisites

- Existing Spring Boot 3.x+ application (project uses 4.0.5)
- Java 17+ (project uses 25)
- Maven or Gradle
- Spring Security configured (recommended for production MCP exposure)

---

## Step 1: Add the Dependency

Add the MCP companion dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>com.raynermendez</groupId>
    <artifactId>spring-boot-mcp-companion</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

That's the only code change required to any existing file.

---

## Step 2: Annotate an Existing Method

Find any existing `@Service` or `@RestController` method. Add `@McpTool`:

```java
// EXISTING code — no changes to class structure, no new imports needed
// except the single @McpTool annotation import.
@Service
public class OrderService {

    // Existing method — HTTP behavior unchanged
    public Order getOrder(String orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    // Same method, now also an MCP tool — zero changes to the method body
    @McpTool(description = "Retrieves an order by its identifier")
    public Order getMcpOrder(@McpInput(description = "The order ID") String orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }
}
```

> **Note**: You do NOT have to add `@McpTool` to the same method that handles HTTP.
> Add it to any existing method, or to a new dedicated method. Both approaches work.

---

## Step 3: Configure (Optional)

The framework works with zero configuration. Optionally, customize via `application.yml`:

```yaml
mcp:
  server:
    enabled: true              # default: true
    name: "My Order Service"   # default: spring.application.name
    version: "1.0.0"           # default: application version
    base-path: /mcp            # default: /mcp
```

---

## Step 4: Start the Application

```bash
./mvnw spring-boot:run
```

---

## Step 5: Verify

Check that the tool is discoverable:

```bash
curl -X POST http://localhost:8080/mcp/tools/list \
     -H "Content-Type: application/json" \
     -d '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}'
```

Expected response:
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "tools": [
      {
        "name": "get_mcp_order",
        "description": "Retrieves an order by its identifier",
        "inputSchema": {
          "type": "object",
          "properties": {
            "orderId": { "type": "string", "description": "The order ID" }
          },
          "required": ["orderId"]
        }
      }
    ]
  }
}
```

Invoke the tool:
```bash
curl -X POST http://localhost:8080/mcp/tools/call \
     -H "Content-Type: application/json" \
     -d '{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"get_mcp_order","arguments":{"orderId":"ORD-001"}}}'
```

---

## Disabling MCP Entirely

Set one property:

```yaml
mcp:
  server:
    enabled: false
```

No MCP endpoints are registered. All existing HTTP endpoints continue working.

---

## Exposing a Resource

```java
@Service
public class CatalogService {

    @McpResource(
        uri = "products://{productId}",
        description = "Fetches a product by ID",
        mimeType = "application/json"
    )
    public Product getProduct(@McpInput(name = "productId") String id) {
        return productRepository.findById(id).orElseThrow();
    }
}
```

Read the resource:
```bash
curl -X POST http://localhost:8080/mcp/resources/read \
     -H "Content-Type: application/json" \
     -d '{"jsonrpc":"2.0","id":3,"method":"resources/read","params":{"uri":"products://PROD-42"}}'
```

---

## Exposing a Prompt

```java
@Service
public class SupportService {

    @McpPrompt(
        name = "order_summary_prompt",
        description = "Generates a summary prompt for a given order"
    )
    public PromptResult summarizeOrder(
        @McpInput(name = "orderId", description = "Order to summarize") String orderId
    ) {
        Order order = orderService.getOrder(orderId);
        return PromptResult.of(
            "Please summarize order " + orderId + " with status " + order.getStatus()
        );
    }
}
```

---

## Observability (Zero Configuration)

All invocations automatically emit:
- Structured log entry at INFO level with tool name, status, and duration
- Micrometer counter: `mcp.tool.invocations` (tags: `tool-name`, `status`)
- Micrometer timer: `mcp.tool.duration` (tag: `tool-name`)

Check metrics:
```bash
curl http://localhost:8080/actuator/metrics/mcp.tool.invocations
```

Check health:
```bash
curl http://localhost:8080/actuator/health
# Shows mcpServer component with tool/resource/prompt counts
```

---

## Validation

Add Jakarta Validation constraints to method parameters. They are enforced
automatically at the MCP boundary:

```java
@McpTool(description = "Search orders by customer email")
public List<Order> searchOrders(
    @McpInput(description = "Customer email")
    @NotBlank @Email String email,

    @McpInput(description = "Max results", required = false)
    @Min(1) @Max(100) Integer limit
) {
    return orderRepository.findByEmail(email, limit != null ? limit : 20);
}
```

Calling this tool with a blank email returns a structured validation error —
the `searchOrders` method is never called.

---

## Sensitive Field Redaction

```java
@McpTool(description = "Authenticate a user")
public AuthResult authenticate(
    @McpInput(description = "Username") String username,
    @McpInput(description = "Password", sensitive = true) String password
) {
    // password value is NEVER written to logs
    return authService.authenticate(username, password);
}
```

---

## Next Steps

- Review `specs/001-mcp-annotation-bootstrap/contracts/mcp-endpoints.md` for the
  full HTTP contract.
- See `specs/001-mcp-annotation-bootstrap/data-model.md` for the entity model.
- Run the sample app in `src/main/java/.../sample/` for a working demo.

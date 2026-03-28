# Spring Boot MCP Companion - Quick Start Guide

Get started in 5 minutes ⚡

## 1️⃣ Add Dependency

**Maven:**
```xml
<dependency>
  <groupId>com.raynermendez</groupId>
  <artifactId>spring-boot-mcp-companion-core</artifactId>
  <version>1.0.0</version>
</dependency>
```

**Gradle:**
```gradle
implementation 'com.raynermendez:spring-boot-mcp-companion-core:1.0.0'
```

## 2️⃣ Enable Framework

Add `@EnableMcpCompanion` to your main class:

```java
@SpringBootApplication
@EnableMcpCompanion  // ← One annotation!
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}
```

## 3️⃣ Create a Service

Add annotations to your service methods:

```java
@Service
public class OrderService {

    // Expose as a callable tool
    @McpTool(description = "Get order by ID")
    public Order getOrder(@McpInput(required = true) String orderId) {
        return new Order(orderId, "Widget", new BigDecimal("99.99"), "COMPLETED");
    }

    // Expose as a URI resource
    @McpResource(uri = "order://{id}", description = "Order resource")
    public Order getOrderResource(@McpInput(required = true) String id) {
        return getOrder(id);
    }

    // Expose as a prompt template
    @McpPrompt(name = "summary", description = "Order summary")
    public String generateSummary(@McpInput(required = true) String orderId) {
        Order order = getOrder(orderId);
        return "Order: " + order.getId() + " - $" + order.getPrice();
    }
}
```

## 4️⃣ Run Application

```bash
mvn spring-boot:run
```

## 5️⃣ Test MCP Endpoints

**MCP server runs on port 8090 by default**

### List Tools
```bash
curl -X POST http://localhost:8090/mcp/tools/list \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc": "2.0", "id": 1, "method": "tools/list"}'
```

### Call a Tool
```bash
curl -X POST http://localhost:8090/mcp/tools/call \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "tools/call",
    "params": {
      "name": "get_order",
      "arguments": {"orderId": "ORDER-001"}
    }
  }'
```

### List Prompts
```bash
curl -X POST http://localhost:8090/mcp/prompts/list \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc": "2.0", "id": 1, "method": "prompts/list"}'
```

---

## 📖 Core Annotations

### `@McpTool`
Expose method as a callable function.

```java
@McpTool(description = "What it does")
public String doSomething(@McpInput String param) { ... }
```

### `@McpResource`
Expose method as a URI-based resource.

```java
@McpResource(uri = "path://to/{id}", description = "Resource")
public Data getData(@McpInput String id) { ... }
```

### `@McpPrompt`
Expose method as a prompt template.

```java
@McpPrompt(name = "template-name", description = "What it generates")
public String generate(@McpInput String param) { ... }
```

### `@McpInput`
Mark method parameters.

```java
@McpInput(description = "User ID", required = true, sensitive = false)
String userId
```

---

## ⚙️ Configuration

**application.yml:**
```yaml
mcp:
  server:
    enabled: true                # Enable/disable (default: true)
    base-path: /mcp              # Endpoint prefix (default: /mcp)
    name: My Service             # Server name
    version: 1.0.0               # Server version
```

---

## 🔐 Input Validation

Add Jakarta Bean Validation constraints:

```java
@McpTool
public User createUser(
    @McpInput @Email String email,
    @McpInput @Size(min = 8) String password,
    @McpInput @Min(18) Integer age
) { ... }
```

**Available constraints:**
- `@NotNull`, `@NotBlank`
- `@Email`, `@Pattern(regexp)`
- `@Min(n)`, `@Max(n)`
- `@Size(min, max)`
- Any Jakarta Bean Validation constraint

---

## 🚀 Available Endpoints

| Method | Endpoint | Purpose |
|--------|----------|---------|
| GET | `/mcp/server-info` | Server metadata |
| POST | `/mcp/tools/list` | List all tools |
| POST | `/mcp/tools/call` | Call a tool |
| POST | `/mcp/resources/list` | List all resources |
| POST | `/mcp/resources/read` | Read a resource |
| POST | `/mcp/prompts/list` | List all prompts |
| POST | `/mcp/prompts/get` | Invoke a prompt |

---

## 📚 Documentation

- **Full README:** [README.md](README.md)
- **Contributing:** [CONTRIBUTING.md](CONTRIBUTING.md)
- **Security:** [SECURITY.md](SECURITY.md)
- **Changes:** [CHANGELOG.md](CHANGELOG.md)
- **License:** [LICENSE](LICENSE)

---

## ❓ Common Questions

**Q: Does it affect existing endpoints?**
A: No! Your regular Spring Boot controllers work alongside MCP.

**Q: Can I disable MCP per environment?**
A: Yes! Use `mcp.server.enabled=false` in any profile.

**Q: How do I secure my MCP endpoints?**
A: Add Spring Security and configure in `SecurityConfig`. See [SECURITY.md](SECURITY.md).

**Q: Can I use it without Spring Web?**
A: Yes, mark `spring-boot-starter-web` as optional if not needed.

**Q: How are results serialized?**
A: Automatically as JSON via Jackson. Objects become JSON objects.

---

## 🔗 JSON-RPC 2.0 Format

All requests follow JSON-RPC 2.0:

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "tools/call",
  "params": {
    "name": "tool_name",
    "arguments": { "param": "value" }
  }
}
```

Responses:

**Success:**
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": { "content": [...] }
}
```

**Error:**
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "error": {
    "code": -32603,
    "message": "Error description"
  }
}
```

---

## 🎯 Next Steps

1. **Read** the full [README.md](README.md)
2. **Check** [Contributing](CONTRIBUTING.md) if you want to help
3. **Review** [Security](SECURITY.md) for production deployments
4. **Explore** [Examples](README.md#examples) for your use case

---

## 💬 Need Help?

- 📖 Check [README.md Troubleshooting](README.md#troubleshooting)
- 🐛 [Report a bug](https://github.com/yourusername/spring-boot-mcp-companion/issues)
- 💡 [Request a feature](https://github.com/yourusername/spring-boot-mcp-companion/issues)
- 💬 [Start a discussion](https://github.com/yourusername/spring-boot-mcp-companion/discussions)

---

## 📋 Checklist

Getting ready for production?

- [ ] Read README.md
- [ ] Configure application.yml
- [ ] Add input validation constraints
- [ ] Enable Spring Security
- [ ] Configure HTTPS
- [ ] Test all MCP endpoints
- [ ] Review Security.md
- [ ] Setup monitoring/metrics
- [ ] Load test your tools
- [ ] Document your tools for users

---

**Happy MCP'ing! 🚀**

Next: Read [README.md](README.md) for complete documentation.

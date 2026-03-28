# Spring Boot MCP Companion

A lightweight, production-ready annotation-driven framework for integrating the **Model Context Protocol (MCP)** into Spring Boot applications. Expose your Spring beans as remote-callable functions, resources, and prompts with zero refactoring.

[![Maven Central](https://img.shields.io/maven-central/v/com.raynermendez/spring-boot-mcp-companion-core)](https://central.sonatype.com/artifact/com.raynermendez/spring-boot-mcp-companion-core)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/java-17+-green.svg)](https://adoptopenjdk.net/)
[![Spring Boot](https://img.shields.io/badge/spring%20boot-4.0.5+-green.svg)](https://spring.io/projects/spring-boot)

## Quick Overview

Spring Boot MCP Companion lets you expose Spring components as MCP servers with a single annotation:

```java
@SpringBootApplication
@EnableMcpCompanion  // ← That's it!
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}
```

Then annotate your service methods:

```java
@Service
public class OrderService {

    @McpTool(description = "Get order by ID")
    public Order getOrder(@McpInput(required = true) String orderId) {
        return orderRepository.findById(orderId).orElseThrow();
    }

    @McpResource(uri = "order://{id}", description = "Order resource")
    public Order getOrderResource(@McpInput(required = true) String id) {
        return getOrder(id);
    }

    @McpPrompt(name = "summary", description = "Generate order summary")
    public String generateSummary(@McpInput(required = true) String orderId) {
        Order order = getOrder(orderId);
        return "Order: " + order.getId() + " - $" + order.getPrice();
    }
}
```

Done! Your methods are now accessible via JSON-RPC 2.0 MCP endpoints.

## ✨ Key Features

- **Zero Configuration** - One annotation enables full MCP support
- **Convention over Configuration** - Automatic naming, schema generation, validation
- **Type-Safe** - Full Java type support with automatic JSON schema generation
- **Input Validation** - Jakarta Bean Validation at the boundary
- **Observable** - Built-in Micrometer metrics and health checks
- **Production-Ready** - Security, error handling, and best practices included
- **Non-Invasive** - Works alongside your existing Spring Boot code

## 📖 Documentation

| Document | Purpose |
|----------|---------|
| **[QUICK_START.md](QUICK_START.md)** | Get started in 5 minutes |
| **[FEATURES.md](FEATURES.md)** | Detailed feature descriptions |
| **[EXAMPLES.md](EXAMPLES.md)** | Working examples and patterns |
| **[API_REFERENCE.md](API_REFERENCE.md)** | Complete annotation and endpoint reference |
| **[BEST_PRACTICES.md](BEST_PRACTICES.md)** | Production deployment guidelines |
| **[ADVANCED.md](ADVANCED.md)** | Custom validation, security, observability |
| **[TROUBLESHOOTING.md](TROUBLESHOOTING.md)** | Common issues and solutions |
| **[SECURITY.md](SECURITY.md)** | Vulnerability reporting and security info |
| **[CONTRIBUTING.md](CONTRIBUTING.md)** | How to contribute |

## 🚀 Installation

### Maven

```xml
<dependency>
  <groupId>com.raynermendez</groupId>
  <artifactId>spring-boot-mcp-companion-core</artifactId>
  <version>1.0.0</version>
</dependency>
```

### Gradle

```gradle
implementation 'com.raynermendez:spring-boot-mcp-companion-core:1.0.0'
```

## 💡 Core Concepts

### Annotations

- **`@EnableMcpCompanion`** - Enable MCP support on your `@SpringBootApplication` class
- **`@McpTool`** - Expose a method as a remote-callable function/tool
- **`@McpResource`** - Expose a method as a URI-accessible resource
- **`@McpPrompt`** - Expose a method as a prompt template generator
- **`@McpInput`** - Add metadata to method parameters (description, validation, sensitivity)

### Configuration

The MCP server runs on a **separate embedded server** from your main Spring Boot application:

```yaml
server:
  port: 8080                    # Your main API server (default: 8080)

mcp:
  server:
    enabled: true               # Enable/disable MCP (default: true)
    port: 8090                  # MCP server port - SEPARATE from server.port (default: 8090)
    name: "My Service"          # Server name
    version: "1.0.0"            # Server version
    base-path: /mcp             # Endpoint prefix (default: /mcp)
```

This architecture allows:
- Main API on port 8080, MCP on port 8090
- Independent scaling and configuration
- Separate security policies per server
- Isolated monitoring and logging

### MCP Endpoints

All endpoints use JSON-RPC 2.0 format on the MCP server port (**port 8090** by default, configurable):

**Note:** MCP endpoints are on a separate embedded server from your main application.

| Method | Endpoint | Purpose |
|--------|----------|---------|
| GET | `http://localhost:8090/mcp/server-info` | Server metadata |
| POST | `http://localhost:8090/mcp/tools/list` | List available tools |
| POST | `http://localhost:8090/mcp/tools/call` | Call a tool with arguments |
| POST | `http://localhost:8090/mcp/resources/list` | List available resources |
| POST | `http://localhost:8090/mcp/resources/read` | Read a resource |
| POST | `http://localhost:8090/mcp/prompts/list` | List available prompts |
| POST | `http://localhost:8090/mcp/prompts/get` | Get a prompt with arguments |

Your main application API remains on `http://localhost:8080` (or whatever `server.port` is configured)

## 📋 Common Tasks

**Add input validation:**
```java
@McpTool
public User createUser(
    @McpInput @Email String email,
    @McpInput @Size(min = 8) String password,
    @McpInput @Min(18) Integer age
) { ... }
```

**Mark sensitive parameters:**
```java
@McpInput(sensitive = true) String apiKey,
@McpInput(sensitive = true) String password
```

**Configure with Spring Security:**
See [ADVANCED.md](ADVANCED.md#spring-security-integration)

**Add observability/metrics:**
See [ADVANCED.md](ADVANCED.md#observability)

## 📚 Learn More

- [What is MCP?](https://modelcontextprotocol.io/) - Official MCP documentation
- [QUICK_START.md](QUICK_START.md) - 5-minute getting started guide
- [EXAMPLES.md](EXAMPLES.md) - Order management, document processing, and more
- [CONTRIBUTING.md](CONTRIBUTING.md) - Development setup and contribution guidelines

## ⚖️ License

Apache License 2.0 - See [LICENSE](LICENSE) for details.

## 🤝 Contributing

We welcome contributions! See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

---

**Ready to get started?** → [Quick Start Guide](QUICK_START.md)

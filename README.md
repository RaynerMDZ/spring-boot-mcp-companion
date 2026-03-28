# Spring Boot MCP Companion

A lightweight, production-ready annotation-driven framework for integrating the **Model Context Protocol (MCP)** into Spring Boot applications. Expose your Spring beans as remote-callable functions, resources, and prompts with zero refactoring.

[![Maven Central](https://img.shields.io/maven-central/v/com.raynermendez/spring-boot-mcp-companion-core)](https://central.sonatype.com/artifact/com.raynermendez/spring-boot-mcp-companion-core)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/java-25+-green.svg)](https://adoptopenjdk.net/)
[![Spring Boot](https://img.shields.io/badge/spring%20boot-4.0.5+-green.svg)](https://spring.io/projects/spring-boot)

## Quick Start

```java
@SpringBootApplication
@EnableMcpCompanion  // That's it!
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}

@Service
public class OrderService {
    @McpTool(description = "Get order by ID")
    public Order getOrder(@McpInput String orderId) {
        return orderRepository.findById(orderId).orElseThrow();
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

All documentation is in the **[docs/](docs/)** folder:

- **[docs/index.md](docs/index.md)** - Start here for navigation by role and topic
- **[docs/getting-started/](docs/getting-started/)** - Quick start and project overview
- **[docs/core/](docs/core/)** - API reference, features, and examples
- **[docs/production/](docs/production/)** - Best practices, security, and troubleshooting
- **[docs/contributing/](docs/contributing/)** - Contributing guide and changelog

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

## 📋 Architecture

The MCP server runs on a **separate embedded server** from your main Spring Boot application:

```yaml
server:
  port: 8080          # Main API server

mcp:
  server:
    port: 8090        # MCP server (separate)
    enabled: true
```

- Main API on port 8080
- MCP server on port 8090
- Independent configuration and security

## 🤝 Contributing

See [docs/contributing/CONTRIBUTING.md](docs/contributing/CONTRIBUTING.md) for guidelines.

## ⚖️ License

Apache License 2.0 - See [LICENSE](LICENSE) for details.

---

**📚 [Read the full documentation](docs/index.md)**

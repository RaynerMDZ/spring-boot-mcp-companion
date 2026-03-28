# Spring Boot MCP Companion - Documentation Hub

Welcome to the Spring Boot MCP Companion documentation. This is your central hub for everything you need to integrate the Model Context Protocol (MCP) into your Spring Boot applications.

---

## 🚀 Quick Start

**Get started in 5 minutes:**
- **[Quick Start Guide](QUICK_START.md)** - Add dependency, enable framework, create service, test endpoints

**Just want the essentials?**
- **[README](README.md)** - Project overview, architecture, features, and core concepts

---

## 📖 Documentation by Role

### 👨‍💻 For Java Developers

Start here if you're building MCP tools, resources, and prompts in your Spring Boot application:

1. **[Quick Start](QUICK_START.md)** - Get running in 5 minutes
2. **[Features](FEATURES.md)** - Detailed feature descriptions and capabilities
3. **[API Reference](API_REFERENCE.md)** - Complete annotation and endpoint reference
4. **[Examples](EXAMPLES.md)** - Working code examples for common scenarios
5. **[Best Practices](BEST_PRACTICES.md)** - Production-ready patterns and guidelines
6. **[Advanced](ADVANCED.md)** - Security, caching, transactions, async patterns
7. **[Troubleshooting](TROUBLESHOOTING.md)** - Common issues and solutions

### 🏗️ For Architects & DevOps

Planning deployment and infrastructure for MCP Companion:

1. **[Features](FEATURES.md)** - Architecture and two-server design overview
2. **[Best Practices](BEST_PRACTICES.md)** - Production deployment checklist
3. **[Security](SECURITY.md)** - Security requirements and best practices
4. **[Advanced](ADVANCED.md)** - Observability, metrics, and monitoring
5. **[Troubleshooting](TROUBLESHOOTING.md)** - Common deployment issues

### 🤝 For Contributors

Want to contribute to the project?

1. **[Contributing](CONTRIBUTING.md)** - Development setup and contribution guidelines
2. **[Changelog](CHANGELOG.md)** - Version history and roadmap
3. **[Security](SECURITY.md)** - Vulnerability reporting process

---

## 📚 Documentation by Topic

### Getting Started

| Document | Purpose | Read Time |
|----------|---------|-----------|
| [README](README.md) | Project overview, architecture, quick navigation | 5 min |
| [Quick Start](QUICK_START.md) | 5-minute setup guide with hello-world example | 5 min |
| [Features](FEATURES.md) | Complete feature overview and capabilities | 10 min |

### Core Concepts

| Document | Purpose | Read Time |
|----------|---------|-----------|
| [API Reference](API_REFERENCE.md) | Complete annotation reference and JSON-RPC endpoints | 15 min |
| [Examples](EXAMPLES.md) | 4+ working examples: Order management, document processing, user services | 20 min |
| [Best Practices](BEST_PRACTICES.md) | 10 patterns: naming, validation, error handling, performance, security | 20 min |

### Production & Operations

| Document | Purpose | Read Time |
|----------|---------|-----------|
| [Advanced](ADVANCED.md) | Spring Security, caching, transactions, async patterns, observability | 25 min |
| [Security](SECURITY.md) | Security best practices, vulnerability reporting, compliance | 10 min |
| [Troubleshooting](TROUBLESHOOTING.md) | 12+ issues with solutions: ports, validation, performance, CORS, testing | 20 min |

### Contributing

| Document | Purpose | Read Time |
|----------|---------|-----------|
| [Contributing](CONTRIBUTING.md) | Development setup, build process, contribution guidelines | 10 min |
| [Changelog](CHANGELOG.md) | Version history and roadmap | 5 min |

---

## 🎯 Common Tasks

### I want to...

**Build my first MCP tool**
→ [Quick Start](QUICK_START.md) + [Examples: Order Service](EXAMPLES.md#example-1-order-management-service)

**Secure my MCP endpoints**
→ [Advanced: Spring Security](ADVANCED.md#spring-security-integration) + [Security](SECURITY.md)

**Deploy to production**
→ [Best Practices: Production Deployment](BEST_PRACTICES.md#10-production-deployment) + [Advanced: Observability](ADVANCED.md#observability)

**Add input validation**
→ [Best Practices: Input Validation](BEST_PRACTICES.md#2-input-validation) + [API Reference: @McpInput](API_REFERENCE.md#mcpinput)

**Improve performance**
→ [Best Practices: Performance](BEST_PRACTICES.md#7-performance) + [Troubleshooting: Performance Issues](TROUBLESHOOTING.md#6-performance-issues)

**Debug issues**
→ [Troubleshooting](TROUBLESHOOTING.md) (use Ctrl+F to search for your error)

**Report a bug or security issue**
→ [Contributing](CONTRIBUTING.md) or [Security: Vulnerability Reporting](SECURITY.md#vulnerability-reporting)

**Understand the architecture**
→ [README: Architecture](README.md#-core-concepts) + [Features: Two-Server Design](FEATURES.md#-flexible-configuration)

---

## 🏗️ Architecture Overview

Spring Boot MCP Companion uses a **two-server architecture**:

```
┌─────────────────────────────────────────┐
│         Your Spring Boot App            │
│                                         │
│   Main API Server      MCP Server       │
│   :8080                :8090            │
│   (server.port)    (mcp.server.port)    │
│                                         │
│   @RestController     @McpTool          │
│   /api/...            /mcp/tools/call   │
│   /health/...         /mcp/prompts/get  │
│                                         │
└─────────────────────────────────────────┘
```

**Key Points:**
- Main app runs on `server.port` (default: 8080)
- MCP server runs on separate embedded Tomcat on `mcp.server.port` (default: 8090)
- Both servers run in the same JVM process
- Complete isolation of configuration and security policies
- Independent scaling and monitoring

See [Best Practices: Configuration](BEST_PRACTICES.md#10-production-deployment) for production setup.

---

## 📋 Feature Checklist

- ✅ Zero-friction integration with one `@EnableMcpCompanion` annotation
- ✅ Convention over configuration - sensible defaults for everything
- ✅ Type-safe with automatic JSON Schema generation from Java types
- ✅ Input validation with Jakarta Bean Validation annotations
- ✅ Three types of capabilities: Tools, Resources, Prompts
- ✅ Production observability with Micrometer metrics and Spring Actuator
- ✅ Backward compatible - doesn't interfere with existing Spring code
- ✅ REST endpoints with JSON-RPC 2.0 format
- ✅ Flexible configuration per environment
- ✅ No external dependencies - uses Spring's built-in components
- ✅ Security ready with Spring Security integration
- ✅ Comprehensive documentation and examples

---

## 📊 Documentation Statistics

| Document | Lines | Focus |
|----------|-------|-------|
| README | 185 | Overview and quick navigation |
| QUICK_START | 300 | 5-minute setup guide |
| FEATURES | 310 | Feature descriptions |
| API_REFERENCE | 400+ | Complete API reference |
| EXAMPLES | 500+ | Working code examples |
| BEST_PRACTICES | 600 | Production patterns |
| ADVANCED | 400+ | Advanced topics |
| TROUBLESHOOTING | 539 | Common issues and solutions |
| SECURITY | 250+ | Security best practices |
| CONTRIBUTING | 200+ | Contribution guidelines |
| CHANGELOG | 150+ | Version history |

**Total Documentation:** 3,800+ lines of comprehensive guides and references

---

## 🔗 Related Resources

- **[Model Context Protocol (MCP)](https://modelcontextprotocol.io/)** - Official MCP documentation
- **[Spring Boot](https://spring.io/projects/spring-boot)** - Spring Boot official site
- **[Jakarta Bean Validation](https://jakarta.ee/specifications/bean-validation/)** - Jakarta EE standard
- **[Micrometer](https://micrometer.io/)** - Metrics collection
- **[JSON-RPC 2.0 Specification](https://www.jsonrpc.org/specification)** - JSON-RPC spec

---

## 💬 Getting Help

1. **Check [Troubleshooting](TROUBLESHOOTING.md)** - 12+ common issues with solutions
2. **Search [Best Practices](BEST_PRACTICES.md)** - Production patterns and anti-patterns
3. **Review [Examples](EXAMPLES.md)** - Working code for your use case
4. **Report an issue** - [Contributing](CONTRIBUTING.md#reporting-issues)

---

## 🤝 Contributing

Found a bug? Want to improve documentation? Have a feature idea?

See [Contributing](CONTRIBUTING.md) for:
- Development setup and build process
- How to submit issues and pull requests
- Code style and testing requirements
- Vulnerability reporting

---

## 📄 License

Apache License 2.0 - See [LICENSE](../LICENSE) for details.

---

**Ready to get started?** → [Quick Start Guide](QUICK_START.md)

**Want to understand the architecture?** → [README](README.md#-core-concepts) + [Features](FEATURES.md#-flexible-configuration)

**Building for production?** → [Best Practices](BEST_PRACTICES.md#10-production-deployment) + [Security](SECURITY.md)

# Documentation Navigation Guide

**Lost? Don't know where to find what you need?** This guide helps you find the right documentation for your role.

---

## Quick Navigation by Role

### 👨‍💻 Java Developers

**I want to...**

| Task | Start Here | Then Read |
|------|-----------|-----------|
| Get started in 5 minutes | [Quick Start](getting-started/QUICK_START.md) | — |
| Learn what this framework does | [README](../README.md#key-features) | [Features](core/FEATURES.md) |
| Annotate my first method as an MCP tool | [API Reference](core/API_REFERENCE.md#mcptool) | [Examples](core/EXAMPLES.md) |
| Add input validation to my endpoints | [API Reference](core/API_REFERENCE.md#mcpinput) | [Examples: Validation](core/EXAMPLES.md#input-validation) |
| Handle custom Java types (POJOs) | [Custom Objects](CUSTOM_OBJECTS.md) | [Examples: Custom Types](core/EXAMPLES.md#custom-types) |
| Deploy to production | [Best Practices](production/BEST_PRACTICES.md) | [Troubleshooting](production/TROUBLESHOOTING.md) |
| Debug an issue | [Troubleshooting](production/TROUBLESHOOTING.md) | [Advanced](production/ADVANCED.md) |
| See working code | [Examples](core/EXAMPLES.md) | — |
| Learn about security | [Security Guide](production/SECURITY.md) | [Best Practices](production/BEST_PRACTICES.md#security) |

### 🏗️ Software Architects

**I want to...**

| Task | Start Here | Then Read |
|------|-----------|-----------|
| Understand the system design | [Architecture](ARCHITECTURE.md) | [README: Project Architecture](../README.md#project-architecture) |
| Evaluate for production use | [Best Practices](production/BEST_PRACTICES.md) | [Advanced Topics](production/ADVANCED.md) |
| Review security posture | [Security Guide](production/SECURITY.md) | [Architecture: Security](ARCHITECTURE.md#security-architecture) |
| Understand type mapping | [Architecture: Type Mapping](ARCHITECTURE.md#type-mapping-engine) | [Custom Objects](CUSTOM_OBJECTS.md) |
| Plan integrations | [Features](core/FEATURES.md) | [Advanced: Integrations](production/ADVANCED.md) |
| Performance characteristics | [Architecture: Performance](ARCHITECTURE.md#performance-considerations) | [Best Practices: Performance](production/BEST_PRACTICES.md#performance) |
| Extensibility points | [Architecture: Extensibility](ARCHITECTURE.md#extensibility-points) | [Advanced: Custom Extensions](production/ADVANCED.md#custom-extensions) |

### 🤝 Contributors

**I want to...**

| Task | Start Here | Then Read |
|------|-----------|-----------|
| Set up development environment | [Contributing](contributing/CONTRIBUTING.md) | — |
| Understand project structure | [Architecture](ARCHITECTURE.md) | [README: Project Structure](../README.md#project-structure) |
| Find open issues to work on | GitHub Issues | [Contributing](contributing/CONTRIBUTING.md#finding-issues) |
| Understand version history | [Changelog](contributing/CHANGELOG.md) | — |
| Make a contribution | [Contributing](contributing/CONTRIBUTING.md#making-contributions) | [Contributing: PR Guidelines](contributing/CONTRIBUTING.md#pr-guidelines) |
| Review licensing | [License Analysis](contributing/LICENSE_ANALYSIS.md) | [LICENSE](../LICENSE) |

### 🏢 DevOps & Operations

**I want to...**

| Task | Start Here | Then Read |
|------|-----------|-----------|
| Deploy to production | [Best Practices](production/BEST_PRACTICES.md) | [Advanced: Deployment](production/ADVANCED.md#deployment) |
| Configure monitoring | [Best Practices: Observability](production/BEST_PRACTICES.md#observability) | [Architecture: Monitoring](ARCHITECTURE.md#monitoring--observability) |
| Set up security | [Security Guide](production/SECURITY.md) | [Architecture: Security](ARCHITECTURE.md#security-architecture) |
| Configure application | [README: Configuration](../README.md#configuration) | [Getting Started: README](getting-started/README.md#core-concepts-configuration) |
| Debug production issues | [Troubleshooting](production/TROUBLESHOOTING.md) | [Advanced: Debugging](production/ADVANCED.md#debugging) |
| Containerize the application | [Best Practices: Containerization](production/BEST_PRACTICES.md#containerization) | — |
| Scale horizontally | [Advanced: Scalability](production/ADVANCED.md#scalability) | [Best Practices](production/BEST_PRACTICES.md) |

---

## Documentation by Topic

### 📚 Getting Started

| Document | Audience | Time | Purpose |
|----------|----------|------|---------|
| [README](../README.md) | Everyone | 10 min | Project overview, installation, quick start |
| [Quick Start](getting-started/QUICK_START.md) | Developers | 5 min | Step-by-step setup guide |
| [Project Overview](getting-started/README.md) | Developers | 15 min | Architecture, core concepts, endpoints |
| [MCP Specification](MCP_SPECIFICATION.md) | Architects | 20 min | MCP protocol compliance details |

### 💡 Core Learning

| Document | Audience | Level | Topics |
|----------|----------|-------|--------|
| [API Reference](core/API_REFERENCE.md) | Developers | Intermediate | Annotations, endpoints, properties |
| [Features](core/FEATURES.md) | Everyone | Beginner | Overview of framework capabilities |
| [Examples](core/EXAMPLES.md) | Developers | Beginner-Intermediate | Working code snippets, patterns |
| [Custom Objects](CUSTOM_OBJECTS.md) | Developers | Intermediate | Custom type mapping, serialization |

### 🏭 Production & Operations

| Document | Audience | Time | Topics |
|----------|----------|------|--------|
| [Best Practices](production/BEST_PRACTICES.md) | Architects, DevOps | 30 min | 10 production patterns, guidelines |
| [Advanced Topics](production/ADVANCED.md) | Architects | 45 min | Security, async, caching, observability |
| [Security Guide](production/SECURITY.md) | Security, Architects | 30 min | Threat model, mitigations, guidelines |
| [Troubleshooting](production/TROUBLESHOOTING.md) | Operations, Developers | Variable | Common issues, solutions, debugging |

### 🏗️ System Design

| Document | Audience | Level | Content |
|----------|----------|-------|---------|
| [Architecture](ARCHITECTURE.md) | Architects | Advanced | System design, components, flows |

### 🤝 Contributing

| Document | Audience | Time | Content |
|----------|----------|------|---------|
| [Contributing](contributing/CONTRIBUTING.md) | Contributors | 20 min | Setup, guidelines, PR process |
| [Changelog](contributing/CHANGELOG.md) | Everyone | Variable | Version history, releases |
| [License Analysis](contributing/LICENSE_ANALYSIS.md) | Contributors | 10 min | Licensing details, dependencies |

---

## Documentation Map (by File)

```
docs/
├── README.md                          # ← You are here
├── DOCS_NAVIGATION.md                 # ← This file
├── ARCHITECTURE.md                    # System design & components
├── MCP_SPECIFICATION.md               # MCP protocol compliance
├── CUSTOM_OBJECTS.md                  # Custom type mapping
│
├── getting-started/
│   ├── README.md                      # Architecture & core concepts
│   └── QUICK_START.md                 # 5-minute setup guide
│
├── core/
│   ├── API_REFERENCE.md               # Complete API reference
│   ├── FEATURES.md                    # Feature descriptions
│   └── EXAMPLES.md                    # Code examples
│
├── production/
│   ├── BEST_PRACTICES.md              # Production deployment patterns
│   ├── ADVANCED.md                    # Advanced topics
│   ├── SECURITY.md                    # Security guidelines
│   └── TROUBLESHOOTING.md             # Common issues & solutions
│
└── contributing/
    ├── CONTRIBUTING.md                # Contribution guidelines
    ├── CHANGELOG.md                   # Version history
    └── LICENSE_ANALYSIS.md            # Licensing information

../
├── README.md                          # Main project README
├── pom.xml                            # Maven configuration
└── src/                               # Source code
    ├── main/java/                     # Production code
    └── test/java/                     # Tests
```

---

## Quick Answer Guide

### "I have a question about..."

#### Annotations

- `@EnableMcpCompanion` → [API Reference: EnableMcpCompanion](core/API_REFERENCE.md#enablemcpcompanion)
- `@McpTool` → [API Reference: McpTool](core/API_REFERENCE.md#mcptool)
- `@McpResource` → [API Reference: McpResource](core/API_REFERENCE.md#mcpresource)
- `@McpPrompt` → [API Reference: McpPrompt](core/API_REFERENCE.md#mcpprompt)
- `@McpInput` → [API Reference: McpInput](core/API_REFERENCE.md#mcpinput)
- `@CustomObject` → [Custom Objects](CUSTOM_OBJECTS.md)

#### Configuration

- Setting up MCP server → [Getting Started: Configuration](getting-started/README.md#configuration)
- Application properties → [README: Configuration](../README.md#configuration)
- Spring Security setup → [Advanced: Spring Security](production/ADVANCED.md#spring-security-integration)

#### Type Support

- Supported types → [Architecture: Supported Type Conversions](ARCHITECTURE.md#supported-type-conversions)
- Custom types → [Custom Objects](CUSTOM_OBJECTS.md)
- Generic types → [Architecture: JSON Schema Generation](ARCHITECTURE.md#json-schema-generation)

#### Validation

- Input validation → [Features: Input Validation](core/FEATURES.md#input-validation)
- Validation constraints → [Architecture: Validation Architecture](ARCHITECTURE.md#validation-architecture)
- Custom validators → [Advanced: Custom Validators](production/ADVANCED.md#custom-validators)

#### Security

- Security overview → [Security Guide](production/SECURITY.md)
- Threat model → [Architecture: Security Architecture](ARCHITECTURE.md#security-architecture)
- Best practices → [Best Practices: Security](production/BEST_PRACTICES.md#security)

#### Observability & Monitoring

- Metrics → [Architecture: Monitoring](ARCHITECTURE.md#monitoring--observability)
- Logging → [Advanced: Logging](production/ADVANCED.md#logging)
- Health checks → [Best Practices: Observability](production/BEST_PRACTICES.md#observability)

#### Performance

- Performance tuning → [Advanced: Performance](production/ADVANCED.md#performance-optimization)
- Benchmarks → [Architecture: Performance](ARCHITECTURE.md#performance-considerations)
- Scaling → [Advanced: Scalability](production/ADVANCED.md#scalability)

#### Troubleshooting

- Common issues → [Troubleshooting](production/TROUBLESHOOTING.md)
- Debugging → [Advanced: Debugging](production/ADVANCED.md#debugging)
- Error handling → [Architecture: Error Handling](ARCHITECTURE.md#error-handling--exception-strategy)

#### Examples & Patterns

- Code examples → [Examples](core/EXAMPLES.md)
- Real-world use cases → [README: Common Use Cases](../README.md#common-use-cases)
- Integration patterns → [Advanced: Integration Patterns](production/ADVANCED.md#integration-patterns)

#### Contributing

- How to contribute → [Contributing](contributing/CONTRIBUTING.md)
- Development setup → [Contributing: Setup](contributing/CONTRIBUTING.md#development-setup)
- PR process → [Contributing: Making Contributions](contributing/CONTRIBUTING.md#making-contributions)

---

## Learning Paths

### Path 1: 5-Minute Quick Start

Perfect for: Developers who want to get up and running quickly

1. Read: [README: Quick Start (5 Minutes)](../README.md#quick-start-5-minutes) - 5 min
2. Read: [Quick Start Guide](getting-started/QUICK_START.md) - 5 min

**Total time: 10 minutes**

**Next step:** Try [Examples](core/EXAMPLES.md) for more patterns

---

### Path 2: Complete Developer Onboarding

Perfect for: New team members joining the project

1. Read: [README](../README.md) - 10 min
2. Read: [Project Overview](getting-started/README.md) - 15 min
3. Read: [API Reference](core/API_REFERENCE.md) - 20 min
4. Read: [Examples](core/EXAMPLES.md) - 15 min
5. Read: [Best Practices](production/BEST_PRACTICES.md) - 20 min
6. Do: Set up development environment - 30 min

**Total time: ~110 minutes (1.5-2 hours)**

**Outcome:** Ready to develop features

---

### Path 3: Architect Evaluation

Perfect for: Architects evaluating the framework for adoption

1. Read: [README](../README.md) - 10 min
2. Read: [Architecture](ARCHITECTURE.md) - 30 min
3. Read: [Features](core/FEATURES.md) - 15 min
4. Read: [Security Guide](production/SECURITY.md) - 20 min
5. Read: [Best Practices](production/BEST_PRACTICES.md) - 20 min

**Total time: ~95 minutes (1.5 hours)**

**Outcome:** Complete understanding of system design and production readiness

---

### Path 4: Production Deployment

Perfect for: DevOps and operations engineers preparing for production

1. Read: [Best Practices](production/BEST_PRACTICES.md) - 20 min
2. Read: [Security Guide](production/SECURITY.md) - 20 min
3. Read: [Configuration](../README.md#configuration) - 10 min
4. Read: [Advanced: Deployment](production/ADVANCED.md#deployment) - 15 min
5. Read: [Advanced: Observability](production/ADVANCED.md#observability) - 15 min

**Total time: ~80 minutes**

**Outcome:** Ready to deploy to production safely and securely

---

### Path 5: Security Deep Dive

Perfect for: Security engineers and architects

1. Read: [Security Guide](production/SECURITY.md) - 25 min
2. Read: [Architecture: Security](ARCHITECTURE.md#security-architecture) - 20 min
3. Read: [Best Practices: Security](production/BEST_PRACTICES.md#security) - 15 min
4. Read: [Advanced: Threat Mitigation](production/ADVANCED.md) - 20 min

**Total time: ~80 minutes**

**Outcome:** Deep understanding of security posture and mitigations

---

## Finding the Right Answer

### Search Tips

**If you're looking for:**
- Specific annotation details → [API Reference](core/API_REFERENCE.md)
- How to do something → [Examples](core/EXAMPLES.md)
- Best way to do something → [Best Practices](production/BEST_PRACTICES.md)
- Why something doesn't work → [Troubleshooting](production/TROUBLESHOOTING.md)
- How it's implemented → [Architecture](ARCHITECTURE.md)
- Permission to use → [License Analysis](contributing/LICENSE_ANALYSIS.md)

### Document Density

| Document | Density | Best For |
|----------|---------|----------|
| [README](../README.md) | Low | Quick overview |
| [Examples](core/EXAMPLES.md) | Low-Medium | Learning patterns |
| [Features](core/FEATURES.md) | Medium | Understanding capabilities |
| [Best Practices](production/BEST_PRACTICES.md) | Medium-High | Production decisions |
| [Architecture](ARCHITECTURE.md) | Very High | Deep technical details |
| [API Reference](core/API_REFERENCE.md) | Very High | Precise specifications |

---

## Feedback & Improvements

**Found this guide helpful?** Have suggestions? [Open an issue](https://github.com/RaynerMDZ/spring-boot-mcp-companion/issues)

**Want to contribute documentation improvements?** See [Contributing Guide](contributing/CONTRIBUTING.md)

---

**[← Back to main README](../README.md)**

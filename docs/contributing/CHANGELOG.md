# Changelog

All notable changes to the Spring Boot MCP Companion project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Planned for v1.1.0
- [ ] Streaming response support
- [ ] Batch operation utilities
- [ ] Custom error handling middleware
- [ ] WebSocket transport (experimental)

### Planned for v1.2.0
- [ ] Built-in rate limiting
- [ ] Request/response caching
- [ ] Performance optimization (compression)
- [ ] Multi-tenant support

## [1.0.0] - 2026-03-27

### Added

#### Core Framework Features
- **@EnableMcpCompanion** - Single-annotation framework enablement
- **@McpTool** - Expose methods as remote-callable functions
- **@McpResource** - Expose URI-based data resources
- **@McpPrompt** - Expose reusable prompt templates
- **@McpInput** - Parameter metadata and validation

#### Protocol Support
- Full JSON-RPC 2.0 implementation
- `GET /mcp/server-info` - Server metadata endpoint
- `POST /mcp/tools/list` - List available tools
- `POST /mcp/tools/call` - Invoke tools with arguments
- `POST /mcp/resources/list` - List available resources
- `POST /mcp/resources/read` - Read resource by URI
- `POST /mcp/prompts/list` - List available prompts
- `POST /mcp/prompts/get` - Invoke prompt template

#### Input Validation
- Jakarta Bean Validation integration
- Automatic JSON schema generation from Java types
- Constraint validation (`@Email`, `@Min`, `@Max`, `@Size`, etc.)
- Required/optional parameter handling
- Sensitive parameter redaction in logs

#### Observability
- Micrometer metrics integration
- Tool invocation counters and timers
- Structured logging with SLF4J
- Spring Boot Health indicator
- Actuator endpoints for monitoring

#### Configuration
- Application properties via `application.yml`
- Property source flexibility (env vars, system properties)
- Customizable base path and server metadata
- Enable/disable framework per deployment

#### Spring Integration
- Spring Boot auto-configuration
- SmartInitializingSingleton bean scanning
- AOP aspect-based metrics/logging
- Spring Security ready (optional)
- Component scanning for annotated beans
- Transactional support

#### Documentation
- Comprehensive README with examples
- Multiple use case examples (orders, documents, config)
- API reference
- Security best practices guide
- Performance optimization tips
- Troubleshooting section

#### Testing
- 66 comprehensive unit and integration tests
- Test coverage for all core features:
  - Annotation discovery and registration
  - Parameter extraction and validation
  - Schema generation
  - Tool invocation
  - Error handling
  - Configuration binding
- @SpringBootTest integration tests
- Mocking support with Mockito

#### Build & Distribution
- Maven Central publishing configuration
- Proper POM metadata
- Source and Javadoc JAR generation
- GPG signing support
- Distribution management setup

### Technical Details

#### Supported Java Versions
- Java 25 (tested and verified)
- Java 17+ (minimum Spring Boot 4.0.5 requirement)

#### Supported Spring Boot Versions
- Spring Boot 4.0.5 (tested and verified)
- Spring Boot 4.x compatible

#### Dependencies
- Spring Boot Core
- Spring Web (optional)
- Spring Actuator (optional)
- Spring AOP
- Jackson Databind
- Jakarta Bean Validation
- SLF4J

### Known Limitations
- WebSocket transport not yet implemented (planned for v1.1)
- Streaming responses not yet supported (planned for v1.1)
- Single-tenant only (multi-tenant planned for v2.0)

### Performance
- Sub-50ms response time for typical tools
- Automatic caching ready (configure with Spring Cache)
- Connection pooling recommended for database operations
- Horizontal scaling via stateless design

### Security
- Input validation at MCP boundary
- Sensitive parameter redaction
- Spring Security compatible
- HTTPS ready (configure in application properties)
- Error messages don't expose internal details

### Breaking Changes
None (first release)

---

## Future Versions

### v1.1.0 (Planned Q3 2026)
- Streaming response support for long-running operations
- Batch operation utilities
- Custom error handling middleware
- WebSocket transport (experimental)
- Performance metrics dashboards

### v1.2.0 (Planned Q4 2026)
- Built-in rate limiting
- Request/response caching layer
- Compression support
- Multi-tenant support (beta)
- Additional built-in validators

### v2.0.0 (Planned 2027)
- Multi-tenant support (stable)
- Advanced security features
- GraphQL support
- gRPC transport option
- Distributed tracing integration

---

## How to Upgrade

### From 0.0.1-SNAPSHOT to 1.0.0

No breaking changes. Simply update your pom.xml:

```xml
<!-- Before -->
<dependency>
  <groupId>com.raynermendez</groupId>
  <artifactId>spring-boot-mcp-companion</artifactId>
  <version>0.0.1-SNAPSHOT</version>
</dependency>

<!-- After -->
<dependency>
  <groupId>com.raynermendez</groupId>
  <artifactId>spring-boot-mcp-companion-core</artifactId>
  <version>1.0.0</version>
</dependency>
```

Note: Module name changed from `spring-boot-mcp-companion` to `spring-boot-mcp-companion-core`.

---

## Support

For questions, issues, or suggestions regarding specific versions:
- [GitHub Issues](https://github.com/yourusername/spring-boot-mcp-companion/issues)
- [GitHub Discussions](https://github.com/yourusername/spring-boot-mcp-companion/discussions)

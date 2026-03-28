# Implementation Plan: Spring Boot MCP Annotation Bootstrap

**Branch**: `001-mcp-annotation-bootstrap` | **Date**: 2026-03-27 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/001-mcp-annotation-bootstrap/spec.md`

## Summary

Bootstrap a single-module Spring Boot application that provides an MCP
(Model Context Protocol) annotation mapping framework. The framework discovers
Spring beans annotated with `@McpTool`, `@McpResource`, and `@McpPrompt`,
maps them into MCP-compatible capability descriptors, and exposes them through
a configurable HTTP transport — all without requiring changes to existing
business logic or application structure.

The runtime is Spring Boot 4.0.5 / Java 25, using Spring AI MCP Server as the
underlying MCP protocol runtime and a custom annotation-scanning layer built
on top of it.

## Technical Context

**Language/Version**: Java 25 (Spring Boot 4.0.5 / Java 25)
**Primary Dependencies**:
- `spring-boot-starter-web` — HTTP transport layer
- `spring-boot-starter-validation` — Jakarta Bean Validation at MCP boundary
- `spring-boot-starter-actuator` — Micrometer metrics, health indicators
- `spring-boot-starter-aop` — Cross-cutting observability and validation interception
- `spring-boot-configuration-processor` — IDE metadata generation for `@ConfigurationProperties`
- `io.modelcontextprotocol.sdk:mcp` — MCP protocol types and JSON-RPC structures
- `com.fasterxml.jackson.core:jackson-databind` — JSON serialization for MCP payloads
- `lombok` — Already present in pom.xml
- `spring-boot-starter-test` — Full test stack (JUnit 5, Mockito, MockMvc)

**Storage**: None (in-memory registry only; no database)
**Testing**: JUnit 5 + Mockito (unit), `@WebMvcTest` (slice), `@SpringBootTest` (integration)
**Target Platform**: Spring Boot web application (embedded Tomcat/Jetty); Linux server / macOS dev
**Project Type**: Library / integration framework (single-module app, extractable to starter later)
**Performance Goals**: MCP annotation scanning completes in < 500ms at startup for up to 500 annotated methods; individual tool invocation overhead (framework overhead only) < 5ms p95
**Constraints**: No reflection abuse (use Spring's `AnnotationUtils`); no classpath bytecode scanning outside Spring context; `jakarta.*` namespace (not `javax.*`)
**Scale/Scope**: Single-module bootstrap; designed to support later extraction to `spring-boot-mcp-companion-starter` artifact

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

| Principle | Status | Evidence |
|-----------|--------|---------|
| I. Zero-Friction Integration | ✅ PASS | Framework uses `SmartInitializingSingleton` + annotation scanning; adopters add dependency + annotations only |
| II. Annotation-Driven Config | ✅ PASS | All mapping via `@McpTool`/`@McpResource`/`@McpPrompt`; server config via `@ConfigurationProperties` |
| III. Backward Compatibility | ✅ PASS | No AOP advice on existing annotated methods directly; MCP dispatch is additive; HTTP endpoints untouched |
| IV. Secure Defaults | ✅ PASS | `matchIfMissing=true` enables server by default; all MCP endpoints require auth (delegated to Spring Security); input validation before method dispatch |
| V. Observability | ✅ PASS | AOP `@Around` advice on dispatcher emits Micrometer counter+timer + SLF4J structured log per invocation |
| VI. Validation at MCP Boundary | ✅ PASS | Jakarta Bean Validation constraints on `@McpInput` parameters are enforced before method invocation; violations return structured JSON-RPC error |
| VII. Extensibility via SPI | ✅ PASS | `McpToolRegistry`, `McpMappingEngine`, and transport layer have SPI interfaces; extensions registered as Spring `@Bean`s |

**Quality Gates** (must be verified in implementation):
- [ ] Backward-compat check: existing tests pass without modification after framework added
- [ ] Zero-config smoke test: `@McpTool` on one method → valid tool descriptor, no extra config
- [ ] Security gate: unauthenticated MCP requests rejected by default (with Spring Security present)
- [ ] Observability gate: tool invocation → metric counter incremented + log entry present in test
- [ ] Validation gate: invalid input → structured MCP error, method NOT called

**Complexity Tracking**: No violations. Single-module project with no pattern complexity concerns.

## Project Structure

### Documentation (this feature)

```text
specs/001-mcp-annotation-bootstrap/
├── plan.md              # This file
├── research.md          # Phase 0 — SDK and pattern decisions
├── data-model.md        # Phase 1 — Entity model and registry structure
├── quickstart.md        # Phase 1 — Adopter getting-started guide
├── contracts/
│   └── mcp-endpoints.md # Phase 1 — HTTP contract and annotation API
└── tasks.md             # Phase 2 — /speckit.tasks output (not yet generated)
```

### Source Code (repository root)

```text
src/
├── main/
│   ├── java/
│   │   └── com/raynermendez/spring_boot_mcp_companion/
│   │       ├── SpringBootMcpCompanionApplication.java  (existing — no changes)
│   │       │
│   │       ├── annotation/                             # Public annotation API
│   │       │   ├── McpTool.java
│   │       │   ├── McpResource.java
│   │       │   ├── McpPrompt.java
│   │       │   └── McpInput.java
│   │       │
│   │       ├── config/                                 # Configuration model
│   │       │   ├── McpServerProperties.java            # @ConfigurationProperties("mcp.server")
│   │       │   └── McpAutoConfiguration.java           # @AutoConfiguration + @Conditional*
│   │       │
│   │       ├── model/                                  # Domain entities (in-memory)
│   │       │   ├── McpToolDefinition.java
│   │       │   ├── McpResourceDefinition.java
│   │       │   ├── McpPromptDefinition.java
│   │       │   ├── McpParameterDefinition.java
│   │       │   ├── McpPromptArgument.java
│   │       │   └── MethodHandlerRef.java
│   │       │
│   │       ├── registry/                               # Central capability registry
│   │       │   ├── McpDefinitionRegistry.java          # Interface + thread-safe state machine
│   │       │   └── DefaultMcpDefinitionRegistry.java   # Default implementation
│   │       │
│   │       ├── scanner/                                # Annotation discovery
│   │       │   └── McpAnnotationScanner.java           # SmartInitializingSingleton (per-bean logic is internal)
│   │       │
│   │       ├── mapper/                                 # Annotation → MCP definition mapping
│   │       │   ├── McpMappingEngine.java               # Interface
│   │       │   ├── DefaultMcpMappingEngine.java        # Core mapping logic
│   │       │   └── JsonSchemaGenerator.java            # Java type → JSON Schema
│   │       │
│   │       ├── validation/                             # MCP boundary validation
│   │       │   ├── McpInputValidator.java              # Interface
│   │       │   └── DefaultMcpInputValidator.java       # Jakarta Validation integration
│   │       │
│   │       ├── dispatch/                               # Tool/resource/prompt invocation
│   │       │   ├── McpDispatcher.java                  # Interface
│   │       │   └── DefaultMcpDispatcher.java           # Reflective invocation + error handling
│   │       │
│   │       ├── observability/                          # Cross-cutting observability
│   │       │   ├── McpObservabilityAspect.java         # AOP @Around for metrics + logging
│   │       │   └── McpHealthIndicator.java             # Actuator HealthIndicator
│   │       │
│   │       ├── transport/                              # HTTP protocol layer
│   │       │   └── McpTransportController.java         # @RestController for MCP endpoints
│   │       │
│   │       ├── spi/                                    # Extension point interfaces
│   │       │   └── McpOutputSerializer.java            # Custom result → MCP content
│   │       │
│   │       └── sample/                                 # Demo + integration test fixture
│   │           ├── SampleOrderService.java             # @McpTool, @McpResource, @McpPrompt
│   │           └── SampleOrderController.java          # @RestController (unchanged behavior)
│   │
│   └── resources/
│       ├── application.yml                              # Default config (skeleton)
│       └── META-INF/
│           └── spring/
│               └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
│
└── test/
    └── java/
        └── com/raynermendez/spring_boot_mcp_companion/
            ├── SpringBootMcpCompanionApplicationTests.java  (existing)
            ├── annotation/                               # Annotation unit tests
            ├── scanner/                                  # Scanner unit tests
            ├── mapper/                                   # Mapping engine unit tests
            ├── validation/                               # Validator unit tests
            ├── dispatch/                                 # Dispatcher unit tests
            ├── observability/                            # Metrics + logging tests
            ├── transport/                                # @WebMvcTest slice tests
            └── integration/                              # @SpringBootTest full integration tests
```

**Structure Decision**: Single-module Spring Boot application. All MCP framework
code lives alongside the application entry point in the same Maven module. The
`sample/` package provides a real, runnable demo of all three annotation types.
No structural changes to `SpringBootMcpCompanionApplication.java` are needed.

## Implementation Phases

### Phase A: Foundation

Establish the annotation definitions, configuration model, and auto-configuration
skeleton. This phase makes the framework "addable" to any project.

**Deliverables**:
- `@McpTool`, `@McpResource`, `@McpPrompt`, `@McpInput` annotation types
- `McpServerProperties` record with `@ConfigurationProperties("mcp.server")`
- `McpAutoConfiguration` with `@ConditionalOnProperty(matchIfMissing = true)`
- `AutoConfiguration.imports` registration file
- Updated `pom.xml` with required dependencies
- Unit tests for `McpServerProperties` binding

**Constitution gates covered**: I (addable without changes), II (annotation API), IV (enabled by default)

---

### Phase B: Registry and Scanner

Implement the `McpDefinitionRegistry` state machine and the `McpAnnotationScanner`
that populates it at startup.

**Deliverables**:
- `McpDefinitionRegistry` interface + `DefaultMcpDefinitionRegistry` (thread-safe, immutable after startup)
- `McpAnnotationScanner` implementing `SmartInitializingSingleton`
- Duplicate tool name detection → `BeanCreationException` at startup
- Unit tests: scanner discovers annotated beans; duplicate name fails fast
- Unit tests: registry state transitions (EMPTY → BUILDING → READY / FAILED)

**Constitution gates covered**: I (zero new code in adopter classes), III (additive only)

---

### Phase C: Mapping Engine and Schema Generation

Convert annotated method metadata into `McpToolDefinition` / `McpResourceDefinition` /
`McpPromptDefinition` instances, including JSON Schema generation from Java types.

**Deliverables**:
- `McpMappingEngine` interface + `DefaultMcpMappingEngine`
- `JsonSchemaGenerator` for Java type → JSON Schema object (primitive types, collections, POJOs)
- Bean Validation constraint → JSON Schema property mapping (`@NotNull` → required, `@Size` → minLength, etc.)
- Default name derivation (method name → snake_case) when annotation `name` is blank
- Unit tests: each Java type maps to the correct JSON Schema type
- Unit tests: each Bean Validation constraint produces the correct schema property

**Constitution gates covered**: II (annotation-driven schema), VI (constraints honored in schema)

---

### Phase D: Validation and Dispatch

Implement input validation at the MCP boundary and reflective method dispatch.

**Deliverables**:
- `McpInputValidator` interface + `DefaultMcpInputValidator` (Jakarta Validation integration)
- `McpDispatcher` interface + `DefaultMcpDispatcher` (reflective invocation via `MethodHandlerRef`)
- Void return type handling (returns empty MCP content)
- Runtime exception → structured MCP `isError: true` response (no 500 propagation)
- Unit tests: missing required field → validation error, method not called
- Unit tests: constraint violation → structured error with `violations` array
- Unit tests: valid input → method invoked, result serialized
- Unit tests: method throws runtime exception → `isError: true` response

**Constitution gates covered**: IV (validation before business logic), VI (boundary enforcement)

---

### Phase E: HTTP Transport Layer

Implement the `McpTransportController` exposing all MCP endpoints under the configured base path.

**Deliverables**:
- `McpTransportController` (`@RestController`) with endpoints:
  - `POST {basePath}/tools/list`
  - `POST {basePath}/tools/call`
  - `POST {basePath}/resources/list`
  - `POST {basePath}/resources/read`
  - `POST {basePath}/prompts/list`
  - `POST {basePath}/prompts/get`
  - `GET {basePath}/server-info`
- JSON-RPC 2.0 request/response envelope handling
- `@ConditionalOnProperty` — controller not registered when `mcp.server.enabled=false`
- `@WebMvcTest` slice tests for each endpoint (happy path + error cases)
- `@WebMvcTest` test: `enabled=false` → 404 for all MCP paths

**Constitution gates covered**: I (base path configurable, no conflict with existing paths), III (existing HTTP behavior unchanged)

---

### Phase F: Observability

Add automatic metrics and structured logging for all MCP operations.

**Deliverables**:
- `McpObservabilityAspect` (`@Aspect`) wrapping `McpDispatcher.dispatch*` methods
- Micrometer counters: `mcp.tool.invocations`, `mcp.resource.reads`, `mcp.prompt.resolutions`
- Micrometer timer: `mcp.tool.duration`, `mcp.resource.duration`, `mcp.prompt.duration`
- SLF4J structured log entry per invocation (tool name, status, duration; no param values by default)
- Sensitive param redaction in logs when `@McpInput(sensitive=true)`
- `McpHealthIndicator` (Actuator `HealthIndicator`) reporting registry state + counts
- `@ConditionalOnClass(MeterRegistry.class)` guard for metrics beans
- Unit tests: invocation → counter incremented, timer recorded
- Unit tests: sensitive param → value NOT present in log output
- Integration test: `GET /actuator/health` returns `mcpServer: UP` with counts

**Constitution gates covered**: V (automatic observability), IV (sensitive field redaction)

---

### Phase G: Sample API and Integration Tests

Provide a runnable sample annotated API and full end-to-end integration tests.

**Deliverables**:
- `SampleOrderService` with `@McpTool`, `@McpResource`, and `@McpPrompt` examples
- `SampleOrderController` with `@RestController` endpoints (demonstrate no interference)
- `@SpringBootTest` integration tests:
  - Tools list returns sample tool descriptors
  - Tool invocation returns correct result
  - Resource read returns correct content
  - Prompt resolution returns messages array
  - Existing HTTP endpoints still return correct responses (backward-compat gate)
  - Metrics counter incremented after tool invocation (observability gate)
  - Invalid input → validation error response (validation gate)

**Constitution gates covered**: All 6 quality gates verified by integration tests

---

### Phase H: Configuration Metadata and IDE Support

Generate `spring-configuration-metadata.json` for IDE auto-complete.

**Deliverables**:
- `spring-boot-configuration-processor` configured in `pom.xml` `annotationProcessorPaths`
- All `McpServerProperties` fields annotated with correct Javadoc for metadata generation
- Manual verification: `target/classes/META-INF/spring-configuration-metadata.json` generated
- IDE auto-complete test documented in `quickstart.md`

**Constitution gates covered**: II (IDE-friendly config), I (developer experience)

---

## Dependency Notes

Add to `pom.xml` (in addition to existing dependencies):

```xml
<!-- Web + Validation + Actuator + AOP -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>

<!-- Configuration metadata generation (compile-scope annotation processor) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-configuration-processor</artifactId>
    <optional>true</optional>
</dependency>

<!-- Spring AI MCP Server (Spring AI 1.0.0; imported via spring-ai-bom) -->
<dependency>
    <groupId>io.modelcontextprotocol</groupId>
    <artifactId>mcp</artifactId>
    <version>0.1.0</version>
</dependency>
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
</dependency>
```

## Risk Register

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|-----------|
| Spring AI MCP starter version incompatible with Spring Boot 4.0.5 | Medium | High | Verify compatibility matrix; fallback to implementing MCP transport manually if needed |
| AOP proxy prevents annotation discovery on proxied beans | Low | Medium | Use `AopUtils.getTargetClass()` and `AnnotationUtils.findAnnotation()` which handle proxy transparency |
| `-parameters` compiler flag absent in some adopter projects | Low | Low | Fall back to `@McpInput(name=...)` explicit naming; document requirement |
| MCP protocol version evolves breaking backward compat | Low | Medium | Pin SDK version; isolate protocol types behind internal adapters |

## Open Questions (Resolved)

All questions resolved in `research.md`. No open items.

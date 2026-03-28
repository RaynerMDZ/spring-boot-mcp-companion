# Research: Spring Boot MCP Annotation Bootstrap

**Phase**: 0 — Research
**Feature**: 001-mcp-annotation-bootstrap
**Date**: 2026-03-27

---

## Decision 1: MCP Protocol Runtime

**Decision**: Use the official MCP Java SDK (`io.modelcontextprotocol.sdk:mcp`) as the
protocol type library, combined with the Spring AI MCP Server starter
(`org.springframework.ai:spring-ai-mcp-server-spring-boot-starter`) as the
Spring Boot–integrated MCP runtime.

**Rationale**:
- The official SDK (`io.modelcontextprotocol.sdk`) provides Java record types for all
  MCP protocol structures (tool descriptors, resource descriptors, prompt descriptors,
  JSON-RPC request/response shapes). Using these canonical types ensures protocol
  compliance and eliminates hand-rolled parsing.
- The Spring AI MCP Server starter wraps the official SDK with Spring Boot auto-
  configuration, provides `McpSyncServer` / `McpAsyncServer` beans, handles
  transport (Streamable HTTP and SSE), and exposes a `ToolCallbackProvider` SPI
  for registering tools. This gives us the full MCP server runtime without writing
  transport code.
- Our annotation mapper sits above this layer: it discovers `@McpTool`-annotated
  methods, converts them into `ToolCallback` implementations, and registers them
  with Spring AI's `ToolCallbackProvider`. Similarly for resources and prompts.

**Maven coordinates** (approximate — verify against Maven Central at build time):
```xml
<!-- Official MCP Java SDK (protocol types) — included transitively via Spring AI MCP -->
<dependency>
    <groupId>io.modelcontextprotocol.sdk</groupId>
    <artifactId>mcp</artifactId>
    <!-- version managed by Spring AI BOM -->
</dependency>

<!-- Spring AI MCP Server Spring Boot Starter -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-mcp-server-spring-boot-starter</artifactId>
    <!-- version: align with Spring AI milestone / GA matching Spring Boot 3.x/4.x -->
</dependency>
```

**Alternatives considered**:
- *Hand-rolled HTTP endpoints only*: Maximum control, zero external dependencies,
  but requires implementing full JSON-RPC 2.0 framing, Streamable HTTP transport,
  SSE keep-alive, and capability negotiation from scratch. Not viable for production
  without significant protocol-level investment.
- *Direct use of Spring AI MCP without a custom annotation layer*: Spring AI MCP
  already supports `@Tool`-annotated methods on `@Service` beans. However, it requires
  adopters to import Spring AI throughout their codebase, and its annotation model
  is tightly coupled to Spring AI's own tool-calling conventions. Our custom
  annotation layer is thinner, Spring Boot–specific, and designed for zero-friction
  adoption in non-Spring-AI codebases.

---

## Decision 2: Bean Scanning Strategy

**Decision**: Use `SmartInitializingSingleton` (implemented in a Spring `@Component`)
to scan the fully initialized `ApplicationContext` for beans carrying MCP annotations,
rather than `BeanPostProcessor`.

**Rationale**:
- `BeanPostProcessor` runs during bean initialization, before AOP proxies are fully
  applied. Scanning at this stage can result in inspecting the raw target object rather
  than the final proxy, causing annotation lookups to miss proxy-wrapped methods.
- `SmartInitializingSingleton.afterSingletonsInstantiated()` is called after all
  singleton beans — including AOP proxies — are fully initialized but before
  `ApplicationReadyEvent` fires. This is the correct window for discovering annotated
  beans in their final, proxy-aware form.
- `ApplicationContext.getBeansWithAnnotation()` and `AnnotationUtils.findAnnotation()`
  are used for annotation discovery, respecting Spring's annotation inheritance and
  proxy transparency semantics.

**Alternatives considered**:
- *`BeanPostProcessor`*: Too early; AOP proxies not yet applied. Rejected.
- *`ApplicationReadyEvent` listener*: Works, but fires after the HTTP server is
  accepting traffic, creating a race where MCP endpoints could receive requests
  before the registry is populated. `SmartInitializingSingleton` is earlier and safer.
- *Classpath scanning via `ClassPathScanningCandidateComponentProvider`*: Scans
  bytecode rather than live beans; cannot see Spring-managed proxy state or runtime
  configuration. Rejected.

---

## Decision 3: Configuration Model

**Decision**: Use `@ConfigurationProperties(prefix = "mcp.server")` records (Java 16+
records) annotated with Bean Validation constraints, registered via
`@EnableConfigurationProperties` in the auto-configuration class, with
`spring-boot-configuration-processor` generating `spring-configuration-metadata.json`
for IDE auto-complete.

**Rationale**:
- Java records with `@ConfigurationProperties` are the idiomatic Spring Boot 3+/4.x
  pattern for immutable externalized configuration.
- Bean Validation constraints on the record components (e.g., `@NotBlank`, `@Min`)
  provide fail-fast validation at startup if misconfigured.
- `spring-boot-configuration-processor` reads `@ConfigurationProperties` classes at
  compile time and generates `META-INF/spring-configuration-metadata.json`, enabling
  IDE property completion and documentation in YAML/properties editors.

**Configuration namespace**: `mcp.server.*` for server-level settings,
`mcp.tool.*` for tool-level defaults.

**Alternatives considered**:
- *`@Value` injection*: No type safety, no metadata generation, no validation.
  Rejected.
- *Traditional `@ConfigurationProperties` POJO (non-record)*: Works in Spring Boot 3+
  but records are preferred for immutability and conciseness. Records used.

---

## Decision 4: Auto-Configuration Pattern

**Decision**: Provide one primary auto-configuration class (`McpAutoConfiguration`)
annotated with `@AutoConfiguration(after = SpringApplicationAutoConfiguration.class)`
and `@ConditionalOnProperty(prefix = "mcp.server", name = "enabled", matchIfMissing = true)`.
Register it in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.

**Rationale**:
- `@ConditionalOnProperty(matchIfMissing = true)` implements the "server enabled by
  default, disabled by setting one property" requirement (SC-007).
- The `AutoConfiguration.imports` file is the Spring Boot 3+/4.x standard for
  registering auto-configurations (replaces `spring.factories` for auto-configuration).
- Keeping all conditional beans in one `@AutoConfiguration` class makes the feature
  toggle atomic: one property disables the entire subsystem.

**Alternatives considered**:
- *`@Configuration` with manual `@Import`*: Requires adopter to add `@Import` to their
  application class. Violates zero-friction principle. Rejected.
- *`spring.factories` key `EnableAutoConfiguration`*: Deprecated in Spring Boot 3.x
  in favor of `AutoConfiguration.imports`. Rejected.

---

## Decision 5: Observability Implementation

**Decision**: Use Spring Boot Actuator's `MeterRegistry` (Micrometer) for metrics
and Slf4j structured logging for log entries. Wrap MCP tool dispatch in a Spring AOP
`@Around` advice that records invocation count, latency, and error status per tool name.

**Rationale**:
- Micrometer (`MeterRegistry`) is the standard Spring Boot metrics abstraction.
  Counters and timers registered at dispatch time auto-expose via `/actuator/metrics`.
- AOP `@Around` advice on all annotated method dispatches provides a single
  interception point for observability without modifying tool handler code.
- `@ConditionalOnClass(MeterRegistry.class)` makes metrics conditional on Actuator
  being on the classpath, preserving the optional dependency design.

**Metric names** (Micrometer convention):
- `mcp.tool.invocations` (counter, tags: tool-name, status=[success|error])
- `mcp.tool.duration` (timer, tags: tool-name)
- `mcp.resource.reads` (counter, tags: resource-uri-template, status)
- `mcp.prompt.resolutions` (counter, tags: prompt-name, status)

**Alternatives considered**:
- *Per-tool `@Timed` annotation*: Requires each adopter to annotate their methods.
  Violates zero-friction principle. Rejected.
- *Manual HTTP filter*: Cannot distinguish individual tool names from a single HTTP
  endpoint. Rejected in favor of AOP dispatch interception.

---

## Decision 6: Project Runtime (Actual)

**Decision**: Target Java 25 and Spring Boot 4.0.5 (as found in the existing `pom.xml`).

**Rationale**:
- The project's `pom.xml` already specifies `spring-boot-starter-parent 4.0.5` and
  `java.version=25`. These are the actual runtime constraints, superseding the
  spec's assumption of Spring Boot 3.x / Java 17.
- Spring Boot 4.x is built on Spring Framework 7.x and Jakarta EE 10; it requires
  Java 17 minimum but Java 25 is fully compatible.
- The constitution's "Spring Boot 3.x minimum" constraint is satisfied (4.x ≥ 3.x).

**Impact on design**:
- Use `jakarta.*` namespace (not `javax.*`) for validation annotations.
- Use Java records for `@ConfigurationProperties`.
- `AutoConfiguration.imports` file is the correct registration mechanism.

---

## Decision 7: AOP Interception Pattern

**Decision**: Use a Spring AOP `@Aspect` bean with an `@Around` pointcut targeting
`@annotation(com.raynermendez.spring_boot_mcp_companion.annotation.McpTool)` (and
equivalent pointcuts for `@McpResource` and `@McpPrompt`) for cross-cutting
observability and validation concerns.

**Rationale**:
- Spring AOP (proxy-based) is sufficient for method-level interception on Spring
  beans. Full AspectJ (bytecode weaving) is not needed.
- The `@Around` advice wraps the dispatch call (not the original annotated method
  directly) — the registry calls the method reflectively, and the AOP aspect wraps
  the registry dispatch method. This keeps the original methods clean and avoids
  pointcut-on-proxy issues.
- `spring-boot-starter-aop` is already a required dependency per the spec.

**Alternatives considered**:
- *`HandlerInterceptor` (Spring MVC)*: Only applicable at the HTTP layer; cannot
  intercept at the MCP tool dispatch level. Rejected.
- *Manual try/catch in MappingEngine*: Works but not extensible; duplicates
  observability logic across tool/resource/prompt dispatchers. Rejected.

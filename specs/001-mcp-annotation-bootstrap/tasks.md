# Tasks: Spring Boot MCP Annotation Bootstrap

**Input**: Design documents from `/specs/001-mcp-annotation-bootstrap/`
**Prerequisites**: plan.md ✅ spec.md ✅ research.md ✅ data-model.md ✅ contracts/ ✅ quickstart.md ✅

**Organization**: Tasks are grouped by user story to enable independent implementation
and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3, US4)
- All paths relative to repository root

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Update `pom.xml` and establish the full package structure required by
the implementation plan before any feature work begins.

- [x] T001 Add `spring-boot-starter-web`, `spring-boot-starter-validation`, `spring-boot-starter-actuator`, `spring-boot-starter-aop` dependencies to `pom.xml`
- [x] T002 Add to `pom.xml`: (a) `spring-boot-configuration-processor` as `<optional>true</optional>` dependency; (b) add `io.modelcontextprotocol:mcp` (version 0.1.0) and `com.fasterxml.jackson.core:jackson-databind` dependencies; (c) add `spring-boot-configuration-processor` to `<annotationProcessorPaths>` in `maven-compiler-plugin` alongside Lombok — ensures `spring-configuration-metadata.json` is generated from Phase 1 onward
- [x] T005 Rename `src/main/resources/application.properties` to `application.yml` and add skeleton `mcp.server.*` properties (commented out)
- [x] T006 Create `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` file (empty placeholder)

**Checkpoint**: `./mvnw compile` succeeds with all new dependencies resolved.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core building blocks that ALL user stories depend on. Must complete before
any user story phase begins.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

### Annotation Types

- [x] T007 [P] Create `@McpTool` annotation in `src/main/java/com/raynermendez/spring_boot_mcp_companion/annotation/McpTool.java` with `name`, `description`, `tags` attributes per contracts/mcp-endpoints.md
- [x] T008 [P] Create `@McpResource` annotation in `src/main/java/com/raynermendez/spring_boot_mcp_companion/annotation/McpResource.java` with `uri`, `name`, `description`, `mimeType` attributes
- [x] T009 [P] Create `@McpPrompt` annotation in `src/main/java/com/raynermendez/spring_boot_mcp_companion/annotation/McpPrompt.java` with `name`, `description` attributes
- [x] T010 [P] Create `@McpInput` parameter annotation in `src/main/java/com/raynermendez/spring_boot_mcp_companion/annotation/McpInput.java` with `name`, `description`, `required`, `sensitive` attributes

### Domain Model

- [x] T011 [P] Create `McpParameterDefinition` record in `src/main/java/com/raynermendez/spring_boot_mcp_companion/model/McpParameterDefinition.java` per data-model.md
- [x] T012 [P] Create `MethodHandlerRef` record in `src/main/java/com/raynermendez/spring_boot_mcp_companion/model/MethodHandlerRef.java` per data-model.md
- [x] T013 [P] Create `McpToolDefinition` record in `src/main/java/com/raynermendez/spring_boot_mcp_companion/model/McpToolDefinition.java` per data-model.md
- [x] T014 [P] Create `McpResourceDefinition` record in `src/main/java/com/raynermendez/spring_boot_mcp_companion/model/McpResourceDefinition.java` per data-model.md
- [x] T015 [P] Create `McpPromptDefinition` record in `src/main/java/com/raynermendez/spring_boot_mcp_companion/model/McpPromptDefinition.java` with `McpPromptArgument` nested type per data-model.md

### Configuration

- [x] T016 Create `McpServerProperties` record in `src/main/java/com/raynermendez/spring_boot_mcp_companion/config/McpServerProperties.java` with `@ConfigurationProperties("mcp.server")` — fields: `enabled` (default `true`), `name`, `version`, `basePath` (default `/mcp`), `transport` per data-model.md
- [x] T017 Create `McpAutoConfiguration` class in `src/main/java/com/raynermendez/spring_boot_mcp_companion/config/McpAutoConfiguration.java` with `@AutoConfiguration`, `@EnableConfigurationProperties(McpServerProperties.class)`, and `@ConditionalOnProperty(prefix="mcp.server", name="enabled", matchIfMissing=true)` — no beans yet, just the skeleton
- [x] T018 Register `McpAutoConfiguration` in `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

### Registry

- [x] T019 Create `McpDefinitionRegistry` interface in `src/main/java/com/raynermendez/spring_boot_mcp_companion/registry/McpDefinitionRegistry.java` with `register(McpToolDefinition)`, `register(McpResourceDefinition)`, `register(McpPromptDefinition)`, `getTools()`, `getResources()`, `getPrompts()`, `getState()` methods
- [x] T020 Create `DefaultMcpDefinitionRegistry` in `src/main/java/com/raynermendez/spring_boot_mcp_companion/registry/DefaultMcpDefinitionRegistry.java` implementing `McpDefinitionRegistry` with `RegistryState` enum (`EMPTY`, `BUILDING`, `READY`, `FAILED`), thread-safe `ConcurrentHashMap` storage, duplicate-name detection throwing `IllegalStateException`, and `lock()` method to transition to `READY`
- [x] T021 Write unit tests for `DefaultMcpDefinitionRegistry` in `src/test/java/com/raynermendez/spring_boot_mcp_companion/registry/DefaultMcpDefinitionRegistryTest.java` — cover: duplicate tool name fails, state transitions, registry is immutable after lock()

**Checkpoint**: All model/annotation/config/registry types compile and registry tests pass. User story phases can now begin.

### Security Gate (Constitution QG3 — Required Before US1)

- [ ] T021.5 [P] Add `spring-boot-starter-security` as an `<optional>true</optional>` dependency in `pom.xml` — this brings Spring Security onto the classpath for the security gate test without forcing adopters to include it
- [ ] T021.6 Create `McpSecurityAutoConfiguration` in `src/main/java/com/raynermendez/spring_boot_mcp_companion/config/McpSecurityAutoConfiguration.java` with `@AutoConfiguration`, `@ConditionalOnClass(SecurityFilterChain.class)`, and `@ConditionalOnMissingBean(McpSecurityConfigurer.class)`: registers a default `SecurityFilterChain` that requires authentication for all `${mcp.server.base-path}/**` paths; also emits a `WARN` log at startup if Spring Security is NOT on the classpath (constitution Principle IV: security must never be silently permissive)
- [ ] T021.7 [P] Write `@WebMvcTest` security test in `src/test/java/com/raynermendez/spring_boot_mcp_companion/transport/McpSecurityGateTest.java` — cover: unauthenticated `POST /mcp/tools/list` returns 401/403; unauthenticated `POST /mcp/tools/call` returns 401/403; authenticated request (mocked) passes through to dispatcher — **this test MUST pass to satisfy constitution Quality Gate 3**

---

## Phase 3: User Story 1 — Annotate an Existing Endpoint as an MCP Tool (Priority: P1) 🎯 MVP

**Goal**: A developer annotates one method with `@McpTool`, starts the application,
and immediately sees it as a discoverable, invocable MCP tool — zero changes to
existing code.

**Independent Test**: `POST /mcp/tools/list` returns a descriptor for the annotated
method; `POST /mcp/tools/call` executes it and returns the result. Existing HTTP
endpoints behave identically.

### Mapping Engine

- [x] T022 [US1] Create `McpMappingEngine` interface in `src/main/java/com/raynermendez/spring_boot_mcp_companion/mapper/McpMappingEngine.java` with `toToolDefinition(Object bean, Method method, McpTool annotation)` method
- [x] T023 [US1] Create `JsonSchemaGenerator` in `src/main/java/com/raynermendez/spring_boot_mcp_companion/mapper/JsonSchemaGenerator.java` that maps Java types → JSON Schema objects: primitives, String, Number, Boolean, List/Array → `array`, POJO/Map → `object`; reflects `@NotNull`/`required`, `@Size` → minLength/maxLength, `@Min`/`@Max` → minimum/maximum
- [x] T024 [US1] Create `DefaultMcpMappingEngine` in `src/main/java/com/raynermendez/spring_boot_mcp_companion/mapper/DefaultMcpMappingEngine.java` implementing `McpMappingEngine`: derives tool `name` from annotation or method name (camelCase → snake_case), builds `McpParameterDefinition` list from method parameters + `@McpInput` attributes, delegates schema generation to `JsonSchemaGenerator`, constructs `MethodHandlerRef` with live bean reference
- [x] T025 [P] [US1] Write unit tests for `DefaultMcpMappingEngine` in `src/test/java/com/raynermendez/spring_boot_mcp_companion/mapper/DefaultMcpMappingEngineTest.java` — cover: name derivation, schema generation per Java type, `@McpInput` attributes applied, void return type handled
- [x] T026 [P] [US1] Write unit tests for `JsonSchemaGenerator` in `src/test/java/com/raynermendez/spring_boot_mcp_companion/mapper/JsonSchemaGeneratorTest.java` — cover each Java type → JSON Schema type mapping, each Bean Validation constraint → schema property

### Annotation Scanner

- [x] T027 [US1] Create `McpAnnotationScanner` in `src/main/java/com/raynermendez/spring_boot_mcp_companion/scanner/McpAnnotationScanner.java` implementing `SmartInitializingSingleton`: in `afterSingletonsInstantiated()` iterate all beans via `applicationContext.getBeanDefinitionNames()`, use `AopUtils.getTargetClass()` + `AnnotationUtils.findAnnotation()` to find `@McpTool`/`@McpResource`/`@McpPrompt` on each method, delegate to `McpMappingEngine`, register definitions in `McpDefinitionRegistry`, lock registry when done; on duplicate-name error → rethrow as `BeanCreationException`
- [x] T028 [US1] Write unit tests for `McpAnnotationScanner` in `src/test/java/com/raynermendez/spring_boot_mcp_companion/scanner/McpAnnotationScannerTest.java` — cover: annotated method registered in registry, unannotated method ignored, duplicate name causes `BeanCreationException`, registry is READY after scan

### Dispatcher

- [x] T029 [US1] Create `McpDispatcher` interface in `src/main/java/com/raynermendez/spring_boot_mcp_companion/dispatch/McpDispatcher.java` with `dispatchTool(String name, Map<String, Object> arguments)` → returns `McpToolResult`; create `McpToolResult` value record with `content`, `isError` fields
- [x] T030 [US1] Create `DefaultMcpDispatcher` in `src/main/java/com/raynermendez/spring_boot_mcp_companion/dispatch/DefaultMcpDispatcher.java`: look up `McpToolDefinition` by name (return `isError` response if not found), deserialize argument map to method parameter types, invoke method reflectively via `MethodHandlerRef.targetBean()`, serialize result to JSON string for MCP content; catch all `RuntimeException`s → return `isError: true` with exception message (never propagate as 500)
- [x] T031 [P] [US1] Write unit tests for `DefaultMcpDispatcher` in `src/test/java/com/raynermendez/spring_boot_mcp_companion/dispatch/DefaultMcpDispatcherTest.java` — cover: valid invocation returns result, unknown tool name returns isError response, runtime exception in method returns isError response, void return produces empty content

### HTTP Transport (Tools)

- [x] T032 [US1] Create `McpTransportController` in `src/main/java/com/raynermendez/spring_boot_mcp_companion/transport/McpTransportController.java` as `@RestController` with `@ConditionalOnProperty(prefix="mcp.server", name="enabled", matchIfMissing=true)`: implement `POST {basePath}/tools/list` (returns all tool descriptors in JSON-RPC 2.0 envelope) and `POST {basePath}/tools/call` (validates JSON-RPC structure, delegates to `McpDispatcher`, wraps result); use `@Value("${mcp.server.base-path:/mcp}")` for base path
- [x] T033 [US1] Create JSON-RPC 2.0 request/response record types in `src/main/java/com/raynermendez/spring_boot_mcp_companion/transport/` — `JsonRpcRequest`, `JsonRpcResponse`, `JsonRpcError` — matching the shapes in contracts/mcp-endpoints.md
- [x] T034 [P] [US1] Write `@WebMvcTest` slice tests for tools endpoints in `src/test/java/com/raynermendez/spring_boot_mcp_companion/transport/McpTransportControllerToolsTest.java` — cover: tools/list returns descriptor array, tools/call returns result, unknown tool returns -32601 error, `mcp.server.enabled=false` → 404

### Sample API and Integration Test

- [x] T035 [US1] Create `SampleOrderController` in `src/main/java/com/raynermendez/spring_boot_mcp_companion/sample/SampleOrderController.java` as `@RestController` with `GET /api/orders/{orderId}` returning a hardcoded `Order` response (demonstrates existing HTTP endpoint that must remain unchanged)
- [x] T036 [US1] Create `SampleOrderService` in `src/main/java/com/raynermendez/spring_boot_mcp_companion/sample/SampleOrderService.java` as `@Service` with a `getOrder(String orderId)` method annotated `@McpTool(description="Retrieves an order by its ID")` and `@McpInput` on the parameter
- [x] T037 [US1] Write `@SpringBootTest` integration test in `src/test/java/com/raynermendez/spring_boot_mcp_companion/integration/McpToolIntegrationTest.java` — cover: `POST /mcp/tools/list` returns sample tool descriptor; `POST /mcp/tools/call` executes `SampleOrderService.getOrder()` and returns result; `GET /api/orders/{orderId}` still returns HTTP 200 (backward-compat gate)

**Checkpoint**: User Story 1 is fully functional. `./mvnw test` passes. An MCP client can discover and invoke the sample tool.

---

## Phase 4: User Story 2 — Configure MCP Server via application.yml (Priority: P2)

**Goal**: All MCP server behavior is controlled via `application.yml` only — no
Java code required. IDE auto-complete works for `mcp.*` properties.

**Independent Test**: Toggle `mcp.server.enabled=false` → all MCP endpoints disappear;
toggle back → they return. Change `mcp.server.base-path` → endpoints move. IDE shows
property descriptions in auto-complete.

### Configuration Completion

- [x] T038 [US2] Complete `McpServerProperties` Javadoc on every field so `spring-boot-configuration-processor` generates descriptions in `spring-configuration-metadata.json`
- [x] T039 [US2] Add Javadoc to every field in `McpServerProperties` describing the property name, type, default value, and effect — these descriptions are picked up by `spring-boot-configuration-processor` (already configured in T002) to populate `spring-configuration-metadata.json`
- [x] T040 [US2] Update `McpAutoConfiguration` to inject `McpServerProperties` and configure `McpTransportController` base path dynamically from `mcpServerProperties.basePath()`; add `McpDefinitionRegistry` and `McpAnnotationScanner` bean definitions here so they are conditionally registered with the server
- [x] T041 [US2] Implement `GET {basePath}/server-info` endpoint in `McpTransportController` returning server name/version from `McpServerProperties` per contracts/mcp-endpoints.md
- [x] T042 [P] [US2] Write `@SpringBootTest` integration test in `src/test/java/com/raynermendez/spring_boot_mcp_companion/integration/McpConfigurationTest.java` — cover: `mcp.server.enabled=false` → `/mcp/tools/list` returns 404; custom `mcp.server.base-path=/api/mcp` → tools/list is at `/api/mcp/tools/list`; server-info returns configured name and version
- [x] T043 [P] [US2] Verify `target/classes/META-INF/spring-configuration-metadata.json` is generated after `./mvnw compile` and contains `mcp.server.enabled`, `mcp.server.base-path`, `mcp.server.name`, `mcp.server.version` entries — document verification step in `quickstart.md`

**Checkpoint**: Feature toggle and base-path work. IDE shows MCP property completions.

---

## Phase 5: User Story 3 — Validate Inputs at the MCP Boundary (Priority: P3)

**Goal**: Invalid or missing inputs are caught at the MCP layer and returned as
structured JSON-RPC errors. The underlying method is never called for invalid input.

**Independent Test**: Send a `tools/call` request with a missing required parameter →
get a `-32602 Invalid params` response with a `violations` array; the sample service
method is NOT invoked.

### Validation Layer

- [x] T044 [US3] Create `McpInputValidator` interface in `src/main/java/com/raynermendez/spring_boot_mcp_companion/validation/McpInputValidator.java` with `validate(McpToolDefinition definition, Map<String, Object> arguments)` → returns `List<McpViolation>`; create `McpViolation` record with `field`, `message`
- [x] T045 [US3] Create `DefaultMcpInputValidator` in `src/main/java/com/raynermendez/spring_boot_mcp_companion/validation/DefaultMcpInputValidator.java`: for each `McpParameterDefinition` with `required=true`, check argument map contains non-null value; use Jakarta `Validator` to evaluate `@NotBlank`, `@Size`, `@Min`, `@Max`, `@Pattern` constraints applied on the parameter; collect all violations; return empty list if all pass
- [x] T046 [US3] Integrate `McpInputValidator` into `DefaultMcpDispatcher.dispatchTool()`: call validator before reflective invocation; if violations non-empty, return a `JsonRpcError(-32602)` with `data.violations` array immediately — method MUST NOT be invoked
- [x] T047 [P] [US3] Write unit tests for `DefaultMcpInputValidator` in `src/test/java/com/raynermendez/spring_boot_mcp_companion/validation/DefaultMcpInputValidatorTest.java` — cover: missing required field → violation returned; `@NotBlank` violated → violation returned; `@Min`/`@Max` violated → violation; valid input → empty violations list
- [x] T048 [P] [US3] Write unit tests confirming `DefaultMcpDispatcher` calls validator and does NOT invoke method when violations present in `src/test/java/com/raynermendez/spring_boot_mcp_companion/dispatch/DefaultMcpDispatcherValidationTest.java`
- [x] T049 [US3] Add `@NotBlank` constraint to `SampleOrderService.getOrder()` `orderId` parameter and write `@SpringBootTest` integration test in `src/test/java/com/raynermendez/spring_boot_mcp_companion/integration/McpValidationIntegrationTest.java` — cover: blank orderId → -32602 response with violations array; `SampleOrderService.getOrder()` not called; valid orderId → method called, result returned

**Checkpoint**: All validation tests pass. Invalid inputs never reach business logic.

---

## Phase 6: User Story 4 — Observe MCP Operations via Actuator and Logs (Priority: P4)

**Goal**: Every MCP operation automatically emits a Micrometer metric and a structured
log entry. The Actuator health endpoint reports registry state and capability counts.

**Independent Test**: Invoke any MCP tool → `GET /actuator/metrics/mcp.tool.invocations`
counter incremented; structured log entry with tool name and duration present in output;
`GET /actuator/health` shows `mcpServer: UP` with counts.

### Observability Aspect

- [x] T050 [US4] Create `McpObservabilityAspect` in `src/main/java/com/raynermendez/spring_boot_mcp_companion/observability/McpObservabilityAspect.java` as `@Aspect @Component` with `@ConditionalOnClass(MeterRegistry.class)`: `@Around` pointcut on `DefaultMcpDispatcher.dispatchTool()`, `dispatchResource()`, `dispatchPrompt()` — record `Counter.increment("mcp.tool.invocations", "tool-name", name, "status", status)`, `Timer.record("mcp.tool.duration", ...)`, and SLF4J structured log at INFO: tool name, status (SUCCESS/ERROR), durationMs; log parameter names but NOT values by default
- [x] T051 [US4] Implement sensitive field redaction in `McpObservabilityAspect`: before logging, filter out argument entries where the corresponding `McpParameterDefinition.sensitive()` is `true`; replace value with `"[REDACTED]"` in log output only
- [x] T052 [P] [US4] Write unit tests for `McpObservabilityAspect` in `src/test/java/com/raynermendez/spring_boot_mcp_companion/observability/McpObservabilityAspectTest.java` — cover: invocation → counter incremented; failed invocation → error-tagged counter incremented; sensitive param → value NOT present in captured log output (use log capture library or SLF4J test appender)

### Health Indicator

- [x] T053 [US4] Create `McpHealthIndicator` in `src/main/java/com/raynermendez/spring_boot_mcp_companion/observability/McpHealthIndicator.java` implementing Spring Boot `HealthIndicator`: reports `UP` with details `{toolCount, resourceCount, promptCount, registryState}` when registry is `READY`; reports `DOWN` with error details when registry is `FAILED`
- [x] T054 [P] [US4] Write unit test for `McpHealthIndicator` in `src/test/java/com/raynermendez/spring_boot_mcp_companion/observability/McpHealthIndicatorTest.java` — cover: READY registry → UP health with correct counts; FAILED registry → DOWN health

### Observability Integration Test

- [x] T055 [US4] Write `@SpringBootTest` integration test in `src/test/java/com/raynermendez/spring_boot_mcp_companion/integration/McpObservabilityIntegrationTest.java` — cover: invoke sample tool → `mcp.tool.invocations` counter incremented (using `MeterRegistry` bean); structured log entry contains tool name and duration; `GET /actuator/health` returns `mcpServer` component with `status: UP` and correct toolCount; `GET /actuator/metrics/mcp.tool.invocations` returns metric data

**Checkpoint**: All 6 constitution quality gates are now verified by the full test suite.

---

## Phase 7: Resources, Prompts, and SPI (Polish & Cross-Cutting)

**Purpose**: Complete the remaining MCP capability types (resources, prompts), add SPI
extension points, and run the full validation from `quickstart.md`.

- [x] T056 [P] Extend `McpTransportController` with `POST {basePath}/resources/list` and `POST {basePath}/resources/read` endpoints per contracts/mcp-endpoints.md, delegating to `McpDispatcher.dispatchResource()`
- [x] T057 [P] Extend `McpTransportController` with `POST {basePath}/prompts/list` and `POST {basePath}/prompts/get` endpoints per contracts/mcp-endpoints.md, delegating to `McpDispatcher.dispatchPrompt()`
- [x] T058 [P] Implement `McpDispatcher.dispatchResource()` and `dispatchPrompt()` in `DefaultMcpDispatcher` — analogous to `dispatchTool()` but using `McpResourceDefinition` and `McpPromptDefinition` lookups
- [x] T059 [P] Add `@McpResource` and `@McpPrompt` examples to `SampleOrderService` in `src/main/java/com/raynermendez/spring_boot_mcp_companion/sample/SampleOrderService.java`
- [x] T060 [P] Create `McpOutputSerializer` SPI interface in `src/main/java/com/raynermendez/spring_boot_mcp_companion/spi/McpOutputSerializer.java` with `serialize(Object result, McpToolDefinition definition)` → `String`; make `DefaultMcpDispatcher` use this interface (default implementation: Jackson `ObjectMapper.writeValueAsString`)
- [x] T061 [P] Create `McpMappingEngine` extensions for `@McpResource` and `@McpPrompt` in `DefaultMcpMappingEngine` — derive `McpResourceDefinition` and `McpPromptDefinition` from annotated methods
- [x] T062 [P] Extend `McpAnnotationScanner` to also scan for `@McpResource` and `@McpPrompt` annotated methods and register corresponding definitions in the registry
- [x] T063 Write `@SpringBootTest` integration tests for resources and prompts in `src/test/java/com/raynermendez/spring_boot_mcp_companion/integration/McpResourcesPromptsIntegrationTest.java` — cover: resources/list returns sample resource; resources/read returns content; prompts/list returns sample prompt; prompts/get returns messages array
- [x] T064 [P] Write `@WebMvcTest` tests for resource and prompt endpoints in `src/test/java/com/raynermendez/spring_boot_mcp_companion/transport/McpTransportControllerResourcesPromptsTest.java`
- [x] T065 Run `./mvnw verify` and confirm all tests pass; confirm `target/classes/META-INF/spring-configuration-metadata.json` contains all `mcp.server.*` property entries
- [x] T066 Update `application.yml` with commented-out example of each `mcp.server.*` property with descriptions matching the quickstart guide

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately
- **Foundational (Phase 2)**: Depends on Setup completion — BLOCKS all user stories
- **US1 (Phase 3)**: Depends on Foundational completion — no dependency on US2/US3/US4
- **US2 (Phase 4)**: Depends on Foundational completion; benefits from US1 (reuses `McpTransportController`) but is independently testable
- **US3 (Phase 5)**: Depends on US1 (extends `McpDispatcher`) — MUST complete US1 first
- **US4 (Phase 6)**: Depends on US1 (wraps `McpDispatcher`) — MUST complete US1 first; US3 and US4 can proceed in parallel after US1
- **Polish (Phase 7)**: Depends on US1 complete; US2/US3/US4 can be in progress simultaneously

### User Story Dependencies

- **US1 (P1)**: Can start after Phase 2 complete — MVP deliverable
- **US2 (P2)**: Can start after Phase 2 complete — runs in parallel with US1 on configuration side
- **US3 (P3)**: Requires US1 complete (extends dispatcher)
- **US4 (P4)**: Requires US1 complete (wraps dispatcher)
- **US3 and US4**: Can be worked in parallel after US1 is done

### Within Each User Story

- Model/annotation tasks before engine tasks
- Engine tasks before scanner/dispatcher tasks
- Scanner/dispatcher before transport (HTTP controller)
- Transport before integration tests

### Parallel Opportunities

- T007–T010 (annotation types): all parallel
- T011–T015 (model records): all parallel
- T025, T026 (mapping engine unit tests): parallel with T024 (implementation)
- T042, T043 (US2 tests): parallel with each other
- T047, T048 (US3 unit tests): parallel with each other
- T052, T054 (US4 unit tests): parallel with each other
- T056–T062 (Polish phase): mostly parallel — different files

---

## Parallel Execution Examples

### Phase 2 Foundational (run concurrently)

```
Task T007: Create @McpTool annotation
Task T008: Create @McpResource annotation
Task T009: Create @McpPrompt annotation
Task T010: Create @McpInput annotation
Task T011: Create McpParameterDefinition record
Task T012: Create MethodHandlerRef record
Task T013: Create McpToolDefinition record
Task T014: Create McpResourceDefinition record
Task T015: Create McpPromptDefinition record
```

### Phase 3 User Story 1 — Unit Tests (run concurrently after implementations)

```
Task T025: Unit tests for DefaultMcpMappingEngine
Task T026: Unit tests for JsonSchemaGenerator
Task T031: Unit tests for DefaultMcpDispatcher
Task T034: @WebMvcTest for McpTransportController (tools)
```

### After US1 Complete (run concurrently on separate branches or pair)

```
Developer A: Phase 4 — US2 (configuration, IDE metadata)
Developer B: Phase 5 — US3 (validation layer)
  or
Developer B: Phase 6 — US4 (observability)
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001–T006)
2. Complete Phase 2: Foundational (T007–T021)
3. Complete Phase 3: User Story 1 (T022–T037)
4. **STOP and VALIDATE**: `./mvnw test` passes; `POST /mcp/tools/list` returns sample tool
5. Demo to stakeholders — the core value is deliverable here

### Incremental Delivery

1. Phase 1 + Phase 2 → Foundation ready (`./mvnw compile`)
2. Phase 3 (US1) → Core tool annotation works → **MVP Demo**
3. Phase 4 (US2) → Configuration + IDE support → **Configuration Demo**
4. Phase 5 (US3) → Validation gate closed → **Security Demo**
5. Phase 6 (US4) → Observability complete → **Production-Ready Demo**
6. Phase 7 (Polish) → Resources, prompts, SPI → **Feature Complete**

### Single-Developer Sequential Path

```
Phase 1 (T001–T006) → Phase 2 (T007–T021) → Phase 3 (T022–T037)
→ Phase 4 (T038–T043) → Phase 5 (T044–T049) → Phase 6 (T050–T055)
→ Phase 7 (T056–T066)
```

---

## Notes

- [P] tasks operate on different files and have no incomplete-task dependencies
- [USn] label maps each task to its user story for traceability to spec.md
- Each user story phase ends with a `@SpringBootTest` integration test covering the
  story's acceptance scenarios from spec.md
- Commit after each logical group (e.g., after all annotations, after registry,
  after each user story integration test passes)
- The constitution's 6 quality gates are all verified by integration tests in phases
  3–6 — do not skip these tests
- `./mvnw verify` at the end of Phase 7 is the final all-gates check

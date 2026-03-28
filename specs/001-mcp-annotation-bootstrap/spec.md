# Feature Specification: Spring Boot MCP Annotation Bootstrap

**Feature Branch**: `001-mcp-annotation-bootstrap`
**Created**: 2026-03-27
**Status**: Draft
**Input**: Bootstrap a reusable MCP annotation mapper/wrapper for Spring Boot

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Annotate an Existing Endpoint as an MCP Tool (Priority: P1)

A developer on an existing Spring Boot team adds the MCP annotation framework
to their project, annotates one existing `@RestController` method with
`@McpTool`, starts the application, and immediately sees that method exposed
as a discoverable MCP tool — without changing any existing controller code,
tests, or HTTP behavior.

**Why this priority**: This is the core value proposition. If a developer
cannot annotate and expose a single existing method as an MCP tool with zero
changes to existing code, the framework has not delivered its primary promise.

**Independent Test**: Can be fully tested by (1) adding the dependency,
(2) adding `@McpTool` to one existing method, (3) calling the MCP tools-list
endpoint, and verifying the tool descriptor appears. No other stories needed.

**Acceptance Scenarios**:

1. **Given** a running Spring Boot application with at least one `@McpTool`-annotated method, **When** a client calls the MCP tools-list endpoint, **Then** the response includes the tool descriptor with name, description, and input schema.
2. **Given** an annotated method exists, **When** a client invokes the tool via the MCP execute endpoint, **Then** the existing method executes and its result is returned in MCP-compatible format.
3. **Given** the framework is added with no `@McpTool` annotations present, **When** the application starts, **Then** it starts normally and the tools-list endpoint returns an empty list.
4. **Given** the framework is added to a project, **When** the application starts, **Then** all pre-existing HTTP endpoints behave identically to their pre-framework behavior.

---

### User Story 2 - Configure MCP Server Behavior via application.yml (Priority: P2)

A developer controls the MCP server's base path, enabled/disabled state,
server name, version, and security defaults entirely through `application.yml`
without writing any Java configuration code.

**Why this priority**: Externalized configuration is the primary ergonomic
promise. Teams must be able to onboard, customize, and disable MCP behavior
per-environment with configuration only.

**Independent Test**: Can be fully tested by toggling `mcp.server.enabled`
in `application.yml` and verifying MCP endpoints appear or disappear
accordingly, with no code changes.

**Acceptance Scenarios**:

1. **Given** `mcp.server.enabled: false` in `application.yml`, **When** the application starts, **Then** no MCP endpoints are registered and no existing endpoints are affected.
2. **Given** `mcp.server.base-path: /api/mcp` in `application.yml`, **When** a client calls `/api/mcp/tools/list`, **Then** the tools list is returned.
3. **Given** `mcp.server.name` and `mcp.server.version` are configured, **When** a client calls the MCP server-info endpoint, **Then** the configured values are reflected in the response.
4. **Given** IDE tooling is active, **When** a developer types `mcp.` in `application.yml`, **Then** auto-complete shows available MCP properties with descriptions.

---

### User Story 3 - Validate Inputs at the MCP Boundary (Priority: P3)

When a client invokes an MCP tool with an invalid or missing required input,
the framework rejects the request at the MCP layer with a structured error
response — without the invalid input ever reaching existing business logic.

**Why this priority**: Boundary validation is a security and correctness
requirement independent of the broader integration layer.

**Independent Test**: Can be tested by invoking an MCP tool with a missing
required field and asserting a structured error response (not a 500) and
that no downstream method was invoked.

**Acceptance Scenarios**:

1. **Given** a tool method has a required parameter, **When** the MCP request omits that parameter, **Then** the framework returns a structured validation error and the underlying method is NOT called.
2. **Given** a tool method parameter has constraint annotations, **When** the MCP request violates those constraints, **Then** the error details are included in the structured error response.
3. **Given** a well-formed invocation request, **When** the framework validates it, **Then** validation passes silently and the method executes normally.

---

### User Story 4 - Observe MCP Operations via Actuator and Logs (Priority: P4)

A platform engineer can see MCP invocation counts, error rates, and latency
in Actuator metrics endpoints and in structured application logs, with no
per-tool configuration required.

**Why this priority**: Observability enables production adoption and is
required by the project constitution but is independent of tool behavior.

**Independent Test**: Can be tested by invoking any MCP tool, then querying
an Actuator metrics endpoint and verifying the invocation counter incremented
and a structured log entry containing the tool name exists.

**Acceptance Scenarios**:

1. **Given** an MCP tool is invoked successfully, **When** Actuator metrics are queried, **Then** the invocation count metric for that tool has incremented.
2. **Given** an MCP tool invocation fails, **When** Actuator metrics are queried, **Then** the error count metric for that tool has incremented.
3. **Given** any MCP tool is invoked, **When** application logs are examined, **Then** a structured log entry exists containing the tool name, status, and duration — with no sensitive parameter values included by default.

---

### Edge Cases

- What happens when two `@McpTool` annotations declare the same tool name? The framework MUST fail fast at startup with a descriptive error (no silent overwrite).
- What happens when an annotated method throws an unchecked exception at runtime? The framework MUST catch it and return a structured MCP error response rather than propagating a 500.
- What happens when `mcp.server.enabled` is absent from configuration? The server MUST default to enabled (convention over configuration).
- What happens when a method return type is `void`? The framework MUST return an empty MCP content response rather than failing.
- What happens when the same method has both `@McpTool` and a standard Spring HTTP mapping? Both MUST work independently; the MCP annotation MUST NOT interfere with the existing HTTP behavior.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The framework MUST expose annotated Spring component methods as MCP-compatible tool descriptors discoverable at a configurable endpoint.
- **FR-002**: The framework MUST invoke annotated methods when a valid MCP tool execution request is received, mapping inputs to method arguments.
- **FR-003**: The framework MUST derive the MCP tool name, description, and input schema from annotation attributes and method parameter metadata, applying sensible defaults when attributes are omitted.
- **FR-004**: The framework MUST preserve all existing HTTP endpoint behavior unchanged after being added to a project; no existing request/response contracts are altered.
- **FR-005**: The framework MUST allow all MCP server behavior to be enabled, disabled, or customized exclusively through `application.yml` or `.properties`, with no Java code changes required.
- **FR-006**: The framework MUST generate IDE-friendly configuration metadata so that MCP properties appear with descriptions and type information in editor auto-complete.
- **FR-007**: The framework MUST validate all inbound MCP tool input payloads against declared constraints before routing to the underlying method.
- **FR-008**: Validation failures MUST produce structured error responses identifying the failing field(s) and reason(s); the underlying method MUST NOT be called on validation failure.
- **FR-009**: The framework MUST emit at minimum one structured log entry and one metric increment per MCP tool invocation on both success and failure paths.
- **FR-010**: The framework MUST register health and metric contributions compatible with Spring Boot Actuator for production monitoring.
- **FR-011**: The framework MUST detect duplicate tool name registrations at application startup and fail with a descriptive error.
- **FR-012**: The framework MUST support MCP resources and MCP prompts (in addition to tools) via dedicated annotations with the same convention-over-configuration behavior.
- **FR-013**: The framework MUST be usable in any standard Spring Boot 3.x web application by adding a single dependency and annotations only — no base class inheritance, interface implementation, or package restructuring required.
- **FR-014**: The bootstrap project MUST include a sample annotated API (stub controller + service) to demonstrate and validate all annotation types out of the box.

### Key Entities

- **McpTool**: A named, invocable capability mapped from a Spring component method. Has name, description, input schema, and output schema.
- **McpResource**: A named, readable data source mapped from a Spring component method. Has URI template, description, and MIME type.
- **McpPrompt**: A named, parameterized prompt template mapped from a Spring component method. Has name, description, and argument schema.
- **McpServerProperties**: The externalized configuration model for the MCP server — enabled flag, base path, server name, version, and security defaults.
- **McpDefinitionRegistry**: The in-memory registry of all discovered and validated MCP capability definitions (tools, resources, and prompts), populated at application startup.
- **McpAnnotationScanner**: The component that scans the Spring application context for annotated beans and registers them in the McpDefinitionRegistry.
- **McpMappingEngine**: The component that derives MCP capability descriptors from annotation metadata and Java method signatures.
- **McpTransportController**: The Spring MVC controller exposing MCP protocol endpoints (tools/list, tools/call, resources/list, resources/read, prompts/list, prompts/get).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A developer with an existing Spring Boot application can add the framework and expose their first MCP tool in under 5 minutes, from dependency added to first successful MCP tool invocation.
- **SC-002**: Adding the framework to an existing application causes zero regressions in existing endpoint behavior, verified by all pre-existing tests passing without modification.
- **SC-003**: All MCP tool invocations emit observable output (log entry and metric) with no additional per-tool configuration required — 100% coverage out of the box.
- **SC-004**: Invalid MCP inputs are rejected with structured errors 100% of the time; no invalid input reaches existing business logic.
- **SC-005**: The framework requires zero Java code changes to existing classes — 100% of integration is achievable through annotations and configuration alone.
- **SC-006**: MCP properties appear in IDE auto-complete within a standard Spring Boot project using the framework dependency.
- **SC-007**: The framework is fully disabled by setting one configuration property, without removing the dependency or changing any code.

## Assumptions

- The target adopter has an existing Spring Boot 4.x application with Java 17+. This bootstrap project itself runs Spring Boot 4.0.5 / Java 25.
- The existing application uses standard Spring MVC (`@RestController`, `@Service`, `@Component`); reactive (WebFlux) support is out of scope for this bootstrap.
- The MCP protocol version targeted is the current stable release; breaking protocol changes will require a framework MAJOR version bump.
- Authentication and authorization for MCP endpoints delegate to the host application's existing Spring Security configuration when Spring Security is present on the classpath. When Spring Security is absent, the framework MUST surface an explicit startup warning and MAY register a no-op security guard that rejects all unauthenticated MCP requests until security is configured. Security is never silently permissive by default (constitution Principle IV).
- The initial bootstrap is a single Maven module; extraction into a dedicated starter artifact is a future iteration.
- Method parameter names are available at runtime (compiled with `-parameters`); the Spring Boot parent POM enables this by default.
- Sensitive field redaction in logs is configurable via annotation attributes on individual parameters; the default is to log parameter names but not values.

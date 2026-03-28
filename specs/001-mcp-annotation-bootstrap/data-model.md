# Data Model: Spring Boot MCP Annotation Bootstrap

**Phase**: 1 — Design
**Feature**: 001-mcp-annotation-bootstrap
**Date**: 2026-03-27

---

## Overview

This document describes the domain entities, their fields, relationships, and
state transitions for the MCP annotation framework. All entities are in-memory
(no persistence layer). The registry is populated once at application startup and
is read-only at runtime.

---

## Core Entities

### 1. McpToolDefinition

Represents a fully-resolved MCP tool mapped from a Java method.

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| `name` | `String` | Required, unique, non-blank, matches `[a-z][a-z0-9_-]*` | MCP tool name (snake_case by convention) |
| `description` | `String` | Required, non-blank | Human-readable description for the MCP client |
| `inputSchema` | `JsonSchema` | Required | JSON Schema object describing the tool's input parameters |
| `handlerRef` | `MethodHandlerRef` | Required | Reference to the Spring bean and method that handles this tool |
| `inputParameters` | `List<McpParameterDefinition>` | Required, may be empty | Ordered list of mapped input parameters |
| `outputType` | `Class<?>` | Required | Java return type used for output serialization |
| `sensitive` | `boolean` | Defaults to `false` | If `true`, parameter values are redacted in logs |
| `tags` | `Set<String>` | Optional | Arbitrary tags for filtering/grouping |

**Validation rules**:
- `name` MUST be globally unique within the `McpDefinitionRegistry`. Duplicate
  detection happens at registration time; duplicate triggers startup failure.
- `name` defaults to the method name in snake_case if not specified in the annotation.
- `description` is required if the method has no Javadoc-compatible default; the
  annotation attribute MUST be non-blank.

---

### 2. McpResourceDefinition

Represents a fully-resolved MCP resource mapped from a Java method.

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| `uri` | `String` | Required, unique, valid URI template | URI template identifying the resource |
| `name` | `String` | Required, non-blank | Human-readable resource name |
| `description` | `String` | Optional | Human-readable description |
| `mimeType` | `String` | Optional, defaults to `application/json` | MIME type of the resource content |
| `handlerRef` | `MethodHandlerRef` | Required | Spring bean + method handling reads |
| `uriVariables` | `List<McpParameterDefinition>` | May be empty | URI template variables mapped to method params |

**Validation rules**:
- `uri` MUST be unique across all registered resources.
- `uri` MUST be a valid RFC 6570 URI template (validated at startup).

---

### 3. McpPromptDefinition

Represents a fully-resolved MCP prompt mapped from a Java method.

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| `name` | `String` | Required, unique, non-blank | MCP prompt name |
| `description` | `String` | Optional | Human-readable description |
| `arguments` | `List<McpPromptArgument>` | May be empty | Named arguments the prompt accepts |
| `handlerRef` | `MethodHandlerRef` | Required | Spring bean + method handling prompt resolution |

---

### 4. McpParameterDefinition

Represents a single input parameter in a tool or resource definition.

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| `name` | `String` | Required, non-blank | Parameter name (from annotation or Java param name) |
| `description` | `String` | Optional | Description for schema generation |
| `required` | `boolean` | Defaults to `true` | Whether the parameter is required |
| `jsonType` | `String` | Required | JSON Schema type (`string`, `number`, `boolean`, `object`, `array`) |
| `constraints` | `List<ValidationConstraint>` | May be empty | Bean Validation constraints to enforce |
| `sensitive` | `boolean` | Defaults to `false` | If `true`, value is redacted in logs |
| `javaType` | `Class<?>` | Required | Java type for deserialization |

---

### 5. McpPromptArgument

Represents a single argument accepted by an MCP prompt.

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| `name` | `String` | Required, non-blank | Argument name |
| `description` | `String` | Optional | Human-readable description |
| `required` | `boolean` | Defaults to `false` | Whether the argument is required |

---

### 6. MethodHandlerRef

A value object referencing a specific method on a Spring bean.

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| `beanName` | `String` | Required | Spring `ApplicationContext` bean name |
| `beanType` | `Class<?>` | Required | Bean class (after proxy unwrapping) |
| `method` | `Method` | Required | `java.lang.reflect.Method` to invoke |
| `targetBean` | `Object` | Required | Live bean reference (held at registration time) |

**Note**: `targetBean` holds a reference to the Spring-managed bean (or its proxy).
Invocation always goes through the proxy to preserve AOP behavior (e.g., `@Transactional`).

---

### 7. McpDefinitionRegistry

The central in-memory registry of all discovered MCP definitions.

| Field | Type | Description |
|-------|------|-------------|
| `tools` | `Map<String, McpToolDefinition>` | Tool definitions keyed by tool name |
| `resources` | `Map<String, McpResourceDefinition>` | Resource definitions keyed by URI template |
| `prompts` | `Map<String, McpPromptDefinition>` | Prompt definitions keyed by prompt name |

**State transitions**:

```
[EMPTY] ──(scan start)──► [BUILDING] ──(all beans scanned, no errors)──► [READY]
                                      ──(duplicate name / validation error)──► [FAILED]

[READY] ──(runtime)──► [READY]   (read-only at runtime; no mutations)
[FAILED] ──(application refuses to start)
```

**Invariants**:
- Registry transitions from `BUILDING` → `READY` exactly once during application startup.
- Once in `READY` state, the registry is immutable (no further registrations or removals).
- The registry MUST be in `READY` state before the MCP transport controller accepts requests.

---

### 8. McpServerProperties (`@ConfigurationProperties(prefix = "mcp.server")`)

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `enabled` | `boolean` | `true` | Master switch for the entire MCP subsystem |
| `name` | `String` | Application name | MCP server name reported in server-info |
| `version` | `String` | Application version | MCP server version reported in server-info |
| `basePath` | `String` | `/mcp` | Base path for all MCP endpoints |
| `transport` | `TransportType` | `HTTP` | Transport type (`HTTP`, `SSE`) |

---

### 9. McpInvocationRecord (Observability, not persisted)

A transient value object captured per MCP operation invocation for logging and metrics.

| Field | Type | Description |
|-------|------|-------------|
| `operationType` | `OperationType` | `TOOL`, `RESOURCE`, `PROMPT` |
| `operationName` | `String` | Tool name, resource URI, or prompt name |
| `startTime` | `Instant` | Invocation start timestamp |
| `durationMs` | `long` | Elapsed time in milliseconds |
| `status` | `InvocationStatus` | `SUCCESS` or `ERROR` |
| `errorType` | `String` | Exception class name (if `ERROR`), otherwise `null` |

---

## Entity Relationships

```
McpDefinitionRegistry
  ├── 1..* McpToolDefinition
  │         └── 1    MethodHandlerRef  (→ Spring Bean + Method)
  │         └── 0..* McpParameterDefinition
  ├── 0..* McpResourceDefinition
  │         └── 1    MethodHandlerRef
  │         └── 0..* McpParameterDefinition  (URI variables)
  └── 0..* McpPromptDefinition
            └── 1    MethodHandlerRef
            └── 0..* McpPromptArgument
```

---

## Annotation-to-Entity Mapping

| Annotation | Produces | Registry Key |
|------------|----------|-------------|
| `@McpTool` on a method | `McpToolDefinition` | `name` attribute (default: method name in snake_case) |
| `@McpResource` on a method | `McpResourceDefinition` | `uri` attribute |
| `@McpPrompt` on a method | `McpPromptDefinition` | `name` attribute |
| `@McpInput` on a parameter | `McpParameterDefinition` | `name` attribute (default: Java param name) |

---

## JSON Schema Generation Rules

The `McpMappingEngine` generates a JSON Schema object for each tool's `inputSchema`
by inspecting each `McpParameterDefinition`:

- `String` → `{"type": "string"}`
- `int` / `Integer` / `long` / `Long` → `{"type": "integer"}`
- `double` / `Double` / `float` / `Float` → `{"type": "number"}`
- `boolean` / `Boolean` → `{"type": "boolean"}`
- `List<T>` / `T[]` → `{"type": "array", "items": <T-schema>}`
- `Map<String, V>` / POJO → `{"type": "object", "properties": {...}}`
- Bean Validation constraints are reflected:
  - `@NotNull` / required=true → added to `required` array in schema
  - `@Size(min, max)` → `minLength` / `maxLength` (strings), `minItems` / `maxItems` (arrays)
  - `@Min` / `@Max` → `minimum` / `maximum` (numbers)
  - `@Pattern` → `pattern`

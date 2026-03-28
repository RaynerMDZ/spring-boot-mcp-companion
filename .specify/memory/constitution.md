<!--
SYNC IMPACT REPORT
==================
Version change: [UNVERSIONED / blank template] → 1.0.0
Constitution status: Initial authoring — all placeholders filled for the first time.

Modified principles: N/A (first version)

Added sections:
  - Core Principles (7 principles)
  - Technical Constraints
  - Quality Gates
  - Governance

Removed sections: N/A

Templates requiring updates:
  ✅ .specify/memory/constitution.md — this file (written now)
  ✅ .specify/templates/plan-template.md — Constitution Check section is generic and
     compatible; no structural changes needed. Principle names can be referenced as-is.
  ✅ .specify/templates/spec-template.md — template is generic and fully compatible;
     no section additions or removals required.
  ✅ .specify/templates/tasks-template.md — task phases (Setup, Foundational, User Story,
     Polish) align with the seven principles; the security-hardening and observability
     tasks in Phase N match Principles IV and VI directly. No structural update needed.

Follow-up TODOs:
  - None. All fields fully resolved from user input and project context.
-->

# Spring Boot MCP Annotation Mapper Constitution

## Core Principles

### I. Zero-Friction Integration (NON-NEGOTIABLE)

The framework MUST layer on top of any existing Spring Boot application without
requiring refactoring of existing business logic, controllers, services, or
application structure. Adoption MUST be achievable by adding a dependency and
annotations only — no architectural rewrites, contract changes, or invasive code
modifications are permitted as a prerequisite for use.

**Rationale**: Real-world codebases cannot afford regressions or structural churn
as the price of new tooling adoption. If the framework demands structural changes,
it will not be adopted.

### II. Annotation-Driven, Externalized Configuration

All MCP mapping — tools, resources, prompts, inputs, outputs, and metadata — MUST
be expressible through Java annotations and/or externalized configuration in
`.properties` or `.yml` files. No programmatic wiring or XML configuration should
be required for standard use cases. Convention over configuration MUST reduce
boilerplate to the minimum necessary for each mapping.

**Rationale**: Declarative configuration keeps MCP mapping auditable, diff-able,
and reviewable without tracing through imperative code.

### III. Backward Compatibility (NON-NEGOTIABLE)

Adding MCP exposure to an existing endpoint or service MUST NOT alter that
endpoint's existing HTTP behavior, response contract, validation rules, or error
semantics. MCP exposure is strictly additive. Any change that could break an
existing API caller is forbidden. Existing integration tests MUST continue to
pass without modification after the framework is applied.

**Rationale**: Teams will only adopt tooling that provides a credible guarantee
that it cannot break what is already in production.

### IV. Secure Defaults

All MCP-exposed capabilities MUST require explicit authorization by default.
Security MUST be opt-out (disabled per-tool via explicit annotation or config),
never opt-in. Input payloads received over MCP MUST be validated before reaching
existing business logic. Sensitive fields MUST support redaction in logs and
responses. Transport-level security (TLS) MUST be enforced in non-local profiles.

**Rationale**: Wrapping existing APIs as MCP tools increases the attack surface.
Defaults that are secure reduce the risk of inadvertent exposure.

### V. Observability

Every MCP operation (tool invocation, resource fetch, prompt resolution) MUST
emit structured log entries, metrics (invocation count, latency, error rate), and
optionally distributed traces. Observability instrumentation MUST be automatic for
all mapped capabilities — no per-mapping configuration required to get baseline
visibility. Sensitive fields MUST be excluded from observability output by default.

**Rationale**: MCP servers running in production need the same operational
visibility as any other service endpoint; observability must not be an afterthought.

### VI. Validation at the MCP Boundary

The framework MUST validate all inbound MCP inputs (type coercion, required
fields, constraint checks) before passing data to existing business logic. Existing
Bean Validation (`javax.validation` / `jakarta.validation`) constraints MUST be
honored automatically when present. Validation failures MUST return structured MCP
error responses — they MUST NOT propagate as unhandled exceptions into the
underlying Spring application context.

**Rationale**: Validation at the boundary prevents malformed MCP payloads from
corrupting or confusing internal service state.

### VII. Extensibility via SPI

The framework MUST expose well-defined extension points (Service Provider
Interfaces) for custom serializers, input/output transformers, authentication
providers, and transport adapters. Core extension points MUST be usable without
modifying framework source code. Third-party or project-local extensions MUST be
registerable through standard Spring `@Bean` declarations or `spring.factories`
entries.

**Rationale**: No framework can anticipate every production requirement. Extension
points prevent forks and allow teams to adapt the framework to edge cases without
losing upgrade compatibility.

## Technical Constraints

- **Language & Runtime**: Java 17+ (LTS). Kotlin compatibility is a secondary goal;
  it MUST NOT break Java-first design decisions.
- **Spring Boot Compatibility**: Spring Boot 3.x (minimum). Spring Boot 2.x support
  is explicitly out of scope.
- **MCP Specification**: All generated MCP surfaces MUST conform to the MCP
  specification version targeted at project inception. Breaking spec changes
  require a MAJOR version bump of this framework.
- **Dependency Footprint**: The core annotation module MUST introduce no transitive
  dependencies beyond the Spring Boot starter ecosystem and a minimal MCP SDK.
  Optional features (e.g., distributed tracing) MUST be behind optional/provided
  scopes.
- **Build System**: Maven and Gradle MUST both be supported as consumer build tools.
  The framework itself is built with Maven.
- **No Reflection Abuse**: Annotation processing MUST use Spring's standard
  `BeanPostProcessor` / `ApplicationContext` mechanisms. Unsafe reflection or
  bytecode manipulation at runtime is forbidden.

## Quality Gates

Every feature shipped in this framework MUST satisfy the following gates before
merge:

1. **Backward-compat check**: An existing Spring Boot application with no MCP
   annotations MUST start and pass all its existing tests unchanged after adding
   the framework dependency.
2. **Zero-config smoke test**: A single `@McpTool`-annotated method (or equivalent)
   on an existing controller MUST produce a valid MCP tool descriptor without any
   additional configuration.
3. **Security gate**: All new MCP-exposed surfaces MUST have a corresponding test
   asserting that unauthenticated requests are rejected by default.
4. **Observability gate**: All new MCP operations MUST emit at least one
   structured log entry and one metric increment verifiable in a test.
5. **Validation gate**: All new input mappings MUST have a test confirming that
   invalid inputs produce structured MCP error responses, not 500s.
6. **No breaking API changes** without a MAJOR version bump and a published
   migration guide.

## Governance

This constitution supersedes all other development practices and conventions for
this project. Any practice not covered here defaults to idiomatic Spring Boot and
standard Java conventions.

**Amendment procedure**:
1. Propose the amendment in a pull request with a written rationale.
2. Identify all principles or constraints affected and document the impact.
3. Update `CONSTITUTION_VERSION` per semantic versioning rules:
   - **MAJOR**: Backward-incompatible governance change, principle removal, or
     redefinition that changes existing behavior expectations.
   - **MINOR**: New principle or section added, or material guidance expansion.
   - **PATCH**: Clarification, wording fix, or non-semantic refinement.
4. Update `LAST_AMENDED_DATE` to the amendment date.
5. Update dependent templates (plan, spec, tasks) if constitution changes alter
   required sections or task categories.
6. Amendments to Principles I, III, or IV require explicit sign-off from a
   project maintainer before merge, as these are marked NON-NEGOTIABLE.

**Compliance review**: Every pull request description MUST include a brief
"Constitution Check" confirming which principles are exercised and that none are
violated. The plan template's Constitution Check section is the authoritative gate.

**Runtime guidance**: For agent-assisted development workflows, consult the
`.specify/templates/` directory for spec, plan, and task templates aligned to
this constitution.

---

**Version**: 1.0.0 | **Ratified**: 2026-03-27 | **Last Amended**: 2026-03-27

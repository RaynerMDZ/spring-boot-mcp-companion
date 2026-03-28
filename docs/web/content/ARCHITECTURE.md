# Spring Boot MCP Companion - Architecture Guide

**Audience:** Software Architects, Senior Developers, System Designers

This document explains the internal architecture and design decisions of Spring Boot MCP Companion.

---

## Table of Contents

1. [System Architecture](#system-architecture)
2. [Component Breakdown](#component-breakdown)
3. [Request/Response Flow](#requestresponse-flow)
4. [Type Mapping Engine](#type-mapping-engine)
5. [Validation Architecture](#validation-architecture)
6. [Security Architecture](#security-architecture)
7. [Error Handling Strategy](#error-handling-strategy)
8. [Extensibility Points](#extensibility-points)
9. [Design Patterns Used](#design-patterns-used)

---

## System Architecture

### High-Level System Design

```
┌──────────────────────────────────────────────────────────────┐
│         Unified Spring Boot Application                      │
│              (Single Server, Port 8080)                      │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐ │
│  │  Your REST API Controllers, Services, Repositories     │ │
│  │  ↓ (Decorated with @McpTool/@McpResource/@McpPrompt) │ │
│  └────────────────────────────────────────────────────────┘ │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐ │
│  │  1. DISCOVERY PHASE (Startup)                          │ │
│  │  - Scan for @McpTool/@McpResource/@McpPrompt         │ │
│  │  - Extract metadata from annotations & methods        │ │
│  │  - Generate JSON schemas for all parameters           │ │
│  │  - Register handlers in dispatcher                    │ │
│  └────────────────────────────────────────────────────────┘ │
│                         ↓                                    │
│  ┌────────────────────────────────────────────────────────┐ │
│  │  2. REQUEST PROCESSING PHASE (Runtime)                 │ │
│  │  - Receive REST or JSON-RPC 2.0 request               │ │
│  │  - Route: /api/** → REST handlers, /mcp/** → MCP     │ │
│  │  - Security checks & rate limiting                    │ │
│  │  - Parameter validation                               │ │
│  │  - Type conversion (JSON → Java)                      │ │
│  │  - Method invocation                                  │ │
│  │  - Response serialization (Java → JSON)               │ │
│  │  - Error handling & formatting                        │ │
│  └────────────────────────────────────────────────────────┘ │
│                         ↓                                    │
│  ┌────────────────────────────────────────────────────────┐ │
│  │  3. OUTPUT PHASE                                        │ │
│  │  - JSON-RPC 2.0 response (for /mcp/*)                 │ │
│  │  - REST response (for /api/**)                        │ │
│  │  - Metrics & monitoring                               │ │
│  │  - Logging & audit trail                              │ │
│  └────────────────────────────────────────────────────────┘ │
│                                                              │
└──────────────────────────────────────────────────────────────┘
                         ↓
┌──────────────────────────────────────────────────────────────┐
│  HTTP Endpoints (All on Port 8080)                           │
│                                                              │
│  REST API:                                                   │
│  - GET /api/v1/users, POST /api/v1/users, etc.            │
│                                                              │
│  MCP Endpoints:                                              │
│  - GET  /mcp/server-info → Server metadata                 │
│  - POST /mcp/tools/list → List available tools            │
│  - POST /mcp/tools/call → Invoke a tool                   │
│  - POST /mcp/resources/list → List resources              │
│  - POST /mcp/resources/read → Read a resource             │
│  - POST /mcp/prompts/list → List prompts                  │
│  - POST /mcp/prompts/get → Get a prompt                   │
│  - POST /mcp/initialize → Initialize session              │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### Unified Single-Port Architecture

MCP endpoints are served on the **same server** as your main Spring Boot application:

**Benefits:**
- ✅ Simpler cloud deployment (single port, one load balancer rule)
- ✅ Shared resource pool (more efficient thread utilization)
- ✅ Lower infrastructure cost (one service vs two)
- ✅ Path-based routing (REST and MCP coexist)
- ✅ Non-invasive integration (doesn't modify business logic)
- ✅ Optional (disable with `mcp.server.enabled: false`)

**Configuration:**
```yaml
server:
  port: 8080              # Main application server (REST API + MCP)

mcp:
  server:
    enabled: true         # Enable MCP endpoints
    base-path: /mcp       # MCP endpoints served at /mcp/**
```

**Endpoint Routing:**
- REST API: `http://localhost:8080/api/v1/**`
- MCP Endpoints: `http://localhost:8080/mcp/**`

---

## Component Breakdown

### 1. Configuration & Auto-Configuration

**Location:** `com.raynermendez.spring_boot_mcp_companion.config`

```
McpAutoConfiguration         - Spring Boot auto-configuration entry point
├── McpServerProperties      - Configuration properties (type-safe)
├── McpWebConfig            - Web server & servlet configuration
└── McpEnvironmentPostProcessor - Environment variable binding
```

**Responsibilities:**
- Detect `@EnableMcpCompanion` annotation on main class
- Initialize Spring context with MCP beans
- Start embedded MCP server on configured port
- Load configuration from `application.yml` or environment
- Set up security filters and interceptors

**Key Interface:**
```java
@Configuration
@EnableConfigurationProperties(McpServerProperties.class)
public class McpAutoConfiguration {
    // Registers all MCP beans conditionally
    @ConditionalOnProperty(name = "mcp.server.enabled",
                          havingValue = "true",
                          matchIfMissing = true)
    public McpServer mcpServer(...) { ... }
}
```

### 2. Annotation Discovery & Metadata Extraction

**Location:** `com.raynermendez.spring_boot_mcp_companion.discovery`

```
MethodMetadataExtractor     - Scans and catalogs annotated methods
├── AnnotationScanner       - Find all @McpTool/@McpResource/@McpPrompt
├── MethodSignatureParser   - Extract parameter info
└── JavadocExtractor        - Parse Javadoc for descriptions
```

**Responsibilities:**
- Scan Spring context for annotated methods at startup
- Extract parameter names, types, constraints
- Generate descriptions from annotations or Javadoc
- Build internal registry of all tools/resources/prompts
- Handle inheritance and interface implementations

**Process Flow:**
```
Application Startup
  ↓
Spring Scans @EnableMcpCompanion
  ↓
MethodMetadataExtractor initialized
  ↓
For each Spring Bean:
  - Scan all methods for @McpTool/@McpResource/@McpPrompt
  - For each annotated method:
    * Extract parameter types, names, annotations
    * Parse @McpInput metadata
    * Get description from @McpTool or Javadoc
    * Build MethodMetadata object
    * Register in dispatcher
  ↓
Schema generation (concurrent)
  ↓
MCP server ready, listening on port 8090
```

### 3. Type Mapping Engine

**Location:** `com.raynermendez.spring_boot_mcp_companion.mapper`

```
DefaultMcpMappingEngine       - Type conversion orchestrator
├── JsonSchemaGenerator        - Java type → JSON Schema
├── JavaToJsonConverter        - Java object → JSON
├── JsonToJavaConverter        - JSON → Java object (via Jackson)
└── GenericTypeResolver        - Handle generic types (List<T>, Optional<T>)
```

**Responsibilities:**
- Map Java types to JSON schemas for MCP
- Convert incoming JSON to Java objects
- Convert Java response objects to JSON
- Handle type erasure with generics
- Support custom objects via annotation

**Type Mapping Examples:**

| Java Type | JSON Schema |
|-----------|-------------|
| `String` | `{"type": "string"}` |
| `Integer` | `{"type": "integer"}` |
| `BigDecimal` | `{"type": "number"}` |
| `LocalDateTime` | `{"type": "string", "format": "date-time"}` |
| `LocalDate` | `{"type": "string", "format": "date"}` |
| `Boolean` | `{"type": "boolean"}` |
| `List<String>` | `{"type": "array", "items": {"type": "string"}}` |
| `Map<String, String>` | `{"type": "object", "additionalProperties": true}` |
| `Optional<String>` | `{"type": "string"}` (nullable) |
| `enum OrderStatus` | `{"type": "string", "enum": ["PENDING", "COMPLETED"]}` |

**Custom Object Support:**

The framework supports POJOs (Plain Old Java Objects) through Jackson's ObjectMapper:

```java
public class Order {
    private String id;
    private List<Item> items;
    private BigDecimal total;

    // Jackson requires a no-arg constructor
    public Order() {}

    // Getters and setters (required for Jackson)
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    // ... more getters/setters
}

// Usage in a tool:
@McpTool(description = "Create order")
public OrderResponse createOrder(@McpInput Order order) {
    // Jackson automatically converts JSON to Order
    return new OrderResponse(order.getId(), order.getTotal());
}
```

### 4. Input Validation Engine

**Location:** `com.raynermendez.spring_boot_mcp_companion.validation`

```
ValidationEngine              - Jakarta Bean Validation orchestrator
├── ConstraintValidator       - Validate parameters against annotations
├── ConversionValidator       - Type conversion validation
├── SensitiveDataMasker       - Mask passwords/tokens in logs
└── CustomValidators          - Custom validation rules
```

**Responsibilities:**
- Validate incoming JSON matches expected types
- Apply Jakarta constraints (`@Email`, `@Size`, etc.)
- Clear error messages for validation failures
- Prevent invalid data from reaching business logic
- Mask sensitive data in error messages

**Validation Flow:**
```
Incoming JSON-RPC Request
  ↓
Parse JSON to parameter map
  ↓
For each parameter:
  1. Type conversion validation
  2. Constraint annotation validation
  3. Custom validator rules
  ↓
All valid?
  YES → Proceed to method invocation
  NO  → Return 400 Bad Request with error details
```

**Example:**
```java
@McpTool
public User createUser(
    @McpInput @Email String email,           // Validates email format
    @McpInput @Size(min=8, max=128) String pwd,  // Password length
    @McpInput @Min(18) @Max(120) Integer age     // Age range
) { ... }

// Invalid request:
{
  "email": "invalid-email",    // ❌ Validation error: not an email
  "pwd": "short",               // ❌ Validation error: too short
  "age": 15                      // ❌ Validation error: less than 18
}

// Response:
{
  "error": "Validation failed",
  "details": [
    {"field": "email", "message": "must be a valid email address"},
    {"field": "pwd", "message": "size must be between 8 and 128"},
    {"field": "age", "message": "must be greater than or equal to 18"}
  ]
}
```

### 5. Dispatcher & Router

**Location:** `com.raynermendez.spring_boot_mcp_companion.dispatch`

```
DefaultMcpDispatcher          - Route JSON-RPC calls to handlers
├── ToolDispatcher            - Route /tools/call requests
├── ResourceDispatcher        - Route /resources/read requests
└── PromptDispatcher          - Route /prompts/get requests
```

**Responsibilities:**
- Maintain registry of all tools/resources/prompts
- Match incoming requests to registered handlers
- Invoke methods with validated parameters
- Handle errors during invocation
- Return formatted responses

**Dispatch Logic:**
```
JSON-RPC Request: {"method": "tools/call", "params": {"name": "get_user", "arguments": {...}}}
  ↓
Extract method name ("tools/call") and tool name ("get_user")
  ↓
ToolDispatcher.dispatch(toolName, arguments)
  ↓
Lookup "get_user" in registry
  ↓
Found: Method reference + metadata
  ↓
Validate arguments
  ↓
Invoke: bean.getUser(argumentValue)
  ↓
Format response
  ↓
Send JSON-RPC response
```

### 6. Transport & Protocol Handler

**Location:** `com.raynermendez.spring_boot_mcp_companion.transport`

```
McpTransportController        - HTTP endpoint handler
├── JsonRpcRequest            - Request parsing
├── JsonRpcResponse           - Response formatting
├── StreamingResponse         - Support for large responses
└── StreamableResponse        - Streaming transport wrapper
```

**Responsibilities:**
- Handle HTTP requests on MCP server
- Parse JSON-RPC 2.0 protocol
- Format responses per JSON-RPC 2.0 spec
- Support streaming for large responses
- Manage MCP protocol version negotiation

**Supported Endpoints:**

| Endpoint | JSON-RPC Method | Purpose |
|----------|-----------------|---------|
| `GET /mcp/server-info` | N/A (custom) | Server metadata |
| `POST /mcp/tools/list` | `tools/list` | List all tools |
| `POST /mcp/tools/call` | `tools/call` | Invoke a tool |
| `POST /mcp/resources/list` | `resources/list` | List all resources |
| `POST /mcp/resources/read` | `resources/read` | Read a resource |
| `POST /mcp/prompts/list` | `prompts/list` | List all prompts |
| `POST /mcp/prompts/get` | `prompts/get` | Get a prompt |
| `POST /mcp/initialize` | `initialize` | Initialize protocol |

### 7. Security Layer

**Location:** `com.raynermendez.spring_boot_mcp_companion.security`

```
SecurityConfiguration         - Security setup
├── RateLimitInterceptor      - Rate limiting (requests/sec)
├── InputSanitizer            - Prevent injection attacks
├── SensitiveDataFilter       - Mask passwords/tokens
├── SlowlorisProtectionFilter - DoS protection
├── RequestBoundaryValidator  - Malformed request detection
└── ErrorSanitizer            - Hide internal details
```

**Responsibilities:**
- Rate limit requests (default: 100 req/sec)
- Sanitize user inputs
- Mask sensitive parameters in logs
- Protect against slowloris attacks
- Validate request structure
- Sanitize error messages
- Support Spring Security integration

**Security Filters:**
```
Incoming Request
  ↓ (Rate Limiting Check)
  ├─ Too many requests? → 429 Too Many Requests
  ↓
  ├─ (Slowloris Detection)
  ├─ Taking too long? → 408 Request Timeout
  ↓
  ├─ (Input Validation)
  ├─ Invalid format? → 400 Bad Request
  ↓
  ├─ (Spring Security - Optional)
  ├─ Unauthorized? → 401 Unauthorized
  ↓
  ├─ (Execution)
  ├─ Error during execution? → 500 with sanitized message
  ↓
  └─ Success → 200 with response
```

### 8. Error Handling & Exception Strategy

**Location:** `com.raynermendez.spring_boot_mcp_companion.exception`

```
GlobalExceptionHandler        - Centralized exception handling
├── ValidationException       - Invalid input parameters
├── MethodInvocationException - Business logic errors
├── JsonRpcException          - Protocol errors
└── ErrorSanitizer            - Clean up error details
```

**Responsibilities:**
- Catch all exceptions
- Format into JSON-RPC error responses
- Sanitize stack traces (don't leak internals)
- Preserve important error details for debugging
- Log errors for monitoring

**Error Response Format:**
```json
{
  "jsonrpc": "2.0",
  "error": {
    "code": -32603,
    "message": "Internal error",
    "data": {
      "message": "User not found",
      "type": "UserNotFoundException",
      "code": "USER_NOT_FOUND"
    }
  },
  "id": 1
}
```

---

## Request/Response Flow

### Complete Request Lifecycle

```
1. CLIENT SENDS REQUEST
   POST /mcp/tools/call HTTP/1.1
   Content-Type: application/json

   {
     "jsonrpc": "2.0",
     "id": 1,
     "method": "tools/call",
     "params": {
       "name": "create_user",
       "arguments": {
         "email": "user@example.com",
         "password": "secret123",
         "age": 25
       }
     }
   }

2. MCP TRANSPORT LAYER
   McpTransportController.handleToolCall()
   ├─ Parse JSON-RPC request
   ├─ Extract method & parameters
   ├─ Dispatch to ToolDispatcher
   └─ Handle exceptions

3. DISPATCHER LAYER
   DefaultMcpDispatcher.call()
   ├─ Lookup tool "create_user"
   ├─ Get MethodMetadata
   ├─ Get Spring bean instance
   └─ Prepare for invocation

4. SECURITY LAYER
   SecurityFilters process request
   ├─ Check rate limit
   ├─ Validate request format
   ├─ Check Spring Security (if enabled)
   └─ Sanitize inputs

5. VALIDATION LAYER
   ValidationEngine.validate()
   ├─ For each parameter:
   │  ├─ Type conversion
   │  ├─ Constraint checking (@Email, @Size, etc.)
   │  └─ Custom validators
   ├─ Collect all errors
   └─ Return validation result

6. TYPE MAPPING LAYER
   DefaultMcpMappingEngine.toJava()
   ├─ Convert JSON strings to Java types
   ├─ Handle generics (List<T>, Optional<T>)
   ├─ Invoke custom deserializers
   └─ Return strongly-typed parameters

7. METHOD INVOCATION
   BeanInvoker.invoke()
   ├─ Get Spring bean instance
   ├─ Call method with parameters
   ├─ Catch any exceptions
   └─ Return result or exception

8. RESPONSE MAPPING
   DefaultMcpMappingEngine.toJson()
   ├─ Convert Java response to JSON
   ├─ Handle null/empty responses
   ├─ Support streaming for large responses
   └─ Generate proper JSON representation

9. RESPONSE FORMATTING
   JsonRpcResponse.build()
   ├─ Wrap in JSON-RPC 2.0 format
   ├─ Include id and jsonrpc version
   ├─ Add result or error
   └─ Include metadata (timing, etc.)

10. MONITORING & LOGGING
    MetricsCollector & Logger
    ├─ Record execution time
    ├─ Log request/response (sanitized)
    ├─ Track errors
    ├─ Update Micrometer metrics
    └─ Send to observability backend

11. RESPONSE SENT
    HTTP 200 OK
    Content-Type: application/json

    {
      "jsonrpc": "2.0",
      "id": 1,
      "result": {
        "id": "user-12345",
        "email": "user@example.com",
        "createdAt": "2024-01-15T10:30:00Z"
      }
    }
```

---

## Type Mapping Engine

### Supported Type Conversions

**Primitive Types:**
- `String`, `Integer`, `Long`, `Short`, `Byte`
- `Double`, `Float`, `BigDecimal`, `BigInteger`
- `Boolean`, `Character`

**Temporal Types:**
- `LocalDate`, `LocalTime`, `LocalDateTime`
- `ZonedDateTime`, `Instant`, `Duration`, `Period`
- `Date`, `Calendar`, `Timestamp`

**Collections:**
- `List<T>`, `Set<T>`, `Queue<T>`
- `Map<K, V>`, `Dictionary<K, V>`
- Arrays: `T[]`
- `Stream<T>` (converted to List)

**Special Types:**
- `Optional<T>` (nullable values)
- `Enum` (enumeration values)
- Custom POJOs (automatically converted via Jackson)
- `java.net.URL`, `java.nio.file.Path`

**Nested Objects:**
```java
@McpTool
public OrderResponse createOrder(
    @McpInput OrderRequest request
) { ... }

// Automatically maps nested JSON to OrderRequest class
// which may contain List<Item>, Address, Customer objects
```

### JSON Schema Generation

The framework generates proper JSON schemas for all types:

```java
@McpTool
public void example(
    @McpInput String name,
    @McpInput Integer age,
    @McpInput List<String> tags,
    @McpInput Optional<String> notes
) { ... }

// Generates schema:
{
  "tools": [{
    "name": "example",
    "description": "...",
    "inputSchema": {
      "type": "object",
      "properties": {
        "name": {
          "type": "string",
          "description": "..."
        },
        "age": {
          "type": "integer",
          "description": "..."
        },
        "tags": {
          "type": "array",
          "items": {"type": "string"},
          "description": "..."
        },
        "notes": {
          "type": ["string", "null"],
          "description": "..."
        }
      },
      "required": ["name", "age", "tags"]
    }
  }]
}
```

---

## Validation Architecture

### Constraint Support

**Jakarta Bean Validation Constraints:**
- `@NotNull`, `@NotBlank`, `@NotEmpty`
- `@Email`, `@Pattern(regexp = "...")`
- `@Min(n)`, `@Max(n)`, `@Range`
- `@Size(min=x, max=y)`, `@Length`
- `@Positive`, `@Negative`
- `@Digits(integer=x, fraction=y)`
- `@DecimalMin`, `@DecimalMax`
- `@Past`, `@Future` (date validation)

**Custom Validators:**
```java
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MyValidator.class)
public @interface MyConstraint {
    String message() default "Constraint violated";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

public class MyValidator implements ConstraintValidator<MyConstraint, String> {
    public boolean isValid(String value, ConstraintValidatorContext ctx) {
        // Custom validation logic
        return true;
    }
}
```

### Validation Error Response

```json
{
  "jsonrpc": "2.0",
  "error": {
    "code": -32602,
    "message": "Invalid parameters",
    "data": {
      "violations": [
        {
          "field": "email",
          "message": "must be a valid email address",
          "constraint": "Email"
        },
        {
          "field": "password",
          "message": "size must be between 8 and 128",
          "constraint": "Size"
        }
      ]
    }
  },
  "id": 1
}
```

---

## Security Architecture

### Threat Model

| Threat | Mitigation |
|--------|-----------|
| **Injection Attacks** | Input validation, parameterized queries, Spring Security |
| **Sensitive Data Exposure** | Parameter masking, encryption, HTTPS enforcement |
| **DoS Attacks** | Rate limiting, request timeouts, Slowloris protection |
| **Unauthorized Access** | Spring Security integration, authentication, authorization |
| **Information Disclosure** | Error sanitization, audit logging, secure defaults |
| **XML External Entities** | Disabled by default in Jackson |

### Security Configuration

```java
@Configuration
public class McpSecurityConfiguration {

    @Bean
    public RateLimitInterceptor rateLimiter() {
        return new RateLimitInterceptor(
            requestsPerSecond: 100,    // Configurable
            burstsAllowed: 10
        );
    }

    @Bean
    public SensitiveDataFilter sensitiveFilter() {
        return new SensitiveDataFilter(
            sensitivePatterns: ["password", "token", "key", "secret"],
            replacementText: "***REDACTED***"
        );
    }

    @Bean
    public SlowlorisProtectionFilter slowlorisFilter() {
        return new SlowlorisProtectionFilter(
            maxRequestTime: 30_000,  // 30 seconds
            idleTimeout: 5_000       // 5 seconds
        );
    }
}
```

### Spring Security Integration

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain mcpSecurityFilterChain(HttpSecurity http)
            throws Exception {
        http
            .securityMatcher("/mcp/**")
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/mcp/server-info").permitAll()
                .requestMatchers("/mcp/**").authenticated()
            )
            .httpBasic(Customizer.withDefaults());
        return http.build();
    }
}
```

---

## Error Handling Strategy

### JSON-RPC Error Codes

| Code | Meaning | HTTP Status |
|------|---------|-------------|
| `-32700` | Parse error | 400 |
| `-32600` | Invalid request | 400 |
| `-32601` | Method not found | 404 |
| `-32602` | Invalid parameters | 400 |
| `-32603` | Internal error | 500 |
| `-32000` to `-32099` | Server error (reserved) | 500 |

### Error Sanitization

**Before (Unsafe):**
```
Error: Could not find user with ID 'abc' in database schema 'users'
at table 'user_accounts'. Column 'created_at' has value NULL.
NullPointerException at UserRepository.java:456
```

**After (Sanitized):**
```
Error: User not found
Code: USER_NOT_FOUND
```

The full stack trace is logged server-side for debugging, but sanitized in client responses.

---

## Extensibility Points

### 1. Custom Type Mappers

```java
@Component
public class CustomTypeMapper implements MappingConverter {

    @Override
    public boolean supports(Class<?> type) {
        return CustomObject.class.isAssignableFrom(type);
    }

    @Override
    public Object toJava(JsonNode node, Class<?> targetType) {
        // Custom JSON → Java conversion
    }

    @Override
    public JsonNode toJson(Object obj, TypeRef ref) {
        // Custom Java → JSON conversion
    }
}
```

### 2. Custom Validators

```java
@Component
public class UsernameValidator implements ConstraintValidator<Username, String> {

    @Autowired
    private UserRepository userRepository;

    @Override
    public boolean isValid(String username, ConstraintValidatorContext ctx) {
        return !userRepository.existsByUsername(username);
    }
}

// Usage:
@McpTool
public User createUser(@McpInput @Username String username) { ... }
```

### 3. Custom Interceptors

```java
@Component
public class LoggingInterceptor extends HandlerInterceptorAdapter {

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res,
                           Object handler) {
        log.info("MCP Request: {} {}", req.getMethod(), req.getRequestURI());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest req, HttpServletResponse res,
                              Object handler, Exception ex) {
        log.info("MCP Response: {} - {}", res.getStatus(),
                 ex != null ? ex.getMessage() : "OK");
    }
}
```

### 4. Custom Error Handlers

```java
@RestControllerAdvice
public class McpExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<?> handleBusinessException(BusinessException e) {
        return ResponseEntity.status(500).body(
            new JsonRpcError(
                code: -32603,
                message: e.getBusinessMessage(),
                data: Map.of("code", e.getErrorCode())
            )
        );
    }
}
```

---

## Design Patterns Used

| Pattern | Usage | Benefit |
|---------|-------|---------|
| **Decorator** | @McpTool, @McpResource, @McpPrompt annotations | Non-invasive enhancement |
| **Strategy** | Type mappers, validators, dispatchers | Pluggable behavior |
| **Registry** | Tool/resource/prompt catalog | Fast lookup, loose coupling |
| **Facade** | MCP Companion public API | Simple interface hiding complexity |
| **Adapter** | Spring Bean → MCP Tool bridge | Seamless integration |
| **Interceptor** | Security filters, logging | Cross-cutting concerns |
| **Builder** | JSON schema generation, response building | Flexible configuration |
| **Template Method** | Abstract validators, dispatchers | Code reuse |
| **Observer** | Spring events, metrics | Loosely coupled reactions |

---

## Performance Considerations

### Startup Performance

- **Cold Start:** ~500-800ms on fresh JVM
- **Warm Start:** ~100-200ms (after JIT compilation)
- **Metadata Extraction:** O(n) where n = number of annotated methods
- **Schema Generation:** Parallelized with ForkJoinPool

### Runtime Performance

| Operation | Latency (p99) | Notes |
|-----------|---------------|-------|
| Tool invocation | 2-5ms | Direct call + serialization |
| Validation | 0.5-2ms | Per parameter |
| Type mapping | 0.5-1ms | Per parameter |
| Total overhead | 3-8ms | Per request |

### Memory Usage

- **Metadata Cache:** ~2-3 MB per 100 tools
- **Schema Cache:** ~1-2 MB per 100 tools
- **Total Overhead:** ~5-10 MB (negligible)

---

## Monitoring & Observability

### Built-in Metrics (Micrometer)

```
# Tool invocations
mcp.tools.calls{tool="get_user"} - counter
mcp.tools.duration{tool="get_user"} - timer
mcp.tools.errors{tool="get_user", error="ValidationException"} - counter

# Validation
mcp.validation.failures - counter
mcp.validation.duration - timer

# Rate limiting
mcp.ratelimit.rejections - counter
```

### Log Categories

```
[com.raynermendez.spring_boot_mcp_companion.transport] - HTTP traffic
[com.raynermendez.spring_boot_mcp_companion.security] - Security events
[com.raynermendez.spring_boot_mcp_companion.validation] - Validation failures
[com.raynermendez.spring_boot_mcp_companion.dispatch] - Method invocations
[com.raynermendez.spring_boot_mcp_companion.exception] - Error handling
```

---

## Future Architecture Improvements

### Planned Enhancements

- **Async Streaming Tools** - StreamingResponse as first-class citizen
- **WebSocket Transport** - Bidirectional MCP over WebSockets
- **GraphQL Endpoint** - Expose MCP tools via GraphQL
- **Multi-Tenant Support** - Tool scoping by tenant/org
- **Custom Middleware Pipeline** - User-defined request/response interceptors
- **gRPC Endpoint** - Alternative to HTTP/JSON-RPC for low-latency RPC

---

**[← Back to main README](../README.md)**

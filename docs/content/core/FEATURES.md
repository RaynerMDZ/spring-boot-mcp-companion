# Spring Boot MCP Companion - Features

## Overview of Features

### ✅ Zero-Friction Integration

Add a single annotation to your Spring Boot main class and the entire MCP framework is available:

```java
@SpringBootApplication
@EnableMcpCompanion
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}
```

No XML configuration, no manual bean wiring, no setup code. Your existing Spring components are automatically available as MCP endpoints.

### ✅ Convention over Configuration

The framework uses sensible defaults and derives values from your code:

- **Naming**: Java method names automatically convert to snake_case for MCP (e.g., `getOrder()` → `get_order`)
- **Descriptions**: Uses Javadoc if available, falls back to annotation descriptions
- **Parameter Schemas**: Automatically generates JSON schemas from Java types
- **Validation Rules**: Reads Jakarta Bean Validation annotations (`@Email`, `@Size`, etc.)

### ✅ Type-Safe with Automatic JSON Schema Generation

All Java types are automatically converted to JSON Schema:

```java
@McpTool
public Order createOrder(
    @McpInput String orderId,
    @McpInput BigDecimal price,
    @McpInput LocalDateTime createdAt,
    @McpInput List<Item> items,
    @McpInput OrderStatus status
) { ... }
```

Generates:
- Correct JSON type mappings (`BigDecimal` → `"number"`, `LocalDateTime` → `"string"` with format)
- Enum value constraints
- List/array type definitions
- Nested object schemas
- Required field tracking

### ✅ Input Validation

Validation happens automatically at the MCP boundary using Jakarta Bean Validation:

```java
@McpTool
public User createUser(
    @McpInput @NotBlank String name,
    @McpInput @Email String email,
    @McpInput @Size(min = 8, max = 128) String password,
    @McpInput @Min(18) @Max(120) Integer age
) { ... }
```

Invalid requests are rejected with clear error messages before your code runs.

**Supported Constraints:**
- `@NotNull`, `@NotBlank`
- `@Email`, `@Pattern(regexp = "...")`
- `@Min(n)`, `@Max(n)`
- `@Size(min = x, max = y)`
- `@Positive`, `@Negative`
- Any custom Jakarta Validator

### ✅ Three Types of Capabilities

**Tools (Functions)**
- Remote-callable business logic
- Sync or async execution
- Named parameters with validation

```java
@McpTool(description = "Create a new user account")
public User createUser(@McpInput String email) { ... }
```

**Resources**
- Data accessible by URI pattern
- Streaming support
- MIME type metadata

```java
@McpResource(
    uri = "user://{userId}/profile",
    description = "User profile resource",
    mimeType = "application/json"
)
public UserProfile getUserProfile(@McpInput String userId) { ... }
```

**Prompts**
- Reusable prompt templates
- Parameter substitution
- Dynamic content generation

```java
@McpPrompt(
    name = "user_summary",
    description = "Generate a summary of a user's activity"
)
public String generateUserSummary(@McpInput String userId) { ... }
```

### ✅ Production Observability

Built-in observability without code changes:

**Metrics** (Micrometer)
- Request counts per tool/resource/prompt
- Execution times (latency)
- Error rates
- Custom tags and dimensions

**Logging**
- Structured request/response logging
- Sensitive parameter masking
- Debug-level operation traces
- Integration with Spring's logging framework

**Health Checks**
- Spring Boot Actuator integration
- `/actuator/health` shows MCP status
- Custom health indicators

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics
  metrics:
    tags:
      application: my-service
```

### ✅ Backward Compatible

MCP Companion doesn't interfere with existing Spring Boot functionality:

- Your regular `@RestController` endpoints still work
- Other Spring features (`@Service`, `@Repository`, etc.) work normally
- Can be disabled per environment: `mcp.server.enabled=false`
- Non-invasive - only touches beans you explicitly annotate

### ✅ REST Endpoints

All capabilities exposed via standard REST/JSON-RPC 2.0:

```bash
# List available tools
curl -X POST http://localhost:8080/mcp/tools/list \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc": "2.0", "id": 1, "method": "tools/list"}'

# Call a tool
curl -X POST http://localhost:8080/mcp/tools/call \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 2,
    "method": "tools/call",
    "params": {
      "name": "create_order",
      "arguments": {"orderId": "123", "price": "99.99"}
    }
  }'
```

### ✅ Flexible Configuration

Control MCP behavior via YAML configuration. MCP runs on a **separate embedded server**:

```yaml
server:
  port: 8080                  # Main application port

mcp:
  server:
    enabled: true             # Enable MCP (default: true)
    port: 8090                # MCP server port (default: 8090)
    name: "Order Service"
    version: "1.0.0"
    base-path: /mcp
```

**Per environment configuration:**
```yaml
# application-dev.yml
server:
  port: 8080
mcp:
  server:
    enabled: true
    port: 8090

# application-prod.yml
server:
  port: 8080                  # Different ports per environment
mcp:
  server:
    enabled: true
    port: 9090

# application-test.yml
server:
  port: 8080
mcp:
  server:
    enabled: false            # Disable MCP in tests
```

### ✅ No External Dependencies

Minimal footprint:
- Uses Spring Boot's built-in components
- No proprietary protocols
- Standard JSON serialization (Jackson)
- Standard validation (Jakarta)
- Works with Spring's ecosystem

### ✅ Security Ready

Multiple levels of security:

**Sensitive Parameter Handling**
```java
@McpInput(sensitive = true) String apiKey,
@McpInput(sensitive = true) String password
```
- Not logged or included in metrics dimensions
- Masked in debug output

**Spring Security Integration**
```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) {
        http.authorizeHttpRequests(auth -> auth
            .requestMatchers("/mcp/**").authenticated()
            .anyRequest().permitAll()
        );
        return http.build();
    }
}
```

**HTTPS Support**
```yaml
server:
  ssl:
    enabled: true
    key-store: ${KEYSTORE_PATH}
    key-store-password: ${KEYSTORE_PASSWORD}
```

**CORS Configuration**
```java
@Configuration
public class CorsConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/mcp/**")
            .allowedOrigins("https://trusted-domain.com")
            .allowedMethods("GET", "POST")
            .maxAge(3600);
    }
}
```

## Feature Comparison

| Feature | Spring MCP Companion | Manual Implementation |
|---------|---------------------|----------------------|
| Setup Time | 5 minutes | Hours |
| Lines of Code | Annotations only | Hundreds |
| JSON Schema Generation | Automatic | Manual |
| Input Validation | Automatic | Manual |
| Documentation | Generated | Manual |
| Metrics | Built-in | Custom |
| Error Handling | Built-in | Custom |
| Type Safety | Full | Partial |

## What You Get Out of the Box

1. **Auto-discovering annotations** on your Spring beans
2. **JSON Schema generation** from Java types
3. **Input validation** using existing Jakarta Bean Validation annotations
4. **REST endpoints** exposing all MCP operations
5. **Error handling** with proper HTTP status codes
6. **Metrics collection** via Micrometer
7. **Health checks** via Spring Actuator
8. **Security integration** points for your auth needs
9. **Configuration properties** for customization
10. **Comprehensive logging** with sensitive data masking

See [API_REFERENCE.md](API_REFERENCE.md) for complete annotation details and [EXAMPLES.md](EXAMPLES.md) for working examples.

# Spring Boot MCP Companion

A lightweight, production-ready annotation-driven framework for integrating the **Model Context Protocol (MCP)** into Spring Boot applications. Expose your Spring beans as remote-callable functions, resources, and prompts with zero refactoring.

[![Maven Central](https://img.shields.io/maven-central/v/com.raynermendez/spring-boot-mcp-companion-core)](https://central.sonatype.com/artifact/com.raynermendez/spring-boot-mcp-companion-core)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/java-25+-green.svg)](https://adoptopenjdk.net/)
[![Spring Boot](https://img.shields.io/badge/spring%20boot-4.0.5+-green.svg)](https://spring.io/projects/spring-boot)

## Table of Contents

- [What is MCP?](#what-is-mcp)
- [Features](#features)
- [Quick Start](#quick-start)
  - [Installation](#installation)
  - [Basic Usage](#basic-usage)
  - [Run Your First MCP Server](#run-your-first-mcp-server)
- [Core Concepts](#core-concepts)
  - [@EnableMcpCompanion](#enablemcpcompanion)
  - [@McpTool](#mcptool)
  - [@McpResource](#mcpresource)
  - [@McpPrompt](#mcpprompt)
  - [@McpInput](#mcpinput)
- [Configuration](#configuration)
- [MCP Endpoints](#mcp-endpoints)
- [Examples](#examples)
  - [Order Management Service](#order-management-service)
  - [Document Processing](#document-processing)
  - [Configuration Reader](#configuration-reader)
- [Advanced Usage](#advanced-usage)
  - [Custom Input Validation](#custom-input-validation)
  - [Error Handling](#error-handling)
  - [Integration with Spring Security](#integration-with-spring-security)
  - [Observability & Metrics](#observability--metrics)
- [Best Practices](#best-practices)
- [API Reference](#api-reference)
- [Security Considerations](#security-considerations)
- [Performance & Optimization](#performance--optimization)
- [Troubleshooting](#troubleshooting)
- [Contributing](#contributing)
- [License](#license)

---

## What is MCP?

**Model Context Protocol (MCP)** is a standardized JSON-RPC 2.0 protocol for exposing application capabilities to AI models and other clients. It enables:

- **Functions/Tools** - Remote-callable business logic
- **Resources** - Data accessible via URI patterns
- **Prompts** - Reusable template generators
- **Server Info** - Metadata about available capabilities

This framework makes it trivial to expose your Spring Boot application's functionality through the MCP protocol.

**Learn more:** [Model Context Protocol Documentation](https://modelcontextprotocol.io/)

---

## Features

✅ **Zero-Friction Integration** - One annotation (`@EnableMcpCompanion`) enables the entire framework
✅ **Convention over Configuration** - Automatic naming, schema generation, defaults
✅ **Type-Safe** - Full Java type support with automatic JSON schema generation
✅ **Input Validation** - Jakarta Bean Validation at MCP boundary
✅ **Production Observability** - Micrometer metrics, structured logging, health checks
✅ **Backward Compatible** - Non-invasive, doesn't affect existing Spring Boot behavior
✅ **REST Endpoints** - Standard JSON-RPC 2.0 endpoints
✅ **Flexible Configuration** - Customize naming, paths, behavior via YAML
✅ **No External Dependencies** - Minimal footprint, uses Spring Boot ecosystem
✅ **Security Ready** - Optional Spring Security integration, sensitive parameter handling

---

## Quick Start

### Installation

#### Maven
```xml
<dependency>
  <groupId>com.raynermendez</groupId>
  <artifactId>spring-boot-mcp-companion-core</artifactId>
  <version>1.0.0</version>
</dependency>
```

#### Gradle
```gradle
dependencies {
  implementation 'com.raynermendez:spring-boot-mcp-companion-core:1.0.0'
}
```

### Basic Usage

**1. Enable the framework** in your Spring Boot main class:

```java
@SpringBootApplication
@EnableMcpCompanion  // ← One annotation!
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}
```

**2. Annotate your service methods:**

```java
@Service
public class OrderService {

    @McpTool(description = "Get order details by ID")
    public Order getOrder(@McpInput(required = true) String orderId) {
        return orderRepository.findById(orderId);
    }
}
```

**3. That's it!** The method is now callable via MCP.

### Run Your First MCP Server

```bash
mvn spring-boot:run
```

Test the MCP endpoints:

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
      "name": "get_order",
      "arguments": {
        "orderId": "ORDER-001"
      }
    }
  }'
```

---

## Core Concepts

### @EnableMcpCompanion

**Location:** Add to your `@SpringBootApplication` main class.

**What it does:**
- Enables the entire MCP framework
- Registers all required beans automatically
- Activates endpoint scanning and registration
- Configures auto-configuration via `application.yml`

```java
@SpringBootApplication
@EnableMcpCompanion
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}
```

**Configuration via application.yml:**
```yaml
mcp:
  server:
    enabled: true              # Enable/disable MCP
    base-path: /mcp            # Endpoint prefix
    name: my-service           # Server name
    version: 1.0.0             # Server version
```

---

### @McpTool

**Purpose:** Expose a method as a remotely-callable function.

**Attributes:**
- `name()` - Tool name in MCP protocol (defaults to method name)
- `description()` - What the tool does
- `tags[]` - Optional categorization

**Example:**
```java
@Service
public class OrderService {

    @McpTool(
        description = "Create a new order",
        tags = {"orders", "write"}
    )
    public OrderResult createOrder(
        @McpInput(description = "Customer ID") String customerId,
        @McpInput(description = "List of item IDs") List<String> itemIds,
        @McpInput(description = "Discount percentage", required = false) BigDecimal discount
    ) {
        // Business logic
        return new OrderResult(orderId, total, estimatedDelivery);
    }
}
```

**MCP Invocation:**
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "tools/call",
  "params": {
    "name": "create_order",
    "arguments": {
      "customerId": "CUST-123",
      "itemIds": ["ITEM-1", "ITEM-2"],
      "discount": 10.0
    }
  }
}
```

**Response:**
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "content": [
      {
        "type": "text",
        "text": "{\"orderId\":\"ORDER-001\",\"total\":189.99,\"estimatedDelivery\":\"2026-03-30\"}"
      }
    ]
  }
}
```

---

### @McpResource

**Purpose:** Expose data via URI patterns, like REST resources.

**Attributes:**
- `uri()` - URI template (e.g., `"file:///{path}"`, `"user://{id}"`)
- `name()` - Resource name
- `description()` - What it provides
- `mimeType()` - Content type (default: `"application/octet-stream"`)

**Example:**
```java
@Service
public class FileService {

    @McpResource(
        uri = "file:///{filePath}",
        description = "Read file content",
        mimeType = "text/plain"
    )
    public String readFile(@McpInput(description = "File path") String filePath) {
        return Files.readString(Paths.get(filePath));
    }

    @McpResource(
        uri = "config://app/{section}/{key}",
        description = "Get configuration value",
        mimeType = "application/json"
    )
    public String getConfig(
        @McpInput(description = "Config section") String section,
        @McpInput(description = "Config key") String key
    ) {
        return configService.get(section, key);
    }
}
```

**MCP Invocation:**
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "resources/read",
  "params": {
    "uri": "file:///documents/report.txt"
  }
}
```

---

### @McpPrompt

**Purpose:** Expose reusable prompt templates that generate formatted text.

**Attributes:**
- `name()` - Prompt template name
- `description()` - What it generates

**Example:**
```java
@Service
public class PromptService {

    @McpPrompt(
        name = "order-summary",
        description = "Generate formatted order summary"
    )
    public String generateOrderSummary(@McpInput(required = true) String orderId) {
        Order order = orderService.getOrder(orderId);
        return String.format(
            "Order Summary\n" +
            "=============\n" +
            "ID: %s\n" +
            "Item: %s\n" +
            "Price: $%.2f\n" +
            "Status: %s",
            order.getId(), order.getName(), order.getPrice(), order.getStatus()
        );
    }

    @McpPrompt(
        name = "code-review-template",
        description = "Generate code review checklist"
    )
    public String generateCodeReviewTemplate(
        @McpInput(description = "PR title") String prTitle,
        @McpInput(description = "Files changed count") int filesChanged
    ) {
        return "# Code Review: " + prTitle + "\n\n" +
               "## Files Changed: " + filesChanged + "\n\n" +
               "- [ ] Code follows style guide\n" +
               "- [ ] Tests added/updated\n" +
               "- [ ] Documentation updated\n" +
               "- [ ] No breaking changes\n";
    }
}
```

**MCP Invocation:**
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "prompts/get",
  "params": {
    "name": "order-summary",
    "arguments": {
      "orderId": "ORDER-001"
    }
  }
}
```

---

### @McpInput

**Purpose:** Mark method parameters as MCP inputs with metadata.

**Attributes:**
- `name()` - Parameter name (defaults to method parameter name)
- `description()` - What the parameter expects
- `required` - `true` (default) = required, `false` = optional
- `sensitive` - `true` = redacted in logs (passwords, tokens)

**Example:**
```java
@McpTool(description = "Create user")
public User createUser(
    @McpInput(
        description = "User email address",
        required = true
    )
    String email,

    @McpInput(
        description = "User password",
        required = true,
        sensitive = true  // Won't appear in logs
    )
    String password,

    @McpInput(
        description = "User full name",
        required = false
    )
    String fullName
) {
    return userService.create(email, password, fullName);
}
```

**Validation Features:**
- `@NotNull` - Required field validation
- `@NotBlank` - Non-empty string validation
- `@Email` - Email format validation
- `@Min / @Max` - Number range validation
- `@Size` - String/collection size validation
- Any Jakarta Bean Validation constraint

```java
@McpTool(description = "Create product")
public Product createProduct(
    @McpInput @NotBlank String name,
    @McpInput @Min(0) BigDecimal price,
    @McpInput(required = false) @Size(max = 1000) String description
) {
    return productService.create(name, price, description);
}
```

---

## Configuration

### application.yml

```yaml
spring:
  application:
    name: my-mcp-service

# Optional: MCP configuration (uncomment to customize)
mcp:
  server:
    # Enable/disable MCP endpoints (default: true)
    enabled: true

    # Base path for all MCP endpoints (default: /mcp)
    base-path: /mcp

    # Server name advertised to MCP clients (default: spring-boot-mcp-companion)
    name: My Service Name

    # Server version advertised to clients (default: 1.0.0)
    version: 1.0.0
```

### Property Sources

Properties can come from:
- `application.yml` / `application.properties`
- Environment variables: `MCP_SERVER_ENABLED=true`, `MCP_SERVER_BASE_PATH=/api/mcp`
- System properties: `-Dmcp.server.enabled=true`
- Spring profiles: `application-prod.yml`

**Example with profiles:**
```yaml
# application-prod.yml
mcp:
  server:
    enabled: true
    name: production-service
    version: 1.0.0
```

Run with: `mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=prod"`

---

## MCP Endpoints

All endpoints use JSON-RPC 2.0 protocol.

### GET `/mcp/server-info`

Get server metadata.

**Response:**
```json
{
  "name": "my-service",
  "version": "1.0.0"
}
```

### POST `/mcp/tools/list`

List all available tools.

**Request:**
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "tools/list"
}
```

**Response:**
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "tools": [
      {
        "name": "get_order",
        "description": "Retrieves an order by its ID",
        "inputSchema": {
          "type": "object",
          "properties": {
            "orderId": {
              "type": "string",
              "description": "Order ID"
            }
          },
          "required": ["orderId"]
        }
      }
    ]
  }
}
```

### POST `/mcp/tools/call`

Invoke a tool.

**Request:**
```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "method": "tools/call",
  "params": {
    "name": "get_order",
    "arguments": {
      "orderId": "ORDER-001"
    }
  }
}
```

**Success Response:**
```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "result": {
    "content": [
      {
        "type": "text",
        "text": "{\"orderId\":\"ORDER-001\",\"amount\":99.99,\"status\":\"COMPLETED\"}"
      }
    ]
  }
}
```

**Error Response:**
```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "error": {
    "code": -32601,
    "message": "Tool not found",
    "data": null
  }
}
```

### POST `/mcp/resources/list`

List all available resources.

**Request:**
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "resources/list"
}
```

**Response:**
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "resources": [
      {
        "uri": "order://{orderId}",
        "name": "order",
        "description": "Retrieves order by ID as resource",
        "mimeType": "application/json"
      }
    ]
  }
}
```

### POST `/mcp/resources/read`

Read a resource.

**Request:**
```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "method": "resources/read",
  "params": {
    "uri": "order://ORDER-001"
  }
}
```

**Response:**
```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "result": {
    "uri": "order://ORDER-001",
    "content": "{\"orderId\":\"ORDER-001\",\"amount\":99.99,\"status\":\"COMPLETED\"}",
    "mimeType": "application/json"
  }
}
```

### POST `/mcp/prompts/list`

List all available prompts.

**Response:**
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "prompts": [
      {
        "name": "order-summary",
        "description": "Generates an order summary prompt",
        "arguments": [
          {
            "name": "orderId",
            "description": "Order ID",
            "required": true
          }
        ]
      }
    ]
  }
}
```

### POST `/mcp/prompts/get`

Invoke a prompt template.

**Request:**
```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "method": "prompts/get",
  "params": {
    "name": "order-summary",
    "arguments": {
      "orderId": "ORDER-001"
    }
  }
}
```

---

## Examples

### Order Management Service

Complete example showing all annotation types:

```java
@Service
public class OrderService {

    @Autowired
    private OrderRepository orderRepository;

    // ========== TOOLS ==========
    // Callable functions for business logic

    @McpTool(
        description = "Retrieve an order by ID",
        tags = {"orders", "read"}
    )
    public Order getOrder(@McpInput(required = true) String orderId) {
        return orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));
    }

    @McpTool(
        description = "Create a new order",
        tags = {"orders", "write"}
    )
    public Order createOrder(
        @McpInput(description = "Customer ID", required = true) String customerId,
        @McpInput(description = "List of item IDs") List<String> itemIds,
        @McpInput(description = "Discount percentage", required = false) BigDecimal discount
    ) {
        Order order = new Order(customerId, itemIds, discount);
        return orderRepository.save(order);
    }

    @McpTool(description = "Cancel an order")
    public boolean cancelOrder(
        @McpInput(required = true) String orderId,
        @McpInput(required = false) String reason
    ) {
        Order order = getOrder(orderId);
        order.setStatus("CANCELLED");
        order.setCancellationReason(reason);
        orderRepository.save(order);
        return true;
    }

    // ========== RESOURCES ==========
    // URI-based data access (like REST endpoints)

    @McpResource(
        uri = "order://{orderId}",
        description = "Order as resource",
        mimeType = "application/json"
    )
    public Order getOrderResource(@McpInput(required = true) String orderId) {
        return getOrder(orderId);
    }

    @McpResource(
        uri = "orders://customer/{customerId}",
        description = "All orders for a customer",
        mimeType = "application/json"
    )
    public List<Order> getCustomerOrders(@McpInput(required = true) String customerId) {
        return orderRepository.findByCustomerId(customerId);
    }

    // ========== PROMPTS ==========
    // Reusable template generators

    @McpPrompt(
        name = "order-summary",
        description = "Generate order summary for display"
    )
    public String generateOrderSummary(@McpInput(required = true) String orderId) {
        Order order = getOrder(orderId);
        return String.format(
            "Order Summary\n" +
            "=============\n" +
            "ID: %s\n" +
            "Customer: %s\n" +
            "Items: %d\n" +
            "Total: $%.2f\n" +
            "Status: %s",
            order.getId(),
            order.getCustomerId(),
            order.getItems().size(),
            order.getTotal(),
            order.getStatus()
        );
    }

    @McpPrompt(
        name = "order-details-html",
        description = "Generate HTML order details"
    )
    public String generateOrderDetailsHtml(@McpInput(required = true) String orderId) {
        Order order = getOrder(orderId);
        return String.format(
            "<div class='order'>\n" +
            "  <h2>Order #%s</h2>\n" +
            "  <p>Customer: %s</p>\n" +
            "  <p>Total: $%.2f</p>\n" +
            "  <p>Status: <strong>%s</strong></p>\n" +
            "</div>",
            order.getId(),
            order.getCustomerId(),
            order.getTotal(),
            order.getStatus()
        );
    }

    // ========== REGULAR METHODS ==========
    // NOT exposed to MCP

    public boolean orderExists(String orderId) {
        return orderRepository.existsById(orderId);
    }

    public void updateInventory(List<String> itemIds) {
        // Internal business logic
    }
}
```

---

### Document Processing

```java
@Service
public class DocumentService {

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private PdfProcessor pdfProcessor;

    @McpTool(description = "Extract text from document")
    public String extractText(@McpInput(required = true) String documentId) {
        Document doc = documentRepository.findById(documentId)
            .orElseThrow(() -> new DocumentNotFoundException(documentId));
        return pdfProcessor.extractText(doc.getFilePath());
    }

    @McpResource(
        uri = "document://{documentId}",
        description = "Get document metadata",
        mimeType = "application/json"
    )
    public Document getDocument(@McpInput(required = true) String documentId) {
        return documentRepository.findById(documentId)
            .orElseThrow(() -> new DocumentNotFoundException(documentId));
    }

    @McpResource(
        uri = "document://{documentId}/content",
        description = "Get document content",
        mimeType = "application/pdf"
    )
    public byte[] getDocumentContent(@McpInput(required = true) String documentId) {
        Document doc = getDocument(documentId);
        return Files.readAllBytes(Paths.get(doc.getFilePath()));
    }

    @McpPrompt(
        name = "document-summary",
        description = "Generate document summary"
    )
    public String generateSummary(
        @McpInput(required = true) String documentId,
        @McpInput(description = "Summary length: short/medium/long", required = false) String length
    ) {
        Document doc = getDocument(documentId);
        String text = extractText(documentId);
        return generateSummaryFromText(text, length != null ? length : "medium");
    }
}
```

---

### Configuration Reader

```java
@Service
public class ConfigService {

    @Autowired
    private Environment environment;

    @McpTool(description = "Get configuration value")
    public String getConfigValue(
        @McpInput(description = "Config key", required = true) String key,
        @McpInput(description = "Default value", required = false) String defaultValue
    ) {
        return environment.getProperty(key, defaultValue);
    }

    @McpResource(
        uri = "config://{key}",
        description = "Get config value as resource",
        mimeType = "text/plain"
    )
    public String getConfigResource(@McpInput(required = true) String key) {
        String value = environment.getProperty(key);
        if (value == null) {
            throw new ConfigKeyNotFoundException("Config key not found: " + key);
        }
        return value;
    }

    @McpPrompt(
        name = "app-info",
        description = "Generate application info"
    )
    public String generateAppInfo() {
        return String.format(
            "Application: %s\n" +
            "Version: %s\n" +
            "Environment: %s\n" +
            "Debug Mode: %s",
            environment.getProperty("spring.application.name"),
            environment.getProperty("app.version"),
            environment.getActiveProfiles().length > 0 ?
                String.join(",", environment.getActiveProfiles()) : "default",
            environment.getProperty("debug")
        );
    }
}
```

---

## Advanced Usage

### Custom Input Validation

Use Jakarta Bean Validation annotations:

```java
@McpTool(description = "Create user account")
public User createUser(
    @McpInput @Email String email,
    @McpInput @Size(min = 8, max = 20) String password,
    @McpInput @Pattern(regexp = "^[a-zA-Z ]+$") String fullName,
    @McpInput(required = false) @Min(18) @Max(120) Integer age
) {
    return userService.create(email, password, fullName, age);
}
```

**Supported Validations:**
- `@NotNull` / `@NotBlank` - Required fields
- `@Email` - Valid email format
- `@Min(n)` / `@Max(n)` - Number ranges
- `@Size(min, max)` - String/collection length
- `@Pattern(regexp)` - String pattern matching
- `@DecimalMin` / `@DecimalMax` - Decimal ranges
- Any custom constraint validator

Validation errors automatically return JSON-RPC error responses:

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "error": {
    "code": -32602,
    "message": "Invalid parameters",
    "data": {
      "violations": [
        {
          "parameter": "email",
          "constraint": "Email",
          "message": "must be a valid email address"
        }
      ]
    }
  }
}
```

### Error Handling

Exceptions in tool methods automatically convert to JSON-RPC errors:

```java
@McpTool(description = "Delete user")
public void deleteUser(@McpInput(required = true) String userId) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));

    if (user.isAdmin()) {
        throw new IllegalOperationException("Cannot delete admin users");
    }

    userRepository.delete(user);
}
```

**Error Responses:**

Custom exception → JSON-RPC error:
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "error": {
    "code": -32603,
    "message": "User not found: USER-123",
    "data": null
  }
}
```

### Integration with Spring Security

Enable optional authentication:

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-security</artifactId>
</dependency>
```

Configure security:

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/mcp/server-info").permitAll()  // Public
                .requestMatchers("/mcp/**").authenticated()       // Protected
                .anyRequest().permitAll()
            )
            .httpBasic(withDefaults());
        return http.build();
    }
}
```

Access authenticated user in tools:

```java
@McpTool(description = "Get my orders")
public List<Order> getMyOrders() {
    SecurityContext context = SecurityContextHolder.getContext();
    String userId = context.getAuthentication().getName();
    return orderRepository.findByCustomerId(userId);
}
```

### Observability & Metrics

The framework automatically provides:

**Metrics (via Micrometer):**
```
mcp.tools.invoked{tool=get_order} = 42
mcp.tools.errors{tool=get_order} = 1
mcp.tools.duration{tool=get_order} = 150ms
```

Enable in `application.yml`:
```yaml
management:
  endpoints:
    web:
      exposure:
        include: prometheus,health,metrics
```

Access metrics:
```bash
curl http://localhost:8080/actuator/metrics
curl http://localhost:8080/actuator/metrics/mcp.tools.invoked
```

**Health Check:**
```bash
curl http://localhost:8080/actuator/health
```

Response:
```json
{
  "status": "UP",
  "components": {
    "mcpHealthIndicator": {
      "status": "UP",
      "details": {
        "tools": 5,
        "resources": 3,
        "prompts": 2
      }
    }
  }
}
```

**Structured Logging:**
The framework logs all MCP invocations with structured fields:

```
2026-03-27 10:30:45 INFO McpObservabilityAspect -
  [MCP_TOOL] method=get_order params={orderId=ORDER-001}
  duration=42ms result=success
```

---

## Best Practices

### 1. **Use Descriptive Names and Descriptions**

```java
// ✅ Good
@McpTool(description = "Retrieve customer order by unique order ID")
public Order getOrder(@McpInput(description = "Unique order identifier") String orderId)

// ❌ Poor
@McpTool
public Order get(String o)
```

### 2. **Mark Sensitive Parameters**

```java
// ✅ Good
@McpTool
public LoginResult authenticate(
    @McpInput(description = "User email", required = true) String email,
    @McpInput(description = "User password", required = true, sensitive = true) String password
)

// ❌ Poor
@McpTool
public LoginResult authenticate(
    String email,
    String password  // No sensitive flag!
)
```

### 3. **Use Appropriate Annotation Types**

```java
// ✅ Tool - for business operations
@McpTool(description = "Process payment")
public PaymentResult processPayment(...)

// ✅ Resource - for data retrieval
@McpResource(uri = "transaction://{id}")
public Transaction getTransaction(...)

// ✅ Prompt - for template generation
@McpPrompt(name = "receipt")
public String generateReceipt(...)

// ❌ Don't overuse tools for simple read operations
@McpTool(description = "Get user by ID")  // Should be @McpResource
public User getUser(String userId)
```

### 4. **Add Validation Constraints**

```java
// ✅ Good - explicit validation
@McpTool
public User createUser(
    @McpInput @Email String email,
    @McpInput @Size(min = 8) String password,
    @McpInput @Min(18) Integer age
)

// ❌ Poor - no validation
@McpTool
public User createUser(String email, String password, Integer age)
```

### 5. **Group Related Tools with Tags**

```java
@McpTool(tags = {"orders", "read"})
public Order getOrder(...)

@McpTool(tags = {"orders", "write"})
public Order createOrder(...)

@McpTool(tags = {"orders", "admin"})
public void deleteOrder(...)
```

### 6. **Use Meaningful Return Types**

```java
// ✅ Good - structured response
public class OrderResult {
    public String orderId;
    public BigDecimal total;
    public String status;
}

// ❌ Poor - unstructured
@McpTool
public String createOrder(...)  // Returns JSON string

// ✅ Also fine - let framework serialize
@McpTool
public Order createOrder(...)  // Returns Order object
```

### 7. **Document Complex Scenarios**

```java
/**
 * Processes a bulk order with validation.
 *
 * Rules:
 * - Customer must exist
 * - Items must be in stock
 * - Minimum order amount is $10
 * - Maximum 100 items per order
 *
 * @param customerId must be existing customer ID
 * @param itemIds must not be empty
 * @param expedited whether to use expedited shipping
 * @return order confirmation
 */
@McpTool(description = "Process bulk order with business rules")
public OrderConfirmation processBulkOrder(
    @McpInput(description = "Existing customer ID") String customerId,
    @McpInput(description = "List of item IDs (max 100)") List<String> itemIds,
    @McpInput(description = "Use expedited shipping", required = false) Boolean expedited
) {
    // Implementation
}
```

### 8. **Keep Methods Focused**

```java
// ✅ Good - single responsibility
@McpTool(description = "Get order")
public Order getOrder(String orderId) { ... }

@McpTool(description = "Update order status")
public Order updateOrderStatus(String orderId, String status) { ... }

// ❌ Poor - too many responsibilities
@McpTool(description = "Get or update order")
public Object manageOrder(String orderId, String status) { ... }
```

---

## API Reference

### Annotations

| Annotation | Target | Purpose | Key Attributes |
|-----------|--------|---------|-----------------|
| `@EnableMcpCompanion` | Class | Enable framework | N/A |
| `@McpTool` | Method | Expose as callable function | `name`, `description`, `tags` |
| `@McpResource` | Method | Expose as URI resource | `uri`, `name`, `description`, `mimeType` |
| `@McpPrompt` | Method | Expose as prompt template | `name`, `description` |
| `@McpInput` | Parameter | Mark method parameter | `name`, `description`, `required`, `sensitive` |

### Configuration Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `mcp.server.enabled` | boolean | `true` | Enable/disable MCP framework |
| `mcp.server.base-path` | string | `/mcp` | URL prefix for endpoints |
| `mcp.server.name` | string | `spring-boot-mcp-companion` | Server name advertised to clients |
| `mcp.server.version` | string | `1.0.0` | Server version |

### Exceptions

| Exception | When Thrown | HTTP Code |
|-----------|-------------|-----------|
| `ToolNotFoundException` | Tool name doesn't match any method | -32601 (JSON-RPC) |
| `InvalidParametersException` | Parameter validation fails | -32602 (JSON-RPC) |
| `ResourceNotFoundException` | Resource URI doesn't match | -32601 (JSON-RPC) |
| `PromptNotFoundException` | Prompt name doesn't exist | -32601 (JSON-RPC) |
| Application Exception | Tool business logic error | -32603 (JSON-RPC) |

---

## Security Considerations

### 1. **Sensitive Parameter Handling**

```java
@McpTool
public LoginResult authenticate(
    String email,
    @McpInput(sensitive = true) String password
) { ... }
```

Sensitive parameters are:
- Omitted from request logs
- Not included in metrics dimensions
- Masked in debug output
- Should never appear in responses

### 2. **Input Validation**

All `@McpInput` parameters with validation constraints are validated before method invocation:

```java
@McpTool
public User createUser(
    @McpInput @Email String email,
    @McpInput @Size(min = 8) String password
) { ... }
```

Invalid inputs are rejected with detailed error messages.

### 3. **Enable Spring Security (Optional)**

For authentication/authorization:

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-security</artifactId>
</dependency>
```

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize
            .requestMatchers("/mcp/**").authenticated()
            .anyRequest().permitAll()
        ).httpBasic(withDefaults());
        return http.build();
    }
}
```

### 4. **HTTPS in Production**

Always use HTTPS in production:

```yaml
server:
  ssl:
    key-store: classpath:keystore.p12
    key-store-password: ${KEYSTORE_PASSWORD}
    key-store-type: PKCS12
    enabled: true
```

### 5. **Rate Limiting (Optional)**

Use Spring Cloud Gateway or similar:

```xml
<dependency>
  <groupId>org.springframework.cloud</groupId>
  <artifactId>spring-cloud-starter-gateway</artifactId>
</dependency>
```

### 6. **Audit Logging**

The framework logs all invocations:

```yaml
logging:
  level:
    com.raynermendez.spring_boot_mcp_companion: DEBUG
```

Logs include:
- Tool/resource/prompt invocation
- Parameters (sensitive ones redacted)
- Execution duration
- Success/failure status
- Any errors

---

## Performance & Optimization

### 1. **Caching Results**

```java
@Service
@CacheConfig(cacheNames = "orders")
public class OrderService {

    @McpTool(description = "Get order")
    @Cacheable
    public Order getOrder(String orderId) {
        return orderRepository.findById(orderId);
    }
}
```

### 2. **Async Operations**

```java
@McpTool(description = "Process batch orders")
public CompletableFuture<List<Order>> processBatchOrders(
    @McpInput List<String> orderIds
) {
    return CompletableFuture.supplyAsync(() ->
        orderIds.parallelStream()
            .map(this::getOrder)
            .collect(Collectors.toList())
    );
}
```

### 3. **Connection Pooling**

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
```

### 4. **Lazy Loading**

```java
@McpResource(uri = "order://{id}")
public Order getOrder(String id) {
    return orderRepository.findById(id)
        .orElseThrow();
}
```

### 5. **Pagination for Large Results**

```java
@McpTool(description = "List orders")
public Page<Order> listOrders(
    @McpInput(description = "Page number (0-indexed)", required = false)
    Integer page,
    @McpInput(description = "Page size", required = false)
    Integer size
) {
    PageRequest pageRequest = PageRequest.of(
        page != null ? page : 0,
        size != null ? size : 20
    );
    return orderRepository.findAll(pageRequest);
}
```

---

## Troubleshooting

### Tool Not Found

**Error:**
```json
{
  "error": {
    "code": -32601,
    "message": "Tool not found"
  }
}
```

**Solutions:**
1. Check tool name matches method name (in camelCase or explicit `name` attribute)
2. Verify `@McpTool` annotation is present
3. Ensure method is in a Spring bean (`@Service`, `@Component`, etc.)
4. Check if `mcp.server.enabled` is `true`
5. Verify the bean is being scanned by Spring

### Invalid Parameters Error

**Error:**
```json
{
  "error": {
    "code": -32602,
    "message": "Invalid parameters",
    "data": {
      "violations": [...]
    }
  }
}
```

**Solutions:**
1. Check parameter names match `@McpInput` definitions
2. Verify required parameters are provided
3. Validate input format matches type (string, number, array, etc.)
4. Check validation constraints (`@Email`, `@Min`, etc.)
5. Review method signature for parameter types

### No Beans Registered

**Error:** Endpoints return empty lists, no tools/resources/prompts found

**Solutions:**
1. Verify `@EnableMcpCompanion` is on main class
2. Ensure beans are in Spring context:
   ```java
   @SpringBootApplication
   @EnableMcpCompanion
   public class MyApplication { ... }
   ```
3. Check package structure - Spring scans main package and subpackages
4. Verify `mcp.server.enabled` property is not set to `false`
5. Check Spring component scan configuration

### Transitive Dependencies

**Issue:** Getting unwanted dependencies (web, actuator)

**Solution:** Mark as optional in your pom.xml:
```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-web</artifactId>
  <optional>true</optional>
</dependency>
```

### Slow Performance

**Solutions:**
1. Enable caching:
   ```yaml
   spring:
     cache:
       type: caffeine
   ```

2. Configure connection pooling:
   ```yaml
   spring:
     datasource:
       hikari:
         maximum-pool-size: 20
   ```

3. Use pagination for large result sets

4. Monitor metrics:
   ```bash
   curl http://localhost:8080/actuator/metrics/mcp.tools.duration
   ```

### Logging Issues

**Enable debug logging:**
```yaml
logging:
  level:
    com.raynermendez.spring_boot_mcp_companion: DEBUG
```

**Sensitive parameters not masked:**
```java
// Mark parameter as sensitive
@McpInput(sensitive = true) String password
```

---

## Contributing

We welcome contributions! Please:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Write tests for new functionality
4. Ensure all tests pass (`mvn clean verify`)
5. Commit with clear messages (`git commit -m 'Add amazing feature'`)
6. Push to your fork
7. Open a Pull Request

**Code Style:**
- Follow [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)
- Use meaningful variable names
- Add Javadoc for public APIs
- Keep methods focused and small

**Testing:**
```bash
# Run all tests
mvn clean verify

# Run specific test class
mvn test -Dtest=EnableMcpCompanionTest

# Run with coverage
mvn clean verify jacoco:report
```

---

## License

This project is licensed under the **Apache License 2.0** - see the [LICENSE](LICENSE) file for details.

---

## Support

- 📖 **Documentation:** [Full API Documentation](https://github.com/yourusername/spring-boot-mcp-companion/docs)
- 🐛 **Issues:** [GitHub Issues](https://github.com/yourusername/spring-boot-mcp-companion/issues)
- 💬 **Discussions:** [GitHub Discussions](https://github.com/yourusername/spring-boot-mcp-companion/discussions)
- 📧 **Email:** support@example.com

---

## Acknowledgments

- [Model Context Protocol](https://modelcontextprotocol.io/) - Protocol specification
- [Spring Boot](https://spring.io/projects/spring-boot) - Framework foundation
- [Jakarta Bean Validation](https://jakarta.ee/specifications/bean-validation/) - Input validation

---

## Roadmap

- [ ] v1.0.0 - Initial release (stable API)
- [ ] v1.1.0 - Streaming responses support
- [ ] v1.2.0 - WebSocket transport (real-time updates)
- [ ] v2.0.0 - Multi-tenant support
- [ ] v2.1.0 - Built-in authentication/authorization UI
- [ ] v2.2.0 - Performance optimizations (caching, compression)

---

## Version History

### 1.0.0 (Current)
- ✅ Core MCP protocol implementation
- ✅ Annotations: @EnableMcpCompanion, @McpTool, @McpResource, @McpPrompt, @McpInput
- ✅ Automatic schema generation
- ✅ Input validation (Jakarta Bean Validation)
- ✅ Observability (Micrometer, health checks)
- ✅ Spring Security integration
- ✅ Configuration via application.yml
- ✅ 66 comprehensive tests

---

**Happy MCP'ing! 🚀**

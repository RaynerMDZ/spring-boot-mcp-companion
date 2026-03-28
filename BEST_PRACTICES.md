# Best Practices - Spring Boot MCP Companion

## 1. Design Principles

### Single Responsibility
Each `@McpTool`, `@McpResource`, and `@McpPrompt` should do one thing well.

❌ **Bad:**
```java
@McpTool(description = "Do everything")
public String doEverything(String input) {
    // Handles 10 different cases, validates multiple ways
}
```

✅ **Good:**
```java
@McpTool(description = "Create a new user")
public User createUser(@McpInput String email) { ... }

@McpTool(description = "Delete a user")
public void deleteUser(@McpInput String userId) { ... }

@McpTool(description = "Update user profile")
public User updateUserProfile(@McpInput String userId, @McpInput String name) { ... }
```

### Clear Naming

Use descriptive, action-oriented names:

❌ **Bad:**
```java
@McpTool
public Result process(@McpInput String x) { ... }

@McpResource(uri = "res://{id}")
public Thing get(@McpInput String id) { ... }
```

✅ **Good:**
```java
@McpTool(description = "Process payment for an order")
public PaymentResult processOrderPayment(@McpInput String orderId) { ... }

@McpResource(uri = "invoice://{invoiceId}", description = "Invoice document")
public Invoice getInvoice(@McpInput String invoiceId) { ... }
```

### Meaningful Descriptions

Descriptions are client-facing documentation. Be specific and helpful.

❌ **Bad:**
```java
@McpTool(description = "Get data")
public Data getData(@McpInput String id) { ... }
```

✅ **Good:**
```java
@McpTool(description = "Retrieve customer order history with detailed line items, including prices, quantities, and current status for each order")
public List<Order> getCustomerOrders(@McpInput String customerId) { ... }
```

---

## 2. Input Validation

### Use Jakarta Bean Validation Annotations

Leverage existing Spring validation framework:

✅ **Good:**
```java
@McpTool
public User createUser(
    @McpInput @NotBlank String name,
    @McpInput @Email String email,
    @McpInput @Size(min = 8, max = 128) String password,
    @McpInput @Positive BigDecimal balance
) { ... }
```

### Provide Clear Error Messages

Custom messages help users fix issues:

✅ **Good:**
```java
@McpInput @NotBlank(message = "User name is required") String name,
@McpInput @Email(message = "Must be a valid email address") String email,
@McpInput @Size(min = 8, message = "Password must be at least 8 characters") String password
```

### Validate Business Logic

Don't rely only on bean validation:

✅ **Good:**
```java
@McpTool
public Order createOrder(
    @McpInput @Min(1) Integer quantity,
    @McpInput @Positive BigDecimal price
) {
    // Beyond bean validation: check business rules
    if (!inventory.hasStock(quantity)) {
        throw new InsufficientStockException("Only " + inventory.getAvailable() + " available");
    }
    return orderService.createOrder(quantity, price);
}
```

---

## 3. Error Handling

### Be Specific with Exceptions

Use specific exceptions so clients understand what happened:

❌ **Bad:**
```java
try {
    return orderRepository.findById(orderId).orElseThrow();
} catch (Exception e) {
    throw new RuntimeException("Error");
}
```

✅ **Good:**
```java
return orderRepository.findById(orderId)
    .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));
```

### Include Context in Error Messages

Help clients and support teams understand what went wrong:

✅ **Good:**
```java
if (inventory.getQuantity() < requestedQuantity) {
    throw new InsufficientStockException(
        "Requested " + requestedQuantity + " units but only " +
        inventory.getQuantity() + " in stock for product " + productId
    );
}
```

---

## 4. Return Types

### Return Data, Not Messages

Return structured data that clients can work with:

❌ **Bad:**
```java
@McpTool
public String getOrder(@McpInput String orderId) {
    return "Order ID: " + orderId + ", Status: PENDING";
}
```

✅ **Good:**
```java
@McpTool
public Order getOrder(@McpInput String orderId) {
    return orderRepository.findById(orderId).orElseThrow();
}

// Returns JSON object with proper structure
```

### Use Records for Consistent Response Types

```java
public record OrderResponse(
    String id,
    String status,
    BigDecimal total,
    List<LineItem> items
) {}

@McpTool
public OrderResponse getOrder(@McpInput String orderId) {
    Order order = orderRepository.findById(orderId).orElseThrow();
    return new OrderResponse(
        order.getId(),
        order.getStatus(),
        order.getTotal(),
        order.getItems()
    );
}
```

---

## 5. Sensitive Data Handling

### Mark Sensitive Parameters

Prevent passwords and secrets from appearing in logs:

✅ **Good:**
```java
@McpTool
public AuthToken authenticate(
    @McpInput String username,
    @McpInput(sensitive = true) String password
) {
    // password won't appear in logs or metrics
}
```

### Never Log Secrets

Even with `sensitive=true`, be extra careful:

❌ **Bad:**
```java
logger.info("User login attempt: username={}, password={}", username, password);
```

✅ **Good:**
```java
logger.info("User login attempt: username={}", username);
// Let the framework handle sensitive parameter masking
```

### Use Environment Variables for Secrets

Never hardcode secrets:

❌ **Bad:**
```java
String apiKey = "sk-1234567890abcdef";
```

✅ **Good:**
```yaml
app:
  api-key: ${API_KEY:}  # From environment variable
```

```java
@Value("${app.api-key}")
private String apiKey;
```

---

## 6. Resource Design

### Use Meaningful URI Templates

URI patterns should reflect resource hierarchy:

✅ **Good:**
```java
@McpResource(uri = "user://{userId}")
public User getUser(@McpInput String userId) { ... }

@McpResource(uri = "user://{userId}/orders")
public List<Order> getUserOrders(@McpInput String userId) { ... }

@McpResource(uri = "user://{userId}/orders/{orderId}")
public Order getUserOrder(@McpInput String userId, @McpInput String orderId) { ... }
```

### Use Appropriate MIME Types

Set correct MIME types for resource content:

✅ **Good:**
```java
@McpResource(uri = "user://{id}/avatar", mimeType = "image/png")
public byte[] getUserAvatar(@McpInput String id) { ... }

@McpResource(uri = "report://{id}/pdf", mimeType = "application/pdf")
public byte[] getReportPdf(@McpInput String id) { ... }

@McpResource(uri = "doc://{id}/html", mimeType = "text/html")
public String getDocumentHtml(@McpInput String id) { ... }
```

---

## 7. Performance

### Avoid N+1 Queries

Use joins and eager loading:

❌ **Bad:**
```java
@McpTool
public List<OrderWithItems> getOrders() {
    return orderRepository.findAll()  // Query 1
        .stream()
        .map(order -> new OrderWithItems(
            order,
            itemRepository.findByOrderId(order.getId())  // N additional queries!
        ))
        .collect(toList());
}
```

✅ **Good:**
```java
@McpTool
public List<OrderWithItems> getOrders() {
    return orderRepository.findAllWithItems();  // Single query with join
}
```

### Cache When Appropriate

Use Spring's `@Cacheable` for expensive operations:

✅ **Good:**
```java
@McpTool
@Cacheable(value = "userProfiles", key = "#userId")
public UserProfile getUserProfile(@McpInput String userId) {
    // Expensive operation - cached after first call
    return expensiveProfileComputation(userId);
}
```

### Paginate Large Results

Don't return entire datasets:

❌ **Bad:**
```java
@McpTool
public List<Order> getAllOrders() {
    return orderRepository.findAll();  // Could be millions
}
```

✅ **Good:**
```java
@McpTool
public Page<Order> getOrders(
    @McpInput(required = false) Integer page,
    @McpInput(required = false) Integer size
) {
    int pageNum = page != null ? page : 0;
    int pageSize = size != null ? size : 20;
    return orderRepository.findAll(
        PageRequest.of(pageNum, pageSize, Sort.by("createdAt").descending())
    );
}
```

---

## 8. Testing

### Test Your Tools

Write unit tests for your tools:

✅ **Good:**
```java
@SpringBootTest
class OrderToolsTest {

    @Autowired
    private OrderService orderService;

    @Test
    void testCreateOrderSuccess() {
        Order order = orderService.createOrder("PROD-123", 5);
        assertThat(order.getId()).isNotNull();
        assertThat(order.getQuantity()).isEqualTo(5);
    }

    @Test
    void testCreateOrderValidationError() {
        assertThatThrownBy(() -> orderService.createOrder("", 5))
            .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void testGetOrderNotFound() {
        assertThatThrownBy(() -> orderService.getOrder("NONEXISTENT"))
            .isInstanceOf(OrderNotFoundException.class);
    }
}
```

### Test Edge Cases

Consider boundary conditions:

✅ **Good:**
```java
@Test
void testZeroQuantity() {
    assertThatThrownBy(() -> orderService.createOrder("PROD-123", 0))
        .isInstanceOf(ConstraintViolationException.class);
}

@Test
void testNegativePrice() {
    assertThatThrownBy(() -> orderService.createOrder("PROD-123", -100))
        .isInstanceOf(ConstraintViolationException.class);
}

@Test
void testEmptyList() {
    List<Order> orders = orderService.getOrders();
    assertThat(orders).isEmpty();
}
```

---

## 9. Documentation

### Use Javadoc

Document your tools with Javadoc:

✅ **Good:**
```java
/**
 * Creates a new order for the specified product.
 *
 * <p>Validates inventory availability and charges payment immediately.
 * Throws {@link InsufficientStockException} if quantity exceeds available stock.
 *
 * @param productId the product ID to order
 * @param quantity the quantity to order (must be >= 1)
 * @return the created order with order ID and status
 * @throws OrderException if order creation fails
 * @throws InsufficientStockException if quantity exceeds available stock
 */
@McpTool(description = "Create a new order")
public Order createOrder(
    @McpInput(description = "Product ID") String productId,
    @McpInput(description = "Quantity to order") Integer quantity
) { ... }
```

### Provide Examples

Help users understand usage:

✅ **Good:**
```java
/**
 * Searches for orders by customer.
 *
 * Example:
 * <pre>
 * POST /mcp/tools/call
 * {
 *   "jsonrpc": "2.0",
 *   "method": "tools/call",
 *   "params": {
 *     "name": "search_orders_by_customer",
 *     "arguments": {"customerId": "CUST-123"}
 *   }
 * }
 * </pre>
 */
@McpTool(description = "Find all orders for a customer")
public List<Order> searchOrdersByCustomer(
    @McpInput(description = "Customer ID") String customerId
) { ... }
```

---

## 10. Production Deployment

### Enable Security

Always use Spring Security in production:

```yaml
# application-prod.yml
mcp:
  server:
    enabled: true

management:
  endpoints:
    web:
      exposure:
        include: health,metrics  # Limit exposed endpoints

spring:
  security:
    require-https: true
```

### Monitor Performance

Enable metrics collection:

```yaml
management:
  metrics:
    tags:
      application: ${spring.application.name}
    export:
      prometheus:
        enabled: true
```

### Use HTTPS

Always in production:

```yaml
server:
  ssl:
    enabled: true
    key-store: ${KEYSTORE_PATH}
    key-store-password: ${KEYSTORE_PASSWORD}
```

### Implement Rate Limiting

Protect against abuse:

```java
@Configuration
public class RateLimitConfig {
    @Bean
    public RateLimiter mcpRateLimiter() {
        return RateLimiter.create(100);  // 100 requests per second
    }
}
```

---

## Summary

✅ **Do:**
- Single responsibility per tool/resource/prompt
- Clear, descriptive names and descriptions
- Comprehensive input validation
- Specific error messages
- Return structured data
- Mark sensitive parameters
- Use caching for expensive operations
- Write tests for edge cases
- Document with Javadoc and examples
- Enable security in production

❌ **Don't:**
- Overload methods with multiple responsibilities
- Assume input is valid
- Log or expose sensitive data
- Return raw strings or messages
- Hardcode secrets
- Perform N+1 queries
- Return unlimited result sets
- Skip testing
- Run without security in production

See [ADVANCED.md](ADVANCED.md) for more patterns and [SECURITY.md](SECURITY.md) for security guidelines.

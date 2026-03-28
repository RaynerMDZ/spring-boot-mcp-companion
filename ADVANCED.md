# Advanced Usage - Spring Boot MCP Companion

## Spring Security Integration

### Authenticate MCP Endpoints

Require authentication for all MCP endpoints:

```java
@Configuration
@EnableWebSecurity
public class McpSecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/mcp/**").authenticated()
                .requestMatchers("/mcp/server-info").permitAll()
                .anyRequest().permitAll()
            )
            .httpBasic(withDefaults());
        return http.build();
    }
}
```

### Access Current Principal in Tools

Get the authenticated user in your tools:

```java
@Service
public class SecureOrderService {
    private final Authentication authentication;

    @McpTool(description = "Get my orders")
    public List<Order> getMyOrders() {
        String userId = authentication.getPrincipal().toString();
        return orderRepository.findByUserId(userId);
    }

    @McpTool(description = "Cancel my order")
    public void cancelOrder(@McpInput String orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow();
        String userId = authentication.getPrincipal().toString();

        if (!order.getUserId().equals(userId)) {
            throw new AccessDeniedException("Cannot cancel order you don't own");
        }

        order.setStatus("CANCELLED");
        orderRepository.save(order);
    }
}
```

### Use @PreAuthorize for Fine-Grained Access

```java
@Service
public class AdminOrderService {

    @McpTool
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteOrder(@McpInput String orderId) {
        orderRepository.deleteById(orderId);
    }

    @McpTool
    @PreAuthorize("@orderService.isOrderOwner(#orderId, principal)")
    public Order updateOrder(@McpInput String orderId, @McpInput String status) {
        Order order = orderRepository.findById(orderId).orElseThrow();
        order.setStatus(status);
        return orderRepository.save(order);
    }
}
```

---

## Custom Input Validation

### Create Custom Validators

```java
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PhoneNumberValidator.class)
public @interface ValidPhoneNumber {
    String message() default "Invalid phone number format";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

public class PhoneNumberValidator implements ConstraintValidator<ValidPhoneNumber, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || value.matches("^[+]?[0-9]{10,}$");
    }
}
```

Use in tools:

```java
@McpTool
public Order createOrderWithPhone(
    @McpInput @ValidPhoneNumber String phoneNumber,
    @McpInput String productId
) {
    // Phone number is validated automatically
    return orderService.createOrder(productId, phoneNumber);
}
```

### Conditional Validation

```java
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ConditionalRequiredValidator.class)
public @interface ConditionalRequired {
    String message() default "This field is required in this context";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
    String condition() default "";
}

public class ConditionalRequiredValidator implements ConstraintValidator<ConditionalRequired, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // Custom logic based on context
        return value != null && !value.isBlank();
    }
}
```

---

## Observability and Metrics

### Access Built-in Metrics

The framework automatically collects metrics:

```yaml
management:
  metrics:
    export:
      prometheus:
        enabled: true
  endpoints:
    web:
      exposure:
        include: metrics,prometheus

spring:
  application:
    name: order-service
```

View metrics at `http://localhost:8080/actuator/metrics`

### Add Custom Metrics

```java
@Service
public class OrderService {
    private final MeterRegistry meterRegistry;

    public OrderService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @McpTool
    public Order createOrder(@McpInput String productId, @McpInput Integer quantity) {
        // Track custom metric
        meterRegistry.counter("orders.created", "product", productId).increment();

        Order order = new Order(productId, quantity);
        return orderRepository.save(order);
    }

    @McpTool
    public Order getOrder(@McpInput String orderId) {
        // Measure operation duration
        return meterRegistry.timer("order.lookup.time").record(() ->
            orderRepository.findById(orderId).orElseThrow()
        );
    }
}
```

### Custom Health Indicators

```java
@Component
public class OrderServiceHealth implements HealthIndicator {
    private final OrderRepository orderRepository;

    @Override
    public Health health() {
        try {
            long count = orderRepository.count();
            return Health.up()
                .withDetail("orders_total", count)
                .withDetail("status", count > 0 ? "healthy" : "empty")
                .build();
        } catch (Exception e) {
            return Health.down()
                .withException(e)
                .build();
        }
    }
}
```

View at `http://localhost:8080/actuator/health`

---

## Transaction Management

### Transactional Tools

Ensure multiple operations happen atomically:

```java
@Service
public class TransactionalOrderService {

    @McpTool(description = "Create order and reserve inventory")
    @Transactional
    public Order createOrderWithReservation(
        @McpInput String productId,
        @McpInput Integer quantity
    ) {
        // Both operations in same transaction
        Order order = orderRepository.save(new Order(productId, quantity));
        inventoryService.reserve(productId, quantity);

        // If reserve throws, order creation is rolled back
        return order;
    }

    @McpTool
    @Transactional(readOnly = true)
    public List<Order> getOrders() {
        // Read-only optimization
        return orderRepository.findAll();
    }

    @McpTool
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAuditTrail(String event) {
        // Always creates new transaction, independent of parent
        auditRepository.save(new AuditLog(event));
    }
}
```

### Handling Transaction Conflicts

```java
@Service
public class PaymentService {

    @McpTool
    @Transactional
    @Retryable(value = DataAccessException.class, maxAttempts = 3, backoff = @Backoff(delay = 100))
    public Payment processPayment(@McpInput String orderId, @McpInput BigDecimal amount) {
        Order order = orderRepository.findById(orderId).orElseThrow();
        // Automatically retries if there's a transaction conflict
        return paymentRepository.save(new Payment(order, amount));
    }

    @Recover
    public Payment recover(DataAccessException e, String orderId, BigDecimal amount) {
        logger.error("Payment processing failed after retries", e);
        throw new PaymentProcessingException("Could not process payment");
    }
}
```

---

## Async Processing

### Asynchronous Tool Execution

```java
@Service
@EnableAsync
public class DocumentProcessingService {

    @McpTool(description = "Submit document for processing")
    public ProcessingJob submitDocument(@McpInput String documentPath) {
        ProcessingJob job = new ProcessingJob(
            UUID.randomUUID().toString(),
            documentPath,
            "PENDING"
        );
        jobRepository.save(job);

        // Process asynchronously
        processDocumentAsync(documentPath, job.getId());

        return job;
    }

    @Async
    public void processDocumentAsync(String path, String jobId) {
        try {
            DocumentAnalysis result = heavyProcessing(path);
            jobRepository.updateResult(jobId, result);
        } catch (Exception e) {
            jobRepository.markFailed(jobId, e.getMessage());
        }
    }

    @McpResource(uri = "job://{jobId}", description = "Get job status")
    public ProcessingJob getJobStatus(@McpInput String jobId) {
        return jobRepository.findById(jobId).orElseThrow();
    }
}
```

### Async with CompletableFuture

```java
@Service
public class AsyncOrderService {

    @McpTool
    public Order createOrderAsync(@McpInput String productId) {
        Order order = new Order(productId);
        order = orderRepository.save(order);

        // Non-blocking operations
        CompletableFuture.runAsync(() -> sendConfirmationEmail(order))
            .exceptionally(ex -> {
                logger.error("Failed to send email", ex);
                return null;
            });

        CompletableFuture.runAsync(() -> updateAnalytics(order))
            .exceptionally(ex -> {
                logger.error("Failed to update analytics", ex);
                return null;
            });

        return order;
    }

    private void sendConfirmationEmail(Order order) {
        emailService.sendOrderConfirmation(order);
    }

    private void updateAnalytics(Order order) {
        analyticsService.recordOrderCreated(order);
    }
}
```

---

## Caching

### Cache Tool Results

```java
@Service
@EnableCaching
public class UserService {

    @McpTool
    @Cacheable(value = "userProfiles", key = "#userId", unless = "#result == null")
    public UserProfile getUserProfile(@McpInput String userId) {
        // Expensive operation - cached after first call
        return expensiveProfileComputation(userId);
    }

    @McpTool
    @CacheEvict(value = "userProfiles", key = "#userId")
    public User updateUser(@McpInput String userId, @McpInput String name) {
        User user = userRepository.findById(userId).orElseThrow();
        user.setName(name);
        return userRepository.save(user);
    }

    @McpTool
    @CachePut(value = "userProfiles", key = "#userId")
    public UserProfile refreshUserProfile(@McpInput String userId) {
        // Refreshes cache with latest data
        return expensiveProfileComputation(userId);
    }
}
```

### Custom Cache Configuration

```java
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager(
            "userProfiles",
            "orderCache",
            "productCache"
        );
    }
}
```

---

## Database Access Patterns

### Pagination and Sorting

```java
@Service
public class OrderQueryService {

    @McpTool
    public Page<Order> getOrdersPagedAndSorted(
        @McpInput(required = false) Integer page,
        @McpInput(required = false) Integer size,
        @McpInput(required = false) String sortBy
    ) {
        int pageNum = page != null ? page : 0;
        int pageSize = Math.min(size != null ? size : 20, 100);  // Max 100
        String sortField = sortBy != null ? sortBy : "createdAt";

        return orderRepository.findAll(
            PageRequest.of(pageNum, pageSize, Sort.by(sortField).descending())
        );
    }

    @McpTool
    public List<Order> getOrdersByDateRange(
        @McpInput LocalDate startDate,
        @McpInput LocalDate endDate
    ) {
        return orderRepository.findByCreatedAtBetween(startDate, endDate);
    }
}
```

### Complex Queries with Specifications

```java
@Service
public class AdvancedOrderSearch {
    private final OrderRepository orderRepository;

    @McpTool
    public List<Order> advancedSearch(
        @McpInput(required = false) String status,
        @McpInput(required = false) BigDecimal minPrice,
        @McpInput(required = false) BigDecimal maxPrice,
        @McpInput(required = false) String customerId
    ) {
        Specification<Order> spec = Specification.where(null);

        if (status != null) {
            spec = spec.and((root, query, builder) ->
                builder.equal(root.get("status"), status)
            );
        }
        if (minPrice != null) {
            spec = spec.and((root, query, builder) ->
                builder.greaterThanOrEqualTo(root.get("price"), minPrice)
            );
        }
        if (maxPrice != null) {
            spec = spec.and((root, query, builder) ->
                builder.lessThanOrEqualTo(root.get("price"), maxPrice)
            );
        }
        if (customerId != null) {
            spec = spec.and((root, query, builder) ->
                builder.equal(root.get("customerId"), customerId)
            );
        }

        return orderRepository.findAll(spec);
    }
}
```

---

## Event-Driven Architecture

### Publish Events

```java
@Service
public class EventPublishingOrderService {
    private final ApplicationEventPublisher eventPublisher;
    private final OrderRepository orderRepository;

    @McpTool
    public Order createOrder(@McpInput String productId, @McpInput Integer quantity) {
        Order order = new Order(productId, quantity);
        order = orderRepository.save(order);

        // Publish event for other components to react to
        eventPublisher.publishEvent(new OrderCreatedEvent(order));

        return order;
    }

    @McpTool
    public void cancelOrder(@McpInput String orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow();
        order.setStatus("CANCELLED");
        orderRepository.save(order);

        eventPublisher.publishEvent(new OrderCancelledEvent(order));
    }
}

// Event listener
@Service
public class OrderEventListener {

    @EventListener
    public void onOrderCreated(OrderCreatedEvent event) {
        // Send confirmation email, update analytics, etc.
        sendConfirmationEmail(event.getOrder());
    }

    @EventListener
    public void onOrderCancelled(OrderCancelledEvent event) {
        // Release inventory, refund payment, etc.
        releaseInventory(event.getOrder());
    }
}
```

---

## Error Handling

### Global Exception Handler

```java
@RestControllerAdvice
public class McpExceptionHandler {

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleOrderNotFound(OrderNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse(-32001, "Order not found: " + e.getMessage()));
    }

    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientStock(InsufficientStockException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ErrorResponse(-32002, "Insufficient stock: " + e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception e) {
        logger.error("Unexpected error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ErrorResponse(-32603, "Internal server error"));
    }
}

record ErrorResponse(int code, String message) {}
```

---

## Testing Advanced Features

### Integration Testing with Transactions

```java
@SpringBootTest
@Transactional
class OrderServiceIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private InventoryService inventoryService;

    @Test
    void testOrderCreationRolledBackOnInventoryError() {
        // Should be rolled back if inventory fails
        assertThatThrownBy(() ->
            orderService.createOrderWithReservation("PROD-999", 1000)
        ).isInstanceOf(InsufficientStockException.class);

        // Verify order wasn't created
        assertThat(orderRepository.count()).isEqualTo(0);
    }
}
```

---

See [BEST_PRACTICES.md](BEST_PRACTICES.md) for production deployment guidelines and [EXAMPLES.md](EXAMPLES.md) for working examples.

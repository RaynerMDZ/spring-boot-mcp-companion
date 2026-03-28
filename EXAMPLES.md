# Examples - Spring Boot MCP Companion

## Example 1: Order Management Service

A complete example showing all three MCP capability types.

### Model

```java
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record Order(
    String id,
    String productName,
    BigDecimal price,
    String status,
    LocalDateTime createdAt
) {}
```

### Service

```java
import org.springframework.stereotype.Service;
import com.raynermendez.spring_boot_mcp_companion.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class OrderService {
    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    /**
     * Creates a new order in the system.
     * Tool: Callable function to create orders
     */
    @McpTool(description = "Create a new order with the given details")
    public Order createOrder(
        @McpInput(description = "Product name") String productName,
        @McpInput(description = "Price in USD") BigDecimal price,
        @McpInput(description = "Initial status", required = false) String status
    ) {
        Order order = new Order(
            UUID.randomUUID().toString(),
            productName,
            price,
            status != null ? status : "PENDING",
            LocalDateTime.now()
        );
        return orderRepository.save(order);
    }

    /**
     * Resource: Access order by URI pattern
     */
    @McpResource(
        uri = "order://{orderId}",
        description = "Retrieve order details by ID",
        mimeType = "application/json"
    )
    public Order getOrderResource(@McpInput(description = "Order ID") String orderId) {
        return orderRepository.findById(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
    }

    /**
     * Tool: Query orders by status
     */
    @McpTool(description = "Find all orders with a specific status")
    public List<Order> findOrdersByStatus(
        @McpInput(description = "Order status (PENDING, PROCESSING, COMPLETED)") String status
    ) {
        return orderRepository.findByStatus(status);
    }

    /**
     * Prompt: Generate order summary
     */
    @McpPrompt(
        name = "order_summary",
        description = "Generate a formatted summary of an order"
    )
    public String generateOrderSummary(
        @McpInput(description = "Order ID") String orderId
    ) {
        Order order = getOrderResource(orderId);
        return String.format(
            "Order Summary\n" +
            "=============\n" +
            "ID: %s\n" +
            "Product: %s\n" +
            "Price: $%.2f\n" +
            "Status: %s\n" +
            "Created: %s",
            order.id(),
            order.productName(),
            order.price(),
            order.status(),
            order.createdAt()
        );
    }

    /**
     * Tool: Update order status
     */
    @McpTool(description = "Update the status of an existing order")
    public Order updateOrderStatus(
        @McpInput(description = "Order ID") String orderId,
        @McpInput(description = "New status") String newStatus
    ) {
        Order order = getOrderResource(orderId);
        Order updated = new Order(
            order.id(),
            order.productName(),
            order.price(),
            newStatus,
            order.createdAt()
        );
        return orderRepository.save(updated);
    }
}
```

### Test Calls

```bash
# Create an order
curl -X POST http://localhost:8080/mcp/tools/call \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "tools/call",
    "params": {
      "name": "create_order",
      "arguments": {
        "productName": "Laptop",
        "price": 999.99
      }
    }
  }'

# Get order by resource URI
curl -X POST http://localhost:8080/mcp/resources/read \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 2,
    "method": "resources/read",
    "params": {
      "uri": "order://123-abc"
    }
  }'

# Generate order summary (prompt)
curl -X POST http://localhost:8080/mcp/prompts/get \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 3,
    "method": "prompts/get",
    "params": {
      "name": "order_summary",
      "arguments": {"orderId": "123-abc"}
    }
  }'
```

---

## Example 2: Document Processing Service

Shows working with files, validation, and error handling.

### Service

```java
import org.springframework.stereotype.Service;
import com.raynermendez.spring_boot_mcp_companion.annotation.*;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

@Service
public class DocumentService {
    private final DocumentRepository documentRepository;
    private final DocumentProcessor processor;

    @McpTool(description = "Upload and analyze a document")
    public DocumentAnalysis analyzeDocument(
        @McpInput @NotBlank(message = "Filename is required") String filename,
        @McpInput @NotBlank String content,
        @McpInput @Pattern(regexp = "^(pdf|txt|docx)$") String fileType
    ) {
        // Validate file size (example constraint)
        if (content.length() > 10_000_000) {
            throw new IllegalArgumentException("File too large (max 10MB)");
        }

        Document doc = new Document(
            UUID.randomUUID().toString(),
            filename,
            fileType,
            content,
            LocalDateTime.now()
        );
        documentRepository.save(doc);

        // Process the document
        DocumentAnalysis analysis = processor.analyze(doc);
        return analysis;
    }

    @McpTool(description = "Extract text from a document")
    public String extractText(@McpInput String documentId) {
        Document doc = documentRepository.findById(documentId)
            .orElseThrow(() -> new IllegalArgumentException("Document not found"));
        return processor.extractText(doc);
    }

    @McpPrompt(
        name = "document_summary",
        description = "Generate a summary of document analysis"
    )
    public String summarizeAnalysis(@McpInput String documentId) {
        Document doc = documentRepository.findById(documentId)
            .orElseThrow();
        DocumentAnalysis analysis = processor.analyze(doc);

        return String.format(
            "Document: %s\n" +
            "Type: %s\n" +
            "Pages: %d\n" +
            "Words: %d\n" +
            "Key Topics: %s",
            doc.filename(),
            doc.fileType(),
            analysis.pageCount(),
            analysis.wordCount(),
            String.join(", ", analysis.topics())
        );
    }

    @McpResource(
        uri = "document://{documentId}/raw",
        description = "Access raw document content",
        mimeType = "text/plain"
    )
    public String getRawDocument(@McpInput String documentId) {
        Document doc = documentRepository.findById(documentId)
            .orElseThrow();
        return doc.content();
    }
}
```

---

## Example 3: Configuration Reader Service

Simple service exposing application configuration as resources.

### Service

```java
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Service;
import com.raynermendez.spring_boot_mcp_companion.annotation.*;

@Service
public class ConfigurationReaderService {
    private final ApplicationProperties appProps;
    private final Environment environment;

    public ConfigurationReaderService(
        ApplicationProperties appProps,
        Environment environment
    ) {
        this.appProps = appProps;
        this.environment = environment;
    }

    @McpTool(description = "Get current application environment (dev/staging/prod)")
    public String getCurrentEnvironment() {
        String[] profiles = environment.getActiveProfiles();
        return profiles.length > 0 ? profiles[0] : "default";
    }

    @McpTool(description = "Get the application version")
    public String getApplicationVersion() {
        return appProps.getVersion();
    }

    @McpTool(description = "Check if a feature is enabled")
    public boolean isFeatureEnabled(@McpInput String featureName) {
        return appProps.getFeatures().isEnabled(featureName);
    }

    @McpResource(
        uri = "config://app-info",
        description = "Get application metadata"
    )
    public ApplicationInfo getAppInfo() {
        return new ApplicationInfo(
            appProps.getName(),
            appProps.getVersion(),
            getCurrentEnvironment(),
            System.getProperty("java.version")
        );
    }

    @McpPrompt(
        name = "deployment_status",
        description = "Get current deployment status and configuration"
    )
    public String getDeploymentStatus() {
        return String.format(
            "Deployment Status\n" +
            "=================\n" +
            "Environment: %s\n" +
            "Version: %s\n" +
            "Java Version: %s\n" +
            "Active Profiles: %s",
            getCurrentEnvironment(),
            getApplicationVersion(),
            System.getProperty("java.version"),
            String.join(", ", environment.getActiveProfiles())
        );
    }
}
```

---

## Example 4: Input Validation Examples

Shows best practices for validation.

### Service with Comprehensive Validation

```java
@Service
public class UserService {

    @McpTool(description = "Create a new user account")
    public User createUser(
        @McpInput @NotBlank(message = "Name is required") String name,
        @McpInput @Email(message = "Invalid email format") String email,
        @McpInput @Size(min = 8, max = 128, message = "Password must be 8-128 characters")
        String password,
        @McpInput @Min(18) @Max(120) Integer age
    ) {
        // Validation happens automatically before this code runs
        User user = new User(name, email, password, age);
        return userRepository.save(user);
    }

    @McpTool(description = "Update user profile")
    public User updateProfile(
        @McpInput @NotBlank String userId,
        @McpInput(required = false) String name,
        @McpInput(required = false) @Email String email,
        @McpInput(required = false) String bio
    ) {
        User user = userRepository.findById(userId)
            .orElseThrow();

        if (name != null) user.setName(name);
        if (email != null) user.setEmail(email);
        if (bio != null) user.setBio(bio);

        return userRepository.save(user);
    }

    @McpTool(description = "Search users by email pattern")
    public List<User> searchByEmail(
        @McpInput @Email(message = "Provide a valid email pattern") String emailPattern
    ) {
        return userRepository.findByEmailLike(emailPattern);
    }
}
```

---

## Integration Patterns

### With Spring Security

```java
@Service
public class SecureOrderService {
    private final SecurityContext securityContext;
    private final OrderRepository orderRepository;

    @McpTool(description = "Get user's own orders")
    public List<Order> getMyOrders() {
        String userId = securityContext.getCurrentUserId();
        return orderRepository.findByUserId(userId);
    }

    @McpTool(description = "Cancel order (owner only)")
    public void cancelOrder(@McpInput String orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow();
        String userId = securityContext.getCurrentUserId();

        if (!order.getUserId().equals(userId)) {
            throw new AccessDeniedException("Cannot cancel order you don't own");
        }

        order.setStatus("CANCELLED");
        orderRepository.save(order);
    }
}
```

### With Database Transactions

```java
@Service
public class TransactionalOrderService {

    @McpTool(description = "Create order and reserve inventory")
    @Transactional
    public Order createOrderWithReservation(
        @McpInput String productId,
        @McpInput Integer quantity
    ) {
        // Both operations happen in same transaction
        Order order = orderRepository.save(
            new Order(productId, quantity)
        );

        inventoryService.reserve(productId, quantity);
        // If reserve throws, order creation is rolled back

        return order;
    }
}
```

### With Async Processing

```java
@Service
public class AsyncProcessingService {

    @McpTool(description = "Submit document for async processing")
    public ProcessingJob submitForProcessing(@McpInput String documentPath) {
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
    void processDocumentAsync(String path, String jobId) {
        // Long-running operation
        Result result = heavyProcessing(path);
        jobRepository.updateStatus(jobId, "COMPLETE", result);
    }

    @McpResource(
        uri = "job://{jobId}",
        description = "Get async job status"
    )
    public ProcessingJob getJobStatus(@McpInput String jobId) {
        return jobRepository.findById(jobId).orElseThrow();
    }
}
```

---

See [ADVANCED.md](ADVANCED.md) for more patterns and [API_REFERENCE.md](API_REFERENCE.md) for complete annotation documentation.

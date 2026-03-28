# Troubleshooting - Spring Boot MCP Companion

## Common Issues and Solutions

### 1. Tools/Resources/Prompts Not Showing Up

**Symptom:** `/mcp/tools/list` returns empty list even though you have `@McpTool` methods.

**Possible Causes:**

1. **Missing `@EnableMcpCompanion` annotation**
   ```java
   @SpringBootApplication
   @EnableMcpCompanion  // ← Must be present!
   public class MyApplication { ... }
   ```

2. **Method not public or class not a Spring component**
   ```java
   @Service  // ← Must have @Service, @Component, etc.
   public class MyService {
       @McpTool  // ← Method must be public
       public String myTool(@McpInput String param) { ... }
   }
   ```

3. **MCP disabled in configuration**
   ```yaml
   mcp:
     server:
       enabled: false  # Change to true
   ```

4. **Class not being scanned**
   - Ensure your service is in a package under your main application class
   - Or explicitly configure component scan:
   ```java
   @SpringBootApplication
   @EnableMcpCompanion
   @ComponentScan(basePackages = {"com.myapp", "com.other.package"})
   public class MyApplication { ... }
   ```

**Solution:**
```bash
# Check logs for bean scanning
curl http://localhost:8080/mcp/server-info

# Should show your tools
```

---

### 2. Validation Not Working

**Symptom:** Invalid inputs are not being rejected; validation annotations are ignored.

**Possible Causes:**

1. **Missing `spring-boot-starter-validation` dependency**
   ```xml
   <dependency>
       <groupId>org.springframework.boot</groupId>
       <artifactId>spring-boot-starter-validation</artifactId>
   </dependency>
   ```

2. **Using javax.validation instead of jakarta.validation**
   ```java
   // ❌ Wrong
   import javax.validation.constraints.Email;

   // ✅ Correct
   import jakarta.validation.constraints.Email;
   ```

3. **Validation disabled globally**
   ```yaml
   spring:
     mvc:
       throw-exception-if-no-handler-found: true
   ```

**Solution:**
```bash
# Test validation with invalid input
curl -X POST http://localhost:8080/mcp/tools/call \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "tools/call",
    "params": {
      "name": "create_user",
      "arguments": {
        "email": "invalid-email",  # Invalid
        "password": "short"         # Too short
      }
    }
  }'

# Should return validation error
```

---

### 3. Port Already in Use

**Symptom:** Application won't start; error says port 8080 is already in use.

**Solution:**

1. **Change the port**
   ```yaml
   server:
     port: 8081
   ```

2. **Kill the process using the port**
   ```bash
   # Find process on port 8080
   lsof -i :8080

   # Kill it (replace PID with actual process ID)
   kill -9 <PID>
   ```

---

### 4. Sensitive Parameters Still Appearing in Logs

**Symptom:** Passwords or API keys are visible in application logs.

**Possible Causes:**

1. **Not marked as sensitive**
   ```java
   // ❌ Wrong
   @McpInput String password

   // ✅ Correct
   @McpInput(sensitive = true) String password
   ```

2. **Explicitly logging the parameter**
   ```java
   // ❌ Wrong
   logger.info("User password: {}", password);

   // ✅ Correct - Don't log it
   logger.info("Password validation successful");
   ```

3. **DEBUG level logging enabled in production**
   ```yaml
   logging:
     level:
       com.raynermendez.spring_boot_mcp_companion: INFO  # Not DEBUG
   ```

**Solution:**
- Mark all sensitive parameters with `sensitive = true`
- Never log sensitive data
- Use INFO level in production

---

### 5. Type Mismatch Errors

**Symptom:** Getting "invalid parameter type" or JSON conversion errors.

**Possible Causes:**

1. **Unsupported type**
   ```java
   // ❌ These types may not serialize correctly
   @McpInput BufferedReader reader
   @McpInput InputStream stream

   // ✅ Use supported types
   @McpInput String content
   @McpInput byte[] data
   ```

2. **Wrong type annotation**
   ```java
   // ❌ Wrong
   @McpInput Integer price  // Come as string "99.99"

   // ✅ Correct
   @McpInput BigDecimal price
   ```

**Supported Types:**
- Primitives: `int`, `long`, `float`, `double`, `boolean`
- Wrappers: `Integer`, `Long`, `Float`, `Double`, `Boolean`
- Strings: `String`
- Numbers: `BigDecimal`, `BigInteger`
- Dates: `LocalDate`, `LocalDateTime`, `LocalTime`, `ZonedDateTime`
- Collections: `List<T>`, `Set<T>`, `Map<K,V>`
- Enums: Any enum type
- POJOs: Any class with getters/setters
- Records: Java records

---

### 6. Performance Issues

**Symptom:** Tools are slow or timing out.

**Possible Causes:**

1. **N+1 database queries**
   ```java
   // ❌ Slow - Makes separate query per item
   List<Order> orders = orderRepository.findAll()
       .stream()
       .map(order -> new OrderDTO(order,
           itemRepository.findByOrderId(order.getId())))  // ← N queries!
       .collect(toList());

   // ✅ Fast - Single query with join
   List<Order> orders = orderRepository.findAllWithItems();
   ```

2. **No caching**
   ```java
   // ❌ Slow
   @McpTool
   public UserProfile getUserProfile(@McpInput String userId) {
       return expensiveComputation(userId);  // Recomputed every time
   }

   // ✅ Fast
   @McpTool
   @Cacheable(value = "userProfiles", key = "#userId")
   public UserProfile getUserProfile(@McpInput String userId) {
       return expensiveComputation(userId);  // Computed once, cached
   }
   ```

3. **Returning huge result sets**
   ```java
   // ❌ Could be millions of records
   @McpTool
   public List<Order> getAllOrders() {
       return orderRepository.findAll();
   }

   // ✅ Paginated
   @McpTool
   public Page<Order> getOrders(@McpInput(required = false) Integer page) {
       return orderRepository.findAll(PageRequest.of(page != null ? page : 0, 20));
   }
   ```

**Solution:**
- Use Spring Data JPA projections and eager loading
- Add caching with `@Cacheable`
- Implement pagination for large result sets
- Monitor with metrics: `/actuator/metrics`

---

### 7. Spring Security Not Protecting MCP Endpoints

**Symptom:** MCP endpoints are accessible without authentication.

**Possible Causes:**

1. **No security configuration**
   ```java
   // ❌ Missing security config

   // ✅ Add this
   @Configuration
   @EnableWebSecurity
   public class SecurityConfig {
       @Bean
       public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
           http.authorizeHttpRequests(auth -> auth
               .requestMatchers("/mcp/**").authenticated()
               .anyRequest().permitAll()
           );
           return http.build();
       }
   }
   ```

2. **Not allowing POST for /mcp/** endpoints**
   ```java
   // ❌ Wrong
   .requestMatchers("/mcp/tools/call").permitAll()  // Only matches GET

   // ✅ Correct
   .requestMatchers(HttpMethod.POST, "/mcp/tools/call").authenticated()
   ```

**Solution:**
- Add Spring Security configuration
- All `/mcp/**` endpoints should require authentication
- Use `@PreAuthorize` for fine-grained access control

---

### 8. CORS Errors in Browser

**Symptom:** Browser reports CORS error when accessing MCP endpoints.

**Solution:**

```java
@Configuration
public class CorsConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/mcp/**")
            .allowedOrigins("https://your-frontend.com")
            .allowedMethods("GET", "POST", "OPTIONS")
            .allowedHeaders("Content-Type", "Authorization")
            .allowCredentials(true)
            .maxAge(3600);
    }
}
```

Or in YAML:

```yaml
spring:
  web:
    cors:
      allowed-origins: https://your-frontend.com
      allowed-methods: GET,POST,OPTIONS
      allowed-headers: Content-Type,Authorization
      max-age: 3600
```

---

### 9. Memory Leaks or High Memory Usage

**Symptom:** Application memory usage grows over time.

**Possible Causes:**

1. **Unbounded cache**
   ```java
   // ❌ Cache grows forever
   @Cacheable(value = "data", key = "#id")
   public Data getData(@McpInput String id) { ... }

   // ✅ Cache with size limit
   ```

2. **Collecting all results in memory**
   ```java
   // ❌ Huge memory for large datasets
   return repository.findAll();  // All in memory

   // ✅ Stream or paginate
   return repository.findAll(PageRequest.of(page, 20));
   ```

**Solution:**
- Configure cache eviction policies
- Use pagination for large datasets
- Monitor heap usage: `/actuator/metrics/jvm.memory.used`

---

### 10. "Invalid Params" Error on Valid Input

**Symptom:** Valid-looking input is rejected with "invalid params" error.

**Possible Causes:**

1. **Parameter name mismatch**
   ```java
   @McpTool
   public Order getOrder(@McpInput String orderId) { ... }

   // Call with wrong name
   curl ... -d '{"params": {"arguments": {"order_id": "123"}}}'  // ❌ Wrong name

   // Should be
   curl ... -d '{"params": {"arguments": {"orderId": "123"}}}'  // ✅ Correct
   ```

2. **Wrong type in JSON**
   ```java
   @McpTool
   public void createOrder(@McpInput Integer quantity) { ... }

   // ❌ Wrong - sending string
   {"quantity": "5"}

   // ✅ Correct - sending number
   {"quantity": 5}
   ```

**Solution:**
- Use tools/list to see exact parameter names
- Check JSON types match parameter types
- Use JSON-RPC 2.0 format correctly

---

### 11. Application Won't Start

**Symptom:** Application fails to start with auto-configuration errors.

**Check:**

1. **Correct Spring Boot version**
   ```xml
   <parent>
       <groupId>org.springframework.boot</groupId>
       <artifactId>spring-boot-starter-parent</artifactId>
       <version>4.0.5</version>  <!-- Or 4.0.x -->
   </parent>
   ```

2. **Java version 25+**
   ```xml
   <properties>
       <java.version>25</java.version>
   </properties>
   ```

3. **All dependencies present**
   ```bash
   mvn dependency:tree | grep spring
   ```

4. **Logs for error details**
   ```bash
   mvn spring-boot:run 2>&1 | grep -A5 ERROR
   ```

---

### 12. Testing MCP Endpoints

**Can't test locally?**

```bash
# Test tool list
curl -X POST http://localhost:8080/mcp/tools/list \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc": "2.0", "id": 1, "method": "tools/list"}'

# Test server info
curl http://localhost:8080/mcp/server-info

# Test health
curl http://localhost:8080/actuator/health
```

Use [tools list endpoint debug](#12-testing-mcp-endpoints) to verify setup.

---

### Getting Help

If you're still stuck:

1. **Check logs**
   ```bash
   # For Spring Boot logs
   tail -f logs/application.log

   # Enable DEBUG logging temporarily
   logging:
     level:
       com.raynermendez.spring_boot_mcp_companion: DEBUG
   ```

2. **Verify configuration**
   ```bash
   # Show all MCP configuration
   curl http://localhost:8080/actuator/configprops | grep -i mcp
   ```

3. **Review test examples**
   See working test cases in `src/test/java` directory.

4. **Report an issue**
   - [GitHub Issues](https://github.com/RaynerMDZ/spring-boot-mcp-companion/issues)
   - Include: error message, configuration, method annotation, Spring Boot version

---

See [BEST_PRACTICES.md](BEST_PRACTICES.md) for preventive measures and [API_REFERENCE.md](API_REFERENCE.md) for complete reference.

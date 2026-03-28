# Security Policy

## Reporting a Vulnerability

If you discover a security vulnerability in Spring Boot MCP Companion, please report it responsibly to us rather than disclosing it publicly.

### Reporting Process

1. **DO NOT** create a public GitHub issue for security vulnerabilities
2. **Email** your findings to: **security@example.com**
3. **Include** in your report:
   - Vulnerability description
   - Affected version(s)
   - Steps to reproduce
   - Potential impact
   - Suggested fix (if you have one)

### Response Timeline

- **Acknowledgment**: Within 24 hours
- **Assessment**: Within 7 days
- **Fix development**: Within 14 days of assessment
- **Public disclosure**: Coordinated with you, typically 30-90 days from report

## Security Best Practices

When using Spring Boot MCP Companion, follow these practices:

### 1. **Input Validation**

The framework automatically validates inputs using Jakarta Bean Validation. Always add appropriate constraints:

```java
@McpTool
public User createUser(
    @McpInput @Email String email,
    @McpInput @Size(min = 8) String password,
    @McpInput @Min(18) @Max(120) Integer age
) { ... }
```

### 2. **Sensitive Parameters**

Mark sensitive parameters to prevent them from appearing in logs:

```java
@McpInput(sensitive = true) String password,
@McpInput(sensitive = true) String apiKey,
@McpInput(sensitive = true) String token
```

Sensitive parameters are:
- Omitted from request logs
- Not included in metrics dimensions
- Masked in debug output

### 3. **Enable HTTPS**

Always use HTTPS in production:

```yaml
server:
  ssl:
    enabled: true
    key-store: ${KEYSTORE_PATH}
    key-store-password: ${KEYSTORE_PASSWORD}
    key-store-type: PKCS12
```

### 4. **Configure Authentication**

Require authentication for MCP endpoints:

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

### 5. **Rate Limiting**

Implement rate limiting to prevent abuse:

```xml
<dependency>
  <groupId>org.springframework.cloud</groupId>
  <artifactId>spring-cloud-starter-gateway</artifactId>
</dependency>
```

### 6. **Error Handling**

The framework returns generic error messages to prevent information disclosure:

```json
{
  "error": {
    "code": -32603,
    "message": "Internal server error"
  }
}
```

Database errors, stack traces, and other internal details are logged but not sent to clients.

### 7. **Dependency Updates**

Keep dependencies updated:

```bash
# Check for outdated dependencies
mvn versions:display-dependency-updates

# Update to latest versions
mvn versions:use-latest-versions
```

### 8. **Logging Configuration**

Don't log sensitive information:

```yaml
logging:
  level:
    # Avoid DEBUG in production
    com.raynermendez.spring_boot_mcp_companion: INFO
    org.springframework: INFO

  # Consider using a proper logging service
  file:
    name: /var/log/application.log
    max-size: 10MB
    max-history: 30
```

## Known Security Considerations

### 1. **Reflection-based Method Invocation**

The framework uses reflection to call annotated methods. Ensure that:
- Only authorized Spring beans are scanned
- Bean class loaders are properly configured
- No malicious bytecode is loaded dynamically

### 2. **JSON Schema Generation**

JSON schemas are generated from Java types. Be aware that:
- Complex nested types may reveal internal structure
- Generic types are exposed in schema
- Consider if schema visibility is appropriate

### 3. **Error Messages**

While generic, error messages might reveal:
- Tool/resource/prompt names
- Parameter names and types
- Whether a feature exists

This is minimal information disclosure but consider your threat model.

## Security Headers

Add security headers to responses:

```java
@Configuration
public class SecurityHeadersConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor((request, response, handler) -> {
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("X-Frame-Options", "DENY");
            response.setHeader("X-XSS-Protection", "1; mode=block");
            response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
            return true;
        });
    }
}
```

## CORS Configuration

If exposing to external clients, configure CORS properly:

```java
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/mcp/**")
            .allowedOrigins("https://trusted-domain.com")
            .allowedMethods("GET", "POST")
            .allowedHeaders("Content-Type", "Authorization")
            .allowCredentials(true)
            .maxAge(3600);
    }
}
```

## Encryption

### At Rest
Consider encrypting sensitive data in your database:
- Passwords (use bcrypt)
- API keys (use encryption)
- PII (consider hashing)

### In Transit
- Use HTTPS (TLS 1.2+)
- Use authentication (OAuth, JWT, etc.)
- Validate certificate chains

## Compliance

### GDPR
If processing EU user data:
- Implement data export functionality
- Support data deletion
- Log access and modifications
- Implement data minimization

### HIPAA
If processing health data:
- Encrypt data at rest and in transit
- Audit all access
- Implement backup and disaster recovery
- Ensure business associate agreements

### PCI DSS
If processing payment card data:
- Don't store full card numbers
- Use tokenization
- Implement network segmentation
- Regular security testing

## Security Testing

### Manual Testing

```bash
# Test input validation
curl -X POST http://localhost:8080/mcp/tools/call \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "params": {
      "name": "create_user",
      "arguments": {
        "email": "invalid-email",
        "password": "short"
      }
    }
  }'

# Test authentication
curl -X POST http://localhost:8080/mcp/tools/list \
  # Should get 401 Unauthorized if security enabled
```

### Automated Testing

```bash
# Run security-focused tests
mvn test -Dtest=*SecurityTest

# Check for known vulnerabilities
mvn org.owasp:dependency-check-maven:check
```

### Dependency Scanning

```bash
# Check for outdated/vulnerable dependencies
mvn versions:display-dependency-updates
mvn org.owasp:dependency-check-maven:check

# Use SBOM (Software Bill of Materials)
mvn cyclonedx:makeBom
```

## Version Support

| Version | Status | Supported Until |
|---------|--------|-----------------|
| 1.0.x   | Active | 2027-03-27      |
| 0.x     | EOL    | 2026-06-27      |

Security patches are provided for supported versions.

## Security Advisories

Security advisories are published here:
- [GitHub Security Advisories](https://github.com/yourusername/spring-boot-mcp-companion/security/advisories)
- [Maven Central](https://central.sonatype.com/artifact/com.raynermendez/spring-boot-mcp-companion-core)

## Third-Party Security Audit

For enterprise deployments, we recommend:
- Third-party penetration testing
- Code review by security experts
- Vulnerability scanning
- Compliance verification

## Contact

**Security Team:** security@example.com
**PGP Key:** [Key ID and fingerprint]

---

Thank you for helping us keep Spring Boot MCP Companion secure! 🔒

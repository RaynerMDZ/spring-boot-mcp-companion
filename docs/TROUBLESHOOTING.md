# Troubleshooting Guide

Comprehensive troubleshooting guide for Spring Boot MCP Companion framework.

## Quick Diagnostics

### Health Check Script

```bash
#!/bin/bash
set -e

echo "=== Spring Boot MCP Companion - Health Check ==="
echo

# Check Java version
echo "[1] Java Version:"
java -version 2>&1 | head -1

# Check main API health
echo -e "\n[2] Main API Health (http://localhost:8080/actuator/health):"
curl -s http://localhost:8080/actuator/health | jq . || echo "FAILED - API not responding"

# Check MCP server health
echo -e "\n[3] MCP Server Health (http://localhost:8090/mcp/tools/list):"
curl -s -X POST http://localhost:8090/mcp/tools/list \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc": "2.0", "id": "1", "method": "tools/list", "params": {}}' | jq .result.tools[0].name || echo "FAILED - MCP not responding"

# Check port availability
echo -e "\n[4] Port Availability:"
netstat -tlnp 2>/dev/null | grep -E "8080|8090" || echo "Ports not in LISTEN state"

# Check running processes
echo -e "\n[5] Java Processes:"
ps aux | grep -i java | grep -v grep || echo "No Java processes found"

# Check memory usage
echo -e "\n[6] Memory Status:"
free -h

echo -e "\n=== End Health Check ==="
```

---

## Issue Categories

### A. Startup Issues

#### A1: Application Fails to Start

**Error Message:**
```
Error creating bean with name 'mcpAutoConfiguration'
```

**Causes & Solutions:**

| Cause | Solution |
|-------|----------|
| Java version < 17 | Upgrade to Java 17+ LTS |
| Spring Boot < 4.0.5 | Upgrade Spring Boot to 4.0.5+ |
| Port 8080 or 8090 in use | Change ports in application.yml or kill conflicting process |
| Invalid configuration YAML | Validate YAML syntax with linter |
| Missing dependencies | Run `mvn dependency:resolve` |

**Debugging:**
```bash
# Run with verbose logging
java -Dlogging.level.root=DEBUG -jar app.jar 2>&1 | tee startup.log

# Check for port conflicts
lsof -i :8080
lsof -i :8090

# Validate Spring Boot config
java -jar app.jar --spring.config.location=application.yml --debug
```

#### A2: "No Such Method" or "Method Not Found"

**Error Message:**
```
NoSuchMethodException: com.example.MyService.myMethod()
```

**Causes & Solutions:**

1. **Method signature mismatch**
   - Tool method must accept correct parameter types
   - Check `@McpInput` annotations match method parameters

2. **Method visibility**
   - Method must be public
   - Class must be accessible to Spring classpath scanner

3. **Reflection security checks**
   - Method must be in user-defined classes, not JDK/framework classes
   - Check DefaultMcpDispatcher.verifyMethodSecurity() logs

**Fix:**
```java
// ✅ Correct
@McpTool(name = "myTool", description = "...")
public MyResponse myMethod(
    @McpInput(name = "input", description = "...") String input
) {
    // ...
}

// ❌ Wrong - private method
private String myMethod(String input) { }

// ❌ Wrong - parameter type mismatch
@McpInput(name = "input", type = "number") String input  // Should be number type, not String
```

---

### B. Runtime Issues

#### B1: "Tool Not Found" or "Resource Not Found"

**Error:**
```
Tool not found: myTool
```

**Diagnosis:**
```bash
# List available tools
curl -X POST http://localhost:8090/mcp/tools/list \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc": "2.0", "id": "1", "method": "tools/list"}' | jq .
```

**Causes & Solutions:**

| Cause | Solution |
|-------|----------|
| Tool not annotated with `@McpTool` | Add annotation to method |
| Class not scanned by Spring | Ensure class is in scanned packages |
| Wrong tool name in request | Check exact tool name (case-sensitive) |
| Tool in disabled/non-active profile | Activate required Spring profile |

**Fix Example:**
```java
@RestController
@RequestMapping("/api")
public class MyTools {

    @McpTool(name = "getUserData", description = "Get user information")
    public UserData getUser(
        @McpInput(name = "userId") String userId
    ) {
        // Implementation
    }
}

// Enable scanning
@SpringBootApplication
@EnableMcpCompanion  // ← Required to scan for @McpTool
public class Application { }
```

#### B2: "Validation Failed" Error

**Error:**
```json
{
  "error": "Error invoking tool 'myTool': Validation failed: [violations...]"
}
```

**Common Validation Errors:**

| Error | Cause | Fix |
|-------|-------|-----|
| `Missing required parameter: X` | Parameter marked as required but not provided | Provide all required parameters |
| `Invalid type for parameter X` | Parameter type doesn't match schema | Check parameter type definition |
| `String value exceeds max length` | Input too long | Reduce input length |
| `Number out of range` | Number exceeds min/max | Provide value in valid range |

**Example:**
```json
// ❌ Invalid - missing required parameter
{
  "jsonrpc": "2.0",
  "id": "1",
  "method": "tools/call",
  "params": {
    "name": "myTool"
    // Missing: "arguments"
  }
}

// ✅ Valid
{
  "jsonrpc": "2.0",
  "id": "1",
  "method": "tools/call",
  "params": {
    "name": "myTool",
    "arguments": {
      "userId": "user123"
    }
  }
}
```

---

### C. Security & Rate Limiting Issues

#### C1: 429 Too Many Requests

**Symptom:**
```
HTTP 429 Too Many Requests
Retry-After: 60
```

**Cause:** Client exceeded rate limit (100 requests/minute per IP)

**Solutions:**

```bash
# Check rate limit status
curl -s http://localhost:8080/actuator/prometheus | grep rate_limiter

# If behind proxy, ensure X-Forwarded-For is set
curl -H "X-Forwarded-For: 192.168.1.100" http://localhost:8090/mcp/tools/list

# Batch requests to reduce frequency
# Instead of 100 requests/minute, spread them out

# Wait before retrying (check Retry-After header)
sleep 60 && curl http://localhost:8090/mcp/tools/list
```

**Increase Rate Limit (if needed):**

Edit `src/main/java/...RateLimitInterceptor.java`:
```java
// Change this line (currently 100)
private static final int RATE_LIMIT_PER_MINUTE = 100;
// To:
private static final int RATE_LIMIT_PER_MINUTE = 200;
```

Rebuild and redeploy.

#### C2: 413 Request Entity Too Large

**Error:**
```
HTTP 413 Payload Too Large
```

**Cause:** Request body exceeds 1MB limit

**Solutions:**

1. **Reduce payload size:**
   ```bash
   # Check request size
   curl -X POST \
     -H "Content-Length: $(echo -n 'json-data' | wc -c)" \
     http://localhost:8090/mcp/tools/call
   ```

2. **Batch large data:**
   ```json
   // Instead of one large request
   POST /tools/call { "data": [large array] }

   // Split into multiple requests
   POST /tools/call { "data": [first 100 items] }
   POST /tools/call { "data": [next 100 items] }
   ```

3. **Increase limit (if needed):**

   Edit `McpRequestSizeFilter.java`:
   ```java
   private static final long MAX_REQUEST_SIZE = 1_048_576L; // 1 MB
   // Change to:
   private static final long MAX_REQUEST_SIZE = 5_242_880L; // 5 MB
   ```

#### C3: Information Leakage in Error Messages

**Symptom:** Error response contains sensitive details like:
- Database connection strings
- Internal file paths
- Source code details

**This is prevented by:** `EnhancedExceptionSanitizer`

**Verify it's working:**
```bash
# Trigger an error (e.g., call non-existent tool)
curl -X POST http://localhost:8090/mcp/tools/call \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "1",
    "method": "tools/call",
    "params": {
      "name": "nonexistent",
      "arguments": {}
    }
  }'

# Should return generic error, not stack trace
# ✅ Correct: { "error": "Tool not found: nonexistent" }
# ❌ Wrong: { "error": "at database://... caused by..." }
```

---

### D. Performance Issues

#### D1: High Latency or Slow Responses

**Diagnosis:**
```bash
# Check response times
time curl -X POST http://localhost:8090/mcp/tools/list \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc": "2.0", "id": "1", "method": "tools/list"}'

# Check thread pool status
curl -s http://localhost:8080/actuator/metrics/process.threads.live | jq .measurements[0].value

# Check memory usage
curl -s http://localhost:8080/actuator/metrics/jvm.memory.used | jq .measurements[0].value

# Check GC activity
jstat -gc -h20 <pid> 1000  # Every 1 second, 20 line header
```

**Common Causes & Fixes:**

| Cause | Symptom | Fix |
|-------|---------|-----|
| Thread pool exhausted | All requests slow | Increase `server.tomcat.max-threads` |
| High memory usage | Frequent GC pauses | Increase JVM heap (-Xmx) |
| Slow tool implementation | Some tools very slow | Optimize tool code, add caching |
| Network latency | Consistent delays | Check network, use latency monitoring |

**Performance Tuning:**
```yaml
server:
  tomcat:
    max-threads: 300        # Was: default (depends on CPU cores)
    min-spare-threads: 20
    accept-count: 150

mcp:
  server:
    port: 8090
```

**JVM Tuning:**
```bash
java -Xms2G -Xmx4G \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=100 \
  -XX:+ParallelRefProcEnabled \
  -Xlog:gc:logs/gc.log \
  -jar app.jar
```

#### D2: Memory Leaks

**Symptom:** Memory usage continuously increases

**Diagnosis:**
```bash
# Monitor memory trend over time
while true; do
  date
  curl -s http://localhost:8080/actuator/metrics/jvm.memory.used \
    | jq '.measurements[0].value / (1024*1024*1024) | floor'
  sleep 5
done

# Dump heap when large
jmap -dump:live,format=b,file=heap_$(date +%s).bin <pid>
```

**Analysis:**
```bash
# Analyze heap dump
# Using Eclipse Memory Analyzer or similar:
# 1. Open heap dump
# 2. Generate leak suspects report
# 3. Check for large object arrays
# 4. Trace back to root cause
```

**Common Leak Sources:**

1. **Rate limiter buckets** (if cleanup fails)
   - Check RateLimitInterceptor cleanup is running
   - Monitor: `mcp_rate_limiter_client_buckets`

2. **Tool result caching** (if implemented)
   - Add cache eviction policy
   - Implement TTL-based cache

3. **Spring bean leaks**
   - Check for circular dependencies
   - Verify all resources are closed

---

### E. Configuration Issues

#### E1: Properties Not Recognized

**Problem:** Setting `mcp.server.port: 8090` has no effect

**Solutions:**

1. **Check file location & format:**
   ```bash
   # Must be in src/main/resources/
   src/main/resources/application.yml    # ✅
   src/main/resources/application.yaml   # ✅

   # Not in these locations ❌
   src/application.yml
   application.properties
   ```

2. **Verify YAML syntax:**
   ```yaml
   # ✅ Correct
   mcp:
     server:
       port: 8090

   # ❌ Wrong (tabs)
   mcp:
   	server:
   		port: 8090

   # ❌ Wrong (quotes missing)
   mcp.server.port = 8090
   ```

3. **Check Spring profile:**
   ```bash
   # If using profiles, check active profile
   java -Dspring.profiles.active=production -jar app.jar

   # Properties file format:
   # application-production.yml (for profile 'production')
   ```

4. **Verify property names:**
   ```bash
   # Check what Spring sees
   java -jar app.jar --debug 2>&1 | grep "mcp.server"
   ```

#### E2: MCP Server Not Starting on Different Port

**Problem:**
```
MCP server still runs on 8090 even with config change
```

**Solutions:**

1. **Verify config is loaded:**
   ```bash
   java -jar app.jar \
     --mcp.server.port=9090 \
     --spring.config.location=application.yml \
     --debug 2>&1 | grep "mcp.server.port"
   ```

2. **Check for port conflicts:**
   ```bash
   netstat -tlnp | grep 9090
   ```

3. **Verify in code:**
   ```java
   @Autowired
   private McpServerProperties props;

   @PostConstruct
   void checkConfig() {
       System.out.println("MCP Port: " + props.port());
   }
   ```

---

### F. Network & Proxy Issues

#### F1: X-Forwarded-For Header Not Working

**Problem:** Behind proxy, rate limiter sees proxy IP instead of client IP

**Symptoms:**
- Different clients hitting same rate limit
- Logs show proxy IP for all requests

**Solutions:**

```yaml
server:
  tomcat:
    remoteip:
      remote-ip-header: X-Forwarded-For
      protocol-header: X-Forwarded-Proto
      internal-proxies: 10\.0\.0\.[0-9]+|192\.168\.[0-9]+\.[0-9]+
```

**Verify fix:**
```bash
# With proxy
curl -H "X-Forwarded-For: 192.168.1.100" http://localhost:8090/mcp/tools/list

# Check that 192.168.1.100 is identified in rate limiter logs
tail -f logs/application.log | grep "192.168.1.100\|rate limit"
```

#### F2: CORS or Cross-Origin Issues

**Problem:** MCP called from browser gets CORS error

**Solution:**

Spring Boot handles CORS automatically for same-origin requests. For cross-origin:

```java
@Configuration
public class CorsConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/mcp/**")
            .allowedOrigins("https://example.com")
            .allowedMethods("GET", "POST", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(true)
            .maxAge(3600);
    }
}
```

---

## Diagnostic Commands Reference

```bash
# System info
java -version
mvn -version
uname -a

# Process monitoring
ps aux | grep java
top -p <pid>
jps -l -v

# Port checking
netstat -tlnp | grep 8090
lsof -i :8090

# Network testing
curl -v http://localhost:8090/mcp/tools/list
curl -I http://localhost:8080/actuator/health
nc -zv localhost 8090

# Logging
tail -f logs/application.log
grep ERROR logs/application.log
grep -E "WARN|ERROR" logs/application.log | tail -20

# Metrics
curl http://localhost:8080/actuator/metrics
curl http://localhost:8080/actuator/prometheus

# Thread dump
jcmd <pid> Thread.print > thread_dump.txt
jstack <pid> > thread_dump.txt

# Memory analysis
jmap -heap <pid>
jmap -histo:live <pid> | head -20

# GC monitoring
jstat -gc <pid> 1000  # Every 1 second
jstat -gccause <pid>  # Show last GC cause
```

---

## Getting Help

If issues persist:

1. **Collect diagnostics:**
   ```bash
   ./health-check.sh > diagnostics.txt 2>&1
   tail -1000 logs/application.log >> diagnostics.txt
   jmap -heap <pid> >> diagnostics.txt
   ```

2. **Check GitHub Issues:**
   https://github.com/RaynerMDZ/spring-boot-mcp-companion/issues

3. **Review Documentation:**
   - OPERATIONAL_RUNBOOK.md (this directory)
   - API documentation in docs/
   - Spring Boot official docs: https://spring.io/

4. **Contact:**
   - GitHub Issues for framework bugs
   - Maven Central page for dependency info

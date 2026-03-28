# Operational Runbook

## Spring Boot MCP Companion - Production Operations Guide

This runbook covers operational procedures for deploying and managing the Spring Boot MCP Companion framework in production.

## Table of Contents

1. [Deployment](#deployment)
2. [Configuration](#configuration)
3. [Health Monitoring](#health-monitoring)
4. [Performance Tuning](#performance-tuning)
5. [Troubleshooting](#troubleshooting)
6. [Emergency Procedures](#emergency-procedures)

---

## Deployment

### Prerequisites

- Java 17 LTS or later
- Spring Boot 4.0.5 or later
- Maven 3.8.1+ (for building)

### Quick Start

```bash
# 1. Add dependency to pom.xml
<dependency>
    <groupId>com.raynermendez</groupId>
    <artifactId>spring-boot-mcp-companion-core</artifactId>
    <version>1.0.0</version>
</dependency>

# 2. Enable MCP in main application class
@SpringBootApplication
@EnableMcpCompanion
public class YourApplication {
    public static void main(String[] args) {
        SpringApplication.run(YourApplication.class, args);
    }
}

# 3. Configure in application.yml
server:
  port: 8080                      # Main API port
  servlet:
    context-path: /api

mcp:
  server:
    enabled: true                 # Enable MCP server
    port: 8090                    # MCP server port (separate instance)
    base-path: /mcp               # MCP endpoint base path
```

### Deployment Architecture

```
┌─────────────────────────────────────────────┐
│          Load Balancer / Ingress             │
└────────────┬────────────────────────────────┘
             │
    ┌────────┴────────┐
    │                 │
┌───▼────────┐   ┌────▼────────┐
│ Main API   │   │ MCP Server   │
│ :8080/api  │   │ :8090/mcp    │
└────────────┘   └─────────────┘
```

### Health Checks

**Main Application Health:**
```bash
curl http://localhost:8080/actuator/health
```

**MCP Server Health (via tools list endpoint):**
```bash
curl -X POST http://localhost:8090/mcp/tools/list \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc": "2.0", "id": "1", "method": "tools/list", "params": {}}'
```

---

## Configuration

### Core Settings

| Setting | Default | Description |
|---------|---------|-------------|
| `mcp.server.enabled` | `true` | Enable/disable MCP server |
| `mcp.server.port` | `8090` | MCP server port |
| `mcp.server.base-path` | `/mcp` | MCP endpoint base path |
| `server.port` | `8080` | Main API port |

### Recommended Production Settings

```yaml
server:
  port: 8080
  servlet:
    context-path: /api
  tomcat:
    max-threads: 200
    max-connections: 10000
    min-spare-threads: 10
    accept-count: 100
    connection-timeout: 30000
    keep-alive-timeout: 60000

mcp:
  server:
    enabled: true
    port: 8090
    base-path: /mcp

management:
  endpoints:
    web:
      exposure:
        include: health,metrics,info
  metrics:
    export:
      prometheus:
        enabled: true
```

### Security Configuration

The framework includes built-in security features:

- **Rate Limiting**: 100 requests/minute per client IP
- **Request Size Limit**: 1MB maximum request body
- **Async Timeout**: 30 seconds per request
- **Slowloris Protection**: Detects incomplete HTTP requests
- **Exception Sanitization**: Prevents sensitive data leakage
- **Input Validation**: Strict type checking on parameters

---

## Health Monitoring

### Key Metrics

Monitor these metrics in production:

```
# Throughput
mcp_dispatcher_tool_invocations_total
mcp_dispatcher_tool_errors_total

# Latency
mcp_dispatcher_tool_latency_seconds

# Rate Limiter
mcp_rate_limiter_rejected_requests_total
mcp_rate_limiter_client_buckets

# Thread Pool
process_threads_live
process_threads_peak
```

### Prometheus Endpoint

```bash
curl http://localhost:8080/actuator/prometheus
```

### Performance Baselines

Expected production performance:

| Metric | Baseline | Alert Threshold |
|--------|----------|-----------------|
| Throughput | 1888 req/sec | <1000 req/sec |
| Latency P95 | <100ms | >200ms |
| Memory Growth | <50% per hour | >75% per hour |
| Error Rate | <0.1% | >1% |
| GC Pause Time | <100ms | >500ms |

---

## Performance Tuning

### Thread Pool Configuration

If you observe high latency or thread exhaustion:

```yaml
server:
  tomcat:
    max-threads: 300          # Increase for high concurrency
    min-spare-threads: 20
    accept-count: 150
    max-connections: 20000
```

### Memory Tuning

```bash
# Recommended JVM settings for production
java -Xms2G -Xmx4G \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:+ParallelRefProcEnabled \
  -jar your-app.jar
```

### Timeout Adjustments

If legitimate requests are timing out:

Edit the async request timeout in McpWebConfig (default: 30 seconds)

```yaml
# Note: Currently hardcoded to 30 seconds
# Create custom property if needed for different timeout
```

---

## Troubleshooting

### Common Issues

#### Issue: High Latency or Slow Responses

**Diagnosis:**
```bash
# Check thread pool status
curl http://localhost:8080/actuator/metrics/process.threads.live

# Check memory usage
curl http://localhost:8080/actuator/metrics/jvm.memory.used
```

**Solutions:**
1. Increase thread pool size (see Performance Tuning)
2. Check GC logs for excessive pauses
3. Profile application with JFR (Java Flight Recorder)

**GC Monitoring:**
```bash
# Enable GC logging
java -Xlog:gc:logs/gc.log:time,level,tags \
  -jar your-app.jar
```

#### Issue: 429 Too Many Requests Errors

**Cause:** Client exceeds rate limit (100 requests/minute per IP)

**Diagnosis:**
```bash
# Check rate limiter metrics
curl http://localhost:8080/actuator/prometheus \
  | grep mcp_rate_limiter
```

**Solutions:**
1. Batch requests to reduce frequency
2. Distribute requests across multiple IP addresses (if using load balancer)
3. Contact system admin if legitimate heavy traffic expected
4. Modify rate limit in RateLimitInterceptor if needed

#### Issue: 413 Request Entity Too Large

**Cause:** Request payload exceeds 1MB limit

**Solutions:**
1. Reduce payload size by:
   - Pagination
   - Filtering unnecessary fields
   - Compression
2. Modify limit in McpRequestSizeFilter if needed
3. Increase limit: Change `MAX_REQUEST_SIZE = 1_048_576L`

#### Issue: "No ServletContext set" Error

**Cause:** MCP context initialization failure

**Solution:**
```bash
# Ensure mcp.server.enabled=true is set
# Check logs for servlet container startup errors
tail -f logs/application.log | grep "MCP\|Servlet"
```

#### Issue: Null Pointer Exception in RateLimitInterceptor

**Cause:** Remote address is null and no X-Forwarded-For header

**Solution:**
```yaml
# If behind proxy, ensure proxy sets X-Forwarded-For header
# Or configure Spring to trust proxy headers
server:
  tomcat:
    remoteip:
      remote-ip-header: X-Forwarded-For
      protocol-header: X-Forwarded-Proto
```

### Log Analysis

**Look for these patterns:**

```
# Normal operation
[INFO] MCP Transport Controller initialized at base path: /mcp
[INFO] MCP Embedded Server context initialized on port 8090

# Warnings (non-critical)
[WARN] Rate limit exceeded for client

# Errors (needs investigation)
[ERROR] Error invoking tool
[ERROR] Slowloris protection triggered
[ERROR] Input validation failed
```

---

## Emergency Procedures

### Graceful Shutdown

```bash
# Send SIGTERM to gracefully shutdown
kill -TERM <pid>

# Application will:
# 1. Stop accepting new requests
# 2. Wait up to 30 seconds for in-flight requests to complete
# 3. Close database connections
# 4. Shutdown MCP server
# 5. Exit
```

### Kill Without Waiting (Force Shutdown)

```bash
# Only use if graceful shutdown hangs
kill -9 <pid>
```

### Restart Procedure

```bash
# 1. Start application
java -jar app.jar

# 2. Wait for startup messages
# Look for: "Started YourApplication"

# 3. Verify health
curl http://localhost:8080/actuator/health
curl http://localhost:8090/mcp/tools/list

# 4. If health checks fail, check logs
tail -f logs/application.log
```

### Rollback Procedure

If critical bug is found:

```bash
# 1. Stop current version
kill -TERM <current-pid>

# 2. Wait for graceful shutdown (up to 30s)

# 3. Start previous version
java -jar app-1.0.0-PREVIOUS.jar

# 4. Verify health checks pass

# 5. Monitor for errors
```

---

## Production Checklist

Before deploying to production:

- [ ] Java 17+ is installed
- [ ] Spring Boot 4.0.5+ configured
- [ ] `mcp.server.enabled=true` is set
- [ ] Both port 8080 and 8090 are available
- [ ] Load balancer is configured to route to both ports
- [ ] Health check endpoints are monitored
- [ ] Log aggregation is configured
- [ ] Metrics collection is enabled
- [ ] JVM memory allocation is appropriate
- [ ] GC logging is enabled
- [ ] Backup/restore procedure is documented

---

## Support & Escalation

### Internal Issues
- Check troubleshooting section above
- Review application logs
- Check metrics and health endpoints

### Framework Issues
- GitHub: https://github.com/RaynerMDZ/spring-boot-mcp-companion
- Maven Central: com.raynermendez:spring-boot-mcp-companion-core

---

## Appendix: Useful Commands

```bash
# Get application PID
ps aux | grep "java.*app.jar"

# Monitor real-time metrics
watch -n 1 'curl -s http://localhost:8080/actuator/metrics/jvm.memory.used'

# Extract application logs
docker logs <container-id> | grep "ERROR\|WARN"

# Test MCP endpoint
curl -X POST http://localhost:8090/mcp/tools/list \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "test-1",
    "method": "tools/list",
    "params": {}
  }' | jq .

# Check port availability
netstat -tlnp | grep 8080
netstat -tlnp | grep 8090

# Monitor thread count
jcmd <pid> Thread.print | wc -l

# Export heap dump if needed
jmap -dump:live,format=b,file=heap.bin <pid>
```

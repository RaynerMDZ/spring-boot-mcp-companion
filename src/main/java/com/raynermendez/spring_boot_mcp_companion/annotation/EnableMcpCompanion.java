package com.raynermendez.spring_boot_mcp_companion.annotation;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;
import com.raynermendez.spring_boot_mcp_companion.config.McpAutoConfiguration;

import java.lang.annotation.*;

/**
 * Enable the Spring Boot MCP Companion framework in a Spring Boot application.
 *
 * <p>Add this annotation to your {@code @SpringBootApplication} main class to enable
 * MCP (Model Context Protocol) server functionality. This will:
 *
 * <ul>
 *   <li>Scan Spring beans for {@code @McpTool}, {@code @McpResource}, and {@code @McpPrompt} annotations</li>
 *   <li>Register MCP endpoints under the configured base path (default: {@code /mcp})</li>
 *   <li>Enable input validation and observability</li>
 *   <li>Provide health and metrics via Spring Boot Actuator</li>
 * </ul>
 *
 * <p><b>Usage Example:</b>
 *
 * <pre>
 * &#64;SpringBootApplication
 * &#64;EnableMcpCompanion
 * public class MyApplication {
 *     public static void main(String[] args) {
 *         SpringApplication.run(MyApplication.class, args);
 *     }
 * }
 * </pre>
 *
 * <p>All MCP behavior is controlled via {@code application.yml} or {@code application.properties}:
 *
 * <pre>
 * mcp:
 *   server:
 *     enabled: true           # Enable/disable MCP server (default: true)
 *     base-path: /mcp         # Base path for MCP endpoints (default: /mcp)
 *     name: my-app-mcp        # Server name advertised in MCP protocol
 *     version: 1.0.0          # Server version advertised in MCP protocol
 * </pre>
 *
 * @see com.raynermendez.spring_boot_mcp_companion.annotation.McpTool
 * @see com.raynermendez.spring_boot_mcp_companion.annotation.McpResource
 * @see com.raynermendez.spring_boot_mcp_companion.annotation.McpPrompt
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@EnableAutoConfiguration
@Import(McpAutoConfiguration.class)
public @interface EnableMcpCompanion {
}

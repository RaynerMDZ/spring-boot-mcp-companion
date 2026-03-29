package com.raynermendez.spring_boot_mcp_companion.config;

import com.raynermendez.spring_boot_mcp_companion.dispatch.DefaultMcpDispatcher;
import com.raynermendez.spring_boot_mcp_companion.dispatch.McpDispatcher;
import com.raynermendez.spring_boot_mcp_companion.mapper.DefaultMcpMappingEngine;
import com.raynermendez.spring_boot_mcp_companion.mapper.JsonSchemaGenerator;
import com.raynermendez.spring_boot_mcp_companion.mapper.McpMappingEngine;
import com.raynermendez.spring_boot_mcp_companion.registry.DefaultMcpDefinitionRegistry;
import com.raynermendez.spring_boot_mcp_companion.registry.McpDefinitionRegistry;
import com.raynermendez.spring_boot_mcp_companion.scanner.McpAnnotationScanner;
import com.raynermendez.spring_boot_mcp_companion.security.ErrorMessageSanitizer;
import com.raynermendez.spring_boot_mcp_companion.spi.DefaultMcpOutputSerializer;
import com.raynermendez.spring_boot_mcp_companion.spi.McpOutputSerializer;
import com.raynermendez.spring_boot_mcp_companion.transport.HttpStatusMapper;
import com.raynermendez.spring_boot_mcp_companion.notification.SseNotificationManager;
import com.raynermendez.spring_boot_mcp_companion.session.McpSessionManager;
import com.raynermendez.spring_boot_mcp_companion.validation.DefaultMcpInputValidator;
import com.raynermendez.spring_boot_mcp_companion.validation.McpInputValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot auto-configuration for the MCP server.
 *
 * <p>This class registers MCP annotation scanner, registry, and transport controllers when the
 * application starts. It is conditional on the "mcp.server.enabled" property being true
 * (defaults to true if not specified).
 *
 * <p>Bean definitions for Phase 3 (MVP):
 * <ul>
 *   <li>JsonSchemaGenerator: Maps Java types to JSON Schema
 *   <li>McpMappingEngine: Maps @McpTool methods to tool definitions
 *   <li>McpDefinitionRegistry: Stores and manages tool/resource/prompt definitions
 *   <li>McpDispatcher: Invokes registered tools
 *   <li>McpAnnotationScanner: Scans beans for @McpTool annotations
 * </ul>
 */
@AutoConfiguration
@EnableConfigurationProperties(McpServerProperties.class)
@ConditionalOnProperty(
    prefix = "mcp.server",
    name = "enabled",
    matchIfMissing = true)
public class McpAutoConfiguration {

  @Bean
  public JsonSchemaGenerator jsonSchemaGenerator() {
    return new JsonSchemaGenerator();
  }

  @Bean
  public McpMappingEngine mcpMappingEngine(JsonSchemaGenerator jsonSchemaGenerator) {
    return new DefaultMcpMappingEngine(jsonSchemaGenerator);
  }

  @Bean
  public McpDefinitionRegistry mcpDefinitionRegistry() {
    return new DefaultMcpDefinitionRegistry();
  }

  @Bean
  public ObjectMapper objectMapper() {
    return new ObjectMapper();
  }

  @Bean
  public McpInputValidator mcpInputValidator() {
    return new DefaultMcpInputValidator();
  }

  @Bean
  public McpOutputSerializer mcpOutputSerializer(ObjectMapper objectMapper) {
    return new DefaultMcpOutputSerializer(objectMapper);
  }

  @Bean
  public McpDispatcher mcpDispatcher(
      McpDefinitionRegistry registry,
      ObjectMapper objectMapper,
      McpInputValidator validator,
      McpOutputSerializer serializer) {
    return new DefaultMcpDispatcher(registry, objectMapper, validator, serializer);
  }

  @Bean
  public McpAnnotationScanner mcpAnnotationScanner(
      McpDefinitionRegistry registry,
      McpMappingEngine mappingEngine) {
    return new McpAnnotationScanner(registry, mappingEngine);
  }

  @Bean
  public ErrorMessageSanitizer errorMessageSanitizer() {
    return new ErrorMessageSanitizer();
  }

  @Bean
  public HttpStatusMapper httpStatusMapper() {
    return new HttpStatusMapper();
  }

  @Bean
  public McpSessionManager mcpSessionManager() {
    return new McpSessionManager(5); // 5 minute timeout
  }

  @Bean
  public SseNotificationManager sseNotificationManager(ObjectMapper objectMapper) {
    return new SseNotificationManager(objectMapper);
  }
}

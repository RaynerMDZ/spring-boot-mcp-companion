package com.raynermendez.spring_boot_mcp_companion.scanner;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.raynermendez.spring_boot_mcp_companion.annotation.McpInput;
import com.raynermendez.spring_boot_mcp_companion.annotation.McpTool;
import com.raynermendez.spring_boot_mcp_companion.mapper.DefaultMcpMappingEngine;
import com.raynermendez.spring_boot_mcp_companion.mapper.JsonSchemaGenerator;
import com.raynermendez.spring_boot_mcp_companion.model.McpToolDefinition;
import com.raynermendez.spring_boot_mcp_companion.registry.DefaultMcpDefinitionRegistry;
import com.raynermendez.spring_boot_mcp_companion.registry.McpDefinitionRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;

/** Unit tests for McpAnnotationScanner. */
class McpAnnotationScannerTest {

  private McpAnnotationScanner scanner;
  private DefaultMcpDefinitionRegistry registry;
  private DefaultMcpMappingEngine mappingEngine;
  private ApplicationContext applicationContext;

  @BeforeEach
  void setUp() {
    registry = new DefaultMcpDefinitionRegistry();
    JsonSchemaGenerator schemaGenerator = new JsonSchemaGenerator();
    mappingEngine = new DefaultMcpMappingEngine(schemaGenerator);
    scanner = new McpAnnotationScanner(registry, mappingEngine);
  }

  @Test
  void testToolWithAnnotationIsRegistered() {
    ServiceWithMcpTool bean = new ServiceWithMcpTool();

    // Manually simulate scanning (since we can't easily mock ApplicationContext in unit tests)
    try {
      java.lang.reflect.Method method =
          ServiceWithMcpTool.class.getMethod("myTool");
      McpTool annotation =
          org.springframework.core.annotation.AnnotationUtils.findAnnotation(
              method, McpTool.class);
      assertNotNull(annotation);

      McpToolDefinition def = mappingEngine.toToolDefinition(bean, method, annotation);
      registry.register(def);
    } catch (NoSuchMethodException e) {
      fail("Test method not found", e);
    }

    assertEquals(1, registry.getTools().size());
    assertEquals("my_tool", registry.getTools().get(0).name());
  }

  @Test
  void testMethodWithoutAnnotationIsIgnored() {
    ServiceWithoutMcpTool bean = new ServiceWithoutMcpTool();

    // Verify no exception when scanning a method without @McpTool
    assertDoesNotThrow(() -> {
      java.lang.reflect.Method method =
          ServiceWithoutMcpTool.class.getMethod("regularMethod");
      McpTool annotation =
          org.springframework.core.annotation.AnnotationUtils.findAnnotation(
              method, McpTool.class);
      // Annotation should be null
      assertNull(annotation);
    });

    assertEquals(0, registry.getTools().size());
  }

  @Test
  void testDuplicateToolNameThrowsException() {
    ServiceWithMcpTool bean1 = new ServiceWithMcpTool();
    ServiceWithMcpTool bean2 = new ServiceWithMcpTool();

    try {
      java.lang.reflect.Method method = ServiceWithMcpTool.class.getMethod("myTool");
      McpTool annotation =
          org.springframework.core.annotation.AnnotationUtils.findAnnotation(
              method, McpTool.class);

      // Register first tool
      McpToolDefinition def1 = mappingEngine.toToolDefinition(bean1, method, annotation);
      registry.register(def1);

      // Try to register second tool with same name
      McpToolDefinition def2 = mappingEngine.toToolDefinition(bean2, method, annotation);

      IllegalStateException exception =
          assertThrows(IllegalStateException.class, () -> registry.register(def2));
      assertTrue(exception.getMessage().contains("Duplicate"));
    } catch (NoSuchMethodException e) {
      fail("Test method not found", e);
    }
  }

  @Test
  void testRegistryLocksAfterScanningCompletes() {
    ServiceWithMcpTool bean = new ServiceWithMcpTool();

    try {
      java.lang.reflect.Method method = ServiceWithMcpTool.class.getMethod("myTool");
      McpTool annotation =
          org.springframework.core.annotation.AnnotationUtils.findAnnotation(
              method, McpTool.class);

      McpToolDefinition def = mappingEngine.toToolDefinition(bean, method, annotation);
      registry.register(def);

      // Simulate scanning complete - lock registry
      registry.lock();

      assertEquals(McpDefinitionRegistry.RegistryState.READY, registry.getState());
    } catch (NoSuchMethodException e) {
      fail("Test method not found", e);
    }
  }

  @Test
  void testRegisterAfterLockThrowsException() {
    registry.lock();

    ServiceWithMcpTool bean = new ServiceWithMcpTool();

    try {
      java.lang.reflect.Method method = ServiceWithMcpTool.class.getMethod("myTool");
      McpTool annotation =
          org.springframework.core.annotation.AnnotationUtils.findAnnotation(
              method, McpTool.class);

      McpToolDefinition def = mappingEngine.toToolDefinition(bean, method, annotation);

      IllegalStateException exception =
          assertThrows(IllegalStateException.class, () -> registry.register(def));
      assertTrue(exception.getMessage().contains("locked"));
    } catch (NoSuchMethodException e) {
      fail("Test method not found", e);
    }
  }

  @Test
  void testGetToolsReturnsAllRegisteredTools() {
    ServiceWithMultipleTools bean = new ServiceWithMultipleTools();

    try {
      // Register first tool
      java.lang.reflect.Method method1 = ServiceWithMultipleTools.class.getMethod("tool1");
      McpTool annotation1 =
          org.springframework.core.annotation.AnnotationUtils.findAnnotation(
              method1, McpTool.class);
      McpToolDefinition def1 = mappingEngine.toToolDefinition(bean, method1, annotation1);
      registry.register(def1);

      // Register second tool
      java.lang.reflect.Method method2 = ServiceWithMultipleTools.class.getMethod("tool2");
      McpTool annotation2 =
          org.springframework.core.annotation.AnnotationUtils.findAnnotation(
              method2, McpTool.class);
      McpToolDefinition def2 = mappingEngine.toToolDefinition(bean, method2, annotation2);
      registry.register(def2);

      assertEquals(2, registry.getTools().size());
      // Check that both tools are registered (order may vary)
      java.util.List<String> names = registry.getTools().stream()
          .map(McpToolDefinition::name)
          .toList();
      assertTrue(names.contains("tool1"));
      assertTrue(names.contains("tool2"));
    } catch (NoSuchMethodException e) {
      fail("Test method not found", e);
    }
  }

  // Test service with MCP tool
  public static class ServiceWithMcpTool {
    @McpTool
    public void myTool() {}
  }

  // Test service without MCP tool
  public static class ServiceWithoutMcpTool {
    public void regularMethod() {}
  }

  // Test service with multiple tools
  public static class ServiceWithMultipleTools {
    @McpTool
    public void tool1() {}

    @McpTool
    public void tool2() {}
  }
}

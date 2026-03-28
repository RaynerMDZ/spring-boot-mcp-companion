package com.raynermendez.spring_boot_mcp_companion.dispatch;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raynermendez.spring_boot_mcp_companion.mapper.DefaultMcpMappingEngine;
import com.raynermendez.spring_boot_mcp_companion.mapper.JsonSchemaGenerator;
import com.raynermendez.spring_boot_mcp_companion.model.McpToolDefinition;
import com.raynermendez.spring_boot_mcp_companion.annotation.McpInput;
import com.raynermendez.spring_boot_mcp_companion.annotation.McpTool;
import com.raynermendez.spring_boot_mcp_companion.registry.DefaultMcpDefinitionRegistry;
import com.raynermendez.spring_boot_mcp_companion.spi.DefaultMcpOutputSerializer;
import com.raynermendez.spring_boot_mcp_companion.validation.DefaultMcpInputValidator;
import java.lang.reflect.Method;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for DefaultMcpDispatcher. */
class DefaultMcpDispatcherTest {

  private DefaultMcpDispatcher dispatcher;
  private DefaultMcpDefinitionRegistry registry;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    registry = new DefaultMcpDefinitionRegistry();
    objectMapper = new ObjectMapper();
    var validator = new DefaultMcpInputValidator();
    var serializer = new DefaultMcpOutputSerializer(objectMapper);
    dispatcher = new DefaultMcpDispatcher(registry, objectMapper, validator, serializer);
  }

  @Test
  void testValidToolInvocationReturnsResult() throws NoSuchMethodException {
    TestService bean = new TestService();
    Method method = TestService.class.getMethod("add", int.class, int.class);
    McpTool annotation = org.springframework.core.annotation.AnnotationUtils.findAnnotation(
        method, McpTool.class);

    JsonSchemaGenerator schemaGenerator = new JsonSchemaGenerator();
    DefaultMcpMappingEngine mappingEngine = new DefaultMcpMappingEngine(schemaGenerator);
    McpToolDefinition toolDef = mappingEngine.toToolDefinition(bean, method, annotation);
    registry.register(toolDef);

    Map<String, Object> arguments = Map.of("a", 5, "b", 3);
    McpDispatcher.McpToolResult result = dispatcher.dispatchTool("add", arguments);

    assertFalse(result.isError());
    assertEquals(1, result.content().size());
    assertEquals("text", result.content().get(0).type());
    assertEquals("8", result.content().get(0).text());
  }

  @Test
  void testUnknownToolNameReturnsError() {
    Map<String, Object> arguments = Map.of("name", "test");
    McpDispatcher.McpToolResult result = dispatcher.dispatchTool("unknown_tool", arguments);

    assertTrue(result.isError());
    assertEquals(1, result.content().size());
    assertTrue(result.content().get(0).text().contains("not found"));
  }

  @Test
  void testMethodThrowingExceptionReturnsError() throws NoSuchMethodException {
    TestService bean = new TestService();
    Method method = TestService.class.getMethod("throwException");
    McpTool annotation = org.springframework.core.annotation.AnnotationUtils.findAnnotation(
        method, McpTool.class);

    JsonSchemaGenerator schemaGenerator = new JsonSchemaGenerator();
    DefaultMcpMappingEngine mappingEngine = new DefaultMcpMappingEngine(schemaGenerator);
    McpToolDefinition toolDef = mappingEngine.toToolDefinition(bean, method, annotation);
    registry.register(toolDef);

    McpDispatcher.McpToolResult result = dispatcher.dispatchTool("throw_exception", Map.of());

    assertTrue(result.isError());
    assertTrue(result.content().get(0).text().contains("Error invoking tool"));
  }

  @Test
  void testMethodWithNoParametersInvokesCorrectly() throws NoSuchMethodException {
    TestService bean = new TestService();
    Method method = TestService.class.getMethod("getValue");
    McpTool annotation = org.springframework.core.annotation.AnnotationUtils.findAnnotation(
        method, McpTool.class);

    JsonSchemaGenerator schemaGenerator = new JsonSchemaGenerator();
    DefaultMcpMappingEngine mappingEngine = new DefaultMcpMappingEngine(schemaGenerator);
    McpToolDefinition toolDef = mappingEngine.toToolDefinition(bean, method, annotation);
    registry.register(toolDef);

    McpDispatcher.McpToolResult result = dispatcher.dispatchTool("get_value", Map.of());

    assertFalse(result.isError());
    assertEquals("text", result.content().get(0).type());
    assertEquals("42", result.content().get(0).text());
  }

  @Test
  void testVoidReturnTypeProducesEmptyContent() throws NoSuchMethodException {
    TestService bean = new TestService();
    Method method = TestService.class.getMethod("doNothing");
    McpTool annotation = org.springframework.core.annotation.AnnotationUtils.findAnnotation(
        method, McpTool.class);

    JsonSchemaGenerator schemaGenerator = new JsonSchemaGenerator();
    DefaultMcpMappingEngine mappingEngine = new DefaultMcpMappingEngine(schemaGenerator);
    McpToolDefinition toolDef = mappingEngine.toToolDefinition(bean, method, annotation);
    registry.register(toolDef);

    McpDispatcher.McpToolResult result = dispatcher.dispatchTool("do_nothing", Map.of());

    assertFalse(result.isError());
    assertEquals(1, result.content().size());
    assertEquals("null", result.content().get(0).text());
  }

  @Test
  void testMethodResultSerializedToJson() throws NoSuchMethodException {
    TestService bean = new TestService();
    Method method = TestService.class.getMethod("getObject");
    McpTool annotation = org.springframework.core.annotation.AnnotationUtils.findAnnotation(
        method, McpTool.class);

    JsonSchemaGenerator schemaGenerator = new JsonSchemaGenerator();
    DefaultMcpMappingEngine mappingEngine = new DefaultMcpMappingEngine(schemaGenerator);
    McpToolDefinition toolDef = mappingEngine.toToolDefinition(bean, method, annotation);
    registry.register(toolDef);

    McpDispatcher.McpToolResult result = dispatcher.dispatchTool("get_object", Map.of());

    assertFalse(result.isError());
    String content = result.content().get(0).text();
    assertTrue(content.contains("\"name\""));
    assertTrue(content.contains("\"test\""));
  }

  // Test service
  public static class TestService {
    @McpTool
    public int add(int a, int b) {
      return a + b;
    }

    @McpTool
    public void throwException() {
      throw new RuntimeException("Test error");
    }

    @McpTool
    public int getValue() {
      return 42;
    }

    @McpTool
    public void doNothing() {
      // Intentionally empty
    }

    @McpTool
    public TestObject getObject() {
      return new TestObject("test");
    }
  }

  public static class TestObject {
    public String name;

    public TestObject(String name) {
      this.name = name;
    }

    // For Jackson deserialization
    public TestObject() {
      this.name = "";
    }
  }
}

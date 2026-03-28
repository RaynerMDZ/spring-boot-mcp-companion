package com.raynermendez.spring_boot_mcp_companion.mapper;

import static org.junit.jupiter.api.Assertions.*;

import com.raynermendez.spring_boot_mcp_companion.annotation.McpInput;
import com.raynermendez.spring_boot_mcp_companion.annotation.McpTool;
import com.raynermendez.spring_boot_mcp_companion.model.McpParameterDefinition;
import com.raynermendez.spring_boot_mcp_companion.model.McpToolDefinition;
import jakarta.validation.constraints.Size;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for DefaultMcpMappingEngine. */
class DefaultMcpMappingEngineTest {

  private DefaultMcpMappingEngine mappingEngine;

  @BeforeEach
  void setUp() {
    JsonSchemaGenerator schemaGenerator = new JsonSchemaGenerator();
    mappingEngine = new DefaultMcpMappingEngine(schemaGenerator);
  }

  @Test
  void testExplicitToolName() throws NoSuchMethodException {
    TestService bean = new TestService();
    Method method = TestService.class.getMethod("methodWithExplicitName");
    McpTool annotation = method.getAnnotation(McpTool.class);

    McpToolDefinition definition = mappingEngine.toToolDefinition(bean, method, annotation);

    assertEquals("custom_name", definition.name());
  }

  @Test
  void testBlankNameDerivesFromMethod() throws NoSuchMethodException {
    TestService bean = new TestService();
    Method method = TestService.class.getMethod("getOrderInfo");
    McpTool annotation = method.getAnnotation(McpTool.class);

    McpToolDefinition definition = mappingEngine.toToolDefinition(bean, method, annotation);

    assertEquals("get_order_info", definition.name());
  }

  @Test
  void testMethodWithMcpInputParameter() throws NoSuchMethodException {
    TestService bean = new TestService();
    Method method = TestService.class.getMethod("fetchData", String.class);
    McpTool annotation = method.getAnnotation(McpTool.class);

    McpToolDefinition definition = mappingEngine.toToolDefinition(bean, method, annotation);

    assertEquals(1, definition.parameters().size());
    McpParameterDefinition param = definition.parameters().get(0);
    assertEquals("query", param.name());
    assertEquals("search query", param.description());
  }

  @Test
  void testParameterWithSizeConstraint() throws NoSuchMethodException {
    TestService bean = new TestService();
    Method method = TestService.class.getMethod("searchItems", String.class);
    McpTool annotation = method.getAnnotation(McpTool.class);

    McpToolDefinition definition = mappingEngine.toToolDefinition(bean, method, annotation);

    assertEquals(1, definition.parameters().size());
    McpParameterDefinition param = definition.parameters().get(0);
    Map<String, Object> jsonSchema = param.jsonSchema();

    assertTrue(jsonSchema.containsKey("minLength"));
    assertEquals(1, jsonSchema.get("minLength"));
    assertTrue(jsonSchema.containsKey("maxLength"));
    assertEquals(50, jsonSchema.get("maxLength"));
  }

  @Test
  void testVoidReturnType() throws NoSuchMethodException {
    TestService bean = new TestService();
    Method method = TestService.class.getMethod("voidMethod");
    McpTool annotation = method.getAnnotation(McpTool.class);

    McpToolDefinition definition = mappingEngine.toToolDefinition(bean, method, annotation);

    assertNotNull(definition);
    assertEquals("void_method", definition.name());
    assertEquals(0, definition.parameters().size());
  }

  @Test
  void testMethodWithNoParameters() throws NoSuchMethodException {
    TestService bean = new TestService();
    Method method = TestService.class.getMethod("noParams");
    McpTool annotation = method.getAnnotation(McpTool.class);

    McpToolDefinition definition = mappingEngine.toToolDefinition(bean, method, annotation);

    assertEquals(0, definition.parameters().size());
    Map<String, Object> inputSchema = definition.inputSchema();
    assertFalse(inputSchema.containsKey("required"));
  }

  @Test
  void testMethodWithMultipleParameters() throws NoSuchMethodException {
    TestService bean = new TestService();
    Method method = TestService.class.getMethod("multiParam", String.class, int.class);
    McpTool annotation = method.getAnnotation(McpTool.class);

    McpToolDefinition definition = mappingEngine.toToolDefinition(bean, method, annotation);

    assertEquals(2, definition.parameters().size());
    List<McpParameterDefinition> params = definition.parameters();
    assertEquals("name", params.get(0).name());
    assertEquals("count", params.get(1).name());

    // Check input schema properties
    Map<String, Object> inputSchema = definition.inputSchema();
    assertTrue(inputSchema.containsKey("properties"));
    Map<String, Object> props = (Map<String, Object>) inputSchema.get("properties");
    assertEquals(2, props.size());
  }

  @Test
  void testDescriptionFromAnnotation() throws NoSuchMethodException {
    TestService bean = new TestService();
    Method method = TestService.class.getMethod("methodWithExplicitName");
    McpTool annotation = method.getAnnotation(McpTool.class);

    McpToolDefinition definition = mappingEngine.toToolDefinition(bean, method, annotation);

    assertEquals("A test method", definition.description());
  }

  @Test
  void testTagsFromAnnotation() throws NoSuchMethodException {
    TestService bean = new TestService();
    Method method = TestService.class.getMethod("getOrderInfo");
    McpTool annotation = method.getAnnotation(McpTool.class);

    McpToolDefinition definition = mappingEngine.toToolDefinition(bean, method, annotation);

    assertEquals(2, definition.tags().length);
    assertEquals("order", definition.tags()[0]);
    assertEquals("info", definition.tags()[1]);
  }

  // Test service class with various annotated methods
  public static class TestService {

    @McpTool(name = "custom_name", description = "A test method")
    public void methodWithExplicitName() {}

    @McpTool(tags = {"order", "info"})
    public void getOrderInfo() {}

    @McpTool
    public void fetchData(@McpInput(name = "query", description = "search query") String q) {}

    @McpTool
    public void searchItems(@Size(min = 1, max = 50) String searchTerm) {}

    @McpTool
    public void voidMethod() {}

    @McpTool
    public void noParams() {}

    @McpTool
    public void multiParam(String name, int count) {}
  }
}

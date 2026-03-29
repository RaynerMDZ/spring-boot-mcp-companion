package com.raynermendez.spring_boot_mcp_companion.registry;

import static org.junit.jupiter.api.Assertions.*;

import com.raynermendez.spring_boot_mcp_companion.model.McpParameterDefinition;
import com.raynermendez.spring_boot_mcp_companion.model.McpPromptDefinition;
import com.raynermendez.spring_boot_mcp_companion.model.McpResourceDefinition;
import com.raynermendez.spring_boot_mcp_companion.model.McpToolDefinition;
import com.raynermendez.spring_boot_mcp_companion.model.MethodHandlerRef;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for DefaultMcpDefinitionRegistry. */
class DefaultMcpDefinitionRegistryTest {

  private DefaultMcpDefinitionRegistry registry;
  private MethodHandlerRef mockHandler;

  @BeforeEach
  void setUp() throws NoSuchMethodException {
    registry = new DefaultMcpDefinitionRegistry();
    // Create a mock handler using a real Method for testing
    Object bean = new Object();
    Method method = Object.class.getMethod("toString");
    mockHandler = new MethodHandlerRef(bean, method, "testBean");
  }

  @Test
  void testRegistryInitialStateIsEmpty() {
    assertEquals(McpDefinitionRegistry.RegistryState.EMPTY, registry.getState());
  }

  @Test
  void testRegisterToolTransitionsStateToBuilding() {
    McpToolDefinition tool =
        new McpToolDefinition(
            "testTool",
            "Test Tool",
            "A test tool",
            new String[0],
            List.of(),
            Collections.emptyMap(),
            mockHandler);

    registry.register(tool);

    assertEquals(McpDefinitionRegistry.RegistryState.BUILDING, registry.getState());
  }

  @Test
  void testDuplicateToolNameThrowsException() {
    McpToolDefinition tool1 =
        new McpToolDefinition(
            "sameName",
            "Same Name Tool",
            "First tool",
            new String[0],
            List.of(),
            Collections.emptyMap(),
            mockHandler);
    McpToolDefinition tool2 =
        new McpToolDefinition(
            "sameName",
            "Same Name Tool",
            "Second tool",
            new String[0],
            List.of(),
            Collections.emptyMap(),
            mockHandler);

    registry.register(tool1);

    IllegalStateException exception =
        assertThrows(IllegalStateException.class, () -> registry.register(tool2));
    assertTrue(exception.getMessage().contains("Duplicate tool name: sameName"));
  }

  @Test
  void testRegisterResourceWithDuplicateUriThrowsException() {
    McpResourceDefinition resource1 =
        new McpResourceDefinition(
            "file:///path", "Resource 1", "Description 1", "text/plain", mockHandler);
    McpResourceDefinition resource2 =
        new McpResourceDefinition(
            "file:///path", "Resource 2", "Description 2", "text/plain", mockHandler);

    registry.register(resource1);

    IllegalStateException exception =
        assertThrows(IllegalStateException.class, () -> registry.register(resource2));
    assertTrue(exception.getMessage().contains("Duplicate resource URI: file:///path"));
  }

  @Test
  void testRegisterPromptWithDuplicateNameThrowsException() {
    McpPromptDefinition prompt1 =
        new McpPromptDefinition("sameName", "Same Name Prompt", "First prompt", List.of(), mockHandler);
    McpPromptDefinition prompt2 =
        new McpPromptDefinition("sameName", "Same Name Prompt", "Second prompt", List.of(), mockHandler);

    registry.register(prompt1);

    IllegalStateException exception =
        assertThrows(IllegalStateException.class, () -> registry.register(prompt2));
    assertTrue(exception.getMessage().contains("Duplicate prompt name: sameName"));
  }

  @Test
  void testLockTransitionsStateToReady() {
    registry.lock();

    assertEquals(McpDefinitionRegistry.RegistryState.READY, registry.getState());
  }

  @Test
  void testRegisterAfterLockThrowsException() {
    registry.lock();

    McpToolDefinition tool =
        new McpToolDefinition(
            "toolAfterLock",
            "Tool After Lock",
            "A tool after lock",
            new String[0],
            List.of(),
            Collections.emptyMap(),
            mockHandler);

    IllegalStateException exception =
        assertThrows(IllegalStateException.class, () -> registry.register(tool));
    assertTrue(exception.getMessage().contains("registry is locked"));
  }

  @Test
  void testGetToolsReturnsRegisteredTools() {
    McpToolDefinition tool =
        new McpToolDefinition(
            "myTool",
            "My Tool",
            "A test tool",
            new String[0],
            List.of(),
            Collections.emptyMap(),
            mockHandler);

    registry.register(tool);

    List<McpToolDefinition> tools = registry.getTools();
    assertEquals(1, tools.size());
    assertEquals("myTool", tools.get(0).name());
  }

  @Test
  void testGetResourcesReturnsRegisteredResources() {
    McpResourceDefinition resource =
        new McpResourceDefinition(
            "file:///test", "Test Resource", "A test resource", "text/plain", mockHandler);

    registry.register(resource);

    List<McpResourceDefinition> resources = registry.getResources();
    assertEquals(1, resources.size());
    assertEquals("file:///test", resources.get(0).uri());
  }

  @Test
  void testGetPromptsReturnsRegisteredPrompts() {
    McpPromptDefinition prompt =
        new McpPromptDefinition("myPrompt", "My Prompt", "A test prompt", List.of(), mockHandler);

    registry.register(prompt);

    List<McpPromptDefinition> prompts = registry.getPrompts();
    assertEquals(1, prompts.size());
    assertEquals("myPrompt", prompts.get(0).name());
  }

  @Test
  void testGettersReturnUnmodifiableCollections() {
    McpToolDefinition tool =
        new McpToolDefinition(
            "tool1",
            "Tool 1",
            "A test tool",
            new String[0],
            List.of(),
            Collections.emptyMap(),
            mockHandler);
    registry.register(tool);

    List<McpToolDefinition> tools = registry.getTools();

    assertThrows(
        UnsupportedOperationException.class,
        () -> tools.add(tool),
        "getTools() should return an unmodifiable list");
  }

  @Test
  void testLockIsIdempotent() {
    registry.lock();
    assertEquals(McpDefinitionRegistry.RegistryState.READY, registry.getState());

    registry.lock();
    assertEquals(McpDefinitionRegistry.RegistryState.READY, registry.getState());
  }
}

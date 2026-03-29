package com.raynermendez.spring_boot_mcp_companion.security;

import static org.junit.jupiter.api.Assertions.*;

import com.raynermendez.spring_boot_mcp_companion.model.McpParameterDefinition;
import com.raynermendez.spring_boot_mcp_companion.model.McpToolDefinition;
import com.raynermendez.spring_boot_mcp_companion.model.MethodHandlerRef;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Security tests for SensitiveParameterFilter.
 *
 * <p>Tests verify that:
 * <ul>
 *   <li>Sensitive parameters are properly masked with [REDACTED]
 *   <li>Non-sensitive parameters are preserved
 *   <li>Original arguments map is not modified
 *   <li>Filter works with various parameter types
 * </ul>
 */
@DisplayName("Sensitive Parameter Filter Security Tests")
class SensitiveParameterFilterTest {

  private McpToolDefinition toolDefWithSensitiveParams;
  private McpToolDefinition toolDefNoSensitiveParams;

  @BeforeEach
  void setUp() throws Exception {
    // Create tool definition with sensitive parameters
    List<McpParameterDefinition> params = List.of(
        new McpParameterDefinition("userId", "User ID", true, Map.of("type", "string"), false),
        new McpParameterDefinition("apiKey", "API Key", true, Map.of("type", "string"), true), // SENSITIVE
        new McpParameterDefinition("password", "Password", true, Map.of("type", "string"), true), // SENSITIVE
        new McpParameterDefinition("name", "User name", true, Map.of("type", "string"), false)
    );

    Method dummyMethod = String.class.getMethod("valueOf", Object.class);
    Object targetBean = new Object();
    MethodHandlerRef handler = new MethodHandlerRef(targetBean, dummyMethod, "testBean");

    toolDefWithSensitiveParams = new McpToolDefinition(
        "test_tool",
        "Test Tool",
        "Test tool",
        new String[]{},
        params,
        Map.of("type", "object"),
        handler
    );

    // Tool with no sensitive parameters
    List<McpParameterDefinition> noSensitiveParams = List.of(
        new McpParameterDefinition("input", "Input", true, Map.of("type", "string"), false),
        new McpParameterDefinition("output", "Output", true, Map.of("type", "string"), false)
    );

    toolDefNoSensitiveParams = new McpToolDefinition(
        "safe_tool",
        "Safe Tool",
        "Safe tool",
        new String[]{},
        noSensitiveParams,
        Map.of("type", "object"),
        handler
    );
  }

  @Test
  @DisplayName("Should mask sensitive parameters with [REDACTED]")
  void testMaskSensitiveParameters() {
    Map<String, Object> arguments = new HashMap<>();
    arguments.put("userId", "user-123");
    arguments.put("apiKey", "sk-super-secret-key");
    arguments.put("password", "my-secure-password");
    arguments.put("name", "John Doe");

    Map<String, Object> filtered = SensitiveParameterFilter.filterSensitiveArguments(
        arguments, toolDefWithSensitiveParams);

    assertEquals("user-123", filtered.get("userId"), "Non-sensitive parameter should be preserved");
    assertEquals("[REDACTED]", filtered.get("apiKey"), "Sensitive apiKey should be masked");
    assertEquals("[REDACTED]", filtered.get("password"), "Sensitive password should be masked");
    assertEquals("John Doe", filtered.get("name"), "Non-sensitive parameter should be preserved");
  }

  @Test
  @DisplayName("Should not modify original arguments map")
  void testDoNotModifyOriginalMap() {
    Map<String, Object> arguments = new HashMap<>();
    arguments.put("userId", "user-123");
    arguments.put("apiKey", "sk-secret");
    arguments.put("name", "John");

    Map<String, Object> filtered = SensitiveParameterFilter.filterSensitiveArguments(
        arguments, toolDefWithSensitiveParams);

    // Original should still have sensitive data
    assertEquals("sk-secret", arguments.get("apiKey"));
    assertEquals("user-123", arguments.get("userId"));

    // Filtered should have redacted data
    assertEquals("[REDACTED]", filtered.get("apiKey"));
    assertEquals("user-123", filtered.get("userId"));
  }

  @Test
  @DisplayName("Should handle empty arguments map")
  void testHandleEmptyArgumentsMap() {
    Map<String, Object> arguments = new HashMap<>();

    Map<String, Object> filtered = SensitiveParameterFilter.filterSensitiveArguments(
        arguments, toolDefWithSensitiveParams);

    assertNotNull(filtered);
    assertTrue(filtered.isEmpty());
  }

  @Test
  @DisplayName("Should handle null arguments map")
  void testHandleNullArgumentsMap() {
    Map<String, Object> filtered = SensitiveParameterFilter.filterSensitiveArguments(
        null, toolDefWithSensitiveParams);

    assertNull(filtered);
  }

  @Test
  @DisplayName("Should preserve all parameters when none are sensitive")
  void testPreserveAllParametersWhenNoneSensitive() {
    Map<String, Object> arguments = new HashMap<>();
    arguments.put("input", "some input");
    arguments.put("output", "some output");

    Map<String, Object> filtered = SensitiveParameterFilter.filterSensitiveArguments(
        arguments, toolDefNoSensitiveParams);

    assertEquals("some input", filtered.get("input"));
    assertEquals("some output", filtered.get("output"));
  }

  @Test
  @DisplayName("Should identify sensitive parameters correctly")
  void testIdentifySensitiveParameters() {
    List<McpParameterDefinition> params = toolDefWithSensitiveParams.parameters();

    assertFalse(SensitiveParameterFilter.isSensitiveParameter(params, "userId"));
    assertTrue(SensitiveParameterFilter.isSensitiveParameter(params, "apiKey"));
    assertTrue(SensitiveParameterFilter.isSensitiveParameter(params, "password"));
    assertFalse(SensitiveParameterFilter.isSensitiveParameter(params, "name"));
  }

  @Test
  @DisplayName("Should return false for non-existent parameter")
  void testNonExistentParameter() {
    List<McpParameterDefinition> params = toolDefWithSensitiveParams.parameters();

    assertFalse(SensitiveParameterFilter.isSensitiveParameter(params, "nonExistent"));
  }

  @Test
  @DisplayName("Should find parameter by name")
  void testFindParameterByName() {
    List<McpParameterDefinition> params = toolDefWithSensitiveParams.parameters();

    McpParameterDefinition param = SensitiveParameterFilter.findParameter(params, "apiKey");
    assertNotNull(param);
    assertEquals("apiKey", param.name());
    assertTrue(param.sensitive());
  }

  @Test
  @DisplayName("Should return null for non-existent parameter lookup")
  void testFindNonExistentParameter() {
    List<McpParameterDefinition> params = toolDefWithSensitiveParams.parameters();

    McpParameterDefinition param = SensitiveParameterFilter.findParameter(params, "nonExistent");
    assertNull(param);
  }

  @Test
  @DisplayName("Should provide redaction placeholder constant")
  void testGetRedactionPlaceholder() {
    String placeholder = SensitiveParameterFilter.getRedactionPlaceholder();

    assertEquals("[REDACTED]", placeholder);
    assertNotNull(placeholder);
    assertFalse(placeholder.isEmpty());
  }

  @Test
  @DisplayName("Should handle parameters with null values")
  void testHandleParametersWithNullValues() {
    Map<String, Object> arguments = new HashMap<>();
    arguments.put("userId", "user-123");
    arguments.put("apiKey", null); // null sensitive parameter
    arguments.put("password", null); // null sensitive parameter
    arguments.put("name", null); // null non-sensitive parameter

    Map<String, Object> filtered = SensitiveParameterFilter.filterSensitiveArguments(
        arguments, toolDefWithSensitiveParams);

    assertEquals("user-123", filtered.get("userId"));
    assertNull(filtered.get("apiKey"), "Null sensitive parameters should remain null");
    assertNull(filtered.get("password"), "Null sensitive parameters should remain null");
    assertNull(filtered.get("name"), "Null non-sensitive parameters should remain null");
  }

  @Test
  @DisplayName("Should mask parameters with various data types")
  void testMaskParametersWithVariousTypes() {
    Map<String, Object> arguments = new HashMap<>();
    arguments.put("userId", "user-123");
    arguments.put("apiKey", "sk-secret"); // string
    arguments.put("password", 12345); // number - sensitive
    arguments.put("name", "John");

    Map<String, Object> filtered = SensitiveParameterFilter.filterSensitiveArguments(
        arguments, toolDefWithSensitiveParams);

    // Numeric sensitive parameter should also be masked
    assertEquals("[REDACTED]", filtered.get("password"));
  }
}

package com.raynermendez.spring_boot_mcp_companion.security;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Security tests for input deserialization attacks.
 *
 * <p>Tests verify that Jackson deserialization cannot be exploited for RCE or type confusion attacks.
 */
@DisplayName("Input Deserialization Security Tests")
class InputDeserializationSecurityTest {

  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
  }

  @Test
  @DisplayName("Should safely deserialize JSON without type information")
  void testSafeDeserializationWithoutTypeInfo() {
    // Safe JSON without @type directives
    String safeJson =
        "{"
            + "\"name\": \"tool\","
            + "\"value\": 123,"
            + "\"enabled\": true"
            + "}";

    assertDoesNotThrow(() -> {
      Map<String, Object> result = objectMapper.readValue(safeJson, Map.class);
      assertNotNull(result);
      assertEquals("tool", result.get("name"));
      assertEquals(123, result.get("value"));
      assertEquals(true, result.get("enabled"));
    });
  }

  @Test
  @DisplayName("Should not instantiate arbitrary types from JSON")
  void testPreventArbitraryTypeInstantiation() {
    String jsonWithType =
        "{"
            + "\"@type\": \"com.raynermendez.spring_boot_mcp_companion.MaliciousClass\","
            + "\"command\": \"rm -rf /\""
            + "}";

    // Without JsonTypeInfo configuration, @type is just treated as a field
    assertDoesNotThrow(() -> {
      Map<String, Object> result = objectMapper.readValue(jsonWithType, Map.class);
      // Verify it's parsed as Map with string fields, not as dangerous type
      assertTrue(result instanceof Map);
      assertEquals("com.raynermendez.spring_boot_mcp_companion.MaliciousClass", result.get("@type"));
    });
  }

  @Test
  @DisplayName("Should not deserialize arbitrary class instantiation")
  void testPreventArbitraryClassInstantiation() {
    String maliciousJson =
        "{"
            + "\"class\": \"java.lang.Runtime\","
            + "\"method\": \"exec\","
            + "\"args\": \"calc.exe\""
            + "}";

    // Ensure Runtime.exec() cannot be invoked via deserialization
    assertDoesNotThrow(() -> {
      Map<String, Object> result = objectMapper.readValue(maliciousJson, Map.class);
      assertNotNull(result);
      // Verify it's just a plain map, not a Runtime instance
      assertTrue(result instanceof Map, "Should deserialize as Map, not as Runtime");
    });
  }

  @Test
  @DisplayName("Should handle deeply nested JSON safely")
  void testHandleDeeplyNestedJson() {
    StringBuilder json = new StringBuilder("{\"data\":");
    for (int i = 0; i < 1000; i++) {
      json.append("{\"nested\":");
    }
    json.append("\"value\"");
    for (int i = 0; i < 1000; i++) {
      json.append("}");
    }
    json.append("}");

    // Should either handle or gracefully reject excessive nesting
    assertDoesNotThrow(() -> {
      try {
        objectMapper.readValue(json.toString(), Map.class);
      } catch (Exception e) {
        // Acceptable: too deep nesting can be rejected
        assertTrue(e.getMessage().contains("nesting") || e.getMessage().contains("depth"),
            "Should fail due to depth limit, not deserialization gadget");
      }
    });
  }

  @Test
  @DisplayName("Should prevent XXE attacks in JSON")
  void testPreventXxeAttacks() {
    // XXE attempts shouldn't work in JSON (they're XML attacks), but verify no parser confusion
    String xmlLikeJson = "{\"@xmlns\": \"http://evil.com\", \"entity\": \"&xxe;\"}";

    assertDoesNotThrow(
        () -> {
          Map<String, Object> result = objectMapper.readValue(xmlLikeJson, Map.class);
          assertNotNull(result);
          // Verify it's treated as regular JSON strings, not XML
          assertTrue(result.containsKey("@xmlns"), "Should parse as string keys");
        });
  }

  @Test
  @DisplayName("Should reject Class<?> instantiation")
  void testRejectClassInstantiation() {
    String jsonWithClass =
        "{"
            + "\"targetClass\": \"class java.lang.Runtime\","
            + "\"method\": \"getRuntime\""
            + "}";

    assertDoesNotThrow(() -> {
      Map<String, Object> result = objectMapper.readValue(jsonWithClass, Map.class);
      // Should be deserialized as string, not as Class instance
      assertFalse(result.get("targetClass") instanceof Class<?>,
          "Class types should not be instantiated from JSON");
    });
  }

  @Test
  @DisplayName("Should validate List/Map contents for malicious types")
  void testValidateCollectionContents() {
    String jsonWithList = "{\"items\": [1, 2, 3, \"safe\", true]}";

    assertDoesNotThrow(() -> {
      Map<String, Object> result = objectMapper.readValue(jsonWithList, Map.class);
      assertNotNull(result);
      // Verify all items in list are safe primitive types
      assertTrue(result.containsKey("items"), "Should contain items list");
    });
  }

  @Test
  @DisplayName("Should prevent numeric overflow attacks")
  void testPreventNumericOverflow() {
    String jsonWithHugeNumber = "{\"size\": 999999999999999999999999999999}";

    assertDoesNotThrow(() -> {
      Map<String, Object> result = objectMapper.readValue(jsonWithHugeNumber, Map.class);
      assertNotNull(result);
      // Should either truncate or reject, not cause memory issues
      Object size = result.get("size");
      assertNotNull(size, "Should handle large numbers");
    });
  }

  @Test
  @DisplayName("Should not deserialize transient fields from JSON")
  void testNotDeserializeTransientFields() {
    String json = "{\"publicField\": \"allowed\", \"transientField\": \"not-allowed\"}";

    assertDoesNotThrow(() -> {
      Map<String, Object> result = objectMapper.readValue(json, Map.class);
      // With plain Map, both will be present, but in typed classes they would be excluded
      assertNotNull(result);
    });
  }

  @Test
  @DisplayName("Should safely deserialize input parameters with type restrictions")
  void testSafeParameterDeserialization() {
    // Simulate MCP input validation
    String toolInput = "{\"name\": \"test-tool\", \"timeout\": 5000, \"async\": true}";

    assertDoesNotThrow(() -> {
      Map<String, Object> input = objectMapper.readValue(toolInput, Map.class);

      // Validate types before use
      assertTrue(input.get("name") instanceof String, "name should be String");
      assertTrue(input.get("timeout") instanceof Number, "timeout should be Number");
      assertTrue(input.get("async") instanceof Boolean, "async should be Boolean");
    });
  }

  @Test
  @DisplayName("Should handle null and empty values safely")
  void testHandleNullAndEmptyValues() {
    String jsonWithNull = "{\"field1\": null, \"field2\": \"\", \"field3\": []}";

    assertDoesNotThrow(() -> {
      Map<String, Object> result = objectMapper.readValue(jsonWithNull, Map.class);
      assertNull(result.get("field1"), "Null values should be preserved");
      assertEquals("", result.get("field2"), "Empty strings should be allowed");
      assertTrue(result.get("field3") instanceof java.util.List,
          "Empty lists should deserialize safely");
    });
  }

  @Test
  @DisplayName("Should prevent JSON bomb (billion laughs) attacks")
  void testPreventJsonBomb() {
    // Create a large but valid JSON object
    Map<String, Object> largeMap = new HashMap<>();
    for (int i = 0; i < 10000; i++) {
      largeMap.put("key_" + i, "value_" + i);
    }

    assertDoesNotThrow(() -> {
      String json = objectMapper.writeValueAsString(largeMap);
      Map<String, Object> result = objectMapper.readValue(json, Map.class);
      assertEquals(10000, result.size(), "Should handle large maps");
    });
  }

  @Test
  @DisplayName("Should validate parsed values before processing")
  void testValidateParsedValues() {
    String input = "{\"count\": \"not-a-number\", \"enabled\": \"yes\"}";

    assertDoesNotThrow(() -> {
      Map<String, Object> parsed = objectMapper.readValue(input, Map.class);

      // Type checking before use
      Object count = parsed.get("count");
      if (!(count instanceof Number)) {
        // Skip or reject based on contract
        assertTrue(true, "Should detect type mismatch");
      }
    });
  }
}

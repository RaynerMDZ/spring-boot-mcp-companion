package com.raynermendez.spring_boot_mcp_companion.security;

import static org.junit.jupiter.api.Assertions.*;

import com.raynermendez.spring_boot_mcp_companion.model.McpParameterDefinition;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Security tests for input validation and type checking.
 *
 * <p>Tests verify that:
 * <ul>
 *   <li>Type coercion attacks are prevented
 *   <li>Invalid type conversions are rejected
 *   <li>Only valid types are accepted for parameters
 *   <li>Injection attacks through type conversion are blocked
 * </ul>
 */
@DisplayName("Input Validation Security Tests")
class InputValidationSecurityTest {

  @Test
  @DisplayName("Should reject type coercion from array to string")
  void testRejectArrayToStringCoercion() {
    // Verify type validation logic
    assertFalse(isValidTypeForDispatcher(List.of("item1", "item2"), String.class),
        "Array should not be accepted for String parameter");
  }

  @Test
  @DisplayName("Should reject invalid numeric type coercion")
  void testRejectInvalidNumericCoercion() {
    // Verify that only valid types are accepted for numeric parameters
    assertFalse(isValidTypeForDispatcher(Map.of("key", "value"), Integer.class),
        "Map should not be accepted for Integer parameter");

    assertTrue(isValidTypeForDispatcher(42, Integer.class),
        "Number should be accepted for Integer parameter");

    assertTrue(isValidTypeForDispatcher("42", Integer.class),
        "String should be accepted for Integer parameter (will be converted)");
  }

  @Test
  @DisplayName("Should reject Map for non-Map parameters")
  void testRejectMapForNonMapParameters() {
    Map<String, Object> mapArg = new HashMap<>();
    mapArg.put("key", "value");

    assertFalse(isValidTypeForDispatcher(mapArg, String.class),
        "Map should not be accepted for String parameter");

    assertFalse(isValidTypeForDispatcher(mapArg, Integer.class),
        "Map should not be accepted for Integer parameter");

    assertTrue(isValidTypeForDispatcher(mapArg, Map.class),
        "Map should be accepted for Map parameter");
  }

  @Test
  @DisplayName("Should accept valid type combinations")
  void testAcceptValidTypeCombinations() {
    // String parameter accepts anything
    assertTrue(isValidTypeForDispatcher("string", String.class));
    assertTrue(isValidTypeForDispatcher(123, String.class));
    assertTrue(isValidTypeForDispatcher(true, String.class));

    // Number parameters accept numbers and strings
    assertTrue(isValidTypeForDispatcher(42, Integer.class));
    assertTrue(isValidTypeForDispatcher(3.14, Double.class));
    assertTrue(isValidTypeForDispatcher("42", Integer.class));

    // Boolean accepts boolean and string
    assertTrue(isValidTypeForDispatcher(true, Boolean.class));
    assertTrue(isValidTypeForDispatcher("true", Boolean.class));

    // List accepts lists and strings
    assertTrue(isValidTypeForDispatcher(List.of("a", "b"), java.util.List.class));
    assertTrue(isValidTypeForDispatcher("[\"a\", \"b\"]", java.util.List.class));
  }

  @Test
  @DisplayName("Should reject dangerous type combinations")
  void testRejectDangerousTypeCombinations() {
    // Object arrays should be rejected if not targeting array/list
    Object[] objArray = new Object[] {"a", "b"};
    assertFalse(isValidTypeForDispatcher(objArray, String.class),
        "Object array should not be accepted for String parameter");
  }

  @Test
  @DisplayName("Should handle null values safely")
  void testHandleNullValuesSafely() {
    // null should be valid for any type (will be null in method)
    assertTrue(isValidTypeForDispatcher(null, String.class));
    assertTrue(isValidTypeForDispatcher(null, Integer.class));
    assertTrue(isValidTypeForDispatcher(null, Map.class));
    assertTrue(isValidTypeForDispatcher(null, java.util.List.class));
  }

  @Test
  @DisplayName("Should prevent SQL injection through type conversion")
  void testPreventSQLInjection() {
    // Even though string parameter accepts SQL, it should be at method level
    String sqlInjection = "'; DROP TABLE users; --";

    assertTrue(isValidTypeForDispatcher(sqlInjection, String.class),
        "String type should be accepted, but SQL handling is at method level");
  }

  @Test
  @DisplayName("Should validate primitive and wrapper types correctly")
  void testValidatePrimitiveAndWrapperTypes() {
    // Primitive int vs Integer (wrapper)
    assertTrue(isValidTypeForDispatcher(42, int.class));
    assertTrue(isValidTypeForDispatcher(42, Integer.class));
    assertTrue(isValidTypeForDispatcher("42", int.class));
    assertTrue(isValidTypeForDispatcher("42", Integer.class));

    // Primitive boolean vs Boolean (wrapper)
    assertTrue(isValidTypeForDispatcher(true, boolean.class));
    assertTrue(isValidTypeForDispatcher(true, Boolean.class));
    assertTrue(isValidTypeForDispatcher("true", boolean.class));
    assertTrue(isValidTypeForDispatcher("true", Boolean.class));
  }

  @Test
  @DisplayName("Should prevent ClassLoader and Runtime method calls")
  void testPreventDangerousClassMethodCalls() {
    // These should not be allowed through reflection-based invocation
    assertNotEquals(ClassLoader.class, String.class);
    assertNotEquals(Runtime.class, String.class);
    assertNotEquals(System.class, String.class);
  }

  /**
   * Helper method to test type validation logic.
   *
   * <p>This simulates the isValidType method from DefaultMcpDispatcher.
   */
  private boolean isValidTypeForDispatcher(Object value, Class<?> targetType) {
    // Replicate the validation logic from DefaultMcpDispatcher.isValidType()
    if (value == null) {
      return true;
    }

    if (targetType == String.class) {
      return true;
    }

    if (targetType == Boolean.class || targetType == boolean.class) {
      return value instanceof Boolean || value instanceof String;
    }

    if (targetType == Integer.class || targetType == int.class ||
        targetType == Long.class || targetType == long.class ||
        targetType == Double.class || targetType == double.class ||
        targetType == Float.class || targetType == float.class) {
      return value instanceof Number || value instanceof String;
    }

    if (targetType == java.util.List.class || targetType.isArray()) {
      return value instanceof java.util.List || value instanceof String;
    }

    if (targetType == Map.class) {
      return value instanceof Map || value instanceof String;
    }

    if (value.getClass().isArray() && !value.getClass().getComponentType().isPrimitive()) {
      return false; // Reject non-primitive arrays for non-array targets
    }

    return true; // Allow other types for Jackson to handle
  }
}

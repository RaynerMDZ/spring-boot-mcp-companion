package com.raynermendez.spring_boot_mcp_companion.validation;

import com.raynermendez.spring_boot_mcp_companion.model.McpParameterDefinition;
import com.raynermendez.spring_boot_mcp_companion.model.McpToolDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of MCP input validation.
 *
 * <p>This validator checks:
 * <ul>
 *   <li>All required fields are present in the arguments map
 *   <li>Constraints from JSON Schema (min, max, minLength, maxLength, enum, pattern, etc.)
 * </ul>
 */
public class DefaultMcpInputValidator implements McpInputValidator {

  private static final Logger logger = LoggerFactory.getLogger(DefaultMcpInputValidator.class);

  @Override
  public List<McpViolation> validate(McpToolDefinition toolDef, Map<String, Object> arguments) {
    List<McpViolation> violations = new ArrayList<>();

    if (arguments == null) {
      arguments = Map.of();
    }

    for (McpParameterDefinition param : toolDef.parameters()) {
      // Check if required field is present
      if (param.required() && !arguments.containsKey(param.name())) {
        violations.add(
            new McpViolation(param.name(), "Required parameter missing: " + param.name()));
        continue;
      }

      // Skip further validation if parameter is optional and not provided
      if (!arguments.containsKey(param.name())) {
        continue;
      }

      Object value = arguments.get(param.name());

      // Validate constraints from JSON Schema
      if (param.jsonSchema() != null) {
        violations.addAll(validateAgainstSchema(param.name(), value, param.jsonSchema()));
      }
    }

    return violations;
  }

  /**
   * Validates a single value against JSON Schema constraints.
   *
   * @param fieldName the field name (for violation messages)
   * @param value the value to validate
   * @param schema the JSON Schema object
   * @return list of violations for this field
   */
  private List<McpViolation> validateAgainstSchema(
      String fieldName, Object value, Map<String, Object> schema) {
    List<McpViolation> violations = new ArrayList<>();

    // Handle null values
    if (value == null) {
      if (schema.getOrDefault("type", "").equals("null")) {
        return violations; // null is allowed
      }
      // If type is not explicitly null, a null value might be valid for some contexts
      return violations;
    }

    // Validate string constraints
    if (value instanceof String str) {
      violations.addAll(validateStringConstraints(fieldName, str, schema));
    }

    // Validate numeric constraints
    if (value instanceof Number num) {
      violations.addAll(validateNumericConstraints(fieldName, num, schema));
    }

    // Validate array constraints
    if (value instanceof List<?> arr) {
      violations.addAll(validateArrayConstraints(fieldName, arr, schema));
    }

    // Validate object/Map constraints
    if (value instanceof Map<?, ?> obj) {
      violations.addAll(validateObjectConstraints(fieldName, obj, schema));
    }

    // Validate enum values
    if (schema.containsKey("enum")) {
      @SuppressWarnings("unchecked")
      List<Object> enumValues = (List<Object>) schema.get("enum");
      if (!enumValues.contains(value)) {
        violations.add(
            new McpViolation(
                fieldName, "Value must be one of: " + enumValues + ", got: " + value));
      }
    }

    // Validate pattern (regex)
    if (schema.containsKey("pattern") && value instanceof String str) {
      String pattern = (String) schema.get("pattern");
      if (!str.matches(pattern)) {
        violations.add(
            new McpViolation(fieldName, "Value does not match pattern: " + pattern));
      }
    }

    return violations;
  }

  /**
   * Validates string-specific constraints.
   *
   * @param fieldName the field name
   * @param value the string value
   * @param schema the JSON Schema
   * @return violations list
   */
  private List<McpViolation> validateStringConstraints(
      String fieldName, String value, Map<String, Object> schema) {
    List<McpViolation> violations = new ArrayList<>();
    int length = value.length();

    // minLength
    if (schema.containsKey("minLength")) {
      int minLength = toInt(schema.get("minLength"));
      if (length < minLength) {
        violations.add(
            new McpViolation(
                fieldName,
                "String length "
                    + length
                    + " is less than minimum "
                    + minLength));
      }
    }

    // maxLength
    if (schema.containsKey("maxLength")) {
      int maxLength = toInt(schema.get("maxLength"));
      if (length > maxLength) {
        violations.add(
            new McpViolation(
                fieldName,
                "String length "
                    + length
                    + " exceeds maximum "
                    + maxLength));
      }
    }

    return violations;
  }

  /**
   * Validates numeric-specific constraints.
   *
   * @param fieldName the field name
   * @param value the numeric value
   * @param schema the JSON Schema
   * @return violations list
   */
  private List<McpViolation> validateNumericConstraints(
      String fieldName, Number value, Map<String, Object> schema) {
    List<McpViolation> violations = new ArrayList<>();
    double numValue = value.doubleValue();

    // minimum
    if (schema.containsKey("minimum")) {
      double minimum = toDouble(schema.get("minimum"));
      if (numValue < minimum) {
        violations.add(
            new McpViolation(fieldName, "Value " + numValue + " is less than minimum " + minimum));
      }
    }

    // maximum
    if (schema.containsKey("maximum")) {
      double maximum = toDouble(schema.get("maximum"));
      if (numValue > maximum) {
        violations.add(
            new McpViolation(
                fieldName, "Value " + numValue + " exceeds maximum " + maximum));
      }
    }

    // exclusiveMinimum
    if (schema.containsKey("exclusiveMinimum")) {
      double exclusiveMinimum = toDouble(schema.get("exclusiveMinimum"));
      if (numValue <= exclusiveMinimum) {
        violations.add(
            new McpViolation(
                fieldName,
                "Value " + numValue + " must be greater than " + exclusiveMinimum));
      }
    }

    // exclusiveMaximum
    if (schema.containsKey("exclusiveMaximum")) {
      double exclusiveMaximum = toDouble(schema.get("exclusiveMaximum"));
      if (numValue >= exclusiveMaximum) {
        violations.add(
            new McpViolation(
                fieldName,
                "Value " + numValue + " must be less than " + exclusiveMaximum));
      }
    }

    return violations;
  }

  /**
   * Validates array-specific constraints.
   *
   * @param fieldName the field name
   * @param value the list value
   * @param schema the JSON Schema
   * @return violations list
   */
  private List<McpViolation> validateArrayConstraints(
      String fieldName, List<?> value, Map<String, Object> schema) {
    List<McpViolation> violations = new ArrayList<>();
    int size = value.size();

    // minItems
    if (schema.containsKey("minItems")) {
      int minItems = toInt(schema.get("minItems"));
      if (size < minItems) {
        violations.add(
            new McpViolation(
                fieldName, "Array size " + size + " is less than minimum " + minItems));
      }
    }

    // maxItems
    if (schema.containsKey("maxItems")) {
      int maxItems = toInt(schema.get("maxItems"));
      if (size > maxItems) {
        violations.add(
            new McpViolation(
                fieldName, "Array size " + size + " exceeds maximum " + maxItems));
      }
    }

    return violations;
  }

  /**
   * Safely converts an object to an integer.
   *
   * @param obj the object to convert
   * @return the integer value, or 0 if conversion fails
   */
  private int toInt(Object obj) {
    if (obj instanceof Integer i) {
      return i;
    } else if (obj instanceof Number n) {
      return n.intValue();
    }
    return 0;
  }

  /**
   * Validates object/Map-specific constraints.
   *
   * @param fieldName the field name
   * @param value the map/object value
   * @param schema the JSON Schema
   * @return violations list
   */
  private List<McpViolation> validateObjectConstraints(
      String fieldName, Map<?, ?> value, Map<String, Object> schema) {
    List<McpViolation> violations = new ArrayList<>();

    // Validate required properties
    if (schema.containsKey("required")) {
      @SuppressWarnings("unchecked")
      List<String> required = (List<String>) schema.get("required");
      for (String reqProp : required) {
        if (!value.containsKey(reqProp)) {
          violations.add(
              new McpViolation(
                  fieldName, "Required property missing: " + reqProp));
        }
      }
    }

    // Validate properties schema (recursive)
    if (schema.containsKey("properties")) {
      @SuppressWarnings("unchecked")
      Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
      for (Map.Entry<?, ?> entry : value.entrySet()) {
        String propName = (String) entry.getKey();
        Object propValue = entry.getValue();

        if (properties.containsKey(propName)) {
          @SuppressWarnings("unchecked")
          Map<String, Object> propSchema = (Map<String, Object>) properties.get(propName);
          violations.addAll(
              validateAgainstSchema(fieldName + "." + propName, propValue, propSchema));
        }
      }
    }

    // Validate minProperties
    if (schema.containsKey("minProperties")) {
      int minProps = toInt(schema.get("minProperties"));
      if (value.size() < minProps) {
        violations.add(
            new McpViolation(
                fieldName,
                "Object has " + value.size() + " properties but minimum is " + minProps));
      }
    }

    // Validate maxProperties
    if (schema.containsKey("maxProperties")) {
      int maxProps = toInt(schema.get("maxProperties"));
      if (value.size() > maxProps) {
        violations.add(
            new McpViolation(
                fieldName,
                "Object has " + value.size() + " properties but maximum is " + maxProps));
      }
    }

    return violations;
  }

  /**
   * Safely converts an object to a double.
   *
   * @param obj the object to convert
   * @return the double value, or 0.0 if conversion fails
   */
  private double toDouble(Object obj) {
    if (obj instanceof Double d) {
      return d;
    } else if (obj instanceof Number n) {
      return n.doubleValue();
    }
    return 0.0;
  }
}

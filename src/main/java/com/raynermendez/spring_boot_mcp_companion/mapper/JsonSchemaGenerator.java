package com.raynermendez.spring_boot_mcp_companion.mapper;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps Java types to JSON Schema representations.
 *
 * <p>This component generates JSON Schema for various Java types including primitives,
 * collections, POJOs, and records. It also reflects on Bean Validation annotations to add
 * schema constraints (min/max, required, etc.).
 */
public class JsonSchemaGenerator {

  /**
   * Generates a JSON Schema representation for the given Java type.
   *
   * @param type the Java type to generate schema for
   * @return a Map representing the JSON Schema object
   */
  public Map<String, Object> generateSchema(Class<?> type) {
    if (type == null) {
      return Map.of("type", "object");
    }

    // Primitive wrapper types
    if (type == String.class) {
      return Map.of("type", "string");
    }
    if (type == Integer.class || type == int.class) {
      return Map.of("type", "integer");
    }
    if (type == Long.class || type == long.class) {
      return Map.of("type", "integer");
    }
    if (type == Double.class || type == double.class) {
      return Map.of("type", "number");
    }
    if (type == Float.class || type == float.class) {
      return Map.of("type", "number");
    }
    if (type == Boolean.class || type == boolean.class) {
      return Map.of("type", "boolean");
    }
    if (type == BigDecimal.class) {
      return Map.of("type", "number");
    }
    if (type == BigInteger.class) {
      return Map.of("type", "integer");
    }

    // Collection types
    if (Collection.class.isAssignableFrom(type) || type.isAssignableFrom(List.class)) {
      return Map.of("type", "array", "items", Map.of("type", "object"));
    }

    // Map types
    if (Map.class.isAssignableFrom(type)) {
      return Map.of("type", "object");
    }

    // POJO or Record - reflect on fields
    return generatePojoSchema(type);
  }

  /**
   * Generates schema for a POJO or Record type by reflecting on its fields.
   *
   * @param type the POJO or Record class
   * @return a JSON Schema object with properties and potentially required array
   */
  private Map<String, Object> generatePojoSchema(Class<?> type) {
    Map<String, Object> schema = new HashMap<>();
    schema.put("type", "object");

    Map<String, Object> properties = new HashMap<>();
    List<String> required = new java.util.ArrayList<>();

    Field[] fields = type.getDeclaredFields();
    for (Field field : fields) {
      // Skip static fields and synthetic fields
      if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
        continue;
      }
      if (field.isSynthetic()) {
        continue;
      }

      String fieldName = field.getName();
      Class<?> fieldType = field.getType();

      // Generate schema for field type (without recursion for now)
      Map<String, Object> fieldSchema = generateFieldSchema(fieldType, field);
      properties.put(fieldName, fieldSchema);

      // Check for @NotNull constraint
      if (field.isAnnotationPresent(NotNull.class)) {
        required.add(fieldName);
      }
    }

    if (!properties.isEmpty()) {
      schema.put("properties", properties);
    }
    if (!required.isEmpty()) {
      schema.put("required", required);
    }

    return schema;
  }

  /**
   * Generates schema for a single field, incorporating validation constraints.
   *
   * @param fieldType the type of the field
   * @param field the Field object (for reflection on annotations)
   * @return a JSON Schema object for the field
   */
  private Map<String, Object> generateFieldSchema(Class<?> fieldType, Field field) {
    Map<String, Object> fieldSchema = new HashMap<>(generateSchema(fieldType));

    // Apply @Size constraints
    Size sizeConstraint = field.getAnnotation(Size.class);
    if (sizeConstraint != null) {
      if (fieldType == String.class) {
        if (sizeConstraint.min() > 0) {
          fieldSchema.put("minLength", sizeConstraint.min());
        }
        if (sizeConstraint.max() < Integer.MAX_VALUE) {
          fieldSchema.put("maxLength", sizeConstraint.max());
        }
      } else if (Collection.class.isAssignableFrom(fieldType) || fieldType.isArray()) {
        if (sizeConstraint.min() > 0) {
          fieldSchema.put("minItems", sizeConstraint.min());
        }
        if (sizeConstraint.max() < Integer.MAX_VALUE) {
          fieldSchema.put("maxItems", sizeConstraint.max());
        }
      }
    }

    // Apply @Min and @Max constraints for numbers
    Min minConstraint = field.getAnnotation(Min.class);
    if (minConstraint != null) {
      fieldSchema.put("minimum", minConstraint.value());
    }

    Max maxConstraint = field.getAnnotation(Max.class);
    if (maxConstraint != null) {
      fieldSchema.put("maximum", maxConstraint.value());
    }

    return fieldSchema;
  }

  /**
   * Generates schema for method parameter types (used by mapping engine).
   *
   * @param parameterType the parameter type
   * @param parameterAnnotations annotations on the parameter
   * @return a JSON Schema object for the parameter
   */
  public Map<String, Object> generateParameterSchema(
      Class<?> parameterType, java.lang.annotation.Annotation[] parameterAnnotations) {
    Map<String, Object> schema = new HashMap<>(generateSchema(parameterType));

    // Apply constraints from parameter annotations
    for (java.lang.annotation.Annotation annotation : parameterAnnotations) {
      if (annotation instanceof Size sizeConstraint) {
        if (parameterType == String.class) {
          if (sizeConstraint.min() > 0) {
            schema.put("minLength", sizeConstraint.min());
          }
          if (sizeConstraint.max() < Integer.MAX_VALUE) {
            schema.put("maxLength", sizeConstraint.max());
          }
        } else if (Collection.class.isAssignableFrom(parameterType)
            || parameterType.isArray()) {
          if (sizeConstraint.min() > 0) {
            schema.put("minItems", sizeConstraint.min());
          }
          if (sizeConstraint.max() < Integer.MAX_VALUE) {
            schema.put("maxItems", sizeConstraint.max());
          }
        }
      } else if (annotation instanceof Min minConstraint) {
        schema.put("minimum", minConstraint.value());
      } else if (annotation instanceof Max maxConstraint) {
        schema.put("maximum", maxConstraint.value());
      }
    }

    return schema;
  }
}

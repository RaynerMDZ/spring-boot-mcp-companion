package com.raynermendez.spring_boot_mcp_companion.mapper;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for JsonSchemaGenerator. */
class JsonSchemaGeneratorTest {

  private JsonSchemaGenerator generator;

  @BeforeEach
  void setUp() {
    generator = new JsonSchemaGenerator();
  }

  @Test
  void testStringType() {
    Map<String, Object> schema = generator.generateSchema(String.class);

    assertEquals("string", schema.get("type"));
  }

  @Test
  void testIntegerTypes() {
    Map<String, Object> schemaInt = generator.generateSchema(int.class);
    assertEquals("integer", schemaInt.get("type"));

    Map<String, Object> schemaLong = generator.generateSchema(long.class);
    assertEquals("integer", schemaLong.get("type"));

    Map<String, Object> schemaInteger = generator.generateSchema(Integer.class);
    assertEquals("integer", schemaInteger.get("type"));

    Map<String, Object> schemaLongWrapper = generator.generateSchema(Long.class);
    assertEquals("integer", schemaLongWrapper.get("type"));
  }

  @Test
  void testFloatingPointTypes() {
    Map<String, Object> schemaDouble = generator.generateSchema(double.class);
    assertEquals("number", schemaDouble.get("type"));

    Map<String, Object> schemaFloat = generator.generateSchema(float.class);
    assertEquals("number", schemaFloat.get("type"));

    Map<String, Object> schemaDoubleWrapper = generator.generateSchema(Double.class);
    assertEquals("number", schemaDoubleWrapper.get("type"));

    Map<String, Object> schemaFloatWrapper = generator.generateSchema(Float.class);
    assertEquals("number", schemaFloatWrapper.get("type"));
  }

  @Test
  void testBooleanType() {
    Map<String, Object> schemaPrimitive = generator.generateSchema(boolean.class);
    assertEquals("boolean", schemaPrimitive.get("type"));

    Map<String, Object> schemaWrapper = generator.generateSchema(Boolean.class);
    assertEquals("boolean", schemaWrapper.get("type"));
  }

  @Test
  void testBigDecimalAndBigInteger() {
    Map<String, Object> schemaBigDecimal = generator.generateSchema(BigDecimal.class);
    assertEquals("number", schemaBigDecimal.get("type"));

    Map<String, Object> schemaBigInteger = generator.generateSchema(BigInteger.class);
    assertEquals("integer", schemaBigInteger.get("type"));
  }

  @Test
  void testListType() {
    Map<String, Object> schema = generator.generateSchema(List.class);

    assertEquals("array", schema.get("type"));
    assertTrue(schema.containsKey("items"));
  }

  @Test
  void testCollectionType() {
    Map<String, Object> schema = generator.generateSchema(java.util.Collection.class);

    assertEquals("array", schema.get("type"));
  }

  @Test
  void testMapType() {
    Map<String, Object> schema = generator.generateSchema(Map.class);

    assertEquals("object", schema.get("type"));
  }

  @Test
  void testPojoType() {
    Map<String, Object> schema = generator.generateSchema(SamplePojo.class);

    assertEquals("object", schema.get("type"));
    assertTrue(schema.containsKey("properties"));
    Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
    assertTrue(properties.containsKey("name"));
  }

  @Test
  void testNotNullConstraintAddToRequired() {
    Map<String, Object> schema = generator.generateSchema(PojoWithConstraints.class);

    assertEquals("object", schema.get("type"));
    assertTrue(schema.containsKey("required"));
    List<String> required = (List<String>) schema.get("required");
    assertTrue(required.contains("requiredField"));
  }

  @Test
  void testSizeConstraintOnString() {
    Map<String, Object> schema = generator.generateSchema(PojoWithStringSize.class);

    Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
    Map<String, Object> fieldSchema = (Map<String, Object>) properties.get("sizedString");

    assertEquals("string", fieldSchema.get("type"));
    assertEquals(1, fieldSchema.get("minLength"));
    assertEquals(100, fieldSchema.get("maxLength"));
  }

  @Test
  void testSizeConstraintOnArray() {
    Map<String, Object> schema = generator.generateSchema(PojoWithArraySize.class);

    Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
    Map<String, Object> fieldSchema = (Map<String, Object>) properties.get("items");

    assertEquals("array", fieldSchema.get("type"));
    assertEquals(1, fieldSchema.get("minItems"));
    assertEquals(10, fieldSchema.get("maxItems"));
  }

  @Test
  void testMinConstraintOnNumber() {
    Map<String, Object> schema = generator.generateSchema(PojoWithMinMax.class);

    Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
    Map<String, Object> fieldSchema = (Map<String, Object>) properties.get("count");

    assertEquals(0L, ((Number) fieldSchema.get("minimum")).longValue());
  }

  @Test
  void testMaxConstraintOnNumber() {
    Map<String, Object> schema = generator.generateSchema(PojoWithMinMax.class);

    Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
    Map<String, Object> fieldSchema = (Map<String, Object>) properties.get("count");

    assertEquals(100L, ((Number) fieldSchema.get("maximum")).longValue());
  }

  @Test
  void testUnknownTypeReturnsObject() {
    // Test with a custom POJO class
    Map<String, Object> schema = generator.generateSchema(SamplePojo.class);

    assertEquals("object", schema.get("type"));
  }

  @Test
  void testNullTypeReturnsObject() {
    Map<String, Object> schema = generator.generateSchema(null);

    assertEquals("object", schema.get("type"));
  }

  @Test
  void testParameterSchemaWithConstraints() {
    // Test by using a field that has constraints
    Map<String, Object> schema = generator.generateParameterSchema(String.class, new java.lang.annotation.Annotation[0]);

    assertEquals("string", schema.get("type"));
    // Without annotations, the basic schema should just be type string
    assertTrue(schema.containsKey("type"));
  }

  // Test POJOs
  public static class SamplePojo {
    public String name;
    public int age;
  }

  public static class PojoWithConstraints {
    @NotNull public String requiredField;

    public String optionalField;
  }

  public static class PojoWithStringSize {
    @Size(min = 1, max = 100) public String sizedString;
  }

  public static class PojoWithArraySize {
    @Size(min = 1, max = 10) public List<String> items;
  }

  public static class PojoWithMinMax {
    @Min(0)
    @Max(100)
    public int count;
  }
}

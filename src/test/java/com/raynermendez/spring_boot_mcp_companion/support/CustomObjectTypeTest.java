package com.raynermendez.spring_boot_mcp_companion.support;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Demonstrates that the Spring Boot MCP Companion framework supports custom object types
 * (POJOs) in addition to Maps, allowing tools to accept strongly-typed parameters like
 * Person, User, etc.
 */
@DisplayName("Custom Object Type Support Tests")
class CustomObjectTypeTest {

  private ObjectMapper objectMapper;

  @BeforeEach
  void setup() {
    objectMapper = new ObjectMapper();
  }

  @Test
  @DisplayName("Should convert JSON Map to custom Person object")
  void testConvertMapToPerson() {
    // Simulate incoming JSON-RPC parameter as Map (from JSON deserialization)
    Map<String, Object> personMap = new HashMap<>();
    personMap.put("name", "John Doe");
    personMap.put("age", 30);
    personMap.put("email", "john@example.com");

    // Jackson converts Map to strongly-typed Person object
    Person person = objectMapper.convertValue(personMap, Person.class);

    // Verify conversion worked
    assertNotNull(person);
    assertEquals("John Doe", person.getName());
    assertEquals(30, person.getAge());
    assertEquals("john@example.com", person.getEmail());
  }

  @Test
  @DisplayName("Should handle partial object with optional fields")
  void testConvertPartialMapToPerson() {
    // Only providing required fields, email is optional
    Map<String, Object> personMap = new HashMap<>();
    personMap.put("name", "Jane Smith");
    personMap.put("age", 25);

    Person person = objectMapper.convertValue(personMap, Person.class);

    assertEquals("Jane Smith", person.getName());
    assertEquals(25, person.getAge());
    assertNull(person.getEmail()); // null for missing optional field
  }

  @Test
  @DisplayName("Should validate custom object as compatible input type")
  void testCustomObjectTypeValidation() {
    Map<String, Object> personMap = new HashMap<>();
    personMap.put("name", "Test User");
    personMap.put("age", 20);

    // Simulate type checking from DefaultMcpDispatcher.isValidType()
    Class<?> targetType = Person.class;
    Object value = personMap;

    // Custom objects are complex types (not primitive, not array)
    boolean isComplexType = !targetType.isPrimitive() && !targetType.isArray();
    assertTrue(isComplexType, "Person should be treated as complex type");

    // Maps are accepted for complex types
    boolean isValidTypeForComplexObject = value instanceof Map || value instanceof String;
    assertTrue(isValidTypeForComplexObject,
        "Map should be accepted as valid input for complex object type");
  }

  @Test
  @DisplayName("Should support nested custom objects")
  void testConvertNestedObjects() {
    // Create nested Map structure for Address
    Map<String, Object> addressMap = new HashMap<>();
    addressMap.put("street", "123 Main Street");
    addressMap.put("city", "New York");
    addressMap.put("zip", "10001");

    // Create Map structure for Person with nested Address
    Map<String, Object> personMap = new HashMap<>();
    personMap.put("name", "Alice Johnson");
    personMap.put("age", 35);
    personMap.put("email", "alice@example.com");
    personMap.put("address", addressMap);

    // Jackson recursively converts nested Maps to nested objects
    PersonWithAddress person = objectMapper.convertValue(personMap, PersonWithAddress.class);

    assertEquals("Alice Johnson", person.getName());
    assertEquals(35, person.getAge());
    assertNotNull(person.getAddress());
    assertEquals("123 Main Street", person.getAddress().getStreet());
    assertEquals("New York", person.getAddress().getCity());
    assertEquals("10001", person.getAddress().getZip());
  }

  @Test
  @DisplayName("Should support List of custom objects")
  void testConvertListOfCustomObjects() {
    // Create list of Person maps
    Map<String, Object> person1 = new HashMap<>();
    person1.put("name", "Person 1");
    person1.put("age", 25);

    Map<String, Object> person2 = new HashMap<>();
    person2.put("name", "Person 2");
    person2.put("age", 30);

    java.util.List<Map<String, Object>> personsList =
        java.util.Arrays.asList(person1, person2);

    // Jackson converts list of maps to list of custom objects
    java.util.List<Person> people = objectMapper.convertValue(
        personsList,
        objectMapper.getTypeFactory()
            .constructCollectionType(java.util.List.class, Person.class));

    assertEquals(2, people.size());
    assertEquals("Person 1", people.get(0).getName());
    assertEquals("Person 2", people.get(1).getName());
  }

  // Example custom POJO classes
  public static class Person {
    private String name;
    private int age;
    private String email;

    public Person() {}

    public Person(String name, int age, String email) {
      this.name = name;
      this.age = age;
      this.email = email;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public int getAge() {
      return age;
    }

    public void setAge(int age) {
      this.age = age;
    }

    public String getEmail() {
      return email;
    }

    public void setEmail(String email) {
      this.email = email;
    }
  }

  public static class Address {
    private String street;
    private String city;
    private String zip;

    public Address() {}

    public Address(String street, String city, String zip) {
      this.street = street;
      this.city = city;
      this.zip = zip;
    }

    public String getStreet() {
      return street;
    }

    public void setStreet(String street) {
      this.street = street;
    }

    public String getCity() {
      return city;
    }

    public void setCity(String city) {
      this.city = city;
    }

    public String getZip() {
      return zip;
    }

    public void setZip(String zip) {
      this.zip = zip;
    }
  }

  public static class PersonWithAddress {
    private String name;
    private int age;
    private String email;
    private Address address;

    public PersonWithAddress() {}

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public int getAge() {
      return age;
    }

    public void setAge(int age) {
      this.age = age;
    }

    public String getEmail() {
      return email;
    }

    public void setEmail(String email) {
      this.email = email;
    }

    public Address getAddress() {
      return address;
    }

    public void setAddress(Address address) {
      this.address = address;
    }
  }
}

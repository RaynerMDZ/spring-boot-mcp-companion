# Using Custom POJOs as MCP Tool Parameters

The Spring Boot MCP Companion framework fully supports custom object types (Plain Old Java Objects / POJOs) as tool parameters. When a tool parameter is defined as a custom class, the framework automatically converts incoming JSON to strongly-typed objects using Jackson's ObjectMapper.

## Overview

When an MCP client sends a JSON object to a tool parameter expecting a custom type like `Person`, `User`, or `Order`, the framework will:

1. **Accept JSON object input** from the MCP client
2. **Deserialize to Map** (standard JSON deserialization from JSON-RPC)
3. **Validate** the data against JSON Schema constraints
4. **Use Jackson** to convert the Map to your custom object type
5. **Pass strongly-typed object** to your tool method

**No special annotations are needed** - just define your POJO and use it as a parameter type.

---

## Basic Example

### 1. Define a Custom Class (POJO)

```java
public class Person {
    private String name;
    private int age;
    private String email;

    // Default constructor (required for Jackson)
    public Person() {}

    // Getters and setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getAge() { return age; }
    public void setAge(int age) { this.age = age; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
```

### 2. Use the Custom Object in a Tool

```java
@RestController
@RequestMapping("/api/tools")
public class PersonTools {

    @McpTool(description = "Process person information")
    public String processPerson(
        @McpInput(description = "Person object with name, age, and email")
        Person person
    ) {
        // person is a strongly-typed Person object, not a Map!
        return String.format(
            "Processing %s (age %d, email %s)",
            person.getName(),
            person.getAge(),
            person.getEmail()
        );
    }
}
```

### 3. Call the Tool with JSON

**Request:**
```bash
curl -X POST http://localhost:8080/mcp/tools/call \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "1",
    "method": "tools/call",
    "params": {
      "name": "process_person",
      "arguments": {
        "person": {
          "name": "John Doe",
          "age": 30,
          "email": "john@example.com"
        }
      }
    }
  }'
```

**Response:**
```json
{
  "jsonrpc": "2.0",
  "id": "1",
  "result": {
    "contents": [
      {
        "type": "text",
        "text": "Processing John Doe (age 30, email john@example.com)"
      }
    ]
  }
}
```

---

## Advanced Features

### Nested Objects

Define objects that contain other custom objects:

```java
public class Address {
    private String street;
    private String city;
    private String zip;

    public Address() {}
    // getters/setters...
}

public class PersonWithAddress {
    private String name;
    private int age;
    private Address address;  // Nested custom object

    public PersonWithAddress() {}
    // getters/setters...
}

@McpTool(description = "Process person with address")
public String processPerson(
    @McpInput(description = "Person with address")
    PersonWithAddress person
) {
    // Jackson automatically handles nested object conversion
    return "Person " + person.getName() +
           " lives in " + person.getAddress().getCity();
}
```

**Input:**
```json
{
  "person": {
    "name": "Alice",
    "age": 35,
    "address": {
      "street": "123 Main St",
      "city": "New York",
      "zip": "10001"
    }
  }
}
```

### Lists of Custom Objects

Tool parameters can accept lists of custom objects:

```java
@McpTool(description = "Process team members")
public String processTeam(
    @McpInput(description = "List of team members")
    java.util.List<Person> members
) {
    return "Processing team of " + members.size() + " members: " +
           members.stream()
               .map(Person::getName)
               .collect(java.util.stream.Collectors.joining(", "));
}
```

**Input:**
```json
{
  "members": [
    { "name": "Alice", "age": 25, "email": "alice@example.com" },
    { "name": "Bob", "age": 30, "email": "bob@example.com" },
    { "name": "Charlie", "age": 28, "email": "charlie@example.com" }
  ]
}
```

### Map of Custom Objects

Store custom objects in a Map:

```java
@McpTool(description = "Process employees")
public String processEmployees(
    @McpInput(description = "Map of employee ID to Person")
    java.util.Map<String, Person> employees
) {
    return "Processing " + employees.size() + " employees";
}
```

---

## How It Works

The framework uses Jackson's `ObjectMapper.convertValue()` to transform incoming JSON data:

```
1. Client sends JSON:
   {"name": "John", "age": 30, "email": "john@example.com"}

2. JSON deserialized to Map:
   Map<String, Object> with keys: "name", "age", "email"

3. Type validation:
   Checks if Person.class is a valid target (complex type)

4. Jackson conversion:
   objectMapper.convertValue(map, Person.class)

5. Method invocation:
   Tool receives strongly-typed Person object
```

---

## Requirements for Custom Classes

For Jackson to successfully deserialize to your custom class:

### 1. **Public No-Arg Constructor**

Jackson uses reflection to instantiate your class without arguments:

```java
public class Person {
    public Person() {}  // Required!
    // ...
}
```

### 2. **Getter and Setter Methods**

Properties must have accessor methods for Jackson to read/write values:

```java
public class Person {
    private String name;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
```

### 3. **Field Names Match JSON Keys**

By default, JSON keys match field names exactly. Use `@JsonProperty` to customize:

```java
public class Person {
    @JsonProperty("full_name")  // Maps JSON "full_name" to this field
    private String name;
}
```

### 4. **Supported Field Types**

Any type that Jackson supports can be a field:
- Primitives: `int`, `long`, `double`, `boolean`, etc.
- Objects: `String`, `Date`, `BigDecimal`, etc.
- Collections: `List<T>`, `Set<T>`, `Map<K,V>`
- Custom objects (recursive nesting)
- Enums

---

## Jackson Annotations

Use Jackson annotations for fine-grained control over deserialization:

### @JsonProperty
Map JSON keys to different field names:
```java
@JsonProperty("email_address")
private String email;
```

### @JsonIgnore
Don't deserialize this field from JSON:
```java
@JsonIgnore
private String internalId;
```

### @JsonCreator
Use a custom constructor for deserialization:
```java
@JsonCreator
public Person(
    @JsonProperty("name") String name,
    @JsonProperty("age") int age
) {
    this.name = name;
    this.age = age;
}
```

### @JsonAlias
Accept multiple JSON keys for the same field:
```java
@JsonAlias({"email_address", "email_addr"})
private String email;
```

---

## Error Handling

If JSON doesn't match your class structure, the framework returns an error:

### Missing Required Field
```json
{"name": "John"}  // Missing "age"
```

**Response:**
```json
{
  "error": "Failed to convert parameter 'person':
            Required creator parameter 'age' not found"
}
```

### Type Mismatch
```json
{"name": "John", "age": "thirty"}  // age should be number
```

**Response:**
```json
{
  "error": "Failed to convert parameter 'person':
            Cannot deserialize value of type 'int' from string 'thirty'"
}
```

---

## Custom Objects vs Maps

Both approaches work, but custom objects are type-safe and recommended:

### Using a Map (Not Recommended)
```java
@McpTool
public String process(
    @McpInput Person person
) {
    // Wrong - person is a Map, not a Person object!
    // Would cause runtime ClassCastException
}
```

### Using a Custom Object (Recommended)
```java
@McpTool
public String process(
    @McpInput Person person
) {
    // Correct - person is strongly typed as Person
    String name = person.getName();  // Type-safe!
    int age = person.getAge();
    // Compile-time type checking, no casting needed
}
```

---

## Best Practices

1. **Use custom objects for complex parameters** - More type-safe than Maps
2. **Keep constructors simple** - Use no-arg constructor + setters
3. **Use @JsonProperty for clarity** - Explicit mapping is better than implicit
4. **Document field requirements** - Use JavaDoc or descriptive names
5. **Leverage IDE support** - Custom objects work better with autocomplete

---

## See Also

- [API Reference - @McpInput](./core/API_REFERENCE.md)
- [Examples - Tool Definitions](./core/EXAMPLES.md)
- [Input Validation](./core/FEATURES.md#input-validation)
- [Jackson Documentation](https://github.com/FasterXML/jackson)

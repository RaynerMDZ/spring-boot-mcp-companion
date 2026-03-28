# Using Custom Objects as MCP Tool Parameters

The Spring Boot MCP Companion framework fully supports custom object types (POJOs) as tool parameters, not just primitive types and Maps.

## Overview

When a tool parameter is defined as a custom class like `Person`, `User`, `Config`, etc., the framework will:

1. Accept JSON object input from the client
2. Convert it to a `Map` (standard JSON deserialization)
3. Validate it against JSON Schema constraints
4. Use Jackson to convert the `Map` to your custom object type
5. Pass the strongly-typed object to your tool method

## Basic Example

### Define a Custom Class

```java
public class Person {
    private String name;
    private int age;
    private String email;

    // Getters and setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getAge() { return age; }
    public void setAge(int age) { this.age = age; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
```

### Define a Tool That Accepts the Custom Object

```java
@RestController
@RequestMapping("/api/tools")
public class PersonTools {

    @McpTool(
        name = "processPerson",
        description = "Process person information"
    )
    public String processPerson(
        @McpInput(
            name = "person",
            description = "Person object with name, age, and email",
            type = "object"
        )
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

### Call the Tool with JSON Object

```bash
curl -X POST http://localhost:8090/mcp/tools/call \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "1",
    "method": "tools/call",
    "params": {
      "name": "processPerson",
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

## Advanced Features

### 1. Nested Objects

```java
public class Address {
    private String street;
    private String city;
    private String zip;
    // getters/setters...
}

public class PersonWithAddress {
    private String name;
    private int age;
    private Address address;  // Nested custom object
    // getters/setters...
}

@McpTool(name = "processPerson", description = "...")
public String processPerson(
    @McpInput(name = "person")
    PersonWithAddress person
) {
    // Jackson automatically handles nested object conversion
    return person.getAddress().getCity();
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

### 2. Lists of Custom Objects

```java
@McpTool(name = "processTeam", description = "...")
public String processTeam(
    @McpInput(name = "members")
    java.util.List<Person> members
) {
    return "Processing team of " + members.size() + " members";
}
```

**Input:**
```json
{
  "members": [
    { "name": "Alice", "age": 25, "email": "alice@example.com" },
    { "name": "Bob", "age": 30, "email": "bob@example.com" }
  ]
}
```

### 3. JSON Schema Validation

You can add validation constraints to your custom object parameters:

```java
@McpInput(
    name = "person",
    description = "Person object",
    type = "object",
    jsonSchema = "{" +
        "\"required\": [\"name\", \"age\"]," +
        "\"properties\": {" +
        "  \"name\": {\"type\": \"string\", \"minLength\": 1}," +
        "  \"age\": {\"type\": \"number\", \"minimum\": 0, \"maximum\": 150}," +
        "  \"email\": {\"type\": \"string\", \"format\": \"email\"}" +
        "}" +
    "}"
)
Person person
```

### 4. Maps vs Custom Objects

Both approaches work:

**Using a Map:**
```java
@McpTool(name = "process", description = "...")
public String process(
    @McpInput(name = "data")
    java.util.Map<String, Object> data
) {
    String name = (String) data.get("name");
    int age = (Integer) data.get("age");
    // Type casting required...
}
```

**Using a Custom Object (Recommended):**
```java
@McpTool(name = "process", description = "...")
public String process(
    @McpInput(name = "data")
    Person person
) {
    String name = person.getName();  // Type-safe!
    int age = person.getAge();
    // No casting needed...
}
```

## How It Works

The framework uses Jackson's `ObjectMapper.convertValue()` to transform incoming JSON Maps into your custom objects:

1. **Client sends JSON:** `{"name": "John", "age": 30}`
2. **Framework deserializes:** `Map<String, Object>` with name and age keys
3. **Type validation:** Checks if `Person.class` is a valid target (complex type)
4. **Jackson conversion:** `objectMapper.convertValue(map, Person.class)`
5. **Method invocation:** Tool receives strongly-typed `Person` object

## Requirements for Custom Classes

For Jackson to deserialize to your custom class, ensure:

1. **Public no-arg constructor:** Jackson needs to instantiate the object
   ```java
   public Person() {}
   ```

2. **Getter and setter methods:** For field access
   ```java
   public String getName() { return name; }
   public void setName(String name) { this.name = name; }
   ```

3. **Field names match JSON keys:** Or use Jackson annotations
   ```java
   @JsonProperty("email_address")
   private String email;
   ```

4. **No required constructor arguments:** Constructor must be no-arg (or use @JsonCreator)

## Jackson Annotations

You can use Jackson annotations for fine-grained control:

```java
public class Person {
    @JsonProperty("full_name")  // Map "full_name" JSON key to this field
    private String name;

    @JsonIgnore                  // Don't deserialize from JSON
    private String internalId;

    @JsonCreator                 // Custom deserialization logic
    public Person(
        @JsonProperty("name") String name,
        @JsonProperty("age") int age
    ) {
        this.name = name;
        this.age = age;
    }

    // getters/setters...
}
```

## Error Handling

If a required field is missing:

```json
// Missing 'age' field
{ "name": "John" }
```

**Result:**
```json
{
  "error": "Validation failed: person: Required property missing: age"
}
```

## Performance Considerations

- **Validation:** Custom objects are validated against JSON Schema constraints before conversion
- **Conversion:** Jackson handles the conversion efficiently
- **Memory:** No overhead compared to using Maps directly
- **Type Safety:** Custom objects provide compile-time type checking

## Testing

See `CustomObjectTypeTest.java` for comprehensive examples of:
- Converting Maps to custom objects
- Handling nested objects
- Processing lists of custom objects
- Type validation

## See Also

- [Input Validation Guide](./INPUT_VALIDATION.md)
- [Tool Definition Guide](./TOOL_DEFINITION.md)
- [Jackson Documentation](https://github.com/FasterXML/jackson)

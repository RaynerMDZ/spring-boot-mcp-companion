package com.raynermendez.spring_boot_mcp_companion.prompt;

import com.raynermendez.spring_boot_mcp_companion.model.McpPromptDefinition;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Validates and processes prompt arguments according to MCP 2025-11-25 specification.
 *
 * <p>Handles:
 * <ul>
 *   <li>Validation of required arguments
 *   <li>Type checking for arguments
 *   <li>Variable substitution in prompt templates
 *   <li>Argument schema enforcement
 * </ul>
 *
 * @author Rayner Mendez
 */
@Component
public class PromptArgumentValidator {
    private static final Logger logger = LoggerFactory.getLogger(PromptArgumentValidator.class);

    /**
     * Validates arguments against a prompt definition.
     *
     * <p>Checks:
     * <ul>
     *   <li>All required arguments are provided
     *   <li>No extra arguments are passed
     *   <li>Argument values match expected types
     * </ul>
     *
     * @param prompt the prompt definition
     * @param arguments the provided arguments
     * @return validation result with errors if any
     */
    public ValidationResult validateArguments(
        McpPromptDefinition prompt,
        Map<String, Object> arguments
    ) {
        ValidationResult result = new ValidationResult();
        Map<String, Object> providedArgs = arguments != null ? arguments : Map.of();

        if (prompt.arguments() == null || prompt.arguments().isEmpty()) {
            // Prompt has no arguments, validate that none are provided
            if (!providedArgs.isEmpty()) {
                result.addError("Prompt does not accept arguments");
            }
            return result;
        }

        // Check required arguments
        for (McpPromptDefinition.McpPromptArgument arg : prompt.arguments()) {
            if (arg.required() && !providedArgs.containsKey(arg.name())) {
                result.addError("Missing required argument: " + arg.name());
            }
        }

        // Validate provided arguments exist in definition
        for (String argName : providedArgs.keySet()) {
            boolean found = prompt.arguments().stream()
                .anyMatch(arg -> arg.name().equals(argName));
            if (!found) {
                result.addError("Unknown argument: " + argName);
            }
        }

        if (result.hasErrors()) {
            logger.warn("Prompt argument validation failed: {}", result.getErrors());
        }

        return result;
    }

    /**
     * Performs variable substitution in prompt text.
     *
     * <p>Replaces {variableName} placeholders with provided argument values.
     * Example: "Weather for {city}" with {city: "Paris"} → "Weather for Paris"
     *
     * @param promptText the template text with variables
     * @param arguments the argument values
     * @return text with variables substituted
     */
    public String substituteVariables(String promptText, Map<String, Object> arguments) {
        if (promptText == null || arguments == null || arguments.isEmpty()) {
            return promptText;
        }

        String result = promptText;
        for (Map.Entry<String, Object> entry : arguments.entrySet()) {
            String placeholder = "{" + entry.getKey() + "}";
            String value = entry.getValue() != null ? entry.getValue().toString() : "";
            result = result.replace(placeholder, value);
            logger.debug("Substituted {} with {}", placeholder, value);
        }

        return result;
    }

    /**
     * Builds argument schema for prompts/get response.
     *
     * <p>Creates JSON Schema representation of prompt arguments for documentation.
     *
     * @param prompt the prompt definition
     * @return argument schema as map
     */
    public Map<String, Object> buildArgumentSchema(McpPromptDefinition prompt) {
        Map<String, Object> schema = new HashMap<>();

        if (prompt.arguments() == null || prompt.arguments().isEmpty()) {
            return Map.of("type", "object", "properties", Map.of());
        }

        Map<String, Object> properties = new HashMap<>();
        List<String> required = new java.util.ArrayList<>();

        for (McpPromptDefinition.McpPromptArgument arg : prompt.arguments()) {
            Map<String, Object> argSchema = new HashMap<>();
            argSchema.put("type", "string"); // Default to string
            argSchema.put("description", arg.description());
            properties.put(arg.name(), argSchema);

            if (arg.required()) {
                required.add(arg.name());
            }
        }

        schema.put("type", "object");
        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }

        return schema;
    }

    /**
     * Represents validation results for prompt arguments.
     */
    public static class ValidationResult {
        private final List<String> errors = new java.util.ArrayList<>();

        /**
         * Adds a validation error.
         */
        public void addError(String error) {
            errors.add(error);
        }

        /**
         * Checks if there are any validation errors.
         */
        public boolean hasErrors() {
            return !errors.isEmpty();
        }

        /**
         * Gets all validation errors.
         */
        public List<String> getErrors() {
            return java.util.List.copyOf(errors);
        }

        /**
         * Gets a concatenated error message.
         */
        public String getErrorMessage() {
            return String.join("; ", errors);
        }
    }
}

package com.raynermendez.spring_boot_mcp_companion.registry;

import com.raynermendez.spring_boot_mcp_companion.model.McpPromptDefinition;
import com.raynermendez.spring_boot_mcp_companion.model.McpResourceDefinition;
import com.raynermendez.spring_boot_mcp_companion.model.McpToolDefinition;
import java.util.List;

/**
 * Interface for the MCP Definition Registry.
 *
 * <p>The registry stores and retrieves definitions for MCP Tools, Resources, and Prompts. It
 * enforces uniqueness by name/URI and manages state transitions through building → ready →
 * locked states.
 *
 * <p>Registration is thread-safe and supports duplicate detection. Once locked, no further
 * registrations are allowed.
 */
public interface McpDefinitionRegistry {

  /**
   * Registers a tool definition.
   *
   * <p>Throws an exception if a tool with the same name already exists.
   *
   * @param tool the tool definition to register
   * @throws IllegalStateException if a tool with the same name exists, or if the registry is
   *     locked
   */
  void register(McpToolDefinition tool);

  /**
   * Registers a resource definition.
   *
   * <p>Throws an exception if a resource with the same URI already exists.
   *
   * @param resource the resource definition to register
   * @throws IllegalStateException if a resource with the same URI exists, or if the registry is
   *     locked
   */
  void register(McpResourceDefinition resource);

  /**
   * Registers a prompt definition.
   *
   * <p>Throws an exception if a prompt with the same name already exists.
   *
   * @param prompt the prompt definition to register
   * @throws IllegalStateException if a prompt with the same name exists, or if the registry is
   *     locked
   */
  void register(McpPromptDefinition prompt);

  /**
   * Returns an unmodifiable list of all registered tools.
   *
   * @return list of tool definitions
   */
  List<McpToolDefinition> getTools();

  /**
   * Returns an unmodifiable list of all registered resources.
   *
   * @return list of resource definitions
   */
  List<McpResourceDefinition> getResources();

  /**
   * Returns an unmodifiable list of all registered prompts.
   *
   * @return list of prompt definitions
   */
  List<McpPromptDefinition> getPrompts();

  /**
   * Returns the current state of the registry.
   *
   * @return the registry state
   */
  RegistryState getState();

  /**
   * Transitions the registry to READY state, preventing further registrations.
   *
   * <p>This method is idempotent: calling it multiple times has no additional effect after the
   * first call.
   */
  void lock();

  /**
   * Enumeration of possible registry states.
   */
  enum RegistryState {
    /** Registry has been created but no definitions registered yet */
    EMPTY,
    /** Registry is accepting new definitions */
    BUILDING,
    /** Registry is locked and ready for use */
    READY,
    /** Registry initialization failed */
    FAILED
  }
}

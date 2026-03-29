package com.raynermendez.spring_boot_mcp_companion.registry;

import com.raynermendez.spring_boot_mcp_companion.model.McpPromptDefinition;
import com.raynermendez.spring_boot_mcp_companion.model.McpResourceDefinition;
import com.raynermendez.spring_boot_mcp_companion.model.McpResourceTemplate;
import com.raynermendez.spring_boot_mcp_companion.model.McpToolDefinition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe implementation of the MCP Definition Registry.
 *
 * <p>This class manages registration of Tools, Resources (direct and templates), and Prompts
 * using concurrent data structures. It enforces uniqueness by name/URI/template-name and
 * transitions through states as definitions are added and the registry is finalized.
 *
 * <p>Implements MCP 2025-11-25 specification for resource templates support.
 */
public class DefaultMcpDefinitionRegistry implements McpDefinitionRegistry {

  private final ConcurrentHashMap<String, McpToolDefinition> tools;
  private final ConcurrentHashMap<String, McpResourceDefinition> resources;
  private final ConcurrentHashMap<String, McpResourceTemplate> resourceTemplates;
  private final ConcurrentHashMap<String, McpPromptDefinition> prompts;
  private volatile RegistryState state;

  /** Initializes a new registry in the EMPTY state. */
  public DefaultMcpDefinitionRegistry() {
    this.tools = new ConcurrentHashMap<>();
    this.resources = new ConcurrentHashMap<>();
    this.resourceTemplates = new ConcurrentHashMap<>();
    this.prompts = new ConcurrentHashMap<>();
    this.state = RegistryState.EMPTY;
  }

  @Override
  public void register(McpToolDefinition tool) {
    if (state == RegistryState.READY) {
      throw new IllegalStateException("Cannot register tool: registry is locked");
    }

    if (tools.putIfAbsent(tool.name(), tool) != null) {
      throw new IllegalStateException("Duplicate tool name: " + tool.name());
    }

    if (state == RegistryState.EMPTY) {
      state = RegistryState.BUILDING;
    }
  }

  @Override
  public void register(McpResourceDefinition resource) {
    if (state == RegistryState.READY) {
      throw new IllegalStateException("Cannot register resource: registry is locked");
    }

    if (resources.putIfAbsent(resource.uri(), resource) != null) {
      throw new IllegalStateException("Duplicate resource URI: " + resource.uri());
    }

    if (state == RegistryState.EMPTY) {
      state = RegistryState.BUILDING;
    }
  }

  @Override
  public void register(McpPromptDefinition prompt) {
    if (state == RegistryState.READY) {
      throw new IllegalStateException("Cannot register prompt: registry is locked");
    }

    if (prompts.putIfAbsent(prompt.name(), prompt) != null) {
      throw new IllegalStateException("Duplicate prompt name: " + prompt.name());
    }

    if (state == RegistryState.EMPTY) {
      state = RegistryState.BUILDING;
    }
  }

  @Override
  public void register(McpResourceTemplate template) {
    if (state == RegistryState.READY) {
      throw new IllegalStateException("Cannot register resource template: registry is locked");
    }

    if (resourceTemplates.putIfAbsent(template.name(), template) != null) {
      throw new IllegalStateException("Duplicate resource template name: " + template.name());
    }

    if (state == RegistryState.EMPTY) {
      state = RegistryState.BUILDING;
    }
  }

  @Override
  public List<McpToolDefinition> getTools() {
    return Collections.unmodifiableList(new ArrayList<>(tools.values()));
  }

  @Override
  public List<McpResourceDefinition> getResources() {
    return Collections.unmodifiableList(new ArrayList<>(resources.values()));
  }

  @Override
  public List<McpResourceTemplate> getResourceTemplates() {
    return Collections.unmodifiableList(new ArrayList<>(resourceTemplates.values()));
  }

  @Override
  public List<McpPromptDefinition> getPrompts() {
    return Collections.unmodifiableList(new ArrayList<>(prompts.values()));
  }

  @Override
  public RegistryState getState() {
    return state;
  }

  @Override
  public void lock() {
    if (state != RegistryState.READY) {
      state = RegistryState.READY;
    }
  }
}

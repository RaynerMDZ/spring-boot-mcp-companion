package com.raynermendez.spring_boot_mcp_companion.scanner;

import com.raynermendez.spring_boot_mcp_companion.annotation.McpPrompt;
import com.raynermendez.spring_boot_mcp_companion.annotation.McpResource;
import com.raynermendez.spring_boot_mcp_companion.annotation.McpTool;
import com.raynermendez.spring_boot_mcp_companion.mapper.McpMappingEngine;
import com.raynermendez.spring_boot_mcp_companion.model.McpPromptDefinition;
import com.raynermendez.spring_boot_mcp_companion.model.McpResourceDefinition;
import com.raynermendez.spring_boot_mcp_companion.model.McpToolDefinition;
import com.raynermendez.spring_boot_mcp_companion.registry.McpDefinitionRegistry;
import java.lang.reflect.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;

/**
 * Scans Spring beans for @McpTool annotations and registers them with the MCP registry.
 *
 * <p>This scanner is invoked after all Spring beans are instantiated and configured. It iterates
 * through all beans, reflects on their methods for @McpTool annotations, and registers matching
 * tools with the MCP Definition Registry.
 */
public class McpAnnotationScanner implements ApplicationContextAware {

  private static final Logger logger = LoggerFactory.getLogger(McpAnnotationScanner.class);

  private final McpDefinitionRegistry registry;
  private final McpMappingEngine mappingEngine;
  private ApplicationContext applicationContext;

  public McpAnnotationScanner(
      McpDefinitionRegistry registry,
      McpMappingEngine mappingEngine) {
    this.registry = registry;
    this.mappingEngine = mappingEngine;
  }

  @Override
  public void setApplicationContext(ApplicationContext applicationContext) {
    this.applicationContext = applicationContext;
  }

  /**
   * Triggered after the ApplicationContext is fully initialized.
   *
   * @param event the context refreshed event
   */
  @EventListener(ContextRefreshedEvent.class)
  public void scanAnnotations(ContextRefreshedEvent event) {
    logger.debug("Starting MCP annotation scanning...");

    // Get all bean definition names from the registry
    if (applicationContext instanceof org.springframework.context.support.AbstractApplicationContext) {
      org.springframework.context.support.AbstractApplicationContext abstractContext =
          (org.springframework.context.support.AbstractApplicationContext) applicationContext;
      BeanDefinitionRegistry bdRegistry =
          (BeanDefinitionRegistry) abstractContext.getBeanFactory();

      String[] beanNames = bdRegistry.getBeanDefinitionNames();

      for (String beanName : beanNames) {
        try {
          scanBeanForTools(beanName);
        } catch (Exception e) {
          logger.warn("Error scanning bean '{}' for MCP annotations: {}", beanName, e.getMessage());
        }
      }
    }

    // Lock the registry to prevent further registrations
    registry.lock();
    logger.info("MCP annotation scanning completed. Registry locked. Tools: {}, Resources: {}, Prompts: {}",
        registry.getTools().size(), registry.getResources().size(), registry.getPrompts().size());
  }

  /**
   * Scans a single bean for @McpTool, @McpResource, and @McpPrompt annotated methods.
   *
   * @param beanName the name of the bean to scan
   */
  private void scanBeanForTools(String beanName) {
    Object bean = applicationContext.getBean(beanName);
    if (bean == null) {
      return;
    }

    // Use AopUtils to get the target class (handles proxies)
    Class<?> targetClass = AopUtils.getTargetClass(bean);

    // Get all declared methods on the target class
    Method[] methods = targetClass.getDeclaredMethods();

    for (Method method : methods) {
      // Scan for @McpTool
      McpTool toolAnnotation = org.springframework.core.annotation.AnnotationUtils.findAnnotation(
          method, McpTool.class);
      if (toolAnnotation != null) {
        try {
          method.setAccessible(true);
          McpToolDefinition toolDef = mappingEngine.toToolDefinition(bean, method, toolAnnotation);
          registry.register(toolDef);
          logger.debug("Registered MCP tool '{}' from bean '{}' method '{}'",
              toolDef.name(), beanName, method.getName());
        } catch (IllegalStateException e) {
          if (e.getMessage().contains("Duplicate")) {
            logger.warn("Duplicate MCP tool detected: {}", e.getMessage());
            throw new org.springframework.beans.factory.BeanCreationException(
                beanName, "Duplicate MCP tool name: " + e.getMessage(), e);
          }
          throw e;
        }
      }

      // Scan for @McpResource
      McpResource resourceAnnotation = org.springframework.core.annotation.AnnotationUtils
          .findAnnotation(method, McpResource.class);
      if (resourceAnnotation != null) {
        try {
          method.setAccessible(true);
          McpResourceDefinition resourceDef = mappingEngine.toResourceDefinition(
              bean, method, resourceAnnotation);
          registry.register(resourceDef);
          logger.debug("Registered MCP resource '{}' from bean '{}' method '{}'",
              resourceDef.uri(), beanName, method.getName());
        } catch (IllegalStateException e) {
          if (e.getMessage().contains("Duplicate")) {
            logger.warn("Duplicate MCP resource detected: {}", e.getMessage());
            throw new org.springframework.beans.factory.BeanCreationException(
                beanName, "Duplicate MCP resource URI: " + e.getMessage(), e);
          }
          throw e;
        }
      }

      // Scan for @McpPrompt
      McpPrompt promptAnnotation = org.springframework.core.annotation.AnnotationUtils.findAnnotation(
          method, McpPrompt.class);
      if (promptAnnotation != null) {
        try {
          method.setAccessible(true);
          McpPromptDefinition promptDef = mappingEngine.toPromptDefinition(
              bean, method, promptAnnotation);
          registry.register(promptDef);
          logger.debug("Registered MCP prompt '{}' from bean '{}' method '{}'",
              promptDef.name(), beanName, method.getName());
        } catch (IllegalStateException e) {
          if (e.getMessage().contains("Duplicate")) {
            logger.warn("Duplicate MCP prompt detected: {}", e.getMessage());
            throw new org.springframework.beans.factory.BeanCreationException(
                beanName, "Duplicate MCP prompt name: " + e.getMessage(), e);
          }
          throw e;
        }
      }
    }
  }
}

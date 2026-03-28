package com.raynermendez.spring_boot_mcp_companion.model;

import java.lang.reflect.Method;

/**
 * Immutable record holding a reference to a Spring bean method that handles MCP requests.
 *
 * <p>This record stores both the Spring bean instance (targetBean) and the Method object,
 * allowing the runtime to invoke the handler via reflection when MCP requests are received.
 */
public record MethodHandlerRef(
    Object targetBean,
    Method method,
    String beanName) {

  /**
   * Creates a new method handler reference.
   *
   * @param targetBean the actual Spring bean instance
   * @param method the method to invoke on the bean
   * @param beanName the Spring bean name
   */
  public MethodHandlerRef {}
}

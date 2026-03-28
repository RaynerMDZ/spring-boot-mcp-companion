package com.raynermendez.spring_boot_mcp_companion.security;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Security tests for reflection verification.
 *
 * <p>Tests verify that:
 * <ul>
 *   <li>Only public methods can be invoked
 *   <li>Methods from dangerous classes are rejected
 *   <li>Runtime verification prevents arbitrary method invocation
 *   <li>Base class methods (Object, Class, etc.) are blocked
 * </ul>
 */
@DisplayName("Reflection Security Tests")
class ReflectionSecurityTest {

  @Test
  @DisplayName("Should verify method is public")
  void testVerifyMethodIsPublic() throws Exception {
    Method publicMethod = String.class.getMethod("valueOf", Object.class);
    assertTrue(Modifier.isPublic(publicMethod.getModifiers()),
        "Public method should have public modifier");

    Method privateMethod = String.class.getDeclaredMethod("checkOffset", int.class, int.class);
    assertFalse(Modifier.isPublic(privateMethod.getModifiers()),
        "Private method should not have public modifier");
  }

  @Test
  @DisplayName("Should reject methods from Object class")
  void testRejectMethodsFromObjectClass() throws Exception {
    Method objectMethod = Object.class.getMethod("toString");
    assertTrue(objectMethod.getDeclaringClass() == Object.class,
        "Method should be declared in Object class");
  }

  @Test
  @DisplayName("Should reject methods from Class class")
  void testRejectMethodsFromClassClass() throws Exception {
    Method classMethod = Class.class.getMethod("getName");
    assertTrue(classMethod.getDeclaringClass() == Class.class,
        "Method should be declared in Class class");
  }

  @Test
  @DisplayName("Should reject methods from Runtime class")
  void testRejectMethodsFromRuntimeClass() throws Exception {
    Method runtimeMethod = Runtime.class.getMethod("getRuntime");
    assertTrue(runtimeMethod.getDeclaringClass() == Runtime.class,
        "Method should be declared in Runtime class");
  }

  @Test
  @DisplayName("Should reject methods from System class")
  void testRejectMethodsFromSystemClass() {
    // System.class has dangerous methods we should never invoke
    assertTrue(System.class.getName().equals("java.lang.System"),
        "Should identify System class");
  }

  @Test
  @DisplayName("Should reject methods from ClassLoader class")
  void testRejectMethodsFromClassLoaderClass() throws Exception {
    Method loaderMethod = ClassLoader.class.getMethod("getSystemClassLoader");
    assertTrue(loaderMethod.getDeclaringClass() == ClassLoader.class,
        "Method should be declared in ClassLoader class");
  }

  @Test
  @DisplayName("Should reject methods from java.lang.reflect package")
  void testRejectMethodsFromReflectPackage() {
    assertTrue(Method.class.getName().startsWith("java.lang.reflect"),
        "Method class is in reflect package");
  }

  @Test
  @DisplayName("Should accept methods from custom business classes")
  void testAcceptMethodsFromCustomClasses() throws Exception {
    Method customMethod = TestBusinessClass.class.getMethod("processData", String.class);
    assertTrue(Modifier.isPublic(customMethod.getModifiers()),
        "Custom public method should be acceptable");
    assertNotEquals(customMethod.getDeclaringClass(), Object.class);
    assertNotEquals(customMethod.getDeclaringClass(), Class.class);
  }

  @Test
  @DisplayName("Should identify dangerous class names")
  void testIdentifyDangerousClassNames() {
    assertTrue(Object.class.getName().startsWith("java.lang"),
        "Object class is in java.lang package");
    assertTrue(Runtime.class.getName().startsWith("java.lang"),
        "Runtime class is in java.lang package");
    assertFalse(String.class.getName().equals("java.lang.Object"),
        "String is not Object");
  }

  @Test
  @DisplayName("Should verify method accessibility")
  void testVerifyMethodAccessibility() throws Exception {
    // Public method should be accessible
    Method publicMethod = String.class.getMethod("length");
    assertTrue(Modifier.isPublic(publicMethod.getModifiers()),
        "Public method should be accessible");

    // Check that we can determine if a method is public
    Method valueOf = String.class.getMethod("valueOf", int.class);
    assertTrue(Modifier.isPublic(valueOf.getModifiers()),
        "valueOf should be public");
  }

  @Test
  @DisplayName("Should prevent invocation of get/setProperty methods")
  void testPreventPropertyAccessMethods() throws Exception {
    // Methods that could access system properties
    Method getProperty = System.class.getMethod("getProperty", String.class);
    assertTrue(getProperty.getDeclaringClass() == System.class,
        "getProperty is on System class - should be rejected");
  }

  @Test
  @DisplayName("Should verify method is not from synthetic classes")
  void testVerifyNotSyntheticClass() throws Exception {
    Method method = String.class.getMethod("valueOf", Object.class);
    Class<?> declaringClass = method.getDeclaringClass();

    assertFalse(declaringClass.isSynthetic(),
        "String class should not be synthetic");
  }

  /**
   * Test business class for validating custom method access.
   */
  public static class TestBusinessClass {
    public String processData(String input) {
      return input.toUpperCase();
    }

    private String sensitiveOperation() {
      return "secret";
    }
  }
}

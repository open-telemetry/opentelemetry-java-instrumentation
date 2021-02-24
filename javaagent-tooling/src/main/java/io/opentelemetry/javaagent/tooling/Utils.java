/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.javaagent.bootstrap.AgentClassLoader;
import io.opentelemetry.javaagent.bootstrap.AgentClassLoader.BootstrapClassLoaderProxy;
import java.net.URL;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDefinition;

public class Utils {

  private static final BootstrapClassLoaderProxy unitTestBootstrapProxy =
      new BootstrapClassLoaderProxy(new URL[0]);

  /** Return the classloader the core agent is running on. */
  public static ClassLoader getAgentClassLoader() {
    return AgentInstaller.class.getClassLoader();
  }

  /** Return a classloader which can be used to look up bootstrap resources. */
  public static BootstrapClassLoaderProxy getBootstrapProxy() {
    if (getAgentClassLoader() instanceof AgentClassLoader) {
      return ((AgentClassLoader) getAgentClassLoader()).getBootstrapProxy();
    }
    // in a unit test
    return unitTestBootstrapProxy;
  }

  /** com.foo.Bar to com/foo/Bar.class */
  public static String getResourceName(String className) {
    return className.replace('.', '/') + ".class";
  }

  /** com/foo/Bar to com.foo.Bar */
  public static String getClassName(String internalName) {
    return internalName.replace('/', '.');
  }

  /** com.foo.Bar to com/foo/Bar */
  public static String getInternalName(Class<?> clazz) {
    return clazz.getName().replace('.', '/');
  }

  /**
   * Convert class name to a format that can be used as part of inner class name by replacing all
   * '.'s with '$'s.
   *
   * @param className class named to be converted
   * @return converted name
   */
  public static String convertToInnerClassName(String className) {
    return className.replace('.', '$');
  }

  /**
   * Get method definition for given {@link TypeDefinition} and method name.
   *
   * @param type type
   * @param methodName method name
   * @return {@link MethodDescription} for given method
   * @throws IllegalStateException if more then one method matches (i.e. in case of overloaded
   *     methods) or if no method found
   */
  public static MethodDescription getMethodDefinition(TypeDefinition type, String methodName) {
    return type.getDeclaredMethods().filter(named(methodName)).getOnly();
  }

  /** Returns the current stack trace with multiple entries on new lines. */
  public static String getStackTraceAsString() {
    StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
    StringBuilder stringBuilder = new StringBuilder();
    String lineSeparator = System.getProperty("line.separator");
    for (StackTraceElement element : stackTrace) {
      stringBuilder.append(element.toString());
      stringBuilder.append(lineSeparator);
    }
    return stringBuilder.toString();
  }

  private Utils() {}
}

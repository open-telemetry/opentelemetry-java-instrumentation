/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.opentelemetry.auto.tooling;

import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.auto.bootstrap.AgentClassLoader;
import io.opentelemetry.auto.bootstrap.AgentClassLoader.BootstrapClassLoaderProxy;
import java.lang.reflect.Method;
import java.net.URL;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDefinition;

public class Utils {

  // This is used in HelperInjectionTest.groovy
  private static Method findLoadedClassMethod = null;

  private static final BootstrapClassLoaderProxy unitTestBootstrapProxy =
      new BootstrapClassLoaderProxy(new URL[0]);

  static {
    try {
      findLoadedClassMethod = ClassLoader.class.getDeclaredMethod("findLoadedClass", String.class);
    } catch (final NoSuchMethodException | SecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  /** Return the classloader the core agent is running on. */
  public static ClassLoader getAgentClassLoader() {
    return AgentInstaller.class.getClassLoader();
  }

  /** Return a classloader which can be used to look up bootstrap resources. */
  public static BootstrapClassLoaderProxy getBootstrapProxy() {
    if (getAgentClassLoader() instanceof AgentClassLoader) {
      return ((AgentClassLoader) getAgentClassLoader()).getBootstrapProxy();
    } else {
      // in a unit test
      return unitTestBootstrapProxy;
    }
  }

  /** com.foo.Bar -> com/foo/Bar.class */
  public static String getResourceName(final String className) {
    if (!className.endsWith(".class")) {
      return className.replace('.', '/') + ".class";
    } else {
      return className;
    }
  }

  /** com/foo/Bar.class -> com.foo.Bar */
  public static String getClassName(final String resourceName) {
    return resourceName.replaceAll("\\.class\\$", "").replace('/', '.');
  }

  /** com.foo.Bar -> com/foo/Bar */
  public static String getInternalName(final String resourceName) {
    return resourceName.replaceAll("\\.class\\$", "").replace('.', '/');
  }

  /**
   * Convert class name to a format that can be used as part of inner class name by replacing all
   * '.'s with '$'s.
   *
   * @param className class named to be converted
   * @return convertd name
   */
  public static String converToInnerClassName(final String className) {
    return className.replaceAll("\\.", "\\$");
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
  public static MethodDescription getMethodDefinition(
      final TypeDefinition type, final String methodName) {
    return type.getDeclaredMethods().filter(named(methodName)).getOnly();
  }

  /** @return The current stack trace with multiple entries on new lines. */
  public static String getStackTraceAsString() {
    final StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
    final StringBuilder stringBuilder = new StringBuilder();
    final String lineSeparator = System.getProperty("line.separator");
    for (final StackTraceElement element : stackTrace) {
      stringBuilder.append(element.toString());
      stringBuilder.append(lineSeparator);
    }
    return stringBuilder.toString();
  }

  private Utils() {}
}

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.testing.common;

import java.lang.reflect.Method;

public final class AgentClassLoaderAccess {

  private static final ClassLoader agentClassLoader;

  static {
    try {
      Class<?> agentInitializerClass =
          ClassLoader.getSystemClassLoader()
              .loadClass("io.opentelemetry.javaagent.bootstrap.AgentInitializer");
      Method getExtensionsClassLoader =
          agentInitializerClass.getDeclaredMethod("getExtensionsClassLoader");
      agentClassLoader = (ClassLoader) getExtensionsClassLoader.invoke(null);
    } catch (Throwable t) {
      throw new AssertionError("Could not access agent classLoader", t);
    }
  }

  // public for use by downstream distros
  public static Class<?> loadClass(String name) {
    try {
      return agentClassLoader.loadClass(name);
    } catch (ClassNotFoundException e) {
      throw new AssertionError("Could not load class from agent classloader", e);
    }
  }

  private AgentClassLoaderAccess() {}
}

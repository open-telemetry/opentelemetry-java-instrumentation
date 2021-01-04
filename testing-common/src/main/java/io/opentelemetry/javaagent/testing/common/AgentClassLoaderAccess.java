/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.testing.common;

import java.lang.reflect.Field;

public final class AgentClassLoaderAccess {

  private static final ClassLoader agentClassLoader;

  static {
    try {
      Class<?> agentInitializerClass =
          ClassLoader.getSystemClassLoader()
              .loadClass("io.opentelemetry.javaagent.bootstrap.AgentInitializer");
      Field agentClassloader = agentInitializerClass.getDeclaredField("AGENT_CLASSLOADER");
      agentClassloader.setAccessible(true);
      agentClassLoader = (ClassLoader) agentClassloader.get(null);
    } catch (Throwable t) {
      throw new Error("Could not access agent classLoader");
    }
  }

  public static ClassLoader getAgentClassLoader() {
    return agentClassLoader;
  }

  static Class<?> loadClass(String name) {
    try {
      return agentClassLoader.loadClass(name);
    } catch (ClassNotFoundException e) {
      throw new Error("Could not load class from agent classloader", e);
    }
  }

  private AgentClassLoaderAccess() {}
}

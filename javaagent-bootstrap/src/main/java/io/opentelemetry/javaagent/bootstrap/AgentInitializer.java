/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Agent start up logic.
 *
 * <p>This class is loaded and called by {@code io.opentelemetry.javaagent.OpenTelemetryAgent}
 *
 * <p>The intention is for this class to be loaded by bootstrap classloader to make sure we have
 * unimpeded access to the rest of agent parts.
 */
public final class AgentInitializer {

  // Accessed via reflection from tests.
  // fields must be managed under class lock
  @Nullable private static ClassLoader agentClassLoader = null;

  // called via reflection in the OpenTelemetryAgent class
  public static void initialize(Instrumentation inst, URL bootstrapUrl) throws Exception {
    startAgent(inst, bootstrapUrl);
  }

  private static synchronized void startAgent(Instrumentation inst, URL bootstrapUrl)
      throws Exception {
    if (agentClassLoader == null) {
      agentClassLoader = createAgentClassLoader("inst", bootstrapUrl);

      Class<?> agentInstallerClass =
          agentClassLoader.loadClass("io.opentelemetry.javaagent.tooling.AgentInstaller");
      Method agentInstallerMethod =
          agentInstallerClass.getMethod("installBytebuddyAgent", Instrumentation.class);
      ClassLoader savedContextClassLoader = Thread.currentThread().getContextClassLoader();
      try {
        Thread.currentThread().setContextClassLoader(agentClassLoader);
        agentInstallerMethod.invoke(null, inst);
      } finally {
        Thread.currentThread().setContextClassLoader(savedContextClassLoader);
      }
    }
  }

  // TODO misleading name
  public static synchronized ClassLoader getAgentClassLoader() {
    return agentClassLoader;
  }

  /**
   * Create the agent classloader. This must be called after the bootstrap jar has been appended to
   * the bootstrap classpath.
   *
   * @param innerJarFilename Filename of internal jar to use for the classpath of the agent
   *     classloader
   * @return Agent Classloader
   */
  @SuppressWarnings("unchecked")
  private static ClassLoader createAgentClassLoader(String innerJarFilename, URL bootstrapUrl)
      throws Exception {
    ClassLoader agentParent;
    if (isJavaBefore9()) {
      agentParent = null; // bootstrap
    } else {
      // platform classloader is parent of system in java 9+
      agentParent = getPlatformClassLoader();
    }

    Class<?> loaderClass =
        ClassLoader.getSystemClassLoader()
            .loadClass("io.opentelemetry.javaagent.bootstrap.AgentClassLoader");
    Constructor<ClassLoader> constructor =
        (Constructor<ClassLoader>)
            loaderClass.getDeclaredConstructor(URL.class, String.class, ClassLoader.class);
    ClassLoader agentClassLoader =
        constructor.newInstance(bootstrapUrl, innerJarFilename, agentParent);

    Class<?> extensionClassLoaderClass =
        agentClassLoader.loadClass("io.opentelemetry.javaagent.tooling.ExtensionClassLoader");
    return (ClassLoader)
        extensionClassLoaderClass
            .getDeclaredMethod("getInstance", ClassLoader.class)
            .invoke(null, agentClassLoader);
  }

  private static ClassLoader getPlatformClassLoader()
      throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    /*
     Must invoke ClassLoader.getPlatformClassLoader by reflection to remain
     compatible with java 8.
    */
    Method method = ClassLoader.class.getDeclaredMethod("getPlatformClassLoader");
    return (ClassLoader) method.invoke(null);
  }

  public static boolean isJavaBefore9() {
    return System.getProperty("java.version").startsWith("1.");
  }

  private AgentInitializer() {}
}

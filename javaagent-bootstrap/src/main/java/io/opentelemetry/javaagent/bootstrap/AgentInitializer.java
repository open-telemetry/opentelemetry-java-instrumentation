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

/**
 * Agent start up logic.
 *
 * <p>This class is loaded and called by {@code io.opentelemetry.javaagent.OpenTelemetryAgent}
 *
 * <p>The intention is for this class to be loaded by bootstrap classloader to make sure we have
 * unimpeded access to the rest of agent parts.
 */
public class AgentInitializer {

  // Accessed via reflection from tests.
  // fields must be managed under class lock
  private static ClassLoader AGENT_CLASSLOADER = null;

  // called via reflection in the OpenTelemetryAgent class
  public static void initialize(Instrumentation inst, URL bootstrapUrl) throws Exception {
    startAgent(inst, bootstrapUrl);
  }

  private static synchronized void startAgent(Instrumentation inst, URL bootstrapUrl)
      throws Exception {
    if (AGENT_CLASSLOADER == null) {
      ClassLoader agentClassLoader = createAgentClassLoader("inst", bootstrapUrl);
      Class<?> agentInstallerClass =
          agentClassLoader.loadClass("io.opentelemetry.javaagent.tooling.AgentInstaller");
      Method agentInstallerMethod =
          agentInstallerClass.getMethod("installBytebuddyAgent", Instrumentation.class);
      ClassLoader savedContextClassLoader = Thread.currentThread().getContextClassLoader();
      try {
        Thread.currentThread().setContextClassLoader(AGENT_CLASSLOADER);
        agentInstallerMethod.invoke(null, inst);
      } finally {
        Thread.currentThread().setContextClassLoader(savedContextClassLoader);
      }
      AGENT_CLASSLOADER = agentClassLoader;
    }
  }

  /**
   * Create the agent classloader. This must be called after the bootstrap jar has been appended to
   * the bootstrap classpath.
   *
   * @param innerJarFilename Filename of internal jar to use for the classpath of the agent
   *     classloader
   * @return Agent Classloader
   */
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
    Constructor constructor =
        loaderClass.getDeclaredConstructor(URL.class, String.class, ClassLoader.class);
    return (ClassLoader) constructor.newInstance(bootstrapUrl, innerJarFilename, agentParent);
  }

  private static ClassLoader getPlatformClassLoader()
      throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    /*
     Must invoke ClassLoader.getPlatformClassLoader by reflection to remain
     compatible with java 7 + 8.
    */
    Method method = ClassLoader.class.getDeclaredMethod("getPlatformClassLoader");
    return (ClassLoader) method.invoke(null);
  }

  public static boolean isJavaBefore9() {
    return System.getProperty("java.version").startsWith("1.");
  }
}

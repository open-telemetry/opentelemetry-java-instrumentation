/*
 * Copyright 2017 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package datadog.trace.agent;

import java.io.*;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.jar.JarFile;

/** Entry point for initializing the agent. */
public class TracingAgent {
  private static ClassLoader AGENT_CLASSLOADER = null;

  public static void premain(final String agentArgs, final Instrumentation inst) throws Exception {
    startAgent(agentArgs, inst);
  }

  public static void agentmain(final String agentArgs, final Instrumentation inst)
      throws Exception {
    startAgent(agentArgs, inst);
  }

  private static synchronized void startAgent(final String agentArgs, final Instrumentation inst)
      throws Exception {
    if (null == AGENT_CLASSLOADER) {
      final File toolingJar =
          extractToTmpFile(
              TracingAgent.class.getClassLoader(),
              "agent-tooling-and-instrumentation.jar.zip",
              "agent-tooling-and-instrumentation.jar");
      final File bootstrapJar =
          extractToTmpFile(
              TracingAgent.class.getClassLoader(),
              "agent-bootstrap.jar.zip",
              "agent-bootstrap.jar");

      // bootstrap jar must be appended before agent classloader is created.
      inst.appendToBootstrapClassLoaderSearch(new JarFile(bootstrapJar));
      final ClassLoader agentClassLoader = createDatadogClassLoader(bootstrapJar, toolingJar);

      final ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
      try {
        Thread.currentThread().setContextClassLoader(agentClassLoader);

        { // install agent
          final Class<?> agentInstallerClass =
              agentClassLoader.loadClass("datadog.trace.agent.tooling.AgentInstaller");
          final Method agentInstallerMethod =
              agentInstallerClass.getMethod("installBytebuddyAgent", Instrumentation.class);
          agentInstallerMethod.invoke(null, inst);
        }
        { // install global tracer
          final Class<?> tracerInstallerClass =
              agentClassLoader.loadClass("datadog.trace.agent.tooling.TracerInstaller");
          final Method tracerInstallerMethod =
              tracerInstallerClass.getMethod("installGlobalTracer");
          tracerInstallerMethod.invoke(null);
          final Method logVersionInfoMethod = tracerInstallerClass.getMethod("logVersionInfo");
          logVersionInfoMethod.invoke(null);
        }

        AGENT_CLASSLOADER = agentClassLoader;
      } finally {
        Thread.currentThread().setContextClassLoader(contextLoader);
      }
    }
  }

  /**
   * Create the datadog classloader. This must be called after the bootstrap jar has been appened to
   * the bootstrap classpath.
   *
   * @param bootstrapJar datadog bootstrap jar which has been appended to the bootstrap loader
   * @param toolingJar jar to use for the classpath of the datadog classloader
   * @return Datadog Classloader
   */
  private static ClassLoader createDatadogClassLoader(File bootstrapJar, File toolingJar)
      throws Exception {
    final ClassLoader agentParent;
    final String javaVersion = System.getProperty("java.version");
    if (javaVersion.startsWith("1.7") || javaVersion.startsWith("1.8")) {
      agentParent = null; // bootstrap
    } else {
      // platform classloader is parent of system in java 9+
      agentParent = getPlatformClassLoader();
    }
    Class<?> loaderClass =
        ClassLoader.getSystemClassLoader()
            .loadClass("datadog.trace.agent.bootstrap.DatadogClassLoader");
    Constructor constructor =
        loaderClass.getDeclaredConstructor(URL.class, URL.class, ClassLoader.class);
    return (ClassLoader)
        constructor.newInstance(
            bootstrapJar.toURI().toURL(), toolingJar.toURI().toURL(), agentParent);
  }

  /** Extract sourcePath out of loader to a temporary file named destName. */
  private static File extractToTmpFile(ClassLoader loader, String sourcePath, String destName)
      throws Exception {
    final String destPrefix;
    final String destSuffix;
    {
      final int i = destName.lastIndexOf('.');
      if (i > 0) {
        destPrefix = destName.substring(0, i);
        destSuffix = destName.substring(i);
      } else {
        destPrefix = destName;
        destSuffix = "";
      }
    }
    InputStream inputStream = null;
    OutputStream outputStream = null;
    try {
      inputStream = loader.getResourceAsStream(sourcePath);
      if (inputStream == null) {
        throw new RuntimeException(sourcePath + ": Not found by loader: " + loader);
      }

      int readBytes;
      final byte[] buffer = new byte[4096];
      final File tmpFile = File.createTempFile(destPrefix, destSuffix);
      tmpFile.deleteOnExit();
      outputStream = new FileOutputStream(tmpFile);
      while ((readBytes = inputStream.read(buffer)) > 0) {
        outputStream.write(buffer, 0, readBytes);
      }

      return tmpFile;
    } finally {
      if (null != inputStream) {
        inputStream.close();
      }
      if (null != outputStream) {
        outputStream.close();
      }
    }
  }

  private static ClassLoader getPlatformClassLoader()
      throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    // must invoke ClassLoader.getPlatformClassLoader by reflection to remain compatible with java 7 + 8.
    final Method method = ClassLoader.class.getDeclaredMethod("getPlatformClassLoader");
    return (ClassLoader) method.invoke(null);
  }

  public static void main(final String... args) {
    try {
      System.out.println(getAgentVersion());
    } catch (Exception e) {
      System.out.println("Failed to parse agent version");
      e.printStackTrace();
    }
  }

  /**
   * Read version file out of the agent jar.
   *
   * @return Agent version
   */
  public static String getAgentVersion() throws Exception {
    BufferedReader output = null;
    InputStreamReader input = null;
    final StringBuilder sb = new StringBuilder();
    try {
      input =
          new InputStreamReader(
              TracingAgent.class.getResourceAsStream("/dd-java-agent.version"), "UTF-8");
      output = new BufferedReader(input);
      for (int c = output.read(); c != -1; c = output.read()) sb.append((char) c);
    } finally {
      if (null != input) {
        input.close();
      }
      if (null != output) {
        output.close();
      }
    }
    return sb.toString().trim();
  }
}

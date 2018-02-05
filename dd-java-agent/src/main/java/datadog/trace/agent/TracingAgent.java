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
import java.lang.reflect.Method;
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
      final JarFile toolingJar =
          new JarFile(
              extractToTmpFile(
                  TracingAgent.class.getClassLoader(),
                  "agent-tooling-and-instrumentation.jar.zip",
                  "agent-tooling-and-instrumentation.jar"));
      final JarFile bootStrapJar =
          new JarFile(
              extractToTmpFile(
                  TracingAgent.class.getClassLoader(),
                  "agent-bootstrap.jar.zip",
                  "agent-bootstrap.jar"));

      final ClassLoader agentClassLoader = TracingAgent.class.getClassLoader();

      inst.appendToSystemClassLoaderSearch(bootStrapJar);
      inst.appendToSystemClassLoaderSearch(toolingJar);

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
        final Method tracerInstallerMethod = tracerInstallerClass.getMethod("installGlobalTracer");
        tracerInstallerMethod.invoke(null);
        // TODO
        // - assert global tracer class is on bootstrap
        // - assert global tracer impl class is on agent classloader
        final Method logVersionInfoMethod = tracerInstallerClass.getMethod("logVersionInfo");
        logVersionInfoMethod.invoke(null);
      }

      AGENT_CLASSLOADER = agentClassLoader;
    }
  }

  /**
   * Extract {@param loader}'s resource, {@param sourcePath}, to a temporary file named {@param
   * destName}.
   */
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

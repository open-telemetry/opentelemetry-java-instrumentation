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

package io.opentelemetry.auto.bootstrap;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.instrument.Instrumentation;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.CodeSource;
import java.util.Arrays;
import java.util.List;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Entry point for initializing the agent.
 *
 * <p>The bootstrap process of the agent is somewhat complicated and care has to be taken to make
 * sure things do not get broken by accident.
 *
 * <p>JVM loads this class onto app's classloader, afterwards agent needs to inject its classes onto
 * bootstrap classpath. This leads to this class being visible on bootstrap. This in turn means that
 * this class may be loaded again on bootstrap by accident if we ever reference it after bootstrap
 * has been setup.
 *
 * <p>In order to avoid this we need to make sure we do a few things:
 *
 * <ul>
 *   <li>Do as little as possible here
 *   <li>Never reference this class after we have setup bootstrap and jumped over to 'real' agent
 *       code
 *   <li>Do not store any static data in this class
 *   <li>Do dot touch any logging facilities here so we can configure them later
 * </ul>
 */
public class AgentBootstrap {
  private static final Class<?> thisClass = AgentBootstrap.class;

  public static void premain(final String agentArgs, final Instrumentation inst) {
    agentmain(agentArgs, inst);
  }

  public static void agentmain(final String agentArgs, final Instrumentation inst) {
    try {

      URL bootstrapURL = installBootstrapJar(inst);

      Class<?> agentClass =
          ClassLoader.getSystemClassLoader().loadClass("io.opentelemetry.auto.bootstrap.Agent");
      Method startMethod = agentClass.getMethod("start", Instrumentation.class, URL.class);
      startMethod.invoke(null, inst, bootstrapURL);
    } catch (final Throwable ex) {
      // Don't rethrow.  We don't have a log manager here, so just print.
      System.err.println("ERROR " + thisClass.getName());
      ex.printStackTrace();
    }
  }

  private static synchronized URL installBootstrapJar(final Instrumentation inst)
      throws IOException, URISyntaxException {
    URL javaAgentJarURL = null;

    // First try Code Source
    CodeSource codeSource = thisClass.getProtectionDomain().getCodeSource();

    if (codeSource != null) {
      javaAgentJarURL = codeSource.getLocation();
      File bootstrapFile = new File(javaAgentJarURL.toURI());

      if (!bootstrapFile.isDirectory()) {
        checkJarManifestMainClassIsThis(javaAgentJarURL);
        inst.appendToBootstrapClassLoaderSearch(new JarFile(bootstrapFile));
        return javaAgentJarURL;
      }
    }

    System.out.println("Could not get bootstrap jar from code source, using -javaagent arg");

    // ManagementFactory indirectly references java.util.logging.LogManager
    // - On Oracle-based JDKs after 1.8
    // - On IBM-based JDKs since at least 1.7
    // This prevents custom log managers from working correctly
    // Use reflection to bypass the loading of the class
    List<String> arguments = getVMArgumentsThroughReflection();

    String agentArgument = null;
    for (String arg : arguments) {
      if (arg.startsWith("-javaagent")) {
        if (agentArgument == null) {
          agentArgument = arg;
        } else {
          throw new RuntimeException(
              "Multiple javaagents specified and code source unavailable, not installing tracing agent");
        }
      }
    }

    if (agentArgument == null) {
      throw new RuntimeException(
          "Could not find javaagent parameter and code source unavailable, not installing tracing agent");
    }

    // argument is of the form -javaagent:/path/to/java-agent.jar=optionalargumentstring
    Matcher matcher = Pattern.compile("-javaagent:([^=]+).*").matcher(agentArgument);

    if (!matcher.matches()) {
      throw new RuntimeException("Unable to parse javaagent parameter: " + agentArgument);
    }

    File javaagentFile = new File(matcher.group(1));
    if (!(javaagentFile.exists() || javaagentFile.isFile())) {
      throw new RuntimeException("Unable to find javaagent file: " + javaagentFile);
    }
    javaAgentJarURL = javaagentFile.toURI().toURL();
    checkJarManifestMainClassIsThis(javaAgentJarURL);
    inst.appendToBootstrapClassLoaderSearch(new JarFile(javaagentFile));

    return javaAgentJarURL;
  }

  private static List<String> getVMArgumentsThroughReflection() {
    try {
      // Try Oracle-based
      Class managementFactoryHelperClass =
          thisClass.getClassLoader().loadClass("sun.management.ManagementFactoryHelper");

      Class vmManagementClass = thisClass.getClassLoader().loadClass("sun.management.VMManagement");

      Object vmManagement;

      try {
        vmManagement =
            managementFactoryHelperClass.getDeclaredMethod("getVMManagement").invoke(null);
      } catch (final NoSuchMethodException e) {
        // Older vm before getVMManagement() existed
        Field field = managementFactoryHelperClass.getDeclaredField("jvm");
        field.setAccessible(true);
        vmManagement = field.get(null);
        field.setAccessible(false);
      }

      return (List<String>) vmManagementClass.getMethod("getVmArguments").invoke(vmManagement);

    } catch (final ReflectiveOperationException e) {
      try { // Try IBM-based.
        Class VMClass = thisClass.getClassLoader().loadClass("com.ibm.oti.vm.VM");
        String[] argArray = (String[]) VMClass.getMethod("getVMArgs").invoke(null);
        return Arrays.asList(argArray);
      } catch (final ReflectiveOperationException e1) {
        // Fallback to default
        System.out.println(
            "WARNING: Unable to get VM args through reflection.  A custom java.util.logging.LogManager may not work correctly");

        return ManagementFactory.getRuntimeMXBean().getInputArguments();
      }
    }
  }

  private static boolean checkJarManifestMainClassIsThis(final URL jarUrl) throws IOException {
    URL manifestUrl = new URL("jar:" + jarUrl + "!/META-INF/MANIFEST.MF");
    String mainClassLine = "Main-Class: " + thisClass.getCanonicalName();
    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(manifestUrl.openStream(), StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.equals(mainClassLine)) {
          return true;
        }
      }
    }
    throw new RuntimeException(
        "opentelemetry-javaagent is not installed, because class '"
            + thisClass.getCanonicalName()
            + "' is located in '"
            + jarUrl
            + "'. Make sure you don't have this .class file anywhere, besides opentelemetry-javaagent.jar");
  }

  /**
   * Main entry point.
   *
   * @param args command line agruments
   */
  public static void main(final String... args) {
    try {
      System.out.println(getAgentVersion());
    } catch (final Exception e) {
      System.out.println("Failed to parse agent version");
      e.printStackTrace();
    }
  }

  /**
   * Read version file out of the agent jar.
   *
   * @return Agent version
   */
  public static String getAgentVersion() throws IOException {
    StringBuilder sb = new StringBuilder();
    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(
                thisClass.getResourceAsStream("/opentelemetry-javaagent.version"),
                StandardCharsets.UTF_8))) {

      for (int c = reader.read(); c != -1; c = reader.read()) {
        sb.append((char) c);
      }
    }

    return sb.toString().trim();
  }
}

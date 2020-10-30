/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent;

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
 * Premain-Class for the OpenTelemetry Java agent.
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
public class OpenTelemetryAgent {
  private static final Class<?> thisClass = OpenTelemetryAgent.class;

  public static void premain(String agentArgs, Instrumentation inst) {
    agentmain(agentArgs, inst);
  }

  public static void agentmain(String agentArgs, Instrumentation inst) {
    try {

      URL bootstrapURL = installBootstrapJar(inst);

      Class<?> agentInitializerClass =
          ClassLoader.getSystemClassLoader()
              .loadClass("io.opentelemetry.javaagent.bootstrap.AgentInitializer");
      Method startMethod =
          agentInitializerClass.getMethod("initialize", Instrumentation.class, URL.class);
      startMethod.invoke(null, inst, bootstrapURL);
    } catch (Throwable ex) {
      // Don't rethrow.  We don't have a log manager here, so just print.
      System.err.println("ERROR " + thisClass.getName());
      ex.printStackTrace();
    }

    try {
      // Call oshi.SystemInfo.getCurrentPlatformEnum() to activate SystemMetrics.
      // Oshi instrumentation will intercept this call and enable SystemMetrics.
      Class<?> oshiSystemInfoClass = ClassLoader.getSystemClassLoader().loadClass("oshi.SystemInfo");
      Method getCurrentPlatformEnumMethod = oshiSystemInfoClass.getMethod("getCurrentPlatformEnum");
      getCurrentPlatformEnumMethod.invoke(null);
    } catch (Throwable ex) {
      // OK
    }
  }

  private static synchronized URL installBootstrapJar(Instrumentation inst)
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
      } catch (NoSuchMethodException e) {
        // Older vm before getVMManagement() existed
        Field field = managementFactoryHelperClass.getDeclaredField("jvm");
        field.setAccessible(true);
        vmManagement = field.get(null);
        field.setAccessible(false);
      }

      return (List<String>) vmManagementClass.getMethod("getVmArguments").invoke(vmManagement);

    } catch (ReflectiveOperationException e) {
      try { // Try IBM-based.
        Class VMClass = thisClass.getClassLoader().loadClass("com.ibm.oti.vm.VM");
        String[] argArray = (String[]) VMClass.getMethod("getVMArgs").invoke(null);
        return Arrays.asList(argArray);
      } catch (ReflectiveOperationException e1) {
        // Fallback to default
        System.out.println(
            "WARNING: Unable to get VM args through reflection.  A custom java.util.logging.LogManager may not work correctly");

        return ManagementFactory.getRuntimeMXBean().getInputArguments();
      }
    }
  }

  private static boolean checkJarManifestMainClassIsThis(URL jarUrl) throws IOException {
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
  public static void main(String... args) {
    try {
      System.out.println(OpenTelemetryAgent.class.getPackage().getImplementationVersion());
    } catch (Exception e) {
      System.out.println("Failed to parse agent version");
      e.printStackTrace();
    }
  }
}

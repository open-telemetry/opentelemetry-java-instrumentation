/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent;

import io.opentelemetry.javaagent.bootstrap.AgentInitializer;
import java.io.File;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.net.URISyntaxException;
import java.security.CodeSource;
import java.util.Arrays;
import java.util.List;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
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
// Too early for logging
@SuppressWarnings("SystemOut")
public final class OpenTelemetryAgent {
  private static final Class<?> thisClass = OpenTelemetryAgent.class;

  public static void premain(String agentArgs, Instrumentation inst) {
    agentmain(agentArgs, inst);
  }

  public static void agentmain(String agentArgs, Instrumentation inst) {
    try {
      File javaagentFile = installBootstrapJar(inst);
      AgentInitializer.initialize(inst, javaagentFile);
    } catch (Throwable ex) {
      // Don't rethrow.  We don't have a log manager here, so just print.
      System.err.println("ERROR " + thisClass.getName());
      ex.printStackTrace();
    }
  }

  private static synchronized File installBootstrapJar(Instrumentation inst)
      throws IOException, URISyntaxException {

    // First try Code Source
    CodeSource codeSource = thisClass.getProtectionDomain().getCodeSource();

    if (codeSource != null) {
      File javaagentFile = new File(codeSource.getLocation().toURI());

      if (javaagentFile.isFile()) {
        // passing verify false for vendors who sign the agent jar, because jar file signature
        // verification is very slow before the JIT compiler starts up, which on Java 8 is not until
        // after premain executes
        JarFile agentJar = new JarFile(javaagentFile, false);
        verifyJarManifestMainClassIsThis(javaagentFile, agentJar);
        inst.appendToBootstrapClassLoaderSearch(agentJar);
        return javaagentFile;
      }
    }

    System.out.println("Could not get bootstrap jar from code source, using -javaagent arg");

    // ManagementFactory indirectly references java.util.logging.LogManager
    // - On Oracle-based JDKs after 1.8
    // - On IBM-based JDKs since at least 1.7
    // This prevents custom log managers from working correctly
    // Use reflection to bypass the loading of the class
    List<String> arguments = getVmArgumentsThroughReflection();

    String agentArgument = null;
    for (String arg : arguments) {
      if (arg.startsWith("-javaagent")) {
        if (agentArgument == null) {
          agentArgument = arg;
        } else {
          throw new IllegalStateException(
              "Multiple javaagents specified and code source unavailable, "
                  + "not installing tracing agent");
        }
      }
    }

    if (agentArgument == null) {
      throw new IllegalStateException(
          "Could not find javaagent parameter and code source unavailable, "
              + "not installing tracing agent");
    }

    // argument is of the form -javaagent:/path/to/java-agent.jar=optionalargumentstring
    Matcher matcher = Pattern.compile("-javaagent:([^=]+).*").matcher(agentArgument);

    if (!matcher.matches()) {
      throw new IllegalStateException("Unable to parse javaagent parameter: " + agentArgument);
    }

    File javaagentFile = new File(matcher.group(1));
    if (!javaagentFile.isFile()) {
      throw new IllegalStateException("Unable to find javaagent file: " + javaagentFile);
    }

    JarFile agentJar = new JarFile(javaagentFile, false);
    verifyJarManifestMainClassIsThis(javaagentFile, agentJar);
    inst.appendToBootstrapClassLoaderSearch(agentJar);
    return javaagentFile;
  }

  private static List<String> getVmArgumentsThroughReflection() {
    ClassLoader classLoader = ClassLoader.getSystemClassLoader();
    try {
      // Try Oracle-based
      Class managementFactoryHelperClass =
          classLoader.loadClass("sun.management.ManagementFactoryHelper");

      Class vmManagementClass = classLoader.loadClass("sun.management.VMManagement");

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
        Class vmClass = classLoader.loadClass("com.ibm.oti.vm.VM");
        String[] argArray = (String[]) vmClass.getMethod("getVMArgs").invoke(null);
        return Arrays.asList(argArray);
      } catch (ReflectiveOperationException e1) {
        // Fallback to default
        System.out.println(
            "WARNING: Unable to get VM args through reflection. "
                + "A custom java.util.logging.LogManager may not work correctly");

        return ManagementFactory.getRuntimeMXBean().getInputArguments();
      }
    }
  }

  // this protects against the case where someone adds the contents of opentelemetry-javaagent.jar
  // by mistake to their application's "uber.jar"
  //
  // the reason this can cause issues is because we locate the agent jar based on the CodeSource of
  // the OpenTelemetryAgent class, and then we add that jar file to the bootstrap class path
  //
  // but if we find the OpenTelemetryAgent class in an uber jar file, and we add that (whole) uber
  // jar file to the bootstrap class loader, that can cause some applications to break, as there's a
  // lot of application and library code that doesn't handle getClassLoader() returning null
  // (e.g. https://github.com/qos-ch/logback/pull/291)
  private static void verifyJarManifestMainClassIsThis(File jarFile, JarFile agentJar)
      throws IOException {
    Manifest manifest = agentJar.getManifest();
    if (manifest.getMainAttributes().getValue("Premain-Class") == null) {
      throw new IllegalStateException(
          "The agent was not installed, because the agent was found in '"
              + jarFile
              + "', which doesn't contain a Premain-Class manifest attribute. Make sure that you"
              + " haven't included the agent jar file inside of an application uber jar.");
    }
  }

  /**
   * Main entry point.
   *
   * @param args command line arguments
   */
  public static void main(String... args) {
    try {
      System.out.println(OpenTelemetryAgent.class.getPackage().getImplementationVersion());
    } catch (RuntimeException e) {
      System.out.println("Failed to parse agent version");
      e.printStackTrace();
    }
  }

  private OpenTelemetryAgent() {}
}

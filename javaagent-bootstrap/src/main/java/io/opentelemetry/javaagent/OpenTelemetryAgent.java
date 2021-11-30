/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent;

import io.opentelemetry.javaagent.bootstrap.AgentInitializer;
import io.opentelemetry.javaagent.bootstrap.InstrumentationHolder;
import java.io.File;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.net.URISyntaxException;
import java.security.CodeSource;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

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

  public static void premain(String agentArgs, Instrumentation inst) {
    startAgent(inst, true);
  }

  public static void agentmain(String agentArgs, Instrumentation inst) {
    startAgent(inst, false);
  }

  private static void startAgent(Instrumentation inst, boolean fromPremain) {
    try {
      File javaagentFile = installBootstrapJar(inst);
      InstrumentationHolder.setInstrumentation(inst);
      AgentInitializer.initialize(inst, javaagentFile, fromPremain);
    } catch (Throwable ex) {
      // Don't rethrow.  We don't have a log manager here, so just print.
      System.err.println("ERROR " + OpenTelemetryAgent.class.getName());
      ex.printStackTrace();
    }
  }

  private static synchronized File installBootstrapJar(Instrumentation inst)
      throws IOException, URISyntaxException {

    CodeSource codeSource = OpenTelemetryAgent.class.getProtectionDomain().getCodeSource();

    if (codeSource == null) {
      throw new IllegalStateException("could not get agent jar location");
    }

    File javaagentFile = new File(codeSource.getLocation().toURI());

    if (!javaagentFile.isFile()) {
      throw new IllegalStateException(
          "agent jar location doesn't appear to be a file: " + javaagentFile.getAbsolutePath());
    }

    // passing verify false for vendors who sign the agent jar, because jar file signature
    // verification is very slow before the JIT compiler starts up, which on Java 8 is not until
    // after premain execution completes
    JarFile agentJar = new JarFile(javaagentFile, false);
    verifyJarManifestMainClassIsThis(javaagentFile, agentJar);
    inst.appendToBootstrapClassLoaderSearch(agentJar);
    return javaagentFile;
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

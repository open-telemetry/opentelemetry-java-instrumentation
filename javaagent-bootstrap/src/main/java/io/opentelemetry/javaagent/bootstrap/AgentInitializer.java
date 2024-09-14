/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap;

import io.opentelemetry.instrumentation.api.internal.ConfigPropertiesUtil;
import java.io.File;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Constructor;
import java.security.PrivilegedAction;
import java.security.PrivilegedExceptionAction;
import javax.annotation.Nullable;

/**
 * Agent start up logic.
 *
 * <p>This class is loaded and called by {@code io.opentelemetry.javaagent.OpenTelemetryAgent}
 *
 * <p>The intention is for this class to be loaded by bootstrap class loader to make sure we have
 * unimpeded access to the rest of agent parts.
 */
public final class AgentInitializer {

  @Nullable private static ClassLoader agentClassLoader = null;
  @Nullable private static AgentStarter agentStarter = null;
  private static boolean isSecurityManagerSupportEnabled = false;
  private static volatile boolean agentStarted = false;

  public static void initialize(Instrumentation inst, File javaagentFile, boolean fromPremain)
      throws Exception {
    if (agentClassLoader != null) {
      return;
    }

    // we expect that at this point agent jar has been appended to boot class path and all agent
    // classes are loaded in boot loader
    if (AgentInitializer.class.getClassLoader() != null) {
      throw new IllegalStateException("agent initializer should be loaded in boot loader");
    }

    isSecurityManagerSupportEnabled = isSecurityManagerSupportEnabled();

    // this call deliberately uses anonymous class instead of lambda because using lambdas too
    // early on early jdk8 (see isEarlyOracle18 method) causes jvm to crash. See CrashEarlyJdk8Test.
    execute(
        new PrivilegedExceptionAction<Void>() {
          @Override
          public Void run() throws Exception {
            agentClassLoader = createAgentClassLoader("inst", javaagentFile);
            agentStarter = createAgentStarter(agentClassLoader, inst, javaagentFile);
            if (!fromPremain || !delayAgentStart()) {
              agentStarter.start();
              agentStarted = true;
            }
            return null;
          }
        });
  }

  private static void execute(PrivilegedExceptionAction<Void> action) throws Exception {
    // When security manager support is enabled we use doPrivileged even if security manager is not
    // present because security manager could be installed later. ByteBuddy initialization captures
    // the access control context used during transformation. If we don't use doPrivileged here then
    // that context will not have the privileges if security manager is installed later.
    if (isSecurityManagerSupportEnabled) {
      doPrivilegedExceptionAction(action);
    } else {
      action.run();
    }
  }

  private static boolean isSecurityManagerSupportEnabled() {
    return getBoolean("otel.javaagent.experimental.security-manager-support.enabled", false);
  }

  private static boolean getBoolean(String property, boolean defaultValue) {
    // this call deliberately uses anonymous class instead of lambda because using lambdas too
    // early on early jdk8 (see isEarlyOracle18 method) causes jvm to crash. See CrashEarlyJdk8Test.
    return doPrivileged(
        new PrivilegedAction<Boolean>() {
          @Override
          public Boolean run() {
            return ConfigPropertiesUtil.getBoolean(property, defaultValue);
          }
        });
  }

  @SuppressWarnings("removal") // AccessController is deprecated for removal
  private static <T> T doPrivilegedExceptionAction(PrivilegedExceptionAction<T> action)
      throws Exception {
    return java.security.AccessController.doPrivileged(action);
  }

  @SuppressWarnings("removal") // AccessController is deprecated for removal
  private static <T> T doPrivileged(PrivilegedAction<T> action) {
    return java.security.AccessController.doPrivileged(action);
  }

  /**
   * Test whether we are running on oracle 1.8 before 1.8.0_40.
   *
   * @return true for oracle 1.8 before 1.8.0_40
   */
  private static boolean isEarlyOracle18() {
    // Java HotSpot(TM) 64-Bit Server VM or OpenJDK 64-Bit Server VM
    String vmName = System.getProperty("java.vm.name");
    if (!vmName.contains("HotSpot") && !vmName.contains("OpenJDK")) {
      return false;
    }
    // 1.8.0_31
    String javaVersion = System.getProperty("java.version");
    if (!javaVersion.startsWith("1.8")) {
      return false;
    }
    int index = javaVersion.indexOf('_');
    if (index == -1) {
      return false;
    }
    String minorVersion = javaVersion.substring(index + 1);
    try {
      int version = Integer.parseInt(minorVersion);
      if (version >= 40) {
        return false;
      }
    } catch (NumberFormatException exception) {
      return false;
    }

    return true;
  }

  private static boolean delayAgentStart() {
    if (!isEarlyOracle18()) {
      return false;
    }

    return agentStarter.delayStart();
  }

  /**
   * Call to this method is inserted into {@code sun.launcher.LauncherHelper.checkAndLoadMain()}.
   */
  @SuppressWarnings("unused")
  public static void delayedStartHook() throws Exception {
    // this call deliberately uses anonymous class instead of lambda because using lambdas too
    // early on early jdk8 (see isEarlyOracle18 method) causes jvm to crash. See CrashEarlyJdk8Test.
    execute(
        new PrivilegedExceptionAction<Void>() {
          @Override
          public Void run() {
            agentStarter.start();
            agentStarted = true;
            return null;
          }
        });
  }

  /**
   * Check whether agent has started or not along with VM.
   *
   * <p>This method is used by
   * io.opentelemetry.javaagent.tooling.AgentStarterImpl#InetAddressClassFileTransformer internally
   * to check whether agent has started.
   *
   * @param vmStarted flag about whether VM has started or not.
   * @return {@code true} if agent has started or not along with VM, {@code false} otherwise.
   */
  @SuppressWarnings("unused")
  public static boolean isAgentStarted(boolean vmStarted) {
    return vmStarted && agentStarted;
  }

  public static ClassLoader getExtensionsClassLoader() {
    // agentStarter can be null when running tests
    return agentStarter != null ? agentStarter.getExtensionClassLoader() : null;
  }

  /**
   * Create the agent class loader. This must be called after the bootstrap jar has been appended to
   * the bootstrap classpath.
   *
   * @param innerJarFilename Filename of internal jar to use for the classpath of the agent class
   *     loader
   * @return Agent Classloader
   */
  private static ClassLoader createAgentClassLoader(String innerJarFilename, File javaagentFile) {
    return new AgentClassLoader(javaagentFile, innerJarFilename, isSecurityManagerSupportEnabled);
  }

  private static AgentStarter createAgentStarter(
      ClassLoader agentClassLoader, Instrumentation instrumentation, File javaagentFile)
      throws Exception {
    Class<?> starterClass =
        agentClassLoader.loadClass("io.opentelemetry.javaagent.tooling.AgentStarterImpl");
    Constructor<?> constructor =
        starterClass.getDeclaredConstructor(Instrumentation.class, File.class, boolean.class);
    return (AgentStarter)
        constructor.newInstance(instrumentation, javaagentFile, isSecurityManagerSupportEnabled);
  }

  private AgentInitializer() {}
}

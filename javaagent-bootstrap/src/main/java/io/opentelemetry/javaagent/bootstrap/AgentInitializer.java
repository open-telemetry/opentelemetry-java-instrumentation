/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap;

import static java.util.Objects.requireNonNull;

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

  public static void initialize(
      Instrumentation inst, File javaagentFile, boolean fromPremain, @Nullable String agentArgs)
      throws Exception {
    if (agentClassLoader != null) {
      return;
    }

    // this call deliberately uses anonymous class instead of lambda because using lambdas too
    // early on early jdk8 (see isEarlyOracle18 method) causes jvm to crash. See CrashEarlyJdk8Test.
    doPrivileged(
        new PrivilegedAction<Void>() {
          @Override
          public Void run() {
            setSystemProperties(agentArgs);
            return null;
          }
        });

    // we expect that at this point agent jar has been appended to boot class path and all agent
    // classes are loaded in boot loader
    if (AgentInitializer.class.getClassLoader() != null) {
      throw new IllegalStateException("agent initializer should be loaded in boot loader");
    }

    // JDK tools in $JAVA_HOME/bin also run on the JVM, and they pick up JAVA_TOOL_OPTIONS or
    // _JAVA_OPTIONS when those are set globally, instrumenting them only adds startup overhead,
    // so skip them unless the agent has been explicitly enabled
    if (fromPremain && shouldSkipJdkTool()) {
      return;
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
              requireNonNull(agentStarter);
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
    return isConfigEnabled(
        "otel.javaagent.experimental.security-manager-support.enabled",
        "OTEL_JAVAAGENT_EXPERIMENTAL_SECURITY_MANAGER_SUPPORT_ENABLED");
  }

  /**
   * Test whether this JVM is running a JDK tool that should not be instrumented. Explicitly
   * enabling the agent with {@code otel.javaagent.enabled=true} overrides the detection.
   *
   * @return true when the agent should not be started
   */
  @SuppressWarnings("SystemOut")
  private static boolean shouldSkipJdkTool() {
    if (isConfigEnabled("otel.javaagent.enabled", "OTEL_JAVAAGENT_ENABLED")) {
      // explicitly enabled, instrument the tool
      return false;
    }
    String command = getJvmCommand();
    if (!isJdkToolMainClass(command)) {
      return false;
    }
    if (isConfigEnabled("otel.javaagent.debug", "OTEL_JAVAAGENT_DEBUG")) {
      // only log the command with debug enabled to avoid exposing potentially sensitive arguments
      System.err.println("JDK tool detected for command '" + command + "'");
    }
    System.err.println(
        "JDK tool detected, enable agent debug for details, agent will not be started. "
            + "To override this behavior, set otel.javaagent.enabled=true as an agent argument "
            + "or system property, or OTEL_JAVAAGENT_ENABLED=true as an environment variable");
    return true;
  }

  /**
   * Get the command that started this JVM.
   *
   * @return command, {@literal null} when not available, e.g. when the JVM was not started by the
   *     java launcher
   */
  @Nullable
  private static String getJvmCommand() {
    // this call deliberately uses anonymous class instead of lambda because using lambdas too
    // early on early jdk8 (see isEarlyOracle18 method) causes jvm to crash. See CrashEarlyJdk8Test.
    return doPrivileged(
        new PrivilegedAction<String>() {
          @Override
          public String run() {
            return System.getProperty("sun.java.command");
          }
        });
  }

  // this only reads the system property and the environment variable, the configuration file is
  // not available yet as it is read by javaagent-tooling which has not been loaded at this point
  private static boolean isConfigEnabled(String propertyName, String environmentVariableName) {
    // this call deliberately uses anonymous class instead of lambda because using lambdas too
    // early on early jdk8 (see isEarlyOracle18 method) causes jvm to crash. See CrashEarlyJdk8Test.
    return doPrivileged(
        new PrivilegedAction<Boolean>() {
          @Override
          public Boolean run() {
            String value = System.getProperty(propertyName);
            if (value == null) {
              value = System.getenv(environmentVariableName);
            }
            return Boolean.parseBoolean(value);
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
    } catch (NumberFormatException ignored) {
      return false;
    }

    return true;
  }

  private static boolean delayAgentStart() {
    if (!isEarlyOracle18()) {
      return false;
    }

    return requireNonNull(agentStarter).delayStart();
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
            requireNonNull(agentStarter).start();
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

  @Nullable
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

  @SuppressWarnings("SystemOut")
  static void setSystemProperties(@Nullable String agentArgs) {
    boolean debug = false;
    if (agentArgs != null && !agentArgs.isEmpty()) {
      for (String option : agentArgs.split(";")) {
        int i = option.indexOf('=');
        if (i < 0) {
          System.err.println("Malformed agent argument: " + option);
          continue;
        }

        String key = option.substring(0, i).trim();
        String value = option.substring(i + 1).trim();
        System.setProperty(key, value);
        if (key.equals("otel.javaagent.debug")) {
          debug = Boolean.parseBoolean(value);
        }
        if (debug) {
          System.err.println("Setting property [" + key + "] = " + value);
        }
      }
    }
  }

  static boolean isJdkToolMainClass(@Nullable String cmd) {
    if (cmd == null) {
      return false;
    }
    int spaceIndex = cmd.indexOf(' ');
    String first = spaceIndex == -1 ? cmd : cmd.substring(0, spaceIndex);

    if (first.endsWith(".jar")) {
      // java -jar /path/to/app.jar
      return false;
    }

    // sun.java.command is of the form "<module>/<mainClass>" when the main class belongs to a
    // named module, e.g. "jdk.compiler/com.sun.tools.javac.Main", match the module name then
    int slashIndex = first.indexOf('/');
    String name = slashIndex == -1 ? first : first.substring(0, slashIndex);

    return name.startsWith("java.")
        || name.startsWith("jdk.")
        || name.startsWith("sun.")
        // com.sun. is also used outside of the jdk (e.g. glassfish), match tools packages only
        || name.startsWith("com.sun.tools.")
        || name.startsWith("com.sun.corba.se.")
        || name.startsWith("com.sun.javafx.tools.")
        || name.startsWith("com.sun.java.util.jar.pack.");
  }
}

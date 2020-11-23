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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Agent start up logic.
 *
 * <p>This class is loaded and called by {@code io.opentelemetry.javaagent.OpenTelemetryAgent}
 *
 * <p>The intention is for this class to be loaded by bootstrap classloader to make sure we have
 * unimpeded access to the rest of agent parts.
 */
public class AgentInitializer {

  private static final String SIMPLE_LOGGER_SHOW_DATE_TIME_PROPERTY =
      "io.opentelemetry.javaagent.slf4j.simpleLogger.showDateTime";
  private static final String SIMPLE_LOGGER_DATE_TIME_FORMAT_PROPERTY =
      "io.opentelemetry.javaagent.slf4j.simpleLogger.dateTimeFormat";
  private static final String SIMPLE_LOGGER_DATE_TIME_FORMAT_DEFAULT =
      "'[opentelemetry.auto.trace 'yyyy-MM-dd HH:mm:ss:SSS Z']'";
  private static final String SIMPLE_LOGGER_DEFAULT_LOG_LEVEL_PROPERTY =
      "io.opentelemetry.javaagent.slf4j.simpleLogger.defaultLogLevel";

  private static final Logger log;

  static {
    // We can configure logger here because io.opentelemetry.auto.AgentBootstrap doesn't touch
    // it.
    configureLogger();
    log = LoggerFactory.getLogger(AgentInitializer.class);
  }

  // Accessed via reflection from tests.
  // fields must be managed under class lock
  private static ClassLoader AGENT_CLASSLOADER = null;

  public static void initialize(Instrumentation inst, URL bootstrapUrl) {
    startAgent(inst, bootstrapUrl);

    boolean appUsingCustomLogManager = isAppUsingCustomLogManager();

    /*
     * java.util.logging.LogManager maintains a final static LogManager, which is created during class initialization.
     *
     * JMXFetch uses jre bootstrap classes which touch this class. This means applications which require a custom log
     * manager may not have a chance to set the global log manager if jmxfetch runs first. JMXFetch will incorrectly
     * set the global log manager in cases where the app sets the log manager system property or when the log manager
     * class is not on the system classpath.
     *
     * Our solution is to delay the initialization of jmxfetch when we detect a custom log manager being used.
     *
     * Once we see the LogManager class loading, it's safe to start jmxfetch because the application is already setting
     * the global log manager and jmxfetch won't be able to touch it due to classloader locking.
     */

    /*
     * Similar thing happens with AgentTracer on (at least) zulu-8 because it uses OkHttp which indirectly loads JFR
     * events which in turn loads LogManager. This is not a problem on newer JDKs because there JFR uses different
     * logging facility.
     */
    if (isJavaBefore9WithJfr() && appUsingCustomLogManager) {
      log.debug("Custom logger detected. Delaying Agent Tracer initialization.");
      registerLogManagerCallback(new InstallAgentTracerCallback());
    } else {
      installAgentTracer();
    }
  }

  private static void registerLogManagerCallback(ClassLoadCallBack callback) {
    try {
      Class<?> agentInstallerClass =
          AGENT_CLASSLOADER.loadClass("io.opentelemetry.javaagent.tooling.AgentInstaller");
      Method registerCallbackMethod =
          agentInstallerClass.getMethod("registerClassLoadCallback", String.class, Runnable.class);
      registerCallbackMethod.invoke(null, "java.util.logging.LogManager", callback);
    } catch (Exception ex) {
      log.error("Error registering callback for " + callback.getName(), ex);
    }
  }

  protected abstract static class ClassLoadCallBack implements Runnable {

    @Override
    public void run() {
      /*
       * This callback is called from within bytecode transformer. This can be a problem if callback tries
       * to load classes being transformed. To avoid this we start a thread here that calls the callback.
       * This seems to resolve this problem.
       */
      Thread thread =
          new Thread(
              new Runnable() {
                @Override
                public void run() {
                  try {
                    execute();
                  } catch (Exception e) {
                    log.error("Failed to run class loader callback {}", getName(), e);
                  }
                }
              });
      thread.setName("agent-startup-" + getName());
      thread.setDaemon(true);
      thread.start();
    }

    public abstract String getName();

    public abstract void execute();
  }

  protected static class InstallAgentTracerCallback extends ClassLoadCallBack {

    @Override
    public String getName() {
      return "agent-tracer";
    }

    @Override
    public void execute() {
      installAgentTracer();
    }
  }

  private static synchronized void startAgent(Instrumentation inst, URL bootstrapUrl) {
    if (AGENT_CLASSLOADER == null) {
      try {
        ClassLoader agentClassLoader = createAgentClassLoader("inst", bootstrapUrl);
        Class<?> agentInstallerClass =
            agentClassLoader.loadClass("io.opentelemetry.javaagent.tooling.AgentInstaller");
        Method agentInstallerMethod =
            agentInstallerClass.getMethod("installBytebuddyAgent", Instrumentation.class);
        agentInstallerMethod.invoke(null, inst);
        AGENT_CLASSLOADER = agentClassLoader;
      } catch (Throwable ex) {
        log.error("Throwable thrown while installing the agent", ex);
      }
    }
  }

  private static synchronized void installAgentTracer() {
    if (AGENT_CLASSLOADER == null) {
      throw new IllegalStateException("Agent should have been started already");
    }
    // TracerInstaller.installAgentTracer can be called multiple times without any problem
    // so there is no need to have a 'agentTracerInstalled' flag here.
    try {
      // install global tracer
      Class<?> tracerInstallerClass =
          AGENT_CLASSLOADER.loadClass("io.opentelemetry.javaagent.tooling.TracerInstaller");
      Method tracerInstallerMethod = tracerInstallerClass.getMethod("installAgentTracer");
      tracerInstallerMethod.invoke(null);
      Method logVersionInfoMethod = tracerInstallerClass.getMethod("logVersionInfo");
      logVersionInfoMethod.invoke(null);
    } catch (Throwable ex) {
      log.error("Throwable thrown while installing the agent tracer", ex);
    }
  }

  private static void configureLogger() {
    setSystemPropertyDefault(SIMPLE_LOGGER_SHOW_DATE_TIME_PROPERTY, "true");
    setSystemPropertyDefault(
        SIMPLE_LOGGER_DATE_TIME_FORMAT_PROPERTY, SIMPLE_LOGGER_DATE_TIME_FORMAT_DEFAULT);

    if (isDebugMode()) {
      setSystemPropertyDefault(SIMPLE_LOGGER_DEFAULT_LOG_LEVEL_PROPERTY, "DEBUG");
    }
  }

  private static void setSystemPropertyDefault(String property, String value) {
    if (System.getProperty(property) == null) {
      System.setProperty(property, value);
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

  /**
   * Determine if we should log in debug level according to otel.javaagent.debug
   *
   * @return true if we should
   */
  private static boolean isDebugMode() {
    String tracerDebugLevelSysprop = "otel.javaagent.debug";
    String tracerDebugLevelProp = System.getProperty(tracerDebugLevelSysprop);

    if (tracerDebugLevelProp != null) {
      return Boolean.parseBoolean(tracerDebugLevelProp);
    }

    String tracerDebugLevelEnv =
        System.getenv(tracerDebugLevelSysprop.replace('.', '_').toUpperCase());

    if (tracerDebugLevelEnv != null) {
      return Boolean.parseBoolean(tracerDebugLevelEnv);
    }
    return false;
  }

  /**
   * Search for java or agent-tracer sysprops which indicate that a custom log manager will be used.
   * Also search for any app classes known to set a custom log manager.
   *
   * @return true if we detect a custom log manager being used.
   */
  private static boolean isAppUsingCustomLogManager() {
    String tracerCustomLogManSysprop = "otel.app.customlogmanager";
    String customLogManagerProp = System.getProperty(tracerCustomLogManSysprop);
    String customLogManagerEnv =
        System.getenv(tracerCustomLogManSysprop.replace('.', '_').toUpperCase());

    if (customLogManagerProp != null || customLogManagerEnv != null) {
      log.debug("Prop - customlogmanager: " + customLogManagerProp);
      log.debug("Env - customlogmanager: " + customLogManagerEnv);
      // Allow setting to skip these automatic checks:
      return Boolean.parseBoolean(customLogManagerProp)
          || Boolean.parseBoolean(customLogManagerEnv);
    }

    String jbossHome = System.getenv("JBOSS_HOME");
    if (jbossHome != null) {
      log.debug("Env - jboss: " + jbossHome);
      // JBoss/Wildfly is known to set a custom log manager after startup.
      // Originally we were checking for the presence of a jboss class,
      // but it seems some non-jboss applications have jboss classes on the classpath.
      // This would cause jmxfetch initialization to be delayed indefinitely.
      // Checking for an environment variable required by jboss instead.
      return true;
    }

    String logManagerProp = System.getProperty("java.util.logging.manager");
    if (logManagerProp != null) {
      boolean onSysClasspath =
          ClassLoader.getSystemResource(logManagerProp.replaceAll("\\.", "/") + ".class") != null;
      log.debug("Prop - logging.manager: " + logManagerProp);
      log.debug("logging.manager on system classpath: " + onSysClasspath);
      // Some applications set java.util.logging.manager but never actually initialize the logger.
      // Check to see if the configured manager is on the system classpath.
      // If so, it should be safe to initialize jmxfetch which will setup the log manager.
      return !onSysClasspath;
    }

    return false;
  }

  private static boolean isJavaBefore9() {
    return System.getProperty("java.version").startsWith("1.");
  }

  private static boolean isJavaBefore9WithJfr() {
    if (!isJavaBefore9()) {
      return false;
    }
    // FIXME: this is quite a hack because there maybe jfr classes on classpath somehow that have
    // nothing to do with JDK but this should be safe because only thing this does is to delay
    // tracer install
    String jfrClassResourceName = "jdk.jfr.Recording".replace('.', '/') + ".class";
    return Thread.currentThread().getContextClassLoader().getResource(jfrClassResourceName) != null;
  }
}

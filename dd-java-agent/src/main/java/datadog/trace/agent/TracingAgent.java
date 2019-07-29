package datadog.trace.agent;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.jar.JarFile;

/** Entry point for initializing the agent. */
public class TracingAgent {

  private static final String SIMPLE_LOGGER_SHOW_DATE_TIME_PROPERTY =
      "datadog.slf4j.simpleLogger.showDateTime";
  private static final String SIMPLE_LOGGER_DATE_TIME_FORMAT_PROPERTY =
      "datadog.slf4j.simpleLogger.dateTimeFormat";
  private static final String SIMPLE_LOGGER_DATE_TIME_FORMAT_DEFAULT =
      "'[dd.tracing.agent - 'yyyy-MM-dd HH:mm:ss:SSS Z']'";
  private static final String SIMPLE_LOGGER_DEFAULT_LOG_LEVEL_PROPERTY =
      "datadog.slf4j.simpleLogger.defaultLogLevel";

  // fields must be managed under class lock
  private static ClassLoader AGENT_CLASSLOADER = null;
  private static ClassLoader JMXFETCH_CLASSLOADER = null;
  private static URL BOOTSTRAP_URL = null;

  public static void premain(final String agentArgs, final Instrumentation inst) throws Exception {
    agentmain(agentArgs, inst);
  }

  public static void agentmain(final String agentArgs, final Instrumentation inst)
      throws Exception {
    configureLogger();
    startDatadogAgent(inst);
    if (isAppUsingCustomLogManager()) {
      System.out.println("Custom logger detected. Delaying JMXFetch initialization.");
      /*
       * java.util.logging.LogManager maintains a final static LogManager, which is created during class initialization.
       *
       * JMXFetch uses jre bootstrap classes which touch this class. This means applications which require a custom log manager may not have a chance to set the global log manager if jmxfetch runs first. JMXFetch will incorrectly set the global log manager in cases where the app sets the log manager system property or when the log manager class is not on the system classpath.
       *
       * Our solution is to delay the initilization of jmxfetch when we detect a custom log manager being used.
       *
       * Once we see the LogManager class loading, it's safe to start jmxfetch because the application is already setting the global log manager and jmxfetch won't be able to touch it due to classloader locking.
       */
      final Class<?> agentInstallerClass =
          AGENT_CLASSLOADER.loadClass("datadog.trace.agent.tooling.AgentInstaller");
      final Method registerCallbackMethod =
          agentInstallerClass.getMethod("registerClassLoadCallback", String.class, Runnable.class);
      registerCallbackMethod.invoke(
          null,
          "java.util.logging.LogManager",
          new Runnable() {
            @Override
            public void run() {
              try {
                startJmxFetch(inst);
              } catch (final Exception e) {
                throw new RuntimeException(e);
              }
            }
          });
    } else {
      startJmxFetch(inst);
    }
  }

  public static synchronized void startDatadogAgent(final Instrumentation inst) throws Exception {
    installBootstrapJar(inst);
    if (AGENT_CLASSLOADER == null) {
      final ClassLoader agentClassLoader =
          createDatadogClassLoader("agent-tooling-and-instrumentation.jar.zip");
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

  public static synchronized void startJmxFetch(final Instrumentation inst) throws Exception {
    installBootstrapJar(inst);
    if (JMXFETCH_CLASSLOADER == null) {
      final ClassLoader jmxFetchClassLoader = createDatadogClassLoader("agent-jmxfetch.jar.zip");
      final ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
      try {
        Thread.currentThread().setContextClassLoader(jmxFetchClassLoader);
        final Class<?> jmxFetchAgentClass =
            jmxFetchClassLoader.loadClass("datadog.trace.agent.jmxfetch.JMXFetch");
        final Method jmxFetchInstallerMethod = jmxFetchAgentClass.getMethod("run");
        jmxFetchInstallerMethod.invoke(null);
        JMXFETCH_CLASSLOADER = jmxFetchClassLoader;
      } finally {
        Thread.currentThread().setContextClassLoader(contextLoader);
      }
    }
  }

  private static void configureLogger() {
    setSystemPropertyDefault(SIMPLE_LOGGER_SHOW_DATE_TIME_PROPERTY, "true");
    setSystemPropertyDefault(
        SIMPLE_LOGGER_DATE_TIME_FORMAT_PROPERTY, SIMPLE_LOGGER_DATE_TIME_FORMAT_DEFAULT);
  }

  private static void setSystemPropertyDefault(final String property, final String value) {
    if (System.getProperty(property) == null) {
      System.setProperty(property, value);
    }
  }

  private static synchronized void installBootstrapJar(final Instrumentation inst)
      throws Exception {
    if (BOOTSTRAP_URL == null) {
      BOOTSTRAP_URL = TracingAgent.class.getProtectionDomain().getCodeSource().getLocation();

      // bootstrap jar must be appended before agent classloader is created.
      inst.appendToBootstrapClassLoaderSearch(new JarFile(new File(BOOTSTRAP_URL.toURI())));
    }
  }

  /**
   * Create the datadog classloader. This must be called after the bootstrap jar has been appened to
   * the bootstrap classpath.
   *
   * @param innerJarFilename Filename of internal jar to use for the classpath of the datadog
   *     classloader
   * @return Datadog Classloader
   */
  private static ClassLoader createDatadogClassLoader(final String innerJarFilename)
      throws Exception {
    final ClassLoader agentParent;
    final String javaVersion = System.getProperty("java.version");
    if (javaVersion.startsWith("1.7") || javaVersion.startsWith("1.8")) {
      agentParent = null; // bootstrap
    } else {
      // platform classloader is parent of system in java 9+
      agentParent = getPlatformClassLoader();
    }

    final Class<?> loaderClass =
        ClassLoader.getSystemClassLoader().loadClass("datadog.trace.bootstrap.DatadogClassLoader");
    final Constructor constructor =
        loaderClass.getDeclaredConstructor(
            URL.class, String.class, ClassLoader.class, ClassLoader.class);
    return (ClassLoader)
        constructor.newInstance(
            BOOTSTRAP_URL, innerJarFilename, TracingAgent.class.getClassLoader(), agentParent);
  }

  private static ClassLoader getPlatformClassLoader()
      throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    /*
     Must invoke ClassLoader.getPlatformClassLoader by reflection to remain
     compatible with java 7 + 8.
    */
    final Method method = ClassLoader.class.getDeclaredMethod("getPlatformClassLoader");
    return (ClassLoader) method.invoke(null);
  }

  /**
   * Search for java or datadog-tracer sysprops which indicate that a custom log manager will be
   * used. Also search for any app classes known to set a custom log manager.
   *
   * @return true if we detect a custom log manager being used.
   */
  private static boolean isAppUsingCustomLogManager() {
    final boolean debugEnabled =
        "debug".equalsIgnoreCase(System.getProperty(SIMPLE_LOGGER_DEFAULT_LOG_LEVEL_PROPERTY));

    final String tracerCustomLogManSysprop = "dd.app.customlogmanager";
    final String customLogManagerProp = System.getProperty(tracerCustomLogManSysprop);
    final String customLogManagerEnv =
        System.getenv(tracerCustomLogManSysprop.replace('.', '_').toUpperCase());

    if (customLogManagerProp != null || customLogManagerEnv != null) {
      if (debugEnabled) {
        System.out.println("Prop - customlogmanager: " + customLogManagerProp);
        System.out.println("Env - customlogmanager: " + customLogManagerEnv);
      }
      // Allow setting to skip these automatic checks:
      return Boolean.parseBoolean(customLogManagerProp)
          || Boolean.parseBoolean(customLogManagerEnv);
    }

    final String jbossHome = System.getenv("JBOSS_HOME");
    if (jbossHome != null) {
      if (debugEnabled) {
        System.out.println("Env - jboss: " + jbossHome);
      }
      // JBoss/Wildfly is known to set a custom log manager after startup.
      // Originally we were checking for the presence of a jboss class,
      // but it seems some non-jboss applications have jboss classes on the classpath.
      // This would cause jmxfetch initialization to be delayed indefinitely.
      // Checking for an environment variable required by jboss instead.
      return true;
    }

    final String logManagerProp = System.getProperty("java.util.logging.manager");
    if (logManagerProp != null) {
      final boolean onSysClasspath =
          ClassLoader.getSystemResource(logManagerProp.replaceAll("\\.", "/") + ".class") != null;
      if (debugEnabled) {
        System.out.println("Prop - logging.manager: " + logManagerProp);
        System.out.println("logging.manager on system classpath: " + onSysClasspath);
      }
      // Some applications set java.util.logging.manager but never actually initialize the logger.
      // Check to see if the configured manager is on the system classpath.
      // If so, it should be safe to initialize jmxfetch which will setup the log manager.
      return !onSysClasspath;
    }

    return false;
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
    final StringBuilder sb = new StringBuilder();
    try (final BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(
                TracingAgent.class.getResourceAsStream("/dd-java-agent.version"),
                StandardCharsets.UTF_8))) {

      for (int c = reader.read(); c != -1; c = reader.read()) {
        sb.append((char) c);
      }
    }

    return sb.toString().trim();
  }
}

package datadog.trace.bootstrap;

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
 * <p>This class is loaded and called by {@code datadog.trace.bootstrap.AgentBootstrap}
 *
 * <p>The intention is for this class to be loaded by bootstrap classloader to make sure we have
 * unimpeded access to the rest of Datadog's agent parts.
 */
// We cannot use lombok here because we need to configure logger first
public class Agent {

  private static final String SIMPLE_LOGGER_SHOW_DATE_TIME_PROPERTY =
      "datadog.slf4j.simpleLogger.showDateTime";
  private static final String SIMPLE_LOGGER_DATE_TIME_FORMAT_PROPERTY =
      "datadog.slf4j.simpleLogger.dateTimeFormat";
  private static final String SIMPLE_LOGGER_DATE_TIME_FORMAT_DEFAULT =
      "'[dd.trace 'yyyy-MM-dd HH:mm:ss:SSS Z']'";
  private static final String SIMPLE_LOGGER_DEFAULT_LOG_LEVEL_PROPERTY =
      "datadog.slf4j.simpleLogger.defaultLogLevel";

  // We cannot use lombok here because we need to configure logger first
  private static final Logger log;

  static {
    // We can configure logger here because datadog.trace.agent.AgentBootstrap doesn't touch it.
    configureLogger();
    log = LoggerFactory.getLogger(Agent.class);
  }

  // fields must be managed under class lock
  private static ClassLoader PARENT_CLASSLOADER = null;
  private static ClassLoader BOOTSTRAP_PROXY = null;
  private static ClassLoader AGENT_CLASSLOADER = null;
  private static ClassLoader JMXFETCH_CLASSLOADER = null;
  private static ClassLoader PROFILING_CLASSLOADER = null;

  public static void start(final Instrumentation inst, final URL bootstrapURL) {
    createParentClassloader(bootstrapURL);

    // Profiling agent startup code is written in a way to allow `startProfilingAgent` be called
    // multiple times
    // If early profiling is enabled then this call will start profiling.
    // If early profiling is disabled then later call will do this.
    startProfilingAgent(bootstrapURL, true);

    startDatadogAgent(inst, bootstrapURL);

    final boolean appUsingCustomLogManager = isAppUsingCustomLogManager();

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
    if (appUsingCustomLogManager) {
      log.debug("Custom logger detected. Delaying JMXFetch initialization.");
      registerLogManagerCallback(new StartJmxCallback(bootstrapURL));
    } else {
      startJmx(bootstrapURL);
    }

    /*
     * Similar thing happens with DatadogTracer on (at least) zulu-8 because it uses OkHttp which indirectly loads JFR
     * events which in turn loads LogManager. This is not a problem on newer JDKs because there JFR uses different
     * logging facility.
     */
    if (isJavaBefore9WithJFR() && appUsingCustomLogManager) {
      log.debug("Custom logger detected. Delaying Datadog Tracer initialization.");
      registerLogManagerCallback(new InstallDatadogTracerCallback(bootstrapURL));
    } else {
      installDatadogTracer();
    }

    /*
     * Similar thing happens with Profiler on (at least) zulu-8 because it uses OkHttp which indirectly loads JFR
     * events which in turn loads LogManager. This is not a problem on newer JDKs because there JFR uses different
     * logging facility.
     */
    if (isJavaBefore9() && appUsingCustomLogManager) {
      log.debug("Custom logger detected. Delaying Profiling Agent startup.");
      registerLogManagerCallback(new StartProfilingAgentCallback(inst, bootstrapURL));
    } else {
      startProfilingAgent(bootstrapURL, false);
    }
  }

  private static void registerLogManagerCallback(final ClassLoadCallBack callback) {
    try {
      final Class<?> agentInstallerClass =
          AGENT_CLASSLOADER.loadClass("datadog.trace.agent.tooling.AgentInstaller");
      final Method registerCallbackMethod =
          agentInstallerClass.getMethod("registerClassLoadCallback", String.class, Runnable.class);
      registerCallbackMethod.invoke(null, "java.util.logging.LogManager", callback);
    } catch (final Exception ex) {
      log.error("Error registering callback for " + callback.getName(), ex);
    }
  }

  protected abstract static class ClassLoadCallBack implements Runnable {

    final URL bootstrapURL;

    ClassLoadCallBack(final URL bootstrapURL) {
      this.bootstrapURL = bootstrapURL;
    }

    @Override
    public void run() {
      /*
       * This callback is called from within bytecode transformer. This can be a problem if callback tries
       * to load classes being transformed. To avoid this we start a thread here that calls the callback.
       * This seems to resolve this problem.
       */
      final Thread thread =
          new Thread(
              new Runnable() {
                @Override
                public void run() {
                  try {
                    execute();
                  } catch (final Exception e) {
                    log.error("Failed to run class loader callback {}", getName(), e);
                  }
                }
              });
      thread.setName("dd-agent-startup-" + getName());
      thread.setDaemon(true);
      thread.start();
    }

    public abstract String getName();

    public abstract void execute();
  }

  protected static class StartJmxCallback extends ClassLoadCallBack {
    StartJmxCallback(final URL bootstrapURL) {
      super(bootstrapURL);
    }

    @Override
    public String getName() {
      return "jmxfetch";
    }

    @Override
    public void execute() {
      startJmx(bootstrapURL);
    }
  }

  protected static class InstallDatadogTracerCallback extends ClassLoadCallBack {
    InstallDatadogTracerCallback(final URL bootstrapURL) {
      super(bootstrapURL);
    }

    @Override
    public String getName() {
      return "datadog-tracer";
    }

    @Override
    public void execute() {
      installDatadogTracer();
    }
  }

  protected static class StartProfilingAgentCallback extends ClassLoadCallBack {
    StartProfilingAgentCallback(final Instrumentation inst, final URL bootstrapURL) {
      super(bootstrapURL);
    }

    @Override
    public String getName() {
      return "datadog-profiler";
    }

    @Override
    public void execute() {
      startProfilingAgent(bootstrapURL, false);
    }
  }

  private static synchronized void createParentClassloader(final URL bootstrapURL) {
    if (PARENT_CLASSLOADER == null) {
      try {
        final Class<?> bootstrapProxyClass =
            ClassLoader.getSystemClassLoader()
                .loadClass("datadog.trace.bootstrap.DatadogClassLoader$BootstrapClassLoaderProxy");
        final Constructor constructor = bootstrapProxyClass.getDeclaredConstructor(URL.class);
        BOOTSTRAP_PROXY = (ClassLoader) constructor.newInstance(bootstrapURL);

        final ClassLoader grandParent;
        if (isJavaBefore9()) {
          grandParent = null; // bootstrap
        } else {
          // platform classloader is parent of system in java 9+
          grandParent = getPlatformClassLoader();
        }

        PARENT_CLASSLOADER = createDatadogClassLoader("shared.isolated", bootstrapURL, grandParent);
      } catch (final Throwable ex) {
        log.error("Throwable thrown creating parent classloader", ex);
      }
    }
  }

  private static synchronized void startDatadogAgent(
      final Instrumentation inst, final URL bootstrapURL) {
    if (AGENT_CLASSLOADER == null) {
      try {
        final ClassLoader agentClassLoader =
            createDatadogClassLoader(
                "agent-tooling-and-instrumentation.isolated", bootstrapURL, PARENT_CLASSLOADER);

        final Class<?> agentInstallerClass =
            agentClassLoader.loadClass("datadog.trace.agent.tooling.AgentInstaller");
        final Method agentInstallerMethod =
            agentInstallerClass.getMethod("installBytebuddyAgent", Instrumentation.class);
        agentInstallerMethod.invoke(null, inst);
        AGENT_CLASSLOADER = agentClassLoader;
      } catch (final Throwable ex) {
        log.error("Throwable thrown while installing the Datadog Agent", ex);
      }
    }
  }

  private static synchronized void installDatadogTracer() {
    if (AGENT_CLASSLOADER == null) {
      throw new IllegalStateException("Datadog agent should have been started already");
    }
    // TracerInstaller.installGlobalTracer can be called multiple times without any problem
    // so there is no need to have a 'datadogTracerInstalled' flag here.
    try {
      // install global tracer
      final Class<?> tracerInstallerClass =
          AGENT_CLASSLOADER.loadClass("datadog.trace.agent.tooling.TracerInstaller");
      final Method tracerInstallerMethod = tracerInstallerClass.getMethod("installGlobalTracer");
      tracerInstallerMethod.invoke(null);
      final Method logVersionInfoMethod = tracerInstallerClass.getMethod("logVersionInfo");
      logVersionInfoMethod.invoke(null);
    } catch (final Throwable ex) {
      log.error("Throwable thrown while installing the Datadog Tracer", ex);
    }
  }

  private static synchronized void startJmx(final URL bootstrapURL) {
    startJmxFetch(bootstrapURL);
    initializeJmxThreadCpuTimeProvider();
  }

  /** Enable JMX based thread CPU time provider once it is safe to touch JMX */
  private static synchronized void initializeJmxThreadCpuTimeProvider() {
    log.info("Initializing JMX thread CPU time provider");
    if (AGENT_CLASSLOADER == null) {
      throw new IllegalStateException("Datadog agent should have been started already");
    }
    try {
      final Class<?> tracerInstallerClass =
          AGENT_CLASSLOADER.loadClass("datadog.trace.common.util.ThreadCpuTimeAccess");
      final Method enableJmxMethod = tracerInstallerClass.getMethod("enableJmx");
      enableJmxMethod.invoke(null);
    } catch (final Throwable ex) {
      log.error("Throwable thrown while initializing JMX thread CPU time provider", ex);
    }
  }

  private static synchronized void startJmxFetch(final URL bootstrapURL) {
    if (JMXFETCH_CLASSLOADER == null) {
      final ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
      try {
        final ClassLoader jmxFetchClassLoader =
            createDatadogClassLoader("agent-jmxfetch.isolated", bootstrapURL, PARENT_CLASSLOADER);
        Thread.currentThread().setContextClassLoader(jmxFetchClassLoader);
        final Class<?> jmxFetchAgentClass =
            jmxFetchClassLoader.loadClass("datadog.trace.agent.jmxfetch.JMXFetch");
        final Method jmxFetchInstallerMethod = jmxFetchAgentClass.getMethod("run");
        jmxFetchInstallerMethod.invoke(null);
        JMXFETCH_CLASSLOADER = jmxFetchClassLoader;
      } catch (final Throwable ex) {
        log.error("Throwable thrown while starting JmxFetch", ex);
      } finally {
        Thread.currentThread().setContextClassLoader(contextLoader);
      }
    }
  }

  private static synchronized void startProfilingAgent(
      final URL bootstrapURL, final boolean isStartingFirst) {
    final ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
    try {
      if (PROFILING_CLASSLOADER == null) {
        PROFILING_CLASSLOADER =
            createDatadogClassLoader("agent-profiling.isolated", bootstrapURL, PARENT_CLASSLOADER);
      }
      Thread.currentThread().setContextClassLoader(PROFILING_CLASSLOADER);
      final Class<?> profilingAgentClass =
          PROFILING_CLASSLOADER.loadClass("com.datadog.profiling.agent.ProfilingAgent");
      final Method profilingInstallerMethod = profilingAgentClass.getMethod("run", Boolean.TYPE);
      profilingInstallerMethod.invoke(null, isStartingFirst);
    } catch (final ClassFormatError e) {
      /*
      Profiling is compiled for Java8. Loading it on Java7 results in ClassFormatError
      (more specifically UnsupportedClassVersionError). Just ignore and continue when this happens.
      */
      log.error("Profiling requires OpenJDK 8 or above - skipping");
      log.debug("Cannot start profiling agent ", e);
    } catch (final Throwable ex) {
      log.error("Throwable thrown while starting profiling agent", ex);
    } finally {
      Thread.currentThread().setContextClassLoader(contextLoader);
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

  private static void setSystemPropertyDefault(final String property, final String value) {
    if (System.getProperty(property) == null) {
      System.setProperty(property, value);
    }
  }

  /**
   * Create the datadog classloader. This must be called after the bootstrap jar has been appened to
   * the bootstrap classpath.
   *
   * @param innerJarFilename Filename of internal jar to use for the classpath of the datadog
   *     classloader
   * @param bootstrapURL
   * @return Datadog Classloader
   */
  private static ClassLoader createDatadogClassLoader(
      final String innerJarFilename, final URL bootstrapURL, final ClassLoader parent)
      throws Exception {

    final Class<?> loaderClass =
        ClassLoader.getSystemClassLoader().loadClass("datadog.trace.bootstrap.DatadogClassLoader");
    final Constructor constructor =
        loaderClass.getDeclaredConstructor(
            URL.class, String.class, ClassLoader.class, ClassLoader.class);
    return (ClassLoader)
        constructor.newInstance(bootstrapURL, innerJarFilename, BOOTSTRAP_PROXY, parent);
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
   * Determine if we should log in debug level according to dd.trace.debug
   *
   * @return true if we should
   */
  private static boolean isDebugMode() {
    final String tracerDebugLevelSysprop = "dd.trace.debug";
    final String tracerDebugLevelProp = System.getProperty(tracerDebugLevelSysprop);

    if (tracerDebugLevelProp != null) {
      return Boolean.parseBoolean(tracerDebugLevelProp);
    }

    final String tracerDebugLevelEnv =
        System.getenv(tracerDebugLevelSysprop.replace('.', '_').toUpperCase());

    if (tracerDebugLevelEnv != null) {
      return Boolean.parseBoolean(tracerDebugLevelEnv);
    }
    return false;
  }

  /**
   * Search for java or datadog-tracer sysprops which indicate that a custom log manager will be
   * used. Also search for any app classes known to set a custom log manager.
   *
   * @return true if we detect a custom log manager being used.
   */
  private static boolean isAppUsingCustomLogManager() {
    final String tracerCustomLogManSysprop = "dd.app.customlogmanager";
    final String customLogManagerProp = System.getProperty(tracerCustomLogManSysprop);
    final String customLogManagerEnv =
        System.getenv(tracerCustomLogManSysprop.replace('.', '_').toUpperCase());

    if (customLogManagerProp != null || customLogManagerEnv != null) {
      log.debug("Prop - customlogmanager: " + customLogManagerProp);
      log.debug("Env - customlogmanager: " + customLogManagerEnv);
      // Allow setting to skip these automatic checks:
      return Boolean.parseBoolean(customLogManagerProp)
          || Boolean.parseBoolean(customLogManagerEnv);
    }

    final String jbossHome = System.getenv("JBOSS_HOME");
    if (jbossHome != null) {
      log.debug("Env - jboss: " + jbossHome);
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

  private static boolean isJavaBefore9WithJFR() {
    if (!isJavaBefore9()) {
      return false;
    }
    // FIXME: this is quite a hack because there maybe jfr classes on classpath somehow that have
    // nothing to do with JDK but this should be safe because only thing this does is to delay
    // tracer install
    final String jfrClassResourceName = "jdk.jfr.Recording".replace('.', '/') + ".class";
    return Thread.currentThread().getContextClassLoader().getResource(jfrClassResourceName) != null;
  }
}

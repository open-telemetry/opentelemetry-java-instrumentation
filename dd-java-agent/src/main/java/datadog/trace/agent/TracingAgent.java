package datadog.trace.agent;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.instrument.Instrumentation;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
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

/** Entry point for initializing the agent. */
public class TracingAgent {

  private static final String SIMPLE_LOGGER_SHOW_DATE_TIME_PROPERTY =
      "datadog.slf4j.simpleLogger.showDateTime";
  private static final String SIMPLE_LOGGER_DATE_TIME_FORMAT_PROPERTY =
      "datadog.slf4j.simpleLogger.dateTimeFormat";
  private static final String SIMPLE_LOGGER_DATE_TIME_FORMAT_DEFAULT =
      "'[dd.trace 'yyyy-MM-dd HH:mm:ss:SSS Z']'";
  private static final String SIMPLE_LOGGER_DEFAULT_LOG_LEVEL_PROPERTY =
      "datadog.slf4j.simpleLogger.defaultLogLevel";

  // fields must be managed under class lock
  private static ClassLoader AGENT_CLASSLOADER = null;

  public static void premain(final String agentArgs, final Instrumentation inst) throws Exception {
    agentmain(agentArgs, inst);
  }

  public static void agentmain(final String agentArgs, final Instrumentation inst)
      throws Exception {
    configureLogger();

    final URL bootstrapURL = installBootstrapJar(inst);

    startDatadogAgent(inst, bootstrapURL);
  }

  private static synchronized void startDatadogAgent(
      final Instrumentation inst, final URL bootstrapURL) throws Exception {

    if (AGENT_CLASSLOADER == null) {
      final ClassLoader agentClassLoader =
          createDatadogClassLoader("agent-tooling-and-instrumentation.isolated", bootstrapURL);
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
        {
          // install global tracer
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

  private static void configureLogger() {
    setSystemPropertyDefault(SIMPLE_LOGGER_SHOW_DATE_TIME_PROPERTY, "true");
    setSystemPropertyDefault(
        SIMPLE_LOGGER_DATE_TIME_FORMAT_PROPERTY, SIMPLE_LOGGER_DATE_TIME_FORMAT_DEFAULT);

    final boolean debugEnabled = isDebugMode();
    if (debugEnabled) {
      setSystemPropertyDefault(SIMPLE_LOGGER_DEFAULT_LOG_LEVEL_PROPERTY, "DEBUG");
    }
  }

  private static void setSystemPropertyDefault(final String property, final String value) {
    if (System.getProperty(property) == null) {
      System.setProperty(property, value);
    }
  }

  private static synchronized URL installBootstrapJar(final Instrumentation inst)
      throws IOException, URISyntaxException {
    URL bootstrapURL = null;

    // First try Code Source
    final CodeSource codeSource = TracingAgent.class.getProtectionDomain().getCodeSource();

    if (codeSource != null) {
      bootstrapURL = codeSource.getLocation();
      final File bootstrapFile = new File(bootstrapURL.toURI());

      if (!bootstrapFile.isDirectory()) {
        inst.appendToBootstrapClassLoaderSearch(new JarFile(bootstrapFile));
        return bootstrapURL;
      }
    }

    System.out.println("Could not get bootstrap jar from code source, using -javaagent arg");

    // ManagementFactory indirectly references java.util.logging.LogManager
    // - On Oracle-based JDKs after 1.8
    // - On IBM-based JDKs since at least 1.7
    // This prevents custom log managers from working correctly
    // Use reflection to bypass the loading of the class
    final List<String> arguments = getVMArgumentsThroughReflection();

    String agentArgument = null;
    for (final String arg : arguments) {
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

    // argument is of the form -javaagent:/path/to/dd-java-agent.jar=optionalargumentstring
    final Matcher matcher = Pattern.compile("-javaagent:([^=]+).*").matcher(agentArgument);

    if (!matcher.matches()) {
      throw new RuntimeException("Unable to parse javaagent parameter: " + agentArgument);
    }

    bootstrapURL = new URL("file:" + matcher.group(1));
    inst.appendToBootstrapClassLoaderSearch(new JarFile(new File(bootstrapURL.toURI())));

    return bootstrapURL;
  }

  private static List<String> getVMArgumentsThroughReflection() {
    try {
      // Try Oracle-based
      final Class managementFactoryHelperClass =
          TracingAgent.class.getClassLoader().loadClass("sun.management.ManagementFactoryHelper");

      final Class vmManagementClass =
          TracingAgent.class.getClassLoader().loadClass("sun.management.VMManagement");

      Object vmManagement;

      try {
        vmManagement =
            managementFactoryHelperClass.getDeclaredMethod("getVMManagement").invoke(null);
      } catch (final NoSuchMethodException e) {
        // Older vm before getVMManagement() existed
        final Field field = managementFactoryHelperClass.getDeclaredField("jvm");
        field.setAccessible(true);
        vmManagement = field.get(null);
        field.setAccessible(false);
      }

      return (List<String>) vmManagementClass.getMethod("getVmArguments").invoke(vmManagement);

    } catch (final ReflectiveOperationException e) {
      try { // Try IBM-based.
        final Class VMClass = TracingAgent.class.getClassLoader().loadClass("com.ibm.oti.vm.VM");
        final String[] argArray = (String[]) VMClass.getMethod("getVMArgs").invoke(null);
        return Arrays.asList(argArray);
      } catch (final ReflectiveOperationException e1) {
        // Fallback to default
        System.out.println(
            "WARNING: Unable to get VM args through reflection.  A custom java.util.logging.LogManager may not work correctly");

        return ManagementFactory.getRuntimeMXBean().getInputArguments();
      }
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
      final String innerJarFilename, final URL bootstrapURL) throws Exception {
    final ClassLoader agentParent;
    if (isJavaBefore9()) {
      agentParent = null; // bootstrap
    } else {
      // platform classloader is parent of system in java 9+
      agentParent = getPlatformClassLoader();
    }

    final Class<?> loaderClass =
        ClassLoader.getSystemClassLoader().loadClass("datadog.trace.bootstrap.DatadogClassLoader");
    final Constructor constructor =
        loaderClass.getDeclaredConstructor(URL.class, String.class, ClassLoader.class);
    return (ClassLoader) constructor.newInstance(bootstrapURL, innerJarFilename, agentParent);
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

  private static boolean isJavaBefore9() {
    return System.getProperty("java.version").startsWith("1.");
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

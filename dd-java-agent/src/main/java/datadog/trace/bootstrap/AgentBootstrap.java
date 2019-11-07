package datadog.trace.bootstrap;

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

  public static void premain(final String agentArgs, final Instrumentation inst) throws Exception {
    agentmain(agentArgs, inst);
  }

  public static void agentmain(final String agentArgs, final Instrumentation inst)
      throws Exception {

    final URL bootstrapURL = installBootstrapJar(inst);

    final Class<?> agentClass =
        ClassLoader.getSystemClassLoader().loadClass("datadog.trace.bootstrap.Agent");
    final Method startMethod = agentClass.getMethod("start", Instrumentation.class, URL.class);
    startMethod.invoke(null, inst, bootstrapURL);
  }

  private static synchronized URL installBootstrapJar(final Instrumentation inst)
      throws IOException, URISyntaxException {
    URL bootstrapURL = null;

    // First try Code Source
    final CodeSource codeSource = AgentBootstrap.class.getProtectionDomain().getCodeSource();

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
          AgentBootstrap.class.getClassLoader().loadClass("sun.management.ManagementFactoryHelper");

      final Class vmManagementClass =
          AgentBootstrap.class.getClassLoader().loadClass("sun.management.VMManagement");

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
        final Class VMClass = AgentBootstrap.class.getClassLoader().loadClass("com.ibm.oti.vm.VM");
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
                AgentBootstrap.class.getResourceAsStream("/dd-java-agent.version"),
                StandardCharsets.UTF_8))) {

      for (int c = reader.read(); c != -1; c = reader.read()) {
        sb.append((char) c);
      }
    }

    return sb.toString().trim();
  }
}

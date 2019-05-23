package datadog.trace.agent.test;

import static java.nio.charset.StandardCharsets.UTF_8;

import io.opentracing.Tracer;
import io.opentracing.util.GlobalTracer;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

public class IntegrationTestUtils {

  /** Returns the classloader the core agent is running on. */
  public static ClassLoader getAgentClassLoader() {
    return getTracingAgentFieldClassloader("AGENT_CLASSLOADER");
  }

  /** Returns the classloader the jmxfetch is running on. */
  public static ClassLoader getJmxFetchClassLoader() {
    return getTracingAgentFieldClassloader("JMXFETCH_CLASSLOADER");
  }

  private static ClassLoader getTracingAgentFieldClassloader(final String fieldName) {
    Field classloaderField = null;
    try {
      Class<?> tracingAgentClass =
          tracingAgentClass =
              ClassLoader.getSystemClassLoader().loadClass("datadog.trace.agent.TracingAgent");
      classloaderField = tracingAgentClass.getDeclaredField(fieldName);
      classloaderField.setAccessible(true);
      return (ClassLoader) classloaderField.get(null);
    } catch (final Exception e) {
      throw new IllegalStateException(e);
    } finally {
      if (null != classloaderField) {
        classloaderField.setAccessible(false);
      }
    }
  }

  /** Returns the URL to the jar the agent appended to the bootstrap classpath * */
  public static ClassLoader getBootstrapProxy() throws Exception {
    final ClassLoader agentClassLoader = getAgentClassLoader();
    final Field field = agentClassLoader.getClass().getDeclaredField("bootstrapProxy");
    try {
      field.setAccessible(true);
      return (ClassLoader) field.get(agentClassLoader);
    } finally {
      field.setAccessible(false);
    }
  }

  /** See {@link IntegrationTestUtils#createJarWithClasses(String, Class[])} */
  public static URL createJarWithClasses(final Class<?>... classes) throws IOException {
    return createJarWithClasses(null, classes);
  }
  /**
   * Create a temporary jar on the filesystem with the bytes of the given classes.
   *
   * <p>The jar file will be removed when the jvm exits.
   *
   * @param mainClassname The name of the class to use for Main-Class and Premain-Class. May be null
   * @param classes classes to package into the jar.
   * @return the location of the newly created jar.
   * @throws IOException
   */
  public static URL createJarWithClasses(final String mainClassname, final Class<?>... classes)
      throws IOException {
    final File tmpJar = File.createTempFile(UUID.randomUUID().toString() + "-", ".jar");
    tmpJar.deleteOnExit();

    final Manifest manifest = new Manifest();
    if (mainClassname != null) {
      final Attributes mainAttributes = manifest.getMainAttributes();
      mainAttributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
      mainAttributes.put(Attributes.Name.MAIN_CLASS, mainClassname);
      mainAttributes.put(new Attributes.Name("Premain-Class"), mainClassname);
    }
    final JarOutputStream target = new JarOutputStream(new FileOutputStream(tmpJar), manifest);
    for (final Class<?> clazz : classes) {
      addToJar(clazz, target);
    }
    target.close();

    return tmpJar.toURI().toURL();
  }

  private static void addToJar(final Class<?> clazz, final JarOutputStream jarOutputStream)
      throws IOException {
    InputStream inputStream = null;
    ClassLoader loader = clazz.getClassLoader();
    if (null == loader) {
      // bootstrap resources can be fetched through the system loader
      loader = ClassLoader.getSystemClassLoader();
    }
    try {
      final JarEntry entry = new JarEntry(getResourceName(clazz.getName()));
      jarOutputStream.putNextEntry(entry);
      inputStream = loader.getResourceAsStream(getResourceName(clazz.getName()));

      final byte[] buffer = new byte[1024];
      while (true) {
        final int count = inputStream.read(buffer);
        if (count == -1) {
          break;
        }
        jarOutputStream.write(buffer, 0, count);
      }
      jarOutputStream.closeEntry();
    } finally {
      if (inputStream != null) {
        inputStream.close();
      }
    }
  }

  public static void registerOrReplaceGlobalTracer(final Tracer tracer) {
    try {
      GlobalTracer.register(tracer);
    } catch (final Exception e) {
      // Force it anyway using reflection
      Field field = null;
      try {
        field = GlobalTracer.class.getDeclaredField("tracer");
        field.setAccessible(true);
        field.set(null, tracer);
      } catch (final Exception e2) {
        throw new IllegalStateException(e2);
      } finally {
        if (null != field) {
          field.setAccessible(false);
        }
      }
    }

    if (!GlobalTracer.isRegistered()) {
      throw new RuntimeException("Unable to register the global tracer.");
    }
  }

  /** com.foo.Bar -> com/foo/Bar.class */
  public static String getResourceName(final String className) {
    return className.replace('.', '/') + ".class";
  }

  public static String[] getBootstrapPackagePrefixes() throws Exception {
    final Field f =
        getAgentClassLoader()
            .loadClass("datadog.trace.agent.tooling.Constants")
            .getField("BOOTSTRAP_PACKAGE_PREFIXES");
    return (String[]) f.get(null);
  }

  public static String[] getAgentPackagePrefixes() throws Exception {
    final Field f =
        getAgentClassLoader()
            .loadClass("datadog.trace.agent.tooling.Constants")
            .getField("AGENT_PACKAGE_PREFIXES");
    return (String[]) f.get(null);
  }

  /**
   * On a separate JVM, run the main method for a given class.
   *
   * @param mainClassName The name of the entry point class. Must declare a main method.
   * @param printOutputStreams if true, print stdout and stderr of the child jvm
   * @return the return code of the child jvm
   * @throws Exception
   */
  public static int runOnSeparateJvm(
      final String mainClassName,
      final String[] jvmArgs,
      final String[] mainMethodArgs,
      final Map<String, String> envVars,
      final boolean printOutputStreams)
      throws Exception {
    final String separator = System.getProperty("file.separator");
    final String classpath = System.getProperty("java.class.path");
    final String path = System.getProperty("java.home") + separator + "bin" + separator + "java";
    final List<String> commands = new ArrayList<>();
    commands.add(path);
    commands.addAll(Arrays.asList(jvmArgs));
    commands.add("-cp");
    commands.add(classpath);
    commands.add(mainClassName);
    commands.addAll(Arrays.asList(mainMethodArgs));
    final ProcessBuilder processBuilder = new ProcessBuilder(commands.toArray(new String[0]));
    processBuilder.environment().putAll(envVars);
    final Process process = processBuilder.start();

    waitFor(process, 30, TimeUnit.SECONDS);

    if (printOutputStreams) {
      final BufferedReader stdInput =
          new BufferedReader(new InputStreamReader(process.getInputStream(), UTF_8));

      final BufferedReader stdError =
          new BufferedReader(new InputStreamReader(process.getErrorStream(), UTF_8));
      System.out.println("--- " + mainClassName + " stdout ---");
      String s = null;
      while ((s = stdInput.readLine()) != null) {
        System.out.println(s);
      }
      System.out.println("--- stdout end ---");
      System.out.println("--- " + mainClassName + " stderr ---");
      while ((s = stdError.readLine()) != null) {
        System.out.println(s);
      }
      System.out.println("--- stderr end ---");
    }
    return process.exitValue();
  }

  private static void waitFor(final Process process, final long timeout, final TimeUnit unit)
      throws InterruptedException, TimeoutException {
    final long startTime = System.nanoTime();
    long rem = unit.toNanos(timeout);

    do {
      try {
        process.exitValue();
        return;
      } catch (final IllegalThreadStateException ex) {
        if (rem > 0) {
          Thread.sleep(Math.min(TimeUnit.NANOSECONDS.toMillis(rem) + 1, 100));
        }
      }
      rem = unit.toNanos(timeout) - (System.nanoTime() - startTime);
    } while (rem > 0);
    throw new TimeoutException();
  }
}

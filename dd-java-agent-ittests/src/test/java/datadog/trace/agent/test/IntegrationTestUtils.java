package datadog.trace.agent.test;

import io.opentracing.Scope;
import io.opentracing.Tracer;
import io.opentracing.util.GlobalTracer;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

public class IntegrationTestUtils {

  public static <T extends Object> Object runUnderTrace(
      final String rootOperationName, final Callable<T> r) {
    final Scope scope = GlobalTracer.get().buildSpan(rootOperationName).startActive(true);
    try {
      return r.call();
    } catch (final Exception e) {
      throw new IllegalStateException(e);
    } finally {
      scope.close();
    }
  }

  /** Returns the classloader the core agent is running on. */
  public static ClassLoader getAgentClassLoader() {
    Field classloaderField = null;
    try {
      Class<?> tracingAgentClass =
          tracingAgentClass =
              ClassLoader.getSystemClassLoader().loadClass("datadog.trace.agent.TracingAgent");
      classloaderField = tracingAgentClass.getDeclaredField("AGENT_CLASSLOADER");
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

  /** Returns the classloader the jmxfetch is running on. */
  public static ClassLoader getJmxFetchClassLoader() {
    Field classloaderField = null;
    try {
      Class<?> tracingAgentClass =
          tracingAgentClass =
              ClassLoader.getSystemClassLoader().loadClass("datadog.trace.agent.TracingAgent");
      classloaderField = tracingAgentClass.getDeclaredField("JMXFETCH_CLASSLOADER");
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

  /**
   * Create a temporary jar on the filesystem with the bytes of the given classes.
   *
   * <p>The jar file will be removed when the jvm exits.
   *
   * @param classes classes to package into the jar.
   * @return the location of the newly created jar.
   * @throws IOException
   */
  public static URL createJarWithClasses(final Class<?>... classes) throws IOException {
    final File tmpJar = File.createTempFile(UUID.randomUUID().toString() + "-", ".jar");
    tmpJar.deleteOnExit();

    final Manifest manifest = new Manifest();
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
            .loadClass("datadog.trace.agent.tooling.Utils")
            .getField("BOOTSTRAP_PACKAGE_PREFIXES");
    return (String[]) f.get(null);
  }

  public static String[] getAgentPackagePrefixes() throws Exception {
    final Field f =
        getAgentClassLoader()
            .loadClass("datadog.trace.agent.tooling.Utils")
            .getField("AGENT_PACKAGE_PREFIXES");
    return (String[]) f.get(null);
  }

  public static void awaitGC() {
    Object obj = new Object();
    final WeakReference<Object> ref = new WeakReference<>(obj);
    obj = null;
    awaitGC(ref);
  }

  public static void awaitGC(final WeakReference<?> ref) {
    while (ref.get() != null) {
      System.gc();
      System.runFinalization();
    }
  }
}

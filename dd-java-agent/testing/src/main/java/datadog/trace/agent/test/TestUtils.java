package datadog.trace.agent.test;

import static io.opentracing.log.Fields.ERROR_OBJECT;

import datadog.trace.agent.tooling.Utils;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.net.URL;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

public class TestUtils {

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

  /** Get the tracer implementation out of the GlobalTracer */
  public static Tracer getUnderlyingGlobalTracer() {
    Field field = null;
    try {
      field = GlobalTracer.class.getDeclaredField("tracer");
      field.setAccessible(true);
      return (Tracer) field.get(GlobalTracer.get());
    } catch (final Exception e2) {
      throw new IllegalStateException(e2);
    } finally {
      if (null != field) {
        field.setAccessible(false);
      }
    }
  }

  public static <T extends Object> Object withSystemProperty(
      final String name, final String value, final Callable<T> r) {
    if (value == null) {
      System.clearProperty(name);
    } else {
      System.setProperty(name, value);
    }
    try {
      return r.call();
    } catch (final Exception e) {
      throw new IllegalStateException(e);
    } finally {
      System.clearProperty(name);
    }
  }

  public static <T extends Object> Object runUnderTrace(
      final String rootOperationName, final Callable<T> r) throws Exception {
    final Scope scope = GlobalTracer.get().buildSpan(rootOperationName).startActive(true);
    try {
      return r.call();
    } catch (final Exception e) {
      final Span span = scope.span();
      Tags.ERROR.set(span, true);
      span.log(Collections.singletonMap(ERROR_OBJECT, e));

      throw e;
    } finally {
      scope.close();
    }
  }

  public static byte[] convertToByteArray(final InputStream resource) throws IOException {
    final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    int bytesRead;
    final byte[] data = new byte[1024];
    while ((bytesRead = resource.read(data, 0, data.length)) != -1) {
      buffer.write(data, 0, bytesRead);
    }
    buffer.flush();
    return buffer.toByteArray();
  }

  public static byte[] convertToByteArray(final Class<?> clazz) throws IOException {
    InputStream inputStream = null;
    try {
      inputStream =
          clazz.getClassLoader().getResourceAsStream(Utils.getResourceName(clazz.getName()));
      return convertToByteArray(inputStream);
    } finally {
      if (inputStream != null) {
        inputStream.close();
      }
    }
  }

  /**
   * Create a temporary jar on the filesystem with the bytes of the given classes.
   *
   * <p>The jar file will be removed when the jvm exits.
   *
   * @param loader classloader used to load bytes
   * @param resourceNames names of resources to copy into the new jar
   * @return the location of the newly created jar.
   * @throws IOException
   */
  public static URL createJarWithClasses(final ClassLoader loader, final String... resourceNames)
      throws IOException {
    final File tmpJar = File.createTempFile(UUID.randomUUID().toString() + "", ".jar");
    tmpJar.deleteOnExit();

    final Manifest manifest = new Manifest();
    final JarOutputStream target = new JarOutputStream(new FileOutputStream(tmpJar), manifest);
    for (final String resourceName : resourceNames) {
      InputStream is = null;
      try {
        is = loader.getResourceAsStream(resourceName);
        addToJar(resourceName, convertToByteArray(is), target);
      } finally {
        if (null != is) {
          is.close();
        }
      }
    }
    target.close();

    return tmpJar.toURI().toURL();
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
    final File tmpJar = File.createTempFile(UUID.randomUUID().toString() + "", ".jar");
    tmpJar.deleteOnExit();

    final Manifest manifest = new Manifest();
    final JarOutputStream target = new JarOutputStream(new FileOutputStream(tmpJar), manifest);
    for (final Class<?> clazz : classes) {
      addToJar(Utils.getResourceName(clazz.getName()), convertToByteArray(clazz), target);
    }
    target.close();

    return tmpJar.toURI().toURL();
  }

  public static URL createJarWithClasses() {

    return null;
  }

  private static void addToJar(
      final String resourceName, final byte[] bytes, final JarOutputStream jarOutputStream)
      throws IOException {
    final JarEntry entry = new JarEntry(resourceName);
    jarOutputStream.putNextEntry(entry);
    jarOutputStream.write(bytes, 0, bytes.length);
    jarOutputStream.closeEntry();
  }

  /** Open up a random, reusable port. */
  public static int randomOpenPort() {
    final ServerSocket socket;
    try {
      socket = new ServerSocket(0);
      socket.setReuseAddress(true);
      socket.close();
      return socket.getLocalPort();
    } catch (final IOException ioe) {
      ioe.printStackTrace();
      return -1;
    }
  }

  public static void awaitGC() {
    Object obj = new Object();
    final WeakReference ref = new WeakReference<>(obj);
    obj = null;
    while (ref.get() != null) {
      System.gc();
    }
  }
}

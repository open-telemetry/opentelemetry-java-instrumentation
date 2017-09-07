package datadog.trace.agent.test;

import datadog.trace.agent.tooling.Utils;
import io.opentracing.Scope;
import io.opentracing.Tracer;
import io.opentracing.util.GlobalTracer;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

public class TestUtils {
  private static Method findLoadedClassMethod = null;

  static {
    try {
      findLoadedClassMethod = ClassLoader.class.getDeclaredMethod("findLoadedClass", String.class);
    } catch (NoSuchMethodException | SecurityException e) {
      throw new IllegalStateException(e);
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

  public static boolean isClassLoaded(final String className, final ClassLoader classLoader) {
    try {
      findLoadedClassMethod.setAccessible(true);
      final Class<?> loadedClass = (Class<?>) findLoadedClassMethod.invoke(classLoader, className);
      return null != loadedClass && loadedClass.getClassLoader() == classLoader;
    } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
      throw new IllegalStateException(e);
    } finally {
      findLoadedClassMethod.setAccessible(false);
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
    final File tmpJar = File.createTempFile(UUID.randomUUID().toString() + "", ".jar");
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
    try {
      final JarEntry entry = new JarEntry(Utils.getResourceName(clazz.getName()));
      jarOutputStream.putNextEntry(entry);
      inputStream =
          clazz.getClassLoader().getResourceAsStream(Utils.getResourceName(clazz.getName()));

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
}

package datadog.trace.agent.tooling;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class Utils {
  /* packages which will be loaded on the bootstrap classloader*/
  public static final String[] BOOTSTRAP_PACKAGE_PREFIXES = {
    "io.opentracing", "datadog.slf4j", "datadog.trace.bootstrap", "datadog.trace.api"
  };
  public static final String[] AGENT_PACKAGE_PREFIXES = {
    "datadog.trace.agent", "datadog.opentracing", "datadog.trace.common"
  };

  private static Method findLoadedClassMethod = null;

  static {
    try {
      findLoadedClassMethod = ClassLoader.class.getDeclaredMethod("findLoadedClass", String.class);
    } catch (NoSuchMethodException | SecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  /** Return the classloader the core agent is running on. */
  public static ClassLoader getAgentClassLoader() {
    return AgentInstaller.class.getClassLoader();
  }

  /** com.foo.Bar -> com/foo/Bar.class */
  public static String getResourceName(final String className) {
    return className.replace('.', '/') + ".class";
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

  static boolean getConfigEnabled(final String name, final boolean fallback) {
    final String property =
        System.getProperty(
            name, System.getenv(name.toUpperCase().replaceAll("[^a-zA-Z0-9_]", "_")));
    return property == null ? fallback : Boolean.parseBoolean(property);
  }

  private Utils() {}
}

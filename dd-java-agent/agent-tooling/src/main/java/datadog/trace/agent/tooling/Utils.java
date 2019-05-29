package datadog.trace.agent.tooling;

import static net.bytebuddy.matcher.ElementMatchers.named;

import datadog.trace.bootstrap.DatadogClassLoader;
import datadog.trace.bootstrap.DatadogClassLoader.BootstrapClassLoaderProxy;
import java.lang.reflect.Method;
import java.net.URL;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDefinition;

public class Utils {

  // This is used in HelperInjectionTest.groovy
  private static Method findLoadedClassMethod = null;

  private static final BootstrapClassLoaderProxy unitTestBootstrapProxy =
      new BootstrapClassLoaderProxy(new URL[0], null);

  static {
    try {
      findLoadedClassMethod = ClassLoader.class.getDeclaredMethod("findLoadedClass", String.class);
    } catch (final NoSuchMethodException | SecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  /** Return the classloader the core agent is running on. */
  public static ClassLoader getAgentClassLoader() {
    return AgentInstaller.class.getClassLoader();
  }

  /** Return a classloader which can be used to look up bootstrap resources. */
  public static BootstrapClassLoaderProxy getBootstrapProxy() {
    if (getAgentClassLoader() instanceof DatadogClassLoader) {
      return ((DatadogClassLoader) getAgentClassLoader()).getBootstrapProxy();
    } else {
      // in a unit test
      return unitTestBootstrapProxy;
    }
  }

  /** com.foo.Bar -> com/foo/Bar.class */
  public static String getResourceName(final String className) {
    if (!className.endsWith(".class")) {
      return className.replace('.', '/') + ".class";
    } else {
      return className;
    }
  }

  /** com/foo/Bar.class -> com.foo.Bar */
  public static String getClassName(final String resourceName) {
    return resourceName.replaceAll("\\.class\\$", "").replace('/', '.');
  }

  /** com.foo.Bar -> com/foo/Bar */
  public static String getInternalName(final String resourceName) {
    return resourceName.replaceAll("\\.class\\$", "").replace('.', '/');
  }

  /**
   * Convert class name to a format that can be used as part of inner class name by replacing all
   * '.'s with '$'s.
   *
   * @param className class named to be converted
   * @return convertd name
   */
  public static String converToInnerClassName(final String className) {
    return className.replaceAll("\\.", "\\$");
  }

  /**
   * Get method definition for given {@link TypeDefinition} and method name.
   *
   * @param type type
   * @param methodName method name
   * @return {@link MethodDescription} for given method
   * @throws IllegalStateException if more then one method matches (i.e. in case of overloaded
   *     methods) or if no method found
   */
  public static MethodDescription getMethodDefinition(
      final TypeDefinition type, final String methodName) {
    return type.getDeclaredMethods().filter(named(methodName)).getOnly();
  }

  /** @return The current stack trace with multiple entries on new lines. */
  public static String getStackTraceAsString() {
    StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
    StringBuilder stringBuilder = new StringBuilder();
    String lineSeparator = System.getProperty("line.separator");
    for (StackTraceElement element : stackTrace) {
      stringBuilder.append(element.toString());
      stringBuilder.append(lineSeparator);
    }
    return stringBuilder.toString();
  }

  private Utils() {}
}

package datadog.trace.agent.tooling;

import static net.bytebuddy.matcher.ElementMatchers.named;

import datadog.trace.bootstrap.DatadogClassLoader;
import datadog.trace.bootstrap.DatadogClassLoader.BootstrapClassLoaderProxy;
import java.lang.reflect.Method;
import java.net.URL;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDefinition;

public class Utils {
  /**
   * packages which will be loaded on the bootstrap classloader
   *
   * <p>Updates should be mirrored in TestUtils#BOOTSTRAP_PACKAGE_PREFIXES_COPY
   */
  public static final String[] BOOTSTRAP_PACKAGE_PREFIXES = {
    "io.opentracing",
    "datadog.slf4j",
    "datadog.trace.bootstrap",
    "datadog.trace.api",
    "datadog.trace.context"
  };

  public static final String[] AGENT_PACKAGE_PREFIXES = {
    "datadog.trace.common",
    "datadog.trace.agent",
    "datadog.trace.instrumentation",
    // guava
    "com.google.auto",
    "com.google.common",
    "com.google.thirdparty.publicsuffix",
    // WeakConcurrentMap
    "com.blogspot.mydailyjava.weaklockfree",
    // bytebuddy
    "net.bytebuddy",
    // OT contribs for dd trace resolver
    "io.opentracing.contrib",
    // jackson
    "org.msgpack",
    "com.fasterxml.jackson",
    "org.yaml.snakeyaml",
  };

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
   * ','s with '$'s.
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

  static boolean getConfigEnabled(final String name, final boolean fallback) {
    final String property =
        System.getProperty(
            name, System.getenv(name.toUpperCase().replaceAll("[^a-zA-Z0-9_]", "_")));
    return property == null ? fallback : Boolean.parseBoolean(property);
  }

  private Utils() {}
}

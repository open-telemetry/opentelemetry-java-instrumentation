package datadog.trace.bootstrap;

import java.net.URL;
import java.net.URLClassLoader;

/** Classloader used to run the core datadog agent. */
public class DatadogClassLoader extends URLClassLoader {
  static {
    ClassLoader.registerAsParallelCapable();
  }

  // Calling java.lang.instrument.Instrumentation#appendToBootstrapClassLoaderSearch
  // adds a jar to the bootstrap class lookup, but not to the resource lookup.
  // As a workaround, we keep a reference to the bootstrap jar
  // to use only for resource lookups.
  private final BootstrapClassLoaderProxy bootstrapProxy;

  /**
   * Construct a new DatadogClassLoader
   *
   * @param bootstrapJarLocation Used for resource lookups.
   * @param agentJarLocation Classpath of this classloader.
   * @param parent Classloader parent. Should null (bootstrap), or the platform classloader for java
   *     9+.
   */
  public DatadogClassLoader(URL bootstrapJarLocation, URL agentJarLocation, ClassLoader parent) {
    super(new URL[] {agentJarLocation}, parent);
    bootstrapProxy = new BootstrapClassLoaderProxy(new URL[] {bootstrapJarLocation}, null);
  }

  @Override
  public URL getResource(String resourceName) {
    final URL bootstrapResource = bootstrapProxy.getResource(resourceName);
    if (null == bootstrapResource) {
      return super.getResource(resourceName);
    } else {
      return bootstrapResource;
    }
  }

  /**
   * @param className binary name of class
   * @return true if this loader has attempted to load the given class
   */
  public boolean hasLoadedClass(final String className) {
    return findLoadedClass(className) != null;
  }

  public BootstrapClassLoaderProxy getBootstrapProxy() {
    return bootstrapProxy;
  }

  /**
   * A stand-in for the bootstrap classloader. Used to look up bootstrap resources and resources
   * appended by instrumentation.
   *
   * <p>This class is thread safe.
   */
  public static final class BootstrapClassLoaderProxy extends URLClassLoader {
    static {
      ClassLoader.registerAsParallelCapable();
    }

    public BootstrapClassLoaderProxy(URL[] urls, ClassLoader parent) {
      super(urls, parent);
    }

    @Override
    public void addURL(URL url) {
      super.addURL(url);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
      throw new ClassNotFoundException(name);
    }
  }
}

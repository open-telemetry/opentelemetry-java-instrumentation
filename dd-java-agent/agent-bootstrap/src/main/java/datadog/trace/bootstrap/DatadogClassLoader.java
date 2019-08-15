package datadog.trace.bootstrap;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import lombok.extern.slf4j.Slf4j;

/**
 * Classloader used to run the core datadog agent.
 *
 * <p>It is built around the concept of a jar inside another jar. This classloader loads the files
 * of the internal jar to load classes and resources.
 */
@Slf4j
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
   * @param internalJarFileName File name of the internal jar
   * @param parent Classloader parent. Should null (bootstrap), or the platform classloader for java
   *     9+.
   */
  public DatadogClassLoader(
      final URL bootstrapJarLocation, final String internalJarFileName, final ClassLoader parent) {
    super(new URL[] {}, parent);
    bootstrapProxy = new BootstrapClassLoaderProxy(new URL[] {bootstrapJarLocation});

    if (internalJarFileName != null) { // some tests pass null
      try {
        // The fields of the URL are mostly dummy.  InternalJarURLHandler is the only important
        // field.  If extending this class from Classloader instead of URLClassloader required less
        // boilerplate it could be used and the need for dummy fields would be reduced

        final URL internalJarURL =
            new URL(
                "x-internal-jar",
                null,
                0,
                "/",
                new InternalJarURLHandler(internalJarFileName, bootstrapProxy));

        addURL(internalJarURL);
      } catch (final MalformedURLException e) {
        // This can't happen with current URL constructor
        log.error("URL malformed.  Unsupported JDK?", e);
      }
    }
  }

  @Override
  public URL getResource(final String resourceName) {
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

    public BootstrapClassLoaderProxy(final URL[] urls) {
      super(urls, null);
    }

    @Override
    public void addURL(final URL url) {
      super.addURL(url);
    }

    @Override
    protected Class<?> findClass(final String name) throws ClassNotFoundException {
      throw new ClassNotFoundException(name);
    }
  }
}

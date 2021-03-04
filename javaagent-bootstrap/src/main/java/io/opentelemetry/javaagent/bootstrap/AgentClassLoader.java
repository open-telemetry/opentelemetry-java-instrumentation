/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * Classloader used to run the core agent.
 *
 * <p>It is built around the concept of a jar inside another jar. This classloader loads the files
 * of the internal jar to load classes and resources.
 */
public class AgentClassLoader extends URLClassLoader {

  // NOTE it's important not to use slf4j in this class, because this class is used before slf4j is
  // configured, and so using slf4j here would initialize slf4j-simple before we have a chance to
  // configure the logging levels

  static {
    ClassLoader.registerAsParallelCapable();
  }

  private static final String AGENT_INITIALIZER_JAR =
      System.getProperty("otel.javaagent.experimental.initializer.jar", "");

  // Calling java.lang.instrument.Instrumentation#appendToBootstrapClassLoaderSearch
  // adds a jar to the bootstrap class lookup, but not to the resource lookup.
  // As a workaround, we keep a reference to the bootstrap jar
  // to use only for resource lookups.
  private final BootstrapClassLoaderProxy bootstrapProxy;

  /**
   * Construct a new AgentClassLoader.
   *
   * @param bootstrapJarLocation Used for resource lookups.
   * @param internalJarFileName File name of the internal jar
   * @param parent Classloader parent. Should null (bootstrap), or the platform classloader for java
   *     9+.
   */
  public AgentClassLoader(
      URL bootstrapJarLocation, String internalJarFileName, ClassLoader parent) {
    super(new URL[] {}, parent);

    // some tests pass null
    bootstrapProxy =
        bootstrapJarLocation == null
            ? new BootstrapClassLoaderProxy(new URL[0])
            : new BootstrapClassLoaderProxy(new URL[] {bootstrapJarLocation});

    InternalJarUrlHandler internalJarUrlHandler =
        new InternalJarUrlHandler(internalJarFileName, bootstrapJarLocation);
    try {
      // The fields of the URL are mostly dummy.  InternalJarURLHandler is the only important
      // field.  If extending this class from Classloader instead of URLClassloader required less
      // boilerplate it could be used and the need for dummy fields would be reduced
      addURL(new URL("x-internal-jar", null, 0, "/", internalJarUrlHandler));
    } catch (MalformedURLException e) {
      // This can't happen with current URL constructor
      throw new IllegalStateException("URL malformed.  Unsupported JDK?", e);
    }

    if (!AGENT_INITIALIZER_JAR.isEmpty()) {
      URL url;
      try {
        url = new File(AGENT_INITIALIZER_JAR).toURI().toURL();
      } catch (MalformedURLException e) {
        throw new IllegalStateException(
            "Filename could not be parsed: "
                + AGENT_INITIALIZER_JAR
                + ". Initializer is not installed",
            e);
      }

      addURL(url);
    }
  }

  @Override
  public URL getResource(String resourceName) {
    URL bootstrapResource = bootstrapProxy.getResource(resourceName);
    if (null == bootstrapResource) {
      return super.getResource(resourceName);
    } else {
      return bootstrapResource;
    }
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

    public BootstrapClassLoaderProxy(URL[] urls) {
      super(urls, null);
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

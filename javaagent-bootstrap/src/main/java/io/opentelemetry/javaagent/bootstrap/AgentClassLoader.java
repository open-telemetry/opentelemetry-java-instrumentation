/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Classloader used to run the core agent.
 *
 * <p>It is built around the concept of a jar inside another jar. This classloader loads the files
 * of the internal jar to load classes and resources.
 */
public class AgentClassLoader extends URLClassLoader {
  static {
    ClassLoader.registerAsParallelCapable();
  }

  private static final Logger log = LoggerFactory.getLogger(AgentClassLoader.class);

  private static final String AGENT_INITIALIZER_JAR =
      System.getProperty("otel.initializer.jar", "");

  // Calling java.lang.instrument.Instrumentation#appendToBootstrapClassLoaderSearch
  // adds a jar to the bootstrap class lookup, but not to the resource lookup.
  // As a workaround, we keep a reference to the bootstrap jar
  // to use only for resource lookups.
  private final BootstrapClassLoaderProxy bootstrapProxy;

  /**
   * Construct a new AgentClassLoader
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

    InternalJarURLHandler internalJarURLHandler =
        new InternalJarURLHandler(internalJarFileName, bootstrapJarLocation);
    try {
      // The fields of the URL are mostly dummy.  InternalJarURLHandler is the only important
      // field.  If extending this class from Classloader instead of URLClassloader required less
      // boilerplate it could be used and the need for dummy fields would be reduced
      addURL(new URL("x-internal-jar", null, 0, "/", internalJarURLHandler));
    } catch (MalformedURLException e) {
      // This can't happen with current URL constructor
      log.error("URL malformed.  Unsupported JDK?", e);
    }

    if (!AGENT_INITIALIZER_JAR.isEmpty()) {
      URL url;
      try {
        url = new File(AGENT_INITIALIZER_JAR).toURI().toURL();
      } catch (MalformedURLException e) {
        log.warn(
            "Filename could not be parsed: "
                + AGENT_INITIALIZER_JAR
                + ". Initializer is not installed");
        return;
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

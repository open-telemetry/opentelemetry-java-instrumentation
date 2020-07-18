/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.auto.bootstrap;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import lombok.extern.slf4j.Slf4j;

/**
 * Classloader used to run the core agent.
 *
 * <p>It is built around the concept of a jar inside another jar. This classloader loads the files
 * of the internal jar to load classes and resources.
 */
@Slf4j
public class AgentClassLoader extends URLClassLoader {
  static {
    ClassLoader.registerAsParallelCapable();
  }

  private static final String AGENT_INITIALIZER_JAR = System.getProperty("ota.initializer.jar", "");

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
      final URL bootstrapJarLocation, final String internalJarFileName, final ClassLoader parent) {
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
    } catch (final MalformedURLException e) {
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
  public URL getResource(final String resourceName) {
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

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * This classloader is used to load arbitrary extensions for Otel Java instrumentation agent. Such
 * extensions may include SDK components (exporters or propagators) and additional instrumentations.
 * They have to be isolated and shaded to reduce interference with the user application and to make
 * it compatible with shaded SDK used by the agent.
 */
// TODO find a way to initialize logging before using this class
// TODO support scanning a folder for several extension jars and keep them isolated from each other
// Used by AgentInitializer
@SuppressWarnings({"unused", "SystemOut"})
public class ExtensionClassLoader extends URLClassLoader {
  // NOTE it's important not to use slf4j in this class, because this class is used before slf4j is
  // configured, and so using slf4j here would initialize slf4j-simple before we have a chance to
  // configure the logging levels

  static {
    ClassLoader.registerAsParallelCapable();
  }

  public static ClassLoader getInstance(ClassLoader parent) {
    // TODO add support for old deprecated property otel.javaagent.experimental.exporter.jar
    URL extension =
        parseLocation(
            System.getProperty(
                "otel.javaagent.experimental.extensions",
                System.getenv("OTEL_JAVAAGENT_EXPERIMENTAL_EXTENSIONS")));

    if (extension == null) {
      extension =
          parseLocation(
              System.getProperty(
                  "otel.javaagent.experimental.initializer.jar",
                  System.getenv("OTEL_JAVAAGENT_EXPERIMENTAL_INITIALIZER_JAR")));
      // TODO when logging is configured add warning about deprecated property
    }

    if (extension != null) {
      try {
        URL wrappedUrl = new URL("otel", null, -1, "/", new RemappingUrlStreamHandler(extension));
        return new ExtensionClassLoader(wrappedUrl, parent);
      } catch (MalformedURLException e) {
        // This can't happen with current URL constructor
        throw new IllegalStateException("URL malformed.  Unsupported JDK?", e);
      }
    }
    return parent;
  }

  private static URL parseLocation(String name) {
    if (name == null) {
      return null;
    }
    try {
      return new File(name).toURI().toURL();
    } catch (MalformedURLException e) {
      System.err.printf(
          "Filename could not be parsed: %s. Extension location is ignored%n", e.getMessage());
    }
    return null;
  }

  private ExtensionClassLoader(URL url, ClassLoader parent) {
    super(new URL[] {url}, parent);
  }
}

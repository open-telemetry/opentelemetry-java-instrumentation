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
public class ExtensionClassLoader extends URLClassLoader {

  public static ClassLoader getInstance(ClassLoader parent) {
    // TODO add support for old deprecated properties, otel.exporter.jar and otel.initializer.jar
    URL extension = parseLocation(System.getProperty("otel.javaagent.extensions", System.getenv("OTEL_JAVAAGENT_EXTENSIONS")));
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
      System.err.println("Filename could not be parsed: %s. Extension location is ignored");
      e.printStackTrace();
    }
    return null;
  }

  public ExtensionClassLoader(URL url, ClassLoader parent) {
    super(new URL[] {url}, parent);
  }
}

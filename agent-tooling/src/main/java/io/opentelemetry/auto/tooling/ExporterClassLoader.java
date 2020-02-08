package io.opentelemetry.auto.tooling;

import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLStreamHandlerFactory;

public class ExporterClassLoader extends URLClassLoader {
  public ExporterClassLoader(final URL[] urls, final ClassLoader parent) {
    super(urls, parent);
  }

  public ExporterClassLoader(final URL[] urls) {
    super(urls);
  }

  public ExporterClassLoader(
      final URL[] urls, final ClassLoader parent, final URLStreamHandlerFactory factory) {
    super(urls, parent, factory);
  }
}

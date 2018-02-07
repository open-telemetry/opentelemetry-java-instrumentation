package datadog.trace.agent;

import java.net.URL;
import java.net.URLClassLoader;

public class DatadogClassLoader extends URLClassLoader {
  // TODO: doc: explain why we need to use the bootstrap jar for resource lookup
  private final ClassLoader bootstrapResourceLocator;

  public DatadogClassLoader(URL bootstrapJarLocation, URL agentJarLocation, ClassLoader parent) {
    super(new URL[] {agentJarLocation}, parent);
    bootstrapResourceLocator = new URLClassLoader(new URL[] {bootstrapJarLocation}, null);
  }

  @Override
  public URL getResource(String resourceName) {
    final URL bootstrapResource = bootstrapResourceLocator.getResource(resourceName);
    if (null == bootstrapResource) {
      return super.getResource(resourceName);
    } else {
      return bootstrapResource;
    }
  }
}

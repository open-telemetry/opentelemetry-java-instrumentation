/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling

import io.opentelemetry.javaagent.spi.exporter.MetricExporterFactory
import io.opentelemetry.javaagent.spi.exporter.SpanExporterFactory
import io.opentelemetry.sdk.metrics.export.MetricExporter
import io.opentelemetry.sdk.trace.export.SpanExporter
import java.nio.charset.StandardCharsets
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import spock.lang.Specification

class ExporterClassLoaderTest extends Specification {

  // Verifies https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/542
  def "does not look in parent classloader for metric exporters"() {
    setup:
    def parentClassloader = new URLClassLoader([createJarWithClasses(MetricExporterFactoryParent)] as URL[])
    def childClassloader = new ExporterClassLoader(createJarWithClasses(MetricExporterFactoryChild), parentClassloader)

    when:
    ServiceLoader<MetricExporterFactory> serviceLoader = ServiceLoader.load(MetricExporterFactory, childClassloader)

    then:
    serviceLoader.size() == 1
  }

  def "does not look in parent classloader for span exporters"() {
    setup:
    def parentClassloader = new URLClassLoader([createJarWithClasses(SpanExporterFactoryParent)] as URL[])
    def childClassloader = new ExporterClassLoader(createJarWithClasses(SpanExporterFactoryChild), parentClassloader)

    when:
    ServiceLoader<SpanExporterFactory> serviceLoader = ServiceLoader.load(SpanExporterFactory, childClassloader)

    then:
    serviceLoader.size() == 1
  }

  static class MetricExporterFactoryParent implements MetricExporterFactory {

    @Override
    MetricExporter fromConfig(Properties config) {
      return null
    }

    @Override
    Set<String> getNames() {
      return null
    }
  }

  static class MetricExporterFactoryChild implements MetricExporterFactory {

    @Override
    MetricExporter fromConfig(Properties config) {
      return null
    }

    @Override
    Set<String> getNames() {
      return null
    }
  }

  static class SpanExporterFactoryParent implements SpanExporterFactory {

    @Override
    SpanExporter fromConfig(Properties config) {
      return null
    }

    @Override
    Set<String> getNames() {
      return null
    }
  }

  static class SpanExporterFactoryChild implements SpanExporterFactory {

    @Override
    SpanExporter fromConfig(Properties config) {
      return null
    }

    @Override
    Set<String> getNames() {
      return null
    }
  }

  static URL createJarWithClasses(final Class<?>... classes)
    throws IOException {
    File tmpJar = File.createTempFile(UUID.randomUUID().toString() + "-", ".jar")
    tmpJar.deleteOnExit()

    JarOutputStream target = new JarOutputStream(new FileOutputStream(tmpJar))
    for (Class<?> clazz : classes) {
      addToJar(clazz, clazz.getInterfaces()[0], target)
    }
    target.close()

    return tmpJar.toURI().toURL()
  }

  //This is mostly copy-pasted from IntegrationTestUtils, but we need to save service files as well
  private static void addToJar(final Class<?> clazz, final Class<?> serviceInterface, final JarOutputStream jarOutputStream)
    throws IOException {
    String resourceName = getResourceName(clazz.getName())

    ClassLoader loader = clazz.getClassLoader()
    if (null == loader) {
      // bootstrap resources can be fetched through the system loader
      loader = ClassLoader.getSystemClassLoader()
    }

    InputStream inputStream = null
    try {
      JarEntry entry = new JarEntry(resourceName)
      jarOutputStream.putNextEntry(entry)
      inputStream = loader.getResourceAsStream(resourceName)

      byte[] buffer = new byte[1024]
      while (true) {
        int count = inputStream.read(buffer)
        if (count == -1) {
          break
        }
        jarOutputStream.write(buffer, 0, count)
      }
      jarOutputStream.closeEntry()

      JarEntry serviceEntry = new JarEntry("META-INF/services/" + serviceInterface.getName())
      jarOutputStream.putNextEntry(serviceEntry)
      jarOutputStream.write(clazz.getName().getBytes(StandardCharsets.UTF_8))
      jarOutputStream.closeEntry()
    } finally {
      if (inputStream != null) {
        inputStream.close()
      }
    }
  }

  /** com.foo.Bar -> com/foo/Bar.class */
  private static String getResourceName(final String className) {
    return className.replace('.', '/') + ".class"
  }
}

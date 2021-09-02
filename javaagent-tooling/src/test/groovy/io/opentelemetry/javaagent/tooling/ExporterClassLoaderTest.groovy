/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling

import groovy.transform.CompileStatic
import io.opentelemetry.javaagent.spi.exporter.MetricExporterFactory
import io.opentelemetry.javaagent.spi.exporter.SpanExporterFactory
import io.opentelemetry.sdk.metrics.export.MetricExporter
import io.opentelemetry.sdk.trace.export.SpanExporter
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.jar.Manifest

class ExporterClassLoaderTest extends Specification {

  // Verifies https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/542
  def "does not look in parent classloader for metric exporters"() {
    setup:
    def parentClassloader = new ParentClassLoader([createJarWithClasses(MetricExporterFactoryParent)] as URL[])
    def childClassloader = new ExporterClassLoader(createJarWithClasses(MetricExporterFactoryChild), parentClassloader)

    when:
    ServiceLoader<MetricExporterFactory> serviceLoader = ServiceLoader.load(MetricExporterFactory, childClassloader)

    then:
    serviceLoader.size() == 1

    and:
    childClassloader.manifest != null

    when:
    MetricExporterFactory instance = serviceLoader.iterator().next()
    Class clazz = instance.getClass()

    then:
    clazz.getClassLoader() == childClassloader
  }

  def "does not look in parent classloader for span exporters"() {
    setup:
    def parentClassloader = new ParentClassLoader([createJarWithClasses(SpanExporterFactoryParent)] as URL[])
    def childClassloader = new ExporterClassLoader(createJarWithClasses(SpanExporterFactoryChild), parentClassloader)

    when:
    ServiceLoader<SpanExporterFactory> serviceLoader = ServiceLoader.load(SpanExporterFactory, childClassloader)

    then:
    serviceLoader.size() == 1

    and:
    childClassloader.manifest != null

    when:
    SpanExporterFactory instance = serviceLoader.iterator().next()
    Class clazz = instance.getClass()

    then:
    clazz.getClassLoader() == childClassloader
  }

  // Verifies that loading of exporter jar succeeds when there is a space in path to exporter jar
  def "load jar with space in path"() {
    setup:
    def parentClassloader = new ParentClassLoader()
    // " .jar" is used to make path to jar contain a space
    def childClassloader = new ExporterClassLoader(createJarWithClasses(" .jar", MetricExporterFactoryChild), parentClassloader)

    when:
    ServiceLoader<MetricExporterFactory> serviceLoader = ServiceLoader.load(MetricExporterFactory, childClassloader)

    then:
    serviceLoader.size() == 1

    and:
    childClassloader.manifest != null

    when:
    MetricExporterFactory instance = serviceLoader.iterator().next()
    Class clazz = instance.getClass()

    then:
    clazz.getClassLoader() == childClassloader

    and:
    clazz.getPackage().getImplementationVersion() == "test-implementation-version"
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

  static URL createJarWithClasses(final Class<?>... classes) {
    createJarWithClasses(".jar", classes)
  }

  static URL createJarWithClasses(final String suffix, final Class<?>... classes)
    throws IOException {
    File tmpJar = File.createTempFile(UUID.randomUUID().toString() + "-", suffix)
    tmpJar.deleteOnExit()

    JarOutputStream target = new JarOutputStream(new FileOutputStream(tmpJar))
    for (Class<?> clazz : classes) {
      addToJar(clazz, clazz.getInterfaces()[0], target)
    }

    Manifest manifest = new Manifest()
    Attributes attributes = manifest.getMainAttributes()
    attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0")
    attributes.put(Attributes.Name.SPECIFICATION_TITLE, "test-specification-title")
    attributes.put(Attributes.Name.SPECIFICATION_VERSION, "test-specification-version")
    attributes.put(Attributes.Name.SPECIFICATION_VENDOR, "test-specification-vendor")
    attributes.put(Attributes.Name.IMPLEMENTATION_TITLE, "test-implementation-title")
    attributes.put(Attributes.Name.IMPLEMENTATION_VERSION, "test-implementation-version")
    attributes.put(Attributes.Name.IMPLEMENTATION_VENDOR, "test-implementation-vendor")

    JarEntry manifestEntry = new JarEntry(JarFile.MANIFEST_NAME)
    target.putNextEntry(manifestEntry)
    manifest.write(target)
    target.closeEntry()

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

  @CompileStatic
  private static class ParentClassLoader extends URLClassLoader {

    ParentClassLoader() {
      super()
    }

    ParentClassLoader(URL[] urls) {
      super(urls)
    }

    @Override
    Package getPackage(String name) {
      // ExporterClassLoader uses getPackage to check whether package has already been
      // defined. As getPackage also searches packages from parent class loader we return
      // null here to ensure that package is defined in ExporterClassLoader.
      null
    }

    @Override
    Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
      // test classes are available in system class loader filter them so that
      // they would be loaded by ExporterClassLoader
      if (name.startsWith(ExporterClassLoaderTest.getName())) {
        throw new ClassNotFoundException(name)
      }
      return super.loadClass(name, resolve)
    }
  }
}

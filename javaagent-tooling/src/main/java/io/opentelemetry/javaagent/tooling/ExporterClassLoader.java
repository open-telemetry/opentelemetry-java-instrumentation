/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import static io.opentelemetry.javaagent.tooling.ShadingRemapper.rule;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import net.bytebuddy.jar.asm.ClassReader;
import net.bytebuddy.jar.asm.ClassWriter;
import net.bytebuddy.jar.asm.commons.ClassRemapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExporterClassLoader extends URLClassLoader {

  private static final Logger log = LoggerFactory.getLogger(ExporterClassLoader.class);

  // We need to prefix the names to prevent the gradle shadowJar relocation rules from touching
  // them. It's possible to do this by excluding this class from shading, but it may cause issue
  // with transitive dependencies down the line.
  private static final ShadingRemapper remapper =
      new ShadingRemapper(
          rule("#io.opentelemetry.api", "#io.opentelemetry.javaagent.shaded.io.opentelemetry.api"),
          rule(
              "#io.opentelemetry.context",
              "#io.opentelemetry.javaagent.shaded.io.opentelemetry.context"),
          rule(
              "#io.opentelemetry.extension.aws",
              "#io.opentelemetry.javaagent.shaded.io.opentelemetry.extension.aws"),
          rule("#java.util.logging.Logger", "#io.opentelemetry.javaagent.bootstrap.PatchLogger"),
          rule("#org.slf4j", "#io.opentelemetry.javaagent.slf4j"));

  private final Manifest manifest;

  public ExporterClassLoader(URL url, ClassLoader parent) {
    super(new URL[] {url}, parent);
    this.manifest = getManifest(url);
  }

  @Override
  public Enumeration<URL> getResources(String name) throws IOException {
    // A small hack to prevent other exporters from being loaded by this classloader if they
    // should happen to appear on the classpath.
    if (name.equals("META-INF/services/io.opentelemetry.javaagent.spi.exporter.SpanExporterFactory")
        || name.equals(
            "META-INF/services/io.opentelemetry.javaagent.spi.exporter.MetricExporterFactory")) {
      return findResources(name);
    }
    return super.getResources(name);
  }

  @Override
  protected Class<?> findClass(String name) throws ClassNotFoundException {
    // Use resource loading to get the class as a stream of bytes, then use ASM to transform it.
    InputStream in = getResourceAsStream(name.replace('.', '/') + ".class");
    if (in == null) {
      throw new ClassNotFoundException(name);
    }
    try {
      byte[] bytes = remapClassBytes(in);
      definePackageIfNeeded(name);
      return defineClass(name, bytes, 0, bytes.length);
    } catch (IOException e) {
      throw new ClassNotFoundException(name, e);
    } finally {
      try {
        in.close();
      } catch (IOException e) {
        log.debug(e.getMessage(), e);
      }
    }
  }

  private void definePackageIfNeeded(String className) {
    String packageName = getPackageName(className);
    if (packageName == null) {
      // default package
      return;
    }
    if (isPackageDefined(packageName)) {
      // package has already been defined
      return;
    }
    try {
      definePackage(packageName);
    } catch (IllegalArgumentException e) {
      // this exception is thrown when the package has already been defined, which is possible due
      // to race condition with the check above
      if (!isPackageDefined(packageName)) {
        // this shouldn't happen however
        log.error(e.getMessage(), e);
      }
    }
  }

  private boolean isPackageDefined(String packageName) {
    return getPackage(packageName) != null;
  }

  private void definePackage(String packageName) {
    if (manifest == null) {
      definePackage(packageName, null, null, null, null, null, null, null);
    } else {
      definePackage(packageName, manifest, null);
    }
  }

  private static byte[] remapClassBytes(InputStream in) throws IOException {
    ClassWriter cw = new ClassWriter(0);
    ClassReader cr = new ClassReader(in);
    cr.accept(new ClassRemapper(cw, remapper), ClassReader.EXPAND_FRAMES);
    return cw.toByteArray();
  }

  private static String getPackageName(String className) {
    int index = className.lastIndexOf('.');
    return index == -1 ? null : className.substring(0, index);
  }

  private static Manifest getManifest(URL url) {
    try (JarFile jarFile = new JarFile(url.toURI().getPath())) {
      return jarFile.getManifest();
    } catch (IOException | URISyntaxException e) {
      log.warn(e.getMessage(), e);
    }
    return null;
  }
}

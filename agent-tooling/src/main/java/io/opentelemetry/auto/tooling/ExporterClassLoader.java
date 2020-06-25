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
package io.opentelemetry.auto.tooling;

import static io.opentelemetry.auto.tooling.ShadingRemapper.rule;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.jar.asm.ClassReader;
import net.bytebuddy.jar.asm.ClassWriter;
import net.bytebuddy.jar.asm.commons.ClassRemapper;

@Slf4j
public class ExporterClassLoader extends URLClassLoader {
  // We need to prefix the names to prevent the gradle shadowJar relocation rules from touching
  // them. It's possible to do this by excluding this class from shading, but it may cause issue
  // with transitive dependencies down the line.
  private static final ShadingRemapper remapper =
      new ShadingRemapper(
          rule(
              "#io.opentelemetry.OpenTelemetry",
              "#io.opentelemetry.auto.shaded.io.opentelemetry.OpenTelemetry"),
          rule("#io.opentelemetry.common", "#io.opentelemetry.auto.shaded.io.opentelemetry.common"),
          rule(
              "#io.opentelemetry.context",
              "#io.opentelemetry.auto.shaded.io.opentelemetry.context"),
          rule(
              "#io.opentelemetry.correlationcontext",
              "#io.opentelemetry.auto.shaded.io.opentelemetry.correlationcontext"),
          rule(
              "#io.opentelemetry.internal",
              "#io.opentelemetry.auto.shaded.io.opentelemetry.internal"),
          rule(
              "#io.opentelemetry.metrics",
              "#io.opentelemetry.auto.shaded.io.opentelemetry.metrics"),
          rule("#io.opentelemetry.trace", "#io.opentelemetry.auto.shaded.io.opentelemetry.trace"),
          rule("#java.util.logging.Logger", "#io.opentelemetry.auto.bootstrap.PatchLogger"),
          rule("#org.slf4j", "#io.opentelemetry.auto.slf4j"));

  private final Manifest manifest;

  public ExporterClassLoader(final URL url, final ClassLoader parent) {
    super(new URL[] {url}, parent);
    this.manifest = getManifest(url);
  }

  @Override
  public Enumeration<URL> getResources(final String name) throws IOException {
    // A small hack to prevent other exporters from being loaded by this classloader if they
    // should happen to appear on the classpath.
    if (name.equals(
            "META-INF/services/io.opentelemetry.sdk.contrib.auto.config.SpanExporterFactory")
        || name.equals(
            "META-INF/services/io.opentelemetry.sdk.contrib.auto.config.MetricExporterFactory")) {
      return findResources(name);
    }
    return super.getResources(name);
  }

  @Override
  protected Class<?> findClass(final String name) throws ClassNotFoundException {
    // Use resource loading to get the class as a stream of bytes, then use ASM to transform it.
    InputStream in = getResourceAsStream(name.replace('.', '/') + ".class");
    if (in == null) {
      throw new ClassNotFoundException(name);
    }
    try {
      final byte[] bytes = remapClassBytes(in);
      definePackageIfNeeded(name);
      return defineClass(name, bytes, 0, bytes.length);
    } catch (final IOException e) {
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
    final ClassWriter cw = new ClassWriter(0);
    final ClassReader cr = new ClassReader(in);
    cr.accept(new ClassRemapper(cw, remapper), ClassReader.EXPAND_FRAMES);
    return cw.toByteArray();
  }

  private static String getPackageName(String className) {
    int index = className.lastIndexOf('.');
    return index == -1 ? null : className.substring(0, index);
  }

  private static Manifest getManifest(URL url) {
    try {
      JarFile jarFile = new JarFile(url.getFile());
      return jarFile.getManifest();
    } catch (IOException e) {
      log.warn(e.getMessage(), e);
    }
    return null;
  }
}

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import static io.opentelemetry.javaagent.tooling.ShadingRemapper.rule;

import io.opentelemetry.javaagent.bootstrap.InternalJarUrlConnection;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

class RemappingUrlStreamHandler extends URLStreamHandler {

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

  private final JarFile delegateJarFile;

  public RemappingUrlStreamHandler(URL delegateJarFileLocation) {
    try {
      delegateJarFile = new JarFile(new File(delegateJarFileLocation.toURI()), false);
    } catch (URISyntaxException | IOException e) {
      throw new IllegalStateException("Unable to read internal jar", e);
    }
  }

  /** {@inheritDoc} */
  protected URLConnection openConnection(URL url) {
    try {
      String file = url.getFile();
      if ("/".equals(file)) {
        // "/" is used as the default url of the jar
        // This is called by the SecureClassLoader trying to obtain permissions
        // nullInputStream() is not available until Java 11
        return new InternalJarUrlConnection(url, new ByteArrayInputStream(new byte[0]), 0);
      }

      if (file.startsWith("/")) {
        file = file.substring(1);
      }
      JarEntry entry = delegateJarFile.getJarEntry(file);
      if (entry == null) {
        return null;
      }

      if (file.endsWith(".class")) {
        return new RemappingUrlConnection(url, delegateJarFile, entry);
      } else {
        InputStream is = delegateJarFile.getInputStream(entry);
        return new InternalJarUrlConnection(url, is, entry.getSize());
      }
    } catch (IOException e) {
      System.err.printf("Failed to load and remap %s: %s%n", url, e.getMessage());
    }
    return new InternalJarUrlConnection(url, new ByteArrayInputStream(new byte[0]), 0);
  }
}

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

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
        return new InputStreamUrlConnection(url, new ByteArrayInputStream(new byte[0]), 0);
      }

      if (file.startsWith("/")) {
        file = file.substring(1);
      }
      JarEntry entry = delegateJarFile.getJarEntry(file);
      if (entry == null) {
        return null;
      }

      // That will NOT remap the content of files under META-INF/services
      if (file.endsWith(".class")) {
        return new RemappingUrlConnection(url, delegateJarFile, entry);
      } else {
        InputStream is = delegateJarFile.getInputStream(entry);
        return new InputStreamUrlConnection(url, is, entry.getSize());
      }
    } catch (IOException e) {
      System.err.printf("Failed to load and remap %s: %s%n", url, e.getMessage());
    }
    return new InputStreamUrlConnection(url, new ByteArrayInputStream(new byte[0]), 0);
  }
}

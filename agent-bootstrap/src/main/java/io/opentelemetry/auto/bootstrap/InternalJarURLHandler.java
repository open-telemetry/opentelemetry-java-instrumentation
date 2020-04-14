/*
 * Copyright 2020, OpenTelemetry Authors
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
package io.opentelemetry.auto.bootstrap;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.file.NoSuchFileException;
import java.security.Permission;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class InternalJarURLHandler extends URLStreamHandler {
  private final Map<String, JarEntry> filenameToEntry = new HashMap<>();
  private JarFile bootstrapJarFile;

  InternalJarURLHandler(final String internalJarFileName, final URL bootstrapJarLocation) {
    final String filePrefix = internalJarFileName + "/";

    try {
      if (bootstrapJarLocation != null) {
        bootstrapJarFile = new JarFile(new File(bootstrapJarLocation.toURI()), false);
        final Enumeration<JarEntry> entries = bootstrapJarFile.entries();
        while (entries.hasMoreElements()) {
          final JarEntry entry = entries.nextElement();

          if (!entry.isDirectory() && entry.getName().startsWith(filePrefix)) {
            filenameToEntry.put(entry.getName().substring(internalJarFileName.length()), entry);
          }
        }
      }
    } catch (final URISyntaxException | IOException e) {
      log.error("Unable to read internal jar", e);
    }

    if (filenameToEntry.isEmpty()) {
      log.warn("No internal jar entries found");
    }
  }

  @Override
  protected URLConnection openConnection(final URL url) throws IOException {
    final String filename = url.getFile().replaceAll("\\.class$", ".classdata");
    if ("/".equals(filename)) {
      // "/" is used as the default url of the jar
      // This is called by the SecureClassLoader trying to obtain permissions

      // nullInputStream() is not available until Java 11
      return new InternalJarURLConnection(url, new ByteArrayInputStream(new byte[0]));
    } else if (filenameToEntry.containsKey(filename)) {
      final JarEntry entry = filenameToEntry.get(filename);
      return new InternalJarURLConnection(url, bootstrapJarFile.getInputStream(entry));
    } else {
      throw new NoSuchFileException(url.getFile(), null, url.getFile() + " not in internal jar");
    }
  }

  private static class InternalJarURLConnection extends URLConnection {
    private final InputStream inputStream;

    private InternalJarURLConnection(final URL url, final InputStream inputStream) {
      super(url);
      this.inputStream = inputStream;
    }

    @Override
    public void connect() {
      connected = true;
    }

    @Override
    public InputStream getInputStream() {
      return inputStream;
    }

    @Override
    public Permission getPermission() {
      // No permissions needed because all classes are in memory
      return null;
    }
  }
}

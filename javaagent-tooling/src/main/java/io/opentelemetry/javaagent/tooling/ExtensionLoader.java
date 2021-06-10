/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import net.bytebuddy.dynamic.loading.MultipleParentClassLoader;

/**
 * This class creates a classloader which encapsulates arbitrary extensions for Otel Java
 * instrumentation agent. Such extensions may include SDK components (exporters or propagators) and
 * additional instrumentations. They have to be isolated and shaded to reduce interference with the
 * user application and to make it compatible with shaded SDK used by the agent. Thus each extension
 * jar gets a separate classloader and all of them are aggregated with the help of {@link
 * MultipleParentClassLoader}.
 */
// TODO find a way to initialize logging before using this class
// Used by AgentInitializer
@SuppressWarnings({"unused", "SystemOut"})
public class ExtensionLoader {
  // NOTE it's important not to use slf4j in this class, because this class is used before slf4j is
  // configured, and so using slf4j here would initialize slf4j-simple before we have a chance to
  // configure the logging levels

  public static ClassLoader getInstance(ClassLoader parent) {
    List<URL> extensions = new ArrayList<>();

    includeEmbeddedExtensionsIfFound(parent, extensions);

    // TODO add support for old deprecated property otel.javaagent.experimental.exporter.jar
    extensions.addAll(
        parseLocation(
            System.getProperty(
                "otel.javaagent.experimental.extensions",
                System.getenv("OTEL_JAVAAGENT_EXPERIMENTAL_EXTENSIONS"))));

    extensions.addAll(
        parseLocation(
            System.getProperty(
                "otel.javaagent.experimental.initializer.jar",
                System.getenv("OTEL_JAVAAGENT_EXPERIMENTAL_INITIALIZER_JAR"))));
    // TODO when logging is configured add warning about deprecated property

    List<ClassLoader> delegates = new ArrayList<>(extensions.size());
    for (URL url : extensions) {
      delegates.add(getDelegate(parent, url));
    }
    return new MultipleParentClassLoader(parent, delegates);
  }

  private static void includeEmbeddedExtensionsIfFound(ClassLoader parent, List<URL> extensions) {
    URL embeddedExtension = parent.getResource("otel-extensions.jar");
    if (embeddedExtension != null) {
      try {
        File tempFile = Files.createTempFile("otel-extensions", null).toFile();
        tempFile.deleteOnExit();
        extractFile(embeddedExtension, tempFile);
        addFileUrl(extensions, tempFile);
      } catch (IOException ignored) {
        System.err.println("Failed to open embedded extensions");
      }
    }
  }

  private static URLClassLoader getDelegate(ClassLoader parent, URL extensionUrl) {
    return new URLClassLoader(new URL[] {extensionUrl}, parent);
  }

  private static List<URL> parseLocation(String locationName) {
    List<URL> result = new ArrayList<>();

    if (locationName == null) {
      return result;
    }

    File location = new File(locationName);
    if (location.isFile()) {
      addFileUrl(result, location);
    } else if (location.isDirectory()) {
      File[] files = location.listFiles(f -> f.isFile() && f.getName().endsWith(".jar"));
      if (files != null) {
        // TODO filter out agent file itself
        for (File file : files) {
          addFileUrl(result, file);
        }
      }
    }
    return result;
  }

  private static void addFileUrl(List<URL> result, File file) {
    try {
      URL wrappedUrl = new URL("otel", null, -1, "/", new RemappingUrlStreamHandler(file));
      result.add(wrappedUrl);
    } catch (MalformedURLException ignored) {
      System.err.println("Ignoring " + file);
    }
  }

  public static void extractFile(URL url, File outputFile) throws IOException {
    try (InputStream in = url.openStream();
        ReadableByteChannel rbc = Channels.newChannel(in);
        FileOutputStream fos = new FileOutputStream(outputFile)) {
      fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
    }
  }
}

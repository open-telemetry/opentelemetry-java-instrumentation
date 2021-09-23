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
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
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
public class ExtensionClassLoader extends URLClassLoader {
  public static final String EXTENSIONS_CONFIG = "otel.javaagent.extensions";

  // NOTE it's important not to use slf4j in this class, because this class is used before slf4j is
  // configured, and so using slf4j here would initialize slf4j-simple before we have a chance to
  // configure the logging levels

  static {
    ClassLoader.registerAsParallelCapable();
  }

  public static ClassLoader getInstance(ClassLoader parent, File javaagentFile) {
    List<URL> extensions = new ArrayList<>();

    includeEmbeddedExtensionsIfFound(parent, extensions, javaagentFile);

    extensions.addAll(
        parseLocation(
            System.getProperty(EXTENSIONS_CONFIG, System.getenv("OTEL_JAVAAGENT_EXTENSIONS")),
            javaagentFile));

    extensions.addAll(
        parseLocation(
            System.getProperty(
                "otel.javaagent.experimental.extensions",
                System.getenv("OTEL_JAVAAGENT_EXPERIMENTAL_EXTENSIONS")),
            javaagentFile));

    extensions.addAll(
        parseLocation(
            System.getProperty(
                "otel.javaagent.experimental.initializer.jar",
                System.getenv("OTEL_JAVAAGENT_EXPERIMENTAL_INITIALIZER_JAR")),
            javaagentFile));
    // TODO when logging is configured add warning about deprecated property

    if (extensions.isEmpty()) {
      return parent;
    }

    List<ClassLoader> delegates = new ArrayList<>(extensions.size());
    for (URL url : extensions) {
      delegates.add(getDelegate(parent, url));
    }
    return new MultipleParentClassLoader(parent, delegates);
  }

  private static void includeEmbeddedExtensionsIfFound(
      ClassLoader parent, List<URL> extensions, File javaagentFile) {
    try {
      JarFile jarFile = new JarFile(javaagentFile, false);
      Enumeration<JarEntry> entryEnumeration = jarFile.entries();
      String prefix = "extensions/";
      File tempDirectory = null;
      while (entryEnumeration.hasMoreElements()) {
        JarEntry jarEntry = entryEnumeration.nextElement();

        if (jarEntry.getName().startsWith(prefix) && !jarEntry.isDirectory()) {
          tempDirectory = ensureTempDirectoryExists(tempDirectory);

          File tempFile = new File(tempDirectory, jarEntry.getName().substring(prefix.length()));
          if (tempFile.createNewFile()) {
            tempFile.deleteOnExit();
            extractFile(jarFile, jarEntry, tempFile);
            addFileUrl(extensions, tempFile);
          } else {
            System.err.println("Failed to create temp file " + tempFile);
          }
        }
      }
    } catch (IOException ex) {
      System.err.println("Failed to open embedded extensions " + ex.getMessage());
    }
  }

  private static File ensureTempDirectoryExists(File tempDirectory) throws IOException {
    if (tempDirectory == null) {
      tempDirectory = Files.createTempDirectory("otel-extensions").toFile();
      tempDirectory.deleteOnExit();
    }
    return tempDirectory;
  }

  private static URLClassLoader getDelegate(ClassLoader parent, URL extensionUrl) {
    return new ExtensionClassLoader(new URL[] {extensionUrl}, parent);
  }

  private static List<URL> parseLocation(String locationName, File javaagentFile) {
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
        for (File file : files) {
          if (!file.getAbsolutePath().equals(javaagentFile.getAbsolutePath())) {
            addFileUrl(result, file);
          }
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

  private static void extractFile(JarFile jarFile, JarEntry jarEntry, File outputFile)
      throws IOException {
    try (InputStream in = jarFile.getInputStream(jarEntry);
        ReadableByteChannel rbc = Channels.newChannel(in);
        FileOutputStream fos = new FileOutputStream(outputFile)) {
      fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
    }
  }

  private ExtensionClassLoader(URL[] urls, ClassLoader parent) {
    super(urls, parent);
  }
}

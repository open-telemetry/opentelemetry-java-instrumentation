/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import static java.util.logging.Level.WARNING;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.jar.Manifest;
import java.util.logging.Logger;
import javax.annotation.Nullable;

class JarFileDetector {
  private final Supplier<String[]> getProcessHandleArguments;
  private final Function<String, String> getSystemProperty;
  private final Predicate<Path> fileExists;
  private final Function<Path, Optional<Manifest>> manifestReader;

  private static final Logger logger = Logger.getLogger(JarFileDetector.class.getName());

  public JarFileDetector() {
    this(
        ProcessArguments::getProcessArguments,
        System::getProperty,
        Files::isRegularFile,
        JarFileDetector::readManifest);
  }

  // visible for tests
  JarFileDetector(
      Supplier<String[]> getProcessHandleArguments,
      Function<String, String> getSystemProperty,
      Predicate<Path> fileExists,
      Function<Path, Optional<Manifest>> manifestReader) {
    this.getProcessHandleArguments = getProcessHandleArguments;
    this.getSystemProperty = getSystemProperty;
    this.fileExists = fileExists;
    this.manifestReader = manifestReader;
  }

  @Nullable
  Path getJarPath() {
    return ResourceDiscoveryCache.get(
        "jarPath",
        () -> {
          Path jarPath = getJarPathFromProcessHandle();
          if (jarPath != null) {
            return jarPath;
          }
          return getJarPathFromSunCommandLine();
        });
  }

  Optional<Manifest> getManifestFromJarFile() {
    Path jarPath = getJarPath();
    if (jarPath == null) {
      return Optional.empty();
    }
    return manifestReader.apply(jarPath);
  }

  private static Optional<Manifest> readManifest(Path jarPath) {
    try (InputStream s =
        new URL(String.format("jar:%s!/META-INF/MANIFEST.MF", jarPath.toUri())).openStream()) {
      Manifest manifest = new Manifest();
      manifest.read(s);
      return Optional.of(manifest);
    } catch (Exception e) {
      logger.log(WARNING, "Error reading manifest", e);
      return Optional.empty();
    }
  }

  @Nullable
  private Path getJarPathFromProcessHandle() {
    String[] javaArgs = getProcessHandleArguments.get();
    for (int i = 0; i < javaArgs.length; ++i) {
      if ("-jar".equals(javaArgs[i]) && (i < javaArgs.length - 1)) {
        return Paths.get(javaArgs[i + 1]);
      }
    }
    return null;
  }

  @Nullable
  private Path getJarPathFromSunCommandLine() {
    // the jar file is the first argument in the command line string
    String programArguments = getSystemProperty.apply("sun.java.command");
    if (programArguments == null) {
      return null;
    }

    // Take the path until the first space. If the path doesn't exist extend it up to the next
    // space. Repeat until a path that exists is found or input runs out.
    int next = 0;
    while (true) {
      int nextSpace = programArguments.indexOf(' ', next);
      if (nextSpace == -1) {
        return pathIfExists(programArguments);
      }
      Path path = pathIfExists(programArguments.substring(0, nextSpace));
      next = nextSpace + 1;
      if (path != null) {
        return path;
      }
    }
  }

  @Nullable
  private Path pathIfExists(String programArguments) {
    Path candidate;
    try {
      candidate = Paths.get(programArguments);
    } catch (InvalidPathException e) {
      return null;
    }
    return fileExists.test(candidate) ? candidate : null;
  }
}

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import io.opentelemetry.sdk.autoconfigure.spi.internal.ConditionalResourceProvider;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

public abstract class JarResourceDetector implements ConditionalResourceProvider {
  protected static final Logger logger = Logger.getLogger(JarServiceNameDetector.class.getName());
  private static final Pattern JAR_FILE_VERSION_PATTERN = Pattern.compile("[-_]v?\\d.*");
  private static final Pattern ANY_DIGIT = Pattern.compile("\\d");
  protected final Supplier<String[]> getProcessHandleArguments;
  protected final Function<String, String> getSystemProperty;
  protected final Predicate<Path> fileExists;

  public JarResourceDetector(
      Supplier<String[]> getProcessHandleArguments,
      Function<String, String> getSystemProperty,
      Predicate<Path> fileExists) {
    this.getProcessHandleArguments = getProcessHandleArguments;
    this.getSystemProperty = getSystemProperty;
    this.fileExists = fileExists;
  }

  protected Optional<NameAndVersion> getServiceNameAndVersion() {
    Path jarPath = getJarPathFromProcessHandle();
    if (jarPath == null) {
      jarPath = getJarPathFromSunCommandLine();
    }
    if (jarPath == null) {
      return Optional.empty();
    }

    String jarName = jarPath.getFileName().toString();
    int dotIndex = jarName.lastIndexOf(".");
    if (dotIndex == -1 || ANY_DIGIT.matcher(jarName.substring(dotIndex)).find()) {
      // don't change if digit it extension, it's probably a version
      return Optional.of(new NameAndVersion(jarName, Optional.empty()));
    }

    return Optional.of(JarResourceDetector.getNameAndVersion(jarName.substring(0, dotIndex)));
  }

  private static NameAndVersion getNameAndVersion(String jarNameWithoutExtension) {
    Matcher matcher = JAR_FILE_VERSION_PATTERN.matcher(jarNameWithoutExtension);
    if (matcher.find()) {
      int start = matcher.start();
      String name = jarNameWithoutExtension.substring(0, start);
      String version = jarNameWithoutExtension.substring(start + 1);
      return new NameAndVersion(name, Optional.of(version));
    }

    return new NameAndVersion(jarNameWithoutExtension, Optional.empty());
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

  @Override
  public int order() {
    // make it run later than the SpringBootServiceNameDetector
    return 1000;
  }

  protected static class NameAndVersion {
    final String name;
    final Optional<String> version;

    NameAndVersion(String name, Optional<String> version) {
      this.name = name;
      this.version = version;
    }

    @Override
    public String toString() {
      return version
          .map(v -> String.format("name: %s, version: %s", name, v))
          .orElse(String.format("name: %s", name));
    }
  }
}

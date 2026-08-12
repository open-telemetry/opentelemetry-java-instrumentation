/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.utils;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import io.opentelemetry.instrumentation.docs.internal.InstrumentationType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;

public record FileManager(Path rootDir) {
  private static final Logger logger = Logger.getLogger(FileManager.class.getName());

  public List<InstrumentationPath> getInstrumentationPaths() throws IOException {
    Path instrumentationRoot = rootDir.resolve("instrumentation");

    try (Stream<Path> walk = Files.walk(instrumentationRoot)) {
      return walk.filter(Files::isDirectory)
          // the exclusion rules are written against repository-relative paths, so a checkout
          // directory that happens to contain something like "build" or "-common-" does not
          // exclude every module
          .filter(dir -> isValidInstrumentationPath(rootDir.relativize(dir).toString()))
          .map(this::parseInstrumentationPath)
          .collect(toList());
    }
  }

  private InstrumentationPath parseInstrumentationPath(Path directory) {
    InstrumentationType instrumentationType =
        InstrumentationType.fromString(directory.getFileName().toString());

    // the directory is known to end with a javaagent/library segment nested under
    // instrumentation/, so it always has a parent
    Path moduleDirectory = requireNonNull(directory.getParent());
    String name = moduleDirectory.getFileName().toString();
    String namespace = name.contains("-") ? name.split("-")[0] : name;

    return new InstrumentationPath(
        name,
        toUnixString(rootDir.relativize(moduleDirectory)),
        namespace,
        namespace,
        instrumentationType);
  }

  public static boolean isValidInstrumentationPath(String filePath) {
    if (filePath == null || filePath.isEmpty()) {
      return false;
    }
    String normalized = normalizeSeparators(filePath);
    String instrumentationSegment = "instrumentation/";

    if (!normalized.contains(instrumentationSegment)) {
      return false;
    }

    int javaagentCount = normalized.split("/javaagent", -1).length - 1;
    if (javaagentCount > 1) {
      return false;
    }

    if (normalized.matches(
        ".*(/test/|/testing|/build/|-common/|-common-|common-|/compile-stub/|-testing|bootstrap/src).*")) {
      return false;
    }

    return normalized.endsWith("javaagent") || normalized.endsWith("library");
  }

  private static String normalizeSeparators(String filePath) {
    return filePath.replace('\\', '/');
  }

  /**
   * Renders a relative path using forward slashes on every platform, so that generated
   * documentation is identical regardless of the operating system it was generated on.
   */
  private static String toUnixString(Path relativePath) {
    return StreamSupport.stream(relativePath.spliterator(), false)
        .map(Path::toString)
        .collect(joining("/"));
  }

  public List<Path> findBuildGradleFiles(String instrumentationDirectory) {
    Path modulePath = rootDir.resolve(instrumentationDirectory);

    try (Stream<Path> walk = Files.walk(modulePath)) {
      return walk.filter(Files::isRegularFile)
          .filter(
              path ->
                  path.getFileName().toString().equals("build.gradle.kts")
                      && !containsElement(modulePath.relativize(path), "testing")
                      && !isInNestedInstrumentationModule(path, modulePath))
          .collect(toList());
    } catch (IOException e) {
      logger.severe("Error traversing directory: " + e.getMessage());
      return new ArrayList<>();
    }
  }

  /**
   * Checks if a file path is inside a nested instrumentation module. A nested module is identified
   * by having a javaagent/ or library/ directory that is NOT at the root level.
   *
   * @param filePath The file path to check
   * @param rootPath The root instrumentation directory path
   * @return true if the file is in a nested instrumentation module
   */
  private static boolean isInNestedInstrumentationModule(Path filePath, Path rootPath) {
    Path relativePath = rootPath.relativize(filePath);

    // Find the first javaagent or library segment
    for (int i = 0; i < relativePath.getNameCount(); i++) {
      String segment = relativePath.getName(i).toString();
      if (segment.equals("javaagent") || segment.equals("library")) {
        // If javaagent/library is not the first segment, it's a nested module
        return i > 0;
      }
    }

    return false;
  }

  private static boolean containsElement(Path path, String element) {
    for (Path segment : path) {
      if (segment.toString().equals(element)) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  public String getMetaDataFile(String instrumentationDirectory) {
    Path metadataFile = rootDir.resolve(instrumentationDirectory).resolve("metadata.yaml");
    if (Files.exists(metadataFile)) {
      return readFileToString(metadataFile);
    }
    return null;
  }

  @Nullable
  public static String readFileToString(Path filePath) {
    try {
      return Files.readString(filePath);
    } catch (IOException e) {
      logger.severe("Error reading file: " + e.getMessage());
      return null;
    }
  }
}

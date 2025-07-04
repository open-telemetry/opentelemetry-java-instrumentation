/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.utils;

import io.opentelemetry.instrumentation.docs.internal.InstrumentationType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;

public record FileManager(String rootDir) {
  private static final Logger logger = Logger.getLogger(FileManager.class.getName());

  public List<InstrumentationPath> getInstrumentationPaths() throws IOException {
    Path rootPath = Paths.get(rootDir + "instrumentation");

    try (Stream<Path> walk = Files.walk(rootPath)) {
      return walk.filter(Files::isDirectory)
          .filter(dir -> isValidInstrumentationPath(dir.toString()))
          .map(dir -> parseInstrumentationPath(dir.toString()))
          .collect(Collectors.toList());
    }
  }

  @Nullable
  private static InstrumentationPath parseInstrumentationPath(String filePath) {
    if (filePath == null || filePath.isEmpty()) {
      return null;
    }

    String instrumentationSegment = "/instrumentation/";
    int startIndex = filePath.indexOf(instrumentationSegment) + instrumentationSegment.length();
    String[] parts = filePath.substring(startIndex).split("/");

    if (parts.length < 2) {
      return null;
    }

    InstrumentationType instrumentationType =
        InstrumentationType.fromString(parts[parts.length - 1]);
    String name = parts[parts.length - 2];
    String namespace = name.contains("-") ? name.split("-")[0] : name;

    return new InstrumentationPath(name, filePath, namespace, namespace, instrumentationType);
  }

  public static boolean isValidInstrumentationPath(String filePath) {
    if (filePath == null || filePath.isEmpty()) {
      return false;
    }
    String instrumentationSegment = "instrumentation/";

    if (!filePath.contains(instrumentationSegment)) {
      return false;
    }

    int javaagentCount = filePath.split("/javaagent", -1).length - 1;
    if (javaagentCount > 1) {
      return false;
    }

    if (filePath.matches(
        ".*(/test/|/testing|/build/|-common/|-common-|common-|-testing|bootstrap/src).*")) {
      return false;
    }

    return filePath.endsWith("javaagent") || filePath.endsWith("library");
  }

  public List<String> findBuildGradleFiles(String instrumentationDirectory) {
    Path rootPath = Paths.get(rootDir + instrumentationDirectory);

    try (Stream<Path> walk = Files.walk(rootPath)) {
      return walk.filter(Files::isRegularFile)
          .filter(
              path ->
                  path.getFileName().toString().equals("build.gradle.kts")
                      && !path.toString().contains("/testing/"))
          .map(Path::toString)
          .collect(Collectors.toList());
    } catch (IOException e) {
      logger.severe("Error traversing directory: " + e.getMessage());
      return new ArrayList<>();
    }
  }

  @Nullable
  public String getMetaDataFile(String instrumentationDirectory) {
    String metadataFile = rootDir + instrumentationDirectory + "/metadata.yaml";
    if (Files.exists(Paths.get(metadataFile))) {
      return readFileToString(metadataFile);
    }
    return null;
  }

  @Nullable
  public static String readFileToString(String filePath) {
    try {
      return Files.readString(Paths.get(filePath));
    } catch (IOException e) {
      logger.severe("Error reading file: " + e.getMessage());
      return null;
    }
  }
}

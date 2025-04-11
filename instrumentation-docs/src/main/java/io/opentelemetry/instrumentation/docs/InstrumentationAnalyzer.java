/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs;

import static io.opentelemetry.instrumentation.docs.parsers.GradleParser.parseGradleFile;

import io.opentelemetry.instrumentation.docs.internal.DependencyInfo;
import io.opentelemetry.instrumentation.docs.internal.InstrumentationEntity;
import io.opentelemetry.instrumentation.docs.internal.InstrumentationType;
import io.opentelemetry.instrumentation.docs.utils.FileManager;
import io.opentelemetry.instrumentation.docs.utils.InstrumentationPath;
import io.opentelemetry.instrumentation.docs.utils.YamlHelper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

class InstrumentationAnalyzer {

  private final FileManager fileManager;

  InstrumentationAnalyzer(FileManager fileManager) {
    this.fileManager = fileManager;
  }

  /**
   * Converts a list of {@link InstrumentationPath} into a list of {@link InstrumentationEntity},
   *
   * @param paths the list of {@link InstrumentationPath} objects to be converted
   * @return a list of {@link InstrumentationEntity} objects with aggregated types
   */
  public static List<InstrumentationEntity> convertToEntities(List<InstrumentationPath> paths) {
    Map<String, InstrumentationEntity> entityMap = new HashMap<>();

    for (InstrumentationPath path : paths) {
      String key = path.group() + ":" + path.namespace() + ":" + path.instrumentationName();
      if (!entityMap.containsKey(key)) {
        entityMap.put(
            key,
            new InstrumentationEntity.Builder()
                .srcPath(path.srcPath().replace("/javaagent", "").replace("/library", ""))
                .instrumentationName(path.instrumentationName())
                .namespace(path.namespace())
                .group(path.group())
                .build());
      }
    }

    return new ArrayList<>(entityMap.values());
  }

  /**
   * Analyzes the given root directory to find all instrumentation paths and then analyze them.
   * Extracts version information from each instrumentation's build.gradle file. Extracts
   * information from metadata.yaml files.
   *
   * @return a list of {@link InstrumentationEntity}
   */
  List<InstrumentationEntity> analyze() {
    List<InstrumentationPath> paths = fileManager.getInstrumentationPaths();
    List<InstrumentationEntity> entities = convertToEntities(paths);

    for (InstrumentationEntity entity : entities) {
      List<String> gradleFiles = fileManager.findBuildGradleFiles(entity.getSrcPath());
      analyzeVersions(gradleFiles, entity);

      String metadataFile = fileManager.getMetaDataFile(entity.getSrcPath());
      if (metadataFile != null) {
        entity.setMetadata(YamlHelper.metaDataParser(metadataFile));
      }
    }
    return entities;
  }

  void analyzeVersions(List<String> files, InstrumentationEntity entity) {
    Map<InstrumentationType, Set<String>> versions = new HashMap<>();
    for (String file : files) {
      String fileContents = fileManager.readFileToString(file);
      DependencyInfo results = null;

      if (file.contains("/javaagent/")) {
        results = parseGradleFile(fileContents, InstrumentationType.JAVAAGENT);
        versions
            .computeIfAbsent(InstrumentationType.JAVAAGENT, k -> new HashSet<>())
            .addAll(results.versions());
      } else if (file.contains("/library/")) {
        results = parseGradleFile(fileContents, InstrumentationType.LIBRARY);
        versions
            .computeIfAbsent(InstrumentationType.LIBRARY, k -> new HashSet<>())
            .addAll(results.versions());
      }
      if (results != null && results.minJavaVersionSupported() != null) {
        entity.setMinJavaVersion(results.minJavaVersionSupported());
      }
    }
    entity.setTargetVersions(versions);
  }
}

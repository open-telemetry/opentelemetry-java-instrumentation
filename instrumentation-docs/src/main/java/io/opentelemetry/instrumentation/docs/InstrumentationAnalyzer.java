/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs;

import static io.opentelemetry.instrumentation.docs.GradleParser.parseGradleFile;

import io.opentelemetry.instrumentation.docs.utils.FileManager;
import io.opentelemetry.instrumentation.docs.utils.InstrumentationPath;
import io.opentelemetry.instrumentation.docs.utils.YamlHelper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

class InstrumentationAnalyzer {

  private final FileManager fileSearch;

  InstrumentationAnalyzer(FileManager fileSearch) {
    this.fileSearch = fileSearch;
  }

  /**
   * Converts a list of InstrumentationPath objects into a list of InstrumentationEntity objects.
   * Each InstrumentationEntity represents a unique combination of group, namespace, and
   * instrumentation name. The types of instrumentation (e.g., library, javaagent) are aggregated
   * into a list within each entity.
   *
   * @param paths the list of InstrumentationPath objects to be converted
   * @return a list of InstrumentationEntity objects with aggregated types
   */
  public static List<InstrumentationEntity> convertToEntities(List<InstrumentationPath> paths) {
    Map<String, InstrumentationEntity> entityMap = new HashMap<>();

    for (InstrumentationPath path : paths) {
      String key = path.group() + ":" + path.namespace() + ":" + path.instrumentationName();
      if (!entityMap.containsKey(key)) {
        entityMap.put(
            key,
            new InstrumentationEntity(
                path.srcPath().replace("/javaagent", "").replace("/library", ""),
                path.instrumentationName(),
                path.namespace(),
                path.group()));
      }
    }

    return new ArrayList<>(entityMap.values());
  }

  /**
   * Analyzes the given root directory to find all instrumentation paths and then analyze them.
   * Extracts version information from each instrumentation's build.gradle file. Extracts
   * information from metadata.yaml files.
   *
   * @return a list of InstrumentationEntity objects with target versions
   */
  List<InstrumentationEntity> analyze() throws IOException {
    List<InstrumentationPath> paths = fileSearch.getInstrumentationPaths();
    List<InstrumentationEntity> entities = convertToEntities(paths);

    for (InstrumentationEntity entity : entities) {
      List<String> gradleFiles = fileSearch.findBuildGradleFiles(entity.getSrcPath());
      analyzeVersions(gradleFiles, entity);

      String metadataFile = fileSearch.getMetaDataFile(entity.getSrcPath());
      if (metadataFile != null) {
        entity.setMetadata(YamlHelper.metaDataParser(metadataFile));
      }

      String emittedTelemetryMetadata = fileSearch.getEmittedTelemetryMetadata(entity.getSrcPath());
      if (emittedTelemetryMetadata != null) {
        EmittedTelemetry telemetry = YamlHelper.emittedTelemetryParser(emittedTelemetryMetadata);
        if (telemetry != null && telemetry.getScope() != null) {
          entity.setScope(telemetry.getScope());
        }
      }
    }
    return entities;
  }

  void analyzeVersions(List<String> files, InstrumentationEntity entity) {
    Map<InstrumentationType, Set<String>> versions = new HashMap<>();
    for (String file : files) {
      String fileContents = fileSearch.readFileToString(file);

      if (file.contains("/javaagent/")) {
        Set<String> results = parseGradleFile(fileContents, InstrumentationType.JAVAAGENT);
        versions
            .computeIfAbsent(InstrumentationType.JAVAAGENT, k -> new HashSet<>())
            .addAll(results);
      } else if (file.contains("/library/")) {
        Set<String> results = parseGradleFile(fileContents, InstrumentationType.LIBRARY);
        versions.computeIfAbsent(InstrumentationType.LIBRARY, k -> new HashSet<>()).addAll(results);
      }
    }
    entity.setTargetVersions(versions);
  }
}

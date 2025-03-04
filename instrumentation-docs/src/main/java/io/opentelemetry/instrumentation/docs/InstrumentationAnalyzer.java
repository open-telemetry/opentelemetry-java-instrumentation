/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs;

import static io.opentelemetry.instrumentation.docs.GradleParser.parseMuzzleBlock;

import io.opentelemetry.instrumentation.docs.utils.FileManager;
import io.opentelemetry.instrumentation.docs.utils.InstrumentationPath;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
                path.group(),
                new ArrayList<>()));
      }
      entityMap.get(key).getTypes().add(path.type());
    }

    return new ArrayList<>(entityMap.values());
  }

  /**
   * Analyzes the given root directory to find all instrumentation paths and then analyze them. -
   * Extracts version information from each instrumentation's build.gradle file.
   *
   * @return a list of InstrumentationEntity objects with target versions
   */
  List<InstrumentationEntity> analyze() {
    List<InstrumentationPath> paths = fileSearch.getInstrumentationPaths();
    List<InstrumentationEntity> entities = convertToEntities(paths);

    for (InstrumentationEntity entity : entities) {
      List<String> gradleFiles = fileSearch.findBuildGradleFiles(entity.getSrcPath());
      analyzeVersions(gradleFiles, entity);
    }
    return entities;
  }

  void analyzeVersions(List<String> files, InstrumentationEntity entity) {
    List<String> versions = new ArrayList<>();
    for (String file : files) {
      String fileContents = fileSearch.readFileToString(file);
      versions.addAll(parseMuzzleBlock(fileContents));
    }
    entity.setTargetVersions(versions);
  }
}

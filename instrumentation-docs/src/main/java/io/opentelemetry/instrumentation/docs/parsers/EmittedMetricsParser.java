/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.parsers;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.opentelemetry.instrumentation.docs.internal.EmittedMetrics;
import io.opentelemetry.instrumentation.docs.utils.FileManager;
import io.opentelemetry.instrumentation.docs.utils.YamlHelper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * This class is responsible for parsing metric-* files from the `.telemetry` directory of an
 * instrumentation module and converting them into the {@link EmittedMetrics} format.
 */
public class EmittedMetricsParser {
  private static final Logger logger = Logger.getLogger(EmittedMetricsParser.class.getName());

  /**
   * Looks for metric files in the .telemetry directory, and combines them into a map where the key
   * represents the "when", and the value is a list of metrics emitted under that condition.
   *
   * @param instrumentationDirectory the directory to traverse
   * @return contents of aggregated files
   */
  public static Map<String, EmittedMetrics> getMetricsFromFiles(
      String rootDir, String instrumentationDirectory) {
    Path telemetryDir = Paths.get(rootDir + "/" + instrumentationDirectory, ".telemetry");

    Map<String, List<EmittedMetrics.MetricsByScope>> metricsByWhen =
        parseAllMetricFiles(telemetryDir);

    return aggregateMetricsByScope(metricsByWhen);
  }

  /**
   * Parses all metric files in the given .telemetry directory and returns a map where the key is
   * the 'when' condition and the value is a list of metrics grouped by scope.
   *
   * @param telemetryDir the path to the .telemetry directory
   * @return a map of 'when' to list of metrics by scope
   */
  private static Map<String, List<EmittedMetrics.MetricsByScope>> parseAllMetricFiles(
      Path telemetryDir) {
    Map<String, List<EmittedMetrics.MetricsByScope>> metricsByWhen = new HashMap<>();
    if (Files.exists(telemetryDir) && Files.isDirectory(telemetryDir)) {
      try (Stream<Path> files = Files.list(telemetryDir)) {
        files
            .filter(path -> path.getFileName().toString().startsWith("metrics-"))
            .forEach(
                path -> {
                  String content = FileManager.readFileToString(path.toString());
                  if (content != null) {
                    String when = content.substring(0, content.indexOf('\n'));
                    String whenKey = when.replace("when: ", "");

                    int metricsIndex = content.indexOf("metrics_by_scope:");
                    if (metricsIndex != -1) {
                      String yaml = "when: " + whenKey + "\n" + content.substring(metricsIndex);
                      EmittedMetrics parsed;
                      try {
                        parsed = YamlHelper.emittedMetricsParser(yaml);
                      } catch (Exception e) {
                        logger.severe(
                            "Error parsing metrics file (" + path + "): " + e.getMessage());
                        return;
                      }
                      if (parsed.getMetricsByScope() != null) {
                        metricsByWhen.putIfAbsent(whenKey, new ArrayList<>());
                        metricsByWhen.get(whenKey).addAll(parsed.getMetricsByScope());
                      }
                    }
                  }
                });
      } catch (IOException e) {
        logger.severe("Error reading metrics files: " + e.getMessage());
      }
    }
    return metricsByWhen;
  }

  /**
   * Aggregates metrics under the same scope for each 'when' condition, deduplicating metrics by
   * name.
   *
   * @param metricsByWhen map of 'when' to list of metrics by scope
   * @return a map of 'when' to aggregated EmittedMetrics
   */
  private static Map<String, EmittedMetrics> aggregateMetricsByScope(
      Map<String, List<EmittedMetrics.MetricsByScope>> metricsByWhen) {
    Map<String, EmittedMetrics> result = new HashMap<>();
    for (Map.Entry<String, List<EmittedMetrics.MetricsByScope>> entry : metricsByWhen.entrySet()) {
      String when = entry.getKey();
      List<EmittedMetrics.MetricsByScope> allScopes = entry.getValue();
      Map<String, Map<String, EmittedMetrics.Metric>> metricsByScopeName = new HashMap<>();

      for (EmittedMetrics.MetricsByScope scopeEntry : allScopes) {
        String scope = scopeEntry.getScope();
        metricsByScopeName.putIfAbsent(scope, new HashMap<>());
        Map<String, EmittedMetrics.Metric> metricMap = metricsByScopeName.get(scope);

        for (EmittedMetrics.Metric metric : scopeEntry.getMetrics()) {
          metricMap.put(metric.getName(), metric); // deduplicate by name
        }
      }

      List<EmittedMetrics.MetricsByScope> mergedScopes = new ArrayList<>();
      for (Map.Entry<String, Map<String, EmittedMetrics.Metric>> scopeEntry :
          metricsByScopeName.entrySet()) {
        mergedScopes.add(
            new EmittedMetrics.MetricsByScope(
                scopeEntry.getKey(), new ArrayList<>(scopeEntry.getValue().values())));
      }
      result.put(when, new EmittedMetrics(when, mergedScopes));
    }
    return result;
  }

  /**
   * Takes in a raw string representation of the aggregated EmittedMetrics yaml map, separated by
   * the {@code when}, indicating the conditions under which the metrics are emitted. Deduplicates
   * the metrics by name and then returns a new map of EmittedMetrics objects.
   *
   * @param input raw string representation of EmittedMetrics yaml
   * @return map where the key is the {@code when} condition and the value is the corresponding
   *     EmittedMetrics
   * @throws JsonProcessingException if parsing fails
   */
  // visible for testing
  public static Map<String, EmittedMetrics> parseMetrics(Map<String, StringBuilder> input)
      throws JsonProcessingException {
    Map<String, EmittedMetrics> metricsMap = new HashMap<>();
    for (Map.Entry<String, StringBuilder> entry : input.entrySet()) {
      String when = entry.getKey();
      StringBuilder content = entry.getValue();

      EmittedMetrics metrics = YamlHelper.emittedMetricsParser(content.toString());
      if (metrics.getMetricsByScope() == null) {
        continue;
      }

      List<EmittedMetrics.MetricsByScope> deduplicatedScopes = new ArrayList<>();
      for (EmittedMetrics.MetricsByScope scopeEntry : metrics.getMetricsByScope()) {
        String scope = scopeEntry.getScope();
        Map<String, EmittedMetrics.Metric> dedupedMetrics = new HashMap<>();
        for (EmittedMetrics.Metric metric : scopeEntry.getMetrics()) {
          dedupedMetrics.put(metric.getName(), metric);
        }
        deduplicatedScopes.add(
            new EmittedMetrics.MetricsByScope(scope, new ArrayList<>(dedupedMetrics.values())));
      }
      metricsMap.put(when, new EmittedMetrics(when, deduplicatedScopes));
    }
    return metricsMap;
  }

  private EmittedMetricsParser() {}
}

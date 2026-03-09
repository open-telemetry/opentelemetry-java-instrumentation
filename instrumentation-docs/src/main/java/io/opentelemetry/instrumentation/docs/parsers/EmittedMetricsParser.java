/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.parsers;

import static io.opentelemetry.instrumentation.docs.parsers.TelemetryParser.normalizeWhenCondition;

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
import javax.annotation.Nullable;

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
                    String whenKey = normalizeWhenCondition(content);

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
      EmittedMetrics emittedMetrics = new EmittedMetrics(when, mergedScopes);
      enrichMetricsWithInstrumentType(emittedMetrics);
      result.put(when, emittedMetrics);
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
      EmittedMetrics emittedMetrics = new EmittedMetrics(when, deduplicatedScopes);
      enrichMetricsWithInstrumentType(emittedMetrics);
      metricsMap.put(when, emittedMetrics);
    }
    return metricsMap;
  }

  /**
   * Infers the InstrumentType from the MetricDataType string and isMonotonic flag, since MetricData
   * only contains the aggregated type (e.g., LONG_SUM, DOUBLE_GAUGE)
   *
   * @param metricDataType the MetricDataType string (e.g., "LONG_SUM", "DOUBLE_GAUGE")
   * @param isMonotonic whether the metric is monotonic (for SUM types), null if not applicable
   * @return the inferred InstrumentType string
   */
  private static String inferInstrumentType(String metricDataType, @Nullable Boolean isMonotonic) {
    if (metricDataType == null || metricDataType.isEmpty()) {
      return "UNKNOWN";
    }

    return switch (metricDataType) {
      case "HISTOGRAM", "EXPONENTIAL_HISTOGRAM", "SUMMARY" -> "histogram";
      case "LONG_GAUGE", "DOUBLE_GAUGE" -> "gauge";
      case "LONG_SUM", "DOUBLE_SUM" -> {
        // Use isMonotonic flag to distinguish between COUNTER and UP_DOWN_COUNTER
        if (isMonotonic != null && isMonotonic) {
          yield "counter";
        } else if (isMonotonic != null) {
          yield "updowncounter";
        } else {
          // Unknown, default to counter
          yield "counter";
        }
      }
      default -> "UNKNOWN";
    };
  }

  /**
   * Populates the instrumentType field for each metric based on its MetricDataType and isMonotonic
   * flag. This is called after parsing the YAML to enrich the metric data with inferred instrument
   * types.
   *
   * @param metrics the EmittedMetrics object to enrich
   */
  private static void enrichMetricsWithInstrumentType(EmittedMetrics metrics) {
    if (metrics.getMetricsByScope() == null) {
      return;
    }

    for (EmittedMetrics.MetricsByScope scope : metrics.getMetricsByScope()) {
      if (scope.getMetrics() == null) {
        continue;
      }

      for (EmittedMetrics.Metric metric : scope.getMetrics()) {
        if (metric.getInstrumentType() == null || metric.getInstrumentType().isEmpty()) {
          String inferredType = inferInstrumentType(metric.getType(), metric.getIsMonotonic());
          metric.setInstrumentType(inferredType);
        }
      }
    }
  }

  private EmittedMetricsParser() {}
}

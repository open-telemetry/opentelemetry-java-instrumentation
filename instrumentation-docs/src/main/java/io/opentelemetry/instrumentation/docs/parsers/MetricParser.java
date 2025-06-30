/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.parsers;

import io.opentelemetry.instrumentation.docs.internal.EmittedMetrics;
import io.opentelemetry.instrumentation.docs.internal.InstrumentationModule;
import io.opentelemetry.instrumentation.docs.internal.TelemetryAttribute;
import io.opentelemetry.instrumentation.docs.utils.FileManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This class is responsible for parsing metric files from the `.telemetry` directory of an
 * instrumentation module and filtering them by scope.
 */
public class MetricParser {

  /**
   * Retrieves metrics for a given instrumentation module, filtered by scope.
   *
   * @param module the instrumentation module
   * @param fileManager the file manager to use for file operations
   * @return a map where the key is the 'when' condition and the value is a list of metrics
   */
  public static Map<String, List<EmittedMetrics.Metric>> getMetrics(
      InstrumentationModule module, FileManager fileManager) {
    Map<String, EmittedMetrics> metrics =
        EmittedMetricsParser.getMetricsFromFiles(fileManager.rootDir(), module.getSrcPath());

    if (metrics.isEmpty()) {
      return new HashMap<>();
    }

    String scopeName = module.getScopeInfo().getName();
    return filterMetricsByScope(metrics, scopeName);
  }

  /**
   * Filters metrics by scope and aggregates attributes for each metric kind.
   *
   * @param metricsByScope the map of metrics by scope
   * @param scopeName the name of the scope to filter metrics for
   * @return a map of filtered metrics by 'when'
   */
  private static Map<String, List<EmittedMetrics.Metric>> filterMetricsByScope(
      Map<String, EmittedMetrics> metricsByScope, String scopeName) {

    Map<String, Map<String, MetricAggregator.AggregatedMetricInfo>> aggregatedMetrics =
        new HashMap<>();

    for (Map.Entry<String, EmittedMetrics> entry : metricsByScope.entrySet()) {
      if (!hasValidMetrics(entry.getValue())) {
        continue;
      }

      String when = entry.getValue().getWhen();
      Map<String, Map<String, MetricAggregator.AggregatedMetricInfo>> result =
          MetricAggregator.aggregateMetrics(when, entry.getValue(), scopeName);

      // Merge result into aggregatedMetrics
      for (Map.Entry<String, Map<String, MetricAggregator.AggregatedMetricInfo>> e :
          result.entrySet()) {
        String whenKey = e.getKey();
        Map<String, MetricAggregator.AggregatedMetricInfo> metricMap =
            aggregatedMetrics.computeIfAbsent(whenKey, k -> new HashMap<>());

        for (Map.Entry<String, MetricAggregator.AggregatedMetricInfo> metricEntry :
            e.getValue().entrySet()) {
          String metricName = metricEntry.getKey();
          MetricAggregator.AggregatedMetricInfo newInfo = metricEntry.getValue();
          MetricAggregator.AggregatedMetricInfo existingInfo = metricMap.get(metricName);
          if (existingInfo == null) {
            metricMap.put(metricName, newInfo);
          } else {
            existingInfo.attributes.addAll(newInfo.attributes);
          }
        }
      }
    }

    return MetricAggregator.buildFilteredMetrics(aggregatedMetrics);
  }

  private static boolean hasValidMetrics(EmittedMetrics metrics) {
    return metrics != null && metrics.getMetricsByScope() != null;
  }

  /** Helper class to aggregate metrics by scope and name. */
  static class MetricAggregator {
    /**
     * Aggregates metrics for a given 'when' condition, metrics object, and target scope name.
     *
     * @param when the 'when' condition
     * @param metrics the EmittedMetrics object
     * @param targetScopeName the scope name to filter by
     * @return a map of aggregated metrics by 'when' and metric name
     */
    public static Map<String, Map<String, AggregatedMetricInfo>> aggregateMetrics(
        String when, EmittedMetrics metrics, String targetScopeName) {
      Map<String, Map<String, AggregatedMetricInfo>> aggregatedMetrics = new HashMap<>();
      Map<String, AggregatedMetricInfo> metricKindMap =
          aggregatedMetrics.computeIfAbsent(when, k -> new HashMap<>());

      for (EmittedMetrics.MetricsByScope metricsByScope : metrics.getMetricsByScope()) {
        if (metricsByScope.getScope().equals(targetScopeName)) {
          for (EmittedMetrics.Metric metric : metricsByScope.getMetrics()) {
            AggregatedMetricInfo aggInfo =
                metricKindMap.computeIfAbsent(
                    metric.getName(),
                    k ->
                        new AggregatedMetricInfo(
                            metric.getName(),
                            metric.getDescription(),
                            metric.getType(),
                            metric.getUnit()));
            if (metric.getAttributes() != null) {
              for (TelemetryAttribute attr : metric.getAttributes()) {
                aggInfo.attributes.add(new TelemetryAttribute(attr.getName(), attr.getType()));
              }
            }
          }
        }
      }
      return aggregatedMetrics;
    }

    /**
     * Builds a filtered metrics map from aggregated metrics.
     *
     * @param aggregatedMetrics the aggregated metrics map
     * @return a map where the key is the 'when' condition and the value is a list of metrics
     */
    public static Map<String, List<EmittedMetrics.Metric>> buildFilteredMetrics(
        Map<String, Map<String, AggregatedMetricInfo>> aggregatedMetrics) {
      Map<String, List<EmittedMetrics.Metric>> result = new HashMap<>();
      for (Map.Entry<String, Map<String, AggregatedMetricInfo>> entry :
          aggregatedMetrics.entrySet()) {
        String when = entry.getKey();
        List<EmittedMetrics.Metric> metrics = result.computeIfAbsent(when, k -> new ArrayList<>());
        for (AggregatedMetricInfo aggInfo : entry.getValue().values()) {
          metrics.add(
              new EmittedMetrics.Metric(
                  aggInfo.name,
                  aggInfo.description,
                  aggInfo.type,
                  aggInfo.unit,
                  new ArrayList<>(aggInfo.attributes)));
        }
      }
      return result;
    }

    /** Data class to hold aggregated metric information. */
    static class AggregatedMetricInfo {
      final String name;
      final String description;
      final String type;
      final String unit;
      final Set<TelemetryAttribute> attributes = new HashSet<>();

      AggregatedMetricInfo(String name, String description, String type, String unit) {
        this.name = name;
        this.description = description;
        this.type = type;
        this.unit = unit;
      }
    }

    private MetricAggregator() {}
  }

  private MetricParser() {}
}

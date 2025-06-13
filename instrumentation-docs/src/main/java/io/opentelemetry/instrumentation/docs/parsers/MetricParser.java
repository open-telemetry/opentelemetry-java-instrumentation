/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.parsers;

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

public class MetricParser {
  private static final Logger logger = Logger.getLogger(MetricParser.class.getName());

  /**
   * Looks for metric files in the .telemetry directory, and combines them into a single list of
   * metrics.
   *
   * @param instrumentationDirectory the directory to traverse
   * @return contents of aggregated files
   */
  public static Map<String, EmittedMetrics> getMetricsFromFiles(
      String rootDir, String instrumentationDirectory) {
    Map<String, StringBuilder> metricsByWhen = new HashMap<>();
    Path telemetryDir = Paths.get(rootDir + "/" + instrumentationDirectory, ".telemetry");

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

                    metricsByWhen.putIfAbsent(whenKey, new StringBuilder("metrics:\n"));

                    // Skip the metric label ("metrics:") so we can aggregate into one list
                    int metricsIndex = content.indexOf("metrics:\n");
                    if (metricsIndex != -1) {
                      String contentAfterMetrics =
                          content.substring(metricsIndex + "metrics:\n".length());
                      metricsByWhen.get(whenKey).append(contentAfterMetrics);
                    }
                  }
                });
      } catch (IOException e) {
        logger.severe("Error reading metrics files: " + e.getMessage());
      }
    }

    return parseMetrics(metricsByWhen);
  }

  /**
   * Takes in a raw string representation of the aggregated EmittedMetrics yaml map, separated by
   * the `when`, indicating the conditions under which the metrics are emitted. deduplicates the
   * metrics by name and then returns a new map EmittedMetrics objects.
   *
   * @param input raw string representation of EmittedMetrics yaml
   * @return {@code Map<String, EmittedMetrics>} where the key is the `when` condition
   */
  // visible for testing
  public static Map<String, EmittedMetrics> parseMetrics(Map<String, StringBuilder> input) {
    Map<String, EmittedMetrics> metricsMap = new HashMap<>();
    for (Map.Entry<String, StringBuilder> entry : input.entrySet()) {
      String when = entry.getKey();
      StringBuilder content = entry.getValue();

      EmittedMetrics metrics = YamlHelper.emittedMetricsParser(content.toString());
      if (metrics.getMetrics() == null) {
        continue;
      }

      Map<String, EmittedMetrics.Metric> deduplicatedMetrics = new HashMap<>();
      for (EmittedMetrics.Metric metric : metrics.getMetrics()) {
        deduplicatedMetrics.put(metric.getName(), metric);
      }

      List<EmittedMetrics.Metric> uniqueMetrics = new ArrayList<>(deduplicatedMetrics.values());
      metricsMap.put(when, new EmittedMetrics(when, uniqueMetrics));
    }
    return metricsMap;
  }

  private MetricParser() {}
}

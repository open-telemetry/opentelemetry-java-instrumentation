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
import java.util.Collections;
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
  public static EmittedMetrics getMetricsFromFiles(
      String rootDir, String instrumentationDirectory) {
    StringBuilder metricsContent = new StringBuilder("metrics:\n");
    Path telemetryDir = Paths.get(rootDir + "/" + instrumentationDirectory, ".telemetry");

    if (Files.exists(telemetryDir) && Files.isDirectory(telemetryDir)) {
      try (Stream<Path> files = Files.list(telemetryDir)) {
        files
            .filter(path -> path.getFileName().toString().startsWith("metrics-"))
            .forEach(
                path -> {
                  String content = FileManager.readFileToString(path.toString());
                  if (content != null) {
                    // Skip the first line of yaml ("metrics:") so we can aggregate into one list
                    int firstNewline = content.indexOf('\n');
                    if (firstNewline != -1) {
                      String contentWithoutFirstLine = content.substring(firstNewline + 1);
                      metricsContent.append(contentWithoutFirstLine);
                    }
                  }
                });
      } catch (IOException e) {
        logger.severe("Error reading metrics files: " + e.getMessage());
      }
    }

    return parseMetrics(metricsContent.toString());
  }

  /**
   * Takes in a raw string representation of the aggregated EmittedMetrics yaml, deduplicates the
   * metrics by name and then returns a new EmittedMetrics object.
   *
   * @param input raw string representation of EmittedMetrics yaml
   * @return EmittedMetrics
   */
  // visible for testing
  public static EmittedMetrics parseMetrics(String input) {
    EmittedMetrics metrics = YamlHelper.emittedMetricsParser(input);
    if (metrics.getMetrics() == null) {
      return new EmittedMetrics(Collections.emptyList());
    }

    // deduplicate metrics by name
    Map<String, EmittedMetrics.Metric> deduplicatedMetrics = new HashMap<>();
    for (EmittedMetrics.Metric metric : metrics.getMetrics()) {
      deduplicatedMetrics.put(metric.getName(), metric);
    }

    List<EmittedMetrics.Metric> uniqueMetrics = new ArrayList<>(deduplicatedMetrics.values());
    return new EmittedMetrics(uniqueMetrics);
  }

  private MetricParser() {}
}

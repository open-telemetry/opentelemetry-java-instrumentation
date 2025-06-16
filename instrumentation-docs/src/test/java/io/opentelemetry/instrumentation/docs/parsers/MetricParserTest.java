/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.parsers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

import io.opentelemetry.instrumentation.docs.internal.EmittedMetrics;
import io.opentelemetry.instrumentation.docs.utils.FileManager;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

@SuppressWarnings("NullAway")
class MetricParserTest {

  @Test
  void parseMetricsDeduplicatesMetricsByName() {
    String input =
        """
        metrics:
          - name: metric1
            type: counter
          - name: metric1
            type: counter
          - name: metric2
            type: gauge
        """;

    Map<String, StringBuilder> metricMap = new HashMap<>();
    metricMap.put("default", new StringBuilder(input));

    Map<String, EmittedMetrics> result = MetricParser.parseMetrics(metricMap);
    List<String> metricNames =
        result.get("default").getMetrics().stream()
            .map(EmittedMetrics.Metric::getName)
            .sorted()
            .toList();

    assertThat(metricNames).hasSize(2);
    assertThat(metricNames).containsExactly("metric1", "metric2");
  }

  @Test
  void parseMetricsHandlesEmptyInput() {
    String input = "metrics:\n";
    Map<String, StringBuilder> metricMap = new HashMap<>();
    metricMap.put("default", new StringBuilder(input));

    Map<String, EmittedMetrics> result = MetricParser.parseMetrics(metricMap);
    assertThat(result).isEmpty();
  }

  @Test
  void getMetricsFromFilesCombinesFilesCorrectly(@TempDir Path tempDir) throws IOException {
    Path telemetryDir = Files.createDirectories(tempDir.resolve(".telemetry"));

    String file1Content = "when: default\n  metrics:\n  - name: metric1\n    type: counter\n";
    String file2Content = "when: default\n  metrics:\n  - name: metric2\n    type: gauge\n";

    Files.writeString(telemetryDir.resolve("metrics-1.yaml"), file1Content);
    Files.writeString(telemetryDir.resolve("metrics-2.yaml"), file2Content);

    // Create a non-metrics file that should be ignored
    Files.writeString(telemetryDir.resolve("other-file.yaml"), "some content");

    try (MockedStatic<FileManager> fileManagerMock = mockStatic(FileManager.class)) {
      fileManagerMock
          .when(
              () -> FileManager.readFileToString(telemetryDir.resolve("metrics-1.yaml").toString()))
          .thenReturn(file1Content);
      fileManagerMock
          .when(
              () -> FileManager.readFileToString(telemetryDir.resolve("metrics-2.yaml").toString()))
          .thenReturn(file2Content);

      Map<String, EmittedMetrics> result = MetricParser.getMetricsFromFiles(tempDir.toString(), "");

      EmittedMetrics metrics = result.get("default");

      assertThat(metrics.getMetrics()).hasSize(2);
      List<String> metricNames =
          metrics.getMetrics().stream().map(EmittedMetrics.Metric::getName).sorted().toList();
      assertThat(metricNames).containsExactly("metric1", "metric2");
    }
  }

  @Test
  void getMetricsFromFilesHandlesNonexistentDirectory() {
    Map<String, EmittedMetrics> result = MetricParser.getMetricsFromFiles("/nonexistent", "path");
    assertThat(result).isEmpty();
  }
}

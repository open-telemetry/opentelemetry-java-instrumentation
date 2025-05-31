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
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

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

    EmittedMetrics result = MetricParser.parseMetrics(input);
    List<String> metricNames =
        result.getMetrics().stream().map(EmittedMetrics.Metric::getName).sorted().toList();

    assertThat(metricNames).hasSize(2);
    assertThat(metricNames).containsExactly("metric1", "metric2");
  }

  @Test
  void parseMetricsHandlesEmptyInput() {
    String input = "metrics:\n";
    EmittedMetrics result = MetricParser.parseMetrics(input);
    assertThat(result.getMetrics()).isEmpty();
  }

  @Test
  void getMetricsFromFilesCombinesFilesCorrectly(@TempDir Path tempDir) throws IOException {
    Path telemetryDir = Files.createDirectories(tempDir.resolve(".telemetry"));

    String file1Content = "metrics:\n  - name: metric1\n    type: counter\n";
    String file2Content = "metrics:\n  - name: metric2\n    type: gauge\n";

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

      EmittedMetrics result = MetricParser.getMetricsFromFiles(tempDir.toString(), "");

      assertThat(result.getMetrics()).hasSize(2);
      List<String> metricNames =
          result.getMetrics().stream().map(EmittedMetrics.Metric::getName).sorted().toList();
      assertThat(metricNames).containsExactly("metric1", "metric2");
    }
  }

  @Test
  void getMetricsFromFilesHandlesNonexistentDirectory() {
    EmittedMetrics result = MetricParser.getMetricsFromFiles("/nonexistent", "path");
    assertThat(result.getMetrics()).isEmpty();
  }
}

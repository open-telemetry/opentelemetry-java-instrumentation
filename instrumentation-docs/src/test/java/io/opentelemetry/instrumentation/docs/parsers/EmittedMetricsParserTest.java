/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.parsers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.opentelemetry.instrumentation.docs.internal.EmittedMetrics;
import io.opentelemetry.instrumentation.docs.internal.TelemetryAttribute;
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

class EmittedMetricsParserTest {

  @Test
  void parseMetricsDeduplicatesMetricsByName() throws JsonProcessingException {
    String input =
        """
        metrics_by_scope:
          - scope: io.opentelemetry.alibaba-druid-1.0
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

    Map<String, EmittedMetrics> result = EmittedMetricsParser.parseMetrics(metricMap);
    List<String> metricNames =
        result.get("default").getMetricsByScope().get(0).getMetrics().stream()
            .map(EmittedMetrics.Metric::getName)
            .sorted()
            .toList();

    assertThat(metricNames).hasSize(2);
    assertThat(metricNames).containsExactly("metric1", "metric2");
  }

  @Test
  void parseMetricsHandlesEmptyInput() throws JsonProcessingException {
    String input = "metrics_by_scope:\n";
    Map<String, StringBuilder> metricMap = new HashMap<>();
    metricMap.put("default", new StringBuilder(input));

    Map<String, EmittedMetrics> result = EmittedMetricsParser.parseMetrics(metricMap);
    assertThat(result).isEmpty();
  }

  @Test
  void getMetricsFromFilesCombinesFilesCorrectly(@TempDir Path tempDir) throws IOException {
    Path telemetryDir = Files.createDirectories(tempDir.resolve(".telemetry"));

    String file1Content =
        """
    when: default
    metrics_by_scope:
      - scope: io.opentelemetry.MetricParserTest
        metrics:
          - name: metric1
            type: counter
    """;

    String file2Content =
        """
    when: default
    metrics_by_scope:
      - scope: io.opentelemetry.MetricParserTest
        metrics:
          - name: metric2
            type: gauge
    """;

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

      Map<String, EmittedMetrics> result =
          EmittedMetricsParser.getMetricsFromFiles(tempDir.toString(), "");

      EmittedMetrics.MetricsByScope metrics =
          result.get("default").getMetricsByScope().stream()
              .filter(scope -> scope.getScope().equals("io.opentelemetry.MetricParserTest"))
              .findFirst()
              .orElseThrow();

      assertThat(metrics.getMetrics()).hasSize(2);
      List<String> metricNames =
          metrics.getMetrics().stream().map(EmittedMetrics.Metric::getName).sorted().toList();
      assertThat(metricNames).containsExactly("metric1", "metric2");
    }
  }

  @Test
  void getMetricsFromFilesUnionsAttributesForSameMetric(@TempDir Path tempDir) throws IOException {
    Path telemetryDir = Files.createDirectories(tempDir.resolve(".telemetry"));

    String withState =
        """
    when: Java17
    metrics_by_scope:
      - scope: io.opentelemetry.runtime-telemetry
        metrics:
          - name: jvm.thread.count
            type: LONG_SUM
            attributes:
              - name: jvm.thread.daemon
                type: BOOLEAN
              - name: jvm.thread.state
                type: STRING
    """;

    String withoutState =
        """
    when: Java17
    metrics_by_scope:
      - scope: io.opentelemetry.runtime-telemetry
        metrics:
          - name: jvm.thread.count
            type: LONG_SUM
            attributes:
              - name: jvm.thread.daemon
                type: BOOLEAN
    """;

    Files.writeString(telemetryDir.resolve("metrics-1.yaml"), withState);
    Files.writeString(telemetryDir.resolve("metrics-2.yaml"), withoutState);

    try (MockedStatic<FileManager> fileManagerMock = mockStatic(FileManager.class)) {
      fileManagerMock
          .when(
              () -> FileManager.readFileToString(telemetryDir.resolve("metrics-1.yaml").toString()))
          .thenReturn(withState);
      fileManagerMock
          .when(
              () -> FileManager.readFileToString(telemetryDir.resolve("metrics-2.yaml").toString()))
          .thenReturn(withoutState);

      Map<String, EmittedMetrics> result =
          EmittedMetricsParser.getMetricsFromFiles(tempDir.toString(), "");

      EmittedMetrics.MetricsByScope metrics =
          result.get("Java17").getMetricsByScope().stream()
              .filter(scope -> scope.getScope().equals("io.opentelemetry.runtime-telemetry"))
              .findFirst()
              .orElseThrow();

      assertThat(metrics.getMetrics()).hasSize(1);
      List<String> attributeNames =
          metrics.getMetrics().get(0).getAttributes().stream()
              .map(TelemetryAttribute::getName)
              .sorted()
              .toList();
      assertThat(attributeNames).containsExactly("jvm.thread.daemon", "jvm.thread.state");
    }
  }

  @Test
  void getMetricsFromFilesHandlesNonexistentDirectory() throws JsonProcessingException {
    Map<String, EmittedMetrics> result =
        EmittedMetricsParser.getMetricsFromFiles("/nonexistent", "path");
    assertThat(result).isEmpty();
  }
}

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.testing.internal.MetaDataCollector;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableHistogramData;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableHistogramPointData;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableMetricData;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.testing.internal.jackson.databind.JsonNode;
import io.opentelemetry.testing.internal.jackson.dataformat.yaml.YAMLMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MetricMetadataTest {
  private static final String NAME = "messaging.client.operation.duration";
  private static final InstrumentationScopeInfo SCOPE =
      InstrumentationScopeInfo.builder("io.opentelemetry.pulsar-2.8").setVersion("1.0").build();
  private static final Attributes SEND =
      Attributes.builder().put("messaging.operation.name", "send").put("server.port", 6650).build();
  private static final Attributes RECEIVE =
      Attributes.builder()
          .put("messaging.operation.name", "receive")
          .put("messaging.destination.subscription.name", "test_sub")
          .build();

  @TempDir Path tempDir;
  private String previousCollectMetadata;

  @BeforeEach
  void enableCollection() {
    previousCollectMetadata = System.getProperty("collectMetadata");
    System.setProperty("collectMetadata", "true");
  }

  @AfterEach
  void restoreCollection() {
    if (previousCollectMetadata == null) {
      System.clearProperty("collectMetadata");
    } else {
      System.setProperty("collectMetadata", previousCollectMetadata);
    }
  }

  @ParameterizedTest
  @MethodSource("snapshots")
  void accumulatesAllObservedAttributes(List<List<Attributes>> snapshots) throws IOException {
    InstrumentationTestRunner runner =
        mock(
            InstrumentationTestRunner.class,
            withSettings().useConstructor(OpenTelemetry.noop()).defaultAnswer(CALLS_REAL_METHODS));
    for (List<Attributes> points : snapshots) {
      when(runner.getExportedMetrics()).thenReturn(singletonList(metric(points)));
      runner.waitAndAssertMetrics(SCOPE.getName(), NAME, metrics -> metrics.hasSize(1));
    }

    Path module = tempDir.resolve("instrumentation/pulsar/pulsar-2.8");
    MetaDataCollector.writeTelemetryToFiles(
        module.resolve("javaagent").toString(),
        runner.metricsByScope,
        runner.tracesByScope,
        runner.eventsByScope,
        runner.instrumentationScopes);
    List<Path> metricFiles;
    try (Stream<Path> files = Files.list(module.resolve(".telemetry"))) {
      metricFiles =
          files
              .filter(path -> path.getFileName().toString().startsWith("metrics-"))
              .collect(toList());
    }
    assertThat(metricFiles).hasSize(1);
    YAMLMapper yaml = new YAMLMapper();
    JsonNode output = yaml.readTree(metricFiles.get(0).toFile());
    assertThat(output.get("metrics_by_scope").get(0).get("scope").asText())
        .isEqualTo(SCOPE.getName());
    assertThat(output.get("metrics_by_scope").get(0).get("metrics").get(0))
        .isEqualTo(
            yaml.readTree(
                String.join(
                    "\n",
                    "name: messaging.client.operation.duration",
                    "description: Duration of messaging operation.",
                    "type: HISTOGRAM",
                    "is_monotonic: null",
                    "unit: s",
                    "attributes:",
                    "- name: messaging.destination.subscription.name",
                    "  type: STRING",
                    "- name: messaging.operation.name",
                    "  type: STRING",
                    "- name: server.port",
                    "  type: LONG")));
  }

  private static Stream<Arguments> snapshots() {
    return Stream.of(
        argumentSet("send point first", singletonList(asList(SEND, RECEIVE))),
        argumentSet("receive point first", singletonList(asList(RECEIVE, SEND))),
        argumentSet("send snapshot first", asList(singletonList(SEND), singletonList(RECEIVE))),
        argumentSet("receive snapshot first", asList(singletonList(RECEIVE), singletonList(SEND))),
        argumentSet(
            "initial empty snapshot",
            asList(emptyList(), singletonList(SEND), singletonList(RECEIVE))),
        argumentSet(
            "duplicates and trailing empty snapshot",
            asList(asList(SEND, RECEIVE), singletonList(SEND), emptyList())));
  }

  private static MetricData metric(List<Attributes> points) {
    return ImmutableMetricData.createDoubleHistogram(
        Resource.empty(),
        SCOPE,
        NAME,
        "Duration of messaging operation.",
        "s",
        ImmutableHistogramData.create(
            AggregationTemporality.CUMULATIVE,
            points.stream()
                .map(
                    attributes ->
                        ImmutableHistogramPointData.create(
                            0,
                            1,
                            attributes,
                            1,
                            false,
                            0,
                            false,
                            0,
                            emptyList(),
                            singletonList(1L)))
                .collect(toList())));
  }
}

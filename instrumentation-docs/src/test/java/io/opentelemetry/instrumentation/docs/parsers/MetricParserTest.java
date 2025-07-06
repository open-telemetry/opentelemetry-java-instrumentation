/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.parsers;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.docs.internal.EmittedMetrics;
import io.opentelemetry.instrumentation.docs.internal.TelemetryAttribute;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NullAway")
class MetricParserTest {
  @Test
  void testFiltersMetricsByScope() {
    String targetScopeName = "my-instrumentation-scope";

    EmittedMetrics.Metric metric1 = createMetric("my.metric1", "desc1", "attr1");
    EmittedMetrics.Metric otherMetric = createMetric("other.metric", "desc2", "other.attr");

    EmittedMetrics.MetricsByScope targetMetricsByScope =
        new EmittedMetrics.MetricsByScope(targetScopeName, List.of(metric1));

    EmittedMetrics.MetricsByScope otherMetricsByScope =
        new EmittedMetrics.MetricsByScope("other-scope", List.of(otherMetric));

    EmittedMetrics emittedMetrics =
        new EmittedMetrics("default", List.of(targetMetricsByScope, otherMetricsByScope));

    Map<String, Map<String, MetricParser.MetricAggregator.AggregatedMetricInfo>> metrics =
        MetricParser.MetricAggregator.aggregateMetrics("default", emittedMetrics, targetScopeName);

    Map<String, List<EmittedMetrics.Metric>> result =
        MetricParser.MetricAggregator.buildFilteredMetrics(metrics);

    assertThat(result.get("default")).hasSize(1);
    assertThat(result.get("default").get(0).getName()).isEqualTo("my.metric1");
  }

  @Test
  void testAggregatesAndDeduplicatesAttributes() {
    String targetScopeName = "my-instrumentation-scope";

    EmittedMetrics.Metric metric1 =
        new EmittedMetrics.Metric(
            "my.metric1",
            "desc1",
            "gauge",
            "unit",
            List.of(
                new TelemetryAttribute("attr1", "STRING"),
                new TelemetryAttribute("attr2", "STRING")));

    EmittedMetrics.Metric metric1Dup =
        new EmittedMetrics.Metric(
            "my.metric1",
            "desc1",
            "gauge",
            "unit",
            List.of(
                new TelemetryAttribute("attr1", "STRING"),
                new TelemetryAttribute("attr3", "STRING")));

    EmittedMetrics.MetricsByScope targetMetricsByScope =
        new EmittedMetrics.MetricsByScope(targetScopeName, List.of(metric1, metric1Dup));

    EmittedMetrics emittedMetrics = new EmittedMetrics("default", List.of(targetMetricsByScope));

    Map<String, Map<String, MetricParser.MetricAggregator.AggregatedMetricInfo>> metrics =
        MetricParser.MetricAggregator.aggregateMetrics("default", emittedMetrics, targetScopeName);

    Map<String, List<EmittedMetrics.Metric>> result =
        MetricParser.MetricAggregator.buildFilteredMetrics(metrics);
    List<TelemetryAttribute> attrs = result.get("default").get(0).getAttributes();

    assertThat(attrs).hasSize(3);
    assertThat(attrs.stream().map(TelemetryAttribute::getName))
        .containsExactlyInAnyOrder("attr1", "attr2", "attr3");
  }

  @Test
  void testPreservesMetricMetadata() {
    String targetScopeName = "my-instrumentation-scope";

    EmittedMetrics.Metric metric1 =
        createMetric("my.metric1", "description of my.metric1", "attr1");

    EmittedMetrics.MetricsByScope targetMetricsByScope =
        new EmittedMetrics.MetricsByScope(targetScopeName, List.of(metric1));

    EmittedMetrics emittedMetrics = new EmittedMetrics("default", List.of(targetMetricsByScope));

    Map<String, Map<String, MetricParser.MetricAggregator.AggregatedMetricInfo>> metrics =
        MetricParser.MetricAggregator.aggregateMetrics("default", emittedMetrics, targetScopeName);

    Map<String, List<EmittedMetrics.Metric>> result =
        MetricParser.MetricAggregator.buildFilteredMetrics(metrics);

    EmittedMetrics.Metric foundMetric = result.get("default").get(0);
    assertThat(foundMetric.getName()).isEqualTo("my.metric1");
    assertThat(foundMetric.getDescription()).isEqualTo("description of my.metric1");
    assertThat(foundMetric.getType()).isEqualTo("gauge");
    assertThat(foundMetric.getUnit()).isEqualTo("unit");
  }

  private static EmittedMetrics.Metric createMetric(
      String name, String description, String attrName) {
    return new EmittedMetrics.Metric(
        name, description, "gauge", "unit", List.of(new TelemetryAttribute(attrName, "STRING")));
  }
}

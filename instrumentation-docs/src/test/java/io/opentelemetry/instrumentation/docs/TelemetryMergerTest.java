/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.docs.internal.EmittedMetrics;
import io.opentelemetry.instrumentation.docs.internal.EmittedSpans;
import io.opentelemetry.instrumentation.docs.internal.ManualTelemetryEntry;
import io.opentelemetry.instrumentation.docs.internal.TelemetryAttribute;
import io.opentelemetry.instrumentation.docs.internal.TelemetryMerger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TelemetryMergerTest {

  @Test
  void testOverrideMode() {
    List<ManualTelemetryEntry> manualTelemetry = createManualTelemetry();

    // Emitted telemetry that should be ignored
    Map<String, List<EmittedMetrics.Metric>> autoMetrics = createEmittedMetrics();
    Map<String, List<EmittedSpans.Span>> autoSpans = createEmittedSpans();

    TelemetryMerger.MergedTelemetryData result =
        TelemetryMerger.merge(
            manualTelemetry,
            true, // override mode
            autoMetrics,
            autoSpans,
            "test-instrumentation");

    // Should only contain manual telemetry
    assertThat(result.metrics()).hasSize(1);
    assertThat(result.spans()).hasSize(1);

    List<EmittedMetrics.Metric> defaultMetrics = result.metrics().get("default");
    assertThat(defaultMetrics).hasSize(1);
    assertThat(defaultMetrics.get(0).getName()).isEqualTo("manual.metric");

    List<EmittedSpans.Span> defaultSpans = result.spans().get("default");
    assertThat(defaultSpans).hasSize(1);
    assertThat(defaultSpans.get(0).getSpanKind()).isEqualTo("CLIENT");
  }

  @Test
  void testMergeMode() {
    List<ManualTelemetryEntry> manualTelemetry = createManualTelemetry();

    Map<String, List<EmittedMetrics.Metric>> autoMetrics = createEmittedMetrics();
    Map<String, List<EmittedSpans.Span>> autoSpans = createEmittedSpans();

    TelemetryMerger.MergedTelemetryData result =
        TelemetryMerger.merge(
            manualTelemetry,
            false, // merge mode
            autoMetrics,
            autoSpans,
            "test-instrumentation");

    // Should contain both manual and emitted telemetry
    assertThat(result.metrics()).hasSize(1);
    assertThat(result.spans()).hasSize(1);

    List<EmittedMetrics.Metric> defaultMetrics = result.metrics().get("default");
    assertThat(defaultMetrics).hasSize(2);

    List<EmittedSpans.Span> defaultSpans = result.spans().get("default");
    assertThat(defaultSpans).hasSize(2);
  }

  @Test
  void testConflictResolution() {
    // Create manual telemetry with same name as emitted
    List<ManualTelemetryEntry> manualTelemetry = createConflictingManualTelemetry();

    Map<String, List<EmittedMetrics.Metric>> autoMetrics = createEmittedMetrics();
    Map<String, List<EmittedSpans.Span>> autoSpans = createEmittedSpans();

    // Test merge mode with conflicts
    TelemetryMerger.MergedTelemetryData result =
        TelemetryMerger.merge(
            manualTelemetry,
            false, // merge mode
            autoMetrics,
            autoSpans,
            "test-instrumentation");

    // Should prefer manual telemetry for conflicts
    List<EmittedMetrics.Metric> defaultMetrics = result.metrics().get("default");
    assertThat(defaultMetrics).hasSize(1);
    EmittedMetrics.Metric metric = defaultMetrics.get(0);
    assertThat(metric.getName()).isEqualTo("auto.metric");
    assertThat(metric.getDescription()).isEqualTo("Manual description overrides auto");

    List<EmittedSpans.Span> defaultSpans = result.spans().get("default");
    assertThat(defaultSpans).hasSize(1);
    EmittedSpans.Span span = defaultSpans.get(0);
    assertThat(span.getSpanKind()).isEqualTo("SERVER");
    assertThat(span.getAttributes()).hasSize(1);
    assertThat(span.getAttributes().get(0).getName()).isEqualTo("manual.attribute");
  }

  @Test
  void testEmptyManualTelemetry() {
    // Empty manual telemetry
    List<ManualTelemetryEntry> manualTelemetry = new ArrayList<>();

    Map<String, List<EmittedMetrics.Metric>> autoMetrics = createEmittedMetrics();
    Map<String, List<EmittedSpans.Span>> autoSpans = createEmittedSpans();

    // Test merge mode with empty manual telemetry
    TelemetryMerger.MergedTelemetryData result =
        TelemetryMerger.merge(
            manualTelemetry,
            false, // merge mode
            autoMetrics,
            autoSpans,
            "test-instrumentation");

    // Should only contain emitted telemetry
    assertThat(result.metrics()).hasSize(1);
    assertThat(result.spans()).hasSize(1);

    List<EmittedMetrics.Metric> defaultMetrics = result.metrics().get("default");
    assertThat(defaultMetrics).hasSize(1);
    assertThat(defaultMetrics.get(0).getName()).isEqualTo("auto.metric");

    List<EmittedSpans.Span> defaultSpans = result.spans().get("default");
    assertThat(defaultSpans).hasSize(1);
    assertThat(defaultSpans.get(0).getSpanKind()).isEqualTo("SERVER");
  }

  @Test
  void testMultipleWhenConditions() {
    List<ManualTelemetryEntry> manualTelemetry = createMultipleWhenManualTelemetry();

    // Test override mode with multiple conditions
    TelemetryMerger.MergedTelemetryData result =
        TelemetryMerger.merge(
            manualTelemetry,
            true, // override mode
            new HashMap<>(),
            new HashMap<>(),
            "test-instrumentation");

    assertThat(result.metrics()).hasSize(2);
    assertThat(result.metrics()).containsKey("default");
    assertThat(result.metrics()).containsKey("experimental");

    assertThat(result.spans()).hasSize(1);
    assertThat(result.spans()).containsKey("default");
  }

  private static List<ManualTelemetryEntry> createManualTelemetry() {
    List<ManualTelemetryEntry> manualTelemetry = new ArrayList<>();

    ManualTelemetryEntry entry = new ManualTelemetryEntry();
    entry.setWhen("default");

    ManualTelemetryEntry.ManualMetric metric = new ManualTelemetryEntry.ManualMetric();
    metric.setName("manual.metric");
    metric.setDescription("Manual metric description");
    metric.setType("COUNTER");
    metric.setUnit("1");
    List<TelemetryAttribute> metricAttrs = new ArrayList<>();
    metricAttrs.add(new TelemetryAttribute("manual.attr", "STRING"));
    metric.setAttributes(metricAttrs);
    entry.setMetrics(List.of(metric));

    ManualTelemetryEntry.ManualSpan span = new ManualTelemetryEntry.ManualSpan();
    span.setSpanKind("CLIENT");
    List<TelemetryAttribute> spanAttrs = new ArrayList<>();
    spanAttrs.add(new TelemetryAttribute("manual.span.attr", "STRING"));
    span.setAttributes(spanAttrs);
    entry.setSpans(List.of(span));

    manualTelemetry.add(entry);
    return manualTelemetry;
  }

  private static List<ManualTelemetryEntry> createConflictingManualTelemetry() {
    List<ManualTelemetryEntry> manualTelemetry = new ArrayList<>();

    ManualTelemetryEntry entry = new ManualTelemetryEntry();
    entry.setWhen("default");

    // Add manual metric with same name as emitted
    ManualTelemetryEntry.ManualMetric metric = new ManualTelemetryEntry.ManualMetric();
    metric.setName("auto.metric");
    metric.setDescription("Manual description overrides auto");
    metric.setType("HISTOGRAM");
    metric.setUnit("s");
    entry.setMetrics(List.of(metric));

    // Add manual span with same kind as emitted
    ManualTelemetryEntry.ManualSpan span = new ManualTelemetryEntry.ManualSpan();
    span.setSpanKind("SERVER");
    List<TelemetryAttribute> spanAttrs = new ArrayList<>();
    spanAttrs.add(new TelemetryAttribute("manual.attribute", "STRING"));
    span.setAttributes(spanAttrs);
    entry.setSpans(List.of(span));

    manualTelemetry.add(entry);
    return manualTelemetry;
  }

  private static List<ManualTelemetryEntry> createMultipleWhenManualTelemetry() {
    List<ManualTelemetryEntry> manualTelemetry = new ArrayList<>();

    // Default condition
    ManualTelemetryEntry defaultEntry = new ManualTelemetryEntry();
    defaultEntry.setWhen("default");
    ManualTelemetryEntry.ManualMetric defaultMetric = new ManualTelemetryEntry.ManualMetric();
    defaultMetric.setName("default.metric");
    defaultMetric.setDescription("Default metric");
    defaultMetric.setType("COUNTER");
    defaultMetric.setUnit("1");
    defaultEntry.setMetrics(List.of(defaultMetric));

    ManualTelemetryEntry.ManualSpan defaultSpan = new ManualTelemetryEntry.ManualSpan();
    defaultSpan.setSpanKind("CLIENT");
    defaultEntry.setSpans(List.of(defaultSpan));

    // Experimental condition
    ManualTelemetryEntry experimentalEntry = new ManualTelemetryEntry();
    experimentalEntry.setWhen("experimental");
    ManualTelemetryEntry.ManualMetric expMetric = new ManualTelemetryEntry.ManualMetric();
    expMetric.setName("experimental.metric");
    expMetric.setDescription("Experimental metric");
    expMetric.setType("HISTOGRAM");
    expMetric.setUnit("s");
    experimentalEntry.setMetrics(List.of(expMetric));

    manualTelemetry.add(defaultEntry);
    manualTelemetry.add(experimentalEntry);
    return manualTelemetry;
  }

  private static Map<String, List<EmittedMetrics.Metric>> createEmittedMetrics() {
    Map<String, List<EmittedMetrics.Metric>> autoMetrics = new HashMap<>();

    List<TelemetryAttribute> attrs = new ArrayList<>();
    attrs.add(new TelemetryAttribute("auto.attr", "STRING"));

    EmittedMetrics.Metric metric =
        EmittedMetrics.Metric.builder()
            .name("auto.metric")
            .description("emitted metric")
            .type("COUNTER")
            .unit("1")
            .attributes(attrs)
            .build();

    autoMetrics.put("default", new ArrayList<>(List.of(metric)));
    return autoMetrics;
  }

  private static Map<String, List<EmittedSpans.Span>> createEmittedSpans() {
    Map<String, List<EmittedSpans.Span>> autoSpans = new HashMap<>();

    EmittedSpans.Span span = new EmittedSpans.Span();
    span.setSpanKind("SERVER");
    List<TelemetryAttribute> attrs = new ArrayList<>();
    attrs.add(new TelemetryAttribute("auto.span.attr", "STRING"));
    span.setAttributes(attrs);

    autoSpans.put("default", new ArrayList<>(List.of(span)));
    return autoSpans;
  }
}

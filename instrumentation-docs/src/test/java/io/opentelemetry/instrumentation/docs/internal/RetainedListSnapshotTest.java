/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RetainedListSnapshotTest {

  @Test
  void recordListsAreSnapshotsAndRemainReadOnly() {
    List<String> examples = new ArrayList<>(List.of("example"));
    ConfigurationOption option =
        new ConfigurationOption(
            "name",
            null,
            "description",
            null,
            ConfigurationType.STRING,
            examples,
            null,
            null,
            null,
            null,
            null,
            null);
    examples.add("later");

    assertThat(option.examples()).containsExactly("example");
    assertThatThrownBy(() -> option.examples().add("other"))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThat(option.withId("id").examples()).containsExactly("example");

    List<String> required = new ArrayList<>(List.of("name"));
    DeclarativeSchema schema = new DeclarativeSchema("object", required, Map.of());
    required.add("later");

    assertThat(schema.required()).containsExactly("name");
    assertThatThrownBy(() -> schema.required().add("other"))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThat(new DeclarativeSchema("object", null, Map.of()).required()).isNull();
  }

  @Test
  void metadataAndManualTelemetryKeepEditableSnapshots() {
    ConfigurationOption option =
        new ConfigurationOption("name", "description", null, ConfigurationType.STRING);
    List<ConfigurationOption> configurations = new ArrayList<>(List.of(option));
    InstrumentationMetadata metadata = new InstrumentationMetadata();
    metadata.setConfigurations(configurations);
    configurations.clear();

    assertThat(metadata.getConfigurations()).containsExactly(option);
    metadata.getConfigurations().clear();
    assertThat(metadata.getConfigurations()).isEmpty();

    ManualTelemetryEntry.ManualMetric metric =
        new ManualTelemetryEntry.ManualMetric("name", "description", "type", "unit", List.of());
    List<ManualTelemetryEntry.ManualMetric> metrics = new ArrayList<>(List.of(metric));
    ManualTelemetryEntry entry = new ManualTelemetryEntry();
    entry.setMetrics(metrics);
    metrics.clear();

    assertThat(entry.getMetrics()).containsExactly(metric);
    entry.getMetrics().clear();
    assertThat(entry.getMetrics()).isEmpty();
  }

  @Test
  void emittedModelListsAreSnapshots() {
    EmittedEvents.Event event = new EmittedEvents.Event("event", null, List.of());
    List<EmittedEvents.EventsByScope> eventScopes =
        new ArrayList<>(List.of(new EmittedEvents.EventsByScope("scope", List.of(event))));
    EmittedEvents events = new EmittedEvents("default", eventScopes);
    eventScopes.clear();
    assertThat(events.getEventsByScope()).hasSize(1);

    EmittedMetrics.Metric metric =
        new EmittedMetrics.Metric("metric", "description", "LONG_SUM", "1", List.of());
    List<EmittedMetrics.MetricsByScope> metricScopes =
        new ArrayList<>(List.of(new EmittedMetrics.MetricsByScope("scope", List.of(metric))));
    EmittedMetrics metrics = new EmittedMetrics("default", metricScopes);
    metricScopes.clear();
    assertThat(metrics.getMetricsByScope()).hasSize(1);

    EmittedSpans.Span span = new EmittedSpans.Span("INTERNAL", List.of());
    List<EmittedSpans.SpansByScope> spanScopes =
        new ArrayList<>(List.of(new EmittedSpans.SpansByScope("scope", List.of(span))));
    EmittedSpans spans = new EmittedSpans("default", spanScopes);
    spanScopes.clear();
    assertThat(spans.getSpansByScope()).hasSize(1);

    List<EmittedScope.Scope> scopeList =
        new ArrayList<>(List.of(new EmittedScope.Scope("scope", null, null, null)));
    EmittedScope scope = new EmittedScope(scopeList);
    scopeList.clear();
    assertThat(scope.getScopes()).hasSize(1);
    scope.setScopes(null);
    assertThat(scope.getScopes()).isNull();
  }

  @Test
  void instrumentationModuleSnapshotsListsInsideMaps() {
    EmittedMetrics.Metric metric =
        new EmittedMetrics.Metric("metric", "description", "LONG_SUM", "1", List.of());
    List<EmittedMetrics.Metric> metricList = new ArrayList<>(List.of(metric));
    Map<String, List<EmittedMetrics.Metric>> metricMap = new HashMap<>();
    metricMap.put("default", metricList);

    InstrumentationModule module =
        new InstrumentationModule.Builder("test").metrics(metricMap).build();
    metricList.clear();
    metricMap.clear();

    assertThat(module.getMetrics()).containsEntry("default", List.of(metric));
    module.getMetrics().get("default").clear();
    assertThat(module.getMetrics().get("default")).isEmpty();
  }
}

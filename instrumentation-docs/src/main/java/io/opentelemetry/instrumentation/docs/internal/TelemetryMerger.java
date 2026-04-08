/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Handles merging of manual telemetry entries (from metadata.yaml) with emitted telemetry data
 * (from .telemetry files). This class is internal and is hence not for public use. Its APIs are
 * unstable and can change at any time.
 */
public class TelemetryMerger {
  private static final Logger logger = Logger.getLogger(TelemetryMerger.class.getName());

  /**
   * Merges manual telemetry entries with emitted telemetry data based on the override setting.
   *
   * @param manualTelemetry the manual telemetry entries from metadata.yaml
   * @param overrideTelemetry whether to override emitted telemetry
   * @param emittedMetrics the emitted metrics from .telemetry files
   * @param emittedSpans the emitted spans from .telemetry files
   * @param instrumentationName the name of the instrumentation (for logging)
   * @return merged telemetry data as separate maps for metrics and spans
   */
  public static MergedTelemetryData merge(
      List<ManualTelemetryEntry> manualTelemetry,
      boolean overrideTelemetry,
      Map<String, List<EmittedMetrics.Metric>> emittedMetrics,
      Map<String, List<EmittedSpans.Span>> emittedSpans,
      String instrumentationName) {

    if (overrideTelemetry) {
      logger.info(
          "Override mode enabled for " + instrumentationName + ", ignoring emitted telemetry");
      return convertManualTelemetryOnly(manualTelemetry);
    }

    return mergeManualAndEmitted(
        manualTelemetry, emittedMetrics, emittedSpans, instrumentationName);
  }

  /** Converts manual telemetry entries to the standard format, ignoring emitted data. */
  private static MergedTelemetryData convertManualTelemetryOnly(
      List<ManualTelemetryEntry> manualTelemetry) {
    Map<String, List<EmittedMetrics.Metric>> metrics = new HashMap<>();
    Map<String, List<EmittedSpans.Span>> spans = new HashMap<>();

    for (ManualTelemetryEntry entry : manualTelemetry) {
      String when = entry.getWhen();

      if (!entry.getMetrics().isEmpty()) {
        List<EmittedMetrics.Metric> convertedMetrics = convertManualMetrics(entry.getMetrics());
        metrics.computeIfAbsent(when, k -> new ArrayList<>()).addAll(convertedMetrics);
      }

      if (!entry.getSpans().isEmpty()) {
        List<EmittedSpans.Span> convertedSpans = convertManualSpans(entry.getSpans());
        spans.computeIfAbsent(when, k -> new ArrayList<>()).addAll(convertedSpans);
      }
    }

    return new MergedTelemetryData(metrics, spans);
  }

  /**
   * Merges manual telemetry with emitted telemetry, deduplicating by name within the same 'when'
   * condition.
   */
  private static MergedTelemetryData mergeManualAndEmitted(
      List<ManualTelemetryEntry> manualTelemetry,
      Map<String, List<EmittedMetrics.Metric>> emittedMetrics,
      Map<String, List<EmittedSpans.Span>> emittedSpans,
      String instrumentationName) {

    // Start with emitted data (create mutable copies of the lists)
    Map<String, List<EmittedMetrics.Metric>> mergedMetrics = new HashMap<>();
    for (Map.Entry<String, List<EmittedMetrics.Metric>> entry : emittedMetrics.entrySet()) {
      mergedMetrics.put(entry.getKey(), new ArrayList<>(entry.getValue()));
    }

    Map<String, List<EmittedSpans.Span>> mergedSpans = new HashMap<>();
    for (Map.Entry<String, List<EmittedSpans.Span>> entry : emittedSpans.entrySet()) {
      mergedSpans.put(entry.getKey(), new ArrayList<>(entry.getValue()));
    }

    // Add manual telemetry, deduplicating by name
    for (ManualTelemetryEntry entry : manualTelemetry) {
      String when = entry.getWhen();

      if (!entry.getMetrics().isEmpty()) {
        List<EmittedMetrics.Metric> convertedMetrics = convertManualMetrics(entry.getMetrics());
        List<EmittedMetrics.Metric> existingMetrics =
            mergedMetrics.computeIfAbsent(when, k -> new ArrayList<>());
        mergeMetricsList(existingMetrics, convertedMetrics, when, instrumentationName);
      }

      if (!entry.getSpans().isEmpty()) {
        List<EmittedSpans.Span> convertedSpans = convertManualSpans(entry.getSpans());
        List<EmittedSpans.Span> existingSpans =
            mergedSpans.computeIfAbsent(when, k -> new ArrayList<>());
        mergeSpansList(existingSpans, convertedSpans, when, instrumentationName);
      }
    }

    return new MergedTelemetryData(mergedMetrics, mergedSpans);
  }

  /** Merges metrics lists, deduplicating by metric name and logging conflicts. */
  private static void mergeMetricsList(
      List<EmittedMetrics.Metric> existing,
      List<EmittedMetrics.Metric> toAdd,
      String when,
      String instrumentationName) {

    Set<String> existingNames = new HashSet<>();
    for (EmittedMetrics.Metric metric : existing) {
      existingNames.add(metric.getName());
    }

    for (EmittedMetrics.Metric metric : toAdd) {
      if (existingNames.contains(metric.getName())) {
        logger.warning(
            String.format(
                "Manual metric '%s' in 'when: %s' conflicts with emitted metric in %s. Using manual definition.",
                metric.getName(), when, instrumentationName));
        // Remove the existing metric and add the manual one
        existing.removeIf(m -> m.getName().equals(metric.getName()));
      }
      existing.add(metric);
      existingNames.add(metric.getName());
    }
  }

  /** Merges spans lists, deduplicating by span kind and logging conflicts. */
  private static void mergeSpansList(
      List<EmittedSpans.Span> existing,
      List<EmittedSpans.Span> toAdd,
      String when,
      String instrumentationName) {

    Set<String> existingKinds = new HashSet<>();
    for (EmittedSpans.Span span : existing) {
      existingKinds.add(span.getSpanKind());
    }

    for (EmittedSpans.Span span : toAdd) {
      if (existingKinds.contains(span.getSpanKind())) {
        logger.warning(
            String.format(
                "Manual span kind '%s' in 'when: %s' conflicts with emitted span in %s. Using manual definition.",
                span.getSpanKind(), when, instrumentationName));
        // Remove the existing span and add the manual one
        existing.removeIf(s -> s.getSpanKind().equals(span.getSpanKind()));
      }
      existing.add(span);
      existingKinds.add(span.getSpanKind());
    }
  }

  /** Converts manual metrics to the standard EmittedMetrics.Metric format. */
  private static List<EmittedMetrics.Metric> convertManualMetrics(
      List<ManualTelemetryEntry.ManualMetric> manualMetrics) {
    List<EmittedMetrics.Metric> converted = new ArrayList<>();
    for (ManualTelemetryEntry.ManualMetric manual : manualMetrics) {
      converted.add(
          EmittedMetrics.Metric.builder()
              .name(manual.getName())
              .description(manual.getDescription())
              .type(manual.getType())
              .unit(manual.getUnit())
              .attributes(manual.getAttributes())
              .build());
    }
    return converted;
  }

  /** Converts manual spans to the standard EmittedSpans.Span format. */
  private static List<EmittedSpans.Span> convertManualSpans(
      List<ManualTelemetryEntry.ManualSpan> manualSpans) {
    List<EmittedSpans.Span> converted = new ArrayList<>();
    for (ManualTelemetryEntry.ManualSpan manual : manualSpans) {
      converted.add(new EmittedSpans.Span(manual.getSpanKind(), manual.getAttributes()));
    }
    return converted;
  }

  /**
   * Container for merged telemetry data. This class is internal and is hence not for public use.
   * Its APIs are unstable and can change at any time.
   */
  public record MergedTelemetryData(
      Map<String, List<EmittedMetrics.Metric>> metrics,
      Map<String, List<EmittedSpans.Span>> spans) {}

  private TelemetryMerger() {}
}

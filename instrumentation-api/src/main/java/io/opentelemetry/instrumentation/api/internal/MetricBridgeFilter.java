/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Filters metric names using configured exact-match and prefix rules.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class MetricBridgeFilter {

  public static final String DROP_METRICS_CONFIG_PROPERTY =
      "otel.instrumentation.metric-bridge.drop-metrics";
  public static final String DEFAULT_DROP_METRICS = "";

  private final List<String> dropPrefixes;
  private final Set<String> dropExact;

  private MetricBridgeFilter(List<String> dropPrefixes, Set<String> dropExact) {
    this.dropPrefixes = dropPrefixes;
    this.dropExact = dropExact;
  }

  /**
   * Creates a filter from a comma-separated list of metric names or prefixes. e.g.,
   * "jvm.*,process.*,system.cpu.time"
   *
   * @param dropMetricsConfig The comma-separated configuration string. If null or empty, the filter
   *     allows all.
   */
  public static MetricBridgeFilter create(@Nullable String dropMetricsConfig) {
    if (dropMetricsConfig == null || dropMetricsConfig.trim().isEmpty()) {
      return new MetricBridgeFilter(emptyList(), emptySet());
    }

    String[] parts = dropMetricsConfig.split(",");

    List<String> prefixes =
        Arrays.stream(parts)
            .map(String::trim)
            .filter(s -> s.endsWith(".*"))
            .map(s -> s.substring(0, s.length() - 1))
            .collect(toList());

    Set<String> exact =
        Arrays.stream(parts).map(String::trim).filter(s -> !s.endsWith(".*")).collect(toSet());

    return new MetricBridgeFilter(prefixes, exact);
  }

  /**
   * Evaluates whether a given metric name should be dropped based on the filter rules.
   *
   * @param metricName The name of the metric to check.
   * @return true if the metric matches the blocklist and should be dropped, false otherwise.
   */
  public boolean shouldDrop(String metricName) {
    if (dropExact.contains(metricName)) {
      return true;
    }
    for (String prefix : dropPrefixes) {
      if (metricName.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }
}

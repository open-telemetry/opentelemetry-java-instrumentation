/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics.java17;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.runtimemetrics.java8.internal.JmxRuntimeMetricsFactory;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import javax.annotation.Nullable;

/** Builder for {@link RuntimeMetrics}. */
public final class RuntimeMetricsBuilder {

  private final OpenTelemetry openTelemetry;
  // Visible for testing
  final EnumMap<JfrFeature, Boolean> enabledFeatureMap;

  private boolean disableJmx = false;
  private boolean enableExperimentalJmxTelemetry = false;

  RuntimeMetricsBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
    enabledFeatureMap = new EnumMap<>(JfrFeature.class);
    for (JfrFeature feature : JfrFeature.values()) {
      enabledFeatureMap.put(feature, feature.isDefaultEnabled());
    }
  }

  /** Enable telemetry collection for all {@link JfrFeature}s. */
  @CanIgnoreReturnValue
  public RuntimeMetricsBuilder enableAllFeatures() {
    Arrays.stream(JfrFeature.values()).forEach(this::enableFeature);
    return this;
  }

  /** Enable telemetry collection associated with the {@link JfrFeature}. */
  @CanIgnoreReturnValue
  public RuntimeMetricsBuilder enableFeature(JfrFeature feature) {
    enabledFeatureMap.put(feature, true);
    return this;
  }

  /** Disable telemetry collection for all {@link JfrFeature}s. */
  @CanIgnoreReturnValue
  public RuntimeMetricsBuilder disableAllFeatures() {
    Arrays.stream(JfrFeature.values()).forEach(this::disableFeature);
    return this;
  }

  /** Disable telemetry collection for all metrics. */
  @CanIgnoreReturnValue
  public RuntimeMetricsBuilder disableAllMetrics() {
    disableAllFeatures();
    disableAllJmx();
    return this;
  }

  /** Disable telemetry collection associated with the {@link JfrFeature}. */
  @CanIgnoreReturnValue
  public RuntimeMetricsBuilder disableFeature(JfrFeature feature) {
    enabledFeatureMap.put(feature, false);
    return this;
  }

  /** Disable all JMX telemetry collection. */
  @CanIgnoreReturnValue
  public RuntimeMetricsBuilder disableAllJmx() {
    disableJmx = true;
    return this;
  }

  /** Enable experimental JMX telemetry collection. */
  @CanIgnoreReturnValue
  public RuntimeMetricsBuilder enableExperimentalJmxTelemetry() {
    enableExperimentalJmxTelemetry = true;
    return this;
  }

  /** Build and start an {@link RuntimeMetrics} with the config from this builder. */
  public RuntimeMetrics build() {
    List<AutoCloseable> observables =
        disableJmx
            ? List.of()
            : JmxRuntimeMetricsFactory.buildObservables(
                openTelemetry, enableExperimentalJmxTelemetry);
    RuntimeMetrics.JfrRuntimeMetrics jfrRuntimeMetrics = buildJfrMetrics();
    return new RuntimeMetrics(openTelemetry, observables, jfrRuntimeMetrics);
  }

  @Nullable
  private RuntimeMetrics.JfrRuntimeMetrics buildJfrMetrics() {
    if (enabledFeatureMap.values().stream().noneMatch(isEnabled -> isEnabled)) {
      return null;
    }
    return RuntimeMetrics.JfrRuntimeMetrics.build(openTelemetry, enabledFeatureMap::get);
  }
}

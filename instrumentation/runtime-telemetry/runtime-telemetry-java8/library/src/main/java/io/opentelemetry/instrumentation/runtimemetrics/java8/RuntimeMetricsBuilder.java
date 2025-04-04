/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics.java8;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.instrumentation.runtimemetrics.java8.internal.JmxRuntimeMetricsFactory;
import java.util.List;

/** Builder for {@link RuntimeMetrics}. */
public final class RuntimeMetricsBuilder {

  private final MeterProvider meterProvider;

  private boolean enableExperimentalJmxTelemetry = false;

  RuntimeMetricsBuilder(MeterProvider meterProvider) {
    this.meterProvider = meterProvider;
  }

  /** Enable all JMX telemetry collection. */
  @CanIgnoreReturnValue
  public RuntimeMetricsBuilder enableExperimentalJmxTelemetry() {
    enableExperimentalJmxTelemetry = true;
    return this;
  }

  /** Build and start an {@link RuntimeMetrics} with the config from this builder. */
  public RuntimeMetrics build() {
    List<AutoCloseable> observables =
        JmxRuntimeMetricsFactory.buildObservables(meterProvider, enableExperimentalJmxTelemetry);
    return new RuntimeMetrics(observables);
  }
}

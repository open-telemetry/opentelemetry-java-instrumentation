/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics.java8;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.runtimemetrics.java8.internal.JmxRuntimeMetricsFactory;
import java.util.List;

/** Builder for {@link RuntimeMetrics}. */
public final class RuntimeMetricsBuilder {

  private final OpenTelemetry openTelemetry;

  private boolean emitExperimentalTelemetry = false;
  private boolean captureGcCause = false;

  RuntimeMetricsBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /** Enable all JMX telemetry collection. */
  @CanIgnoreReturnValue
  public RuntimeMetricsBuilder emitExperimentalTelemetry() {
    emitExperimentalTelemetry = true;
    return this;
  }

  /** Enable the capture of the jvm.gc.cause attribute with the jvm.gc.duration metric. */
  @CanIgnoreReturnValue
  public RuntimeMetricsBuilder captureGcCause() {
    captureGcCause = true;
    return this;
  }

  /** Build and start an {@link RuntimeMetrics} with the config from this builder. */
  public RuntimeMetrics build() {
    List<AutoCloseable> observables =
        JmxRuntimeMetricsFactory.buildObservables(
            openTelemetry, emitExperimentalTelemetry, captureGcCause);
    return new RuntimeMetrics(observables);
  }
}

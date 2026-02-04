/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics.java8;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.runtimetelemetry.RuntimeTelemetry;
import io.opentelemetry.instrumentation.runtimetelemetry.RuntimeTelemetryBuilder;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.Experimental;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.Internal;

/**
 * Builder for {@link RuntimeMetrics}.
 *
 * @deprecated Use {@link RuntimeTelemetryBuilder} in the {@code runtime-telemetry} module instead.
 */
@Deprecated
public final class RuntimeMetricsBuilder {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.runtime-telemetry-java8";

  private final RuntimeTelemetryBuilder delegate;

  RuntimeMetricsBuilder(OpenTelemetry openTelemetry) {
    this.delegate = RuntimeTelemetry.builder(openTelemetry);
    // Set instrumentation name for backward compatibility
    Internal.setJmxInstrumentationName(delegate, INSTRUMENTATION_NAME);
    // Disable all JFR features - java8 module was JMX-only. This also ensures backward
    // compatibility if the unified module adds new default-enabled JFR features in the future.
    Internal.setDisableAllJfrFeatures(delegate, true);
    // java8 default for captureGcCause was false (unified module default is true)
    Internal.setCaptureGcCause(delegate, false);
  }

  /**
   * Enable experimental JMX telemetry collection. When running on Java 17+, this will also enable
   * experimental JFR metrics. To drop unwanted JFR metrics, configure metric views.
   *
   * @deprecated Use {@link Experimental#setEmitExperimentalMetrics(RuntimeTelemetryBuilder,
   *     boolean)} instead.
   */
  @Deprecated
  @CanIgnoreReturnValue
  public RuntimeMetricsBuilder emitExperimentalTelemetry() {
    Experimental.setEmitExperimentalMetrics(delegate, true);
    return this;
  }

  /**
   * Enable the capture of the jvm.gc.cause attribute with the jvm.gc.duration metric.
   *
   * @deprecated Use {@link RuntimeTelemetry#builder(OpenTelemetry)} in the {@code
   *     runtime-telemetry} module instead. The unified {@link RuntimeTelemetryBuilder} enables GC
   *     cause capture by default.
   */
  @Deprecated
  @CanIgnoreReturnValue
  public RuntimeMetricsBuilder captureGcCause() {
    Internal.setCaptureGcCause(delegate, true);
    return this;
  }

  /** Build and start an {@link RuntimeMetrics} with the config from this builder. */
  public RuntimeMetrics build() {
    return new RuntimeMetrics(delegate.build());
  }
}
